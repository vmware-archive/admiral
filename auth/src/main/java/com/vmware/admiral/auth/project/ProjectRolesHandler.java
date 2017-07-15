/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.auth.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.Principal.PrincipalType;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.auth.util.PrincipalUtil;
import com.vmware.admiral.auth.util.UserGroupsUpdater;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;

/**
 * Handles assignment/unassignment of multiple users with multiple roles to/from a project. Used for
 * PATCH and PUT requests for the {@link ProjectService}.
 */
public class ProjectRolesHandler {

    private static final String BODY_IS_REQUIRED_MESSAGE = "Body is required.";
    private static final String BODY_IS_REQUIRED_MESSAGE_CODE = "auth.body.required";

    public static final EnumSet<AuthRole> PROJECT_ROLES = EnumSet.of(AuthRole.PROJECT_ADMIN,
            AuthRole.PROJECT_MEMBER, AuthRole.PROJECT_VIEWER);

    private static final String NOT_PROJECT_ROLE_MESSAGE = String.format(
            "Role should be one of: %s", PROJECT_ROLES);

    /**
     * DTO for custom PATCH and PUT requests to {@link ProjectService} instances. Used for
     * assignment/unassigment of users with various role.
     */
    public static class ProjectRoles {

        /**
         * Assignment/unassignment of project administrators.
         */
        public PrincipalRoleAssignment administrators;

        /**
         * Assignment/unassignment of project members.
         */
        public PrincipalRoleAssignment members;

        /**
         * Assignment/unassignment of project viewers.
         */
        public PrincipalRoleAssignment viewers;

    }

    private Service service;
    private String projectLink;

    public ProjectRolesHandler(Service service, String projectLink) {
        AssertUtil.assertNotNull(service, "service");
        AssertUtil.assertNotEmpty(projectLink, "projectLink");
        this.service = service;
        this.projectLink = projectLink;
    }

    public static boolean isProjectRolesUpdate(Operation op) {
        ProjectRoles body = op.getBody(ProjectRoles.class);
        if (body == null) {
            return false;
        }
        boolean updateAdmins = body.administrators != null && hasRolesUpdate(body.administrators);
        boolean updateMembers = body.members != null && hasRolesUpdate(body.members);
        boolean updateViewers = body.viewers != null && hasRolesUpdate(body.viewers);
        return updateAdmins || updateMembers || updateViewers;
    }

    private static boolean hasRolesUpdate(PrincipalRoleAssignment rolesAssignment) {
        return (rolesAssignment.add != null && !rolesAssignment.add.isEmpty())
                || (rolesAssignment.remove != null && !rolesAssignment.remove.isEmpty());
    }

    public DeferredResult<ProjectState> handleRolesUpdate(ProjectState project,
            ProjectRoles patchBody, Operation requestorOperation) {
        if (patchBody == null) {
            return DeferredResult.failed(new LocalizableValidationException(
                    BODY_IS_REQUIRED_MESSAGE, BODY_IS_REQUIRED_MESSAGE_CODE));
        }

        return handleRolesAssignment(project, patchBody, requestorOperation);
    }

