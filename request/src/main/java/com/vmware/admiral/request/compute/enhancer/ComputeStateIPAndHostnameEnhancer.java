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

import static com.vmware.admiral.compute.ComputeConstants.CUSTOM_PROP_SUBNET;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.getCustomProperty;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.logging.Level;

import com.vmware.admiral.request.utils.ComputeStateUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceHost;

public class ComputeStateIPAndHostnameEnhancer extends ComputeEnhancer {

    private ServiceHost host;
    private URI referer;

    public ComputeStateIPAndHostnameEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeState> enhance(EnhanceContext context, ComputeState cs) {
        if (cs.address != null) {
            host.log(Level.INFO, "IP Address set by customization [%s]", cs.address);
            String subnet = getCustomProperty(cs.customProperties, CUSTOM_PROP_SUBNET);
            return ComputeStateUtils.patchSubnetsNicsAndDescriptions(host,
                    new HashSet<>(Arrays.asList(cs.documentSelfLink)),
                    new HashSet<>(Arrays.asList(cs.address)), subnet)
                    .thenApply(v -> cs);
        } else {
            return DeferredResult.completed(cs);
        }
    }
}
