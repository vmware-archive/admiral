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

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.junit.Test;

import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class AuthUtilsTest {

    @Test
    public void testCreateAuthorizationHeader() {
        assertNull(AuthUtils.createAuthorizationHeader(null));

        String email = "test@test.test";
        String password = "test";
        String expectedHeader = String.format("Basic %s",
                new String(Base64.getEncoder()
                        .encode(String.format("%s:%s", email, password).getBytes())));
        AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
        credentials.type = AuthCredentialsType.Password.toString();
        credentials.userEmail = email;
        credentials.privateKey = password;
        assertEquals(expectedHeader, AuthUtils.createAuthorizationHeader(credentials));
    }

    @Test
    public void testIsDevOpsAdmin() throws Throwable {
        Method setAuthCtxMethod = Operation.class.getDeclaredMethod("setAuthorizationContext",
                AuthorizationContext.class);

        Claims guestClaims = new Claims.Builder().setSubject(GuestUserService.SELF_LINK)
                .getResult();
        AuthorizationContext guestContext = AuthorizationContext.Builder.create()
                .setClaims(guestClaims).getResult();

        // TODO Currently all authorized non-guest users are devOpsAdmins. Needs to be changed after
        // roles are introduced. Also, a case for developer authorization context and cloud admin
        // need to be added.
        Claims devOpsClaims = new Claims.Builder()
                .setSubject(AuthUtils.getUserStateDocumentLink("some-user@local")).getResult();
        AuthorizationContext devOpsContext = AuthorizationContext.Builder.create()
                .setClaims(devOpsClaims).getResult();

        setAuthCtxMethod.setAccessible(true);

        Operation op = new Operation();
        setAuthCtxMethod.invoke(op, (AuthorizationContext) null);
        assertEquals(null, op.getAuthorizationContext());
        assertFalse("<null> authorization context should not be treated as devOps admin context",
                AuthUtils.isDevOpsAdmin(op));

        setAuthCtxMethod.invoke(op, guestContext);
        assertFalse("Guest authorization context should not be trated as devOps admin context",
                AuthUtils.isDevOpsAdmin(op));

        setAuthCtxMethod.invoke(op, devOpsContext);
        assertTrue("Any non-guest authorized user should be a devOps admin",
                AuthUtils.isDevOpsAdmin(op));

        setAuthCtxMethod.setAccessible(false);
    }

    @Test
    public void testBuildUsersQuery() {

        List<String> testUsers = Arrays.asList(
                "/users/user1@test.com",
                "/users/user2@test.com",
                "/users/admi@dev.local");

        Query queryForUsers = AuthUtils.buildUsersQuery(testUsers);

        // kind and userlinks clauses
        List<Query> topLevelClauses = queryForUsers.booleanClauses;
        assertNotNull(topLevelClauses);
        assertEquals(2, topLevelClauses.size());

        topLevelClauses.forEach((query) -> {
            assertEquals(Occurance.MUST_OCCUR, query.occurance);
            assertTrue(query.booleanClauses == null
                    || query.booleanClauses.isEmpty()
                    || query.booleanClauses.size() == testUsers.size());

            if (query.booleanClauses == null || query.booleanClauses.isEmpty()) {
                assertEquals(MatchType.TERM, query.term.matchType);
            } else {
                // this is the userLinks query clause
                query.booleanClauses.forEach((clause) -> {
                    assertEquals(Occurance.SHOULD_OCCUR, clause.occurance);
                    assertEquals(MatchType.TERM, clause.term.matchType);
                    assertEquals(ServiceDocument.FIELD_NAME_SELF_LINK, clause.term.propertyName);
                    assertTrue(testUsers.contains(clause.term.matchValue));
                });
            }
        });
    }

    @Test
    public void testBuildUsersQueryNoUsers() {

        Query queryForUsers = AuthUtils.buildUsersQuery(null);

        // kind and userlinks clauses
        List<Query> topLevelClauses = queryForUsers.booleanClauses;
        assertNotNull(topLevelClauses);
        assertEquals(2, topLevelClauses.size());

        topLevelClauses.forEach((query) -> {
            assertEquals(Occurance.MUST_OCCUR, query.occurance);
            assertEquals(MatchType.TERM, query.term.matchType);
            assertTrue(Arrays
                    .asList(ServiceDocument.FIELD_NAME_KIND, ServiceDocument.FIELD_NAME_SELF_LINK)
                    .contains(query.term.propertyName));

            if (query.term.propertyName.equals(ServiceDocument.FIELD_NAME_SELF_LINK)) {
                assertEquals(AuthUtils.USERS_QUERY_NO_USERS_SELF_LINK, query.term.matchValue);
            }
        });
    }

}
