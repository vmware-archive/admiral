/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
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

import com.vmware.xenon.common.LocalizableValidationException;

public class AssertUtil {

    public static final String PROPERTY_CANNOT_BE_EMPTY_MESSAGE_FORMAT = "%s cannot be empty";
    public static final String PROPERTY_MUST_BE_EMPTY_MESSAGE_FORMAT = "%s must be empty";

    public static void assertNotNull(Object value, String propertyName) {
        if (value == null) {
            throw new LocalizableValidationException("'" + propertyName + "' is required",
                    "common.assertion.property.required", propertyName);
        }
    }

    public static void assertNotEmpty(String value, String propertyName) {
        if (value == null || value.isEmpty()) {
            throw new LocalizableValidationException(String.format(PROPERTY_CANNOT_BE_EMPTY_MESSAGE_FORMAT, propertyName),
                    "common.assertion.property.not.empty", propertyName);
        }
    }

    public static void assertNotNullOrEmpty(String value, String propertyName) {
        assertNotNull(value, propertyName);
        assertNotEmpty(value, propertyName);
    }

    public static void assertNotEmpty(Map<?, ?> value, String propertyName) {
        if (value == null || value.isEmpty()) {
            throw new LocalizableValidationException(String.format(PROPERTY_CANNOT_BE_EMPTY_MESSAGE_FORMAT, propertyName),
                    "common.assertion.property.not.empty", propertyName);
        }
    }

    public static void assertNotEmpty(Collection<?> value, String propertyName) {
        if (value == null || value.isEmpty()) {
            throw new LocalizableValidationException(String.format(PROPERTY_CANNOT_BE_EMPTY_MESSAGE_FORMAT, propertyName),
                    "common.assertion.property.not.empty", propertyName);
        }
    }

    public static void assertNotEmpty(Object[] value, String propertyName) {
        if (value == null || value.length == 0) {
            throw new LocalizableValidationException(String.format(PROPERTY_CANNOT_BE_EMPTY_MESSAGE_FORMAT, propertyName),
                    "common.assertion.property.not.empty", propertyName);

        }
    }

    public static void assertEmpty(Collection<?> value, String propertyName) {
        if (value != null && !value.isEmpty()) {
            throw new LocalizableValidationException(
                    String.format(PROPERTY_MUST_BE_EMPTY_MESSAGE_FORMAT, propertyName),
                    "common.assertion.property.empty", propertyName);
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
