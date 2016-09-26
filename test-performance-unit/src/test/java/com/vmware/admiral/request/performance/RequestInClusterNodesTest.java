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

package com.vmware.admiral.request.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
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
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class RequestInClusterNodesTest extends RequestBaseTest {
    private static final int NODE_COUNT = Integer.getInteger(
            "test.cluster.performance.node.count", 3);
    private static final int CONCURRENT_REQUESTS_COUNT = Integer.getInteger(
            "test.cluster.performance.concurrent.requests.count", 1);
    private static final int ITERATIONS_COUNT = Integer.getInteger(
            "test.cluster.performance.count", 5);
    private static final int DOCKER_HOST_COUNT = Integer.getInteger(
            "test.cluster.performance.docker.host.count", 10);
    private final Object initializationLock = new Object();

    private final ExecutorService executor = Executors
            .newFixedThreadPool(CONCURRENT_REQUESTS_COUNT);

    private List<VerificationHost> hosts;

    final AtomicInteger containerRequestsCount = new AtomicInteger();
    int containerRequestVerifiedCount;
    int removeRequestsCount;
    int removeRequestsVerifiedCount;

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
        int numberOfInstances = 2 * ITERATIONS_COUNT;
        groupPlacementState = createGroupResourcePlacement(resourcePool, numberOfInstances);
    }

    @Override
    protected ContainerDescription createContainerDescription() throws Throwable {
        synchronized (initializationLock) {
            ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
            desc.documentSelfLink = UUID.randomUUID().toString();
            desc.portBindings = null;
            ContainerDescription containerDesc = doPost(desc,
                    ContainerDescriptionService.FACTORY_LINK);
            assertNotNull(containerDesc);
            return containerDesc;
        }
    }

    protected void createMultipleDockerHosts() throws Throwable {
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        synchronized (super.initializationLock) {
            for (int i = 0; i < DOCKER_HOST_COUNT; i++) {
                createDockerHost(dockerHostDesc, resourcePool, true);
            }
        }
    }

    public void tearDown() throws Throwable {

        host.log("**************************************************************************");
        host.log("**************************************************************************");
        host.log("**** Requested: [%s], RequestedCount: [%s], RequestedVerified: [%s]****",
                ITERATIONS_COUNT, containerRequestsCount.get(), containerRequestVerifiedCount);
        host.log("**** RemoveRequested: [%s], RemoveVerified: [%s].                   *****",
                removeRequestsCount, removeRequestsVerifiedCount);
        host.log("**************************************************************************");
        host.log("**************************************************************************");
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    private void waitForReplicatedFactoryServiceAvailable(VerificationHost h) throws Throwable {
        for (String factoryLink : getFactoryServiceList()) {
            h.waitForReplicatedFactoryServiceAvailable(UriUtils.buildUri(h, factoryLink));
        }
    }

    public void addPeerNodes(int nodeCount) throws Throwable {
        hosts = new ArrayList<>(nodeCount);
        hosts.add(host);
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
    public void testRequestLifeCycle() throws Throwable {
        long timeoutInMicros = getTimeoutInMicros();

        assertTrue(ITERATIONS_COUNT > CONCURRENT_REQUESTS_COUNT);
        final Map<String, RequestBrokerState> containerRequests = sendProvisioningRequests();
        List<RequestBrokerState> containerRequestsValues = waitForRequestsToComplete(
                timeoutInMicros, containerRequests);

        testContainersShouldNotBeInUnknownStateAfterProvisioning();
        testContainersShouldNotBeInProvisioningStateAfterProvisioning();
        testContainersCountShouldBeEqualToContainerRequests();
        testDataShouldBeConsistentAcrossNodes();

        List<RequestBrokerState> removeRequests = removeContainers(timeoutInMicros,
                containerRequests);

        testContainersShouldBeRemoved();

        assertTrue("Not all container requests completed: " + containerRequestsValues.size(),
                containerRequestsValues.isEmpty());
        assertTrue("Not all remove requests completed: " + removeRequests.size(),
                removeRequests.isEmpty());

    }

    private void testContainersShouldBeRemoved() {
        TestContext ctx = testCreate(1);
        QueryTask q = QueryUtil.buildQuery(ContainerState.class, false);
        QueryUtil.addCountOption(q);

        new ServiceDocumentQuery<ContainerState>(host, ContainerState.class)
                .query(q,
                        (r) -> {
                            if (r.hasException()) {
                                ctx.failIteration(r.getException());
                            } else if (r.hasResult()) {
                                ctx.failIteration(new IllegalStateException(
                                        "Containers were not removed properly. Found containers: "
                                                + r.getCount()));
                            } else {
                                ctx.completeIteration();
                            }
                        });
        ctx.await();
    }

    private void testContainersShouldNotBeInUnknownStateAfterProvisioning() throws Throwable {

        TestContext ctx = testCreate(1);
        QueryTask q = QueryUtil.buildPropertyQuery(ContainerState.class, "powerState",
                PowerState.UNKNOWN.toString());
        QueryUtil.addCountOption(q);

        new ServiceDocumentQuery<ContainerState>(host, ContainerState.class)
                .query(q,
                        (r) -> {
                            if (r.hasException()) {
                                ctx.failIteration(r.getException());
                            } else if (r.hasResult()) {
                                ctx.failIteration(new IllegalStateException(r.getCount() +
                                        " containers in state UNKNOWN"));
                            } else {
                                ctx.completeIteration();
                            }
                        });
        ctx.await();
    }

    private long getTimeoutInMicros() {
        long timeoutInMicros = TimeUnit.SECONDS.toMicros(ITERATIONS_COUNT);
        if (ITERATIONS_COUNT < 10) {
            timeoutInMicros = TimeUnit.SECONDS.toMicros(10);
        } else if (ITERATIONS_COUNT > 100) {
            timeoutInMicros = TimeUnit.SECONDS.toMicros(120);
        }
        return timeoutInMicros;
    }

    private void testContainersShouldNotBeInProvisioningStateAfterProvisioning() throws Throwable {

        TestContext ctx = testCreate(1);
        QueryTask q = QueryUtil.buildPropertyQuery(ContainerState.class, "powerState",
                PowerState.PROVISIONING.toString());
        QueryUtil.addCountOption(q);

        new ServiceDocumentQuery<ContainerState>(host, ContainerState.class)
                .query(q,
                        (r) -> {
                            if (r.hasException()) {
                                ctx.failIteration(r.getException());
                            } else if (r.hasResult()) {
                                ctx.failIteration(new IllegalStateException(r.getCount() +
                                        " containers in state PROVISIONING after the requests are finished"));
                            } else {
                                ctx.completeIteration();
                            }
                        });
        ctx.await();
    }

    private void testContainersCountShouldBeEqualToContainerRequests() throws Throwable {

        TestContext ctx = testCreate(1);
        QueryTask q = QueryUtil.buildQuery(ContainerState.class, false);
        QueryUtil.addCountOption(q);

        new ServiceDocumentQuery<ContainerState>(host, ContainerState.class)
                .query(q,
                        (r) -> {
                            if (r.hasException()) {
                                ctx.failIteration(r.getException());
                            } else if (r.hasResult()) {
                                if (r.getCount() == ITERATIONS_COUNT) {
                                    ctx.completeIteration();
                                } else {
                                    ctx.failIteration(new IllegalStateException(
                                            "Expected " + ITERATIONS_COUNT +
                                                    " containers but found " + r.getCount()));
                                }
                            } else {
                                ctx.failIteration(new IllegalStateException(
                                        "No containers found after the provisioning"));
                            }
                        });
        ctx.await();
    }

    private void testDataShouldBeConsistentAcrossNodes() throws Throwable {
        int retryCount = 10;

        TestContext ctx = testCreate(retryCount);
        List<Long> counts = new ArrayList<Long>();

        // Give the cluster some time in order to sync the data.
        Thread.sleep(20000);
        QueryTask q = QueryUtil.buildPropertyQuery(ContainerState.class, "powerState",
                PowerState.RUNNING.toString());
        QueryUtil.addCountOption(q);

        for (int i = 0; i < retryCount; i++) {
            new ServiceDocumentQuery<ContainerState>(host, ContainerState.class)
                    .query(q,
                            (r) -> {
                                if (r.hasException()) {
                                    ctx.failIteration(r.getException());
                                } else {
                                    counts.add(r.getCount());
                                    ctx.completeIteration();
                                }
                            });
        }
        ctx.await();
        long first = counts.get(0);
        for (int i = 1; i < retryCount; i++) {
            assertTrue("Data across nodes in inconsistent", first == counts.get(i));
        }
    }

    private List<RequestBrokerState> waitForRequestsToComplete(long timeoutInMicros,
            final Map<String, RequestBrokerState> containerRequests)
            throws Throwable, InterruptedException {
        List<RequestBrokerState> containerRequestsValues = new ArrayList<>(
                containerRequests.values());

        long timeout = Utils.getNowMicrosUtc() + timeoutInMicros;
        while (timeout > Utils.getNowMicrosUtc()) {
            for (Iterator<RequestBrokerState> itr = containerRequestsValues.iterator(); itr
                    .hasNext();) {
                RequestBrokerState containerRequest = getDocument(RequestBrokerState.class,
                        itr.next().documentSelfLink);
                if (RequestBrokerState.SubStage.COMPLETED == containerRequest.taskSubStage &&
                        TaskStage.FINISHED == containerRequest.taskInfo.stage) {
                    itr.remove();
                    verifyContainerRequest(containerRequest);
                    containerRequestVerifiedCount++;
                    containerRequests.put(containerRequest.documentSelfLink, containerRequest);
                    host.log("Container request completed for: %s. Total completed: %s",
                            containerRequest.documentSelfLink, containerRequestVerifiedCount);
                } else if (RequestBrokerState.SubStage.ERROR == containerRequest.taskSubStage) {
                    host.log("Container request failed for: %s.",
                            containerRequest.documentSelfLink);
                    itr.remove();
                    containerRequests.remove(containerRequest.documentSelfLink);
                    //take it off from the list of requests to be removed.
                } else {
                    host.log("Container request %s in stage: %s.",
                            containerRequest.documentSelfLink, containerRequest.taskSubStage);
                }
            }
            if (containerRequestsValues.isEmpty()) {
                break;
            }
            host.log(
                    "Waiting for all container requests left %s to complete. Total completed: %s",
                    containerRequestsValues.size(), containerRequestVerifiedCount);
            Thread.sleep(WAIT_THREAD_SLEEP_IN_MILLIS);
        }

        host.log("########  All requests completed and verified ######## ");
        return containerRequestsValues;
    }

    private Map<String, RequestBrokerState> sendProvisioningRequests()
            throws InterruptedException, ExecutionException {
        int countPerThread = ITERATIONS_COUNT / CONCURRENT_REQUESTS_COUNT;
        int adjustedThreadCount = countPerThread
                + (ITERATIONS_COUNT - countPerThread * CONCURRENT_REQUESTS_COUNT);

        host.log("########  Starting all requests %s ######## ", ITERATIONS_COUNT);
        final Map<String, RequestBrokerState> containerRequests = new HashMap<>(ITERATIONS_COUNT);

        final List<Future<?>> futures = new ArrayList<>(ITERATIONS_COUNT);
        for (int i = 0; i < CONCURRENT_REQUESTS_COUNT; i++) {
            int count = i == 0 ? adjustedThreadCount : countPerThread;
            Future<?> future = executor.submit(() -> {
                for (int j = 0; j < count; j++) {
                    host.log("########  Start of testRequestLifeCycle [%s] ######## ", j);
                    RequestBrokerState containerRequest = createContainerRequest();
                    containerRequests.put(containerRequest.documentSelfLink, containerRequest);
                    containerRequestsCount.incrementAndGet();
                }
            });
            futures.add(future);
        }

        // wait for all submissions to finish.
        for (Future<?> future : futures) {
            future.get();
        }

        assertEquals(ITERATIONS_COUNT, containerRequests.size());

        host.log("########  All requests started. ######## ");
        return containerRequests;
    }

    private List<RequestBrokerState> removeContainers(long timeoutInMicros,
            final Map<String, RequestBrokerState> containerRequests)
            throws Throwable, InterruptedException {
        long timeout;
        List<RequestBrokerState> removeRequests = new ArrayList<>();
        for (RequestBrokerState containerRequest : containerRequests.values()) {
            RequestBrokerState removeRequest = removeRequest(containerRequest);
            removeRequests.add(removeRequest);
            removeRequestsCount++;
        }

        host.log("########  Removing request in progress... ######## ");
        timeout = Utils.getNowMicrosUtc() + timeoutInMicros;
        while (timeout > Utils.getNowMicrosUtc()) {
            for (Iterator<RequestBrokerState> itr = removeRequests.iterator(); itr.hasNext();) {
                RequestBrokerState removeRequest = itr.next();
                removeRequest = getDocument(RequestBrokerState.class,
                        removeRequest.documentSelfLink);
                if (RequestBrokerState.SubStage.COMPLETED == removeRequest.taskSubStage) {
                    itr.remove();
                    verifyRemoveRequest(removeRequest);
                    removeRequestsVerifiedCount++;
                    host.log("Remove request completed for: %s. Total completed: %s",
                            removeRequest.documentSelfLink, removeRequestsVerifiedCount);
                } else if (RequestBrokerState.SubStage.ERROR == removeRequest.taskSubStage) {
                    host.log("Remove request failed for: %s.", removeRequest.documentSelfLink);
                    itr.remove();
                } else {
                    host.log("Remove request %s in stage: %s.", removeRequest.documentSelfLink,
                            removeRequest.taskSubStage);
                }
            }
            if (removeRequests.isEmpty()) {
                break;
            }
            host.log(
                    "Waiting for all remove requests left %s to complete. Total completed: %s",
                    removeRequests.size(), removeRequestsVerifiedCount);
            Thread.sleep(WAIT_THREAD_SLEEP_IN_MILLIS);
        }
        return removeRequests;
    }

    private RequestBrokerState removeRequest(RequestBrokerState containerRequest) throws Throwable {
        // 4. Remove the container
        RequestBrokerState removeRequest = TestRequestStateFactory.createRequestState();
        removeRequest.operation = ContainerOperationType.DELETE.id;
        removeRequest.resourceLinks = new ArrayList<>();
        RequestBrokerState request = getDocument(RequestBrokerState.class,
                containerRequest.documentSelfLink);
        removeRequest.resourceLinks.addAll(request.resourceLinks);
        removeRequest = startRequest(removeRequest);

        return removeRequest;
    }

    private void verifyRemoveRequest(RequestBrokerState removeRequest) throws Throwable {
        ContainerState containerState = searchForDocument(ContainerState.class,
                removeRequest.resourceLinks.get(0));
        assertNull(containerState);

        // Verify request status
        String requestTrackerLink = removeRequest.requestTrackerLink;
        waitFor(() -> {
            RequestStatus rs = getDocument(RequestStatus.class, requestTrackerLink);
            assertNotNull(rs);
            return Integer.valueOf(100).equals(rs.progress);
        });
    }

    private RequestBrokerState createContainerRequest() {
        try {
            // 1. Request a container instance:
            RequestBrokerState request = TestRequestStateFactory.createRequestState();
            request.resourceDescriptionLink = createContainerDescription().documentSelfLink;
            request.tenantLinks = groupPlacementState.tenantLinks;
            host.log("########  Start of request ######## ");
            request = startRequest(request);

            // wait for request completed state:

            host.log("######## Request started for %s ######## ", request.documentSelfLink);
            return request;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private RequestBrokerState verifyContainerRequest(RequestBrokerState request) throws Throwable {
        String requestSelfLink = request.documentSelfLink;

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
        if (request == null) {
            host.log("*** request can't be retrieved, retrying... ****");
            request = getDocument(RequestBrokerState.class, requestSelfLink);
        }
        assertNotNull("ResourceLinks null for requestSelfLink: " + requestSelfLink,
                request.resourceLinks);
        assertEquals(1, request.resourceLinks.size());
        ContainerState containerState = getDocument(ContainerState.class,
                request.resourceLinks.get(0));
        assertNotNull(containerState);

        // Verify request status
        String requestTrackerLink = request.requestTrackerLink;
        waitFor(() -> {
            RequestStatus rs = getDocument(RequestStatus.class, requestTrackerLink);
            assertNotNull(rs);
            return Integer.valueOf(100).equals(rs.progress);
        });

        return request;
    }
}
