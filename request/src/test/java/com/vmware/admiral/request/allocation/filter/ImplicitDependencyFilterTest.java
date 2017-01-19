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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.request.composition.CompositionGraph;
import com.vmware.admiral.request.composition.CompositionGraph.ResourceNode;

public class ImplicitDependencyFilterTest extends BaseTestCase {

    private static final String CONTAINER_DESC_BASE_NAME = "test desc";

    @BeforeClass
    public static void beforeClass() {
        CompositeComponentRegistry.registerComponent(ResourceType.CONTAINER_TYPE.getName(),
                ContainerDescriptionService.FACTORY_LINK,
                ContainerDescription.class, ContainerFactoryService.SELF_LINK,
                ContainerState.class);
    }

    @Test
    public void testDuplicateHostPorts() throws Throwable {
        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "80";
        portBinding.hostPort = "8080";
        PortBinding[] bindings = new PortBinding[] { portBinding };

        CompositeDescriptionExpanded expanded = createExpandedComposite(bindings, bindings);
        Map<String, ResourceNode> resourceNodes = new CompositionGraph().populateResourceNodesByName(expanded.componentDescriptions);
        ImplicitDependencyFilters filters = ImplicitDependencyFilters.build(resourceNodes, expanded);
        filters.apply();

        assertTrue(resourceNodes.get(CONTAINER_DESC_BASE_NAME + 0).dependsOn != null ^
                resourceNodes.get(CONTAINER_DESC_BASE_NAME + 1).dependsOn != null);
    }

    @Test
    public void testDuplicateHostPortsWithPredefinedDependency() throws Throwable {
        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "80";
        portBinding.hostPort = "8080";
        PortBinding[] bindings = new PortBinding[] { portBinding };

        CompositeDescriptionExpanded expanded = createExpandedComposite(bindings, bindings, bindings);
        Map<String, ResourceNode> resourceNodes = new CompositionGraph().populateResourceNodesByName(expanded.componentDescriptions);

        // Add dependency
        ResourceNode n1 = resourceNodes.get(CONTAINER_DESC_BASE_NAME + 1);
        ResourceNode n2 = resourceNodes.get(CONTAINER_DESC_BASE_NAME + 2);
        n1.dependsOn = new HashSet<>();
        n1.dependsOn.add(n2.name);

        ImplicitDependencyFilters filters = ImplicitDependencyFilters.build(resourceNodes, expanded);
        filters.apply();

        assertEquals(1, n1.dependsOn.size());
        assertTrue(n1.dependsOn.contains(n2.name));
        assertNull(n2.dependsOn);

        ResourceNode n0 = resourceNodes.get(CONTAINER_DESC_BASE_NAME + 0);
        assertNotNull(n0.dependsOn);
        assertEquals(1, n0.dependsOn.size());
        assertTrue(n0.dependsOn.contains(n1.name));
    }

    /**
     * Extend the setup of testDuplicateHostPortsWithPredefinedDependency with a second predefined dependency
     */
    @Test
    public void testDuplicateHostPortsWithLinearDependencies() throws Throwable {
        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "80";
        portBinding.hostPort = "8080";
        PortBinding[] bindings = new PortBinding[] { portBinding };

        CompositeDescriptionExpanded expanded = createExpandedComposite(bindings, bindings, bindings);
        Map<String, ResourceNode> resourceNodes = new CompositionGraph().populateResourceNodesByName(expanded.componentDescriptions);

        // Add dependency
        ResourceNode n1 = resourceNodes.get(CONTAINER_DESC_BASE_NAME + 1);
        ResourceNode n2 = resourceNodes.get(CONTAINER_DESC_BASE_NAME + 2);
        n1.dependsOn = new HashSet<>();
        n1.dependsOn.add(n2.name);

        ResourceNode n0 = resourceNodes.get(CONTAINER_DESC_BASE_NAME + 0);
        n2.dependsOn = new HashSet<>();
        n2.dependsOn.add(n0.name);

        ImplicitDependencyFilters filters = ImplicitDependencyFilters.build(resourceNodes, expanded);
        filters.apply();

        assertNull(n0.dependsOn);

        assertEquals(1, n1.dependsOn.size());
        assertTrue(n1.dependsOn.contains(n2.name));

        assertEquals(1, n2.dependsOn.size());
        assertTrue(n2.dependsOn.contains(n0.name));
    }

