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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.EventTopicService.EventTopicState;
import com.vmware.admiral.service.common.EventTopicService.TopicTaskInfo;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;

public class ExtensibilitySubscriptionManagerTest extends BaseTestCase {

    private TestRequestSender sender;
    private ExtensibilitySubscriptionManager manager;

    @Before
    public void setUp() throws Throwable {
        sender = host.getTestRequestSender();

        host.startServiceAndWait(ConfigurationFactoryService.class,
                ConfigurationFactoryService.SELF_LINK);

        manager = new ExtensibilitySubscriptionManager();
        host.startServiceAndWait(manager, ExtensibilitySubscriptionManager.SELF_LINK, null);

        host.startServiceAndWait(ExtensibilitySubscriptionFactoryService.class,
                ExtensibilitySubscriptionFactoryService.SELF_LINK);

        host.startFactory(new EventTopicService());
        waitForServiceAvailability(EventTopicService.FACTORY_LINK);
    }

    @Test
    public void testInitialState() throws Throwable {
        assertNotNull(manager);
        Map<String, ExtensibilitySubscription> map = getExtensibilitySubscriptions();
        assertNotNull(map);
        assertEquals(0, map.size());

        Map<String, Duration> timeouts = getTimeoutsPerStageAndSubstage();
        assertNotNull(map);
        assertEquals(0, map.size());
    }

    @Test
    public void testNotInitialized() throws Throwable {
        Operation result = sender.sendAndWait(Operation
                .createDelete(host, ExtensibilitySubscriptionManager.SELF_LINK));
        assertNotNull(result);
        assertEquals(Operation.STATUS_CODE_OK, result.getStatusCode());

        Field field = ExtensibilitySubscriptionManager.class.getDeclaredField("initialized");
        AtomicBoolean b = getPrivateField(field, manager);
        assertNotNull(b);
        assertFalse(b.get());
    }

    @Test
    public void testAddRemoveExtensibility() throws Throwable {
        ExtensibilitySubscription state1 = createExtensibilityState("substage1", "uri1");
        ExtensibilitySubscription state2 = createExtensibilityState("substage2", "uri2");
        ExtensibilitySubscription state3 = createExtensibilityState("substage3", "uri3");
        Map<String, ExtensibilitySubscription> map = getExtensibilitySubscriptions();
        assertNotNull(map);

        URI uri = UriUtils.buildUri(host, ExtensibilitySubscriptionService.FACTORY_LINK);
        ExtensibilitySubscription result1 = sender
                .sendPostAndWait(uri, state1, ExtensibilitySubscription.class);
        assertNotNull(result1);
        verifyMapSize(map, 1);

        ExtensibilitySubscription result2 = sender
                .sendPostAndWait(uri, state2, ExtensibilitySubscription.class);
        assertNotNull(result2);
        verifyMapSize(map, 2);

        ExtensibilitySubscription result3 = sender
                .sendPostAndWait(uri, state3, ExtensibilitySubscription.class);
        assertNotNull(result3);
        verifyMapSize(map, 3);

        Operation delete = Operation.createDelete(host, result1.documentSelfLink);
        result1 = sender.sendAndWait(delete, ExtensibilitySubscription.class);
        assertNotNull(result1);
        verifyMapSize(map, 2);
    }

    @Test
    public void testAddRemoveTopic() throws Throwable {
        EventTopicState state1 = createTopicState("substage1", "topic1");
        EventTopicState state2 = createTopicState("substage2", "topic2");
        EventTopicState state3 = createTopicState("substage3", "topic3");
        Map<String, Duration> map = getTimeoutsPerStageAndSubstage();
        assertNotNull(map);

        URI uri = UriUtils.buildUri(host, EventTopicService.FACTORY_LINK);
        EventTopicState result1 = sender
                .sendPostAndWait(uri, state1, EventTopicState.class);
        assertNotNull(result1);
        verifyMapSize(map, 1);

        EventTopicState result2 = sender
                .sendPostAndWait(uri, state2, EventTopicState.class);
        assertNotNull(result2);
        verifyMapSize(map, 2);

        EventTopicState result3 = sender
                .sendPostAndWait(uri, state3, EventTopicState.class);
        assertNotNull(result3);
        verifyMapSize(map, 3);

        Operation delete = Operation.createDelete(host, result1.documentSelfLink);
        result1 = sender.sendAndWait(delete, EventTopicState.class);
        assertNotNull(result1);
        verifyMapSize(map, 2);
    }

    private Map<String, ExtensibilitySubscription> getExtensibilitySubscriptions()
            throws Exception {
        Field f = ExtensibilitySubscriptionManager.class.getDeclaredField("subscriptions");
        return getPrivateField(f, manager);
    }

    private Map<String, Duration> getTimeoutsPerStageAndSubstage()
            throws Exception {
        Field f = ExtensibilitySubscriptionManager.class
                .getDeclaredField("timeoutsPerTaskStageAndSubstage");
        return getPrivateField(f, manager);
    }

    private void verifyMapSize(@SuppressWarnings("rawtypes") Map map, int count) throws Throwable {
        waitFor(map, count);
    }

    private void waitFor(Map map, int count) {
        TestContext context = new TestContext(1, Duration.ofMinutes(1));
        schedule(map, count, context);
        context.await();
    }

    private void schedule(Map map, int count, TestContext context) {
        if (map.size() != count) {
            host.schedule(() -> {
                schedule(map, count, context);
                return;
            }, 3000, TimeUnit.MILLISECONDS);
        }
        context.completeIteration();
    }

    private ExtensibilitySubscription createExtensibilityState(String substage, String uri) {
        ExtensibilitySubscription state = new ExtensibilitySubscription();
        state.task = "task";
        state.stage = "stage";
        state.substage = substage;
        state.callbackReference = UriUtils.buildUri(uri);
        state.blocking = false;
        return state;
    }

    private EventTopicState createTopicState(String substage, String topicId) {
        EventTopicState state = new EventTopicState();
        state.topicTaskInfo = new TopicTaskInfo();
        state.topicTaskInfo.task = "task";
        state.topicTaskInfo.stage = "stage";
        state.topicTaskInfo.substage = substage;
        state.id = topicId;
        state.name = topicId;
        state.blockable = false;
        state.schema = "";
        return state;
    }

}