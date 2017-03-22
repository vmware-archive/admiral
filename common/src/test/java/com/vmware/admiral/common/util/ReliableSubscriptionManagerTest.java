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
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.SubscriptionManager.SubscriptionNotification;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestNodeGroupManager;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.ExampleService.ExampleServiceState;

public class ReliableSubscriptionManagerTest {

    @FunctionalInterface
    public static interface TestWaitForHandler {
        boolean test() throws Throwable;
    }

    private static final int WAIT_FOR_STATE_CHANGE_COUNT = Integer.getInteger(
            "dcp.management.test.change.count", 2500);
    private static final int WAIT_THREAD_SLEEP_IN_MILLIS = Integer.getInteger(
            "dcp.management.test.wait.thread.sleep.millis", 20);
    private static final long SUBSCRIPTION_START_TIMEOUT_SECONDS = 10;
    private static final long NOTIFICATION_TIMEOUT_SECONDS = 10;

    private static final String INITIAL_NAME_FIELD_VALUE = "initial-name";
    private static final String PATCHED_NAME_FIELD_VALUE = "patched-name";

    private ServiceHost peer;
    private TestRequestSender sender;
    private TestNodeGroupManager nodeGroup;
    private ExampleServiceState exampleState;

    @Before
    public void setup() throws Throwable {
        this.nodeGroup = new TestNodeGroupManager();

        // create and start 3 verification hosts
        for (int i = 0; i < 3; i++) {
            VerificationHost host = createAndStartHost();
            this.nodeGroup.addHost(host);
        }
        // and join nodes
        this.nodeGroup.joinNodeGroupAndWaitForConvergence();
        this.nodeGroup.setTimeout(Duration.ofSeconds(30));
        this.nodeGroup.updateQuorum(2);

        // wait for factory availability
        this.nodeGroup.waitForFactoryServiceAvailable(ExampleService.FACTORY_LINK);

        // choose random host as sender
        this.peer = this.nodeGroup.getHost();
        this.sender = new TestRequestSender(this.peer);

        // create example service
        this.exampleState = createExampleServiceState();
    }

    @After
    public void tearDown() {
        if (this.nodeGroup != null) {
            for (ServiceHost host : this.nodeGroup.getAllHosts()) {
                ((VerificationHost) host).tearDown();
            }
            this.nodeGroup = null;
        }
    }

