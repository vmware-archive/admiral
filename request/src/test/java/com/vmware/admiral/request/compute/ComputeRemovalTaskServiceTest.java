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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.ComputeRemovalTaskService.ComputeRemovalTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class ComputeRemovalTaskServiceTest extends RequestBaseTest {
    private RequestBrokerState request;

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
        DeploymentProfileConfig.getInstance().setTest(true);

        // create a single powered-on compute available for placement
        createVmGuestCompute(true);

        request = TestRequestStateFactory.createRequestState(ResourceType.COMPUTE_TYPE.getName(),
                hostDesc.documentSelfLink);
        request.tenantLinks = computeGroupPlacementState.tenantLinks;
        request.resourceCount = 1;
    }

    @After
    public void tearDown() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(false);
    }

    @Test
    public void testComputeRemovalResourceOperationCycleAfterAllocation() throws Throwable {
        request.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST, "true");

        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);

        // verify the resources are created as expected:
        assertEquals(request.resourceCount, request.resourceLinks.size());
        List<String> computeStateLinks = findResourceLinks(ComputeState.class,
                request.resourceLinks);

        // create a host removal task
        ComputeRemovalTaskState state = new ComputeRemovalTaskState();
        state.resourceLinks = request.resourceLinks;
        state = doPost(state, ComputeRemovalTaskService.FACTORY_LINK);

        assertNotNull("task is null", state);
        String taskSelfLink = state.documentSelfLink;
        assertNotNull("task self link is missing", taskSelfLink);

        waitForTaskSuccess(taskSelfLink, ComputeRemovalTaskState.class);

        // verify that the compute states were removed
        computeStateLinks = findResourceLinks(ComputeState.class, computeStateLinks);
        assertTrue("ComputeState not removed: " + computeStateLinks,
                computeStateLinks.isEmpty());
    }

    @Test
    public void testRequestBrokerComputeRemovalResourceOperationCycleAfterAllocation()
            throws Throwable {
        request.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST, "true");
        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);

        // verify the resources are created as expected:
        assertEquals(request.resourceCount, request.resourceLinks.size());
        List<String> computeStateLinks = findResourceLinks(ComputeState.class,
                request.resourceLinks);

        assertNotNull("ComputeStates were not allocated", computeStateLinks);
        assertEquals(request.resourceCount, computeStateLinks.size());

        // create a host removal task - RequestBroker
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.COMPUTE_TYPE.getName();
        request.resourceLinks = computeStateLinks;
        request.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;

        request = startRequest(request);
        waitForRequestToComplete(request);

        // verify that the compute states were removed
        computeStateLinks = findResourceLinks(ContainerState.class, computeStateLinks);
        assertTrue("ComputeStates not removed: " + computeStateLinks,
                computeStateLinks.isEmpty());
    }

    @Test
    public void testRequestBrokerComputeRemovalResourceOperationCycleAfterProvision()
            throws Throwable {
        request.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST, "true");
        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);

        // verify the resources are created as expected:
        assertEquals(request.resourceCount, request.resourceLinks.size());
        List<String> computeStateLinks = findResourceLinks(ComputeState.class,
                request.resourceLinks);

        assertNotNull("ComputeStates were not allocated", computeStateLinks);
        assertEquals(request.resourceCount, computeStateLinks.size());

        // provision compute task - RequestBroker
        RequestBrokerState provisionRequest = new RequestBrokerState();
        provisionRequest.resourceType = ResourceType.COMPUTE_TYPE.getName();
        provisionRequest.resourceLinks = computeStateLinks;
        provisionRequest.operation = ContainerOperationType.CREATE.id;

        provisionRequest = startRequest(provisionRequest);

        waitForRequestToComplete(provisionRequest);

        // verify that the compute states were created
        computeStateLinks = findResourceLinks(ComputeState.class, computeStateLinks);
        assertEquals("ComputeStates were not provisioned: " + computeStateLinks,
                provisionRequest.resourceLinks.size(),
                computeStateLinks.size());

        // remove compute states
        RequestBrokerState removeRequest = new RequestBrokerState();
        removeRequest.resourceType = ResourceType.COMPUTE_TYPE.getName();
        removeRequest.resourceLinks = computeStateLinks;
        removeRequest.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;

        removeRequest = startRequest(removeRequest);

        waitForRequestToComplete(removeRequest);

        // verify that the compute states were removed
        computeStateLinks = findResourceLinks(ComputeState.class, computeStateLinks);
        assertTrue("ComputeStates not removed: " + computeStateLinks,
                computeStateLinks.isEmpty());
    }

    @Test
    public void testRequestBrokerComputeRemovalWithContainerHostResourceOperationCycleAfterAllocation()
            throws Throwable {
        request.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST, "true");
        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);

        // verify the resources are created as expected:
        assertEquals(request.resourceCount, request.resourceLinks.size());
        List<String> computeStateLinks = findResourceLinks(ComputeState.class,
                request.resourceLinks);

        assertNotNull("ComputeStates were not allocated", computeStateLinks);
        assertEquals(request.resourceCount, computeStateLinks.size());

        // create a host removal task - RequestBroker
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.COMPUTE_TYPE.getName();
        request.resourceLinks = computeStateLinks;
        request.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;

        request = startRequest(request);

        waitForRequestToComplete(request);

        // verify that the compute states were removed
        computeStateLinks = findResourceLinks(ContainerState.class, computeStateLinks);
        assertTrue("ComputeStates not removed: " + computeStateLinks,
                computeStateLinks.isEmpty());

    }
}
