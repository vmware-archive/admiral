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

package com.vmware.admiral.service.test;

import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Mock Docker Adapter service to be used in unit and integration tests.
 */
public class MockDockerNetworkAdapterService extends BaseMockAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_NETWORK;

    public static final String MOCK_CURRENT_EXECUTED_OPERATION_KEY = "MOCK_CURRENT_EXECUTED_OPERATION_KEY";
    public static final String FAILURE_EXPECTED = "FAILURE_EXPECTED";
    public static final String MOCK_HOST_ASSIGNED_ADDRESS = "192.168.1.129";

    public boolean isFailureExpected;
    public String computeHostIpAddress = MOCK_HOST_ASSIGNED_ADDRESS;

    // Map of network ids by hostId. hostId -> Map of networkId -> networkReference
    private static final Map<String, Map<String, String>> NETWORK_IDS = new ConcurrentHashMap<>();
    // Map of network ids and names by hostId. hostId -> Map of networkId -> network name
    private static final Map<String, Map<String, String>> NETWORK_NAMES = new ConcurrentHashMap<>();

    static {
        // TODO (VBV-806) - These special initializations are required because some Container
        // Service tests rely on such external network but they create them directly by creating a
        // network state (rather than requesting it through the request broker), and in such way the
        // MockDockerNetworkAdapterService is not properly initialized.

        addNetworkId("test-docker-host-compute", "test-external-network",
                "test-external-network");
        addNetworkName("test-docker-host-compute", "test-external-network",
                "test-external-network");
        addNetworkId("test-docker-host-compute2", "test-external-network",
                "test-external-network");
        addNetworkName("test-docker-host-compute2", "test-external-network",
                "test-external-network");
        addNetworkId("test-docker-host-compute3", "test-external-network",
                "test-external-network");
        addNetworkName("test-docker-host-compute3", "test-external-network",
                "test-external-network");
    }

    private static class MockAdapterRequest extends AdapterRequest {

        public boolean isProvisioning() {
            return NetworkOperationType.CREATE.id.equals(operationTypeId);
        }

        public boolean isDeprovisioning() {
            return NetworkOperationType.DELETE.id.equals(operationTypeId);
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
                removeNetworkByReference(state.resourceReference);
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

        processRequest(state, taskInfo, null, null);
    }

    private void processRequest(MockAdapterRequest state, TaskState taskInfo,
            ContainerNetworkState network, ContainerNetworkDescription networkDesc) {
        if (TaskStage.FAILED == taskInfo.stage) {
            logInfo("Failed request based on network resource:  %s",
                    state.resourceReference);
            patchTaskStage(state, taskInfo.failure);
            return;
        }

        if (network == null) {
            getDocument(ContainerNetworkState.class, state.resourceReference, taskInfo,
                    (networkState) -> processRequest(state, taskInfo, networkState, networkDesc));
            return;
        }

        // define expected failure dynamically for every request
        if (network.customProperties != null
                && network.customProperties.remove(FAILURE_EXPECTED) != null) {
            patchTaskStage(state, new IllegalStateException("Simulated failure"));
            return;
        }

        if (networkDesc == null && !state.isDeprovisioning()) {
            getDocument(ContainerNetworkDescription.class,
                    UriUtils.buildUri(getHost(), network.descriptionLink), taskInfo,
                    (desc) -> processRequest(state, taskInfo, network, desc));
            return;
        }

        if (state.isProvisioning()) {
            addNetworkId(Service.getId(network.originatingHostLink), network.id,
                    state.resourceReference.toString());
            addNetworkName(Service.getId(network.originatingHostLink), network.id, network.name);
            patchTaskStage(state, (Throwable) null);
        } else if (state.isDeprovisioning()) {
            removeNetworkByReference(state.resourceReference);
            patchTaskStage(state, (Throwable) null);
        } else if (NetworkOperationType.INSPECT.id.equals(state.operationTypeId)) {
            patchTaskStage(state, (Throwable) null);
        }
    }

    public static synchronized void resetNetworks() {
        NETWORK_IDS.clear();
        NETWORK_NAMES.clear();
    }

    private synchronized void removeNetworkByReference(URI networkReference) {
        Iterator<Map.Entry<String, Map<String, String>>> itHost = NETWORK_IDS.entrySet().iterator();
        while (itHost.hasNext()) {
            Map.Entry<String, Map<String, String>> networkIdsByHost = itHost.next();
            Iterator<Entry<String, String>> itNetworks = networkIdsByHost.getValue().entrySet()
                    .iterator();
            while (itNetworks.hasNext()) {
                Entry<String, String> entry = itNetworks.next();
                if (entry.getValue().endsWith(networkReference.getPath())) {
                    Utils.log(MockDockerNetworkAdapterService.class,
                            MockDockerNetworkAdapterService.class.getSimpleName(), Level.INFO,
                            "Network with id: %s and container ref: %s removed.", entry.getKey(),
                            networkReference);
                    String hostId = networkIdsByHost.getKey();
                    if (NETWORK_NAMES.containsKey(hostId)) {
                        NETWORK_NAMES.get(hostId).remove(entry.getKey());
                    }
                    itNetworks.remove();
                    return;
                }
            }
        }
        Utils.logWarning("**************** No networkId found for reference: %s",
                networkReference.getPath());
    }

    public static synchronized void addNetworkId(String hostId, String networkId,
            String networkReference) {
        Utils.log(MockDockerNetworkAdapterService.class,
                MockDockerNetworkAdapterService.class.getSimpleName(),
                Level.INFO, "Network with id: %s and network ref: %s created in host: %s.",
                networkId, networkReference, hostId);
        if (!NETWORK_IDS.containsKey(hostId)) {
            NETWORK_IDS.put(hostId, new ConcurrentHashMap<>());
        }
        NETWORK_IDS.get(hostId).put(networkId, networkReference);
    }

    public static synchronized void addNetworkName(String hostId, String networkId, String name) {
        if (!NETWORK_NAMES.containsKey(hostId)) {
            NETWORK_NAMES.put(hostId, new ConcurrentHashMap<>());
        }
        NETWORK_NAMES.get(hostId).put(networkId, name);
    }

    public static synchronized Set<String> getNetworkIdsByHost(String hostId) {
        if (NETWORK_IDS.containsKey(hostId)) {
            return NETWORK_IDS.get(hostId).keySet();
        } else {
            return Collections.emptySet();
        }
    }

    public static synchronized String getNetworkNameById(String networkId) {
        Iterator<Map<String, String>> iteratorHost = NETWORK_NAMES.values().iterator();
        while (iteratorHost.hasNext()) {
            Map<String, String> networkIdsAndNamesByHost = iteratorHost.next();
            if (networkIdsAndNamesByHost.containsKey(networkId)) {
                return networkIdsAndNamesByHost.get(networkId);
            }
        }
        return null;
    }
}