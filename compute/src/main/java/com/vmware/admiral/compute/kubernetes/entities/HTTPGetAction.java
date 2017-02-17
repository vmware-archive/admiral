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
 * HTTPGetAction describes an action based on HTTP Get requests.
 */
public class HTTPGetAction {

    /**
     * Path to access on the HTTP server.
     */
    public String path;

    /**
     * Name or number of the port to access on the container.
     */
    public String port;

    /**
     * Host name to connect to, defaults to the pod IP.
     * You probably want to set "Host" in httpHeaders instead.
     */
    public String host;

    /**
     * Scheme to use for connecting to the host. Defaults to HTTP.
     */
    public String scheme;
}
