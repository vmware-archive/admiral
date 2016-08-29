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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Split a formatted string and put the elements in the map using the given argNames
 */
public class SplitStringToMapSerializer extends StdSerializer<String> {
    private static final long serialVersionUID = 1L;
    private final String splitRegex;
    private final String[] argNames;

    protected SplitStringToMapSerializer(String splitRegex, String... argNames) {
        super(String.class);

        this.splitRegex = splitRegex;
        this.argNames = argNames;
    }

    @Override
    public void serialize(String value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException {

        Map<String, String> map = new HashMap<>();

        String[] split = value.split(splitRegex, argNames.length);
        for (int i = 0; i < split.length; ++i) {
            if ((split[i] != null) && (!split[i].trim().isEmpty())) {
                map.put(argNames[i], split[i]);
            }
        }

        jgen.writeObject(map);
    }

}
