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

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePolicyService;
import com.vmware.admiral.request.ContainerClusteringTaskService.ContainerClusteringTaskState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class ContainerClusteringTaskServiceTest extends RequestBaseTest {

    private RequestBrokerState request;

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        request = TestRequestStateFactory.createRequestState();
        request.resourceDescriptionLink = containerDesc.documentSelfLink;
        request.tenantLinks = groupPolicyState.tenantLinks;
        request.resourceCount = 3;
        Map<String, String> customProp = new HashMap<>();
        customProp.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, "test");
        request.customProperties = customProp;
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

        long containersNumberBeforeProvisioning = MockDockerAdapterService.getNumberOfContainers();
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

        long containersNumberBeforeClustering = MockDockerAdapterService.getNumberOfContainers();

        // Number of containers before provisioning.
        assertEquals(2, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers. Set resource count to be 1+ the number
        // of resources in the cluster
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
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
                ContainerClusteringTaskFactoryService.SELF_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ContainerClusteringTaskState.class);

        waitForRequestToComplete(day2OperationClustering);

        long containersNumberAfterClustering = MockDockerAdapterService.getNumberOfContainers();
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

    // Jira issue VSYM-1170
    @Test
    public void testContainerClusteringTaskServiceIncrementByOneWithDisabledHost()
            throws Throwable {
        String hostSelfLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK,
                CommonTestStateFactory.DOCKER_COMPUTE_ID);

        long containersNumberBeforeProvisioning = MockDockerAdapterService.getNumberOfContainers();
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

        long containersNumberBeforeClustering = MockDockerAdapterService.getNumberOfContainers();

        // Number of containers before provisioning.
        assertEquals(2, containersNumberBeforeClustering);

        // disable the container hosts in order to fail the operation
        ComputeState state = new ComputeState();
        state.powerState = PowerState.SUSPEND;

        doOperation(state,
                UriUtilsExtended.buildUri(host, hostSelfLink), false,
                Action.PATCH);
        waitFor(() -> {
            ComputeState cs = getDocument(ComputeState.class, hostSelfLink);
            return PowerState.SUSPEND.equals(cs.powerState);
        });

        // Create Day 2 operation for clustering containers. Set resource count to be 1+ the number
        // of resources in the cluster
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
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
                ContainerClusteringTaskFactoryService.SELF_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskError(containerClusteringTaskLink, ContainerClusteringTaskState.class);

        // container should not be deleted if the operation fails
        long containersNumberAfterClustering = MockDockerAdapterService.getNumberOfContainers();
        assertEquals(desiredResourceCount - 1, containersNumberAfterClustering);

        // delete created stuff
        doDelete(UriUtils.buildUri(host, clustered.documentSelfLink), false);
        doDelete(UriUtils.buildUri(host, hostSelfLink), false);
    }

    @Test
    public void testContainerClusteringTaskServiceIncrementByTwo()
            throws Throwable {

        long containersNumberBeforeProvisioning = MockDockerAdapterService.getNumberOfContainers();
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

        long containersNumberBeforeClustering = MockDockerAdapterService.getNumberOfContainers();

        // Number of containers before provisioning.
        assertEquals(2, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers. Set resource count to be 1+ the number
        // of resources in the cluster
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
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
                ContainerClusteringTaskFactoryService.SELF_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ContainerClusteringTaskState.class);

        waitForRequestToComplete(day2OperationClustering);

        long containersNumberAfterClustering = MockDockerAdapterService.getNumberOfContainers();
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

        long containersNumberBeforeProvisioning = MockDockerAdapterService.getNumberOfContainers();
        assertEquals(0, containersNumberBeforeProvisioning);

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        long containersNumberBeforeClustering = MockDockerAdapterService.getNumberOfContainers();

        Set<String> containersIdsBeforeClustering = MockDockerAdapterService.getContainerIds();

        // Number of containers before provisioning.
        assertEquals(3, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers.
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
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
                ContainerClusteringTaskFactoryService.SELF_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ContainerClusteringTaskState.class);

        waitForRequestToComplete(day2OperationClustering);

        long containersNumberAfterClustering = MockDockerAdapterService.getNumberOfContainers();
        // Number of containers after clustering, should be increased with 7.
        assertEquals(5, containersNumberAfterClustering);

        Set<String> containersIdsAfterClustering = MockDockerAdapterService.getContainerIds();

        assertTrue(containersIdsAfterClustering.containsAll(containersIdsBeforeClustering));

        GroupResourcePolicyService.GroupResourcePolicyPoolState policyState = getDocument(
                GroupResourcePolicyService.GroupResourcePolicyPoolState.class,
                groupPolicyState.documentSelfLink);

        assertEquals(5, policyState.availableInstancesCount);
        assertEquals(5, policyState.allocatedInstancesCount);
    }

    @Test
    public void testContainerClusteringTaskAddContainersServiceInsufficientPolicy()
            throws Throwable {

        long containersNumberBeforeProvisioning = MockDockerAdapterService.getNumberOfContainers();
        assertEquals(0, containersNumberBeforeProvisioning);

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        long containersNumberBeforeClustering = MockDockerAdapterService.getNumberOfContainers();

        Set<String> containersIdsBeforeClustering = MockDockerAdapterService.getContainerIds();

        // Number of containers before provisioning.
        assertEquals(3, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers.
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        // Set 'resourceCount' to 10, which is 7 more than initial resources. This means that 7 new
        // containers should be provisioned.
        day2OperationClustering.resourceCount = 30; // policy size is 10
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to cluster containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ContainerClusteringTaskFactoryService.SELF_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskError(containerClusteringTaskLink, ContainerClusteringTaskState.class);

        long containersNumberAfterClustering = MockDockerAdapterService.getNumberOfContainers();
        // Number of containers after clustering, should be the same.
        assertEquals(3, containersNumberAfterClustering);

        GroupResourcePolicyService.GroupResourcePolicyPoolState policyState = getDocument(
                GroupResourcePolicyService.GroupResourcePolicyPoolState.class,
                groupPolicyState.documentSelfLink);

        assertEquals(7, policyState.availableInstancesCount);
        assertEquals(3, policyState.allocatedInstancesCount);

        Set<String> containersIdsAfterClustering = MockDockerAdapterService.getContainerIds();

        assertTrue(containersIdsAfterClustering.containsAll(containersIdsBeforeClustering));
    }

    @Test
    public void testContainerClusteringTaskAddContainersServiceRemove()
            throws Throwable {

        long containersNumberBeforeProvisioning = MockDockerAdapterService.getNumberOfContainers();
        assertEquals(0, containersNumberBeforeProvisioning);

        request = startRequest(request);
        RequestBrokerState initialState = waitForRequestToComplete(request);

        long containersNumberBeforeClustering = MockDockerAdapterService.getNumberOfContainers();

        // Number of containers before provisioning.
        assertEquals(3, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers.
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialState.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        // Set 'resourceCount' to 10, which is 7 more than initial resources. This means that 7 new
        // containers should be provisioned.
        day2OperationClustering.resourceCount = 2; // policy size is 10
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = initialState.customProperties;

        try {
            day2OperationClustering = startRequest(day2OperationClustering);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "The following exception appears while trying to cluster containers: %s", e));
        }

        String containerClusteringTaskLink = UriUtils.buildUriPath(
                ContainerClusteringTaskFactoryService.SELF_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        waitForTaskSuccess(containerClusteringTaskLink, ContainerClusteringTaskState.class);

        long containersNumberAfterClustering = MockDockerAdapterService.getNumberOfContainers();
        // Number of containers after clustering, should be the same.
        assertEquals(2, containersNumberAfterClustering);

        GroupResourcePolicyService.GroupResourcePolicyPoolState policyState = getDocument(
                GroupResourcePolicyService.GroupResourcePolicyPoolState.class,
                groupPolicyState.documentSelfLink);

        assertEquals(8, policyState.availableInstancesCount);
        assertEquals(2, policyState.allocatedInstancesCount);
    }

    @Test
    public void testContainerClusteringTaskWithSystemContainer() throws Throwable {
        long containersNumberBeforeClustering = MockDockerAdapterService.getNumberOfContainers();
        ContainerState container = TestRequestStateFactory.createContainer();
        container.descriptionLink = containerDesc.documentSelfLink;
        container.adapterManagementReference = containerDesc.instanceAdapterReference;
        container.groupResourcePolicyLink = groupPolicyState.documentSelfLink;
        container.system = Boolean.TRUE;
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        List<String> resourceLinks = new ArrayList<String>();
        resourceLinks.add(container.documentSelfLink);
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = request.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        day2OperationClustering.resourceCount = 10;

        day2OperationClustering = startRequest(day2OperationClustering);
        RequestBrokerState clusteringRequest = waitForRequestToFail(day2OperationClustering);

        assertEquals("It should not be possible: clustering task for system container",
                "Day2 operations are not supported for system container",
                clusteringRequest.taskInfo.failure.message);

        long containersNumberAfterClustering = MockDockerAdapterService.getNumberOfContainers();

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

        long containersNumberBeforeProvisioning = MockDockerAdapterService.getNumberOfContainers();
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

        MockDockerAdapterService.getContainerIds();

        String compositeComponentLink = initialRequest.resourceLinks.get(0);

        CompositeComponent cc = getDocument(CompositeComponent.class, compositeComponentLink);
        List<String> initialLinks = cc.componentLinks;

        compositeComponentLink = compositeComponentLink
                .substring(compositeComponentLink.lastIndexOf("/"));

        // increase name1 instances. The containers should be placed on the same host as all other
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = "/resources/container-descriptions/"
                + desc1.documentSelfLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
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
                ContainerClusteringTaskFactoryService.SELF_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        ContainerClusteringTaskState clusteringTask = waitForTaskSuccess(
                containerClusteringTaskLink, ContainerClusteringTaskState.class);
        assertNotNull(clusteringTask.resourceLinks);

        RequestBrokerState clusteringRequest = waitForRequestToComplete(day2OperationClustering);
        assertNotNull(clusteringRequest.resourceLinks);
        assertEquals(clusteringRequest.resourceLinks, clusteringTask.resourceLinks);

        Set<String> allContainerLinks = new HashSet<>();
        allContainerLinks.addAll(initialLinks);
        allContainerLinks.addAll(clusteringRequest.resourceLinks);

        Set<String> parentLinks = new HashSet<>();
        allContainerLinks.forEach(link -> {
            try {
                parentLinks.add(getDocument(ContainerState.class, link).parentLink);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        });

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

        long containersNumberBeforeProvisioning = MockDockerAdapterService.getNumberOfContainers();
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

        MockDockerAdapterService.getContainerIds();

        String compositeComponentLink = initialRequest.resourceLinks.get(0);

        CompositeComponent cc = getDocument(CompositeComponent.class, compositeComponentLink);
        List<String> initialLinks = cc.componentLinks;

        compositeComponentLink = compositeComponentLink
                .substring(compositeComponentLink.lastIndexOf("/"));

        // increase name1 instances. The containers should not be placed on the same host as desc2
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = "/resources/container-descriptions/"
                + desc1.documentSelfLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
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
                ContainerClusteringTaskFactoryService.SELF_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        ContainerClusteringTaskState clusteringTask = waitForTaskSuccess(
                containerClusteringTaskLink, ContainerClusteringTaskState.class);
        assertNotNull(clusteringTask.resourceLinks);

        RequestBrokerState clusteringRequest = waitForRequestToComplete(day2OperationClustering);
        assertNotNull(clusteringRequest.resourceLinks);
        assertEquals(clusteringRequest.resourceLinks, clusteringTask.resourceLinks);

        Set<String> allContainerLinks = new HashSet<>();
        allContainerLinks.addAll(initialLinks);
        allContainerLinks.addAll(clusteringRequest.resourceLinks);

        Set<ContainerState> containerStates = new HashSet<>();
        allContainerLinks.forEach(link -> {
            try {
                containerStates.add(getDocument(ContainerState.class, link));
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        });

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

        long containersNumberBeforeProvisioning = MockDockerAdapterService.getNumberOfContainers();
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

        MockDockerAdapterService.getContainerIds();

        String compositeComponentLink = initialRequest.resourceLinks.get(0);

        CompositeComponent cc = getDocument(CompositeComponent.class, compositeComponentLink);
        List<String> initialLinks = cc.componentLinks;

        compositeComponentLink = compositeComponentLink
                .substring(compositeComponentLink.lastIndexOf("/"));

        // increase name1 instances. The containers should be placed on the same host as all other
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = "/resources/container-descriptions/"
                + desc1.documentSelfLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
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
                ContainerClusteringTaskFactoryService.SELF_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        ContainerClusteringTaskState clusteringTask = waitForTaskSuccess(
                containerClusteringTaskLink, ContainerClusteringTaskState.class);
        assertNotNull(clusteringTask.resourceLinks);

        RequestBrokerState clusteringRequest = waitForRequestToComplete(day2OperationClustering);
        assertNotNull(clusteringRequest.resourceLinks);
        assertEquals(clusteringRequest.resourceLinks, clusteringTask.resourceLinks);

        Set<String> allContainerLinks = new HashSet<>();
        allContainerLinks.addAll(initialLinks);
        allContainerLinks.addAll(clusteringRequest.resourceLinks);

        Set<String> parentLinks = new HashSet<>();
        allContainerLinks.forEach(link -> {
            try {
                parentLinks.add(getDocument(ContainerState.class, link).parentLink);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        });

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

        long containersNumberBeforeProvisioning = MockDockerAdapterService.getNumberOfContainers();
        assertEquals(0, containersNumberBeforeProvisioning);

        ContainerDescriptionService.ContainerDescription desc1 = TestRequestStateFactory
                .createContainerDescription("name1");
        desc1._cluster = 3;
        desc1 = doPost(desc1, ContainerDescriptionService.FACTORY_LINK);

        request.resourceCount = 1;
        request.resourceDescriptionLink = desc1.documentSelfLink;
        request = startRequest(request);
        RequestBrokerState initialRequest = waitForRequestToComplete(request);

        long containersNumberBeforeClustering = MockDockerAdapterService.getNumberOfContainers();

        // Number of containers before provisioning.
        assertEquals(3, containersNumberBeforeClustering);

        // Create Day 2 operation for clustering containers.
        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = initialRequest.resourceDescriptionLink;
        day2OperationClustering.tenantLinks = groupPolicyState.tenantLinks;
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
                ContainerClusteringTaskFactoryService.SELF_LINK,
                extractId(day2OperationClustering.documentSelfLink));
        ContainerClusteringTaskState clusteringTask = waitForTaskSuccess(
                containerClusteringTaskLink, ContainerClusteringTaskState.class);
        assertNotNull(clusteringTask.resourceLinks);

        RequestBrokerState clusteringRequest = waitForRequestToComplete(day2OperationClustering);
        assertNotNull(clusteringRequest.resourceLinks);
        assertEquals(clusteringRequest.resourceLinks, clusteringTask.resourceLinks);

        long containersNumberAfterClustering = MockDockerAdapterService.getNumberOfContainers();
        // Number of containers after clustering, should be increased with 7.
        assertEquals(10, containersNumberAfterClustering);

        Set<String> allContainerLinks = new HashSet<>();
        allContainerLinks.addAll(initialRequest.resourceLinks);
        allContainerLinks.addAll(clusteringRequest.resourceLinks);

        assertEquals(10, allContainerLinks.size());

        Set<String> parentLinks = new HashSet<>();
        allContainerLinks.forEach(link -> {
            try {
                parentLinks.add(getDocument(ContainerState.class, link).parentLink);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        });

        assertEquals("All containers should be on different hosts", 10, parentLinks.size());
    }
}
