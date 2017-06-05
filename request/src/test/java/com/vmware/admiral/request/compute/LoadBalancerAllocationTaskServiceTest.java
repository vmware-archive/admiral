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
import com.vmware.admiral.request.compute.LoadBalancerAllocationTaskService.LoadBalancerAllocationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;

/**
 * Tests for the {@link LoadBalancerAllocationTaskService} class.
 */
public class LoadBalancerAllocationTaskServiceTest extends RequestBaseTest {

    private LoadBalancerDescription loadBalancerDesc;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();

        // setup Load Balancer description
        createLoadBalancerDescription(UUID.randomUUID().toString());
    }

    @Test
    public void testAllocationTaskServiceLifeCycle() throws Throwable {
        // TODO: enhance once the task implementation is complete
        LoadBalancerAllocationTaskState allocationTask = createLoadBalancerAllocationTask(
                loadBalancerDesc.documentSelfLink);
        allocationTask = allocate(allocationTask);
    }

    private LoadBalancerAllocationTaskState createLoadBalancerAllocationTask(
            String loadBalancerDescLink) {
        LoadBalancerAllocationTaskState allocationTask = new LoadBalancerAllocationTaskState();
        allocationTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        allocationTask.customProperties = new HashMap<>();
        return allocationTask;
    }

    private LoadBalancerAllocationTaskState allocate(
            LoadBalancerAllocationTaskState allocationTask)
            throws Throwable {
        allocationTask = startAllocationTask(allocationTask);
        host.log("Start allocation test: " + allocationTask.documentSelfLink);

        allocationTask = waitForTaskSuccess(allocationTask.documentSelfLink,
                LoadBalancerAllocationTaskState.class);

        return allocationTask;
    }

    private LoadBalancerAllocationTaskState startAllocationTask(
            LoadBalancerAllocationTaskState allocationTask) throws Throwable {
        LoadBalancerAllocationTaskState outAllocationTask = doPost(
                allocationTask, LoadBalancerAllocationTaskService.FACTORY_LINK);
        assertNotNull(outAllocationTask);
        return outAllocationTask;
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
                desc.protocol = "HTTP";
                desc.port = 80;
                desc.instanceProtocol = "HTTP";
                desc.instancePort = 80;

                loadBalancerDesc = doPost(desc, LoadBalancerDescriptionService.FACTORY_LINK);
                assertNotNull(loadBalancerDesc);
            }
            return loadBalancerDesc;
        }
    }
}
