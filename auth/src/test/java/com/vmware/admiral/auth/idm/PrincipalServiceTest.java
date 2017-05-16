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
        assertEquals("Fritz", service.getPrincipal("fritz"));
        assertEquals("Gloria", service.getPrincipal("Gloria"));
        assertEquals(null, service.getPrincipal("conie"));
    }

    @Test
    public void testPrincipals() {
        assertTrue(service.getPrincipals("").isEmpty());

        List<String> principals = service.getPrincipals("fritz");
        assertTrue(principals.size() == 1 && principals.contains("Fritz"));

        principals = service.getPrincipals("Gloria");
        assertTrue(principals.size() == 1 && principals.contains("Gloria"));

        principals = service.getPrincipals("conni");
        assertTrue(principals.size() == 1 && principals.contains("Connie"));

        principals = service.getPrincipals("i");
        assertTrue(principals.size() == 3 && principals.contains("Fritz")
                && principals.contains("Gloria") && principals.contains("Connie"));

        principals = service.getPrincipals("O");
        assertTrue(principals.size() == 2 && principals.contains("Gloria")
                && principals.contains("Connie"));
    }

}
