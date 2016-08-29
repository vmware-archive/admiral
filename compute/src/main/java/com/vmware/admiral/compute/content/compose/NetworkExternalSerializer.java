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
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * See {@link NetworkExternal} serialization/deserialization particularities.
 */
public class NetworkExternalSerializer extends StdSerializer<NetworkExternal> {

    private static final long serialVersionUID = 1L;

    public NetworkExternalSerializer() {
        this(NetworkExternal.class);
    }

    protected NetworkExternalSerializer(Class<NetworkExternal> t) {
        super(t);
    }

    @Override
    public void serialize(NetworkExternal external, JsonGenerator gen, SerializerProvider provider)
            throws IOException {

        if (external.value != null) {
            gen.writeObject(external.value);
            return;
        }

        Map<String, Object> map = new HashMap<>();
        if (external.name != null) {
            map.put("name", external.name);
        }
        gen.writeObject(map);
    }

}
