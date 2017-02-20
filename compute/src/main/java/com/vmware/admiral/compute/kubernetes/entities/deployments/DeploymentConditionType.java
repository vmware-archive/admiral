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

public enum DeploymentConditionType {

    /**
     * Available means the deployment is available, ie. at least the minimum available
     * replicas required are up and running for at least minReadySeconds.
     */
    Available,

    /**
     * Progressing means the deployment is progressing. Progress for a deployment is
     * considered when a new replica set is created or adopted, and when new pods scale
     * up or old pods scale down. Progress is not estimated for paused deployments or
     * when progressDeadlineSeconds is not specified.
     */
    Progressing,

    /**
     * ReplicaFailure is added in a deployment when one of its pods fails to be created
     * or deleted.
     */
    ReplicaFailure
}
