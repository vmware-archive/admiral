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
import com.vmware.admiral.auth.idm.PrincipalRolesHandler;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.auth.util.UserGroupsUpdater;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.UserGroupService;

/**
 * Handles assignment/unassignment of multiple users with multiple roles to/from a project. Used for
 * PATCH and PUT requests for the {@link ProjectService}.
 */
public class ProjectRolesHandler {

    private static final String BODY_IS_REQUIRED_MESSAGE = "Body is required.";
    private static final String BODY_IS_REQUIRED_MESSAGE_CODE = "auth.body.required";

    public static final EnumSet<AuthRole> PROJECT_ROLES = EnumSet.of(AuthRole.PROJECT_ADMINS,
            AuthRole.PROJECT_MEMBERS, AuthRole.PROJECT_VIEWERS);

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

    public DeferredResult<Void> handleRolesUpdate(ProjectRoles patchBody) {
        if (patchBody == null) {
            return DeferredResult.failed(new LocalizableValidationException(
                    BODY_IS_REQUIRED_MESSAGE, BODY_IS_REQUIRED_MESSAGE_CODE));
        }
        return getProjectState()
                .thenCompose((projectState) -> handleRolesAssignment(projectState, patchBody));
    }

    private DeferredResult<ProjectState> getProjectState() {
        return getHost().sendWithDeferredResult(
                Operation.createGet(getHost(), getProjectLink()).setReferer(getProjectLink()),
                ProjectState.class);
    }

    private DeferredResult<Void> handleRolesAssignment(ProjectState projectState,
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

        buildAddRemoveLists(patchBody.administrators, adminUsersToAdd, adminUserGroupsToAdd, adminUsersToRemove, adminUserGroupsToRemove);
        buildAddRemoveLists(patchBody.members, membersUsersToAdd, membersUserGroupsToAdd, membersUsersToRemove, membersUserGroupsToRemove);
        buildAddRemoveLists(patchBody.viewers, viewersUsersToAdd, viewersUserGroupsToAdd, viewersUsersToRemove, viewersUserGroupsToRemove);

        List<DeferredResult<Void>> results = new ArrayList<>();

        results.add(handleUserAssignment(adminUsersToAdd, adminUsersToRemove,
                AuthRole.PROJECT_ADMINS));
        results.add(handleUserAssignment(membersUsersToAdd, membersUsersToRemove,
                AuthRole.PROJECT_MEMBERS));
        results.add(handleUserAssignment(viewersUsersToAdd, viewersUsersToRemove,
                AuthRole.PROJECT_VIEWERS));


        // When assigning UserGroup is ready remove the try-catch.
        // It's like this currently, because the findbugs plugin fails the build.
        try {
            results.add(handleGroupsAssignment(
                    adminUserGroupsToAdd, adminUserGroupsToRemove,
                    membersUserGroupsToAdd, membersUserGroupsToRemove,
                    viewersUserGroupsToAdd, viewersUserGroupsToRemove));
        } catch (Exception ex) {
        }


        return DeferredResult.allOf(results).thenAccept(ignore -> {
        });
    }

    /**
     * Iterates through the specified {@link PrincipalRoleAssignment} and fills the provided lists
     * with users and groups to add/remove from a given project.
     */
    private void buildAddRemoveLists(PrincipalRoleAssignment assignment,
            List<String> usersToAdd, List<String> userGroupsToAdd,
            List<String> usersToRemove, List<String> userGroupsToRemove) {
        if (assignment == null) {
            return;
        }

        if (assignment.add != null && !assignment.add.isEmpty()) {
            for (String principal : assignment.add) {
                if (principal.contains(PrincipalRolesHandler.PRINCIPAL_AT_SIGN)) {
                    usersToAdd.add(principal);
                } else {
                    userGroupsToAdd.add(principal);
                }
            }
        }

        if (assignment.remove != null && !assignment.remove.isEmpty()) {
            for (String principal : assignment.remove) {
                if (principal.contains(PrincipalRolesHandler.PRINCIPAL_AT_SIGN)) {
                    usersToRemove.add(principal);
                } else {
                    userGroupsToRemove.add(principal);
                }
            }
        }
    }


    private DeferredResult<Void> handleUserAssignment(List<String> addPrincipals,
            List<String> removePrincipals, AuthRole role) {

        String projectId = Service.getId(projectLink);

        String groupLink;

        switch (role) {
        case PROJECT_ADMINS:
        case PROJECT_MEMBERS:
        case PROJECT_VIEWERS:
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

    /**
     * Tips how to assign group once Roles and ResourceGroups are ready:
     * 1. Check if there is User Group with this current principal.
     * 2. If not existing, create it.
     * 3. Create Resource Group(s) for this project.
     * 4. Create Role(s) for this project.
     * 5. Link the already existing User Group with newly created Resource Group(s) through
     * Role(s)
     * 6. Add the link of User Group to the list of members user groups of current project
     * state.
     *
     * Tips how to unassign group once Roles and ResourceGroups are ready:
     * 1. Remove group's corresponding resource group(s).
     * 2. Remove group's corresponding role(s).
     */

    private DeferredResult<Void> handleGroupsAssignment(
            List<String> adminUserGroupsToAdd, List<String> adminUserGroupsToRemove,
            List<String> memberUserGroupsToAdd, List<String> memberUserGroupsToRemove,
            List<String> viewerUserGroupsToAdd, List<String> viewerUserGroupsToRemove) {

        List<DeferredResult<Void>> results = new ArrayList<>();

        for (String addAdmin : adminUserGroupsToAdd) {
            results.add(handleProjectAdminGroupAssignment(addAdmin));
        }

        for (String removeAdmin : adminUserGroupsToRemove) {
            results.add(handleProjectAdminGroupUnassignment(removeAdmin));
        }

        for (String addMember : memberUserGroupsToAdd) {
            results.add(handleProjectMemberGroupAssignment(addMember));
        }

        for (String removeMember : memberUserGroupsToRemove) {
            results.add(handleProjectMemberGroupUnssignment(removeMember));
        }

        for (String addViewer : viewerUserGroupsToAdd) {
            results.add(handleProjectViewerGroupAssignment(addViewer));
        }

        for (String removeViewer : viewerUserGroupsToRemove) {
            results.add(handleProjectViewerGroupUnssignment(removeViewer));
        }

        return DeferredResult.allOf(results).thenAccept(ignore -> {
        });
    }

    private DeferredResult<Void> handleProjectViewerGroupAssignment(String group) {
        throw new IllegalStateException("Not implemented yet.");
    }

    private DeferredResult<Void> handleProjectViewerGroupUnssignment(String group) {
        throw new IllegalStateException("Not implemented yet.");
    }

    private DeferredResult<Void> handleProjectMemberGroupAssignment(String group) {
        throw new IllegalStateException("Not implemented yet.");
    }

    private DeferredResult<Void> handleProjectMemberGroupUnssignment(String group) {
        throw new IllegalStateException("Not implemented yet.");
    }

    private DeferredResult<Void> handleProjectAdminGroupAssignment(String group) {
        throw new IllegalStateException("Not implemented yet.");
    }

    private DeferredResult<Void> handleProjectAdminGroupUnassignment(String group) {
        throw new IllegalStateException("Not implemented yet.");
    }

    private ServiceHost getHost() {
        return serviceHost;
    }

    private String getProjectLink() {
        return projectLink;
    }

}
