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
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public abstract class BaseMockAdapterService extends StatelessService {

    public static final String FAILURE_EXPECTED = "FAILURE_EXPECTED";

    protected void patchTaskStage(AdapterRequest state, Throwable exception) {

        patchTaskStage(state,
                exception == null ? null : Utils.toServiceErrorResponse(exception),
                null);
    }

    protected void patchTaskStage(AdapterRequest state, ServiceErrorResponse errorResponse) {
        patchTaskStage(state, errorResponse, null);
    }

    protected void patchTaskStage(AdapterRequest state, ServiceErrorResponse errorResponse,
            ServiceTaskCallbackResponse callbackResponse) {

        if (state.serviceTaskCallback.isEmpty()) {
            return;
        }

        if (errorResponse != null) {
            callbackResponse = state.serviceTaskCallback.getFailedResponse(errorResponse);
        } else if (callbackResponse == null) {
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
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setBody(callbackResponse)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(
                                        "Notifying parent task %s from mock docker host adapter failed: %s",
                                        o.getUri(), Utils.toString(e));
                            }
                        }));
    }

    @SuppressWarnings("unchecked")
    protected <T> void getDocument(Class<T> type, URI reference, TaskState taskInfo,
            Consumer<T> callbackFunction) {
        final Object[] result = new Object[] { null };
        sendRequest(Operation.createGet(reference)
                .setCompletion((o, e) -> {
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
                            String errMsg = "Can't find resource: [%s]";
                            logSevere(errMsg, reference);
                            taskInfo.stage = TaskStage.FAILED;
                            taskInfo.failure = Utils.toServiceErrorResponse(
                                    new IllegalStateException(String.format(errMsg, reference)));
                        }
                    }
                    callbackFunction.accept((T) result[0]);
                }));
    }

}
