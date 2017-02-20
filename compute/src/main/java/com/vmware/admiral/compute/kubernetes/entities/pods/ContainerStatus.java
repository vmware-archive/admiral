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

package com.vmware.admiral.compute.kubernetes.entities.pods;

/**
 * ContainerStatus contains details for the current status of this container.
 */
public class ContainerStatus {
    /**
     * This must be a DNS_LABEL. Each container in a pod must have a unique name.
     * Cannot be updated.
     */
    public String name;

    /**
     * Details about the container’s current condition.
     */
    public ContainerState state;

    /**
     * Details about the container’s last termination condition.
     */
    public ContainerState lastState;

    /**
     * Specifies whether the container has passed its readiness probe.
     */
    public Boolean ready;

    /**
     * The number of times the container has been restarted, currently based on the number of
     * dead containers that have not yet been removed.
     */
    public Integer restartCount;

    /**
     * The image the container is running.
     */
    public String image;

    /**
     * ImageID of the container’s image.
     */
    public String imageID;

    /**
     * Container’s ID in the format docker://<container_id>.
     */
    public String containerID;
}
