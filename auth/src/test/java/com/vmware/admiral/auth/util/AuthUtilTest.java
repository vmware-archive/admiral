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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.auth.util.AuthUtil.BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.BASIC_USERS_RESOURCE_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.BASIC_USERS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.CLOUD_ADMINS_RESOURCE_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.CLOUD_ADMINS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.DEFAULT_BASIC_USERS_EXTENDED_ROLE_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.DEFAULT_BASIC_USERS_ROLE_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.DEFAULT_CLOUD_ADMINS_ROLE_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.DEFAULT_IDENTIFIER;
import static com.vmware.admiral.auth.util.AuthUtil.buildBasicUsersExtendedResourceGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildBasicUsersExtendedRole;
import static com.vmware.admiral.auth.util.AuthUtil.buildBasicUsersResourceGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildBasicUsersRole;
import static com.vmware.admiral.auth.util.AuthUtil.buildBasicUsersUserGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildCloudAdminsResourceGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildCloudAdminsRole;
import static com.vmware.admiral.auth.util.AuthUtil.buildCloudAdminsUserGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildProjectAdminsRole;
import static com.vmware.admiral.auth.util.AuthUtil.buildProjectAdminsUserGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildProjectMembersRole;
import static com.vmware.admiral.auth.util.AuthUtil.buildProjectMembersUserGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildProjectResourceGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildProjectViewersRole;
import static com.vmware.admiral.auth.util.AuthUtil.buildProjectViewersUserGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildResourceGroupState;
import static com.vmware.admiral.auth.util.AuthUtil.buildRoleState;
import static com.vmware.admiral.auth.util.AuthUtil.buildUserGroupState;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;

public class AuthUtilTest {
    private static final String SAMPLE_PROJECT_ID = "project_id";
    private static final String SAMPLE_USER_GROUP_LINK = "user_group_link";
    private static final String SAMPLE_RESOURCE_GROUP_LINK = "resource_group_link";
    private static final String SAMPLE_SELF_LINK = "self_link";

    @Test
    public void testBuildCloudAdminsUserGroup() {
        UserGroupState userGroupState = buildCloudAdminsUserGroup();
        assertEquals(CLOUD_ADMINS_USER_GROUP_LINK, userGroupState.documentSelfLink);
        assertNotNull(userGroupState.query);
    }

    @Test
    public void testBuildCloudAdminsResourceGroup() {
        ResourceGroupState resourceGroupState = buildCloudAdminsResourceGroup();
        assertEquals(CLOUD_ADMINS_RESOURCE_GROUP_LINK, resourceGroupState.documentSelfLink);
        assertNotNull(resourceGroupState.query);
    }

    @Test
    public void testBuildCloudAdminsRole() {
        RoleState roleState = buildCloudAdminsRole(DEFAULT_IDENTIFIER,
                CLOUD_ADMINS_USER_GROUP_LINK);
        assertEquals(DEFAULT_CLOUD_ADMINS_ROLE_LINK, roleState.documentSelfLink);
        assertEquals(CLOUD_ADMINS_USER_GROUP_LINK, roleState.userGroupLink);
        assertEquals(CLOUD_ADMINS_RESOURCE_GROUP_LINK, roleState.resourceGroupLink);
    }

    @Test
    public void testBuildBasicUsersUserGroup() {
        UserGroupState userGroupState = buildBasicUsersUserGroup();
        assertEquals(BASIC_USERS_USER_GROUP_LINK, userGroupState.documentSelfLink);
        assertNotNull(userGroupState.query);
    }

    @Test
    public void testBuildBasicUsersResourceGroup() {
        ResourceGroupState resourceGroupState = buildBasicUsersResourceGroup();
        assertEquals(BASIC_USERS_RESOURCE_GROUP_LINK, resourceGroupState.documentSelfLink);
        assertNotNull(resourceGroupState.query);
    }

