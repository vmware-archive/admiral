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

import java.util.Arrays;

/**
 * Utilities for dealing with arrays
 */
public class ArrayUtils {
    /**
     * Convert an Object array to a String array by invoking toString on each element
     *
     * @param value
     * @return String[]
     */
    public static String[] toStringArray(Object value) {
        if (value instanceof String[]) {
            // already a string array, just need a cast
            return (String[]) value;

        } else {
            // perform a toString on each element and return a new array
            Object[] valueArray = (Object[]) value;
            return Arrays.stream(valueArray)
                    .map(o -> o.toString())
                    .toArray(size -> new String[size]);
        }
    }

}
