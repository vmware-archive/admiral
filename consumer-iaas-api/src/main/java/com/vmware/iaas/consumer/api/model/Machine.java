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

public class Machine extends ZoneScopedResource {

    @ApiModelProperty(value = "Flavor of machine instance.", example = "small, medium, large")
    public String instanceType; // TODO: THis works for Amazon, Azure, but not for vSphere. Need a Union type with cpu, memory.
    // TODO: Call this flavor?

    @ApiModelProperty(value = "A link to the actual image uesd to provision this machine.")
    public String imageLink;

    @ApiModelProperty(value = "Resource links to the network interfaces of this machine.")
    public Set<String> networkInterfaceLinks;

    @ApiModelProperty(value = "Resource links to the machine-disks of this machine.")
    public Set<String> machineDiskLinks;

}