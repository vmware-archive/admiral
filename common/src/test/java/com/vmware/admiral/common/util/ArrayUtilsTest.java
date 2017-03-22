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

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.vmware.admiral.common.util.ArrayUtils;

/**
 * Tests for ArrayUtils
 */
public class ArrayUtilsTest {
    @Test
    public void testToStringArrayAlreadyString() {
        Object alreadyStringArray = new String[] { "One", "Two" };

        String[] result = ArrayUtils.toStringArray(alreadyStringArray);

        // since the source was already a string array, the result should be the same object
        assertSame("not same", alreadyStringArray, result);
    }

    @Test
    public void testToStringArray() {
        Object source = new Object[] { "One", "Two" };
        String[] expectedResult = new String[] { "One", "Two" };

        String[] result = ArrayUtils.toStringArray(source);
        assertArrayEquals("not equals", expectedResult, result);
    }
}
