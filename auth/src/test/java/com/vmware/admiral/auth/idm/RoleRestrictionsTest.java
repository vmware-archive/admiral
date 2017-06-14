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

package com.vmware.admiral.auth.idm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

public class RoleRestrictionsTest extends AuthBaseTest {

    @Test
    public void testBasicUserCantReadRestrictedContent() throws Throwable {
        // Create simple container description with admins user.
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        ContainerDescription description = new ContainerDescription();
        description.name = "test-name";
        description.image = "ubuntu";
        description = doPost(description, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(description);
        assertNotNull(description.documentSelfLink);

        // Verify basic user cannot see it.
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        List<String> containerDescriptionLinks = getDocument(
                ServiceDocumentQueryResult.class, ContainerDescriptionService.FACTORY_LINK)
                .documentLinks;
        assertTrue(containerDescriptionLinks == null || containerDescriptionLinks.isEmpty());
        try {
            ContainerDescription cd = getDocument(
                    ContainerDescription.class, description.documentSelfLink);
            fail("It was expected IllegalAccessError when user attempt to get restricted document");
        } catch (IllegalAccessError ex) {
            assertTrue(ex.getMessage().startsWith("forbidden"));
        }
    }

    @Test
    public void testBasicUserCanReadAllowedContent() throws Throwable {
        // Create simple project with admins user. Basic user should be able to read it.
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        ProjectState projectState = new ProjectState();
        projectState.name = "test-name";
        projectState.description = "test-description";
        projectState.isPublic = true;
        projectState = doPost(projectState, ProjectFactoryService.SELF_LINK);
        assertNotNull(projectState);
        assertNotNull(projectState.documentSelfLink);

        // Verify basic user can read this document
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_CONNIE));
        List<String> projectLinks = getDocument(ServiceDocumentQueryResult.class,
                ProjectFactoryService.SELF_LINK).documentLinks;
        assertTrue(projectLinks != null && !projectLinks.isEmpty());
        assertTrue(projectLinks.contains(projectState.documentSelfLink));

        ProjectState state = getDocument(ProjectState.class, projectState.documentSelfLink);
        assertNotNull(state);
        assertEquals(projectState.name, state.name);
        assertEquals(projectState.description, state.description);
    }
}
