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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.PrincipalProvider;
import com.vmware.xenon.common.test.TestContext;

public class LocalPrincipalProviderTest extends AuthBaseTest {

    private PrincipalProvider provider = new LocalPrincipalProvider();

    @Before
    public void injectHost() throws Exception {
        Field hostField = provider.getClass().getDeclaredField("host");
        if (!hostField.isAccessible()) {
            hostField.setAccessible(true);
        }
        host.assumeIdentity(buildUserServicePath(ADMIN_USERNAME));
        hostField.set(provider, host);
        hostField.setAccessible(false);
    }

    @Test
    public void testGetPrincipalWithCallback() {
        String principalId = "connie@admiral.com";
        final String[] state = new String[1];
        TestContext ctx = new TestContext(1, Duration.ofSeconds(30));
        provider.getPrincipal(principalId, (userState, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            state[0] = userState;
            ctx.completeIteration();
        });
        ctx.await();
        assertNotNull(state[0]);
        assertEquals(principalId, state[0]);
    }

    @Test
    public void testGetPrincipalsWithCallback() {
        String criteria = "i";
        String expectedPrincipal1 = "connie@admiral.com";
        String expectedPrincipal2 = "fritz@admiral.com";
        String expectedPrincipal3 = "gloria@admiral.com";

        List<String> principals = new ArrayList<>();
        TestContext ctx = new TestContext(1, Duration.ofSeconds(30));
        provider.getPrincipals(criteria, (userStates, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            principals.addAll(userStates);
            ctx.completeIteration();
        });
        ctx.await();

        assertEquals(3, principals.size());
        assertTrue(principals.contains(expectedPrincipal1));
        assertTrue(principals.contains(expectedPrincipal2));
        assertTrue(principals.contains(expectedPrincipal3));
    }

}
