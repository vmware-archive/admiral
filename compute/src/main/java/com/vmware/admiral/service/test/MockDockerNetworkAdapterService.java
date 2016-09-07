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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Mock Docker Adapter service to be used in unit and integration tests.
 */
public class MockDockerNetworkAdapterService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_NETWORK;

    public static final String MOCK_CURRENT_EXECUTED_OPERATION_KEY = "MOCK_CURRENT_EXECUTED_OPERATION_KEY";
    public static final String FAILURE_EXPECTED = "FAILURE_EXPECTED";
    public static final String MOCK_HOST_ASSIGNED_ADDRESS = "192.168.1.129";

    public boolean isFailureExpected;
    public String computeHostIpAddress = MOCK_HOST_ASSIGNED_ADDRESS;

    // Map of network ids by hostId. hostId -> Map of networkId -> networkReference
    private static final Map<String, ContainerNetworkState> NETWORKS = new ConcurrentHashMap<>();

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
            patchProvisioningTask(state, taskInfo.failure);
            return;
        }

        // static way to define expected failure
        if (this.isFailureExpected) {
            logInfo("Expected failure request for resource:  %s", state.resourceReference);
            patchProvisioningTask(state, new IllegalStateException("Simulated failure"));
            return;
        }

        // define expected failure dynamically for every request
        if (state.customProperties != null
                && state.customProperties.containsKey(FAILURE_EXPECTED)) {
            logInfo("Expected failure request from custom props for resource:  %s",
                    state.resourceReference);
            patchProvisioningTask(state, new IllegalStateException("Simulated failure"));
            return;
        }

        processRequest(state, taskInfo, null, null);
    }

    private void processRequest(MockAdapterRequest state, TaskState taskInfo,
            ContainerNetworkState network, ContainerNetworkDescription networkDesc) {
        if (TaskStage.FAILED == taskInfo.stage) {
            logInfo("Failed request based on network resource:  %s",
                    state.resourceReference);
            patchProvisioningTask(state, taskInfo.failure);
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
            patchProvisioningTask(state, new IllegalStateException("Simulated failure"));
            return;
        }

        if (networkDesc == null && !state.isDeprovisioning()) {
            getDocument(ContainerNetworkDescription.class,
                    UriUtils.buildUri(getHost(), network.descriptionLink), taskInfo,
                    (desc) -> processRequest(state, taskInfo, network, desc));
            return;
        }

        if (state.isProvisioning()) {
            addNetwork(state.resourceReference, network);
            patchProvisioningTask(state, (Throwable) null);
        } else if (state.isDeprovisioning()) {
            removeNetworkByReference(state.resourceReference);
            patchProvisioningTask(state, (Throwable) null);
        }
    }

    private void patchProvisioningTask(MockAdapterRequest state, Throwable exception) {
        patchProvisioningTask(state,
                exception == null ? null : Utils.toServiceErrorResponse(exception));
    }

    private void patchProvisioningTask(MockAdapterRequest state, ServiceErrorResponse errorResponse) {
        if (state.serviceTaskCallback.isEmpty()) {
            return;
        }
        ServiceTaskCallbackResponse callbackResponse = null;
        if (errorResponse != null) {
            callbackResponse = state.serviceTaskCallback.getFailedResponse(errorResponse);
        } else {
            callbackResponse = state.serviceTaskCallback.getFinishedResponse();
        }

        URI callbackReference = URI.create(state.serviceTaskCallback.serviceSelfLink);
        if (callbackReference.getScheme() == null) {
            callbackReference = UriUtils.buildUri(getHost(),
                    state.serviceTaskCallback.serviceSelfLink);
        }

        // tell the parent we are done. We are a mock service, so we get things done, fast.
        sendRequest(Operation
                .createPatch(callbackReference)
                .setBody(callbackResponse)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(
                                "Notifying parent task %s from mock docker adapter failed: %s",
                                o.getUri(), Utils.toString(e));
                    }
                }));
    }

    @SuppressWarnings("unchecked")
    private <T> void getDocument(Class<T> type, URI reference, TaskState taskInfo,
            Consumer<T> callbackFunction) {
        final Object[] result = new Object[] { null };
        sendRequest(Operation.createGet(reference)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logSevere(e);
                                taskInfo.stage = TaskStage.FAILED;
                                taskInfo.failure = Utils.toServiceErrorResponse(e);
                            } else {
                                result[0] = o.getBody(type);
                                if (result[0] != null) {
                                    logInfo("Get Document: [%s]", reference);
                                    taskInfo.stage = TaskStage.FINISHED;
                                } else {
                                    String errMsg = String.format("Can't find resource: [%s]",
                                            reference);
                                    logSevere(errMsg);
                                    taskInfo.stage = TaskStage.FAILED;
                                    taskInfo.failure = Utils
                                            .toServiceErrorResponse(new IllegalStateException(
                                                    errMsg));
                                }
                            }
                            callbackFunction.accept((T) result[0]);
                        }));

    }

    public static synchronized void resetNetworks() {
        NETWORKS.clear();
    }

    public static synchronized void removeNetworkById(String id) {
        Iterator<Map.Entry<String, ContainerNetworkState>> it = NETWORKS.entrySet().iterator();
        while (it.hasNext()) {
            ContainerNetworkState network = it.next().getValue();
            if (id.equals(network.id)) {
                it.remove();
            }
        }
    }

    private synchronized void removeNetworkByReference(URI networkReference) {
        NETWORKS.remove(networkReference.toString());
    }

    public static synchronized void addNetwork(URI networkReference, ContainerNetworkState network) {
        NETWORKS.put(networkReference.toString(), network);
    }
}