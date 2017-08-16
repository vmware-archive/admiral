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

package com.vmware.iaas.consumer.api.model.specifications;

import java.util.Set;

import io.swagger.annotations.ApiModelProperty;

import com.vmware.iaas.consumer.api.model.base.ResourceSpecification;

public class NetConnectivitySpecification extends ResourceSpecification {

    @ApiModelProperty(value = "A set of TCP/IP ports that this network link should expose.")
    public Set<Integer> ports;

    // Note: Only one of the following two fields should be specified.
    @ApiModelProperty(value = "A specification that defines the network that this network interface should plug into.")
    public NetworkSpecification networkSpecification;
    @ApiModelProperty(value = "Resource link to a network instance that this network interface should plug into.")
    public String networkLink;

}
