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

package com.vmware.admiral.request.allocation.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.compute.BindingUtils;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.request.compute.allocation.filter.ComputeBinpackAffinityHostFilter;
import com.vmware.admiral.request.compute.allocation.filter.ComputeClusterAntiAffinityHostFilter;
import com.vmware.admiral.request.compute.allocation.filter.ComputeServiceAffinityHostFilter;
import com.vmware.admiral.request.compute.allocation.filter.ComputeServiceAntiAffinityHostFilter;
import com.vmware.admiral.request.compute.allocation.filter.ComputeSpreadAffinityHostFilter;
import com.vmware.admiral.request.compute.allocation.filter.ComputeToNetworkAffinityHostFilter;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.ServiceHost;

/**
 * Composition of all affinity rules used for host selection filtering and evaluating the resource
 * dependencies
 */
public final class AffinityFilters {
    private final Collection<AffinityFilter> filters;

    private AffinityFilters() {
        this.filters = new ArrayList<>();
    }

    public static AffinityFilters build(ServiceHost host, Object desc) {
        AffinityFilters filters = new AffinityFilters();
        filters.initialize(host, desc);
        return filters;
    }

    private void initialize(ServiceHost host, Object desc) {
        if (ContainerDescription.class.isInstance(desc)) {
            initialize(host, (ContainerDescription) desc);
        } else if (ComputeDescription.class.isInstance(desc)) {
            initialize(host, (ComputeDescription) desc);
        } else if (ComputeNetworkDescription.class.isInstance(desc)) {
            // TODO initialize(host, (ComputeNetworkDescription) desc)
        } else if (ContainerNetworkDescription.class.isInstance(desc)) {
            initialize(host, (ContainerNetworkDescription) desc);
        } else if (ComponentDescription.class.isInstance(desc)) {
            initialize(host, ((ComponentDescription) desc));
        } else if (ContainerVolumeDescription.class.isInstance(desc)) {
            // TODO initialize(host, (ContainerVolumeDescription) desc)
        } else if (ClosureDescription.class.isInstance(desc)) {
            initialize(host, (ClosureDescription) desc);
        } else {
            throw new IllegalArgumentException("Unsupported type:" + desc.getClass());
        }
    }

    private void initialize(ServiceHost host, ContainerNetworkDescription desc) {

    }

    private void initialize(ServiceHost host, ComputeDescription desc) {
        filters.add(new ComputeServiceAffinityHostFilter(host, desc));

        filters.add(new ComputeServiceAntiAffinityHostFilter(host, desc));
        filters.add(new ComputeClusterAntiAffinityHostFilter(host, desc));

        filters.add(new ComputeToNetworkAffinityHostFilter(host, desc));
        filters.add(new ComputeBinpackAffinityHostFilter(host, desc));
        filters.add(new ComputeSpreadAffinityHostFilter(host, desc));
    }

    private void initialize(ServiceHost host, ComponentDescription desc) {
        filters.add(new ComponentFilter(desc));
        initialize(host, desc.getServiceDocument());
    }

    private void initialize(ServiceHost host, ClosureDescription desc) {

    }

    private void initialize(ServiceHost host, ContainerDescription desc) {

        // host affinity filters:
        filters.add(new PodAffinityHostFilter(host, desc));
        filters.add(new VolumesFromAffinityHostFilter(host, desc));
        filters.add(new ServiceAffinityHostFilter(host, desc));
        filters.add(new DeploymentPolicyAffinityFilter(host, desc));
        filters.add(new ClusterServiceLinkAffinityHostFilter(host, desc));
        filters.add(new ServiceLinkAffinityFilter(host, desc));

        // networks define a host affinity filter since external networks may be available only on
        // particular hosts or (KV-store) clusters, and regular networks have to guarantee that
        // all containers of an application are deployed where they can talk to each other, e.g.
        // same host or same (KV-store) cluster.
        filters.add(new ContainerToNetworkAffinityHostFilter(host, desc));
        filters.add(new NamedVolumeAffinityHostFilter(host, desc));

        // host anti-affinity filters:
        filters.add(new ExposedPortsHostFilter(host, desc));
        filters.add(new ServiceAntiAffinityHostFilter(host, desc));
        filters.add(new ClusterAntiAffinityHostFilter(host, desc));

        // non host related dependency only
        filters.add(new DependsOnAffinityHostFilter(desc));

        // advanced policy filters
        filters.add(new BinpackAffinityHostFilter(host, desc));
        filters.add(new SpreadAffinityHostFilter(host, desc));

    }

    @SuppressWarnings("rawtypes")
    public Queue<HostSelectionFilter> getQueue() {
        // return only HostSelectionFilter instances by filtering and downcasting
        return filters.stream()
                .filter((f) -> f instanceof HostSelectionFilter)
                .map(HostSelectionFilter.class::cast)
                .collect(Collectors.toCollection(ConcurrentLinkedQueue::new));
    }

    /**
     * Get all unique constraints not taking into consideration the affinity flag or the type. The
     * soft constraints will be overridden in case there are hard constraints with the same name.
     */
    public Set<String> getUniqueDependencies() {
        final Map<String, AffinityConstraint> constraints = new HashMap<>();
        for (final AffinityFilter filter : filters) {
            if (filter.hasEffectOnDependency()) {
                mergeUniqueConstraints(constraints, filter.getAffinityConstraints());
            }
        }

        return constraints.values().stream().map((c) -> c.name).collect(Collectors.toSet());
    }

    private void mergeUniqueConstraints(final Map<String, AffinityConstraint> constraintsTo,
            final Map<String, AffinityConstraint> constraintsFrom) {
        for (final AffinityConstraint constraintFrom : constraintsFrom.values()) {
            final AffinityConstraint constraintTo = constraintsTo.get(constraintFrom.name);
            if (constraintTo == null || (constraintTo.isSoft() && constraintFrom.isHard())) {
                // we need all unique constraints with hard constraints overriding the soft once
                constraintsTo.put(constraintFrom.name, constraintFrom);
            }
        }
    }

    private static class ComponentFilter implements AffinityFilter {

        private final ComponentDescription componentDescription;

        private ComponentFilter(ComponentDescription componentDescription) {
            this.componentDescription = componentDescription;

        }

        @Override
        public boolean isActive() {
            return componentDescription.bindings != null && !componentDescription.bindings
                    .isEmpty();
        }

        @Override
        public Map<String, AffinityConstraint> getAffinityConstraints() {
            return componentDescription.bindings.stream()
                    .filter(b -> b.isProvisioningTimeBinding())
                    .map(b -> BindingUtils
                            .extractComponentNameFromBindingExpression(
                                    b.placeholder.bindingExpression))
                    .distinct()
                    .collect(Collectors.toMap(
                            Function.identity(), AffinityConstraint::new));
        }

        @Override
        public boolean hasEffectOnDependency() {
            return this.componentDescription.componentJson != null;
        }
    }
}
