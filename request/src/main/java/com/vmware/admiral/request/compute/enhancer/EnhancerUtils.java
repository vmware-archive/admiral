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
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.photon.controller.model.resources.ResourceState;

public class EnhancerUtils {

    public static final String SSH_AUTHORIZED_KEYS = "ssh_authorized_keys";
    public static final String WRITE_FILES_ELEMENT = "write_files";
    public static final String RUNCMD_ELEMENT = "runcmd";

    private static final ObjectMapper objectMapper = createObjectMapper();

    static class WriteFiles {
        public String path;
        public String permissions;
        public String content;

        public WriteFiles(String path, String permissions, String content) {
            this.path = path;
            this.permissions = permissions;
            this.content = content;
        }
    }

    public static ObjectMapper objectMapper() {
        return objectMapper;
    }

    static String getCustomProperty(ResourceState resource, String propName) {
        return getCustomProperty(resource.customProperties, propName);
    }

    static String getCustomProperty(Map<String, String> customProperties, String propName) {
        if (customProperties == null) {
            return null;
        }
        return customProperties.get(propName);
    }

    static boolean enableContainerHost(Map<String, String> customProperties) {
        return customProperties
                .containsKey(ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME);
    }

    static String loadResource(String fileName) throws IOException {
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

    private static ObjectMapper createObjectMapper() {
        YAMLFactory factory = new YAMLFactory();
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);

        ObjectMapper objectMapper = new ObjectMapper(factory);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        return objectMapper;
    }
}