    @Test
    public void testDuplicateHostPortsWithPredefinedComplexDependencies() throws Throwable {
        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "80";
        portBinding.hostPort = "8080";
        PortBinding[] bindings = new PortBinding[] { portBinding };

        CompositeDescriptionExpanded expanded = createExpandedComposite(bindings, bindings, null, bindings);
        Map<String, ResourceNode> resourceNodes = new CompositionGraph().populateResourceNodesByName(expanded.componentDescriptions);

        // Add dependencies
        ResourceNode n0 = resourceNodes.get(CONTAINER_DESC_BASE_NAME + 0);
        ResourceNode n1 = resourceNodes.get(CONTAINER_DESC_BASE_NAME + 1);
        ResourceNode n2 = resourceNodes.get(CONTAINER_DESC_BASE_NAME + 2);
        ResourceNode n3 = resourceNodes.get(CONTAINER_DESC_BASE_NAME + 3);

        n1.dependsOn = new HashSet<>();
        n1.dependsOn.add(n0.name);

        n2.dependsOn = new HashSet<>();
        n2.dependsOn.add(n1.name);
        n2.dependsOn.add(n3.name);

        ImplicitDependencyFilters filters = ImplicitDependencyFilters.build(resourceNodes, expanded);
        filters.apply();

        assertNull(n0.dependsOn);
        assertNotNull(n1.dependsOn);
        assertEquals(1, n1.dependsOn.size());
        assertNotNull(n2.dependsOn);
        assertEquals(2, n2.dependsOn.size());
        assertNotNull(n3.dependsOn);
        assertEquals(1, n3.dependsOn.size());
        assertTrue(n3.dependsOn.contains(n1.name));
    }

    @Test
    public void testDuplicateHostPortsMultipleContainers() throws Throwable {
        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "80";
        portBinding.hostPort = "8080";
        PortBinding[] bindings = new PortBinding[] { portBinding };

        CompositeDescriptionExpanded expanded = createExpandedComposite(bindings, bindings, null);
        Map<String, ResourceNode> resourceNodes = new CompositionGraph().populateResourceNodesByName(expanded.componentDescriptions);
        ImplicitDependencyFilters filters = ImplicitDependencyFilters.build(resourceNodes, expanded);
        filters.apply();

        assertTrue(resourceNodes.get(expanded.componentDescriptions.get(0).name).dependsOn != null ^
                resourceNodes.get(expanded.componentDescriptions.get(1).name).dependsOn != null);

        expanded = createExpandedComposite(bindings, bindings, bindings);
        resourceNodes = new CompositionGraph().populateResourceNodesByName(expanded.componentDescriptions);
        filters = ImplicitDependencyFilters.build(resourceNodes, expanded);
        filters.apply();

        int size = 0;
        if (resourceNodes.get(expanded.componentDescriptions.get(0).name).dependsOn != null) {
            size += resourceNodes.get(expanded.componentDescriptions.get(0).name).dependsOn.size();
        }

        if (resourceNodes.get(expanded.componentDescriptions.get(1).name).dependsOn != null) {
            size += resourceNodes.get(expanded.componentDescriptions.get(1).name).dependsOn.size();
        }

        if (resourceNodes.get(expanded.componentDescriptions.get(2).name).dependsOn != null) {
            size += resourceNodes.get(expanded.componentDescriptions.get(2).name).dependsOn.size();
        }

        assertEquals(2, size);
    }

    @Test
    public void testSingleComponent() throws Throwable {
        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "80";
        portBinding.hostPort = "8080";
        PortBinding[] bindings = new PortBinding[] { portBinding };

        CompositeDescriptionExpanded expanded = createExpandedComposite(bindings);
        Map<String, ResourceNode> resourceNodes = new CompositionGraph().populateResourceNodesByName(expanded.componentDescriptions);
        ImplicitDependencyFilters filters = ImplicitDependencyFilters.build(resourceNodes, expanded);
        filters.apply();

        assertNull(resourceNodes.get(expanded.componentDescriptions.get(0).name).dependsOn);
    }

    @Test
    public void testNoDependencies() throws Throwable {
        CompositeDescriptionExpanded expanded = createExpandedComposite(null, null);
        Map<String, ResourceNode> resourceNodes = new CompositionGraph().populateResourceNodesByName(expanded.componentDescriptions);
        ImplicitDependencyFilters filters = ImplicitDependencyFilters.build(resourceNodes, expanded);
        filters.apply();

        assertNull(resourceNodes.get(expanded.componentDescriptions.get(0).name).dependsOn);
        assertNull(resourceNodes.get(expanded.componentDescriptions.get(1).name).dependsOn);
    }

    private CompositeDescriptionExpanded createExpandedComposite(PortBinding[]... bindings) {
        CompositeDescriptionExpanded cdExpanded = new CompositeDescriptionExpanded();
        cdExpanded.name = "Test Composite";
        cdExpanded.componentDescriptions = new ArrayList<>();

        int i = 0;
        for (PortBinding[] binding : bindings) {
            ContainerDescription desc = new ContainerDescription();
            desc.name = CONTAINER_DESC_BASE_NAME + i++;
            desc.portBindings = binding;

            cdExpanded.componentDescriptions.add(
                    new ComponentDescription(desc, ResourceType.CONTAINER_TYPE.getName(), desc.name, new ArrayList<>()));
        }

        return cdExpanded;
    }
}
