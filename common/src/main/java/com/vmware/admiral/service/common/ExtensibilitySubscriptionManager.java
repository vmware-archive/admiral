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
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.SubscriptionManager;
import com.vmware.admiral.common.util.SubscriptionManager.SubscriptionNotification;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionCallbackService.ExtensibilitySubscriptionCallback;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
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
            AbstractTaskStatefulService<?, ?> taskService, T state,
            Consumer<T> callback) {

        ExtensibilitySubscription extensibilitySubscription = getExtensibilitySubscription(state);

        if (extensibilitySubscription == null) {
            // no extensibility subscription registered, continue task execution
            callback.accept(state);
            return;
        }

        if (extensibilitySubscription.blocking) {
            // blocking notification
            sendBlockingNotificationCall(taskService, extensibilitySubscription, state);
        } else {
            // asynchronous notification
            sendAsyncNotificationCall(extensibilitySubscription, state);
            // continue task execution
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
     * Sends notification to subscriber
     *
     * @param service
     *            task service
     * @param extensibility
     *            extensibility state
     * @param state
     *            task state to send
     */
    @SuppressWarnings("rawtypes")
    private <T extends TaskServiceDocument> void sendAsyncNotificationCall(
            ExtensibilitySubscription extensibility,
            T state) {
        logFine("Sending async notification to [%s] for [%s]",
                extensibility.callbackReference, state.documentSelfLink);

        sendExternalNotification(extensibility, state, NOTIFICATION_RETRY_COUNT);
    }

    /**
     * Sends notification to subscriber. Client should post to the callback state to resume the task
     * execution.
     *
     * @param service
     *            task service
     * @param extensibility
     *            extensibility state
     * @param state
     *            task state to send
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private <T extends TaskServiceDocument> void sendBlockingNotificationCall(
            AbstractTaskStatefulService service,
            ExtensibilitySubscription extensibility, T state) {

        logFine("Sending blocking notification to [%s] for [%s]",
                extensibility.callbackReference, state.documentSelfLink);

        ExtensibilitySubscriptionCallback callbackState = new ExtensibilitySubscriptionCallback();
        callbackState.taskStateJson = Utils.toJson(state);

        sendRequest(Operation
                .createPost(this, ExtensibilitySubscriptionCallbackService.FACTORY_LINK)
                .setBody(callbackState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failure creating extensibility callback: %s", Utils.toJson(e));
                        service.failTask("Failure creating extensibility callback", e);
                        return;
                    }

                    ExtensibilitySubscriptionCallback result = o
                            .getBody(ExtensibilitySubscriptionCallback.class);

                    T taskToSend = (T) Utils.fromJson(result.taskStateJson, service.getStateType());
                    service.processForExtensibility(taskToSend);

                    sendExternalNotification(extensibility, buildDataToSend(result, taskToSend),
                            NOTIFICATION_RETRY_COUNT);
                }));
    }

    /**
     * Sends a service document to external url. Supports retry in case of an error and if task
     * service is provided this method will call failTask when no more retries left.
     *
     * @param extensibility
     *            extensibility state
     * @param body
     *            document to send
     * @param retriesLeft
     *            number of retries left before give up
     */
    private void sendExternalNotification(ExtensibilitySubscription extensibility,
            ServiceDocument body, int retriesLeft) {
        //URI uri = URI.create(extensibility.callbackReference);
        sendRequest(Operation.createPost(extensibility.callbackReference)
                .setBody(body)
                .setReferer(getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (retriesLeft <= 1) {
                            logWarning("Cannot notify [%s] for task [%s]. Error: %s",
                                    extensibility.callbackReference.toString(), body.documentSelfLink, e.getMessage());
                            // TODO log to eventlogs
                            // TODO fail task or something?
                        } else {
                            getHost().schedule(() -> {
                                sendExternalNotification(extensibility, body, retriesLeft - 1);
                            }, NOTIFICATION_RETRY_WAIT, TimeUnit.SECONDS);
                        }
                    }
                }));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private <T extends TaskServiceDocument> ServiceDocument buildDataToSend(
            ExtensibilitySubscriptionCallback result, T taskToSend) {
        ExtensibilitySubscriptionCallback data = new ExtensibilitySubscriptionCallback();

        data.serviceCallback = UriUtils.buildUri(result.documentSelfLink);
        data.taskStateJson = Utils.toJson(taskToSend);

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

    private void addExtensibilitySubscription(ExtensibilitySubscription state) {
        logFine("Added extensibility [%s] with callback [%s]",
                state.documentSelfLink, state.callbackReference);
        subscriptions.put(state.documentSelfLink, state);
    }

    private void removeExtensibilitySubscription(String extensibilityLink) {
        logFine("Remove extensibility for [%s]", extensibilityLink);
        subscriptions.remove(extensibilityLink);
    }

    /**
     * Construct documentSelfLink of Task state by adding {@link ExtensibilitySubscriptionService}
     * FACTORY_LINK for prefix. For example: DummyServiceTaskState:FINISHED:COMPLETED will be
     * converted to: [/config/extensibility-subscriptions/DummyServiceTaskState:FINISHED:COMPLETED]
     */
    private <T extends TaskServiceDocument<?>> String constructKey(T state) {
        return String.format("%s/%s:%s:%s", ExtensibilitySubscriptionService.FACTORY_LINK,
                state.getClass().getSimpleName(),
                state.taskInfo.stage.name(), state.taskSubStage.name());
    }

    private ExtensibilitySubscription getExtensibilitySubscription(TaskServiceDocument<?> task) {
        return subscriptions.get(constructKey(task));
    }

}
