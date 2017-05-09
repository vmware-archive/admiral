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

import static com.vmware.photon.controller.model.data.SchemaField.DATATYPE_STRING;

import java.util.logging.Level;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.admiral.service.common.EventTopicService.EventTopicState;
import com.vmware.photon.controller.model.data.Schema;
import com.vmware.photon.controller.model.data.SchemaBuilder;
import com.vmware.photon.controller.model.data.SchemaField.Constraint;
import com.vmware.photon.controller.model.data.SchemaField.Type;
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
        topic.schema = Utils.toJson(extendSchemaWithCommonFields(schema));
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

    private static Schema extendSchemaWithCommonFields(SchemaBuilder schema) {
        if (schema == null) {
            schema = new SchemaBuilder();
        }
        addCommonSchemaFields(schema);
        return schema.build();
    }

    private static SchemaBuilder addCommonSchemaFields(SchemaBuilder schema) {
        return schema
                .addField("requestId")
                .withDataType(DATATYPE_STRING)
                .withLabel("Request id")
                .withDescription("Request id")
                .done()

                .addField("componentId")
                .withDataType(DATATYPE_STRING)
                .withLabel("Component id")
                .withDescription("Component id")
                .done()

                .addField("blueprintId")
                .withDataType(DATATYPE_STRING)
                .withLabel("Blueprint name")
                .withDescription("Blueprint name")
                .done()

                .addField("componentTypeId")
                .withDataType(DATATYPE_STRING)
                .withLabel("Component type id")
                .withDescription("Component type id")
                .done()

                .addField("owner")
                .withDataType(DATATYPE_STRING)
                .withLabel("Owner")
                .withDescription("Owner")
                .done()

                .addField("customProperties")
                .withType(Type.MAP)
                .withDataType(DATATYPE_STRING)
                .withLabel("Properties of the resource(Read Only)")
                .withDescription("Resource Properties.")
                .withConstraint(Constraint.readOnly, true)
                .done();
    }
}
