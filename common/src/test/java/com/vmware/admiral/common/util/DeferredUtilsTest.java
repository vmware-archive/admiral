/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.junit.Test;

public class DeferredUtilsTest {

    @Test
    public void testLogErrorAndThrow() {
        Exception e = new Exception("err");

        AtomicBoolean b = new AtomicBoolean();
        CompletionException completionException = DeferredUtils.logErrorAndThrow(e, t -> {
            b.set(true);
            return "-";
        }, getClass());

        assertNotNull(completionException);
        assertNotSame(e, completionException);
        assertTrue(b.get());
    }

    @Test
    public void testLogException() {
        Exception e = new Exception("err");

        AtomicBoolean b = new AtomicBoolean();
        DeferredUtils.logException(e, Level.INFO, t -> {
            b.set(true);
            return "-";
        }, getClass());

        assertTrue(b.get());
    }

    @Test
    public void testWrap() {
        Exception e = new Exception("err");

        CompletionException completionException = DeferredUtils.wrap(e);

        assertNotNull(completionException);
        assertNotSame(e, completionException);

        CompletionException completionException2 = DeferredUtils.wrap(completionException);
        assertNotNull(completionException2);
        assertSame(completionException, completionException2);
    }

}