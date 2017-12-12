/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.request.ClusteringTaskService.ClusteringTaskState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class ContainerClusteringTaskServiceTest extends RequestBaseTest {

    private RequestBrokerState request;

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        request = TestRequestStateFactory.createRequestState();
        request.resourceDescriptionLink = containerDesc.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        request.resourceCount = 3;
    }

    @Test
    public void testContainerClusteringTaskServiceIncrementByOne()
            throws Throwable {

        List<String> hostLinks = new ArrayList<>();
        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        // Set up a ContainerDescription with _cluster > 0
        ContainerDescriptionService.ContainerDescription clustered = TestRequestStateFactory
                .createContainerDescription("clustered");
        clustered._cluster = 2;
        clustered.portBindings = null;
        clustered.documentSelfLink = UUID.randomUUID().toString();
        clustered = doPost(clustered, ContainerDescriptionService.FACTORY_LINK);

        request.resourceDescriptionLink = clustered.documentSelfLink;
        request.resourceCount = 1;

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        long containersNumberBeforeClustering = getExistingContainersInAdapter().size();

        // Number of containers before provisioning.
        assertEquals(2, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers. Set resource count to be 1+ the number
        // of resources in the cluster
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        int desiredResourceCount = clustered._cluster + 1;
        day2OperationClustering.resourceCount = desiredResourceCount;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to clustered containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ClusteringTaskState.class);

        day2OperationClustering = waitForRequestToComplete(day2OperationClustering);
        assertNotNull(day2OperationClustering.resourceLinks);
        Set<String> allContainerLinks = new HashSet<>();
        allContainerLinks.addAll(initialState.resourceLinks);
        allContainerLinks.addAll(day2OperationClustering.resourceLinks);

        for (String containerLink : allContainerLinks) {
            ContainerState containerState = getDocument(ContainerState.class, containerLink);
            // containers not provisioned from template do not have component link
            assertNull(containerState.compositeComponentLink);
        }

        long containersNumberAfterClustering = getExistingContainersInAdapter().size();
        assertEquals(desiredResourceCount /* 3 */, containersNumberAfterClustering);

        // delete created stuff
        doDelete(UriUtils.buildUri(host, clustered.documentSelfLink), false);
        hostLinks.forEach(link -> {
            try {
                doDelete(UriUtils.buildUri(host, link), false);
            } catch (Throwable throwable) {
            }
        });
    }

    @Test
    public void testContainerClusteringSameResourceCount()
            throws Throwable {

        List<String> hostLinks = new ArrayList<>();
        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        // Set up a ContainerDescription with _cluster > 0
        ContainerDescriptionService.ContainerDescription clustered = TestRequestStateFactory
                .createContainerDescription("clustered");
        clustered._cluster = 2;
        clustered.portBindings = null;
        clustered.documentSelfLink = UUID.randomUUID().toString();
        clustered = doPost(clustered, ContainerDescriptionService.FACTORY_LINK);

        request.resourceDescriptionLink = clustered.documentSelfLink;
        request.resourceCount = 1;

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        long containersNumberBeforeClustering = getExistingContainersInAdapter().size();

        // Number of containers before provisioning.
        assertEquals(2, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers. Set resource count to be 1+ the number
        // of resources in the cluster
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        int desiredResourceCount = clustered._cluster;
        day2OperationClustering.resourceCount = desiredResourceCount;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to clustered containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ClusteringTaskState.class);

        waitForRequestToComplete(day2OperationClustering);

        long containersNumberAfterClustering = getExistingContainersInAdapter().size();
        assertEquals(desiredResourceCount /* 2 */, containersNumberAfterClustering);

        // delete created stuff
        doDelete(UriUtils.buildUri(host, clustered.documentSelfLink), false);
        hostLinks.forEach(link -> {
            try {
                doDelete(UriUtils.buildUri(host, link), false);
            } catch (Throwable throwable) {
            }
        });
    }

    // Jira issue VSYM-1170
    @Test
    public void testContainerClusteringTaskServiceIncrementByOneWithDisabledHost()
            throws Throwable {
        String hostSelfLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK,
                CommonTestStateFactory.DOCKER_COMPUTE_ID);

        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        // Set up a ContainerDescription with _cluster > 0
        ContainerDescriptionService.ContainerDescription clustered = TestRequestStateFactory
                .createContainerDescription("clustered");
        clustered._cluster = 2;
        clustered.portBindings = null;
        clustered.documentSelfLink = UUID.randomUUID().toString();
        clustered = doPost(clustered, ContainerDescriptionService.FACTORY_LINK);

        request.resourceDescriptionLink = clustered.documentSelfLink;
        request.resourceCount = 1;

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        long containersNumberBeforeClustering = getExistingContainersInAdapter().size();

        // Number of containers before provisioning.
        assertEquals(2, containersNumberBeforeClustering);
        waitFor(() -> {
            ComputeState cs = getDocument(ComputeState.class, hostSelfLink);
            return ((cs.customProperties.get("__Containers") != null) &&
                    ( Integer.valueOf(cs.customProperties.get("__Containers")) == 2));
        });

        // disable the container hosts in order to fail the operation
        ComputeState state = new ComputeState();
        state.powerState = PowerState.SUSPEND;

        doOperation(state,
                UriUtils.buildUri(host, hostSelfLink), false,
                Action.PATCH);
        waitFor(() -> {
            ComputeState cs = getDocument(ComputeState.class, hostSelfLink);
            return PowerState.SUSPEND.equals(cs.powerState);
        });

        // Create Day 2 operation for clustering containers. Set resource count to be 1+ the number
        // of resources in the cluster
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        int desiredResourceCount = clustered._cluster + 1;
        day2OperationClustering.resourceCount = desiredResourceCount;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to clustered containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskError(containerClusteringTaskLink, ClusteringTaskState.class);

        // container should not be deleted if the operation fails
        long containersNumberAfterClustering = getExistingContainersInAdapter().size();
        assertEquals(desiredResourceCount - 1, containersNumberAfterClustering);

        // delete created stuff
        doDelete(UriUtils.buildUri(host, clustered.documentSelfLink), false);
        doDelete(UriUtils.buildUri(host, hostSelfLink), false);
    }

    @Test
    public void testContainerClusteringTaskServiceIncrementByTwo()
            throws Throwable {

        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        // Set up a ContainerDescription with _cluster > 0
        ContainerDescriptionService.ContainerDescription clustered = TestRequestStateFactory
                .createContainerDescription("clustered");
        clustered._cluster = 2;
        clustered.portBindings = null;
        clustered.documentSelfLink = UUID.randomUUID().toString();
        clustered = doPost(clustered, ContainerDescriptionService.FACTORY_LINK);

        request.resourceDescriptionLink = clustered.documentSelfLink;
        request.resourceCount = 1;

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        long containersNumberBeforeClustering = getExistingContainersInAdapter().size();

        // Number of containers before provisioning.
        assertEquals(2, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers. Set resource count to be 1+ the number
        // of resources in the cluster
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        int desiredResourceCount = clustered._cluster + 2;
        day2OperationClustering.resourceCount = desiredResourceCount;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to clustered containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ClusteringTaskState.class);

        waitForRequestToComplete(day2OperationClustering);

        long containersNumberAfterClustering = getExistingContainersInAdapter().size();
        assertEquals(desiredResourceCount /* 3 */, containersNumberAfterClustering);

        // delete created stuff
        doDelete(UriUtils.buildUri(host, clustered.documentSelfLink), false);
    }

    @Test
    public void testContainerClusteringTaskServiceAddContainers() throws Throwable {

        // Add some hosts
        List<String> hostLinks = new ArrayList<>();
        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        Set<ContainerState> containersBeforeClustering = getExistingContainersInAdapter();
        Set<String> containersIdsBeforeClustering = containersBeforeClustering.stream()
                .map(cs -> cs.id).collect(Collectors.toSet());
        long containersNumberBeforeClustering = containersBeforeClustering.size();

        // Number of containers before provisioning.
        assertEquals(3, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers.
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        // Set 'resourceCount' to 5, which is 2 more than initial resources. This means that 2 new
        // containers should be provisioned.
        day2OperationClustering.resourceCount = 5;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to clustered containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ClusteringTaskState.class);

        waitForRequestToComplete(day2OperationClustering);

        Set<ContainerState> containersAfterClustering = getExistingContainersInAdapter();
        long containersNumberAfterClustering = containersAfterClustering.size();
        // Number of containers after clustering, should be increased with 7.
        assertEquals(5, containersNumberAfterClustering);

        Set<String> containersIdsAfterClustering = containersAfterClustering.stream()
                .map(cs -> cs.id).collect(Collectors.toSet());

        assertTrue(containersIdsAfterClustering.containsAll(containersIdsBeforeClustering));

        GroupResourcePlacementService.GroupResourcePlacementPoolState placementState = getDocument(
                GroupResourcePlacementService.GroupResourcePlacementPoolState.class,
                groupPlacementState.documentSelfLink);

        assertEquals(5, placementState.availableInstancesCount);
        assertEquals(5, placementState.allocatedInstancesCount);
    }

    @Test
    public void testContainerClusteringTaskAddContainersServiceInsufficientPlacement()
            throws Throwable {

        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        Set<ContainerState> containersBeforeClustering = getExistingContainersInAdapter();
        Set<String> containersIdsBeforeClustering = containersBeforeClustering.stream()
                .map(cs -> cs.id).collect(Collectors.toSet());
        long containersNumberBeforeClustering = containersBeforeClustering.size();

        // Number of containers before provisioning.
        assertEquals(3, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers.
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        // Set 'resourceCount' to 10, which is 7 more than initial resources. This means that 7 new
        // containers should be provisioned.
        day2OperationClustering.resourceCount = 30; // placement size is 10
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to cluster containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskError(containerClusteringTaskLink, ClusteringTaskState.class);

        long containersNumberAfterClustering = getExistingContainersInAdapter().size();
        // Number of containers after clustering, should be the same.
        assertEquals(3, containersNumberAfterClustering);

        GroupResourcePlacementService.GroupResourcePlacementPoolState placementState = getDocument(
                GroupResourcePlacementService.GroupResourcePlacementPoolState.class,
                groupPlacementState.documentSelfLink);

        assertEquals(7, placementState.availableInstancesCount);
        assertEquals(3, placementState.allocatedInstancesCount);

        Set<ContainerState> containersAfterClustering = getExistingContainersInAdapter();
        Set<String> containersIdsAfterClustering = containersAfterClustering.stream()
                .map(cs -> cs.id).collect(Collectors.toSet());

        assertTrue(containersIdsAfterClustering.containsAll(containersIdsBeforeClustering));
    }

    @Test
    public void testContainerClusteringTaskAddContainersServiceRemove()
            throws Throwable {

        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        long containersNumberBeforeClustering = getExistingContainersInAdapter().size();

        // Number of containers before provisioning.
        assertEquals(3, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers.
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        // Set 'resourceCount' to 10, which is 7 more than initial resources. This means that 7 new
        // containers should be provisioned.
        day2OperationClustering.resourceCount = 2; // placement size is 10
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to cluster containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ClusteringTaskState.class);

        long containersNumberAfterClustering = getExistingContainersInAdapter().size();
        // Number of containers after clustering, should be the same.
        assertEquals(2, containersNumberAfterClustering);

        GroupResourcePlacementService.GroupResourcePlacementPoolState placementState = getDocument(
                GroupResourcePlacementService.GroupResourcePlacementPoolState.class,
                groupPlacementState.documentSelfLink);

        assertEquals(8, placementState.availableInstancesCount);
        assertEquals(2, placementState.allocatedInstancesCount);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testClusteringTaskServiceValidateStateOnStartNegative() throws Throwable {
        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        long containersNumberBeforeClustering = getExistingContainersInAdapter().size();

        // Number of containers before provisioning.
        assertEquals(3, containersNumberBeforeClustering);

        ClusteringTaskState clusteringState = new ClusteringTaskState();
        clusteringState.resourceCount = -2;
        clusteringState.resourceDescriptionLink = initialState.resourceDescriptionLink;
        clusteringState.resourceType = ResourceType.CONTAINER_TYPE.getName();
        try {
            doPost(clusteringState, ClusteringTaskService.FACTORY_LINK);
        } catch (LocalizableValidationException e) {
            if (e.getMessage().contains("'resourceCount' must be greater than 0.")) {
                throw e;
            }
        }
        fail("Should fail with: 'resourceCount' must be greater than 0.");
    }

    @Test
    public void testContainerClusteringTaskWithSystemContainer() throws Throwable {
        long containersNumberBeforeClustering = getExistingContainersInAdapter().size();
        ContainerState container = TestRequestStateFactory.createContainer();
        container.descriptionLink = containerDesc.documentSelfLink;
        container.adapterManagementReference = containerDesc.instanceAdapterReference;
        container.groupResourcePlacementLink = groupPlacementState.documentSelfLink;
        container.system = Boolean.TRUE;
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        List<String> resourceLinks = new ArrayList<>();
        resourceLinks.add(container.documentSelfLink);
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = request.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        day2OperationClustering.resourceCount = 10;

        day2OperationClustering = startRequest(day2OperationClustering);
        RequestBrokerState clusteringRequest = waitForRequestToFail(day2OperationClustering);

        assertEquals("It should not be possible: clustering task for system container",
                "Day2 operations are not supported for system container",
                clusteringRequest.taskInfo.failure.message);

        long containersNumberAfterClustering = getExistingContainersInAdapter().size();

        assertEquals("New containers should not be deployed!", containersNumberBeforeClustering,
                containersNumberAfterClustering);
    }

    @Test
    public void testContainerClusteringPlacementWhenAffinityRules() throws Throwable {

        // Add some hosts
        List<String> hostLinks = new ArrayList<>();
        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        // Create a composite description with two containers. name2 has affinity to name1
        ContainerDescriptionService.ContainerDescription desc1 = TestRequestStateFactory
                .createContainerDescription("name1");
        desc1.portBindings = null;
        ContainerDescriptionService.ContainerDescription desc2 = TestRequestStateFactory
                .createContainerDescription("name2");
        desc2.portBindings = null;
        desc2.affinity = new String[] { desc1.name };

        CompositeDescriptionService.CompositeDescription compositeDesc = createCompositeDesc(desc1,
                desc2);

        request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        request.resourceDescriptionLink = compositeDesc.documentSelfLink;
        request.resourceCount = 1;

        request = startRequest(request);
        RequestBrokerState initialRequest = waitForRequestToComplete(request);

        String compositeComponentLink = initialRequest.resourceLinks.iterator().next();

        CompositeComponent cc = getDocument(CompositeComponent.class, compositeComponentLink);
        List<String> initialLinks = cc.componentLinks;

        compositeComponentLink = compositeComponentLink
                .substring(compositeComponentLink.lastIndexOf("/"));

        // increase name1 instances. The containers should be placed on the same host as all other
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = "/resources/container-descriptions/"
                + desc1.documentSelfLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        day2OperationClustering.resourceCount = 4;
        day2OperationClustering.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY,
                compositeComponentLink);

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to cluster containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        ClusteringTaskState clusteringTask = waitForTaskSuccess(
                containerClusteringTaskLink, ClusteringTaskState.class);
        assertNotNull(clusteringTask.resourceLinks);

        RequestBrokerState clusteringRequest = waitForRequestToComplete(day2OperationClustering);
        assertNotNull(clusteringRequest.resourceLinks);
        assertEquals(clusteringRequest.resourceLinks, clusteringTask.resourceLinks);

        Set<String> allContainerLinks = new HashSet<>();
        allContainerLinks.addAll(initialLinks);
        allContainerLinks.addAll(clusteringRequest.resourceLinks);

        Set<String> parentLinks = new HashSet<>();
        for (String containerLink : allContainerLinks) {
            ContainerState containerState = getDocument(ContainerState.class, containerLink);
            parentLinks.add(containerState.parentLink);
            assertNotNull(containerState.compositeComponentLink);
        }

        assertEquals("All containers should be on the same host", 1, parentLinks.size());
    }

    @Test
    public void testContainerClusteringPlacementWhenAntiAffinityRules() throws Throwable {

        // Add some hosts
        List<String> hostLinks = new ArrayList<>();
        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        // Create a composite description with two containers. name2 has anti affinity to name1
        ContainerDescriptionService.ContainerDescription desc1 = TestRequestStateFactory
                .createContainerDescription("name1");
        desc1.portBindings = null;
        ContainerDescriptionService.ContainerDescription desc2 = TestRequestStateFactory
                .createContainerDescription("name2");
        desc2.portBindings = null;
        desc2.affinity = new String[] {
                AffinityConstraint.AffinityConstraintType.ANTI_AFFINITY_PREFIX + desc1.name };

        CompositeDescriptionService.CompositeDescription compositeDesc = createCompositeDesc(desc1,
                desc2);

        request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        request.resourceDescriptionLink = compositeDesc.documentSelfLink;
        request.resourceCount = 1;

        request = startRequest(request);
        RequestBrokerState initialRequest = waitForRequestToComplete(request);

        String compositeComponentLink = initialRequest.resourceLinks.iterator().next();

        CompositeComponent cc = getDocument(CompositeComponent.class, compositeComponentLink);
        List<String> initialLinks = cc.componentLinks;

        compositeComponentLink = compositeComponentLink
                .substring(compositeComponentLink.lastIndexOf("/"));

        // increase name1 instances. The containers should not be placed on the same host as desc2
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = "/resources/container-descriptions/"
                + desc1.documentSelfLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        day2OperationClustering.resourceCount = 4;
        day2OperationClustering.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY,
                compositeComponentLink);

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to cluster containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        ClusteringTaskState clusteringTask = waitForTaskSuccess(
                containerClusteringTaskLink, ClusteringTaskState.class);
        assertNotNull(clusteringTask.resourceLinks);

        RequestBrokerState clusteringRequest = waitForRequestToComplete(day2OperationClustering);
        assertNotNull(clusteringRequest.resourceLinks);
        assertEquals(clusteringRequest.resourceLinks, clusteringTask.resourceLinks);

        Set<String> allContainerLinks = new HashSet<>();
        allContainerLinks.addAll(initialLinks);
        allContainerLinks.addAll(clusteringRequest.resourceLinks);

        Set<ContainerState> containerStates = new HashSet<>();
        for (String containerLink : allContainerLinks) {
            ContainerState containerState = getDocument(ContainerState.class, containerLink);
            containerStates.add(containerState);
            assertNotNull(containerState.compositeComponentLink);
        }

        Map<Boolean, List<ContainerState>> containers = containerStates.stream().collect(
                Collectors.partitioningBy(c -> c.descriptionLink.contains(desc2.documentSelfLink)));

        String desc2ParentLink = containers.get(Boolean.TRUE).get(0).parentLink;

        assertFalse("There should not be any container placed on " + desc2.name + " host.",
                containers.get(Boolean.FALSE).stream().map(c -> c.parentLink)
                        .anyMatch(p -> p.equals(desc2ParentLink)));
    }

    @Test
    public void testContainerClusteringPlacementWhenVolumesFrom() throws Throwable {

        // Add some hosts
        List<String> hostLinks = new ArrayList<>();
        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        hostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), (long) Integer.MAX_VALUE,
                true).documentSelfLink);

        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        // Create a composite description with two containers. name2 has affinity to name1
        ContainerDescriptionService.ContainerDescription desc1 = TestRequestStateFactory
                .createContainerDescription("name1");
        desc1.portBindings = null;
        ContainerDescriptionService.ContainerDescription desc2 = TestRequestStateFactory
                .createContainerDescription("name2");
        desc2.volumesFrom = new String[] { desc1.name };
        desc2.portBindings = null;

        CompositeDescriptionService.CompositeDescription compositeDesc = createCompositeDesc(desc1,
                desc2);

        request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        request.resourceDescriptionLink = compositeDesc.documentSelfLink;
        request.resourceCount = 1;

        request = startRequest(request);
        RequestBrokerState initialRequest = waitForRequestToComplete(request);

        String compositeComponentLink = initialRequest.resourceLinks.iterator().next();

        CompositeComponent cc = getDocument(CompositeComponent.class, compositeComponentLink);
        List<String> initialLinks = cc.componentLinks;

        compositeComponentLink = compositeComponentLink
                .substring(compositeComponentLink.lastIndexOf("/"));

        // increase name1 instances. The containers should be placed on the same host as all other
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = "/resources/container-descriptions/"
                + desc1.documentSelfLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        day2OperationClustering.resourceCount = 4;
        day2OperationClustering.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY,
                compositeComponentLink);

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to cluster containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        ClusteringTaskState clusteringTask = waitForTaskSuccess(
                containerClusteringTaskLink, ClusteringTaskState.class);
        assertNotNull(clusteringTask.resourceLinks);

        RequestBrokerState clusteringRequest = waitForRequestToComplete(day2OperationClustering);
        assertNotNull(clusteringRequest.resourceLinks);
        assertEquals(clusteringRequest.resourceLinks, clusteringTask.resourceLinks);

        Set<String> allContainerLinks = new HashSet<>();
        allContainerLinks.addAll(initialLinks);
        allContainerLinks.addAll(clusteringRequest.resourceLinks);

        Set<String> parentLinks = new HashSet<>();
        for (String containerLink : allContainerLinks) {
            ContainerState containerState = getDocument(ContainerState.class, containerLink);
            parentLinks.add(containerState.parentLink);
            assertNotNull(containerState.compositeComponentLink);
        }

        assertEquals("All containers should be on the same host", 1, parentLinks.size());
    }

    @Test
    public void testContainerClusteringPlacementClusteringAntiAffinity() throws Throwable {

        // Add some hosts
        List<String> hostLinks = new ArrayList<>();
        IntStream.range(0, 9).forEach(i -> {
            try {
                hostLinks.add(createDockerHost(
                        createDockerHostDescription(), createResourcePool(),
                        (long) Integer.MAX_VALUE, true).documentSelfLink);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        });

        long containersNumberBeforeProvisioning = getExistingContainersInAdapter().size();
        assertEquals(0, containersNumberBeforeProvisioning);

        ContainerDescriptionService.ContainerDescription desc1 = TestRequestStateFactory
                .createContainerDescriptionWithPortBindingsHostPortSet();
        desc1._cluster = 3;
        desc1 = doPost(desc1, ContainerDescriptionService.FACTORY_LINK);

        request.resourceCount = 1;
        request.resourceDescriptionLink = desc1.documentSelfLink;
        request = startRequest(request);
        RequestBrokerState initialRequest = waitForRequestToComplete(request);

        long containersNumberBeforeClustering = getExistingContainersInAdapter().size();

        // Number of containers before provisioning.
        assertEquals(3, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers.
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialRequest.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        // Set 'resourceCount' to 10, which is 7 more than initial resources. This means that 7 new
        // containers should be provisioned.
        day2OperationClustering.resourceCount = 10;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialRequest.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to clustered containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ClusteringTaskService.FACTORY_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        ClusteringTaskState clusteringTask = waitForTaskSuccess(
                containerClusteringTaskLink, ClusteringTaskState.class);
        assertNotNull(clusteringTask.resourceLinks);

        RequestBrokerState clusteringRequest = waitForRequestToComplete(day2OperationClustering);
        assertNotNull(clusteringRequest.resourceLinks);
        assertEquals(clusteringRequest.resourceLinks, clusteringTask.resourceLinks);

        long containersNumberAfterClustering = getExistingContainersInAdapter().size();
        // Number of containers after clustering, should be increased with 7.
        assertEquals(10, containersNumberAfterClustering);

        Set<String> allContainerLinks = new HashSet<>();
        allContainerLinks.addAll(initialRequest.resourceLinks);
        allContainerLinks.addAll(clusteringRequest.resourceLinks);

        assertEquals(10, allContainerLinks.size());

        Set<String> parentLinks = new HashSet<>();
        for (String containerLink : allContainerLinks) {
            ContainerState containerState = getDocument(ContainerState.class, containerLink);
            parentLinks.add(containerState.parentLink);
            assertNull(containerState.compositeComponentLink);
        }

        assertEquals("All containers should be on different hosts", 10, parentLinks.size());
    }
}
