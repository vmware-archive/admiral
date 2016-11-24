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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.gson.JsonElement;

import com.vmware.admiral.closures.util.ClosureUtils;

public class ClosureLogConfigDeserializer extends StdDeserializer<JsonElement> {

    public ClosureLogConfigDeserializer() {
        this(null);
    }

    protected ClosureLogConfigDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public JsonElement deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

        JsonNode node = p.getCodec().readTree(p);

        return ClosureUtils.toJsonElement(node);
    }

}
