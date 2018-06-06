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

package com.vmware.admiral.adapter.pks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PKSExceptionTest {

    @Test
    public void testErrorCode() {
        int n = (int) (System.currentTimeMillis() % 10000);
        PKSException e = new PKSException("err", null, n);

        assertNotNull(e);
        assertNull(e.getCause());
        assertEquals(n, e.getErrorCode());
        assertEquals("err", e.getMessage());
    }
}