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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.compute.BindingUtils.FIELD_SEPARATOR;
import static com.vmware.admiral.compute.BindingUtils.RESOURCE;
import static com.vmware.admiral.request.util.TestRequestStateFactory.createContainerDescription;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.content.Binding;
import com.vmware.admiral.compute.content.Binding.BindingPlaceholder;
import com.vmware.admiral.compute.content.Binding.ComponentBinding;
import com.vmware.admiral.request.composition.CompositionGraph.ResourceNode;
import com.vmware.xenon.common.LocalizableValidationException;

public class CompositionGraphTest {
    private CompositionGraph graph;

    @Before
    public void setup() {
        graph = new CompositionGraph() {
            @Override
            protected void calculateImplicitDependencies(
                    CompositeDescriptionExpanded compositeDescription) {
                // Do nothing since we are a simple unit test and cannot deal with
                // inter-component dependencies, which depend on JSON deserialization
            }
        };
    }

    @Test
    public void testWithOneNode() {
        ContainerDescription desc = createContainerDescription();
        CompositeDescriptionExpanded compositeDesc = createCompositeDesc(Arrays.asList(desc),
                Collections.emptyList());
        Collection<ResourceNode> resourceNodes = graph.calculateGraph(compositeDesc);
        assertEquals(1, resourceNodes.size());

        ResourceNode resourceNode = resourceNodes.iterator().next();
        assertEquals(desc.name, resourceNode.name);
        assertEquals(desc.documentSelfLink, resourceNode.resourceDescLink);
        assertNull(resourceNode.dependsOn);
        assertNull(resourceNode.dependents);
    }

    @Test(expected = LocalizableValidationException.class)
    public void failWhenSingleNodeWithDependency() {
        ContainerDescription desc = createContainerDescription();
        desc.volumesFrom = new String[] { "non_existing_container_name" };

        CompositeDescriptionExpanded compositeDesc = createCompositeDesc(Arrays.asList(desc),
                Collections.emptyList());
        graph.calculateGraph(compositeDesc);
        // expect error for not finding dependencies.
    }

    @Test(expected = LocalizableValidationException.class)
    public void failWhenTwoComponentsWithSameName() {
        String sameName = "sameName";
        ContainerDescription desc1 = createContainerDescription(sameName);
        ContainerDescription desc2 = createContainerDescription(sameName);
        desc1.volumesFrom = new String[] { desc2.name };

        CompositeDescriptionExpanded compositeDesc = createCompositeDesc(
                Arrays.asList(desc1, desc2), Collections.emptyList());
        graph.calculateGraph(compositeDesc);
        // expect error for not two components with same name.
    }

    @Test
    public void testDependsOn() {
        ContainerDescription desc1 = createContainerDescription("name1");
        ContainerDescription desc2 = createContainerDescription("name2");
        ContainerDescription desc3 = createContainerDescription("name3");

        desc1.dependsOn = new String[] { desc2.name, desc3.name };
        desc2.dependsOn = new String[] { desc3.name };

        CompositeDescriptionExpanded compositeDesc = createCompositeDesc(
                Arrays.asList(desc1, desc2, desc3), Collections.emptyList());
        List<ResourceNode> nodes = graph.calculateGraph(compositeDesc);

        //the order should be name3 -> name2 -> name1
        Object[] actualOrder = nodes.stream().map(node -> node.name).toArray();
        assertArrayEquals(new String[] { desc3.name, desc2.name, desc1.name }, actualOrder);

        assertTrue(nodes.get(0).dependents.contains(desc1.name));
        assertTrue(nodes.get(0).dependents.contains(desc2.name));
        assertTrue(nodes.get(0).dependsOn == null || nodes.get(0).dependsOn.isEmpty());

        assertTrue(nodes.get(1).dependents.contains(desc1.name));
        assertTrue(!nodes.get(1).dependents.contains(desc3.name));
        assertTrue(nodes.get(1).dependsOn.contains(desc3.name));
        assertTrue(!nodes.get(1).dependsOn.contains(desc1.name));

        assertTrue(nodes.get(2).dependsOn.contains(desc2.name));
        assertTrue(nodes.get(2).dependsOn.contains(desc3.name));
        assertTrue(nodes.get(2).dependents == null || nodes.get(0).dependents.isEmpty());
    }

    @Test(expected = LocalizableValidationException.class)
    public void failWhenInitialCyclicDependecies() {
        ContainerDescription desc1 = createContainerDescription("name1");
        ContainerDescription desc2 = createContainerDescription("name2");
        ContainerDescription desc3 = createContainerDescription("name3");

        desc1.volumesFrom = new String[] { desc2.name };
        desc2.affinity = new String[] { desc3.name };
        desc3.affinity = new String[] { desc1.name };

        CompositeDescriptionExpanded compositeDesc = createCompositeDesc(
                Arrays.asList(desc1, desc2, desc3), Collections.emptyList());
        graph.calculateGraph(compositeDesc);
        // expect error for cyclic dependencies
    }

