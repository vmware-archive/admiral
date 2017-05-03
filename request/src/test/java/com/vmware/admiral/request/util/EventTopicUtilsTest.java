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

package com.vmware.admiral.request.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.utils.EventTopicUtils;
import com.vmware.admiral.request.utils.EventTopicUtils.SchemaBuilder;
import com.vmware.admiral.service.common.EventTopicService.TopicTaskInfo;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Utils;

/**
 * JUnit test of EventTopicUtils class.
 */
public class EventTopicUtilsTest extends RequestBaseTest {

    @Test
    public void testSchemaBuilder() {
        String fieldName = "resourceNames";
        String dataType = String.class.getSimpleName();
        String description = "description";
        String label = "label";
        boolean multivalued = true;

        EventTopicUtils.SchemaBuilder schemaBuilder = EventTopicUtils
                .SchemaBuilder.create();
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

        EventTopicUtils.SchemaBuilder schemaBuilder = EventTopicUtils
                .SchemaBuilder.create();
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

    @Test
    public void testCreateEventTopic() {
        EventTopicUtils
                .registerEventTopic(UUID.randomUUID().toString(), UUID.randomUUID().toString
                                (), UUID.randomUUID().toString(), UUID.randomUUID().toString(), false,
                        SchemaBuilder.create(), new TopicTaskInfo(), host);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testCreateEventTopicWithEmptyId() {
        EventTopicUtils.registerEventTopic(null, UUID.randomUUID().toString
                        (), UUID.randomUUID().toString(), UUID.randomUUID().toString(), false,
                SchemaBuilder.create(), new TopicTaskInfo(), host);

    }

    @Test(expected = LocalizableValidationException.class)
    public void testCreateEventTopicWithEmptyName() {
        EventTopicUtils
                .registerEventTopic(UUID.randomUUID().toString(), null,
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(), false,
                        SchemaBuilder.create(), new TopicTaskInfo(), host);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testCreateEventTopicWithEmptyTaskInfo() {
        EventTopicUtils
                .registerEventTopic(UUID.randomUUID().toString(), UUID.randomUUID().toString
                                (), UUID.randomUUID().toString(), UUID.randomUUID().toString(), false,
                        SchemaBuilder.create(), null, host);
    }

}
