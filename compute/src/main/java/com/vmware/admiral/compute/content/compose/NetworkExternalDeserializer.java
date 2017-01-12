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

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.util.ClassUtil;

import com.vmware.xenon.common.LocalizableValidationException;

/**
 * See {@link NetworkExternal} serialization/deserialization particularities.
 */
public class NetworkExternalDeserializer extends StdDeserializer<NetworkExternal> {

    private static final long serialVersionUID = 1L;

    public NetworkExternalDeserializer() {
        this(NetworkExternal.class);
    }

    protected NetworkExternalDeserializer(Class<?> vc) {
        super(vc);
    }

    @SuppressWarnings("unchecked")
    @Override
    public NetworkExternal deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {

        Object object = p.readValueAs(Object.class);

        NetworkExternal external = new NetworkExternal();

        if (object instanceof Boolean) {
            external.value = (Boolean) object;
        } else if (object instanceof Map) {
            Map<String, String> map = (Map<String, String>) object;
            external.name = map.get("name");
        } else {
            throw new LocalizableValidationException("Invalid external object class '"
                    + ClassUtil.getClassDescription(object) + "'!",
                    "compute.external.deserialization.error", ClassUtil.getClassDescription(object));
        }

        return external;
    }

}