    private DeferredResult<ProjectState> handleRolesAssignment(ProjectState projectState,
            ProjectRoles patchBody, Operation requestorOperation) {
        List<String> adminUsersToAdd = new ArrayList<>();
        List<String> adminUsersToRemove = new ArrayList<>();
        List<String> membersUsersToAdd = new ArrayList<>();
        List<String> membersUsersToRemove = new ArrayList<>();
        List<String> viewersUsersToAdd = new ArrayList<>();
        List<String> viewersUsersToRemove = new ArrayList<>();

        List<String> adminUserGroupsToAdd = new ArrayList<>();
        List<String> adminUserGroupsToRemove = new ArrayList<>();
        List<String> membersUserGroupsToAdd = new ArrayList<>();
        List<String> membersUserGroupsToRemove = new ArrayList<>();
        List<String> viewersUserGroupsToAdd = new ArrayList<>();
        List<String> viewersUserGroupsToRemove = new ArrayList<>();

        List<DeferredResult<Void>> principalResults = new ArrayList<>();
        List<DeferredResult<Void>> results = new ArrayList<>();

        buildAddRemoveLists(patchBody.administrators, adminUsersToAdd, adminUserGroupsToAdd,
                adminUsersToRemove, adminUserGroupsToRemove, principalResults, requestorOperation);
        buildAddRemoveLists(patchBody.members, membersUsersToAdd, membersUserGroupsToAdd,
                membersUsersToRemove, membersUserGroupsToRemove, principalResults,
                requestorOperation);
        buildAddRemoveLists(patchBody.viewers, viewersUsersToAdd, viewersUserGroupsToAdd,
                viewersUsersToRemove, viewersUserGroupsToRemove, principalResults,
                requestorOperation);

        return DeferredResult.allOf(principalResults)
                .thenCompose(ignore -> {
                    results.add(handleUserAssignment(adminUsersToAdd, adminUsersToRemove,
                            AuthRole.PROJECT_ADMIN));
                    results.add(handleUserAssignment(membersUsersToAdd, membersUsersToRemove,
                            AuthRole.PROJECT_MEMBER));
                    results.add(handleUserAssignment(membersUsersToAdd, membersUsersToRemove,
                            AuthRole.PROJECT_MEMBER_EXTENDED));
                    results.add(handleUserAssignment(viewersUsersToAdd, viewersUsersToRemove,
                            AuthRole.PROJECT_VIEWER));
                    results.add(handleGroupsAssignment(
                            projectState,
                            adminUserGroupsToAdd, adminUserGroupsToRemove,
                            membersUserGroupsToAdd, membersUserGroupsToRemove,
                            viewersUserGroupsToAdd, viewersUserGroupsToRemove));
                    return DeferredResult.allOf(results);
                })
                .thenCompose(ignore -> DeferredResult.completed(projectState));
    }

    /**
     * Iterates through the specified {@link PrincipalRoleAssignment} and fills the provided lists
     * with users and groups to add/remove from a given project.
     *
     * @param requestorService
     * @param requestorOperation
     */
    private void buildAddRemoveLists(PrincipalRoleAssignment assignment,
            List<String> usersToAdd, List<String> userGroupsToAdd,
            List<String> usersToRemove, List<String> userGroupsToRemove,
            List<DeferredResult<Void>> principalResults, Operation requestorOperation) {
        if (assignment == null) {
            return;
        }

        if (assignment.add != null && !assignment.add.isEmpty()) {
            assignment.add.stream().forEach(principalId -> {
                principalResults.add(PrincipalUtil
                        .getPrincipal(service, requestorOperation, principalId)
                        .thenAccept(p -> {
                            if (PrincipalType.USER.equals(p.type)) {
                                usersToAdd.add(principalId);
                            } else {
                                userGroupsToAdd.add(principalId);
                            }
                        }));
            });
        }

        if (assignment.remove != null && !assignment.remove.isEmpty()) {
            assignment.remove.stream().forEach(principalId -> {
                principalResults.add(PrincipalUtil
                        .getPrincipal(service, requestorOperation, principalId)
                        .thenAccept(p -> {
                            if (PrincipalType.USER.equals(p.type)) {
                                usersToRemove.add(principalId);
                            } else {
                                userGroupsToRemove.add(principalId);
                            }
                        }));
            });
        }
    }

    private DeferredResult<Void> handleUserAssignment(List<String> addPrincipals,
            List<String> removePrincipals, AuthRole role) {

        String projectId = Service.getId(projectLink);

        String groupLink;

        switch (role) {
        case PROJECT_ADMIN:
        case PROJECT_MEMBER:
        case PROJECT_MEMBER_EXTENDED:
        case PROJECT_VIEWER:
            groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    role.buildRoleWithSuffix(projectId));
            break;

        default:
            return DeferredResult.failed(new IllegalArgumentException(NOT_PROJECT_ROLE_MESSAGE));
        }

        List<DeferredResult<Void>> results = new ArrayList<>();

