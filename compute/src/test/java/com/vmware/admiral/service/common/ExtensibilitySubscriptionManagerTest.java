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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestRequestSender;

public class ExtensibilitySubscriptionManagerTest extends BaseTestCase {

    private TestRequestSender sender;
    private ExtensibilitySubscriptionManager manager;

    @Before
    public void setUp() throws Throwable {
        sender = host.getTestRequestSender();

        host.startServiceAndWait(ConfigurationFactoryService.class,
                ConfigurationFactoryService.SELF_LINK);
        host.startServiceAndWait(ExtensibilitySubscriptionFactoryService.class,
                ExtensibilitySubscriptionFactoryService.SELF_LINK);

        manager = new ExtensibilitySubscriptionManager();
        host.startServiceAndWait(manager, ExtensibilitySubscriptionManager.SELF_LINK, null);
    }

    @Test
    public void testInitialState() throws Throwable {
        assertNotNull(manager);
        Map<String, ExtensibilitySubscription> map = getExtensibilityManagerInternalMap();
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
        Map<String, ExtensibilitySubscription> map = getExtensibilityManagerInternalMap();
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

    private Map<String, ExtensibilitySubscription> getExtensibilityManagerInternalMap()
            throws Exception {
        Field f = ExtensibilitySubscriptionManager.class.getDeclaredField("extensions");
        return getPrivateField(f, manager);
    }

    private void verifyMapSize(Map map, int count) throws Throwable {
        waitFor(() -> map.size() == count);
    }

    private ExtensibilitySubscription createExtensibilityState(String substage, String uri) {
        ExtensibilitySubscription state = new ExtensibilitySubscription();
        state.task = "task";
        state.stage = "stage";
        state.substage = substage;
        state.callbackReference = uri;
        state.blocking = false;
        return state;
    }

}