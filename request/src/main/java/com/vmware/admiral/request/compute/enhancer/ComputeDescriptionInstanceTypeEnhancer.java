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

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.VsphereConstants;
import com.vmware.admiral.compute.profile.InstanceTypeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceHost;

public class ComputeDescriptionInstanceTypeEnhancer extends ComputeDescriptionEnhancer {
    static final String REQUESTED_INSTANCE_TYPE = "__requestedInstanceType";
    private ServiceHost host;
    private URI referer;

    public ComputeDescriptionInstanceTypeEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeDescription> enhance(EnhanceContext context,
            ComputeDescription cd) {

        if (VsphereConstants.COMPUTE_VSPHERE_TYPE.equals(cd.customProperties.get(
                VsphereConstants.COMPUTE_COMPONENT_TYPE_ID)) && cd.instanceType == null
                && !cd.customProperties.containsKey(REQUESTED_INSTANCE_TYPE)) {
            if (cd.cpuCount < 1) {
                return DeferredResult.failed(new IllegalStateException(
                     "CPU count cannot be 0 for Endpoint specific blueprints."));
            }

            if (cd.totalMemoryBytes < 1) {
                return DeferredResult.failed(new IllegalStateException(
                     "MEMORY cannot be 0 for Endpoint specific blueprints."));
            }

            return DeferredResult.completed(cd);
        }

        if (cd.instanceType == null && !cd.customProperties.containsKey(REQUESTED_INSTANCE_TYPE)) {
            return DeferredResult.completed(cd);
        }

        return getProfileState(host, referer, context)
                .thenCompose(profile -> {
                    context.profile = profile;

                    String requestedInstanceType = cd.customProperties
                            .containsKey(REQUESTED_INSTANCE_TYPE)
                                    ? cd.customProperties.get(REQUESTED_INSTANCE_TYPE)
                                    : cd.instanceType;
                    InstanceTypeDescription instanceTypeDescription = null;
                    if (profile.computeProfile != null
                            && profile.computeProfile.instanceTypeMapping != null) {
                        instanceTypeDescription = PropertyUtils.getPropertyCaseInsensitive(
                                profile.computeProfile.instanceTypeMapping, requestedInstanceType);
                    }

                    if (instanceTypeDescription != null) {
                        if (instanceTypeDescription.instanceType != null) {
                            cd.customProperties.put(REQUESTED_INSTANCE_TYPE, requestedInstanceType);
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
                            profile.documentSelfLink, requestedInstanceType)));

                });

    }
}
