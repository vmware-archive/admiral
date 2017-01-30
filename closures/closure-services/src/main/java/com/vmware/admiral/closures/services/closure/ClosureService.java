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

package com.vmware.admiral.closures.services.closure;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.closures.drivers.DriverRegistry;
import com.vmware.admiral.closures.drivers.ExecutionDriver;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.util.ClosureProps;
import com.vmware.admiral.closures.util.ClosureUtils;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.service.common.AbstractTaskStatefulService.TaskStatusState;
import com.vmware.admiral.service.common.LogService.LogServiceState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Represents closure service
 *
 */
public class ClosureService<T extends TaskServiceDocument<E>, E extends Enum<E>>
        extends StatefulService {

    private static final int RETRIES_COUNT = Integer
            .getInteger("com.vmware.admiral.service.tasks.retries", 3);
    private static final int MAX_LOG_SIZE_BYTES = Integer
            .getInteger("com.vmware.admiral.closures.max.log.size.bytes",
                    200 * 1024);
    private static final long DEFAULT_CLOSURE_EXPIRATION_DAYS = 10;

    private final transient DriverRegistry driverRegistry;

    public ClosureService(DriverRegistry driverRegistry, long maintenanceTimeout) {
        super(Closure.class);

        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);

        super.setMaintenanceIntervalMicros(maintenanceTimeout);

        this.driverRegistry = driverRegistry;
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logWarning("Skipping maintenance since service is not available: %s ", getUri());
            return;
        }

        sendRequest(Operation
                .createGet(getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to fetch closure state. Reason:" + ex.getMessage());
                        post.fail(new Exception("Unable to fetch closure state."));
                    } else {
                        Closure closure = op.getBody(Closure.class);

                        sendRequest(Operation
                                .createGet(this, closure.descriptionLink)
                                .setCompletion((o, e) -> {
                                    if (e != null) {
                                        logWarning("Failed to fetch closure definition. Reason: ",
                                                e);

                                        sendRequest(Operation
                                                .createDelete(getUri())
                                                .setCompletion((dop, dex) -> {
                                                    if (ex != null) {
                                                        logWarning("Self delete failed: {}",
                                                                Utils.toString(ex));
                                                    }
                                                }));

                                        post.fail(
                                                new Exception(
                                                        "Unable to fetch closure definition: " + e
                                                                .getMessage()));
                                    } else {
                                        ClosureDescription taskDef = o
                                                .getBody(ClosureDescription.class);

                                        processMaintenance(post, closure, taskDef);
                                    }
                                }));

                    }
                }));
    }

    @Override
    public void handleStart(Operation startOp) {
        if (!hasBody(startOp)) {
            return;
        }

        Closure closure = startOp.getBody(Closure.class);
        logInfo("Closure state: %s, closure definition: %s", closure.state,
                closure.descriptionLink);
        if (isNotValid(startOp, closure)) {
            return;
        }

        if (closure.state == TaskStage.CREATED || closure.state == TaskStage.CANCELLED) {
            initializeTask(startOp, closure);
        } else {
            this.setState(startOp, closure);
            startOp.setBody(closure).complete();
        }
    }

    @Override
    public void handlePatch(Operation patchOp) {
        Closure requestedState = patchOp.getBody(Closure.class);
        Closure currentState = this.getState(patchOp);

        ServiceTaskCallbackResponse callbackResponse = patchOp
                .getBody(ServiceTaskCallbackResponse.class);
        TaskState taskInfo = callbackResponse.taskInfo;
        if (TaskState.isFailed(taskInfo) || TaskState.isCancelled(taskInfo)) {
            logWarning("Infrastructure failure detected: state: %s reason: %s", taskInfo.stage,
                    taskInfo.failure.message);

            currentState.state = taskInfo.stage;
            currentState.errorMsg = taskInfo.failure.message;
            this.setState(patchOp, currentState);
            patchOp.setBody(currentState).complete();
            return;
        }

        try {
            verifyPatchRequest(currentState, requestedState);
            Closure currentClosure = null;
            if (isDone(currentState)) {
                currentClosure = this.getState(patchOp);
                currentClosure.logs = requestedState.logs;

                this.setState(patchOp, currentClosure);
                patchOp.setBody(currentClosure).complete();
            } else {
                currentClosure = updateState(patchOp, requestedState);
                logInfo("Closure state: %s, closure definition: %s", currentClosure.state,
                        currentClosure.descriptionLink);

                this.setState(patchOp, currentClosure);
                patchOp.setBody(currentClosure).complete();

                handleStateChanged(currentClosure);
            }

            updateRequestTracker(fromClosure(currentClosure));

        } catch (Exception ex) {
            logSevere("Error while patching closure: ", ex);
            patchOp.fail(ex);
        }
    }

    private ClosureTaskState fromClosure(Closure currentClosure) {
        ClosureTaskState closureTaskState = new ClosureTaskState();
        closureTaskState.taskInfo = new TaskState();
        closureTaskState.taskInfo.stage = currentClosure.state;
        closureTaskState.tenantLinks = currentClosure.tenantLinks;

        if (currentClosure.state == TaskStage.CREATED) {
            closureTaskState.taskSubStage = ClosureTaskState.SubStage.CREATED;
        } else if (currentClosure.state == TaskStage.STARTED) {
            closureTaskState.taskSubStage = ClosureTaskState.SubStage.CLOSURE_EXECUTING;
        } else if (currentClosure.state == TaskStage.FINISHED) {
            closureTaskState.taskSubStage = ClosureTaskState.SubStage.COMPLETED;
        } else if (currentClosure.state == TaskStage.FAILED
                || currentClosure.state == TaskStage.CANCELLED) {
            closureTaskState.taskSubStage = ClosureTaskState.SubStage.ERROR;
        }

        return closureTaskState;
    }

    public static class ClosureTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ClosureTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            CLOSURE_EXECUTING,
            COMPLETED,
            ERROR;
        }

        @Documentation(description = "The description that defines the closure description.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        /** Set by a Task with the links of the provisioned resources. */
        @Documentation(description = "Set by a Task with the links of the provisioned resources.")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL, OPTIONAL }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

    }

    protected void updateRequestTracker(ClosureTaskState state) {
        updateRequestTracker(state, RETRIES_COUNT);
    }

    protected void updateRequestTracker(ClosureTaskState state, int retryCount) {
        if (state != null && state.requestTrackerLink != null) {
            sendRequest(Operation
                    .createPatch(this, state.requestTrackerLink)
                    .setBody(fromTask(state))
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            // log but don't fail the task
                            if (ex instanceof CancellationException) {
                                logFine("CancellationException: Failed to update request tracker: %s",
                                        state.requestTrackerLink);
                                // retry only the finished and failed updates. The others are not so
                                // important
                            } else if (TaskStage.FINISHED.name()
                                    .equals(state.taskInfo.stage.name())
                                    || TaskStage.FAILED.name().equals(state.taskInfo.stage.name())
                                    && retryCount > 0) {
                                getHost().schedule(
                                        () -> updateRequestTracker(state, retryCount - 1),
                                        QueryUtil.QUERY_RETRY_INTERVAL_MILLIS,
                                        TimeUnit.MILLISECONDS);
                            } else {
                                logWarning("Failed to update request tracker: %s. Error: %s",
                                        state.requestTrackerLink, Utils.toString(ex));
                            }
                        }
                    }));

        } else if (state != null && state.documentSelfLink != null) {
            logFine("Task doesn't have a requestTrackerLink set: %s ", state.documentSelfLink);
        }
    }

    protected TaskStatusState fromTask(ClosureTaskState state) {
        return fromTask(new TaskStatusState(), state);
    }

    protected <S extends TaskStatusState> S fromTask(S taskStatus, ClosureTaskState state) {
        taskStatus.documentSelfLink = getSelfId();
        taskStatus.phase = "Closure Execution";
        taskStatus.taskInfo = state.taskInfo;
        taskStatus.subStage = state.taskSubStage.name();
        taskStatus.tenantLinks = state.tenantLinks;
        if (state.customProperties != null) {
            taskStatus.eventLogLink = state.customProperties.get(
                    TaskStatusState.FIELD_NAME_EVENT_LOG_LINK);
        }

        // task progress is the current stage divided by number of normal stages (not including the
        // error one)
        taskStatus.progress = 100 * state.taskSubStage.ordinal()
                / (state.taskSubStage.getClass().getEnumConstants().length - 2);

        if (taskStatus.progress > 100) {
            // reached error state
            taskStatus.progress = 100;
        }

        return taskStatus;
    }

    private void handleStateChanged(Closure closure) {
        fetchLogs(closure, () -> {
        });
        if (isDone(closure)) {
            sendRequest(Operation
                    .createGet(this, closure.descriptionLink)
                    .setReferer(this.getUri())
                    .setCompletion((op, ex) -> {
                        if (ex != null) {
                            logWarning(
                                    "Failed to fetch definition of closure: "
                                            + closure.documentSelfLink + " Reason: ",
                                    ex);
                        } else {
                            ClosureDescription closureDesc = op.getBody(ClosureDescription.class);

                            getHost().schedule(() -> fetchLogs(closure, () -> {
                                if (!ClosureProps.IS_KEEP_ON_COMPLETION_ON
                                        && closure.state != TaskStage.CANCELLED) {
                                    // clean execution container
                                    logInfo("Cleaning execution container for closure: "
                                            + closure.documentSelfLink);
                                    getExecutionDriver(closureDesc).cleanClosure(closure,
                                            (error) -> logWarning(
                                                    "Unable to clean resources for %s",
                                                    closure.documentSelfLink));
                                }
                            }), 3, TimeUnit.SECONDS);

                            if (closureDesc.notifyUrl != null
                                    && closureDesc.notifyUrl.length() > 0) {
                                // Call webhook posting closure state
                                callWebhook(closureDesc.notifyUrl, closure);
                            }

                            if (closure.serviceTaskCallback != null) {
                                notifyCallerService(closure);
                            }
                        }
                    }));
        }
    }

    private boolean isDone(Closure closure) {
        return closure.state == TaskStage.FAILED
                || closure.state == TaskStage.FINISHED
                || closure.state == TaskStage.CANCELLED;
    }

    protected void notifyCallerService(Closure closureState) {
        if (closureState.serviceTaskCallback.isEmpty()) {
            return;
        }
        if (closureState.serviceTaskCallback.isExternal()) {
            sendRequestStateToExternalUrl(closureState.serviceTaskCallback.serviceSelfLink,
                    closureState);
        } else {
            ClosureCallbackCompleteResponse callbackResponse = new ClosureCallbackCompleteResponse();
            if (closureState.state == TaskStage.FINISHED) {
                callbackResponse.copy(closureState.serviceTaskCallback.getFinishedResponse());
            } else {
                callbackResponse
                        .copy(closureState.serviceTaskCallback.getFailedResponse(new Exception(
                                closureState.errorMsg)));
            }

            sendRequest(
                    Operation.createPatch(this, closureState.serviceTaskCallback.serviceSelfLink)
                            .setBody(callbackResponse)
                            .setCompletion((o, e) -> {
                                if (e != null) {
                                    logWarning("Notifying parent task %s failed: %s", o.getUri(),
                                            Utils.toString(e));
                                }
                            }));
        }
    }

    private static class ClosureCallbackCompleteResponse
            extends ServiceTaskCallback.ServiceTaskCallbackResponse {
        //        List<String> resourceLinks;
    }

    private void sendRequestStateToExternalUrl(String callbackReference, Closure state) {
        // send put with the RequestState as the body
        logInfo("Calling callback URI: %s", callbackReference);

        try {
            URI callbackUri = URI.create(callbackReference);
            sendRequest(Operation.createPost(callbackUri)
                    .setBody(state)
                    .setReferer(this.getUri())
                    .forceRemote()
                    .setCompletion((op, ex) -> {
                        if (ex != null) {
                            logSevere("Failure calling callback '%s' for registry state: %s",
                                    op.getUri(), Utils.toString(ex));
                        }
                    }));
        } catch (Exception e) {
            logSevere(e);
        }
    }

    private void callWebhook(String webHookUriStr, Closure closure) {
        logInfo("Calling execution container for closure: " + closure.documentSelfLink);
        URI webHookUri = UriUtils.buildUri(webHookUriStr);
        sendRequest(Operation
                .createPost(webHookUri)
                .setReferer(this.getUri())
                .setExpiration(
                        TimeUnit.SECONDS.toMicros(ClosureProps.DEFAULT_WEB_HOOK_EXPIRATION_TIMEOUT)
                                + Utils
                                .getNowMicrosUtc())
                .setBody(closure)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning(
                                "Unable to send closure state to: " + webHookUriStr + " Reason: ",
                                ex);
                    } else {
                        logInfo("Successfully sent closure state to: %s", webHookUri);
                    }
                }));
    }

    @Override
    public void handlePut(Operation put) {
        Closure reqClosure = put.getBody(Closure.class);
        Closure closure = this.getState(put);

        sendRequest(Operation
                .createGet(this, closure.documentSelfLink)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to execute closure! Reason:" + ex.getMessage());
                        put.fail(new Exception("Unable to fetch closure state."));
                    } else {
                        Closure currentState = op.getBody(Closure.class);

                        if (reqClosure.inputs != null) {
                            currentState.inputs.putAll(reqClosure.inputs);
                        }
                        this.setState(put, currentState);
                        put.setBody(currentState).complete();
                    }
                }));
    }

    @Override
    public void handlePost(Operation post) {
        Closure reqClosure = post.getBody(Closure.class);
        Closure closure = this.getState(post);

        closure.inputs = reqClosure.inputs;
        closure.serviceTaskCallback = reqClosure.serviceTaskCallback;
        closure.closureSemaphore = UUID.randomUUID().toString();
        if (isNotValid(post, closure)) {
            return;
        }

        logInfo("Closure state: %s, closure definition: %s", closure.state,
                closure.descriptionLink);
        sendRequest(Operation
                .createGet(this, closure.descriptionLink)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to execute closure! Reason:" + ex.getMessage());
                        post.fail(new Exception("Unable to fetch script source."));
                    } else {
                        ClosureDescription taskDef = op.getBody(ClosureDescription.class);

                        if (ClosureUtils.isEmpty(taskDef.sourceURL)) {
                            logInfo("Executing script: %s", taskDef.source);
                        } else {
                            logInfo("Executing script from URL: %s", taskDef.sourceURL);
                        }

                        processExecution(post, closure, taskDef);
                    }
                }));
    }

    // PRIVATE METHODS

    private void fetchLogs(Closure closure, Runnable operation) {
        if (closure.resourceLinks == null || closure.resourceLinks.size() <= 0) {
            return;
        }
        String resourceLink = closure.resourceLinks.iterator().next();
        String containerId = UriUtils.getLastPathSegment(resourceLink);
        String logsURI = ManagementUriParts.CONTAINER_LOGS + "?id=" + containerId;
        sendRequest(Operation
                .createGet(this, logsURI)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to fetch logs for closure! %s Reason: %s",
                                closure.documentSelfLink,
                                ex.getMessage());
                    } else {
                        logInfo("Logs fetched successfully for closure: %s",
                                closure.documentSelfLink);

                        LogServiceState logState = op.getBody(LogServiceState.class);
                        byte[] fetchedLogs = shrinkToMaxAllowedSize(logState.logs);
                        if (shouldUpdateLogs(closure.logs, fetchedLogs)) {
                            closure.logs = fetchedLogs;
                            sendSelfPatch(closure);
                        }

                        operation.run();
                    }
                }));
    }

    private static boolean shouldUpdateLogs(byte[] oldLogs, byte[] newLogs) {
        if (oldLogs == newLogs) {
            return false;
        }
        if (newLogs == null) {
            return false;
        }
        if (oldLogs == null) {
            return true;
        }
        if (newLogs.length != oldLogs.length) {
            return true;
        }
        return false;
    }

    private byte[] shrinkToMaxAllowedSize(byte[] targetArray) {
        if (targetArray == null || targetArray.length <= MAX_LOG_SIZE_BYTES) {
            return targetArray;
        }

        byte[] limitedArray = new byte[MAX_LOG_SIZE_BYTES];
        System.arraycopy(targetArray, targetArray.length - MAX_LOG_SIZE_BYTES, limitedArray, 0,
                MAX_LOG_SIZE_BYTES);
        return limitedArray;
    }

    private void initializeTask(Operation post, Closure closure) {
        sendRequest(Operation
                .createGet(this, closure.descriptionLink)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to fetch closure definition closure! Reason:" + ex
                                .getMessage());
                        post.fail(new Exception("Unable to fetch script source."));
                    } else {
                        initTask(closure, op);

                        this.setState(post, closure);
                        post.setBody(closure).complete();
                    }
                }));
    }

    private void processMaintenance(Operation op, Closure closure, ClosureDescription taskDef) {
        if (closure == null) {
            logWarning("Skip maintenance call...");
            return;
        }

        if (isTaskExpired(closure, taskDef)) {
            completeCancelTask(taskDef, closure);
        }

        op.complete();
    }

    private void completeCancelTask(ClosureDescription taskDef, Closure closure) {
        logInfo("Closure execution elapsed configured timeout. The closure will be cancelled...");

        closure.state = TaskStage.CANCELLED;

        sendSelfPatch(closure);

        getExecutionDriver(taskDef)
                .cleanClosure(closure,
                        (error) -> logWarning("Unable to clean resources for %s",
                                closure.documentSelfLink));
    }

    private boolean isTaskExpired(Closure closure, ClosureDescription taskDef) {
        if (closure.state != TaskStage.STARTED) {
            return false;
        }
        if (taskDef.resources == null) {
            logWarning("No constraints constraints bound to closure.");
        }

        if (closure.lastLeasedTimeMillis == null) {
            return false;
        }

        long timeElapsed = System.currentTimeMillis() - closure.lastLeasedTimeMillis;
        if (timeElapsed > (taskDef.resources.timeoutSeconds * 1000)) {
            logInfo("Timeout elapsed=%s, timeout=%s of closure=%s", timeElapsed,
                    taskDef.resources.timeoutSeconds *
                            1000, closure.documentSelfLink);
            return true;
        }

        return false;
    }

    private void initTask(Closure closure, Operation op) {
        ClosureDescription closureDesc = op.getBody(ClosureDescription.class);
        closure.inputs = new HashMap<>();
        closure.name = closureDesc.name;
        if (closureDesc.inputs != null) {
            closureDesc.inputs.forEach((k, v) -> {
                closure.inputs.put(k, v);
            });
        }
        closure.outputs = new HashMap<>();
        if (closureDesc.outputNames != null) {
            closureDesc.outputNames.forEach((k) -> {
                closure.outputs.put(k, null);
            });
        }

        closure.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(TimeUnit.DAYS
                .toMicros(DEFAULT_CLOSURE_EXPIRATION_DAYS));
    }

    private void verifyPatchRequest(Closure currentState, Closure requestedState) {
        switch (currentState.state) {
        case CREATED:
        case CANCELLED:
            verifyReadyPatch(currentState, requestedState);
            break;
        case STARTED:
            verifyLeasedPatch(currentState, requestedState);
            break;
        case FINISHED:
        case FAILED:
            break;
        //            throw new IllegalArgumentException(
        //                    "Invalid state change requested: " + requestedState.state + " was: " + currentState.state);
        default:
            throw new IllegalArgumentException(
                    "Unexpected state change requested: " + requestedState.state + " was: "
                            + currentState.state);
        }
    }

    private Closure updateState(Operation patch, Closure requestedState) {
        Closure currentState = this.getState(patch);

        if (requestedState.resourceLinks != null) {
            currentState.resourceLinks = requestedState.resourceLinks;
        }
        if (requestedState.state != null) {
            currentState.state = requestedState.state;
        }
        if (requestedState.inputs != null) {
            currentState.inputs = requestedState.inputs;
        }
        if (requestedState.outputs != null) {
            currentState.outputs = requestedState.outputs;
        }
        if (requestedState.errorMsg != null) {
            currentState.errorMsg = requestedState.errorMsg;
        }
        if (requestedState.closureSemaphore != null) {
            currentState.closureSemaphore = requestedState.closureSemaphore;
        }

        if (requestedState.state == TaskStage.STARTED) {
            currentState.lastLeasedTimeMillis = System.currentTimeMillis();
        }

        if (requestedState.serviceTaskCallback != null) {
            currentState.serviceTaskCallback = requestedState.serviceTaskCallback;
        }

        if (requestedState.logs != null) {
            currentState.logs = requestedState.logs;
        }

        return currentState;
    }

    private void verifyLeasedPatch(Closure currentState, Closure requestedState) {

        if (requestedState.state == null && requestedState.resourceLinks != null) {
            return;
        }

        if (requestedState.state == TaskStage.CREATED) {
            throw new IllegalArgumentException(
                    "Invalid state change requested: " + requestedState.state + " was: "
                            + currentState.state);
        } else if (currentState.closureSemaphore != null && !currentState.closureSemaphore
                .equals(requestedState.closureSemaphore)
                && currentState.state != TaskStage.CREATED) {
            throw new IllegalArgumentException("Unexpected version state on patch request: "
                    + requestedState.closureSemaphore + " expected: "
                    + currentState.closureSemaphore);
        }
    }

    private void verifyReadyPatch(Closure currentState, Closure requestedState) {

        if (requestedState.state == null && requestedState.resourceLinks != null) {
            return;
        }

        if (requestedState.state != TaskStage.STARTED) {
            if (requestedState.state == currentState.state) {
                return;
            }
            if (currentState.state == TaskStage.CREATED
                    && requestedState.state == TaskStage.FAILED) {
                // failed on start
                return;
            }
            throw new IllegalArgumentException(
                    "Invalid state change requested: " + requestedState.state + " was: "
                            + currentState.state);
        }
    }

    private void processExecution(Operation op, Closure closure, ClosureDescription taskDef) {
        switch (closure.state) {
        case CREATED:
        case CANCELLED:
            handleReadyState(op, closure, taskDef);
            break;
        case STARTED:
            handledLeasedState(op, closure);
            break;
        case FINISHED:
        case FAILED:
            handleDoneState(op, closure);
            break;
        default:
            logWarning("Unsupported closure lease state: %s", closure.state);
        }

    }

    private void handleReadyState(Operation op, Closure closure, ClosureDescription closureDesc) {
        ExecutionDriver execDriver = getExecutionDriver(closureDesc);
        if (execDriver == null) {
            logWarning("No exec driver found");
            op.fail(new Exception("No execution driver available."));
            return;
        }
        sendSelfPatch(closure);

        String token = getToken(op);
        execDriver.executeClosure(closure, closureDesc, token, (error) -> {
            closure.state = TaskStage.FAILED;
            closure.errorMsg = error.getMessage();
            sendSelfPatch(closure);
        });

        this.setState(op, closure);
        op.setBody(closure).complete();

    }

    private String getToken(Operation op) {
        Operation.AuthorizationContext authCtx = op.getAuthorizationContext();
        return authCtx != null ? authCtx.getToken() : "";
    }

    private void handledLeasedState(Operation op, Closure closure) {
        logInfo("Closure is already being executed by : %s", closure.documentSelfLink);
        op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
        op.fail(new IllegalArgumentException(
                String.format("Closure has already been executed: %s", closure.documentSelfLink)));
    }

    private void handleDoneState(Operation op, Closure closure) {
        logInfo("Closure has already been executed: %s", closure.documentSelfLink);
        op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
        op.fail(new IllegalArgumentException(
                String.format("Closure has already been executed: %s", closure.documentSelfLink)));
    }

    private ExecutionDriver getExecutionDriver(ClosureDescription taskDef) {
        return driverRegistry.getDriver(taskDef.runtime);
    }

    private void sendSelfPatch(Closure body) {
        sendRequest(Operation
                .createPatch(getUri())
                .setBody(body)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Self patch failed: %s", Utils.toString(ex));
                    }
                }));
    }

    private boolean isNotValid(Operation op, Closure body) {
        if (body.descriptionLink == null || body.descriptionLink.isEmpty()) {
            op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
            op.fail(new IllegalArgumentException(
                    String.format("Closure description link is required: %s",
                            body.documentSelfLink)));
            return true;
        }

        return false;
    }

    private boolean hasBody(Operation op) {
        if (!op.hasBody()) {
            op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
            op.fail(new IllegalArgumentException("Empty body is provided."));
            return false;
        }
        return true;
    }

}