    @Test(expected = LocalizableValidationException.class)
    public void failWhenInnerCyclicDependecies() {
        ContainerDescription desc1 = createContainerDescription("name1");
        ContainerDescription desc2 = createContainerDescription("name2");
        ContainerDescription desc3 = createContainerDescription("name3");
        ContainerDescription desc4 = createContainerDescription("name4");

        desc1.volumesFrom = new String[] { desc3.name, desc2.name };
        desc2.affinity = new String[] { desc4.name };
        desc4.affinity = new String[] { desc1.name };

        CompositeDescriptionExpanded compositeDesc = createCompositeDesc(
                Arrays.asList(desc1, desc2, desc3, desc4), Collections.emptyList());
        graph.calculateGraph(compositeDesc);
        graph.getNodesPerExecutionLevel(0);
        // expect error for cyclic dependencies
    }

    @Test
    public void simpleDependency() {
        simpleDependencyImpl(false);
    }

    @Test
    public void simpleClusteredDependency() {
        simpleDependencyImpl(true);
    }

    private void simpleDependencyImpl(boolean cluster) {
        ContainerDescription desc1 = createContainerDescription("name1");
        ContainerDescription desc2 = createContainerDescription("name2");
        desc1.volumesFrom = new String[] { desc2.name };
        if (cluster) {
            desc1._cluster = 2;
        }

        CompositeDescriptionExpanded compositeDesc = createCompositeDesc(
                Arrays.asList(desc1, desc2), Collections.emptyList());
        graph.calculateGraph(compositeDesc);
        Map<String, ResourceNode> resourceNodesByName = graph.getResourceNodesByName();
        assertEquals(2, resourceNodesByName.size());
        ResourceNode resourceNode1 = resourceNodesByName.get(desc1.name);
        assertNotNull(resourceNode1);

        ResourceNode resourceNode2 = resourceNodesByName.get(desc2.name);
        assertNotNull(resourceNode2);

        assertEquals(desc1.name, resourceNode1.name);
        assertEquals(desc1.documentSelfLink, resourceNode1.resourceDescLink);

        assertNull(resourceNode1.dependents);

        assertEquals(1, resourceNode1.dependsOn.size());
        assertEquals(resourceNode2.name, resourceNode1.dependsOn.iterator().next());

        assertEquals(desc2.name, resourceNode2.name);
        assertEquals(desc2.documentSelfLink, resourceNode2.resourceDescLink);

        assertNull(resourceNode2.dependsOn);

        assertEquals(1, resourceNode2.dependents.size());

        assertEquals(resourceNode1.name, resourceNode2.dependents.iterator().next());
    }

