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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.ContainerAllocationTaskFactoryService;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class RequestInClusterNodesTest extends RequestBaseTest {
    private static final int NODE_COUNT = Integer.getInteger(
            "test.cluster.performance.node.count", 3);
    private static final int DOCKER_HOST_COUNT = Integer.getInteger(
            "test.cluster.performance.docker.host.count", 5);
    private List<VerificationHost> hosts;
    private Map<String, ServerX509TrustManager> trustManagers = new HashMap<>();

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
        if (hosts == null) {
            hosts = new ArrayList<>(nodeCount);
        }
        for (int i = 0; i < nodeCount; i++) {
            Map.Entry<VerificationHost, ServerX509TrustManager> entry = createHostWithTrustManager(
                    TimeUnit.SECONDS.toMicros(1));
            VerificationHost h = entry.getKey();
            hosts.add(h);
            startServices(h);
            waitForReplicatedFactoryServiceAvailable(h);
            addPeerNode(h);

            ServerX509TrustManager serverX509TrustManager = entry.getValue();
            trustManagers.put(h.getId(), serverX509TrustManager);
            serverX509TrustManager.start();
        }

        host.joinNodesAndVerifyConvergence(nodeCount);
        int quorum = (hosts.size() + 1) / 2 + 1;
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
    }

    @Test
    public void testRequestLifeCycleInClusterWhenOneNodeDown() throws Throwable {
        stopOneNode();
        provisionContainer();
    }

    @Test
    public void testCompositeComponentWithContainerNetworkRequestLifeCycleBridge()
            throws Throwable {
        provisionApplicationWithNetwork(false);
    }

    @Ignore("VBV-984")
    @Test
    public void testCompositeComponentWithContainerNetworkRequestLifeCycleBridgeOneNodeDown()
            throws Throwable {
        stopOneNode();
        provisionApplicationWithNetwork(false);
    }

    @Test
    public void testCompositeComponentWithContainerNetworkRequestLifeCycleOverlay()
            throws Throwable {
        provisionApplicationWithNetwork(true);
    }

    @Test
    public void testCompositeComponentWithContainerNetworkRequestLifeCycleOverlayOneNodeDown()
            throws Throwable {
        stopOneNode();
        provisionApplicationWithNetwork(true);
    }

    private void stopOneNode() throws Throwable {
        VerificationHost hostToStop = hosts.get(0);
        host.stopHost(hostToStop);
        waitForReplicatedFactoryServiceAvailable(host);
        hosts.remove(0);
    }

    private void provisionApplicationWithNetwork(boolean overlay) throws Throwable {
        ComputeState dockerHost1 = null;
        ComputeState dockerHost2 = null;
        if (overlay) {
            ComputeDescription dockerHostDesc = createDockerHostDescription();

            // "set" the same KV-store for the Docker Hosts created. We already have a couple of
            // hosts added and the provisioning should be on the hosts configured with KV-store
            dockerHostDesc.customProperties.put(
                    ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store");
            dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);

            dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        }

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

        if (overlay) {
            container2Desc.affinity = new String[] { "!container1:hard" };
        }

        container2Desc.networks = new HashMap<>();
        container2Desc.networks.put(networkName, new ServiceNetwork());

        CompositeDescription compositeDesc = createCompositeDesc(networkDesc, container1Desc,
                container2Desc);
        assertNotNull(compositeDesc);

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

        if (overlay) {
            boolean containerIsProvisionedOnAnyHosts = cont1.parentLink
                    .equals(dockerHost1.documentSelfLink)
                    || cont1.parentLink.equals(dockerHost2.documentSelfLink);
            assertTrue(containerIsProvisionedOnAnyHosts);

            containerIsProvisionedOnAnyHosts = cont2.parentLink.equals(dockerHost1.documentSelfLink)
                    || cont2.parentLink.equals(dockerHost2.documentSelfLink);
            assertTrue(containerIsProvisionedOnAnyHosts);
            ContainerNetworkState network = getDocument(ContainerNetworkState.class, networkLink);
            boolean networkIsProvisionedOnAnyHosts = network.originatingHostLink
                    .equals(dockerHost1.documentSelfLink)
                    || network.originatingHostLink.equals(dockerHost2.documentSelfLink);
            assertTrue(networkIsProvisionedOnAnyHosts);
            // provisioned on different hosts
            assertFalse(cont1.parentLink.equals(cont2.parentLink));
        } else {
            // provisioned on the same host
            assertTrue(cont1.parentLink.equals(cont2.parentLink));
        }

        assertEquals(cc.documentSelfLink, cont1.compositeComponentLink);
        assertEquals(cc.documentSelfLink, cont2.compositeComponentLink);
        assertTrue((getDocument(ContainerNetworkState.class, networkLink).compositeComponentLinks
                .size() == 1)
                && getDocument(ContainerNetworkState.class, networkLink).compositeComponentLinks
                .contains(cc.documentSelfLink));

        RequestBrokerState day2RemovalRequest = new RequestBrokerState();
        day2RemovalRequest.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        day2RemovalRequest.resourceLinks = new HashSet<>(Collections.singletonList(
                cc.documentSelfLink));
        day2RemovalRequest.operation = ContainerOperationType.DELETE.id;

        day2RemovalRequest = startRequest(day2RemovalRequest);
        waitForRequestToComplete(day2RemovalRequest);

        // verify the CompositeComponent has been removed
        cc = searchForDocument(CompositeComponent.class, cc.documentSelfLink);
        assertNull(cc);

        // verify the network and container states has been removed
        assertNull(searchForDocument(ContainerNetworkState.class, networkLink));
        assertNull(searchForDocument(ContainerState.class, containerLink1));
        assertNull(searchForDocument(ContainerState.class, containerLink2));

        if (dockerHost1 != null && dockerHost2 != null) {
            delete(dockerHost1.documentSelfLink);
            delete(dockerHost2.documentSelfLink);
        }
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
        containerState = searchForDocument(ContainerState.class,
                request.resourceLinks.iterator().next());
        assertNull(containerState);

        // Verify request status
        rs = getDocument(RequestStatus.class, request.requestTrackerLink);
        assertNotNull(rs);
        assertEquals(Integer.valueOf(100), rs.progress);
    }

    @Test
    public void testCertificateReplication() throws Throwable {
        //import a certificate in the first host
        SslTrustCertificateState sslTrustCert1 = new SslTrustCertificateState();
        String sslTrust1 = CommonTestStateFactory.getFileContent("test_ssl_trust.PEM").trim();
        sslTrustCert1.certificate = sslTrust1;
        sslTrustCert1.subscriptionLink = null;
        sslTrustCert1 = doPost(sslTrustCert1, SslTrustCertificateService.FACTORY_LINK);

        //add a new node with an empty db
        addPeerNodes(1);

        VerificationHost lastHost = hosts.get(hosts.size() - 1);

        //check that the corresponding trust manager of the new node has loaded the certificate
        waitFor("The certificate should be trusted", () -> {
            try {
                ServerX509TrustManager trustManager = trustManagers.get(lastHost.getId());
                X509Certificate[] certificateChain = CertificateUtil
                        .createCertificateChain(sslTrust1);
                trustManager.checkServerTrusted(certificateChain, "RSA");
                return true;
            } catch (Exception e) {
                return false;
            }
        });

    }

}
