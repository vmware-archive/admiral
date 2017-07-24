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

import io.swagger.annotations.ApiModelProperty;

import com.vmware.iaas.consumer.api.model.base.ResourceSpecification;

public class NetworkSpecification extends ResourceSpecification {

    @ApiModelProperty(value = "The type of this network.", example = "public, isolated")
    public NetworkType networkType;

    @ApiModelProperty(value = "The type of IP address assignment to use for machines on this network.", example = "static, dynamic")
    public IpAssignment assignment;

    // TODO: Need to share with com.vmware.admiral.compute.ComputeNetworkDescriptionService.NetworkType.
    // For now, making a copy here to retain the separation between the API layer and the service layer.
    public enum NetworkType {
        PUBLIC,
        ISOLATED,
        EXTERNAL
    }

    // TODO: Add a boolean for outbound access for ISOLATED or fold that concept into the enum.

    // TODO: Need to share with com.vmware.admiral.compute.ComputeNetworkDescriptionService.
    // TODO: Or is it com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment?
    public enum IpAssignment {
        STATIC,
        DYNAMIC
    }


}
