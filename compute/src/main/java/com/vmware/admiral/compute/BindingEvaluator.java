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
import java.util.HashMap;
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
import com.google.gson.JsonPrimitive;

import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.content.Binding;
import com.vmware.admiral.compute.content.Binding.ComponentBinding;
import com.vmware.admiral.compute.content.ComponentTemplate;
import com.vmware.admiral.compute.content.CompositeTemplate;
import com.vmware.admiral.compute.content.NestedState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;

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
    public static void evaluateBindings(CompositeTemplate compositeTemplate) {

        if (compositeTemplate.bindings == null || compositeTemplate.bindings.isEmpty()) {
            return;
        }

        Map<String, ComponentTemplate<?>> componentNameToTemplate = getComponentNameToDescription(
                compositeTemplate);

        Map<String, ComponentBinding> bindingByComponentName = getBindingByComponentName(
                compositeTemplate.bindings);

        for (Binding.ComponentBinding componentBinding : bindingByComponentName.values()) {
            ComponentTemplate componentTemplate = componentNameToTemplate
                    .get(componentBinding.componentName);

            for (Binding binding : componentBinding.bindings) {
                if (binding.isProvisioningTimeBinding()) {
                    continue;
                }

                try {
                    evaluateBinding(binding, componentBinding.componentName, componentTemplate,
                            componentNameToTemplate,
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

    public static NestedState evaluateProvisioningTimeBindings(
            NestedState state,
            List<Binding> bindings,
            Map<String, NestedState> provisionedResources) {
        NestedState result = state;
        Map<String, Object> evaluatedBindingMap = new HashMap<>();
        for (Binding binding : bindings) {
            if (!binding.isProvisioningTimeBinding()) {
                continue;
            }
            try {
                evaluateProvisioningTimeBinding(binding, provisionedResources, evaluatedBindingMap);
            } catch (ReflectiveOperationException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            Map<String, Object> resultBindingMap = TemplateSerializationUtils
                    .serializeNestedState(state, objectMapper, objectAsStringWriter);
            applyEvaluatedState(resultBindingMap, evaluatedBindingMap, bindings);
            if (!evaluatedBindingMap.isEmpty()) {
                result = TemplateSerializationUtils.deserializeServiceDocument(resultBindingMap,
                        state.object.getClass());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private static void applyEvaluatedState(Map<String, Object> resultBindingMap,
            Map<String, Object> evaluatedBindingMap, List<Binding> bindings) {
        evaluatedBindingMap.forEach((k, v) -> {
            Binding targetBinding = findBindingByExpression(k, bindings);
            if (targetBinding != null) {
                setValue(resultBindingMap, targetBinding.targetFieldPath, v);
            }
        });

    }

    private static Binding findBindingByExpression(String k, List<Binding> bindings) {
        for (Binding b : bindings) {
            if (k.equalsIgnoreCase(b.placeholder.bindingExpression)) {
                return b;
            }
        }

        return null;
    }

    private static void evaluateProvisioningTimeBinding(Binding binding,
            Map<String, NestedState> provisionedResources, Map<String, Object> evaluatedBindings)
            throws ReflectiveOperationException, IOException {

        String componentName = BindingUtils
                .extractComponentNameFromBindingExpression(binding.placeholder.bindingExpression);

        NestedState provisionedResource = provisionedResources.get(componentName);
        if (provisionedResource == null) {
            return;
        }

        Object value = getFieldValueByPath(
                BindingUtils.convertToFieldPath(binding.placeholder.bindingExpression),
                provisionedResource);

        value = BindingUtils.valueForBinding(binding, value);
        evaluatedBindings.put(binding.placeholder.bindingExpression, value);
    }

    private static void evaluateBinding(
            Binding binding,
            String componentName,
            ComponentTemplate componentTemplate,
            Map<String, ComponentTemplate<?>> componentNameToTemplate,
            Map<String, Binding.ComponentBinding> allBindings,
            Set<String> visited) throws ReflectiveOperationException, IOException {

        Object rootSourceValue = resolveValue(binding, componentName, componentTemplate,
                componentNameToTemplate, allBindings, visited);

        if (rootSourceValue != null) {
            Map<String, Object> serializedComponentTemplate = TemplateSerializationUtils
                    .serializeComponentTemplate(componentTemplate, objectMapper,
                            objectAsStringWriter);
            setValue((Map<String, Object>) serializedComponentTemplate.get("data"),
                    binding.targetFieldPath, rootSourceValue);
            ComponentTemplate<?> updatedComponentTemplate = TemplateSerializationUtils
                    .deserializeComponent(serializedComponentTemplate, objectMapper);
            componentTemplate.data = updatedComponentTemplate.data;
            componentTemplate.children = updatedComponentTemplate.children;
            componentTemplate.type = updatedComponentTemplate.type;
            componentTemplate.dependsOn = updatedComponentTemplate.dependsOn;
        }

    }

    private static Object resolveValue(Binding binding, String templateName,
            ComponentTemplate targetTemplate,
            Map<String, ComponentTemplate<?>> componentNameToDescription,
            Map<String, Binding.ComponentBinding> allBindings, Set<String> visited)
            throws ReflectiveOperationException {

        // Assume the <<description>>.name is the same as the component name because of
        // CompositeTemplateUtil#sanitizeCompositeTemplate
        String componentName = templateName;

        if (visited.contains(componentName)) {
            throw new LocalizableValidationException("Cyclic bindings cannot be evaluated", "compute.cyclic.bindings");
        }
        visited.add(componentName);

        String bindingExpression = binding.placeholder.bindingExpression;
        List<String> sourceFieldPath = BindingUtils.convertToFieldPath(bindingExpression);
        String sourceComponentName = BindingUtils
                .extractComponentNameFromBindingExpression(bindingExpression);

        ComponentTemplate sourceTemplate = componentNameToDescription.get(sourceComponentName);

        Object rootSourceValue = getFieldValueByPath(sourceFieldPath, sourceTemplate.data);

        // if the source value is null it may be bound to something else
        if (rootSourceValue == null) {
            Optional<Binding> isSourceValueABinding = findBinding(sourceFieldPath,
                    sourceComponentName,
                    allBindings);
            if (isSourceValueABinding.isPresent()) {
                Binding nestedBinding = isSourceValueABinding.get();
                rootSourceValue = resolveValue(nestedBinding, sourceComponentName, sourceTemplate,
                        componentNameToDescription,
                        allBindings, visited);
            }
        }

        return BindingUtils.valueForBinding(binding, rootSourceValue);
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

        boolean isCurrentFieldLink = false;
        boolean isParentFieldLink = false;

        NestedState currentNestedState = null;
        Object value = null;

        if (startObject instanceof NestedState) {
            value = ((NestedState) startObject).object;
            currentNestedState = (NestedState) startObject;
        } else {
            value = startObject;
        }

        for (int i = 0; i < fieldPath.size(); ++i) {
            String fieldName = fieldPath.get(i);
            if (value == null) {
                return null;
            }

            // this field contains links
            isCurrentFieldLink =
                    NestedState.getNestedObjectType(value.getClass(), fieldName) != null;

            // special case for a map
            if (value instanceof Map) {
                value = ((Map) value).get(fieldName);

                if (isCurrentFieldLink) {
                    String link = (String) value;
                    value = currentNestedState.children.get(link).object;
                    currentNestedState = currentNestedState.children.get(link);
                }

                continue;
            }

            if (value instanceof List) {
                value = ((List) value).get(Integer.parseInt(fieldName));

                /**
                 * Here we have an index e.g. "0". We have to know if the List is a list of links
                 * in order to take the corresponding child of the NestedState if needed. So we keep
                 * a flag if the "parent" field is a link field
                 */
                if (isParentFieldLink) {
                    String link = (String) value;
                    value = currentNestedState.children.get(link).object;
                    currentNestedState = currentNestedState.children.get(link);
                }

                continue;
            }

            if (value.getClass().isArray()) {
                value = ((Object[]) value)[Integer.parseInt(fieldName)];

                if (isParentFieldLink) {
                    String link = (String) value;
                    value = currentNestedState.children.get(link).object;
                    currentNestedState = currentNestedState.children.get(link);
                }

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
                if (value instanceof Closure) {
                    value = fromClosureMap(value, field);
                } else {
                    value = field.get(value);

                    if (value instanceof String && isCurrentFieldLink) {
                        String link = (String) value;
                        value = currentNestedState.children.get(link).object;
                        currentNestedState = currentNestedState.children.get(link);
                    }
                }
            } else {
                // handle special case, as we implicitly put any not know property into
                // customProperties.
                value = tryGetValueFromCustomProperties(type, value, fieldName);
            }
            isParentFieldLink = isCurrentFieldLink;
        }
        return value;
    }

    private static Object fromClosureMap(Object value, Field field) throws IllegalAccessException {
        Map values = (Map) field.get(value);
        Map convertedMap = new HashMap(values.size());

        values.forEach((k, v) -> {
            Object objVal;
            if (v instanceof JsonPrimitive) {
                objVal = ((JsonPrimitive) v).getAsString();
            } else {
                objVal = v.toString();
            }
            convertedMap.put(k, objVal);
        });

        return convertedMap;
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

    private static Map<String, ComponentTemplate<?>> getComponentNameToDescription(
            CompositeTemplate compositeTemplate) {
        if (compositeTemplate.components == null) {
            return Collections.emptyMap();
        }
        return compositeTemplate.components;
    }
}
