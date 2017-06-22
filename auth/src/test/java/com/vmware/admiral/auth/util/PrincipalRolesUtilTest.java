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

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.idm.SecurityContext.ProjectEntry;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.test.TestContext;

public class PrincipalRolesUtilTest extends AuthBaseTest {

    @Test
    public void testGetDirectlyAssignedSystemRoles() throws GeneralSecurityException {
        // Verify for cloud admin.
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        DeferredResult<Set<AuthRole>> result = PrincipalRolesUtil
                .getDirectlyAssignedSystemRoles(host, USER_EMAIL_ADMIN);

        Set<AuthRole> roles = new HashSet<>();
        TestContext ctx = testCreate(1);
        result.whenComplete((rolesResult, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            roles.addAll(rolesResult);
            ctx.completeIteration();
        });
        ctx.await();

        assertTrue(roles.contains(AuthRole.CLOUD_ADMINS));
        assertTrue(roles.contains(AuthRole.BASIC_USERS));
        assertTrue(roles.contains(AuthRole.BASIC_USERS_EXTENDED));

        // Verify for basic user.
        result = PrincipalRolesUtil
                .getDirectlyAssignedSystemRoles(host, USER_EMAIL_BASIC_USER);
        Set<AuthRole> roles1 = new HashSet<>();
        TestContext ctx1 = testCreate(1);
        result.whenComplete((rolesResult, ex) -> {
            if (ex != null) {
                ctx1.failIteration(ex);
                return;
            }
            roles1.addAll(rolesResult);
            ctx1.completeIteration();
        });
        ctx1.await();

        assertTrue(roles1.contains(AuthRole.BASIC_USERS));
        assertTrue(roles1.contains(AuthRole.BASIC_USERS_EXTENDED));
    }

    @Test
    public void getDirectlyAssignedProjectRoles() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        ProjectState project = new ProjectState();
        project.name = "test";
        project.description = "test-description";
        project = doPost(project, ProjectFactoryService.SELF_LINK);
        assertNotNull(project.documentSelfLink);

        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = Collections.singletonList(USER_EMAIL_ADMIN);
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.members = roleAssignment;
        projectRoles.administrators = roleAssignment;
        doPatch(projectRoles, project.documentSelfLink);

        DeferredResult<List<ProjectEntry>> result = PrincipalRolesUtil
                .getDirectlyAssignedProjectRoles(host, USER_EMAIL_ADMIN);

        TestContext ctx = testCreate(1);
        List<ProjectEntry> entries = new ArrayList<>();
        result.whenComplete((resultEntries, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            entries.addAll(resultEntries);
            ctx.completeIteration();
        });
        ctx.await();

        assertEquals(1, entries.size());
        assertEquals(project.name, entries.get(0).name);
        assertEquals(project.documentSelfLink, entries.get(0).documentSelfLink);
        assertTrue(entries.get(0).roles.contains(AuthRole.PROJECT_ADMINS));
        assertTrue(entries.get(0).roles.contains(AuthRole.PROJECT_MEMBERS));
    }
}
