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

import java.util.List;

/**
 * PodStatus represents information about the status of a pod.
 * Status may trail the actual state of a system.
 */
public class PodStatus {

    /**
     * Current condition of the pod.
     */
    public String phase;

    /**
     * Current service state of pod.
     */
    public List<PodCondition> conditions;

    /**
     * A human readable message indicating details about why the pod is in this condition.
     */
    public String message;

    /**
     * A brief CamelCase message indicating details about
     * why the pod is in this state. e.g. OutOfDisk
     */
    public String reason;

    /**
     * IP address of the host to which the pod is assigned. Empty if not yet scheduled.
     */
    public String hostIP;

    /**
     * IP address allocated to the pod. Routable at least within the cluster.
     * Empty if not yet allocated.
     */
    public String podIP;

    /**
     * RFC 3339 date and time at which the object was acknowledged by the Kubelet.
     */
    public String startTime;

    /**
     * The list has one entry per container in the manifest.
     * Each entry is currently the output of docker inspect. More info:
     */
    public List<ContainerStatus> containerStatuses;
}
