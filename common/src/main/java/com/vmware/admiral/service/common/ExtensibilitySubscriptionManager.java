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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.SubscriptionManager;
import com.vmware.admiral.common.util.SubscriptionManager.SubscriptionNotification;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionCallbackService.ExtensibilitySubscriptionCallback;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
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

    // internal map of the registered extensibility subscriptions
    private final Map<String, ExtensibilitySubscription> subscriptions = new ConcurrentHashMap<>();

    private SubscriptionManager<ConfigurationState> subscriptionManager;

    private AtomicBoolean initialized = new AtomicBoolean();

    public ExtensibilitySubscriptionManager() {
    }

    @Override
    public void handleStart(Operation start) {
        initialize(start);
    }

    @Override
    public void handleStop(Operation delete) {
        if (subscriptionManager != null) {
            subscriptionManager.close();
        }
        initialized.set(false);

        super.handleStop(delete);
    }

    <T extends TaskServiceDocument<?>> void handleStagePatch(
            ServiceTaskCallbackResponse notificationPayload,
            ServiceTaskCallbackResponse replyPayload, T state,
            Consumer<T> callback) {

        ExtensibilitySubscription extensibilitySubscription = getExtensibilitySubscription(state);

        if (extensibilitySubscription == null) {
            // no extensibility subscription registered, continue task execution
            callback.accept(state);
            return;
        }

        if (extensibilitySubscription.blocking) {
            // blocking notification
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

        ensureSubscriptionTargetExists(op, () -> {
            subscribe();
            loadExtensibilityStates(op);
        });
    }

    private void subscribe() {
        subscriptionManager = new SubscriptionManager<>(
                getHost(), getHost().nextUUID(),
                ExtensibilitySubscriptionService.LAST_UPDATED_DOCUMENT_KEY,
                ConfigurationState.class, true);

        subscriptionManager.start(this::updateExtensibilityCache);
    }

    private void loadExtensibilityStates(Operation op) {
        QueryTask q = QueryUtil.buildQuery(ExtensibilitySubscription.class, false);
        QueryUtil.addExpandOption(q);
        new ServiceDocumentQuery<>(getHost(), ExtensibilitySubscription.class)
                .query(q, (r) -> {
                    if (r.hasException()) {
                        logSevere("Exception while initializing LifecycleExtensibilityManager. "
                                + "Error: [%s]", r.getException().getMessage());
                        op.fail(r.getException());
                    } else if (r.hasResult()) {
                        ExtensibilitySubscription state = r.getResult();
                        addExtensibilitySubscription(state);
                    } else {
                        logInfo("Loaded %d extensibility states", subscriptions.size());
                        op.complete();
                    }
                });
    }

    private void updateExtensibilityCache(SubscriptionNotification<ConfigurationState> prop) {
        if (prop.isDelete()) {
            throw new IllegalStateException("Deleting update extensibility configuration is "
                    + "not expected");
        }

        String extensibilityLink = prop.getResult().value;

        sendRequest(Operation.createGet(this, extensibilityLink)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        removeExtensibilitySubscription(extensibilityLink);
                        return;
                    }
                    if (e != null) {
                        logSevere("Error getting '%s' for extensibility subscription: %s",
                                extensibilityLink, Utils.toJson(e));
                        throw e instanceof RuntimeException
                                ? (RuntimeException) e
                                : new RuntimeException(e);
                    }

                    addExtensibilitySubscription(o.getBody(ExtensibilitySubscription.class));
                }));
    }

    /**
     * Sends notification to subscriber. Client should post to the callback state to resume the task
     * execution.
     *
     * @param notificationPayload
     *            payload that task sent to subscriber,
     * @param replyPayload
     *            payload which task accept for response
     * @param extensibility
     *            subscription info.
     * @param state
     *            task state to send
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
     * @param service
     *            task service
     * @param extensibility
     *            extensibility state
     * @param state
     *            task state to send
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
        String payLoadAsJson = Utils.toJson(notificationPayloadData);
        // Filter task fields in order to leave only notification payload fields.
        T filteredTask = (T) Utils.fromJson(payLoadAsJson, state.getClass());
        return filteredTask;
    }

    /**
     * Sends a service document to external url. Supports retry in case of an error and if task
     * service is provided this method will call failTask when no more retries left.
     *
     *
     * @param extensibility
     *            extensibility state
     * @param body
     *            document to send
     * @param state
     *            - task state
     * @param retriesLeft
     *            number of retries left before give up
     *
     */
    @SuppressWarnings("rawtypes")
    private <T extends TaskServiceDocument> void sendExternalNotification(
            ExtensibilitySubscription extensibility,
            ServiceDocument body, T state,
            int retriesLeft) {

        sendRequest(Operation.createPost(extensibility.callbackReference)
                .setBody(body)
                .setReferer(getUri())
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
                            logWarning("Request to [%s] for task [%s] expired! ",
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
        PropertyUtils.mergeObjects(notificationPayload, notificationPayloadData, PropertyUtils.SHALLOW_MERGE_STRATEGY);

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

    private void ensureSubscriptionTargetExists(Operation op, Runnable callback) {
        new ServiceDocumentQuery<>(getHost(), ConfigurationState.class)
                .queryDocument(ExtensibilitySubscriptionService.LAST_UPDATED_DOCUMENT_KEY, (r) -> {
                    if (r.hasException()) {
                        op.fail(r.getException());
                    } else if (r.hasResult()) {
                        // configuration document exists, proceed.

                        callback.run();
                    } else {
                        // create new configuration document with empty value and subscribe to it
                        ConfigurationState body = ExtensibilitySubscriptionService
                                .buildConfigurationStateWithValue(null);

                        sendRequest(Operation
                                .createPost(this, ConfigurationFactoryService.SELF_LINK)
                                .addPragmaDirective(
                                        Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                                .setBody(body)
                                .setCompletion((o, e) -> {
                                    if (e != null) {
                                        op.fail(e);
                                        return;
                                    }

                                    callback.run();
                                }));
                    }
                });
    }

    private void failTask(String errMsg, String taskDocumentSelfLink) {
        if (errMsg == null) {
            errMsg = "Unexpected State";
        }

        logWarning(errMsg);

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
        logFine("Added extensibility [%s] with callback [%s]",
                state.documentSelfLink, state.callbackReference);
        subscriptions.put(UriUtils.getLastPathSegment(state.documentSelfLink), state);
    }

    private void removeExtensibilitySubscription(String extensibilityLink) {
        logFine("Remove extensibility for [%s]", extensibilityLink);
        subscriptions.remove(UriUtils.getLastPathSegment(extensibilityLink));
    }

    private <T extends TaskServiceDocument<?>> String constructKey(T state) {
        return String.format("%s:%s:%s", state.getClass().getSimpleName(),
                state.taskInfo.stage.name(), state.taskSubStage.name());
    }

    private ExtensibilitySubscription getExtensibilitySubscription(TaskServiceDocument<?> task) {
        return subscriptions.get(constructKey(task));
    }

}
