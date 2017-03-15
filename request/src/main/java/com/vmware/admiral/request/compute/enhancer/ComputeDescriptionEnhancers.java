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
import java.util.LinkedList;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceHost;

/**
 * Composition of all Compute description enhancers used to enhance the ComputeDescription during
 * request.
 */
public class ComputeDescriptionEnhancers extends ComputeDescriptionEnhancer {

    private final LinkedList<ComputeDescriptionEnhancer> enhancers;

    private ComputeDescriptionEnhancers() {
        this.enhancers = new LinkedList<>();
    }

    private void initialize(ServiceHost host, URI referer) {
        this.enhancers.add(new ComputeDescriptionProfileEnhancer(host, referer));
        this.enhancers.add(new GuestCredentialsComputeDescriptionEnhancer(host, referer));
        this.enhancers.add(new ComputeDescriptionContainerHostRemoteAPIEnhancer());
    }

    public static ComputeDescriptionEnhancers build(ServiceHost host, URI referer) {
        ComputeDescriptionEnhancers enhancers = new ComputeDescriptionEnhancers();
        enhancers.initialize(host, referer);
        return enhancers;
    }

    @Override
    public DeferredResult<ComputeDescription> enhance(EnhanceContext context,
            ComputeDescription cd) {
        ComputeDescriptionEnhancer enhancer = enhancers.poll();
        if (enhancer == null) {
            return DeferredResult.completed(cd);
        }

        return doEnhance(context, cd, enhancer, enhancers.poll());
    }

    private DeferredResult<ComputeDescription> doEnhance(EnhanceContext context,
            ComputeDescription cd, ComputeDescriptionEnhancer enhancer,
            ComputeDescriptionEnhancer next) {

        if (next != null) {
            return enhancer.enhance(context, cd)
                    .thenCompose(desc -> doEnhance(context, desc, next, enhancers.poll()));
        }
        return enhancer.enhance(context, cd);
    }
}
