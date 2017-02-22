/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.service.common.ResourceNamePrefixService.ResourceNamePrefixState;

public class CommonInitialBootServiceTest extends ComputeBaseTest {

    @Test
    public void testVerifyInitialBootServiceWillStopAfterAllInstancesCreated() throws Throwable {
        waitForServiceAvailability(ResourceNamePrefixService.DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK);

        waitForInitialBootServiceToBeSelfStopped(CommonInitialBootService.SELF_LINK);
    }

    @Test
    public void testDefaultResourcePrefixNameCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(ResourceNamePrefixService.DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK);
        ResourceNamePrefixState defaultNamePrefixState = getDocument(ResourceNamePrefixState.class,
                ResourceNamePrefixService.DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK);
        assertNotNull(defaultNamePrefixState);
        assertNull(defaultNamePrefixState.tenantLinks);
    }

    @Test
    public void testDefaultRegistryStateCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(RegistryService.DEFAULT_INSTANCE_LINK);
        RegistryState registryState = getDocument(RegistryState.class,
                RegistryService.DEFAULT_INSTANCE_LINK);
        assertNotNull(registryState);
        assertEquals(RegistryService.DEFAULT_REGISTRY_ADDRESS, registryState.address);
        assertEquals(RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE, registryState.endpointType);
    }

    @Test
    public void testVerifyRestartOfInitialBootServiceDoesNotUpdateInstance() throws Throwable {
        waitForServiceAvailability(ResourceNamePrefixService.DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK);
        ResourceNamePrefixState defaultNamePrefixState = getDocument(ResourceNamePrefixState.class,
                ResourceNamePrefixService.DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK);
        assertNotNull(defaultNamePrefixState);
        assertEquals(0, defaultNamePrefixState.documentVersion);

        waitForInitialBootServiceToBeSelfStopped(CommonInitialBootService.SELF_LINK);

        //simulate a restart of the service host
        startInitialBootService(CommonInitialBootService.class, CommonInitialBootService.SELF_LINK);

        waitForInitialBootServiceToBeSelfStopped(CommonInitialBootService.SELF_LINK);

        defaultNamePrefixState = getDocument(ResourceNamePrefixState.class,
                ResourceNamePrefixService.DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK);
        assertNotNull(defaultNamePrefixState);
        assertEquals("Document should not be updated once it exists.", 0,
                defaultNamePrefixState.documentVersion);
    }
}
