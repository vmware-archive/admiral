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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.ContainerHostNetworkConfigState;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.ContainerNetworkConfigState;
import com.vmware.admiral.compute.container.ShellContainerExecutorService.ShellContainerExecutorState;
import com.vmware.admiral.host.HostInitServiceHelper;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class ContainerHostNetworkConfigServiceTest extends BaseTestCase {

    private String networkConfigServiceSelfLink;
    private static final String HOST_ID = "testHostId";
    private MockShellContainerExecutorService mockExecutorService;
    private static boolean testMode;

    private void startServices(ServiceHost serviceHost) {
        // these tests need test mode false in order to receive post operations in the mock executor service
        testMode = DeploymentProfileConfig.getInstance().isTest();
        DeploymentProfileConfig.getInstance().setTest(false);
        HostInitServiceHelper.startServices(host, ContainerHostNetworkConfigFactoryService.class);
    }

    @Before
    public void setUp() throws Throwable {
        startServices(host);
        mockExecutorService = new MockShellContainerExecutorService(false);
        host.startService(Operation.createPost(UriUtilsExtended.buildUri(host,
                ShellContainerExecutorService.SELF_LINK)), mockExecutorService);

        waitForServiceAvailability(ContainerHostNetworkConfigFactoryService.SELF_LINK);

        networkConfigServiceSelfLink = UriUtils.buildUriPath(
                ContainerHostNetworkConfigFactoryService.SELF_LINK, HOST_ID);
        ContainerHostNetworkConfigState state = new ContainerHostNetworkConfigState();
        state.documentSelfLink = networkConfigServiceSelfLink;

        doPost(state, ContainerHostNetworkConfigFactoryService.SELF_LINK);
    }

    @After
    public void tearDown() throws Throwable {
        // restore test mode
        DeploymentProfileConfig.getInstance().setTest(testMode);
    }

    /** Test creating a network connection for an artificial application, containing of 2 nodes of wordpress containerm 1 node mysql (only refferenced to it) and 2 nodes of some authentication service. */
    @Test
    public void testContainerHostNetworkConfigServicePatch() throws Throwable {
        ContainerHostNetworkConfigState state = new ContainerHostNetworkConfigState();
        state.containerNetworkConfigs = new LinkedHashMap<String, ContainerHostNetworkConfigService.ContainerNetworkConfigState>();

        ContainerNetworkConfigState containerConfigState = new ContainerNetworkConfigState();
        containerConfigState.internalServiceNetworkLinks = new HashSet<>();
        containerConfigState.internalServiceNetworkLinks.add("wordpress-dcp15:3306:10.0.0.1:32782");
        containerConfigState.internalServiceNetworkLinks.add("wordpress-dcp15:8443:10.0.0.1:32783");
        containerConfigState.internalServiceNetworkLinks.add("wordpress-dcp15:8443:10.0.0.2:32769");
        state.containerNetworkConfigs.put(containerLink("wordpress-dcp15"), containerConfigState);

        containerConfigState = new ContainerNetworkConfigState();
        containerConfigState.internalServiceNetworkLinks = new HashSet<>();
        containerConfigState.internalServiceNetworkLinks.add("wordpress-dcp78:3306:10.0.0.1:32782");
        containerConfigState.internalServiceNetworkLinks.add("wordpress-dcp78:8443:10.0.0.1:32783");
        containerConfigState.internalServiceNetworkLinks.add("wordpress-dcp78:8443:10.0.0.2:32769");
        state.containerNetworkConfigs.put(containerLink("wordpress-dcp78"), containerConfigState);

        containerConfigState = new ContainerNetworkConfigState();
        containerConfigState.internalServiceNetworkLinks = new HashSet<>();
        containerConfigState.internalServiceNetworkLinks.add("horizon-dcp123:3306:10.0.0.1:32782");
        state.containerNetworkConfigs.put(containerLink("horizon-dcp123"), containerConfigState);

        containerConfigState = new ContainerNetworkConfigState();
        containerConfigState.internalServiceNetworkLinks = new HashSet<>();
        containerConfigState.internalServiceNetworkLinks.add("horizon-dcp234:3306:10.0.0.1:32782");
        state.containerNetworkConfigs.put(containerLink("horizon-dcp234"), containerConfigState);

        doOperation(state, UriUtils.buildUri(host, networkConfigServiceSelfLink), false,
                Service.Action.PATCH);

        assertNotNull(mockExecutorService.lastBody);

        List<String> expectedLinks = new ArrayList<String>();
        expectedLinks.add("wordpress-dcp15:3306:10.0.0.1:32782");
        expectedLinks.add("wordpress-dcp15:8443:10.0.0.1:32783");
        expectedLinks.add("wordpress-dcp15:8443:10.0.0.2:32769");
        expectedLinks.add("wordpress-dcp78:3306:10.0.0.1:32782");
        expectedLinks.add("wordpress-dcp78:8443:10.0.0.1:32783");
        expectedLinks.add("wordpress-dcp78:8443:10.0.0.2:32769");
        expectedLinks.add("horizon-dcp123:3306:10.0.0.1:32782");
        expectedLinks.add("horizon-dcp234:3306:10.0.0.1:32782");

        List<String> expectedExecutedCommand = new ArrayList<String>();
        expectedExecutedCommand.add(ContainerHostNetworkConfigService.RECONFIGURE_APP_NAME);
        for (String links : expectedLinks) {
            expectedExecutedCommand.add("-i");
            expectedExecutedCommand.add(links);
        }

        assertEquals(expectedExecutedCommand.size(), mockExecutorService.lastBody.command.length);
        assertEquals(new HashSet<>(expectedExecutedCommand),
                new HashSet<>(Arrays.asList(mockExecutorService.lastBody.command)));
    }

    /**
     * Tests that concurrent patching will merge all links correctly and also call the executor
     * service with all links, instead of mashing them up. Although this behavior is promised and
     * handled by Xenon and it's OWNER_SELECTION and NOT CONCURRENT_UPDATE_HANDLING, we still
     * want to make sure we don't fall into this case, i.e. we always want to pass the
     * networking agent the correct state.
     * */
    @Test
    public void testConcurrentPatch() throws Throwable {
        ContainerHostNetworkConfigState firstState = new ContainerHostNetworkConfigState();
        firstState.containerNetworkConfigs = new LinkedHashMap<String, ContainerHostNetworkConfigService.ContainerNetworkConfigState>();

        ContainerNetworkConfigState containerConfigState = new ContainerNetworkConfigState();
        containerConfigState.internalServiceNetworkLinks = new HashSet<>();
        containerConfigState.internalServiceNetworkLinks = new HashSet<>();
        containerConfigState.internalServiceNetworkLinks.add("wordpress-dcp15:3306:10.0.0.1:32782");
        firstState.containerNetworkConfigs.put(containerLink("wordpress-dcp15"),
                containerConfigState);

        containerConfigState = new ContainerNetworkConfigState();
        containerConfigState.internalServiceNetworkLinks = new HashSet<>();
        containerConfigState.internalServiceNetworkLinks.add("wordpress-dcp78:8443:10.0.0.2:32769");
        firstState.containerNetworkConfigs.put(containerLink("wordpress-dcp78"),
                containerConfigState);

        containerConfigState = new ContainerNetworkConfigState();
        containerConfigState.internalServiceNetworkLinks = new HashSet<>();
        containerConfigState.internalServiceNetworkLinks.add("horizon-dcp234:3306:10.0.0.1:32782");
        firstState.containerNetworkConfigs.put(containerLink("horizon-dcp234"),
                containerConfigState);

        ContainerHostNetworkConfigState secondState = new ContainerHostNetworkConfigState();
        secondState.containerNetworkConfigs = new HashMap<String, ContainerHostNetworkConfigService.ContainerNetworkConfigState>();

        containerConfigState = new ContainerNetworkConfigState();
        containerConfigState.internalServiceNetworkLinks = new HashSet<>();
        containerConfigState.internalServiceNetworkLinks.add("mysql-dcp20:3306:10.0.0.1:32782");
        secondState.containerNetworkConfigs.put(containerLink("mysql-dcp20"),
                containerConfigState);

        host.testStart(2);
        host.send(Operation.createPatch(UriUtils.buildUri(host, networkConfigServiceSelfLink))
                .setBody(firstState)
                .setCompletion((o, e) -> {
                    host.completeIteration();
                }));
        host.send(Operation.createPatch(UriUtils.buildUri(host, networkConfigServiceSelfLink))
                .setBody(secondState)
                .setCompletion((o, e) -> {
                    host.completeIteration();
                }));
        host.testWait();
        host.logThroughput();

        assertNotNull(mockExecutorService.lastBody);

        List<String> expectedLinks = new ArrayList<>();
        expectedLinks.add("wordpress-dcp15:3306:10.0.0.1:32782");
        expectedLinks.add("wordpress-dcp78:8443:10.0.0.2:32769");
        expectedLinks.add("horizon-dcp234:3306:10.0.0.1:32782");
        expectedLinks.add("mysql-dcp20:3306:10.0.0.1:32782");

        List<String> expectedExecutedCommand = new ArrayList<String>();
        expectedExecutedCommand.add(ContainerHostNetworkConfigService.RECONFIGURE_APP_NAME);
        for (String links : expectedLinks) {
            expectedExecutedCommand.add("-i");
            expectedExecutedCommand.add(links);
        }

        assertEquals(expectedExecutedCommand.size(), mockExecutorService.lastBody.command.length);
        assertEquals(new HashSet<>(expectedExecutedCommand),
                new HashSet<>(Arrays.asList(mockExecutorService.lastBody.command)));
    }

    @Test
    public void testDuplicateContainerHostNetworkConfigServicePatch() throws Throwable {
        ContainerHostNetworkConfigState state = new ContainerHostNetworkConfigState();
        state.containerNetworkConfigs = new LinkedHashMap<String, ContainerHostNetworkConfigService.ContainerNetworkConfigState>();

        ContainerNetworkConfigState containerConfigState = new ContainerNetworkConfigState();
        containerConfigState.publicServiceNetworkLinks = new HashSet<>();
        containerConfigState.publicServiceNetworkLinks.add("http:wordpress.cmp:10.152.8.23:32867");
        containerConfigState.publicServiceNetworkLinks.add("http:wordpress.cmp:10.152.8.23:32868");
        state.containerNetworkConfigs.put(containerLink("wordpress-dcp15"), containerConfigState);

        doOperation(state, UriUtils.buildUri(host, networkConfigServiceSelfLink), false,
                Service.Action.PATCH);

        containerConfigState = new ContainerNetworkConfigState();
        containerConfigState.publicServiceNetworkLinks = new HashSet<>();
        containerConfigState.publicServiceNetworkLinks.add("http:wordpress.cmp:10.152.8.23:32867");
        containerConfigState.publicServiceNetworkLinks.add("http:wordpress.cmp:10.152.8.23:32868");
        state.containerNetworkConfigs.put(containerLink("wordpress-dcp20"), containerConfigState);

        doOperation(state, UriUtils.buildUri(host, networkConfigServiceSelfLink), false,
                Service.Action.PATCH);

        assertNotNull(mockExecutorService.lastBody);

        List<String> expectedLinks = new ArrayList<String>();
        expectedLinks.add("http:wordpress.cmp:10.152.8.23:32867");
        expectedLinks.add("http:wordpress.cmp:10.152.8.23:32868");

        List<String> expectedExecutedCommand = new ArrayList<String>();
        expectedExecutedCommand.add(ContainerHostNetworkConfigService.RECONFIGURE_APP_NAME);
        for (String links : expectedLinks) {
            expectedExecutedCommand.add("-p");
            expectedExecutedCommand.add(links);
        }

        assertEquals(expectedExecutedCommand.size(), mockExecutorService.lastBody.command.length);
        assertEquals(new HashSet<>(expectedExecutedCommand),
                new HashSet<>(Arrays.asList(mockExecutorService.lastBody.command)));
    }

    private static final String containerLink(String containerId) {
        return UriUtils.buildUriPath(ContainerFactoryService.SELF_LINK, containerId);
    }

    public class MockShellContainerExecutorService extends StatelessService {
        private ShellContainerExecutorState lastBody;
        private boolean delayFirstCall;

        public MockShellContainerExecutorService(boolean delayFirstCall) {
            this.delayFirstCall = delayFirstCall;
        }

        @Override
        public void handlePost(Operation post) {
            Callable<Void> c = new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    if (post.hasBody()) {
                        lastBody = post.getBody(ShellContainerExecutorState.class);
                    }

                    post.complete();
                    return null;
                }
            };

            if (delayFirstCall) {
                delayFirstCall = false;
                ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
                executor.schedule(c, 50, TimeUnit.MILLISECONDS);
            } else {
                try {
                    c.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
