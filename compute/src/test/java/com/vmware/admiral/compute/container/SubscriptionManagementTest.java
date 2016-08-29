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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.SubscriptionManager;
import com.vmware.admiral.common.util.SubscriptionManager.SubscriptionNotification;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.test.MinimalTestServiceState;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.MinimalTestService;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class SubscriptionManagementTest extends ComputeBaseTest {
    private Service service;
    private List<SubscriptionNotification<MinimalTestServiceState>> results;
    private MinimalTestServiceState state;
    private MinimalTestServiceState updatedState;
    private SubscriptionNotification<MinimalTestServiceState> notification;
    private SubscriptionManager<MinimalTestServiceState> subscriptionManager;
    private final String updatedTestId = "updatedTestId";
    private final String updatedTestValue = "updateTestValue";
    private TestContext activeContext;

    @Before
    public void setUp() throws Throwable {
        state = new MinimalTestServiceState();
        state.id = updatedTestId;
        state.stringValue = updatedTestValue;

        service = initService();
        results = Collections.synchronizedList(new ArrayList<>());
        subscriptionManager = new SubscriptionManager<>(
                host, host.getId(), service.getSelfLink(), MinimalTestServiceState.class);

        waitForServiceAvailability(ServiceUriPaths.CORE_QUERY_TASKS);
        waitForServiceAvailability(service.getSelfLink());
    }

    @After
    public void tearDown() throws Throwable {
        unsubscribe();
    }

    private String subscribe() throws Throwable {
        TestContext ctx = testCreate(1);
        subscriptionManager.setCompletionHandler((e) -> {
            if (e != null) {
                ctx.failIteration(e);
                return;
            }
            ctx.completeIteration();
        });
        String subscriptionId = subscriptionManager.start(handler());
        testWait(ctx);
        // reset completion handler
        subscriptionManager.setCompletionHandler(null);
        return subscriptionId;
    }

    private void unsubscribe() throws Throwable {
        if (this.subscriptionManager == null) {
            return;
        }

        TestContext ctx = testCreate(1);
        subscriptionManager.setCompletionHandler((e) -> {
            if (e != null) {
                ctx.failIteration(e);
                return;
            }
            ctx.completeIteration();
        });
        subscriptionManager.close();
        testWait(ctx);
    }

    @Test
    public void testNotificationSubscriptionUpdates() throws Throwable {
        subscribe();

        state.documentSelfLink = service.getSelfLink();
        doOperation(Action.PUT, state);

        notification = getNotification();
        assertNotNull(notification);
        assertNotNull(notification.getResult());
        assertTrue(notification.isUpdate());
        updatedState = notification.getResult();
        assertEquals(updatedTestId, updatedState.id);
        assertEquals(updatedTestValue, updatedState.stringValue);

        state.id = updatedTestValue + updatedTestId;

        doOperation(Action.PATCH, state);

        notification = getNotification();
        assertNotNull(notification.getResult());
        assertTrue(notification.isUpdate());
        updatedState = notification.getResult();
        assertEquals(updatedTestValue + updatedTestId, updatedState.id);
        assertEquals(updatedTestValue, updatedState.stringValue);

        doOperation(Action.DELETE, new ServiceDocument());

        notification = getNotification();
        assertNotNull(notification.getResult());
        assertTrue(notification.isDelete());

        assertFalse(subscriptionManager.isSubscribed());
    }

    @Test
    public void testPollForUpdates() throws Throwable {
        setFinalStatic(SubscriptionManager.class
                .getDeclaredField("DEFAULT_SUBSCRIPTION_POLLING_PERIOD_MILLIS"), 20L);
        setFinalStatic(SubscriptionManager.class
                .getDeclaredField("SUBSCRIPTION_POLLING_STRATEGY"), true);

        subscriptionManager.close();
        subscriptionManager = new SubscriptionManager<>(
                host, host.getId(), service.getSelfLink(), MinimalTestServiceState.class, true);

        subscribe();

        doOperation(Action.PUT, state);

        notification = getNotification();
        assertNotNull(notification.getResult());
        assertTrue(notification.isUpdate());
        updatedState = notification.getResult();
        assertEquals(updatedTestId, updatedState.id);
        assertEquals(updatedTestValue, updatedState.stringValue);

        state.id = updatedTestValue + updatedTestId;

        doOperation(Action.PATCH, state);

        notification = getNotification();
        assertNotNull(notification.getResult());
        assertTrue(notification.isUpdate());
        updatedState = notification.getResult();
        assertEquals(updatedTestValue + updatedTestId, updatedState.id);
        assertEquals(updatedTestValue, updatedState.stringValue);

        doOperation(Action.DELETE, new ServiceDocument());

        notification = getNotification();
        assertNotNull(notification.getResult());
        assertTrue(notification.isDelete());

        assertFalse(subscriptionManager.isSubscribed());
    }

    private void doOperation(Action action, Object state) throws Throwable {
        TestContext ctx = testCreate(2);

        this.activeContext = ctx;
        // wait also for the update notification to get called in the handler.
        Operation op = new Operation();
        op.setUri(service.getUri())
                .setAction(action)
                .setBody(state)
                .setCompletion(ctx.getCompletion());
        host.send(op);
        testWait(ctx);
    }

    private Service initService() throws Throwable {
        return host.doThroughputServiceStart(1, MinimalTestService.class,
                host.buildMinimalTestState(),
                EnumSet.of(Service.ServiceOption.PERSISTENCE), null).get(0);
    }

    private Consumer<SubscriptionNotification<MinimalTestServiceState>> handler() {
        return (r) -> {
            results.clear();
            results.add(r);
            this.activeContext.completeIteration();
        };
    }

    private SubscriptionNotification<MinimalTestServiceState> getNotification() {
        if (results.isEmpty()) {
            return null;
        }
        SubscriptionNotification<MinimalTestServiceState> result = results.get(0);
        results.clear();
        return result;
    }
}
