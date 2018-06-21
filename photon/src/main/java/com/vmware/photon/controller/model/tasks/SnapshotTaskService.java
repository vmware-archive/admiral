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

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;

/**
 * Snapshot Task Service.
 */
public class SnapshotTaskService extends TaskService<SnapshotTaskService.SnapshotTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/snapshot-tasks";

    /**
     * Represents the state of a snapshot task.
     */
    public static class SnapshotTaskState extends TaskService.TaskServiceState {

        public static final long DEFAULT_EXPIRATION_MICROS = TimeUnit.MINUTES
                .toMicros(10);

        /**
         * Link to the snapshot service.
         */
        public String snapshotLink;

        /**
         * Link to the Snapshot adapter.
         */
        public URI snapshotAdapterReference;

        /**
         * Value indicating whether the service should treat this as a mock request and complete the
         * work flow without involving the underlying compute host infrastructure.
         */
        public boolean isMockRequest;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;
    }

    public SnapshotTaskService() {
        super(SnapshotTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }

            SnapshotTaskState state = start.getBody(SnapshotTaskState.class);
            validateState(state);

            URI computeHost = buildSnapshotUri(state);
            sendRequest(Operation.createGet(computeHost)
                    .setCompletion((o, e) -> {
                        validateSnapshotAndStart(start, o, e, state);
                    }));
        } catch (Throwable e) {
            logSevere(e);
            start.fail(e);
        }
    }

    public void validateState(SnapshotTaskState state) {
        if (state.snapshotLink == null || state.snapshotLink.isEmpty()) {
            throw new IllegalArgumentException("snapshotLink is required");
        }

        if (state.snapshotAdapterReference == null) {
            throw new IllegalArgumentException(
                    "snapshotAdapterReference required");
        }

        if (state.taskInfo == null || state.taskInfo.stage == null) {
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskState.TaskStage.CREATED;
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + SnapshotTaskState.DEFAULT_EXPIRATION_MICROS;
        }
    }

    private void validateSnapshotAndStart(Operation startPost, Operation get,
            Throwable e, SnapshotTaskState state) {
        if (e != null) {
            logWarning(() -> String.format("Failure retrieving snapshot state (%s): %s",
                    get.getUri(), e.toString()));
            startPost.complete();
            failTask(e);
            return;
        }

        SnapshotService.SnapshotState snapshotState = get
                .getBody(SnapshotService.SnapshotState.class);
        if (snapshotState.name == null || snapshotState.name.isEmpty()) {
            failTask(new IllegalArgumentException("Invalid snapshot name"));
            return;
        }
        if (snapshotState.computeLink == null
                || snapshotState.computeLink.isEmpty()) {
            failTask(new IllegalArgumentException("Invalid computeReference"));
            return;
        }

        // we can complete start operation now, it will index and cache the
        // update state
        startPost.complete();

        SnapshotTaskState newState = new SnapshotTaskState();
        newState.taskInfo = new TaskState();
        newState.taskInfo.stage = TaskState.TaskStage.STARTED;
        newState.snapshotLink = state.snapshotLink;
        newState.snapshotAdapterReference = state.snapshotAdapterReference;
        newState.isMockRequest = state.isMockRequest;
        // now we are ready to start our self-running state machine
        sendSelfPatch(newState);
    }

    private void sendSelfPatch(TaskState.TaskStage nextStage, Throwable error) {
        SnapshotTaskState body = new SnapshotTaskState();
        body.taskInfo = new TaskState();
        if (error != null) {
            body.taskInfo.stage = TaskState.TaskStage.FAILED;
            body.taskInfo.failure = Utils.toServiceErrorResponse(error);
        } else {
            body.taskInfo.stage = nextStage;
        }
        sendSelfPatch(body);
    }

    @Override
    public void handlePatch(Operation patch) {
        SnapshotTaskState body = patch.getBody(SnapshotTaskState.class);
        SnapshotTaskState currentState = getState(patch);
        // this validates AND transitions the stage to the next state by using
        // the patchBody.
        if (validateStageTransition(patch, body, currentState)) {
            return;
        }
        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            createSnapshot(currentState, null);
            break;
        case FINISHED:
            logInfo(() -> "Task is complete");
            break;
        case FAILED:
        case CANCELLED:
            break;
        default:
            break;
        }
    }

    private void createSnapshot(SnapshotTaskState updatedState,
            String subTaskLink) {
        if (subTaskLink == null) {
            createSubTaskForSnapshotCallback(updatedState);
            return;
        }
        logFine(() -> String.format("Starting to create snapshot using sub task %s", subTaskLink));

        ResourceOperationRequest sr = new ResourceOperationRequest();
        sr.resourceReference = UriUtils.buildUri(getHost(),
                updatedState.snapshotLink);
        sr.taskReference = UriUtils.buildUri(getHost(), subTaskLink);
        sr.isMockRequest = updatedState.isMockRequest;
        sendRequest(Operation
                .createPatch(updatedState.snapshotAdapterReference)
                .setBody(sr)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                failTask(e);
                                return;
                            }
                        }));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void createSubTaskForSnapshotCallback(SnapshotTaskState currentState) {
        SubTaskService.SubTaskState subTaskInitState = new SubTaskService.SubTaskState();
        // tell the sub task with what to patch us, on completion
        subTaskInitState.serviceTaskCallback = ServiceTaskCallback
                .create(UriUtils.buildPublicUri(getHost(), getSelfLink()))
                .onSuccessFinishTask();
        Operation startPost = Operation
                .createPost(this, SubTaskService.FACTORY_LINK)
                .setBody(subTaskInitState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(() -> String.format("Failure creating sub task: %s",
                                        Utils.toString(e)));
                                this.sendSelfPatch(TaskState.TaskStage.FAILED,
                                        e);
                                return;
                            }
                            SubTaskService.SubTaskState body = o
                                    .getBody(SubTaskService.SubTaskState.class);
                            createSnapshot(currentState, body.documentSelfLink);
                        });
        sendRequest(startPost);
    }

    public boolean validateStageTransition(Operation patch,
            SnapshotTaskState patchBody, SnapshotTaskState currentState) {

        if (patchBody.taskInfo != null && patchBody.taskInfo.failure != null) {
            logWarning(() -> String.format("Task failed: %s",
                    Utils.toJson(patchBody.taskInfo.failure)));
            currentState.taskInfo.failure = patchBody.taskInfo.failure;
        } else {
            if (patchBody.taskInfo == null || patchBody.taskInfo.stage == null) {
                patch.fail(new IllegalArgumentException(
                        "taskInfo and taskInfo.stage are required"));
                return true;
            }
        }
        logFine(() -> String.format("Current: %s. New: %s", currentState.taskInfo.stage,
                patchBody.taskInfo.stage));

        if (TaskState.isFailed(currentState.taskInfo)
                || TaskState.isCancelled(currentState.taskInfo)) {
            patch.fail(new IllegalStateException(
                    "task in failed state, can not transition to " + patchBody.taskInfo.stage));
            return true;
        }

        // update current stage to new stage
        currentState.taskInfo.stage = patchBody.taskInfo.stage;
        adjustStat(patchBody.taskInfo.stage.toString(), 1);

        return false;
    }

    private URI buildSnapshotUri(SnapshotTaskState updatedState) {
        URI snapshot = UriUtils.buildUri(getHost(), updatedState.snapshotLink);
        return snapshot;
    }

    private void failTask(Throwable e) {
        logWarning(() -> String.format("Self patching to FAILED, task failure: %s", e.toString()));
        sendSelfPatch(TaskState.TaskStage.FAILED, e);
    }
}
