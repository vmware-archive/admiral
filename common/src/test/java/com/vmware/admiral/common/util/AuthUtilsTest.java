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

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.util.Base64;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TestServiceHost.SomeExampleService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.SystemUserService;
import com.vmware.xenon.services.common.authn.AuthenticationConstants;

public class AuthUtilsTest {

    private static final Field AUTH_CTX_FIELD = ReflectionUtils.getField(Operation.class,
            "authorizationCtx");

    private VerificationHost host;

    @Before
    public void setUp() throws Throwable {
        ServiceHost.Arguments args = new ServiceHost.Arguments();
        args.sandbox = null; // ask runtime to pick a random storage location
        args.port = 0; // ask runtime to pick a random port
        args.isAuthorizationEnabled = false;

        host = new VerificationHost();

        host = VerificationHost.initialize(host, args);
        host.start();
    }

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

        // Bearer token
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL";
        expectedHeader = String.format("Bearer %s", token);
        credentials = new AuthCredentialsServiceState();
        credentials.type = "Bearer";
        credentials.privateKey = token;
        assertEquals(expectedHeader, AuthUtils.createAuthorizationHeader(credentials));
    }

    @Test
    public void testCleanupSessionData() {
        // No authentication
        Operation getOp = Operation.createGet(UriUtils.buildUri("http://localhost/foo/bar"));
        assertNull(getOp.getRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER));
        assertNull(getOp.getCookies());

        AuthUtils.cleanupSessionData(getOp);
        assertEquals("", getOp.getResponseHeader(Operation.REQUEST_AUTH_TOKEN_HEADER));
        assertAuthCookie(getOp);

        // Empty authentication
        getOp.addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, "");

        AuthUtils.cleanupSessionData(getOp);
        assertEquals("", getOp.getResponseHeader(Operation.REQUEST_AUTH_TOKEN_HEADER));
        assertAuthCookie(getOp);

        // Some authentication
        getOp.addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, "token");
        AuthUtils.cleanupSessionData(getOp);
        assertEquals("", getOp.getResponseHeader(Operation.REQUEST_AUTH_TOKEN_HEADER));
        assertAuthCookie(getOp);
    }

    @Test
    public void testValidateSessionData() throws Exception {
        // No operation
        AuthUtils.validateSessionData(host,null, null, null);

        // No authentication
        Operation getOp = Operation.createGet(UriUtils.buildUri("http://localhost/foo/bar"));
        AUTH_CTX_FIELD.set(getOp, null);

        AuthUtils.validateSessionData(host, getOp, null, getOp.getAuthorizationContext());
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

        AuthUtils.validateSessionData(host, getOp, null, getOp.getAuthorizationContext());
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
        AuthUtils.validateSessionData(host, getOp, null, getOp.getAuthorizationContext());
        assertEquals(authCtxUser, getOp.getAuthorizationContext());

        // Regular user valid authentication through token
        claimsBuilder = new Claims.Builder();
        claimsBuilder.setIssuer(AuthenticationConstants.DEFAULT_ISSUER);
        claimsBuilder.setSubject("/core/authz/regular-user");

        authCtxBuilder = AuthorizationContext.Builder.create();
        authCtxBuilder.setClaims(claimsBuilder.getResult());
        authCtxBuilder.setToken("regular-token");

        authCtxUser = authCtxBuilder.getResult();

        Service s = new SomeExampleService();
        host.addPrivilegedService(SomeExampleService.class);
        host.cacheAuthorizationContext(s, authCtxUser.getToken(), authCtxUser);

        AUTH_CTX_FIELD.set(getOp, null);
        getOp.addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER,
                authCtxUser.getToken());
        AuthUtils.validateSessionData(host, getOp, null, null);
        assertEquals(authCtxUser, getOp.getAuthorizationContext());
        host.clearAuthorizationContext(s, authCtxUser.getClaims().getSubject());

        // Regular user after logout authentication
        getOp.addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, authCtxUser.getToken());
        AuthUtils.cleanupSessionData(getOp);

        AUTH_CTX_FIELD.set(getOp, authCtxUser);
        AuthUtils.validateSessionData(host, getOp, null, getOp.getAuthorizationContext());
        assertNull(getOp.getAuthorizationContext());

        claimsBuilder = new Claims.Builder();
        claimsBuilder.setIssuer(AuthenticationConstants.DEFAULT_ISSUER);
        claimsBuilder.setSubject(SystemUserService.SELF_LINK);

        authCtxBuilder = AuthorizationContext.Builder.create();
        authCtxBuilder.setClaims(claimsBuilder.getResult());
        authCtxBuilder.setToken("guest-token");

        AuthorizationContext authCtxGuestUser = authCtxBuilder.getResult();

        AUTH_CTX_FIELD.set(getOp, authCtxUser);
        AuthUtils.validateSessionData(host, getOp, authCtxGuestUser,
                getOp.getAuthorizationContext());
        assertEquals(authCtxGuestUser, getOp.getAuthorizationContext());
    }

    private void assertAuthCookie(Operation op) {
        String cookieHeader = op.getResponseHeader(Operation.SET_COOKIE_HEADER);
        Cookie cookie = ClientCookieDecoder.LAX.decode(cookieHeader);
        assertEquals(AuthenticationConstants.REQUEST_AUTH_TOKEN_COOKIE, cookie.name());
        assertEquals("", cookie.value());
        assertEquals(0, cookie.maxAge());
    }

}
