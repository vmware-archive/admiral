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
import static org.junit.Assert.assertNull;

import static com.vmware.admiral.host.ManagementHostAuthUsersTest.doRestrictedOperation;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.login;
import static com.vmware.admiral.service.common.AuthBootstrapService.waitForInitConfig;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.xenon.common.Operation;

/**
 * Similar to {@link ManagementHostAuthUsersIT} but this test includes 2 nodes, only SSL enabled
 * and the users' passwords are encrypted.
 */
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

        stopHost(hostOne);

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

        stopHost(hostTwo);

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

        stopHost(hostOne);
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

}
