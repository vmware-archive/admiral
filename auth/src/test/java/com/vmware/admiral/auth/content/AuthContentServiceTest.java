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

package com.vmware.admiral.auth.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.auth.util.AuthUtil.CLOUD_ADMINS_USER_GROUP_LINK;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.content.AuthContentService;
import com.vmware.admiral.auth.idm.content.AuthContentService.AuthContentBody;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserService.UserState;

public class AuthContentServiceTest extends AuthBaseTest {
    private String projectOnlyContent;
    private String authContent;

    @Before
    public void setup() throws GeneralSecurityException, IOException {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        projectOnlyContent = new BufferedReader(new InputStreamReader(getClass().getClassLoader()
                .getResourceAsStream("content-projects-only.json")))
                .lines().collect(Collectors.joining("\n"));

        authContent = new BufferedReader(new InputStreamReader(getClass().getClassLoader()
                .getResourceAsStream("auth-content.json")))
                .lines().collect(Collectors.joining("\n"));

    }

    @Test
    public void testImportContentWithProjectsOnly() throws Throwable {
        AuthContentBody body = Utils.fromJson(projectOnlyContent, AuthContentBody.class);
        TestContext ctx = testCreate(1);
        host.send(Operation.createPost(host, AuthContentService.SELF_LINK)
                .setBody(projectOnlyContent)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                    } else {
                        ctx.completeIteration();
                    }
                }));
        ctx.await();

        List<String> projectLinks = getDocumentLinksOfType(ProjectState.class);
        projectLinks.remove("/projects/default-project");

        assertEquals(body.projects.size(), projectLinks.size());

        List<String> projectToImportNames = body.projects.stream()
                .map(p -> p.name)
                .collect(Collectors.toList());

        for (String link : projectLinks) {
            ProjectState state = getDocument(ProjectState.class, link);
            assertTrue(projectToImportNames.contains(state.name));
        }
    }

    @Test
    public void testImportContentWithProjectAndUsers() throws Throwable {
        AuthContentBody body = Utils.fromJson(authContent, AuthContentBody.class);
        TestContext ctx = testCreate(1);
        host.send(Operation.createPost(host, AuthContentService.SELF_LINK)
                .setBody(authContent)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                    } else {
                        ctx.completeIteration();
                    }
                }));
        ctx.await();

        List<String> projectLinks = getDocumentLinksOfType(ProjectState.class);
        projectLinks.remove("/projects/default-project");

        assertEquals(body.projects.size(), projectLinks.size());

        List<String> projectToImportNames = body.projects.stream()
                .map(p -> p.name)
                .collect(Collectors.toList());

        for (String link : projectLinks) {
            ProjectState state = getDocument(ProjectState.class, link);
            assertTrue(projectToImportNames.contains(state.name));
        }

        // Verify Tony is added in cloud admins and Connie is removed
        UserState tonyState = getDocument(UserState.class,
                buildUserServicePath(USER_EMAIL_BASIC_USER));

        UserState connieState = getDocument(UserState.class,
                buildUserServicePath(USER_EMAIL_CONNIE));

        assertNotNull(tonyState);
        assertNotNull(connieState);

        assertTrue(tonyState.userGroupLinks.contains(CLOUD_ADMINS_USER_GROUP_LINK));
        assertTrue(!connieState.userGroupLinks.contains(CLOUD_ADMINS_USER_GROUP_LINK));
    }

    @Test
    public void testImportContentToAssignAndUnassignGroups() throws Throwable {
        // First assign developers to cloud admins role, because
        // from config file will we will unassign them.
        String developers = "developers";
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = new ArrayList<>();
        roleAssignment.add.add(AuthRole.CLOUD_ADMINS.getName());
        doPatch(roleAssignment, UriUtils.buildUriPath(PrincipalService.SELF_LINK, developers,
                PrincipalService.ROLES_SUFFIX));

        RoleState roleState = getDocument(RoleState.class, UriUtils.buildUriPath(RoleService
                .FACTORY_LINK, AuthRole.CLOUD_ADMINS.buildRoleWithSuffix(developers)));
        assertNotNull(roleState);

        // Import content
        AuthContentBody body = Utils.fromJson(authContent, AuthContentBody.class);
        TestContext ctx = testCreate(1);
        host.send(Operation.createPost(host, AuthContentService.SELF_LINK)
                .setBody(body)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                    } else {
                        ctx.completeIteration();
                    }
                }));
        ctx.await();

        // Verify Tony is added in cloud admins and Connie is removed
        UserState tonyState = getDocument(UserState.class,
                buildUserServicePath(USER_EMAIL_BASIC_USER));

        UserState connieState = getDocument(UserState.class,
                buildUserServicePath(USER_EMAIL_CONNIE));

        assertNotNull(tonyState);
        assertNotNull(connieState);

        assertTrue(tonyState.userGroupLinks.contains(CLOUD_ADMINS_USER_GROUP_LINK));
        assertTrue(!connieState.userGroupLinks.contains(CLOUD_ADMINS_USER_GROUP_LINK));

        // Verify superusers are cloud admins.
        String superusers = "superusers";
        roleState = getDocument(RoleState.class, UriUtils.buildUriPath(RoleService
                .FACTORY_LINK, AuthRole.CLOUD_ADMINS.buildRoleWithSuffix(superusers)));
        assertNotNull(roleState);

        // Verify developers are unassigned from cloud admins.
        TestContext ctx1 = testCreate(1);
        host.send(Operation.createGet(host, UriUtils.buildUriPath(RoleService
                .FACTORY_LINK, AuthRole.CLOUD_ADMINS.buildRoleWithSuffix(developers)))
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (ex instanceof ServiceNotFoundException) {
                            ctx1.completeIteration();
                            return;
                        }
                        ctx1.failIteration(ex);
                        return;
                    }
                    ctx1.failIteration(
                            new RuntimeException("Superusers should've been unassigned from "
                                    + "cloud admins."));
                }));
        ctx1.await();
    }
}
