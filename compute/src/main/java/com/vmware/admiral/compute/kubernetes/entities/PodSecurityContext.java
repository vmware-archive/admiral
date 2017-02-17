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

package com.vmware.admiral.compute.kubernetes.entities;

import java.util.List;

/**
 * PodSecurityContext holds pod-level security attributes and common container settings.
 */
public class PodSecurityContext {

    /**
     * The UID to run the entrypoint of the container process.
     * Defaults to user specified in image metadata if unspecified.
     * May also be set in SecurityContext. If set in both SecurityContext and PodSecurityContext,
     * the value specified in SecurityContext takes precedence for that container.
     */
    public Long runAsUser;

    /**
     * Indicates that the container must run as a non-root user.
     * If true, the Kubelet will validate the image at runtime to ensure that it does not run as
     * UID 0 (root) and fail to start the container if it does.
     */
    public Boolean runAsNonRoot;

    /**
     * A list of groups applied to the first process run in each container,
     * in addition to the container’s primary GID.
     */
    public List<Integer> supplementalGroups;

    /**
     * A special supplemental group that applies to all containers in a pod.
     * Some volume types allow the Kubelet to change the ownership of that volume to be owned by the pod:
     */
    public Long fsGroup;
}
