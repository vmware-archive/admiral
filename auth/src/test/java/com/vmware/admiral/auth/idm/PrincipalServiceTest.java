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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;

public class PrincipalServiceTest extends AuthBaseTest {

    @Before
    public void setIdentity() throws GeneralSecurityException {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
    }

    @Test
    public void testGetPrincipalWithValidInput() {
        Principal admin = testRequest(Operation::createGet,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, USER_EMAIL_ADMIN), false, null,
                Principal.class);
        assertNotNull(admin);
        assertEquals(USER_EMAIL_ADMIN, admin.id);

        Principal connie = testRequest(Operation::createGet,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, USER_EMAIL_CONNIE), false, null,
                Principal.class);
        assertNotNull(connie);
        assertEquals(USER_EMAIL_CONNIE, connie.id);
    }

    @Test
    public void testGetPrincipalWithInvalidInput() {
        // Test with empty principal id.
        testRequest(Operation::createGet, PrincipalService.SELF_LINK, true,
                new IllegalStateException(
                        "Expected exception != null when searching for principal with empty id."),
                null);

        // Test with non present principal id.
        testRequest(Operation::createGet,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, "no-such-user"), true,
                new IllegalStateException(
                        "Expected exception != null when searching for non present principal."),
                null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetPrincipalsWithValidInput() {
        // match a single user
        ArrayList<Principal> principals = testRequest(Operation::createGet,
                String.format("%s/?%s=%s", PrincipalService.SELF_LINK,
                        PrincipalService.CRITERIA_QUERY, USER_EMAIL_ADMIN),
                false, null, ArrayList.class);
        assertEquals(1, principals.size());
        assertEquals(USER_EMAIL_ADMIN, principals.iterator().next().id);

        // match multiple users
        principals = testRequest(Operation::createGet, String.format("%s/?%s=%s",
                PrincipalService.SELF_LINK, PrincipalService.CRITERIA_QUERY, "i"), false, null,
                ArrayList.class);
        List<String> expectedPrincipals = Arrays.asList(USER_EMAIL_ADMIN, USER_EMAIL_BASIC_USER,
                USER_EMAIL_GLORIA, USER_EMAIL_CONNIE);
        assertEquals(expectedPrincipals.size(), principals.size());
        assertTrue(USER_EMAIL_ADMIN, principals.stream().allMatch((principal) -> {
            return expectedPrincipals.contains(principal.id);
        }));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetPrincipalsWithInvalidInput() {

        // Test with empty criteria
        testRequest(Operation::createGet,
                String.format("%s/?%s=", PrincipalService.SELF_LINK,
                        PrincipalService.CRITERIA_QUERY),
                true,
                new IllegalStateException(
                        "Expected exception != null when searching for principal with empty criteria."),
                null);

        // Test with missing user
        ArrayList<Principal> principals = testRequest(Operation::createGet,
                String.format("%s/?%s=%s", PrincipalService.SELF_LINK,
                        PrincipalService.CRITERIA_QUERY, "no-such-user"),
                false, null, ArrayList.class);
        assertEquals(0, principals.size());
    }

    @Test
    public void testGetSecurityContextShouldPass() throws GeneralSecurityException {
        // Assume the identity of admin, because basic user should not be able to use
        // PrincipalService and get data for other users.
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        SecurityContext securityContext = testRequest(Operation::createGet,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, USER_EMAIL_ADMIN,
                        PrincipalService.SECURITY_CONTEXT_SUFFIX),
                false, null, SecurityContext.class);
        assertEquals(USER_EMAIL_ADMIN, securityContext.id);
    }

    @Test
    public void testGetSecurityContextShouldFail() {
        testRequest(Operation::createGet,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, "no-such-user",
                        PrincipalService.SECURITY_CONTEXT_SUFFIX),
                true,
                new IllegalStateException(
                        "Expected exception != null when retrieving security context for a missing user"),
                null);
    }

    private <T> T testRequest(BiFunction<ServiceHost, String, Operation> opFunction,
            String requestPath, boolean expectFailure, Throwable throwOnPass,
            Class<T> resultClass) {
        ArrayList<T> result = new ArrayList<>(1);

        Operation op = opFunction.apply(host, requestPath)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (expectFailure) {
                            host.completeIteration();
                        } else {
                            host.failIteration(ex);
                        }
                    } else {
                        if (expectFailure) {
                            host.failIteration(throwOnPass != null ? throwOnPass
                                    : new IllegalArgumentException(String.format(
                                    "Request to %s was expected to fail but passed",
                                    requestPath)));
                        } else {
                            try {
                                result.add(o.getBody(resultClass));
                                host.completeIteration();
                            } catch (Throwable er) {
                                host.failIteration(er);
                            }
                        }
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();

        if (expectFailure) {
            return null;
        } else {
            assertEquals(String.format("Failed to retrieve response body of class %s",
                    resultClass.getName()), 1, result.size());
            return result.iterator().next();
        }
    }

    @Test
    public void testAssignAndUnassignExistingGroupsToRole() throws Throwable {
        String superusers = "superusers";
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = new ArrayList<>();
        roleAssignment.add.add(AuthRole.CLOUD_ADMINS.getName());

        String uri = UriUtils.buildUriPath(PrincipalService.SELF_LINK, superusers, "roles");
        // Assingn superusers to cloud admins
        doPatch(roleAssignment, uri);

        // Verify superusers got assigned and required roles are created.
        String superusersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, AuthRole
                .CLOUD_ADMINS.buildRoleWithSuffix(superusers));

        RoleState roleState = getDocument(RoleState.class, superusersRoleLink);
        assertNotNull(roleState);
        assertEquals(superusersRoleLink, roleState.documentSelfLink);

        // Unassign superusers from cloud admins
        roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.remove = new ArrayList<>();
        roleAssignment.remove.add(AuthRole.CLOUD_ADMINS.getName());
        doPatch(roleAssignment, uri);

        // Verify superusers got unassigned and required roles are deleted
        TestContext ctx = testCreate(1);
        Operation getSuperusersRole = Operation.createGet(host, superusersRoleLink)
                .setReferer(host.getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        if (ex instanceof ServiceNotFoundException) {
                            ctx.completeIteration();
                            return;
                        }
                        ctx.failIteration(ex);
                        return;
                    }
                    ctx.failIteration(new RuntimeException("After unassign user group, role "
                            + "should be deleted."));
                });
        host.send(getSuperusersRole);
        ctx.await();
    }
}
