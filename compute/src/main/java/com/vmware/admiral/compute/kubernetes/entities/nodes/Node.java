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

import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;

/**
 * Node is a worker node in Kubernetes. Each node will have a unique
 * identifier in the cache (i.e. in etcd).
 */
public class Node extends BaseKubernetesObject {

    /**
     * Spec defines the behavior of a node.
     */
    public NodeSpec spec;

    /**
     * Most recently observed status of the node. Populated by the system.
     */
    public NodeStatus status;
}
