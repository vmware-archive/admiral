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

package com.vmware.admiral.image.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.registry.mock.BaseMockRegistryTestCase;
import com.vmware.admiral.adapter.registry.mock.MockRegistryPathConstants;
import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse.Result;
import com.vmware.admiral.host.HostInitRegistryAdapterServiceConfig;
import com.vmware.admiral.image.service.mock.MockRegistryAdapterService;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class ContainerImageServiceTest extends BaseMockRegistryTestCase {

    private static final String BASE_REGISTRY_PATH = MockRegistryPathConstants
            .MOCK_REGISTRY_PATH_HOSTNAME_AND_PORT;
    private static final String REGISTRY_WITH_NAMESPACE_PATH = String.format("%s%s",
            MockRegistryPathConstants.MOCK_REGISTRY_PATH_HOSTNAME_AND_PORT,
            MockRegistryPathConstants.REGISTRY_NAMESPACE_PATH);
    private static final String QUERY_IN_THE_BASE =
            "/images?q=ubuntu&tenantLinks=/projects/default-project&documentType=true&limit=10000";
    private static final String QUERY_IN_THE_NAMESPACE = "/images?q=ubuntu&registry=namespace"
            + "&tenantLinks=/projects/default-project&documentType=true&limit=10000";
    private static final String QUERY_IN_NOT_EXISTING_NAMESPACE =
            "/images?q=ubuntu&registry=anotherNamespace"
            + "&tenantLinks=/projects/default-project&documentType=true&limit=10000";

    private RegistryState registryWithNamespaceState;
    private RegistryState baseRegistryState;

    @Before
    public void setUp() throws Throwable {
        Field f = HostInitRegistryAdapterServiceConfig.class.getDeclaredField
                ("registryAdapterReference");
        f.setAccessible(true);
        f.set(null, UriUtils.buildUri(
                host, MockRegistryAdapterService.class));

        startServices();
        waitForServiceAvailability(MockRegistryAdapterService.SELF_LINK);
        waitForServiceAvailability(ContainerImageService.SELF_LINK);
        waitForServiceAvailability(RegistryFactoryService.SELF_LINK);
        waitForServiceAvailability(RegistryAdapterService.SELF_LINK);
    }

    private void startServices() {
        host.startService(new RegistryFactoryService());
        host.startService(Operation.createPost(UriUtils.buildUri(
                host, MockRegistryAdapterService.class)),
                new MockRegistryAdapterService());
        host.startService(Operation.createPost(UriUtils.buildUri(
                host, ContainerImageService.class)),
                new ContainerImageService());
        host.startService(Operation.createPost(UriUtils.buildUri(
                host, RegistryAdapterService.class)),
                new RegistryAdapterService());
    }

    @Test
    public void testSearchInBaseRegistry() throws Throwable {
        createBaseRegistryState();
        List<Result> results = searchForImages(host.getUri().toString() + QUERY_IN_THE_BASE);
        verifyResults(results, 5, 3, true);
    }

    @Test
    public void testSearchInRegistryWithNamespace() throws Throwable {
        createRegistryWithNamespaceState();
        List<Result> results = searchForImages(host.getUri().toString() + QUERY_IN_THE_NAMESPACE);
        verifyResults(results, 0, 3, false);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testSearchInRegistryWithNamespaceShouldFail() throws Throwable {
        createRegistryWithNamespaceState();
        searchForImages(host.getUri().toString() + QUERY_IN_NOT_EXISTING_NAMESPACE);
    }

    private void createBaseRegistryState() throws Throwable {
        baseRegistryState = new RegistryState();
        baseRegistryState.address = BASE_REGISTRY_PATH;
        baseRegistryState.name = "";
        doPost(baseRegistryState, RegistryFactoryService.SELF_LINK);
    }

    private void createRegistryWithNamespaceState() throws Throwable {
        registryWithNamespaceState = new RegistryState();
        registryWithNamespaceState.address = REGISTRY_WITH_NAMESPACE_PATH;
        registryWithNamespaceState.name = MockRegistryPathConstants.REGISTRY_NAMESPACE_NAME;
        doPost(registryWithNamespaceState, RegistryFactoryService.SELF_LINK);
    }

    private List<Result> searchForImages(String registry) {
        List<Result> results = new ArrayList<>();
        Operation op = Operation.createGet(UriUtils.buildUri(registry))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE, "Can't find images");
                        host.failIteration(e);
                    } else {
                        results.addAll(o.getBody(RegistrySearchResponse.class).results);
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();

        return results;
    }

    private void verifyResults(List<Result> results, int expectedResultsInTheBase, int
            expectedResultsInTheNamespace, boolean shouldShowResultsFromBase) {
        if (shouldShowResultsFromBase) {
            assertEquals(expectedResultsInTheBase, results.size());
            int baseCounter = 0;
            int namespaceCounter = 0;
            for (Result res: results) {
                assertTrue(res.official);
                if (res.name.contains(MockRegistryPathConstants.MOCK_REGISTRY_HOSTNAME_AND_PORT)) {
                    if (res.name.contains(MockRegistryPathConstants.REGISTRY_NAMESPACE_PATH)) {
                        namespaceCounter++;
                    }
                    baseCounter++;
                } else {
                    throw new IllegalStateException("Found image with unexpected path: " + res.name);
                }
            }
            assertEquals(expectedResultsInTheBase, baseCounter);
            assertEquals(expectedResultsInTheNamespace, namespaceCounter);
        } else {
            assertEquals(expectedResultsInTheNamespace, results.size());
            for (Result res: results) {
                if (!res.name.contains(MockRegistryPathConstants.REGISTRY_NAMESPACE_PATH)) {
                    throw new IllegalStateException("Found image with unexpected path: " + res.name);
                }
            }
        }
    }
}