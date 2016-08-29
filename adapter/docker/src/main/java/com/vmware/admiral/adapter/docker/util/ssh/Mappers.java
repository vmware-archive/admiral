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

package com.vmware.admiral.adapter.docker.util.ssh;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.vmware.xenon.common.Utils;

/**
 * Utilities for transforming the output of a command (a String) to some other useful object
 */
public interface Mappers {
    /**
     * create a mapper for a JSON representation of the given class
     */
    public static <T> Function<String, T> jsonMapper(Class<T> clazz) {
        return (s) -> {
            return Utils.fromJson(s, clazz);
        };
    }

    /**
     * lambda for extracting the first element of a collection
     */
    @SuppressWarnings("rawtypes")
    public static final Function<Collection, Object> EXTRACT_FIRST_COLLECTION_ELEMENT = (
            c) -> c.iterator().next();

    /**
     * lambda for extracting the first element of a JSON array
     */
    public static final Function<String, Object> EXTRACT_FIRST_JSON_ELEMENT = jsonMapper(
            Collection.class).andThen(EXTRACT_FIRST_COLLECTION_ELEMENT);

    /**
     * map a multi-line YAML-ish string (format "key: value")
     *
     * this is a simple implementation that ignores nested (indented keys)
     */
    public static Function<String, Object> SIMPLE_YAML_MAPPER = (s) -> {
        Map<String, Object> map = new HashMap<>();
        for (String line : s.split("[\r\n]+")) {
            // ignore indented lines since these are actually subelements of the previous line
            if (line.startsWith(" ") || line.startsWith("-")) {
                continue;
            }
            String[] tokens = line.split(":", 2);
            map.put(tokens[0], tokens[1]);
        }
        return map;
    };

    /**
     * Map a multiline text to a collection of the lines
     */
    public static Function<String, Collection<String>> NEWLINE_DELIMITED_MAPPER = (s) -> {
        return Arrays.asList(s.split("[\r\n]+"));
    };

}
