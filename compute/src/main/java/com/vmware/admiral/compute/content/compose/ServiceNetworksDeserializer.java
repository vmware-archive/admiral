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
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.util.ClassUtil;

import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Utils;

public class ServiceNetworksDeserializer extends StdDeserializer<ServiceNetworks> {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger
            .getLogger(ServiceNetworksDeserializer.class.getName());

    public ServiceNetworksDeserializer() {
        this(ServiceNetworks.class);
    }

    protected ServiceNetworksDeserializer(Class<?> vc) {
        super(vc);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ServiceNetworks deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {

        Object object = p.readValueAs(Object.class);

        ServiceNetworks networks = new ServiceNetworks();

        if (object instanceof List) {
            networks.values = ((List<String>) object).toArray(new String[] {});
        } else if (object instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) object;
            networks.valuesMap = new HashMap<String, ServiceNetwork>();

            try {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String network = (String) entry.getKey();
                    Map<String, Object> m = (Map<String, Object>) entry.getValue();
                    ServiceNetwork sn = ServiceNetwork.fromMap(m);
                    networks.valuesMap.put(network, sn);
                }
            } catch (Exception e) {
                logger.severe("Invalid yaml input for networks: " + Utils.toString(e));
                throw new LocalizableValidationException("Invalid networks object class '"
                        + ClassUtil.getClassDescription(object) + "'!",
                        "compute.service.network.deserialization.error",
                        ClassUtil.getClassDescription(object));
            }
        } else {
            throw new LocalizableValidationException("Invalid networks object class '"
                    + ClassUtil.getClassDescription(object) + "'!",
                    "compute.service.network.deserialization.error", ClassUtil.getClassDescription(object));
        }

        return networks;
    }

}
