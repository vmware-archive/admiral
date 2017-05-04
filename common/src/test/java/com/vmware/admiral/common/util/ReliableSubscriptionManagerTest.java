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

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.SubscriptionManager.SubscriptionNotification;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.ExampleService.ExampleServiceState;
import com.vmware.xenon.services.common.NodeGroupService.NodeGroupConfig;

public class ReliableSubscriptionManagerTest {

    @FunctionalInterface
    public static interface TestWaitForHandler {
        boolean test() throws Throwable;
    }

    private static final int WAIT_FOR_OPERATION_RESPONSE = Integer.getInteger(
            "dcp.management.test.wait.operation.response.seconds", 30);
    private static final long SUBSCRIPTION_START_TIMEOUT_SECONDS = Integer.getInteger(
            "dcp.management.test.subscription.start.timeout.seconds", 10);
    private static final long NOTIFICATION_TIMEOUT_SECONDS = Integer.getInteger(
            "dcp.management.test.notification.timeout.seconds", 10);

    private static final String INITIAL_NAME_FIELD_VALUE = "initial-name";
    private static final String PATCHED_NAME_FIELD_VALUE = "patched-name";
    private static final int CLUSTER_NODES = 3;

    private ExampleServiceState exampleState;
    private VerificationHost cluster;
    private VerificationHost peerHost;

    @Before
    public void setUp() throws Throwable {
        // create and start 3 verification hosts
        cluster = VerificationHost.create();
        ServiceHost.Arguments args = VerificationHost.buildDefaultServiceHostArguments(0);
        VerificationHost.initialize(cluster, args);
        cluster.start();
        cluster.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(100));

        // join nodes
        cluster.setUpPeerHosts(CLUSTER_NODES);
        cluster.joinNodesAndVerifyConvergence(3);
        cluster.setNodeGroupQuorum(2);

        // peer used to send posts.
        peerHost = cluster.getPeerHost();

        // wait for replicated factory availability
        cluster.waitForReplicatedFactoryServiceAvailable(UriUtils.buildUri(peerHost,
                ExampleService.FACTORY_LINK));

