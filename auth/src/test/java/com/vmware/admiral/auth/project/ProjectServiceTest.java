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

import static com.vmware.admiral.auth.util.PrincipalUtil.encode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.idm.SecurityContext;
import com.vmware.admiral.auth.idm.SecurityContext.ProjectEntry;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.project.ProjectService.ExpandedProjectState;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.auth.util.ProjectUtil;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.UniquePropertiesService.UniquePropertiesState;
import com.vmware.admiral.service.common.harbor.Harbor;
import com.vmware.admiral.service.common.harbor.mock.MockHarborApiProxyService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;

public class ProjectServiceTest extends AuthBaseTest {

    private static final String PROJECT_NAME = "test.name";
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
        waitForServiceAvailability(ProjectService.UNIQUE_PROJECT_NAMES_SERVICE_LINK);
        waitForServiceAvailability(ProjectFactoryService.SELF_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        project = createProject(PROJECT_NAME, PROJECT_DESCRIPTION, PROJECT_IS_PUBLIC);
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.viewers = new PrincipalRoleAssignment();
        projectRoles.administrators.add = Collections.singletonList(USER_EMAIL_ADMIN);
        projectRoles.members.add = Collections.singletonList(USER_EMAIL_ADMIN);
        projectRoles.viewers.add = Collections.singletonList(USER_EMAIL_BASIC_USER);

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
    public void testProjectRolesLifeCycle() throws Throwable {
        // Create project
        ProjectState testProject = createProject("project-test");
        String projectId = Service.getId(testProject.documentSelfLink);

        String defaultAdminsRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId));
        String defaultMembersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId));
        String defaultViewersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(projectId));

        String defaultAdminsLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId));
        String defaultMembersLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId));
        String defaultViewersLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(projectId));

        String defaultAdminsResGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId));
        String defaultMembersResGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId));
        String defaultViewersResGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(projectId));

        String membersExtendedResourceGroupLink = UriUtils.buildUriPath(
                ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId));
        String membersExtendedRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId));

        // Verify default documents are created.
        assertDocumentExists(defaultAdminsLink);
        assertDocumentExists(defaultMembersLink);
        assertDocumentExists(defaultViewersLink);
        assertDocumentExists(defaultAdminsRoleLink);
        assertDocumentExists(defaultMembersRoleLink);
        assertDocumentExists(defaultViewersRoleLink);
        assertDocumentExists(defaultAdminsResGroupLink);
        assertDocumentExists(defaultMembersResGroupLink);
        assertDocumentExists(defaultViewersResGroupLink);
        assertDocumentExists(membersExtendedRoleLink);
        assertDocumentExists(membersExtendedResourceGroupLink);

        // Assign principal of type user and validate.
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators.add = Collections.singletonList(USER_EMAIL_CONNIE);
        projectRoles.members.add = Collections.singletonList(USER_EMAIL_GLORIA);
        doPatch(projectRoles, testProject.documentSelfLink);

        ExpandedProjectState expandedState = getExpandedProjectState(testProject.documentSelfLink);
        assertTrue(expandedState.administrators.size() == 1);
        assertTrue(expandedState.administrators.get(0).email.equals(USER_EMAIL_CONNIE));
        assertTrue(expandedState.members.size() == 1);
        assertTrue(expandedState.members.get(0).email.equals(USER_EMAIL_GLORIA));

        UserState connieState = getDocumentNoWait(UserState.class, buildUserServicePath(
                USER_EMAIL_CONNIE));
        UserState gloriaState = getDocumentNoWait(UserState.class, buildUserServicePath(
                USER_EMAIL_GLORIA));
        assertTrue(connieState.userGroupLinks.contains(defaultAdminsLink));
        assertTrue(gloriaState.userGroupLinks.contains(defaultMembersLink));

        // Unassign principal of type user and validate
        projectRoles = new ProjectRoles();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators.remove = Collections.singletonList(USER_EMAIL_CONNIE);
        projectRoles.members.remove = Collections.singletonList(USER_EMAIL_GLORIA);
        doPatch(projectRoles, testProject.documentSelfLink);

        expandedState = getExpandedProjectState(testProject.documentSelfLink);
        assertTrue(expandedState.administrators.size() == 0);
        assertTrue(expandedState.members.size() == 0);

        connieState = getDocumentNoWait(UserState.class, buildUserServicePath(
                USER_EMAIL_CONNIE));
        gloriaState = getDocumentNoWait(UserState.class, buildUserServicePath(
                USER_EMAIL_GLORIA));
        assertTrue(!connieState.userGroupLinks.contains(defaultAdminsLink));
        assertTrue(!gloriaState.userGroupLinks.contains(defaultMembersLink));

        // Assign principal of type group and validate
        projectRoles = new ProjectRoles();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators.add = Collections.singletonList(USER_GROUP_SUPERUSERS);
        projectRoles.members.add = Collections.singletonList(USER_GROUP_DEVELOPERS);
        doPatch(projectRoles, testProject.documentSelfLink);

        testProject = getDocumentNoWait(ProjectState.class, testProject.documentSelfLink);
        assertTrue(testProject.administratorsUserGroupLinks.size() == 2);
        assertTrue(testProject.membersUserGroupLinks.size() == 2);

        String superusersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId,
                        encode(USER_GROUP_SUPERUSERS)));

        String developersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId,
                        encode(USER_GROUP_DEVELOPERS)));

        String developerExtendedRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED
                        .buildRoleWithSuffix(projectId, encode(USER_GROUP_DEVELOPERS)));

        String developersExtendedResourceGroupLink = UriUtils
                .buildUriPath(ResourceGroupService.FACTORY_LINK, AuthRole.PROJECT_MEMBER_EXTENDED
                        .buildRoleWithSuffix(projectId, encode(USER_GROUP_DEVELOPERS)));

        assertDocumentExists(superusersRoleLink);
        assertDocumentExists(developersRoleLink);
        assertDocumentExists(developerExtendedRoleLink);
        assertDocumentExists(developersExtendedResourceGroupLink);

        expandedState = getExpandedProjectState(testProject.documentSelfLink);
        assertTrue(expandedState.administrators.size() == 1);
        assertTrue(expandedState.administrators.get(0).id.equals(USER_GROUP_SUPERUSERS));
        assertTrue(expandedState.members.size() == 1);
        assertTrue(expandedState.members.get(0).id.equals(USER_GROUP_DEVELOPERS));

        // Unassign principal of type group and validate.
        projectRoles = new ProjectRoles();
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators.remove = Collections.singletonList(USER_GROUP_SUPERUSERS);
        projectRoles.members.remove = Collections.singletonList(USER_GROUP_DEVELOPERS);
        doPatch(projectRoles, testProject.documentSelfLink);

        assertDocumentNotExists(superusersRoleLink);
        assertDocumentNotExists(developersRoleLink);
        assertDocumentNotExists(developerExtendedRoleLink);
        assertDocumentNotExists(developersExtendedResourceGroupLink);

        expandedState = getExpandedProjectState(testProject.documentSelfLink);
        assertTrue(expandedState.administrators.size() == 0);
        assertTrue(expandedState.members.size() == 0);

        // Assign principal of type group and user and delete the project, validate that all
        // resources are cleaned.
        projectRoles.administrators = new PrincipalRoleAssignment();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.administrators.add = Arrays.asList(USER_GROUP_SUPERUSERS, USER_EMAIL_CONNIE);
        projectRoles.members.add = Arrays.asList(USER_GROUP_DEVELOPERS, USER_EMAIL_GLORIA);
        doPatch(projectRoles, testProject.documentSelfLink);

        assertDocumentExists(superusersRoleLink);
        assertDocumentExists(developersRoleLink);
        assertDocumentExists(developerExtendedRoleLink);
        assertDocumentExists(developersExtendedResourceGroupLink);

        deleteProject(testProject);

        assertDocumentNotExists(superusersRoleLink);
        assertDocumentNotExists(developersRoleLink);
        assertDocumentNotExists(developerExtendedRoleLink);
        assertDocumentNotExists(developersExtendedResourceGroupLink);
        assertDocumentNotExists(defaultAdminsLink);
        assertDocumentNotExists(defaultMembersLink);
        assertDocumentNotExists(defaultViewersLink);
        assertDocumentNotExists(defaultAdminsResGroupLink);
        assertDocumentNotExists(defaultMembersResGroupLink);
        assertDocumentNotExists(defaultViewersResGroupLink);
        assertDocumentNotExists(defaultAdminsRoleLink);
        assertDocumentNotExists(defaultMembersRoleLink);
        assertDocumentNotExists(defaultViewersRoleLink);
        assertDocumentNotExists(membersExtendedRoleLink);
        assertDocumentNotExists(membersExtendedResourceGroupLink);

        connieState = getDocumentNoWait(UserState.class, connieState.documentSelfLink);
        gloriaState = getDocumentNoWait(UserState.class, gloriaState.documentSelfLink);
        assertTrue(!connieState.userGroupLinks.contains(defaultAdminsLink));
        assertTrue(!gloriaState.userGroupLinks.contains(defaultMembersLink));

    }

    @Test
    public void testPatch() throws Throwable {

        final String patchedName = "patched-name";
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

    // TODO: Remove waitFor() once patch is stable.
    @Test
    public void testProjectRolesPatch() throws Throwable {
        // verify initial state
        ExpandedProjectState expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertNotNull(expandedState.viewers);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(1, expandedState.members.size());
        assertEquals(1, expandedState.viewers.size());
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        assertEquals(USER_EMAIL_ADMIN, expandedState.members.iterator().next().email);
        assertEquals(USER_EMAIL_BASIC_USER, expandedState.viewers.iterator().next().email);

        // make a batch user operation: add and remove members
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.members.remove = Arrays.asList(USER_EMAIL_ADMIN);
        projectRoles.members.add = Arrays.asList(USER_EMAIL_GLORIA, USER_EMAIL_CONNIE);
        doPatch(projectRoles, expandedState.documentSelfLink);

        waitFor(() -> {
            UserState gloria = getDocumentNoWait(UserState.class,
                    UriUtils.buildUriPath(UserService.FACTORY_LINK, encode(USER_EMAIL_GLORIA)));

            String groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(
                            Service.getId(project.documentSelfLink)));
            return gloria.userGroupLinks.contains(groupLink);
        });

        waitFor(() -> {
            UserState connie = getDocumentNoWait(UserState.class,
                    UriUtils.buildUriPath(UserService.FACTORY_LINK, encode(USER_EMAIL_CONNIE)));

            String groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(
                            Service.getId(project.documentSelfLink)));
            return connie.userGroupLinks.contains(groupLink);
        });

        waitFor(() -> {
            UserState admin = getDocumentNoWait(UserState.class,
                    UriUtils.buildUriPath(UserService.FACTORY_LINK, encode(USER_EMAIL_ADMIN)));

            String groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(
                            Service.getId(project.documentSelfLink)));
            return !admin.userGroupLinks.contains(groupLink);
        });

        // verify result
        expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(1, expandedState.viewers.size());
        assertEquals(2, expandedState.members.size()); // one removed, two added
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        assertEquals(USER_EMAIL_BASIC_USER, expandedState.viewers.iterator().next().email);
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

        waitFor(() -> {
            UserState gloria = getDocumentNoWait(UserState.class,
                    UriUtils.buildUriPath(UserService.FACTORY_LINK, encode(USER_EMAIL_GLORIA)));

            String groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(
                            Service.getId(project.documentSelfLink)));
            return !gloria.userGroupLinks.contains(groupLink);
        });

        waitFor(() -> {
            UserState connie = getDocumentNoWait(UserState.class,
                    UriUtils.buildUriPath(UserService.FACTORY_LINK, encode(USER_EMAIL_CONNIE)));

            String groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(
                            Service.getId(project.documentSelfLink)));
            return !connie.userGroupLinks.contains(groupLink);
        });

        waitFor(() -> {
            UserState admin = getDocumentNoWait(UserState.class,
                    UriUtils.buildUriPath(UserService.FACTORY_LINK, encode(USER_EMAIL_ADMIN)));

            String groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(
                            Service.getId(project.documentSelfLink)));
            return !admin.userGroupLinks.contains(groupLink);
        });

        // verify result
        expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.members);
        assertEquals(0, expandedState.members.size());
    }

    /**
     * Test with a PATCH request that updates both the project state and the user roles.
     */
    // TODO: remove waitFor() once Patch is stable.
    @Test
    public void testMixedPatch() throws Throwable {
        // verify initial state
        ExpandedProjectState expandedState = getExpandedProjectState(project.documentSelfLink);
        assertEquals(PROJECT_NAME, expandedState.name);
        assertEquals(PROJECT_IS_PUBLIC, expandedState.isPublic);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertNotNull(expandedState.viewers);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(1, expandedState.members.size());
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        assertEquals(USER_EMAIL_ADMIN, expandedState.members.iterator().next().email);
        assertEquals(USER_EMAIL_BASIC_USER, expandedState.viewers.iterator().next().email);

        // Patch name, public flag and roles at the same time
        final String patchedName = "patched-name";
        final boolean patchedPublicFlag = !PROJECT_IS_PUBLIC;
        ProjectMixedPatchDto patchBody = new ProjectMixedPatchDto();
        patchBody.name = patchedName;
        patchBody.isPublic = patchedPublicFlag;
        patchBody.members = new PrincipalRoleAssignment();
        patchBody.members.add = Arrays.asList(USER_EMAIL_GLORIA, USER_EMAIL_CONNIE);
        doPatch(patchBody, expandedState.documentSelfLink);

        waitFor(() -> {
            UserState gloria = getDocumentNoWait(UserState.class,
                    UriUtils.buildUriPath(UserService.FACTORY_LINK, encode(USER_EMAIL_GLORIA)));

            String groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(
                            Service.getId(project.documentSelfLink)));
            return gloria.userGroupLinks.contains(groupLink);
        });

        waitFor(() -> {
            UserState connie = getDocumentNoWait(UserState.class,
                    UriUtils.buildUriPath(UserService.FACTORY_LINK, encode(USER_EMAIL_CONNIE)));

            String groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                    AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(
                            Service.getId(project.documentSelfLink)));
            return connie.userGroupLinks.contains(groupLink);
        });

        // Verify result
        expandedState = getExpandedProjectState(project.documentSelfLink);
        assertEquals(patchedName, expandedState.name);
        assertNotEquals(patchedName, PROJECT_NAME);
        assertEquals(patchedPublicFlag, expandedState.isPublic);
        assertNotEquals(patchedPublicFlag, PROJECT_IS_PUBLIC);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertEquals(1, expandedState.viewers.size());
        assertEquals(1, expandedState.administrators.size());
        assertEquals(3, expandedState.members.size());
        assertEquals(USER_EMAIL_BASIC_USER, expandedState.viewers.iterator().next().email);
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        List<String> expectedMembers = Arrays
                .asList(USER_EMAIL_ADMIN, USER_EMAIL_CONNIE, USER_EMAIL_GLORIA);
        assertTrue(expandedState.members.stream()
                .allMatch((userState) -> expectedMembers.contains(userState.email)));

    }

    @Test
    public void testPut() throws Throwable {
        final String updatedName = "updated-name";
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

    // https://github.com/vmware/admiral/issues/183 - On migration of the data VIC 1.1 to 1.2
    // default project should not be updated
    @Test
    public void testPostUpgradeDefaultProject() throws Throwable {
        ProjectState projectState = new ProjectState();
        projectState.name = "test";
        projectState.documentSelfLink = ProjectService.DEFAULT_PROJECT_LINK;
        projectState.administratorsUserGroupLink = "test";
        ProjectState updatedState = doPost(projectState, ProjectFactoryService.SELF_LINK);
        assertNull(updatedState.administratorsUserGroupLink);
    }

    @Test
    public void testPutWithSameName() throws Throwable {
        final String updatedDescription = "updatedDescription";
        final boolean updatedIsPublic = !PROJECT_IS_PUBLIC;

        ProjectState updateState = new ProjectState();
        updateState.name = project.name;
        updateState.description = updatedDescription;
        updateState.isPublic = updatedIsPublic;
        updateState.documentSelfLink = project.documentSelfLink;

        ProjectState updatedState = updateProject(updateState);
        assertEquals(updatedDescription, updatedState.description);
        assertEquals(updatedIsPublic, updatedState.isPublic);
    }

    public void testProjectRolesPutUsers() throws Throwable {
        // verify initial state
        ExpandedProjectState expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertNotNull(expandedState.viewers);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(1, expandedState.members.size());
        assertEquals(1, expandedState.viewers.size());
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        assertEquals(USER_EMAIL_ADMIN, expandedState.members.iterator().next().email);
        assertEquals(USER_EMAIL_BASIC_USER, expandedState.viewers.iterator().next().email);

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
        assertNotNull(expandedState.viewers);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(1, expandedState.viewers.size());
        assertEquals(2, expandedState.members.size()); // one removed, two added
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        assertEquals(USER_EMAIL_BASIC_USER, expandedState.viewers.iterator().next().email);
        assertTrue(expandedState.members.stream()
                .anyMatch((member) -> member.email.equals(USER_EMAIL_GLORIA)));
        assertTrue(expandedState.members.stream()
                .anyMatch((member) -> member.email.equals(USER_EMAIL_CONNIE)));
    }

    @Test
    public void testProjectRolesPutGroups() throws Throwable {
        // verify initial state
        ExpandedProjectState expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.administratorsUserGroupLinks);
        assertNotNull(expandedState.membersUserGroupLinks);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(1, expandedState.members.size());

        String expectedAdministratorsUserGroupLink = UriUtils.buildUriPath(
                UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));
        String expectedMembersUserGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));

        assertEquals(expectedAdministratorsUserGroupLink,
                expandedState.administratorsUserGroupLinks.iterator().next());
        assertEquals(expectedMembersUserGroupLink,
                expandedState.membersUserGroupLinks.iterator().next());

        // make a batch user operation: add and remove group
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.members.add = Arrays.asList(USER_GROUP_DEVELOPERS);

        // assert that the new role does not exist
        String roleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(Service.getId(project.documentSelfLink),
                        encode(USER_GROUP_DEVELOPERS)));
        assertDocumentNotExists(roleLink);

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

        // verify that the new role is created
        assertDocumentExists(roleLink);

        // verify that the user group is added to the project
        expandedState = getExpandedProjectState(project.documentSelfLink);
        assertEquals(2, expandedState.membersUserGroupLinks.size());
        assertTrue(expandedState.membersUserGroupLinks.stream()
                .anyMatch((userGroupLink) -> userGroupLink.equals(
                        UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                                encode(USER_GROUP_DEVELOPERS)))));
    }

    @Test
    public void testProjectRolesPatchGroups() throws Throwable {
        // verify initial state
        ExpandedProjectState expandedState = getExpandedProjectState(project.documentSelfLink);
        assertNotNull(expandedState.administratorsUserGroupLinks);
        assertNotNull(expandedState.membersUserGroupLinks);
        assertNotNull(expandedState.administrators);
        assertNotNull(expandedState.members);
        assertNotNull(expandedState.viewers);
        assertEquals(1, expandedState.administrators.size());
        assertEquals(1, expandedState.members.size());
        assertEquals(1, expandedState.viewers.size());
        assertEquals(USER_EMAIL_ADMIN, expandedState.administrators.iterator().next().email);
        assertEquals(USER_EMAIL_ADMIN, expandedState.members.iterator().next().email);
        assertEquals(USER_EMAIL_BASIC_USER, expandedState.viewers.iterator().next().email);

        String expectedAdministratorsUserGroupLink = UriUtils.buildUriPath(
                UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));
        String expectedMembersUserGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));

        assertEquals(expectedAdministratorsUserGroupLink,
                expandedState.administratorsUserGroupLinks.iterator().next());
        assertEquals(expectedMembersUserGroupLink,
                expandedState.membersUserGroupLinks.iterator().next());

        // make a batch user operation: add group
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = new PrincipalRoleAssignment();
        projectRoles.members.add = Arrays.asList(USER_GROUP_DEVELOPERS);

        // assert that the new role does not exist
        String roleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(Service.getId(project.documentSelfLink),
                        encode(USER_GROUP_DEVELOPERS)));
        assertDocumentNotExists(roleLink);

        host.testStart(1);

        Operation.createPatch(host, expandedState.documentSelfLink)
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

        // verify that the new role is created
        assertDocumentExists(roleLink);

        expandedState = getExpandedProjectState(project.documentSelfLink);
        assertEquals(2, expandedState.membersUserGroupLinks.size());
        // verify that the user group link is added to the members group links
        assertTrue(expandedState.membersUserGroupLinks.stream()
                .anyMatch((userGroupLink) -> userGroupLink.equals(
                        UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                                encode(USER_GROUP_DEVELOPERS)))));

        // verify that the user group is added to the members
        assertTrue(expandedState.members.stream()
                .anyMatch((principal) -> principal.id.equals(USER_GROUP_DEVELOPERS)));
    }

    @Test
    public void testDelete() throws Throwable {
        String admins = project.administratorsUserGroupLinks.iterator().next();
        String members = project.membersUserGroupLinks.iterator().next();
        String viewers = project.viewersUserGroupLinks.iterator().next();
        deleteProject(project);

        // Verify the default UserGroups are deleted
        UserGroupState adminsGroup = getDocumentNoWait(UserGroupState.class, admins);
        assertNull(adminsGroup);

        UserGroupState membersGroups = getDocumentNoWait(UserGroupState.class, members);
        assertNull(membersGroups);

        UserGroupState viewersGroups = getDocumentNoWait(UserGroupState.class, viewers);
        assertNull(viewersGroups);
    }

    @Test
    public void testDeleteVerifyCleanup() throws Throwable {
        // Add fritz as member and admin in the project.
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = Collections.singletonList(USER_EMAIL_ADMIN);
        ProjectRoles roles = new ProjectRoles();
        roles.administrators = roleAssignment;
        roles.members = roleAssignment;
        roles.viewers = roleAssignment;
        doPatch(roles, project.documentSelfLink);

        String fritzLink = buildUserServicePath(USER_EMAIL_ADMIN);

        String resourceGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                Service.getId(project.documentSelfLink));

        String viewersGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_VIEWER
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));
        String membersGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));
        String adminsGroupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));

        String adminsRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(Service.getId(project.documentSelfLink),
                        adminsGroupLink));
        String membersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(
                        Service.getId(project.documentSelfLink), membersGroupLink));
        String viewersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(
                        Service.getId(project.documentSelfLink), viewersGroupLink));

        // verify fritz is added.
        UserState fritzState = getDocument(UserState.class, fritzLink);
        assertTrue(fritzState.userGroupLinks.contains(viewersGroupLink));
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

        UserGroupState viewersGroups = getDocumentNoWait(UserGroupState.class, viewersGroupLink);
        assertNull(viewersGroups);

        // Verify that the ResourceGroup is deleted
        ResourceGroupState resourceGroup = getDocumentNoWait(ResourceGroupState.class,
                resourceGroupLink);
        assertNull(resourceGroup);

        // Verify that the AdminRole is delete
        RoleState adminRoleState = getDocumentNoWait(RoleState.class, adminsRoleLink);
        assertNull(adminRoleState);

        // Verify that the MemberRole is delete
        RoleState memberRoleState = getDocumentNoWait(RoleState.class, membersRoleLink);
        assertNull(memberRoleState);

        // Verify that the ViewerRole is delete
        RoleState viewerRoleState = getDocumentNoWait(RoleState.class, viewersRoleLink);
        assertNull(viewerRoleState);

        // Verify fritz's userstate is patched
        fritzState = getDocument(UserState.class, fritzLink);
        assertTrue(!fritzState.userGroupLinks.contains(membersGroupLink));
        assertTrue(!fritzState.userGroupLinks.contains(adminsGroupLink));
        assertTrue(!fritzState.userGroupLinks.contains(viewersGroupLink));
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
        } catch (Exception e) {
            if (!e.getMessage().contains(ProjectUtil.PROJECT_IN_USE_MESSAGE)) {
                throw e;
            }
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
        String adminsLinks = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));
        String membersLinks = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));
        String viewersLinks = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_VIEWER
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));
        assertDocumentExists(adminsLinks);
        assertDocumentExists(membersLinks);
        assertDocumentExists(viewersLinks);
    }

    @Test
    public void testResourceGroupsAutoCreatedOnProjectCreate() {
        String projectId = Service.getId(project.documentSelfLink);
        String adminsResourceGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId));
        String membersResourceGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId));
        String viewersResourceGroupLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(projectId));
        String extendedMembersResourceGroupLink = UriUtils.buildUriPath(
                ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId));
        assertDocumentExists(adminsResourceGroupLink);
        assertDocumentExists(membersResourceGroupLink);
        assertDocumentExists(viewersResourceGroupLink);
        assertDocumentExists(extendedMembersResourceGroupLink);
    }

    @Test
    public void testRolesAutoCreatedOnProjectCreate() {
        String adminsRoleLinks = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));
        String membersRoleLinks = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));
        String viewersRoleLinks = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_VIEWER
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));

        String extendedMembersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED
                        .buildRoleWithSuffix(Service.getId(project.documentSelfLink)));

        assertDocumentExists(adminsRoleLinks);
        assertDocumentExists(membersRoleLinks);
        assertDocumentExists(viewersRoleLinks);
        assertDocumentExists(extendedMembersRoleLink);
    }

    @Test
    public void testGetStateWithMembers() {
        ExpandedProjectState stateWithMembers = getExpandedProjectState(
                project.documentSelfLink);
        assertNotNull(stateWithMembers);

        assertNotNull(stateWithMembers.administrators);
        assertTrue(stateWithMembers.administrators.size() == 1);
        assertTrue(stateWithMembers.administrators.get(0).email.equals(USER_EMAIL_ADMIN));

        assertNotNull(stateWithMembers.members);
        assertTrue(stateWithMembers.members.size() == 1);
        assertTrue(stateWithMembers.members.get(0).email.equals(USER_EMAIL_ADMIN));

        assertNotNull(stateWithMembers.viewers);
        assertTrue(stateWithMembers.viewers.get(0).email.equals(USER_EMAIL_BASIC_USER));
    }

    @Test
    public void testGetExpandedProjectStateReturnsEmptyListsOnMissingUserGroups() throws Throwable {
        // update project state to have no admins and members group links stored
        project.administratorsUserGroupLinks = null;
        project.membersUserGroupLinks = null;
        project.viewersUserGroupLinks = null;
        project = doPut(project);

        ExpandedProjectState stateWithMembers = getExpandedProjectState(
                project.documentSelfLink);
        assertNotNull(stateWithMembers);

        assertNotNull(stateWithMembers.administrators);
        assertTrue(stateWithMembers.administrators.size() == 0);

        assertNotNull(stateWithMembers.members);
        assertTrue(stateWithMembers.members.size() == 0);

        assertNotNull(stateWithMembers.viewers);
        assertTrue(stateWithMembers.viewers.size() == 0);
    }

    @Test
    public void testBuildQueryProjectsFromProjectIndex() throws Throwable {

        Query query = ProjectUtil.buildQueryForProjectsFromProjectIndex(Long.parseLong(
                ProjectService.DEFAULT_PROJECT_INDEX));

        QueryTask queryTask = QueryUtil.buildQuery(ProjectState.class, true, query);
        QueryUtil.addExpandOption(queryTask);

        List<ProjectState> results = new ArrayList<>();
        TestContext ctx = testCreate(1);
        new ServiceDocumentQuery<>(host, ProjectState.class).query(queryTask, (r) -> {
            if (r.hasException()) {
                ctx.failIteration(r.getException());
            } else if (r.hasResult()) {
                results.add(r.getResult());
            } else {
                ctx.completeIteration();
            }
        });

        ctx.await();

        assertEquals(1, results.size());
        assertEquals(ProjectService.DEFAULT_PROJECT_LINK, results.get(0).documentSelfLink);
    }

    @Test
    public void testDefaultProjectUserGroupsAreCreated() throws Throwable {
        ProjectState defaultProject = getDocumentNoWait(ProjectState.class,
                ProjectService.DEFAULT_PROJECT_LINK);

        assertNotNull(defaultProject);
        assertNotNull(defaultProject.documentSelfLink);
        assertNotNull(defaultProject.administratorsUserGroupLinks);
        assertNotNull(defaultProject.membersUserGroupLinks);
        assertNotNull(defaultProject.viewersUserGroupLinks);

        assertEquals(1, defaultProject.administratorsUserGroupLinks.size());
        assertEquals(1, defaultProject.membersUserGroupLinks.size());
        assertEquals(1, defaultProject.viewersUserGroupLinks.size());

        String adminsGroupLink = defaultProject.administratorsUserGroupLinks.iterator().next();
        String membersGroupLink = defaultProject.membersUserGroupLinks.iterator().next();
        String viewersGroupLink = defaultProject.viewersUserGroupLinks.iterator().next();

        UserGroupState adminGroup = getDocumentNoWait(UserGroupState.class, adminsGroupLink);
        UserGroupState membersGroup = getDocumentNoWait(UserGroupState.class, membersGroupLink);
        UserGroupState viewersGroup = getDocumentNoWait(UserGroupState.class, viewersGroupLink);

        assertNotNull(adminGroup.documentSelfLink);
        assertNotNull(membersGroup.documentSelfLink);
        assertNotNull(viewersGroup.documentSelfLink);
    }

    @Test
    public void testCreateProjectWithDuplicateNameShouldFail() throws Throwable {
        createProject("test-name");

        try {
            createProjectExpectFailure("test-name");
            fail("Project create with same name should've failed");
        } catch (Exception ex) {
            assertTrue(ex instanceof IllegalStateException);
            assertTrue(ex.getMessage().contains("test-name"));
        }
    }

    @Test
    public void testUpdateProjectWithDuplicateNameShouldFail() throws Throwable {
        createProject("test-name");
        ProjectState testProject = createProject("test-name-1");

        ProjectState state = new ProjectState();
        state.name = "test-name";
        state.documentSelfLink = testProject.documentSelfLink;
        try {
            updateProject(state);
            fail("Project update with same name should've failed");
        } catch (Exception ex) {
            assertTrue(ex instanceof IllegalStateException);
            assertTrue(ex.getMessage().contains("test-name"));
        }

    }

    @Test
    public void testPatchProjectNameInVicMode() throws Throwable {
        ConfigurationState vicConfigProperty = new ConfigurationState();
        vicConfigProperty.documentSelfLink = ConfigurationUtil.VIC_MODE_PROPERTY;
        vicConfigProperty.key = ConfigurationUtil.VIC_MODE_PROPERTY;
        vicConfigProperty.value = Boolean.TRUE.toString();
        vicConfigProperty = doPost(vicConfigProperty, ConfigurationFactoryService.SELF_LINK);

        ProjectState testProject = createProject("test-name");
        testProject.name = "another-name";

        try {
            doPatch(testProject, testProject.documentSelfLink);
            fail("Project patch with different name should've failed");
        } catch (Exception ex) {
            assertTrue(ex instanceof LocalizableValidationException);
        }
    }

    @Test
    public void testPatchProjectWithDuplicateNameShouldFail() throws Throwable {
        createProject("test-name");
        ProjectState testProject = createProject("test-name-1");

        ProjectState state = new ProjectState();
        state.name = "test-name";
        state.documentSelfLink = testProject.documentSelfLink;
        try {
            patchProject(state, state.documentSelfLink);
            fail("Project update with same name should've failed");
        } catch (Exception ex) {
            assertTrue(ex.getCause() instanceof IllegalStateException);
            assertTrue(ex.getMessage().contains("test-name"));
        }

    }

    @Test
    public void testProjectIndexIsReadOnly() throws Throwable {
        ProjectState testProject = createProject("test-project");
        String projectIndex = testProject.customProperties
                .get(ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX);

        // Patch with null for custom properties.
        testProject.customProperties = null;
        testProject = patchProject(testProject, testProject.documentSelfLink);
        assertNotNull(testProject.customProperties);
        assertEquals(projectIndex, testProject.customProperties.get(
                ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX));

        // Patch with changed project index.
        testProject.customProperties.put(ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX, "testValue");
        testProject = patchProject(testProject, testProject.documentSelfLink);
        assertNotNull(testProject.customProperties);
        assertEquals(projectIndex, testProject.customProperties.get(
                ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX));

        // Put with null for custom properties.
        testProject.customProperties = null;
        testProject = updateProject(testProject);
        assertNotNull(testProject.customProperties);
        assertEquals(projectIndex, testProject.customProperties.get(
                ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX));

        // Put with changed project index.
        testProject.customProperties.put(ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX, "testValue");
        testProject = updateProject(testProject);
        assertNotNull(testProject.customProperties);
        assertEquals(projectIndex, testProject.customProperties.get(
                ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX));
    }

    @Test
    public void testCreateMultipleProjectsAtOnceWithSameName() throws Throwable {
        ProjectState state = new ProjectState();
        for (int i = 0; i < 10; i++) {
            state.name = "test-name";
            createProjectNoWait(state);
        }

        Thread.sleep(1000);
        List<ProjectState> projects = new ArrayList<>();
        TestContext ctx = testCreate(1);
        host.send(Operation.createGet(host, UriUtils.buildExpandLinksQueryUri(
                UriUtils.buildUri(ProjectFactoryService.SELF_LINK)).toString())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                        return;
                    }
                    ServiceDocumentQueryResult result = o.getBody(ServiceDocumentQueryResult.class);
                    projects.addAll(result.documents.values().stream()
                            .map(p -> (ProjectState) p)
                            .collect(Collectors.toList()));
                    ctx.completeIteration();
                }));
        ctx.await();

        List<ProjectState> testProjects = projects.stream()
                .filter(p -> p.name.equalsIgnoreCase("test-name"))
                .collect(Collectors.toList());

        assertEquals(1, testProjects.size());
    }

    @Test
    public void testAssignProjectRoleToPrincipalWithoutUserState() throws Throwable {
        deleteUser(encode(USER_EMAIL_CONNIE));
        assertDocumentNotExists(
                AuthUtil.buildUserServicePathFromPrincipalId(encode(USER_EMAIL_CONNIE)));

        ProjectState projectState = createProject("test-test");

        ProjectRoles roleAssignment = new ProjectRoles();
        roleAssignment.administrators = new PrincipalRoleAssignment();
        roleAssignment.administrators.add = Collections.singletonList(USER_EMAIL_CONNIE);

        doPatch(roleAssignment, projectState.documentSelfLink);

        ExpandedProjectState expandedProjectState = getExpandedProjectState(
                projectState.documentSelfLink);

        assertTrue(expandedProjectState.administrators.size() == 1);

        Principal principal = expandedProjectState.administrators.get(0);
        assertEquals(USER_EMAIL_CONNIE, principal.id);

        assertDocumentExists(
                AuthUtil.buildUserServicePathFromPrincipalId(encode(USER_EMAIL_CONNIE)));

        SecurityContext connieContext = getSecurityContext(USER_EMAIL_CONNIE);

        assertTrue(connieContext.roles.contains(AuthRole.BASIC_USER));
        assertTrue(connieContext.roles.contains(AuthRole.BASIC_USER_EXTENDED));

        assertTrue(connieContext.projects.size() == 1);

        ProjectEntry entry = connieContext.projects.get(0);
        assertEquals(projectState.documentSelfLink, entry.documentSelfLink);
        assertEquals(projectState.name, entry.name);
        assertTrue(entry.roles.contains(AuthRole.PROJECT_ADMIN));
    }

    @Test
    public void testDevOpsAdminCanAssignUsersToProject() throws Throwable {
        ProjectState project = createProject("project");
        ProjectRoles roles = new ProjectRoles();
        roles.administrators = new PrincipalRoleAssignment();
        roles.administrators.add = Collections.singletonList(USER_EMAIL_BASIC_USER);
        doPatch(roles, project.documentSelfLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        ProjectRoles roles1 = new ProjectRoles();
        roles1.members = new PrincipalRoleAssignment();
        roles1.members.add = Collections.singletonList(USER_EMAIL_CONNIE);
        doPatch(roles1, project.documentSelfLink);

        ExpandedProjectState expandedProjectState = getExpandedProjectState(
                project.documentSelfLink);

        assertTrue(expandedProjectState.administrators.size() == 1);
        assertTrue(expandedProjectState.administrators.get(0).id.equals(USER_EMAIL_BASIC_USER));
        assertTrue(expandedProjectState.members.size() == 1);
        assertTrue(expandedProjectState.members.get(0).id.equals(USER_EMAIL_CONNIE));
    }

    @Test
    public void testProjectNameIsUnclaimedAfterUpdateOrDelete() throws Throwable {
        ProjectState project = createProject("test-name");
        assertNotNull(project.documentSelfLink);

        ProjectState project1 = new ProjectState();
        project1.name = "new-name";
        project = patchProject(project1, project.documentSelfLink);
        assertEquals(project1.name, project.name);

        project1 = createProject("test-name");
        assertNotNull(project1.documentSelfLink);

        deleteProject(project1);

        project1 = createProject("test-name");
        assertNotNull(project1.documentSelfLink);

        ProjectState projectPut = new ProjectState();
        projectPut.name = "updated-name";
        projectPut.documentSelfLink = project1.documentSelfLink;
        updateProject(projectPut);

        project1 = createProject("test-name");
        assertNotNull(project1.documentSelfLink);
    }

    @Test
    public void testProjectIndexClaim() throws Throwable {
        ProjectState project = createProject("test-name");
        String projectIndex = project.customProperties
                .get(ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX);

        String projectIndexesUri = ProjectService.UNIQUE_PROJECT_INDEXES_SERVICE_LINK;
        List<String> claimedIndexes = getDocumentNoWait(UniquePropertiesState.class,
                projectIndexesUri).uniqueProperties;

        assertTrue(claimedIndexes.contains(projectIndex));

        deleteProject(project);

        claimedIndexes = getDocumentNoWait(UniquePropertiesState.class,
                projectIndexesUri).uniqueProperties;

        assertTrue(!claimedIndexes.contains(projectIndex));
    }

    @Test
    public void testProjectNameValidation() throws Throwable {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 255; i++) {
            builder.append("a");
        }
        String tooLongName = builder.toString();
        String[] invalidNames = new String[] { "testName", "TestName", "test&name", "test*name",
                tooLongName };

        for (String invalidName : invalidNames) {
            try {
                createProject(invalidName);
                fail("Creation of project should have failed due to invalid name");
            } catch (Throwable ex) {
                assertTrue(ex instanceof LocalizableValidationException);
            }
        }

        String[] validNames = new String[] { "testname", "test123name", "test-name", "test_name",
                "test.project.name", "1235" };

        for (String validName : validNames) {
            ProjectState state = createProject(validName);
            assertNotNull(state.documentSelfLink);
        }
    }

    @Test
    public void testHarborVerifyOnProjectDelete() throws Throwable {
        ConfigurationState mockHarborUri = new ConfigurationState();
        mockHarborUri.key = Harbor.CONFIGURATION_URL_PROPERTY_NAME;
        mockHarborUri.value = "test.uri";
        mockHarborUri.documentSelfLink = Harbor.CONFIGURATION_URL_PROPERTY_NAME;

        mockHarborUri = doPost(mockHarborUri, ConfigurationFactoryService.SELF_LINK);
        assertNotNull(mockHarborUri);
        assertNotNull(mockHarborUri.documentSelfLink);

        ProjectState test = createProject("test-project");
        assertNotNull(test.documentSelfLink);

        MockHarborApiProxyService.IS_PROJECT_DELETABLE.set(true);
        deleteProject(test);

        test = createProject("test-project");
        assertNotNull(test.documentSelfLink);

        MockHarborApiProxyService.IS_PROJECT_DELETABLE.set(false);
        try {
            deleteProject(test);
        } catch (Throwable ex) {
            assertTrue(ex.getCause() instanceof LocalizableValidationException);
            assertTrue(ex.getCause().getMessage()
                    .contains("Project is not removable: mocked message"));
        }

    }

    private void createProjectNoWait(ProjectState state) {
        Operation op = Operation.createPost(host, ProjectFactoryService.SELF_LINK)
                .setBody(state);
        host.send(op);
    }

    private ResourcePoolState createResourcePool() throws Throwable {
        ResourcePoolState pool = new ResourcePoolState();
        pool.name = "pool";

        return doPost(pool, ResourcePoolService.FACTORY_LINK);
    }

}
