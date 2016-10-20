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

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.content.Binding;
import com.vmware.admiral.compute.content.Binding.ComponentBinding;
import com.vmware.admiral.compute.content.YamlMapper;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.ServiceDocument;

/**
 * Utility class for working with Strings on a Composite description level, that have bindings in
 * them. The bindings can be used to reference field values between Components in a Composite
 * description. Using bindings creates an implicit dependencies between Components.
 *
 * <p>
 * A bindings takes the form {@code ${...}}. The binding value is a bean path expression, using
 * {@code ~} as delimiter. The first element in the expression is the component name, followed a
 * field name.
 *
 * <p>
 * Example :
 *
 * <pre class="code">
 * components:
 *    wordpress:
 *      type: App.Container
 *      data:
 *        restart_policy: ${mysql~restart_policy}
 *        ...
 *    mysql:
 *      type: App.Container
 *      data:
 *          restart_policy: "no"
 *          ...
 * </pre>
 *
 * <p>
 * {@code _resource} is a reserved value. When used in the binding expression, it represent the
 * provisioned resource. The syntax is {@code ${_resource~compName~fieldName}, which is translates
 * to using the value of field {@code fieldName} of the provisioned resource defined by
 * {@code compName} Component.
 *
 * <p>
 * Example :
 *
 * <pre class="code">
 * components:
 *    wordpress:
 *      type: App.Container
 *      data:
 *        env:
 *          - var: WORDPRESS_DB_HOST
 *            value: ${_resource~mysql~address}
 *        ...
 *    mysql:
 *      type: App.Container
 *      data:
 *          restart_policy: "no"
 *          ...
 * </pre>
 *
 */
public class BindingEvaluator {

    private static ObjectMapper objectMapper;
    private static ObjectWriter objectAsStringWriter;

    static {
        objectMapper = new ObjectMapper(new YAMLFactory());
        FilterProvider filters = new SimpleFilterProvider().addFilter(
                YamlMapper.SERVICE_DOCUMENT_FILTER, SimpleBeanPropertyFilter.serializeAll());
        objectAsStringWriter = objectMapper.writer(filters);
    }

    /**
     * Take a composite description evaluate the bindings and set the results in the Descriptions.
     * Basically go through each binding and try to get the source value and set the target value.
     * If the source value happens to be bound to another value recurse.
     */
    public static void evaluateBindings(CompositeDescriptionExpanded compositeDescription) {

        if (compositeDescription.bindings == null) {
            return;
        }

        Map<String, ComponentDescription> componentNameToDescription = getComponentNameToDescription(
                compositeDescription);

        Map<String, ComponentBinding> bindingByComponentName = getBindingByComponentName(
                compositeDescription.bindings);

        for (Binding.ComponentBinding componentBinding : bindingByComponentName.values()) {
            ComponentDescription description = componentNameToDescription
                    .get(componentBinding.componentName);

            for (Binding binding : componentBinding.bindings) {
                if (binding.isProvisioningTimeBinding()) {
                    continue;
                }

                try {
                    evaluateBinding(binding, description, componentNameToDescription,
                            bindingByComponentName,
                            new HashSet<>());
                } catch (ReflectiveOperationException | IOException e) {
                    throw new RuntimeException(e);
                }
            }

        }
    }

