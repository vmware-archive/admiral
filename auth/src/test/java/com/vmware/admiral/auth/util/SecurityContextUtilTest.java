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

package com.vmware.admiral.auth.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.SecurityContext;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTemplate;

public class SecurityContextUtilTest extends AuthBaseTest {

    @Test
    public void testBuildBasicUserInfo() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        SecurityContext context = new SecurityContext();

        QueryTemplate.waitToComplete(SecurityContextUtil.buildBasicUserInfo(host,
                host.getUri().toString(), USER_EMAIL_ADMIN, context));

        assertEquals(USER_EMAIL_ADMIN, context.id);
        assertEquals(USER_EMAIL_ADMIN, context.email);
        assertEquals(USER_NAME_ADMIN, context.name);
    }
}
