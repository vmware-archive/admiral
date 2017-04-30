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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.ServerSocket;
import java.net.URI;
import java.util.Arrays;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.HttpVersion;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.RequestProtocol;
import com.vmware.admiral.compute.container.maintenance.ContainerStats;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class HealthConfigServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
    }


    @Test
    public void testContainerDescriptionServices() throws Throwable {
        verifyService(
                FactoryService.create(ContainerDescriptionService.class),
                ContainerDescription.class,
                (prefix, index) -> {
                    ContainerDescription containerDesc = createContainerDescription();
                    return containerDesc;
                } ,
                (prefix, serviceDocument) -> {
                    ContainerDescription contDesc = (ContainerDescription) serviceDocument;
                    HealthConfig healthConfig = contDesc.healthConfig;
                    assertEquals(healthConfig.protocol, RequestProtocol.HTTP);
                    assertEquals(healthConfig.healthyThreshold, Integer.valueOf(2));
                    assertEquals(healthConfig.unhealthyThreshold, Integer.valueOf(2));
                    assertEquals(healthConfig.httpMethod, Action.GET);
                    assertEquals(healthConfig.httpVersion, HttpVersion.HTTP_v1_1);
                    assertEquals(healthConfig.urlPath, TestHealthService.SELF_LINK);
                    assertEquals(healthConfig.timeoutMillis, Integer.valueOf(2000));
                });
    }

    @Test
    public void testHealthConfigPatch() throws Throwable {
        ContainerDescription containerDesc = createContainerDescription();

        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        ContainerDescription patchConfig = new ContainerDescription();
        patchConfig.healthConfig = new HealthConfig();
        patchConfig.healthConfig.urlPath = "/ping";
        patchConfig.healthConfig.protocol = RequestProtocol.TCP;
        doOperation(patchConfig, UriUtils.buildUri(host, containerDesc.documentSelfLink), false,
                Action.PATCH);

        ContainerDescription config = getDocument(ContainerDescription.class,
                containerDesc.documentSelfLink);
        assertEquals(patchConfig.healthConfig.urlPath, config.healthConfig.urlPath);

    }

    @Test
    public void testHealthConfigIdempotentPost() throws Throwable {
        String id = UUID.randomUUID().toString();
        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = ContainerDescriptionService.FACTORY_LINK + "/" + id;

        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);


        ContainerDescription putConfig = createContainerDescription();
        putConfig.documentSelfLink = ContainerDescriptionService.FACTORY_LINK + "/" + id;
        putConfig.healthConfig.urlPath = "/ping1";

        doOperation(putConfig, UriUtils.buildUri(host, putConfig.documentSelfLink),
                false,
                Action.PUT);

        containerDesc = getDocument(ContainerDescription.class, containerDesc.documentSelfLink);
        assertEquals(putConfig.healthConfig.urlPath, containerDesc.healthConfig.urlPath);

        putConfig = createContainerDescription();
        putConfig.documentSelfLink = ContainerDescriptionService.FACTORY_LINK + "/" + id;
        putConfig.healthConfig.urlPath = "/ping2";
        containerDesc = doPost(putConfig, ContainerDescriptionService.FACTORY_LINK);

        containerDesc = getDocument(ContainerDescription.class, containerDesc.documentSelfLink);
        assertEquals(putConfig.healthConfig.urlPath, containerDesc.healthConfig.urlPath);

        delete(containerDesc.documentSelfLink);

        putConfig = createContainerDescription();
        putConfig.documentSelfLink = ContainerDescriptionService.FACTORY_LINK + "/" + id;
        putConfig.healthConfig.urlPath = "/ping3";

        containerDesc = doPost(putConfig, ContainerDescriptionService.FACTORY_LINK);
        containerDesc = getDocument(ContainerDescription.class, containerDesc.documentSelfLink);
        assertEquals(putConfig.healthConfig.urlPath, containerDesc.healthConfig.urlPath);
    }

    private void verifyHealthFailureAfterTreshold(int unhealthyTreshold, ContainerDescription containerDesc,
            ContainerState container) throws Throwable {
        for (int i = 1; i < unhealthyTreshold; i++) {
            HealthChecker.getInstance().doHealthCheck(host, containerDesc.documentSelfLink);
            // expect the container to have changed status to degraded after maintenance - container url
            // is wrong
            final int failureCountExpected = i;
            waitFor(() -> {
                ContainerStats containerStats = getContainerStats(container.documentSelfLink);
                if (containerStats.healthCheckSuccess == null) {
                    return false;
                }
                return containerStats.healthCheckSuccess == false
                        && containerStats.healthFailureCount == failureCountExpected;
            });

            PowerState expectedPowerState;
            if (i == unhealthyTreshold) {
                expectedPowerState = PowerState.ERROR;
            } else {
                expectedPowerState = PowerState.RUNNING;
            }

            boolean checkForDegraded = i < unhealthyTreshold;

            waitFor(() -> {
                ContainerState containerWithError = getDocument(ContainerState.class, container.documentSelfLink);

                if (checkForDegraded && !containerWithError.status.equals(ContainerState.CONTAINER_DEGRADED_STATUS)) {
                    return false;
                }

                assertEquals(expectedPowerState, containerWithError.powerState);

                return true;
            });
        }

    }

    @Test
    public void testHealthCheckWithHttpAndDefaultPortTresholds() throws Throwable {
        // Create health config and a container to check the health for

        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "mockDescId");
        containerDesc.healthConfig = new HealthConfig();
        containerDesc.healthConfig.protocol = RequestProtocol.HTTP;
        containerDesc.healthConfig.httpMethod = Action.GET;
        containerDesc.healthConfig.urlPath = TestHealthService.SELF_LINK;
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        ContainerState container = createContainerStateNoAddress(containerDesc.documentSelfLink);
        container.address = host.getPreferredAddress();
        container.powerState = PowerState.RUNNING;
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        verifyHealthFailureAfterTreshold(3, containerDesc, container);

        // Start a test service to ping for health check
        TestHealthService pingService = new TestHealthService();
        URI pingServiceUri = UriUtils.buildUri(host, TestHealthService.SELF_LINK);
        host.startService(Operation.createPost(pingServiceUri), pingService);
        waitForServiceAvailability(TestHealthService.SELF_LINK);

        verifyHealthSuccessAfterTreshold(3, containerDesc, container);
    }

    @Test
    public void testHealthCheckWithHttpAndDefaultTimeout() throws Throwable {
        // Create health config and a container to check the health for

        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "mockDescId");
        containerDesc.healthConfig = new HealthConfig();
        containerDesc.healthConfig.protocol = RequestProtocol.HTTP;
        containerDesc.healthConfig.httpMethod = Action.GET;
        containerDesc.healthConfig.urlPath = TestHealthService.SELF_LINK;
        containerDesc.healthConfig.healthyThreshold = 1;
        containerDesc.healthConfig.unhealthyThreshold = 1;
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        ContainerState container = createContainerStateNoAddress(containerDesc.documentSelfLink);
        container.address = host.getPreferredAddress();
        container.powerState = PowerState.RUNNING;
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        // Start a test service to ping for health check
        TestHealthService pingService = new TestHealthService();
        URI pingServiceUri = UriUtils.buildUri(host, TestHealthService.SELF_LINK);
        host.startService(Operation.createPost(pingServiceUri), pingService);
        waitForServiceAvailability(TestHealthService.SELF_LINK);

        // Should succeed as the default timeout is enough for the request to the test service to succeed
        verifyHealthSuccessAfterTreshold(containerDesc.healthConfig.healthyThreshold, containerDesc, container);

        // should fail the next health checks because the timeout is less than the health check operation is going to take
        containerDesc.healthConfig.urlPath = TestHealthService.SELF_LINK + "/processTime=2000";
        containerDesc.healthConfig.timeoutMillis = 1000;
        doPatch(containerDesc, containerDesc.documentSelfLink);

        verifyHealthFailureAfterTreshold(containerDesc.healthConfig.unhealthyThreshold, containerDesc, container);
    }


    @Test
    public void testHealthCheckWithTcp() throws Throwable {
        // Create health config and a container to check the health for
        String mockContainerDescriptionLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "mockDescId");

        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = mockContainerDescriptionLink;

        ServerSocket serverSocket = new ServerSocket(0);
        containerDesc.healthConfig.protocol = RequestProtocol.TCP;
        containerDesc.healthConfig.port = serverSocket.getLocalPort();
        containerDesc.healthConfig.healthyThreshold = 1;
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        ContainerState container = createContainerStateNoAddress(mockContainerDescriptionLink);
        container.address = "fake";
        container.powerState = PowerState.RUNNING;
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        // Do maintenance
        verifyHealthFailureAfterTreshold(containerDesc.healthConfig.unhealthyThreshold.intValue(),
                containerDesc, container);

        ContainerState patch = new ContainerState();
        patch.address = "localhost";
        URI uri = UriUtils.buildUri(host, container.documentSelfLink);
        doOperation(patch, uri, false, Action.PATCH);

        try {
            verifyHealthSuccessAfterTreshold(containerDesc.healthConfig.healthyThreshold, containerDesc, container);
        } finally {
            serverSocket.close();
        }

    }

    @Test
    public void testHealthCheckWithCommand() throws Throwable {
        // Create health config and a container to check the health for
        String mockContainerDescriptionLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "mockDescId");

        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = mockContainerDescriptionLink;

        containerDesc.healthConfig.healthyThreshold = 1;
        containerDesc.healthConfig.protocol = RequestProtocol.COMMAND;
        containerDesc.healthConfig.command = "echo";
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        ContainerState container = createContainerStateNoAddress(mockContainerDescriptionLink);
        container.adapterManagementReference = UriUtils.buildPublicUri(host,
                MockDockerAdapterService.SELF_LINK);
        container.powerState = PowerState.RUNNING;
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        verifyHealthFailureAfterTreshold(containerDesc.healthConfig.unhealthyThreshold, containerDesc, container);

        MockDockerAdapterService dockerAdapterService = new MockDockerAdapterService();
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerAdapterService.class)), dockerAdapterService);
        waitForServiceAvailability(MockDockerAdapterService.SELF_LINK);

        verifyHealthSuccessAfterTreshold(containerDesc.healthConfig.healthyThreshold, containerDesc, container);
    }

    @Test
    public void testHealthCheckSuccessWithHttpAndDefaultPort() throws Throwable {
        // Create health config and a container to check the health for
        String mockContainerDescriptionLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "mockDescId");

        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = mockContainerDescriptionLink;
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        ContainerState container = createContainerStateNoAddress(mockContainerDescriptionLink);
        container.address = host.getPreferredAddress();
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        // Start a test service to ping for health check
        TestHealthService pingService = new TestHealthService();
        URI pingServiceUri = UriUtils.buildUri(host, TestHealthService.SELF_LINK);
        host.startService(Operation.createPost(pingServiceUri), pingService);
        waitForServiceAvailability(TestHealthService.SELF_LINK);

        verifyHealthSuccessAfterTreshold(containerDesc.healthConfig.healthyThreshold, containerDesc, container);
    }

    @Test
    public void testHealthCheckSuccessWithHttpAndPortBindings() throws Throwable {

        ComputeState containerHost = new ComputeState();
        containerHost.address = host.getPreferredAddress();
        containerHost.descriptionLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, "mockId");
        containerHost = doPost(containerHost, ComputeService.FACTORY_LINK);

        // Create health config and a container to check the health for
        String mockContainerDescriptionLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "mockDescId");
        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = mockContainerDescriptionLink;
        containerDesc.healthConfig.port = 85;
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        ContainerState container = createContainerStateNoAddress(mockContainerDescriptionLink);
        container.parentLink = containerHost.documentSelfLink;
        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "85";
        portBinding.hostPort = String.valueOf(host.getPort());
        container.ports = Arrays.asList(portBinding);
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        // Start a test service to ping for health check
        TestHealthService pingService = new TestHealthService();
        URI pingServiceUri = UriUtils.buildUri(host, TestHealthService.SELF_LINK);
        host.startService(Operation.createPost(pingServiceUri), pingService);
        waitForServiceAvailability(TestHealthService.SELF_LINK);

        verifyHealthSuccessAfterTreshold(containerDesc.healthConfig.healthyThreshold, containerDesc, container);
    }

    @Test
    public void testHealthCheckSuccessWithTcpAndPortBindings() throws Throwable {

        ComputeState containerHost = new ComputeState();
        containerHost.address = host.getPreferredAddress();
        containerHost.descriptionLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, "mockId");
        containerHost = doPost(containerHost, ComputeService.FACTORY_LINK);

        // Create health config and a container to check the health for
        String mockContainerDescriptionLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "mockDescId");
        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = mockContainerDescriptionLink;
        containerDesc.healthConfig.protocol = RequestProtocol.TCP;
        containerDesc.healthConfig.port = 8085;
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        ContainerState container = createContainerStateNoAddress(mockContainerDescriptionLink);
        container.parentLink = containerHost.documentSelfLink;
        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "8085";
        portBinding.hostPort = "8085";
        container.ports = Arrays.asList(portBinding);
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        try (ServerSocket socket = new ServerSocket(containerDesc.healthConfig.port)) {
            verifyHealthSuccessAfterTreshold(containerDesc.healthConfig.healthyThreshold, containerDesc, container);
        }
    }


    private void verifyHealthSuccessAfterTreshold(int successTreshold, ContainerDescription containerDesc, ContainerState container)
            throws Throwable {

        final String containerLink = container.documentSelfLink;

        for (int i = 1; i <= successTreshold; i++) {
            // Do maintenance
            HealthChecker.getInstance().doHealthCheck(host, containerDesc.documentSelfLink);

            final int successCount = i;
            waitFor(() -> {
                ContainerStats containerStats = getContainerStats(containerLink);

                return containerStats.healthCheckSuccess != null
                        && containerStats.healthCheckSuccess
                        && containerStats.healthSuccessCount == successCount;
            });
        }

        waitFor(() -> {
            ContainerState healthyContainer = getDocument(ContainerState.class, containerLink);

            return ContainerState.CONTAINER_RUNNING_STATUS.equals(healthyContainer.status)
                    && PowerState.RUNNING.equals(healthyContainer.powerState);
        });
    }

    private ContainerState createContainerStateNoAddress(String containerDescriptionLink) {
        ContainerState container = new ContainerState();
        container.descriptionLink = containerDescriptionLink;
        container.status = ContainerState.CONTAINER_RUNNING_STATUS;
        container.address = null;
        container.adapterManagementReference = URI
                .create("http://remote-host:8082/docker-executor");
        return container;
    }

    private ContainerDescription createContainerDescription() {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.image = "image:latest";
        containerDesc.healthConfig = createHealthConfig();
        return containerDesc;
    }

    private HealthConfig createHealthConfig() {
        HealthConfig healthConfig = new HealthConfig();
        healthConfig.protocol = RequestProtocol.HTTP;
        healthConfig.healthyThreshold = 2;
        healthConfig.unhealthyThreshold = 2;
        healthConfig.httpMethod = Action.GET;
        healthConfig.httpVersion = HttpVersion.HTTP_v1_1;
        healthConfig.urlPath = TestHealthService.SELF_LINK;
        healthConfig.timeoutMillis = 2000;

        return healthConfig;
    }

    private ContainerStats getContainerStats(String containerLink) throws Throwable {
        ServiceStats serviceStats = getDocument(ServiceStats.class, containerLink
                + ServiceHost.SERVICE_URI_SUFFIX_STATS);
        assertNotNull(serviceStats);
        return ContainerStats.transform(serviceStats);
    }

    private static class TestHealthService extends StatelessService {
        public static final String SELF_LINK = "/health-test";

        @Override
        public void handleRequest(Operation op) {
            // query encoded sleep to test timeouts
            String query = op.getUri().getQuery();
            if (query != null) {
                int sleepTime = Integer.parseInt(query.split("=")[1]);
                try {
                    Thread.sleep(sleepTime);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            op.complete();
        }
    }

}
