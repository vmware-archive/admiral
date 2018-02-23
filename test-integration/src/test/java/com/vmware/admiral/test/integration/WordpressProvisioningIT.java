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

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;
import static com.vmware.admiral.request.ReservationAllocationTaskService.CONTAINER_HOST_ID_CUSTOM_PROPERTY;

import java.net.Socket;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerLogService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.Utils;

/**
 * Test multi container provisioning of Wordpress and MySQL from a YAML template
 */
@RunWith(Parameterized.class)
public class WordpressProvisioningIT extends BaseProvisioningOnCoreOsIT {
    private static final String WP_PATH = "wp-admin/install.php?step=1";
    private static final String WP_NAME = "wordpress";
    private static final String MYSQL_NAME = "mysql";
    private static final String EXTERNAL_NETWORK_NAME = "external_wpnet";
    private static final String MYSQL_START_MESSAGE_BEGIN = "Ready for start up.";
    private static final String MYSQL_START_MESSAGE_END = "ready for connections";
    private static final int MYSQL_START_WAIT_RETRY_COUNT = 20;
    private static final int MYSQL_START_WAIT_PRERIOD_MILLIS = 5000;
    private static final int STATUS_CODE_WAIT_POLLING_RETRY_COUNT = 30;
    private static final int NUMBER_OF_NETWORKS_PER_APPLICATION = 1;


    private String compositeDescriptionLink;
    private ContainerNetworkState externalNetwork;

    private enum NetworkType {
        CUSTOM, // agent or bindings
        USER_DEFINED_BRIDGE, USER_DEFINED_OVERLAY, EXTERNAL_BRIDGE, EXTERNAL_OVERLAY, BRIDGE
    }

