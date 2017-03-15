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

import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceHost;

/**
 * Composition of all Compute state enhancers used to enhance the ComputeState during request.
 */
public class ComputeStateEnhancers extends ComputeEnhancer {

    private final LinkedList<ComputeEnhancer> enhancers;

    private ComputeStateEnhancers() {
        this.enhancers = new LinkedList<>();
    }

    private void initialize(ServiceHost host, URI referer) {
        this.enhancers.add(new CloudConfigLoaderEnhancer());
        this.enhancers.add(new ComputeStateGuestCredentialsEnhancer(host, referer));
        this.enhancers.add(new ComputeStateContainerHostRemoteAPIComputeEnhancer(host, referer));
        this.enhancers.add(new ComputeStateSoftwareAgentEnhancer());
        this.enhancers.add(new CloudConfigSerializeEnhancer(host));
        this.enhancers.add(new ComputeStateDiskEnhancer(host, referer));
    }

    public static ComputeStateEnhancers build(ServiceHost host, URI referer) {
        ComputeStateEnhancers enhancers = new ComputeStateEnhancers();
        enhancers.initialize(host, referer);
        return enhancers;
    }

    @Override
    public DeferredResult<ComputeState> enhance(EnhanceContext context,
            ComputeState cs) {
        ComputeEnhancer enhancer = enhancers.poll();
        if (enhancer == null) {
            return DeferredResult.completed(cs);
        }

        return doEnhance(context, cs, enhancer, enhancers.poll());
    }

    private DeferredResult<ComputeState> doEnhance(EnhanceContext context,
            ComputeState cs, ComputeEnhancer enhancer,
            ComputeEnhancer next) {

        if (next != null) {
            return enhancer.enhance(context, cs)
                    .thenCompose(desc -> doEnhance(context, desc, next, enhancers.poll()));
        }
        return enhancer.enhance(context, cs);
    }
}
