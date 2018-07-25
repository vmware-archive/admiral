/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.pks.entities;

import com.google.gson.annotations.SerializedName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel
public class PKSPlan {

    @SerializedName("id")
    @ApiModelProperty(
            value = "The id of the plan.")
    public String id;

    @SerializedName("name")
    @ApiModelProperty(
            value = "The name of the plan.")
    public String name;

    @SerializedName("description")
    @ApiModelProperty(
            value = "The description of the plan")
    public String description;

    @SerializedName("worker_instances")
    @ApiModelProperty(
            value = "The number of worker nodes")
    public String workerInstances;

}
