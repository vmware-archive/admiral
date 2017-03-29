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

import java.net.URI;
import java.util.logging.Level;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.profile.ComputeImageDescription;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class ComputeDescriptionImageEnhancer extends ComputeDescriptionEnhancer {
    static final String TEMPLATE_LINK = "__templateComputeLink";

    private ServiceHost host;
    private URI referer;

    public ComputeDescriptionImageEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeDescription> enhance(EnhanceContext context,
            ComputeDescription cd) {

        if (cd.customProperties.containsKey(ComputeConstants.CUSTOM_PROP_IMAGE_REF_NAME)) {
            cd.customProperties.put("__requestedImageType",
                    cd.customProperties.get(ComputeConstants.CUSTOM_PROP_IMAGE_REF_NAME));
            return apply(cd, cd.customProperties.get(ComputeConstants.CUSTOM_PROP_IMAGE_REF_NAME));
        }
        if (cd.customProperties.containsKey(TEMPLATE_LINK)) {
            cd.customProperties.put("__requestedImageType", context.imageType);
            cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, context.imageType);
            return DeferredResult.completed(cd);
        }
        if (context.imageType == null) {
            return DeferredResult.failed(new IllegalStateException(
                    String.format("No imageType specified for requested compute %s", cd.name)));
        }

        return getProfileState(context)
                .thenCompose(profile -> {
                    context.profile = profile;
                    cd.customProperties.put("__requestedImageType", context.imageType);
                    String absImageId = context.imageType;
                    String imageId = null;
                    ComputeImageDescription imageDesc = getComputeImageDescription(profile,
                            absImageId);
                    if (imageDesc != null) {
                        if (imageDesc.image != null) {
                            imageId = imageDesc.image;
                        } else if (imageDesc.imageByRegion != null) {
                            imageId = imageDesc.imageByRegion.get(context.regionId);
                        }
                    }
                    if (imageId == null) {
                        return DeferredResult.failed(new IllegalStateException(String.format(
                                "No matching image type defined in profile: %s, for requested instance type: %s",
                                profile.documentSelfLink, absImageId)));
                    }
                    return apply(cd, imageId);
                });
    }

    private DeferredResult<ComputeDescription> apply(ComputeDescription cd, String image) {
        try {
            URI imageUri = URI.create(image);
            String scheme = imageUri.getScheme();
            if (scheme != null
                    && (scheme.startsWith("http")
                            || scheme.startsWith("file"))) {
                cd.customProperties.put(OVA_URI, imageUri.toString());
            }
            cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, image);
        } catch (Throwable t) {
            return DeferredResult.failed(t);
        }
        return DeferredResult.completed(cd);
    }

    private ComputeImageDescription getComputeImageDescription(ProfileStateExpanded profile,
            String imageId) {
        if (profile.computeProfile != null && profile.computeProfile.imageMapping != null) {
            return PropertyUtils.getPropertyCaseInsensitive(profile.computeProfile.imageMapping,
                    imageId);
        }
        return null;
    }

    private DeferredResult<ProfileStateExpanded> getProfileState(EnhanceContext context) {
        if (context.profile != null) {
            return DeferredResult.completed(context.profile);
        }
        host.log(Level.INFO, "Loading profile state for %s", context.profileLink);

        URI profileUri = UriUtils.buildUri(host, context.profileLink);
        return host.sendWithDeferredResult(
                Operation.createGet(ProfileStateExpanded.buildUri(profileUri)).setReferer(referer),
                ProfileStateExpanded.class);
    }
}
