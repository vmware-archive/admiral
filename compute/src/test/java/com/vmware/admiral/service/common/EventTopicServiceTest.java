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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.service.common.EventTopicService.EventTopicState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.TestRequestSender.FailureResponse;

public class EventTopicServiceTest extends BaseTestCase {

    private static final String EVENT_TASK = "DummyTask";
    private static final String EVENT_NAME = "Name assignment";
    private static final String CHANGE_CONTAINER_NAME_SELF_LINK = "change-container-name";
    private static final String CHANGE_COMPUTE_NAME_SELF_LINK = "change-compute-name";

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        sender = host.getTestRequestSender();

        host.startFactory(new EventTopicService());

        waitForServiceAvailability(EventTopicService.FACTORY_LINK);

        initializeChangeResourceNameTopics();
    }

    @Test
    public void testCreateEventRegistryTopic() {
        EventTopicState state = createEventTopicState("dummy-link", EVENT_NAME, EVENT_TASK,
                TaskStage.FINISHED.name(), DefaultSubStage.COMPLETED.name(), false, new String());

        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        EventTopicState result = sender
                .sendPostAndWait(uri, state, EventTopicState.class);

        assertNotNull(result);
        assertNotNull(result.documentSelfLink);
        assertNotNull(result.topicTaskInfo);

        assertEquals(state.topicTaskInfo.task, result.topicTaskInfo.task);
        assertEquals(state.topicTaskInfo.stage, result.topicTaskInfo.stage);
        assertEquals(state.topicTaskInfo.substage, result.topicTaskInfo.substage);

        uri = UriUtils.buildUri(host, result.documentSelfLink);
        result = sender.sendGetAndWait(uri, EventTopicState.class);
        assertNotNull(result);
    }

    @Test
    public void testEmptyName() {
        EventTopicState state = createEventTopicState(null, null, EVENT_TASK,
                TaskStage.FINISHED.name(), DefaultSubStage.COMPLETED.name(), false, new String());

        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        Operation op = Operation
                .createPost(uri)
                .setBody(state);

        FailureResponse failure = sender.sendAndWaitFailure(op);
        assertNotNull(failure);
        assertEquals("'name' is required.", failure.failure.getMessage());
    }

    @Test
    public void testEmptyTask() {
        EventTopicState state = createEventTopicState(null, EVENT_NAME, null,
                TaskStage.FINISHED.name(), DefaultSubStage.COMPLETED.name(), false, new String());

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
        EventTopicState state = createEventTopicState(null, EVENT_NAME, EVENT_TASK,
                null, DefaultSubStage.COMPLETED.name(), false, new String());

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
        EventTopicState state = createEventTopicState(null, EVENT_NAME, EVENT_TASK,
                TaskStage.FINISHED.name(), null, false, new String());

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
        EventTopicState state = createEventTopicState(null, EVENT_NAME, EVENT_TASK,
                TaskStage.FINISHED.name(), DefaultSubStage.COMPLETED.name(), null, new String());

        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        Operation op = Operation
                .createPost(uri)
                .setBody(state);

        FailureResponse failure = sender.sendAndWaitFailure(op);
        assertNotNull(failure);
        assertEquals("'Blocking' is required.", failure.failure.getMessage());
    }

    @Test
    public void testEmptySchema() {
        EventTopicState state = createEventTopicState(null, EVENT_NAME, EVENT_TASK,
                TaskStage.FINISHED.name(), DefaultSubStage.COMPLETED.name(), true, null);

        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        Operation op = Operation
                .createPost(uri)
                .setBody(state);

        FailureResponse failure = sender.sendAndWaitFailure(op);
        assertNotNull(failure);
        assertEquals("'Schema' is required.", failure.failure.getMessage());
    }

    @Test
    public void testCreateionOfChangeContainerNameTopic() {
        // On start service creates new topic. No need for explicit post for creation.
        TestContext context = new TestContext(1, Duration.ofSeconds(120));
        verifyThatTopicExists(CHANGE_CONTAINER_NAME_SELF_LINK, context);
        context.await();
    }

    @Test
    public void testCreateionOfChangeComputeNameTopic() {
        // On start service creates new topic. No need for explicit post for creation.
        TestContext context = new TestContext(1, Duration.ofSeconds(120));
        verifyThatTopicExists(CHANGE_COMPUTE_NAME_SELF_LINK, context);
        context.await();
    }

    private void verifyThatTopicExists(String topicSelfLink, TestContext context) {
        // On start service creates new topic. No need for explicit post for creation.
        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        Operation.createGet(uri)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.schedule(() -> {
                            verifyThatTopicExists(topicSelfLink, context);
                        }, 3, TimeUnit.SECONDS);
                        return;
                    }
                    ServiceDocumentQueryResult result = o.getBody(ServiceDocumentQueryResult.class);
                    assertNotNull(result);
                    assertNotNull(result.documentLinks);

                    if (result.documentLinks.isEmpty()) {
                        // Topics are not created yet. Let's try next iteration?
                        host.schedule(() -> {
                            verifyThatTopicExists(topicSelfLink, context);
                        }, 3, TimeUnit.SECONDS);
                        return;
                    }

                    assertTrue(result.documentLinks.contains(EventTopicService.FACTORY_LINK
                            + "/"
                            + topicSelfLink));
                    context.completeIteration();

                }).sendWith(host);

    }

    private EventTopicState createEventTopicState(String documentSelfLink, String name, String
            task, String stage,
            String subStage, Boolean blocking, String schema) {

        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = task;
        taskInfo.stage = stage;
        taskInfo.substage = subStage;

        EventTopicState state = new EventTopicState();
        state.documentSelfLink = documentSelfLink;
        state.name = name;
        state.topicTaskInfo = taskInfo;
        state.blockable = blocking;
        state.schema = schema;
        return state;

    }

    private void initializeChangeResourceNameTopics() {

        EventTopicState changeContainerName = createEventTopicState(CHANGE_CONTAINER_NAME_SELF_LINK,
                "Change Container's name",
                "ContainerAllocationTaskState", TaskStage.STARTED.name(), "BUILD_RESOURCE_LINKS",
                true, new String());

        EventTopicState changeComputeName = createEventTopicState(CHANGE_COMPUTE_NAME_SELF_LINK,
                "Change Compute's name",
                "ComputeAllocationTaskState", TaskStage.STARTED.name(), "SELECT_PLACEMENT_COMPUTES",
                true, new String());

        List<EventTopicState> topics = Arrays.asList(new EventTopicState[]
                { changeContainerName, changeComputeName });

        TestContext context = new TestContext(2, Duration.ofMinutes(1));

        topics.stream().forEach(topic -> {

            host.sendRequest(Operation.createPost(host, EventTopicService.FACTORY_LINK)
                    .setReferer(host.getUri())
                    .setBody(topic)
                    .addPragmaDirective(
                            Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            host.log(Level.SEVERE,
                                    String.format(
                                            "Unable to register '%s' topic. "
                                                    + "Exception: %s",
                                            topic.name, e.getMessage()));
                            context.failIteration(e);
                            return;
                        }
                        context.completeIteration();
                    }));

        });

        context.await();

    }

}
