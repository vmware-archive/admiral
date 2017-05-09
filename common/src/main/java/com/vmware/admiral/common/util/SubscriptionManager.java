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

package com.vmware.admiral.common.util;

import java.io.Closeable;
import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.common.util.SubscriptionManager.SubscriptionNotification.NotificationOperation;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceSubscriptionState.ServiceSubscriber;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ReliableSubscriptionService;

/**
 * A helper class to extract and manage the common service subscription operations. This class
 * implements subscription polling strategy that if enabled will switch from notification to polling
 * for notification.
 */
public class SubscriptionManager<T extends ServiceDocument> implements Closeable {
    private static final boolean SUBSCRIPTION_POLLING_STRATEGY = Boolean
            .getBoolean("dcp.management.subscription.remote.polling.enabled");
    private static final long DEFAULT_SUBSCRIPTION_POLLING_PERIOD_MILLIS = Long.getLong(
            "dcp.management.subscription.default.polling.period.millis",
            TimeUnit.SECONDS.toMillis(60));

    private final ServiceHost host;
    private final String subscribeForServiceLink;
    private final boolean subscribeForNotifications;
    private final ServiceDocumentQuery<T> documentQuery;
    private final String uniqueSubscriptionId;
    private final Class<T> type;
    private volatile boolean stopPolling;
    private volatile long schedulingPeriodInMillis = DEFAULT_SUBSCRIPTION_POLLING_PERIOD_MILLIS;
    private volatile String subscriptionLink;

    /* Last time the document was update in microseconds since UNIX epoch */
    private volatile long documentUpdateTimeMicros;

    /* Optional subscribe/unsubscribe handler to handle if any exceptions */
    private Consumer<Throwable> completionHandler;

    public SubscriptionManager(ServiceHost host, String uniqueSubscriptionId,
            String subscribeForServiceLink, Class<T> type) {
        this(host, uniqueSubscriptionId, subscribeForServiceLink, type, false);
    }

    public SubscriptionManager(ServiceHost host, String uniqueSubscriptionId,
            String subscribeForServiceLink, Class<T> type, boolean useSubscriptionPollingStrategy) {
        AssertUtil.assertNotNull(host, "serviceHost");
        AssertUtil.assertNotEmpty(subscribeForServiceLink, "subscribeForServiceLink");
        AssertUtil.assertNotNull(type, "type");
        AssertUtil.assertNotNull(uniqueSubscriptionId, "uniqueSubscriptionId");
        this.host = host;
        this.type = type;
        this.uniqueSubscriptionId = uniqueSubscriptionId;
        this.subscribeForServiceLink = subscribeForServiceLink;
        this.subscribeForNotifications = useSubscriptionPollingStrategy ?
                !SUBSCRIPTION_POLLING_STRATEGY : true;
        this.documentQuery = new ServiceDocumentQuery<T>(host, type);
    }

    public boolean isSubscribed() {
        return this.subscriptionLink != null;
    }

    public String getSubscriptionLink() {
        return this.subscriptionLink;
    }

    /**
     * The scheduling period in milliseconds. Default is DEFAULT_SUBSCRIPTION_POLLING_PERIOD_MILLIS.
     * This property is only used in case the <code>useSubscriptionPollingStrategy</code> is set to
     * true.
     */
    public SubscriptionManager<T> setSchedulingPeriodInMillis(long schedulingPeriodInMillis) {
        this.schedulingPeriodInMillis = schedulingPeriodInMillis;
        return this;
    }

    /**
     * Set completion handler that it is called during startup and close of the subscription
     * manager. The handler will be called in both cases - success or error. The parameter supplied
     * will be Throwable in case of exception and null in case of success.
     */
    public SubscriptionManager<T> setCompletionHandler(Consumer<Throwable> completionHandler) {
        this.completionHandler = completionHandler;
        return this;
    }

    /**
     * Set the subscription link in case this is a new instance of already active subscription
     */
    public SubscriptionManager<T> setSubscriptionLink(String subscriptionLink) {
        this.subscriptionLink = subscriptionLink;
        return this;
    }

