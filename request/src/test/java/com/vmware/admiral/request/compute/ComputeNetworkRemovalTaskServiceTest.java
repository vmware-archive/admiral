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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.ComputeNetworkRemovalTaskService.ComputeNetworkRemovalTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
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
        ComputeNetwork cn = createNetwork("my net", true);
        cn = doPost(cn,
                ComputeNetworkDescriptionService.FACTORY_LINK);

        ComputeNetworkRemovalTaskState removalTask = createComputeNetworkRemovalTask(
                cn.documentSelfLink, 1);

        removalTask = remove(removalTask);

        ComputeNetwork networkState = getDocumentNoWait(ComputeNetwork.class,
                removalTask.resourceLinks.iterator().next());

        assertNull(networkState);
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
                ComputeNetwork cn = createNetwork(name, false);
                computeNetwork = doPost(cn, ComputeNetworkService.FACTORY_LINK);
                assertNotNull(computeNetwork);
            }
            return computeNetwork;
        }
    }

    private ComputeNetwork createNetwork(String name, boolean external) {
        ComputeNetwork cn = TestRequestStateFactory
                .createComputeNetworkState(name, UriUtils
                        .buildUriPath(ComputeNetworkDescriptionService.FACTORY_LINK, "test-desc"));
        cn.documentSelfLink = UUID.randomUUID().toString();
        cn.external = external;
        return cn;
    }

}
