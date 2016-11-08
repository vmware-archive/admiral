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

package com.vmware.admiral.request.cluster;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.ContainerAllocationTaskFactoryService;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class RequestInClusterNodesTest extends RequestBaseTest {
    private static final int NODE_COUNT = Integer.getInteger(
            "test.cluster.performance.node.count", 3);
    private static final int DOCKER_HOST_COUNT = Integer.getInteger(
            "test.cluster.performance.docker.host.count", 5);
    private List<VerificationHost> hosts;

    //should completely override the one in the base class
    @Override
    @Before
    public void setUp() throws Throwable {
        MockDockerAdapterService.resetContainers();
        startServices(host);
        waitForReplicatedFactoryServiceAvailable(host);
        addPeerNodes(NODE_COUNT);

        setUpDockerHostAuthentication();
        // setup Docker Host:
        createResourcePool();
        createMultipleDockerHosts();

        // setup Group Policy:
        groupPlacementState = createGroupResourcePlacement(resourcePool);
    }

    protected void createMultipleDockerHosts() throws Throwable {
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        synchronized (super.initializationLock) {
            for (int i = 0; i < DOCKER_HOST_COUNT; i++) {
                createDockerHost(dockerHostDesc, resourcePool, true);
            }
        }
    }

    private void waitForReplicatedFactoryServiceAvailable(VerificationHost h) throws Throwable {
        for (String factoryLink : getFactoryServiceList()) {
            h.waitForReplicatedFactoryServiceAvailable(UriUtils.buildUri(h, factoryLink));
        }
    }

    public void addPeerNodes(int nodeCount) throws Throwable {
        hosts = new ArrayList<>(nodeCount);
        int newNodesCount = nodeCount - 1;//the initial host is already created
        for (int i = 0; i < newNodesCount; i++) {
            VerificationHost h = createHost();
            hosts.add(h);
            startServices(h);
            waitForReplicatedFactoryServiceAvailable(h);
            addPeerNode(h);
        }

        host.joinNodesAndVerifyConvergence(nodeCount);
        int quorum = NODE_COUNT / 2 + 1;
        host.setNodeGroupQuorum(quorum);

        host.log("*****************************************************************************");
        host.log("*** All nodes with quorum [%s] are joined [%s] ***", quorum,
                hosts.stream().map((h) -> h.getUri() + " - " + h.getId())
                        .collect(Collectors.toList()));
        host.log("*****************************************************************************");
    }

    public void addPeerNode(VerificationHost h) throws Throwable {
        URI nodeGroupUri = UriUtils.buildUri(host.getPublicUri(),
                ServiceUriPaths.DEFAULT_NODE_GROUP);
        URI newNodeGroupUri = UriUtils.buildUri(h.getPublicUri(),
                ServiceUriPaths.DEFAULT_NODE_GROUP);

        host.addPeerNode(h);
        host.testStart(1);
        host.joinNodeGroup(newNodeGroupUri, nodeGroupUri, NODE_COUNT);
        host.testWait();

    }

    @Test
    public void testRequestLifeCycleInCluster() throws Throwable {
        provisionContainer();

        // stop one of the hosts in the cluster
        VerificationHost hostToStop = hosts.get(0);
        host.stopHost(hostToStop);
        waitForReplicatedFactoryServiceAvailable(host);

        provisionContainer();
    }

    private void provisionContainer() throws Throwable {
        host.log("########  Start of testRequestLifeCycle in cluster with 3 nodes ######## ");
        // setup Container desc:
        ContainerDescription containerDesc = createContainerDescription();

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
                request.resourceLinks.get(0));
        assertNotNull(containerState);

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);
        assertEquals(Integer.valueOf(100), rs.progress);

        // 4. Remove the container
        request = TestRequestStateFactory.createRequestState();
        request.operation = ContainerOperationType.DELETE.id;
        request.resourceLinks = new ArrayList<>();
        request.resourceLinks.add(containerState.documentSelfLink);
        request = startRequest(request);

        request = waitForRequestToComplete(request);

        // Verify the container is removed
        containerState = searchForDocument(ContainerState.class, request.resourceLinks.get(0));
        assertNull(containerState);

        // Verify request status
        rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);
        assertEquals(Integer.valueOf(100), rs.progress);
    }
}
