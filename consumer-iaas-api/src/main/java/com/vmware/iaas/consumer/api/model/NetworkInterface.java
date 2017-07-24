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

import com.vmware.iaas.consumer.api.model.base.Resource;

public class NetworkInterface extends Resource {

    @ApiModelProperty(value = "Resource link to the machine instance for this network interface.", required = true)
    public String machineLink;

    @ApiModelProperty(value = "Resource link to the network instance that this network interface plugs into.")
    public String networkLink;

    @ApiModelProperty(value = "A link to the subnet within the network that this Network instance plugs into.")
    public String subnetLink;

    @ApiModelProperty(value = "The device index of this nic.")
    public Integer deviceIndex;

    @ApiModelProperty(value = "A set of security groups that apply to this network interface.")
    public Set<String> securityGroupLinks;

}
