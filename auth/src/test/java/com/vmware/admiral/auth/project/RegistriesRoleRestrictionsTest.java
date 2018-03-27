/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.ApiVersion;
import com.vmware.admiral.service.common.RegistryService.RegistryState;

public class RegistriesRoleRestrictionsTest extends AuthBaseTest {

    private static final String AUTH_CONTENT_FILE_NAME = "single-project-with-admin-member-viewer-auth-content.json";

    private static final String USER_EMAIL_CLOUD_ADMIN = "administrator@admiral.com";

    private static final String PROJECT_NAME = "test.project";

    private static final String USER_EMAIL_PROJECT_ADMIN = "fritz@admiral.com";
    private static final String USER_EMAIL_PROJECT_MEMBER = "connie@admiral.com";
    private static final String USER_EMAIL_PROJECT_VIEWER = "tony@admiral.com";

    private static final String USER_EMAIL_ANOTHER_USER = "gloria@admiral.com";

    @Before
    public void setup() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_CLOUD_ADMIN));
        loadAuthContent(AUTH_CONTENT_FILE_NAME);
    }

    @Test
    public void testOnlyCloudAdminCanCreateGlobalRegistry() throws Throwable {

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_CLOUD_ADMIN));
        createRegistry("cloud-admin-global-registry",
                "cloud-admin.global.registry.com",
                null);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_PROJECT_ADMIN));
        try {
            createRegistry("project-admin-global-registry",
                    "project-admin.global.registry.com",
                    null);
            fail("Project admin should not be able to create global registries");
        } catch (IllegalAccessError e) {
            // expected
        }

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_PROJECT_MEMBER));
        try {
            createRegistry("project-member-global-registry",
                    "project-member.global.registry.com",
                    null);
            fail("Project member should not be able to create global registries");
        } catch (IllegalAccessError e) {
            // expected
        }

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_PROJECT_VIEWER));
        try {
            createRegistry("project-viewer-global-registry",
                    "project-viewer.global.registry.com",
                    null);
            fail("Project viewer should not be able to create global registries");
        } catch (IllegalAccessError e) {
            // expected
        }

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ANOTHER_USER));
        try {
            createRegistry("basic-user-global-registry",
                    "basic-user.global.registry.com",
                    null);
            fail("Basic user should not be able to create global registries");
        } catch (IllegalAccessError e) {
            // expected
        }

    }

    @Test
    public void testOnlyCloudAdminAndProjectAdminCanCreateProjectRegistry() throws Throwable {

        String projectLink = getProjectLinkByName(PROJECT_NAME);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_CLOUD_ADMIN));
        createRegistry("cloud-admin-project-registry",
                "cloud-admin.project.registry.com",
                projectLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_PROJECT_ADMIN));
        createRegistry("project-admin-project-registry",
                "project-admin.project.registry.com",
                projectLink);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_PROJECT_MEMBER));
        try {
            createRegistry("project-member-project-registry",
                    "project-member.project.registry.com",
                    projectLink);
            fail("Project member should not be able to create project registries");
        } catch (IllegalAccessError e) {
            // expected
        }

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_PROJECT_VIEWER));
        try {
            createRegistry("project-viewer-project-registry",
                    "project-viewer.project.registry.com",
                    projectLink);
            fail("Project viewer should not be able to create project registries");
        } catch (IllegalAccessError e) {
            // expected
        }

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ANOTHER_USER));
        try {
            createRegistry("basic-user-project-registry",
                    "basic-user.project.registry.com",
                    projectLink);
            fail("Basic user should not be able to create project registries");
        } catch (IllegalAccessError e) {
            // expected
        }

    }

    @Test
    public void testGlobalRegistriesAreAccessibleFromAllUsers() throws Throwable {
        RegistryState globalRegistry = createRegistryAsCloudAdmin("global-registry",
                "global.registry.com", null);
        String globalRegistryLink = globalRegistry.documentSelfLink;

        // everybody should be able to read the global registry
        verifyDocumentAccessible(globalRegistryLink, USER_EMAIL_CLOUD_ADMIN, true);
        verifyDocumentAccessible(globalRegistryLink, USER_EMAIL_PROJECT_ADMIN, true);
        verifyDocumentAccessible(globalRegistryLink, USER_EMAIL_PROJECT_MEMBER, true);
        verifyDocumentAccessible(globalRegistryLink, USER_EMAIL_PROJECT_VIEWER, true);
        verifyDocumentAccessible(globalRegistryLink, USER_EMAIL_ANOTHER_USER, true);

        // only cloud admin should be able to update it
        verifyRegistryEditable(globalRegistryLink, USER_EMAIL_CLOUD_ADMIN, true);
        verifyRegistryEditable(globalRegistryLink, USER_EMAIL_PROJECT_ADMIN, false);
        verifyRegistryEditable(globalRegistryLink, USER_EMAIL_PROJECT_MEMBER, false);
        verifyRegistryEditable(globalRegistryLink, USER_EMAIL_PROJECT_VIEWER, false);
        verifyRegistryEditable(globalRegistryLink, USER_EMAIL_ANOTHER_USER, false);
    }

    @Test
    public void testProjectRegistriesAreAccessibleFromMembersAndCloudAdminsOnly() throws Throwable {
        RegistryState projectRegistry = createRegistryAsCloudAdmin("global-registry",
                "project.registry.com", getProjectLinkByName(PROJECT_NAME));
        String projectRegistryLink = projectRegistry.documentSelfLink;

        // all project members and cloud admins should be able to read the project registry. Members
        // of other projects should not be able to read it.
        verifyDocumentAccessible(projectRegistryLink, USER_EMAIL_CLOUD_ADMIN, true);
        verifyDocumentAccessible(projectRegistryLink, USER_EMAIL_PROJECT_ADMIN, true);
        verifyDocumentAccessible(projectRegistryLink, USER_EMAIL_PROJECT_MEMBER, true);
        verifyDocumentAccessible(projectRegistryLink, USER_EMAIL_PROJECT_VIEWER, true);
        verifyDocumentAccessible(projectRegistryLink, USER_EMAIL_ANOTHER_USER, false);

        // Only cloud admins and project admins should be able to update the project registry
        verifyRegistryEditable(projectRegistryLink, USER_EMAIL_CLOUD_ADMIN, true);
        verifyRegistryEditable(projectRegistryLink, USER_EMAIL_PROJECT_ADMIN, true);
        verifyRegistryEditable(projectRegistryLink, USER_EMAIL_PROJECT_MEMBER, false);
        verifyRegistryEditable(projectRegistryLink, USER_EMAIL_PROJECT_VIEWER, false);
        verifyRegistryEditable(projectRegistryLink, USER_EMAIL_ANOTHER_USER, false);
    }

    private RegistryState createRegistryAsCloudAdmin(String name, String address,
            String projectLink) throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_CLOUD_ADMIN));
        return createRegistry(name, address, projectLink);

    }

    private RegistryState createRegistry(String name, String address, String projectLink)
            throws Throwable {
        RegistryState registry = new RegistryState();
        registry.name = name;
        registry.address = address;
        registry.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;
        registry.customProperties = new HashMap<>();
        registry.customProperties.put(RegistryService.API_VERSION_PROP_NAME,
                ApiVersion.V1.toString());
        if (projectLink != null && !projectLink.isEmpty()) {
            registry.tenantLinks = Collections.singletonList(projectLink);
        }

        RegistryState resultRegistry = doPost(registry, RegistryFactoryService.SELF_LINK);

        host.log(Level.INFO, "Created %s registry '%s' [%s].",
                address == null ? "global" : "project-specific",
                name,
                resultRegistry.documentSelfLink);

        return resultRegistry;
    }

    private void verifyRegistryEditable(String documentLink, String userEmail,
            boolean expectEditable) throws Throwable {
        final String updatedName = "Updated name " + UUID.randomUUID().toString();
        RegistryState patchState = new RegistryState();
        patchState.name = updatedName;

        host.assumeIdentity(buildUserServicePath(userEmail));

        try {
            RegistryState resultState = doPatch(patchState, documentLink);
            host.log(Level.INFO, "Updated name of registry [%s] to '%s'",
                    documentLink,
                    updatedName);
            if (!expectEditable) {
                String error = String.format("%s must not be able to update %s",
                        userEmail,
                        documentLink);
                host.log(Level.SEVERE, error);
                throw new IllegalStateException(error);
            } else {
                assertNotNull(resultState);
                assertEquals(updatedName, resultState.name);
            }
        } catch (IllegalAccessError e) {
            if (expectEditable) {
                String error = String.format("%s must be able to update %s",
                        userEmail,
                        documentLink);
                host.log(Level.SEVERE, error);
                throw new IllegalStateException(error, e);
            }
        }
    }

}
