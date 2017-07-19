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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.project.ProjectService.ExpandedProjectState;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;

public class ProjectRolesHandlerTest extends AuthBaseTest {

    private static final String PROJECT_NAME = "testName";
    private static final String PROJECT_DESCRIPTION = "testDescription";
    private static final boolean PROJECT_IS_PUBLIC = false;

    private ProjectRolesHandler rolesHandler;
    private ProjectState project;
    private Operation testOperationByAdmin;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ProjectFactoryService.SELF_LINK);
        waitForServiceAvailability(UserGroupService.FACTORY_LINK);

        testOperationByAdmin = createAuthorizedOperation(
                host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN)));

        project = createProject(PROJECT_NAME, PROJECT_DESCRIPTION, PROJECT_IS_PUBLIC);

        rolesHandler = new ProjectRolesHandler(privilegedTestService, project.documentSelfLink);
    }

    @Test
    public void testAssignExistingUserGroupToProjectShouldCreateNewRoleAndAssignTheUserGroupToTheProject()
            throws Throwable {
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.administrators.add = Collections.singletonList(USER_GROUP_SUPERUSERS);

        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                USER_GROUP_SUPERUSERS);
        String resourceGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(Service.getId(project
                        .documentSelfLink)));
        String roleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(Service.getId(project.documentSelfLink),
                        USER_GROUP_SUPERUSERS));

        assertDocumentExists(userGroupLink);
        assertDocumentExists(resourceGroupLink);
        assertDocumentNotExists(roleLink);

        host.testStart(1);
        rolesHandler.handleRolesUpdate(project, projectRoles, testOperationByAdmin)
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                        return;
                    }
                    host.completeIteration();
                });
        host.testWait();

        // verify that the role is deleted
        assertDocumentExists(roleLink);

        // verify that the user group link is added to the project
        assertTrue(project.administratorsUserGroupLinks.stream()
                .anyMatch((userGroup) -> userGroup.equals(userGroupLink)));
    }

    @Test
    public void testUnassignExistingGroupFromProjectShouldDeleteRoleAndUnassignTheGroupFromTheProject()
            throws Throwable {
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.administrators.remove = Collections.singletonList(USER_GROUP_SUPERUSERS);

        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                USER_GROUP_SUPERUSERS);
        String resourceGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                Service.getId(project.documentSelfLink));
        String roleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(Service.getId(project.documentSelfLink),
                        USER_GROUP_SUPERUSERS));

        assertDocumentNotExists(roleLink);

        // create the role manually
        RoleState role = AuthUtil.buildProjectAdminsRole(
                Service.getId(project.documentSelfLink), userGroupLink, resourceGroupLink);

        doPost(role, RoleService.FACTORY_LINK);
        assertDocumentExists(roleLink);

        host.testStart(1);
        rolesHandler.handleRolesUpdate(project, projectRoles, testOperationByAdmin)
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                        return;
                    }
                    host.completeIteration();
                });
        host.testWait();

        // verify that the role is deleted
        assertDocumentNotExists(roleLink);

        // verify that the user group link is removed from the project
        assertFalse(project.administratorsUserGroupLinks.stream()
                .anyMatch((userGroup) -> userGroup.equals(userGroupLink)));
    }

    @Test
    public void testAssignNotExistingPrincipalGroupShouldFail() {
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.administrators.add = Collections.singletonList("test-group");

        host.testStart(1);

        rolesHandler.handleRolesUpdate(project, projectRoles, testOperationByAdmin)
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        if (ex.getCause() instanceof ServiceNotFoundException) {
                            host.completeIteration();
                            return;
                        }
                        host.failIteration(ex);
                        return;
                    }
                    host.failIteration(new Exception(
                            String.format("Should've thrown %s", new ServiceNotFoundException())));
                });
        host.testWait();
    }

    @Test
    public void testAssignExistingPrincipalGroupWithNotExistingUserGroupStateShouldCreateANewUserGroupState()
            throws Throwable {
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.members.add = Collections.singletonList(USER_GROUP_SUPERUSERS);

        String userGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                USER_GROUP_SUPERUSERS);

        // delete the existing user group
        doDelete(UriUtils.buildUri(host, userGroupLink), false);
        assertDocumentNotExists(userGroupLink);

        host.testStart(1);
        rolesHandler.handleRolesUpdate(project, projectRoles, testOperationByAdmin)
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                        return;
                    }
                    host.completeIteration();
                });
        host.testWait();

        // verify that the user group is created
        assertDocumentExists(userGroupLink);
    }

    @Test
    public void testAssignPrincipalAsGroup() {
        String groupId = "superusers";

        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.members.add = Collections.singletonList(groupId);

        String projectId = Service.getId(project.documentSelfLink);

        doPatch(projectRoles, project.documentSelfLink);

        String resourceGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId, groupId));

        String roleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId, groupId));

        assertDocumentExists(resourceGroupLink);
        assertDocumentExists(roleLink);

        projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.members.remove = Collections.singletonList(groupId);

        doPatch(projectRoles, project.documentSelfLink);

        assertDocumentNotExists(resourceGroupLink);
        assertDocumentNotExists(roleLink);
    }

    @Test
    public void testAssignPrincipalOfTypeGroupTwice() {
        String groupId = "superusers";

        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.members.add = Collections.singletonList(groupId);

        String projectId = Service.getId(project.documentSelfLink);

        // patch twice and verify principal is present only once in the project.
        doPatch(projectRoles, project.documentSelfLink);

        doPatch(projectRoles, project.documentSelfLink);

        String resourceGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId, groupId));

        String roleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId, groupId));

        assertDocumentExists(resourceGroupLink);
        assertDocumentExists(roleLink);

        ExpandedProjectState projectState = getExpandedProjectState(project.documentSelfLink);
        assertEquals(1, projectState.members.size());
        // default one and the assigned one.
        assertEquals(2, projectState.membersUserGroupLinks.size());
    }
}
