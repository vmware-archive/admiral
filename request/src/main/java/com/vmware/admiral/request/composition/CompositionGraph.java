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

package com.vmware.admiral.request.composition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.volume.VolumeUtil;
import com.vmware.admiral.request.allocation.filter.AffinityFilters;
import com.vmware.admiral.request.allocation.filter.ImplicitDependencyFilters;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.ServiceHost;

public class CompositionGraph {
    private final Map<String, ResourceNode> resourceNodesByName;

    public CompositionGraph() {
        this.resourceNodesByName = new HashMap<>();
    }

    /**
     * Calculate the dependency order of the {@link ContainerDescription}s.
     *
     * @param host
     *            - serviceHost, optional
     * @param compositeDescription
     *            - list of {@link ContainerDescription}s to be provisioned.
     * @return list of {@link ResourceNode} order based on dependencies.
     *
     * @throws IllegalArgumentException
     *             If there are nodes with same name, missing nodes or the graph has cyclic
     *             dependencies.
     */
    public List<ResourceNode> calculateGraph(ServiceHost host,
            CompositeDescriptionExpanded compositeDescription) {
        AssertUtil.assertNotEmpty(compositeDescription.componentDescriptions, "serviceDocuments");

        populateResourceNodesByName(compositeDescription.componentDescriptions);
        calculateResourceDependsOnNodes(host, compositeDescription);
        addNamedVolumeConstraints(compositeDescription.componentDescriptions);
        calculateResourceNodeDependents();

        // Store dependOn ResourceNodes by ResourceNode name.
        final HashMap<String, Set<String>> dependsOn = new HashMap<>(
                compositeDescription.componentDescriptions.size());

        // First level nodes are the ones that don't depends on any other task.
        final Queue<ResourceNode> queue = calculateQueueWithFirstLevelNodes(dependsOn);

        // At least one node that has no dependency is needed to start
        if (queue.isEmpty()) {
            throw new LocalizableValidationException("Cyclic dependency detected.", "request.composition.cyclic.dependency");
        }

        // Store all nodes that are currently processed to check for cyclic dependencies
        final List<ResourceNode> processed = new ArrayList<>(
                compositeDescription.componentDescriptions.size());

        topologicalSortProcessingByBreathFirstSearch(queue, dependsOn, processed);

        if (processed.size() != resourceNodesByName().size()) {
            throw new LocalizableValidationException("Cyclic dependency detected after processing.",
                    "request.composition.cyclic.dependency.processing");
        }

        return processed;
    }

    /**
     * Calculate the dependency order of the {@link ContainerDescription}s.
     *
     * @param compositeDescription
     *            - list of {@link ContainerDescription}s to be provisioned.
     * @return list of {@link ResourceNode} order based on dependencies.
     *
     * @throws IllegalArgumentException
     *             If there are nodes with same name, missing nodes or the graph has cyclic
     *             dependencies.
     */
    public List<ResourceNode> calculateGraph(
            final CompositeDescriptionExpanded compositeDescription)
            throws IllegalArgumentException {

        return calculateGraph(null, compositeDescription);
    }

    private void addNamedVolumeConstraints(
            Collection<ComponentDescription> componentDescriptions) {

        VolumeUtil.applyLocalNamedVolumeConstraints(componentDescriptions);
    }

