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

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

public class ServiceUtilsTest {

    @Test
    public void testDefaultTaskExpirationTime() throws Exception {
        long t = ServiceUtils.getDefaultTaskExpirationTimeInMicros();
        long ttl = t - System.currentTimeMillis() * 1000;
        long diffInSeconds = Math.abs(ServiceUtils.EXPIRATION_MICROS - ttl) / 1_000_000;
        assertTrue(diffInSeconds <= 1);
    }

    @Test
    public void testExpirationTimeFromNow() throws Exception {
        long e = 1_000_000;
        long t = ServiceUtils.getExpirationTimeFromNowInMicros(e);
        long ttl = t - System.currentTimeMillis() * 1000;
        long diffInSeconds = Math.abs(e - ttl) / 1_000_000;
        assertTrue(diffInSeconds <= 1);
    }

    @Test
    public void sendSelfDelete() throws Exception {
        ServiceUtils.sendSelfDelete(new StatelessService() {
            private final String uri = "uri";
            @Override
            public URI getUri() {
                return URI.create(uri);
            }

            @Override
            public void sendRequest(Operation op) {
                assertEquals(Action.DELETE, op.getAction());
                assertEquals(uri, op.getUri().toString());
            }
        });
    }

    @Test
    public void addServiceRequestRoute() throws Exception {
        ServiceUtils.addServiceRequestRoute(null, Service.Action.GET,
                "description", ServiceDocument.class);

        ServiceDocument document = new ServiceDocument();
        ServiceUtils.addServiceRequestRoute(document, Service.Action.GET,
                "description", ServiceDocument.class);
        assertNotNull(document.documentDescription);
        assertNotNull(document.documentDescription.serviceRequestRoutes);
        assertNotNull(document.documentDescription.serviceRequestRoutes.get(Service.Action.GET));
    }

    @Test
    public void testHandleExceptions() throws Exception {
        final AtomicInteger flag = new AtomicInteger();
        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                flag.set(-1);
            } else {
                flag.addAndGet(10);
            }
        };

        // case 1 - no operation, no callback function
        ServiceUtils.handleExceptions(null, null);

        // case 2 - no operation with callback function
        ServiceUtils.handleExceptions(null, () -> flag.set(1));
        assertEquals(1, flag.get());

        // case 3 - no operation with callback function throwing exception
        ServiceUtils.handleExceptions(null, () -> {
            throw new RuntimeException("error");
        });

        // case 4 - with operation and callback
        Operation op4 = Operation.createGet(null).setCompletion(completionHandler);
        ServiceUtils.handleExceptions(op4, () -> flag.set(0));
        assertEquals(0, flag.get());

        // case 5 - with operation and callback and op complete
        final Operation op5 = Operation.createGet(null).setCompletion(completionHandler);
        ServiceUtils.handleExceptions(op5, () -> {
            flag.set(0);
            op5.complete();
        });
        assertEquals(10, flag.get());

        // case 6 - with operation and callback throwing exception
        Operation op6 = Operation.createGet(null).setCompletion(completionHandler);
        ServiceUtils.handleExceptions(op6, () -> {
            flag.set(0);
            throw new RuntimeException("error");
        });
        assertEquals(-1, flag.get());
    }

    @Test
    public void testIsExpired() {
        assertFalse(ServiceUtils.isExpired(null));

        ServiceDocument sd = new ServiceDocument();
        sd.documentExpirationTimeMicros = 0;
        assertFalse(ServiceUtils.isExpired(new ServiceDocument()));

        sd.documentExpirationTimeMicros = Utils.getSystemNowMicrosUtc()
                - TimeUnit.MINUTES.toMicros(1);
        assertTrue(ServiceUtils.isExpired(sd));

        sd.documentExpirationTimeMicros = Utils.getSystemNowMicrosUtc()
                + TimeUnit.MINUTES.toMicros(1);
        assertFalse(ServiceUtils.isExpired(sd));
    }
}