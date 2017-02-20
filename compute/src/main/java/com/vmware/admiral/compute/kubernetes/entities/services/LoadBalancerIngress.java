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

package com.vmware.admiral.compute.kubernetes.entities.services;

/**
 * LoadBalancerIngress represents the status of a load-balancer ingress point: traffic intended
 * for the service should be sent to an ingress point.
 */
public class LoadBalancerIngress {

    /**
     * IP is set for load-balancer ingress points that are
     * IP based (typically GCE or OpenStack load-balancers)
     */
    public String ip;

    /**
     * Hostname is set for load-balancer ingress points that are
     * DNS based (typically AWS load-balancers)
     */
    public String hostname;
}
