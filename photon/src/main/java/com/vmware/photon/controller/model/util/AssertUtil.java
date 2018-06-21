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

package com.vmware.photon.controller.model.util;

/**
 * This class assists in asserting arguments.
 */
public class AssertUtil {

    /**
     * Assert that the specified argument is not {@code null} otherwise throwing
     * {@link IllegalArgumentException} with the specified {@code errorMessage}.
     * <p>
     * {@code AssertUtil.assertNotNull(someArg, "'someArg' must be set.");}
     * @param obj
     *         the object to validate
     * @param errorMessage
     *         the exception message if invalid
     * @throws IllegalArgumentException
     *         if provided {@code obj} is {@code null}
     */
    public static void assertNotNull(Object obj, String errorMessage) {
        if (obj == null) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Assert that provided {@code expression} is {@code true} otherwise throwing an
     * {@link IllegalArgumentException} with the specified {@code errorMessage}.
     * <p>
     * {@code AssertUtil.assertTrue(arr.length>0, "'arr' shall not be empty.");}
     * @param expression
     *         the boolean expression to check
     * @param errorMessage
     *         the exception message if invalid
     * @throws IllegalArgumentException
     *         if expression is <code>false</code>
     */
    public static void assertTrue(boolean expression, String errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Assert that provided {@code expression} is {@code false} otherwise throwing an
     * {@link IllegalArgumentException} with the specified {@code errorMessage}.
     * <p>
     * {@code AssertUtil.assertFalse(arr.length>0, "'arr' shall be empty.");}
     * @param expression
     *         the boolean expression to check
     * @param errorMessage
     *         the exception message if invalid
     * @throws IllegalArgumentException
     *         if expression is <code>false</code>
     */
    public static void assertFalse(boolean expression, String errorMessage) {
        if (expression) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public static void assertNotEmpty(String value, String errorMessage) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

}
