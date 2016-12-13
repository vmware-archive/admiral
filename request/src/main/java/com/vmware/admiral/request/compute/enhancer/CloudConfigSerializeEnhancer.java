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

import static com.vmware.admiral.compute.ComputeConstants.OVA_URI;
import static com.vmware.admiral.compute.ComputeConstants.OVF_COREOS_CLOUD_INIT_PROP;
import static com.vmware.admiral.compute.ComputeConstants.OVF_LINUX_CLOUD_INIT_PROP;
import static com.vmware.admiral.compute.ComputeConstants.OVF_PROP_PREFIX;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.SSH_AUTHORIZED_KEYS;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.objectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.ServiceHost;

public class CloudConfigSerializeEnhancer extends ComputeDescriptionEnhancer {

    private ServiceHost host;

    public CloudConfigSerializeEnhancer(ServiceHost host) {
        this.host = host;
    }

    @Override
    public void enhance(EnhanceContext context,
            ComputeDescription cd, BiConsumer<ComputeDescription, Throwable> callback) {

        if (context.content != null && !context.content.isEmpty()) {
            try {
                context.content = order(context.content);

                String payload = objectMapper().writeValueAsString(context.content);
                StringBuilder sb = new StringBuilder("#cloud-config\n");
                sb.append(payload);

                String cloudConfig = sb.toString();
                host.log(Level.INFO, "Cloud config file to use [%s]", cloudConfig);

                if (EndpointType.vsphere.name().equals(context.endpointType)
                        && cd.customProperties.containsKey(OVA_URI)) {
                    if (context.imageType.equals("coreos")) {
                        cd.customProperties.put(OVF_PROP_PREFIX + OVF_COREOS_CLOUD_INIT_PROP,
                                cloudConfig);
                    } else {
                        cd.customProperties.put(OVF_PROP_PREFIX + OVF_LINUX_CLOUD_INIT_PROP,
                                cloudConfig);
                    }
                } else {
                    cd.customProperties.put(ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME,
                            cloudConfig);
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        callback.accept(cd, null);
    }

    private Map<String, Object> order(Map<String, Object> content) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        Object val = content.remove(SSH_AUTHORIZED_KEYS);
        if (val != null) {
            map.put(SSH_AUTHORIZED_KEYS, val);
        }
        map.putAll(content);

        return map;
    }

}
