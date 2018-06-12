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

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.LogoutProvider;
import com.vmware.admiral.auth.idm.SessionService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class LocalLogoutProviderTest extends AuthBaseTest {

    private LogoutProvider provider = new LocalLogoutProvider();

    @Before
    public void injectHost() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        provider.init(host.startServiceAndWait(SessionService.class,
                SessionService.SELF_LINK + "-test"));
    }

    @Test
    public void testDoLogoutWithoutSession() {
        Operation op = Operation.createGet(UriUtils.buildUri("http://localhost/foo/bar"));

        provider.doLogout(op);

        TestContext ctx = testCreate(1);
        op.nestCompletion((o, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        assertEquals(Operation.STATUS_CODE_OK, op.getStatusCode());
        // String cookie = op.getResponseHeader(Operation.SET_COOKIE_HEADER);
        // assertTrue(cookie != null
        // && cookie.startsWith(AuthenticationConstants.REQUEST_AUTH_TOKEN_COOKIE));
    }

}
