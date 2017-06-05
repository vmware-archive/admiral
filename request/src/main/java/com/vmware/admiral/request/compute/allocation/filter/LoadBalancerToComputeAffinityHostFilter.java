/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.compute.allocation.filter;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide affinity between a load
 * balancer and the instance it connects to.
 */
public class LoadBalancerToComputeAffinityHostFilter implements HostSelectionFilter<FilterContext> {
    private final ServiceHost host;
    private final LoadBalancerDescription desc;

    public LoadBalancerToComputeAffinityHostFilter(ServiceHost host, LoadBalancerDescription desc) {
        this.host = host;
        this.desc = desc;
    }

    @Override
    public boolean isActive() {
        return desc.computeDescriptionLink != null;
    }

    @Override
    public void filter(FilterContext state, Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {
        callback.complete(hostSelectionMap, null);
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        DeferredResult<String> computeNameDR = host
                .sendWithDeferredResult(
                        Operation.createGet(host, desc.computeDescriptionLink)
                                .setReferer(host.getUri()),
                        ComputeDescription.class)
                .thenApply(cd -> cd.name);
        CompletableFuture<String> computeNameFuture =
                (CompletableFuture<String>) computeNameDR.toCompletionStage();

        try {
            String computeName = computeNameFuture.get(120, TimeUnit.SECONDS);
            return Collections.singletonMap(computeName, new AffinityConstraint(computeName));
        } catch (TimeoutException e) {
            host.log(Level.WARNING, "Timeout loading compute description %s.",
                    desc.computeDescriptionLink);
            return Collections.emptyMap();
        } catch (Exception e) {
            host.log(Level.WARNING, "Error loading compute description %s, reason:%s",
                    desc.computeDescriptionLink, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