    @Test
    public void complexGraph() {
        // Graph:
        // ..................................
        // .1...d0.........d1............d2..
        // ................/\.........../....
        // .2............d3..d4........d5....
        // ............./............../.....
        // .3.........d6..............d7.....
        // ........../..\............/..\....
        // .4......d8....d9........d10..d11..
        // ......./.|.\......................
        // .5..d12.d13.d14...................
        // ..................................

        ContainerDescription[] descs = new ContainerDescription[15];
        // level 1:
        descs[0] = createContainerDescription("name0");
        descs[1] = createContainerDescription("name1");
        descs[2] = createContainerDescription("name2");

        // level 2:
        descs[3] = createContainerDescription("name3");
        descs[4] = createContainerDescription("name4");
        descs[5] = createContainerDescription("name5");

        descs[1].volumesFrom = new String[] { descs[3].name };
        descs[1].affinity = new String[] { descs[4].name };
        descs[2].affinity = new String[] { descs[5].name };

        // level 3:
        descs[6] = createContainerDescription("name6");
        descs[7] = createContainerDescription("name7");
        descs[3].volumesFrom = new String[] { descs[6].name };
        descs[5].links = new String[] { descs[7].name };

        // level 4:
        descs[8] = createContainerDescription("name8");
        descs[9] = createContainerDescription("name9");
        descs[10] = createContainerDescription("name10");
        descs[11] = createContainerDescription("name11");

        descs[6].affinity = new String[] { descs[9].name, descs[8].name };
        descs[7].affinity = new String[] { descs[10].name, descs[11].name };

        // level 5:
        descs[12] = createContainerDescription("name12");
        descs[13] = createContainerDescription("name13");
        descs[14] = createContainerDescription("name14");
        descs[8].volumesFrom = new String[] { descs[12].name, descs[13].name };
        descs[8].affinity = new String[] { descs[14].name };

        CompositeDescriptionExpanded compositeDesc = createCompositeDesc(Arrays.asList(descs),
                Collections.emptyList());
        Collection<ResourceNode> calculatedGraph = graph.calculateGraph(compositeDesc);
        assertEquals(descs.length, calculatedGraph.size());

        Map<String, ResourceNode> resourceNodesByName = graph.getResourceNodesByName();

        assertEquals(descs.length, resourceNodesByName.size());

        // Level 1:
        ResourceNode node0 = resourceNodesByName.get(descs[0].name);
        assertNotNull(node0);
        assertNull(node0.dependsOn);
        assertNull(node0.dependents);

        ResourceNode node1 = resourceNodesByName.get(descs[1].name);
        assertNull(node1.dependents);
        assertEquals(2, node1.dependsOn.size());
        assertTrue(node1.dependsOn.stream()
                .allMatch((n) -> n.equals(descs[3].name) || n.equals(descs[4].name)));

        ResourceNode node2 = resourceNodesByName.get(descs[2].name);
        assertNull(node2.dependents);
        assertEquals(1, node2.dependsOn.size());
        assertEquals(descs[5].name, node2.dependsOn.iterator().next());

        // Level 2:
        ResourceNode node3 = resourceNodesByName.get(descs[3].name);
        assertEquals(1, node3.dependsOn.size());
        assertEquals(descs[6].name, node3.dependsOn.iterator().next());
        assertEquals(1, node3.dependents.size());
        assertEquals(descs[1].name, node3.dependents.iterator().next());

        ResourceNode node4 = resourceNodesByName.get(descs[4].name);
        assertNotNull(node4);
        assertNull(node4.dependsOn);
        assertEquals(1, node3.dependents.size());
        assertEquals(descs[1].name, node4.dependents.iterator().next());

        ResourceNode node5 = resourceNodesByName.get(descs[5].name);
        assertEquals(1, node5.dependsOn.size());
        assertEquals(descs[7].name, node5.dependsOn.iterator().next());
        assertEquals(1, node5.dependents.size());
        assertEquals(descs[2].name, node5.dependents.iterator().next());

        // Level 3:
        ResourceNode node6 = resourceNodesByName.get(descs[6].name);
        assertEquals(2, node1.dependsOn.size());
        assertTrue(node6.dependsOn.stream()
                .allMatch((n) -> n.equals(descs[8].name) || n.equals(descs[9].name)));
        assertEquals(1, node6.dependents.size());
        assertEquals(descs[3].name, node6.dependents.iterator().next());

        ResourceNode node7 = resourceNodesByName.get(descs[7].name);
        assertEquals(2, node7.dependsOn.size());
        assertTrue(node7.dependsOn.stream()
                .allMatch((n) -> n.equals(descs[10].name) || n.equals(descs[11].name)));
        assertEquals(1, node7.dependents.size());
        assertEquals(descs[5].name, node7.dependents.iterator().next());

        // Level 4:
        ResourceNode node8 = resourceNodesByName.get(descs[8].name);
        assertEquals(3, node8.dependsOn.size());
        assertTrue(node8.dependsOn.stream()
                .allMatch((n) -> n.equals(descs[12].name)
                        || n.equals(descs[13].name)
                        || n.equals(descs[14].name)));
        assertEquals(1, node8.dependents.size());
        assertEquals(descs[6].name, node8.dependents.iterator().next());

        ResourceNode node9 = resourceNodesByName.get(descs[9].name);
        assertNull(node9.dependsOn);
        assertEquals(1, node9.dependents.size());
        assertEquals(descs[6].name, node9.dependents.iterator().next());

        ResourceNode node10 = resourceNodesByName.get(descs[10].name);
        assertNull(node10.dependsOn);
        assertEquals(1, node10.dependents.size());
        assertEquals(descs[7].name, node10.dependents.iterator().next());

        ResourceNode node11 = resourceNodesByName.get(descs[11].name);
        assertNull(node11.dependsOn);
        assertEquals(1, node11.dependents.size());
        assertEquals(descs[7].name, node11.dependents.iterator().next());

        // Level 5:
        ResourceNode node12 = resourceNodesByName.get(descs[12].name);
        assertNull(node12.dependsOn);
        assertEquals(1, node12.dependents.size());
        assertEquals(descs[8].name, node12.dependents.iterator().next());

        ResourceNode node13 = resourceNodesByName.get(descs[13].name);
        assertNull(node13.dependsOn);
        assertEquals(1, node13.dependents.size());
        assertEquals(descs[8].name, node13.dependents.iterator().next());

        ResourceNode node14 = resourceNodesByName.get(descs[14].name);
        assertNull(node14.dependsOn);
        assertEquals(1, node14.dependents.size());
        assertEquals(descs[8].name, node14.dependents.iterator().next());

        // Execution level 1: (nodes that could be process first)
        assertTrue(graph.getNodesPerExecutionLevel(1).stream().allMatch(
                (n) -> descs[0].name.equals(n.name)
                        || descs[4].name.equals(n.name)
                        || descs[9].name.equals(n.name)
                        || descs[10].name.equals(n.name)
                        || descs[11].name.equals(n.name)
                        || descs[12].name.equals(n.name)
                        || descs[13].name.equals(n.name)
                        || descs[14].name.equals(n.name)
        ));

        // Execution level 2: (nodes that could be process next)
        assertTrue(graph.getNodesPerExecutionLevel(2).stream().allMatch(
                (n) -> descs[8].name.equals(n.name) || descs[7].name.equals(n.name)));

        // Execution level 3: (nodes that could be process next)
        assertTrue(graph.getNodesPerExecutionLevel(3).stream().allMatch(
                (n) -> descs[6].name.equals(n.name) || descs[5].name.equals(n.name)));

        // Execution level 4: (nodes that could be process next)
        assertTrue(graph.getNodesPerExecutionLevel(4).stream().allMatch(
                (n) -> descs[3].name.equals(n.name) || descs[2].name.equals(n.name)));
        // Execution level 5: (nodes that could be process next)
        assertTrue(graph.getNodesPerExecutionLevel(6).stream().allMatch(
                (n) -> descs[1].name.equals(n.name)));

        assertTrue(graph.getNodesPerExecutionLevel(7).isEmpty());

        assertEquals(Arrays.asList(
                node14, node4, node13, node12, node11, node9, node10, node0,
                node8, node7,
                node6, node5,
                node3, node2,
                node1
        ), calculatedGraph);

    }

