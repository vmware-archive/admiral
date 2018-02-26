/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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
import com.vmware.xenon.common.Utils;

public class ContainerPortsImplicitDependencyFilter implements ImplicitDependencyFilter {

    private final CompositeDescriptionExpanded compositeDescription;
    private Map<String, ResourceNode> resourceNodesByName;

    public ContainerPortsImplicitDependencyFilter(
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
                    return b.dependsOn == null ? 0 : 1;
                }

                if (b.dependsOn == null) {
                    return -1;
                }

                return (int) (b.dependsOn.stream().filter((n) -> nodeNames.contains(n)).count()
                        - a.dependsOn.stream().filter((n) -> nodeNames.contains(n)).count());
            });

            ResourceNode current = nodes.get(0);
            ResourceNode lastToDeploy = new ResourceNode();
            lastToDeploy.name = current.name;

            // if no dependencies at all, we need to start
            // setting them from somewhere
            if (current.dependsOn == null) {
                current.dependsOn = new HashSet<>();
                current.dependsOn.add(nodes.get(1).name);
            }

            while (nodes.size() > 1) {
                nodes.remove(current);
                ResourceNode newNode = null;
                // Select the new node from nodes this node depends on - we don't want to add more
                // dependencies unnecessarily
                if (current.dependsOn != null && current.dependsOn.size() > 0) {
                    List<String> currentNodeNames = nodes.stream().map((n) -> n.name)
                            .collect(Collectors.toList());
                    List<String> nodesForSelection = current.dependsOn.stream()
                            .filter((n) -> {
                                return currentNodeNames.contains(n);
                            })
                            .collect(Collectors.toList());

                    if (!nodesForSelection.isEmpty()) {
                        // No need to add a dependency - it already exists as we chose
                        // among current node's dependencies
                        newNode = nodes.stream()
                                .filter((n) -> nodesForSelection.contains(n.name))
                                .collect(Collectors.toList()).get(0);
                    }
                }

                if (newNode == null) {
                    current = selectNextNode(nodes, current, lastToDeploy);
                } else {
                    current = newNode;
                }
            }
        }
    }

    /*
     * Select the next node to handle an populate dependencies for the current node. At this point
     * the current not should have no dependencies (or we wouldn't choose the next node randomly).
     * If the next node in the nodes list also has no dependencies, attach it as the last node to
     * deploy - that is, add to it a dependency on the last processed node nobody depends on.
     */
    private ResourceNode selectNextNode(List<ResourceNode> nodes, ResourceNode current,
            ResourceNode lastToDeploy) {
        ResourceNode newNode = nodes.get(0);

        if (newNode.dependsOn != null && !newNode.dependsOn.contains(current.name)) {
            if (current.dependsOn == null) {
                current.dependsOn = new HashSet<>();
            }
            current.dependsOn.add(newNode.name);
        } else if (newNode.dependsOn == null && current.dependsOn == null) {
            newNode.dependsOn = new HashSet<>();
            newNode.dependsOn.add(lastToDeploy.name);
            lastToDeploy.name = newNode.name;
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
                    continue;
                }

                for (PortBinding port : containerDescription.portBindings) {
                    if (port.hostPort == null || port.hostPort.isEmpty()) {
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
