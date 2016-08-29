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

package com.vmware.admiral.adapter.docker.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test mapping of property names (CapitalizedLikeThis) to switch names (--capitalized-like-this)
 */
public class PropertyToSwitchNameMapperTest {

    @Test
    public void testMapping() {
        PropertyToSwitchNameMapper mapper = new PropertyToSwitchNameMapper();
        assertEquals("capitalized-like-this", mapper.apply("CapitalizedLikeThis"));
    }
}
