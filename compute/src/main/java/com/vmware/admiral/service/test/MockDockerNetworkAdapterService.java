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

import java.util.ArrayList;
import java.util.List;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState.PowerState;
import com.vmware.admiral.service.test.MockDockerNetworkToHostService.MockDockerNetworkToHostState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

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
                removeNetworkByReference(state);
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
            createNetworkToHost(state, network);
        } else if (state.isDeprovisioning()) {
            removeNetworkByReference(state);
        } else if (NetworkOperationType.INSPECT.id.equals(state.operationTypeId)) {
            patchTaskStage(state, (Throwable) null);
        }
    }

    private void createNetworkToHost(MockAdapterRequest state,
            ContainerNetworkState networkState) {
        MockDockerNetworkToHostState networkToHostState = new MockDockerNetworkToHostState();
        networkToHostState.hostLink = networkState.originatingHostLink;
        networkToHostState.id = networkState.id;
        networkToHostState.name = networkState.name;

        sendRequest(Operation
                .createPost(this, MockDockerNetworkToHostService.FACTORY_LINK)
                .setBody(networkToHostState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        patchTaskStage(state, e);
                    } else {
                        logInfo("Mock Network in docker host created successfully");
                        patchContainerNetworkState(state, networkState);
                    }
                }));
    }

    private void patchContainerNetworkState(MockAdapterRequest state,
            ContainerNetworkState networkState) {
        networkState.powerState = PowerState.CONNECTED;
        sendRequest(Operation.createPatch(state.resourceReference)
                .setBody(networkState)
                .setCompletion((o, e) -> {
                    Throwable patchException = null;
                    if (e != null) {
                        logSevere(e);
                        patchException = e;
                    }
                    patchTaskStage(state, patchException);
                }));
    }

    private void removeNetworkByReference(MockAdapterRequest state) {
        String networkName = Service.getId(state.resourceReference.getPath());
        QueryTask q = QueryUtil.buildPropertyQuery(MockDockerNetworkToHostState.class,
                MockDockerNetworkToHostState.FIELD_NAME_NAME, networkName);
        List<String> mockNetworkToHostLinks = new ArrayList<>();
        new ServiceDocumentQuery<>(getHost(), MockDockerNetworkToHostState.class).query(q,
                (r) -> {
                    if (r.hasException()) {
                        patchTaskStage(state, r.getException());
                    } else if (r.hasResult()) {
                        mockNetworkToHostLinks.add(r.getDocumentSelfLink());
                    } else {
                        removeMockNetworkFromHost(state, mockNetworkToHostLinks);
                    }
                });
    }

    private void removeMockNetworkFromHost(MockAdapterRequest state,
            List<String> mockNetworkToHostLinks) {
        for (String mockNetworkToHostLink : mockNetworkToHostLinks) {
            sendRequest(Operation.createDelete(this, mockNetworkToHostLink)
                    .setBody(new ServiceDocument())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            logWarning("No mock network %s found in host in mock adapter. Error %s",
                                    mockNetworkToHostLink, e.getMessage());
                        } else {
                            logInfo("Mock network %s removed from mock adapter",
                                    mockNetworkToHostLink);
                        }
                    }));
        }
        patchTaskStage(state, (Throwable) null);
    }
}