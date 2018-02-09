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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class VersionUtilTest {

    @Test
    public void testExtractNumericVersion() {
        assertEquals("18.01.0", VersionUtil.extractNumericVersion("18.01.0-ce"));
        assertEquals("1.2.0", VersionUtil.extractNumericVersion("v1.2.0-13652-9d146bc"));
        assertEquals("1", VersionUtil.extractNumericVersion("1"));
        try {
            String noIntegerValue = "no-integer";
            VersionUtil.extractNumericVersion(noIntegerValue);
            fail(String.format("Should not be able to parse an integer from '%s'", noIntegerValue));
        } catch (IllegalArgumentException ex) {
            assertTrue(
                    ex.getMessage().startsWith(VersionUtil.CANNOT_EXTRACT_NUMERIC_VERSION_MESSAGE));
        }
    }

    @Test
    public void testCompareNumericVersions() {
        assertEquals(0, VersionUtil.compareNumericVersions("18.01.0", "18.1.0"));
        assertEquals(0, VersionUtil.compareNumericVersions("1.2.3", "1.2.3"));
        assertEquals(1, VersionUtil.compareNumericVersions("1.2.3", "1.2"));
        assertEquals(1, VersionUtil.compareNumericVersions("1.2.3", "1.2.1"));
        assertEquals(-1, VersionUtil.compareNumericVersions("1", "1.2.1"));
        assertEquals(-1, VersionUtil.compareNumericVersions("1.2.0", "1.2.1"));
    }

    @Test
    public void testCompareRawVersions() {
        assertEquals(0, VersionUtil.compareRawVersions("18.01.0-ce", "v18.1.0"));
        assertEquals(0, VersionUtil.compareRawVersions("v1.2.3", "1.2.3-abcde1234"));
        assertEquals(1, VersionUtil.compareRawVersions("ver1.2.3", "1.2"));
        assertEquals(1, VersionUtil.compareRawVersions("version1.2.3", "v1.2.1"));
        assertEquals(-1, VersionUtil.compareRawVersions("rel1", "1.2.1-qe"));
        assertEquals(-1, VersionUtil.compareRawVersions("sth1.2.0", "1.2.1sth"));
    }

}
