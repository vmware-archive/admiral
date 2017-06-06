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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.LoadBalancerRemovalTaskService.LoadBalancerRemovalTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;

/**
 * Tests for the {@link LoadBalancerRemovalTaskService} class.
 */
public class LoadBalancerRemovalTaskServiceTest extends RequestBaseTest {

    private LoadBalancerDescription loadBalancerDesc;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();

        // setup Load Balancer description
        createLoadBalancerDescription(UUID.randomUUID().toString());
    }

    @Test
    public void testRemovalTaskServiceLifeCycle() throws Throwable {
        // TODO: enhance once the task implementation is complete
        LoadBalancerRemovalTaskState removalTask = createLoadBalancerRemovalTask(
                loadBalancerDesc.documentSelfLink);
        removalTask = remove(removalTask);
    }

    private LoadBalancerRemovalTaskState createLoadBalancerRemovalTask(
            String loadBalancerDescLink) {
        LoadBalancerRemovalTaskState removalTask = new LoadBalancerRemovalTaskState();
        removalTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        removalTask.customProperties = new HashMap<>();
        return removalTask;
    }

    private LoadBalancerRemovalTaskState remove(
            LoadBalancerRemovalTaskState removalTask)
            throws Throwable {
        removalTask = startRemovalTask(removalTask);
        host.log("Start removal test: " + removalTask.documentSelfLink);

        removalTask = waitForTaskSuccess(removalTask.documentSelfLink,
                LoadBalancerRemovalTaskState.class);

        return removalTask;
    }

    private LoadBalancerRemovalTaskState startRemovalTask(
            LoadBalancerRemovalTaskState removalTask) throws Throwable {
        LoadBalancerRemovalTaskState outRemovalTask = doPost(
                removalTask, LoadBalancerRemovalTaskService.FACTORY_LINK);
        assertNotNull(outRemovalTask);
        return outRemovalTask;
    }

    private LoadBalancerDescription createLoadBalancerDescription(String name)
            throws Throwable {
        synchronized (initializationLock) {
            if (loadBalancerDesc == null) {
                LoadBalancerDescription desc = TestRequestStateFactory
                        .createLoadBalancerDescription(name);
                desc.documentSelfLink = UUID.randomUUID().toString();
                desc.computeDescriptionLink = ComputeDescriptionService.FACTORY_LINK
                        + "/dummy-compute-link";

                loadBalancerDesc = doPost(desc, LoadBalancerDescriptionService.FACTORY_LINK);
                assertNotNull(loadBalancerDesc);
            }
            return loadBalancerDesc;
        }
    }
}
