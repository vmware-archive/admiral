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
 * ServicePort contains information on service’s port.
 */
public class ServicePort {

    /**
     * The name of this port within the service. This must be a DNS_LABEL.
     * All ports within a ServiceSpec must have unique names.
     * This maps to the Name field in EndpointPort objects.
     */
    public String name;

    /**
     * The IP protocol for this port. Supports "TCP" and "UDP". Default is TCP.
     */
    public String protocol;

    /**
     * The port that will be exposed by this service.
     */
    public Integer port;

    /**
     * Number or name of the port to access on the pods targeted by the service.
     * Number must be in the range 1 to 65535. Name must be an IANA_SVC_NAME. If this is a string,
     * it will be looked up as a named port in the target Pod’s container ports.
     * If this is not specified, the value of the port field is used (an identity map).
     * This field is ignored for services with clusterIP=None, and should be omitted
     * or set equal to the port field.
     */
    public String targetPort;

    /**
     * The port on each node on which this service is exposed when type=NodePort or LoadBalancer.
     * Usually assigned by the system. If specified, it will be allocated to the service if
     * unused or else creation of the service will fail. Default is to auto-allocate a port
     * if the ServiceType of this Service requires one.
     */
    public Integer nodePort;
}
