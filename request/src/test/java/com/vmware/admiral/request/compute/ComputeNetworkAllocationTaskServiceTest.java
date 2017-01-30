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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.ComputeNetworkAllocationTaskService.ComputeNetworkAllocationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;

public class ComputeNetworkAllocationTaskServiceTest extends RequestBaseTest {

    protected ComputeNetworkDescription computeNetworkDesc;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        // setup Compute Network description.
        createComputeNetworkDescription(UUID.randomUUID().toString());
    }

    @Test
    public void testAllocationTaskServiceLifeCycle() throws Throwable {

        ComputeNetworkAllocationTaskState allocationTask = createComputeNetworkAllocationTask(
                computeNetworkDesc.documentSelfLink, 1);
        allocationTask = allocate(allocationTask);

        ComputeNetwork networkState = getDocument(ComputeNetwork.class,
                allocationTask.resourceLinks.iterator().next());

        assertNotNull(networkState);
        assertEquals(computeNetworkDesc.documentSelfLink, networkState.descriptionLink);
        assertTrue(networkState.name.contains(computeNetworkDesc.name));
        assertEquals(allocationTask.resourceLinks.iterator().next(), networkState.documentSelfLink);

    }

    @Test
    public void testExternalNetworkAllocation() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net", true);
        networkDescription = doPost(networkDescription,
                ComputeNetworkDescriptionService.FACTORY_LINK);

        ComputeNetworkAllocationTaskState allocationTask = createComputeNetworkAllocationTask(
                networkDescription.documentSelfLink, 1);

        allocationTask = allocate(allocationTask);

        ComputeNetwork networkState = getDocument(ComputeNetwork.class,
                allocationTask.resourceLinks.iterator().next());

        assertNotNull(networkState);
        assertEquals(networkDescription.documentSelfLink, networkState.descriptionLink);

        assertTrue(networkState.name.contains(networkDescription.name));
    }

    private ComputeNetworkAllocationTaskState createComputeNetworkAllocationTask(
            String networkDocSelfLink, long resourceCount) {

        ComputeNetworkAllocationTaskState allocationTask = new ComputeNetworkAllocationTaskState();
        allocationTask.resourceDescriptionLink = networkDocSelfLink;
        allocationTask.resourceCount = resourceCount;
        allocationTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        allocationTask.customProperties = new HashMap<>();
        return allocationTask;
    }

    private ComputeNetworkAllocationTaskState allocate(
            ComputeNetworkAllocationTaskState allocationTask)
            throws Throwable {
        allocationTask = startAllocationTask(allocationTask);
        host.log("Start allocation test: " + allocationTask.documentSelfLink);

        allocationTask = waitForTaskSuccess(allocationTask.documentSelfLink,
                ComputeNetworkAllocationTaskState.class);

        assertNotNull("ResourceLinks null for allocation: " + allocationTask.documentSelfLink,
                allocationTask.resourceLinks);
        assertEquals("Resource count not equal for: " + allocationTask.documentSelfLink,
                allocationTask.resourceCount, Long.valueOf(allocationTask.resourceLinks.size()));

        host.log("Finished allocation test: " + allocationTask.documentSelfLink);
        return allocationTask;
    }

    private ComputeNetworkAllocationTaskState startAllocationTask(
            ComputeNetworkAllocationTaskState allocationTask) throws Throwable {
        ComputeNetworkAllocationTaskState outAllocationTask = doPost(
                allocationTask, ComputeNetworkAllocationTaskService.FACTORY_LINK);
        assertNotNull(outAllocationTask);
        return outAllocationTask;
    }

    protected ComputeNetworkDescription createComputeNetworkDescription(String name)
            throws Throwable {
        synchronized (initializationLock) {
            if (computeNetworkDesc == null) {
                ComputeNetworkDescription desc = createNetworkDescription(name, false);
                computeNetworkDesc = doPost(desc,
                        ComputeNetworkDescriptionService.FACTORY_LINK);
                assertNotNull(containerNetworkDesc);
            }
            return computeNetworkDesc;
        }
    }

    private ComputeNetworkDescription createNetworkDescription(String name, boolean external) {
        ComputeNetworkDescription desc = TestRequestStateFactory
                .createComputeNetworkDescription(name);
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc.external = external;
        if (external) {
            desc.connectivity = "my-net-profile";
        }
        return desc;
    }
}
