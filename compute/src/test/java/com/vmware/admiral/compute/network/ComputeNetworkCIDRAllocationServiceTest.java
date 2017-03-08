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

package com.vmware.admiral.compute.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest.RequestType;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.test.TestContext;

public class ComputeNetworkCIDRAllocationServiceTest extends ComputeBaseTest {
    private static final String NETWORK_LINK = "/myNetworkLink";
    private List<String> networkCIDRAllocationsForDeletion;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ComputeNetworkCIDRAllocationService.FACTORY_LINK);
        networkCIDRAllocationsForDeletion = new ArrayList<>();
    }

    @After
    public void tearDown() throws Throwable {
        for (String selfLink : networkCIDRAllocationsForDeletion) {
            delete(selfLink);
        }
    }

    @Test
    public void testCreateComputeNetworkCIDRAllocationService() throws Throwable {
        verifyService(
                FactoryService.create(ComputeNetworkCIDRAllocationService.class),
                ComputeNetworkCIDRAllocationState.class,
                (prefix, index) -> {
                    ComputeNetworkCIDRAllocationState networkCIDRAllocation =
                            new ComputeNetworkCIDRAllocationState();
                    networkCIDRAllocation.networkLink = NETWORK_LINK;

                    return networkCIDRAllocation;
                },
                (prefix, serviceDocument) -> {
                    ComputeNetworkCIDRAllocationState networkCIDRAllocation =
                            (ComputeNetworkCIDRAllocationState) serviceDocument;
                    networkCIDRAllocationsForDeletion.add(networkCIDRAllocation.documentSelfLink);
                    assertEquals(NETWORK_LINK, networkCIDRAllocation.networkLink);
                    assertNotNull(networkCIDRAllocation.allocatedCIDRs);
                    assertEquals(0, networkCIDRAllocation.allocatedCIDRs.size());
                    assertNull(networkCIDRAllocation.lastAllocatedCIDR);
                });
    }

    /**
     * Test that CIDRs are uniquely allocated in case of concurrent allocation requests.
     */
    @Test
    @Ignore("Ignore until CIDRAllocationService starts returning non hardcoded values.")
    public void testCIDRAllocationConcurrency() throws Throwable {
        int concurrentRequestsCount = 10;

        String cidrAllocationLink = createNetworkCIDRAllocationState();

        List<DeferredResult<ComputeNetworkCIDRAllocationState>> allocationRequests = new
                ArrayList<>();

        // Send multiple concurrent allocation requests.
        for (int i = 0; i < concurrentRequestsCount; i++) {

            ComputeNetworkCIDRAllocationRequest request = new ComputeNetworkCIDRAllocationRequest();
            request.requestType = RequestType.ALLOCATE;
            request.subnetLink = "subnet" + i;
            allocationRequests.add(
                    host.sendWithDeferredResult(
                            Operation.createPatch(host, cidrAllocationLink),
                            ComputeNetworkCIDRAllocationState.class));
        }

        // Assert that all allocation requests got a unique result value.
        DeferredResult.allOf(allocationRequests)
                .whenComplete((allocations, throwable) -> {
                    assertNull(throwable);
                    assertNotNull(allocations);

                    long distinctAllocatedCIDRs = allocations.stream()
                            .map(allocation -> allocation.lastAllocatedCIDR)
                            .distinct()
                            .count();

                    assertEquals("Not all allocated CIDRs are distinct.",
                            concurrentRequestsCount, distinctAllocatedCIDRs);
                });
    }

    @Test
    public void testCIDRAllocateDeallocate() throws Throwable {
        String cidrAllocationLink = createNetworkCIDRAllocationState();

        // Allocate.
        ComputeNetworkCIDRAllocationRequest request = new ComputeNetworkCIDRAllocationRequest();
        request.requestType = RequestType.ALLOCATE;
        request.subnetLink = "subnet";

        ComputeNetworkCIDRAllocationState allocation = patchService(cidrAllocationLink, request);

        assertNotNull(allocation.lastAllocatedCIDR);
        assertNotNull(allocation.allocatedCIDRs);
        assertEquals(1, allocation.allocatedCIDRs.size());
        assertTrue(allocation.allocatedCIDRs.containsKey(request.subnetLink));
        assertEquals(allocation.lastAllocatedCIDR, allocation.allocatedCIDRs.get(request
                .subnetLink));

        // Deallocate.
        request = new ComputeNetworkCIDRAllocationRequest();
        request.requestType = RequestType.DEALLOCATE;
        request.subnetLink = "subnet";

        ComputeNetworkCIDRAllocationState deallocation = patchService(cidrAllocationLink, request);

        assertFalse(deallocation.allocatedCIDRs.containsKey(request.subnetLink));
        assertFalse(deallocation.allocatedCIDRs.containsValue(allocation.lastAllocatedCIDR));
    }

    private String createNetworkCIDRAllocationState() throws Throwable {
        ComputeNetworkCIDRAllocationState state = new ComputeNetworkCIDRAllocationState();
        state.networkLink = NETWORK_LINK;
        state = doPost(state, ComputeNetworkCIDRAllocationService.FACTORY_LINK);
        networkCIDRAllocationsForDeletion.add(state.documentSelfLink);

        return state.documentSelfLink;
    }

    private ComputeNetworkCIDRAllocationState patchService(
            String cidrAllocationLink,
            ComputeNetworkCIDRAllocationRequest request) {

        TestContext ctx = testCreate(1);

        final ComputeNetworkCIDRAllocationState[] allocation = new ComputeNetworkCIDRAllocationState[1];
        host.sendWithDeferredResult(Operation.createPatch(host, cidrAllocationLink)
                .setBody(request), ComputeNetworkCIDRAllocationState.class)
                .whenComplete((allocationState, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                        return;
                    }
                    allocation[0] = allocationState;
                    ctx.completeIteration();
                });
        ctx.await();

        return allocation[0];
    }
}