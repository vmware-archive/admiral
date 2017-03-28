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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.admiral.host.ManagementHostAuthUsersTest.AUTH_TOKEN_RETRY_COUNT;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.login;
import static com.vmware.admiral.service.common.AuthBootstrapService.waitForInitConfig;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
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

    private List<ManagementHost> hostsToTeardown;

    private ManagementHost hostOne;
    private ManagementHost hostTwo;

    private int PORT_ONE;
    private int PORT_TWO;

    private String HOST_ONE;
    private String HOST_TWO;
    private List<String> ALL_HOSTS;

    @Before
    public void setUp() throws Throwable {
        PORT_ONE = 20000 + new Random().nextInt(5000);
        PORT_TWO = PORT_ONE + 1;

        HOST_ONE = LOCALHOST + PORT_ONE;
        HOST_TWO = LOCALHOST + PORT_TWO;

        ALL_HOSTS = Arrays.asList(HOST_ONE, HOST_TWO);

        hostOne = setUpHost(PORT_ONE, null, Arrays.asList(HOST_ONE));
        waitForInitConfig(hostOne, hostOne.localUsers);

        hostTwo = setUpHost(PORT_TWO, null, ALL_HOSTS);
        waitForInitConfig(hostTwo, hostTwo.localUsers);
        String tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        TestContext ctx = new TestContext(1, Duration.ofSeconds(60));
        createUpdateQuorumOperation(hostOne, 2, ctx, tokenOne, AUTH_TOKEN_RETRY_COUNT);
        ctx.await();

        hostsToTeardown = Arrays.asList(hostOne, hostTwo);

        // Initialize provisioning context
        initializeProvisioningContext(hostOne);
    }

    @After
    public void tearDown() throws Throwable {
        tearDownHost(hostsToTeardown);
    }

    @Test
    public void testRestrictedOperationWithNodesRestarted() throws Throwable {

        /*
         * Within this test the nodes are restarted but not at the same time, there's always a
         * running node.
         */

        String tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);

        String tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenTwo, hostTwo);

        List<ManagementHost> hosts = Arrays.asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts);

        /*
         * ==== Restart node1 ====================================================================
         */

        stopHost(hostOne);

        // We should explicitly set the quorum in the running node to 1 here, but now the
        // ClusterMonitoringService will take care of that. Otherwise the next operation will hang.

        assertClusterWithToken(tokenTwo, hostTwo);
        assertClusterFromNodes(hostTwo);

        hostOne = startHost(hostOne, null, ALL_HOSTS);

        tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);

        tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenTwo, hostTwo);
        assertClusterFromNodes(hostOne, hostTwo);

        hosts = Arrays.asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts);

        /*
         * ==== Restart node2 ====================================================================
         */

        stopHost(hostTwo);

        // We should explicitly set the quorum in the running node to 1 here, but now the
        // ClusterMonitoringService will take care of that. Otherwise the next operation will hang.

        tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);
        assertClusterFromNodes(hostOne);

        hostTwo = startHost(hostTwo, null, ALL_HOSTS);

        tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);

        tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenTwo, hostTwo);
        assertClusterFromNodes(hostOne, hostTwo);

        hosts = Arrays.asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts);
    }

    @Test
    public void testRestrictedOperationWithNodesStoppedAndStarted() throws Throwable {

        /*
         * Within this test the nodes are restarted at the same time, both nodes are stopped and
         * then started again.
         */

        String tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);

        String tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenTwo, hostTwo);

        List<ManagementHost> hosts = Arrays.asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts);

        /*
         * ==== Stop both nodes ==================================================================
         */

        stopHost(hostOne);
        stopHost(hostTwo);

        /*
         * ==== Start both nodes =================================================================
         */

        // start 1st node with quorum = 1 (peer nodes = self)
        hostOne = startHost(hostOne, hostOne.getStorageSandbox(), Arrays.asList(HOST_ONE));
        // start 2nd node with quorum = 2 (peer nodes = self & 1st node )
        hostTwo = startHost(hostTwo, null, ALL_HOSTS);

        tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);

        tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo);

        assertClusterFromNodes(hostOne, hostTwo);

        hosts = Arrays.asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts);
    }

    @Test
    public void testReplicationOfDocumentsAfterRestart() throws Throwable {

        Map<String, String> headers = new HashMap<>();
        headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, login(hostOne, USERNAME, PASSWORD, true));

        assertContainerDescription(hostOne, headers);
        assertContainerDescription(hostTwo, headers);

        // Restart hostTwo
        stopHost(hostTwo);
        hostTwo = startHost(hostTwo, null, ALL_HOSTS);

        // Get token from hostOne
        headers = new HashMap<>();
        headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, login(hostOne, USERNAME, PASSWORD, true));
        assertContainerDescription(hostOne, headers);

        // Get token from hostTwo
        headers = new HashMap<>();
        headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, login(hostTwo, USERNAME, PASSWORD, true));
        assertContainerDescription(hostTwo, headers);
    }

    @Test
    public void testProvisioningOfContainerInCluster() throws Throwable {

        Map<String, String> headersHostOne = getAuthenticationHeaders(hostOne);

        TestContext waiter = new TestContext(1, Duration.ofSeconds(30));
        disableDataCollection(hostOne, headersHostOne.get("x-xenon-auth-token"), waiter);
        waiter.await();

        Map<String, String> headersHostTwo = getAuthenticationHeaders(hostTwo);
        waiter = new TestContext(1, Duration.ofSeconds(30));
        disableDataCollection(hostTwo, headersHostTwo.get("x-xenon-auth-token"), waiter);
        waiter.await();

        // 1. Request a container instance:
        RequestBrokerState request = TestRequestStateFactory
                .createRequestState(ResourceType.CONTAINER_TYPE.getName(), "test-container-desc");
        request.resourceDescriptionLink = ContainerDescriptionService.FACTORY_LINK
                + "/test-container-desc";
        request.tenantLinks = groupResourcePlacementState.tenantLinks;
        request.documentSelfLink = "test-request";
        hostOne.log(Level.INFO, "########  Start of request ######## ");
        startRequest(headersHostOne, hostOne, request);

        URI uri = UriUtils.buildUri(hostOne,
                RequestBrokerFactoryService.SELF_LINK + "/" + request.documentSelfLink);

        // 2. Wait for provisioning request to finish
        RequestJSONResponseMapper response = waitTaskToCompleteAndGetResponse(headersHostOne,
                hostOne,
                uri);

        hostOne.log(Level.INFO, "########  Request finished. ######## ");

        assertNotNull(response.resourceLinks);

        assertEquals(1, response.resourceLinks.size());
        String resourceLink = response.resourceLinks.get(0);

        // Get the container from second host, the document should be replicated.
        URI containersUri = UriUtils.buildUri(hostTwo, resourceLink);
        String containerAsJson = null;

        try {
            containerAsJson = getResource(hostTwo, headersHostTwo, containersUri);
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

        Map<String, String> headersHostOne = getAuthenticationHeaders(hostOne);

        TestContext waiter = new TestContext(1, Duration.ofSeconds(30));
        disableDataCollection(hostOne, headersHostOne.get("x-xenon-auth-token"), waiter);
        waiter.await();

        Map<String, String> headersHostTwo = getAuthenticationHeaders(hostTwo);
        waiter = new TestContext(1, Duration.ofSeconds(30));
        disableDataCollection(hostTwo, headersHostTwo.get("x-xenon-auth-token"), waiter);
        waiter.await();

        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.documentSelfLink = UUID.randomUUID().toString();
        container1Desc.name = "container1";
        container1Desc.networks = new HashMap<>();

        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription();
        container2Desc.documentSelfLink = UUID.randomUUID().toString();
        container2Desc.name = "container2";
        container2Desc.portBindings = null;

        CompositeDescription compositeDesc = createCompositeDesc(headersHostOne, hostOne,
                container1Desc,
                container2Desc);

        // 1. Request a composite container:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);
        request.documentSelfLink = UUID.randomUUID().toString();
        request.tenantLinks = groupResourcePlacementState.tenantLinks;
        startRequest(headersHostOne, hostOne, request);

        URI uri = UriUtils.buildUri(hostOne,
                RequestBrokerFactoryService.SELF_LINK + "/" + request.documentSelfLink);

        // 2. Wait for provisioning request to finish
        RequestJSONResponseMapper response = waitTaskToCompleteAndGetResponse(headersHostOne,
                hostOne,
                uri);

        hostOne.log(Level.INFO, "########  Request finished. ######## ");

        assertNotNull(response.resourceLinks);

        // Resource links should contain 1 composite-component element. Something like:
        // "resourceLinks": ["/resources/composite-components/09fc328c-aae8-4a61-9fde-0a789bd7ecd1"]
        assertEquals(1, response.resourceLinks.size());

        // Get composition from second host. It should be replicated.
        URI compositeComponentURI = UriUtils.buildUri(hostTwo, response.resourceLinks.get(0));

        String compositeComponentAsJSON = getResource(hostTwo, headersHostTwo,
                compositeComponentURI);
        CompositeComponentJSONResponseMapper compositeComponent = Utils
                .fromJson(compositeComponentAsJSON, CompositeComponentJSONResponseMapper.class);
        assertNotNull(compositeComponent);

        assertEquals(compositeDesc.documentSelfLink, compositeComponent.compositeDescriptionLink);
        assertNotNull(compositeComponent.componentLinks);
        assertEquals(2, compositeComponent.componentLinks.size());

    }

}
