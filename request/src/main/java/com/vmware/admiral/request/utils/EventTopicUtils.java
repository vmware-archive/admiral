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

package com.vmware.admiral.request.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.admiral.service.common.EventTopicService.EventTopicState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;

/**
 * Utility class for operations related to {@link EventTopicState}.
 */
public abstract class EventTopicUtils {

    public static void registerEventTopic(String id, String name, String description,
            String documentSelfLink, boolean blockable, SchemaBuilder schema,
            EventTopicService.TopicTaskInfo taskInfo, ServiceHost host) {

        // validate if provided data is valid.
        validateTopicInfo(id, name, taskInfo, host);

        EventTopicState topic = new EventTopicState();
        topic.id = id;
        topic.name = name;
        topic.description = description;
        topic.documentSelfLink = documentSelfLink;
        topic.blockable = blockable;
        topic.schema = extendSchemaWithCommonFields(schema);
        topic.topicTaskInfo = taskInfo;

        registerEventTopic(host, topic);

    }

    private static void registerEventTopic(ServiceHost host, EventTopicState topic) {
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
                    }
                }));
    }

    private static void validateTopicInfo(String id, String name, EventTopicService.TopicTaskInfo
            taskInfo, ServiceHost host) {

        AssertUtil.assertNotNull(id, "'id' can not be null");

        AssertUtil.assertNotNull(name, "'name' can not be null");

        AssertUtil.assertNotNull(taskInfo, "'taskInfo' can not be null");

        AssertUtil.assertNotNull(host, "'host' can not be null");
    }

    private static String extendSchemaWithCommonFields(SchemaBuilder schema) {
        if (schema == null) {
            schema = SchemaBuilder.create();
        }
        addCommonSchemaFields(schema);
        return schema.build();
    }

    /**
     * Helper class that creates schema for EventTopics.
     */
    public static class SchemaBuilder {

        private static final String FIELD_DATA_TYPE = "dataType";
        private static final String FIELD_LABEL = "label";
        private static final String FIELD_DESCRIPTION = "description";
        private static final String FIELD_MULTIVALUED = "multivalued";
        private static final String FIELD_READONLY = "readOnly";

        private List<Map<String, Map<String, String>>> entitiesHolder = new ArrayList<>();

        // Field to its properties.
        private Map<String, Map<String, String>> entities;

        private SchemaBuilder() {
        }

        public static SchemaBuilder create() {
            return new EventTopicUtils.SchemaBuilder();
        }

        public SchemaBuilder addField(String fieldName) {
            if (entities == null || !entities.isEmpty()) {
                entities = new HashMap<>();
            }
            entities.put(fieldName, new HashMap<>());
            entitiesHolder.add(entities);
            return this;
        }

        public SchemaBuilder addDataType(String dataType) {
            if (entities == null) {
                throw new IllegalArgumentException("'entities' can not be null");
            }

            Entry<String, Map<String, String>> entry = entities.entrySet().iterator().next();
            Map<String, String> value = entry.getValue();
            value.put(FIELD_DATA_TYPE, dataType);
            return this;
        }

        public SchemaBuilder addLabel(String label) {
            if (entities == null) {
                throw new IllegalArgumentException("'entities' can not be null");
            }

            Entry<String, Map<String, String>> entry = entities.entrySet().iterator().next();
            Map<String, String> value = entry.getValue();
            value.put(FIELD_LABEL, label);
            return this;
        }

        public SchemaBuilder addDescription(String description) {
            if (entities == null) {
                throw new IllegalArgumentException("'entities' can not be null");
            }

            Entry<String, Map<String, String>> entry = entities.entrySet().iterator().next();
            Map<String, String> value = entry.getValue();
            value.put(FIELD_DESCRIPTION, description);
            return this;
        }

        public SchemaBuilder whereMultiValued(Boolean multivalued) {
            if (entities == null) {
                throw new IllegalArgumentException("'entities' can not be null");
            }

            Entry<String, Map<String, String>> entry = entities.entrySet().iterator().next();
            Map<String, String> value = entry.getValue();
            value.put(FIELD_MULTIVALUED, String.valueOf(multivalued));
            return this;
        }

        public SchemaBuilder whereReadOnly(Boolean readOnly) {
            Entry<String, Map<String, String>> entry = entities.entrySet().iterator().next();
            Map<String, String> value = entry.getValue();
            value.put(FIELD_READONLY, String.valueOf(readOnly));
            return this;
        }

        public String build() {
            return Utils.toJson(entitiesHolder);
        }
    }

    private static SchemaBuilder addCommonSchemaFields(SchemaBuilder schema) {
        schema
                .addField("requestId")
                .addDataType(String.class.getSimpleName())
                .addLabel("Request id")
                .addDescription("Request id")
                .whereMultiValued(false)

                .addField("componentId")
                .addDataType(String.class.getSimpleName())
                .addLabel("Component id")
                .addDescription("Component id")
                .whereMultiValued(false)

                .addField("blueprintId")
                .addDataType(String.class.getSimpleName())
                .addLabel("Blueprint name")
                .addDescription("Blueprint name")
                .whereMultiValued(false)

                .addField("componentTypeId")
                .addDataType(String.class.getSimpleName())
                .addLabel("Component type id")
                .addDescription("Component type id")
                .whereMultiValued(false)

                .addField("owner")
                .addDataType(String.class.getSimpleName())
                .addLabel("Owner")
                .addDescription("Owner")
                .whereMultiValued(false)

                .addField("customProperties")
                .addDataType(String.class.getSimpleName())
                .addLabel("Properties of the resource(Read Only)")
                .addDescription("Resource Properties.")
                .whereMultiValued(false)
                .whereReadOnly(true);

        return schema;
    }

}
