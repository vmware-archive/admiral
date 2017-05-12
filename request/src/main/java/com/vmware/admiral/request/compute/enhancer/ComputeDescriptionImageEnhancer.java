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

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.profile.ComputeImageDescription;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceHost;

public class ComputeDescriptionImageEnhancer extends ComputeDescriptionEnhancer {

    static final String TEMPLATE_LINK = "__templateComputeLink";

    private ServiceHost host;
    private URI referer;

    public ComputeDescriptionImageEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeDescription> enhance(
            EnhanceContext context,
            ComputeDescription cd) {

        if (cd.customProperties.containsKey(ComputeConstants.CUSTOM_PROP_IMAGE_REF_NAME)) {
            apply(context,
                    cd,
                    cd.customProperties.get(ComputeConstants.CUSTOM_PROP_IMAGE_REF_NAME),
                    null /* imageLink */);

            return DeferredResult.completed(cd);
        }

        if (cd.customProperties.containsKey(TEMPLATE_LINK)) {
            return DeferredResult.completed(cd);
        }

        String imageType = cd.customProperties.get(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME);
        if (imageType == null) {
            return DeferredResult.failed(new IllegalStateException(
                    String.format("No imageType specified for requested compute %s", cd.name)));
        }

        return getProfileState(host, referer, context)
                .thenAccept(profile -> {

                    context.profile = profile;

                    String profileImage = null;
                    String profileImageLink = null;

                    ComputeImageDescription imageDesc = getComputeImageDescription(profile, imageType);
                    if (imageDesc != null) {
                        if (imageDesc.imageLink != null) {
                            profileImageLink = imageDesc.imageLink;
                        } else if (imageDesc.image != null) {
                            profileImage = imageDesc.image;
                        } else if (imageDesc.imageByRegion != null) {
                            profileImage = imageDesc.imageByRegion.get(context.regionId);
                        }
                        if (profileImage == null && profileImageLink == null) {
                            throw new IllegalStateException(String.format(
                                    "The profile '%s' matched for requested image type '%s' does not specify image",
                                    profile.documentSelfLink, imageType));
                        }
                    } else {
                        throw new IllegalStateException(String.format(
                                "No matching image type defined in profile: %s, for requested image type: %s",
                                profile.documentSelfLink, imageType));
                    }
                    apply(context, cd, profileImage, profileImageLink);
                })
                .thenApply(woid -> cd);
    }

    private void apply(EnhanceContext context, ComputeDescription cd, String image, String imageLink) {
        if (imageLink != null) {
            context.resolvedImageLink = imageLink;
        } else {
            URI imageUri = URI.create(image);
            String scheme = imageUri.getScheme();
            if (scheme != null
                    && (scheme.toLowerCase().startsWith("http")
                            || scheme.toLowerCase().startsWith("file"))) {
                cd.customProperties.put(OVA_URI, imageUri.toString());
            }
            context.resolvedImage = image;
        }
    }

    private ComputeImageDescription getComputeImageDescription(
            ProfileStateExpanded profile,
            String imageId) {
        if (profile.computeProfile != null && profile.computeProfile.imageMapping != null) {
            return PropertyUtils.getPropertyCaseInsensitive(profile.computeProfile.imageMapping,
                    imageId);
        }
        return null;
    }
}
