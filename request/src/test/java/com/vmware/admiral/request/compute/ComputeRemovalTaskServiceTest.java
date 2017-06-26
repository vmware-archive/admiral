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

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.ComputeRemovalTaskService.ComputeRemovalTaskState;
import com.vmware.admiral.request.compute.ComputeRemovalTaskService.ComputeRemovalTaskState.SubStage;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.test.TestContext;

public class ComputeRemovalTaskServiceTest extends ComputeRequestBaseTest {
    private RequestBrokerState request;


    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();

        ComputeDescription cd = createVMComputeDescription(false);
        request = TestRequestStateFactory.createRequestState(ResourceType.COMPUTE_TYPE.getName(),
                cd.documentSelfLink);
        request.tenantLinks = computeGroupPlacementState.tenantLinks;
        request.resourceCount = 1;
    }

    @After
    public void tearDown() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(false);
    }

    @Test
    public void testComputeRemovalResourceOperationCycleAfterAllocation() throws Throwable {
        // compute states after compute allocation request
        List<String> computeStateLinks = createComputeAllocationRequest();

        // create a compute removal task
        ComputeRemovalTaskState state = new ComputeRemovalTaskState();
        state.resourceLinks = request.resourceLinks;
        state = doPost(state, ComputeRemovalTaskService.FACTORY_LINK);

        assertNotNull("task is null", state);
        String taskSelfLink = state.documentSelfLink;
        assertNotNull("task self link is missing", taskSelfLink);

        // verify that lifecycleState is set to SUSPEND
        assertNotNull("state.resourceLinks is missing", state.resourceLinks);
        state.resourceLinks.stream().forEach(csLink -> {
            try {
                waitForPropertyValue(csLink, ComputeState.class,
                        ComputeState.FIELD_NAME_LIFECYCLE_STATE, LifecycleState.SUSPEND);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });

        waitForTaskSuccess(taskSelfLink, ComputeRemovalTaskState.class);

        // verify that the compute states were removed
        computeStateLinks = findResourceLinks(ComputeState.class, computeStateLinks);
        assertTrue("ComputeState not removed: " + computeStateLinks,
                computeStateLinks.isEmpty());
    }

    @Test
    public void testComputeRemovalResourceOperationCycleAfterAllocationShouldFail() throws Throwable {
        // compute states after compute allocation request
        createComputeAllocationRequest();

        Set<String> extendedResourceLink = new HashSet<>(request.resourceLinks);
        extendedResourceLink.add("missing compute link");

        // create a compute removal task with missing compute link
        ComputeRemovalTaskState state = new ComputeRemovalTaskState();
        state.resourceLinks = extendedResourceLink;
        state = doPost(state, ComputeRemovalTaskService.FACTORY_LINK);

        assertNotNull("task is null", state);
        String taskSelfLink = state.documentSelfLink;
        assertNotNull("task self link is missing", taskSelfLink);

        waitForTaskError(taskSelfLink, ComputeRemovalTaskState.class);
    }

    @Test
    public void testComputeRemovalResourceOperationCycleAfterAllocationShouldCompleteOnDeallocationRequest() throws Throwable {
        // compute states after compute allocation request
        List<String> computeStateLinks = createComputeAllocationRequest();

        Set<String> extendedResourceLink = new HashSet<>(request.resourceLinks);
        extendedResourceLink.add("missing compute link");

        // create a compute removal task with missing compute link
        ComputeRemovalTaskState state = new ComputeRemovalTaskState();
        state.resourceLinks = extendedResourceLink;
        Map<String, String> customProps = new HashMap<>();
        customProps.put(RequestUtils.FIELD_NAME_DEALLOCATION_REQUEST, Boolean.TRUE.toString());
        state.customProperties = customProps;

        state = doPost(state, ComputeRemovalTaskService.FACTORY_LINK);

        assertNotNull("task is null", state);
        String taskSelfLink = state.documentSelfLink;
        assertNotNull("task self link is missing", taskSelfLink);

        waitForTaskSuccess(taskSelfLink, ComputeRemovalTaskState.class);

        // verify that the compute states were not removed
        computeStateLinks = findResourceLinks(ComputeState.class, computeStateLinks);
        assertTrue("ComputeState is removed: " + computeStateLinks,
                !computeStateLinks.isEmpty());
    }

    @Test
    public void testRequestBrokerComputeRemovalResourceOperationCycleAfterAllocation()
            throws Throwable {
        // compute states after compute allocation request
        List<String> computeStateLinks = createComputeAllocationRequest();

        assertNotNull("ComputeStates were not allocated", computeStateLinks);
        assertEquals(request.resourceCount, computeStateLinks.size());

        // create a compute removal task - RequestBroker
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.COMPUTE_TYPE.getName();
        request.resourceLinks = new HashSet<>(computeStateLinks);
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
        // compute states after compute allocation request
        List<String> computeStateLinks = createComputeAllocationRequest();

        assertNotNull("ComputeStates were not allocated", computeStateLinks);
        assertEquals(request.resourceCount, computeStateLinks.size());

        // provision compute task - RequestBroker
        RequestBrokerState provisionRequest = new RequestBrokerState();
        provisionRequest.resourceType = ResourceType.COMPUTE_TYPE.getName();
        provisionRequest.resourceLinks = new HashSet<>(computeStateLinks);
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
        removeRequest.resourceLinks = new HashSet<>(computeStateLinks);
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
        // compute states after compute allocation request
        List<String> computeStateLinks = createComputeAllocationRequest();

        assertNotNull("ComputeStates were not allocated", computeStateLinks);
        assertEquals(request.resourceCount, computeStateLinks.size());

        // create a compute removal task - RequestBroker
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.COMPUTE_TYPE.getName();
        request.resourceLinks = new HashSet<>(computeStateLinks);
        request.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;

        request = startRequest(request);

        waitForRequestToComplete(request);

        // verify that the compute states were removed
        computeStateLinks = findResourceLinks(ContainerState.class, computeStateLinks);
        assertTrue("ComputeStates not removed: " + computeStateLinks,
                computeStateLinks.isEmpty());

    }

    @Test
    public void testEnhanceExtensibilityResponse() throws Throwable {
        final String customPropName = "CustomProp";

        // compute states after compute allocation request
        List<String> computeStateLinks = createComputeAllocationRequest();

        ComputeRemovalTaskService service = new ComputeRemovalTaskService();
        service.setHost(host);

        // create a compute removal task with missing compute link
        ComputeRemovalTaskState state = new ComputeRemovalTaskState();
        state.customProperties = new HashMap<>();
        state.customProperties.put(customPropName, "no");
        state.customProperties.put(RequestUtils.FIELD_NAME_DEALLOCATION_REQUEST, Boolean.TRUE.toString());
        state.resourceLinks = request.resourceLinks;
        state.taskInfo = TaskState.createAsFinished();
        state.taskSubStage = SubStage.COMPLETED;

        state = doPost(state, ComputeRemovalTaskService.FACTORY_LINK);

        assertNotNull("task is null", state);
        String taskSelfLink = state.documentSelfLink;
        assertNotNull("task self link is missing", taskSelfLink);

        waitForTaskSuccess(taskSelfLink, ComputeRemovalTaskState.class);
        String taskLink = state.documentSelfLink;

        ComputeRemovalTaskService.ExtensibilityCallbackResponse payload =
                (ComputeRemovalTaskService.ExtensibilityCallbackResponse) service
                        .notificationPayload();

        payload.customProperties = new HashMap<>();
        payload.customProperties.put(customPropName, "yes");

        TestContext context = new TestContext(1, Duration.ofMinutes(1));

        service.enhanceExtensibilityResponse(state, payload, () -> {
            try {
                ComputeState cs = getDocument(ComputeState.class,
                        computeStateLinks.get(0));

                Map<String, String> props = cs.customProperties;
                assertTrue("Expected property not found", cs.customProperties.containsKey
                        (customPropName));

                assertTrue("The property was not updated properly",
                        cs.customProperties.get(customPropName).equalsIgnoreCase("yes"));

            } catch (Throwable t) {
                context.failIteration(t);
                return;
            }
            context.completeIteration();
        });

        context.await();
    }

    private List<String> createComputeAllocationRequest() throws Throwable {
        request.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST, "true");

        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);

        // verify the resources are created as expected:
        assertEquals(request.resourceCount, request.resourceLinks.size());
        List<String> computeStateLinks = findResourceLinks(ComputeState.class,
                request.resourceLinks);

        return computeStateLinks;
    }
}
