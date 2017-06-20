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
import static org.junit.Assert.assertTrue;

import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.authn.AuthenticationConstants;

public class SessionServiceTest extends AuthBaseTest {

    @Test
    public void testInvalidPathShouldFail() throws Exception {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        TestContext ctx = testCreate(1);
        Operation get = Operation
                .createGet(host, UriUtils.buildUriPath(SessionService.SELF_LINK, "/foo"))
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null && ex instanceof ServiceNotFoundException) {
                        ctx.completeIteration();
                        return;
                    }
                    ctx.failIteration(new IllegalStateException());
                });
        host.send(get);
        ctx.await();
    }

    @Test
    public void testLogoutWithSessionShouldPass() throws Exception {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        AtomicReference<Operation> getCompleted = new AtomicReference<Operation>();

        TestContext ctx = testCreate(1);
        Operation get = Operation.createGet(host, ManagementUriParts.AUTH_LOGOUT)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                        return;
                    }
                    getCompleted.set(o);
                    ctx.completeIteration();
                });
        host.send(get);
        ctx.await();

        assertEquals(Operation.STATUS_CODE_OK, getCompleted.get().getStatusCode());
        String cookie = getCompleted.get().getResponseHeader(Operation.SET_COOKIE_HEADER);
        assertTrue(cookie != null
                && cookie.startsWith(AuthenticationConstants.REQUEST_AUTH_TOKEN_COOKIE));
    }

    @Test
    public void testInvalidSessionShouldFail() {
        TestContext ctx = testCreate(1);
        Operation get = Operation.createGet(host, ManagementUriParts.AUTH_LOGOUT)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null && ex instanceof IllegalAccessError) {
                        ctx.completeIteration();
                        return;
                    }
                    ctx.failIteration(new IllegalStateException());
                });
        host.send(get);
        ctx.await();
    }

    @Test
    public void testGetSecurityForCurrentUserAsCloudAdmin() throws GeneralSecurityException {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        SecurityContext context = getSecurityContext();
        assertTrue(context.roles.contains(AuthRole.CLOUD_ADMIN));
        assertTrue(context.roles.contains(AuthRole.BASIC_USER));
    }

    @Test
    public void testGetSecurityForCurrentUserAsBasicUser() throws GeneralSecurityException {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        SecurityContext context = getSecurityContext();
        assertTrue(!context.roles.contains(AuthRole.CLOUD_ADMIN));
        assertTrue(context.roles.contains(AuthRole.BASIC_USER));
    }

}