    @Test
    public void testBindingDependencies() {
        ContainerDescription desc1 = createContainerDescription("name1");
        ContainerDescription desc2 = createContainerDescription("name2");
        ContainerDescription desc3 = createContainerDescription("name3");

        ComponentBinding cbDesc1 = new ComponentBinding(desc1.name, Arrays.asList(
                binding(Collections.emptyList(),
                        RESOURCE + FIELD_SEPARATOR + desc2.name + "~address"),
                binding(Collections.emptyList(),
                        RESOURCE + FIELD_SEPARATOR + desc3.name + "~address")
        ));

        ComponentBinding cbDesc2 = new ComponentBinding(desc2.name, Arrays.asList(
                binding(Collections.emptyList(),
                        RESOURCE + FIELD_SEPARATOR + desc3.name + "~address")
        ));

        CompositeDescriptionExpanded compositeDesc = createCompositeDesc(
                Arrays.asList(desc1, desc2, desc3), Arrays.asList(cbDesc1, cbDesc2));

        List<ResourceNode> nodes = graph.calculateGraph(compositeDesc);
        //the order should be name3 -> name2 -> name1
        Object[] actualOrder = nodes.stream().map(node -> node.name).toArray();
        assertArrayEquals(new String[] { desc3.name, desc2.name, desc1.name }, actualOrder);

        assertTrue(nodes.get(0).dependents.contains(desc1.name));
        assertTrue(nodes.get(0).dependents.contains(desc2.name));
        assertTrue(nodes.get(0).dependsOn == null || nodes.get(0).dependsOn.isEmpty());

        assertTrue(nodes.get(1).dependents.contains(desc1.name));
        assertTrue(!nodes.get(1).dependents.contains(desc3.name));
        assertTrue(nodes.get(1).dependsOn.contains(desc3.name));
        assertTrue(!nodes.get(1).dependsOn.contains(desc1.name));

        assertTrue(nodes.get(2).dependsOn.contains(desc2.name));
        assertTrue(nodes.get(2).dependsOn.contains(desc3.name));
        assertTrue(nodes.get(2).dependents == null || nodes.get(0).dependents.isEmpty());
    }

    public static CompositeDescriptionExpanded createCompositeDesc(
            List<ContainerDescription> containerDescriptions,
            List<Binding.ComponentBinding> componentBindings) {
        CompositeDescriptionExpanded compositeDescription = new CompositeDescriptionExpanded();
        compositeDescription.componentDescriptions = containerDescriptions.stream()
                .map(cd -> new ComponentDescription(cd,
                        ResourceType.CONTAINER_TYPE.getName(), cd.name,
                        componentBindings.stream().filter(cb -> cb.componentName.equals(cd.name))
                                .flatMap(cb -> cb.bindings.stream())
                                .collect(Collectors.toList()))).collect(Collectors.toList());
        compositeDescription.bindings = componentBindings;
        return compositeDescription;
    }

    private static Binding binding(List<String> targetFieldPath, String placeholder) {
        return new Binding(targetFieldPath, String.format("${%s}", placeholder),
                new BindingPlaceholder(placeholder));
    }

}
