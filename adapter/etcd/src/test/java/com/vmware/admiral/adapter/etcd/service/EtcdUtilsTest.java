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

package com.vmware.admiral.adapter.etcd.service;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.junit.Test;

import com.vmware.xenon.common.Operation;

public class EtcdUtilsTest {

    @Test
    public void testGetBodyParameters() throws UnsupportedEncodingException {
        String body = "value={\"id\":\"GlobalDefault/10.0.0.0/24\",\"sequence\":\"AAAAAAAAAQAAAAAAAAABAAAAAAAAAAAAAAAACA==\"}";

        Operation operation = new Operation();
        operation.setBody(body);

        Map<String, String> bodyParameters = EtcdUtils.getBodyParameters(operation);
        assertEquals(
                "{\"id\":\"GlobalDefault/10.0.0.0/24\",\"sequence\":\"AAAAAAAAAQAAAAAAAAABAAAAAAAAAAAAAAAACA==\"}",
                bodyParameters.get("value"));
    }
}
