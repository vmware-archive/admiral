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
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonPrimitive;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.content.Binding;
import com.vmware.admiral.compute.content.Binding.BindingPlaceholder;
import com.vmware.admiral.compute.content.ComponentTemplate;
import com.vmware.admiral.compute.content.CompositeTemplate;
import com.vmware.admiral.compute.content.NestedState;
import com.vmware.admiral.compute.content.TemplateComputeState;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;

public class BindingEvaluatorTest {

    @BeforeClass
    public static void setUp() {
        HostInitComputeServicesConfig.initCompositeComponentRegistry();
    }

    @Test
    public void testEvaluateSingleBindingSimple() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription._cluster = 5;

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bindings = Arrays.asList(
                binding(Arrays.asList("_cluster"), "A~_cluster"));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

        assertEquals(firstDescription._cluster, secondDescription._cluster);
    }

    @Test
    public void testEvaluateSingleClosureBinding() {
        Closure closure = new Closure();
        closure.name = "Closure";

        JsonPrimitive inStr = new JsonPrimitive("localhost");
        closure.inputs = new HashMap<>();
        closure.inputs.put("hostname", inStr);

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "Container";

        List<Binding> bindings = Arrays.asList(
                binding(Arrays.asList("hostname"), "Closure~inputs~hostname"));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("Container", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(closure, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        closure = (Closure) compositeTemplate.components.get("Closure").data;
        secondDescription = (ContainerDescription) compositeTemplate.components
                .get("Container").data;

        assertEquals(closure.inputs.get("hostname").getAsString(), secondDescription.hostname);
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
                binding(Arrays.asList("_cluster"), "A~_cluster"),
                binding(Arrays.asList("memory_limit"), "A~memory_limit"));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

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

        List<Binding> bindings = Arrays
                .asList(binding(Arrays.asList("hostname"), "A~logConfig~type"));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

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
                binding(Arrays.asList("hostname"), "A~customProperties~key"));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

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
                binding(Arrays.asList("log_config", "type"), "B~hostname"));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("A", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

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
                binding(Arrays.asList("hostname"), "A~_cluster"));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

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
                binding(Arrays.asList("_cluster"), "A~hostname"));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

        assertNotNull(secondDescription._cluster);
        assertEquals(firstDescription.hostname, secondDescription._cluster.toString());
    }

    @Test(expected = LocalizableValidationException.class)
    public void testEvaluateBindingsCyclicDependency() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bBindings = Arrays.asList(
                binding(Arrays.asList("_cluster"), "A~_cluster"));
        Binding.ComponentBinding bComponentBinding = new Binding.ComponentBinding("B", bBindings);

        List<Binding> aBindings = Arrays.asList(binding(Arrays.asList("_cluster"), "B~_cluster"));
        Binding.ComponentBinding aComponentBinding = new Binding.ComponentBinding("A", aBindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                        .asList(firstDescription, secondDescription),
                Arrays.asList(bComponentBinding, aComponentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);
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

        List<Binding> bBindings = Arrays.asList(binding(Arrays.asList("hostname"), "A~hostname"));
        Binding.ComponentBinding bComponentBinding = new Binding.ComponentBinding("B", bBindings);

        List<Binding> aBindings = Arrays
                .asList(binding(Arrays.asList("memory_limit"), "B~memory_limit"));
        Binding.ComponentBinding aComponentBinding = new Binding.ComponentBinding("A", aBindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                        .asList(firstDescription, secondDescription),
                Arrays.asList(bComponentBinding, aComponentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

        assertEquals(firstDescription.hostname, secondDescription.hostname);
        assertEquals(firstDescription.memoryLimit, secondDescription.memoryLimit);
    }

    @Test
    public void testEvaluateComplexBindingsRecursively() {
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

        List<Binding> aBindings = Arrays
                .asList(binding(Arrays.asList("memory_limit"), "B~memory_swap_limit"));
        Binding.ComponentBinding aComponentBinding = new Binding.ComponentBinding("A", aBindings);

        List<Binding> bBindings = Arrays
                .asList(binding(Arrays.asList("memory_swap_limit"), "C~memory_limit"));
        Binding.ComponentBinding bComponentBinding = new Binding.ComponentBinding("B", bBindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                        .asList(firstDescription, secondDescription, thirdDescription),
                Arrays.asList(bComponentBinding, aComponentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;
        thirdDescription = (ContainerDescription) compositeTemplate.components.get("C").data;

        assertEquals(firstDescription.memoryLimit, thirdDescription.memoryLimit);
        assertEquals(secondDescription.memorySwapLimit, thirdDescription.memoryLimit);
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

        List<Binding> aBindings = Arrays
                .asList(binding(Arrays.asList("memory_limit"), "B~memory_limit"));
        Binding.ComponentBinding aComponentBinding = new Binding.ComponentBinding("A", aBindings);

        List<Binding> bBindings = Arrays
                .asList(binding(Arrays.asList("memory_limit"), "C~memory_limit"));
        Binding.ComponentBinding bComponentBinding = new Binding.ComponentBinding("B", bBindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                        .asList(firstDescription, secondDescription, thirdDescription),
                Arrays.asList(bComponentBinding, aComponentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;
        thirdDescription = (ContainerDescription) compositeTemplate.components.get("C").data;

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

        List<Binding> bindings = Arrays
                .asList(binding(Arrays.asList("memory_limit"), "A~_cluster"));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

        assertNotNull(secondDescription.memoryLimit);
        assertEquals(firstDescription._cluster.toString(),
                secondDescription.memoryLimit.toString());
    }

    @Test
    public void testEvaluateSimpleProvisioningBinding() {

        List<Binding> bindings = Arrays
                .asList(binding(Arrays.asList("parentLink"), "_resource~A~parentLink"));

        Map<String, NestedState> containers = new HashMap<>();
        ContainerState containerState = new ContainerState();
        containerState.parentLink = "some-host";
        containers.put("A", new NestedState(containerState));

        ContainerState containerStateE = new ContainerState();
        NestedState evalObj = BindingEvaluator
                .evaluateProvisioningTimeBindings(new NestedState(containerStateE), bindings,
                        containers);
        assertNotNull(evalObj);
        assertEquals(((ContainerState) evalObj.object).parentLink, containerState.parentLink);

    }

    @Test
    public void testEvaluateSimpleBindingToObjectWithLinks() {

        List<Binding> bindings = Arrays
                .asList(binding(Arrays.asList("networks", "0", "address"),
                        "_resource~A~parentLink"));

        Map<String, NestedState> computes = new HashMap<>();
        TemplateComputeState computeStateA = new TemplateComputeState();
        computeStateA.parentLink = "some-host";

        computes.put("A", new NestedState(computeStateA));

        TemplateComputeState computeState = new TemplateComputeState();

        computeState.networkInterfaceLinks = Arrays.asList("nis-link");
        NetworkInterfaceState nis = new NetworkInterfaceState();
        nis.documentSelfLink = "nis-link";

        NestedState nestedState = new NestedState(computeState);
        nestedState.children.put(nis.documentSelfLink, new NestedState(nis));

        NestedState evalObj = BindingEvaluator
                .evaluateProvisioningTimeBindings(nestedState, bindings, computes);
        assertNotNull(evalObj);
        assertEquals(((NetworkInterfaceState) evalObj.children.get("nis-link").object).address,
                computeStateA.parentLink);

    }

    @Test
    public void testEvaluateSimpleBindingFromObjectWithLinks() {

        List<Binding> bindings = Arrays
                .asList(binding(Arrays.asList("parentLink"),
                        "_resource~A~networkInterfaceLinks~0~address"));

        Map<String, NestedState> computes = new HashMap<>();
        TemplateComputeState computeStateA = new TemplateComputeState();
        computeStateA.parentLink = "some-host";

        computeStateA.networkInterfaceLinks = Arrays.asList("nis-link");
        NetworkInterfaceState nis = new NetworkInterfaceState();
        nis.documentSelfLink = "nis-link";
        nis.address = "some-address";

        NestedState nestedStateA = new NestedState(computeStateA);
        nestedStateA.children.put("nis-link", new NestedState(nis));

        computes.put("A", nestedStateA);

        TemplateComputeState computeState = new TemplateComputeState();

        NestedState nestedState = new NestedState(computeState);

        NestedState evalObj = BindingEvaluator
                .evaluateProvisioningTimeBindings(nestedState, bindings, computes);
        assertNotNull(evalObj);
        assertEquals(nis.address, ((TemplateComputeState) evalObj.object).parentLink);

    }

    public static CompositeTemplate createCompositeTemplate(
            List<? extends ResourceState> containerDescriptions,
            List<Binding.ComponentBinding> componentBindings) {
        CompositeTemplate compositeTemplate = new CompositeTemplate();
        compositeTemplate.components = containerDescriptions.stream()
                .collect(Collectors.toMap(cd -> cd.name, cd -> {
                    ComponentTemplate componentTemplate = new ComponentTemplate();
                    componentTemplate.type = ResourceType.CONTAINER_TYPE.getContentType();
                    componentTemplate.data = cd;
                    return componentTemplate;
                }));
        compositeTemplate.bindings = componentBindings;
        return compositeTemplate;
    }

    @Test
    public void testEvaluateSingleBindingCustomProperty() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription.customProperties = new HashMap<>();
        firstDescription.customProperties.put("key", "20");

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bindings = Arrays.asList(binding(Arrays.asList("_cluster"), "A~key"));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

        assertEquals(new Integer(20), secondDescription._cluster);
    }

    @Test
    public void testEvaluateSingleBindingWithDefaultValue() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        List<Binding> bindings = Arrays.asList(
                binding(Arrays.asList("_cluster"), "A~_cluster", "5"));
        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

        assertNull(firstDescription._cluster);
        assertEquals(new Integer(5), secondDescription._cluster);
    }

    @Test
    public void testEvaluateSingleBindingWithAdditionalContent() {
        ContainerDescription firstDescription = new ContainerDescription();
        firstDescription.name = "A";
        firstDescription.hostname = "10.0.0.1";

        ContainerDescription secondDescription = new ContainerDescription();
        secondDescription.name = "B";

        Binding binding = new Binding(Arrays.asList("hostname"), "${A~hostname}:2376",
                new BindingPlaceholder("A~hostname"));
        List<Binding> bindings = Arrays.asList(binding);

        Binding.ComponentBinding componentBinding = new Binding.ComponentBinding("B", bindings);

        CompositeTemplate compositeTemplate = createCompositeTemplate(Arrays
                .asList(firstDescription, secondDescription), Arrays.asList(componentBinding));

        BindingEvaluator.evaluateBindings(compositeTemplate);

        firstDescription = (ContainerDescription) compositeTemplate.components.get("A").data;
        secondDescription = (ContainerDescription) compositeTemplate.components.get("B").data;

        assertNotNull(secondDescription.hostname);
        assertEquals("10.0.0.1:2376", secondDescription.hostname);
    }

    private static Binding binding(List<String> targetFieldPath, String placeholder) {
        return new Binding(targetFieldPath, String.format("${%s}", placeholder),
                new BindingPlaceholder(placeholder));
    }

    private static Binding binding(List<String> targetFieldPath, String placeholder,
            String defaultValue) {
        return new Binding(targetFieldPath, String.format("${%s}", placeholder),
                new BindingPlaceholder(placeholder, defaultValue));
    }
}