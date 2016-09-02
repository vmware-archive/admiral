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

package com.vmware.admiral.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.content.Binding;

public class BindingEvaluatorTest {

    @Test
    public void testEvaluateSingleBindingSimple() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription._cluster = 5;

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bindings = Arrays.asList(
                new Binding(Arrays.asList("_cluster"), "A~_cluster", false));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);

        firstDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(0).component;
        secondDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(1).component;

        assertEquals(firstDescription._cluster, secondDescription._cluster);
    }

    @Test
    public void testEvaluateMultipleBindingsSimple() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription._cluster = 5;
        firstDescription.memoryLimit = 5L;

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bindings = Arrays.asList(
                new Binding(Arrays.asList("_cluster"), "A~_cluster", false),
                new Binding(Arrays.asList("memory_limit"), "A~memory_limit", false));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);

        firstDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(0).component;
        secondDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(1).component;

        assertEquals(firstDescription._cluster, secondDescription._cluster);
        assertEquals(firstDescription.memoryLimit, secondDescription.memoryLimit);
    }

    @Test
    public void testEvaluateSingleBindingNestedSource() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription.logConfig = new LogConfig();
        firstDescription.logConfig.type = "type";

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bindings = Arrays.asList(
                new Binding(Arrays.asList("hostname"), "A~logConfig~type", false));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);

        firstDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(0).component;
        secondDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(1).component;

        assertEquals(firstDescription.logConfig.type, secondDescription.hostname);
    }

    @Test
    public void testEvaluateSingleBindingNestedSourceMap() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription.customProperties = new HashMap<>();
        firstDescription.customProperties.put("key", "value");

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bindings = Arrays.asList(
                new Binding(Arrays.asList("hostname"), "A~customProperties~key", false));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);

        firstDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(0).component;
        secondDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(1).component;

        assertEquals(firstDescription.customProperties.get("key"), secondDescription.hostname);
    }

    @Test
    public void testEvaluateSingleBindingNestedTarget() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription.logConfig = new LogConfig();

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";
        secondDescription.hostname = "hostname";

        List<Binding> bindings = Arrays.asList(
                new Binding(Arrays.asList("log_config", "type"), "B~hostname", false));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("A", bindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);

        firstDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(0).component;
        secondDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(1).component;

        assertEquals(firstDescription.logConfig.type, secondDescription.hostname);
    }

    @Test
    public void testEvaluateBindingsToString() {
        /**
         * B's hostname should be A's _cluster, but hostname is string and _cluster is Integer
         */
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription._cluster = 5;

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bindings = Arrays.asList(
                new Binding(Arrays.asList("hostname"), "A~_cluster", false));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);

        firstDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(0).component;
        secondDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(1).component;

        assertNotNull(secondDescription.hostname);
        assertEquals(firstDescription._cluster.toString(), secondDescription.hostname);
    }

    @Test
    public void testEvaluateBindingsParseInt() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription._cluster = 5;
        firstDescription.hostname = "12";

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bindings = Arrays.asList(
                new Binding(Arrays.asList("_cluster"), "A~hostname", false));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);

        firstDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(0).component;
        secondDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(1).component;

        assertNotNull(secondDescription._cluster);
        assertEquals(firstDescription.hostname, secondDescription._cluster.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEvaluateBindingsCyclicDependency() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bBindings = Arrays.asList(
                new Binding(Arrays.asList("_cluster"), "A~_cluster", false));
        Binding.ComponentBinding bComponentBinding = new Binding.ComponentBinding("B", bBindings);

        List<Binding> aBindings = Arrays.asList(
                new Binding(Arrays.asList("_cluster"), "B~_cluster", false));
        Binding.ComponentBinding aComponentBinding = new Binding.ComponentBinding("A", aBindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription),
                Arrays.asList(bComponentBinding, aComponentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);
    }

    @Test
    public void testEvaluateBindingsCyclicDependencyButResolvable() {
        /**
         * A has a binding to B and B has a binding to A, but different fields
         */

        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription.hostname = "firstHostname";

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";
        secondDescription.memoryLimit = 5L;

        List<Binding> bBindings = Arrays.asList(
                new Binding(Arrays.asList("hostname"), "A~hostname", false));
        Binding.ComponentBinding bComponentBinding = new Binding.ComponentBinding("B", bBindings);

        List<Binding> aBindings = Arrays.asList(
                new Binding(Arrays.asList("memory_limit"), "B~memory_limit", false));
        Binding.ComponentBinding aComponentBinding = new Binding.ComponentBinding("A", aBindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription),
                Arrays.asList(bComponentBinding, aComponentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);

        firstDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(0).component;
        secondDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(1).component;

        assertEquals(firstDescription.hostname, secondDescription.hostname);
        assertEquals(firstDescription.memoryLimit, secondDescription.memoryLimit);
    }

    @Test
    public void testEvaluateBindingsRecursively() {
        /**
         * A has a binding to B and B has a binding to C
         */

        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        ContainerDescription thirdDescription = new ContainerDescription();
        thirdDescription.name = "C";
        thirdDescription.memoryLimit = 5L;

        List<Binding> aBindings = Arrays.asList(
                new Binding(Arrays.asList("memory_limit"), "B~memory_limit", false));
        Binding.ComponentBinding aComponentBinding = new Binding.ComponentBinding("A", aBindings);

        List<Binding> bBindings = Arrays.asList(
                new Binding(Arrays.asList("memory_limit"), "C~memory_limit", false));
        Binding.ComponentBinding bComponentBinding = new Binding.ComponentBinding("B", bBindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription, thirdDescription),
                Arrays.asList(bComponentBinding, aComponentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);

        firstDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(0).component;
        secondDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(1).component;
        thirdDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(2).component;

        assertEquals(firstDescription.memoryLimit, thirdDescription.memoryLimit);
        assertEquals(secondDescription.memoryLimit, thirdDescription.memoryLimit);
    }

    @Test
    public void testEvaluateSingleBindingDifferentNumberTypes() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription._cluster = 5;

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bindings = Arrays.asList(
                new Binding(Arrays.asList("memory_limit"), "A~_cluster", false));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);

        firstDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(0).component;
        secondDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(1).component;

        assertNotNull(secondDescription.memoryLimit);
        assertEquals(firstDescription._cluster.toString(),
                secondDescription.memoryLimit.toString());
    }

    @Test
    public void testEvaluateSimpleProvisioningBinding() {

        List<Binding> bindings = Arrays.asList(
                new Binding(Arrays.asList("parentLink"), "_resource~A~parentLink",
                        true));

        Map<String, Object> containers = new HashMap<>();
        ContainerState containerState = new ContainerState();
        containerState.parentLink = "some-host";
        containers.put("A", containerState);

        ContainerState containerStateE = new ContainerState();
        Object o = BindingEvaluator
                .evaluateProvisioningTimeBindings(containerStateE, bindings, containers);
        assertEquals(((ContainerState) o).parentLink, containerState.parentLink);

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
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
        compositeDescription.bindings = componentBindings;
        return compositeDescription;
    }

    @Test
    public void testEvaluateSingleBindingCustomProperty() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription.customProperties = new HashMap<>();
        firstDescription.customProperties.put("key", "20");

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bindings = Arrays.asList(
                new Binding(Arrays.asList("_cluster"), "A~key", false));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeDescriptionExpanded compositeDescription = createCompositeDesc(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeDescription);

        firstDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(0).component;
        secondDescription = (ContainerDescription) compositeDescription.componentDescriptions
                .get(1).component;

        assertEquals(new Integer(20), secondDescription._cluster);
    }
}