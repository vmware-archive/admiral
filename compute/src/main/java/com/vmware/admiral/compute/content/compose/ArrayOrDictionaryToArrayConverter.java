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

package com.vmware.admiral.compute.content.compose;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.util.StdConverter;

/**
 * YAML converter to deserialize Docker compose entities that can be represented indistinctly with
 * a dictionary or an array. e.g.
 * <pre>
 * environment:
 * &nbsp;&nbsp;RACK_ENV: development
 * &nbsp;&nbsp;SHOW: 'true'
 * &nbsp;&nbsp;SESSION_SECRET:
 *
 * environment:
 * - RACK_ENV=development
 * - SHOW=true
 * - SESSION_SECRET
 * </pre>
 * The result is a {@code String[]} representation of the key/value pairs as expected by the
 * ContainerDescription.
 */
public class ArrayOrDictionaryToArrayConverter extends StdConverter<Object, String[]> {

    @SuppressWarnings("unchecked")
    @Override
    public String[] convert(Object entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof List) {
            return ((List<Object>) entity).stream().map(o -> o.toString()).toArray(String[]::new);
        }
        if (entity instanceof Map) {
            List<String> entries = new ArrayList<>();
            for (Entry<String, Object> entry : ((Map<String, Object>) entity).entrySet()) {
                entries.add(entry.getKey() + "=" + entry.getValue().toString());
            }
            return entries.toArray(new String[] {});
        }
        throw new IllegalArgumentException();
    }

}
