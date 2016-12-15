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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.request.composition.CompositionGraph.ResourceNode;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;

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
    }

    public void apply() {
        for (ImplicitDependencyFilter filter : filters) {
            if (filter.isActive()) {
                filter.apply();
            }
        }
    }

    private static class ContainerPortsImplicitDependencyFilter
            implements ImplicitDependencyFilter {

        private final CompositeDescriptionExpanded compositeDescription;
        private Map<String, ResourceNode> resourceNodesByName;

        private ContainerPortsImplicitDependencyFilter(
                Map<String, ResourceNode> resourceNodesByName, CompositeDescriptionExpanded desc) {
            this.compositeDescription = desc;
            this.resourceNodesByName = resourceNodesByName;
        }

        @Override
        public boolean isActive() {
            return compositeDescription.componentDescriptions.stream().map((cd) -> {
                return Utils.fromJson(cd.componentJson,
                        CompositeComponentRegistry.metaByType(cd.type).descriptionClass);
            }).filter((cd) -> {
                return cd instanceof ContainerDescription
                        && ((ContainerDescription) cd).portBindings != null;
            }).count() > 1;
        }

        @Override
        public void apply() {
            Map<String, List<ResourceNode>> exposedHostPorts = buildExposedHostPortsMap();

            populateDependencies(exposedHostPorts);

        }

        private void populateDependencies(Map<String, List<ResourceNode>> exposedHostPorts) {
            for (Map.Entry<String, List<ResourceNode>> portToNode : exposedHostPorts.entrySet()) {
                List<ResourceNode> nodes = portToNode.getValue();
                List<String> nodeNames = nodes.stream().collect(Collectors.mapping((n) -> {
                    return n.name;
                }, Collectors.toList()));

                if (nodes.size() <= 1) {
                    continue;
                }

                nodes.sort((a, b) -> {
                    if (a.dependsOn == null) {
                        return b.dependsOn == null ? 0 : -1;
                    }

                    if (b.dependsOn == null) {
                        return 1;
                    }

                    return (int) (a.dependsOn.stream().filter((n) -> {
                        return nodeNames.contains(n);
                    }).count()
                            - b.dependsOn.stream().filter((n) -> {
                                return nodeNames.contains(n);
                            }).count());
                });

                ResourceNode current = nodes.get(0);
                while (nodes.size() > 1) {
                    nodes.remove(current);
                    if (current.dependsOn != null && current.dependsOn.size() > 0) {
                        List<String> nodesForSelection = current.dependsOn.stream()
                                .filter((n) -> {
                                    return nodeNames.contains(n);
                                })
                                .collect(Collectors.toList());

                        if (!nodesForSelection.isEmpty()) {
                            current = nodes.stream()
                                    .filter((n) -> n.name
                                            .equals(nodesForSelection.iterator().next()))
                                    .collect(Collectors.toList()).get(0);
                        } else {
                            current = selectRandomNode(nodes, current);
                        }

                    } else {
                        current = selectRandomNode(nodes, current);
                    }
                }
            }
        }

        private ResourceNode selectRandomNode(List<ResourceNode> nodes, ResourceNode current) {
            String nextNode = nodes.get(0).name;
            ResourceNode newNode = nodes.stream()
                    .filter((n) -> n.name.equals(nextNode))
                    .collect(Collectors.toList()).get(0);

            if (newNode.dependsOn != null && !newNode.dependsOn.contains(current.name)) {
                if (current.dependsOn == null) {
                    current.dependsOn = new HashSet<>();
                }
                current.dependsOn.add(nextNode);
            }

            return newNode;
        }

        private Map<String, List<ResourceNode>> buildExposedHostPortsMap() {
            Map<String, List<ResourceNode>> exposedHostPorts = new HashMap<String, List<ResourceNode>>();
            for (final ComponentDescription cd : compositeDescription.componentDescriptions) {
                ResourceState resourceState = Utils.fromJson(cd.componentJson,
                        CompositeComponentRegistry.metaByType(cd.type).descriptionClass);
                if (resourceState instanceof ContainerDescription) {
                    ContainerDescription containerDescription = (ContainerDescription) resourceState;
                    if (containerDescription.portBindings == null) {
                        return exposedHostPorts;
                    }

                    for (PortBinding port : containerDescription.portBindings) {
                        if (port.hostPort == null) {
                            continue;
                        }

                        ResourceNode resourceNode = resourceNodesByName.get(cd.name);
                        if (exposedHostPorts.get(port.hostPort) == null) {
                            exposedHostPorts.put(port.hostPort, new ArrayList<>());
                        }

                        exposedHostPorts.get(port.hostPort).add(resourceNode);
                    }
                }
            }

            return exposedHostPorts;
        }
    }

}
