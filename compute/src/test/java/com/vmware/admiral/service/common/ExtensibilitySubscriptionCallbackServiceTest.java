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

import static java.time.temporal.ChronoUnit.SECONDS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionCallbackService.ExtensibilitySubscriptionCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;

public class ExtensibilitySubscriptionCallbackServiceTest extends BaseTestCase {

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        sender = host.getTestRequestSender();

        host.startFactory(ExtensibilitySubscriptionCallbackService.class.newInstance());
    }

    @Test
    public void testGetEmpty() throws InterruptedException {
        ServiceDocumentQueryResult result = sender.sendAndWait(
                Operation.createGet(host, ExtensibilitySubscriptionCallbackService.FACTORY_LINK),
                ServiceDocumentQueryResult.class);
        assertNotNull(result);
        assertNotNull(result.documentCount);
        assertEquals(0L, (long) result.documentCount);
    }

    @Test()
    public void testCreateCallback() {
        ExtensibilitySubscriptionCallback state = createExtensibilityCallback(
                ExtensibilitySubscriptionCallback.Status.BLOCKED);

        URI uri = UriUtils.buildUri(host, ExtensibilitySubscriptionCallbackService.FACTORY_LINK);
        ExtensibilitySubscriptionCallback result = sender.sendPostAndWait(uri, state,
                ExtensibilitySubscriptionCallback.class);

        assertNotNull(result);
        assertNotNull(result.documentSelfLink);
        assertNotNull(result.taskStateJson);
    }

    @Test(expected = RuntimeException.class)
    public void testPostValidation() {
        ExtensibilitySubscriptionCallback state = createExtensibilityCallback(
                ExtensibilitySubscriptionCallback.Status.DONE);

        URI uri = UriUtils.buildUri(host, ExtensibilitySubscriptionCallbackService.FACTORY_LINK);
        sender.sendPostAndWait(uri, state, ExtensibilitySubscriptionCallback.class);
        // Issue a second POST with status = DONE.
        sender.sendPostAndWait(uri, state, ExtensibilitySubscriptionCallback.class);
    }

    @Test()
    public void testPut() {
        ExtensibilitySubscriptionCallback state = createExtensibilityCallback(
                ExtensibilitySubscriptionCallback.Status.DONE);

        URI uri = UriUtils.buildUri(host, ExtensibilitySubscriptionCallbackService.FACTORY_LINK);
        Throwable[] exceptions = new IllegalArgumentException[1];

        TestContext context = new TestContext(1, Duration.ofSeconds(30));
        Operation.createPut(uri)
                .setReferer(host.getUri())
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        exceptions[0] = e;
                        context.completeIteration();
                        return;
                    }
                    context.fail(e);

                }).sendWith(host);
        ;

        context.await();
        assertNotNull(exceptions[0]);
        assertEquals("Action not supported", exceptions[0].getMessage());

    }

    @Test
    public void testTimeout() throws Throwable {
        ExtensibilitySubscriptionCallback state = createExtensibilityCallback(
                ExtensibilitySubscriptionCallback.Status.BLOCKED);

        state.due = LocalDateTime.now().plus(5, SECONDS);
        state.serviceTaskCallback = new ServiceTaskCallback();
        state.serviceTaskCallback.serviceSelfLink = TestStatelessService.SELF_LINK;
        state.replyPayload = new ServiceTaskCallbackResponse();

        DeferredResult<Void> done = new DeferredResult<>();
        this.host.startService(new TestStatelessService(done));
        this.host.waitForServiceAvailable(TestStatelessService.SELF_LINK);

        URI uri = UriUtils.buildUri(host, ExtensibilitySubscriptionCallbackService.FACTORY_LINK);
        sender.sendPostAndWait(uri, state, ExtensibilitySubscriptionCallback.class);

        this.waitFor("Task did not time out.", () -> done.isDone());
    }

    private ExtensibilitySubscriptionCallback createExtensibilityCallback(
            ExtensibilitySubscriptionCallback.Status status) {
        ExtensibilitySubscriptionCallback callback = new ExtensibilitySubscriptionCallback();
        callback.taskStateClassName = "taskStateClassName";
        callback.status = status;
        callback.taskStateJson = "taskStateJson";
        callback.documentSelfLink = UUID.randomUUID().toString();
        callback.due = LocalDateTime.now();
        return callback;
    }

    public static class TestStatelessService extends StatelessService {
        public static String SELF_LINK = TestStatelessService.class.getSimpleName();
        private DeferredResult<Void> done;

        public TestStatelessService(DeferredResult<Void> done) {
            this.done = done;
        }

        @Override
        public void handlePatch(Operation patch) {
            patch.complete();
            done.complete(null);
        }
    }

}
