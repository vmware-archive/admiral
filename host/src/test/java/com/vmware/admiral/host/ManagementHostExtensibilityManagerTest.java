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

package com.vmware.admiral.host;

import static com.vmware.xenon.common.CommandLineArgumentParser.ARGUMENT_ASSIGNMENT;
import static com.vmware.xenon.common.CommandLineArgumentParser.ARGUMENT_PREFIX;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.host.DummyService.DummyServiceTaskState;
import com.vmware.admiral.host.DummyService.DummyServiceTaskState.SubStage;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionFactoryService;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class ManagementHostExtensibilityManagerTest extends ManagementHostBaseTest {

    private ManagementHost host;

    private URI subscriptionUri;

    private static final TemporaryFolder SANDBOX = new TemporaryFolder();

    @Before
    public void setUp() throws Throwable {
        SANDBOX.create();
        List<String> args = new ArrayList<>(Arrays.asList(
                // generate a random sandbox
                ARGUMENT_PREFIX + "sandbox" + ARGUMENT_ASSIGNMENT + SANDBOX.getRoot().toPath()));
        host = createManagementHost(args.toArray(new String[args.size()]), true);
        subscriptionUri = UriUtils.buildUri(host,
                ExtensibilitySubscriptionFactoryService.SELF_LINK);
    }

    @After
    public void tearDown() throws Throwable {
        if (host != null && host.getStorageSandbox() != null) {
            host.stop();
        }
        SANDBOX.delete();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSubscriptionEmptyUrl() {

        ExtensibilitySubscription subscription = createExtensibilityState(
                // Subscription sub stage.
                DefaultSubStage.CREATED.name(),
                // URI to Subscriber which will change container's name.
                null,
                // Target task.When it is finished subscriber will start.
                DummyServiceTaskState.class.getSimpleName(),
                // Subscription stage.
                TaskStage.STARTED.name(),
                false);

        subscription = sendOperation(host, subscriptionUri,
                subscription, ExtensibilitySubscription.class, Action.POST);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSubscriptionEmptySubStage() {

        ExtensibilitySubscription subscription = createExtensibilityState(
                // Subscription sub stage.
                null,
                // URI to Subscriber which will change container's name.
                UriUtils.buildUri(host, DummySubscriber.SELF_LINK),
                // Target task.When it is finished subscriber will start.
                DummyServiceTaskState.class.getSimpleName(),
                // Subscription stage.
                TaskStage.STARTED.name(),
                false);

        subscription = sendOperation(host, subscriptionUri,
                subscription, ExtensibilitySubscription.class, Action.POST);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidSubscriptionEmptyStage() {

        ExtensibilitySubscription subscription = createExtensibilityState(
                // Subscription sub stage.
                DefaultSubStage.CREATED.name(),
                // URI to Subscriber which will change container's name.
                UriUtils.buildUri(host, DummySubscriber.SELF_LINK),
                // Target task.When it is finished subscriber will start.
                DummyServiceTaskState.class.getSimpleName(),
                // Subscription stage.
                null,
                false);

        subscription = sendOperation(host, subscriptionUri,
                subscription, ExtensibilitySubscription.class, Action.POST);
    }

    /**
     * Test asynchronous subscription when task completes.
     */
    @Test
    public void testSubscriptionOnCOMPLETEDSubStage() throws InterruptedException, IOException {

        ExtensibilitySubscription subscription = createExtensibilityState(
                // Subscription sub stage.
                DefaultSubStage.COMPLETED.name(),
                // URI to Subscriber which will change container's name.
                UriUtils.buildUri(host, DummySubscriber.SELF_LINK),
                // Target task.When it is finished subscriber will start.
                DummyServiceTaskState.class.getSimpleName(),
                // Subscription stage.
                TaskStage.FINISHED.name(),
                // Subscription will be async (won't block task).
                false);

        subscription = sendOperation(host, subscriptionUri,
                subscription, ExtensibilitySubscription.class, Action.POST);

        // Create container which name will be changed when DummyTaskState completes.
        ContainerState containerState = new ContainerState();
        containerState.name = "container-name";
        containerState.documentSelfLink = "dummy-container";
        containerState = sendOperation(host,
                UriUtils.buildUri(host, ContainerFactoryService.SELF_LINK), containerState,
                ContainerState.class, Action.POST);

        // Create customProperties which DummySubscriber will use to make a diff between blocking
        // and non-blocking.
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put("blocking", Boolean.TRUE.toString());

        // Create dummy task which will trigger subscriber when it is FINISHED(COMPLETED).
        DummyServiceTaskState dummyState = new DummyServiceTaskState();
        dummyState.containerState = containerState;
        dummyState.blocking = false;
        dummyState.customProperties = customProperties;
        URI dummyServiceUri = UriUtils.buildUri(host, DummyService.SELF_LINK);

        // Initialize CountDownWatch to wait for subscriber.
        TestContext context = new TestContext(1, Duration.ofSeconds(330));
        sendOperation(host, dummyServiceUri, dummyState,
                DummyServiceTaskState.class, Action.POST);

        // Wait for subscriber to change the above container's name.
        verifyContainerNameHasBeenUpdated(containerState.documentSelfLink, context);
        context.await();
    }

    @Test
    public void testBlockingSubscription() throws InterruptedException {

        ExtensibilitySubscription subscription = createExtensibilityState(
                // Subscription sub stage.
                DummyServiceTaskState.SubStage.FILTER.name(),
                // URI to Subscriber which will change container's name.
                UriUtils.buildUri(host, DummySubscriber.SELF_LINK),
                // Target task.When it is finished subscriber will start.
                DummyServiceTaskState.class.getSimpleName(),
                // Subscription stage.
                TaskStage.STARTED.name(),
                // Subscription will block the task until subscriber finished and notifies it.
                true);

        subscription = sendOperation(host, subscriptionUri,
                subscription, ExtensibilitySubscription.class, Action.POST);

        // Create dummy task which will trigger subscriber when it reaches STARTED(FILTER).
        DummyServiceTaskState dummyState = new DummyServiceTaskState();
        dummyState.documentSelfLink = "dummy-self-link";

        // This name will be patched once task is created and than from subscriber, prior FILTER
        // subStage is started.
        dummyState.name = "before-subscription";
        dummyState.blocking = true;

        URI dummyServiceUri = UriUtils.buildUri(host, DummyService.SELF_LINK);

        // Create task in order to track modification of its name across different stages.
        dummyState = sendOperation(host, dummyServiceUri, dummyState,
                DummyServiceTaskState.class, Action.POST);

        verifyDummyStateServiceCompletes(dummyState.documentSelfLink);

        DummyServiceTaskState completedDummyState = sendOperation(host,
                UriUtils.buildUri(host, dummyState.documentSelfLink),
                null, DummyServiceTaskState.class, Action.GET);

        Assert.assertNotNull(completedDummyState.name);
        Assert.assertNotEquals(dummyState.name, completedDummyState.name);

        // TODO when tests are executed in parallel one thread can change DummyServiceTaskState.name
        // after subscriber.
        // Assert.assertEquals(DummySubscriber.SELF_LINK, completedDummyState.name);

    }

    private void verifyContainerNameHasBeenUpdated(String containerLink, TestContext context) {

        // Get container and verify that its name has been updated by subscriber.
        ContainerState containerState = sendOperation(host,
                UriUtils.buildUri(host, containerLink),
                null, ContainerState.class, Action.GET);

        // If subscriber doesn't update container's name within 30 seconds, context will expire.
        while (!containerState.name.equals(DummySubscriber.class.getSimpleName())) {
            containerState = sendOperation(host,
                    UriUtils.buildUri(host, containerLink),
                    null, ContainerState.class, Action.GET);
        }
        context.completeIteration();
    }

    private void verifyDummyStateServiceCompletes(String dummyTaskSelfLink)
            throws InterruptedException {
        TestContext context = new TestContext(1, Duration.ofSeconds(320));
        // Get container and verify that its name has been updated by subscriber.
        DummyServiceTaskState dummyState = sendOperation(host,
                UriUtils.buildUri(host, dummyTaskSelfLink),
                null, DummyServiceTaskState.class, Action.GET);

        while (dummyState.taskInfo.stage != TaskState.TaskStage.FINISHED
                && dummyState.taskSubStage != SubStage.COMPLETED) {
            host.log(Level.WARNING, "Not ready yet!");
            Thread.sleep(3000L);
            dummyState = sendOperation(host,
                    UriUtils.buildUri(host, dummyTaskSelfLink),
                    null, DummyServiceTaskState.class, Action.GET);
        }
        context.completeIteration();
    }

    private ExtensibilitySubscription createExtensibilityState(String substage, URI uri,
            String task, String stage, boolean blocking) {

        ExtensibilitySubscription state = new ExtensibilitySubscription();
        state.task = task;
        state.stage = stage;
        state.substage = substage;
        state.callbackReference = uri;
        state.blocking = blocking;
        return state;
    }

}
