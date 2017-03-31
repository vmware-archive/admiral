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
import static org.junit.Assert.assertNull;

import static com.vmware.admiral.host.ManagementHostAuthUsersTest.AUTH_TOKEN_RETRY_COUNT;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.doRestrictedOperation;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.login;
import static com.vmware.admiral.service.common.AuthBootstrapService.waitForInitConfig;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.xenon.common.test.TestContext;

/**
 * Similar to {@link ManagementHostClusterOf2NodesIT} but this test includes 3 nodes, only SSL
 * enabled and the users' passwords are encrypted.
 */
@Ignore
public class ManagementHostClusterOf3NodesIT extends BaseManagementHostClusterIT {

    private List<ManagementHost> hostsToTeardown;

    @Before
    public void setUp() {
        hostsToTeardown = new ArrayList<>();
    }

    @After
    public void tearDown() throws Throwable {
        tearDownHost(hostsToTeardown);
    }

    @Test
    public void testInvalidAccess() throws Throwable {
        String testName = "testInvalidAccess ";

        int portOne = 20000 + new Random().nextInt(1000);
        int portTwo = portOne + 1;
        int portThree = portTwo + 1;

        String hostOneAddress = LOCALHOST + portOne;
        String hostTwoAddress = LOCALHOST + portTwo;
        String hostThreeAddress = LOCALHOST + portThree;

        List<String> allHosts = Collections.synchronizedList(
                Arrays.asList(hostOneAddress, hostTwoAddress, hostThreeAddress));

        ManagementHost hostOne;

        ManagementHost hostTwo;

        ManagementHost hostThree;

        System.out.println(testName + "setting up first host");
        // Setup of first host
        hostOne = setUpHost(portOne, null, Arrays.asList(hostOneAddress));
        waitForInitConfig(hostOne, hostOne.localUsers);
        waitAuthServices(hostOne);
        String tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);

        System.out.println(testName + "setting up second host");
        // Setup of second host
        hostTwo = setUpHost(portTwo, null, Arrays.asList(hostOneAddress, hostTwoAddress));
        waitForInitConfig(hostTwo, hostTwo.localUsers);
        waitAuthServices(hostTwo);
        String tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        // Update the quorum of first host.
        TestContext ctx = new TestContext(1, Duration.ofSeconds(60));
        createUpdateQuorumOperation(hostOne, 2, ctx, tokenOne, AUTH_TOKEN_RETRY_COUNT);
        ctx.await();
        assertClusterWithToken(tokenTwo, hostTwo);

        System.out.println(testName + "setting up third host");
        // Setup of third host
        hostThree = setUpHost(portThree, null, allHosts);
        waitForInitConfig(hostThree, hostThree.localUsers);
        waitAuthServices(hostThree);
        String tokenThree = login(hostThree, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenThree, hostThree);

        hostsToTeardown = Arrays.asList(hostOne, hostTwo, hostThree);

        System.out.println(testName + "asserting login with invalid credentials");
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
        String testName = "testRestrictedOperationWithOneNodeRestarted ";

        int portOne = 20000 + new Random().nextInt(1000);
        int portTwo = portOne + 1;
        int portThree = portTwo + 1;

        String hostOneAddress = LOCALHOST + portOne;
        String hostTwoAddress = LOCALHOST + portTwo;
        String hostThreeAddress = LOCALHOST + portThree;

        List<String> allHosts = Collections.synchronizedList(
                Arrays.asList(hostOneAddress, hostTwoAddress, hostThreeAddress));

        ManagementHost hostOne;
        ManagementHost hostTwo;
        ManagementHost hostThree;

