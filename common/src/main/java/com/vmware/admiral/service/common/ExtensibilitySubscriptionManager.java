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

package com.vmware.admiral.service.common;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.CommonContinuousQueries;
import com.vmware.admiral.common.util.CommonContinuousQueries.ContinuousQueryId;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.service.common.EventTopicService.EventTopicState;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionCallbackService.ExtensibilitySubscriptionCallback;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * ExtensibilitySubscriptionManager enables clients to subscribe and receive notification when a
 * task service reaches desired stage and substage.
 * <p>
 * Notifications can be asynchronous or synchronous (blocking). The first are sent and the task
 * proceeds with its execution. The latter block further task execution and wait callback to be
 * received.
 */
public class ExtensibilitySubscriptionManager extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.EXTENSIBILITY_MANAGER;

    private static final int NOTIFICATION_RETRY_COUNT = Integer.getInteger(
            "com.vmware.admiral.service.extensibility.notification.retries", 3);
    private static final int NOTIFICATION_RETRY_WAIT = Integer.getInteger(
            "com.vmware.admiral.service.extensibility.notification.wait", 15);
    private static final Duration EXTENSIBILITY_TIMEOUT = Duration.parse(
            System.getProperty("com.vmware.admiral.service.extensibility.timeout", "PT30M"));

    private static final String TIMEOUT_SUFFIX = ".timeout";
    // internal map of the registered extensibility subscriptions
    private final Map<String, ExtensibilitySubscription> subscriptions = new ConcurrentHashMap<>();

    private final Map<String, Duration> timeoutsPerTaskStageAndSubstage = new
            ConcurrentHashMap<>();

    private final Map<String, String> topicsPerTaskStageAndSubstage = new
            ConcurrentHashMap<>();

    private AtomicBoolean initialized = new AtomicBoolean();

    public ExtensibilitySubscriptionManager() {
    }

    @Override
    public void handleStart(Operation start) {
        initialize(start);
    }

    @Override
    public void handleStop(Operation delete) {
        initialized.set(false);

        super.handleStop(delete);
    }

    <T extends TaskServiceDocument<?>> void handleStagePatch(T state, Consumer<T> callback,
            Runnable notificationCallback) {

        ExtensibilitySubscription extensibilitySubscription = getExtensibilitySubscription(state);

        if (extensibilitySubscription == null) {
            // no extensibility subscription registered, continue task execution
            callback.accept(state);
            return;
        }

        notificationCallback.run();
    }

    <T extends TaskServiceDocument<?>> void sendNotification(ExtensibilitySubscription
            extensibilitySubscription, ServiceTaskCallbackResponse notificationPayload,
            ServiceTaskCallbackResponse replyPayload, T state, Consumer<T> callback) {

        if (extensibilitySubscription.blocking) {
            // blocking notification
            replyPayload.taskSubStage = state.taskSubStage;
            replyPayload.taskInfo = state.taskInfo;
            sendBlockingNotificationCall(notificationPayload, replyPayload,
                    extensibilitySubscription, state);
        } else {
            // asynchronous notification
            sendAsyncNotificationCall(notificationPayload, replyPayload, extensibilitySubscription,
                    state);
            // continue task execution with original state
            callback.accept(state);
        }
    }

    /**
     * Initialization method that
     * <ul>
     * <li>subscribes for changes on extensibility states</li>
     * <li>loads current subscriptions</li>
     * </ul>
     */
    private void initialize(Operation op) {
        if (initialized.getAndSet(true)) {
            // already initialized
            op.complete();
            return;
        }

        DeferredResult<Void> subscriptionsRead = subscribeForSubscriptions().thenCompose(k ->
                loadSubscriptions());
        DeferredResult<Void> topicsRead = subscribeForTopics().thenCompose(k -> loadTopics());

        DeferredResult.allOf(subscriptionsRead, topicsRead).whenComplete((useless, th) -> {
            if (th != null) {
                op.fail(th);
            } else {
                op.complete();
            }
        });
    }

    private DeferredResult<Void> subscribeForSubscriptions() {
        DeferredResult<Void> subscriptionsRead = new DeferredResult<Void>();
        CommonContinuousQueries
                .subscribeTo(this.getHost(), ContinuousQueryId.EXTENSIBILITY_SUBSCRIPTIONS,
                        op -> onSubscriptionChange(op, subscriptionsRead));

        return subscriptionsRead;
    }

    private DeferredResult<Void> subscribeForTopics() {
        DeferredResult<Void> topicsRead = new DeferredResult<Void>();
        CommonContinuousQueries.subscribeTo(this.getHost(), ContinuousQueryId.EVENT_TOPICS,
                op -> onEventTopicChange(op, topicsRead));

        return topicsRead;
    }

    private void onEventTopicChange(Operation op, DeferredResult<Void> done) {
        op.complete();
        QueryTask queryTask = op.getBody(QueryTask.class);
        if (queryTask.results != null) {
            for (EventTopicState state : queryTask.results.documents.values()
                    .stream().map(o -> (EventTopicState) o).collect(Collectors.toList())) {

                handleUpdateEventTopicState(state);
            }
            done.complete(null);
        }
    }

    private void handleUpdateEventTopicState(EventTopicState state) {
        String customConfig = System.getProperty(state.id + TIMEOUT_SUFFIX);
        Duration timeout = customConfig != null ? Duration.parse(customConfig) :
                EXTENSIBILITY_TIMEOUT;

        String stateKey = constructKey(state);
        timeoutsPerTaskStageAndSubstage.put(stateKey, timeout);
        topicsPerTaskStageAndSubstage.put(stateKey, state.id);
    }

    private DeferredResult<Void> loadTopics() {
        DeferredResult<Void> res = new DeferredResult<>();
        QueryTask q = QueryUtil.buildQuery(EventTopicState.class, false);
        QueryUtil.addExpandOption(q);
        new ServiceDocumentQuery<>(getHost(), EventTopicState.class)
                .query(q, (r) -> {
                    if (r.hasException()) {
                        logSevere("Exception while initializing LifecycleExtensibilityManager. "
                                + "Error: [%s]", r.getException().getMessage());
                        res.fail(r.getException());
                    } else if (r.hasResult()) {
                        EventTopicState state = r.getResult();
                        handleUpdateEventTopicState(state);
                    } else {
                        logInfo("Loaded %d extensibility states", subscriptions.size());
                        res.complete(null);
                    }
                });

        return res;
    }

    private void onSubscriptionChange(Operation op, DeferredResult<Void> done) {
        op.complete();
        QueryTask queryTask = op.getBody(QueryTask.class);
        if (queryTask.results != null) {
            for (ExtensibilitySubscription subscription : queryTask.results.documents.values()
                    .stream().map(o -> o instanceof JsonObject ? Utils.fromJson(o,
                            ExtensibilitySubscription.class) : (ExtensibilitySubscription) o)
                    .collect(Collectors.toList())) {

                if (Action.DELETE.toString().equals(subscription.documentUpdateAction)) {
                    removeExtensibilitySubscription(subscription.documentSelfLink);
                } else {
                    addExtensibilitySubscription(subscription);
                }
            }
            done.complete(null);
        }
    }

    private DeferredResult<Void> loadSubscriptions() {
        DeferredResult<Void> res = new DeferredResult<>();
        QueryTask q = QueryUtil.buildQuery(ExtensibilitySubscription.class, false);
        QueryUtil.addExpandOption(q);
        new ServiceDocumentQuery<>(getHost(), ExtensibilitySubscription.class)
                .query(q, (r) -> {
                    if (r.hasException()) {
                        logSevere("Exception while initializing LifecycleExtensibilityManager. "
                                + "Error: [%s]", r.getException().getMessage());
                        res.fail(r.getException());
                    } else if (r.hasResult()) {
                        ExtensibilitySubscription state = r.getResult();
                        addExtensibilitySubscription(state);
                    } else {
                        logInfo("Loaded %d extensibility states", subscriptions.size());
                        res.complete(null);
                    }
                });

        return res;
    }

    /**
     * Sends notification to subscriber. Client should post to the callback state to resume the task
     * execution.
     *
     * @param notificationPayload payload that task sent to subscriber,
     * @param replyPayload        payload which task accept for response
     * @param extensibility       subscription info.
     * @param state               task state to send
     */
    @SuppressWarnings({ "rawtypes" })
    private <T extends TaskServiceDocument> void sendBlockingNotificationCall(
            ServiceTaskCallbackResponse notificationPayload,
            ServiceTaskCallbackResponse replyPayload,
            ExtensibilitySubscription extensibility, T state) {

        logFine("Sending blocking notification to [%s] for [%s]",
                extensibility.callbackReference, state.documentSelfLink);

        // Create callback which will handle response from subscriber client.
        ExtensibilitySubscriptionCallback callbackState = new ExtensibilitySubscriptionCallback();
        callbackState.taskStateJson = Utils.toJson(state);
        callbackState.taskStateClassName = state.getClass().getSimpleName();

        // Set callback to service task which will be resumed, once subscriber finished.
        callbackState.serviceTaskCallback = ServiceTaskCallback.create(
                state.documentSelfLink,
                state.taskInfo.stage, state.taskSubStage,
                TaskStage.STARTED, DefaultSubStage.ERROR);
        callbackState.requestTrackerLink = state.requestTrackerLink;
        callbackState.replyPayload = replyPayload;
        callbackState.tenantLinks = state.tenantLinks;

        Duration stagePhaseTimeout = getStagePhaseTimeout(state);

        callbackState.due = LocalDateTime.now().plusNanos(stagePhaseTimeout.toNanos());

        sendRequest(Operation
                .createPost(this, ExtensibilitySubscriptionCallbackService.FACTORY_LINK)
                .setBody(callbackState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failure creating extensibility callback: %s", Utils.toJson(e));
                        return;
                    }

                    ExtensibilitySubscriptionCallback result = o
                            .getBody(ExtensibilitySubscriptionCallback.class);

                    sendExternalNotification(extensibility,
                            buildDataToSend(notificationPayload, replyPayload, result),
                            state, NOTIFICATION_RETRY_COUNT);
                }));
    }

    /**
     * Sends notification to subscriber
     *
     * @param notificationPayload notification payload
     * @param replyPayload        reply payload
     * @param extensibility       extensibility state
     * @param state               task state to send
     */
    @SuppressWarnings({ "rawtypes" })
    private <T extends TaskServiceDocument> void sendAsyncNotificationCall(
            ServiceTaskCallbackResponse notificationPayload,
            ServiceTaskCallbackResponse replyPayload,
            ExtensibilitySubscription extensibility, T state) {
        logFine("Sending async notification to [%s] for [%s]",
                extensibility.callbackReference, state.documentSelfLink);
        // Task is filtered to provide only fields declared as notification payload.
        T notificationPayloadState = prepareTaskNotificationPayload(notificationPayload, state);
        sendExternalNotification(extensibility, notificationPayloadState, state,
                NOTIFICATION_RETRY_COUNT);
    }

    @SuppressWarnings("unchecked")
    private <T> T prepareTaskNotificationPayload(ServiceTaskCallbackResponse notificationPayload,
            T state) {
        String stateAsJson = Utils.toJson(state);
        ServiceTaskCallbackResponse notificationPayloadData = Utils.fromJson(
                stateAsJson, notificationPayload.getClass());
        notificationPayloadData.taskInfo.stage = TaskStage.STARTED;
        notificationPayloadData.taskSubStage = DefaultSubStage.CREATED;
        String payLoadAsJson = Utils.toJson(notificationPayloadData);
        // Filter task fields in order to leave only notification payload fields.
        T filteredTask = (T) Utils.fromJson(payLoadAsJson, state.getClass());
        return filteredTask;
    }

    /**
     * Sends a service document to external url. Supports retry in case of an error and if task
     * service is provided this method will call failTask when no more retries left.
     *
     * @param extensibility extensibility state
     * @param body          document to send
     * @param state         - task state
     * @param retriesLeft   number of retries left before give up
     */
    @SuppressWarnings("rawtypes")
    private <T extends TaskServiceDocument> void sendExternalNotification(
            ExtensibilitySubscription extensibility,
            ServiceDocument body, T state,
            int retriesLeft) {

        sendRequest(Operation.createPost(extensibility.callbackReference)
                .setBody(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(
                                "Retrying [%s] times to notify [%s]. Error: [%s]",
                                retriesLeft, extensibility.callbackReference,
                                e.getMessage());

                        if (retriesLeft <= 1) {
                            logWarning("Cannot notify [%s] for task [%s]. Error: %s",
                                    extensibility.callbackReference, body.documentSelfLink,
                                    e.getMessage());

                            failTask(e.getMessage(), state.documentSelfLink);
                        } else if (o.getStatusCode() == Operation.STATUS_CODE_TIMEOUT) {
                            // Call to ExtensibilitySubscriptionCallback will resume the service
                            // task.
                            logWarning("Request to [%s] for task [%s] expired!",
                                    extensibility.callbackReference, body.documentSelfLink);
                        } else {
                            getHost().schedule(() -> {
                                sendExternalNotification(extensibility, body, state,
                                        retriesLeft - 1);
                            }, NOTIFICATION_RETRY_WAIT, TimeUnit.SECONDS);

                        }
                    }
                }));
    }

    @SuppressWarnings("rawtypes")
    private <T extends TaskServiceDocument> ServiceDocument buildDataToSend(
            ServiceTaskCallbackResponse notificationPayload,
            ServiceTaskCallbackResponse replyPayload,
            ExtensibilitySubscriptionCallback result) {

        // Notification payload will give information about the task to subscriber.
        ServiceTaskCallbackResponse notificationPayloadData = Utils.fromJson(
                result.taskStateJson, notificationPayload.getClass());

        //Copy enhanced payload (if some enhancements to payload have been made)
        PropertyUtils.mergeObjects(notificationPayload, notificationPayloadData,
                PropertyUtils.SHALLOW_MERGE_STRATEGY);
        notificationPayload.customProperties = notificationPayload
                .customProperties != null ? filterSystemProperties(notificationPayload
                .customProperties) : null;

        // Get service reply payload in order to notify subscriber which fields are acceptable for
        // response.
        ServiceTaskCallbackResponse replyPayloadData = Utils.fromJson(
                result.taskStateJson, replyPayload.getClass());

        ExtensibilitySubscriptionCallback data = new ExtensibilitySubscriptionCallback();
        data.serviceCallback = UriUtils.buildUri(getHost(), result.documentSelfLink);
        data.notificationPayload = Utils.toJson(notificationPayload);
        data.replyPayload = replyPayloadData;
        data.taskStateClassName = result.taskStateClassName;
        data.tenantLinks = result.tenantLinks;

        return data;
    }

    private static Map<String, String> filterSystemProperties(Map<String, String> properties) {
        return properties.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("_"))
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
    }

    private void failTask(String msg, String taskDocumentSelfLink) {
        String errMsg = msg != null ? msg : "Unexpected State";
        logWarning("Fail extensibility task: %s", errMsg);

        ServiceTaskCallbackResponse body = new ServiceTaskCallbackResponse();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = TaskStage.FAILED;
        body.taskSubStage = DefaultSubStage.ERROR;

        ServiceErrorResponse rsp = new ServiceErrorResponse();
        rsp.message = errMsg;
        body.taskInfo.failure = rsp;

        sendRequest(Operation.createPatch(UriUtils.buildUri(getHost(), taskDocumentSelfLink))
                .setBody(body)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning("Patch for fail task operation failed: %s", Utils.toString(ex));
                    }
                }));
    }

    private void addExtensibilitySubscription(ExtensibilitySubscription state) {
        logInfo("Added extensibility [%s] with callback [%s]",
                state.documentSelfLink, state.callbackReference);
        subscriptions.put(UriUtils.getLastPathSegment(state.documentSelfLink), state);
    }

    private void removeExtensibilitySubscription(String extensibilityLink) {
        logInfo("Remove extensibility for [%s]", extensibilityLink);
        subscriptions.remove(UriUtils.getLastPathSegment(extensibilityLink));
    }

    private <T extends TaskServiceDocument<?>> String constructKey(T state) {
        return String.format("%s:%s:%s", state.getClass().getSimpleName(),
                state.taskInfo.stage.name(), state.taskSubStage.name());
    }

    private String constructKey(EventTopicState state) {
        return String.format("%s:%s:%s", state.topicTaskInfo.task,
                state.topicTaskInfo.stage, state.topicTaskInfo.substage);
    }

    protected ExtensibilitySubscription getExtensibilitySubscription(TaskServiceDocument<?> task) {
        return subscriptions.get(constructKey(task));
    }

    private <T extends TaskServiceDocument> Duration getStagePhaseTimeout(T state) {
        String stateSubstateKey = constructKey(state);
        String topicTimeoutKey =
                topicsPerTaskStageAndSubstage.get(stateSubstateKey) + TIMEOUT_SUFFIX;
        if (state.customProperties != null && state.customProperties.containsKey(topicTimeoutKey)) {
            return Duration.parse(state.customProperties.get(topicTimeoutKey).toString());
        } else {
            return timeoutsPerTaskStageAndSubstage.getOrDefault(stateSubstateKey,
                    EXTENSIBILITY_TIMEOUT);
        }
    }
}
