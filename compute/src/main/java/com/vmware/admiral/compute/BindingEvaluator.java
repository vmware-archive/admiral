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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.content.Binding;

public class BindingEvaluator {

    /**
     * Take a composite description evaluate the bindings and set the results in the *Descriptions.
     * Basically go through each binding and try to get the source value and set the target value.
     * If the source value happens to be bound to another value recurse.
     */
    public static void evaluateBindings(
            CompositeDescriptionExpanded compositeDescription) {

        if (compositeDescription.bindings == null) {
            return;
        }

        Map<String, ComponentDescription> componentNameToDescription = getComponentNameToDescription(
                compositeDescription);


        for (Binding.ComponentBinding componentBinding : compositeDescription.bindings) {
            ComponentDescription description = componentNameToDescription
                    .get(componentBinding.componentName);

            for (Binding binding : componentBinding.bindings) {
                if (binding.isProvisioningTimeBinding) {
                    continue;
                }

                try {
                    evaluateBinding(binding, description, componentNameToDescription,
                            getBindingByComponentName(compositeDescription.bindings),
                            new HashSet<>());
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }

        }
    }

    private static void evaluateBinding(
            Binding binding,
            ComponentDescription targetDescription,
            Map<String, ComponentDescription> componentNameToDescription,
            Map<String, Binding.ComponentBinding> allBindings,
            Set<String> visited) throws ReflectiveOperationException {

        //Assume the <<description>>.name is the same as the component name because of CompositeTemplateUtil#sanitizeCompositeTemplate
        String componentName = targetDescription.name;

        if (visited.contains(componentName)) {
            throw new IllegalArgumentException("Cyclic bindings cannot be evaluated");
        }
        visited.add(componentName);

        List<String> targetFieldPath = binding.targetFieldPath;

        if (getFieldValueByPath(targetFieldPath, targetDescription.component) != null) {
            //already evaluated
            return;
        }

        String bindingExpression = binding.bindingExpression;
        List<String> sourceFieldPath = BindingUtils.convertToFieldPath(bindingExpression);
        String sourceComponentName = BindingUtils
                .extractComponentNameFromBindingExpression(bindingExpression);

        ComponentDescription sourceDescription = componentNameToDescription
                .get(sourceComponentName);

        Object rootSourceValue = getFieldValueByPath(sourceFieldPath, sourceDescription.component);
        Optional<Binding> isSourceValueABinding = findBinding(sourceFieldPath, sourceComponentName,
                allBindings);

        //if the source value is null it may be bound to something else
        if (rootSourceValue == null && isSourceValueABinding.isPresent()) {

            Binding nestedBinding = isSourceValueABinding.get();
            evaluateBinding(nestedBinding, targetDescription, componentNameToDescription,
                    allBindings, visited);
        } else {
            setValue(targetDescription.component, targetFieldPath, rootSourceValue);
        }

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

    private static void setValue(Object rootTargetObject,
            List<String> targetFieldPath, Object value)
            throws ReflectiveOperationException {

        Field targetField = getFieldByPath(rootTargetObject.getClass(), targetFieldPath);
        Object targetObject = getFieldValueByPath(
                targetFieldPath.subList(0, targetFieldPath.size() - 1), rootTargetObject);

        if (value == null || targetField.getType().isAssignableFrom(value.getClass())) {
            targetField.set(targetObject, value);
        } else {
            if (targetField.getType().equals(String.class)) {
                targetField.set(targetObject, value.toString());
            } else if (value.getClass().equals(String.class)) {
                //parse primitive from String
                if (targetField.getType().equals(Boolean.class)) {
                    targetField.set(targetObject, Boolean.parseBoolean(value.toString()));
                } else if (targetField.getType().equals(Double.class)) {
                    targetField.set(targetObject, Double.parseDouble(value.toString()));
                } else if (targetField.getType().equals(Integer.class)) {
                    targetField.set(targetObject, Integer.parseInt(value.toString()));
                } else if (targetField.getType().equals(Long.class)) {
                    targetField.set(targetObject, Long.parseLong(value.toString()));
                }
            } else if (Number.class.isAssignableFrom(targetField.getType()) &&
                    Number.class.isAssignableFrom(value.getClass())) {
                //both are numbers - need to convert
                if (targetField.getType().equals(Integer.class)) {
                    targetField.set(targetObject, ((Number) value).intValue());
                } else if (targetField.getType().equals(Double.class)) {
                    targetField.set(targetObject, ((Number) value).doubleValue());
                } else if (targetField.getType().equals(Long.class)) {
                    targetField.set(targetObject, ((Number) value).longValue());
                }
            }
        }
    }

    private static Field getFieldByPath(Class<?> clazz, List<String> fieldPath)
            throws ReflectiveOperationException {

        /**
         * TODO because of @JsonProperty the field names in the fieldPath may not be the same as the
         * field names in our Container/ComputeDescription. Also some fields have custom
         * serializers/deserializers attached, so the structure may be different, have to handle
         * these cases too
         */

        Class<?> type = clazz;

        Field field = null;
        for (String fieldName : fieldPath) {
            field = type.getField(fieldName);
            type = field.getType();
        }

        return field;
    }

    private static Object getFieldValueByPath(List<String> fieldPath, Object startObject)
            throws ReflectiveOperationException {

        Class<?> type = startObject.getClass();

        Field field = null;
        Object value = startObject;
        for (String fieldName : fieldPath) {
            field = type.getField(fieldName);
            type = field.getType();
            value = field.get(value);
        }

        return value;
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
