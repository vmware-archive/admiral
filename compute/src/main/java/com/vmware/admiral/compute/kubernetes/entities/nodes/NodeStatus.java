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

package com.vmware.admiral.compute.kubernetes.entities.nodes;

import java.util.Map;

/**
 * NodeStatus is information about the current status of a node.
 */
public class NodeStatus {

    /**
     * Capacity represents the total resources of a node.
     */
    public Map<Object, Object> capacity;

    /**
     * Allocatable represents the resources of a node that are available for scheduling.
     * Defaults to Capacity.
     */
    public Map<Object, Object> allocatable;

    /**
     *
     */
    public String phase;

    // TODO: Add the rest required object in case they are needed when we start managing k8s nodes.
}
