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

import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.utils.EventTopicUtils;
import com.vmware.admiral.service.common.EventTopicService.TopicTaskInfo;
import com.vmware.photon.controller.model.data.SchemaBuilder;
import com.vmware.xenon.common.LocalizableValidationException;

/**
 * JUnit test of EventTopicUtils class.
 */
public class EventTopicUtilsTest extends RequestBaseTest {

    @Test
    public void testCreateEventTopic() {
        EventTopicUtils
                .registerEventTopic(UUID.randomUUID().toString(), UUID.randomUUID().toString
                                (), UUID.randomUUID().toString(), UUID.randomUUID().toString(), false,
                        new SchemaBuilder(), new TopicTaskInfo(), host);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testCreateEventTopicWithEmptyId() {
        EventTopicUtils.registerEventTopic(null, UUID.randomUUID().toString
                        (), UUID.randomUUID().toString(), UUID.randomUUID().toString(), false,
                new SchemaBuilder(), new TopicTaskInfo(), host);

    }

    @Test(expected = LocalizableValidationException.class)
    public void testCreateEventTopicWithEmptyName() {
        EventTopicUtils
                .registerEventTopic(UUID.randomUUID().toString(), null,
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(), false,
                        new SchemaBuilder(), new TopicTaskInfo(), host);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testCreateEventTopicWithEmptyTaskInfo() {
        EventTopicUtils
                .registerEventTopic(UUID.randomUUID().toString(), UUID.randomUUID().toString
                                (), UUID.randomUUID().toString(), UUID.randomUUID().toString(), false,
                        new SchemaBuilder(), null, host);
    }

}