        // create example service
        exampleState = createExampleServiceState();
    }

    @After
    public void tearDown() {
        if (cluster != null) {
            cluster.tearDown();
            cluster.tearDownInProcessPeers();
        }
    }

    @Test
    public void testReliableSubscriptionsAfterDocumentOwnerChange() throws Throwable {
        final int runningHosts = cluster.getPeerCount() - 1;
        AtomicInteger counter = new AtomicInteger();
        cluster.getInProcessHostMap().values().forEach((h) -> {
            subscribe(h, this.exampleState, (notification) -> {
                String documentLink = notification.getResult().documentSelfLink;
                if (notification.isDelete()) {
                    cluster.log(Level.INFO,
                            String.format(
                                    "Delete notification received for %s. Ignoring (waiting for updates).",
                                    documentLink));
                }
                if (notification.isUpdate()) {
                    cluster.log(Level.INFO, String.format(
                            "Update notification received for %s. Counting down.", documentLink));
                    counter.incrementAndGet();
                }
            });
        });

        // stop document owner host, wait for and verify ownership change of the document
        String oldOwner = this.exampleState.documentOwner;
        stopOwnerNode(this.exampleState);
        waitForOwnerChange(oldOwner, this.exampleState.documentSelfLink);

        this.exampleState = getDocument(exampleState.documentSelfLink);
        Assert.assertNotEquals(oldOwner, this.exampleState.documentOwner);
        this.cluster.log(Level.INFO, "New owner is %s", this.exampleState.documentOwner);

        this.exampleState = patchDocument(this.exampleState.documentSelfLink);

        TestContext waiter = new TestContext(1,
                Duration.ofSeconds(NOTIFICATION_TIMEOUT_SECONDS));
        waitFor(() -> {
            cluster.log("Waiting for notifications, currently got: %d", counter.get());
            if (counter.get() == runningHosts) {
                return true;
            }
            return false;
        }, waiter);
    }

    private ExampleServiceState createExampleServiceState() {
        ExampleServiceState body = new ExampleServiceState();
        body.name = INITIAL_NAME_FIELD_VALUE;

        // This single element array is used to extract the result from lambda.
        final ExampleServiceState[] postResult = new ExampleServiceState[1];
        TestContext ctx = new TestContext(1, Duration.ofSeconds(WAIT_FOR_OPERATION_RESPONSE));
        Operation.createPost(this.peerHost, ExampleService.FACTORY_LINK)
                .setBody(body)
                .setReferer(cluster.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                    } else {
                        postResult[0] = o.getBody(ExampleServiceState.class);
                        assertEquals(INITIAL_NAME_FIELD_VALUE, postResult[0].name);
                        ctx.completeIteration();
                    }
                }).sendWith(cluster);

        ctx.await();
        return postResult[0];
    }

    private ExampleServiceState patchDocument(String serviceLink) {
        ExampleServiceState patchBody = new ExampleServiceState();
        patchBody.name = PATCHED_NAME_FIELD_VALUE;

        // This single element array is used to extract the result from lambda.
        final ExampleServiceState[] patchResult = new ExampleServiceState[1];
        TestContext ctx = new TestContext(1, Duration.ofSeconds(WAIT_FOR_OPERATION_RESPONSE));
        Operation.createPatch(this.peerHost, serviceLink)
                .setBody(patchBody)
                .setReferer(cluster.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                    } else {
                        patchResult[0] = o.getBody(ExampleServiceState.class);
                        assertEquals(PATCHED_NAME_FIELD_VALUE, patchResult[0].name);
                        ctx.completeIteration();
                    }
                }).sendWith(cluster);
        ctx.await();
        return getDocument(serviceLink);
    }

    private ExampleServiceState getDocument(String serviceLink) {

        // This single element array is used to extract the result from lambda.
        final ExampleServiceState[] getResult = new ExampleServiceState[1];
        TestContext ctx = new TestContext(1, Duration.ofSeconds(WAIT_FOR_OPERATION_RESPONSE));
        Operation.createGet(this.peerHost, serviceLink)
                .setReferer(cluster.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                    } else {
                        getResult[0] = o.getBody(ExampleServiceState.class);
                        ctx.completeIteration();
                    }
                }).sendWith(cluster);
        ctx.await();
        return getResult[0];
    }

    private ExampleServiceState getDocument(ServiceHost onHost, String serviceLink) {

        // This single element array is used to extract the result from lambda.
        final ExampleServiceState[] getResult = new ExampleServiceState[1];
        TestContext ctx = new TestContext(1, Duration.ofSeconds(WAIT_FOR_OPERATION_RESPONSE));
        Operation.createGet(onHost, serviceLink)
                .setReferer(onHost.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                    } else {
                        getResult[0] = o.getBody(ExampleServiceState.class);
                        ctx.completeIteration();
                    }
                }).sendWith(cluster);
        ctx.await();
        return getResult[0];
    }

    private void stopOwnerNode(ExampleServiceState serviceState) {
        // find owner and stop it
        VerificationHost owner = null;
        for (VerificationHost h : this.cluster.getInProcessHostMap().values()) {
            if (!h.getId().equals(serviceState.documentOwner)) {
                peerHost = h;
            } else {
                owner = h;
            }
        }

        owner.log(Level.INFO, "Stopping owner node %s...", owner.getId());

        // Patching the NodeGroupConfig so the stopped node get "kicked" from the group faster.
        NodeGroupConfig cfg = new NodeGroupConfig();
        cfg.nodeRemovalDelayMicros = TimeUnit.SECONDS.toMicros(2);
        this.cluster.setNodeGroupConfig(cfg);
        this.cluster.stopHost(owner);

        cluster.waitForReplicatedFactoryServiceAvailable(UriUtils.buildUri(peerHost,
                ExampleService.FACTORY_LINK));

    }

    private void waitForOwnerChange(String oldOwner, String serviceLink) throws Throwable {
        for (ServiceHost h : this.cluster.getInProcessHostMap().values()) {
            waitForOwnerChangeOnHost(h, oldOwner, serviceLink);
        }
    }

    private void waitForOwnerChangeOnHost(ServiceHost host, String oldOwner, String serviceLink)
            throws Throwable {
        String error = String.format("Failed waiting for ownership change on host %s",
                host.getId());
        waitFor(error, () -> {
            return !getDocument(host, serviceLink).documentOwner.equals(oldOwner);
        });
    }

    private SubscriptionManager<ExampleServiceState> subscribe(ServiceHost h,
            ExampleServiceState serviceState,
            Consumer<SubscriptionNotification<ExampleServiceState>> notificationConsumer) {
        SubscriptionManager<ExampleServiceState> subscriptionManager = new SubscriptionManager<>(
                h, h.getId(), serviceState.documentSelfLink, ExampleServiceState.class);

        TestContext ctx = new TestContext(1,
                Duration.ofSeconds(SUBSCRIPTION_START_TIMEOUT_SECONDS));
        String subscriptionServiceLink;
        // This single element array is used to extract the result from lambda.
        final String[] subscriptionServiceLinkResult = new String[1];
        subscriptionManager.start(notificationConsumer, (subscriptionLink) -> {
            subscriptionServiceLinkResult[0] = subscriptionLink;
            ctx.completeIteration();
        });
        ctx.await();
        subscriptionServiceLink = subscriptionServiceLinkResult[0];

        cluster.log(Level.INFO, "Waiting for subscription for %s on host %s",
                serviceState.documentSelfLink, cluster.getId());
        TestContext testCtx = new TestContext(1,
                Duration.ofSeconds(SUBSCRIPTION_START_TIMEOUT_SECONDS));
        h.registerForServiceAvailability((o, e) -> {
            if (e != null) {
                testCtx.fail(e);
            } else {
                testCtx.complete();
            }
        }, subscriptionServiceLink);
        testCtx.await();
        return subscriptionManager;
    }

    private void waitFor(TestWaitForHandler handler, TestContext context)
            throws Throwable {
        if (!handler.test()) {
            cluster.schedule(() -> {
                try {
                    waitFor(handler, context);
                } catch (Throwable throwable) {
                }
            }, 1, TimeUnit.SECONDS);
        } else {
            context.completeIteration();
        }
    }

    private void waitFor(String errorMessage, TestWaitForHandler handler) throws Throwable {
        TestContext context = new TestContext(1, Duration.ofMinutes(1));
        waitFor(handler, context);
        try {
            context.await();
        } catch (Exception e) {
            throw new Exception(errorMessage, e);
        }
    }

}
