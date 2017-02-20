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

package com.vmware.admiral.compute.kubernetes.entities.pods;

/**
 * ContainerPort represents a network port in a single container.
 */
public class ContainerPort {

    /**
     * If specified, this must be an IANA_SVC_NAME and unique within the pod.
     * Each named port in a pod must have a unique name.
     */
    public String name;

    /**
     * Number of port to expose on the host.
     */
    public Integer hostPort;

    /**
     * Number of port to expose on the pod’s IP address.
     */
    public Integer containerPort;

    /**
     * Protocol for port. Must be UDP or TCP. Defaults to "TCP".
     */
    public String protocol;

    /**
     * What host IP to bind the external port to.
     */
    public String hostIP;
}
