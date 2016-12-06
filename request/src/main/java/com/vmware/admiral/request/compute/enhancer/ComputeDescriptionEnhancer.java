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

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourceState;

/**
 * An base class to be extended by any ComputeDescription enhancer.
 */
public abstract class ComputeDescriptionEnhancer implements Enhancer<ComputeDescription> {
    public static final String SSH_AUTHORIZED_KEYS = "ssh_authorized_keys";
    public static final String OVA_URI = "ova.uri";

    static ObjectMapper objectMapper;

    {
        YAMLFactory factory = new YAMLFactory();
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);

        objectMapper = new ObjectMapper(factory);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
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
}
