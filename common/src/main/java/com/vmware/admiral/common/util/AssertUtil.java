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

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Collection;
import java.util.Map;

public class AssertUtil {

    public static void assertNotNull(Object value, String propertyName) {
        if (value == null) {
            throw new IllegalArgumentException("'" + propertyName + "' is required");
        }
    }

    public static void assertNotEmpty(String value, String propertyName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("'" + propertyName + "' cannot be empty");
        }
    }

    public static void assertNotNullOrEmpty(String value, String propertyName) {
        assertNotNull(value, propertyName);
        assertNotEmpty(value, propertyName);
    }

    public static void assertNotEmpty(Map<?, ?> value, String propertyName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("'" + propertyName + "' cannot be empty");
        }
    }

    public static void assertNotEmpty(Collection<?> value, String propertyName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("'" + propertyName + "' cannot be empty");
        }
    }

    public static void assertNotEmpty(Object[] value, String propertyName) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("'" + propertyName + "' cannot be empty");
        }
    }

    public static void assertTrue(boolean condition, String errMsg) {
        if (!condition) {
            throw new IllegalArgumentException(errMsg);
        }
    }

    public static void assertState(boolean state, String errMsg) {
        if (!state) {
            throw new IllegalStateException(errMsg);
        }
    }

    public static boolean isNumeric(String str) {
        NumberFormat formatter = NumberFormat.getInstance();
        ParsePosition pos = new ParsePosition(0);
        formatter.parse(str, pos);
        return str.length() == pos.getIndex();
    }
}
