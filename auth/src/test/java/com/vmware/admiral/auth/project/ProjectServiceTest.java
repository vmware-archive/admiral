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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

public class ProjectServiceTest extends AuthBaseTest {

    private static final String PROJECT_NAME = "testName";
    private static final String PROJECT_DESCRIPTION = "testDescription";
    private static final boolean PROJECT_IS_PUBLIC = false;

    private ProjectState project;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ProjectService.FACTORY_LINK);

        host.assumeIdentity(buildUserServicePath(ADMIN_USERNAME));
        project = createProject(PROJECT_NAME, PROJECT_DESCRIPTION, PROJECT_IS_PUBLIC);
    }

    @Test
    public void testProjectServices() throws Throwable {
        verifyService(
                FactoryService.create(ProjectService.class),
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
    public void testUpdate() throws Throwable {
        final String updatedName = "updatedName";
        final String updatedDescription = "updatedDescription";
        final boolean updatedIsPublic = true;

        project = createProject(PROJECT_NAME, PROJECT_DESCRIPTION, PROJECT_IS_PUBLIC);

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
    public void testDelete() throws Throwable {
        deleteProject(project);
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
            verifyExceptionMessage(String.format(AssertUtil.PROPERTY_CANNOT_BE_NULL_MESSAGE_FORMAT, ProjectState.FIELD_NAME_NAME), e.getMessage());
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
            verifyExceptionMessage(String.format(AssertUtil.PROPERTY_CANNOT_BE_NULL_MESSAGE_FORMAT, ProjectState.FIELD_NAME_NAME), e.getMessage());
        }
    }

    @Test
    public void testUserGroupsAutoCreatedOnProjectCreate() {
        assertDocumentExists(project.administratorsUserGroupLink);
        assertDocumentExists(project.membersUserGroupLink);
    }

    @Test
    public void testUserGroupsNotOverridenIfSpecifiedOnProjectCreate() throws Throwable {
        String testAdminsGroupLink = createUserGroup().documentSelfLink;
        String testMembersGroupLink = createUserGroup().documentSelfLink;
        ProjectState testProject;

        // only admins group link provided
        testProject = createProject("test-project-1", null, false, testAdminsGroupLink, null);
        assertNotNull(testProject);
        assertNotNull(testProject.administratorsUserGroupLink);
        assertNotNull(testProject.membersUserGroupLink);
        assertEquals(testAdminsGroupLink, testProject.administratorsUserGroupLink);
        assertNotEquals(testMembersGroupLink, testProject.membersUserGroupLink);

        // only members group link provided
        testProject = createProject("test-project-2", null, false, null, testMembersGroupLink);
        assertNotNull(testProject);
        assertNotNull(testProject.administratorsUserGroupLink);
        assertNotNull(testProject.membersUserGroupLink);
        assertNotEquals(testAdminsGroupLink, testProject.administratorsUserGroupLink);
        assertEquals(testMembersGroupLink, testProject.membersUserGroupLink);

        // both admins and members group links provided
        testProject = createProject("test-project-3", null, false, testAdminsGroupLink,
                testMembersGroupLink);
        assertNotNull(testProject);
        assertNotNull(testProject.administratorsUserGroupLink);
        assertNotNull(testProject.membersUserGroupLink);
        assertEquals(testAdminsGroupLink, testProject.administratorsUserGroupLink);
        assertEquals(testMembersGroupLink, testProject.membersUserGroupLink);
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

    private UserGroupState createUserGroup() throws Throwable {

        Query query = QueryUtil.buildPropertyQuery(UserState.class, UserState.FIELD_NAME_SELF_LINK,
                buildUserServicePath(ADMIN_USERNAME)).querySpec.query;

        UserGroupState userGroupState = UserGroupState.Builder
                .create()
                .withQuery(query)
                .build();

        return doPost(userGroupState, UserGroupService.FACTORY_LINK);
    }
}
