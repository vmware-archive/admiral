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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.Principal.PrincipalType;
import com.vmware.admiral.auth.idm.PrincipalProvider;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class LocalPrincipalProviderTest extends AuthBaseTest {

    private PrincipalProvider provider = new LocalPrincipalProvider();

    @Before
    public void injectHost() throws Exception {
        Field hostField = provider.getClass().getDeclaredField("host");
        if (!hostField.isAccessible()) {
            hostField.setAccessible(true);
        }
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
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
        String expectedPrincipal4 = "tony@admiral.com";

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

        assertEquals(4, principals.size());

        for (Principal p : principals) {
            assertTrue(p.email.equals(expectedPrincipal1)
                    || p.email.equals(expectedPrincipal2)
                    || p.email.equals(expectedPrincipal3)
                    || p.email.equals(expectedPrincipal4));
        }
    }

    @Test
    public void testCreatePrincipal() {
        Principal principal = new Principal();
        principal.type = PrincipalType.USER;
        principal.email = "test@admiral.com";
        principal.name = "test";
        principal.password = "testPassword";

        DeferredResult<Principal> result = provider.createPrincipal(principal);

        TestContext ctx = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        result = provider.getPrincipal(principal.email);

        TestContext ctx1 = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx1.failIteration(ex);
                return;
            }
            ctx1.completeIteration();
        });
        ctx1.await();

        Principal newPrincipal = result.getNow(new Principal());
        assertNotNull(newPrincipal);
        assertEquals(principal.email, newPrincipal.email);
        assertEquals(principal.email, newPrincipal.id);
        assertEquals("test", newPrincipal.name);
        assertEquals(PrincipalType.USER, newPrincipal.type);
    }

    @Test
    public void testUpdatePrincipal() {
        Principal principal = new Principal();
        principal.type = PrincipalType.USER;
        principal.email = "test@admiral.com";
        principal.name = "test";
        principal.password = "testPassword";

        DeferredResult<Principal> result = provider.createPrincipal(principal);

        TestContext ctx = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();
        principal = result.getNow(new Principal());
        principal.name = "NewName";
        result = provider.updatePrincipal(principal);

        TestContext ctx1 = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx1.failIteration(ex);
                return;
            }
            ctx1.completeIteration();
        });
        ctx1.await();

        result = provider.getPrincipal(principal.email);

        TestContext ctx2 = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx2.failIteration(ex);
                return;
            }
            ctx2.completeIteration();
        });
        ctx2.await();

        Principal newPrincipal = result.getNow(new Principal());

        assertNotNull(newPrincipal);
        assertEquals(principal.name, newPrincipal.name);
    }

    @Test
    public void testDeletePrincipal() {
        Principal principal = new Principal();
        principal.type = PrincipalType.USER;
        principal.email = "test@admiral.com";
        principal.name = "test";
        principal.password = "testPassword";

        DeferredResult<Principal> result = provider.createPrincipal(principal);

        TestContext ctx = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();
        principal = result.getNow(new Principal());

        result = provider.deletePrincipal(principal.id);
        TestContext ctx1 = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx1.failIteration(ex);
                return;
            }
            ctx1.completeIteration();
        });
        ctx1.await();

        result = provider.getPrincipal(principal.id);
        TestContext ctx2 = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                if (ex.getCause() instanceof ServiceNotFoundException) {
                    ctx2.completeIteration();
                    return;
                }
                ctx2.failIteration(ex);
                return;
            }
            ctx2.failIteration(new RuntimeException("Getting deleted principal should have "
                    + "failed"));
        });
        ctx2.await();
    }

    @Test
    public void testNestedGetGroupsForPrincipal() throws Throwable {
        // Create nested groups.
        LocalPrincipalState itGroup = new LocalPrincipalState();
        itGroup.name = "it";
        itGroup.type = LocalPrincipalType.GROUP;
        itGroup.groupMembersLinks = Collections.singletonList(UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, "superusers"));
        itGroup = doPost(itGroup, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(itGroup);

        LocalPrincipalState organization = new LocalPrincipalState();
        organization.name = "organization";
        organization.type = LocalPrincipalType.GROUP;
        organization.groupMembersLinks = Collections.singletonList(UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, "it"));
        organization = doPost(organization, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(organization);

        // fritz is part of "superusers" which is part of "it", and "it" is part of "organization"
        // verify when get groups for fritz "superusers", "it" and "organization is returned"
        DeferredResult<Set<String>> result = provider.getAllGroupsForPrincipal(
                USER_EMAIL_ADMIN);

        TestContext ctx = testCreate(1);
        Set<String> results = new HashSet<>();

        result.whenComplete((groups, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            results.addAll(groups);
            ctx.completeIteration();
        });

        ctx.await();

        assertTrue(results.contains("superusers"));
        assertTrue(results.contains("it"));
        assertTrue(results.contains("organization"));
    }

    @Test
    public void testSimpleGetGroupsForPrincipal() {
        DeferredResult<Set<String>> result = provider.getAllGroupsForPrincipal(
                USER_EMAIL_ADMIN);

        TestContext ctx = testCreate(1);
        Set<String> results = new HashSet<>();

        result.whenComplete((groups, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            results.addAll(groups);
            ctx.completeIteration();
        });

        ctx.await();

        assertTrue(results.contains("superusers"));
    }

}