    @Parameters(name = "{index}: {0} {1}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "WordPress_with_MySQL_bindings.yaml", NetworkType.CUSTOM },
                // In case of response 500 from docker check:
                // https://github.com/docker/libnetwork/issues/1101
                { "WordPress_with_MySQL_network.yaml", NetworkType.USER_DEFINED_BRIDGE },
                { "WordPress_with_MySQL_network.yaml", NetworkType.USER_DEFINED_OVERLAY },
                { "WordPress_with_MySQL_network_external.yaml", NetworkType.EXTERNAL_BRIDGE },
                { "WordPress_with_MySQL_network_external.yaml", NetworkType.EXTERNAL_OVERLAY },
                { "WordPress_with_MySQL_links.yaml", NetworkType.BRIDGE }
        });
    }

    private final String templateFile;
    private final NetworkType networkType;

    public WordpressProvisioningIT(String templateFile, NetworkType networkType) {
        this.templateFile = templateFile;
        this.networkType = networkType;
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
        boolean setupOnCluster = useOverlayNetwork();
        setupCoreOsHost(DockerAdapterType.API, setupOnCluster, null);
        checkNumberOfNetworks(serviceClient, NUMBER_OF_NETWORKS_PER_APPLICATION);

        if (useExternalNetwork()) {
            setupExternalNetwork();
        }

        logger.info("---------- 5. Create test docker image container description. --------");
        requestContainerAndDelete(getResourceDescriptionLink(false, RegistryType.V1_SSL_SECURE));
    }

    private void setupExternalNetwork() throws Exception {
        logger.info("Setting up external network...");

        ContainerNetworkDescription description = new ContainerNetworkDescription();
        description.documentSelfLink = UUID.randomUUID().toString();
        description.name = EXTERNAL_NETWORK_NAME;
        description.driver = useOverlayNetwork() ? "overlay" : "bridge";
        description.tenantLinks = TENANT;
        description.customProperties = new HashMap<>();
        description.customProperties.put(CONTAINER_HOST_ID_CUSTOM_PROPERTY,
                Service.getId(getDockerHost().documentSelfLink));

        description = postDocument(ContainerNetworkDescriptionService.FACTORY_LINK, description);

        RequestBrokerState request = requestExternalNetwork(description.documentSelfLink);

        externalNetwork = getDocument(request.resourceLinks.iterator().next(),
                ContainerNetworkState.class);
        assertNotNull(externalNetwork);
        assertTrue(externalNetwork.connectedContainersCount == 0);

        logger.info("External network created.");

        // Replace the compositeDescriptionLink to make use of the newly created external network!

        delete(compositeDescriptionLink);

        compositeDescriptionLink = importTemplateWithExternalNetwork(serviceClient, templateFile,
                externalNetwork.name);
    }

    private String importTemplateWithExternalNetwork(ServiceClient serviceClient, String filePath,
            String networkName) throws Exception {
        String template = CommonTestStateFactory.getFileContent(filePath);

        template = template.replaceAll(EXTERNAL_NETWORK_NAME, networkName);

        URI uri = URI.create(
                getBaseUrl() + buildServiceUri(CompositeDescriptionContentService.SELF_LINK));

        Operation op = sendRequest(serviceClient, Operation.createPost(uri)
                .setContentType(MEDIA_TYPE_APPLICATION_YAML)
                .setBody(template));

        String location = op.getResponseHeader(Operation.LOCATION_HEADER);
        assertNotNull("Missing location header", location);

        logger.info("Successfully imported: %s", template);

        return URI.create(location).getPath();
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return compositeDescriptionLink;
    }

    @Override
    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {
        int expectedNumberOfResources = 3;

        if (createsNetworkResource()) {
            // +1 resource for the network itself
            expectedNumberOfResources++;
        }

        assertEquals("Unexpected number of resource links", 1,
                request.resourceLinks.size());

        CompositeComponent cc = getDocument(request.resourceLinks.iterator().next(),
                CompositeComponent.class);
        assertEquals("Unexpected number of component links", expectedNumberOfResources,
                cc.componentLinks.size());

        if (useExternalNetwork()) {
            // check single network state in use for external network
            String usedNetworkLink = getResourceContaining(cc.componentLinks,
                    EXTERNAL_NETWORK_NAME);
            if (usedNetworkLink == null) {
                logger.warning("Cannot find network with name: %s in list components: %s",
                        EXTERNAL_NETWORK_NAME, cc.componentLinks);

                String leftoverNetworkLink = getResourceContaining(cc.componentLinks,
                        ContainerNetworkService.FACTORY_LINK);
                if (leftoverNetworkLink != null) {
                    // A sporadic race condition between an external network just created and the
                    // network data collection process may cause the test to fail and leave some
                    // network leftover. The problem is being fixed but this should avoid keeping
                    // leftover networks in the test Docker hosts.
                    logger.info("Found an external network leftover: %s in list components: %s",
                            leftoverNetworkLink, cc.componentLinks);
                    externalNetworksToDelete.add(leftoverNetworkLink);
                }
            }

            assertEquals(externalNetwork.documentSelfLink, usedNetworkLink);
        }

        String mysqlContainerLink = getResourceContaining(cc.componentLinks, MYSQL_NAME);
        assertNotNull(mysqlContainerLink);

        logger.info("------------- 1. Retrieving container state for %s. -------------",
                mysqlContainerLink);
        ContainerState mysqlContainerState = getDocument(mysqlContainerLink, ContainerState.class);
        assertEquals("Unexpected number of ports", 1, mysqlContainerState.ports.size());
        PortBinding portBinding = mysqlContainerState.ports.get(0);
        String mysqlHostPort = portBinding.hostPort;

        assertNotNull("Failed to find mysql host port", mysqlHostPort);

        // connect to the mysql only by opening a socket to it through the docker exposed port
        logger.info("------------- 2. waiting mysql to start. -------------");
        try {
            waitMysqlToStart(mysqlContainerLink);
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.info("mysql logs: \n%s", getLogs(mysqlContainerLink));
            fail();
        }

        String mysqlHost = getHostnameOfComputeHost(mysqlContainerState.parentLink);
        // connect to the mysql only by opening a socket to it through the docker exposed port
        logger.info("------------- 3. connecting to mysql tcp://%s:%s. -------------", mysqlHost,
                mysqlHostPort);
        try {
            verifyMysqlConnection(mysqlHost, Integer.valueOf(mysqlHostPort),
                    STATUS_CODE_WAIT_POLLING_RETRY_COUNT);
        } catch (Exception e) {
            logger.error("Failed to verify mysql connection: %s", e.getMessage());
            logger.info("mysql logs: \n%s", getLogs(mysqlContainerLink));
            fail();
        }

        int wpContainersCount = 0;
        ContainerState wpContainerState = null;
        String wpHost = null;
        String wpContainerLink;
        while ((wpContainerLink = getResourceContaining(cc.componentLinks, WP_NAME)) != null) {
            cc.componentLinks.remove(wpContainerLink);
            wpContainersCount++;

            logger.info("------------- 4.%s.1. Retrieving container state for %s. -------------",
                    wpContainersCount, wpContainerLink);
            wpContainerState = getDocument(wpContainerLink, ContainerState.class);
            assertEquals("Unexpected number of ports", 1, wpContainerState.ports.size());
            portBinding = wpContainerState.ports.get(0);
            String wpHostPort = portBinding.hostPort;

            assertNotNull("Failed to find WP host port", wpHostPort);

            wpHost = getHostnameOfComputeHost(wpContainerState.parentLink);
            // connect to wordpress main page by accessing a specific container instance through the
            // docker exposed port
            URI uri = URI.create(String.format("http://%s:%s/%s", wpHost, wpHostPort, WP_PATH));
            logger.info(
                    "------------- 4.%s.2. connecting to wordpress main page %s. -------------",
                    wpContainersCount,
                    uri);
            try {
                waitForStatusCode(uri, Operation.STATUS_CODE_OK,
                        STATUS_CODE_WAIT_POLLING_RETRY_COUNT);
            } catch (Exception e) {
                logger.error("Failed to verify wordpress connection: %s", e.getMessage());
                logger.info("wordpress logs: \n%s", getLogs(wpContainerLink));
                logger.info("mysql logs: \n%s", getLogs(mysqlContainerLink));

                logger.info("Trying again.");

                logger.info("------------- 4.%s.2.1. restarting wordpress container -------------",
                        wpContainersCount);
                restartContainerDay2(wpContainerLink);

                wpContainerState = getDocument(wpContainerLink, ContainerState.class);
                wpHostPort = wpContainerState.ports.get(0).hostPort;

                // connect to wordpress main page by accessing a specific container instance through
                // the docker exposed port
                uri = URI.create(String.format("http://%s:%s/%s", wpHost, wpHostPort, WP_PATH));

                logger.info(
                        "------------- 4.%s.2.2. connecting to wordpress main page %s. -------------",
                        wpContainersCount,
                        uri);
                try {
                    waitForStatusCode(uri, Operation.STATUS_CODE_OK,
                            STATUS_CODE_WAIT_POLLING_RETRY_COUNT);
                } catch (Exception eInner) {
                    logger.error("Failed to verify wordpress connection: %s", eInner.getMessage());
                    logger.info("wordpress logs: \n%s", getLogs(wpContainerLink));
                    logger.info("mysql logs: \n%s", getLogs(mysqlContainerLink));
                    fail();
                }
            }
        }

        assertEquals(2, wpContainersCount);

        if (createsNetworkResource()) {
            // Verify the connectedContainersCount number
            String networkLink = getResourceContaining(cc.componentLinks,
                    ContainerNetworkService.FACTORY_LINK);
            assertNotNull(networkLink);
            ContainerNetworkState network = getDocument(networkLink, ContainerNetworkState.class);
            assertNotNull(network);
            assertNotNull(network.connectedContainersCount);
            assertEquals(3, network.connectedContainersCount.intValue()); // 2 wp + 1 mysql
        }
    }

    private void verifyMysqlConnection(String dockerHost, int mysqlHostPort, int retryCount)
            throws Exception {
        for (int i = 0; i < retryCount; i++) {
            Socket clientSocket = null;
            try {
                clientSocket = new Socket(dockerHost, mysqlHostPort);
                if (clientSocket.isConnected()) {
                    return;
                }
                logger.warning("Unable to verify mysql connection. Socket not connected.");
            } catch (Exception e) {
                logger.warning("Unable to verify mysql connection: %s", e.getMessage());
            } finally {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            }
            Thread.sleep(STATE_CHANGE_WAIT_POLLING_PERIOD_MILLIS);
        }

        throw new TimeoutException("Could not connect to mysql");
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

    private void waitMysqlToStart(String containerLink) throws Exception {
        for (int i = 0; i < MYSQL_START_WAIT_RETRY_COUNT; i++) {
            String logs = getLogs(containerLink);
            int beginIndex = logs.indexOf(MYSQL_START_MESSAGE_BEGIN);
            int endIndex = logs.lastIndexOf(MYSQL_START_MESSAGE_END);

            if (beginIndex > -1 && endIndex > beginIndex) {
                return;
            }

            Thread.sleep(MYSQL_START_WAIT_PRERIOD_MILLIS);
        }

        throw new IllegalStateException("Mysql failed to start");
    }

    private boolean useExternalNetwork() {
        switch (networkType) {
        case EXTERNAL_BRIDGE:
        case EXTERNAL_OVERLAY:
            return true;
        default:
            return false;
        }
    }

    private boolean useOverlayNetwork() {
        switch (networkType) {
        case USER_DEFINED_OVERLAY:
        case EXTERNAL_OVERLAY:
            return true;
        default:
            return false;
        }
    }

    private boolean createsNetworkResource() {
        switch (networkType) {
        case USER_DEFINED_BRIDGE:
        case USER_DEFINED_OVERLAY:
        case EXTERNAL_BRIDGE:
        case EXTERNAL_OVERLAY:
            return true;
        default:
            return false;
        }
    }
}
