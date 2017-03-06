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

/**
 * ReplicaSetCondition describes the state of a replica set at a certain point.
 */
public class ReplicaSetCondition {

    /**
     * Type of replica set condition.
     */
    public String type;

    /**
     * Status of the condition, one of True, False, Unknown.
     */
    public String status;

    /**
     * The last time the condition transitioned from one status to another.
     */
    public String lastTransitionTime;

    /**
     * The reason for the condition’s last transition.
     */
    public String reason;

    /**
     * A human readable message indicating details about the transition.
     */
    public String message;
}
