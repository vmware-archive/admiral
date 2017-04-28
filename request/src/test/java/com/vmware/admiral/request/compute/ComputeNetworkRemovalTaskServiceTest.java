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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationRequest.allocationRequest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationState;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.ComputeNetworkRemovalTaskService.ComputeNetworkRemovalTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.UriUtils;

public class ComputeNetworkRemovalTaskServiceTest extends RequestBaseTest {

    protected ComputeNetwork computeNetwork;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        // setup Compute Network description.
        createComputeNetwork(UUID.randomUUID().toString());
    }

    @Test
    public void testNetworkRemoval() throws Throwable {
        ComputeNetwork cn = createNetwork("my net");
        cn = doPost(cn,
                ComputeNetworkService.FACTORY_LINK);

        ComputeNetworkRemovalTaskState removalTask = createComputeNetworkRemovalTask(
                cn.documentSelfLink, 1);

        removalTask = remove(removalTask);

        validateResourcesRemove(removalTask.resourceLinks.iterator().next(), null);
    }

    @Test
    public void testNetworkRemovalNoComputeNetworkShouldSucceed() throws Throwable {
        ComputeNetworkRemovalTaskState removalTask = createComputeNetworkRemovalTask(
                UUID.randomUUID().toString(), 1);

        removalTask = remove(removalTask);

        validateResourcesRemove(removalTask.resourceLinks.iterator().next(), null);
    }

    @Test
    public void testIsolatedNetworkRemoval() throws Throwable {
        SubnetState subnet = createSubnetState();

        ComputeNetworkCIDRAllocationState cidrAllocationState =
                createCIDRAllocation(subnet.networkLink);

        doPatch(allocationRequest(subnet.id, 28),
                ComputeNetworkCIDRAllocationState.class,
                cidrAllocationState.documentSelfLink);

        validateCIDRAllocated(cidrAllocationState.documentSelfLink, subnet.id);

        ComputeNetwork cn = createNetwork("my net");
        cn.networkType = ComputeNetworkDescriptionService.NetworkType.ISOLATED;
        cn.subnetLink = subnet.documentSelfLink;
        cn = doPost(cn, ComputeNetworkService.FACTORY_LINK);
        ComputeNetworkRemovalTaskState removalTask = createComputeNetworkRemovalTask(
                cn.documentSelfLink, 1);

        removalTask = remove(removalTask);

        validateResourcesRemove(removalTask.resourceLinks.iterator().next(),
                subnet.documentSelfLink);

        validateCIDRDeallocated(cidrAllocationState.documentSelfLink, subnet.id);
    }

    @Test
    public void testIsolatedNetworkRemovalNoSubnetStateShouldSucceed() throws Throwable {
        ComputeNetwork cn = createNetwork("my net");
        cn.networkType = ComputeNetworkDescriptionService.NetworkType.ISOLATED;
        cn.subnetLink = UUID.randomUUID().toString();
        cn = doPost(cn, ComputeNetworkService.FACTORY_LINK);
        ComputeNetworkRemovalTaskState removalTask = createComputeNetworkRemovalTask(
                cn.documentSelfLink, 1);

        removalTask = remove(removalTask);

        validateResourcesRemove(removalTask.resourceLinks.iterator().next(), null);
    }

    @Test
    public void testIsolatedNetworkRemovalNoSubnetAdapterShouldSucceed() throws Throwable {
        SubnetState subnet = createSubnetState();
        subnet.instanceAdapterReference = null;
        subnet = doPut(subnet);

        ComputeNetwork cn = createNetwork("my net");
        cn.networkType = ComputeNetworkDescriptionService.NetworkType.ISOLATED;
        cn.subnetLink = subnet.documentSelfLink;
        cn = doPost(cn, ComputeNetworkService.FACTORY_LINK);
        ComputeNetworkRemovalTaskState removalTask = createComputeNetworkRemovalTask(
                cn.documentSelfLink, 1);

        removalTask = remove(removalTask);

        validateResourcesRemove(removalTask.resourceLinks.iterator().next(),
                subnet.documentSelfLink);
    }

    private ComputeNetworkRemovalTaskState createComputeNetworkRemovalTask(
            String networkSelfLink, long resourceCount) {

        ComputeNetworkRemovalTaskState removalTask = new ComputeNetworkRemovalTaskState();
        removalTask.resourceLinks = new HashSet<>();
        removalTask.resourceLinks.add(networkSelfLink);
        removalTask.tenantLinks = computeNetwork.tenantLinks;
        removalTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        removalTask.customProperties = new HashMap<>();
        return removalTask;
    }

    private ComputeNetworkRemovalTaskState remove(
            ComputeNetworkRemovalTaskState removalTask)
            throws Throwable {
        ComputeNetworkRemovalTaskState outRemovalTask = doPost(
                removalTask, ComputeNetworkRemovalTaskService.FACTORY_LINK);
        assertNotNull(outRemovalTask);
        host.log("Start allocation test: " + outRemovalTask.documentSelfLink);

        outRemovalTask = waitForTaskSuccess(outRemovalTask.documentSelfLink,
                ComputeNetworkRemovalTaskState.class);

        assertNotNull("ResourceLinks null for allocation: " + outRemovalTask.documentSelfLink,
                outRemovalTask.resourceLinks);
        host.log("Finished allocation test: " + outRemovalTask.documentSelfLink);
        return outRemovalTask;
    }

    protected ComputeNetwork createComputeNetwork(String name)
            throws Throwable {
        synchronized (initializationLock) {
            if (computeNetwork == null) {
                ComputeNetwork cn = createNetwork(name);
                computeNetwork = doPost(cn, ComputeNetworkService.FACTORY_LINK);
                assertNotNull(computeNetwork);
            }
            return computeNetwork;
        }
    }

    private ComputeNetwork createNetwork(String name) {
        ComputeNetwork cn = TestRequestStateFactory
                .createComputeNetworkState(name, UriUtils
                        .buildUriPath(ComputeNetworkDescriptionService.FACTORY_LINK, "test-desc"));
        cn.documentSelfLink = UUID.randomUUID().toString();
        return cn;
    }

    private void validateCIDRAllocated(String cidrAllocationLink, String subnetId)
            throws Throwable {

        ComputeNetworkCIDRAllocationState cidrAllocation = getDocumentNoWait
                (ComputeNetworkCIDRAllocationState.class, cidrAllocationLink);

        assertNotNull(cidrAllocation);
        assertTrue(cidrAllocation.allocatedCIDRs.containsKey(subnetId));
    }

    private void validateCIDRDeallocated(String cidrAllocationLink, String subnetId)
            throws Throwable {
        ComputeNetworkCIDRAllocationState cidrAllocation = getDocumentNoWait
                (ComputeNetworkCIDRAllocationState.class, cidrAllocationLink);

        assertNotNull(cidrAllocation);
        assertFalse(cidrAllocation.allocatedCIDRs.containsKey(subnetId));
    }

    private void validateResourcesRemove(String computeNetworkLink, String subnetLink)
            throws Throwable {

        ComputeNetwork networkState = getDocumentNoWait(ComputeNetwork.class,
                computeNetworkLink);
        assertNull(networkState);

        if (subnetLink != null) {
            SubnetState subnet = getDocumentNoWait(SubnetState.class, subnetLink);
            assertNull(subnet);
        }

    }

    private ComputeNetworkCIDRAllocationState createCIDRAllocation(String networkLink)
            throws Throwable {

        ComputeNetworkCIDRAllocationState cidrAllocationState =
                new ComputeNetworkCIDRAllocationState();
        cidrAllocationState.networkLink = networkLink;

        return doPost(cidrAllocationState, ComputeNetworkCIDRAllocationService.FACTORY_LINK);
    }
}
