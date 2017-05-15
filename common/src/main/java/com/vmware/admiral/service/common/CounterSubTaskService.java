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

package com.vmware.admiral.service.common;

import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Task tracking the progress of parallel progressing services/tasks. When all services complete the
 * operation issues a PATCH to original service with the taskInfo.stage set to FINISHED, or if the
 * operation fails, set to FAILED
 */
public class CounterSubTaskService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.COUNTER_SUB_TASKS;

    public static class CounterSubTaskState extends ServiceDocument {
        public TaskState taskInfo = new TaskState();
        public long completionsRemaining = 1;
        public long failCount;
        public long finishedCount;

        /** Normalized error threshold between 0 and 1.0. */
        public double errorThreshold;

        /** Callback link and response from the service initiated this task. */
        public ServiceTaskCallback serviceTaskCallback;

        /** (Optional) Custom properties */
        public volatile Map<String, String> customProperties;

        protected ServiceTaskCallbackResponse getFinishedResponse() {
            return serviceTaskCallback.getFinishedResponse();
        }

        protected ServiceTaskCallbackResponse getFailedResponse(ServiceErrorResponse e) {
            return serviceTaskCallback.getFailedResponse(e);
        }

        protected void merge(CounterSubTaskState patchBody) {
            this.customProperties = mergeCustomProperties(this.customProperties,
                    patchBody.customProperties);
        }
    }

    public CounterSubTaskService() {
        super(CounterSubTaskState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    public static void createSubTask(
            AbstractTaskStatefulService<?, ?> service, CounterSubTaskState subTaskInitState,
            Consumer<String> callbackFunc) {
        try {
            final String link = UriUtils.buildUriPath(CounterSubTaskService.FACTORY_LINK,
                    UUID.randomUUID().toString(), service.getSelfLink());
            subTaskInitState.documentSelfLink = link;
            subTaskInitState.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();

            Operation postOp = Operation.createPost(service, CounterSubTaskService.FACTORY_LINK)
                    .setBody(subTaskInitState)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            service.failTask("Failure creating counter sub task: " + link, e);
                            return;
                        }

                        CounterSubTaskState body = o.getBody(CounterSubTaskState.class);
                        service.logInfo("Creating %d tasks(s), reporting through sub task %s",
                                subTaskInitState.completionsRemaining, body.documentSelfLink);
                        callbackFunc.accept(body.documentSelfLink);
                    });
            service.sendRequest(postOp);
        } catch (Throwable e) {
            service.failTask("Failure posting counter sub task", e);
        }
    }

    @Override
    public void handleCreate(Operation post) {
        super.handleCreate(post);
        logInfo("CounterSubTask created.");
    }

    @Override
    public void handlePatch(Operation patch) {
        CounterSubTaskState patchBody = patch.getBody(CounterSubTaskState.class);
        CounterSubTaskState currentState = getState(patch);
        if (patchBody.taskInfo == null || patchBody.taskInfo.stage == null) {
            String error = "taskInfo, taskInfo.stage are required";
            logWarning(error);
            patch.fail(new IllegalArgumentException(error));
            return;
        }

        if (currentState.completionsRemaining <= 0) {
            logWarning("Already completed. Ignoring patch from %s", patch.getReferer());
            patch.complete();
            return;
        }

        switch (patchBody.taskInfo.stage) {
        case STARTED:
            // don't decrement completions remaining.
            break;
        case FINISHED:
            currentState.completionsRemaining--;
            currentState.finishedCount++;
            currentState.merge(patchBody);
            break;
        case FAILED:
        case CANCELLED:
            currentState.completionsRemaining--;
            currentState.failCount++;
            double failedRatio = (double) currentState.failCount / (double) (currentState
                    .finishedCount
                    + currentState.failCount + currentState.completionsRemaining);

            if (currentState.errorThreshold == 0 ||
                    failedRatio > currentState.errorThreshold) {
                logWarning("Notifying parent of task failure from stage %s. Error: %s",
                        patchBody.taskInfo.stage, patchBody.taskInfo.failure == null ? "n.a."
                                : patchBody.taskInfo.failure.message);
                currentState.completionsRemaining = 0;
            }
            break;
        default:
            logInfo("ignoring patch from %s", patch.getReferer());
            patch.complete();
            return;
        }

        // any operation on state before a operation is completed,
        // is guaranteed to be atomic (service is synchronized)
        boolean isFinished = currentState.completionsRemaining == 0;
        patch.complete();

        if (!isFinished) {
            logInfo("Remaining %d", currentState.completionsRemaining);
            return;
        }

        ServiceTaskCallbackResponse responseBody;
        if (TaskStage.FINISHED == patchBody.taskInfo.stage) {
            responseBody = currentState.getFinishedResponse();
        } else {
            responseBody = currentState.getFailedResponse(patchBody.taskInfo.failure);
        }

        responseBody.customProperties = mergeCustomProperties(
                responseBody.customProperties, currentState.customProperties);

        logInfo("Task completing with count [%d]", currentState.finishedCount);
        try {
            sendRequest(Operation.createPatch(this,
                    currentState.serviceTaskCallback.serviceSelfLink)
                    .setBody(responseBody)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            logSevere("Failure notifying parent task. Error: %s",
                                    Utils.toString(e));
                        } else {
                            logFine("Task completed with count [%d]", currentState.finishedCount);
                        }
                    }));
        } catch (Throwable e) {
            logSevere("Can't notify parent task. Error: %s", Utils.toString(e));
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }
}