    private Queue<ResourceNode> calculateQueueWithFirstLevelNodes(
            final HashMap<String, Set<String>> dependsOn) {
        final Queue<ResourceNode> queue = resourceNodesByName().values().stream()
                .filter((r) -> {
                    if (r.dependsOn == null || r.dependsOn.isEmpty()) {
                        r.level = 1;
                        dependsOn.put(r.name, Collections.emptySet());
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toCollection(ConcurrentLinkedQueue::new));
        return queue;
    }

    /*
     * Calculate the nodes that are dependent on the current nodes and return the order.
     */
    private CompositionGraph calculateResourceNodeDependents() {
        for (final ResourceNode resourceNode : resourceNodesByName().values()) {
            if (resourceNode.dependsOn != null) {
                for (final String dependsOnName : resourceNode.dependsOn) {
                    final ResourceNode dependsOnNode = resourceNodesByName().get(dependsOnName);
                    if (dependsOnNode.dependents == null) {
                        dependsOnNode.dependents = new HashSet<>();
                    }
                    dependsOnNode.dependents.add(resourceNode.name);
                }
            }
        }
        return this;
    }

    public Collection<ResourceNode> getNodesPerExecutionLevel(int level) {
        return resourceNodesByName()
                .values().stream()
                .filter((r) -> level == r.level)
                .collect(Collectors.toSet());
    }

    public Map<String, ResourceNode> getResourceNodesByName() {
        return resourceNodesByName();
    }

    private void topologicalSortProcessingByBreathFirstSearch(final Queue<ResourceNode> queue,
            final HashMap<String, Set<String>> dependsOn, final List<ResourceNode> processed) {
        while (!queue.isEmpty()) {
            // process the next one in the queue
            final ResourceNode top = queue.poll();
            processed.add(top);

            if (top.dependents == null || top.dependents.isEmpty()) {
                // no more dependents, move to the next neighbor in the queue
                continue;
            }

            // Make sure the current node (top) have visited all dependents.
            for (final String dependentName : top.dependents) {
                final ResourceNode dependent = resourceNodesByName().get(dependentName);
                Set<String> dependsOnNames = dependsOn.get(dependentName);
                if (dependsOnNames == null) {
                    // keep count if all dependsOn nodes have visited already.
                    dependsOnNames = new HashSet<>(dependent.dependsOn);
                    dependsOn.put(dependent.name, dependsOnNames);
                }

                dependsOnNames.remove(top.name);

                // if the dependent have been visited from all nodes that the dependent depends On,
                // then add it to the queue as the next in the queue to be processed
                if (dependsOnNames.isEmpty()) {
                    queue.add(dependent);
                    dependent.level = top.level + 1;
                    // detect cyclic dependency if any
                    if (processed.contains(dependent)) {
                        throw new LocalizableValidationException(
                                "Cyclic dependency detected during processing.",
                                "request.composition.cyclic.dependency.during.process");
                    }
                }
            }
        }
    }

    public static class ResourceNode {
        public String name;
        public String resourceDescLink;
        public int level;
        public Set<String> dependsOn;
        public Set<String> dependents;
        public String resourceType;

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result
                    + ((resourceDescLink == null) ? 0 : resourceDescLink.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ResourceNode other = (ResourceNode) obj;
            if (name == null) {
                if (other.name != null) {
                    return false;
                }
            } else if (!name.equals(other.name)) {
                return false;
            }
            if (resourceDescLink == null) {
                if (other.resourceDescLink != null) {
                    return false;
                }
            } else if (!resourceDescLink.equals(other.resourceDescLink)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "ResourceNode [name=" + name + ", level=" + level + "]";
        }
    }

    /*
     * Traverse the componentDescriptions and transform the entities to ResourceNode. This method
     * checks for duplicate names and @throw IllegalArgumentException if more than one
     * contanerDescription with same name.
     */
    public Map<String, ResourceNode> populateResourceNodesByName(
            Collection<ComponentDescription> componentDescriptions) {
        this.resourceNodesByName.clear();

        for (final ComponentDescription d : componentDescriptions) {
            final ResourceNode resourceNode = new ResourceNode();
            resourceNode.name = d.name;
            resourceNode.resourceDescLink = d.getServiceDocument().documentSelfLink;
            resourceNode.resourceType = d.type;
            final ResourceNode previousNode = resourceNodesByName.put(
                    resourceNode.name, resourceNode);
            if (previousNode != null) {
                String errMsg = String
                        .format("Components with duplicate name [%s] detected for resources [%s] and [%s].",
                                resourceNode.name, resourceNode.resourceDescLink,
                                previousNode.resourceDescLink);
                throw new LocalizableValidationException(errMsg, "request.composition.duplicate.names",
                        resourceNode.name, resourceNode.resourceDescLink, previousNode.resourceDescLink);
            }
        }

        return resourceNodesByName;
    }

    /*
     * Use the Placement Selection filter to find out the needed dependencies for which the node
     * that a current node is dependent. <code>resourceNode.dependsOn</code> will be calculated.
     */
    public void calculateResourceDependsOnNodes(ServiceHost host,
            CompositeDescriptionExpanded compositeDescription) {
        for (final ComponentDescription cd : compositeDescription.componentDescriptions) {
            final AffinityFilters filters = AffinityFilters.build(host, cd);
            final ResourceNode resourceNode = resourceNodesByName.get(cd.name);
            final Set<String> dependencies = filters.getUniqueDependencies();
            if (!dependencies.isEmpty()) {
                resourceNode.dependsOn = new HashSet<>();
                for (String name : dependencies) {
                    ResourceNode rn = resourceNodesByName.get(name);
                    if (rn == null) {
                        String errMsg = String.format(
                                "Dependency on name: [%s] can't be resolved in component: [%s].",
                                name, resourceNode.name);
                        throw new LocalizableValidationException(errMsg, "request.composition.dependency.not.resolved",
                                name, resourceNode.name);
                    } else {
                        resourceNode.dependsOn.add(rn.name);
                    }
                }
            }
        }
        calculateImplicitDependencies(compositeDescription);
    }

    protected void calculateImplicitDependencies(CompositeDescriptionExpanded compositeDescription) {
        final ImplicitDependencyFilters filters = ImplicitDependencyFilters.build(resourceNodesByName, compositeDescription);
        filters.apply();
    }

    public Map<String, ResourceNode> resourceNodesByName() {
        return resourceNodesByName;
    }

}
