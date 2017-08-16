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
import com.vmware.admiral.adapter.common.VolumeOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState.PowerState;
import com.vmware.admiral.service.test.MockDockerVolumeToHostService.MockDockerVolumeToHostState;
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
 * Mock Docker Adapter service for volumes in order to be used in unit and integration tests.
 * Keeps state of existing volumes in the host in {@link MockDockerVolumeToHostService}
 */
public class MockDockerVolumeAdapterService extends BaseMockAdapterService {

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_VOLUME;

    public static final String MOCK_CURRENT_EXECUTED_OPERATION_KEY = "MOCK_CURRENT_EXECUTED_OPERATION_KEY";
    public static final String FAILURE_EXPECTED = "FAILURE_EXPECTED";
    public static final String MOCK_HOST_ASSIGNED_ADDRESS = "192.168.1.129";

    public boolean isFailureExpected;
    public String computeHostIpAddress = MOCK_HOST_ASSIGNED_ADDRESS;

    private static class MockAdapterRequest extends AdapterRequest {

        public boolean isProvisioning() {
            return VolumeOperationType.CREATE.id.equals(operationTypeId);
        }

        public boolean isDeprovisioning() {
            return VolumeOperationType.DELETE.id.equals(operationTypeId);
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
                removeVolumeByReference(state);
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
            ContainerVolumeState volume, ContainerVolumeDescription volumeDesc) {
        if (volume == null) {
            getDocument(ContainerVolumeState.class, state.resourceReference, taskInfo,
                    (volumeState) -> processRequest(state, taskInfo, volumeState, volumeDesc));
            return;
        }

        // define expected failure dynamically for every request
        if (volume.customProperties != null
                && volume.customProperties.remove(FAILURE_EXPECTED) != null) {
            patchTaskStage(state, new IllegalStateException("Simulated failure"));
            return;
        }

        if (volumeDesc == null && !state.isDeprovisioning()) {
            getDocument(ContainerVolumeDescription.class,
                    UriUtils.buildUri(getHost(), volume.descriptionLink), taskInfo,
                    (desc) -> processRequest(state, taskInfo, volume, desc));
            return;
        }

        if (state.isProvisioning()) {
            createVolumeToHost(state, volume);
        } else if (state.isDeprovisioning()) {
            removeVolumeByReference(state);
        } else if (VolumeOperationType.INSPECT.id.equals(state.operationTypeId)) {
            patchTaskStage(state, (Throwable) null);
        }
    }

    private void createVolumeToHost(MockAdapterRequest state,
            ContainerVolumeState volumeState) {
        MockDockerVolumeToHostState volumeToHostState = new MockDockerVolumeToHostState();
        volumeToHostState.hostLink = volumeState.originatingHostLink;
        volumeToHostState.name = volumeState.name;
        volumeToHostState.driver = volumeState.driver;

        sendRequest(Operation
                .createPost(this, MockDockerVolumeToHostService.FACTORY_LINK)
                .setBody(volumeToHostState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        patchTaskStage(state, e);
                    } else {
                        logInfo("Mock Volume in docker host created successfully");
                        patchContainerVolumeState(state, volumeState);
                    }
                }));
    }

    private void patchContainerVolumeState(MockAdapterRequest state,
            ContainerVolumeState volumeState) {
        volumeState.powerState = PowerState.CONNECTED;
        volumeState.scope = "local";
        sendRequest(Operation.createPatch(state.resourceReference)
                .setBody(volumeState)
                .setCompletion((o, e) -> {
                    Throwable patchException = null;
                    if (e != null) {
                        patchException = e;
                    }
                    patchTaskStage(state, patchException);
                }));
    }

    private void removeVolumeByReference(MockAdapterRequest state) {
        String volumeName = Service.getId(state.resourceReference.getPath());
        QueryTask q = QueryUtil.buildPropertyQuery(MockDockerVolumeToHostState.class,
                MockDockerVolumeToHostState.FIELD_NAME_NAME, volumeName);
        List<String> mockVolumeToHostLinks = new ArrayList<>();
        new ServiceDocumentQuery<>(getHost(),
                MockDockerVolumeToHostState.class).query(q,
                        (r) -> {
                            if (r.hasException()) {
                                patchTaskStage(state, r.getException());
                            } else if (r.hasResult()) {
                                mockVolumeToHostLinks.add(r.getDocumentSelfLink());
                            } else {
                                removeMockVolumeFromHost(state, mockVolumeToHostLinks);
                            }
                        });
    }

    private void removeMockVolumeFromHost(MockAdapterRequest state, List<String> mockVolumeToHostLinks) {
        for (String mockVolumeToHostLink : mockVolumeToHostLinks) {
            sendRequest(Operation.createDelete(this, mockVolumeToHostLink)
                    .setBody(new ServiceDocument())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            logWarning("No mock volume %s found in host in mock adapter. Error %s",
                                    mockVolumeToHostLink, e.getMessage());
                        } else {
                            logInfo("MOck volume %s removed from mock adapter", mockVolumeToHostLink);
                        }
                    }));
        }
        patchTaskStage(state, (Throwable) null);
    }
}
