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

import java.util.EnumSet;

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
import static com.vmware.admiral.auth.util.AuthUtil.buildResourceGroupState;
import static com.vmware.admiral.auth.util.AuthUtil.buildRoleState;
import static com.vmware.admiral.auth.util.AuthUtil.buildUserGroupState;

import org.junit.Test;

import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
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

        String id = AuthRole.PROJECT_ADMINS.buildRoleWithSuffix(SAMPLE_PROJECT_ID);
        String expectedSelfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, id);
        assertEquals(expectedSelfLink, userGroupState.documentSelfLink);
        assertNotNull(userGroupState.query);
    }

    @Test
    public void testBuildProjectMembersUserGroup() {
        UserGroupState userGroupState = buildProjectMembersUserGroup(SAMPLE_PROJECT_ID);

        String id = AuthRole.PROJECT_MEMBERS.buildRoleWithSuffix(SAMPLE_PROJECT_ID);
        String expectedSelfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, id);
        assertEquals(expectedSelfLink, userGroupState.documentSelfLink);
        assertNotNull(userGroupState.query);
    }

    @Test
    public void testBuildProjectResourceGroup() {
        ResourceGroupState resourceGroupState = buildProjectResourceGroup(SAMPLE_PROJECT_ID);
        String expectedSelfLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, SAMPLE_PROJECT_ID);

        assertEquals(expectedSelfLink, resourceGroupState.documentSelfLink);
        assertNotNull(resourceGroupState.query);
    }

    @Test
    public void testBuildProjectAdminsRole() {
        RoleState roleState = buildProjectAdminsRole(SAMPLE_PROJECT_ID, SAMPLE_USER_GROUP_LINK, SAMPLE_RESOURCE_GROUP_LINK);

        String id = AuthRole.PROJECT_ADMINS.buildRoleWithSuffix(SAMPLE_PROJECT_ID, SAMPLE_USER_GROUP_LINK);
        String expectedSelfLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, id);
        assertEquals(expectedSelfLink, roleState.documentSelfLink);
        assertEquals(SAMPLE_USER_GROUP_LINK, roleState.userGroupLink);
        assertEquals(SAMPLE_RESOURCE_GROUP_LINK, roleState.resourceGroupLink);
    }

    @Test
    public void testBuildProjectMembersRole() {
        RoleState roleState = buildProjectMembersRole(SAMPLE_PROJECT_ID, SAMPLE_USER_GROUP_LINK, SAMPLE_RESOURCE_GROUP_LINK);

        String id = AuthRole.PROJECT_MEMBERS.buildRoleWithSuffix(SAMPLE_PROJECT_ID, SAMPLE_USER_GROUP_LINK);
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

        String expectedSelfLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, SAMPLE_PROJECT_ID);
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
        RoleState roleState = buildRoleState(SAMPLE_SELF_LINK, SAMPLE_USER_GROUP_LINK, SAMPLE_RESOURCE_GROUP_LINK, verbs);

        assertEquals(SAMPLE_SELF_LINK, roleState.documentSelfLink);
        assertEquals(SAMPLE_USER_GROUP_LINK, roleState.userGroupLink);
        assertEquals(SAMPLE_RESOURCE_GROUP_LINK, roleState.resourceGroupLink);
        assertEquals(verbs, roleState.verbs);
    }
}
