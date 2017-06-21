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

package com.vmware.admiral.common.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.logging.Logger;

import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.ServiceDocument;

public class PropertyUtils {
    private static final Logger LOGGER = Logger.getLogger(PropertyUtils.class.getName());

    public static <T> T mergeProperty(T copyTo, T copyFrom) {
        if (copyFrom != null) {
            return copyFrom;
        }
        return copyTo;
    }

    public static long mergeLongProperty(long copyTo, long copyFrom) {
        if (copyFrom != 0) {
            return copyFrom;
        }
        return copyTo;
    }

    public static Map<String, String> mergeCustomProperties(Map<String, String> copyTo,
            Map<String, String> copyFrom) {
        if (copyTo == null) {
            return copyFrom;
        } else if (copyFrom != null && !copyFrom.isEmpty()) {
            copyTo.putAll(copyFrom);
        }

        return copyTo;
    }

    public static <T> List<T> mergeLists(List<T> copyTo,
            List<T> copyFrom) {
        if (copyTo == null) {
            return copyFrom;
        } else if (copyFrom != null && !copyFrom.isEmpty()) {
            for (T t : copyTo) {
                if (!copyFrom.contains(t)) {
                    copyFrom.add(t);
                }
            }

            return copyFrom;
        }

        return copyTo;
    }

    /**
     * Perform shallow merge of ServiceDocuments using reflection
     *
     * @param copyTo
     * @param copyFrom
     */
    public static void mergeServiceDocuments(ServiceDocument copyTo, ServiceDocument copyFrom) {
        mergeServiceDocuments(copyTo, copyFrom, SHALLOW_MERGE_STRATEGY);
    }

    /**
     * Perform merge of ServiceDocuments using the given strategy
     *
     * @param copyTo
     * @param copyFrom
     * @param fieldMergeStrategy
     */
    public static void mergeServiceDocuments(ServiceDocument copyTo, ServiceDocument copyFrom,
            BinaryOperator<Object> fieldMergeStrategy) {
        mergeObjects(copyTo, copyFrom, fieldMergeStrategy);
        // the expiration time could be updated
        long exp = copyFrom.documentExpirationTimeMicros;
        if (exp != 0) {
            copyTo.documentExpirationTimeMicros = exp < 0 ? 0 : exp;
        }
    }

    /**
     * Perform merge of Objects using the given strategy
     *
     * @param copyTo
     * @param copyFrom
     * @param fieldMergeStrategy
     */
    public static void mergeObjects(Object copyTo, Object copyFrom,
            BinaryOperator<Object> fieldMergeStrategy) {

        for (Field field : copyFrom.getClass().getFields()) {
            // skip framework and static fields
            if (ServiceDocument.isBuiltInDocumentField(field.getName())
                    || Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                Object value = field.get(copyFrom);
                Object oldValue = field.get(copyTo);
                field.set(copyTo, fieldMergeStrategy.apply(oldValue, value));

            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Merge strategy that will overwrite the target if the source is non null.
     *
     * It will not recurse into merging individual fields of complex objects
     */
    public static final BinaryOperator<Object> SHALLOW_MERGE_STRATEGY = (copyTo, copyFrom) -> {
        return mergeProperty(copyTo, copyFrom);
    };

    public static Optional<Long> getPropertyLong(Map<String, String> properties, String key) {
        if (properties == null) {
            return Optional.empty();
        }
        if (properties.containsKey(key)) {
            try {
                Double value = Double.valueOf(properties.get(key));
                // Some values are written in scientific notation, so we parse them with Double
                return Optional.of(value.longValue());
            } catch (Exception ex) {
                LOGGER.warning(
                        String.format("Could not parse property %s as double: %s", key,
                                properties.get(key)));
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    public static Optional<Double> getPropertyDouble(Map<String, String> properties, String key) {
        if (properties == null) {
            return Optional.empty();
        }
        if (properties.containsKey(key)) {
            try {
                return Optional.of(Double.valueOf(properties.get(key)));
            } catch (Exception ex) {
                LOGGER.warning(
                        String.format("Could not parse property %s as double: %s", key,
                                properties.get(key)));
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    public static Map<String, Object> setPropertyDouble(Map<String, Object> properties, String key,
            String value) {
        if (key == null) {
            return properties;
        }

        try {
            properties.put(key, Double.valueOf(value));
        } catch (Exception e) {
            LOGGER.warning(
                    String.format("Cannot set property %s value %s as double", key, value));
        }

        return properties;
    }

    public static Optional<String> getPropertyString(Map<String, String> properties, String key) {
        if (properties == null) {
            return Optional.empty();
        }
        if (properties.containsKey(key)) {
            return Optional.of(properties.get(key));

        } else {
            return Optional.empty();
        }
    }

    public static Optional<Integer> getPropertyInteger(Map<String, String> properties, String key) {
        if (properties == null) {
            return Optional.empty();
        }
        if (properties.containsKey(key)) {
            try {
                Double value = Double.valueOf(properties.get(key));
                // Some values are written in scientific notation, so we parse them with Double
                return Optional.of(value.intValue());
            } catch (Exception ex) {
                LOGGER.warning(
                        String.format("Could not parse property %s as integer: %s", key,
                                properties.get(key)));
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    public static Map<String, Object> setPropertyInteger(Map<String, Object> properties, String key,
            String value) {
        if (key == null) {
            return properties;
        }

        try {
            properties.put(key, Integer.valueOf(value));
        } catch (Exception e) {
            LOGGER.warning(String.format("Cannot set property %s value %s as integer", key,
                    value));
        }

        return properties;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getValue(Object instance, String fieldName) {
        try {
            Field field = findField(instance.getClass(), fieldName);
            return (T) field.get(instance);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attempt to find a {@link Field field} on the supplied {@link Class} with the supplied
     * {@code name}. Searches all superclasses up to {@link Object}.
     *
     * @param clazz
     *            the class to introspect
     * @param name
     *            the name of the field
     * @return the corresponding Field object, or {@code null} if not found
     */
    public static Field findField(Class<?> clazz, String name) {
        Class<?> searchType = clazz;
        while (Object.class != searchType && searchType != null) {
            Field field = ReflectionUtils.getField(searchType, name);
            if (field != null) {
                return field;
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }

    public static <T> T getPropertyCaseInsensitive(Map<String, T> map, String key) {
        if (key == null) {
            return map.get(null);
        }
        TreeMap<String, T> caseInsensitiveMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitiveMap.putAll(map);
        return caseInsensitiveMap.get(key);
    }
}