        System.out.println(testName + "setting up first host");
        // Setup of first host
        hostOne = setUpHost(portOne, null, Arrays.asList(hostOneAddress));
        waitForInitConfig(hostOne, hostOne.localUsers);
        waitAuthServices(hostOne);
        String tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);

        System.out.println(testName + "setting up second host");
        // Setup of second host
        hostTwo = setUpHost(portTwo, null, Arrays.asList(hostOneAddress, hostTwoAddress));
        waitForInitConfig(hostTwo, hostTwo.localUsers);
        waitAuthServices(hostTwo);
        String tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        // Update the quorum of first host.
        TestContext ctx = new TestContext(1, Duration.ofMinutes(1));
        createUpdateQuorumOperation(hostOne, 2, ctx, tokenOne, AUTH_TOKEN_RETRY_COUNT);
        ctx.await();
        assertClusterWithToken(tokenTwo, hostTwo);

        System.out.println(testName + "setting up third host");
        // Setup of third host
        hostThree = setUpHost(portThree, null, allHosts);
        waitForInitConfig(hostThree, hostThree.localUsers);
        waitAuthServices(hostThree);
        String tokenThree = login(hostThree, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenThree, hostThree);

        hostsToTeardown = Arrays.asList(hostOne, hostTwo, hostThree);

        System.out.println(testName + "stopping host one");
        // Restart node1
        stopHostAndRemoveItFromNodeGroup(hostTwo, tokenTwo, hostOne);

        System.out.println(testName + "asserting cluster with host two");
        tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenTwo, hostTwo);

        System.out.println(testName + "asserting cluster with host three");
        tokenThree = login(hostThree, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenThree, hostThree);

        System.out.println(testName + "starting host one and asserting cluster");
        hostOne = startHost(hostOne, null, allHosts);
        tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);

        List<ManagementHost> hosts = Arrays.asList(hostOne, hostTwo, hostThree);
        validateDefaultContentAdded(hosts);
    }

    @Ignore("To be further reworked or removed.")
    @Test
    public void testRestrictedOperationWithNodesStoppedAndStarted() throws Throwable {
        int portOne = 20000 + new Random().nextInt(1000);
        int portTwo = portOne + 1;
        int portThree = portTwo + 1;

        String hostOneAddress = LOCALHOST + portOne;
        String hostTwoAddress = LOCALHOST + portTwo;
        String hostThreeAddress = LOCALHOST + portThree;

        List<String> allHosts = Collections.synchronizedList(
                Arrays.asList(hostOneAddress, hostTwoAddress, hostThreeAddress));

        ManagementHost hostOne;

        ManagementHost hostTwo;

        ManagementHost hostThree;

        // Setup of first host
        hostOne = setUpHost(portOne, null, Arrays.asList(hostOneAddress));
        waitForInitConfig(hostOne, hostOne.localUsers);
        String tokenOne = login(hostOne, USERNAME, PASSWORD);
        assertClusterWithToken(tokenOne, hostOne);

        // Setup of second host
        hostTwo = setUpHost(portTwo, null, Arrays.asList(hostOneAddress, hostTwoAddress));
        waitForInitConfig(hostTwo, hostTwo.localUsers);
        String tokenTwo = login(hostTwo, USERNAME, PASSWORD);
        // Update the quorum of first host.
        TestContext ctx = new TestContext(1, Duration.ofSeconds(60));
        createUpdateQuorumOperation(hostOne, 2, ctx, tokenOne, AUTH_TOKEN_RETRY_COUNT);
        ctx.await();
        assertClusterWithToken(tokenTwo, hostTwo);

        // Setup of third host
        hostThree = setUpHost(portThree, null, allHosts);
        waitForInitConfig(hostThree, hostThree.localUsers);
        String tokenThree = login(hostThree, USERNAME, PASSWORD);
        assertClusterWithToken(tokenThree, hostThree);

        hostsToTeardown = Arrays.asList(hostOne, hostTwo, hostThree);

        List<ManagementHost> hosts = Arrays.asList(hostOne, hostTwo, hostThree);
        validateDefaultContentAdded(hosts);

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
        hostOne = startHost(hostOne, hostOne.getStorageSandbox(), Arrays.asList(hostOneAddress));
        // start 2nd node with quorum = 2 (peer nodes = self & 1st node )
        hostTwo = startHost(hostTwo, null, Arrays.asList(hostOneAddress, hostTwoAddress));
        // start 3rd node with quorum = 2 (peer nodes = self & other nodes)
        hostThree = startHost(hostThree, null, allHosts);

        tokenOne = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenOne, hostOne);
        tokenTwo = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenTwo, hostTwo);
        tokenThree = login(hostOne, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenThree, hostThree);

        assertClusterFromNodes(hostOne, hostTwo, hostThree);

        hosts = Arrays.asList(hostOne, hostTwo, hostThree);
        validateDefaultContentAdded(hosts);
    }

}
