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

import org.junit.Test;

public class ConversionUtilTest {

    @Test
    public void testBinaryMemoryToBytes() throws Exception {
        assertEquals(2.0, ConversionUtil.memoryToBytes(2, "B"), 0.01);
        assertEquals(2048.0, ConversionUtil.memoryToBytes(2, "KiB"), 0.01);
        assertEquals(2097152.0, ConversionUtil.memoryToBytes(2, "MiB"), 0.01);
        assertEquals(2147483648.0, ConversionUtil.memoryToBytes(2, "GiB"), 0.01);
        assertEquals(2199023255552.0, ConversionUtil.memoryToBytes(2, "TiB"), 0.01);
        assertEquals(2251799813685248.0, ConversionUtil.memoryToBytes(2, "PiB"), 0.01);
    }

    @Test
    public void testDecimalMemoryToBytes() throws Exception {
        assertEquals(2.0, ConversionUtil.memoryToBytes(2, "B"), 0.01);
        assertEquals(2000.0, ConversionUtil.memoryToBytes(2, "KB"), 0.01);
        assertEquals(2000000.0, ConversionUtil.memoryToBytes(2, "MB"), 0.01);
        assertEquals(2000000000.0, ConversionUtil.memoryToBytes(2, "GB"), 0.01);
        assertEquals(2000000000000.0, ConversionUtil.memoryToBytes(2, "TB"), 0.01);
        assertEquals(2000000000000000.0, ConversionUtil.memoryToBytes(2, "PB"), 0.01);
    }

    @Test
    public void testBinaryMemory() throws Exception {
        assertEquals(2.0, ConversionUtil.memoryBinaryConversion(2, "B", "B"), 0.01);
        assertEquals(2048.0, ConversionUtil.memoryBinaryConversion(2, "KiB", "B"), 0.01);
        assertEquals(2097152.0, ConversionUtil.memoryBinaryConversion(2, "MiB", "B"), 0.01);
        assertEquals(2147483648.0, ConversionUtil.memoryBinaryConversion(2, "GiB", "B"), 0.01);
        assertEquals(2199023255552.0, ConversionUtil.memoryBinaryConversion(2, "TiB", "B"), 0.01);
        assertEquals(2251799813685248.0, ConversionUtil.memoryBinaryConversion(2, "PiB", "B"), 0.01);
    }

    @Test
    public void testDecimalMemory() throws Exception {
        assertEquals(2.0, ConversionUtil.memoryDecimalConversion(2, "B", "B"), 0.01);
        assertEquals(2000.0, ConversionUtil.memoryDecimalConversion(2, "KB", "B"), 0.01);
        assertEquals(2000000.0, ConversionUtil.memoryDecimalConversion(2, "MB", "B"), 0.01);
        assertEquals(2000000000.0, ConversionUtil.memoryDecimalConversion(2, "GB", "B"), 0.01);
        assertEquals(2000000000000.0, ConversionUtil.memoryDecimalConversion(2, "TB", "B"), 0.01);
        assertEquals(2000000000000000.0, ConversionUtil.memoryDecimalConversion(2, "PB", "B"), 0.01);
    }

    @Test
    public void testCpuToHertz() throws Exception {
        assertEquals(2, ConversionUtil.cpuToHertz(2, "Hz"));
        assertEquals(2000, ConversionUtil.cpuToHertz(2, "KHz"));
        assertEquals(2000000, ConversionUtil.cpuToHertz(2, "MHz"));
        assertEquals(2000000000L, ConversionUtil.cpuToHertz(2, "GHz"));
        assertEquals(2000000000000L, ConversionUtil.cpuToHertz(2, "THz"));
        assertEquals(2000000000000000L, ConversionUtil.cpuToHertz(2, "PHz"));
        assertEquals(0, ConversionUtil.cpuToHertz(0, "NHz"));
    }

    @Test
    public void testMemoryConversion() throws Exception {
        assertEquals(2147483648.0, ConversionUtil.memoryBinaryConversion(2.0, "GiB", "B"), 0.01);
        assertEquals(2097152.0, ConversionUtil.memoryBinaryConversion(2.0, "GiB", "kiB"), 0.01);
        assertEquals(2048.0, ConversionUtil.memoryBinaryConversion(2.0, "GiB", "MiB"), 0.01);
        assertEquals(2.0, ConversionUtil.memoryBinaryConversion(2.0, "GiB", "GiB"), 0.01);

        assertEquals(2097152.0, ConversionUtil.memoryBinaryConversion(2.0, "MiB", "B"), 0.01);
        assertEquals(2048.0, ConversionUtil.memoryBinaryConversion(2.0, "MiB", "kiB"), 0.01);
        assertEquals(2.0, ConversionUtil.memoryBinaryConversion(2.0, "MiB", "MiB"), 0.01);
        assertEquals(0.00195, ConversionUtil.memoryBinaryConversion(2.0, "MiB", "GiB"), 0.00001);
    }
}
