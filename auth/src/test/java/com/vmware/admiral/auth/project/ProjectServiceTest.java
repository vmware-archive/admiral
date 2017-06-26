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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.project.ProjectService.ExpandedProjectState;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.auth.util.ProjectUtil;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;

public class ProjectServiceTest extends AuthBaseTest {

    private static final String PROJECT_NAME = "testName";
    private static final String PROJECT_DESCRIPTION = "testDescription";
    private static final boolean PROJECT_IS_PUBLIC = false;

    private ProjectState project;

    /**
     * A DTO for test purposes. Used to patch a project state and project roles at the same time
     */
    private static class ProjectMixedPatchDto extends ProjectRoles {
        @SuppressWarnings("unused")
        public String name;
        @SuppressWarnings("unused")
        public boolean isPublic;
    }

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ProjectFactoryService.SELF_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        project = createProject(PROJECT_NAME, PROJECT_DESCRIPTION, PROJECT_IS_PUBLIC);
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.administrators.add = Collections.singletonList(USER_EMAIL_ADMIN);
        projectRoles.members.add = Collections.singletonList(USER_EMAIL_ADMIN);
        doPatch(projectRoles, project.documentSelfLink);
    }

    @Test
    public void testProjectServices() throws Throwable {
        verifyService(
                ProjectFactoryService.class,
                ProjectState.class,
                (prefix, index) -> {
                    ProjectState projectState = new ProjectState();
                    projectState.id = prefix + "id" + index;
                    projectState.name = prefix + "name" + index;
                    projectState.description = prefix + "description" + index;
                    projectState.isPublic = true;

                    return projectState;
                },
                (prefix, serviceDocument) -> {
                    ProjectState projectState = (ProjectState) serviceDocument;
                    assertTrue(projectState.id.startsWith(prefix + "id"));
                    assertTrue(projectState.name.startsWith(prefix + "name"));
                    assertTrue(projectState.description.startsWith(prefix + "description"));
                    assertTrue(projectState.isPublic);
                });
    }

    @Test
    public void testPatch() throws Throwable {

        final String patchedName = "patchedName";
        final String patchedDescription = "patchedDescription";
        final boolean patchedIsPublic = true;

        // patch name
        ProjectState patchState = new ProjectState();
        patchState.name = patchedName;
        ProjectState updatedProject = patchProject(patchState, project.documentSelfLink);
        assertEquals(patchedName, updatedProject.name);
        assertEquals(PROJECT_DESCRIPTION, updatedProject.description);
        assertEquals(PROJECT_IS_PUBLIC, updatedProject.isPublic);

        // patch description
        patchState = new ProjectState();
        patchState.description = patchedDescription;
        updatedProject = patchProject(patchState, project.documentSelfLink);
        assertEquals(patchedName, updatedProject.name);
        assertEquals(patchedDescription, updatedProject.description);
        assertEquals(PROJECT_IS_PUBLIC, updatedProject.isPublic);

        // patch isPublic
        patchState = new ProjectState();
        patchState.isPublic = patchedIsPublic;
        updatedProject = patchProject(patchState, project.documentSelfLink);
        assertEquals(patchedName, updatedProject.name);
        assertEquals(patchedDescription, updatedProject.description);
        assertEquals(patchedIsPublic, updatedProject.isPublic);
    }

    @Test
    public void testProjectRolesPatch() throws Throwable {
        // verify initial state
        ExpandedProjectState expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(1, expandedState.members.size());
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        assertEquals(USER_EMAIL_ADMIN, expandedState.members.iterator().next().email);

        // make a batch user operation: add and remove members
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.members.remove = Arrays.asList(USER_EMAIL_ADMIN);
        projectRoles.members.add = Arrays.asList(USER_EMAIL_GLORIA, USER_EMAIL_CONNIE);
        doPatch(projectRoles, expandedState.documentSelfLink);

        // verify result
        expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(2, expandedState.members.size()); // one removed, two added
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        assertTrue(expandedState.members.stream()
                .anyMatch((member) -> member.email.equals(USER_EMAIL_GLORIA)));
        assertTrue(expandedState.members.stream()
                .anyMatch((member) -> member.email.equals(USER_EMAIL_CONNIE)));

        // make a batch user operation:
        // remove an already missing user, add an already included user
        projectRoles = new ProjectRoles();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.administrators.remove = Arrays.asList(USER_EMAIL_GLORIA);
        projectRoles.administrators.add = Arrays.asList(USER_EMAIL_ADMIN);
        doPatch(projectRoles, expandedState.documentSelfLink);

        // verify result
        expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.administrators);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);

        // make a batch user operation:
        // remove all users from a group
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.members.remove = Arrays.asList(USER_EMAIL_ADMIN, USER_EMAIL_GLORIA,
                USER_EMAIL_CONNIE);
        doPatch(projectRoles, expandedState.documentSelfLink);

        // verify result
        expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.members);
        assertEquals(0, expandedState.members.size());
    }

    /**
     * Test with a PATCH request that updates both the project state and the user roles.
     */
    @Test
    public void testMixedPatch() throws Throwable {
        // verify initial state
        ExpandedProjectState expandedState = getExpandedProjectState(project.documentSelfLink);
        assertEquals(PROJECT_NAME, expandedState.name);
        assertEquals(PROJECT_IS_PUBLIC, expandedState.isPublic);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(1, expandedState.members.size());
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        assertEquals(USER_EMAIL_ADMIN, expandedState.members.iterator().next().email);

        // Patch name, public flag and roles at the same time
        final String patchedName = "patchedName";
        final boolean patchedPublicFlag = !PROJECT_IS_PUBLIC;
        ProjectMixedPatchDto patchBody = new ProjectMixedPatchDto();
        patchBody.name = patchedName;
        patchBody.isPublic = patchedPublicFlag;
        patchBody.members = new PrincipalRoleAssignment();
        patchBody.members.add = Arrays.asList(USER_EMAIL_GLORIA, USER_EMAIL_CONNIE);
        doPatch(patchBody, expandedState.documentSelfLink);

        // Verify result
        expandedState = getExpandedProjectState(project.documentSelfLink);
        assertEquals(patchedName, expandedState.name);
        assertNotEquals(patchedName, PROJECT_NAME);
        assertEquals(patchedPublicFlag, expandedState.isPublic);
        assertNotEquals(patchedPublicFlag, PROJECT_IS_PUBLIC);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(3, expandedState.members.size());
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        List<String> expectedMembers = Arrays
                .asList(USER_EMAIL_ADMIN, USER_EMAIL_CONNIE, USER_EMAIL_GLORIA);
        assertTrue(expandedState.members.stream()
                .allMatch((userState) -> expectedMembers.contains(userState.email)));

    }

    @Test
    public void testPut() throws Throwable {
        final String updatedName = "updatedName";
        final String updatedDescription = "updatedDescription";
        final boolean updatedIsPublic = !PROJECT_IS_PUBLIC;

        ProjectState updateState = new ProjectState();
        updateState.name = updatedName;
        updateState.description = updatedDescription;
        updateState.isPublic = updatedIsPublic;
        updateState.documentSelfLink = project.documentSelfLink;

        ProjectState updatedState = updateProject(updateState);
        assertEquals(updatedName, updatedState.name);
        assertEquals(updatedDescription, updatedState.description);
        assertEquals(updatedIsPublic, updatedState.isPublic);
    }

    @Test
    public void testProjectRolesPut() throws Throwable {
        // verify initial state
        ExpandedProjectState expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(1, expandedState.members.size());
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        assertEquals(USER_EMAIL_ADMIN, expandedState.members.iterator().next().email);

        // make a batch user operation: add and remove members
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.members.remove = Arrays.asList(USER_EMAIL_ADMIN);
        projectRoles.members.add = Arrays.asList(USER_EMAIL_GLORIA, USER_EMAIL_CONNIE);

        host.testStart(1);
        Operation.createPut(host, expandedState.documentSelfLink)
                .setReferer(host.getUri())
                .setBody(projectRoles)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE, Utils.toString(e));
                        host.failIteration(e);
                    } else {
                        try {
                            assertEquals(Operation.STATUS_CODE_OK, o.getStatusCode());
                            host.completeIteration();
                        } catch (AssertionError er) {
                            host.failIteration(er);
                        }
                    }
                }).sendWith(host);
        host.testWait();

        // verify result
        expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(2, expandedState.members.size()); // one removed, two added
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        assertTrue(expandedState.members.stream()
                .anyMatch((member) -> member.email.equals(USER_EMAIL_GLORIA)));
        assertTrue(expandedState.members.stream()
                .anyMatch((member) -> member.email.equals(USER_EMAIL_CONNIE)));
    }

    @Test
    public void testDelete() throws Throwable {
        String admins = project.administratorsUserGroupLinks.iterator().next();
        String members = project.membersUserGroupLinks.iterator().next();
        deleteProject(project);

        // Verify the default UserGroups are deleted
        UserGroupState adminsGroup = getDocumentNoWait(UserGroupState.class, admins);
        assertNull(adminsGroup);

        UserGroupState membersGroups = getDocumentNoWait(UserGroupState.class, members);
        assertNull(membersGroups);
    }

    @Test
    public void testDeleteVerifyCleanup() throws Throwable {
        // Add fritz as member and admin in the project.
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = Collections.singletonList(USER_EMAIL_ADMIN);
        ProjectRoles roles = new ProjectRoles();
        roles.administrators = roleAssignment;
        roles.members = roleAssignment;
        doPatch(roles, project.documentSelfLink);

        String fritzLink = UriUtils.buildUriPath(UserService.FACTORY_LINK, USER_EMAIL_ADMIN);
        String membersGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBERS.buildRoleWithSuffix(Service.getId(project
                        .documentSelfLink)));
        String adminsGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_ADMINS.buildRoleWithSuffix(Service.getId(project
                        .documentSelfLink)));
        String resourceGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                Service.getId(project.documentSelfLink));
        String adminsRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_ADMINS.buildRoleWithSuffix(Service.getId(project
                        .documentSelfLink), adminsGroupLink));
        String membersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_ADMINS.buildRoleWithSuffix(Service.getId(project
                        .documentSelfLink), membersGroupLink));

        // verify fritz is added.
        UserState fritzState = getDocument(UserState.class, fritzLink);
        assertTrue(fritzState.userGroupLinks.contains(membersGroupLink));
        assertTrue(fritzState.userGroupLinks.contains(adminsGroupLink));

        deleteProject(project);
        project = getDocumentNoWait(ProjectState.class, project.documentSelfLink);
        assertNull(project);

        // Verify the default UserGroups are deleted
        UserGroupState adminsGroup = getDocumentNoWait(UserGroupState.class, adminsGroupLink);
        assertNull(adminsGroup);

        UserGroupState membersGroups = getDocumentNoWait(UserGroupState.class, membersGroupLink);
        assertNull(membersGroups);

        // Verify that the ResourceGroup is deleted
        ResourceGroupState resourceGroup = getDocumentNoWait(ResourceGroupState.class, resourceGroupLink);
        assertNull(resourceGroup);

        // Verify that the AdminRole is delete
        RoleState adminRoleState = getDocumentNoWait(RoleState.class, adminsRoleLink);
        assertNull(adminRoleState);

        // Verify that the MemberRole is delete
        RoleState memberRoleState = getDocumentNoWait(RoleState.class, membersRoleLink);
        assertNull(memberRoleState);

        // Verify fritz's userstate is patched
        fritzState = getDocument(UserState.class, fritzLink);
        assertTrue(!fritzState.userGroupLinks.contains(membersGroupLink));
        assertTrue(!fritzState.userGroupLinks.contains(adminsGroupLink));
    }

    @Test
    public void testDeleteProjectAssociatedWithPlacement() throws Throwable {
        ResourcePoolState pool = createResourcePool();
        GroupResourcePlacementState placement = new GroupResourcePlacementState();
        placement.name = "test-reservation";
        placement.resourcePoolLink = pool.documentSelfLink;
        placement.tenantLinks = Arrays.asList(project.documentSelfLink);

        doPost(placement, GroupResourcePlacementService.FACTORY_LINK);

        try {
            deleteProject(project);
        } catch (LocalizableValidationException e) {
            verifyExceptionMessage(e.getMessage(),
                    ProjectUtil.PROJECT_IN_USE_MESSAGE);
        }

    }

    @Test
    public void testPropertiesValidationOnCreate() throws Throwable {
        try {
            createProject(null);
            fail("Creation of project should have failed due to invalid name");
        } catch (AssertionError e) {
            e.printStackTrace();
            throw e;
        } catch (LocalizableValidationException e) {
            verifyExceptionMessage(String.format(AssertUtil.PROPERTY_CANNOT_BE_NULL_MESSAGE_FORMAT,
                    ProjectState.FIELD_NAME_NAME), e.getMessage());
        }
    }

    @Test
    public void testPropertiesValidationOnPatch() throws Throwable {
        ProjectState patchState = new ProjectState();

        ProjectState patchedState = patchProject(patchState, project.documentSelfLink);

        assertEquals(PROJECT_NAME, patchedState.name);
        assertEquals(PROJECT_DESCRIPTION, patchedState.description);
        assertEquals(PROJECT_IS_PUBLIC, patchedState.isPublic);
    }

    @Test
    public void testPropertiesValidationOnUpdate() throws Throwable {
        ProjectState updateState = new ProjectState();
        updateState.documentSelfLink = project.documentSelfLink;

        try {
            updateProject(updateState);
            fail("Update of project should have failed due to invalid name");
        } catch (AssertionError e) {
            e.printStackTrace();
            throw e;
        } catch (LocalizableValidationException e) {
            verifyExceptionMessage(String.format(AssertUtil.PROPERTY_CANNOT_BE_NULL_MESSAGE_FORMAT,
                    ProjectState.FIELD_NAME_NAME), e.getMessage());
        }
    }

    @Test
    public void testUserGroupsAutoCreatedOnProjectCreate() {
        String adminsLinks = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, AuthRole
                .PROJECT_ADMINS.buildRoleWithSuffix(Service.getId(project.documentSelfLink)));
        String membersLinks = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, AuthRole
                .PROJECT_MEMBERS.buildRoleWithSuffix(Service.getId(project.documentSelfLink)));
        assertDocumentExists(adminsLinks);
        assertDocumentExists(membersLinks);
    }

    @Test
    public void testResourceGroupsAutoCreatedOnProjectCreate() {
        String resourceGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, Service.getId(project.documentSelfLink));
        assertDocumentExists(resourceGroupLink);
    }

    @Test
    public void testRolesAutoCreatedOnProjectCreate() {
        String adminsUserGroupId = Service.getId(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, AuthRole
                .PROJECT_ADMINS.buildRoleWithSuffix(Service.getId(project.documentSelfLink))));
        String membersUserGroupId = Service.getId(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, AuthRole
                .PROJECT_MEMBERS.buildRoleWithSuffix(Service.getId(project.documentSelfLink))));

        String adminsRoleLinks = UriUtils.buildUriPath(RoleService.FACTORY_LINK, AuthRole
                .PROJECT_ADMINS.buildRoleWithSuffix(Service.getId(project.documentSelfLink), adminsUserGroupId));
        String membersRoleLinks = UriUtils.buildUriPath(RoleService.FACTORY_LINK, AuthRole
                .PROJECT_MEMBERS.buildRoleWithSuffix(Service.getId(project.documentSelfLink), membersUserGroupId));

        assertDocumentExists(adminsRoleLinks);
        assertDocumentExists(membersRoleLinks);
    }

    @Test
    public void testGetStateWithMembers() {
        ExpandedProjectState stateWithMembers = getExpandedProjectState(
                project.documentSelfLink);
        assertNotNull(stateWithMembers);

        assertNotNull(stateWithMembers.administrators);
        assertTrue(stateWithMembers.administrators.size() == 1);
        assertTrue(stateWithMembers.administrators.iterator()
                .next().documentSelfLink
                .equals(buildUserServicePath(USER_EMAIL_ADMIN)));

        assertNotNull(stateWithMembers.members);
        assertTrue(stateWithMembers.members.size() == 1);
        assertTrue(stateWithMembers.members.iterator()
                .next().documentSelfLink
                .equals(buildUserServicePath(USER_EMAIL_ADMIN)));
    }

    @Test
    public void testGetStateWithMembersReturnsEmptyListsOnMissingUserGroups() throws Throwable {
        // update project state to have no admins and members group links stored
        project.administratorsUserGroupLinks = null;
        project.membersUserGroupLinks = null;
        project = doPut(project);

        ExpandedProjectState stateWithMembers = getExpandedProjectState(
                project.documentSelfLink);
        assertNotNull(stateWithMembers);

        assertNotNull(stateWithMembers.administrators);
        assertTrue(stateWithMembers.administrators.size() == 0);

        assertNotNull(stateWithMembers.members);
        assertTrue(stateWithMembers.members.size() == 0);
    }

    private void assertDocumentExists(String documentLink) {
        assertNotNull(documentLink);

        host.testStart(1);
        Operation.createGet(host, documentLink)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    } else {
                        try {
                            assertNotNull(o.getBodyRaw());
                            host.completeIteration();
                        } catch (AssertionError er) {
                            host.failIteration(er);
                        }
                    }
                }).sendWith(host);
        host.testWait();
    }

    private ResourcePoolState createResourcePool() throws Throwable {
        ResourcePoolState pool = new ResourcePoolState();
        pool.name = "pool";

        return doPost(pool, ResourcePoolService.FACTORY_LINK);
    }

}
