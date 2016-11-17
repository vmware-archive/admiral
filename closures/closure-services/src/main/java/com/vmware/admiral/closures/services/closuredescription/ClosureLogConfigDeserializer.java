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

package com.vmware.admiral.closures.services.closuredescription;

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class ClosureLogConfigDeserializer extends StdDeserializer<JsonElement> {

    public ClosureLogConfigDeserializer() {
        this(null);
    }

    protected ClosureLogConfigDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public JsonElement deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {

        JsonNode node = p.getCodec().readTree(p);

        return toJsonElement(node);
    }

    private JsonElement toJsonElement(JsonNode node) {
        JsonObject jsObject = new JsonObject();

        Iterator<String> fieldsIterator = node.fieldNames();
        while (fieldsIterator.hasNext()) {
            String field = fieldsIterator.next();
            JsonNode childNode = node.get(field);

            JsonElement convertedValue = null;
            if (childNode.isObject()) {
                convertedValue = toJsonElement(childNode);
                jsObject.add(field, convertedValue);
            } else if (childNode.isArray()) {
                convertedValue = toJsonElementArray(childNode);
                jsObject.add(field, convertedValue);
            } else {
                String val = childNode.textValue();
                jsObject.add(field, new JsonPrimitive(val));
            }
        }

        return jsObject;
    }

    private JsonElement toJsonElementArray(JsonNode childNode) {
        JsonArray jsObjArray = new JsonArray();
        Iterator<JsonNode> iterator = childNode.iterator();
        while (iterator.hasNext()) {
            JsonElement convertedValue = null;
            JsonNode node = iterator.next();
            if (node.isObject()) {
                convertedValue = toJsonElement(node);
                jsObjArray.add(convertedValue);
            } else if (node.isArray()) {
                convertedValue = toJsonElementArray(node);
                jsObjArray.add(convertedValue);
            } else {
                String val = node.textValue();
                jsObjArray.add(new JsonPrimitive(val));
            }
        }

        return jsObjArray;
    }

}
