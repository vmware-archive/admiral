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
 * Handler defines a specific action that should be taken.
 */
public class Handler {

    /**
     * One and only one of the following should be specified. Exec specifies the action to take.
     */
    public ExecAction exec;

    /**
     * HTTPGet specifies the http request to perform.
     */
    public HTTPGetAction httpGet;

    /**
     * TCPSocket specifies an action involving a TCP port. TCP hooks not yet supported.
     */
    public TCPSocketAction tcpSocket;
}
