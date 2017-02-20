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

package com.vmware.admiral.compute.kubernetes.entities.deployments;

import java.util.List;

/**
 * DeploymentStatus is the most recently observed status of the Deployment.
 */
public class DeploymentStatus {

    /**
     * The generation observed by the deployment controller.
     */
    public Long observedGeneration;

    /**
     * Total number of non-terminated pods targeted by this deployment (their labels match the selector).
     */
    public Integer replicas;

    /**
     * Total number of non-terminated pods targeted by this deployment that have the desired template spec.
     */
    public Integer updatedReplicas;

    /**
     * Total number of ready pods targeted by this deployment.
     */
    public Integer readyReplicas;

    /**
     * Total number of available pods (ready for at least minReadySeconds) targeted by this deployment.
     */
    public Integer availableReplicas;

    /**
     * Total number of unavailable pods targeted by this deployment.
     */
    public Integer unavailableReplicas;

    /**
     * Represents the latest available observations of a deployment's current state.
     */
    public List<DeploymentCondition> conditions;
}
