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

package com.vmware.admiral.adapter.docker.util;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DEVICE.CGROUP_PERMISSIONS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DEVICE.PATH_IN_CONTAINER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DEVICE.PATH_ON_HOST_PROP_NAME;

import java.util.HashMap;
import java.util.Map;

/**
 * Docker device parsing utility
 */
public class DockerDevice {
    private static final String DEVICE_STRING_SEPARATOR = ":";

    private String pathOnHost;
    private String pathInContainer;
    private String cgroupPermissions;

    public String getPathOnHost() {
        return pathOnHost;
    }

    public String getPathInContainer() {
        return pathInContainer;
    }

    public String getCgroupPermissions() {
        return cgroupPermissions;
    }

    public static DockerDevice fromString(String str) {
        String[] parts = str.split(DEVICE_STRING_SEPARATOR);

        DockerDevice device = new DockerDevice();
        device.pathOnHost = parts[0];
        if (parts.length > 1) {
            device.pathInContainer = parts[1];
        }
        if (parts.length > 2) {
            device.cgroupPermissions = parts[2];
        }

        return device;
    }

    public static DockerDevice fromMap(Map<String, String> map) {
        DockerDevice device = new DockerDevice();
        device.pathOnHost = map.get(PATH_ON_HOST_PROP_NAME);
        if (device.pathOnHost != null) {
            device.pathInContainer = map.get(PATH_IN_CONTAINER_PROP_NAME);
        }
        if (device.pathInContainer != null) {
            device.cgroupPermissions = map.get(CGROUP_PERMISSIONS_PROP_NAME);
        }

        return device;
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        map.put(PATH_ON_HOST_PROP_NAME, pathOnHost);
        map.put(PATH_IN_CONTAINER_PROP_NAME, pathInContainer);
        map.put(CGROUP_PERMISSIONS_PROP_NAME, cgroupPermissions);

        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(pathOnHost);
        if (pathInContainer != null) {
            sb.append(DEVICE_STRING_SEPARATOR);
            sb.append(pathInContainer);

            if (cgroupPermissions != null) {
                sb.append(DEVICE_STRING_SEPARATOR);
                sb.append(cgroupPermissions);
            }
        }

        return sb.toString();
    }
}
