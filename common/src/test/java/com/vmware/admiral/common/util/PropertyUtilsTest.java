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

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

public class PropertyUtilsTest {

    @Test
    public void getPropertyLongTest() {
        Map<String, String> props = new HashMap<>();
        String testKey = "testKey";
        String testValue = "5";
        props.put(testKey, testValue);

        Optional<Long> val = PropertyUtils.getPropertyLong(props, testKey);
        assertEquals(Long.valueOf(testValue), val.get());

        props.put(testKey, "non-number");
        val = PropertyUtils.getPropertyLong(props, testKey);
        assertEquals(Optional.empty(), val);
    }

    @Test
    public void getPropertyDoubleTest() {
        Map<String, String> props = new HashMap<>();
        String testKey = "testKey";
        String testValue = "5.55";
        props.put(testKey, testValue);

        Optional<Double> val = PropertyUtils.getPropertyDouble(props, testKey);
        assertEquals(Double.valueOf(testValue), val.get());

        props.put(testKey, "non-number");
        val = PropertyUtils.getPropertyDouble(props, testKey);
        assertEquals(Optional.empty(), val);
    }

    @Test
    public void setPropertyDoubleTest() {
        Map<String, Object> props = new HashMap<>();
        String testKey = "testKey";
        String testValue = "5.55";

        props = PropertyUtils.setPropertyDouble(props, testKey, testValue);
        assertEquals(Double.valueOf(testValue), props.get(testKey));

        props = PropertyUtils.setPropertyDouble(props, testKey, "non-value");
        assertEquals(Double.valueOf(testValue), props.get(testKey));
    }

}
