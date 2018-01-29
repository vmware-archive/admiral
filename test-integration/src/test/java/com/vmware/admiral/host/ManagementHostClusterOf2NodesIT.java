/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import static com.vmware.admiral.host.ManagementHostAuthUsersTest.AUTH_TOKEN_RETRY_COUNT;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.doRestrictedOperation;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.login;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
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
@Ignore
public class ManagementHostClusterOf2NodesIT extends BaseManagementHostClusterIT {

    private List<ManagementHost> hostsToTeardown;

    private int portOne;
    private int portTwo;

    private String hostOneAddress;
    private String hostTwoAddress;
    private List<String> allHosts;

    private ManagementHost hostOne;
    private ManagementHost hostTwo;

    @Before
    public void setUp() {
        DeploymentProfileConfig.getInstance().setTest(true);
        setupHostWithRetry(3);
    }

    @After
    public void tearDown() {
        tearDownHost(hostsToTeardown);
    }

    @BeforeClass
    public static void init() {
        scheduler = Executors.newScheduledThreadPool(1);
    }

    @AfterClass
    public static void shutdown() {
        scheduler.shutdown();
    }

    @Test
    public void testRestrictedOperationWithNodesRestarted() throws Throwable {
        // Test login with invalid credentials.
        logger.log(Level.INFO, "asserting login with invalid credentials");
        assertNull(login(hostOne, USERNAME, "bad"));
        assertNull(login(hostOne, "bad", PASSWORD));

        assertNull(login(hostTwo, USERNAME, "bad"));
        assertNull(login(hostTwo, "bad", PASSWORD));

        // Restricted operation without authentication.
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, doRestrictedOperation(hostOne, null));
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, doRestrictedOperation(hostTwo, null));

        // Update quorum of host two to 1, because ClusterMonitoringService is disabled.
        String tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        TestContext ctx = new TestContext(1, Duration.ofMinutes(1));
        createUpdateQuorumOperation(hostTwo, 1, ctx, tokenTwo, AUTH_TOKEN_RETRY_COUNT);
        ctx.await();

        // Stop node 1 and wait until it's removed from the node group.
        logger.log(Level.INFO, "stopping host one");
        stopHostAndRemoveItFromNodeGroup(hostTwo, hostOne);

        logger.log(Level.INFO, "asserting cluster with host two");
        tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenTwo, hostTwo);

        logger.log(Level.INFO, "starting host one and asserting cluster");
        logger.log(Level.INFO, "All hosts are " + allHosts);
        hostOne = startHost(hostOne, hostOne.getStorageSandbox(), allHosts, 3);

        // Update quorum of host two to 2, because ClusterMonitoringService is disabled.
        tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        ctx = new TestContext(1, Duration.ofMinutes(1));
        createUpdateQuorumOperation(hostTwo, 2, ctx, tokenTwo, AUTH_TOKEN_RETRY_COUNT);
        ctx.await();

        String tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);

        List<ManagementHost> hosts = Arrays.asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts);
    }

    @Ignore("To be further reworked or removed.")
    @Test
    public void testRestrictedOperationWithNodesStoppedAndStarted() throws Throwable {

        /*
         * ==== Stop both nodes ==================================================================
         */

        stopHost(hostOne, false, false);
        stopHost(hostTwo, false, false);

        /*
         * ==== Start both nodes =================================================================
         */

        // start 1st node with quorum = 1 (peer nodes = self)
        hostOne = startHost(hostOne, hostOne.getStorageSandbox(), Arrays.asList(hostOneAddress));
        // start 2nd node with quorum = 2 (peer nodes = self & 1st node )
        hostTwo = startHost(hostTwo, null, allHosts);

        String tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);

        String tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo);

        assertClusterFromNodes(hostOne, hostTwo);

        List<ManagementHost> hosts = Arrays.asList(hostOne, hostTwo);
        validateDefaultContentAdded(hosts);
    }

    @Test
    public void testReplicationOfDocumentsAfterRestart() throws Throwable {
        Map<String, String> headers = new HashMap<>();
        headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, login(hostOne, USERNAME, PASSWORD, true));

        assertContainerDescription(hostOne, headers);
        assertContainerDescription(hostTwo, headers);

        // Update quorum of host two to 1, because ClusterMonitoringService is disabled.
        String tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        TestContext ctx = new TestContext(1, Duration.ofMinutes(1));
        createUpdateQuorumOperation(hostTwo, 1, ctx, tokenTwo, AUTH_TOKEN_RETRY_COUNT);
        ctx.await();

        stopHostAndRemoveItFromNodeGroup(hostOne, hostTwo);
        hostTwo = startHost(hostTwo, hostTwo.getStorageSandbox(), allHosts, 3);

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

        Map<String, String> headersHostTwo = getAuthenticationHeaders(hostTwo);

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
    }

    @Test
    public void testProvisioningOfApplicationInCluster() throws Throwable {
        Map<String, String> headersHostOne = getAuthenticationHeaders(hostOne);

        Map<String, String> headersHostTwo = getAuthenticationHeaders(hostTwo);

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

    private void setupHostWithRetry(int retryCount) {
        try {
            // Get ports
            portOne = 20000 + new Random().nextInt(5000);
            portTwo = portOne + 1;

            // Build addresses
            hostOneAddress = LOCALHOST + portOne;
            hostTwoAddress = LOCALHOST + portTwo;
            allHosts = Collections.synchronizedList(
                    Arrays.asList(hostOneAddress, hostTwoAddress));

            hostsToTeardown = new ArrayList<>();

            // Setup first host.
            logger.log(Level.INFO, "setting up first host");
            hostOne = setUpHost(portOne, null, Arrays.asList(hostOneAddress));
            hostsToTeardown.add(hostOne);

            // Setup second host.
            logger.log(Level.INFO, "setting up second host");
            hostTwo = setUpHost(portTwo, null, Arrays.asList(hostOneAddress, hostTwoAddress));
            String tokenOne = login(hostOne, USERNAME, PASSWORD, true);
            TestContext ctx = new TestContext(1, Duration.ofMinutes(1));
            createUpdateQuorumOperation(hostOne, 2, ctx, tokenOne, AUTH_TOKEN_RETRY_COUNT);
            ctx.await();
            hostsToTeardown.add(hostTwo);

            initializeProvisioningContext(hostOne);
        } catch (Throwable ex) {
            logger.log(Level.SEVERE, "Setting up hosts failed: " + Utils.toString(ex));
            if (retryCount > 0) {
                tearDownHost(hostsToTeardown);
                setupHostWithRetry(retryCount - 1);
            } else {
                fail(Utils.toString(ex));
            }
        }
    }

}