    /**
     * Applies the binding on a Component, after a dependent component is provisioned.
     */
    public static Object evaluateProvisioningTimeBindings(
            Object state,
            List<Binding> bindings,
            Map<String, Object> provisionedResources) {

        Object result = state;
        for (Binding binding : bindings) {
            if (!binding.isProvisioningTimeBinding()) {
                continue;
            }

            try {
                result = evaluateProvisioningTimeBinding(binding, state, provisionedResources);
            } catch (ReflectiveOperationException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private static Object evaluateProvisioningTimeBinding(Binding binding,
            Object state,
            Map<String, Object> provisionedResources)
            throws ReflectiveOperationException, IOException {

        String componentName = BindingUtils
                .extractComponentNameFromBindingExpression(binding.placeholder.bindingExpression);

        Object provisionedResource = provisionedResources.get(componentName);

        if (provisionedResource == null) {
            return provisionedResource;
        }

        Object value = getFieldValueByPath(
                BindingUtils.convertToFieldPath(binding.placeholder.bindingExpression),
                provisionedResource);

        value = BindingUtils.valueForBinding(binding, value);

        Map<String, Object> serializedDescription = serializeToMap(state);
        setValue(serializedDescription, binding.targetFieldPath, value);
        return deserializeFromMap(serializedDescription, state.getClass());
    }

    private static void evaluateBinding(
            Binding binding,
            ComponentDescription targetDescription,
            Map<String, ComponentDescription> componentNameToDescription,
            Map<String, Binding.ComponentBinding> allBindings,
            Set<String> visited) throws ReflectiveOperationException, IOException {

        Object rootSourceValue = resolveValue(binding, targetDescription,
                componentNameToDescription, allBindings, visited);

        if (rootSourceValue != null) {
            Map<String, Object> serializedDescription = serializeToMap(targetDescription.component);
            setValue(serializedDescription, binding.targetFieldPath, rootSourceValue);
            targetDescription.component = (ServiceDocument) deserializeFromMap(
                    serializedDescription, targetDescription.component.getClass());
        }

    }

    private static Object resolveValue(Binding binding, ComponentDescription targetDescription,
            Map<String, ComponentDescription> componentNameToDescription,
            Map<String, Binding.ComponentBinding> allBindings, Set<String> visited)
            throws ReflectiveOperationException {

        // Assume the <<description>>.name is the same as the component name because of
        // CompositeTemplateUtil#sanitizeCompositeTemplate
        String componentName = targetDescription.name;

        if (visited.contains(componentName)) {
            throw new IllegalArgumentException("Cyclic bindings cannot be evaluated");
        }
        visited.add(componentName);

        String bindingExpression = binding.placeholder.bindingExpression;
        List<String> sourceFieldPath = BindingUtils.convertToFieldPath(bindingExpression);
        String sourceComponentName = BindingUtils
                .extractComponentNameFromBindingExpression(bindingExpression);

        ComponentDescription sourceDescription = componentNameToDescription
                .get(sourceComponentName);

        Object rootSourceValue = getFieldValueByPath(sourceFieldPath, sourceDescription.component);

        // if the source value is null it may be bound to something else
        if (rootSourceValue == null) {
            Optional<Binding> isSourceValueABinding = findBinding(sourceFieldPath,
                    sourceComponentName,
                    allBindings);
            if (isSourceValueABinding.isPresent()) {
                Binding nestedBinding = isSourceValueABinding.get();
                rootSourceValue = resolveValue(nestedBinding, sourceDescription,
                        componentNameToDescription,
                        allBindings, visited);
            }
        }

        return BindingUtils.valueForBinding(binding, rootSourceValue);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serializeToMap(Object object) throws IOException {

        String yaml = objectAsStringWriter.writeValueAsString(object);
        Map<String, Object> serializedObject = objectMapper.readValue(yaml, Map.class);
        return serializedObject;
    }

    private static Object deserializeFromMap(Map<String, Object> map, Class<?> type) {
        return objectMapper.convertValue(map, type);
    }

    private static Map<String, Binding.ComponentBinding> getBindingByComponentName(
            List<Binding.ComponentBinding> bindings) {
        return bindings.stream().collect(Collectors.toMap(cb -> cb.componentName, cb -> cb));
    }

    private static Optional<Binding> findBinding(List<String> fieldPath, String componentName,
            Map<String, Binding.ComponentBinding> allBindings) {

        if (!allBindings.containsKey(componentName)) {
            return Optional.empty();
        }

        return allBindings.get(componentName).bindings.stream()
                .filter(b -> b.targetFieldPath.equals(fieldPath)).findFirst();
    }

    @SuppressWarnings("unchecked")
    private static void setValue(Map<String, Object> targetRoot, List<String> targetFieldPath,
            Object value) {
        if (value != null) {
            value = value.toString();
        }
        Object target = targetRoot;
        for (int i = 0; i < targetFieldPath.size(); ++i) {
            String field = targetFieldPath.get(i);
            if (i == targetFieldPath.size() - 1) {
                if (target instanceof Map) {
                    ((Map<String, Object>) target).put(field, value);
                } else if (target instanceof List) {
                    try {
                        int index = Integer.parseInt(field);
                        ((List<Object>) target).add(index, value);
                    } catch (NumberFormatException e) {
                        ((List<Object>) target).add(field);
                        ((List<Object>) target).add(value);
                    }
                }
                return;
            }
            if (target instanceof Map) {
                Object newTarget = ((Map<String, Object>) target).get(field);
                if (newTarget == null) {
                    initializeField((Map<String, Object>) target, field,
                            targetFieldPath.get(i + 1));
                }
                target = ((Map<String, Object>) target).get(field);
            } else if (target instanceof ArrayList) {
                int index = Integer.parseInt(field);
                if (index >= ((List<Object>) target).size()) {
                    initializeField((ArrayList<Object>) target, index, targetFieldPath.get(i + 1));
                }
                target = ((List<Object>) target).get(Integer.parseInt(field));
            }
        }
    }

    private static void initializeField(Map<String, Object> target, String fieldName,
            String nextFieldName) {
        try {
            Integer.parseInt(nextFieldName);
            // this means that we should create a list
            target.put(fieldName, new ArrayList<>());
        } catch (NumberFormatException e) {
            target.put(fieldName, new LinkedHashMap<>());
        }
    }

    private static void initializeField(ArrayList<Object> target, int index,
            String nextFieldName) {
        target.ensureCapacity(index + 1);
        try {
            Integer.parseInt(nextFieldName);
            // this means that we should create a list
            target.add(index, new ArrayList<>());
        } catch (NumberFormatException e) {
            target.add(index, new LinkedHashMap<>());
        }
    }

    private static String convertSnakeCaseToCamelCase(String fieldName) {
        List<String> words = Arrays.asList(fieldName.split("_"));

        Function<String, String> capitalizeString = w -> Character.toUpperCase(w.charAt(0)) + w
                .substring(1);

        String reduce = words.subList(1, words.size()).stream()
                .map(capitalizeString)
                .reduce("", String::concat);

        return words.get(0) + reduce;
    }

    private static String valueFromMapString(String value, String fieldName) {
        String[] split = value.split("=");
        if (split.length == 2) {
            if (split[0].trim().equals(fieldName)) {
                return split[1].trim();
            }
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private static Object getFieldValueByPath(List<String> fieldPath, Object startObject)
            throws ReflectiveOperationException {

        Object value = startObject;
        for (String fieldName : fieldPath) {
            if (value == null) {
                return null;
            }

            // special case for a map
            if (value instanceof Map) {
                value = ((Map) value).get(fieldName);
                continue;
            }

            if (value instanceof List) {
                value = ((List) value).get(Integer.parseInt(fieldName));
                continue;
            }

            if (value.getClass().isArray()) {
                value = ((Object[]) value)[Integer.parseInt(fieldName)];
                continue;
            }

            // if the value is a string, then check it's key=value
            if (value instanceof String) {
                value = valueFromMapString((String) value, fieldName);
                if (value == null) {
                    return null;
                }
                continue;
            }

            Class<?> type = value.getClass();
            Field field = PropertyUtils.findField(type, fieldName);
            if (field == null) {
                String snakeCaseFieldName = convertSnakeCaseToCamelCase(fieldName);
                field = PropertyUtils.findField(type, snakeCaseFieldName);
            }

            if (field != null) {
                value = field.get(value);
            } else {
                // handle special case, as we implicitly put any not know property into
                // customProperties.
                value = tryGetValueFromCustomProperties(type, value, fieldName);
            }
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    private static Object tryGetValueFromCustomProperties(Class<?> type, Object value,
            String fieldName) {
        Object result = null;
        Field field = PropertyUtils.findField(type, ResourceState.FIELD_NAME_CUSTOM_PROPERTIES);
        if (field != null) {
            try {
                Map<String, Object> customProperties = (Map<String, Object>) field.get(value);
                result = customProperties.get(fieldName);
            } catch (ReflectiveOperationException e) {
                // Do nothing here
            }
        }
        return result;
    }

    private static Map<String, ComponentDescription> getComponentNameToDescription(
            CompositeDescriptionExpanded compositeDescription) {
        if (compositeDescription.componentDescriptions == null) {
            return Collections.emptyMap();
        }
        return compositeDescription.componentDescriptions.stream()
                .collect(Collectors.toMap(cd -> cd.name, cd -> cd));
    }
}
