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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.content.Binding;

public class BindingUtils {

    //Provisioning time binding token
    public static final String RESOURCE = "_resource";

    //CompositeTemplate field names
    public static final String COMPONENTS = "components";
    public static final String DATA = "data";
    public static final String FIELD_SEPARATOR = "~";

    public static List<Binding.ComponentBinding> extractBindings(
            Map<String, Object> initialMap) {

        Map<String, Set<Binding>> bindingsMap = new HashMap<>();
        extractBindings("", initialMap, bindingsMap);

        List<Binding.ComponentBinding> componentBindings = bindingsMap.entrySet().stream()
                .map(e -> new Binding.ComponentBinding(e.getKey(), new ArrayList<>(e.getValue())))
                .collect(Collectors.toList());
        return componentBindings;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void extractBindings(String initialFieldPath, Map<String, Object> map,
            Map<String, Set<Binding>> bindingsPerComponent) {
        Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            String currentFieldPath = appendToFieldPath(initialFieldPath, fieldName);

            if (value instanceof String) {

                if (!isBinding((String) value)) {
                    continue;
                }

                //Remove the entry from the map as we may not be able to map this to the original class
                iterator.remove();

                //remove the ${ and }
                String bindingExpression = ((String) value)
                        .substring(2, ((String) value).length() - 1);
                addBinding(bindingsPerComponent, currentFieldPath, bindingExpression);

            } else if (value instanceof List) {
                List list = (List) value;
                Map<String, Object> listMap = new LinkedHashMap<>();
                for (int i = 0; i < list.size(); ++i) {
                    listMap.put(String.valueOf(i), list.get(i));
                }
                extractBindings(currentFieldPath, listMap, bindingsPerComponent);
                list = listMap.values().stream().collect(Collectors.toList());
                entry.setValue(list);
            } else if (value instanceof Map) {
                extractBindings(currentFieldPath, (Map) value, bindingsPerComponent);
            }
        }
    }

    private static void addBinding(Map<String, Set<Binding>> bindingsPerComponent,
            String currentFieldPath, String bindingExpression) {
        String targetComponentName = extractTargetComponentName(currentFieldPath);
        Set<Binding> bindings = bindingsPerComponent.get(targetComponentName);

        if (bindings == null) {
            bindings = new HashSet<>();
            bindingsPerComponent.put(targetComponentName, bindings);
        }

        List<String> fullFieldPath = Arrays.asList(currentFieldPath.split(FIELD_SEPARATOR));
        Binding binding = new Binding(
                new ArrayList<>(fullFieldPath.subList(1, fullFieldPath.size())),
                bindingExpression,
                isProvisioningTimeBinding(bindingExpression));
        bindings.add(binding);
    }

    private static String appendToFieldPath(String fieldPath, String fieldName) {
        if (COMPONENTS.equals(fieldName) || DATA.equals(fieldName)) {
            return fieldPath;
        }
        return "".equals(fieldPath) ? fieldName : fieldPath + FIELD_SEPARATOR + fieldName;
    }

    private static boolean isProvisioningTimeBinding(String bindingExpression) {
        return bindingExpression.startsWith(RESOURCE);
    }

    private static boolean isBinding(String value) {
        return value.startsWith("${") && value.endsWith("}");
    }

    private static String extractTargetComponentName(String fieldPath) {
        String[] split = fieldPath.split(FIELD_SEPARATOR);
        return split[0];
    }

    public static String extractComponentNameFromBindingExpression(String bindingExpression) {
        String[] split = bindingExpression.split(FIELD_SEPARATOR);
        if (isProvisioningTimeBinding(bindingExpression)) {
            return split[1];
        } else {
            return split[0];
        }
    }

    public static List<String> convertToFieldPath(String bindingExpression) {
        String[] split = bindingExpression.split("~");
        if (isProvisioningTimeBinding(bindingExpression)) {
            return new ArrayList<>(Arrays.asList(Arrays.copyOfRange(split, 2, split.length)));
        } else {
            return new ArrayList<>(Arrays.asList(Arrays.copyOfRange(split, 1, split.length)));
        }
    }
}
