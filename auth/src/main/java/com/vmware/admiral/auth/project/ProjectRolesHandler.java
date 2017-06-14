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

import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.auth.util.UserGroupsUpdater;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;

/**
 * Handles assignment/unassignment of multiple users with multiple roles to/from a project. Used for
 * PATCH and PUT requests for the {@link ProjectService}.
 */
public class ProjectRolesHandler {

    private static final String BODY_IS_REQUIRED_MESSAGE = "Body is required.";
    private static final String BODY_IS_REQUIRED_MESSAGE_CODE = "auth.body.required";

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
        return updateAdmins || updateMembers;
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
        DeferredResult<Void> updateAdmins;
        DeferredResult<Void> updateMembers;

        if (patchBody.administrators == null) {
            updateAdmins = DeferredResult.completed(null);
        } else {
            updateAdmins = UserGroupsUpdater.create(getHost(),
                    projectState.administratorsUserGroupLink, getProjectLink(),
                    patchBody.administrators.add, patchBody.administrators.remove)
                    .update();
        }

        if (patchBody.members == null) {
            updateMembers = DeferredResult.completed(null);
        } else {
            updateMembers = UserGroupsUpdater.create(getHost(),
                    projectState.membersUserGroupLink, getProjectLink(),
                    patchBody.members.add, patchBody.members.remove)
                    .update();
        }

        return DeferredResult.allOf(updateAdmins, updateMembers);
    }

    private ServiceHost getHost() {
        return serviceHost;
    }

    private String getProjectLink() {
        return projectLink;
    }

}
