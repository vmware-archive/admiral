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

package com.vmware.admiral.compute.kubernetes.entities.replicationcontrollers;

import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;

/**
 * ReplicationController represents the configuration of a replication controller.
 */
public class ReplicationController extends BaseKubernetesObject {

    /**
     * Spec defines the specification of the desired behavior of the replication controller.
     */
    public ReplicationControllerSpec spec;

    /**
     * Status is the most recently observed status of the replication controller. T
     * his data may be out of date by some window of time. Populated by the system.
     */
    public ReplicationControllerStatus status;
}
