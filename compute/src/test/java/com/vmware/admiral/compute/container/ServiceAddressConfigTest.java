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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.net.URISyntaxException;

import org.junit.Test;

public class ServiceAddressConfigTest {

    @Test
    public void testAddressFormat() throws URISyntaxException {
        assertEquals("http://wordpress-123.vmware",
                ServiceAddressConfig.formatAddress("wordpress-%s.vmware", "123"));
        assertEquals("http://wordpress-123", ServiceAddressConfig.formatAddress("wordpress", "123"));
        assertEquals("https://wordpress-123/path/sub?query=true",
                ServiceAddressConfig.formatAddress("https://wordpress/path/sub?query=true", "123"));

        try {
            ServiceAddressConfig.formatAddress("https://wordpress:8080", "123");
            fail("Expected to fail because port was provided");
        } catch (Exception e) {
            assertNotNull(e);
        }

        try {
            ServiceAddressConfig.formatAddress("https://wordpress .vmware", "123");
            fail("Expected to fail because of invalid hostname");
        } catch (Exception e) {
            assertNotNull(e);
        }

        try {
            ServiceAddressConfig.formatAddress("https://wordpress.vmware/path%s", "123");
            fail("Expected to fail because format character provided but not on hostname");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }
}
