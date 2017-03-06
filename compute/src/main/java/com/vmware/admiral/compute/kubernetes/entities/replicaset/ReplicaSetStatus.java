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

package com.vmware.admiral.compute.kubernetes.entities.replicaset;

import java.util.List;

/**
 * ReplicaSetStatus represents the current status of a ReplicaSet.
 */
public class ReplicaSetStatus {

    /**
     * Replicas is the most recently oberved number of replicas.
     */
    public Integer replicas;

    /**
     * The number of pods that have labels matching the labels of the pod template of the replicaset.
     */
    public Integer fullyLabeledReplicas;

    /**
     * The number of ready replicas for this replica set.
     */
    public Integer readyReplicas;

    /**
     * The number of available replicas (ready for at least minReadySeconds) for this replica set.
     */
    public Integer availableReplicas;

    /**
     * ObservedGeneration reflects the generation of the most recently observed ReplicaSet.
     */
    public Long observedGeneration;

    /**
     * Represents the latest available observations of a replica set’s current state.
     */
    public List<ReplicaSetCondition> conditions;

}
