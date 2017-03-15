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

package com.vmware.admiral.request.compute.enhancer;

import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.getCustomProperty;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.DeferredResult;

public class ComputeDescriptionContainerHostRemoteAPIEnhancer extends ComputeDescriptionEnhancer {

    public ComputeDescriptionContainerHostRemoteAPIEnhancer() {
    }

    @Override
    public DeferredResult<ComputeDescription> enhance(EnhanceContext context,
            ComputeDescription cd) {
        DeferredResult<ComputeDescription> result = new DeferredResult<>();
        String adapterType = getCustomProperty(cd,
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME);
        if (!DockerAdapterType.API.name().equalsIgnoreCase(adapterType)) {
            result.complete(cd);
        } else {
            cd.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                    ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK);
            result.complete(cd);
        }
        return result;
    }

}
