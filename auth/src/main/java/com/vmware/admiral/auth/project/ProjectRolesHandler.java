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
import java.util.EnumSet;
import java.util.List;

import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.Principal.PrincipalType;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.auth.util.UserGroupsUpdater;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
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

    private ServiceHost serviceHost;
    private String projectLink;

    public ProjectRolesHandler(ServiceHost serviceHost, String projectLink) {
        AssertUtil.assertNotNull(serviceHost, "serviceHost");
        AssertUtil.assertNotEmpty(projectLink, "projectLink");
        this.serviceHost = serviceHost;
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

    public DeferredResult<ProjectState> handleRolesUpdate(ProjectState project, ProjectRoles patchBody) {
        if (patchBody == null) {
            return DeferredResult.failed(new LocalizableValidationException(
                    BODY_IS_REQUIRED_MESSAGE, BODY_IS_REQUIRED_MESSAGE_CODE));
        }

        return handleRolesAssignment(project, patchBody);
    }

    private DeferredResult<ProjectState> handleRolesAssignment(ProjectState projectState,
            ProjectRoles patchBody) {
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

        buildAddRemoveLists(patchBody.administrators, adminUsersToAdd, adminUserGroupsToAdd, adminUsersToRemove, adminUserGroupsToRemove, principalResults);
        buildAddRemoveLists(patchBody.members, membersUsersToAdd, membersUserGroupsToAdd, membersUsersToRemove, membersUserGroupsToRemove, principalResults);
        buildAddRemoveLists(patchBody.viewers, viewersUsersToAdd, viewersUserGroupsToAdd, viewersUsersToRemove, viewersUserGroupsToRemove, principalResults);

        return DeferredResult.allOf(principalResults)
                .thenCompose(ignore -> {
                    results.add(handleUserAssignment(adminUsersToAdd, adminUsersToRemove,
                            AuthRole.PROJECT_ADMIN));
                    results.add(handleUserAssignment(membersUsersToAdd, membersUsersToRemove,
                            AuthRole.PROJECT_MEMBER));
                    results.add(handleUserAssignment(viewersUsersToAdd, viewersUsersToRemove,
                            AuthRole.PROJECT_VIEWER));
                    results.add(handleGroupsAssignment(
                            projectState,
                            adminUserGroupsToAdd, adminUserGroupsToRemove,
                            membersUserGroupsToAdd, membersUserGroupsToRemove,
                            viewersUserGroupsToAdd, viewersUserGroupsToRemove));
                    return DeferredResult.allOf(results);
                })
                .thenCompose(ignore -> {
                    return DeferredResult.completed(projectState);
                });
    }

    private DeferredResult<Principal> getPrincipal(String principal) {
        Operation getPrincipalOp = Operation.createGet(getHost(), UriUtils.buildUriPath(PrincipalService.SELF_LINK, principal))
                .setReferer(getHost().getUri());

        return getHost().sendWithDeferredResult(getPrincipalOp, Principal.class);
    }

    /**
     * Iterates through the specified {@link PrincipalRoleAssignment} and fills the provided lists
     * with users and groups to add/remove from a given project.
     */
    private void buildAddRemoveLists(PrincipalRoleAssignment assignment,
            List<String> usersToAdd, List<String> userGroupsToAdd,
            List<String> usersToRemove, List<String> userGroupsToRemove,
            List<DeferredResult<Void>> principalResults) {
        if (assignment == null) {
            return;
        }

        if (assignment.add != null && !assignment.add.isEmpty()) {
            assignment.add.stream().forEach(principal -> {
                principalResults.add(getPrincipal(principal).thenAccept(p -> {
                    if (PrincipalType.USER.equals(p.type)) {
                        usersToAdd.add(principal);
                    } else {
                        userGroupsToAdd.add(principal);
                    }
                }));
            });
        }

        if (assignment.remove != null && !assignment.remove.isEmpty()) {
            assignment.remove.stream().forEach(principal -> {
                principalResults.add(getPrincipal(principal).thenAccept(p -> {
                    if (PrincipalType.USER.equals(p.type)) {
                        usersToRemove.add(principal);
                    } else {
                        userGroupsToRemove.add(principal);
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
        case PROJECT_VIEWER:
            groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    role.buildRoleWithSuffix(projectId));
            break;

        default:
            return DeferredResult.failed(new IllegalArgumentException(NOT_PROJECT_ROLE_MESSAGE));
        }

        return UserGroupsUpdater.create()
                .setGroupLink(groupLink)
                .setHost(getHost())
                .setReferrer(getHost().getUri().toString())
                .setUsersToAdd(addPrincipals)
                .setUsersToRemove(removePrincipals)
                .update();
    }

    private DeferredResult<Void> handleGroupsAssignment(ProjectState projectState,
            List<String> adminUserGroupsToAdd, List<String> adminUserGroupsToRemove,
            List<String> memberUserGroupsToAdd, List<String> memberUserGroupsToRemove,
            List<String> viewerUserGroupsToAdd, List<String> viewerUserGroupsToRemove)  {

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

    private DeferredResult<Void> handleProjectViewerGroupAssignment(ProjectState projectState, String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);
        RoleState role = AuthUtil.buildProjectViewersRole(projectId, groupId, null);
        return createRole(projectId, groupId, role)
               .thenAccept(ignore -> {
                   projectState.viewersUserGroupLinks.add(role.userGroupLink);
               });
    }

    private DeferredResult<Void> handleProjectViewerGroupUnssignment(ProjectState projectState, String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);

        String roleLink = AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(projectId, groupId);
        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, groupId);
        return deleteRole(roleLink).thenAccept(ignore -> {
            projectState.viewersUserGroupLinks.remove(userGroupLink);
        });
    }

    private DeferredResult<Void> handleProjectMemberGroupAssignment(ProjectState projectState, String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);
        RoleState role = AuthUtil.buildProjectMembersRole(projectId, groupId, null);
        return createRole(projectId, groupId, role)
               .thenAccept(ignore -> {
                   projectState.membersUserGroupLinks.add(role.userGroupLink);
               });
    }

    private DeferredResult<Void> handleProjectMemberGroupUnssignment(ProjectState projectState, String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);

        String roleLink = AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId, groupId);
        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, groupId);

        return deleteRole(roleLink).thenAccept(ignore -> {
            projectState.membersUserGroupLinks.remove(userGroupLink);
        });
    }

    private DeferredResult<Void> handleProjectAdminGroupAssignment(ProjectState projectState, String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);
        RoleState role = AuthUtil.buildProjectAdminsRole(projectId, groupId, null);
        return createRole(projectId, groupId, role)
               .thenAccept(ignore -> {
                   projectState.administratorsUserGroupLinks.add(role.userGroupLink);
               });
    }

    private DeferredResult<Void> handleProjectAdminGroupUnassignment(ProjectState projectState, String groupId) {
        String projectId = Service.getId(projectState.documentSelfLink);
        String roleLink = AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId, groupId);
        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, groupId);

        return deleteRole(roleLink).thenAccept(ignore -> {
            projectState.administratorsUserGroupLinks.remove(userGroupLink);
        });
    }

    private DeferredResult<RoleState> createRole(String projectId, String groupId, RoleState role) {
        Operation principalGroupOp = Operation.createGet(getHost(), UriUtils.buildUriPath(PrincipalService.SELF_LINK, groupId))
                .setReferer(getHost().getUri());

        Operation userGroupGetOp = Operation.createGet(getHost(), UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, groupId))
                .setReferer(getHost().getUri());

        Operation userGroupPostOp = Operation.createPost(getHost(), UriUtils.buildUriPath(UserGroupService.FACTORY_LINK))
                .setReferer(getHost().getUri())
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);

        Operation resourceGroupOp = Operation.createGet(getHost(), UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, projectId))
                .setReferer(getHost().getUri());

        Operation rolePostOp = Operation.createPost(getHost(), UriUtils.buildUriPath(RoleService.FACTORY_LINK))
                .setReferer(getHost().getUri());

        return getHost().sendWithDeferredResult(principalGroupOp, Principal.class)
            .thenCompose(principal -> {
                // retrieve the UserGroupState for the given group
                return getHost().sendWithDeferredResult(userGroupGetOp, UserGroupState.class);
            })
            .thenApply(ug -> Pair.of(ug, (Throwable) null))
            .exceptionally(ex -> Pair.of(null, ex))
            .thenCompose(pair -> {
                // create UserGroupState if doesn't exist
                if (pair.right != null) {
                    userGroupPostOp.setBody(AuthUtil.buildProjectMembersUserGroupByGroupId(groupId));
                    return getHost().sendWithDeferredResult(userGroupPostOp, UserGroupState.class);
                }

                // returns the fetched UserGroupState
                return DeferredResult.completed(pair.left);
            })
            .thenCompose(userGroup -> {
                role.userGroupLink = userGroup.documentSelfLink;
                return getHost().sendWithDeferredResult(resourceGroupOp, ResourceGroupState.class);
            })
            .thenCompose(rg -> {
                role.resourceGroupLink = rg.documentSelfLink;
                rolePostOp.setBody(role);
                return getHost().sendWithDeferredResult(rolePostOp, RoleState.class);
            });
    }

    private DeferredResult<Void> deleteRole(String roleLink) {
        Operation roleDeleteOp = Operation.createDelete(getHost(), UriUtils.buildUriPath(RoleService.FACTORY_LINK, roleLink))
                .setReferer(getHost().getUri());

        return getHost().sendWithDeferredResult(roleDeleteOp).thenAccept(ignore -> {
        });
    }

    private ServiceHost getHost() {
        return serviceHost;
    }

    private String getProjectLink() {
        return projectLink;
    }
}
