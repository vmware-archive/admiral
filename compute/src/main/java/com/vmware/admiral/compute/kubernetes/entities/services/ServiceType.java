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

public enum ServiceType {

    /**
     * ServiceTypeClusterIP means a service will only be accessible inside the
     * cluster, via the ClusterIP.
     */
    ClusterIP,

    /**
     * ServiceTypeNodePort means a service will be exposed on one port of
     * every node, in addition to 'ClusterIP' type.
     */
    NodePort,

    /**
     * ServiceTypeLoadBalancer means a service will be exposed via an
     * external load balancer (if the cloud provider supports it), in addition
     * to 'NodePort' type.
     */
    LoadBalancer,

    /**
     * ServiceTypeExternalName means a service consists of only a reference to
     * an external name that kubedns or equivalent will return as a CNAME
     * record, with no exposing or proxying of any pods involved.
     */
    ExternalName
}
