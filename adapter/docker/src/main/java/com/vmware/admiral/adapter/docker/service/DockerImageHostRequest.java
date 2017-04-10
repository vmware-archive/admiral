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

package com.vmware.admiral.adapter.docker.service;

import java.util.Arrays;
import java.util.List;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ImageOperationType;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;

public class DockerImageHostRequest extends AdapterRequest {

    private byte[] dockerImageData;

    /**
     * A list of tenant links that can access this resource.
     */
    @ServiceDocument.UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.LINKS)
    public List<String> tenantLinks;

    @Override
    public void validate() {
        super.validate();
        if (operationTypeId != null) {
            if (ImageOperationType.instanceById(operationTypeId) == null) {
                throw new IllegalArgumentException("Not valid docker image operationTypeId: "
                        + operationTypeId);
            }
        }
    }

    public ImageOperationType getOperationType() {
        return ImageOperationType.instanceById(operationTypeId);
    }

    public byte[] getDockerImageData() {
        if (dockerImageData == null) {
            return new byte[0];
        }
        return Arrays.copyOf(dockerImageData, dockerImageData.length);
    }

    public void setDockerImageData(byte[] dockerImageData) {
        if (dockerImageData != null) {
            this.dockerImageData = Arrays.copyOf(dockerImageData, dockerImageData.length);
        }
    }
}
