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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.EventTopicService.EventTopicState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

/**
 * Bootstrap service that registers topics for subscription on start up. It will handle registration
 * of all topics in one place.
 */
public class EventTopicRegistrationBootstrapService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.EVENT_TOPIC_REGISTRY_BOOTSTRAP;

    public static final String CONTAINER_NAME_TOPIC_TASK_SELF_LINK = "change-container-name";

    private static final String CONTAINER_NAME_TOPIC_ID = "com.vmware.container.name.assignment";
    private static final String CONTAINER_NAME_TOPIC_NAME = "Name assignment";
    private static final String CONTAINER_NAME_TOPIC_TASK_NAME = "ContainerAllocationTaskState";
    private static final String CONTAINER_NAME_TOPIC_SUBSTAGE = "CONTEXT_PREPARED";
    private static final String CONTAINER_NAME_TOPIC_TASK_DESCRIPTION = "Assign custom container name.";

    public static FactoryService createFactory() {
        return FactoryService.create(EventTopicRegistrationBootstrapService.class);
    }

    public EventTopicRegistrationBootstrapService() {
        super(ServiceDocument.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {

        if (!ServiceHost.isServiceCreate(post)) {
            // do not perform bootstrap logic when the post is NOT from direct client, eg: node
            // restart
            post.complete();
            return;
        }

        createTopics(getHost(), post);
    }

    @Override
    public void handlePut(Operation put) {

        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            // converted PUT due to IDEMPOTENT_POST option
            logInfo("Task has already started. Ignoring converted PUT.");
            put.complete();
            return;
        }

        // normal PUT is not supported
        put.fail(Operation.STATUS_CODE_BAD_METHOD);
    }

    public static CompletionHandler startTask(ServiceHost host) {
        return (o, e) -> {
            if (e != null) {
                host.log(Level.SEVERE, Utils.toString(e));
                return;
            }

            // create service with fixed link
            // POST will be issued multiple times but will be converted to PUT after the first one.
            ServiceDocument doc = new ServiceDocument();
            doc.documentSelfLink = "event-topic-preparation-task";
            Operation.createPost(host, EventTopicRegistrationBootstrapService.FACTORY_LINK)
                    .setBody(doc)
                    .setReferer(host.getUri())
                    .setCompletion((oo, ee) -> {
                        if (ee != null) {
                            host.log(Level.SEVERE, Utils.toString(ee));
                            return;
                        }
                        host.log(Level.INFO, "event-topic-preparation-task is triggered.");
                    })
                    .sendWith(host);

        };
    }

    private static void createTopics(ServiceHost host, Operation post) {

        OperationJoin hostsStatsOperation = OperationJoin
                .create(topicOperations(host));

        hostsStatsOperation.setCompletion((ops, failures) -> {
            if (failures != null) {
                host.log(Level.SEVERE,
                        String.format("Failure while creating topics. Exception: %s ",
                                Utils.toString(failures)));
                post.fail(new Throwable(Utils.toString(failures)));
                return;
            }

            host.log(Level.INFO, "Topics have been created successfully.");
            post.complete();

        });

        hostsStatsOperation.sendWith(host);
    }

    private static List<Operation> topicOperations(ServiceHost host) {
        return Arrays.asList(new Operation[] { createChangeContainerNameTopicOperation(host) });
    }

    private static Operation createChangeContainerNameTopicOperation(ServiceHost host) {
        EventTopicState topic = new EventTopicState();
        topic.id = CONTAINER_NAME_TOPIC_ID;
        topic.name = CONTAINER_NAME_TOPIC_NAME;
        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = CONTAINER_NAME_TOPIC_TASK_NAME;
        taskInfo.stage = TaskStage.STARTED.name();
        taskInfo.substage = CONTAINER_NAME_TOPIC_SUBSTAGE;
        topic.topicTaskInfo = taskInfo;
        topic.documentSelfLink = CONTAINER_NAME_TOPIC_TASK_SELF_LINK;
        topic.description = CONTAINER_NAME_TOPIC_TASK_DESCRIPTION;
        topic.blockable = Boolean.TRUE;
        topic.notificationPayload = TaskPayloadBuilder.initialize()
                .add(Set.class, "resourceNames")
                .add(String.class, "resourceType")
                .toJson();
        topic.replyPayload = topic.notificationPayload;

        return Operation.createPost(host, EventTopicService.FACTORY_LINK)
                .setReferer(host.getUri())
                .setBody(topic)
                .addPragmaDirective(
                        Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE,
                                String.format(
                                        "Unable to register ChangeContainerName topic. Exception: %s",
                                        e.getMessage()));
                    }
                });
    }

    private static class TaskPayloadBuilder {

        private static Map<Class<?>, String> entities = new ConcurrentHashMap<>();

        private static TaskPayloadBuilder initialize() {
            return new TaskPayloadBuilder();
        }

        public TaskPayloadBuilder add(Class<?> type, String name) {

            validateEntity(type, name);
            entities.put(type, name);
            return this;
        }

        public String toJson() {
            return Utils.toJson(entities);
        }

        private void validateEntity(Class<?> type, String name) {
            if (type == null) {
                throw new IllegalArgumentException("'type' is required");
            }

            if (name == null) {
                throw new IllegalArgumentException("'name' is required");
            }
        }

    }

}
