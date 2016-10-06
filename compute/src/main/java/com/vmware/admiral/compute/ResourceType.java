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

package com.vmware.admiral.compute;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;

public enum ResourceType {
    CONTAINER_TYPE(ComputeType.DOCKER_CONTAINER.toString(), "Container.Docker"),
    COMPOSITE_COMPONENT_TYPE("COMPOSITE_COMPONENT", ""),
    COMPUTE_TYPE("COMPUTE", "Compute"),
    CONTAINER_HOST_TYPE("CONTAINER_HOST", ""),
    NETWORK_TYPE("NETWORK", "App.Network"),
    VOLUME_TYPE("VOLUME", "Volume.Docker");

    private final String name;
    private final String contentType;

    private ResourceType(String name, String contentType) {
        this.name = name;
        this.contentType = contentType;
    }

    public String getName() {
        return name;
    }

    public String getContentType() {
        return contentType;
    }

    public static ResourceType fromName(String name) {
        if (name == null || "".equals(name)) {
            throw new IllegalArgumentException("Name cannot be null or empty!");
        }
        for (ResourceType r : ResourceType.values()) {
            if (r.name.equals(name)) {
                return r;
            }
        }
        throw new IllegalArgumentException("No matching type for:" + name);
    }

    public static ResourceType fromContentType(String contentType) {
        if (contentType == null || "".equals(contentType)) {
            throw new IllegalArgumentException("ContentType cannot be null or empty!");
        }
        for (ResourceType r : ResourceType.values()) {
            if (r.contentType.equals(contentType)) {
                return r;
            }
        }
        throw new IllegalArgumentException("No matching type for:" + contentType);
    }

    public static String getAllTypesAsString() {
        return Arrays.asList(ResourceType.values()).stream().map(Object::toString)
                .collect(Collectors.joining(", "));
    }
}
