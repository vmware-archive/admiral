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

import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.LoadBalancerProvisionTaskService.LoadBalancerProvisionTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSLoadBalancerService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for the {@link LoadBalancerProvisionTaskService} class.
 */
public class LoadBalancerProvisionTaskServiceTest extends RequestBaseTest {

    @Override
    public void setUp() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
        super.setUp();
    }

    @Test
    public void testProvisionTaskServiceLifeCycle() throws Throwable {
        // create prerequisites
        ComputeState compute = createVmComputeWithRandomComputeDescription(true, ComputeType.VM_GUEST);
        SubnetState subnet = createSubnetState(null);
        LoadBalancerDescription loadBalancerDesc = createLoadBalancerDescription(
                ComputeDescriptionService.FACTORY_LINK + "/comp-desc");
        LoadBalancerState loadBalancerState = createLoadBalancerState(
                loadBalancerDesc.documentSelfLink, compute.documentSelfLink, subnet.documentSelfLink);

        LoadBalancerProvisionTaskState provisionTask = createLoadBalancerProvisionTask(
                loadBalancerState.documentSelfLink);
        provisionTask = provision(provisionTask);
    }

    private LoadBalancerProvisionTaskState createLoadBalancerProvisionTask(String lbLink) {
        LoadBalancerProvisionTaskState provisionTask = new LoadBalancerProvisionTaskState();
        provisionTask.resourceLinks = Collections.singleton(lbLink);
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

    private LoadBalancerDescription createLoadBalancerDescription(String cdLink) throws Throwable {
        LoadBalancerDescription desc = TestRequestStateFactory
                .createLoadBalancerDescription(UUID.randomUUID().toString());
        desc.computeDescriptionLink = cdLink;

        return doPost(desc, LoadBalancerDescriptionService.FACTORY_LINK);
    }

    private LoadBalancerState createLoadBalancerState(String lbdLink, String computeLink,
            String subnetLink) throws Throwable {
        LoadBalancerState state = TestRequestStateFactory
                .createLoadBalancerState(UUID.randomUUID().toString());
        state.descriptionLink = lbdLink;
        state.endpointLink = this.endpoint.documentSelfLink;
        state.computeLinks = Collections.singleton(computeLink);
        state.subnetLinks = Collections.singleton(subnetLink);
        state.instanceAdapterReference = UriUtils.buildUri(host, AWSLoadBalancerService.SELF_LINK);

        return doPost(state, LoadBalancerService.FACTORY_LINK);
    }
}
