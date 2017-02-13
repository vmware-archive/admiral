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
import java.util.concurrent.atomic.AtomicBoolean;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.SubscriptionManager;
import com.vmware.admiral.common.util.SubscriptionManager.SubscriptionNotification;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * LifecycleExtensibilityManager enables clients to subscribe and receive notification when a task
 * service reaches desired stage and substage.
 * <p>
 * Notifications can be asynchronous or synchronous (blocking). The first are sent and the task
 * proceeds with its execution. The latter block further task execution and wait callback to be
 * received.
 */
public class ExtensibilitySubscriptionManager extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.EXTENSIBILITY_MANAGER;

    // internal map of the registered extensibility subscriptions
    private final Map<String, ExtensibilitySubscription> extensions = new ConcurrentHashMap<>();

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
                        logInfo("Loaded %d extensibility states", extensions.size());
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
     * @param service            task service
     * @param extensibilityState extensibility state
     * @param state              task state to send
     */
    @SuppressWarnings({ "rawtypes", "unused" })
    private <T extends TaskServiceDocument> void sendAsyncNotificationCall(
            AbstractTaskStatefulService service, ExtensibilitySubscription extensibilityState,
            T state) {
    }

    /**
     * Sends notification to subscriber. Client should post to the callback state to resume the task
     * execution.
     *
     * @param service            task service
     * @param extensibilityState extensibility state
     * @param state              task state to send
     */
    @SuppressWarnings({ "rawtypes", "unused" })
    private <T extends TaskServiceDocument> void sendBlockingNotificationCall(
            AbstractTaskStatefulService service,
            ExtensibilitySubscription extensibilityState, T state) {
    }

    /**
     * Sends a service document to external url. Supports retry in case of an
     * error and if task service is provided this method will call failTask
     * when no more retries left.
     *
     * @param url         address to send notification to
     * @param body        document to send
     * @param retriesLeft number of retries left before give up
     * @param taskService task service
     */
    @SuppressWarnings({ "rawtypes", "unused" })
    private void sendExternalNotification(String url, ServiceDocument body,
            int retriesLeft, AbstractTaskStatefulService taskService) {
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
                                .addPragmaDirective(Operation
                                        .PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
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
        extensions.put(state.documentSelfLink, state);
    }

    private void removeExtensibilitySubscription(String extensibilityLink) {
        logFine("Remove extensibility for [%s]", extensibilityLink);

        extensions.remove(extensibilityLink);
    }

}
