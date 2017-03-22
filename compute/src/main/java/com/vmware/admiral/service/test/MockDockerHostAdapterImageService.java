/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ImageOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Mock Docker Image Adapter service to be used in unit and integration tests.
 */
public class MockDockerHostAdapterImageService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_IMAGE_HOST;

    public static final String FAILURE_EXPECTED = "FAILURE_EXPECTED";

    private static class MockAdapterRequest extends AdapterRequest {

        public boolean isBuilding() {
            return ImageOperationType.BUILD.id.equals(operationTypeId);
        }

        public boolean isLoading() {
            return ImageOperationType.LOAD.id.equals(operationTypeId);
        }

        public TaskState validateMock() {
            TaskState taskInfo = new TaskState();
            try {
                validate();
            } catch (Exception e) {
                taskInfo.stage = TaskState.TaskStage.FAILED;
                taskInfo.failure = Utils.toServiceErrorResponse(e);
            }

            return taskInfo;
        }
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.GET) {
            op.setStatusCode(204);
            op.complete();
            return;
        }

        if (op.getAction() != Action.PATCH) {
            op.fail(new IllegalArgumentException("Action not supported"));
            return;
        }

        op.setStatusCode(Operation.STATUS_CODE_ACCEPTED).complete();

        MockDockerHostAdapterImageService.MockAdapterRequest state = op
                .getBody(MockDockerHostAdapterImageService.MockAdapterRequest.class);

        TaskState taskInfo = state.validateMock();

        logInfo("Request accepted for resource: %s", state.resourceReference);
        if (TaskState.TaskStage.FAILED == taskInfo.stage) {
            logInfo("Failed request for resource:  %s", state.resourceReference);
            patchLoadingTask(state, taskInfo.failure);
            return;
        }

        // define expected failure dynamically for every request
        if (state.customProperties != null
                && state.customProperties.containsKey(FAILURE_EXPECTED)) {
            logInfo("Expected failure request from custom props for resource:  %s",
                    state.resourceReference);
            patchLoadingTask(state, new IllegalStateException("Simulated failure"));
            return;
        }

        processRequest(state, taskInfo);
    }

    private void processRequest(MockDockerHostAdapterImageService.MockAdapterRequest state,
            TaskState taskInfo) {
        if (TaskState.TaskStage.FAILED == taskInfo.stage) {
            logInfo("Failed request based on network resource:  %s",
                    state.resourceReference);
            patchLoadingTask(state, taskInfo.failure);
            return;
        }

        if (state.isBuilding() || state.isLoading()) {
            patchLoadingTask(state, (Throwable) null);
        }
    }

    private void patchLoadingTask(MockDockerHostAdapterImageService.MockAdapterRequest state,
            Throwable exception) {
        patchLoadingTask(state,
                exception == null ? null : Utils.toServiceErrorResponse(exception));
    }

    private void patchLoadingTask(MockAdapterRequest state, ServiceErrorResponse errorResponse) {
        if (state.serviceTaskCallback.isEmpty()) {
            return;
        }
        ServiceTaskCallback.ServiceTaskCallbackResponse callbackResponse = null;
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
                                "Notifying parent task %s from mock docker image adapter failed: "
                                        + "%s",
                                o.getUri(), Utils.toString(e));
                    }
                }));
    }
}
