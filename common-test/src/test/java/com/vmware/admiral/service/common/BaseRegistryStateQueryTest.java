/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.junit.Before;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Utils;

/**
 * Base class for RegistryState query test
 */
public abstract class BaseRegistryStateQueryTest extends BaseTestCase {
    private final List<RegistryState> registries = new ArrayList<>();
    private Service service;
    protected final AtomicBoolean expectedResultFound = new AtomicBoolean();
    protected final AtomicBoolean errors = new AtomicBoolean();

    @Before
    public void setUp() throws Throwable {
        expectedResultFound.set(false);
        errors.set(false);

        host.startService(new RegistryFactoryService());

        waitForServiceAvailability(RegistryFactoryService.SELF_LINK);
    }

    protected RegistryState createRegistry(List<String> tenantLinks, String id, String address)
            throws Throwable {

        RegistryState registryState = new RegistryState();
        registryState.documentSelfLink = id;
        registryState.address = address;
        registryState.tenantLinks = tenantLinks;
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;
        registryState = doPost(registryState, RegistryFactoryService.SELF_LINK);
        assertNotNull("Failed to create registry: " + id, registryState);
        registries.add(registryState);

        return registryState;
    }

    protected Consumer<Collection<String>> verifyContainsLink(String message,
            String documentSelfLink) {

        return verify((registryLinks) -> assertTrue(message,
                registryLinks.contains(documentSelfLink)));
    }

    protected Consumer<Collection<String>> verifyDoesntContainLink(String message,
            String documentSelfLink) {

        return verify((registryLinks) -> assertFalse(message,
                registryLinks.contains(documentSelfLink)));
    }

    protected Consumer<Collection<String>> verify(Consumer<Collection<String>> consumer) {
        return (registryLinks) -> {
            try {
                consumer.accept(registryLinks);

                host.completeIteration();

            } catch (Throwable x) {
                host.failIteration(x);
            }
        };
    }

    protected final Consumer<Collection<Throwable>> FAIL_ON_ERROR = (failures) -> {
        host.failIteration(failures.iterator().next());
    };

    protected final Consumer<Collection<Throwable>> FAIL_ON_ERROR_HANDLER = (failures) -> {
        host.log(Level.SEVERE, "Exception during search for registry when group exluded:");
        for (Throwable throwable : failures) {
            host.log(Level.SEVERE, Utils.toString(throwable));
        }
        errors.set(true);
        expectedResultFound.set(true);
    };

}
