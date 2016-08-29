/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container;

import com.google.gson.annotations.SerializedName;

import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;

/**
 * Template spec (CompositeDescription or ContainerImageDescription)
 */
public class TemplateSpec extends CompositeDescription {
    public TemplateType templateType;

    // container image fields

    public String description;

    public String registry;

    @SerializedName("is_automated")
    public Boolean automated;

    @SerializedName("is_trusted")
    public Boolean trusted;

    @SerializedName("is_official")
    public Boolean official;

    @SerializedName("star_count")
    public Integer starCount;

    public static enum TemplateType {
        COMPOSITE_DESCRIPTION,
        CONTAINER_IMAGE_DESCRIPTION
    }
}
