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

package com.vmware.iaas.consumer.api.model.base;

import java.util.Map;

import io.swagger.annotations.ApiModelProperty;

public class Resource {

    @ApiModelProperty(value = "A globally unique identifier.")
    public String id;

    @ApiModelProperty(value = "A link to this same resource instance.")
    public String selfLink;

    @ApiModelProperty(value = "A human-friendly name used as an identifier in APIs that support this option.")
    public String name;

    @ApiModelProperty(value = "A human-friendly description.")
    public String description;

    @ApiModelProperty(value = "Additional properties that may be specific to a cloud type, or that may add additional policy controls.")
    public Map<String, String> customProperties;

    @ApiModelProperty(value = "A link to the tenant that owns this resource instance.")
    public String tenantLink;

    @ApiModelProperty(value = "A link to the Specification that was used to generate this instance.")
    public String specificationLink;

}