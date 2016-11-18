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
import java.util.function.BiConsumer;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.EnvironmentMappingService.EnvironmentMappingState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

public class EnvironmentComputeDescriptionEnhancer implements ComputeDescriptionEnhancer {

    private StatefulService sender;

    public EnvironmentComputeDescriptionEnhancer(StatefulService sender) {
        this.sender = sender;
    }

    @Override
    public void enhance(EnhanceContext context, ComputeDescription cd,
            BiConsumer<ComputeDescription, Throwable> callback) {
        getEnvironmentState(context.environmentLink, (env, e) -> {
            if (e != null) {
                callback.accept(cd, e);
                return;
            }

            String value = env.getMappingValue("instanceType", cd.instanceType);
            if (value != null) {
                cd.instanceType = value;
            }

            if (cd.dataStoreId == null) {
                cd.dataStoreId = env.getMappingValue("placement", "dataStoreId");
            }

            if (cd.authCredentialsLink == null) {
                cd.authCredentialsLink = env.getMappingValue("authentication",
                        "guestAuthLink");
            }
            if (cd.zoneId == null) {
                cd.zoneId = env.getMappingValue("placement", "zoneId");
            }
            if (cd.zoneId == null) {
                cd.zoneId = context.endpointComputeDescription.zoneId;
            }

            String absImageId = context.imageType;
            if (absImageId != null) {
                String imageId = env.getMappingValue("imageType", absImageId);
                if (imageId == null) {
                    imageId = absImageId;
                }
                try {
                    URI imageUri = URI.create(imageId);
                    String scheme = imageUri.getScheme();
                    if (scheme != null
                            && (scheme.startsWith("http") || scheme.startsWith("file"))) {
                        cd.customProperties.put("ova.uri", imageUri.toString());
                        cd.customProperties.put("ovf.uri", imageUri.toString());
                        cd.customProperties.put("ovf.configuration", "");
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

    private <T extends ServiceDocument> void getEnvironmentState(String uriLink,
            BiConsumer<EnvironmentMappingState, Throwable> callback) {
        sender.logInfo("Loading state for %s", uriLink);

        sender.sendRequest(Operation.createGet(sender, uriLink)
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
