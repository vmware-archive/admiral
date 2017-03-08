/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration.client.closures;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class ClosureInputsDeserializer extends StdDeserializer<Map<String, String>> {

    private static final long serialVersionUID = 1L;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public ClosureInputsDeserializer() {
        this(null);
    }

    protected ClosureInputsDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Map<String, String> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

        Map<String, String> result = new HashMap<>();
        JsonNode node = p.getCodec().readTree(p);

        Iterator<String> fieldsIterator = node.fieldNames();
        while (fieldsIterator.hasNext()) {
            String field = fieldsIterator.next();
            JsonNode childNode = node.get(field);
            String json = objectMapper.writeValueAsString(childNode);
            result.put(field, json);
        }

        return result;
    }

}
