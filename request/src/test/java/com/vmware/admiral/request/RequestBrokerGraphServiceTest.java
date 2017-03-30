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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.RequestBrokerGraphService.RequestBrokerGraphResponse;
import com.vmware.admiral.request.RequestBrokerGraphService.TaskServiceDocumentHistory;
import com.vmware.admiral.request.RequestBrokerGraphService.TaskServiceStageWithLink;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState;
import com.vmware.admiral.request.composition.CompositionSubTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionSubTaskService.CompositionSubTaskState;
import com.vmware.admiral.request.composition.CompositionTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionTaskService.CompositionTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;

public class RequestBrokerGraphServiceTest extends RequestBaseTest {

    @Test
    public void testRequestLifeCycle() throws Throwable {
        host.log("########  Start of testRequestLifeCycle ######## ");
        // setup Docker Host:
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        createDockerHost(dockerHostDesc, resourcePool);

        // setup Container desc:
        ContainerDescription containerDesc = createContainerDescription();

        // setup Group Placement:
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        // 1. Request a container instance:
        RequestBrokerState request = TestRequestStateFactory.createRequestState();
        request.resourceDescriptionLink = containerDesc.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // wait for request completed state:
        request = waitForRequestToComplete(request);

        RequestBrokerGraphResponse graph = getDocument(RequestBrokerGraphResponse.class,
                ManagementUriParts.REQUEST_GRAPH, RequestBrokerGraphService.QUERY_PARAM,
                extractId(request.documentSelfLink));
        assertNotNull(graph);
        assertNotNull(graph.tasks);

        TaskServiceDocumentHistory requestTask = graph.tasks.remove(0);
        assertTaskPassingStages(requestTask, RequestBrokerFactoryService.SELF_LINK,
                RequestBrokerState.SubStage.values());

        TaskServiceDocumentHistory reservationTask = graph.tasks.remove(0);
        assertTaskPassingStages(reservationTask, ReservationTaskFactoryService.SELF_LINK,
                ReservationTaskState.SubStage.values());

        TaskServiceDocumentHistory placementReservationTask = graph.tasks.remove(0);
        assertTaskPassingStages(placementReservationTask,
                PlacementHostSelectionTaskService.FACTORY_LINK,
                PlacementHostSelectionTaskState.SubStage.values());

        TaskServiceDocumentHistory allocationTask = graph.tasks.remove(0);
        assertTaskPassingStages(allocationTask, ContainerAllocationTaskFactoryService.SELF_LINK,
                ContainerAllocationTaskState.SubStage.values());

        TaskServiceDocumentHistory placementTask = graph.tasks.remove(0);
        assertTaskPassingStages(placementTask, PlacementHostSelectionTaskService.FACTORY_LINK,
                PlacementHostSelectionTaskState.SubStage.values());
    }

    @Test
    public void testCompositeComponentRequestLifeCycle() throws Throwable {
        host.log("########  Start of testCompositeCompositeRequestLifeCycle ######## ");
        // setup Docker Host:
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        createDockerHost(dockerHostDesc, resourcePool);

        // setup Composite description with 1 container
        CompositeDescription compositeDesc = createCompositeDesc(containerDesc);

        // setup Group Placement:
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        // 1. Request a composite container:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);
        request.tenantLinks = groupPlacementState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // wait for request completed state:
        request = waitForRequestToComplete(request);

        RequestBrokerGraphResponse graph = getDocument(RequestBrokerGraphResponse.class,
                ManagementUriParts.REQUEST_GRAPH, RequestBrokerGraphService.QUERY_PARAM,
                extractId(request.documentSelfLink));
        assertNotNull(graph);

        TaskServiceDocumentHistory compisiteRequestTask = graph.tasks.remove(0);
        assertTaskPassingStages(compisiteRequestTask, RequestBrokerFactoryService.SELF_LINK,
                RequestBrokerState.SubStage.values());

        TaskServiceDocumentHistory compositionTask = graph.tasks.remove(0);
        assertTaskPassingStages(compositionTask, CompositionTaskFactoryService.SELF_LINK,
                CompositionTaskState.SubStage.values());

        TaskServiceDocumentHistory compositionSubTask = graph.tasks.remove(0);
        assertTaskPassingStages(compositionSubTask,
                CompositionSubTaskFactoryService.SELF_LINK,
                CompositionSubTaskState.SubStage.values());

        TaskServiceDocumentHistory requestTask = graph.tasks.remove(0);
        assertTaskPassingStages(requestTask, RequestBrokerFactoryService.SELF_LINK,
                RequestBrokerState.SubStage.values());

        TaskServiceDocumentHistory reservationTask = graph.tasks.remove(0);
        assertTaskPassingStages(reservationTask, ReservationTaskFactoryService.SELF_LINK,
                ReservationTaskState.SubStage.values());

        TaskServiceDocumentHistory placementReservationTask = graph.tasks.remove(0);
        assertTaskPassingStages(placementReservationTask,
                PlacementHostSelectionTaskService.FACTORY_LINK,
                PlacementHostSelectionTaskState.SubStage.values());

        TaskServiceDocumentHistory allocationTask = graph.tasks.remove(0);
        assertTaskPassingStages(allocationTask, ContainerAllocationTaskFactoryService.SELF_LINK,
                ContainerAllocationTaskState.SubStage.values());

        TaskServiceDocumentHistory placementTask = graph.tasks.remove(0);
        assertTaskPassingStages(placementTask, PlacementHostSelectionTaskService.FACTORY_LINK,
                PlacementHostSelectionTaskState.SubStage.values());
    }

    private void assertTaskPassingStages(TaskServiceDocumentHistory task, String factoryLink,
            Enum<?>[] taskSubStages) {
        assertTrue(task.documentSelfLink.startsWith(factoryLink));
        assertFalse(task.stages.isEmpty());

        int currentStageOrdinal = 0;
        for (TaskServiceStageWithLink stage : task.stages) {
            Enum<?> subStage = getSubStage(taskSubStages, stage.taskSubStage);
            assertNotNull(subStage);
            assertTrue(currentStageOrdinal <= subStage.ordinal());
            currentStageOrdinal = subStage.ordinal();
        }
    }

    private static Enum<?> getSubStage(Enum<?>[] taskSubStages, Object taskSubStage) {
        for (Enum<?> stage : taskSubStages) {
            if (stage.name().equals(taskSubStage)) {
                return stage;
            }
        }

        return null;
    }
}
