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

import java.net.URI;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.EnvironmentMappingService.EnvironmentMappingState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;

public class EnvironmentComputeDescriptionEnhancer extends ComputeDescriptionEnhancer {

    private ServiceHost host;
    private URI referer;

    public EnvironmentComputeDescriptionEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public void enhance(EnhanceContext context, ComputeDescription cd,
            BiConsumer<ComputeDescription, Throwable> callback) {
        getEnvironmentState(context.environmentLink, (env, e) -> {
            if (e != null) {
                callback.accept(cd, e);
                return;
            }

            applyInstanceType(cd, env);

            if (cd.dataStoreId == null) {
                cd.dataStoreId = env.getStringMappingValue("placement", "dataStoreId");
            }

            if (cd.authCredentialsLink == null) {
                cd.authCredentialsLink = env.getStringMappingValue("authentication",
                        "guestAuthLink");
            }
            if (cd.zoneId == null) {
                cd.zoneId = env.getStringMappingValue("placement", "zoneId");
            }
            if (cd.zoneId == null && context.endpointComputeDescription != null) {
                cd.zoneId = context.endpointComputeDescription.zoneId;
            }

            String absImageId = context.imageType;
            if (absImageId != null) {
                String imageId = env.getStringMappingValue("imageType", absImageId);
                if (imageId == null) {
                    imageId = absImageId;
                }
                try {
                    URI imageUri = URI.create(imageId);
                    String scheme = imageUri.getScheme();
                    if (scheme != null
                            && (scheme.startsWith("http") || scheme.startsWith("file"))) {
                        cd.customProperties.put(OVA_URI, imageUri.toString());
                    } else {
                        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                                imageId);
                    }
                } catch (Throwable t) {
                    callback.accept(cd, t);
                    return;
                }
            }
            callback.accept(cd, null);
        });

    }

    private void applyInstanceType(ComputeDescription cd, EnvironmentMappingState env) {
        Object value = env.getMappingValue("instanceType", cd.instanceType);
        if (value == null) {
            return;
        }
        if (value instanceof String) {
            cd.instanceType = (String) value;
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> map = (Map<String, Integer>) value;
            Integer cpu = map.get("cpu");
            if (cpu != null) {
                cd.cpuCount = cpu.longValue();
            }
            Integer mem = map.get("mem");
            if (mem != null) {
                cd.totalMemoryBytes = mem.longValue() * 1024 * 1024;
            }
        }

    }

    private <T extends ServiceDocument> void getEnvironmentState(String uriLink,
            BiConsumer<EnvironmentMappingState, Throwable> callback) {
        host.log(Level.INFO, "Loading state for %s", uriLink);

        host.sendRequest(Operation.createGet(host, uriLink)
                .setReferer(referer)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        callback.accept(null, e);
                        return;
                    }

                    EnvironmentMappingState state = o.getBody(EnvironmentMappingState.class);
                    callback.accept(state, null);
                }));
    }
}
