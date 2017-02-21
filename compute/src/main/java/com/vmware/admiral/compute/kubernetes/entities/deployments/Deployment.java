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

import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;

/**
 * Deployment enables declarative updates for Pods and ReplicaSets.
 */
public class Deployment extends BaseKubernetesObject {

    /**
     * Specification of the desired behavior of the Deployment.
     */
    public DeploymentSpec spec;

    /**
     * Most recently observed status of the Deployment.
     */
    public DeploymentStatus status;
}
