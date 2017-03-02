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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.time.Duration;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.service.common.EventTopicService.EventTopicState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.TestRequestSender.FailureResponse;

public class EventRegistryTopicServiceTest extends BaseTestCase {

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        sender = host.getTestRequestSender();
        // Start the example service factory

        host.startFactory(new EventTopicService());

        waitForServiceAvailability(EventTopicService.FACTORY_LINK);

        host.registerForServiceAvailability(EventTopicRegistrationBootstrapService.startTask(host),
                true,
                EventTopicRegistrationBootstrapService.FACTORY_LINK);

        host.startFactory(new EventTopicRegistrationBootstrapService());
        // host.startFactory(EventTopicBootstrapService.class.newInstance());

        waitForServiceAvailability(EventTopicRegistrationBootstrapService.FACTORY_LINK);

        sender.sendPostAndWait(
                UriUtils.buildUri(host, EventTopicRegistrationBootstrapService.FACTORY_LINK),
                new ServiceDocument(), ServiceDocument.class);

    }

    @Test
    public void testCreateEventRegistryTopic() {
        EventTopicState state = createEventTopicState("DummyTask",
                TaskStage.FINISHED.name(), DefaultSubStage.COMPLETED.name(), false);

        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        EventTopicState result = sender
                .sendPostAndWait(uri, state, EventTopicState.class);

        assertNotNull(result);
        assertNotNull(result.documentSelfLink);
        assertEquals(state.task, result.task);
        assertEquals(state.stage, result.stage);
        assertEquals(state.substage, result.substage);

        uri = UriUtils.buildUri(host, result.documentSelfLink);
        result = sender.sendGetAndWait(uri, EventTopicState.class);
        assertNotNull(result);
    }

    @Test
    public void testEmptyTask() {
        EventTopicState state = createEventTopicState(null,
                TaskStage.FINISHED.name(), DefaultSubStage.COMPLETED.name(), false);

        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        Operation op = Operation
                .createPost(uri)
                .setBody(state);

        FailureResponse failure = sender.sendAndWaitFailure(op);
        assertNotNull(failure);
        assertEquals("'Task' is required.", failure.failure.getMessage());
    }

    @Test
    public void testEmptyStage() {
        EventTopicState state = createEventTopicState("DummyTask",
                null, DefaultSubStage.COMPLETED.name(), false);

        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        Operation op = Operation
                .createPost(uri)
                .setBody(state);

        FailureResponse failure = sender.sendAndWaitFailure(op);
        assertNotNull(failure);
        assertEquals("'Stage' is required.", failure.failure.getMessage());
    }

    @Test
    public void testEmptySubStage() {
        EventTopicState state = createEventTopicState("DummyTask",
                TaskStage.FINISHED.name(), null, false);

        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        Operation op = Operation
                .createPost(uri)
                .setBody(state);

        FailureResponse failure = sender.sendAndWaitFailure(op);
        assertNotNull(failure);
        assertEquals("'SubStage' is required.", failure.failure.getMessage());
    }

    @Test
    public void testEmptyBlocking() {
        EventTopicState state = createEventTopicState("DummyTask",
                TaskStage.FINISHED.name(), DefaultSubStage.COMPLETED.name(), null);

        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        Operation op = Operation
                .createPost(uri)
                .setBody(state);

        FailureResponse failure = sender.sendAndWaitFailure(op);
        assertNotNull(failure);
        assertEquals("'Blocking' is required.", failure.failure.getMessage());
    }

    @Test
    public void testCreateionOfChangeContainerNameTopic() {
        // On start service creates new topic. No need for explicit post for creation.
        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        TestContext context = new TestContext(1, Duration.ofSeconds(180));
        Operation.createGet(uri)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        context.fail(e);
                        return;
                    }
                    ServiceDocumentQueryResult result = o.getBody(ServiceDocumentQueryResult.class);
                    assertNotNull(result);
                    assertNotNull(result.documentLinks);
                    assertTrue(result.documentLinks.contains(EventTopicService.FACTORY_LINK
                            + "/"
                            + EventTopicRegistrationBootstrapService.CONTAINER_NAME_TOPIC_TASK_SELF_LINK));

                    context.completeIteration();
                }).sendWith(host);
        ;
        context.await();

    }

    private EventTopicState createEventTopicState(String task, String stage,
            String subStage, Boolean blocking) {
        EventTopicState state = new EventTopicState();
        state.task = task;
        state.stage = stage;
        state.substage = subStage;
        state.blockable = blocking;
        state.notificationPayload = "notificationPayload";
        state.replyPayload = "replayPayload";
        return state;

    }

}
