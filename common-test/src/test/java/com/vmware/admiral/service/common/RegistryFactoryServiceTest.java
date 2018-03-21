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

package com.vmware.admiral.service.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class RegistryFactoryServiceTest extends BaseRegistryStateQueryTest {

    private static final String GLOBAL_REGISTRY_NAME = "global-registry";
    private static final String GLOBAL_REGISTRY_ADDRESS = "global.registry";

    private static final String PROJECT_A_LINK = UriUtils.buildUriPath(ManagementUriParts.PROJECTS,
            "test-project-a");
    private static final String PROJECT_A_REGISTRY_NAME = "project-a-registry";
    private static final String PROJECT_A_REGISTRY_ADDRESS = "project.a.registry";

    private static final String PROJECT_B_LINK = UriUtils.buildUriPath(ManagementUriParts.PROJECTS,
            "test-project-b");
    private static final String PROJECT_B_REGISTRY_NAME = "project-b-registry";
    private static final String PROJECT_B_REGISTRY_ADDRESS = "project.b.registry";

    @Test
    public void testGetWithoutProjectHeaderRetrievesAllRegistries() throws Throwable {

        ArrayList<RegistryState> registries = new ArrayList<>();

        // Create a global registry
        registries.add(createRegistry(
                null,
                GLOBAL_REGISTRY_NAME,
                GLOBAL_REGISTRY_ADDRESS));

        // Create a project-specific registry
        registries.add(createRegistry(
                Collections.singletonList(PROJECT_A_LINK),
                PROJECT_A_REGISTRY_NAME,
                PROJECT_A_REGISTRY_ADDRESS));

        // Create another project-specific registry
        registries.add(createRegistry(
                Collections.singletonList(PROJECT_B_LINK),
                PROJECT_B_REGISTRY_NAME,
                PROJECT_B_REGISTRY_ADDRESS));

        // Do a GET without setting the project header.
        ServiceDocumentQueryResult result = getRegistries();

        // Verify that all registries are listed
        assertNotNull("could not get result for GET from the factory service", result);
        List<String> foundRegistryLinks = result.documentLinks;
        assertEquals("Unexpected number of registries retrieved", registries.size(),
                foundRegistryLinks.size());
        for (RegistryState registry : registries) {
            assertTrue("Expected to find registry " + registry.documentSelfLink,
                    foundRegistryLinks.contains(registry.documentSelfLink));
        }
    }

    @Test
    public void testGetWithProjectHeaderRetrievesGlobalAndProjectSpecificRegistriesOnly()
            throws Throwable {

        ArrayList<RegistryState> expectedRegistries = new ArrayList<>();
        ArrayList<RegistryState> unexpectedRegistries = new ArrayList<>();

        // Create a global registry
        expectedRegistries.add(createRegistry(
                null,
                GLOBAL_REGISTRY_NAME,
                GLOBAL_REGISTRY_ADDRESS));

        // Create a project-specific registry
        expectedRegistries.add(createRegistry(
                Collections.singletonList(PROJECT_A_LINK),
                PROJECT_A_REGISTRY_NAME,
                PROJECT_A_REGISTRY_ADDRESS));

        // Create another project-specific registry
        unexpectedRegistries.add(createRegistry(
                Collections.singletonList(PROJECT_B_LINK),
                PROJECT_B_REGISTRY_NAME,
                PROJECT_B_REGISTRY_ADDRESS));

        // Do a GET with a project header specifying one of the projects
        ServiceDocumentQueryResult result = getRegistries(PROJECT_A_LINK);

        // Verify that all registries are listed
        assertNotNull("could not get result for GET from the factory service", result);
        List<String> foundRegistryLinks = result.documentLinks;
        assertEquals("Unexpected number of registries retrieved", expectedRegistries.size(),
                foundRegistryLinks.size());
        for (RegistryState registry : expectedRegistries) {
            assertTrue("Expected to find registry " + registry.documentSelfLink,
                    foundRegistryLinks.contains(registry.documentSelfLink));
        }
        for (RegistryState registry : unexpectedRegistries) {
            assertFalse("Expected NOT to find registry " + registry.documentSelfLink,
                    foundRegistryLinks.contains(registry.documentSelfLink));
        }
    }

    private ServiceDocumentQueryResult getRegistries() {
        return getRegistries(null);
    }

    private ServiceDocumentQueryResult getRegistries(String projectHeaderValue) {
        ServiceDocumentQueryResult[] result = new ServiceDocumentQueryResult[1];
        Operation get = Operation.createGet(host, RegistryFactoryService.SELF_LINK)
                .setReferer("/" + getClass().getSimpleName())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get registries: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            result[0] = o.getBody(ServiceDocumentQueryResult.class);
                            host.completeIteration();
                        } catch (Throwable e) {
                            host.log(Level.SEVERE, "Failed to read response body: %s",
                                    Utils.toString(e));
                            host.failIteration(e);
                        }
                    }
                });

        if (projectHeaderValue != null) {
            get.addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER, projectHeaderValue);
        }

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result[0];
    }
}
