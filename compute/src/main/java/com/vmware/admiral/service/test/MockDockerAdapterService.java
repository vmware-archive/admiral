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

package com.vmware.admiral.service.test;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.admiral.compute.container.maintenance.ContainerStats;
import com.vmware.admiral.compute.container.maintenance.ContainerStatsEvaluator;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.test.MockDockerContainerToHostService.MockDockerContainerToHostState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceHostLogService;

/**
 * Mock Docker Adapter service to be used in unit and integration tests.
 */
public class MockDockerAdapterService extends BaseMockAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER;

    public static final String MOCK_CURRENT_EXECUTED_OPERATION_KEY =
            "MOCK_CURRENT_EXECUTED_OPERATION_KEY";
    public static final String MOCK_HOST_ASSIGNED_ADDRESS = "127.0.0.1";

    public boolean isFailureExpected;
    public String computeHostIpAddress = MOCK_HOST_ASSIGNED_ADDRESS;

    private static class MockAdapterRequest extends AdapterRequest {

        public boolean isProvisioning() {
            return ContainerOperationType.CREATE.id.equals(operationTypeId);
        }

        public boolean isDeprovisioning() {
            return ContainerOperationType.DELETE.id.equals(operationTypeId);
        }

        public TaskState validateMock() {
            TaskState taskInfo = new TaskState();
            try {
                validate();
            } catch (Exception e) {
                taskInfo.stage = TaskStage.FAILED;
                taskInfo.failure = Utils.toServiceErrorResponse(e);
            }

            return taskInfo;
        }
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.DELETE) {
            if (ServiceHost.isServiceStop(op)) {
                handleDeleteCompletion(op);
                return;
            }
            if (op.hasBody()) {
                MockAdapterRequest state = op.getBody(MockAdapterRequest.class);
                removeContainerByReference(state);
                op.complete();
                return;
            } else {
                op.complete();
                return;
            }
        }

        if (op.getAction() == Action.GET) {
            op.setStatusCode(204);
            op.complete();
            return;
        }

        if (op.getAction() != Action.PATCH) {
            op.fail(new IllegalArgumentException("action not supported"));
            return;
        }

        if (op.getReferer().getPath().equals(ShellContainerExecutorService.SELF_LINK)) {
            op.setBodyNoCloning(emptyExecResult());
        }

        op.setStatusCode(Operation.STATUS_CODE_ACCEPTED).complete();

        MockAdapterRequest state = op.getBody(MockAdapterRequest.class);

        TaskState taskInfo = state.validateMock();

        logInfo("Request accepted for resource: %s", state.resourceReference);
        if (TaskStage.FAILED == taskInfo.stage) {
            logInfo("Failed request for resource:  %s", state.resourceReference);
            patchTaskStage(state, taskInfo.failure);
            return;
        }

        // static way to define expected failure
        if (this.isFailureExpected) {
            logInfo("Expected failure request for resource:  %s", state.resourceReference);
            patchTaskStage(state, new IllegalStateException("Simulated failure"));
            return;
        }

        // define expected failure dynamically for every request
        if (state.customProperties != null
                && state.customProperties.containsKey(FAILURE_EXPECTED)) {
            logInfo("Expected failure request from custom props for resource:  %s",
                    state.resourceReference);
            patchTaskStage(state, new IllegalStateException("Simulated failure"));
            return;
        }

        processContainerRequest(state, taskInfo, null, null);
    }

    private void processContainerRequest(MockAdapterRequest state, TaskState taskInfo,
            ContainerState containerState, ContainerDescription contDesc) {
        if (containerState == null) {
            getDocument(ContainerState.class, state.resourceReference, taskInfo,
                    (container) -> processContainerRequest(state, taskInfo, container, contDesc));
            return;
        }

        // define expected failure dynamically for every request
        if (containerState.customProperties != null
                && containerState.customProperties.remove(FAILURE_EXPECTED) != null) {
            updateContainerState(containerState, () -> patchTaskStage(state,
                    new IllegalStateException("Simulated failure")));
            return;
        }

        if (contDesc == null && !state.isDeprovisioning()) {
            getDocument(ContainerDescription.class,
                    UriUtils.buildUri(getHost(), containerState.descriptionLink), taskInfo,
                    (desc) -> processContainerRequest(state, taskInfo, containerState, desc));
            return;
        }

        if (state.isProvisioning()) {
            createContainerToHost(state, containerState, contDesc);
        } else if (state.isDeprovisioning()) {
            removeContainerByReference(state);
        } else if (ContainerOperationType.INSPECT.id.equals(state.operationTypeId)) {
            patchContainerInspect(state, containerState);
        } else if (ContainerOperationType.STATS.id.equals(state.operationTypeId)) {
            patchContainerStats(state, containerState);
        } else if (ContainerOperationType.FETCH_LOGS.id.equals(state.operationTypeId)) {
            createLogState(state, containerState);
        } else {
            patchContainerPowerState(state, containerState);
        }
    }

    private void createContainerToHost(MockAdapterRequest state,
            ContainerState containerState, ContainerDescription containerDesc) {
        String containerId = UUID.randomUUID().toString();
        containerState.id = containerId;
        MockDockerContainerToHostState containerToHostState = new MockDockerContainerToHostState();
        containerToHostState.parentLink = containerState.parentLink;
        containerToHostState.id = containerId;
        if (containerState.names != null && !containerState.names.isEmpty()) {
            containerToHostState.name = containerState.names.get(0);
        }
        containerToHostState.image = containerState.image;
        containerToHostState.powerState = PowerState.RUNNING;

        sendRequest(Operation.createPost(this, MockDockerContainerToHostService.FACTORY_LINK)
                .setBody(containerToHostState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        patchTaskStage(state, e);
                    } else {
                        logInfo("Mock Container in docker host created successfully");
                        patchContainerStateWithIPAddress(state, containerState, containerDesc);
                    }
                }));
    }

    private void patchContainerStateWithIPAddress(MockAdapterRequest state,
            ContainerState containerState, ContainerDescription containerDesc) {
        containerState.address = this.computeHostIpAddress;
        containerState.powerState = PowerState.RUNNING;
        containerState.created = new Date().getTime();
        containerState.command = containerDesc.command;
        containerState.image = containerDesc.image;
        containerState.status = "Started";
        containerState.documentExpirationTimeMicros = -1;
        if (containerDesc.portBindings != null && containerDesc.portBindings.length != 0) {
            List<PortBinding> portBindings = new ArrayList<>();
            for (PortBinding portBinding : containerDesc.portBindings) {
                if (portBinding.hostPort == null) {
                    // if the port binding doesn't specify a host port, bind a random port
                    int randomPort = new Random().nextInt(65536);
                    portBinding.hostPort = String.valueOf(randomPort);
                }

                portBindings.add(portBinding);
            }
            containerState.ports = portBindings;
        } else {
            PortBinding portBinding = new PortBinding();
            // if the port binding doesn't specify a host port, bind a random port
            int randomPort = new Random().nextInt(65536);
            portBinding.hostPort = String.valueOf(randomPort);
            portBinding.containerPort = String.valueOf(randomPort);
            containerState.ports = new ArrayList<>();
            containerState.ports.add(portBinding);
        }

        sendRequest(Operation.createPatch(state.resourceReference)
                .setBody(containerState)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    Throwable patchException = null;
                    if (e != null) {
                        logSevere(e);
                        patchException = e;
                    }
                    patchTaskStage(state, patchException);
                }));
    }

    private void patchContainerPowerState(MockAdapterRequest state, ContainerState containerState) {

        if (ContainerOperationType.START.id.equals(state.operationTypeId)) {
            containerState.powerState = PowerState.RUNNING;
        } else if (ContainerOperationType.STOP.id.equals(state.operationTypeId)) {
            containerState.powerState = PowerState.STOPPED;
        } else {
            if (containerState.customProperties == null) {
                containerState.customProperties = new HashMap<>();
            }
            containerState.customProperties.put(MOCK_CURRENT_EXECUTED_OPERATION_KEY,
                    state.operationTypeId);
        }

        sendRequest(Operation.createPatch(state.resourceReference)
                .setBody(containerState)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        patchTaskStage(state, e);
                    } else {
                        patchContainerToHostPowerState(state, containerState);
                    }
                }));
    }

    private void patchContainerToHostPowerState(MockAdapterRequest state, ContainerState containerState) {
        String containerName = Service.getId(state.resourceReference.getPath());
        QueryTask q = QueryUtil.buildPropertyQuery(MockDockerContainerToHostState.class,
                MockDockerContainerToHostState.FIELD_NAME_NAME, containerName);
        MockDockerContainerToHostState cthState = new MockDockerContainerToHostState();
        cthState.powerState = containerState.powerState;
        new ServiceDocumentQuery<>(getHost(), MockDockerContainerToHostState.class).query(q,
                (r) -> {
                    if (r.hasException()) {
                        patchTaskStage(state, r.getException());
                    } else if (r.hasResult()) {
                        sendRequest(Operation.createPatch(this, r.getDocumentSelfLink())
                                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                                .setBody(cthState)
                                .setCompletion((o, e) -> {
                                    Throwable patchException = null;
                                    if (e != null) {
                                        logSevere(e);
                                        patchException = e;
                                    }
                                    patchTaskStage(state, patchException);
                                }));
                    }
                });
    }

    private void patchContainerInspect(MockAdapterRequest state, ContainerState container) {
        ContainerState newContainerState = new ContainerState();
        newContainerState.attributes = new HashMap<>();
        newContainerState.attributes.put("Config", "{\"Cmd\": [\"/bin/sh\",\"-c\",\"exit 9\"],"
                + " \"Hostname\": \"ba033ac44011\", \"Image\": \"" + container.image + "\"}");
        newContainerState.attributes.put("HostConfig",
                "{\"CpuShares\": 12, \"CpusetCpus\": 33, \"Memory\": 245, \"MemorySwap\": 123}");
        newContainerState.attributes.put("NetworkSettings", "{\"IPAddress\": \"10.23.47.89\"}");
        newContainerState.address = this.computeHostIpAddress;
        newContainerState.image = container.image;
        logFine("Patching ContainerState: %s ", state.resourceReference);
        sendRequest(Operation.createPatch(state.resourceReference)
                .setBodyNoCloning(newContainerState)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, ex) -> {
                    Throwable patchException = null;
                    if (ex != null) {
                        logSevere(ex);
                        patchException = ex;
                    }
                    patchTaskStage(state, patchException);
                }));
    }

    private void createLogState(MockAdapterRequest state, ContainerState containerState) {
        String logFile = ServiceHostLogService.getDefaultProcessLogName();
        logFine("logFile: %s ", logFile);

        LogService.LogServiceState logServiceState = new LogService.LogServiceState();
        logServiceState.documentSelfLink = Service.getId(state.resourceReference
                .toString());
        logServiceState.tenantLinks = containerState.tenantLinks;
        if (logFile == null) {
            logServiceState.logs = "log file not found".getBytes();

        } else {
            try {
                logServiceState.logs = Files.readAllBytes((new File(logFile)).toPath());
            } catch (Exception e) {
                patchTaskStage(state, e);
                return;
            }
        }

        sendRequest(Operation.createPost(this, LogService.FACTORY_LINK)
                .setBodyNoCloning(logServiceState)
                .setCompletion((o, ex) -> {
                    Throwable patchException = null;
                    if (ex != null) {
                        logSevere(ex);
                        patchException = ex;
                    }
                    patchTaskStage(state, patchException);
                }));
    }

    private void patchContainerStats(MockAdapterRequest state, ContainerState containerState) {
        // CPU calculation:
        // var cpuDelta = stats.cpu_stats.cpu_usage.total_usage -
        // stats.precpu_stats.cpu_usage.total_usage;
        // var systemDelta = stats.cpu_stats.system_cpu_usage - stats.precpu_stats.system_cpu_usage;
        // cpuUsage=((cpuDelta/systemDelta) * stats.cpu_stats.cpu_usage.percpu_usage.length)*100.0;

        int max = 9;
        int min = 1;
        Random rand = new Random();
        int random = rand.nextInt((max - min) + 1) + min;
        int randomNext = rand.nextInt((max - min) + 1) + min;
        JsonObject stats = new JsonObject();
        JsonObject cpu_stats = new JsonObject();
        stats.add("cpu_stats", cpu_stats);
        cpu_stats.addProperty("system_cpu_usage", 684753090000000L * randomNext);

        JsonObject cpu_usage = new JsonObject();
        cpu_stats.add("cpu_usage", cpu_usage);
        cpu_usage.addProperty("total_usage", 257301730000000L * random);

        JsonArray percpu_usage = new JsonArray();
        percpu_usage.add(new JsonPrimitive(208092140000000L * random));
        percpu_usage.add(new JsonPrimitive(49209590000000L * random));
        cpu_usage.add("percpu_usage", percpu_usage);

        JsonObject precpu_stats = new JsonObject();
        stats.add("precpu_stats", precpu_stats);
        precpu_stats.addProperty("system_cpu_usage", 484235090000000L * randomNext);

        JsonObject precpu_usage = new JsonObject();
        precpu_stats.add("cpu_usage", precpu_usage);
        precpu_usage.addProperty("total_usage", 227305640000000L * random);

        // Memory calculation:
        // var memStats = data.memory_stats;
        // var memoryPercentage = (memStats.usage / memStats.limit) * 100;
        JsonObject memory_stats = new JsonObject();
        memory_stats.addProperty("usage", 3042080 * Math.min(random, randomNext));
        memory_stats.addProperty("limit", 5000000 * Math.max(random, randomNext));
        stats.add("memory_stats", memory_stats);

        // Network:
        // data.networks.eth0.rx_bytes, data.networks.eth0.tx_bytes
        JsonObject networks = new JsonObject();
        JsonObject iface = new JsonObject();
        iface.addProperty("rx_bytes", 34887);
        iface.addProperty("tx_bytes", 579367);
        networks.add("eth0", iface);
        stats.add("networks", networks);

        ContainerStats containerStats = ContainerStatsEvaluator
                .calculateStatsValues(stats.toString());
        containerStats.healthCheckSuccess = true;

        URI uri = UriUtils.buildUri(getHost(), containerState.documentSelfLink);
        logFine("Updating container stats: %s ", uri);

        sendRequest(Operation.createPatch(uri)
                .setBodyNoCloning(containerStats)
                .setCompletion((o, ex) -> {
                    Throwable patchException = null;
                    if (ex != null) {
                        logSevere(ex);
                        patchException = ex;
                    }
                    patchTaskStage(state, patchException);
                }));
    }

    private void updateContainerState(ContainerState container, Runnable callbackFunc) {
        sendRequest(Operation.createPut(this, container.documentSelfLink)
                .setBody(container)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere(ex);
                    }
                    callbackFunc.run();
                }));
    }

    private Map<String, Object> emptyExecResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("__output", "");
        result.put("ExitCode", 0);
        return result;
    }

    private void removeContainerByReference(MockAdapterRequest state) {
        String containerName = Service.getId(state.resourceReference.getPath());
        QueryTask q = QueryUtil.buildPropertyQuery(MockDockerContainerToHostState.class,
                MockDockerContainerToHostState.FIELD_NAME_NAME, containerName);
        List<String> mockContainerToHostLinks = new ArrayList<>();
        new ServiceDocumentQuery<>(getHost(), MockDockerContainerToHostState.class).query(q,
                (r) -> {
                    if (r.hasException()) {
                        patchTaskStage(state, r.getException());
                    } else if (r.hasResult()) {
                        mockContainerToHostLinks.add(r.getDocumentSelfLink());
                    } else {
                        removeMockContainerFromHost(state, mockContainerToHostLinks);
                    }
                });
    }

    private void removeMockContainerFromHost(MockAdapterRequest state, List<String> mockContainerToHostLinks) {
        for (String mockContainerToHostLink : mockContainerToHostLinks) {
            sendRequest(Operation.createDelete(this, mockContainerToHostLink)
                    .setBody(new ServiceDocument())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            logWarning("No mock container %s found in host in mock adapter. Error %s",
                                    mockContainerToHostLink, e.getMessage());
                        } else {
                            logInfo("Mock container %s removed from mock adapter", mockContainerToHostLink);
                        }
                    }));
        }
        patchTaskStage(state, (Throwable) null);
    }

}