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

package com.vmware.admiral.host;

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.admiral.host.ManagementHostAuthUsersTest.doRestrictedOperation;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.login;
import static com.vmware.admiral.service.common.AuthBootstrapService.waitForInitConfig;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;

/**
 * Similar to {@link ManagementHostAuthUsersIT} but this test includes 2 nodes, only SSL enabled and
 * the users' passwords are encrypted.
 */
@Ignore("VBV-1018")
public class ManagementHostClusterOf2NodesIT extends BaseManagementHostClusterIT {

    private ManagementHost hostOne;
    private ManagementHost hostTwo;

    private static final int PORT_ONE = 20000 + new Random().nextInt(1000);
    private static final int PORT_TWO = PORT_ONE + 1;

    private static final String HOST_ONE = LOCALHOST + PORT_ONE;
    private static final String HOST_TWO = LOCALHOST + PORT_TWO;
    private static final List<String> ALL_HOSTS = asList(HOST_ONE, HOST_TWO);

    @Before
    public void setUp() throws Throwable {
        hostOne = setUpHost(PORT_ONE, null, ALL_HOSTS);
        hostTwo = setUpHost(PORT_TWO, null, ALL_HOSTS);

        waitForInitConfig(hostOne, hostOne.localUsers);
        waitForInitConfig(hostTwo, hostTwo.localUsers);

        // Initialize provisioning context
        initializeProvisioningContext(hostOne);
    }

    @After
    public void tearDown() throws Throwable {
        tearDownHost(hostOne, hostTwo);
    }

