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

package com.vmware.admiral.request.allocation.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.request.composition.CompositionGraph.ResourceNode;
import com.vmware.xenon.common.ServiceDocument;

/**
 * Composition of all affinity rules used for host selection filtering and evaluating the resource
 * dependencies
 */
public final class ImplicitDependencyFilters {
    private final Collection<ImplicitDependencyFilter> filters;

    private ImplicitDependencyFilters() {
        this.filters = new ArrayList<>();
    }

    public static ImplicitDependencyFilters build(Map<String, ResourceNode> resourceNodesByName,
            ServiceDocument desc) {
        ImplicitDependencyFilters filters = new ImplicitDependencyFilters();
        filters.initialize(resourceNodesByName, desc);
        return filters;
    }

    private void initialize(Map<String, ResourceNode> resourceNodesByName, ServiceDocument desc) {
        if (CompositeDescriptionExpanded.class.isInstance(desc)) {
            initialize(resourceNodesByName, (CompositeDescriptionExpanded) desc);
        } else {
            throw new IllegalArgumentException("Unsupported type:" + desc.getClass());
        }
    }

    private void initialize(Map<String, ResourceNode> resourceNodesByName,
            CompositeDescriptionExpanded desc) {
        filters.add(new ContainerPortsImplicitDependencyFilter(resourceNodesByName, desc));
        filters.add(new SharedUserDefinedNetworkFilter(resourceNodesByName, desc));
    }

    public void apply() {
        for (ImplicitDependencyFilter filter : filters) {
            if (filter.isActive()) {
                filter.apply();
            }
        }
    }

}
