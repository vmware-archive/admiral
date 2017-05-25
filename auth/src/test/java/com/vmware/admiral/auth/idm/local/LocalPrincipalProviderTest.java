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

package com.vmware.admiral.auth.idm.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.Principal.PrincipalType;
import com.vmware.admiral.auth.idm.PrincipalProvider;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.test.TestContext;

public class LocalPrincipalProviderTest extends AuthBaseTest {

    private PrincipalProvider provider = new LocalPrincipalProvider();

    @Before
    public void injectHost() throws Exception {
        Field hostField = provider.getClass().getDeclaredField("host");
        if (!hostField.isAccessible()) {
            hostField.setAccessible(true);
        }
        host.assumeIdentity(buildUserServicePath(USERNAME_ADMIN));
        hostField.set(provider, host);
        hostField.setAccessible(false);
    }

    @Test
    public void testGetPrincipal() {
        String principalId = "connie@admiral.com";
        DeferredResult<Principal> result = provider.getPrincipal(principalId);

        TestContext ctx = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        Principal principal = result.getNow(new Principal());
        assertNotNull(principal);
        assertEquals(principalId, principal.email);
        assertEquals(principalId, principal.id);
        assertEquals("Connie", principal.name);
        assertEquals(PrincipalType.USER, principal.type);
    }

    @Test
    public void testGetPrincipals() {
        String criteria = "i";
        String expectedPrincipal1 = "connie@admiral.com";
        String expectedPrincipal2 = "fritz@admiral.com";
        String expectedPrincipal3 = "gloria@admiral.com";

        DeferredResult<List<Principal>> result = provider.getPrincipals(criteria);

        TestContext ctx = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        List<Principal> principals = result.getNow(new ArrayList<>());

        assertEquals(3, principals.size());

        for (Principal p : principals) {
            assertTrue(p.email.equals(expectedPrincipal1)
                    || p.email.equals(expectedPrincipal2)
                    || p.email.equals(expectedPrincipal3));
        }
    }
}
