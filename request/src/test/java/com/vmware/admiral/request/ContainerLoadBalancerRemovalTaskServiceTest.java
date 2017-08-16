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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService
        .ContainerLoadBalancerState;
import com.vmware.admiral.request.ContainerLoadBalancerRemovalTaskService
        .ContainerLoadBalancerRemovalTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;

/**
 * Tests for the {@link ContainerLoadBalancerRemovalTaskService} class.
 */
public class ContainerLoadBalancerRemovalTaskServiceTest extends ContainerLoadBalancerBaseTest {

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        createContainerLoadBalancerDescription(UUID.randomUUID().toString());
        createContainerLoadBalancerState(loadBalancerDesc.documentSelfLink);
    }

    @Test
    public void testRemovalTaskServiceLifeCycle() throws Throwable {
        ContainerLoadBalancerRemovalTaskState removalTask = createLoadBalancerRemovalTask(
                loadBalancerState.documentSelfLink);

        remove(removalTask, false);

        ContainerLoadBalancerState document = getDocumentNoWait(ContainerLoadBalancerState.class,
                loadBalancerState.documentSelfLink);
        assertNull(document);
    }

    private ContainerLoadBalancerRemovalTaskState createLoadBalancerRemovalTask(String loadBalancerLink) {
        ContainerLoadBalancerRemovalTaskState removalTask = new
                ContainerLoadBalancerRemovalTaskState();
        removalTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        removalTask.customProperties = new HashMap<>();
        removalTask.resourceLinks = new HashSet<>();
        removalTask.resourceLinks.add(loadBalancerLink);
        return removalTask;
    }

    private ContainerLoadBalancerRemovalTaskState remove(ContainerLoadBalancerRemovalTaskState
                                                                 removalTask,
                                                         boolean shouldFail) throws Throwable {
        removalTask = startTask(removalTask);
        host.log("Start removal test: " + removalTask.documentSelfLink);

        removalTask = shouldFail ?
                waitForTaskError(removalTask.documentSelfLink,
                        ContainerLoadBalancerRemovalTaskState.class) :
                waitForTaskSuccess(removalTask.documentSelfLink,
                        ContainerLoadBalancerRemovalTaskState.class);

        return removalTask;
    }

    private ContainerLoadBalancerRemovalTaskState startTask(ContainerLoadBalancerRemovalTaskState
                                                                    removalTask)
            throws Throwable {
        ContainerLoadBalancerRemovalTaskState outRemovalTask = doPost(removalTask,
                ContainerLoadBalancerRemovalTaskService.FACTORY_LINK);
        assertNotNull(outRemovalTask);
        return outRemovalTask;
    }


}
