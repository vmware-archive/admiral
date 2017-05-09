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

package com.vmware.admiral.compute.profile;

import java.util.Map;

import com.vmware.photon.controller.model.Constraint;
import com.vmware.xenon.common.ServiceDocument.UsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.SystemHostInfo.OsFamily;

/**
 * Describes a compute instance type.
 */
public class ComputeImageDescription {

    public String name;

    public String description;

    /**
     * Image identifier (name, path, location, uri, etc.) valid in the context of the particular
     * endpoint this description is created for.
     */
    public String image;

    /**
     * Self-link to the {@link com.vmware.photon.controller.model.resources.ImageService.ImageState image}
     * used to create an instance of this disk service.
     *
     * <p>Set either this or {@link #image} property. If both are set this
     * property has precedence.
     */
    public String imageLink;

    /**
     * Specifies different image identifiers by region; useful (and only applicable) to environments
     * defined for all endpoints of a given type. The {@link #image} field must be {@code null} in
     * order for this field to be used.
     */
    public Map<String, String> imageByRegion;

    /**
     * Optional OS family.
     */
    public OsFamily osFamily;

    /**
     * Constraints of this image to other resources. Different services can specify their specific
     * constraints by using different keys in the map, so that multiple constraints are supported
     * for different purposes - e.g. placement constraints, grouping constraints, etc.
     */
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    public Map<String, Constraint> constraints;
}
