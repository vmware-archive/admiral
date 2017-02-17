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
 * Probe describes a health check to be performed against a container to determine
 * whether it is alive or ready to receive traffic.
 */
public class Probe {

    /**
     * One and only one of the following should be specified. Exec specifies the action to take.
     */
    public ExecAction exec;

    /**
     * HTTPGet specifies the http request to perform.
     */
    public HTTPGetAction httpGet;

    /**
     * TCPSocket specifies an action involving a TCP port. TCP hooks not yet supported
     */
    public TCPSocketAction tcpSocket;

    /**
     * Number of seconds after the container has started before liveness probes are initiated.
     */
    public Integer initialDelaySeconds;

    /**
     * Number of seconds after which the probe times out. Defaults to 1 second. Minimum value is 1.
     */
    public Integer timeoutSeconds;

    /**
     * How often (in seconds) to perform the probe. Default to 10 seconds. Minimum value is 1.
     */
    public Integer periodSeconds;

    /**
     * Minimum consecutive successes for the probe to be considered successful after
     * having failed. Defaults to 1. Must be 1 for liveness.
     */
    public Integer successThreshold;

    /**
     * Minimum consecutive failures for the probe to be considered failed after having succeeded.
     * Defaults to 3.
     */
    public Integer failureThreshold;
}
