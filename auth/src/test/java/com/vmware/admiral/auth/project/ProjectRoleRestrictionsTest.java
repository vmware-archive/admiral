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

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class ProjectRoleRestrictionsTest extends AuthBaseTest {

    private static final String AUTH_CONTENT_FILE_NAME = "roles-restrictions-auth-content.json";
    private static final String USER_EMAIL_FRITZ = "fritz@admiral.com";
    private static final String USER_EMAIL_TONY = "tony@admiral.com";

    @Before
    public void setup() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));
        loadAuthContent(AUTH_CONTENT_FILE_NAME);
    }

    @Test
    public void testComputesFromOtherProjectsAreNotAccessible() throws Throwable {
        // create compute state in project 1 as a member
        ComputeState csProject1 = createComputeStateAsUser(PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_CONNIE);
        // create compute state in project 2 as another member
        ComputeState csProject2 = createComputeStateAsUser(PROJECT_NAME_TEST_PROJECT_2, USER_EMAIL_GLORIA);

        // Project members and admins should be able to access the document, other users should not have access
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_CONNIE, true);
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_FRITZ, true);
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_GLORIA, false);

        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_GLORIA, true);
        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_FRITZ, true);
        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_CONNIE, false);
    }

    @Test
    public void testContainersFromOtherProjectsAreNotAccessible() throws Throwable {
        // create container state in project 1 as a member
        ContainerState csProject1 = createContainerStateAsUser(PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_CONNIE);
        // create container state in project 2 as another member
        ContainerState csProject2 = createContainerStateAsUser(PROJECT_NAME_TEST_PROJECT_2, USER_EMAIL_GLORIA);

        // Project members and admins should be able to access the document, other users should not have access
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_CONNIE, true);
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_FRITZ, true);
        verifyDocumentAccessible(csProject1.documentSelfLink, USER_EMAIL_GLORIA, false);

        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_GLORIA, true);
        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_FRITZ, true);
        verifyDocumentAccessible(csProject2.documentSelfLink, USER_EMAIL_CONNIE, false);
    }

    @Test
    public void testContainersOfOtherMembersOfTheProjectsAreAccessible() throws Throwable {
        // create container state in project 1 as a member
        ContainerState cs1 = createContainerStateAsUser(PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_CONNIE);
        // create container state in project 1 as another member
        ContainerState cs2 = createContainerStateAsUser(PROJECT_NAME_TEST_PROJECT_1, USER_EMAIL_TONY);

        // Project members and admins should be able to access all documents in the project
        verifyDocumentAccessible(cs1.documentSelfLink, USER_EMAIL_CONNIE, true);
        verifyDocumentAccessible(cs1.documentSelfLink, USER_EMAIL_TONY, true);
        verifyDocumentAccessible(cs1.documentSelfLink, USER_EMAIL_FRITZ, true);

        verifyDocumentAccessible(cs2.documentSelfLink, USER_EMAIL_CONNIE, true);
        verifyDocumentAccessible(cs2.documentSelfLink, USER_EMAIL_TONY, true);
        verifyDocumentAccessible(cs2.documentSelfLink, USER_EMAIL_FRITZ, true);
    }

    private void verifyDocumentAccessible(String documentLink, String userEmail, boolean expectAccessible) throws Throwable {
        host.assumeIdentity(buildUserServicePath(userEmail));
        try {
            Object result = getDocument(Object.class, documentLink);
            if (!expectAccessible) {
                throw new IllegalStateException(String.format("%s must not be able to access %s", userEmail, documentLink));
            } else {
                assertNotNull(result);
            }
        } catch (IllegalAccessError e) {
            if (expectAccessible) {
                throw new IllegalStateException(String.format("%s must be able to access %s", userEmail, documentLink), e);
            }
        }
    }

    private ComputeState createComputeStateAsUser(String projectName, String userEmail)
            throws Throwable {
        String projectLink = getProjectLinkByName(projectName);
        String userLink = buildUserServicePath(userEmail);

        host.assumeIdentity(userLink);

        ComputeState cs = new ComputeState();
        cs.name = UUID.randomUUID().toString();
        cs.descriptionLink = "desc-link";
        cs.type = ComputeType.VM_GUEST;
        cs.tenantLinks = new ArrayList<>();
        cs.tenantLinks.add(projectLink);

        ComputeState result = doPost(cs, ComputeService.FACTORY_LINK);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));

        return result;
    }

    private ContainerState createContainerStateAsUser(String projectName, String userEmail)
            throws Throwable {
        String projectLink = getProjectLinkByName(projectName);
        String userLink = buildUserServicePath(userEmail);

        host.assumeIdentity(userLink);

        ContainerState cs = new ContainerState();
        cs.name = UUID.randomUUID().toString();
        cs.descriptionLink = "desc-link";
        cs.tenantLinks = new ArrayList<>();
        cs.tenantLinks.add(projectLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));

        return doPost(cs, ContainerFactoryService.SELF_LINK);
    }

    private String getProjectLinkByName(String projectName) throws Throwable {
        ProjectState project = getProjectByName(projectName);
        return project == null ? null : project.documentSelfLink;
    }

    private ProjectState getProjectByName(String projectName) throws Throwable {
        List<ProjectState> projects = getDocumentsOfType(ProjectState.class);
        return projects.stream()
                .filter(project -> project.name.equals(projectName))
                .findFirst()
                .orElse(null);
    }
}
