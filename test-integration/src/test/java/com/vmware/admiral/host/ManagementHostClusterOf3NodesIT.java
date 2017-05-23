/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.fail;

import static com.vmware.admiral.host.ManagementHostAuthUsersTest.AUTH_TOKEN_RETRY_COUNT;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.doRestrictedOperation;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.login;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;

/**
 * Similar to {@link ManagementHostClusterOf2NodesIT} but this test includes 3 nodes, only SSL
 * enabled and the users' passwords are encrypted.
 */
public class ManagementHostClusterOf3NodesIT extends BaseManagementHostClusterIT {

    private static int portOffset = ThreadLocalRandom.current().nextInt(2000);
    private int portOne;
    private int portTwo;
    private int portThree;

    private String hostOneAddress;
    private String hostTwoAddress;
    private String hostThreeAddress;

    private List<String> allHosts;

    private ManagementHost hostOne;
    private ManagementHost hostTwo;
    private ManagementHost hostThree;

    private List<ManagementHost> hostsToTeardown;

    @Before
    public void setUp() {
        scheduler = Executors.newScheduledThreadPool(1);
        DeploymentProfileConfig.getInstance().setTest(true);
        setupHostWithRetry(3);
    }

    @After
    public void tearDown() {
        tearDownHost(hostsToTeardown);
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warning("Error waiting scheduler shutdown: " + e.getMessage());
        }
    }

    @Test
    public void testRestrictedOperationWithOneNodeRestarted() throws Throwable {
        // Test login with invalid credentials.
        logger.log(Level.INFO, "asserting login with invalid credentials");
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

        // Stop node 1 and wait until it's removed from the node group.
        logger.log(Level.INFO, "stopping host one");
        stopHostAndRemoveItFromNodeGroup(hostTwo, hostOne);

        logger.log(Level.INFO, "asserting cluster with host two");
        String tokenTwo = login(hostTwo, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenTwo, hostTwo);

        logger.log(Level.INFO, "asserting cluster with host three");
        String tokenThree = login(hostThree, USERNAME, PASSWORD, true);
        assertClusterWithToken(tokenThree, hostThree);

        logger.log(Level.INFO, "starting host one and asserting cluster");
        logger.log(Level.INFO, "All hosts are " + allHosts);
        hostOne = startHost(hostOne, hostOne.getStorageSandbox(), allHosts, 3);
        String tokenOne = login(hostOne, USERNAME, PASSWORD, true);
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
        waitAuthServices(hostOne);
        String tokenOne = login(hostOne, USERNAME, PASSWORD);
        assertClusterWithToken(tokenOne, hostOne);

        // Setup of second host
        hostTwo = setUpHost(portTwo, null, Arrays.asList(hostOneAddress, hostTwoAddress));
        waitAuthServices(hostTwo);
        String tokenTwo = login(hostTwo, USERNAME, PASSWORD);
        // Update the quorum of first host.
        TestContext ctx = new TestContext(1, Duration.ofSeconds(60));
        createUpdateQuorumOperation(hostOne, 2, ctx, tokenOne, AUTH_TOKEN_RETRY_COUNT);
        ctx.await();
        assertClusterWithToken(tokenTwo, hostTwo);

        // Setup of third host
        hostThree = setUpHost(portThree, null, allHosts);
        waitAuthServices(hostThree);
        String tokenThree = login(hostThree, USERNAME, PASSWORD);
        assertClusterWithToken(tokenThree, hostThree);

        hostsToTeardown = Arrays.asList(hostOne, hostTwo, hostThree);

        List<ManagementHost> hosts = Arrays.asList(hostOne, hostTwo, hostThree);
        validateDefaultContentAdded(hosts);

        /*
         * ==== Stop all the nodes ===============================================================
         */

        stopHost(hostOne, false, false);
        stopHost(hostTwo, false, false);
        stopHost(hostThree, false, false);

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

    /*
    This method will try to setup three management hosts.
    In case there is error while doing the setup and retry counter is provided != 0
    it will tear down the hosts which it began initializing and retry the setup again.
    If the retry counter is = 0 it will fail the test with the error which caused the setup to fail.
    On each error which cause the setup to fail with either retry or not it will log the error.
     */
    private void setupHostWithRetry(int retryCount) {
        try {
            // Get ports
            portOne = 22000 + portOffset;
            portTwo = portOne + 1;
            portThree = portTwo + 1;
            portOffset += 100;

            // Build addresses
            hostOneAddress = LOCALHOST + portOne;
            hostTwoAddress = LOCALHOST + portTwo;
            hostThreeAddress = LOCALHOST + portThree;

            allHosts = Collections.synchronizedList(
                    Arrays.asList(hostOneAddress, hostTwoAddress, hostThreeAddress));

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

            // Setup third host.
            logger.log(Level.INFO, "setting up third host");
            hostThree = setUpHost(portThree, null, allHosts);
            hostsToTeardown.add(hostThree);

        } catch (Throwable ex) {
            logger.log(Level.SEVERE, "Setting up hosts failed: " + Utils.toString(ex));
            if (retryCount > 0) {
                tearDownHost(hostsToTeardown);
                sleep(10);
                setupHostWithRetry(retryCount - 1);
            } else {
                fail(Utils.toString(ex));
            }
        }
    }

}
