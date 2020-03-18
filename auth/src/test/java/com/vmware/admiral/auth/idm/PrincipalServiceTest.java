/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.auth.idm;

import static com.vmware.admiral.auth.util.PrincipalUtil.encode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.admiral.auth.idm.SecurityContext.ProjectEntry;
import com.vmware.admiral.auth.idm.SecurityContext.SecurityContextPostDto;
import com.vmware.admiral.auth.idm.local.LocalPrincipalFactoryService;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.service.common.harbor.Harbor;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;

public class PrincipalServiceTest extends AuthBaseTest {

    @Before
    public void setIdentity() throws GeneralSecurityException {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
    }

    @Test
    public void testGetPrincipalWithValidInput() {
        Principal admin = testRequest(Operation::createGet,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, USER_EMAIL_ADMIN), false, null,
                Principal.class);
        assertNotNull(admin);
        assertEquals(USER_EMAIL_ADMIN, admin.id);

        Principal connie = testRequest(Operation::createGet,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, USER_EMAIL_CONNIE), false, null,
                Principal.class);
        assertNotNull(connie);
        assertEquals(USER_EMAIL_CONNIE, connie.id);
        assertTrue(connie.groups.contains(USER_GROUP_DEVELOPERS));
    }

    @Test
    public void testGetPrincipalWithInvalidInput() {
        // Test with empty principal id.
        testRequest(Operation::createGet, PrincipalService.SELF_LINK, true,
                new IllegalStateException(
                        "Expected exception != null when searching for principal with empty id."),
                null);

        // Test with non present principal id.
        testRequest(Operation::createGet,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, "no-such-user"), true,
                new IllegalStateException(
                        "Expected exception != null when searching for non present principal."),
                null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetPrincipalsWithValidInput() {
        // match a single user
        ArrayList<Principal> principals = testRequest(Operation::createGet,
                String.format("%s/?%s=%s", PrincipalService.SELF_LINK,
                        PrincipalService.CRITERIA_QUERY, USER_EMAIL_ADMIN),
                false, null, ArrayList.class);
        assertEquals(1, principals.size());
        assertEquals(USER_EMAIL_ADMIN, principals.iterator().next().id);
        assertTrue(principals.iterator().next().groups.contains(USER_GROUP_SUPERUSERS));

        // match multiple users
        principals = testRequest(Operation::createGet, String.format("%s/?%s=%s",
                PrincipalService.SELF_LINK, PrincipalService.CRITERIA_QUERY, "i"), false, null,
                ArrayList.class);
        List<String> expectedPrincipals = Arrays.asList(USER_EMAIL_ADMIN, USER_EMAIL_BASIC_USER,
                USER_EMAIL_GLORIA, USER_EMAIL_CONNIE, USER_EMAIL_ADMIN2, USER_GROUP_SUPERUSERS,
                USER_GROUP_DEVELOPERS, USER_EMAIL_CLOUD_ADMIN, USER_EMAIL_PROJECT_ADMIN_1,
                USER_EMAIL_PROJECT_MEMBER_1, USER_EMAIL_PROJECT_VIEWER_1,
                USER_EMAIL_BASIC_UNASSIGNED_USER);
        assertEquals(expectedPrincipals.size(), principals.size());
        assertTrue(USER_EMAIL_ADMIN, principals.stream().allMatch((principal) -> {
            return expectedPrincipals.contains(principal.id);
        }));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetPrincipalsWithInvalidInput() {

        // Test with empty criteria
        testRequest(Operation::createGet,
                String.format("%s/?%s=", PrincipalService.SELF_LINK,
                        PrincipalService.CRITERIA_QUERY),
                true,
                new IllegalStateException(
                        "Expected exception != null when searching for principal with empty criteria."),
                null);

        // Test with missing user
        ArrayList<Principal> principals = testRequest(Operation::createGet,
                String.format("%s/?%s=%s", PrincipalService.SELF_LINK,
                        PrincipalService.CRITERIA_QUERY, "no-such-user"),
                false, null, ArrayList.class);
        assertEquals(0, principals.size());
    }

    @Test
    public void testGetSecurityContextShouldPass() throws GeneralSecurityException {
        // Assume the identity of admin, because basic user should not be able to use
        // PrincipalService and get data for other users.
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        SecurityContext securityContext = testRequest(Operation::createGet,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, USER_EMAIL_ADMIN,
                        PrincipalService.SECURITY_CONTEXT_SUFFIX),
                false, null, SecurityContext.class);
        assertEquals(USER_EMAIL_ADMIN, securityContext.id);
    }

    @Test
    public void testGetSecurityContextShouldFail() {
        testRequest(Operation::createGet,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, "no-such-user",
                        PrincipalService.SECURITY_CONTEXT_SUFFIX),
                true,
                new IllegalStateException(
                        "Expected exception != null when retrieving security context for a missing user"),
                null);
    }

    private <T> T testRequest(BiFunction<ServiceHost, String, Operation> opFunction,
            String requestPath, boolean expectFailure, Throwable throwOnPass,
            Class<T> resultClass) {
        ArrayList<T> result = new ArrayList<>(1);

        Operation op = opFunction.apply(host, requestPath)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (expectFailure) {
                            host.completeIteration();
                        } else {
                            host.failIteration(ex);
                        }
                    } else {
                        if (expectFailure) {
                            host.failIteration(throwOnPass != null ? throwOnPass
                                    : new IllegalArgumentException(String.format(
                                            "Request to %s was expected to fail but passed",
                                            requestPath)));
                        } else {
                            try {
                                result.add(o.getBody(resultClass));
                                host.completeIteration();
                            } catch (Throwable er) {
                                host.failIteration(er);
                            }
                        }
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();

        if (expectFailure) {
            return null;
        } else {
            assertEquals(String.format("Failed to retrieve response body of class %s",
                    resultClass.getName()), 1, result.size());
            return result.iterator().next();
        }
    }

    @Test
    public void testAssignAndUnassignExistingGroupsToRole() throws Throwable {
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = new ArrayList<>();
        roleAssignment.add.add(AuthRole.CLOUD_ADMIN.name());

        String uri = UriUtils.buildUriPath(PrincipalService.SELF_LINK, USER_GROUP_SUPERUSERS,
                "roles");
        // Assingn superusers to cloud admins
        doPatch(roleAssignment, uri);

        // Verify superusers got assigned and required roles are created.
        String superusersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.CLOUD_ADMIN.buildRoleWithSuffix(encode(USER_GROUP_SUPERUSERS)));

        RoleState roleState = getDocument(RoleState.class, superusersRoleLink);
        assertNotNull(roleState);
        assertEquals(superusersRoleLink, roleState.documentSelfLink);

        // Unassign superusers from cloud admins
        roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.remove = new ArrayList<>();
        roleAssignment.remove.add(AuthRole.CLOUD_ADMIN.name());
        doPatch(roleAssignment, uri);

        // Verify superusers got unassigned and required roles are deleted
        TestContext ctx = testCreate(1);
        Operation getSuperusersRole = Operation.createGet(host, superusersRoleLink)
                .setReferer(host.getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        if (ex instanceof ServiceNotFoundException) {
                            ctx.completeIteration();
                            return;
                        }
                        ctx.failIteration(ex);
                        return;
                    }
                    ctx.failIteration(new RuntimeException("After unassign user group, role "
                            + "should be deleted."));
                });
        host.send(getSuperusersRole);
        ctx.await();
    }

    @Test
    public void testNestedGetGroupsForPrincipal() throws Throwable {
        LocalPrincipalState itGroup = new LocalPrincipalState();
        itGroup.name = "it@admiral.com";
        itGroup.type = LocalPrincipalType.GROUP;
        itGroup.groupMembersLinks = Collections.singletonList(UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, encode(USER_GROUP_SUPERUSERS)));
        itGroup = doPost(itGroup, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(itGroup);

        LocalPrincipalState organization = new LocalPrincipalState();
        organization.name = "organization@admiral.com";
        organization.type = LocalPrincipalType.GROUP;
        organization.groupMembersLinks = Collections.singletonList(UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, encode("it@admiral.com")));
        organization = doPost(organization, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(organization);

        // fritz is part of "superusers" which is part of "it", and "it" is part of "organization"
        // verify when get groups for fritz "superusers", "it" and "organization is returned"
        TestContext ctx = testCreate(1);
        Set<String> groups = new HashSet<>();
        host.send(Operation.createGet(host, UriUtils.buildUriPath(PrincipalService.SELF_LINK,
                USER_EMAIL_ADMIN))
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                        return;
                    }
                    groups.addAll(o.getBody(Principal.class).groups);
                    ctx.completeIteration();
                }));
        ctx.await();

        assertTrue(groups.contains(USER_GROUP_SUPERUSERS));
        assertTrue(groups.contains("it@admiral.com"));
        assertTrue(groups.contains("organization@admiral.com"));
    }

    @Test
    public void testSimpleGetGroupsForPrincipal() throws Throwable {
        TestContext ctx = testCreate(1);
        Set<String> groups = new HashSet<>();
        host.send(Operation.createGet(host, UriUtils.buildUriPath(PrincipalService.SELF_LINK,
                USER_EMAIL_ADMIN))
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                        return;
                    }
                    groups.addAll(o.getBody(Principal.class).groups);
                    ctx.completeIteration();
                }));
        ctx.await();

        assertTrue(groups.contains("superusers@admiral.com"));

    }

    @Test
    public void getRolesForPrincipal() throws Throwable {
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
        projectRoles.viewers = roleAssignment;
        doPatch(projectRoles, project.documentSelfLink);

        PrincipalRoles roles = getDocumentNoWait(PrincipalRoles.class, UriUtils.buildUriPath(
                PrincipalService.SELF_LINK, USER_EMAIL_ADMIN, PrincipalService.ROLES_SUFFIX));

        assertTrue(roles.roles.contains(AuthRole.CLOUD_ADMIN));
        assertTrue(roles.roles.contains(AuthRole.BASIC_USER));
        assertTrue(roles.roles.contains(AuthRole.BASIC_USER_EXTENDED));

        assertEquals(1, roles.projects.size());
        assertEquals(project.documentSelfLink, roles.projects.get(0).documentSelfLink);
        assertEquals(project.name, roles.projects.get(0).name);
        assertTrue(roles.projects.get(0).roles.contains(AuthRole.PROJECT_ADMIN));
        assertTrue(roles.projects.get(0).roles.contains(AuthRole.PROJECT_MEMBER));
        assertTrue(roles.projects.get(0).roles.contains(AuthRole.PROJECT_VIEWER));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetAllRolesForPrincipalWithIndirectRoles() throws Throwable {
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
                LocalPrincipalFactoryService.SELF_LINK, encode(USER_EMAIL_CONNIE)));
        root = doPost(root, LocalPrincipalFactoryService.SELF_LINK);
        assertNotNull(root.documentSelfLink);

        // nestedGroup1 is the group where Connie belongs but we will add nestedGroup1 to
        // nestedGroup2 and we will indirectly assign roles to Connie as we assign a role to
        // nestedGroup2.
        LocalPrincipalState nestedGroup1 = new LocalPrincipalState();
        nestedGroup1.type = LocalPrincipalType.GROUP;
        nestedGroup1.name = "nestedGroup1";
        nestedGroup1.groupMembersLinks = Collections.singletonList(UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, encode(USER_EMAIL_CONNIE)));
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

        URI uri = UriUtils.buildUri(host, PrincipalService.SELF_LINK);
        uri = UriUtils.extendUriWithQuery(uri, PrincipalService.CRITERIA_QUERY, "connie",
                PrincipalService.ROLES_QUERY, PrincipalService.ROLES_QUERY_VALUE);

        List<PrincipalRoles> resultRoles = new ArrayList<>();

        TestContext ctx = testCreate(1);

        Operation getRoles = Operation
                .createGet(uri)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                        return;
                    }
                    resultRoles.addAll(o.getBody(ArrayList.class));
                    ctx.completeIteration();
                });

        host.send(getRoles);

        ctx.await();

        assertEquals(1, resultRoles.size());
        PrincipalRoles connieRoles = resultRoles.get(0);

        assertNotNull(connieRoles.email);
        assertNotNull(connieRoles.id);
        assertNotNull(connieRoles.name);
        assertNotNull(connieRoles.type);
        assertNotNull(connieRoles.roles);
        assertNotNull(connieRoles.projects);

        assertTrue(connieRoles.roles.contains(AuthRole.CLOUD_ADMIN));
        assertTrue(connieRoles.roles.contains(AuthRole.BASIC_USER));
        assertTrue(connieRoles.roles.contains(AuthRole.BASIC_USER_EXTENDED));

        assertEquals(2, connieRoles.projects.size());

        ProjectEntry firstProjectEntry;
        ProjectEntry secondProjectEntry;

        if (connieRoles.projects.get(0).name.equalsIgnoreCase(firstProject.name)) {
            firstProjectEntry = connieRoles.projects.get(0);
            secondProjectEntry = connieRoles.projects.get(1);
        } else {
            firstProjectEntry = connieRoles.projects.get(1);
            secondProjectEntry = connieRoles.projects.get(0);
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
    public void testGetRolesForPrincipalOfTypeGroup() throws Throwable {
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = Collections.singletonList(AuthRole.CLOUD_ADMIN.name());
        doPatch(roleAssignment, UriUtils.buildUriPath(PrincipalService.SELF_LINK,
                USER_GROUP_SUPERUSERS, PrincipalService.ROLES_SUFFIX));

        ProjectState projectState = new ProjectState();
        projectState.name = "test";
        projectState = doPost(projectState, ProjectFactoryService.SELF_LINK);

        ProjectRoles roles = new ProjectRoles();
        roles.administrators = new PrincipalRoleAssignment();
        roles.administrators.add = Collections.singletonList(USER_GROUP_SUPERUSERS);
        doPatch(roles, projectState.documentSelfLink);

        SecurityContext contextById = getDocumentNoWait(SecurityContext.class,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, USER_GROUP_SUPERUSERS,
                        PrincipalService.ROLES_SUFFIX));

        assertTrue(contextById.name.equals(USER_GROUP_SUPERUSERS));
        assertTrue(contextById.roles.contains(AuthRole.CLOUD_ADMIN));
        assertTrue(contextById.projects.size() == 1);
        assertTrue(contextById.projects.get(0).roles.contains(AuthRole.PROJECT_ADMIN));

        String uriString = UriUtils.buildUriPath(PrincipalService.SELF_LINK);
        URI uri = UriUtils.buildUri(uriString);
        uri = UriUtils.extendUriWithQuery(uri, PrincipalService.CRITERIA_QUERY,
                USER_GROUP_SUPERUSERS, PrincipalService.ROLES_QUERY,
                PrincipalService.ROLES_QUERY_VALUE);

        PrincipalRoles[] principalRoles = getDocumentNoWait(PrincipalRoles[].class,
                uri.toString());

        assertTrue(principalRoles.length == 1);
    }

    @Test
    public void testGetSecurityContextWithIdAndPassword() {
        SecurityContext securityContext = getSecurityContextByCredentials(USER_EMAIL_GLORIA,
                "Password1!");

        assertEquals(USER_NAME_GLORIA, securityContext.name);
        assertEquals(USER_EMAIL_GLORIA, securityContext.id);
        assertTrue(securityContext.roles.contains(AuthRole.BASIC_USER));
        assertTrue(securityContext.roles.contains(AuthRole.BASIC_USER_EXTENDED));
    }

    @Test
    public void testGetSecurityContextWithIdAndInvalidPassword() {
        SecurityContextPostDto dto = new SecurityContextPostDto();
        dto.password = "invalid";

        TestContext ctx = testCreate(1);
        Operation post = Operation
                .createPost(host, UriUtils.buildUriPath(PrincipalService.SELF_LINK,
                        USER_EMAIL_GLORIA, PrincipalService.SECURITY_CONTEXT_SUFFIX))
                .setBody(dto)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.completeIteration();
                        return;
                    }
                    ctx.failIteration(new IllegalStateException("Getting security context with "
                            + "invalid password should've failed."));
                });
        host.send(post);
        ctx.await();
    }

    @Test
    public void testGetSecurityContextWithDefaultHarborRegistryCredentials() throws Throwable {

        AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
        credentials.userEmail = UUID.randomUUID().toString();
        credentials.privateKey = UUID.randomUUID().toString();
        credentials.type = AuthCredentialsType.Password.toString();
        credentials = getOrCreateDocument(credentials, AuthCredentialsService.FACTORY_LINK);
        assertNotNull("Failed to create credentials", credentials);

        RegistryState registryState = new RegistryState();
        registryState.documentSelfLink = Harbor.DEFAULT_REGISTRY_LINK;
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;
        registryState.address = "http://harbor.vic.com";
        registryState.authCredentialsLink = credentials.documentSelfLink;
        registryState = getOrCreateDocument(registryState, RegistryFactoryService.SELF_LINK);
        assertNotNull("Failed to create registry", registryState);

        SecurityContext securityContext = getSecurityContextByCredentials(credentials.userEmail,
                credentials.privateKey);

        assertEquals(credentials.userEmail, securityContext.id);
        assertEquals(1, securityContext.roles.size());
        assertTrue(securityContext.roles.contains(AuthRole.CLOUD_ADMIN));

        securityContext = getSecurityContextByCredentials(USER_EMAIL_GLORIA, "Password1!");

        assertEquals(USER_NAME_GLORIA, securityContext.name);
        assertEquals(USER_EMAIL_GLORIA, securityContext.id);
        assertTrue(securityContext.roles.contains(AuthRole.BASIC_USER));
        assertTrue(securityContext.roles.contains(AuthRole.BASIC_USER_EXTENDED));
    }

    @Test
    public void testAssignSystemRoleOnPrincipalWithoutUserState() {
        deleteUser(encode(USER_EMAIL_CONNIE));

        assertDocumentNotExists(AuthUtil.buildUserServicePathFromPrincipalId(
                encode(USER_EMAIL_CONNIE)));

        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = Collections.singletonList(AuthRole.CLOUD_ADMIN.name());
        doPatch(roleAssignment, UriUtils.buildUriPath(PrincipalService.SELF_LINK,
                USER_EMAIL_CONNIE, PrincipalService.ROLES_SUFFIX));

        assertDocumentExists(
                AuthUtil.buildUserServicePathFromPrincipalId(encode(USER_EMAIL_CONNIE)));

        SecurityContext connieContext = getSecurityContext(USER_EMAIL_CONNIE);

        assertTrue(connieContext.roles.contains(AuthRole.CLOUD_ADMIN));
        assertTrue(connieContext.roles.contains(AuthRole.BASIC_USER));
        assertTrue(connieContext.roles.contains(AuthRole.BASIC_USER_EXTENDED));
    }

    @Test
    public void testAssignSystemRoleOnPrincipalWithoutUserGroupState() {
        deleteUserGroup(encode(USER_GROUP_DEVELOPERS));
        assertDocumentNotExists(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                encode(USER_GROUP_DEVELOPERS)));

        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = Collections.singletonList(AuthRole.CLOUD_ADMIN.name());

        doPatch(roleAssignment, UriUtils.buildUriPath(PrincipalService.SELF_LINK,
                USER_GROUP_DEVELOPERS, PrincipalService.ROLES_SUFFIX));

        assertDocumentExists(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                encode(USER_GROUP_DEVELOPERS)));

        SecurityContext developersContext = getSecurityContext(USER_GROUP_DEVELOPERS);

        assertTrue(developersContext.roles.contains(AuthRole.CLOUD_ADMIN));
        assertTrue(developersContext.roles.contains(AuthRole.BASIC_USER));
        assertTrue(developersContext.roles.contains(AuthRole.BASIC_USER_EXTENDED));
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testPrincipalHashCodeEquals() {
        Principal p1 = new Principal();
        Principal p2 = new Principal();
        assertEquals(31, p1.hashCode());

        assertFalse(p1.equals(null));
        assertFalse(p1.equals(this));
        assertTrue(p1.equals(p2));

        p1.id = "name@domain";
        assertNotEquals(31, p1.hashCode());
        assertFalse(p1.equals(p2));

        p2.id = "name2@domain";
        assertFalse(p1.equals(p2));

        p2.id = "name@domain";
        assertTrue(p1.equals(p2));
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testPrincipalRoleHashCodeEquals() {
        PrincipalRoles pr1 = new PrincipalRoles();
        PrincipalRoles pr2 = new PrincipalRoles();
        assertEquals(31, pr1.hashCode());

        assertFalse(pr1.equals(null));
        assertFalse(pr1.equals(this));
        assertTrue(pr1.equals(pr2));

        pr1.id = "name@domain";
        assertNotEquals(31, pr1.hashCode());
        assertFalse(pr1.equals(pr2));

        pr2.id = "name2@domain";
        assertFalse(pr1.equals(pr2));

        pr2.id = "name@domain";
        assertTrue(pr1.equals(pr2));
    }

}
