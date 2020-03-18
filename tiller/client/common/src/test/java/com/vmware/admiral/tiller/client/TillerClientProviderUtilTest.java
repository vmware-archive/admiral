/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.tiller.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class TillerClientProviderUtilTest {

    @Test
    public void testGetTillerClientProvider() {
        TillerClientProvider provider = TillerClientProviderUtil.getTillerClientProvider();
        assertNotNull(provider);
        assertEquals(MockTillerClientProvider.class.getName(), provider.getClass().getName());
    }

    @Test
    public void testGetTillerClientProviderWithPreference() {
        TillerClientProvider provider = TillerClientProviderUtil
                .getTillerClientProvider(MockTillerClientProvider.class.getName());
        assertNotNull(provider);
        assertEquals(MockTillerClientProvider.class.getName(), provider.getClass().getName());
    }

    @Test
    public void testGetTillerClientProviderWithUnsatisfiablePreference() {
        TillerClientProvider provider = TillerClientProviderUtil
                .getTillerClientProvider("no-such-class-name");
        assertNotNull(provider);
        assertEquals(MockTillerClientProvider.class.getName(), provider.getClass().getName());
    }
}
