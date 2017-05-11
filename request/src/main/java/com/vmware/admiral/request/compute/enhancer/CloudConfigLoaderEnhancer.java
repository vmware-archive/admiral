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

import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.enableContainerHost;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.getCustomProperty;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.loadResource;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.objectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Utils;

public class CloudConfigLoaderEnhancer extends ComputeEnhancer {

    @Override
    @SuppressWarnings("unchecked")
    public DeferredResult<ComputeState> enhance(EnhanceContext context,
            ComputeState cs) {
        String fileContent = getCustomProperty(cs,
                ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME);
        if (fileContent == null) {
            boolean supportDocker = enableContainerHost(cs.customProperties);
            String imageType = cs.customProperties.get(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME);
            String fileName = String.format("/%s-content/cloud_config_%s.yml",
                    context.endpointType, supportDocker ? imageType + "_docker" : "base");
            try {
                fileContent = loadResource(fileName);
                if (fileContent != null && !fileContent.trim().isEmpty()) {
                    Map<String, Object> content = objectMapper().readValue(fileContent, Map.class);

                    context.content = content;
                } else {
                    context.content = new LinkedHashMap<>();
                }
            } catch (IOException e) {
                Utils.logWarning("Error reading cloud-config data from %s, reason : %s",
                        fileName, e.getMessage());
            }
            return DeferredResult.completed(cs);
        } else {
            try {
                Map<String, Object> content = objectMapper().readValue(fileContent, Map.class);

                context.content = content;
            } catch (IOException e) {
                Utils.logWarning("Error reading cloud-config data from %s, reason : %s",
                        fileContent, e.getMessage());
            }
            return DeferredResult.completed(cs);
        }
    }
}