        for (String principal : addPrincipals) {
            results.add(PrincipalUtil.getOrCreateUser(service, principal)
                    .thenCompose(user -> UserGroupsUpdater.create()
                            .setGroupLink(groupLink)
                            .setService(service)
                            .setUsersToAdd(Collections.singletonList(principal))
                            .update()));
        }

        for (String principal : removePrincipals) {
            results.add(PrincipalUtil.getOrCreateUser(service, principal)
                    .thenCompose(user -> UserGroupsUpdater.create()
                            .setGroupLink(groupLink)
                            .setService(service)
                            .setUsersToRemove(Collections.singletonList(principal))
                            .update()));
        }

        return DeferredResult.allOf(results).thenAccept(ignore -> {
        });
    }

    private DeferredResult<Void> handleGroupsAssignment(ProjectState projectState,
            List<String> adminUserGroupsToAdd, List<String> adminUserGroupsToRemove,
            List<String> memberUserGroupsToAdd, List<String> memberUserGroupsToRemove,
            List<String> viewerUserGroupsToAdd, List<String> viewerUserGroupsToRemove) {

        List<DeferredResult<Void>> results = new ArrayList<>();

        for (String addAdminGroup : adminUserGroupsToAdd) {
            results.add(handleProjectAdminGroupAssignment(projectState, addAdminGroup));
        }

        for (String removeAdminGroup : adminUserGroupsToRemove) {
            results.add(handleProjectAdminGroupUnassignment(projectState, removeAdminGroup));
        }

        for (String addMemberGroup : memberUserGroupsToAdd) {
            results.add(handleProjectMemberGroupAssignment(projectState, addMemberGroup));
        }

        for (String removeMemberGroup : memberUserGroupsToRemove) {
            results.add(handleProjectMemberGroupUnssignment(projectState, removeMemberGroup));
        }

        for (String addViewer : viewerUserGroupsToAdd) {
            results.add(handleProjectViewerGroupAssignment(projectState, addViewer));
        }

        for (String removeViewer : viewerUserGroupsToRemove) {
            results.add(handleProjectViewerGroupUnssignment(projectState, removeViewer));
        }

        return DeferredResult.allOf(results).thenAccept(ignore -> {
        });
    }

    private DeferredResult<Void> handleProjectViewerGroupAssignment(ProjectState projectState,
            String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);
        RoleState role = AuthUtil.buildProjectViewersRole(projectId, groupId, null);
        return createRole(projectId, groupId, role)
                .thenAccept(ignore -> projectState.viewersUserGroupLinks.add(role.userGroupLink));
    }

    private DeferredResult<Void> handleProjectViewerGroupUnssignment(ProjectState projectState,
            String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);

        String roleLink = AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(projectId, groupId);
        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, groupId);
        return deleteRole(roleLink)
                .thenAccept(ignore -> projectState.viewersUserGroupLinks.remove(userGroupLink));
    }

    private DeferredResult<Void> handleProjectMemberGroupAssignment(ProjectState projectState,
            String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);
        RoleState role = AuthUtil.buildProjectMembersRole(projectId, groupId, null);
        return createRole(projectId, groupId, role)
                .thenCompose(ignore -> createExtendedMemberRole(projectId, groupId))
                .thenAccept(ignore -> projectState.membersUserGroupLinks.add(role.userGroupLink));
    }

    private DeferredResult<Void> handleProjectMemberGroupUnssignment(ProjectState projectState,
            String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);

        String roleLink = AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId, groupId);
        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, groupId);

        return deleteRole(roleLink)
                .thenCompose(ignore -> deleteExtendedMemberRole(projectId, groupId))
                .thenAccept(ignore -> projectState.membersUserGroupLinks.remove(userGroupLink));
    }

    private DeferredResult<Void> handleProjectAdminGroupAssignment(ProjectState projectState,
            String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);
        RoleState role = AuthUtil.buildProjectAdminsRole(projectId, groupId, null);
        return createRole(projectId, groupId, role)
                .thenAccept(ignore -> projectState.administratorsUserGroupLinks
                        .add(role.userGroupLink));
    }

    private DeferredResult<Void> handleProjectAdminGroupUnassignment(ProjectState projectState,
            String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);
        String roleLink = AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId, groupId);
        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, groupId);

        return deleteRole(roleLink)
                .thenAccept(
                        ignore -> projectState.administratorsUserGroupLinks.remove(userGroupLink));
    }

    private DeferredResult<RoleState> createRole(String projectId, String groupId, RoleState role) {
        Operation principalGroupOp = Operation
                .createGet(service, UriUtils.buildUriPath(PrincipalService.SELF_LINK, groupId));

        Operation userGroupGetOp = Operation
                .createGet(service, UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, groupId));

        Operation userGroupPostOp = Operation
                .createPost(service, UriUtils.buildUriPath(UserGroupService.FACTORY_LINK))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);

        Operation resourceGroupOp = Operation.createGet(service,
                UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, projectId));

        Operation rolePostOp = Operation
                .createPost(service, UriUtils.buildUriPath(RoleService.FACTORY_LINK))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);

        return service.sendWithDeferredResult(principalGroupOp, Principal.class)
                .thenCompose(principal -> {
                    // retrieve the UserGroupState for the given group
                    return service.sendWithDeferredResult(userGroupGetOp, UserGroupState.class);
                })
                .thenApply(ug -> Pair.of(ug, (Throwable) null))
                .exceptionally(ex -> Pair.of(null, ex))
                .thenCompose(pair -> {
                    // create UserGroupState if doesn't exist
                    if (pair.right != null) {
                        userGroupPostOp
                                .setBody(AuthUtil.buildProjectMembersUserGroupByGroupId(groupId));
                        return service.sendWithDeferredResult(userGroupPostOp,
                                UserGroupState.class);
                    }

                    // returns the fetched UserGroupState
                    return DeferredResult.completed(pair.left);
                })
                .thenCompose(userGroup -> {
                    role.userGroupLink = userGroup.documentSelfLink;
                    return service.sendWithDeferredResult(resourceGroupOp,
                            ResourceGroupState.class);
                })
                .thenCompose(rg -> {
                    role.resourceGroupLink = rg.documentSelfLink;
                    rolePostOp.setBody(role);
                    return service.sendWithDeferredResult(rolePostOp, RoleState.class);
                });
    }

    private DeferredResult<Void> createExtendedMemberRole(String projectId, String groupId) {

        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, groupId);

        ResourceGroupState resourceGroupState = AuthUtil.buildProjectExtendedMemberResourceGroup(
                projectId, groupId);

        RoleState roleState = AuthUtil.buildProjectExtendedMembersRole(projectId, userGroupLink,
                resourceGroupState.documentSelfLink);

        Operation resourceGroupPostOp = Operation
                .createPost(service, ResourceGroupService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(resourceGroupState);

        Operation rolePostOp = Operation
                .createPost(service, RoleService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(roleState);

        return service.sendWithDeferredResult(resourceGroupPostOp, ResourceGroupState.class)
                .thenCompose(
                        ignore -> service.sendWithDeferredResult(rolePostOp, RoleState.class))
                .thenAccept(ignore -> {
                });
    }

    private DeferredResult<Void> deleteRole(String roleLink) {
        Operation roleDeleteOp = Operation
                .createDelete(service, UriUtils.buildUriPath(RoleService.FACTORY_LINK, roleLink));

        return service.sendWithDeferredResult(roleDeleteOp).thenAccept(ignore -> {
        });
    }

    private DeferredResult<Void> deleteExtendedMemberRole(String projectId, String groupId) {
        String roleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId, groupId));

        String resourceGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId, groupId));

        Operation deleteRgOp = Operation.createDelete(service, resourceGroupLink);

        Operation deleteRoleOp = Operation.createDelete(service, roleLink);

        return service.sendWithDeferredResult(deleteRgOp)
                .thenCompose(ignore -> service.sendWithDeferredResult(deleteRoleOp))
                .thenAccept(ignore -> {
                });
    }

}
