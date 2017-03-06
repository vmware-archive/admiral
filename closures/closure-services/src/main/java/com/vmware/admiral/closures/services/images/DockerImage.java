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

package com.vmware.admiral.closures.services.images;

import com.google.gson.JsonElement;

import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;

public class DockerImage
        extends com.vmware.admiral.service.common.TaskServiceDocument<DockerImage.SubStage> {

    public enum SubStage {
        CREATED,
        COMPLETED,
        ERROR
    }

    @Documentation(description = "Name of the docker image")
    @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT })
    public String name;

    @Documentation(description = "Link of the compute state where the image has been built.")
    @PropertyOptions(usage = { PropertyUsageOption.LINK, PropertyUsageOption.SINGLE_ASSIGNMENT })
    public String computeStateLink;

    @Documentation(description = "Last time accessed")
    public Long lastAccessedTimeMillis;

    @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY)
    public JsonElement imageDetails;

    @Override public String toString() {
        return "DockerImage{" +
                "name='" + name + '\'' +
                ", computeStateLink='" + computeStateLink + '\'' +
                ", lastAccessedTimeMillis=" + lastAccessedTimeMillis +
                ", imageDetails=" + imageDetails +
                '}';
    }
}
