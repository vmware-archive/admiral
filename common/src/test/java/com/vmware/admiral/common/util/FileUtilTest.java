/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import static com.vmware.admiral.common.util.FileUtil.switchToUnixLineEnds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.Test;

/**
 * Tests for FileUtil
 */
public class FileUtilTest {
    private final String TEST_RESOURCE_PROPERTIES_FILE = "/configTest.properties";
    private final String BUILD_NUMBER_PROPERTY_KEY = "__build.number";

    private final String TEST_BACK_SLASHES_PATH_STRING = "\\foo\\bar";
    private final String TEST_FORWARD_SLASHES_PATH_STRING = TEST_BACK_SLASHES_PATH_STRING
            .replace('\\', '/');

    @Test
    public void testGetResourceAsString() {
        String resourceString = FileUtil.getResourceAsString(TEST_RESOURCE_PROPERTIES_FILE, true);
        assertNotNull(resourceString);
        assertTrue(resourceString.contains(BUILD_NUMBER_PROPERTY_KEY));

    }

    @Test
    public void testGetProperties() {
        Properties props = FileUtil.getProperties(TEST_RESOURCE_PROPERTIES_FILE, true);
        assertNotNull(props);
        assertTrue(props.containsKey(BUILD_NUMBER_PROPERTY_KEY));
        assertTrue(props.get(BUILD_NUMBER_PROPERTY_KEY).equals("1"));
    }

    @Test
    public void testGetForwardSlashesPathString() {
        Path path = null;
        String pathString = FileUtil.getForwardSlashesPathString(path);
        assertNull(pathString);

        path = Paths.get(TEST_BACK_SLASHES_PATH_STRING);
        pathString = FileUtil.getForwardSlashesPathString(path);
        assertEquals(TEST_FORWARD_SLASHES_PATH_STRING, pathString);
    }

    @Test
    public void testSwitchToUnixLineEnds() {
        String expectedOutput = "test\n";
        String actualOutput = switchToUnixLineEnds("test\r\n");

        assertEquals(expectedOutput, actualOutput);

        actualOutput = switchToUnixLineEnds(null);
        assertNull(actualOutput);
    }
}
