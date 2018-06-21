/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.tasks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;

/**
 * Utility task that waits for multiple tasks to complete When those tasks have completed the
 * operation they issue a PATCH to this service with the taskInfo.stage set to FINISHED, or if the
 * operation fails, set to FAILED. The subtask service then issues one PATCH to the service
 * referenced via parentTaskLink
 */
public class SubTaskService<E extends Enum<E>> extends TaskService<SubTaskService.SubTaskState<E>> {

    public static final String FACTORY_LINK = UriPaths.TASKS + "/sub-tasks";

    /**
     * Represent the state of subtask service.
     */
    public static class SubTaskState<E extends Enum<E>> extends TaskService.TaskServiceState {
        /**
         * Number of tasks to track.
         */
        public long completionsRemaining = 1;

        /**
         * Number of tasks failed.
         */
        public long failCount;

        /**
         * Number of tasks finished successfully.
         */
        public long finishedCount;

        /**
         * Normalized error threshold between 0 and 1.0.
         */
        public double errorThreshold;

        public ServiceTaskCallback<E> serviceTaskCallback;

        public Set<ResourceOperationResponse> failures = new HashSet<>();

        public Set<ResourceOperationResponse> completed = new HashSet<>();

        /**
         * Tenant links.
         */
        public List<String> tenantLinks;
    }

    public SubTaskService() {
        super(SubTaskState.class);

        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handlePatch(Operation patch) {
        SubTaskState<E> patchBody = getBody(patch);
        SubTaskState<E> currentState = getState(patch);

        if (patchBody.taskInfo == null || patchBody.taskInfo.stage == null) {
            String error = "taskInfo, taskInfo.stage are required";
            logWarning(error);
            patch.fail(new IllegalArgumentException(error));
            return;
        }

        if (currentState.completionsRemaining == 0) {
            // don't do anything, we are done
            patch.complete();
            return;
        }

        if (patchBody.taskInfo.stage == TaskStage.FAILED
                || patchBody.taskInfo.stage == TaskStage.CANCELLED) {
            currentState.failCount++;
            currentState.completionsRemaining--;

            if (ResourceOperationResponse.KIND.equals(patchBody.documentKind)) {
                ResourceOperationResponse r = patch.getBody(ResourceOperationResponse.class);
                currentState.failures.add(r);
            }
        } else if (patchBody.taskInfo.stage == TaskStage.FINISHED) {
            currentState.completionsRemaining--;
            currentState.finishedCount++;
            if (ResourceOperationResponse.KIND.equals(patchBody.documentKind)) {
                ResourceOperationResponse r = patch.getBody(ResourceOperationResponse.class);
                currentState.completed.add(r);
            }
        } else if (patchBody.taskInfo.stage == TaskStage.STARTED) {
            // don't decrement completions remaining.
        } else {
            logFine(() -> String.format("ignoring patch from %s", patch.getReferer()));
            // ignore status updates from boot/power services
            patch.complete();
            return;
        }

        // any operation on state before a operation is completed, is guaranteed
        // to be atomic
        // (service is synchronized)
        logFine(() -> String.format("Remaining %d", currentState.completionsRemaining));
        boolean isFinished = currentState.completionsRemaining == 0;
        patch.complete();

        if (!isFinished) {
            return;
        }

        ServiceTaskCallbackResponse<E> parentPatchBody = currentState.serviceTaskCallback
                .getFinishedResponse();
        if (currentState.failCount > 0) {
            double failedRatio = (double) currentState.failCount
                    / (double) (currentState.finishedCount
                            + currentState.failCount + currentState.completionsRemaining);

            if (currentState.errorThreshold == 0
                    || failedRatio > currentState.errorThreshold) {
                logWarning(() -> String.format("Notifying parent of task failure: %s (%s)",
                        Utils.toJsonHtml(patchBody.failureMessage),
                        patchBody.taskInfo.stage));

                parentPatchBody = currentState.serviceTaskCallback
                        .getFailedResponse(patchBody.taskInfo.failure);
            }
        }

        parentPatchBody.completed = currentState.completed;
        parentPatchBody.failures = currentState.failures;

        currentState.serviceTaskCallback.sendResponse(this, parentPatchBody);

        // we are a one shot task, self DELETE
        sendRequest(Operation.createDelete(this, getSelfLink()));
    }
}