    /**
     * Close the resources and unsubscribe.
     */
    @Override
    public void close() {
        unsubscribe();
    }

    /**
     * Subscribe for the specified service link and provide a notification handler to be callback
     * when there is an update or delete of the specified services.
     *
     * The Consumer<String> callback will contain either the subscription ID or null in case
     * something went wrong.
     */
    public void start(Consumer<SubscriptionNotification<T>> notificationHandler,
            Consumer<String> callback) {
        start(notificationHandler, false, callback);
    }

    public void start(Consumer<SubscriptionNotification<T>> notificationHandler,
            boolean replayState, Consumer<String> callback) {
        if (!subscribeForNotifications) {
            documentUpdateTimeMicros = Utils.getNowMicrosUtc();
            schedulePolling(notificationHandler);
            if (completionHandler != null) {
                completionHandler.accept(null);
            }
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        CountDownLatch countDown = new CountDownLatch(1);

        host.registerForServiceAvailability((o, e) -> {
            if (e != null) {
                host.log(Level.SEVERE, "Error waiting for service: %s. Error: %s",
                        subscribeForServiceLink, Utils.toString(e));
            }
            countDown.countDown();

        }, subscribeForServiceLink);

        boolean waited;
        try {
            waited = countDown.await(10, TimeUnit.SECONDS);
            if (!waited) {
                host.log(Level.WARNING, "Waiting for subscription timed out: %s",
                        subscribeForServiceLink);
            }
        } catch (InterruptedException ex) {
            host.log(Level.WARNING, "Thread interrupted: %s", Utils.toString(ex));
        }

        Operation subscribe = Operation
                .createPost(UriUtils.buildSubscriptionUri(host, subscribeForServiceLink))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_SKIPPED_NOTIFICATIONS)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        Utils.logWarning("Error subscribing for: %s. Error: %s",
                                subscribeForServiceLink, Utils.toString(e));
                    }

