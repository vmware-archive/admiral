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

import java.util.Map;

import io.swagger.annotations.ApiModelProperty;

import com.vmware.iaas.consumer.api.model.base.ResourceSpecification;

public class DiskAttachmentSpecification extends ResourceSpecification {

    @ApiModelProperty(value = "The device path of this machine-disk.")
    public String devicePath;

    @ApiModelProperty(value = "The path to mount this machine-disk at.")
    public String mountPath;

    // Note: Only one of the following two fields should be specified.
    @ApiModelProperty(value = "A specification that represents the block device that this machine-disk maps to.")
    public BlockDeviceSpecification blockDeviceSpecification;
    @ApiModelProperty(value = "Resource link to a block device that this machine-disk should map to.")
    public String blockDeviceLink;

    @ApiModelProperty(value = "")
    public Integer bootOrder;

    @ApiModelProperty(value = "")
    public String[] bootArguments;

    @ApiModelProperty(value = "")
    public BootConfig bootConfig;

    public static class BootConfig {
        // Label of the disk.
        public String label;

        // Data on the disk.
        public Map<String, String> data;
    }

}
