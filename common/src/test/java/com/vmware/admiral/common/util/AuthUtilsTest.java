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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Base64;

import org.junit.Test;

import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.SystemUserService;
import com.vmware.xenon.services.common.authn.AuthenticationConstants;

public class AuthUtilsTest {

    private static final Field AUTH_CTX_FIELD = ReflectionUtils.getField(Operation.class,
            "authorizationCtx");

    @Test
    public void testCreateAuthorizationHeader() {
        // No credentials
        assertNull(AuthUtils.createAuthorizationHeader(null));

        // Non-password credentials
        AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
        credentials.type = AuthCredentialsType.PublicKey.toString();
        assertNull(AuthUtils.createAuthorizationHeader(credentials));

        // Password credentials
        String email = "test@test.test";
        String password = "test";
        String expectedHeader = String.format("Basic %s",
                new String(Base64.getEncoder()
                        .encode(String.format("%s:%s", email, password).getBytes())));
        credentials = new AuthCredentialsServiceState();
        credentials.type = AuthCredentialsType.Password.toString();
        credentials.userEmail = email;
        credentials.privateKey = password;
        assertEquals(expectedHeader, AuthUtils.createAuthorizationHeader(credentials));
    }

    @Test
    public void testCleanupSessionData() {
        // No authentication
        Operation getOp = Operation.createGet(UriUtils.buildUri("http://localhost/foo/bar"));
        assertNull(getOp.getRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER));
        assertNull(getOp.getCookies());

        AuthUtils.cleanupSessionData(getOp);
        assertNull(getOp.getResponseHeader(Operation.REQUEST_AUTH_TOKEN_HEADER));
        assertNull(getOp.getResponseHeader(Operation.SET_COOKIE_HEADER));

        // Empty authentication
        getOp.addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, "");

        AuthUtils.cleanupSessionData(getOp);
        assertNull(getOp.getResponseHeader(Operation.REQUEST_AUTH_TOKEN_HEADER));
        assertNull(getOp.getResponseHeader(Operation.SET_COOKIE_HEADER));

        // Some authentication
        getOp.addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, "token");
        AuthUtils.cleanupSessionData(getOp);
        assertEquals("", getOp.getResponseHeader(Operation.REQUEST_AUTH_TOKEN_HEADER));
        String setCookie = getOp.getResponseHeader(Operation.SET_COOKIE_HEADER);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("token") && setCookie.contains("Path=/; Max-Age=0"));
    }

    @Test
    public void testValidateSessionData() throws Exception {
        // No operation
        AuthUtils.validateSessionData(null, null);

        // No authentication
        Operation getOp = Operation.createGet(UriUtils.buildUri("http://localhost/foo/bar"));
        AUTH_CTX_FIELD.set(getOp, null);

        AuthUtils.validateSessionData(getOp, null);
        assertNull(getOp.getAuthorizationContext());

        // System user authentication
        Claims.Builder claimsBuilder = new Claims.Builder();
        claimsBuilder.setIssuer(AuthenticationConstants.DEFAULT_ISSUER);
        claimsBuilder.setSubject(SystemUserService.SELF_LINK);

        AuthorizationContext.Builder authCtxBuilder = AuthorizationContext.Builder.create();
        authCtxBuilder.setClaims(claimsBuilder.getResult());
        authCtxBuilder.setToken("super-token");

        AuthorizationContext authCtxSystemUser = authCtxBuilder.getResult();
        AUTH_CTX_FIELD.set(getOp, authCtxSystemUser);

        AuthUtils.validateSessionData(getOp, null);
        assertEquals(authCtxSystemUser, getOp.getAuthorizationContext());

        // Regular user valid authentication
        claimsBuilder = new Claims.Builder();
        claimsBuilder.setIssuer(AuthenticationConstants.DEFAULT_ISSUER);
        claimsBuilder.setSubject("/core/authz/regular-user");

        authCtxBuilder = AuthorizationContext.Builder.create();
        authCtxBuilder.setClaims(claimsBuilder.getResult());
        authCtxBuilder.setToken("regular-token");

        AuthorizationContext authCtxUser = authCtxBuilder.getResult();

        AUTH_CTX_FIELD.set(getOp, authCtxUser);
        AuthUtils.validateSessionData(getOp, null);
        assertEquals(authCtxUser, getOp.getAuthorizationContext());

        // Regular user after logout authentication
        getOp.addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, authCtxUser.getToken());
        AuthUtils.cleanupSessionData(getOp);

        AUTH_CTX_FIELD.set(getOp, authCtxUser);
        AuthUtils.validateSessionData(getOp, null);
        assertEquals(null, getOp.getAuthorizationContext());

        claimsBuilder = new Claims.Builder();
        claimsBuilder.setIssuer(AuthenticationConstants.DEFAULT_ISSUER);
        claimsBuilder.setSubject(SystemUserService.SELF_LINK);

        authCtxBuilder = AuthorizationContext.Builder.create();
        authCtxBuilder.setClaims(claimsBuilder.getResult());
        authCtxBuilder.setToken("guest-token");

        AuthorizationContext authCtxGuestUser = authCtxBuilder.getResult();

        AUTH_CTX_FIELD.set(getOp, authCtxUser);
        AuthUtils.validateSessionData(getOp, authCtxGuestUser);
        assertEquals(authCtxGuestUser, getOp.getAuthorizationContext());
    }

}
