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
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.HttpVersion;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.RequestProtocol;
import com.vmware.admiral.compute.container.maintenance.ContainerStats;
import com.vmware.admiral.compute.container.maintenance.ContainerStatsEvaluator;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.TestRequestSender.FailureResponse;

public class ContainerStatsServiceTest extends ComputeBaseTest {
    private ContainerStats containerStats;
    private ContainerState containerState;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(CompositeDescriptionFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
        containerState = new ContainerState();
        containerState.image = "test-image";
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);

        containerStats = new ContainerStats();
    }

    @Test
    public void testPostAndCalculate() throws Throwable {
        String statsJson = buildContainerStatsJson();
        containerStats = patchStats(ContainerStatsEvaluator.calculateStatsValues(statsJson));

        assertEquals(29.92d, containerStats.cpuUsage, 0);

        assertEquals(34887, containerStats.networkIn);
        assertEquals(579367, containerStats.networkOut);

        assertEquals(3042080, containerStats.memUsage);
        assertEquals(5000000, containerStats.memLimit);
    }

    @Test
    public void testPatchHealthStatusHealthConfigNotSet() throws Throwable {
        containerStats = patchStats(containerStats);
        ContainerStats patchState = new ContainerStats();
        patchState.healthCheckSuccess = true;
        ContainerStats stats = patchStats(patchState);

        assertEquals(true, stats.healthCheckSuccess);
    }

    @Test
    public void testPatchHealthStatus() throws Throwable {
        // crate a health config and a container to check the health for

        String mockContainerDescriptionLink = UriUtils.buildUriPath(
                ContainerDescriptionService.FACTORY_LINK, "mockDescId");

        ContainerDescription containerDesc = createContainerDescription();
        containerDesc.documentSelfLink = mockContainerDescriptionLink;
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        containerState = createContainerState(mockContainerDescriptionLink);
        host.log("creating container : " + Utils.toJson(containerState));
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);
        host.log("container = " + Utils.toJson(containerState));

        // Post the first statistics - expect to have a running container after that
        containerStats.healthCheckSuccess = true;

        ContainerStats updatedStats = patchStats(containerStats);
        assertEquals(true, updatedStats.healthCheckSuccess);

        final String containerLink = containerState.documentSelfLink;

        waitFor(
                String.format("Waiting '%s' with status = %s",
                        containerLink, ContainerState.CONTAINER_RUNNING_STATUS),
                () -> {
                    ContainerState containerRunning = getDocument(ContainerState.class,
                            containerLink);
                    host.log("container running = " + Utils.toJson(containerRunning));

                    return containerRunning.status.equals(ContainerState.CONTAINER_RUNNING_STATUS);
                });

        // Degrade the container
        ContainerStats patchHealth = new ContainerStats();
        patchHealth.healthCheckSuccess = false;
        patchStats(patchHealth);

        waitFor(
                String.format("Waiting '%s' with status = %s",
                        containerLink, ContainerState.CONTAINER_DEGRADED_STATUS),
                () -> {
                    ContainerState containerWithError = getDocument(ContainerState.class,
                            containerLink);
                    return containerWithError.status
                            .equals(ContainerState.CONTAINER_DEGRADED_STATUS);
                });

        // Degrade the container another time - should become error
        patchStats(patchHealth);

        waitFor(
                String.format("Waiting '%s' with status = %s and powerState = %s",
                        containerLink, ContainerState.CONTAINER_ERROR_STATUS, PowerState.ERROR),
                () -> {
                    ContainerState containerWithError =
                            getDocument(ContainerState.class, containerLink);
                    return containerWithError.powerState == PowerState.ERROR;
                });

        // Patch with success - expect to go back to degraded
        patchHealth.healthCheckSuccess = true;
        patchStats(patchHealth);

        waitFor(
                String.format("Waiting '%s' with status = %s",
                        containerLink, ContainerState.CONTAINER_DEGRADED_STATUS),
                () -> {
                    ContainerState containerWithError =
                            getDocument(ContainerState.class, containerLink);
                    return containerWithError.status
                            .equals(ContainerState.CONTAINER_DEGRADED_STATUS);
                });

        // Patch with success - expect to go back to running status
        patchStats(patchHealth);

        waitFor(
                String.format("Waiting '%s' with status = %s and powerState = %s", containerLink,
                        ContainerState.CONTAINER_DEGRADED_STATUS, PowerState.RUNNING),
                () -> {
                    ContainerState healthyContainer =
                            getDocument(ContainerState.class, containerLink);
                    if (!healthyContainer.status.equals(ContainerState.CONTAINER_RUNNING_STATUS)) {
                        return false;
                    }

                    assertEquals(PowerState.RUNNING, healthyContainer.powerState);

                    return true;
                });

        delete(containerState.documentSelfLink);
    }

    @Test
    public void testContainerStatsNoParam() {
        TestRequestSender sender = host.getTestRequestSender();
        FailureResponse failureResponse = sender.sendAndWaitFailure(Operation
                .createGet(host, ContainerStatsService.SELF_LINK));
        assertEquals(Operation.STATUS_CODE_FAILURE_THRESHOLD, failureResponse.op.getStatusCode());
        assertTrue(failureResponse.failure instanceof IllegalArgumentException);
    }

    @Test
    public void testContainerStatsWrongParam() {
        TestRequestSender sender = host.getTestRequestSender();
        String query = ContainerStatsService.CONTAINER_ID_QUERY_PARAM + "=no-container";
        URI uri = UriUtils.buildUri(host, ContainerStatsService.SELF_LINK, query);
        FailureResponse failureResponse = sender.sendAndWaitFailure(Operation.createGet(uri));
        assertEquals(Operation.STATUS_CODE_NOT_FOUND, failureResponse.op.getStatusCode());
    }

    @Test
    public void testContainerStats() throws Throwable {
        MockStatsAdapterService mockStatsAdapterService = new MockStatsAdapterService();
        try {
            stopService(mockStatsAdapterService);

            URI adapterServiceUri = UriUtils.buildUri(host, ManagementUriParts.ADAPTER_DOCKER);
            host.startService(Operation.createPost(adapterServiceUri), mockStatsAdapterService);
            waitForServiceAvailability(ManagementUriParts.ADAPTER_DOCKER);

            String mockContainerDescriptionLink = UriUtils.buildUriPath(
                    ContainerDescriptionService.FACTORY_LINK, "mockDescId");

            ContainerDescription containerDesc = createContainerDescription();
            containerDesc.documentSelfLink = mockContainerDescriptionLink;
            containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

            containerState = createContainerState(mockContainerDescriptionLink);
            host.log("creating container : " + Utils.toJson(containerState));
            containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);
            host.log("container = " + Utils.toJson(containerState));

            TestRequestSender sender = host.getTestRequestSender();
            String query = String.format("%s=%s", ContainerStatsService.CONTAINER_ID_QUERY_PARAM,
                    UriUtils.getLastPathSegment(containerState.documentSelfLink));
            URI uri = UriUtils.buildUri(host, ContainerStatsService.SELF_LINK, query);
            Operation response = sender.sendAndWait(Operation.createGet(uri));
            assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
            ServiceStats stats = response.getBody(ServiceStats.class);
            assertNotNull(stats);
            assertEquals(ServiceStats.KIND, stats.documentKind);
        } finally {
            stopService(mockStatsAdapterService);
        }
    }

    private ContainerState createContainerState(String containerDescriptionLink) {
        ContainerState container = new ContainerState();
        container.descriptionLink = containerDescriptionLink;
        container.adapterManagementReference = UriUtils.buildUri(ManagementUriParts.ADAPTER_DOCKER);
        container.status = ContainerState.CONTAINER_RUNNING_STATUS;
        return container;
    }

    private String buildContainerStatsJson() {
        // CPU calculation:
        // var cpuDelta = stats.cpu_stats.cpu_usage.total_usage -
        // stats.precpu_stats.cpu_usage.total_usage;
        // var systemDelta = stats.cpu_stats.system_cpu_usage - stats.precpu_stats.system_cpu_usage;
        // cpuUsage=((cpuDelta/systemDelta) * stats.cpu_stats.cpu_usage.percpu_usage.length)*100.0;

        JsonObject stats = new JsonObject();
        JsonObject cpu_stats = new JsonObject();
        stats.add("cpu_stats", cpu_stats);
        cpu_stats.addProperty("system_cpu_usage", 684753090000000L);

        JsonObject cpu_usage = new JsonObject();
        cpu_stats.add("cpu_usage", cpu_usage);
        cpu_usage.addProperty("total_usage", 257301730000000L);

        JsonArray percpu_usage = new JsonArray();
        percpu_usage.add(new JsonPrimitive(208092140000000L));
        percpu_usage.add(new JsonPrimitive(49209590000000L));
        cpu_usage.add("percpu_usage", percpu_usage);

        JsonObject precpu_stats = new JsonObject();
        stats.add("precpu_stats", precpu_stats);
        precpu_stats.addProperty("system_cpu_usage", 484235090000000L);

        JsonObject precpu_usage = new JsonObject();
        precpu_stats.add("cpu_usage", precpu_usage);
        precpu_usage.addProperty("total_usage", 227305640000000L);

        // Memory calculation:
        // var memStats = data.memory_stats;
        // var memoryPercentage = (memStats.usage / memStats.limit) * 100;
        JsonObject memory_stats = new JsonObject();
        memory_stats.addProperty("usage", 3042080);
        memory_stats.addProperty("limit", 5000000);
        stats.add("memory_stats", memory_stats);

        // Network:
        // data.networks.eth0.rx_bytes, data.networks.eth0.tx_bytes, ...
        JsonObject networks = new JsonObject();
        JsonObject iface = new JsonObject();
        iface.addProperty("rx_bytes", 34887);
        iface.addProperty("tx_bytes", 579367);
        networks.add("eth0", iface);
        stats.add("networks", networks);

        return stats.toString();
    }

    private ContainerStats patchStats(ContainerStats patchState) throws Throwable {
        URI uri = UriUtils.buildUri(host, containerState.documentSelfLink);
        host.testStart(1);
        host.sendRequest(Operation.createPatch(uri)
                .setBody(patchState)
                .setReferer(host.getUri())
                .setCompletion(host.getCompletion()));
        host.testWait();

        ServiceStats serviceStats = getDocument(ServiceStats.class, containerState.documentSelfLink
                + ServiceHost.SERVICE_URI_SUFFIX_STATS);
        assertNotNull(serviceStats);
        return ContainerStats.transform(serviceStats);
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
        healthConfig.timeoutMillis = 2000;

        return healthConfig;
    }

    public static class MockStatsAdapterService extends StatelessService {
        public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER;

        private final Set<URI> resourcesInvokedStats = new ConcurrentSkipListSet<>();

        public boolean isInspectInvokedForResource(URI resource) {
            return resourcesInvokedStats.contains(resource);
        }

        @Override
        public void handlePatch(Operation op) {
            AdapterRequest state = op.getBody(AdapterRequest.class);
            if (ContainerOperationType.STATS.id.equals(state.operationTypeId)) {
                logInfo(
                        ">>>> Invoking MockStatsAdapterService handlePatch for Stats for: %s ",
                        state.resourceReference);
                resourcesInvokedStats.add(state.resourceReference);
            }
            op.complete();
        }
    }

}
