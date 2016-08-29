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

package com.vmware.admiral.compute.content;

import java.util.Arrays;
import java.util.Map;

/**
 * Format a map into a string using String.format()
 *
 * Assume that the format contains one single separator char (e.g. a=b or a:b).
 */
public class MapToFormattedStringDeserializer extends AbstractMapDeserializer<String> {
    private static final long serialVersionUID = 1L;
    private final String format;
    private final String[] argNames;

    public MapToFormattedStringDeserializer(String format, String... argNames) {
        super(String.class);
        this.format = format;
        this.argNames = argNames;
    }

    @Override
    protected String deserializeMap(Map<String, String> map) {

        if (argNames.length == 2) {
            String v0 = map.get(argNames[0]);
            String v1 = map.get(argNames[1]);
            if ((v1 != null) && (!v1.trim().isEmpty())) {
                return String.format(format, new Object[] { v0, v1 });
            } else {
                return v0;
            }
        }

        return String.format(format, Arrays.stream(argNames)
                .map((name) -> map.get(name))
                .toArray());
    }

}
