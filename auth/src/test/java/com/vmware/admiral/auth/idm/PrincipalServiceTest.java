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

package com.vmware.admiral.auth.idm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class PrincipalServiceTest {

    private PrincipalService service = new PrincipalService();

    @Test
    public void testGetPrincipal() {
        assertNull(service.getPrincipal(""));
        assertEquals("Jason", service.getPrincipal("jason"));
        assertEquals("Shauna", service.getPrincipal("Shauna"));
        assertEquals(null, service.getPrincipal("scot"));
    }

    @Test
    public void testPrincipals() {
        assertTrue(service.getPrincipals("").isEmpty());

        List<String> principals = service.getPrincipals("jason");
        assertTrue(principals.size() == 1 && principals.contains("Jason"));

        principals = service.getPrincipals("Shauna");
        assertTrue(principals.size() == 1 && principals.contains("Shauna"));

        principals = service.getPrincipals("scot");
        assertTrue(principals.size() == 1 && principals.contains("Scott"));

        principals = service.getPrincipals("s");
        assertTrue(principals.size() == 3 && principals.contains("Jason")
                && principals.contains("Shauna") && principals.contains("Scott"));

        principals = service.getPrincipals("A");
        assertTrue(principals.size() == 2 && principals.contains("Jason")
                && principals.contains("Shauna"));
    }

}
