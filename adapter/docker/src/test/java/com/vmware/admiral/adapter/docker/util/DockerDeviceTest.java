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

package com.vmware.admiral.adapter.docker.util;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DEVICE.CGROUP_PERMISSIONS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DEVICE.PATH_IN_CONTAINER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DEVICE.PATH_ON_HOST_PROP_NAME;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test for DockerDevice parsing methods
 */
@RunWith(Parameterized.class)
public class DockerDeviceTest {
    private final String description;
    private final String fullDeviceString;
    private final String expectedHostPath;
    private final String expectedContainerPath;
    private final String expectedPermissions;

    @Parameters
    public static List<String[]> data() {
        String[][] data = {
                { "all parts", "/dev/null:/dev/null2:rwm", "/dev/null", "/dev/null2", "rwm" },
                { "two part", "/dev/null:/dev/null2", "/dev/null", "/dev/null2", null },
                { "one part", "/dev/null", "/dev/null", null, null }
        };
        return Arrays.asList(data);
    }

    public DockerDeviceTest(String description, String fullDeviceString, String expectedHostPath,
            String expectedContainerPath, String expectedPermissions) {

        this.description = description;
        this.fullDeviceString = fullDeviceString;
        this.expectedHostPath = expectedHostPath;
        this.expectedContainerPath = expectedContainerPath;
        this.expectedPermissions = expectedPermissions;
    }

    @Test
    public void testConversions() {
        DockerDevice device = DockerDevice.fromString(fullDeviceString);

        // fromString
        verifyDevice("fromString: " + description, device);

        // toMap
        Map<String, String> map = device.toMap();
        verifyMap("toMap: " + description, map);

        // fromMap
        device = DockerDevice.fromMap(map);
        verifyDevice("fromMap: " + description, device);

        // toString
        assertEquals("toString: " + description, fullDeviceString, device.toString());
    }

    private void verifyDevice(String messagePrefix, DockerDevice device) {
        assertEquals(messagePrefix + ": host path", expectedHostPath, device.getPathOnHost());
        assertEquals(messagePrefix + ": container path", expectedContainerPath,
                device.getPathInContainer());
        assertEquals(messagePrefix + ": permissions", expectedPermissions,
                device.getCgroupPermissions());
    }

    private void verifyMap(String messagePrefix, Map<String, String> map) {
        assertEquals(messagePrefix + ": host path", expectedHostPath,
                map.get(PATH_ON_HOST_PROP_NAME));
        assertEquals(messagePrefix + ": container path", expectedContainerPath,
                map.get(PATH_IN_CONTAINER_PROP_NAME));
        assertEquals(messagePrefix + ": permissions", expectedPermissions,
                map.get(CGROUP_PERMISSIONS_PROP_NAME));
    }
}
