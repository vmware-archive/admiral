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

package com.vmware.admiral.service.common;

import static com.vmware.admiral.common.DeploymentProfileConfig.getInstance;
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.host.IExtensibilityRegistryHost;
import com.vmware.admiral.service.common.CounterSubTaskService.CounterSubTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TagAssignmentService.KeyValue;
import com.vmware.admiral.service.common.TagAssignmentService.TagAssignmentRequest;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public abstract class AbstractTaskStatefulService<T extends TaskServiceDocument<E>, E extends Enum<E>>
        extends StatefulService {

    private static final int RETRIES_COUNT = Integer.getInteger(
            "com.vmware.admiral.service.tasks.retries", 3);
    protected static final long COMPLETION_POLLING_PERIOD_MILLIS = Long
            .getLong(
                    "com.vmware.admiral.service.common.AbstractTaskStatefulService.completion.polling.period.millis",
                    TimeUnit.SECONDS.toMillis(3));
    private static final Level DEFAULT_LOG_LEVEL = Level.parse(System.getProperty(
            "com.vmware.admiral.service.tasks.log.level", Level.INFO.getName()));
    public static final int MAX_STATE_SIZE = 1024 * 128;

    private ExtensibilitySubscriptionManager extensibilityManager;

    protected volatile Class<E> subStageType;

    private final String displayName;

    // whether the task should self-delete itself upon completion
    private volatile boolean selfDelete;

    private Level logLevel = DEFAULT_LOG_LEVEL;

    /**
     * SubStages that are indicating a transient state and order of patching can't be guaranteed
     */
    protected Set<E> transientSubStages = Collections.emptySet();

    /**
     * SubStages that are eligible for subscription
     */
    protected EnumSet<E> subscriptionSubStages;

    private volatile String locale;

    public static class TaskStatusState extends MultiTenantDocument {
        public static final String FIELD_NAME_EVENT_LOG_LINK = "eventLogLink";
        public static final String FIELD_NAME_TASK_INFO = "taskInfo";
        public static final String FIELD_NAME_PROGRESS = "progress";
        public static final String FIELD_NAME_SUB_STAGE = "subStage";
        public static final String FIELD_NAME_TASK_INFO_STAGE = "taskInfo.stage";

        /**
         * The name of the TaskService
         */
        public String phase;

        /**
         * TaskInfo state of the current TaskService
         */
        public TaskState taskInfo;

        /**
         * Substage of the current TaskService
         */
        public String subStage;

        /**
         * progress of the task (0-100%) - should only reported by leaf tasks, otherwise null
         */
        public Integer progress;

        /**
         * Name of a given task status
         */
        public String name;

        /**
         * Available when task is marked failed. Link to the corresponding event log.
         */
        public String eventLogLink;

        /**
         * Set of resource links provisioned or performed operation on them.
         */
        public Set<String> resourceLinks;
    }

    public AbstractTaskStatefulService(Class<? extends TaskServiceDocument<E>> stateType,
            Class<E> subStageType, String displayName) {
        super(stateType);
        this.subStageType = subStageType;
        this.displayName = displayName;
        this.subscriptionSubStages = EnumSet.noneOf(subStageType);
    }

    protected void setSelfDelete(boolean selfDelete) {
        this.selfDelete = selfDelete;
    }

    protected void setLogLevel(Level logLevel) {
        this.logLevel = logLevel;
    }

    @Override
    public void handleCreate(Operation post) {
        if (!checkForBody(post)) {
            return;
        }

        T state = getBody(post);

        boolean completed = false;
        try {
            post.nestCompletion((o) -> {
                post.setBody(state);
                post.complete();
            });
            completed = validateNewState(state, post);
        } catch (Exception e) {
            post.fail(e);
            return;
        }

        // in exceptional cases the validation could complete the operation.
        if (!completed) {
            post.complete();
        }

    }

    @Override
    public void handleStart(Operation startPost) {
        T state = getBody(startPost);
        if (state.taskInfo == null) {
            startPost.fail(new IllegalStateException("taskInfo must not be null"));
            return;
        }

        if (state.taskSubStage == null) {
            startPost.fail(new IllegalStateException("taskSubStage must not be null"));
            return;
        }

        if (state.taskInfo.stage.ordinal() >= TaskStage.FINISHED.ordinal()) {
            startPost.complete();
            return; // the task should not restart in this stage
        }

        if (state.taskInfo.stage == TaskStage.CREATED || state.documentVersion == 0) {
            state.taskInfo.stage = TaskStage.STARTED;
            if (!state.serviceTaskCallback.isEmpty()) {
                log(this.logLevel, "Starting task with parent link: %s",
                        state.serviceTaskCallback.serviceSelfLink);
            }
        } else {
            if (!state.serviceTaskCallback.isEmpty()) {
                log(this.logLevel, "Restarting task with parent link: %s",
                        state.serviceTaskCallback.serviceSelfLink);
            }
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();
        }

        if (startPost.getRequestHeader(Operation.ACCEPT_LANGUAGE_HEADER) != null) {
            locale = startPost.getRequestHeader(Operation.ACCEPT_LANGUAGE_HEADER);
        }

        startPost.setBody(state);
        startPost.complete();

        checkAndHandleSubscriptions(state, startPost);
    }

    @Override
    public void sendRequest(Operation op) {
        if (locale != null) {
            op.addRequestHeader(Operation.ACCEPT_LANGUAGE_HEADER, locale);
        }
        super.sendRequest(op);
    }

    private boolean validateNewState(T state, Operation startPost) {
        if (state.documentVersion > 0) {
            logWarning("Document version on create is : %s", state.documentVersion);
            return false;
        }
        if (state.serviceTaskCallback == null) {
            state.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        }

        if (state.taskInfo == null) {
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskStage.CREATED;
        }

        if (state.taskSubStage == null) {
            state.taskSubStage = Enum.valueOf(subStageType, DefaultSubStage.CREATED.name());
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();
        }

        return validateStateOnStart(state, startPost);
    }

    protected boolean validateStateOnStart(T state, Operation startPost)
            throws IllegalArgumentException {
        // validate based on annotations
        Utils.validateState(getStateDescription(), state);

        // apply optional custom validation
        validateStateOnStart(state);
        return false;
    }

    /**
     * Optional custom validation code, if needed. Validation based on state annotations has been
     * already performed.
     */
    protected void validateStateOnStart(T state) throws IllegalArgumentException {
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            return;
        }
        T patchBody = getBody(patch);
        T state = getState(patch);

        // validates AND transitions the stage to the next state by using the patchBody
        if (validateStageTransitionAndState(patch, patchBody, state)) {
            // the patch operation is assumed to be already completed/failed in this case
            return;
        }

        patch.complete();

        checkAndHandleSubscriptions(state, patch);
    }

    // Check if there are subscriptions and run them or resume the task
    private void checkAndHandleSubscriptions(T state, Operation op) {
        if (isExtensibilityResponse(op)) {

            String failure = getExtensibilityFailureMessage(state);
            if (failure != null) {
                this.failTask("Extensibility triggered task failure: " + failure, null);
            } else {

                try {
                    ServiceTaskCallbackResponse replyPayload = op
                            .getBody(this.replyPayload(state).getClass());

                    this.patchCommonFields(state, replyPayload).thenCompose(s ->
                            this.enhanceExtensibilityResponse(state, replyPayload))
                            .whenComplete((r, err) -> {
                                if (err != null) {
                                    failTask("Failure during enhancing extensibility response",
                                            err);
                                } else {
                                    handleStagePatch(state);
                                }
                            });
                } catch (Exception ex) {
                    logSevere(ex);
                    logSevere(
                            "Failed resuming task from extensibility response. Payload = %s, reply"
                                    + " class = %s", op.getBodyRaw(),
                            this.replyPayload(state).getClass());
                    this.failTask("Failed resuming task from extensibility response.", ex);
                }
            }
        } else {
            handleSubscriptions(state);
        }
    }

    private void handleSubscriptions(T state) {
        // Check if Task allows subscription on this stage & prevent sending of event more than once
        if (subscriptionSubStages.contains(state.taskSubStage) && (state.customProperties ==
                null || !state.customProperties.containsKey(constructExtensibilityResponseKey
                (state))) && !skipExtensibility(state)) {
            ExtensibilitySubscriptionManager manager = getExtensibilityManager();
            if (manager != null) {
                BaseExtensibilityCallbackResponse notificationPayload = this.notificationPayload
                        (state);

                //Once payload being enhanced, manager will sent notification to client.
                Runnable notificationCallback = () -> {

                    // Callback will trigger notification call to client.
                    Runnable callback = () -> {
                        manager.sendNotification(manager.getExtensibilitySubscription(state),
                                notificationPayload, this.replyPayload(state), state,
                                this::handleStagePatch);
                    };

                    this.validateAndEnhanceNotificationPayload(state, notificationPayload,
                            callback);
                };

                manager.handleStagePatch(notificationPayload, this.replyPayload(state), state,
                        this::handleStagePatch, notificationCallback);
            } else {
                // ServiceHost is not instance of ManagementHost
                handleStagePatch(state);
            }
        } else {
            // Task doesn't allow subscription on current stage.
            handleStagePatch(state);
        }
    }

    private boolean isExtensibilityResponse(Operation o) {
        return o.getReferer() != null && o.getReferer().toString()
                .contains(ExtensibilitySubscriptionCallbackService.FACTORY_LINK);
    }

    private String constructExtensibilityResponseKey(T state) {
        return String.format("%s:%s:%s", state.getClass().getSimpleName(),
                state.taskInfo.stage.name(), state.taskSubStage.name());
    }

    private String getExtensibilityFailureMessage(T patch) {
        return patch.customProperties != null ? patch.customProperties
                .get(ExtensibilitySubscriptionCallbackService
                        .EXTENSIBILITY_ERROR_MESSAGE) : null;
    }

    private void validateAndEnhanceNotificationPayload(T state,
            BaseExtensibilityCallbackResponse notificationPayload, Runnable callback) {
        if (notificationPayload == null) {
            this.failTask(String.format(
                    "Task [%s] doesn't provide notification payload for extensibility.",
                    this.getClass()), new Throwable());
            return;
        }

        getRelatedResourcesForExtensibility(state)
                .thenCompose(states -> getResourceStatesTags(states).thenApply(tags -> {
                    notificationPayload.tags = tags;
                    notificationPayload.customProperties = states.stream()
                            .flatMap(s -> s.customProperties.entrySet().stream())
                            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
                    return states;
                }).thenCompose(resources -> enhanceNotificationPayload(state, resources,
                        notificationPayload))
                .whenComplete((resources, err) -> {
                    if (err != null) {
                        failTask("Failed during notification payload enhancment",
                                err);
                    } else {
                        this.fillCommonFields(state, notificationPayload,
                                callback);
                    }
                }));
    }

    protected void updateRequestTracker(T state) {
        updateRequestTracker(state, RETRIES_COUNT);
    }

    protected void updateRequestTracker(T state, int retryCount) {
        if (state != null && state.requestTrackerLink != null) {
            sendRequest(Operation
                    .createPatch(this, state.requestTrackerLink)
                    .setBody(fromTask(state))
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            // log but don't fail the task
                            if (ex instanceof CancellationException) {
                                logFine("CancellationException: Failed to update request tracker:"
                                        + " %s", state.requestTrackerLink);
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

    protected void handleStagePatch(T state) {
        if (getInstance().shouldFail(state.taskSubStage)) {
            failTask("Fail task in stage [" + state.taskSubStage
                    + "], based on DeploymentProfileConfig", null);
            return;
        }
        updateRequestTracker(state);

        // calculate whether to self-delete now because below handlers can alter the state through
        // simultaneous PATCH requests
        boolean shouldSelfDelete = this.selfDelete &&
                state.taskInfo.stage.ordinal() > TaskStage.STARTED.ordinal();

        switch (state.taskInfo.stage) {
        case CREATED:
        case STARTED:
            handleStartedStagePatch(state);
            break;
        case FAILED:
            handleFailedStagePatch(state);
            break;
        case FINISHED:
            handleFinishedStagePatch(state);
            break;
        case CANCELLED:
            break;
        default:
            break;
        }

        // self delete the completed task, if needed
        if (shouldSelfDelete) {
            sendSelfDelete();
        }
    }

    protected abstract void handleStartedStagePatch(T state);

    protected void handleFailedStagePatch(T state) {
        ServiceErrorResponse err = state.taskInfo.failure;
        logWarning("Task failed with: %s", err == null ? "n.a." : err.message);
        if (err != null && err.stackTrace != null) {
            logFine("Task failure stack trace: %s", err.stackTrace);
        }
        notifyCallerService(state);
    }

    protected void handleFinishedStagePatch(T state) {
        notifyCallerService(state);
    }

    protected boolean validateStageTransitionAndState(Operation patch,
            T patchBody, T currentState) {

        // referer is only shown if different from the task itself
        final String refererLogPart = patch.getUri().equals(patch.getReferer()) ? ""
                : String.format(" Caller: [%s]", patch.getReferer());

        long currentExpiration = currentState.documentExpirationTimeMicros;
        if (patchBody.taskInfo == null || patchBody.taskInfo.stage == null) {
            patch.fail(new IllegalArgumentException("taskInfo and taskInfo.stage are required"));
            return true;
        }

        if (TaskStage.FAILED == patchBody.taskInfo.stage
                && TaskStage.FAILED == currentState.taskInfo.stage) {
            logWarning("Task patched to failed when already in failed state. %s", refererLogPart);
            patch.complete();
            return true;
        }

        if (currentState.taskInfo.stage.ordinal() > patchBody.taskInfo.stage.ordinal()) {
            if (patchBody.taskSubStage == currentState.taskSubStage
                    && DefaultSubStage.ERROR.name().equals(currentState.taskSubStage.name())
                    && TaskStage.FAILED == currentState.taskInfo.stage) {
                logWarning("Task already failed.%s", refererLogPart);
                patch.complete(); // already failed. No need for another exception.
                return true;
            }
            logWarning("Can't move from %s(%s) to %s(%s).%s",
                    currentState.taskInfo.stage, currentState.taskSubStage,
                    patchBody.taskInfo.stage, patchBody.taskSubStage, refererLogPart);
            if (TaskStage.FAILED == currentState.taskInfo.stage) {
                patch.complete(); // already failed. No need for another exception.
            } else {
                patch.fail(new IllegalArgumentException("stage can not move backwards from: "
                        + currentState.taskInfo.stage + " to: " + patchBody.taskInfo.stage));
            }
            return true;
        }

        if (patchBody.taskSubStage != null
                && currentState.taskSubStage.ordinal() > patchBody.taskSubStage.ordinal()) {
            if (patchBody.taskInfo.stage == currentState.taskInfo.stage &&
                    !transientSubStages.contains(patchBody.taskSubStage)) {
                logWarning("Can't move from %s(%s) to %s(%s).%s",
                        currentState.taskInfo.stage, currentState.taskSubStage,
                        patchBody.taskInfo.stage, patchBody.taskSubStage, refererLogPart);
                patch.fail(new IllegalArgumentException("subStage can not move backwards from:"
                        + currentState.taskSubStage + " to: " + patchBody.taskSubStage));
            } else {
                patch.complete();// different stages
            }
            return true;
        }

        log(this.logLevel, "Moving from %s(%s) to %s(%s).%s",
                currentState.taskInfo.stage, currentState.taskSubStage,
                patchBody.taskInfo.stage, patchBody.taskSubStage, refererLogPart);

        if (patchBody.taskInfo.failure != null) {
            currentState.taskInfo.failure = patchBody.taskInfo.failure;
        }

        // update current stage to new stage
        currentState.taskInfo.stage = patchBody.taskInfo.stage;

        if (patchBody.taskSubStage != null) {
            currentState.taskSubStage = patchBody.taskSubStage;
        }

        adjustStat(patchBody.taskInfo.stage.toString(), 1);

        autoMergeState(patch, patchBody, currentState);
        if (currentState.documentExpirationTimeMicros == 0) {
            currentState.documentExpirationTimeMicros = currentExpiration;
        }
        customStateValidationAndMerge(patch, patchBody, currentState);

        return false;
    }

    /**
     * Performs automatic task state merge based on state annotations.
     */
    protected void autoMergeState(Operation patch, T patchBody, T currentState) {
        // use default merging for AUTO_MERGE_IF_NOT_NULL fields
        Utils.mergeWithState(getStateDescription(), currentState, patchBody);
    }

    /**
     * Performs custom task state validation and merge. Allows sub-classes to provide custom
     * validation and merge code when the automatic merge based on annotations is not sufficient.
     * <p>
     * This method must not complete/fail the given patch operation.
     */
    protected void customStateValidationAndMerge(Operation patch, T patchBody, T currentState) {
    }

    protected void createCounterSubTask(T state, long count,
            Consumer<String> callbackFunction) {
        createCounterSubTask(state, count, DefaultSubStage.COMPLETED, callbackFunction);
    }

    protected void createCounterSubTask(T state, long count, Enum<?> substageComplete,
            Consumer<String> callbackFunction) {
        CounterSubTaskState subTaskInitState = new CounterSubTaskState();
        subTaskInitState.completionsRemaining = count;
        subTaskInitState.documentExpirationTimeMicros = ServiceUtils
                .getDefaultTaskExpirationTimeInMicros();
        subTaskInitState.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(), TaskStage.STARTED, substageComplete,
                TaskStage.STARTED, DefaultSubStage.ERROR);

        CounterSubTaskService.createSubTask(this, subTaskInitState, callbackFunction);
    }

    protected void createCounterSubTaskCallback(T state, long count, boolean external,
            Consumer<ServiceTaskCallback> callbackFunction) {
        createCounterSubTaskCallback(state, count, external, DefaultSubStage.COMPLETED,
                callbackFunction);
    }

    protected void createCounterSubTaskCallback(T state, long count, boolean external,
            Enum<?> substageComplete, Consumer<ServiceTaskCallback> callbackFunction) {
        createCounterSubTaskCallback(state, count, external, false, substageComplete,
                callbackFunction);
    }

    protected void createCounterSubTaskCallback(T state, long count, boolean external,
            boolean useCounterService, Enum<?> substageComplete,
            Consumer<ServiceTaskCallback> callbackFunction) {
        if (count == 1 && !useCounterService) {
            ServiceTaskCallback taksCallback = ServiceTaskCallback.create(
                    external ? getUri().toString() : getSelfLink(),
                    TaskStage.STARTED, substageComplete,
                    TaskStage.STARTED, DefaultSubStage.ERROR);
            callbackFunction.accept(taksCallback);
            return;
        }

        createCounterSubTask(state, count, substageComplete, (link) -> {
            ServiceTaskCallback taksCallback = ServiceTaskCallback.create(
                    external ? UriUtils.buildUri(getHost(), link).toString() : link,
                    TaskStage.FINISHED, TaskStage.FAILED);
            callbackFunction.accept(taksCallback);
        });
    }

    protected void completeSubTasksCounter(ServiceTaskCallback taskCallback, Throwable ex) {
        ServiceTaskCallbackResponse response;
        if (ex == null) {
            response = taskCallback.getFinishedResponse();
        } else {
            response = taskCallback.getFailedResponse(ex);
        }
        URI uri;
        if (taskCallback.isExternal()) {
            uri = URI.create(taskCallback.serviceSelfLink);
        } else {
            uri = UriUtils.buildUri(getHost(), taskCallback.serviceSelfLink);
        }

        sendRequest(Operation.createPatch(uri)
                .setBody(response)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Notifying counting task failed: %s", e);
                    }
                }));
    }

    protected void completeSubTasksCounter(String subTaskLink, Throwable ex) {
        CounterSubTaskState body = new CounterSubTaskState();
        body.taskInfo = new TaskState();
        if (ex == null) {
            body.taskInfo.stage = TaskStage.FINISHED;
        } else {
            body.taskInfo.stage = TaskStage.FAILED;
            body.taskInfo.failure = Utils.toServiceErrorResponse(ex);
        }

        sendRequest(Operation.createPatch(this, subTaskLink)
                .setBody(body)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Notifying counting task failed: %s", e);
                    }
                }));
    }

    /**
     * Moves the task to the given subStage. The method assumes the task stage is STARTED as this is
     * the stage where most sub-stage transition happen.
     */
    protected void proceedTo(E subStage, Consumer<T> patchBodyConfigurator) {
        proceedTo(TaskStage.STARTED, subStage, patchBodyConfigurator);
    }

    protected void proceedTo(E subStage) {
        proceedTo(TaskStage.STARTED, subStage, null);
    }

    /**
     * Completes the task by setting its stage to FINISHED. The subStage can be specified, and if
     * not, the one named COMPLETED will be used, if any.
     */
    protected void complete(E subStage, Consumer<T> patchBodyConfigurator) {
        proceedTo(TaskStage.FINISHED, subStage, patchBodyConfigurator);
    }

    protected void complete(E subStage) {
        complete(subStage, null);
    }

    protected void complete(Consumer<T> patchBodyConfigurator) {
        complete(Enum.valueOf(this.subStageType, DefaultSubStage.COMPLETED.toString()),
                patchBodyConfigurator);
    }

    protected void complete() {
        complete(Enum.valueOf(this.subStageType, DefaultSubStage.COMPLETED.toString()));
    }

    /**
     * Completes the task by setting its stage to FAILED. The subStage can be specified, and if not,
     * the one named ERROR will be used, if any.
     */
    protected void completeWithError(E subStage, Consumer<T> patchBodyConfigurator) {
        proceedTo(TaskStage.FAILED, subStage, patchBodyConfigurator);
    }

    protected void completeWithError(E subStage) {
        completeWithError(subStage, null);
    }

    protected void completeWithError(Consumer<T> patchBodyConfigurator) {
        completeWithError(Enum.valueOf(this.subStageType, DefaultSubStage.ERROR.toString()),
                patchBodyConfigurator);
    }

    protected void completeWithError() {
        completeWithError(Enum.valueOf(this.subStageType, DefaultSubStage.ERROR.toString()));
    }

    /**
     * Moves the task to the specified stage/subStage by sending a patch to self. By default the
     * patch body is empty; use the {@code patchBodyConfigurator} argument to set patch fields that
     * are required for this stage transition.
     */
    @SuppressWarnings("unchecked")
    protected void proceedTo(TaskStage stage, E subStage, Consumer<T> patchBodyConfigurator) {
        T body = null;

        try {
            body = (T) getStateType().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        body.taskInfo = new TaskState();
        body.taskInfo.stage = stage;
        body.taskSubStage = subStage;
        if (patchBodyConfigurator != null) {
            patchBodyConfigurator.accept(body);
        }

        sendRequest(Operation.createPatch(getUri())
                .setBody(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Moving task to %s:%s failed: %s", stage, subStage,
                                e.getMessage());
                    }
                }));
    }

    protected void proceedTo(TaskStage stage, E subStage) {
        proceedTo(stage, subStage, null);
    }

    private void sendSelfDelete() {
        logFine("Self deleting completed task %s", getUri().getPath());
        sendRequest(Operation.createDelete(getUri()));
    }

    protected boolean isFailedOrCancelledTask(T state) {
        return state.taskInfo != null &&
                (TaskStage.FAILED == state.taskInfo.stage ||
                        TaskStage.CANCELLED == state.taskInfo.stage);
    }

    @Override
    public void handleDelete(Operation delete) {
        T state = getState(delete);
        if (state == null || state.taskInfo == null || state.taskInfo.stage == null) {
            delete.complete();
            return;
        }
        switch (state.taskInfo.stage) {
        case CREATED:
        case STARTED:
            String errMsg = "Task expired in stage: %s, sub-stage: %s, expirationTime: %s";
            logWarning(errMsg, state.taskInfo.stage, state.taskSubStage,
                    state.documentExpirationTimeMicros);
            if (!state.serviceTaskCallback.isEmpty()) {
                IllegalStateException e = new IllegalStateException(String.format(errMsg,
                        state.taskInfo.stage, state.taskSubStage,
                        state.documentExpirationTimeMicros));
                sendRequest(Operation
                        .createPatch(this, state.serviceTaskCallback.serviceSelfLink)
                        .setBody(state.serviceTaskCallback
                                .getFailedResponse(e))
                        .addPragmaDirective(
                                Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                logWarning("Updating state to expired failed: %s",
                                        Utils.toString(ex));
                            }
                        }));
            }
            delete.complete();
            break;
        default:
            delete.complete();
            break;
        }
    }

    public void failTask(String errMsg, Throwable t) {
        failTask(errMsg, t, true);
    }

    public void failTask(String errMsg, Throwable t, boolean logAsWarning) {
        final Level logLevel = logAsWarning ? Level.WARNING : Level.INFO;
        final String msg = errMsg == null ? "Unexpected State" : errMsg;
        if (t != null) {
            log(logLevel, () -> String.format("%s%s Error: %s", msg, msg.endsWith(".") ? "" : ".",
                    Utils.toString(t)));
        } else {
            log(logLevel, () -> msg);
        }
        ServiceTaskCallbackResponse body = new ServiceTaskCallbackResponse();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = TaskStage.FAILED;
        body.taskSubStage = DefaultSubStage.ERROR;
        if (t != null) {
            body.taskInfo.failure = getServiceErrorResponse(t);
        } else {
            ServiceErrorResponse rsp = new ServiceErrorResponse();
            rsp.message = msg;
            body.taskInfo.failure = rsp;
        }

        sendRequest(Operation.createPatch(getUri())
                .setBody(body)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning("Self patch failed: %s", Utils.toString(ex));
                    }
                }));
    }

    protected ServiceErrorResponse getServiceErrorResponse(Throwable t) {
        Operation operation = null;
        if (locale != null) {
            operation = new Operation().addRequestHeader(Operation.ACCEPT_LANGUAGE_HEADER,
                    locale);
        }
        return Utils.toValidationErrorResponse(t, operation);
    }

    protected void notifyCallerService(T state) {
        if (state.serviceTaskCallback.isEmpty()) {
            return;
        }
        log(this.logLevel, "Callback to [%s] with state [%s]",
                state.serviceTaskCallback.serviceSelfLink, state.taskInfo.stage);

        if (state.serviceTaskCallback.isExternal()) {
            sendRequestStateToExternalUrl(state.serviceTaskCallback.serviceSelfLink, state);
        } else {
            ServiceTaskCallbackResponse callbackResponse;
            switch (state.taskInfo.stage) {
            case FINISHED:
                callbackResponse = getFinishedCallbackResponse(state);
                break;
            case CANCELLED:
                return;
            case FAILED:
            default:
                callbackResponse = getFailedCallbackResponse(state);
                break;
            }

            // copy the state custom properties
            callbackResponse.customProperties = mergeCustomProperties(
                    callbackResponse.customProperties, state.customProperties);

            sendRequest(Operation.createPatch(this, state.serviceTaskCallback.serviceSelfLink)
                    .setBody(callbackResponse)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            logWarning("Notifying parent task %s failed: %s", o.getUri(),
                                    Utils.toString(e));
                        }
                    }));
        }
    }

    protected ServiceTaskCallbackResponse getFailedCallbackResponse(T state) {
        return state.serviceTaskCallback.getFailedResponse(state.taskInfo.failure);
    }

    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(T state) {
        return state.serviceTaskCallback.getFinishedResponse();
    }

    /**
     * Declares service fields which will be sent to client for information about the task.
     */
    protected BaseExtensibilityCallbackResponse notificationPayload(T state) {
        return null;
    }

    /**
     * Declares service fields which will be merged once response from subscriber is received.
     */
    protected ServiceTaskCallbackResponse replyPayload(T state) {
        return notificationPayload(state);
    }

    /**
     * Extends notification payload in order to include more data, that is not possible to be
     * retrieved directly from task. {@link notificationPayload()} is used like schema. It declares
     * which fields will be merged from task state just before sending an event to subscriber. For
     * example in ContainerAllocationTaskState there is a field 'resourceDescriptionLink' which
     * points to ContainerDescription documentSelfLink. The ContainerDescription itself is not
     * defined in task, so additional logic is neccessary in order to retrieve the object. Here
     * comes the method.
     *
     * @param state               - Task state
     * @param notificationPayload - notification payload of task that will be extended with more data.
     * @param relatedStates       - compute states related to this task, provided by
     * {@link getRelatedResourcesForExtensibility()}
     *
     *
     */
    protected DeferredResult<Void> enhanceNotificationPayload(T state, Collection<ResourceState>
            relatedStates, BaseExtensibilityCallbackResponse notificationPayload) {
        return DeferredResult.completed(null);
    }

    private DeferredResult<Collection<ResourceState>>getRelatedResourcesForExtensibility(T state) {
        List<DeferredResult<ResourceState>> results = getRelatedResourcesLinks(state).stream()
                .map(link -> Operation.createGet(this, link))
                .map(o -> (DeferredResult<ResourceState>)sendWithDeferredResult(o,
                        getRelatedResourceStateType(state)))
                .collect(Collectors.toList());

        return DeferredResult.allOf(results).thenApply(r -> new ArrayList<ResourceState>(r));
    }

    protected boolean skipExtensibility(T state) {
        return false;
    }

    protected Collection<String> getRelatedResourcesLinks(T state) {
        throw new NotImplementedException();
    }

    protected Class<? extends ResourceState> getRelatedResourceStateType(T state) {
        throw new NotImplementedException();
    }

    public<R extends ResourceState> DeferredResult<Map<String, String>> getResourceStatesTags(
            Collection<R> resources) {

        List<String> tagLinks = resources.stream()
                .filter(res -> res.tagLinks != null)
                .flatMap(res -> res.tagLinks.stream())
                .collect(Collectors.toList());

        if (!tagLinks.isEmpty()) {
            List<DeferredResult<TagState>> gets = tagLinks.stream()
                    .map(link -> this.sendWithDeferredResult(Operation.createGet(this.getHost(),
                            link), TagState.class))
                    .collect(Collectors.toList());

            return DeferredResult.allOf(gets).thenApply(tags -> tags.stream()
                    .collect(Collectors.toMap(t -> t.key, t -> t.value)));
        } else {
            return DeferredResult.completed(new HashMap<String, String>());
        }
    }

    /**
     * <p>
     * Once response from client is received, it may contains data that can not be merged
     * automatically to some of the task fields. Additional request to some resource may be
     * needed in order to patch object which is not defined in task itself. <br/>
     * <p>
     * For example:
     * {@link ComputeProvisionTaskService} provides extensibility mechanism for patching
     * ComputeState address. ComputeState is not defined in task, but its self link is.
     * In this case once the response from subscriber is received, additional call is needed
     * to get the corresponded ComputeState(s) in order to patch its address before task
     * is resumed.
     * </p>
     * </p>
     *
     * @param state    - Task state
     */
    public DeferredResult<Void> enhanceExtensibilityResponse(T state, ServiceTaskCallbackResponse
            replyPayload) {
        return DeferredResult.completed(null);
    }

    public DeferredResult<Void> patchCommonFields(T state, ServiceTaskCallbackResponse
            replyPayload) {
        return patchCustomPropertiesFromExtensibilityResponse(state, replyPayload).thenCompose(r ->
                patchTagsFromExtensibilityResponse(state, replyPayload));
    }

    public DeferredResult<Void> patchCustomPropertiesFromExtensibilityResponse(T state,
            ServiceTaskCallbackResponse replyPayload) {
        if (replyPayload.customProperties != null && !replyPayload.customProperties.isEmpty()) {
            List<DeferredResult<Operation>> results = getRelatedResourcesLinks(state).stream()
                    .map(link -> {
                        try {
                            ResourceState doc = getRelatedResourceStateType(state).newInstance();
                            doc.customProperties = new HashMap<>(replyPayload.customProperties);
                            return sendWithDeferredResult(
                                    Operation.createPatch(this, link).setBody(doc));
                        } catch (InstantiationException | IllegalAccessException e) {
                            return DeferredResult.<Operation>failed(e);
                        }
                    })
                    .collect(Collectors.toList());

            return DeferredResult.allOf(results).thenAccept(r -> {
            });
        } else {
            return DeferredResult.completed(null);
        }
    }

    public DeferredResult<Void> patchTagsFromExtensibilityResponse(T state,
            ServiceTaskCallbackResponse replyPayload) {

        Collection<String> resources = getRelatedResourcesLinks(state);

        List<DeferredResult<Operation>> drs = resources.stream()
                .map(link -> {
                    BaseExtensibilityCallbackResponse response = (BaseExtensibilityCallbackResponse) replyPayload;
                    if (response.tags != null) {
                        TagAssignmentRequest req = new TagAssignmentRequest();
                        req.tagsToAssign = response.tags.entrySet().stream()
                                .map(ent -> new KeyValue(ent.getKey(), ent.getValue()))
                                .collect(Collectors.toList());
                        req.resourceLink = link;
                        return req;
                    } else {
                        return null;
                    }
                })
                .filter(r -> r != null)
                .map(req -> sendWithDeferredResult(
                        Operation.createPost(this, TagAssignmentService.SELF_LINK)
                                .setBody(req))).collect(Collectors.toList());

        return DeferredResult.allOf(drs).thenAccept(r -> {
        });
    }

    protected void fillCommonFields(T state,
            BaseExtensibilityCallbackResponse notificationPayload, Runnable callback) {

        Map<String, String> properties = state.customProperties != null ? state.customProperties
                : new HashMap<>();

        if (notificationPayload.customProperties != null) {
            properties.putAll(notificationPayload.customProperties);
        }

        notificationPayload.requestId = properties.get("__request_id");
        notificationPayload.componentId = properties.get("__component_id");
        notificationPayload.blueprintId = properties.get("__blueprint_id");
        notificationPayload.componentTypeId = properties.get("__component_type_id");
        notificationPayload.owner = properties.get("__owner");

        callback.run();
    }

    private void sendRequestStateToExternalUrl(String callbackReference, T state) {
        // send put with the RequestState as the body
        log(this.logLevel, "Calling callback URI: %s", callbackReference);

        try {
            URI callbackUri = URI.create(callbackReference);
            Operation.createPost(callbackUri)
                    .setBody(state)
                    .forceRemote()
                    .setCompletion((op, ex) -> {
                        if (ex != null) {
                            logSevere("Failure calling callback '%s' for registry state: %s",
                                    op.getUri(), Utils.toString(ex));
                        }
                    }).sendWith(this);
        } catch (Exception e) {
            logSevere(e);
        }
    }

    /**
     * Returns extensibility manager associated with the host.
     */
    private ExtensibilitySubscriptionManager getExtensibilityManager() {
        if (extensibilityManager == null) {
            if (getHost() instanceof IExtensibilityRegistryHost) {
                extensibilityManager = ((IExtensibilityRegistryHost) getHost())
                        .getExtensibilityRegistry();
            }
            if (extensibilityManager == null) {
                getHost().log(Level.SEVERE, "Host does not provide extensibility manager");
            }
        }
        return extensibilityManager;
    }

    protected TaskStatusState fromTask(TaskServiceDocument<E> state) {
        return fromTask(new TaskStatusState(), state);
    }

    protected <S extends TaskStatusState> S fromTask(S taskStatus, TaskServiceDocument<E> state) {
        taskStatus.documentSelfLink = getSelfId();
        taskStatus.phase = displayName;
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

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        template.documentDescription.serializedStateSizeLimit = MAX_STATE_SIZE;
        return template;
    }

    public static class BaseExtensibilityCallbackResponse extends ServiceTaskCallbackResponse {
        public String requestId;
        public String componentId;
        public String blueprintId;
        public String componentTypeId;
        public String owner;
        public Map<String, String> tags;
    }
}
