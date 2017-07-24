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

public class MachineSpecification extends ResourceSpecification {

    @ApiModelProperty(value = "Flavor of machine instance.", example = "small, medium, large")
    public String instanceType;

    @ApiModelProperty(value = "Type of image used for this machine.", example = "vmware-gold-master, ubuntu-latest, rhel-compliant, windows")
    public String imageType;

    @ApiModelProperty(value = "A set of network interface specifications for this machine.")
    public Set<NetConnectivitySpecification> networkInterfaceSpecifications;

    @ApiModelProperty(value = "A set of disk specifications for this machine.")
    public Set<DiskAttachmentSpecification> machineDiskSpecifications;

}