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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.SetUtils;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.SecurityContext;
import com.vmware.admiral.auth.idm.SecurityContext.ProjectEntry;
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
        context = QueryTemplate.waitToComplete(SecurityContextUtil
                .buildBasicUserInfo(requestorService, USER_EMAIL_ADMIN, null)).context;

        assertEquals(USER_EMAIL_ADMIN, context.id);
        assertEquals(USER_EMAIL_ADMIN, context.email);
        assertEquals(USER_NAME_ADMIN, context.name);

        // basic user
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        context = QueryTemplate.waitToComplete(getSecurityContextFromSessionService());

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
                .buildDirectSystemRoles(requestorService, USER_EMAIL_ADMIN, null)).context;
        assertNotNull(context.roles);
        assertEquals(rolesAvailableToCloudAdmin.size(), context.roles.size());
        assertTrue(context.roles.stream().allMatch(rolesAvailableToCloudAdmin::contains));

        // basic user
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        context = QueryTemplate.waitToComplete(getSecurityContextFromSessionService());
        assertNotNull(context.roles);
        assertEquals(rolesAvailableToBasicUsers.size(), context.roles.size());
        assertTrue(context.roles.stream().allMatch(rolesAvailableToBasicUsers::contains));
    }

    @Test
    public void testBuildBasicProjectInfo() throws Throwable {
        // load auth content for the test

        // do not assume the identity of the user that we are going to look for - currently he will
        // create all projects and will therefore be added to all of them as both user and admin
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_GLORIA));
        loadAuthContent(FILE_AUTH_CONTENT_PROJECTS_ONLY);

        SecurityContext context;

        // cloud admin
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        HashMap<String, Set<AuthRole>> adminProjectRoles = new HashMap<>();
        adminProjectRoles.put(PROJECT_NAME_TEST_PROJECT_1,
                Collections.singleton(AuthRole.PROJECT_ADMINS));
        adminProjectRoles.put(PROJECT_NAME_TEST_PROJECT_2,
                Collections.singleton(AuthRole.PROJECT_ADMINS));
        context = QueryTemplate.waitToComplete(SecurityContextUtil
                .buildBasicProjectInfo(requestorService, USER_EMAIL_ADMIN, null)).context;
        assertProjectRolesMatch(context.projects, adminProjectRoles);


        // This check is currently disabled, because the project roles and resource groups are
        // not implemented yet, and in order to make it work some things should be hacked, but
        // when there is actual implementation this should work out of the box.
        // cloud basic users
        // host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER));
        // HashMap<String, Set<AuthRole>> userProjectRoles = new HashMap<>();
        // userProjectRoles.put(PROJECT_NAME_TEST_PROJECT_1,
        //         Collections.singleton(AuthRole.PROJECT_MEMBERS));
        // userProjectRoles.put(PROJECT_NAME_TEST_PROJECT_3,
        //         Collections.singleton(AuthRole.PROJECT_ADMINS));
        // context = QueryTemplate.waitToComplete(getSecurityContextFromSessionService());
        // assertProjectRolesMatch(context.projects, userProjectRoles);
    }

    private void assertProjectRolesMatch(List<ProjectEntry> projects,
            Map<String, Set<AuthRole>> expectedProjectRoles) {
        assertNotNull(projects);
        assertNotNull(expectedProjectRoles);
        assertEquals("Unexpected number of projects", expectedProjectRoles.size(), projects.size());

        projects.stream().forEach((project) -> {
            Set<AuthRole> roles = expectedProjectRoles.get(project.name);
            if (roles == null) {
                throw new IllegalArgumentException(String.format(
                        "Security context contains project %s but it was not expected",
                        project.name));
            }

            assertEquals(String.format("Unexpected number of roles for project %s", project.name),
                    roles.size(), project.roles.size());
            if (!SetUtils.isEqualSet(roles, project.roles)) {
                throw new IllegalStateException(
                        String.format("Incorrect roles: %s. Expected: %s.", project.roles, roles));
            }
        });
    }
}
