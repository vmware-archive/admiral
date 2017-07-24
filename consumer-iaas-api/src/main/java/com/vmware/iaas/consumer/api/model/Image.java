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

import io.swagger.annotations.ApiModelProperty;

import com.vmware.iaas.consumer.api.model.base.Resource;

public class Image extends Resource {

    @ApiModelProperty(value = "Operating System family of the image.", example = "linux, win")
    public String osFamily;

    // TODO: More metadata needed. Compare with what BOSH does for images.
}