    @Test
    public void testBuildBasicUsersRole() {
        RoleState roleState = buildBasicUsersRole(DEFAULT_IDENTIFIER,
                BASIC_USERS_USER_GROUP_LINK);
        assertEquals(DEFAULT_BASIC_USERS_ROLE_LINK, roleState.documentSelfLink);
        assertEquals(BASIC_USERS_USER_GROUP_LINK, roleState.userGroupLink);
        assertEquals(BASIC_USERS_RESOURCE_GROUP_LINK, roleState.resourceGroupLink);
    }

    @Test
    public void testBuildBasicUsersExtendedResourceGroup() {
        ResourceGroupState resourceGroupState = buildBasicUsersExtendedResourceGroup();
        assertEquals(BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK, resourceGroupState.documentSelfLink);
        assertNotNull(resourceGroupState.query);
    }

    @Test
    public void testBuildBasicUsersExtendedRole() {
        RoleState roleState = buildBasicUsersExtendedRole(DEFAULT_IDENTIFIER,
                BASIC_USERS_USER_GROUP_LINK);
        assertEquals(DEFAULT_BASIC_USERS_EXTENDED_ROLE_LINK, roleState.documentSelfLink);
        assertEquals(BASIC_USERS_USER_GROUP_LINK, roleState.userGroupLink);
        assertEquals(BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK, roleState.resourceGroupLink);
    }

