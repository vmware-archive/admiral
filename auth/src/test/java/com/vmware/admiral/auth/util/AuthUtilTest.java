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
import static com.vmware.admiral.auth.util.AuthUtil.buildCloudAdminsResourceGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildCloudAdminsRole;
import static com.vmware.admiral.auth.util.AuthUtil.buildEmptyBasicUsersUserGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildEmptyCloudAdminsUserGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildUserGroupState;

import org.junit.Test;

import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;

public class AuthUtilTest {

    @Test
    public void testBuildEmptyCloudAdminsUserGroup() {
        UserGroupState userGroupState = buildEmptyCloudAdminsUserGroup();
        assertEquals(CLOUD_ADMINS_USER_GROUP_LINK, userGroupState.documentSelfLink);
    }

    @Test
    public void testBuildCloudAdminsResourceGroup() {
        ResourceGroupState resourceGroupState = buildCloudAdminsResourceGroup();
        assertEquals(CLOUD_ADMINS_RESOURCE_GROUP_LINK, resourceGroupState.documentSelfLink);
    }

    @Test
    public void testBuildCloudAdminsRole() {
        RoleState userGroupState = buildCloudAdminsRole(DEFAULT_IDENTIFIER,
                CLOUD_ADMINS_USER_GROUP_LINK);
        assertEquals(DEFAULT_CLOUD_ADMINS_ROLE_LINK, userGroupState.documentSelfLink);
    }

    @Test
    public void testBuildEmptyBasicUsersUserGroup() {
        UserGroupState userGroupState = buildEmptyBasicUsersUserGroup();
        assertEquals(BASIC_USERS_USER_GROUP_LINK, userGroupState.documentSelfLink);
    }

    @Test
    public void testBuildBasicUsersResourceGroup() {
        ResourceGroupState resourceGroupState = buildBasicUsersResourceGroup();
        assertEquals(BASIC_USERS_RESOURCE_GROUP_LINK, resourceGroupState.documentSelfLink);
    }

    @Test
    public void testBuildBasicUsersRole() {
        RoleState userGroupState = buildBasicUsersRole(DEFAULT_IDENTIFIER,
                BASIC_USERS_USER_GROUP_LINK);
        assertEquals(DEFAULT_BASIC_USERS_ROLE_LINK, userGroupState.documentSelfLink);
    }

    @Test
    public void testBuildBasicUsersExtendedResourceGroup() {
        ResourceGroupState resourceGroupState = buildBasicUsersExtendedResourceGroup();
        assertEquals(BASIC_USERS_EXTENDED_RESOURCE_GROUP_LINK, resourceGroupState.documentSelfLink);
    }

    @Test
    public void testBuildBasicUsersExtendedRole() {
        RoleState userGroupState = buildBasicUsersExtendedRole(DEFAULT_IDENTIFIER,
                BASIC_USERS_USER_GROUP_LINK);
        assertEquals(DEFAULT_BASIC_USERS_EXTENDED_ROLE_LINK, userGroupState.documentSelfLink);
    }

    @Test
    public void testBuildUserGroupState() {
        String testId = "testIdentifier";
        String testIdSelfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, testId);
        UserGroupState userGroupState = buildUserGroupState(testId);
        assertEquals(testIdSelfLink, userGroupState.documentSelfLink);
    }
}
