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
import java.util.logging.Level;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.profile.InstanceTypeDescription;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class ComputeDescriptionInstanceTypeEnhancer extends ComputeDescriptionEnhancer {
    private ServiceHost host;
    private URI referer;

    public ComputeDescriptionInstanceTypeEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeDescription> enhance(EnhanceContext context,
            ComputeDescription cd) {

        if (cd.instanceType == null) {
            return DeferredResult.completed(cd);
        }

        return getProfileState(context)
                .thenCompose(profile -> {
                    context.profile = profile;

                    InstanceTypeDescription instanceTypeDescription = null;
                    if (profile.computeProfile != null
                            && profile.computeProfile.instanceTypeMapping != null) {
                        instanceTypeDescription = PropertyUtils.getPropertyCaseInsensitive(
                                profile.computeProfile.instanceTypeMapping, cd.instanceType);
                    }

                    if (instanceTypeDescription != null) {
                        if (instanceTypeDescription.instanceType != null) {
                            cd.instanceType = instanceTypeDescription.instanceType;
                        } else {
                            cd.cpuCount = instanceTypeDescription.cpuCount;
                            cd.totalMemoryBytes = instanceTypeDescription.memoryMb * 1024
                                    * 1024;
                        }
                        return DeferredResult.completed(cd);
                    }
                    return DeferredResult.failed(new IllegalStateException(String.format(
                            "No matching instance type defined in profile: %s, for requested instance type: %s",
                            profile.documentSelfLink, cd.instanceType)));

                });

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
