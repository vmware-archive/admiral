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

package com.vmware.admiral.compute.container.loadbalancer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Load balancer backend health check mechanism
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContainerLoadBalancerHealthConfig {

    public String protocol;
    public Integer port;
    public String path;
}
