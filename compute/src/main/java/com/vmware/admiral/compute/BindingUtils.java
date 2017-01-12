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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.content.Binding;
import com.vmware.admiral.compute.content.Binding.BindingPlaceholder;
import com.vmware.admiral.compute.content.Binding.ComponentBinding;
import com.vmware.xenon.common.LocalizableValidationException;

public class BindingUtils {
    /** Prefix for placeholders: "${" */
    public static final String PLACEHOLDER_PREFIX = "${";

    /** Suffix for placeholders: "}" */
    public static final char PLACEHOLDER_SUFFIX = '}';

    /** Value separator for placeholders: ":" */
    public static final String VALUE_SEPARATOR = ":";

    // Provisioning time binding token
    public static final String RESOURCE = "_resource";

    // CompositeTemplate field names
    public static final String COMPONENTS = "components";
    public static final String DATA = "data";
    public static final String FIELD_SEPARATOR = "~";

    @SuppressWarnings("unchecked")
    public static List<Binding.ComponentBinding> extractBindings(
            Map<String, Object> initialMap) {
        List<Binding.ComponentBinding> bindingsPerComponent = new LinkedList<>();
        for (Entry<String, Object> e : initialMap.entrySet()) {
            if (e.getKey().equals(COMPONENTS)) {
                Map<String, Object> components = (Map<String, Object>) e.getValue();
                for (Entry<String, Object> ce : components.entrySet()) {
                    List<Binding> bindings = extractBindings(ce.getKey(), ce.getValue());
                    if (bindings != null && !bindings.isEmpty()) {
                        bindingsPerComponent.add(new ComponentBinding(ce.getKey(), bindings));
                    }
                }

            }
        }

        return bindingsPerComponent;
        // return initialMap.entrySet().stream()
        // .filter(e -> e.getKey().equals(COMPONENTS))
        // .flatMap(e -> ((Map<String, Object>) e.getValue()).entrySet().stream())
        // .map(e -> new Binding.ComponentBinding(e.getKey(),
        // extractBindings(e.getKey(), e.getValue())))
        // .filter(b -> (b.bindings != null && !b.bindings.isEmpty()))
        // .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static List<Binding> extractBindings(String currentComponent, Object value) {
        LinkedList<String> targetPath = new LinkedList<>();
        if (value instanceof Map) {
            return extractBindings(targetPath, (Map<String, Object>) value);
        }
        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static List<Binding> extractBindings(LinkedList<String> targetPath,
            Map<String, Object> map) {

        List<Binding> bindings = new LinkedList<>();

        Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            Binding binding = toBinding(targetPath, fieldName, value);

            if (binding != null) {
                iterator.remove();
                bindings.add(binding);
                continue;
            }

            if (value instanceof List) {
                List list = (List) value;
                Map<String, Object> listMap = new LinkedHashMap<>();
                for (int i = 0; i < list.size(); ++i) {
                    listMap.put(String.valueOf(i), list.get(i));
                }
                bindings.addAll(extractBindings(targetPath, fieldName, listMap));
                list = listMap.values().stream().collect(Collectors.toList());
                entry.setValue(list);
            } else if (value instanceof Map) {
                bindings.addAll(
                        extractBindings(targetPath, fieldName, (Map<String, Object>) value));
            }

        }
        return bindings;
    }

    private static List<Binding> extractBindings(LinkedList<String> targetPath,
            String fieldName, Map<String, Object> map) {
        if (!DATA.equals(fieldName)) {
            targetPath.add(fieldName);
        }
        List<Binding> bindings = extractBindings(targetPath, map);
        if (!DATA.equals(fieldName)) {
            targetPath.pollLast();
        }
        return bindings;
    }

    private static Binding toBinding(LinkedList<String> currentPath, String fieldName,
            Object value) {
        if (!(value instanceof String)) {
            return null;
        }

        String strVal = (String) value;

        Binding binding = null;

        StringBuilder result = new StringBuilder(strVal);
        int startIndex = strVal.indexOf(PLACEHOLDER_PREFIX);
        if (startIndex != -1) {
            int endIndex = findPlaceholderEndIndex(result, startIndex);
            if (endIndex != -1) {
                String placeholder = result.substring(startIndex + PLACEHOLDER_PREFIX.length(),
                        endIndex);
                String defaultValue = null;
                int separatorIndex = placeholder.indexOf(VALUE_SEPARATOR);
                if (separatorIndex != -1) {
                    placeholder = placeholder.substring(0, separatorIndex);
                    defaultValue = placeholder.substring(separatorIndex + VALUE_SEPARATOR.length());
                }

                String fieldExpression = result
                        .replace(startIndex + PLACEHOLDER_PREFIX.length(), endIndex, placeholder)
                        .toString();

                LinkedList<String> currentFieldPath = new LinkedList<>(currentPath);
                currentFieldPath.add(fieldName);
                binding = new Binding(currentFieldPath,
                        fieldExpression, new BindingPlaceholder(placeholder, defaultValue));
            } else {
                throw new LocalizableValidationException(
                        "Incomplete binding expression, missing closing bracket: " + strVal, "compute.binding.incomplete", strVal);
            }
        }

        return binding;
    }

    private static int findPlaceholderEndIndex(CharSequence buf, int startIndex) {
        int index = startIndex + PLACEHOLDER_PREFIX.length();
        while (index < buf.length()) {
            if (buf.charAt(index) == PLACEHOLDER_SUFFIX) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public static boolean isProvisioningTimeBinding(String bindingExpression) {
        return bindingExpression.startsWith(RESOURCE);
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
        String[] split = bindingExpression.split(FIELD_SEPARATOR);
        if (isProvisioningTimeBinding(bindingExpression)) {
            return new ArrayList<>(Arrays.asList(Arrays.copyOfRange(split, 2, split.length)));
        } else {
            return new ArrayList<>(Arrays.asList(Arrays.copyOfRange(split, 1, split.length)));
        }
    }

    public static Object valueForBinding(Binding binding, Object value) {
        if (value == null) {
            value = binding.placeholder.defaultValue;
        }

        String placeholderExpr = String.format("${%s}", binding.placeholder.bindingExpression);
        if (binding.originalFieldExpression.equals(placeholderExpr)) {
            return value;
        }

        return binding.originalFieldExpression.replace(placeholderExpr,
                value != null ? value.toString() : "");
    }
}
