/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.iaas.consumer.api.model;

import java.util.Set;

import io.swagger.annotations.ApiModelProperty;

import com.vmware.iaas.consumer.api.model.base.ZoneScopedResource;

public class LoadBalancer extends ZoneScopedResource {

    @ApiModelProperty(value = "The protocol to load balance.", example = "HTTP, HTTPS")
    public String protocol;

    @ApiModelProperty(value = "The load balancer port.")
    public Integer port;

    @ApiModelProperty(value = "The protocol to use to load balance to the member machines.", example = "HTTP, HTTPS")
    public String memberProtocol;

    @ApiModelProperty(value = "The port to load balance to for all member machines.")
    public Integer memberPort;

    @ApiModelProperty(value = "Resource links to members (load balance targets).")
    public Set<String> memberLinks;

}
