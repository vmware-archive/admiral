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

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.LoadBalancerProvisionTaskService.LoadBalancerProvisionTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.photon.controller.model.resources.LoadBalancerService;

/**
 * Tests for the {@link LoadBalancerProvisionTaskService} class.
 */
public class LoadBalancerProvisionTaskServiceTest extends RequestBaseTest {

    private LoadBalancerDescription loadBalancerDesc;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();

        // setup Load Balancer description
        createLoadBalancerDescription(UUID.randomUUID().toString());
    }

    @Test
    public void testProvisionTaskServiceLifeCycle() throws Throwable {
        // TODO: enhance once the task implementation is complete
        LoadBalancerProvisionTaskState provisionTask = createLoadBalancerProvisionTask(
                loadBalancerDesc.documentSelfLink);
        provisionTask = provision(provisionTask);
    }

    private LoadBalancerProvisionTaskState createLoadBalancerProvisionTask(
            String loadBalancerDescLink) {
        LoadBalancerProvisionTaskState provisionTask = new LoadBalancerProvisionTaskState();
        provisionTask.resourceLinks = Collections
                .singleton(LoadBalancerService.FACTORY_LINK + "/dummy-lb");
        provisionTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        provisionTask.customProperties = new HashMap<>();
        return provisionTask;
    }

    private LoadBalancerProvisionTaskState provision(
            LoadBalancerProvisionTaskState provisionTask)
            throws Throwable {
        provisionTask = startProvisionTask(provisionTask);
        host.log("Start provision test: " + provisionTask.documentSelfLink);

        provisionTask = waitForTaskSuccess(provisionTask.documentSelfLink,
                LoadBalancerProvisionTaskState.class);

        return provisionTask;
    }

    private LoadBalancerProvisionTaskState startProvisionTask(
            LoadBalancerProvisionTaskState provisionTask) throws Throwable {
        LoadBalancerProvisionTaskState outProvisionTask = doPost(
                provisionTask, LoadBalancerProvisionTaskService.FACTORY_LINK);
        assertNotNull(outProvisionTask);
        return outProvisionTask;
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
