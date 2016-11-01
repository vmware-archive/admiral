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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerLogService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.Utils;

/**
 * This test the full networking feature using a complex deployment of simple containers.
 * The application consists of 2 services, 1 service we call client and the other - server.
 * Both are clustered. There is cluster of clients that are linked to a cluster of servers.
 * The server containers are simple http server that on request prints their hostname,
 * which by default is the short container id.
 * The client containers are shell processes that invokes curl to the server container some
 * number of times. The interesting part is that they call the server cluster by it's alias name
 * so it's up to a Load Balancer in the middle to pick up which server to handle the client's
 * request.
 *
 * The test verifies that all clients can successfully access the servers, and that all
 * servers have been accessed.
 * Also verifies that the cluster of servers is accessible through it's publicly exposed service
 * alias.
 */
@Ignore("https://jira-hzn.eng.vmware.com/browse/VSYM-152")
public class ContainerNetworkingIT extends BaseProvisioningOnCoreOsIT {
    private static final String TEMPLATE_FILE = "Identity_server_with_Curl_client.yaml";
    private static final String SERVER_NAME = "server";
    private static final String CLIENT_NAME = "client";

    private static final int SERVER_CLUSTER_SIZE = 2;
    private static final int CLIENT_CLUSTER_SIZE = 5;
    private static final int ALL_RESOURCER_SIZE = CLIENT_CLUSTER_SIZE + SERVER_CLUSTER_SIZE;
    private static final String SERVICE_RESPONSE_PREFIX = "Hello from ";
    private static final String LAST_LOG_ENTRY = "Done with curl loop";
    private static final int EXPECTED_NUMBER_OF_RESPONSE_LOG_ENTRIES = 10;

    private static ServiceClient serviceClient;

    private String compositeDescriptionLink;

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
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE);
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

        assertEquals("Unexpected number of resource links", 1,
                request.resourceLinks.size());

        CompositeComponent cc = getDocument(request.resourceLinks.iterator().next(), CompositeComponent.class);
        assertEquals("Unexpected number of component links", ALL_RESOURCER_SIZE,
                cc.componentLinks.size());

        List<String> clientResourceLinks = cc.componentLinks.stream()
                .filter((l) -> l.contains(CLIENT_NAME))
                .collect(Collectors.toList());

        assertEquals("Unexpected number of client resource links", CLIENT_CLUSTER_SIZE,
                clientResourceLinks.size());

        Set<String> serverHostnames = new HashSet<>();

        // verify that all clients can reach the server cluster
        for (String clientResourceLink : clientResourceLinks) {
            serverHostnames.addAll(getServiceHostNameFromLogs(clientResourceLink));
        }

        List<String> serverResourceLinks = cc.componentLinks.stream()
                .filter((l) -> l.contains(SERVER_NAME))
                .collect(Collectors.toList());

        assertEquals("Unexpected number of server resource links", SERVER_CLUSTER_SIZE,
                serverResourceLinks.size());

        assertEquals("Unexpected number of server hostnames in client responses links",
                SERVER_CLUSTER_SIZE,
                serverHostnames.size());

        // verify that all servers from the cluster have been reached from the clients
        for (String serverResourceLink : serverResourceLinks) {
            ContainerState serverContainer = getDocument(serverResourceLink, ContainerState.class);
            String shortId = getDockerShortId(serverContainer.id);
            assertTrue(serverHostnames.contains(shortId));
        }
    }

    private Set<String> getServiceHostNameFromLogs(String clientResourceLink) throws Exception {
        Set<String> hostnames = new HashSet<>();

        String logRequestUriPath = String.format("%s?%s=%s", ContainerLogService.SELF_LINK,
                ContainerLogService.CONTAINER_ID_QUERY_PARAM, extractId(clientResourceLink));

        String[] logsArr = new String[1];
        waitForStateChange(
                logRequestUriPath,
                (body) -> {
                    LogService.LogServiceState logServiceState = Utils.fromJson(body,
                            LogService.LogServiceState.class);

                    String logs = new String(logServiceState.logs);
                    logsArr[0] = logs;
                    return !logs.equals("--") && logs.contains(LAST_LOG_ENTRY);
                });

        String logs = logsArr[0];

        logger.info("Got logs from container %s: %s", clientResourceLink, logs);

        boolean serviceReached = false;
        int curlIndex = 1;
        Scanner scanner = new Scanner(logs);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            if (line.contains(LAST_LOG_ENTRY)) {
                break;
            }

            if (line.contains(String.format("%s - ", curlIndex))) {
                curlIndex++;
            }

            if (line.contains(SERVICE_RESPONSE_PREFIX)) {
                serviceReached = true;

                hostnames.add(line.substring(line.indexOf(SERVICE_RESPONSE_PREFIX)
                        + SERVICE_RESPONSE_PREFIX.length()));
            } else if (serviceReached) {
                // It is expected that the first few requests have no response because the client container starts making requests
                // immediately, at the same time the network is reconfigure to start handling requests from this container.
                // In terms of time, it is usually less than 10ms. After this every other request should be successful.
                fail("Unexpected result from logs, after connection to the service has "
                        + "established, all requests should have been printing result.");
            }
        }
        scanner.close();

        int totalResponses = curlIndex - 1;

        assertEquals(EXPECTED_NUMBER_OF_RESPONSE_LOG_ENTRIES + "curl commands were not executed",
                EXPECTED_NUMBER_OF_RESPONSE_LOG_ENTRIES, totalResponses);
        assertTrue("After " + EXPECTED_NUMBER_OF_RESPONSE_LOG_ENTRIES
                + " requests the service was not reached", serviceReached);

        return hostnames;
    }

    private static String getDockerShortId(String fullId) {
        return fullId.substring(0, 12);
    }
}
