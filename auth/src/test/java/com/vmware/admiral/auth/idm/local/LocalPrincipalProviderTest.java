/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
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

import static com.vmware.admiral.auth.util.PrincipalUtil.encode;

import java.util.ArrayList;
import java.util.Arrays;
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
    public void injectHost() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        provider.init(privilegedTestService);
    }

    @Test
    public void testGetPrincipal() {
        String principalId = "connie@admiral.com";
        DeferredResult<Principal> result = provider.getPrincipal(null, principalId);

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
        assertTrue(principal.groups.contains(USER_GROUP_DEVELOPERS));
    }

    @Test
    public void testGetPrincipalsOfTypeGroup() {
        String principalId = "superusers@admiral.com";
        DeferredResult<List<Principal>> result = provider.getPrincipals(null, principalId);

        TestContext ctx = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        Principal principal = result.getNow(Collections.singletonList(new Principal())).get(0);
        assertNotNull(principal);
        assertEquals(principalId, principal.id);
        assertEquals("superusers@admiral.com", principal.name);
        assertEquals(PrincipalType.GROUP, principal.type);
    }

    @Test
    public void testGetPrincipalsSearchByEmailOrName() throws Throwable {
        LocalPrincipalState lazyPeonUser = new LocalPrincipalState();
        lazyPeonUser.name = "Lazy Peon";
        lazyPeonUser.type = LocalPrincipalType.USER;
        lazyPeonUser.email = "lazy@peon";
        lazyPeonUser.password = "testPassword";
        lazyPeonUser = doPost(lazyPeonUser, LocalPrincipalFactoryService.SELF_LINK);

        // Get by email.
        DeferredResult<List<Principal>> result = provider.getPrincipals(null, lazyPeonUser.email);

        TestContext ctx = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        Principal principal = result.getNow(Collections.singletonList(new Principal())).get(0);
        assertNotNull(principal);
        assertEquals(lazyPeonUser.id, principal.id);
        assertEquals(lazyPeonUser.name, principal.name);
        assertEquals(PrincipalType.USER, principal.type);
        assertEquals(lazyPeonUser.email, principal.email);

        // Get by name.
        result = provider.getPrincipals(null, lazyPeonUser.name);

        TestContext ctx1 = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx1.failIteration(ex);
                return;
            }
            ctx1.completeIteration();
        });
        ctx1.await();

        principal = result.getNow(Collections.singletonList(new Principal())).get(0);
        assertNotNull(principal);
        assertEquals(lazyPeonUser.id, principal.id);
        assertEquals(lazyPeonUser.name, principal.name);
        assertEquals(PrincipalType.USER, principal.type);
        assertEquals(lazyPeonUser.email, principal.email);
    }

    @Test
    public void testGetPrincipals() {
        String criteria = "i";

        List<String> expectedPrincipals = Arrays.asList(USER_EMAIL_ADMIN, USER_EMAIL_BASIC_USER,
                USER_EMAIL_GLORIA, USER_EMAIL_CONNIE, USER_EMAIL_ADMIN2, USER_GROUP_SUPERUSERS,
                USER_GROUP_DEVELOPERS, USER_EMAIL_CLOUD_ADMIN, USER_EMAIL_PROJECT_ADMIN_1,
                USER_EMAIL_PROJECT_MEMBER_1, USER_EMAIL_PROJECT_VIEWER_1);

        DeferredResult<List<Principal>> result = provider.getPrincipals(null, criteria);

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

        assertEquals(expectedPrincipals.size(), principals.size());

        for (Principal p : principals) {
            if (p.id.equals(USER_EMAIL_CONNIE)) {
                assertTrue(p.groups.contains(USER_GROUP_DEVELOPERS));
            }
            assertTrue(expectedPrincipals.contains(p.id));
        }
    }

    @Test
    public void testCreatePrincipal() {
        Principal principal = new Principal();
        principal.type = PrincipalType.USER;
        principal.email = "test@admiral.com";
        principal.name = "test";
        principal.password = "testPassword";
        principal.id = principal.email;

        DeferredResult<Principal> result = provider.createPrincipal(null, principal);

        TestContext ctx = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        result = provider.getPrincipal(null, principal.email);

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
        principal.id = principal.email;

        DeferredResult<Principal> result = provider.createPrincipal(null, principal);

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
        result = provider.updatePrincipal(null, principal);

        TestContext ctx1 = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx1.failIteration(ex);
                return;
            }
            ctx1.completeIteration();
        });
        ctx1.await();

        result = provider.getPrincipal(null, principal.email);

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
        principal.id = principal.email;

        DeferredResult<Principal> result = provider.createPrincipal(null, principal);

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

        result = provider.deletePrincipal(null, principal.id);
        TestContext ctx1 = testCreate(1);
        result.whenComplete((p, ex) -> {
            if (ex != null) {
                ctx1.failIteration(ex);
                return;
            }
            ctx1.completeIteration();
        });
        ctx1.await();

        result = provider.getPrincipal(null, principal.id);
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
        itGroup.name = "it@admiral.com";
        itGroup.type = LocalPrincipalType.GROUP;
        itGroup.groupMembersLinks = Collections.singletonList(UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, encode("superusers@admiral.com")));
        itGroup = doPost(itGroup, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(itGroup);

        LocalPrincipalState organization = new LocalPrincipalState();
        organization.name = "organization@admiral.com";
        organization.type = LocalPrincipalType.GROUP;
        organization.groupMembersLinks = Collections.singletonList(UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, encode("it@admiral.com")));
        organization = doPost(organization, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(organization);

        // fritz is part of "superusers" which is part of "it", and "it" is part of "organization"
        // verify when get groups for fritz "superusers", "it" and "organization is returned"
        DeferredResult<Set<String>> result = provider.getAllGroupsForPrincipal(null,
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

        assertTrue(results.contains("superusers@admiral.com"));
        assertTrue(results.contains("it@admiral.com"));
        assertTrue(results.contains("organization@admiral.com"));
    }

    @Test
    public void testSimpleGetGroupsForPrincipal() {
        DeferredResult<Set<String>> result = provider.getAllGroupsForPrincipal(null,
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

        assertTrue(results.contains("superusers@admiral.com"));
    }

    @Test
    public void testNestedGetGroupsForPrincipalOfTypeGroup() throws Throwable {
        // Create nested groups.
        LocalPrincipalState itGroup = new LocalPrincipalState();
        itGroup.name = "it@admiral.com";
        itGroup.type = LocalPrincipalType.GROUP;
        itGroup.groupMembersLinks = Collections.singletonList(UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, encode(USER_GROUP_SUPERUSERS)));
        itGroup = doPost(itGroup, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(itGroup);

        LocalPrincipalState organization = new LocalPrincipalState();
        organization.name = "organization@admiral.com";
        organization.type = LocalPrincipalType.GROUP;
        organization.groupMembersLinks = Collections.singletonList(UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, encode("it@admiral.com")));
        organization = doPost(organization, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(organization);

        DeferredResult<Set<String>> result = provider.getAllGroupsForPrincipal(null, USER_GROUP_SUPERUSERS);

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

        assertTrue(results.contains("it@admiral.com"));
        assertTrue(results.contains("organization@admiral.com"));
    }

}
