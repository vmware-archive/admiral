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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.SecurityContext;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTemplate;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;

public class SecurityContextUtilTest extends AuthBaseTest {

    private static final String REQUESTOR_SERVICE_SELF_LINK = "/test/service/";

    private class TestService extends StatelessService {
        @Override
        public ServiceHost getHost() {
            return host;
        }
    }

    private Set<AuthRole> rolesAvailableToBasicUsers;
    private Set<AuthRole> rolesAvailableToCloudAdmin;
    private TestService requestorService;

    @Before
    public void init() {
        // init basic user roles
        rolesAvailableToBasicUsers = new HashSet<>(Arrays.asList(AuthRole.BASIC_USERS,
                AuthRole.BASIC_USERS_EXTENDED));
        rolesAvailableToCloudAdmin = new HashSet<>(rolesAvailableToBasicUsers);

        // init cloud admin roles
        rolesAvailableToCloudAdmin.add(AuthRole.CLOUD_ADMINS);

        // init requestor service
        host.addPrivilegedService(TestService.class);
        requestorService = new TestService();
        requestorService.setSelfLink(REQUESTOR_SERVICE_SELF_LINK);
    }

    @Test
    public void testBuildBasicUserInfo() throws Throwable {
        SecurityContext context;

        // cloud admin
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        context = QueryTemplate.waitToComplete(
                SecurityContextUtil.buildBasicUserInfo(requestorService, USER_EMAIL_ADMIN, null));

        assertEquals(USER_EMAIL_ADMIN, context.id);
        assertEquals(USER_EMAIL_ADMIN, context.email);
        assertEquals(USER_NAME_ADMIN, context.name);

        // basic user
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        context = QueryTemplate.waitToComplete(SecurityContextUtil
                .buildBasicUserInfo(requestorService, USER_EMAIL_BASIC_USER, null));

        assertEquals(USER_EMAIL_BASIC_USER, context.id);
        assertEquals(USER_EMAIL_BASIC_USER, context.email);
        assertEquals(USER_NAME_BASIC_USER, context.name);
    }

    @Test
    public void testBuildDirectSystemRoles() throws Throwable {
        SecurityContext context;

        // cloud admin
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        context = QueryTemplate.waitToComplete(SecurityContextUtil
                .buildDirectSystemRoles(requestorService, USER_EMAIL_ADMIN, null));
        assertNotNull(context.roles);
        assertEquals(rolesAvailableToCloudAdmin.size(), context.roles.size());
        assertTrue(context.roles.stream().allMatch(rolesAvailableToCloudAdmin::contains));

        // basic user
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        context = QueryTemplate.waitToComplete(SecurityContextUtil
                .buildDirectSystemRoles(requestorService, USER_EMAIL_BASIC_USER, null));
        assertNotNull(context.roles);
        assertEquals(rolesAvailableToBasicUsers.size(), context.roles.size());
        assertTrue(context.roles.stream().allMatch(rolesAvailableToBasicUsers::contains));
    }
}
