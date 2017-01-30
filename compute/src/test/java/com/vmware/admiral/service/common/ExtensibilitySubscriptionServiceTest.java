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
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.net.URI;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.host.HostInitServiceHelper;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.TestRequestSender.FailureResponse;

public class ExtensibilitySubscriptionServiceTest extends BaseTestCase {

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        reset();
        sender = host.getTestRequestSender();

        // start services
        HostInitServiceHelper.startServices(host,
                ConfigurationService.ConfigurationFactoryService.class,
                ExtensibilitySubscriptionFactoryService.class);
        // wait to become available
        waitForServiceAvailability(ConfigurationService.ConfigurationFactoryService.SELF_LINK);
        waitForServiceAvailability(ExtensibilitySubscriptionFactoryService.SELF_LINK);
    }

    @After
    public void tearDown() throws Throwable {
        reset();
    }

    @Test
    public void testGetEmpty() throws InterruptedException {
        ServiceDocumentQueryResult result = sender.sendAndWait(
                Operation.createGet(host, ExtensibilitySubscriptionService.FACTORY_LINK),
                ServiceDocumentQueryResult.class);
        assertNotNull(result);
        assertNotNull(result.documentCount);
        assertEquals(0L, (long) result.documentCount);
    }

    @Test
    public void testGetNonExistent() throws Throwable {
        String link = ExtensibilitySubscriptionService.FACTORY_LINK + "/non-existent";
        FailureResponse failure = sender.sendAndWaitFailure(Operation.createGet(host, link));

        assertNotNull(failure);
        assertEquals(Operation.STATUS_CODE_NOT_FOUND, failure.op.getStatusCode());
    }

    @Test
    public void testCreateAndGet() {
        ExtensibilitySubscription state = createExtensibilityState();

        URI uri = UriUtils.buildUri(host, ExtensibilitySubscriptionService.FACTORY_LINK);
        ExtensibilitySubscription result = sender
                .sendPostAndWait(uri, state, ExtensibilitySubscription.class);

        assertNotNull(result);
        assertNotNull(result.documentSelfLink);
        assertEquals(state.task, result.task);
        assertEquals(state.stage, result.stage);
        assertEquals(state.substage, result.substage);

        uri = UriUtils.buildUri(host, result.documentSelfLink);
        result = sender.sendGetAndWait(uri, ExtensibilitySubscription.class);
        assertNotNull(result);
    }

    @Test
    public void testCreateAndUpdate() {
        ExtensibilitySubscription state = createExtensibilityState();
        URI uri = UriUtils.buildUri(host, ExtensibilitySubscriptionService.FACTORY_LINK);

        Operation op = Operation.createPost(uri);
        FailureResponse failure = sender.sendAndWaitFailure(op);
        assertNotNull(failure);

        op = Operation.createPost(uri).setBody(state);
        ExtensibilitySubscription result = sender.sendAndWait(op,
                ExtensibilitySubscription.class);

        assertNotNull(result);
        assertNotNull(result.documentSelfLink);
        assertEquals(state.task, result.task);
        assertEquals(state.stage, result.stage);
        assertEquals(state.substage, result.substage);

        op = Operation.createPost(uri).setBody(state);
        failure = sender.sendAndWaitFailure(op);
        assertEquals(Operation.STATUS_CODE_CONFLICT, failure.op.getStatusCode());

        ExtensibilitySubscription patch = new ExtensibilitySubscription();
        patch.substage = "patched";

        op = Operation.createPatch(host, result.documentSelfLink)
                .setBody(patch);
        failure = sender.sendAndWaitFailure(op);
        assertNotNull(failure);
    }

    @Test
    public void testCreateInvalid() {
        ExtensibilitySubscription state = createExtensibilityState();
        state.task = null;

        Operation op = Operation
                .createPost(host, ExtensibilitySubscriptionService.FACTORY_LINK)
                .setBody(state);
        FailureResponse failure = sender.sendAndWaitFailure(op);

        assertNotNull(failure);
    }

    @Test
    public void testDelete() {
        ExtensibilitySubscription state = createExtensibilityState();

        URI uri = UriUtils.buildUri(host, ExtensibilitySubscriptionService.FACTORY_LINK);
        state = sender.sendPostAndWait(uri, state, ExtensibilitySubscription.class);

        Operation op = Operation.createDelete(host, state.documentSelfLink);
        Operation result = sender.sendAndWait(op);

        assertNotNull(result);
        assertEquals(Operation.STATUS_CODE_OK, result.getStatusCode());
        assertTrue(result.hasBody());
        ExtensibilitySubscription deleted = result.getBody(ExtensibilitySubscription.class);
        assertEquals(state.documentSelfLink, deleted.documentSelfLink);
        assertEquals(state.task, deleted.task);
    }

    @Test
    public void testDeleteInvalid() {
        String link = ExtensibilitySubscriptionService.FACTORY_LINK + "/non-existent";
        Operation op = Operation.createDelete(host, link);
        Operation result = sender.sendAndWait(op);

        assertNotNull(result);
        assertEquals(Operation.STATUS_CODE_OK, result.getStatusCode());
        assertFalse(result.hasBody());
    }

    private ExtensibilitySubscription createExtensibilityState() {
        ExtensibilitySubscription state = new ExtensibilitySubscription();
        state.task = "task";
        state.stage = "stage";
        state.substage = "substage";
        state.callbackReference = "uri";
        state.blocking = false;
        return state;
    }

    private void reset() throws Exception {
        Field f = ExtensibilitySubscriptionManager.class.getDeclaredField("INSTANCE");
        setPrivateField(f, ExtensibilitySubscriptionService.class, null);
    }

}