    @Test
    public void testInvalidAccess() throws Throwable {

        // Invalid credentials

        assertNull(login(hostOne, USERNAME, "bad"));
        assertNull(login(hostOne, "bad", PASSWORD));

        assertNull(login(hostTwo, USERNAME, "bad"));
        assertNull(login(hostTwo, "bad", PASSWORD));

        // Restricted operation without authentication

        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, doRestrictedOperation(hostOne, null));
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, doRestrictedOperation(hostTwo, null));
    }

    @Test
    public void testRestrictedOperationWithNodesRestarted() throws Throwable {

        /*
         * Within this test the nodes are restarted but not at the same time, there's always a
         * running node.
         */

        String tokenOne = login(hostOne, USERNAME, PASSWORD);
        assertClusterWithToken(tokenOne, hostOne, hostTwo);

        String tokenTwo = login(hostTwo, USERNAME, PASSWORD);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo);

        List<ManagementHost> hosts = asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts, tokenOne);

        /*
         * ==== Restart node1 ====================================================================
         */

        stopHostAndRemoveItFromNodeGroup(hostTwo, hostOne);

        // We should explicitly set the quorum in the running node to 1 here, but now the
        // ClusterMonitoringService will take care of that. Otherwise the next operation will hang.

        assertClusterWithToken(tokenTwo, hostTwo);
        assertClusterFromNodes(hostTwo);

        hostOne = startHost(hostOne, null, ALL_HOSTS);

        assertClusterWithToken(tokenOne, hostOne, hostTwo);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo);
        assertClusterFromNodes(hostOne, hostTwo);

        hosts = asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts, tokenOne);

        /*
         * ==== Restart node2 ====================================================================
         */

        stopHostAndRemoveItFromNodeGroup(hostOne, hostTwo);

        // We should explicitly set the quorum in the running node to 1 here, but now the
        // ClusterMonitoringService will take care of that. Otherwise the next operation will hang.

        assertClusterWithToken(tokenOne, hostOne);
        assertClusterFromNodes(hostOne);

        hostTwo = startHost(hostTwo, null, ALL_HOSTS);

        assertClusterWithToken(tokenOne, hostOne, hostTwo);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo);
        assertClusterFromNodes(hostOne, hostTwo);

        hosts = asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts, tokenOne);
    }

    @Test
    public void testRestrictedOperationWithNodesStoppedAndStarted() throws Throwable {

        /*
         * Within this test the nodes are restarted at the same time, both nodes are stopped and
         * then started again.
         */

        String tokenOne = login(hostOne, USERNAME, PASSWORD);
        assertClusterWithToken(tokenOne, hostOne, hostTwo);

        String tokenTwo = login(hostTwo, USERNAME, PASSWORD);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo);

        List<ManagementHost> hosts = asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts, tokenOne);

        /*
         * ==== Stop both nodes ==================================================================
         */

        stopHostAndRemoveItFromNodeGroup(hostTwo, hostOne);
        stopHost(hostTwo);

        /*
         * ==== Start both nodes =================================================================
         */

        // start 1st node with quorum = 1 (peer nodes = self)
        hostOne = startHost(hostOne, hostOne.getStorageSandbox(), asList(HOST_ONE));
        // start 2nd node with quorum = 2 (peer nodes = self & 1st node )
        hostTwo = startHost(hostTwo, null, ALL_HOSTS);

        assertClusterWithToken(tokenOne, hostOne, hostTwo);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo);

        assertClusterFromNodes(hostOne, hostTwo);

        hosts = asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts, tokenOne);
    }

    @Test
    public void testReplicationOfDocumentsAfterRestart() throws Throwable {

        Map<String, String> headers = new HashMap<>();
        headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, login(hostOne, USERNAME, PASSWORD));

        assertContainerDescription(hostOne, headers);
        assertContainerDescription(hostTwo, headers);

        // Restart hostTwo
        stopHost(hostTwo);
        hostTwo = startHost(hostTwo, null, ALL_HOSTS);

        // Get token from hostOne
        headers = new HashMap<>();
        headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, login(hostOne, USERNAME, PASSWORD));

        assertContainerDescription(hostOne, headers);
        assertContainerDescription(hostTwo, headers);

        // Get token from hostTwo
        headers = new HashMap<>();
        headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, login(hostTwo, USERNAME, PASSWORD));

        assertContainerDescription(hostOne, headers);
        assertContainerDescription(hostTwo, headers);
    }

    @Test
    public void testProvisioningOfContainerInCluster() throws Throwable {

        Map<String, String> headers = getAuthenticationHeaders(hostOne);

        TestContext waiter = new TestContext(1, Duration.ofSeconds(30));
        disableDataCollection(hostOne, headers.get("x-xenon-auth-token"), waiter);
        waiter.await();

        waiter = new TestContext(1, Duration.ofSeconds(30));
        disableDataCollection(hostTwo, headers.get("x-xenon-auth-token"), waiter);
        waiter.await();

        // 1. Request a container instance:
        RequestBrokerState request = TestRequestStateFactory
                .createRequestState(ResourceType.CONTAINER_TYPE.getName(), "test-container-desc");
        request.resourceDescriptionLink = ContainerDescriptionService.FACTORY_LINK
                + "/test-container-desc";
        request.tenantLinks = groupResourcePlacementState.tenantLinks;
        request.documentSelfLink = "test-request";
        hostOne.log(Level.INFO, "########  Start of request ######## ");
        startRequest(headers, hostOne, request);

        URI uri = UriUtils.buildUri(hostOne,
                RequestBrokerFactoryService.SELF_LINK + "/" + request.documentSelfLink);

        // 2. Wait for provisioning request to finish
        RequestJSONResponseMapper response = waitTaskToCompleteAndGetResponse(headers, hostOne,
                uri);

        hostOne.log(Level.INFO, "########  Request finished. ######## ");

        assertNotNull(response.resourceLinks);

        assertEquals(1, response.resourceLinks.size());
        String resourceLink = response.resourceLinks.get(0);

        // Get the container from second host, the document should be replicated.
        URI containersUri = UriUtils.buildUri(hostTwo, resourceLink);
        String containerAsJson = null;

        try {
            containerAsJson = getResource(hostTwo, headers, containersUri);
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "Exception appears while trying to get a container %s ", containersUri));
        }
        assertNotNull(containerAsJson);
        ContainerJSONResponseMapper container = Utils.fromJson(containerAsJson,
                ContainerJSONResponseMapper.class);
        assertNotNull(container);
        assertEquals(ContainerDescriptionService.FACTORY_LINK + "/test-container-desc",
                container.descriptionLink);
        assertEquals(PowerState.RUNNING.toString(), container.powerState);

    }

    @Test
    public void testProvisioningOfApplicationInCluster() throws Throwable {

        Map<String, String> headers = getAuthenticationHeaders(hostOne);

        TestContext waiter = new TestContext(1, Duration.ofSeconds(30));
        disableDataCollection(hostOne, headers.get("x-xenon-auth-token"), waiter);
        waiter.await();

        waiter = new TestContext(1, Duration.ofSeconds(30));
        disableDataCollection(hostTwo, headers.get("x-xenon-auth-token"), waiter);
        waiter.await();

        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.name = "container1";
        container1Desc.networks = new HashMap<>();

        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription();
        container2Desc.documentSelfLink = UUID.randomUUID().toString();
        container2Desc.name = "container2";
        container2Desc.portBindings = null;

        CompositeDescription compositeDesc = createCompositeDesc(headers, hostOne, container1Desc,
                container2Desc);

        // 1. Request a composite container:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);
        request.documentSelfLink = UUID.randomUUID().toString();
        request.tenantLinks = groupResourcePlacementState.tenantLinks;
        startRequest(headers, hostOne, request);

        URI uri = UriUtils.buildUri(hostOne,
                RequestBrokerFactoryService.SELF_LINK + "/" + request.documentSelfLink);

        // 2. Wait for provisioning request to finish
        RequestJSONResponseMapper response = waitTaskToCompleteAndGetResponse(headers, hostOne,
                uri);

        hostOne.log(Level.INFO, "########  Request finished. ######## ");

        assertNotNull(response.resourceLinks);

        // Resource links should contain 1 composite-component element. Something like:
        // "resourceLinks": ["/resources/composite-components/09fc328c-aae8-4a61-9fde-0a789bd7ecd1"]
        assertEquals(1, response.resourceLinks.size());

        // Get composition from second host. It should be replicated.
        URI compositeComponentURI = UriUtils.buildUri(hostTwo, response.resourceLinks.get(0));

        String compositeComponentAsJSON = getResource(hostTwo, headers, compositeComponentURI);
        CompositeComponentJSONResponseMapper compositeComponent = Utils
                .fromJson(compositeComponentAsJSON, CompositeComponentJSONResponseMapper.class);
        assertNotNull(compositeComponent);

        assertEquals(compositeDesc.documentSelfLink, compositeComponent.compositeDescriptionLink);
        assertNotNull(compositeComponent.componentLinks);
        assertEquals(2, compositeComponent.componentLinks.size());

    }

}
