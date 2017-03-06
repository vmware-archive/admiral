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

import com.vmware.admiral.compute.kubernetes.entities.common.LabelSelector;
import com.vmware.admiral.compute.kubernetes.entities.pods.PodTemplateSpec;

/**
 * ReplicaSetSpec is the specification of a ReplicaSet.
 */
public class ReplicaSetSpec {

    /**
     * Replicas is the number of desired replicas.
     * This is a pointer to distinguish between explicit zero and unspecified. Defaults to 1.
     */
    public Integer replicas;

    /**
     * Minimum number of seconds for which a newly created pod should be ready without any of
     * its container crashing, for it to be considered available. Defaults to 0
     */
    public Integer minReadySeconds;

    /**
     * Selector is a label query over pods that should match the replica count.
     * If the selector is empty, it is defaulted to the labels present on the pod template.
     * Label keys and values that must match in order to be controlled by this replica set.
     */
    public LabelSelector selector;

    /**
     * Template is the object that describes the pod that will be created
     * if insufficient replicas are detected.
     */
    public PodTemplateSpec template;

}
