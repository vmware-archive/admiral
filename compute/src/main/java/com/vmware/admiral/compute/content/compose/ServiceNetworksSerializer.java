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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * See {@link ServiceNetworks} serialization/deserialization particularities.
 */
public class ServiceNetworksSerializer extends StdSerializer<ServiceNetworks> {

    private static final long serialVersionUID = 1L;

    public ServiceNetworksSerializer() {
        this(ServiceNetworks.class);
    }

    protected ServiceNetworksSerializer(Class<ServiceNetworks> t) {
        super(t);
    }

    @Override
    public void serialize(ServiceNetworks networks, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        if (networks.values != null) {
            gen.writeObject(networks.values);
        } else {
            gen.writeObject(networks.valuesMap);
        }
    }

}
