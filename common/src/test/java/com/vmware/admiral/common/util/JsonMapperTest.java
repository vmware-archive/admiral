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

package com.vmware.admiral.common.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class JsonMapperTest {
    private static final String JSON_SOURCE = "{\"k1\":\"v1\",\"k2\":\"v2\"}";
    private static final String k1 = "k1";
    private static final String v1 = "v1";

    private static final String k2 = "k2";
    private static final String v2 = "v2";

    @Test
    public void testRoundtrip() throws IOException {

        Map<String, String> source = new HashMap<>();
        source.put(k1, v1);
        source.put(k2, v2);

        String json = JsonMapper.toJSON(source);

        Assert.assertNotNull(json);
        Assert.assertEquals(JSON_SOURCE, json);

        Map cloned = JsonMapper.fromJSON(json, Map.class);
        Assert.assertNotNull(cloned);

        Assert.assertEquals(source, cloned);
    }
}
