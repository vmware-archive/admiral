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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.LoadBalancerAllocationTaskService.LoadBalancerAllocationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;

/**
 * Tests for the {@link LoadBalancerAllocationTaskService} class.
 */
public class LoadBalancerAllocationTaskServiceTest extends RequestBaseTest {

    @Test
    public void testAllocationTaskServiceLifeCycle() throws Throwable {
        // create prerequisites
        ComputeDescription computeDesc = createComputeDescription(true);
        List<ComputeState> computes = createComputes(computeDesc, 2);
        LoadBalancerDescription loadBalancerDesc = createLoadBalancerDescription(
                computeDesc.documentSelfLink);

        LoadBalancerAllocationTaskState allocationTask = createLoadBalancerAllocationTask(
                loadBalancerDesc.documentSelfLink);
        allocationTask = allocate(allocationTask);

        LoadBalancerState loadBalancerState = getDocument(LoadBalancerState.class,
                allocationTask.resourceLinks.iterator().next());

        assertNotNull(loadBalancerState);
        assertEquals(loadBalancerDesc.documentSelfLink, loadBalancerState.descriptionLink);
        assertEquals(loadBalancerDesc.name, loadBalancerState.name);
        assertEquals(
                computeDesc.customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME),
                loadBalancerState.endpointLink);
        assertEquals(computes.size(), loadBalancerState.computeLinks.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoEndpointDetails() throws Throwable {
        // create prerequisites
        ComputeDescription computeDesc = createComputeDescription(false);
        createComputes(computeDesc, 2);
        LoadBalancerDescription loadBalancerDesc = createLoadBalancerDescription(null);

        LoadBalancerAllocationTaskState allocationTask = createLoadBalancerAllocationTask(
                loadBalancerDesc.documentSelfLink);
        allocate(allocationTask);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoComputeDesc() throws Throwable {
        // create prerequisites
        LoadBalancerDescription loadBalancerDesc = createLoadBalancerDescription(null);

        LoadBalancerAllocationTaskState allocationTask = createLoadBalancerAllocationTask(
                loadBalancerDesc.documentSelfLink);
        allocate(allocationTask);
    }

    private LoadBalancerAllocationTaskState createLoadBalancerAllocationTask(
            String loadBalancerDescLink) {
        LoadBalancerAllocationTaskState allocationTask = new LoadBalancerAllocationTaskState();
        allocationTask.resourceDescriptionLink = loadBalancerDescLink;
        allocationTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        return allocationTask;
    }

    private LoadBalancerAllocationTaskState allocate(LoadBalancerAllocationTaskState allocationTask)
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

    private ComputeDescription createComputeDescription(boolean addEndpointDetails) throws Throwable {
        ComputeDescription cd = TestRequestStateFactory.createComputeDescriptionForVmGuestChildren();
        if (addEndpointDetails) {
            cd.customProperties.put(ComputeProperties.ENDPOINT_LINK_PROP_NAME, "endpointLink");
            cd.customProperties.put(ComputeConstants.CUSTOM_PROP_ENDPOINT_TYPE_NAME, "aws");
            cd.regionId = "region-1";
        }
        return doPost(cd, ComputeDescriptionService.FACTORY_LINK);
    }

    private List<ComputeState> createComputes(ComputeDescription cd, int count) throws Throwable {
        List<ComputeState> computes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            computes.add(createCompute(cd));
        }
        return computes;
    }

    private ComputeState createCompute(ComputeDescription cd) throws Throwable {
        ComputeState cs = TestRequestStateFactory.createVmHostComputeState();
        cs.descriptionLink = cd.documentSelfLink;
        cs.name = UUID.randomUUID().toString();
        return doPost(cs, ComputeService.FACTORY_LINK);
    }

    private LoadBalancerDescription createLoadBalancerDescription(String cdLink) throws Throwable {
        LoadBalancerDescription desc = TestRequestStateFactory
                .createLoadBalancerDescription(UUID.randomUUID().toString());
        desc.computeDescriptionLink = cdLink;

        return doPost(desc, LoadBalancerDescriptionService.FACTORY_LINK);
    }
}