    @Test
    public void testBuildProjectAdminsUserGroup() {
        UserGroupState userGroupState = buildProjectAdminsUserGroup(SAMPLE_PROJECT_ID);

        String id = AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(SAMPLE_PROJECT_ID);
        String expectedSelfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, id);
        assertEquals(expectedSelfLink, userGroupState.documentSelfLink);
        assertNotNull(userGroupState.query);
    }

    @Test
    public void testBuildProjectMembersUserGroup() {
        UserGroupState userGroupState = buildProjectMembersUserGroup(SAMPLE_PROJECT_ID);

        String id = AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(SAMPLE_PROJECT_ID);
        String expectedSelfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, id);
        assertEquals(expectedSelfLink, userGroupState.documentSelfLink);
        assertNotNull(userGroupState.query);
    }

    @Test
    public void testBuildProjectViewersUserGroup() {
        UserGroupState userGroupState = buildProjectViewersUserGroup(SAMPLE_PROJECT_ID);

        String id = AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(SAMPLE_PROJECT_ID);
        String expectedSelfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, id);
        assertEquals(expectedSelfLink, userGroupState.documentSelfLink);
        assertNotNull(userGroupState.query);
    }

    @Test
    public void testBuildProjectResourceGroup() {
        ResourceGroupState resourceGroupState = buildProjectResourceGroup(SAMPLE_PROJECT_ID);
        String expectedSelfLink = UriUtils
                .buildUriPath(ResourceGroupService.FACTORY_LINK, SAMPLE_PROJECT_ID);

        assertEquals(expectedSelfLink, resourceGroupState.documentSelfLink);
        assertNotNull(resourceGroupState.query);
    }

    @Test
    public void testBuildProjectAdminsRole() {
        RoleState roleState = buildProjectAdminsRole(SAMPLE_PROJECT_ID, SAMPLE_USER_GROUP_LINK,
                SAMPLE_RESOURCE_GROUP_LINK);

        String id = AuthRole.PROJECT_ADMIN
                .buildRoleWithSuffix(SAMPLE_PROJECT_ID, SAMPLE_USER_GROUP_LINK);
        String expectedSelfLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);
        assertEquals(expectedSelfLink, roleState.documentSelfLink);
        assertEquals(SAMPLE_USER_GROUP_LINK, roleState.userGroupLink);
        assertEquals(SAMPLE_RESOURCE_GROUP_LINK, roleState.resourceGroupLink);
    }

    @Test
    public void testBuildProjectMembersRole() {
        RoleState roleState = buildProjectMembersRole(SAMPLE_PROJECT_ID, SAMPLE_USER_GROUP_LINK,
                SAMPLE_RESOURCE_GROUP_LINK);

        String id = AuthRole.PROJECT_MEMBER
                .buildRoleWithSuffix(SAMPLE_PROJECT_ID, SAMPLE_USER_GROUP_LINK);
        String expectedSelfLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);
        assertEquals(expectedSelfLink, roleState.documentSelfLink);
        assertEquals(SAMPLE_USER_GROUP_LINK, roleState.userGroupLink);
        assertEquals(SAMPLE_RESOURCE_GROUP_LINK, roleState.resourceGroupLink);
    }

    @Test
    public void testBuildProjectViewersRole() {
        RoleState roleState = buildProjectViewersRole(SAMPLE_PROJECT_ID, SAMPLE_USER_GROUP_LINK,
                SAMPLE_RESOURCE_GROUP_LINK);

        String id = AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(SAMPLE_PROJECT_ID,
                SAMPLE_USER_GROUP_LINK);
        String expectedSelfLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);
        assertEquals(expectedSelfLink, roleState.documentSelfLink);
        assertEquals(SAMPLE_USER_GROUP_LINK, roleState.userGroupLink);
        assertEquals(SAMPLE_RESOURCE_GROUP_LINK, roleState.resourceGroupLink);
    }

    @Test
    public void testBuildUserGroupState() {
        String testId = "testIdentifier";
        String testIdSelfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, testId);
        UserGroupState userGroupState = buildUserGroupState(testId);
        assertEquals(testIdSelfLink, userGroupState.documentSelfLink);
    }

    @Test
    public void testBuildResourceGroupStateWithIdentifier() {
        Query query = new Query();
        query.occurance = Occurance.MUST_NOT_OCCUR;

        ResourceGroupState resourceGroupState = buildResourceGroupState(SAMPLE_PROJECT_ID, query);

        String expectedSelfLink = UriUtils
                .buildUriPath(ResourceGroupService.FACTORY_LINK, SAMPLE_PROJECT_ID);
        assertEquals(expectedSelfLink, resourceGroupState.documentSelfLink);
        assertEquals(Occurance.MUST_NOT_OCCUR, resourceGroupState.query.occurance);
    }

    @Test
    public void testBuildResourceGroupStateWithSelfLink() {
        Query query = new Query();
        query.occurance = Occurance.MUST_NOT_OCCUR;

        ResourceGroupState resourceGroupState = buildResourceGroupState(query, SAMPLE_SELF_LINK);

        assertEquals(SAMPLE_SELF_LINK, resourceGroupState.documentSelfLink);
        assertEquals(Occurance.MUST_NOT_OCCUR, resourceGroupState.query.occurance);
    }

    @Test
    public void testBuildRoleState() {
        EnumSet<Action> verbs = EnumSet.of(Action.GET);
        RoleState roleState = buildRoleState(SAMPLE_SELF_LINK, SAMPLE_USER_GROUP_LINK,
                SAMPLE_RESOURCE_GROUP_LINK, verbs);

        assertEquals(SAMPLE_SELF_LINK, roleState.documentSelfLink);
        assertEquals(SAMPLE_USER_GROUP_LINK, roleState.userGroupLink);
        assertEquals(SAMPLE_RESOURCE_GROUP_LINK, roleState.resourceGroupLink);
        assertEquals(verbs, roleState.verbs);
    }

    @Test
    public void testIsDevOpsAdmin() throws Throwable {
        Method setAuthCtxMethod = Operation.class.getDeclaredMethod("setAuthorizationContext",
                AuthorizationContext.class);

        Claims guestClaims = new Claims.Builder().setSubject(GuestUserService.SELF_LINK)
                .getResult();
        AuthorizationContext guestContext = AuthorizationContext.Builder.create()
                .setClaims(guestClaims).getResult();

        // TODO Currently all authorized non-guest users are devOpsAdmins. Needs to be changed after
        // roles are introduced. Also, a case for developer authorization context and cloud admin
        // need to be added.
        Claims devOpsClaims = new Claims.Builder()
                .setSubject(AuthUtil.buildUserServicePathFromPrincipalId("some-user@local"))
                .getResult();
        AuthorizationContext devOpsContext = AuthorizationContext.Builder.create()
                .setClaims(devOpsClaims).getResult();

        setAuthCtxMethod.setAccessible(true);

        Operation op = new Operation();
        setAuthCtxMethod.invoke(op, (AuthorizationContext) null);
        assertEquals(null, op.getAuthorizationContext());
        assertFalse("<null> authorization context should not be treated as devOps admin context",
                AuthUtil.isDevOpsAdmin(op));

        setAuthCtxMethod.invoke(op, guestContext);
        assertFalse("Guest authorization context should not be trated as devOps admin context",
                AuthUtil.isDevOpsAdmin(op));

        setAuthCtxMethod.invoke(op, devOpsContext);
        assertTrue("Any non-guest authorized user should be a devOps admin",
                AuthUtil.isDevOpsAdmin(op));

        setAuthCtxMethod.setAccessible(false);
    }

    @Test
    public void testBuildUsersQuery() {

        List<String> testUsers = Arrays.asList(
                "/users/user1@test.com",
                "/users/user2@test.com",
                "/users/admi@dev.local");

        Query queryForUsers = AuthUtil.buildUsersQuery(testUsers);

        // kind and userlinks clauses
        List<Query> topLevelClauses = queryForUsers.booleanClauses;
        assertNotNull(topLevelClauses);
        assertEquals(2, topLevelClauses.size());

        topLevelClauses.forEach((query) -> {
            assertEquals(Occurance.MUST_OCCUR, query.occurance);
            assertTrue(query.booleanClauses == null
                    || query.booleanClauses.isEmpty()
                    || query.booleanClauses.size() == testUsers.size());

            if (query.booleanClauses == null || query.booleanClauses.isEmpty()) {
                assertEquals(MatchType.TERM, query.term.matchType);
            } else {
                // this is the userLinks query clause
                query.booleanClauses.forEach((clause) -> {
                    assertEquals(Occurance.SHOULD_OCCUR, clause.occurance);
                    assertEquals(MatchType.TERM, clause.term.matchType);
                    assertEquals(ServiceDocument.FIELD_NAME_SELF_LINK, clause.term.propertyName);
                    assertTrue(testUsers.contains(clause.term.matchValue));
                });
            }
        });
    }

    @Test
    public void testBuildUsersQueryNoUsers() {

        Query queryForUsers = AuthUtil.buildUsersQuery(null);

        // kind and userlinks clauses
        List<Query> topLevelClauses = queryForUsers.booleanClauses;
        assertNotNull(topLevelClauses);
        assertEquals(2, topLevelClauses.size());

        topLevelClauses.forEach((query) -> {
            assertEquals(Occurance.MUST_OCCUR, query.occurance);
            assertEquals(MatchType.TERM, query.term.matchType);
            assertTrue(Arrays
                    .asList(ServiceDocument.FIELD_NAME_KIND, ServiceDocument.FIELD_NAME_SELF_LINK)
                    .contains(query.term.propertyName));

            if (query.term.propertyName.equals(ServiceDocument.FIELD_NAME_SELF_LINK)) {
                assertEquals(AuthUtil.USERS_QUERY_NO_USERS_SELF_LINK, query.term.matchValue);
            }
        });
    }

    @Test(expected = LocalizableValidationException.class)
    public void testExtractDataFromRoleStateId() {
        String[] in = new String[] {
                "123_abc_project-members", "1234_abc_def_project-admins",
                "invalid", "", null
        };

        String[][] out = new String[][] {
                new String[] { "123", "abc", "project-members" },
                new String[] { "1234", "abc_def", "project-admins" }
        };

        assertArrayEquals(out[0], AuthUtil.extractDataFromRoleStateId(in[0]));
        assertArrayEquals(out[1], AuthUtil.extractDataFromRoleStateId(in[1]));

        try {
            AuthUtil.extractDataFromRoleStateId(in[2]);
            fail("Invalid rolestateId exception expected.");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("projectId_groupdId_roleSuffix"));
        }

        AuthUtil.extractDataFromRoleStateId(in[3]);
        AuthUtil.extractDataFromRoleStateId(in[4]);
    }

}
