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

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Utilities for converting from human readable units to raw value for memory and cpu.
 */
public class ConversionUtil {

    private static List<String> BINARY_MEMORY_UNITS = Arrays.asList("b", "kib", "mib", "gib", "tib", "pib");
    private static List<String> DECIMAL_MEMORY_UNITS = Arrays.asList("b", "kb", "mb", "gb", "tb", "pb");
    private static List<String> CPU_UNITS = Arrays.asList("hz", "khz", "mhz", "ghz", "thz", "phz");

    /**
     * Converts a memory value from human readable form to the value in bytes.
     *
     * @param magnitude the magnitude
     * @param unit the unit of the data (comparison is case insensitve)
     * @return the value converted to bytes
     */
    public static double memoryToBytes(double magnitude, String unit) {
        assertNotNull(unit, "unit");
        unit = unit.toLowerCase();
        long base = 1;
        for (String currentUnit : BINARY_MEMORY_UNITS) {
            if (currentUnit.equals(unit)) {
                return magnitude * base;
            }
            base *= 1024;
        }
        base = 1;
        for (String currentUnit : DECIMAL_MEMORY_UNITS) {
            if (currentUnit.equals(unit)) {
                return magnitude * base;
            }
            base *= 1000;
        }
        return 0;
    }

    /**
     * Converts a CPU value from human readable form to the value in Hz.
     *
     * @param magnitude the magnitude
     * @param unit the unit of the data (comparison is case insensitve)
     * @return the value converted to Hz
     */
    public static long cpuToHertz(long magnitude, String unit) {
        assertNotNull(unit, "unit");
        unit = unit.toLowerCase();
        long base = 1;
        for (String currentUnit : CPU_UNITS) {
            if (currentUnit.equals(unit)) {
                return magnitude * base;
            }
            base *= 1000;
        }
        return 0;
    }
}
