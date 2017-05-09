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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerLogService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.xenon.common.Utils;

/**
 * Test multi container provisioning of Voting App from a YAML template
 */
@RunWith(Parameterized.class)
public class VotingAppProvisioningIT extends BaseProvisioningOnCoreOsIT {
    private static final int NUMBER_OF_NETWORKS_PER_APPLICATION = 1;

    private String compositeDescriptionLink;

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "VotingApp.yaml" },
        });
    }

    private final String templateFile;

    public VotingAppProvisioningIT(String templateFile) {
        this.templateFile = templateFile;
    }

    @BeforeClass
    public static void beforeClass() {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @AfterClass
    public static void afterClass() {
        serviceClient.stop();
    }

    @Before
    public void setUp() throws Exception {
        logger.info("Using " + templateFile + " blueprint");
        compositeDescriptionLink = importTemplate(serviceClient, templateFile);
    }

    @Test
    public void testProvision() throws Exception {
        setupCoreOsHost(DockerAdapterType.API, false);
        checkNumberOfNetworks(serviceClient, NUMBER_OF_NETWORKS_PER_APPLICATION);

        logger.info("---------- 5. Create test docker image container description. --------");
        requestContainerAndDelete(getResourceDescriptionLink(false, RegistryType.V1_SSL_SECURE));
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return compositeDescriptionLink;
    }

    @Override
    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {

        assertEquals("Unexpected number of resource links", 1,
                request.resourceLinks.size());

        CompositeComponent cc = getDocument(request.resourceLinks.iterator().next(),
                CompositeComponent.class);
        assertEquals("Unexpected number of component links", 8,
                cc.componentLinks.size());

        logger.info("----------Assert back-tier network --------");
        String backTierNetworkLink = getResourceContaining(cc.componentLinks,
                "back-tier");
        if (backTierNetworkLink == null) {
            logger.warning("Cannot find network with name: %s in list components: %s",
                    "back-tier", cc.componentLinks);
        }
        ContainerNetworkState backTierNetwork = getDocument(backTierNetworkLink,
                ContainerNetworkState.class);
        assertNotNull(backTierNetwork);
        assertNotNull(backTierNetwork.connectedContainersCount);
        assertEquals(5, backTierNetwork.connectedContainersCount.intValue());

        logger.info("----------Assert front-tier network --------");
        String frontTierNetworkLink = getResourceContaining(cc.componentLinks,
                "front-tier");
        if (frontTierNetworkLink == null) {
            logger.warning("Cannot find network with name: %s in list components: %s",
                    "front-tier", cc.componentLinks);
        }
        ContainerNetworkState frontTierNetwork = getDocument(frontTierNetworkLink,
                ContainerNetworkState.class);
        assertNotNull(frontTierNetwork);
        assertNotNull(frontTierNetwork.connectedContainersCount);
        assertEquals(2, frontTierNetwork.connectedContainersCount.intValue());

        logger.info("----------Assert postgres data base container --------");
        String db = getResourceContaining(cc.componentLinks, "db");
        assertNotNull("DB not found.", db);
        ContainerState dbContainerState = getDocument(db, ContainerState.class);
        assertNotNull("db-data state not found.", dbContainerState);

        logger.info("----------Assert data base volume --------");
        String dbData = getResourceContaining(cc.componentLinks, "db-data");
        assertNotNull("db-data not found.", dbData);
        ContainerState dbDateContainerState = getDocument(dbData, ContainerState.class);
        assertNotNull("db-data state not found.", dbDateContainerState);

        logger.info("----------Assert redis conatainer --------");
        String redis = getResourceContaining(cc.componentLinks, "redis");
        assertNotNull("Redis not found.", redis);
        ContainerState redisContainerState = getDocument(redis, ContainerState.class);
        assertEquals("Unexpected number of ports", 1, redisContainerState.ports.size());
        PortBinding redisPortBinding = redisContainerState.ports.get(0);
        String redisContainerPort = redisPortBinding.containerPort;

        assertNotNull("Failed to find redis host port", redisContainerPort);
        assertEquals("6379", redisContainerPort);

        logger.info("----------Assert result container --------");
        List<String> resultExpectedPorts = new ArrayList<>();
        resultExpectedPorts.add("80");
        resultExpectedPorts.add("5858");
        String result = getResourceContaining(cc.componentLinks, "result");
        assertNotNull("Result not found.", result);
        ContainerState resultContainerState = getDocument(result, ContainerState.class);
        assertNotNull("Result state not found.", resultContainerState);
        assertEquals("Unexpected number of ports", 2, resultContainerState.ports.size());
        String resultAccessHostPort = null;
        for (int i = 0; i < resultContainerState.ports.size(); i++) {
            PortBinding resultPortBinding = resultContainerState.ports.get(i);
            String resultContainerPort = resultPortBinding.containerPort;
            assertNotNull("Failed to find result host port", resultContainerPort);
            assertTrue("In result container fount wrong port: " + resultContainerPort,
                    resultExpectedPorts.contains(resultContainerPort));

            if (resultContainerPort.contentEquals("80")) {
                resultAccessHostPort = resultPortBinding.hostPort;
            }
        }
        assertNotNull("No host port binding found for the result containtainer port 80.",
                resultAccessHostPort);

        logger.info("----------Assert vote container --------");
        String vote = getResourceContaining(cc.componentLinks, "vote");
        assertNotNull("Vote not found.", vote);
        ContainerState voteContainerState = getDocument(vote, ContainerState.class);
        assertNotNull("Vote state not found.", voteContainerState);
        assertEquals("Unexpected number of ports", 1, voteContainerState.ports.size());
        PortBinding votePortBinding = voteContainerState.ports.get(0);
        String voteContainerPort = votePortBinding.containerPort;
        assertNotNull("Failed to find mysql host port", voteContainerPort);
        assertEquals("80", voteContainerPort);

        String worker = getResourceContaining(cc.componentLinks, "worker");
        assertNotNull("Worker not found.", worker);
        ContainerState workerContainerState = getDocument(worker, ContainerState.class);
        assertNotNull("Worker state not found.", workerContainerState);

        logger.info("----------Assert that the data base started correctly --------");
        try {
            waitDBToStart(db);
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.info("db logs: \n%s", getLogs(db));
            fail();
        }

        logger.info("---------- Check connection with vote app --------");
        String voteHost = getHostnameOfComputeHost(voteContainerState.parentLink);
        try {
            verifyConnection(voteHost, Integer.valueOf(votePortBinding.hostPort));
        } catch (Exception e) {
            logger.error("Failed to verify vote connection: %s", e.getMessage());
            logger.info("vote logs: \n%s", getLogs(vote));
            fail();
        }

        logger.info("---------- Check connection with result app --------");
        String resultHost = getHostnameOfComputeHost(voteContainerState.parentLink);
        try {
            verifyConnection(resultHost, Integer.valueOf(resultAccessHostPort));
        } catch (Exception e) {
            logger.error("Failed to verify result connection: %s", e.getMessage());
            logger.info("result logs: \n%s", getLogs(vote));
            fail();
        }
    }

    private void verifyConnection(String dockerHost, int hostPort)
            throws Exception {
        try {
            waitFor(t -> {
                Socket clientSocket = null;
                try {
                    try {
                        clientSocket = new Socket(dockerHost, hostPort);
                        if (clientSocket.isConnected()) {
                            return true;
                        }
                        logger.warning("Unable to verify connection. Socket not connected.");
                        return false;
                    } finally {
                        if (clientSocket != null) {
                            clientSocket.close();
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Unable to verify connection: %s", e.getMessage());
                    return false;
                }
            });
        } catch (Exception e) {
            throw new TimeoutException(
                    String.format("Could not connect to: %s:%s", dockerHost, hostPort));
        }

    }

    private void waitDBToStart(String containerLink) throws Exception {
        try {
            waitFor(t -> {
                try {
                    String logs = getLogs(containerLink);
                    int readyIndex = logs
                            .indexOf("PostgreSQL init process complete; ready for start up.");

                    if (readyIndex > -1) {
                        return true;
                    }
                } catch (Exception ex) {
                    logger.error("Problem getting db logs: ", ex);
                    return false;
                }
                return false;
            });
        } catch (Exception e) {
            throw new IllegalStateException("DB failed to start. " + e.getMessage());
        }
    }

    private String getLogs(String containerLink) throws Exception {
        String logRequestUriPath = String.format("%s?%s=%s", ContainerLogService.SELF_LINK,
                ContainerLogService.CONTAINER_ID_QUERY_PARAM, extractId(containerLink));

        String[] logsArr = new String[1];
        waitForStateChange(
                logRequestUriPath,
                (body) -> {
                    LogService.LogServiceState logServiceState = Utils.fromJson(body,
                            LogService.LogServiceState.class);

                    String logs = new String(logServiceState.logs);
                    logsArr[0] = logs;
                    return !logs.equals("--");
                });

        return logsArr[0];
    }
}
