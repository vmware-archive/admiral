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
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class ClosureInputsSerializer extends StdSerializer<Map> {

    private static final long serialVersionUID = 1L;

    public ClosureInputsSerializer() {
        this(Map.class);
    }

    protected ClosureInputsSerializer(Class<Map> t) {
        super(t);
    }

    @Override
    public void serialize(Map inputs, JsonGenerator gen, SerializerProvider provider)
            throws IOException {

        gen.writeStartObject();
        if (inputs != null) {
            for (Map.Entry<String, String> entry : ((Map<String, String>) inputs).entrySet()) {
                String json = entry.getValue();
                gen.writeFieldName(entry.getKey());
                gen.writeRawValue(json != null ? json : "null");
            }
        }
        gen.writeEndObject();
    }

}
