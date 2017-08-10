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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.SecurityContext;
import com.vmware.admiral.auth.idm.SecurityContext.ProjectEntry;
import com.vmware.admiral.auth.idm.local.LocalPrincipalFactoryService;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class SecurityContextUtilTest extends AuthBaseTest {

    private Set<AuthRole> rolesAvailableToBasicUsers;
    private Set<AuthRole> rolesAvailableToCloudAdmin;

    @Before
    public void init() {
        // init basic user roles
        rolesAvailableToBasicUsers = new HashSet<>(Arrays.asList(AuthRole.BASIC_USER,
                AuthRole.BASIC_USER_EXTENDED));
        rolesAvailableToCloudAdmin = new HashSet<>(rolesAvailableToBasicUsers);

        // init cloud admin roles
        rolesAvailableToCloudAdmin.add(AuthRole.CLOUD_ADMIN);
    }

    @Test
    public void testSecurityContextForCloudAdminAndBasicUser() throws GeneralSecurityException {
        Operation testOperationByAdmin = createAuthorizedOperation(
                host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN)));
        Operation testOperationByBasicUser = createAuthorizedOperation(
                host.assumeIdentity(buildUserServicePath(USER_EMAIL_BASIC_USER)));
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));

        // Verify for cloud admin.
        DeferredResult<SecurityContext> result = SecurityContextUtil.getSecurityContext(
                privilegedTestService, testOperationByAdmin);

        final SecurityContext[] context = new SecurityContext[1];
        TestContext ctx = testCreate(1);
        result.whenComplete((securityContext, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            context[0] = securityContext;
            ctx.completeIteration();
        });
        ctx.await();

        assertEquals(3, context[0].roles.size());
        assertTrue(context[0].roles.contains(AuthRole.CLOUD_ADMIN));
        assertTrue(context[0].roles.contains(AuthRole.BASIC_USER));
        assertTrue(context[0].roles.contains(AuthRole.BASIC_USER_EXTENDED));

        // Verify for basic user.
        result = SecurityContextUtil.getSecurityContext(privilegedTestService,
                testOperationByBasicUser);
        TestContext ctx1 = testCreate(1);
        result.whenComplete((securityContext, ex) -> {
            if (ex != null) {
                ctx1.failIteration(ex);
                return;
            }
            context[0] = securityContext;
            ctx1.completeIteration();
        });
        ctx1.await();

        assertEquals(2, context[0].roles.size());
        assertTrue(context[0].roles.contains(AuthRole.BASIC_USER));
        assertTrue(context[0].roles.contains(AuthRole.BASIC_USER_EXTENDED));
    }

    @Test
    public void testSecurityContextContainsDirectlyAssignedProjectRoles() throws Throwable {
        Operation testOperationByAdmin = createAuthorizedOperation(
                host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN)));

        ProjectState project = new ProjectState();
        project.name = "test";
        project.description = "test-description";
        project = doPost(project, ProjectFactoryService.SELF_LINK);
        assertNotNull(project.documentSelfLink);

        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = Collections.singletonList(USER_EMAIL_ADMIN);
        ProjectRoles projectRoles = new ProjectRoles();
        projectRoles.viewers = roleAssignment;
        projectRoles.members = roleAssignment;
        projectRoles.administrators = roleAssignment;
        doPatch(projectRoles, project.documentSelfLink);

        DeferredResult<SecurityContext> result = SecurityContextUtil.getSecurityContext(
                privilegedTestService, testOperationByAdmin);

        final SecurityContext[] context = new SecurityContext[1];
        TestContext ctx = testCreate(1);
        result.whenComplete((securityContext, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            context[0] = securityContext;
            ctx.completeIteration();
        });
        ctx.await();

        assertEquals(1, context[0].projects.size());
        assertEquals(project.name, context[0].projects.get(0).name);
        assertEquals(project.documentSelfLink, context[0].projects.get(0).documentSelfLink);
        assertTrue(context[0].projects.get(0).roles.contains(AuthRole.PROJECT_ADMIN));
        assertTrue(context[0].projects.get(0).roles.contains(AuthRole.PROJECT_MEMBER));
        assertTrue(context[0].projects.get(0).roles.contains(AuthRole.PROJECT_MEMBER_EXTENDED));
        assertTrue(context[0].projects.get(0).roles.contains(AuthRole.PROJECT_VIEWER));
    }

    @Test
    public void testSecurityContextContainsAllRolesForMultipleProjects() throws Throwable {
        Operation testOperationByAdmin = createAuthorizedOperation(
                host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN)));
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));

        // Scenario: create 2 projects, assign fritz as project admin in 1st and as project
        // member in 2nd project.

        // Create first project and assign fritz as project admin.
        ProjectState firstProject = createProject("first-project");
        assertNotNull(firstProject.documentSelfLink);
        ProjectRoles projectRoles = new ProjectRoles();
        PrincipalRoleAssignment admins = new PrincipalRoleAssignment();
        admins.add = Collections.singletonList(USER_EMAIL_ADMIN);
        projectRoles.administrators = admins;
        doPatch(projectRoles, firstProject.documentSelfLink);

        // Create second project and assign fritz as project member.
        ProjectState secondProject = createProject("second-project");
        assertNotNull(secondProject.documentSelfLink);
        projectRoles = new ProjectRoles();
        PrincipalRoleAssignment members = new PrincipalRoleAssignment();
        members.add = Collections.singletonList(USER_EMAIL_ADMIN);
        projectRoles.members = members;
        doPatch(projectRoles, secondProject.documentSelfLink);

        DeferredResult<SecurityContext> result = SecurityContextUtil.getSecurityContext(
                privilegedTestService, testOperationByAdmin);

        final SecurityContext[] context = new SecurityContext[1];
        TestContext ctx = testCreate(1);
        result.whenComplete((securityContext, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            context[0] = securityContext;
            ctx.completeIteration();
        });
        ctx.await();

        SecurityContext securityContext = context[0];

        assertNotNull(securityContext.email);
        assertNotNull(securityContext.id);
        assertNotNull(securityContext.name);
        assertNotNull(securityContext.roles);
        assertNotNull(securityContext.projects);

        assertTrue(securityContext.roles.contains(AuthRole.CLOUD_ADMIN));
        assertTrue(securityContext.roles.contains(AuthRole.BASIC_USER));
        assertTrue(securityContext.roles.contains(AuthRole.BASIC_USER_EXTENDED));

        assertEquals(2, securityContext.projects.size());

        ProjectEntry firstProjectEntry;
        ProjectEntry secondProjectEntry;

        if (securityContext.projects.get(0).name.equalsIgnoreCase(firstProject.name)) {
            firstProjectEntry = securityContext.projects.get(0);
            secondProjectEntry = securityContext.projects.get(1);
        } else {
            firstProjectEntry = securityContext.projects.get(1);
            secondProjectEntry = securityContext.projects.get(0);
        }

        assertEquals(firstProject.name, firstProjectEntry.name);
        assertEquals(firstProject.documentSelfLink, firstProjectEntry.documentSelfLink);
        assertEquals(1, firstProjectEntry.roles.size());
        assertTrue(firstProjectEntry.roles.contains(AuthRole.PROJECT_ADMIN));

        assertEquals(secondProject.name, secondProjectEntry.name);
        assertEquals(secondProject.documentSelfLink, secondProjectEntry.documentSelfLink);
        assertEquals(2, secondProjectEntry.roles.size());
        assertTrue(secondProjectEntry.roles.contains(AuthRole.PROJECT_MEMBER));
    }

    @Test
    public void testSecurityContextContainsIndirectAssignedRoles() throws Throwable {
        Operation testOperationByBasicUser = createAuthorizedOperation(
                host.assumeIdentity(buildUserServicePath(USER_EMAIL_CONNIE)));
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN2));

        // Scenario: create a group which will contain Connie which is basic user and the group
        // will be assigned to cloud admins. Create nested groups and add Connie in them, assign
        // the nested groups to project roles. Verify that PrincipalRoles for Connie contains all
        // roles where he is assigned indirectly.

        // root is the group where Connie belongs and we assign the group to cloud admins role.
        LocalPrincipalState root = new LocalPrincipalState();
        root.type = LocalPrincipalType.GROUP;
        root.name = "root";
        root.groupMembersLinks = Collections.singletonList(UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, USER_EMAIL_CONNIE));
        root = doPost(root, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(root.documentSelfLink);

        // nestedGroup1 is the group where Connie belongs but we will add nestedGroup1 to
        // nestedGroup2 and we will indirectly assign roles to Connie as we assign a role to
        // nestedGroup2.
        LocalPrincipalState nestedGroup1 = new LocalPrincipalState();
        nestedGroup1.type = LocalPrincipalType.GROUP;
        nestedGroup1.name = "nestedGroup1";
        nestedGroup1.groupMembersLinks = Collections.singletonList(UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, USER_EMAIL_CONNIE));
        nestedGroup1 = doPost(nestedGroup1, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(nestedGroup1.documentSelfLink);

        // nestedGroup2 is the group which contains nestedGroup1
        LocalPrincipalState nestedGroup2 = new LocalPrincipalState();
        nestedGroup2.type = LocalPrincipalType.GROUP;
        nestedGroup2.name = "nestedGroup2";
        nestedGroup2.groupMembersLinks = Collections.singletonList(nestedGroup1.documentSelfLink);
        nestedGroup2 = doPost(nestedGroup2, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(nestedGroup2.documentSelfLink);

        // assign cloud admins role to root user group.
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = Collections.singletonList(AuthRole.CLOUD_ADMIN.name());
        doPatch(roleAssignment, UriUtils.buildUriPath(PrincipalService.SELF_LINK, root.id,
                PrincipalService.ROLES_SUFFIX));

        // Create first project and assign nestedGroup1 as project admin.
        ProjectState firstProject = createProject("first-project");
        assertNotNull(firstProject.documentSelfLink);
        ProjectRoles projectRoles = new ProjectRoles();
        PrincipalRoleAssignment admins = new PrincipalRoleAssignment();
        admins.add = Collections.singletonList(nestedGroup1.id);
        projectRoles.administrators = admins;
        doPatch(projectRoles, firstProject.documentSelfLink);

        // Create second project and assign nestedGroup2 as project member.
        ProjectState secondProject = createProject("second-project");
        assertNotNull(secondProject.documentSelfLink);
        projectRoles = new ProjectRoles();
        PrincipalRoleAssignment members = new PrincipalRoleAssignment();
        members.add = Collections.singletonList(nestedGroup2.id);
        projectRoles.members = members;
        doPatch(projectRoles, secondProject.documentSelfLink);

        DeferredResult<SecurityContext> result = SecurityContextUtil.getSecurityContext(
                privilegedTestService, testOperationByBasicUser);

        final SecurityContext[] context = new SecurityContext[1];
        TestContext ctx = testCreate(1);
        result.whenComplete((securityContext, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            context[0] = securityContext;
            ctx.completeIteration();
        });
        ctx.await();

        SecurityContext securityContext = context[0];

        assertNotNull(securityContext.email);
        assertNotNull(securityContext.id);
        assertNotNull(securityContext.name);
        assertNotNull(securityContext.roles);
        assertNotNull(securityContext.projects);

        assertTrue(securityContext.roles.contains(AuthRole.CLOUD_ADMIN));
        assertTrue(securityContext.roles.contains(AuthRole.BASIC_USER));
        assertTrue(securityContext.roles.contains(AuthRole.BASIC_USER_EXTENDED));

        assertEquals(2, securityContext.projects.size());

        ProjectEntry firstProjectEntry;
        ProjectEntry secondProjectEntry;

        if (securityContext.projects.get(0).name.equalsIgnoreCase(firstProject.name)) {
            firstProjectEntry = securityContext.projects.get(0);
            secondProjectEntry = securityContext.projects.get(1);
        } else {
            firstProjectEntry = securityContext.projects.get(1);
            secondProjectEntry = securityContext.projects.get(0);
        }

        assertEquals(firstProject.name, firstProjectEntry.name);
        assertEquals(firstProject.documentSelfLink, firstProjectEntry.documentSelfLink);
        assertEquals(1, firstProjectEntry.roles.size());
        assertTrue(firstProjectEntry.roles.contains(AuthRole.PROJECT_ADMIN));

        assertEquals(secondProject.name, secondProjectEntry.name);
        assertEquals(secondProject.documentSelfLink, secondProjectEntry.documentSelfLink);
        assertEquals(2, secondProjectEntry.roles.size());
        assertTrue(secondProjectEntry.roles.contains(AuthRole.PROJECT_MEMBER));
    }
}
