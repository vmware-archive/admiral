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

package com.vmware.admiral.compute.kubernetes.entities;

/**
 * PodCondition contains details for the current condition of this pod.
 */
public class PodCondition {

    /**
     * Type is the type of the condition.
     */
    public String type;

    /**
     * Status is the status of the condition. Can be True, False, Unknown.
     */
    public String status;

    /**
     * Last time we probed the condition.
     */
    public String lastProbeTime;

    /**
     * Last time the condition transitioned from one status to another.
     */
    public String lastTransitionTime;

    /**
     * Unique, one-word, CamelCase reason for the condition’s last transition.
     */
    public String reason;

    /**
     * Human-readable message indicating details about last transition.
     */
    public String message;
}
