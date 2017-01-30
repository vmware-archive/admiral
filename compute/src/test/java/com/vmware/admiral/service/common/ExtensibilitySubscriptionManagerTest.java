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

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitServiceHelper;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestRequestSender;

public class ExtensibilitySubscriptionManagerTest extends BaseTestCase {

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        reset();
        sender = host.getTestRequestSender();

        // start services
        HostInitServiceHelper.startServices(host,
                ConfigurationService.ConfigurationFactoryService.class,
                ExtensibilitySubscriptionManager.class,
                ExtensibilitySubscriptionFactoryService.class);
        // wait to become available
        waitForServiceAvailability(ConfigurationService.ConfigurationFactoryService.SELF_LINK);
        waitForServiceAvailability(ExtensibilitySubscriptionFactoryService.SELF_LINK);
        waitForServiceAvailability(ExtensibilitySubscriptionManager.SELF_LINK);
    }

    @After
    public void tearDown() throws Throwable {
        reset();
    }

    @Test
    public void testInitialState() throws Throwable {
        assertNotNull(ExtensibilitySubscriptionManager.getInstance());
        Map<String, ExtensibilitySubscription> map = getExtensibilityManagerInternalMap();
        assertNotNull(map);
        assertEquals(0, map.size());
    }

    @Test(expected = IllegalStateException.class)
    public void testNotInitialized() throws Throwable {
        tearDown();
        ExtensibilitySubscriptionManager.getInstance();
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
        return getPrivateField(f, ExtensibilitySubscriptionManager.getInstance());
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

    private void reset() throws Exception {
        Field f = ExtensibilitySubscriptionManager.class.getDeclaredField("INSTANCE");
        setPrivateField(f, ExtensibilitySubscriptionService.class, null);
    }

}