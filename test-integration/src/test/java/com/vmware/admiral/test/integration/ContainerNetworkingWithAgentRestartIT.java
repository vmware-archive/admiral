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

import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.ContainerLogService;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.Utils;

/**
 * This test verifies that when containers are linked over our network and when the network agent
 * is restarted the client will reconnect after few failures.
 */
@Ignore("https://jira-hzn.eng.vmware.com/browse/VSYM-152")
public class ContainerNetworkingWithAgentRestartIT extends BaseProvisioningOnCoreOsIT {
    private static final String TEMPLATE_FILE = "Identity_server_with_Curl_client_long_lived.yaml";
    private static final String CLIENT_NAME = "client";

    private static final int SERVER_CLUSTER_SIZE = 1;
    private static final int CLIENT_CLUSTER_SIZE = 1;
    private static final int ALL_RESOURCER_SIZE = CLIENT_CLUSTER_SIZE + SERVER_CLUSTER_SIZE;
    private static final String SERVICE_RESPONSE_PREFIX = "Hello from ";
    private static final String LAST_LOG_ENTRY = "Done with curl loop";

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

        assertEquals("Unexpected number of resource links", ALL_RESOURCER_SIZE,
                request.resourceLinks.size());

        List<String> clientResourceLinks = request.resourceLinks.stream()
                .filter((l) -> l.contains(CLIENT_NAME))
                .collect(Collectors.toList());

        assertEquals("Unexpected number of client resource links", CLIENT_CLUSTER_SIZE,
                clientResourceLinks.size());

        String clientLink = clientResourceLinks.get(0);
        String logs = waitForLogs(clientLink, SERVICE_RESPONSE_PREFIX);

        assertNotNull(logs);
        assertTrue(logs.contains(SERVICE_RESPONSE_PREFIX));

        logger.info("Client container is up, received first logs \n%s.", logs);
        logger.info("Restarting network agent...");
        String hostId = Service.getId(dockerHostCompute.documentSelfLink);
        restartContainer(SystemContainerDescriptions.getSystemContainerSelfLink(
                SystemContainerDescriptions.AGENT_CONTAINER_NAME, hostId));
        logger.info("Network agent restarted");

        String newLogs = waitForLogs(clientLink, LAST_LOG_ENTRY);
        assertNotNull(newLogs);
        String restOfLogs = newLogs.replaceAll(logs, "");
        logger.info("Client container logs after network agent restart was triggered \n%s.",
                restOfLogs);

        boolean failuresInLog = false;
        boolean successAfterFailuresFlag = false;

        Scanner scanner = new Scanner(restOfLogs);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            if (line.contains(LAST_LOG_ENTRY)) {
                break;
            }

            if (line.contains("Connection refused")) {
                if (successAfterFailuresFlag) {
                    fail("Expected that after network agent was started up all requests were successful");
                }
                failuresInLog = true;
            } else if (failuresInLog && line.contains(SERVICE_RESPONSE_PREFIX)) {
                successAfterFailuresFlag = true;
            }
        }
        scanner.close();

        assertTrue(
                "Expected that there were connection failures while network agent container was restarted",
                failuresInLog);
        assertTrue("Expected that after network agent was started up all requests were successful",
                successAfterFailuresFlag);
    }

    private String waitForLogs(String clientResourceLink, String stringToContain) throws Exception {
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
                    return !logs.equals("--") && logs.contains(stringToContain);
                });

        String logs = logsArr[0];

        return logs;
    }
}
