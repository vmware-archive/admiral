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

    @Test
    public void testHandlePeriodicMaintenance() throws Throwable {
        // Create health config and a container to check the health for

        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "mockDescId");
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        ContainerState container = createContainerStateNoAddress(containerDesc.documentSelfLink);
        container.powerState = PowerState.RUNNING;
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        // Do maintenance
        new HealthChecker(host).doHealthCheck(containerDesc.documentSelfLink);
        // expect the container to have changed status to degraded after maintenance - container url
        // is wrong
        final String containerLink = container.documentSelfLink;
        waitFor(() -> {
            ContainerStats containerStats = getContainerStats(containerLink);
            if (containerStats.healthCheckSuccess == null) {
                return false;
            }
            return containerStats.healthCheckSuccess == false
                    && containerStats.healthFailureCount == 1;
        });

        waitFor(() -> {
            ContainerState containerWithError = getDocument(ContainerState.class, containerLink);
            if (!containerWithError.status.equals(ContainerState.CONTAINER_DEGRADED_STATUS)) {
                return false;
            }

            assertEquals(PowerState.RUNNING, containerWithError.powerState);

            return true;
        });

        new HealthChecker(host).doHealthCheck(containerDesc.documentSelfLink);

        waitFor(() -> {
            ContainerStats containerStats = getContainerStats(containerLink);
            return containerStats.healthCheckSuccess == false
                    && containerStats.healthFailureCount == 2;
        });

        waitFor(() -> {
            ContainerState containerWithError = getDocument(ContainerState.class, containerLink);
            if (!containerWithError.status.equals(ContainerState.CONTAINER_ERROR_STATUS)) {
                return false;
            }

            assertEquals(PowerState.ERROR, containerWithError.powerState);

            return true;
        });
    }

    @Test
    public void testHandlePeriodicMaintenanceOverTcp() throws Throwable {
        // Create health config and a container to check the health for
        String mockContainerDescriptionLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "mockDescId");

        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = mockContainerDescriptionLink;

        containerDesc.healthConfig.protocol = RequestProtocol.TCP;
        containerDesc.healthConfig.port = 8800;
        containerDesc.healthConfig.healthyThreshold = 1;
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        ContainerState container = createContainerStateNoAddress(mockContainerDescriptionLink);
        container.address = "fake";
        container.powerState = PowerState.RUNNING;
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        // Do maintenance
        new HealthChecker(host).doHealthCheck(containerDesc.documentSelfLink);
        // expect the container to have changed status to degraded after maintenance - container url
        // is wrong
        final String containerLink = container.documentSelfLink;
        waitFor(() -> {
            ContainerStats containerStats = getContainerStats(containerLink);
            if (containerStats.healthCheckSuccess == null) {
                return false;
            }

            return containerStats.healthCheckSuccess == false
                    && containerStats.healthFailureCount == 1;
        });

        waitFor(() -> {
            ContainerState containerWithError = getDocument(ContainerState.class, containerLink);
            if (!containerWithError.status.equals(ContainerState.CONTAINER_DEGRADED_STATUS)) {
                return false;
            }

            assertEquals(PowerState.RUNNING, containerWithError.powerState);

            return true;
        });

        ContainerState patch = new ContainerState();
        patch.address = "localhost";
        URI uri = UriUtils.buildUri(host, containerLink);
        doOperation(patch, uri, false, Action.PATCH);

        try (ServerSocket serverSocket = new ServerSocket(8800)) {
            new HealthChecker(host).doHealthCheck(containerDesc.documentSelfLink);

            waitFor(() -> {
                ContainerState containerWithError = getDocument(ContainerState.class,
                        containerLink);
                if (!containerWithError.status.equals(ContainerState.CONTAINER_RUNNING_STATUS)) {
                    return false;
                }

                assertEquals(PowerState.RUNNING, containerWithError.powerState);

                return true;
            });
        }

    }

    @Test
    public void testHandlePeriodicMaintenanceWithCommand() throws Throwable {
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

        // Do maintenance
        new HealthChecker(host).doHealthCheck(containerDesc.documentSelfLink);
        // expect the container to have changed status to degraded after maintenance - container url
        // is wrong
        final String containerLink = container.documentSelfLink;
        waitFor(() -> {
            ContainerStats containerStats = getContainerStats(containerLink);
            if (containerStats.healthCheckSuccess == null) {
                return false;
            }

            return containerStats.healthCheckSuccess == false
                    && containerStats.healthFailureCount == 1;
        });

        waitFor(() -> {
            ContainerState containerWithError = getDocument(ContainerState.class, containerLink);
            if (!containerWithError.status.equals(ContainerState.CONTAINER_DEGRADED_STATUS)) {
                return false;
            }

            assertEquals(PowerState.RUNNING, containerWithError.powerState);

            return true;
        });

        MockDockerAdapterService dockerAdapterService = new MockDockerAdapterService();
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerAdapterService.class)), dockerAdapterService);
        waitForServiceAvailability(MockDockerAdapterService.SELF_LINK);

        new HealthChecker(host).doHealthCheck(containerDesc.documentSelfLink);

        waitFor(() -> {
            ContainerState containerWithError = getDocument(ContainerState.class, containerLink);
            if (!containerWithError.status.equals(ContainerState.CONTAINER_RUNNING_STATUS)) {
                return false;
            }

            assertEquals(PowerState.RUNNING, containerWithError.powerState);

            return true;
        });

        stopService(dockerAdapterService);
    }

    @Test
    public void testHandlePeriodicMaintenanceWithSuccess() throws Throwable {
        host.log("Test testHandlePeriodicMaintenanceWithSuccess starts...");

        host.log("Creating HealthConfig...");
        // Create health config and a container to check the health for
        String mockContainerDescriptionLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "mockDescId");

        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = mockContainerDescriptionLink;
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        host.log("HealthConfig created. Creating ContainerState...");

        ContainerState container = createContainerStateNoAddress(mockContainerDescriptionLink);
        container.address = host.getPreferredAddress();
        container = doPost(container, ContainerFactoryService.SELF_LINK);

        host.log("ContainerState created. Creating ping service...");

        // Start a test service to ping for health check
        TestHealthService pingService = new TestHealthService();
        URI pingServiceUri = UriUtils.buildUri(host, TestHealthService.SELF_LINK);
        host.log("Ping service created. Starting...");
        host.startService(Operation.createPost(pingServiceUri), pingService);
        host.log("Ping service started.");

        // Do maintenance
        host.log("Starting health check...");
        new HealthChecker(host).doHealthCheck(containerDesc.documentSelfLink);
        host.log("Health check done. Waiting for updates...");

        final String containerLink = container.documentSelfLink;
        waitFor(() -> {
            ContainerStats containerStats = getContainerStats(containerLink);
            host.log("Waiting for health check updates...");

            return containerStats.healthCheckSuccess != null
                    && containerStats.healthCheckSuccess
                    && containerStats.healthSuccessCount == 1;
        });

        host.log("Second health check.");
        new HealthChecker(host).doHealthCheck(containerDesc.documentSelfLink);
        waitFor(() -> {
            ContainerState healthyContainer = getDocument(ContainerState.class, containerLink);

            return ContainerState.CONTAINER_RUNNING_STATUS.equals(healthyContainer.status)
                    && PowerState.RUNNING.equals(healthyContainer.powerState);
        });

        host.log("Test finished");
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
            op.complete();
        }
    }

}
