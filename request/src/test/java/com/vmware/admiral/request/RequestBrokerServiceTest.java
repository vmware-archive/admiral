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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.network.Ipam;
import com.vmware.admiral.compute.container.network.IpamConfig;
import com.vmware.admiral.compute.container.network.NetworkUtils;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.container.volume.VolumeUtil;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState.SubStage;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState;
import com.vmware.admiral.request.composition.CompositionSubTaskService;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.admiral.service.test.MockDockerNetworkAdapterService;
import com.vmware.admiral.service.test.MockDockerVolumeAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class RequestBrokerServiceTest extends RequestBaseTest {

    @After
    public void tearDown() throws Throwable {
        MockDockerNetworkAdapterService.resetNetworks();
    }

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
        String requestSelfLink = request.documentSelfLink;
        request = waitForRequestToComplete(request);

        // 2. Reservation stage:
        String rsrvSelfLink = UriUtils.buildUriPath(ReservationTaskFactoryService.SELF_LINK,
                extractId(requestSelfLink));
        ReservationTaskState rsrvTask = getDocument(ReservationTaskState.class, rsrvSelfLink);
        assertNotNull(rsrvTask);
        assertEquals(request.resourceDescriptionLink, rsrvTask.resourceDescriptionLink);
        assertEquals(requestSelfLink, rsrvTask.serviceTaskCallback.serviceSelfLink);
        assertEquals(request.tenantLinks, rsrvTask.tenantLinks);

        // 3. Allocation stage:
        String allocationSelfLink = UriUtils.buildUriPath(
                ContainerAllocationTaskFactoryService.SELF_LINK,
                extractId(requestSelfLink));
        ContainerAllocationTaskState allocationTask = getDocument(
                ContainerAllocationTaskState.class, allocationSelfLink);
        assertNotNull(allocationTask);
        assertEquals(request.resourceDescriptionLink, allocationTask.resourceDescriptionLink);
        if (allocationTask.serviceTaskCallback == null) {
            allocationTask = getDocument(ContainerAllocationTaskState.class, allocationSelfLink);
        }
        assertEquals(requestSelfLink, allocationTask.serviceTaskCallback.serviceSelfLink);

        request = getDocument(RequestBrokerState.class, requestSelfLink);

        assertNotNull("ResourceLinks null for requestSelfLink: " + requestSelfLink,
                request.resourceLinks);
        assertEquals(1, request.resourceLinks.size());
        ContainerState containerState = getDocument(ContainerState.class,
                request.resourceLinks.iterator().next());
        assertNotNull(containerState);

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);
        assertEquals(Integer.valueOf(100), rs.progress);

        // 4. Remove the container
        request = TestRequestStateFactory.createRequestState();
        request.operation = ContainerOperationType.DELETE.id;
        request.resourceLinks = new HashSet<>();
        request.resourceLinks.add(containerState.documentSelfLink);
        request = startRequest(request);

        request = waitForRequestToComplete(request);

        // Verify the container is removed
        containerState = searchForDocument(ContainerState.class, request.resourceLinks.iterator()
                .next());
        assertNull(containerState);

        // Verify request status
        rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);
        assertEquals(Integer.valueOf(100), rs.progress);
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
        String requestSelfLink = request.documentSelfLink;
        String requestId = extractId(requestSelfLink);
        request = waitForRequestToComplete(request);

        // 2. Reservation stage:
        String allocationTaskId = requestId + "-" + containerDesc.name
                + CompositionSubTaskService.ALLOC_SUFFIX;
        String rsrvSelfLink = UriUtils.buildUriPath(ReservationTaskFactoryService.SELF_LINK,
                allocationTaskId);
        ReservationTaskState rsrvTask = getDocument(ReservationTaskState.class, rsrvSelfLink);
        assertNotNull(rsrvTask);
        String containerDescLink = UriUtils.buildUriPath(ContainerDescriptionService.FACTORY_LINK,
                containerDesc.documentSelfLink);
        String rsrvRequestSelfLink = UriUtils.buildUriPath(RequestBrokerFactoryService.SELF_LINK,
                allocationTaskId);
        assertEquals(containerDescLink, rsrvTask.resourceDescriptionLink);
        assertEquals(rsrvRequestSelfLink, rsrvTask.serviceTaskCallback.serviceSelfLink);
        assertEquals(request.tenantLinks, rsrvTask.tenantLinks);

        // 3. Allocation stage:
        String allocationSelfLink = UriUtils
                .buildUriPath(ContainerAllocationTaskFactoryService.SELF_LINK, allocationTaskId);
        ContainerAllocationTaskState allocationTask = getDocument(
                ContainerAllocationTaskState.class, allocationSelfLink);
        assertNotNull(allocationTask);
        assertEquals(containerDescLink, allocationTask.resourceDescriptionLink);
        if (allocationTask.serviceTaskCallback == null) {
            allocationTask = getDocument(ContainerAllocationTaskState.class, allocationSelfLink);
        }
        assertEquals(rsrvRequestSelfLink, allocationTask.serviceTaskCallback.serviceSelfLink);

        assertNotNull("ResourceLinks null for requestSelfLink: " + requestSelfLink,
                request.resourceLinks);
        assertEquals(1, request.resourceLinks.size());
        ContainerState containerState = getDocument(ContainerState.class,
                request.resourceLinks.iterator().next());
        assertNotNull(containerState);

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);
        assertEquals(Integer.valueOf(100), rs.progress);

        // 4. Remove the composite component
        request = TestRequestStateFactory.createRequestState();
        request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        request.operation = ContainerOperationType.DELETE.id;
        request.resourceLinks = new HashSet<>();
        request.resourceLinks.add(UriUtils.buildUriPath(CompositeComponentFactoryService.SELF_LINK,
                extractId(requestSelfLink)));
        request = startRequest(request);

        request = waitForRequestToComplete(request);

        // Verify the container is removed
        containerState = searchForDocument(ContainerState.class, request.resourceLinks.iterator()
                .next());
        assertNull(containerState);

        // Verify request status
        rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);
        assertEquals(Integer.valueOf(100), rs.progress);
    }

    @Test
    public void testCompositeComponentWithContainerNetworkRequestLifeCycle() throws Throwable {
        host.log(
                "########  Start of testCompositeComponentWithContainerNetworkRequestLifeCycle ######## ");

        // setup Docker Host:
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();

        delete(computeHost.documentSelfLink);
        computeHost = null;

        // "set" the same KV-store for the Docker Hosts created
        dockerHostDesc.customProperties.put(
                ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store");

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        // setup Composite description with 2 containers and 1 network

        String networkName = "mynet";

        ContainerNetworkDescription networkDesc = TestRequestStateFactory
                .createContainerNetworkDescription(networkName);
        networkDesc.documentSelfLink = UUID.randomUUID().toString();

        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.name = "container1";
        container1Desc.networks = new HashMap<>();
        container1Desc.networks.put(networkName, new ServiceNetwork());

        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription();
        container2Desc.documentSelfLink = UUID.randomUUID().toString();
        container2Desc.name = "container2";
        container2Desc.affinity = new String[] { "!container1:hard" };
        container2Desc.networks = new HashMap<>();
        container2Desc.networks.put(networkName, new ServiceNetwork());

        CompositeDescription compositeDesc = createCompositeDesc(networkDesc, container1Desc,
                container2Desc);
        assertNotNull(compositeDesc);

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

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        assertEquals(Integer.valueOf(100), rs.progress);
        assertEquals(1, request.resourceLinks.size());

        String compositeComponentLink = request.resourceLinks.iterator().next();
        CompositeComponent cc = searchForDocument(CompositeComponent.class, compositeComponentLink);

        String networkLink = null;
        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();
        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerNetworkService.FACTORY_LINK)) {
                networkLink = link;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        ContainerState cont1 = searchForDocument(ContainerState.class, containerLink1);
        ContainerState cont2 = searchForDocument(ContainerState.class, containerLink2);

        boolean containerIsProvisionedOnAnyHosts = cont1.parentLink
                .equals(dockerHost1.documentSelfLink)
                || cont1.parentLink.equals(dockerHost2.documentSelfLink);
        assertTrue(containerIsProvisionedOnAnyHosts);

        containerIsProvisionedOnAnyHosts = cont2.parentLink.equals(dockerHost1.documentSelfLink)
                || cont2.parentLink.equals(dockerHost2.documentSelfLink);
        assertTrue(containerIsProvisionedOnAnyHosts);

        // provisioned on different hosts
        assertFalse(cont1.parentLink.equals(cont2.parentLink));

        ContainerNetworkState network = searchForDocument(ContainerNetworkState.class, networkLink);
        boolean networkIsProvisionedOnAnyHosts = network.originatingHostLink
                .equals(dockerHost1.documentSelfLink)
                || network.originatingHostLink.equals(dockerHost2.documentSelfLink);
        assertTrue(networkIsProvisionedOnAnyHosts);

        assertEquals(cc.documentSelfLink, cont1.compositeComponentLink);
        assertEquals(cc.documentSelfLink, cont2.compositeComponentLink);
        assertTrue((network.compositeComponentLinks.size() == 1)
                && network.compositeComponentLinks.contains(cc.documentSelfLink));

        // Delete container 2
        request = TestRequestStateFactory.createRequestState();
        request.operation = ContainerOperationType.DELETE.id;
        request.resourceLinks = new HashSet<>();
        request.resourceLinks.add(containerLink2);
        request = startRequest(request);

        request = waitForRequestToComplete(request);

        // Verify the container is removed
        cont2 = searchForDocument(ContainerState.class, containerLink2);
        assertNull(cont2);

        // Verify the composite component is not removed (there is still 1 container)
        cc = searchForDocument(CompositeComponent.class, compositeComponentLink);
        assertNotNull(cc);

        // Delete container 1
        request = TestRequestStateFactory.createRequestState();
        request.operation = ContainerOperationType.DELETE.id;
        request.resourceLinks = new HashSet<>();
        request.resourceLinks.add(containerLink1);
        request = startRequest(request);

        request = waitForRequestToComplete(request);

        // Verify the container is removed
        cont1 = searchForDocument(ContainerState.class, containerLink2);
        assertNull(cont1);

        // Verify the composite component is not removed (there is still a network)
        cc = searchForDocument(CompositeComponent.class, compositeComponentLink);
        assertNotNull(cc);
    }

    @Test
    public void testCompositeComponentWithContainerExternalNetworkRequestLifeCycle()
            throws Throwable {
        host.log(
                "########  Start of testCompositeComponentWithContainerExternalNetworkRequestLifeCycle ######## ");

        // setup Composite description with 2 containers and 1 external network

        String networkName = "external-net";

        // create external network (same as HostNetworkListDataCollection discovers external
        // networks)
        ContainerNetworkState networkState = new ContainerNetworkState();
        networkState.id = UUID.randomUUID().toString();
        networkState.name = networkName;
        networkState.documentSelfLink = NetworkUtils.buildNetworkLink(networkState.id);
        networkState.external = true;

        networkState.tenantLinks = groupPlacementState.tenantLinks;
        networkState.descriptionLink = String.format("%s-%s",
                ContainerNetworkDescriptionService.DISCOVERED_DESCRIPTION_LINK,
                UUID.randomUUID().toString());
        networkState.originatingHostLink = computeHost.documentSelfLink;
        networkState.parentLinks = new ArrayList<>(
                Arrays.asList(computeHost.documentSelfLink));
        networkState.adapterManagementReference = UriUtils
                .buildUri(ManagementUriParts.ADAPTER_DOCKER_NETWORK);

        networkState.powerState = ContainerNetworkState.PowerState.CONNECTED;
        networkState.driver = "bridge";
        networkState.ipam = new Ipam();
        networkState.ipam.driver = "default";
        networkState.ipam.config = new IpamConfig[1];
        networkState.ipam.config[0] = new IpamConfig();
        networkState.ipam.config[0].subnet = "172.20.0.0/16";
        networkState.ipam.config[0].gateway = "172.20.0.1";
        networkState.connectedContainersCount = 0;
        networkState.options = new HashMap<>();
        networkState = doPost(networkState, ContainerNetworkService.FACTORY_LINK);
        addForDeletion(networkState);
        MockDockerNetworkAdapterService.addNetworkId(extractId(computeHost.documentSelfLink),
                networkState.id, networkState.id);
        MockDockerNetworkAdapterService.addNetworkNames(extractId(computeHost.documentSelfLink),
                networkState.id, networkState.name);

        ContainerNetworkDescription networkDesc = NetworkUtils
                .createContainerNetworkDescription(networkState);
        networkDesc.external = networkState.external;

        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.name = "container1";
        container1Desc._cluster = 2;
        container1Desc.networks = new HashMap<>();
        container1Desc.networks.put(networkName, new ServiceNetwork());

        // create composite description, do not override the documentSelfLinks for the descriptions
        CompositeDescription compositeDesc = createCompositeDesc(false, false, networkDesc,
                container1Desc);
        assertNotNull(compositeDesc);

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

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        assertEquals(Integer.valueOf(100), rs.progress);
        assertEquals(1, request.resourceLinks.size());

        String compositeComponentLink = request.resourceLinks.iterator().next();
        CompositeComponent cc = searchForDocument(CompositeComponent.class, compositeComponentLink);

        String networkLink = null;
        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();
        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerNetworkService.FACTORY_LINK)) {
                networkLink = link;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        // Delete container 2
        request = TestRequestStateFactory.createRequestState();
        request.operation = ContainerOperationType.DELETE.id;
        request.resourceLinks = new HashSet<>();
        request.resourceLinks.add(containerLink2);
        request = startRequest(request);

        request = waitForRequestToComplete(request);

        // Verify the container is removed
        ContainerState cont2 = searchForDocument(ContainerState.class, containerLink2);
        assertNull(cont2);

        // Verify the composite component is not removed (there is still 1 container)
        cc = searchForDocument(CompositeComponent.class, compositeComponentLink);
        assertNotNull(cc);

        // Delete container 1
        request = TestRequestStateFactory.createRequestState();
        request.operation = ContainerOperationType.DELETE.id;
        request.resourceLinks = new HashSet<>();
        request.resourceLinks.add(containerLink1);
        request = startRequest(request);

        request = waitForRequestToComplete(request);

        // Verify the container is removed
        ContainerState cont1 = searchForDocument(ContainerState.class, containerLink2);
        assertNull(cont1);

        // Verify the composite component is removed (only a external network in the application)
        waitFor(() -> {
            CompositeComponent compositeComponent = searchForDocument(CompositeComponent.class,
                    compositeComponentLink);
            return compositeComponent == null;
        });

        // Verify the external network is not removed
        ContainerNetworkState network = searchForDocument(ContainerNetworkState.class, networkLink);
        assertNotNull(network);
    }

    @Test
    public void testRequestLifecycleWithContainerNetworkShouldCleanNetworkStatesOnProvisionAndDeletionFailure()
            throws Throwable {
        host.log(
                "########  Start of "
                        + "testRequestLifecycleWithContainerNetworkShouldCleanNetworkStatesOnProvisionAndDeletionFailure ######## ");

        // 1. Request a network with expected failure:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.CONTAINER_NETWORK_TYPE.getName(),
                containerNetworkDesc.documentSelfLink);
        request.tenantLinks = groupPlacementState.tenantLinks;
        request.customProperties.put(
                ReservationAllocationTaskService.CONTAINER_HOST_ID_CUSTOM_PROPERTY, computeHost.id);

        // This should ensure that both the provisioning and the deletion (cleanup) requests to the
        // mock adapter will fail - during the allocation, the custom properties will be copied into
        // the network state. During the provisioning, the mock adapter will read the
        // EXPECTED_FAILURE from the request's custom properties and during deletion (cleanup) -
        // from the network state.
        request.customProperties.put(MockDockerNetworkAdapterService.FAILURE_EXPECTED,
                Boolean.TRUE.toString());

        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // 2. Wait for reservation removed substage
        waitForRequestToFail(request);

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        // and there must be no container network state left
        ServiceDocumentQueryResult networkStates = getDocument(ServiceDocumentQueryResult.class,
                ContainerNetworkService.FACTORY_LINK);
        assertEquals(0L, networkStates.documentCount.longValue());
    }

    @Test
    public void testRequestLifeCycleWithContainerNetworkFailureShouldCleanNetworks()
            throws Throwable {
        host.log(
                "########  Start of testRequestLifeCycleWithContainerNetworkFailureShouldCleanNetworks ######## ");

        // setup Docker Host:
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();

        delete(computeHost.documentSelfLink);
        computeHost = null;

        // "set" the same KV-store for the Docker Hosts created
        dockerHostDesc.customProperties.put(
                ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store");

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        // setup Composite description with 2 containers and 1 network

        String networkName = "mynet";

        ContainerNetworkDescription networkDesc = TestRequestStateFactory
                .createContainerNetworkDescription(networkName);
        networkDesc.documentSelfLink = UUID.randomUUID().toString();

        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.name = "container1";
        container1Desc.networks = new HashMap<>();
        container1Desc.networks.put(networkName, new ServiceNetwork());

        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription();
        container2Desc.documentSelfLink = UUID.randomUUID().toString();
        container2Desc.name = "container2";
        container2Desc.affinity = new String[] { "!container1:hard" };
        container2Desc.networks = new HashMap<>();
        container2Desc.networks.put(networkName, new ServiceNetwork());
        container2Desc.customProperties.put(MockDockerAdapterService.FAILURE_EXPECTED,
                Boolean.TRUE.toString());

        CompositeDescription compositeDesc = createCompositeDesc(networkDesc, container1Desc,
                container2Desc);
        assertNotNull(compositeDesc);

        // setup Group Placement:
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        // 1. Request a composite container with expected failure:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);
        request.tenantLinks = groupPlacementState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // 2. Wait for reservation removed substage
        waitForRequestToFail(request);

        // 3. Verify that the group placement has been released.
        groupPlacementState = getDocument(GroupResourcePlacementState.class,
                groupPlacementState.documentSelfLink);
        assertEquals(groupPlacementState.allocatedInstancesCount, 0);

        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = compositeDesc.descriptionLinks.iterator();
        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerNetworkDescriptionService.FACTORY_LINK)) {
                continue;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        assertEquals(0,
                groupPlacementState.resourceQuotaPerResourceDesc.get(containerLink1).intValue());
        assertEquals(0,
                groupPlacementState.resourceQuotaPerResourceDesc.get(containerLink2).intValue());

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        // and there must be no container network state left
        ServiceDocumentQueryResult networkStates = getDocument(ServiceDocumentQueryResult.class,
                ContainerNetworkService.FACTORY_LINK);
        assertEquals(0L, networkStates.documentCount.longValue());
    }

    @Test
    public void testNetworkRequestLifeCycleWithNetworkFailureShouldCleanNetworks()
            throws Throwable {
        host.log(
                "########  Start of testNetworkRequestLifeCycleWithNetworkFailureShouldCleanNetworks ######## ");

        // setup 1 network

        String networkName = "mynet";

        ContainerNetworkDescription networkDesc = TestRequestStateFactory
                .createContainerNetworkDescription(networkName);
        networkDesc.documentSelfLink = UUID.randomUUID().toString();
        // e.g. Docker host was not available!
        networkDesc.customProperties.put(MockDockerAdapterService.FAILURE_EXPECTED,
                Boolean.TRUE.toString());

        networkDesc = doPost(networkDesc, ContainerNetworkDescriptionService.FACTORY_LINK);
        addForDeletion(networkDesc);

        // 1. Request a network with expected failure:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.CONTAINER_NETWORK_TYPE.getName(), networkDesc.documentSelfLink);
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // 2. Wait for reservation removed substage
        request = waitForRequestToFail(request);

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        assertEquals(TaskStage.FAILED, rs.taskInfo.stage);
        assertEquals(SubStage.ERROR.name(), rs.subStage);

        // and there must be no container network state left
        ServiceDocumentQueryResult networkStates = getDocument(ServiceDocumentQueryResult.class,
                ContainerNetworkService.FACTORY_LINK);
        assertEquals(0L, networkStates.documentCount.longValue());
    }

    @Test
    public void testRequestLifeCycleWithContainerNetworkAndServiceAntiAffinityFilterFailureShouldCleanNetworks()
            throws Throwable {
        host.log(
                "########  Start of testRequestLifeCycleWithContainerNetworkAndServiceAntiAffinityFilterFailureShouldCleanNetworks ######## ");

        // setup Docker Host:
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();

        delete(computeHost.documentSelfLink);
        computeHost = null;

        // DO NOT "set" the same KV-store for the Docker Hosts created!
        // In this way the ContainerToNetworkAffinityFilter and ServiceAntiAffinityHostFilter will
        // work as expected by saying that there are no hosts available.
        // Containers should be set on the same host because of the network but they "can't"
        // because of the container2 anti-affinity rule.

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        // setup Composite description with 2 containers and 1 network

        String networkName = "mynet";

        ContainerNetworkDescription networkDesc = TestRequestStateFactory
                .createContainerNetworkDescription(networkName);
        networkDesc.documentSelfLink = UUID.randomUUID().toString();

        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription("container1");
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.networks = new HashMap<>();
        container1Desc.networks.put(networkName, new ServiceNetwork());

        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription("container2");
        container2Desc.documentSelfLink = UUID.randomUUID().toString();
        container2Desc.affinity = new String[] { "!container1:hard" };
        container2Desc.networks = new HashMap<>();
        container2Desc.networks.put(networkName, new ServiceNetwork());

        CompositeDescription compositeDesc = createCompositeDesc(networkDesc, container1Desc,
                container2Desc);
        assertNotNull(compositeDesc);

        // setup Group Placement:
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        // 1. Request a composite container with expected failure:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);
        request.tenantLinks = groupPlacementState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // 2. Wait for reservation removed substage
        waitForRequestToFail(request);

        // 3. Verify that the group placement has been released.
        groupPlacementState = getDocument(GroupResourcePlacementState.class,
                groupPlacementState.documentSelfLink);
        assertEquals(groupPlacementState.allocatedInstancesCount, 0);

        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = compositeDesc.descriptionLinks.iterator();
        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerNetworkDescriptionService.FACTORY_LINK)) {
                continue;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        Long container1Quota = groupPlacementState.resourceQuotaPerResourceDesc.get(containerLink1);
        Long container2Quota = groupPlacementState.resourceQuotaPerResourceDesc.get(containerLink2);
        assertTrue((container1Quota != null && container1Quota == 0L && container2Quota == null)
                || (container2Quota != null && container2Quota == 0L && container1Quota == null));

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        // and there must be no container network state left
        ServiceDocumentQueryResult networkStates = getDocument(ServiceDocumentQueryResult.class,
                ContainerNetworkService.FACTORY_LINK);
        assertEquals(0L, networkStates.documentCount.longValue());
    }

    @Test
    public void testRequestLifeCycleWithContainerNetworkAndClusterAntiAffinityFilterShouldPass()
            throws Throwable {
        host.log(
                "########  Start of testRequestLifeCycleWithContainerNetworkAndClusterAntiAffinityFilterShouldPass ######## ");

        // setup Docker Host:
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();

        delete(computeHost.documentSelfLink);
        computeHost = null;

        // setup cluster 1

        dockerHostDesc.customProperties.put(
                ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store-1");

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        List<String> cluster1Hosts = Arrays.asList(dockerHost1.documentSelfLink,
                dockerHost2.documentSelfLink);

        // setup cluster 2

        dockerHostDesc.customProperties.put(
                ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store-2");

        ComputeState dockerHost3 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost3);

        ComputeState dockerHost4 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost4);

        List<String> cluster2Hosts = Arrays.asList(dockerHost3.documentSelfLink,
                dockerHost4.documentSelfLink);

        // setup Composite description with 2 containers and 1 network

        String networkName = "mynet";

        ContainerNetworkDescription networkDesc = TestRequestStateFactory
                .createContainerNetworkDescription(networkName);
        networkDesc.documentSelfLink = UUID.randomUUID().toString();

        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.name = "container1";
        container1Desc.networks = new HashMap<>();
        container1Desc.networks.put(networkName, new ServiceNetwork());

        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription();
        container2Desc.documentSelfLink = UUID.randomUUID().toString();
        container2Desc.name = "container2";
        container2Desc.networks = new HashMap<>();
        container2Desc.networks.put(networkName, new ServiceNetwork());

        CompositeDescription compositeDesc = createCompositeDesc(networkDesc, container1Desc,
                container2Desc);
        assertNotNull(compositeDesc);

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

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        assertEquals(Integer.valueOf(100), rs.progress);
        assertEquals(1, request.resourceLinks.size());

        CompositeComponent cc = getDocument(CompositeComponent.class, request.resourceLinks
                .iterator().next());

        String networkLink = null;
        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();
        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerNetworkService.FACTORY_LINK)) {
                networkLink = link;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        ContainerState cont1 = getDocument(ContainerState.class, containerLink1);
        ContainerState cont2 = getDocument(ContainerState.class, containerLink2);

        List<String> selectedCluster = null;
        if (cluster1Hosts.contains(cont1.parentLink)) {
            selectedCluster = cluster1Hosts;
        } else if (cluster2Hosts.contains(cont1.parentLink)) {
            selectedCluster = cluster2Hosts;
        }

        assertNotNull(selectedCluster);

        // provisioned on the same cluster
        boolean containersAreProvisionedOnTheSameCluster = selectedCluster
                .contains(cont2.parentLink);
        assertTrue(containersAreProvisionedOnTheSameCluster);

        ContainerNetworkState network = getDocument(ContainerNetworkState.class, networkLink);

        // the network as well
        boolean networkProvisionedOnTheSameCluster = selectedCluster
                .contains(network.originatingHostLink);
        assertTrue(networkProvisionedOnTheSameCluster);

        assertEquals(cc.documentSelfLink, cont1.compositeComponentLink);
        assertEquals(cc.documentSelfLink, cont2.compositeComponentLink);
        assertTrue((network.compositeComponentLinks.size() == 1)
                && network.compositeComponentLinks.contains(cc.documentSelfLink));

        // Add another cluster with more hosts so that it would be preferable for selection

        dockerHostDesc.customProperties.put(
                ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store-3");

        addForDeletion(createDockerHost(dockerHostDesc, resourcePool, true));
        addForDeletion(createDockerHost(dockerHostDesc, resourcePool, true));
        addForDeletion(createDockerHost(dockerHostDesc, resourcePool, true));
        addForDeletion(createDockerHost(dockerHostDesc, resourcePool, true));

        // then scaling one container of the app should pick always the same cluster because the
        // network exists only there (i.e. the operation can't fail)

        int SCALE_SIZE = 8;

        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = cont1.descriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        day2OperationClustering.resourceCount = 1 + SCALE_SIZE;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = cont1.customProperties;
        day2OperationClustering.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY,
                Service.getId(cc.documentSelfLink));

        host.log("########  Start of request ######## ");
        request = startRequest(day2OperationClustering);

        // wait for request completed state:
        request = waitForRequestToComplete(request);

        // Verify request status
        rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        assertEquals(Integer.valueOf(100), rs.progress);
        assertEquals(SCALE_SIZE, request.resourceLinks.size());

        // Verify that even there is a bigger cluster, scaled containers are placed on the cluster
        // where the others are,
        // so that the networking between them will be working
        for (String scaledContainerLink : request.resourceLinks) {
            ContainerState scaledContainer = getDocument(ContainerState.class, scaledContainerLink);
            boolean scaledContainersAreProvisionedOnTheSameCluster = selectedCluster
                    .contains(scaledContainer.parentLink);
            assertTrue(scaledContainersAreProvisionedOnTheSameCluster);
        }

        cc = getDocument(CompositeComponent.class, cc.documentSelfLink);

        assertEquals(SCALE_SIZE + 3 /* 2 containers + 1 network */, cc.componentLinks.size());
    }

    @Test
    public void testRequestLifeCycleWithContainerNetworkAndPortBindingConflictOnScaleShouldFail()
            throws Throwable {
        host.log(
                "########  Start of testRequestLifeCycleWithContainerNetworkAndPortBindingConflictOnScaleShouldFail ######## ");

        // setup Docker Host:
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();

        delete(computeHost.documentSelfLink);
        computeHost = null;

        // setup cluster 1

        dockerHostDesc.customProperties.put(
                ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store-1");

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        List<String> cluster1Hosts = Arrays.asList(dockerHost1.documentSelfLink,
                dockerHost2.documentSelfLink);

        // setup cluster 2

        dockerHostDesc.customProperties.put(
                ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store-2");

        ComputeState dockerHost3 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost3);

        ComputeState dockerHost4 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost4);

        List<String> cluster2Hosts = Arrays.asList(dockerHost3.documentSelfLink,
                dockerHost4.documentSelfLink);

        // setup Composite description with 2 containers and 1 network

        String networkName = "mynet";

        ContainerNetworkDescription networkDesc = TestRequestStateFactory
                .createContainerNetworkDescription(networkName);
        networkDesc.documentSelfLink = UUID.randomUUID().toString();

        ContainerDescription container1Desc = TestRequestStateFactory
                .createContainerDescriptionWithPortBindingsHostPortSet();
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.name = "container1";
        container1Desc.networks = new HashMap<>();
        container1Desc.networks.put(networkName, new ServiceNetwork());

        ContainerDescription container2Desc = TestRequestStateFactory
                .createContainerDescriptionWithPortBindingsHostPortSet();
        container2Desc.documentSelfLink = UUID.randomUUID().toString();
        container2Desc.name = "container2";
        container2Desc.networks = new HashMap<>();
        container2Desc.networks.put(networkName, new ServiceNetwork());

        CompositeDescription compositeDesc = createCompositeDesc(networkDesc, container1Desc,
                container2Desc);
        assertNotNull(compositeDesc);

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

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        assertEquals(Integer.valueOf(100), rs.progress);
        assertEquals(1, request.resourceLinks.size());

        CompositeComponent cc = getDocument(CompositeComponent.class,
                request.resourceLinks.iterator().next());

        String networkLink = null;
        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();
        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerNetworkService.FACTORY_LINK)) {
                networkLink = link;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        ContainerState cont1 = getDocument(ContainerState.class, containerLink1);
        ContainerState cont2 = getDocument(ContainerState.class, containerLink2);

        // Verify containers are on different hosts because of the same exposed ports
        assertNotEquals("containers must be deployed on different hosts", cont1.parentLink,
                cont2.parentLink);

        List<String> selectedCluster = null;
        if (cluster1Hosts.contains(cont1.parentLink)) {
            selectedCluster = cluster1Hosts;
        } else if (cluster2Hosts.contains(cont1.parentLink)) {
            selectedCluster = cluster2Hosts;
        }

        assertNotNull(selectedCluster);

        // provisioned on the same cluster
        boolean containersAreProvisionedOnTheSameCluster = selectedCluster
                .contains(cont2.parentLink);
        assertTrue(containersAreProvisionedOnTheSameCluster);

        ContainerNetworkState network = getDocument(ContainerNetworkState.class, networkLink);

        // the network as well
        boolean networkProvisionedOnTheSameCluster = selectedCluster
                .contains(network.originatingHostLink);
        assertTrue(networkProvisionedOnTheSameCluster);

        assertEquals(cc.documentSelfLink, cont1.compositeComponentLink);
        assertEquals(cc.documentSelfLink, cont2.compositeComponentLink);
        assertTrue((network.compositeComponentLinks.size() == 1)
                && network.compositeComponentLinks.contains(cc.documentSelfLink));

        // Add another cluster with more hosts so that it would be preferable for selection

        dockerHostDesc.customProperties.put(
                ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store-3");

        addForDeletion(createDockerHost(dockerHostDesc, resourcePool, true));
        addForDeletion(createDockerHost(dockerHostDesc, resourcePool, true));
        addForDeletion(createDockerHost(dockerHostDesc, resourcePool, true));
        addForDeletion(createDockerHost(dockerHostDesc, resourcePool, true));

        // then scaling one container of the app should pick always the same cluster because the
        // network exists only there (i.e. the operation can't fail)

        int SCALE_SIZE = 8;

        RequestBrokerState day2OperationClustering = TestRequestStateFactory.createRequestState();
        day2OperationClustering.resourceDescriptionLink = cont1.descriptionLink;
        day2OperationClustering.tenantLinks = groupPlacementState.tenantLinks;
        day2OperationClustering.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        day2OperationClustering.resourceCount = 1 + SCALE_SIZE;
        day2OperationClustering.documentDescription = containerDesc.documentDescription;
        day2OperationClustering.customProperties = cont1.customProperties;

        host.log("########  Start of request ######## ");
        request = startRequest(day2OperationClustering);

        // wait for request to fail because of the combination ExposedPortsHostFilter and
        // ContainerToNetworkAffinityHostFilter, between them there are no hosts available!
        waitForRequestToFail(request);

        // Verify request status
        rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);
    }

    @Test
    public void testCompositeComponentWithContainerVolumeRequestLifeCycle() throws Throwable {
        host.log(
                "########  Start of testCompositeComponentWithContainerVolumeRequestLifeCycle ######## ");

        // setup Docker Host:
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();

        delete(computeHost.documentSelfLink);
        computeHost = null;

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        String sharedVolumeName = "postgres";
        String volumeName = String.format("%s:/etc/pgdata/postgres", sharedVolumeName);

        ContainerVolumeDescription volumeDesc = TestRequestStateFactory
                .createContainerVolumeDescription(sharedVolumeName);
        volumeDesc.documentSelfLink = UUID.randomUUID().toString();

        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.name = "container1";
        container1Desc.volumes = new String[] { volumeName };

        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription();
        container2Desc.documentSelfLink = UUID.randomUUID().toString();
        container2Desc.name = "container2";
        container2Desc.affinity = new String[] { "!container1:hard" };

        // setup Composite description with 2 containers and 1 network
        CompositeDescription compositeDesc = createCompositeDesc(volumeDesc, container1Desc,
                container2Desc);
        assertNotNull(compositeDesc);

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

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        assertEquals(Integer.valueOf(100), rs.progress);
        assertEquals(1, request.resourceLinks.size());

        CompositeComponent cc = getDocument(CompositeComponent.class, request.resourceLinks
                .iterator().next());

        String volumeLink = null;
        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();

        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerVolumeService.FACTORY_LINK)) {
                volumeLink = link;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        ContainerState cont1 = getDocument(ContainerState.class, containerLink1);
        ContainerState cont2 = getDocument(ContainerState.class, containerLink2);

        boolean containerIsProvisionedOnAnyHosts = cont1.parentLink
                .equals(dockerHost1.documentSelfLink)
                || cont1.parentLink.equals(dockerHost2.documentSelfLink);
        assertTrue(containerIsProvisionedOnAnyHosts);

        containerIsProvisionedOnAnyHosts = cont2.parentLink.equals(dockerHost1.documentSelfLink)
                || cont2.parentLink.equals(dockerHost2.documentSelfLink);
        assertTrue(containerIsProvisionedOnAnyHosts);

        // provisioned on different hosts
        assertFalse(cont1.parentLink.equals(cont2.parentLink));

        ContainerVolumeState volume = getDocument(ContainerVolumeState.class, volumeLink);
        assertTrue(volume.name.contains(sharedVolumeName));

        String volumeDescProp = volume.customProperties.get("volume propKey string");
        assertNotNull(volumeDescProp);
        assertEquals("volume customPropertyValue string", volumeDescProp);

        String volumeHostPath = volume.originatingHostLink;

        boolean volumeIsProvisionedOnAnyHosts = volumeHostPath.equals(dockerHost1.documentSelfLink)
                || volumeHostPath.equals(dockerHost2.documentSelfLink);

        assertTrue(volumeIsProvisionedOnAnyHosts);

        assertEquals(cc.documentSelfLink, cont1.compositeComponentLink);
        assertEquals(cc.documentSelfLink, cont2.compositeComponentLink);
        assertTrue(volume.compositeComponentLinks.size() == 1
                && volume.compositeComponentLinks.contains(cc.documentSelfLink));
    }

    @Test
    public void testCompositeComponentWithContainerExternalVolumeRequestLifeCycle()
            throws Throwable {
        host.log(
                "########  Start of testCompositeComponentWithContainerExternalVolumeRequestLifeCycle ######## ");

        // setup Composite description with 2 containers and 1 external volume

        String volumeName = "external-vol";

        // create external volume (same as HostVolumeListDataCollection discovers external
        // volumes)
        ContainerVolumeState volumeState = new ContainerVolumeState();
        volumeState.id = UUID.randomUUID().toString();
        volumeState.name = volumeName;
        volumeState.documentSelfLink = VolumeUtil.buildVolumeLink(volumeState.id);
        volumeState.external = true;

        volumeState.tenantLinks = groupPlacementState.tenantLinks;
        volumeState.descriptionLink = String.format("%s-%s",
                ContainerVolumeDescriptionService.DISCOVERED_DESCRIPTION_LINK,
                UUID.randomUUID().toString());
        volumeState.originatingHostLink = computeHost.documentSelfLink;
        volumeState.parentLinks = new ArrayList<>(
                Arrays.asList(computeHost.documentSelfLink));
        volumeState.adapterManagementReference = UriUtils
                .buildUri(ManagementUriParts.ADAPTER_DOCKER_VOLUME);

        volumeState.powerState = ContainerVolumeState.PowerState.CONNECTED;
        volumeState.driver = "local";
        volumeState.options = new HashMap<>();
        volumeState = doPost(volumeState, ContainerVolumeService.FACTORY_LINK);
        addForDeletion(volumeState);
        MockDockerVolumeAdapterService.addVolumeName(extractId(computeHost.documentSelfLink),
                volumeName);

        ContainerVolumeDescription volumeDesc = VolumeUtil
                .createContainerVolumeDescription(volumeState);
        volumeDesc.external = volumeState.external;

        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.name = "container1";
        container1Desc._cluster = 2;
        container1Desc.volumes = new String[] { volumeName + ":/tmp" };

        // create composite description, do not override the documentSelfLinks for the descriptions
        CompositeDescription compositeDesc = createCompositeDesc(false, false, volumeDesc,
                container1Desc);
        assertNotNull(compositeDesc);

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

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        assertEquals(Integer.valueOf(100), rs.progress);
        assertEquals(1, request.resourceLinks.size());

        String compositeComponentLink = request.resourceLinks.iterator().next();
        CompositeComponent cc = searchForDocument(CompositeComponent.class, compositeComponentLink);

        String volumeLink = null;
        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();
        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerVolumeService.FACTORY_LINK)) {
                volumeLink = link;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        // Delete container 2
        request = TestRequestStateFactory.createRequestState();
        request.operation = ContainerOperationType.DELETE.id;
        request.resourceLinks = new HashSet<>();
        request.resourceLinks.add(containerLink2);
        request = startRequest(request);

        request = waitForRequestToComplete(request);

        // Verify the container is removed
        ContainerState cont2 = searchForDocument(ContainerState.class, containerLink2);
        assertNull(cont2);

        // Verify the composite component is not removed (there is still 1 container)
        cc = searchForDocument(CompositeComponent.class, compositeComponentLink);
        assertNotNull(cc);

        // Delete container 1
        request = TestRequestStateFactory.createRequestState();
        request.operation = ContainerOperationType.DELETE.id;
        request.resourceLinks = new HashSet<>();
        request.resourceLinks.add(containerLink1);
        request = startRequest(request);

        request = waitForRequestToComplete(request);

        // Verify the container is removed
        ContainerState cont1 = searchForDocument(ContainerState.class, containerLink2);
        assertNull(cont1);

        // Verify the composite component is removed (only a external volume in the application)
        waitFor(() -> {
            CompositeComponent compositeComponent = searchForDocument(CompositeComponent.class,
                    compositeComponentLink);
            return compositeComponent == null;
        });

        // Verify the external volume is not removed
        ContainerVolumeState volume = searchForDocument(ContainerVolumeState.class, volumeLink);
        assertNotNull(volume);
    }

    @Test
    public void testRequestLifeCycleWithContainerVolumeFailureShouldCleanVolumes()
            throws Throwable {
        host.log(
                "########  Start of testRequestLifeCycleWithContainerVolumeFailureShouldCleanVolumes ######## ");

        // setup Docker Host:
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();

        delete(computeHost.documentSelfLink);
        computeHost = null;

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        // setup Composite description with 2 containers and 1 network

        String volumeName = "myvolume";

        ContainerVolumeDescription volumeDesc = TestRequestStateFactory
                .createContainerVolumeDescription(volumeName);
        volumeDesc.documentSelfLink = UUID.randomUUID().toString();

        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.name = "container1";
        container1Desc.volumes = new String[] { volumeName + ":/tmp" };

        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription();
        container2Desc.documentSelfLink = UUID.randomUUID().toString();
        container2Desc.name = "container2";
        container1Desc.portBindings = null;
        container2Desc.volumes = new String[] { volumeName + ":/tmp" };
        container2Desc.customProperties.put(MockDockerAdapterService.FAILURE_EXPECTED,
                Boolean.TRUE.toString());

        CompositeDescription compositeDesc = createCompositeDesc(volumeDesc, container1Desc,
                container2Desc);
        assertNotNull(compositeDesc);

        // setup Group Placement:
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        // 1. Request a composite container with expected failure:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);
        request.tenantLinks = groupPlacementState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // 2. Wait for reservation removed substage
        waitForRequestToFail(request);

        // 3. Verify that the group placement has been released.
        groupPlacementState = getDocument(GroupResourcePlacementState.class,
                groupPlacementState.documentSelfLink);
        assertEquals(groupPlacementState.allocatedInstancesCount, 0);

        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = compositeDesc.descriptionLinks.iterator();
        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerVolumeDescriptionService.FACTORY_LINK)) {
                continue;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        assertEquals(0,
                groupPlacementState.resourceQuotaPerResourceDesc.get(containerLink1).intValue());
        assertEquals(0,
                groupPlacementState.resourceQuotaPerResourceDesc.get(containerLink2).intValue());

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        // and there must be no container network state left
        ServiceDocumentQueryResult volumeStates = getDocument(ServiceDocumentQueryResult.class,
                ContainerVolumeService.FACTORY_LINK);
        assertEquals(0L, volumeStates.documentCount.longValue());
    }

    @Test
    public void testVolumeRequestLifeCycleWithVolumeFailureShouldCleanVolumes()
            throws Throwable {
        host.log(
                "########  Start of testVolumeRequestLifeCycleWithVolumeFailureShouldCleanVolumes ######## ");

        // setup 1 volume

        String volumeName = "myvol";

        ContainerVolumeDescription volumeDesc = TestRequestStateFactory
                .createContainerVolumeDescription(volumeName);
        volumeDesc.documentSelfLink = UUID.randomUUID().toString();
        // e.g. Docker host was not available!
        volumeDesc.customProperties.put(MockDockerAdapterService.FAILURE_EXPECTED,
                Boolean.TRUE.toString());

        volumeDesc = doPost(volumeDesc, ContainerVolumeDescriptionService.FACTORY_LINK);
        addForDeletion(volumeDesc);

        // 1. Request a volume with expected failure:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.CONTAINER_VOLUME_TYPE.getName(), volumeDesc.documentSelfLink);
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // 2. Wait for reservation removed substage
        request = waitForRequestToFail(request);

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        assertEquals(TaskStage.FAILED, rs.taskInfo.stage);
        assertEquals(SubStage.ERROR.name(), rs.subStage);

        // and there must be no container volume state left
        ServiceDocumentQueryResult volumeStates = getDocument(ServiceDocumentQueryResult.class,
                ContainerVolumeService.FACTORY_LINK);
        assertEquals(0L, volumeStates.documentCount.longValue());
    }

    @Test
    public void testRequestLifeCycleFailureShouldCleanReservations() throws Throwable {
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
        request.customProperties = new HashMap<>();
        request.customProperties.put(MockDockerAdapterService.FAILURE_EXPECTED,
                Boolean.TRUE.toString());

        assertEquals(groupPlacementState.allocatedInstancesCount, 0);
        request = startRequest(request);

        // 2. Wait for reservation removed substage
        waitForRequestToFail(request);

        // 3. Verify that the group placement has been released.
        groupPlacementState = getDocument(GroupResourcePlacementState.class,
                groupPlacementState.documentSelfLink);
        assertEquals(groupPlacementState.allocatedInstancesCount, 0);
        assertEquals(0,
                groupPlacementState.resourceQuotaPerResourceDesc
                        .get(containerDesc.documentSelfLink)
                        .intValue());
    }

    @Test
    public void testRequestLifeCycleWithHostAssociatedAuthentication() throws Throwable {
        host.log(
                "########  Start of testRequestLifeCycleWithHostAssociatedAuthentication ######## ");
        safeDelete(containerDesc);
        safeDelete(resourcePool);
        safeDelete(hostDesc);
        safeDelete(computeHost);
        safeDelete(groupPlacementState);

        // setup Docker Host:
        resourcePool = TestRequestStateFactory.createResourcePool();
        resourcePool.documentSelfLink = UUID.randomUUID().toString();
        resourcePool.id = resourcePool.documentSelfLink;
        resourcePool = doPost(resourcePool, ResourcePoolService.FACTORY_LINK);
        assertNotNull(resourcePool);

        hostDesc = TestRequestStateFactory.createDockerHostDescription();
        hostDesc.documentSelfLink = UUID.randomUUID().toString();
        hostDesc = doPost(hostDesc, ComputeDescriptionService.FACTORY_LINK);
        assertNotNull(hostDesc);

        computeHost = TestRequestStateFactory.createDockerComputeHost();
        computeHost.id = UUID.randomUUID().toString();
        computeHost.resourcePoolLink = resourcePool.documentSelfLink;
        computeHost.descriptionLink = hostDesc.documentSelfLink;
        computeHost.customProperties = new HashMap<>();
        computeHost.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                        CommonTestStateFactory.AUTH_CREDENTIALS_ID));
        computeHost.customProperties.put(
                ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME, Long.MAX_VALUE + "");
        computeHost = doPost(computeHost, ComputeService.FACTORY_LINK);
        assertNotNull(computeHost);

        // setup Container desc:
        ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
        desc.documentSelfLink = UUID.randomUUID().toString();
        containerDesc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(containerDesc);

        // setup Group Placement:
        groupPlacementState = TestRequestStateFactory.createGroupResourcePlacementState();
        groupPlacementState.resourcePoolLink = resourcePool.documentSelfLink;
        groupPlacementState = doPost(groupPlacementState,
                GroupResourcePlacementService.FACTORY_LINK);
        assertNotNull(groupPlacementState);

        // 1. Request a container instance:
        RequestBrokerState request = TestRequestStateFactory.createRequestState();
        request.resourceDescriptionLink = containerDesc.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // wait for request completed state:
        String requestSelfLink = request.documentSelfLink;
        request = waitForRequestToComplete(request);

        assertNotNull("ResourceLinks null for requestSelfLink: " + requestSelfLink,
                request.resourceLinks);
        assertEquals(1, request.resourceLinks.size());
        ContainerState containerState = getDocument(ContainerState.class,
                request.resourceLinks.iterator().next());
        assertNotNull(containerState);
    }

    // @Ignore("Not implemented yet. Part of the Reservation refactoring with multiple reservation
    // selections.")
    @Test
    public void testShouldSelectHostFromAllResourcePools() throws Throwable {
        safeDelete(computeHost);
        computeHost = null;

        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();

        ResourcePoolState defaultResourcePool = getDocument(ResourcePoolState.class,
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);

        try {
            createDockerHost(dockerHostDesc, resourcePool);

            // setup Container desc:
            ContainerDescription containerDesc = createContainerDescription();

            // setup Group Placement:
            GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                    resourcePool);

            GroupResourcePlacementState groupPlacementState2 = TestRequestStateFactory
                    .createGroupResourcePlacementState();
            groupPlacementState2.memoryLimit = 0;
            groupPlacementState2.resourcePoolLink = defaultResourcePool.documentSelfLink;
            groupPlacementState2 = doPost(groupPlacementState2,
                    GroupResourcePlacementService.FACTORY_LINK);

            // 1. Request a container instance:
            RequestBrokerState request = TestRequestStateFactory.createRequestState();
            request.resourceDescriptionLink = containerDesc.documentSelfLink;
            request.tenantLinks = groupPlacementState.tenantLinks;
            host.log("########  Start of request ######## ");
            request = startRequest(request);

            // wait for request completed state:
            waitForRequestToComplete(request);
        } finally {
            safeDelete(computeHost);
            computeHost = null;
        }

    }

    @Test
    public void testContainerShouldNotBeDeployedWhenHostIsSuspended() throws Throwable {
        // Create a docker hosts
        ResourcePoolState resourcePool = createResourcePool();
        ComputeState dockerHost = createDockerHost(createDockerHostDescription(), resourcePool);

        ComputeState computeState = new ComputeState();
        computeState.powerState = PowerState.SUSPEND;

        // Disable the hosts
        doOperation(computeState,
                UriUtils.buildUri(host, dockerHost.documentSelfLink), false,
                Action.PATCH);

        // Try to deploy a container
        ContainerDescription containerDescription = createContainerDescription();
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_TYPE.getName();
        request.resourceDescriptionLink = containerDescription.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        request = startRequest(request);
        request = waitForRequestToFail(request);

        assertTrue(request.taskInfo.failure.message.startsWith(
                "No powered-on container hosts found"));

        // check the custom property from container description
        assertNotNull(request.customProperties);
        String containerDescProp = request.customProperties.get("propKey string");
        assertNotNull(containerDescProp);
        assertEquals("customPropertyValue string", containerDescProp);
    }

    @Test
    public void testContainerShouldBeDeployedOnHostWithStateOn() throws Throwable {
        // Create docker hosts
        ResourcePoolState resourcePool = createResourcePool();
        List<ComputeState> containerHostsToSuspend = new ArrayList<>();
        String testHostSelfLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK,
                CommonTestStateFactory.DOCKER_COMPUTE_ID);

        containerHostsToSuspend.add(getDocument(ComputeState.class, testHostSelfLink));
        containerHostsToSuspend
                .add(createDockerHost(createDockerHostDescription(), resourcePool, true));
        containerHostsToSuspend
                .add(createDockerHost(createDockerHostDescription(), resourcePool, true));
        ComputeState activeDockerHost = createDockerHost(createDockerHostDescription(),
                resourcePool, true);

        // Leave only 1 with power state ON
        ComputeState computeState = new ComputeState();
        computeState.powerState = PowerState.ON;
        doOperation(computeState,
                UriUtils.buildUri(host, activeDockerHost.documentSelfLink), false,
                Action.PATCH);

        computeState.powerState = PowerState.SUSPEND;
        for (ComputeState containerHost : containerHostsToSuspend) {
            doOperation(computeState,
                    UriUtils.buildUri(host, containerHost.documentSelfLink), false,
                    Action.PATCH);
        }

        // Deploy a container
        ContainerDescription containerDescription = createContainerDescription();
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_TYPE.getName();
        request.resourceDescriptionLink = containerDescription.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        request = startRequest(request);
        request = waitForRequestToComplete(request);

        ContainerState containerState = getDocument(ContainerState.class,
                request.resourceLinks.iterator().next());

        // The container should be deployed on the host with power state ON
        assertEquals(containerState.parentLink, activeDockerHost.documentSelfLink);
    }

    @Test
    public void testShouldPublishEventLogOnTaskFailure() throws Throwable {
        // Create a docker hosts
        ResourcePoolState resourcePool = createResourcePool();
        ComputeState dockerHost = createDockerHost(createDockerHostDescription(), resourcePool);

        ComputeState computeState = new ComputeState();
        computeState.powerState = PowerState.SUSPEND;

        // Disable the hosts
        doOperation(computeState,
                UriUtils.buildUri(host, dockerHost.documentSelfLink), false,
                Action.PATCH);

        // Try to deploy a container
        ContainerDescription containerDescription = createContainerDescription();
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_TYPE.getName();
        request.resourceDescriptionLink = containerDescription.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        request = startRequest(request);
        request = waitForRequestToFail(request);
        assertTrue(request.taskInfo.failure.message.startsWith(
                "No powered-on container hosts found"));

        // the event is created after the task is failed, wait for it to be created
        final String requestTrackerLink = request.requestTrackerLink;
        waitFor(() -> {
            RequestStatus rs = getDocument(RequestStatus.class, requestTrackerLink);
            return rs.eventLogLink != null;
        });

        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs.eventLogLink);
        EventLogState el = getDocument(EventLogState.class, rs.eventLogLink);
        assertNotNull(el);
        assertEquals(EventLogType.ERROR, el.eventLogType);
    }

    @Test
    public void testContainerStateShouldBeRemovedAfterFailure() throws Throwable {
        // Create docker hosts
        ResourcePoolState resourcePool = createResourcePool();
        // try to deploy a container
        ContainerDescription containerDescription = createContainerDescription();
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        // stop the adapter service
        MockDockerAdapterService service = new MockDockerAdapterService();
        service.setSelfLink(MockDockerAdapterService.SELF_LINK);
        host.stopService(service);

        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_TYPE.getName();
        request.resourceDescriptionLink = containerDescription.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        request = startRequest(request);
        request = waitForRequestToFail(request);
        ContainerState containerState = searchForDocument(ContainerState.class,
                request.resourceLinks.iterator().next());
        assertNull(containerState);
    }

    @Test
    // Issue VSYM-994 - Request tracker link is empty in the response. UI does not refresh after
    // day2 operation on containers.
    public void testRequestTrackerLinkShouldBeReturnedAndCreated() throws Throwable {
        // setup Docker Host:
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        createDockerHost(dockerHostDesc, resourcePool);

        // setup Container desc:
        ContainerDescription containerDesc = createContainerDescription();

        // setup Group Placement:
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        // Request a container instance:
        RequestBrokerState request = TestRequestStateFactory.createRequestState();
        request.resourceDescriptionLink = containerDesc.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        String requestTrackerLink = request.requestTrackerLink;

        assertNotNull("Request tracker link should not be null!", requestTrackerLink);
        assertFalse("Request tracker link should not be null!", requestTrackerLink.isEmpty());

        waitFor("request tracker stage was not updated", () -> {
            RequestStatus requestStatus = getDocument(RequestStatus.class, requestTrackerLink);
            assertNotNull(requestStatus);
            return requestStatus.taskInfo.stage.ordinal() > TaskStage.CREATED.ordinal();
        });
    }

    @Test
    // Issue VSYM-443
    public void testGroupResourcePlacementAfterFailedProvisionOperation() throws Throwable {
        ResourcePoolState resourcePool = createResourcePool();
        ContainerDescription containerDescription = createContainerDescription();
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);
        long allocatedInstancesCount = groupPlacementState.allocatedInstancesCount;
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_TYPE.getName();
        request.resourceDescriptionLink = containerDescription.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;

        request = startRequest(request);
        waitForRequestToComplete(request);

        groupPlacementState = getDocument(GroupResourcePlacementState.class,
                groupPlacementState.documentSelfLink);
        assertEquals("Allocated instances count is not correct",
                groupPlacementState.allocatedInstancesCount, allocatedInstancesCount + 1);

        // stop the adapter service
        MockDockerAdapterService service = new MockDockerAdapterService();
        service.setSelfLink(MockDockerAdapterService.SELF_LINK);
        host.stopService(service);

        request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_TYPE.getName();
        request.resourceDescriptionLink = containerDescription.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;
        request = startRequest(request);
        waitForRequestToFail(request);

        // verify available instances are not changed after the failure
        groupPlacementState = getDocument(GroupResourcePlacementState.class,
                groupPlacementState.documentSelfLink);
        assertEquals("Allocated instances count is not correct after a failed provisioning",
                groupPlacementState.allocatedInstancesCount, allocatedInstancesCount + 1);
    }

    @Test
    public void testGroupResourcePlacementAfterSuccessfulProvisionOperation() throws Throwable {
        ResourcePoolState resourcePool = createResourcePool();
        ContainerDescription containerDescription = createContainerDescription();
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);
        long allocatedInstancesCount = groupPlacementState.allocatedInstancesCount;
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_TYPE.getName();
        request.resourceDescriptionLink = containerDescription.documentSelfLink;
        request.tenantLinks = groupPlacementState.tenantLinks;

        request = startRequest(request);
        request = waitForRequestToComplete(request);
        Set<String> resourceLinks = request.resourceLinks;

        groupPlacementState = getDocument(GroupResourcePlacementState.class,
                groupPlacementState.documentSelfLink);
        assertEquals("Allocated instances count was not increased after provisioning",
                groupPlacementState.allocatedInstancesCount, allocatedInstancesCount + 1);

        request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_TYPE.getName();
        request.resourceDescriptionLink = containerDescription.documentSelfLink;
        request.resourceLinks = resourceLinks;
        request.tenantLinks = groupPlacementState.tenantLinks;
        request.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
        request = startRequest(request);
        waitForRequestToComplete(request);

        // verify available instances are not changed after the failure
        groupPlacementState = getDocument(GroupResourcePlacementState.class,
                groupPlacementState.documentSelfLink);
        assertEquals("Placement was not cleaned",
                groupPlacementState.allocatedInstancesCount, allocatedInstancesCount);
    }

    @Test
    public void testDeletingRequestBrokerShouldDeleteAssociatedRequestStatus() throws Throwable {
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

        String requestTrackerLink = request.requestTrackerLink;
        RequestStatus requestStatus = searchForDocument(RequestStatus.class, requestTrackerLink);
        assertNotNull(requestStatus);

        delete(request.documentSelfLink);

        waitFor("RequestStatus wasn't deleted: " + requestStatus.documentSelfLink, () -> {
            RequestStatus reqStatus = searchForDocument(RequestStatus.class, requestTrackerLink);
            return reqStatus == null;
        });
    }

    @Test
    public void testCompositeComponentWithContainerServiceLinks() throws Throwable {
        CompositeComponent cc = setUpCompositeWithServiceLinks(false);

        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();
        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerFactoryService.SELF_LINK + "/container1")) {
                containerLink1 = link;
            } else if (link.startsWith(ContainerFactoryService.SELF_LINK + "/container2")) {
                containerLink2 = link;
            }
        }

        ContainerState cont1 = getDocument(ContainerState.class, containerLink1);
        ContainerState cont2 = getDocument(ContainerState.class, containerLink2);

        String[] links = cont2.links;

        assertEquals(1, links.length);
        assertEquals(cont1.names.get(0) + ":mycontainer", cont2.links[0]);

        // Containers are placed on a single host when using "legacy" links
        assertTrue(cont1.parentLink.equals(cont2.parentLink));
    }

    @Test
    public void testCompositeComponentWithContainerServiceLinksAndNetwork() throws Throwable {
        CompositeComponent cc = setUpCompositeWithServiceLinks(true);

        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();
        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerFactoryService.SELF_LINK + "/container1")) {
                containerLink1 = link;
            } else if (link.startsWith(ContainerFactoryService.SELF_LINK + "/container2")) {
                containerLink2 = link;
            }
        }

        ContainerState cont1 = getDocument(ContainerState.class, containerLink1);
        assertNotNull(cont1);

        ContainerState cont2 = getDocument(ContainerState.class, containerLink2);
        String[] links = cont2.networks.values().iterator().next().links;

        assertEquals(1, links.length);
        assertEquals("container1:mycontainer", links[0]);

        // Containers are placed on multiple hosts when using user define network links
    }

    @Test
    public void testConfigureHost() throws Throwable {
        getOrCreateDocument(CommonTestStateFactory.createAuthCredentials(false),
                AuthCredentialsService.FACTORY_LINK);
        createResourcePool();

        // 1. Request to configure host:
        RequestBrokerState request = TestRequestStateFactory.createConfigureHostState();
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // wait for request completed state:
        request = waitForRequestToComplete(request);
    }

    private CompositeComponent setUpCompositeWithServiceLinks(boolean includeNetwork)
            throws Throwable {
        // setup Docker Host:
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();

        delete(computeHost.documentSelfLink);
        computeHost = null;

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription("container1");
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.portBindings = null;

        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription("container2");
        container2Desc.documentSelfLink = UUID.randomUUID().toString();
        container2Desc.links = new String[] { "container1:mycontainer" };
        container1Desc.portBindings = null;

        CompositeDescription compositeDesc;
        if (includeNetwork) {
            ContainerNetworkDescription networkDesc = TestRequestStateFactory
                    .createContainerNetworkDescription("testnet");
            networkDesc.documentSelfLink = UUID.randomUUID().toString();

            container1Desc.networks = Collections.singletonMap(networkDesc.name,
                    new ServiceNetwork());
            container2Desc.networks = Collections.singletonMap(networkDesc.name,
                    new ServiceNetwork());

            compositeDesc = createCompositeDesc(networkDesc, container1Desc, container2Desc);
        } else {
            compositeDesc = createCompositeDesc(container1Desc, container2Desc);
        }

        assertNotNull(compositeDesc);

        // setup Group Placement:
        GroupResourcePlacementState groupPlacememtState = createGroupResourcePlacement(
                resourcePool);

        // 1. Request a composite container:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);
        request.tenantLinks = groupPlacememtState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // wait for request completed state:
        request = waitForRequestToComplete(request);

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);

        assertEquals(Integer.valueOf(100), rs.progress);
        assertEquals(1, request.resourceLinks.size());

        return getDocument(CompositeComponent.class, request.resourceLinks.iterator().next());
    }

}
