/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
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
import com.vmware.xenon.common.LocalizableValidationException;

public enum ResourceType {
    CONTAINER_TYPE(ComputeType.DOCKER_CONTAINER.toString(), "App.Container"),
    COMPOSITE_COMPONENT_TYPE("COMPOSITE_COMPONENT", ""),
    JOIN_COMPOSITE_COMPONENT_TYPE("JOIN_COMPOSITE_COMPONENT", ""),
    COMPUTE_TYPE("COMPUTE", "Compute"),
    CONTAINER_HOST_TYPE("CONTAINER_HOST", ""),
    NETWORK_TYPE("NETWORK", "App.Network"),
    VOLUME_TYPE("CONTAINER_VOLUME", "App.Volume"),
    CLOSURE_TYPE("CLOSURE", "App.Closure"),
    CONFIGURE_HOST_TYPE("CONFIGURE_HOST", ""),
    // CONTAINER_LOAD_BALANCER_TYPE is DEPRECATED and should not be used!
    // still having it in the enumeration for proper (de)serialization
    CONTAINER_LOAD_BALANCER_TYPE("CONTAINER_LOAD_BALANCER", "App.LoadBalancer", true),
    KUBERNETES_GENERIC_TYPE("KUBERNETES_GENERIC", "Kubernetes.Generic"),
    KUBERNETES_POD_TYPE("KUBERNETES_POD", "Kubernetes.Pod"),
    KUBERNETES_DEPLOYMENT_TYPE("KUBERNETES_DEPLOYMENT", "Kubernetes.Deployment"),
    KUBERNETES_SERVICE_TYPE("KUBERNETES_SERVICE", "Kubernetes.Service"),
    KUBERNETES_REPLICATION_CONTROLLER_TYPE("KUBERNETES_REPLICATION_CONTROLLER",
            "Kubernetes.ReplicationController"),
    KUBERNETES_REPLICA_SET_TYPE("KUBERNETES_REPLICA_SET", "Kubernetes.ReplicaSet"),
    PKS_CLUSTER_TYPE("PKS_CLUSTER","");

    private final String name;
    private final String contentType;
    private final boolean deprecated;

    ResourceType(String name, String contentType) {
        this(name, contentType, false);
    }

    ResourceType(String name, String contentType, boolean deprecated) {
        this.name = name;
        this.contentType = contentType;
        this.deprecated = deprecated;
    }

    public String getName() {
        if (deprecated) {
            throw new IllegalStateException(this.name() + " is deprecated");
        }
        return name;
    }

    public String getContentType() {
        if (deprecated) {
            throw new IllegalStateException(this.name() + " is deprecated");
        }
        return contentType;
    }

    public static ResourceType fromName(String name) {
        if (name == null || "".equals(name)) {
            throw new LocalizableValidationException("Name cannot be null or empty!",
                    "common.resource-type.name.empty");
        }
        for (ResourceType r : ResourceType.values()) {
            if (r.name.equals(name) && !r.deprecated) {
                return r;
            }
        }
        throw new LocalizableValidationException("No matching type for:" + name,
                "common.resource-type.name.mismatch", name);
    }

    public static ResourceType fromContentType(String contentType) {
        if (contentType == null || "".equals(contentType)) {
            throw new LocalizableValidationException("ContentType cannot be null or empty!",
                    "common.resource-type.content-type.empty");
        }
        for (ResourceType r : ResourceType.values()) {
            if (r.contentType.equals(contentType) && !r.deprecated) {
                return r;
            }
        }
        throw new LocalizableValidationException("No matching type for:" + contentType,
                "common.resource-type.content-type.mismatch", contentType);
    }

    public static String getAllTypesAsString() {
        return Arrays.stream(ResourceType.values())
                .filter(r -> !r.deprecated)
                .map(Object::toString)
                .collect(Collectors.joining(", "));
    }

    /**
     * Get all non deprecated values
     */
    public static ResourceType[] getValues() {
        return Arrays.stream(ResourceType.values())
                .filter(r -> !r.deprecated)
                .toArray(ResourceType[]::new);
    }

}
