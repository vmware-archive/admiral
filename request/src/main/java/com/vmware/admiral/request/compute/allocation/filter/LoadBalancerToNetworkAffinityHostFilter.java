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

import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.xenon.common.ServiceHost;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide affinity between a load
 * balancer and the network it is connected to.
 */
public class LoadBalancerToNetworkAffinityHostFilter implements HostSelectionFilter<FilterContext> {
    private final LoadBalancerDescription desc;

    public LoadBalancerToNetworkAffinityHostFilter(ServiceHost host, LoadBalancerDescription desc) {
        this.desc = desc;
    }

    @Override
    public boolean isActive() {
        return desc.networkName != null;
    }

    @Override
    public void filter(FilterContext state, Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {
        callback.complete(hostSelectionMap, null);
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        if (desc.networkName != null) {
            return Collections.singletonMap(desc.networkName,
                    new AffinityConstraint(desc.networkName));
        } else {
            return Collections.emptyMap();
        }
    }
}