                    if (completionHandler != null) {
                        // the handler should be called in both cases success or error
                        completionHandler.accept(e);
                    }
                });

        boolean usePublicUri = false;
        ServiceSubscriber sr = ServiceSubscriber.create(replayState).setUsePublicUri(usePublicUri);
        StatelessService notificationTarget = ReliableSubscriptionService.create(subscribe, sr,
                (op) -> handleNotification(op, notificationHandler));

        // make sure the subscription is idempotent using the id of the service to subscribe for
        notificationTarget.setSelfLink(UriUtils.buildUriPath("subscriptions",
                uniqueSubscriptionId, "resource",
                Service.getId(subscribeForServiceLink)));

        Operation.createDelete(host, notificationTarget.getSelfLink())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        Utils.logWarning("Error while stopping subscription service %s",
                                Utils.toString(ex));
                    }
                    host.startSubscriptionService(subscribe, notificationTarget, sr);
                    this.subscriptionLink = notificationTarget.getSelfLink();
                    if (callback != null) {
                        callback.accept(subscriptionLink);
                    }
                }).sendWith(host);
    }

    private void schedulePolling(Consumer<SubscriptionNotification<T>> notificationHandler) {
        if (stopPolling || !host.isStarted()) {
            return;
        }

        host.schedule(() -> {
            try {
                poll(notificationHandler);
            } catch (Throwable e) {
                handlePollingException(notificationHandler, e);
            }
        }, schedulingPeriodInMillis, TimeUnit.MILLISECONDS);
    }

    private void poll(Consumer<SubscriptionNotification<T>> notificationHandler) {
        long currentDocumentUpdateTimeMicros = Utils.getNowMicrosUtc();
        documentQuery
                .queryUpdatedDocumentSince(
                        documentUpdateTimeMicros,
                        subscribeForServiceLink,
                        (r) -> {
                            try {
                                if (stopPolling) {
                                    return;
                                }
                                if (r.hasException()) {
                                    r.throwRunTimeException();
                                } else if (r.hasResult()) {
                                    SubscriptionNotification<T> notification = new SubscriptionNotification<>();
                                    notification.result = r.getResult();
                                    notification.operation = ServiceDocument.isDeleted(r
                                            .getResult()) ? NotificationOperation.DELETE
                                            : NotificationOperation.UPDATE;
                                    Utils.log(getClass(), r.getResult().documentSelfLink,
                                            Level.INFO,
                                            "Notification received for action: [%s]",
                                            notification.operation);
                                    notificationHandler.accept(notification);
                                    documentUpdateTimeMicros = currentDocumentUpdateTimeMicros;
                                }

                                schedulePolling(notificationHandler);
                                schedulingPeriodInMillis = DEFAULT_SUBSCRIPTION_POLLING_PERIOD_MILLIS;
                            } catch (Throwable t) {
                                handlePollingException(notificationHandler, t);
                            }
                        });
    }

    private void handlePollingException(Consumer<SubscriptionNotification<T>> notificationHandler,
            Throwable e) {
        if (e instanceof CancellationException) {
            Utils.logWarning(
                    "Cancellation error scheduling a polling job for resource notifications for: %s",
                    subscribeForServiceLink);
            return;
        }
        Utils.logWarning(
                "Error scheduling a polling job for resource notifications for: %s. Error: %s",
                subscribeForServiceLink, Utils.toString(e));

        // Increase the period on exception to slow down the polling (prevent filling logs and so
        // on). Once a successful completion is done, the period will be reset to default
        if (30 * DEFAULT_SUBSCRIPTION_POLLING_PERIOD_MILLIS > schedulingPeriodInMillis) {
            schedulingPeriodInMillis += schedulingPeriodInMillis;
            Utils.logWarning(
                    "Increasing the scheduled period time to %s milliseconds on error for resource subscription: %s",
                    schedulingPeriodInMillis, subscribeForServiceLink);
        }
        schedulePolling(notificationHandler);
    }

    private void unsubscribe() {
        stopPolling = true;
        if (!isSubscribed()) {
            Utils.logWarning("No subscription link to unsubscribe for service: %s",
                    this.subscribeForServiceLink);
            if (completionHandler != null) {
                completionHandler.accept(null);
            }
            return;
        }

        Operation unSubscribe = Operation.createDelete(buildSubscribeForUri())
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        Utils.logWarning("Error unsubscribing from: %s. Error: %s",
                                o.getUri(), Utils.toString(e));
                    }

                    if (completionHandler != null) {
                        // the completion handler should be called in both cases: error or success:
                        completionHandler.accept(e);
                    }
                });

        host.stopSubscriptionService(unSubscribe, UriUtils.buildUri(host, subscriptionLink));
    }

    private URI buildSubscribeForUri() {
        return UriUtils.buildSubscriptionUri(host, subscribeForServiceLink);
    }

    private void handleNotification(Operation op,
            Consumer<SubscriptionNotification<T>> notificationHandler) {
        try {
            host.log(Level.INFO, "Notification received for action: [%s] and uri: [%s]",
                    op.getAction(), op.getUri());
            SubscriptionNotification<T> notification = new SubscriptionNotification<>();

            if (Action.DELETE == op.getAction()) {
                if (!op.hasBody()) { // service stopped. no changes to the state.
                    op.complete();
                    return;
                } else {
                    notification.operation = NotificationOperation.DELETE;
                    // the subscription is already deleted with the deletion of the document
                    // just reset the subscription link.
                    this.subscriptionLink = null;
                }
            } else {
                notification.operation = NotificationOperation.UPDATE;
            }

            if (op.hasBody()) {
                notification.result = op.getBody(this.type);
            }
            notificationHandler.accept(notification);
            op.complete();
        } catch (Throwable e) {
            Utils.logWarning("Error handling notifications. Error: %s", Utils.toString(e));
            op.fail(e);
        }
    }

    public static class SubscriptionNotification<T extends com.vmware.xenon.common.ServiceDocument> {
        public static enum NotificationOperation {
            UPDATE, DELETE;
        }

        private NotificationOperation operation;
        private T result;

        public boolean isUpdate() {
            return NotificationOperation.UPDATE == operation;
        }

        public boolean isDelete() {
            return NotificationOperation.DELETE == operation;
        }

        public T getResult() {
            return result;
        }
    }
}
