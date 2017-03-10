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

/**
 * NodeSpec describes the attributes that a node is created with.
 */
public class NodeSpec {

    /**
     * PodCIDR represents the pod IP range assigned to the node.
     */
    public String podCIDR;

    /**
     * External ID of the node assigned by some machine database (e.g. a cloud provider). Deprecated.
     */
    public String externalID;

    /**
     * ID of the node assigned by the cloud provider in
     * the format: <ProviderName>://<ProviderSpecificNodeID>
     */
    public String providerID;

    /**
     * Unschedulable controls node schedulability of new pods. By default, node is schedulable.
     */
    public Boolean unschedulable;
}
