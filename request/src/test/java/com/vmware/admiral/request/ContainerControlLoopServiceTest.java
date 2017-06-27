/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.ServerSocket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.request.ContainerControlLoopService.ContainerControlLoopState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class ContainerControlLoopServiceTest extends RequestBaseTest {
    private static final int SINGLE_CONTAINERS_TO_BE_PROVISIONED = 3;

    private ContainerDescription containerDescription1;
    private ContainerDescription containerDescription2;

    @BeforeClass
    public static void beforeForDataCollection() throws Throwable {
        setFinalStatic(ContainerControlLoopService.class
                .getDeclaredField("MAINTENANCE_INTERVAL_MICROS"), TimeUnit.SECONDS.toMicros(1));
    }

    @After
    public void tearDown() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
    }

    @Before
    public void init() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
        waitForServiceAvailability(ContainerControlLoopService.CONTROL_LOOP_INFO_LINK);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyPost() throws Throwable {
        doPost(null, ContainerControlLoopService.CONTROL_LOOP_INFO_LINK);
    }

    @Test
    public void testControlLoopStateCreatedOnStartUp() throws Throwable {
        ContainerControlLoopState controlLoopState = getDocument(
                ContainerControlLoopState.class,
                ContainerControlLoopService.CONTROL_LOOP_INFO_LINK);

        assertNotNull(controlLoopState);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testPostAnotherStateWithoutSelfLinkShouldFail() throws Throwable {
        ContainerControlLoopState controlLoopState = getDocument(
                ContainerControlLoopState.class,
                ContainerControlLoopService.CONTROL_LOOP_INFO_LINK);
        controlLoopState.documentSelfLink = null;

        doPost(controlLoopState, ContainerControlLoopService.CONTROL_LOOP_INFO_LINK);
    }

    @Test
    public void testRedeploymentWithAutoredeployOptionDisabled() throws Throwable {
        final long timoutInMillis = 5000; // 5sec
        ContainerDescription cd = createContainerDescription(false);

        ContainerState state = provisionContainer(cd.documentSelfLink);
        // change the power state of one of them
        state.powerState = PowerState.ERROR;
        doPut(state);

        doOperation(new ContainerControlLoopState(), UriUtils.buildUri(host,
                ContainerControlLoopService.CONTROL_LOOP_INFO_LINK),
                false,
                Service.Action.PATCH);

        long startTime = System.currentTimeMillis();
        AtomicBoolean healthyContainersFound = new AtomicBoolean(false);
        waitFor(() -> {
            retrieveContainerStates(cd.documentSelfLink).thenAccept(containerStates -> {
                long healthyContainers = containerStates.stream().filter(cs -> PowerState.RUNNING.equals(cs.powerState)).count();
                if (healthyContainers != 0) {
                    healthyContainersFound.set(true);
                }
            });

            if (healthyContainersFound.get()) {
                fail("Should not have any healthy containers.");
            }

            return System.currentTimeMillis() - startTime > timoutInMillis;
        });
    }

    @Test
    public void testRedeploymentOfAContainerInCluster() throws Throwable {
        redeploymentOfAContainerInCluster(2, 1);
    }

    @Test
    public void testRedeploymentOfAContainerInClusterAllContainersInError() throws Throwable {
        redeploymentOfAContainerInCluster(2, 2);
    }

    @SuppressWarnings("unchecked")
    private void redeploymentOfAContainerInCluster(int containersInCluster, int containerInError) throws Throwable {
        assertTrue(containersInCluster >= containerInError);
        containerDescription1 = createContainerDescription();
        containerDescription1._cluster = containersInCluster;

        ServerSocket serverSocket = new ServerSocket(0);
        HealthConfig healthConfig = createHealthConfigTcp(serverSocket.getLocalPort());
        healthConfig.autoredeploy = true;
        containerDescription1.healthConfig = healthConfig;
        doPut(containerDescription1);

        try {
            RequestBrokerState request = TestRequestStateFactory.createRequestState(ResourceType.CONTAINER_TYPE.getName(), containerDescription1.documentSelfLink);
            request = startRequest(request);
            request = waitForRequestToComplete(request);

            Iterator<String> iterator = request.resourceLinks.iterator();

            ContainerState containerState = null;

            for (int i = 0; i < containerInError; i++) {
                assertTrue(iterator.hasNext());
                containerState = searchForDocument(ContainerState.class, iterator.next());
                assertNotNull(containerState);
                containerState.powerState = PowerState.ERROR;
                doPut(containerState);
            }

            Map<String, List<String>> containersPerContextId = new HashMap<>();

            retrieveContainerStates(containerDescription1.documentSelfLink).thenAccept(containerStates -> {
                List<String> containersFromDesc1 = containerStates.stream().map(cs -> cs.documentSelfLink).collect(Collectors.toList());
                assertEquals(2, containersFromDesc1.size());

                // clustered containers have same context_id
                containersPerContextId.put(containerStates.get(0).customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY), containersFromDesc1);
            });

            doOperation(new ContainerControlLoopState(), UriUtils.buildUri(host,
                    ContainerControlLoopService.CONTROL_LOOP_INFO_LINK),
                    false,
                    Service.Action.PATCH);

            Map<String, List<String>> redeployedContainersPerContextId = new HashMap<>();

            AtomicBoolean containerFromDesc1Redeployed = new AtomicBoolean(false);

            waitFor(() -> {
                // get all containers from containerDescription1
                retrieveContainerStates(containerDescription1.documentSelfLink).thenAccept(containerStates -> {
                    long healthyContainers = containerStates.stream().filter(cs -> PowerState.RUNNING.equals(cs.powerState)).count();
                    host.log("Healthy containers from %s : %d", containerDescription1.documentSelfLink, healthyContainers);
                    containerFromDesc1Redeployed.set(containerDescription1._cluster == healthyContainers && containerDescription1._cluster == containerStates.size());

                    List<String> containersFromDesc1 = containerStates.stream().map(cs -> cs.documentSelfLink).collect(Collectors.toList());
                    redeployedContainersPerContextId.put(containerStates.get(0).customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY), containersFromDesc1);
                });

                if (containerFromDesc1Redeployed.get()) {
                    containersPerContextId.entrySet().stream().forEach(m -> {
                        String contextId = m.getKey();
                        List<String> redeployedContainers = redeployedContainersPerContextId.get(contextId);
                        host.log("Redeployed container: %s -> %s", StringUtils.join(m.getValue()), StringUtils.join(redeployedContainers));
                    });
                }

                return containerFromDesc1Redeployed.get();
            });
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void redeploymentOfSingleContainers() throws Throwable {
        containerDescription2 = createContainerDescription(false);

        ServerSocket serverSocket = new ServerSocket();
        HealthConfig healthConfig = createHealthConfigTcp(serverSocket.getLocalPort());
        healthConfig.autoredeploy = true;
        containerDescription2.healthConfig = healthConfig;
        containerDescription2.tenantLinks = resourcePool.tenantLinks;
        doPut(containerDescription2);

        // starting a listener for the health check
        try  {
            // provision 3 single containers, 2 of them in ERROR state
            ContainerState state = null;
            for (int i = 0; i < SINGLE_CONTAINERS_TO_BE_PROVISIONED; i++) {
                state = provisionContainer(containerDescription2.documentSelfLink);

                if (i < SINGLE_CONTAINERS_TO_BE_PROVISIONED - 1) {
                    state.powerState = PowerState.ERROR;
                    doPut(state);
                }
            }

            Map<String, List<String>> containersPerContextId = new HashMap<>();

            retrieveContainerStates(containerDescription2.documentSelfLink).thenAccept(containerStates -> {
                containerStates.stream().forEach(cs -> {
                    containersPerContextId.put(cs.customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY), Arrays.asList(cs.documentSelfLink));
                });
            });

            doOperation(new ContainerControlLoopState(), UriUtils.buildUri(host,
                    ContainerControlLoopService.CONTROL_LOOP_INFO_LINK),
                    false,
                    Service.Action.PATCH);

            Map<String, List<String>> redeployedContainersPerContextId = new HashMap<>();
            AtomicBoolean containerFromDesc2Redeployed = new AtomicBoolean(false);

            waitFor(() -> {
                // get all containers from containerDescription2
                retrieveContainerStates(containerDescription2.documentSelfLink).thenAccept(containerStates -> {
                    long healthyContainers = containerStates.stream().filter(cs -> PowerState.RUNNING.equals(cs.powerState)).count();
                    host.log("Healthy containers from %s : %d", containerDescription2.documentSelfLink, healthyContainers);
                    containerFromDesc2Redeployed.set(SINGLE_CONTAINERS_TO_BE_PROVISIONED == healthyContainers && SINGLE_CONTAINERS_TO_BE_PROVISIONED == containerStates.size());

                    containerStates.stream().forEach(cs -> {
                        redeployedContainersPerContextId.put(cs.customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY), Arrays.asList(cs.documentSelfLink));
                    });
                });

                if (containerFromDesc2Redeployed.get()) {
                    containersPerContextId.entrySet().stream().forEach(m -> {
                        String contextId = m.getKey();
                        List<String> redeployedContainers = redeployedContainersPerContextId.get(contextId);
                        host.log("Redeployed container: %s -> %s", StringUtils.join(m.getValue()), StringUtils.join(redeployedContainers));
                    });
                }

                return containerFromDesc2Redeployed.get();
            });
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Ignore("https://jira-hzn.eng.vmware.com/browse/VBV-1427")
    @Test
    public void periodicMaintenanceTest() throws Throwable {
        containerDescription2 = createContainerDescription(false);
        ServerSocket serverSocket = new ServerSocket(0);

        HealthConfig healthConfig = createHealthConfigTcp(serverSocket.getLocalPort());
        healthConfig.autoredeploy = true;

        containerDescription2.healthConfig = healthConfig;
        containerDescription2.tenantLinks = resourcePool.tenantLinks;
        doPut(containerDescription2);

        // starting a listener for the health check
        try  {
            // provision 3 single containers, 2 of them in ERROR state
            ContainerState state = null;
            for (int i = 0; i < SINGLE_CONTAINERS_TO_BE_PROVISIONED; i++) {
                state = provisionContainer(containerDescription2.documentSelfLink);

                if (i < SINGLE_CONTAINERS_TO_BE_PROVISIONED - 1) {
                    state.powerState = PowerState.ERROR;
                    doPut(state);
                }
            }

            Map<String, List<String>> containersPerContextId = new HashMap<>();

            retrieveContainerStates(containerDescription2.documentSelfLink).thenAccept(containerStates -> {
                containerStates.stream().forEach(cs -> {
                    containersPerContextId.put(cs.customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY), Arrays.asList(cs.documentSelfLink));
                });
            });

            Map<String, List<String>> redeployedContainersPerContextId = new HashMap<>();
            AtomicBoolean containerFromDesc2Redeployed = new AtomicBoolean(false);

            //Force test periodic maintenance. An @After method should set this back to true
            DeploymentProfileConfig.getInstance().setTest(false);

            waitFor(() -> {
                // get all containers from containerDescription2
                retrieveContainerStates(containerDescription2.documentSelfLink).thenAccept(containerStates -> {
                    long healthyContainers = containerStates.stream().filter(cs -> PowerState.RUNNING.equals(cs.powerState)).count();
                    host.log("Healthy containers from %s : %d", containerDescription2.documentSelfLink, healthyContainers);
                    containerFromDesc2Redeployed.set(SINGLE_CONTAINERS_TO_BE_PROVISIONED == healthyContainers && SINGLE_CONTAINERS_TO_BE_PROVISIONED == containerStates.size());

                    containerStates.stream().forEach(cs -> {
                        redeployedContainersPerContextId.put(cs.customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY), Arrays.asList(cs.documentSelfLink));
                    });
                });

                if (containerFromDesc2Redeployed.get()) {
                    containersPerContextId.entrySet().stream().forEach(m -> {
                        String contextId = m.getKey();
                        List<String> redeployedContainers = redeployedContainersPerContextId.get(contextId);
                        host.log("Redeployed container: %s -> %s", StringUtils.join(m.getValue()), StringUtils.join(redeployedContainers));
                    });
                }

                return containerFromDesc2Redeployed.get();
            });
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }

    private DeferredResult<List<ContainerState>> retrieveContainerStates(String descriptionLink) {
        Builder builder = Builder.create()
                .addKindFieldClause(ContainerState.class)
                .addFieldClause(ContainerState.FIELD_NAME_DESCRIPTION_LINK, descriptionLink);

        QueryByPages<ContainerState> query = new QueryByPages<>(host, builder.build(),
                ContainerState.class, null);

        return query.collectDocuments(Collectors.toList());
    }
}