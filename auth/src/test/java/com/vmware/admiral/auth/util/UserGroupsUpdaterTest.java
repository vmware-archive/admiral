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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;

public class UserGroupsUpdaterTest extends AuthBaseTest {

    @Before
    public void setup() throws GeneralSecurityException {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
    }

    //TODO: Remove waitFor() once patch is stable.
    @Test
    public void testUserGroupsUpdater() throws Throwable {
        // Create test user group.
        String userGroupSelfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, "testId");
        Query userGroupQuery = AuthUtil.buildQueryForUsers(userGroupSelfLink);
        UserGroupState userGroupState = UserGroupState.Builder
                .create()
                .withQuery(userGroupQuery)
                .withSelfLink(userGroupSelfLink)
                .build();

        userGroupState = doPost(userGroupState, UserGroupService.FACTORY_LINK);
        assertNotNull(userGroupState);

        // Add users.
        DeferredResult<Void> result = UserGroupsUpdater.create()
                .setService(privilegedTestService)
                .setGroupLink(userGroupSelfLink)
                .setUsersToAdd(Arrays.asList(USER_EMAIL_ADMIN, USER_EMAIL_CONNIE))
                .update();

        TestContext ctx = testCreate(1);
        result.whenComplete((o, ex) -> {
            if (ex != null) {
                ctx.failIteration(ex);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        waitFor(() -> {
            UserState connieState = getDocumentNoWait(UserState.class, UriUtils.buildUriPath(
                    UserService.FACTORY_LINK, USER_EMAIL_CONNIE));
            if (!connieState.userGroupLinks.contains(userGroupSelfLink)) {
                return false;
            }
            return true;
        });

        waitFor(() -> {
            UserState adminState = getDocumentNoWait(UserState.class, UriUtils.buildUriPath(
                    UserService.FACTORY_LINK, USER_EMAIL_ADMIN));
            if (!adminState.userGroupLinks.contains(userGroupSelfLink)) {
                return false;
            }
            return true;
        });

        // Verify users are added.
        List<UserState> users = getUsersFromUserGroup(userGroupState.documentSelfLink);
        for (UserState state : users) {
            assertTrue(
                    state.email.equals(USER_EMAIL_ADMIN) || state.email.equals(USER_EMAIL_CONNIE));
        }

        // Remove user.
        result = UserGroupsUpdater.create()
                .setService(privilegedTestService)
                .setGroupLink(userGroupSelfLink)
                .setUsersToRemove(Arrays.asList(USER_EMAIL_CONNIE))
                .update();

        TestContext ctx1 = testCreate(1);
        result.whenComplete((o, ex) -> {
            if (ex != null) {
                ctx1.failIteration(ex);
                return;
            }
            ctx1.completeIteration();
        });
        ctx1.await();

        // Verify user is removed.
        waitFor(() -> {
            UserState connieState = getDocumentNoWait(UserState.class, UriUtils.buildUriPath(
                    UserService.FACTORY_LINK, USER_EMAIL_CONNIE));
            if (connieState.userGroupLinks.contains(userGroupSelfLink)) {
                return false;
            }
            return true;
        });
    }
}
