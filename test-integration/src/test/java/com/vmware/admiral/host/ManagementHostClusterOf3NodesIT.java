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
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Similar to {@link ManagementHostClusterOf2NodesIT} but this test includes 3 nodes, only SSL
 * enabled and the users' passwords are encrypted.
 */
@Ignore("VBV-984")
public class ManagementHostClusterOf3NodesIT extends BaseManagementHostClusterIT {

    private ManagementHost hostOne;
    private ManagementHost hostTwo;
    private ManagementHost hostThree;

    private static final int PORT_ONE = 20000 + new Random().nextInt(1000);
    private static final int PORT_TWO = PORT_ONE + 1;
    private static final int PORT_THREE = PORT_TWO + 1;

    private static final String HOST_ONE = LOCALHOST + PORT_ONE;
    private static final String HOST_TWO = LOCALHOST + PORT_TWO;
    private static final String HOST_THREE = LOCALHOST + PORT_THREE;
    private static final List<String> ALL_HOSTS = asList(HOST_ONE, HOST_TWO, HOST_THREE);

    @Before
    public void setUp() throws Throwable {
        hostOne = setUpHost(PORT_ONE, null, ALL_HOSTS);
        hostTwo = setUpHost(PORT_TWO, null, ALL_HOSTS);
        hostThree = setUpHost(PORT_THREE, null, ALL_HOSTS);

        waitForInitConfig(hostOne, hostOne.localUsers);
        waitForInitConfig(hostTwo, hostTwo.localUsers);
        waitForInitConfig(hostThree, hostThree.localUsers);
    }

    @After
    public void tearDown() throws Throwable {
        tearDownHost(hostOne, hostTwo, hostThree);
    }

    @Test
    public void testInvalidAccess() throws Throwable {

        // Invalid credentials

        assertNull(login(hostOne, USERNAME, "bad"));
        assertNull(login(hostOne, "bad", PASSWORD));

        assertNull(login(hostTwo, USERNAME, "bad"));
        assertNull(login(hostTwo, "bad", PASSWORD));

        assertNull(login(hostThree, USERNAME, "bad"));
        assertNull(login(hostThree, "bad", PASSWORD));

        // Restricted operation without authentication

        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, doRestrictedOperation(hostOne, null));
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, doRestrictedOperation(hostTwo, null));
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, doRestrictedOperation(hostThree, null));
    }

    @Test
    public void testRestrictedOperationWithOneNodeRestarted() throws Throwable {

        /*
         * Within this test the nodes are restarted but not at the same time, there're always at
         * least 2 running nodes.
         */

        String tokenOne = login(hostOne, USERNAME, PASSWORD);
        assertClusterWithToken(tokenOne, hostOne, hostTwo, hostThree);

        String tokenTwo = login(hostTwo, USERNAME, PASSWORD);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo, hostThree);

        String tokenThree = login(hostThree, USERNAME, PASSWORD);
        assertClusterWithToken(tokenThree, hostOne, hostTwo, hostThree);

        /*
         * ==== Restart node1 ====================================================================
         */

        stopHost(hostOne);

        assertClusterWithToken(tokenTwo, hostTwo, hostThree);
        assertClusterWithToken(tokenThree, hostTwo, hostThree);

        hostOne = startHost(hostOne, null, ALL_HOSTS);

        assertClusterWithToken(tokenOne, hostOne, hostTwo, hostThree);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo, hostThree);
        assertClusterWithToken(tokenThree, hostOne, hostTwo, hostThree);
        assertClusterFromNodes(hostOne, hostTwo, hostThree);

        /*
         * ==== Restart node2 ====================================================================
         */

        stopHost(hostTwo);

        assertClusterWithToken(tokenOne, hostOne, hostThree);
        assertClusterWithToken(tokenThree, hostOne, hostThree);
        assertClusterFromNodes(hostOne, hostThree);

        hostTwo = startHost(hostTwo, null, ALL_HOSTS);

        assertClusterWithToken(tokenOne, hostOne, hostTwo, hostThree);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo, hostThree);
        assertClusterWithToken(tokenThree, hostOne, hostTwo, hostThree);
        assertClusterFromNodes(hostOne, hostTwo, hostThree);

        /*
         * ==== Restart node3 ====================================================================
         */

        stopHost(hostThree);

        assertClusterWithToken(tokenOne, hostOne, hostTwo);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo);

        hostThree = startHost(hostThree, null, ALL_HOSTS);

        assertClusterWithToken(tokenOne, hostOne, hostTwo, hostThree);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo, hostThree);
        assertClusterWithToken(tokenThree, hostOne, hostTwo, hostThree);
    }

    @Test
    public void testRestrictedOperationWithNodesStoppedAndStarted() throws Throwable {

        /*
         * Within this test the nodes are restarted at the same time.
         */

        String tokenOne = login(hostOne, USERNAME, PASSWORD);
        assertClusterWithToken(tokenOne, hostOne, hostTwo, hostThree);

        String tokenTwo = login(hostTwo, USERNAME, PASSWORD);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo, hostThree);

        String tokenThree = login(hostThree, USERNAME, PASSWORD);
        assertClusterWithToken(tokenThree, hostOne, hostTwo, hostThree);

        List<ManagementHost> hosts = asList(hostOne, hostTwo, hostThree);
        validateDefaultContentAdded(hosts, tokenOne);

        /*
         * ==== Stop all the nodes ===============================================================
         */

        stopHost(hostOne);
        stopHost(hostTwo);
        stopHost(hostThree);

        /*
         * ==== Start all the nodes ==============================================================
         */

        // start 1st node with quorum = 1 (peer nodes = self)
        hostOne = startHost(hostOne, hostOne.getStorageSandbox(), asList(HOST_ONE));
        // start 2nd node with quorum = 2 (peer nodes = self & 1st node )
        hostTwo = startHost(hostTwo, null, asList(HOST_ONE, HOST_TWO));
        // start 3rd node with quorum = 2 (peer nodes = self & other nodes)
        hostThree = startHost(hostThree, null, ALL_HOSTS);

        assertClusterWithToken(tokenOne, hostOne, hostTwo, hostThree);
        assertClusterWithToken(tokenTwo, hostOne, hostTwo, hostThree);
        assertClusterWithToken(tokenThree, hostOne, hostTwo, hostThree);
        assertClusterFromNodes(hostOne, hostTwo, hostThree);

        hosts = asList(hostOne, hostTwo, hostThree);
        validateDefaultContentAdded(hosts, tokenOne);
    }

}
