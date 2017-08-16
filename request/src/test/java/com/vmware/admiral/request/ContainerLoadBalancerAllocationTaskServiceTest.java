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

package com.vmware.admiral.request;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService
        .ContainerLoadBalancerState;
import com.vmware.admiral.request.ContainerLoadBalancerAllocationTaskService
        .ContainerLoadBalancerAllocationTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;

/**
 * Tests for the {@link ContainerLoadBalancerAllocationTaskService} class.
 */
public class ContainerLoadBalancerAllocationTaskServiceTest extends ContainerLoadBalancerBaseTest {

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();

        // setup description
        createContainerLoadBalancerDescription(UUID.randomUUID().toString());
    }

    @Test
    public void testAllocationTaskServiceLifeCycle() throws Throwable {
        ContainerLoadBalancerAllocationTaskState allocationTask = createLoadBalancerAllocationTask(
                loadBalancerDesc.documentSelfLink);
        allocationTask = allocate(allocationTask);

        ContainerLoadBalancerState state = getDocument(ContainerLoadBalancerState.class,
                allocationTask.resourceLinks.iterator().next());

        assertNotNull(state);
        assertEquals(loadBalancerDesc.documentSelfLink, state.descriptionLink);
        assertTrue(state.name.contains(loadBalancerDesc.name));
        assertEquals(allocationTask.resourceLinks.iterator().next(), state.documentSelfLink);
    }

    private ContainerLoadBalancerAllocationTaskState createLoadBalancerAllocationTask(
            String loadBalancerDescLink) {
        ContainerLoadBalancerAllocationTaskState allocationTask = new
                ContainerLoadBalancerAllocationTaskState();
        allocationTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        allocationTask.resourceDescriptionLink = loadBalancerDescLink;
        allocationTask.resourceCount = 1L;
        allocationTask.customProperties = new HashMap<>();
        return allocationTask;
    }

    private ContainerLoadBalancerAllocationTaskState allocate(
            ContainerLoadBalancerAllocationTaskState allocationTask)
            throws Throwable {
        allocationTask = startAllocationTask(allocationTask);
        host.log("Start allocation test: " + allocationTask.documentSelfLink);

        allocationTask = waitForTaskSuccess(allocationTask.documentSelfLink,
                ContainerLoadBalancerAllocationTaskState.class);

        return allocationTask;
    }

    private ContainerLoadBalancerAllocationTaskState startAllocationTask(
            ContainerLoadBalancerAllocationTaskState allocationTask) throws Throwable {
        ContainerLoadBalancerAllocationTaskState outAllocationTask = doPost(
                allocationTask, ContainerLoadBalancerAllocationTaskService.FACTORY_LINK);
        assertNotNull(outAllocationTask);
        return outAllocationTask;
    }
}