    @Test
    public void testReliableSubscriptionsAfterDOcumentOwnerChange() throws Throwable {
        // subscribe for service updates
        final int runningHosts = nodeGroup.getAllHosts().size() - 1;
        this.nodeGroup.getHost().log(Level.INFO, "Subscribing...");
        CountDownLatch remainingNotifications = new CountDownLatch(runningHosts);
        this.nodeGroup.getAllHosts().forEach((host) -> {
            // subscription managers usually should be closed, but the all the hosts will be torn
            // down after the test execution completes
            subscribe(host, this.exampleState, (notification) -> {
                String documentLink = notification.getResult().documentSelfLink;
                if (notification.isDelete()) {
                    host.log(Level.INFO,
                            String.format(
                                    "Delete notification received for %s. Ignoring (waiting for updates).",
                                    documentLink));
                }
                if (notification.isUpdate()) {
                    host.log(Level.INFO, String.format(
                            "Update notification received for %s. Counting down.", documentLink));
                    remainingNotifications.countDown();
                }
            });
        });

        // stop document owner host, wait for and verify ownership change of the document
        String oldOwner = this.exampleState.documentOwner;
        stopOwnerNode(this.exampleState);
        waitForOwnerChange(oldOwner, this.exampleState.documentSelfLink);

        this.exampleState = getDocument(exampleState.documentSelfLink);
        Assert.assertNotEquals(oldOwner, this.exampleState.documentOwner);
        this.nodeGroup.getHost().log(Level.INFO, "New owner is %s",
                this.exampleState.documentOwner);

        // patch the service
        this.nodeGroup.getHost().log(Level.INFO,
                "Patching document. Will wait for %d notifications", runningHosts);
        this.exampleState = patchDocument(this.exampleState.documentSelfLink);

        // wait for all notifications. If the subscriptions are reliable, all notifications should
        // arrive even after the ownership change for the service
        boolean waited = remainingNotifications.await(NOTIFICATION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
        if (!waited) {
            Assert.fail(String.format("Failed to wait for %d more update notification(s)",
                    remainingNotifications.getCount()));
        }
        this.nodeGroup.getHost().log(Level.INFO, "All notifications received! Test successful.");
    }

    private VerificationHost createAndStartHost() throws Throwable {
        VerificationHost host = VerificationHost.create(0);
        host.start();

        return host;
    }

    private ExampleServiceState createExampleServiceState() {
        ExampleServiceState body = new ExampleServiceState();
        body.name = INITIAL_NAME_FIELD_VALUE;
        Operation post = Operation.createPost(this.peer, ExampleService.FACTORY_LINK).setBody(body);
        ExampleServiceState postResult = this.sender.sendAndWait(post, ExampleServiceState.class);
        assertEquals(INITIAL_NAME_FIELD_VALUE, postResult.name);
        return postResult;
    }

    private ExampleServiceState patchDocument(String serviceLink) {
        ExampleServiceState patchBody = new ExampleServiceState();
        patchBody.name = PATCHED_NAME_FIELD_VALUE;
        Operation patch = Operation.createPatch(this.peer, serviceLink).setBody(patchBody);
        ExampleServiceState patchResult = this.sender.sendAndWait(patch, ExampleServiceState.class);
        assertEquals(PATCHED_NAME_FIELD_VALUE, patchResult.name);
        return getDocument(serviceLink);
    }

    private ExampleServiceState getDocument(String serviceLink) {
        return getDocument(this.peer, this.sender, serviceLink);
    }

    private ExampleServiceState getDocument(ServiceHost onHost, String serviceLink) {
        return getDocument(onHost, this.sender, serviceLink);
    }

    private ExampleServiceState getDocument(ServiceHost onHost, TestRequestSender withSender,
            String serviceLink) {
        Operation getAfterPatch = Operation.createGet(onHost, serviceLink);
        return withSender.sendAndWait(getAfterPatch, ExampleServiceState.class);
    }

    private void stopOwnerNode(ExampleServiceState serviceState) {
        // find owner and stop it
        String serviceLink = serviceState.documentSelfLink;
        VerificationHost owner = (VerificationHost) this.nodeGroup.getAllHosts().stream()
                .filter(host -> host.getId().contentEquals(serviceState.documentOwner))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("couldn't find owner node"));
        owner.log(Level.INFO, "Stopping owner node %s...", owner.getId());
        owner.tearDown();
        this.nodeGroup.removeHost(owner);

        // get remaining peers
        List<VerificationHost> availablePeers = this.nodeGroup.getAllHosts().stream()
                .filter(host -> !host.getId().contentEquals(owner.getId()))
                .map(host -> (VerificationHost) host)
                .collect(Collectors.toList());

        // use one for sender
        this.peer = availablePeers.get(0);
        this.sender = new TestRequestSender(this.peer);

        this.nodeGroup.waitForConvergence();
        availablePeers.forEach(p -> p.waitForServiceAvailable(serviceLink));
    }

    private void waitForOwnerChange(String oldOwner, String serviceLink) throws Throwable {
        for (ServiceHost host : this.nodeGroup.getAllHosts()) {
            waitForOwnerChangeOnHost(host, oldOwner, serviceLink);
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

    private SubscriptionManager<ExampleServiceState> subscribe(ServiceHost host,
            ExampleServiceState serviceState,
            Consumer<SubscriptionNotification<ExampleServiceState>> notificationConsumer) {
        SubscriptionManager<ExampleServiceState> subscriptionManager = new SubscriptionManager<>(
                host, host.getId(), serviceState.documentSelfLink, ExampleServiceState.class);
        String subscriptionServiceLink = subscriptionManager.start(notificationConsumer);

        host.log(Level.INFO, "Waiting for subscription for %s on host %s",
                serviceState.documentSelfLink, host.getId());
        TestContext testCtx = new TestContext(1,
                Duration.ofSeconds(SUBSCRIPTION_START_TIMEOUT_SECONDS));
        host.registerForServiceAvailability((o, e) -> {
            if (e != null) {
                testCtx.fail(e);
            } else {
                testCtx.complete();
            }
        }, subscriptionServiceLink);
        testCtx.await();
        return subscriptionManager;
    }

    private static void waitFor(String errorMessage, TestWaitForHandler handler)
            throws Throwable {
        int iterationCount = WAIT_FOR_STATE_CHANGE_COUNT;
        Thread.sleep(WAIT_THREAD_SLEEP_IN_MILLIS / 5);
        for (int i = 0; i < iterationCount; i++) {
            if (handler.test()) {
                return;
            }
            Thread.sleep(WAIT_THREAD_SLEEP_IN_MILLIS);
        }
        fail(errorMessage);
    }

}
