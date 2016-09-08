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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;

import java.net.Socket;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerLogService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ExposedServiceDescriptionService.ExposedServiceDescriptionState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.xenon.common.Operation;
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
    private static final String PUBLIC_SERVICE_PORT = "80";
    // As defined in the YAML file
    private static final String PUBLIC_SERVICE_WP_ADDRESS = "http://wordpress.cmp";
    private static final String MYSQL_START_MESSAGE_BEGIN = "Ready for start up.";

    private static final String MYSQL_START_MESSAGE_END = "ready for connections";
    private static final int MYSQL_START_WAIT_RETRY_COUNT = 20;
    private static final int MYSQL_START_WAIT_PRERIOD_MILLIS = 5000;
    private static final int STATUS_CODE_WAIT_POLLING_RETRY_COUNT = 30;

    private static ServiceClient serviceClient;

    private String compositeDescriptionLink;

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays
                .asList(new Object[] { "WordPress_with_MySQL_bindings.yaml" },
                        new Object[] { "WordPress_with_MySQL.yaml" });
    }

    private String templateFile;

    public WordpressProvisioningIT(String templateFile) {
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
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return compositeDescriptionLink;
    }

    @Override
    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {
        String dockerHost = getTestRequiredProp("docker.host.address");

        int expectedNumberOfResources = 3;

        assertEquals("Unexpected number of resource links", 1,
                request.resourceLinks.size());

        CompositeComponent cc = getDocument(request.resourceLinks.get(0), CompositeComponent.class);
        assertEquals("Unexpected number of component links", expectedNumberOfResources,
                cc.componentLinks.size());

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

        // connect to the mysql only by opening a socket to it through the docker exposed port
        logger.info("------------- 3. connecting to mysql tcp://%s:%s. -------------", dockerHost,
                mysqlHostPort);
        try {
            verifyMysqlConnection(dockerHost, Integer.valueOf(mysqlHostPort),
                    STATUS_CODE_WAIT_POLLING_RETRY_COUNT);
        } catch (Exception e) {
            logger.error("Failed to verify mysql connection: %s", e.getMessage());
            logger.info("mysql logs: \n%s", getLogs(mysqlContainerLink));
            fail();
        }

        // find the host port of the WP container
        String wpContainerLink = getResourceContaining(cc.componentLinks, WP_NAME);
        assertNotNull(wpContainerLink);

        logger.info("------------- 4. Retrieving container state for %s. -------------",
                wpContainerLink);
        ContainerState wpContainerState = getDocument(wpContainerLink, ContainerState.class);
        assertEquals("Unexpected number of ports", 1, wpContainerState.ports.size());
        portBinding = wpContainerState.ports.get(0);
        String wpHostPort = portBinding.hostPort;

        assertNotNull("Failed to find WP host port", wpHostPort);

        // connect to wordpress main page by accessing a specific container instance through the docker exposed port
        URI uri = URI.create(String.format("http://%s:%s/%s", dockerHost, wpHostPort, WP_PATH));
        logger.info("------------- 5. connecting to wordpress main page %s. -------------", uri);
        try {
            waitForStatusCode(uri, Operation.STATUS_CODE_OK, STATUS_CODE_WAIT_POLLING_RETRY_COUNT);
        } catch (Exception e) {
            logger.error("Failed to verify wordpress connection: %s", e.getMessage());
            logger.info("wordpress logs: \n%s", getLogs(wpContainerLink));
            logger.info("mysql logs: \n%s", getLogs(mysqlContainerLink));

            logger.info("Trying again.");

            logger.info("------------- 5.1. restarting wordpress container -------------");
            restartContainerDay2(wpContainerLink);

            wpContainerState = getDocument(wpContainerLink, ContainerState.class);
            wpHostPort = wpContainerState.ports.get(0).hostPort;

            // connect to wordpress main page by accessing a specific container instance through the docker exposed port
            uri = URI.create(String.format("http://%s:%s/%s", dockerHost, wpHostPort, WP_PATH));

            logger.info("------------- 5.2. connecting to wordpress main page %s. -------------",
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

        // connect to wordpress main page by accessing the publicly exposed service address, reaching all wordpress container nodes
        assertNotNull(wpContainerState.exposedServiceLink);
        ExposedServiceDescriptionState exposedServiceDescriptionState = getDocument(
                wpContainerState.exposedServiceLink, ExposedServiceDescriptionState.class);

        assertEquals(wpContainerState.parentLink, exposedServiceDescriptionState.hostLink);

        String calculatedPublicServiceAddress = exposedServiceDescriptionState.addressConfigs[0].address;
        assertTrue(calculatedPublicServiceAddress.startsWith(PUBLIC_SERVICE_WP_ADDRESS));
        assertNotEquals(calculatedPublicServiceAddress, PUBLIC_SERVICE_WP_ADDRESS);

        String host = UriUtilsExtended.extractHost(calculatedPublicServiceAddress);

        URI publicUri = URI.create(String.format("http://%s:%s/%s", dockerHost,
                PUBLIC_SERVICE_PORT, WP_PATH));
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Host", host);
        logger.info(
                "------------- 6. connecting to wordpress main page by acceessing publically exposed service address %s with Host header %s. -------------",
                publicUri, calculatedPublicServiceAddress);
        waitForStatusCode(publicUri, headers,
                Operation.STATUS_CODE_OK,
                STATUS_CODE_WAIT_POLLING_RETRY_COUNT);
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
}
