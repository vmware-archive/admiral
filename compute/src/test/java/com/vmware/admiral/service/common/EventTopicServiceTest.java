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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.service.common.EventTopicRegistrationBootstrapService.SchemaBuilder;
import com.vmware.admiral.service.common.EventTopicService.EventTopicState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.TestRequestSender.FailureResponse;

public class EventTopicServiceTest extends BaseTestCase {

    private static final String EVENT_TASK = "DummyTask";
    private static final String EVENT_NAME = "Name assignment";

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        sender = host.getTestRequestSender();

        host.startFactory(new EventTopicService());

        waitForServiceAvailability(EventTopicService.FACTORY_LINK);

        host.registerForServiceAvailability(EventTopicRegistrationBootstrapService.startTask(host),
                true,
                EventTopicRegistrationBootstrapService.FACTORY_LINK);

        host.startFactory(new EventTopicRegistrationBootstrapService());

        waitForServiceAvailability(EventTopicRegistrationBootstrapService.FACTORY_LINK);
    }

    @Test
    public void testCreateEventRegistryTopic() {
        EventTopicState state = createEventTopicState(EVENT_NAME, EVENT_TASK,
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
        EventTopicState state = createEventTopicState(null, EVENT_TASK,
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
        EventTopicState state = createEventTopicState(EVENT_NAME, null,
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
        EventTopicState state = createEventTopicState(EVENT_NAME, EVENT_TASK,
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
        EventTopicState state = createEventTopicState(EVENT_NAME, EVENT_TASK,
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
        EventTopicState state = createEventTopicState(EVENT_NAME, EVENT_TASK,
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
        EventTopicState state = createEventTopicState(EVENT_NAME, EVENT_TASK,
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
        verifyThatTopicExists(
                EventTopicRegistrationBootstrapService.CONTAINER_NAME_TOPIC_TASK_SELF_LINK,
                context);
        context.await();
    }

    @Test
    public void testCreateionOfChangeComputeNameTopic() {
        // On start service creates new topic. No need for explicit post for creation.
        TestContext context = new TestContext(1, Duration.ofSeconds(120));
        verifyThatTopicExists(
                EventTopicRegistrationBootstrapService.COMPUTE_NAME_TOPIC_TASK_SELF_LINK, context);
        context.await();
    }

    @Test
    public void testSchemaBuilder() {
        String fieldName = "resourceNames";
        String dataType = String.class.getSimpleName();
        String description = "description";
        String label = "label";
        boolean multivalued = true;

        SchemaBuilder schemaBuilder = EventTopicRegistrationBootstrapService.SchemaBuilder.create();
        schemaBuilder.addField(fieldName)
                .addDataType(dataType)
                .addDescription(description)
                .addLabel(label)
                .whereMultiValued(multivalued);

        String schemaAsJson = schemaBuilder.build();

        assertNotNull(schemaAsJson);

        @SuppressWarnings("unchecked")
        List<Map<String, Map<String, String>>> entitiesHolder = Utils.fromJson(schemaAsJson,
                List.class);

        assertNotNull(entitiesHolder);
        assertEquals(1, entitiesHolder.size());

        Map<String, Map<String, String>> fieldToProperties = entitiesHolder.get(0);

        Entry<String, Map<String, String>> entry = fieldToProperties.entrySet().iterator().next();
        assertEquals(fieldName, entry.getKey());

        Map<String, String> fieldProperties = entry.getValue();
        assertEquals(4, fieldProperties.size());

    }

    @Test
    public void testSchemaBuilderWithMultipleFields() {

        String fieldName = "resourceNames";
        String dataType = String.class.getSimpleName();
        String description = UUID.randomUUID().toString();
        String label = UUID.randomUUID().toString();
        boolean multivalued = true;
        boolean readOnly = false;

        String fieldName2 = "containerDescProperties";
        String dataType2 = String.class.getSimpleName();
        String description2 = UUID.randomUUID().toString();
        String label2 = UUID.randomUUID().toString();
        boolean multivalued2 = false;
        boolean readOnly2 = false;

        SchemaBuilder schemaBuilder = EventTopicRegistrationBootstrapService.SchemaBuilder.create();
        schemaBuilder.addField(fieldName)
                .addDataType(dataType)
                .addDescription(description)
                .addLabel(label)
                .whereMultiValued(multivalued)
                .whereReadOnly(readOnly)
                // Add another field
                .addField(fieldName2)
                .addDataType(dataType2)
                .addDescription(description2)
                .addLabel(label2)
                .whereMultiValued(multivalued2)
                .whereReadOnly(readOnly2);

        String schemaAsJson = schemaBuilder.build();

        assertNotNull(schemaAsJson);

        @SuppressWarnings("unchecked")
        List<Map<String, Map<String, String>>> entitiesHolder = Utils.fromJson(schemaAsJson,
                List.class);

        assertNotNull(entitiesHolder);
        assertEquals(2, entitiesHolder.size());

        entitiesHolder.stream().forEach(entity -> {
            if (entity.containsKey(fieldName)) {
                Map<String, String> fieldProperties = entity.get(fieldName);
                assertNotNull(fieldProperties);
                assertEquals(5, fieldProperties.size());
                assertEquals(dataType, fieldProperties.get("dataType"));
                assertEquals(description, fieldProperties.get("description"));
                assertEquals(label, fieldProperties.get("label"));
                assertEquals(String.valueOf(multivalued), fieldProperties.get("multivalued"));
                assertEquals(String.valueOf(readOnly), fieldProperties.get("readOnly"));
            } else {
                Map<String, String> fieldProperties = entity.get(fieldName2);
                assertNotNull(fieldProperties);
                assertEquals(5, fieldProperties.size());
                assertEquals(dataType2, fieldProperties.get("dataType"));
                assertEquals(description2, fieldProperties.get("description"));
                assertEquals(label2, fieldProperties.get("label"));
                assertEquals(String.valueOf(multivalued2), fieldProperties.get("multivalued"));
                assertEquals(String.valueOf(readOnly2), fieldProperties.get("readOnly"));

            }
        });

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

    private EventTopicState createEventTopicState(String name, String task, String stage,
            String subStage, Boolean blocking, String schema) {

        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = task;
        taskInfo.stage = stage;
        taskInfo.substage = subStage;

        EventTopicState state = new EventTopicState();
        state.name = name;
        state.topicTaskInfo = taskInfo;
        state.blockable = blocking;
        state.schema = schema;
        return state;

    }

}
