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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;

public class CloudConfigLoaderEnhancer extends ComputeDescriptionEnhancer {

    @SuppressWarnings("unchecked")
    @Override
    public void enhance(EnhanceContext context, ComputeDescription cd,
            BiConsumer<ComputeDescription, Throwable> callback) {
        String fileContent = getCustomProperty(cd,
                ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME);
        if (fileContent == null) {
            boolean supportDocker = enableContainerHost(cd.customProperties);
            String imageType = context.imageType;
            try {
                fileContent = loadResource(String.format("/%s-content/cloud_config_%s.yml",
                        context.endpointType, supportDocker ? imageType + "_docker" : "base"));
                if (fileContent != null && !fileContent.trim().isEmpty()) {
                    Map<String, Object> content = objectMapper.readValue(fileContent, Map.class);

                    context.content = content;
                } else {
                    context.content = new LinkedHashMap<>();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            callback.accept(cd, null);
        } else {
            try {
                Map<String, Object> content = objectMapper.readValue(fileContent, Map.class);

                context.content = content;
            } catch (IOException e) {
                e.printStackTrace();
            }
            callback.accept(cd, null);
        }
    }

    private static String loadResource(String fileName) throws IOException {
        try (InputStream is = ComputeAllocationTaskState.class.getResourceAsStream(fileName)) {
            if (is != null) {
                try (InputStreamReader r = new InputStreamReader(is)) {
                    char[] buf = new char[1024];
                    final StringBuilder out = new StringBuilder();

                    int length = r.read(buf);
                    while (length > 0) {
                        out.append(buf, 0, length);
                        length = r.read(buf);
                    }
                    return out.toString();
                }
            }
        }
        return null;
    }
}
