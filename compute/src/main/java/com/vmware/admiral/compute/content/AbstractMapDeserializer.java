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
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * abstract base class for map deserializers
 */
public abstract class AbstractMapDeserializer<T> extends StdDeserializer<T> {
    private static final long serialVersionUID = 1L;

    protected AbstractMapDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public T deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException,
            JsonProcessingException {

        @SuppressWarnings("unchecked")
        Map<String, String> map = jp.readValueAs(Map.class);

        return deserializeMap(map);
    }

    protected abstract T deserializeMap(Map<String, String> map);

}
