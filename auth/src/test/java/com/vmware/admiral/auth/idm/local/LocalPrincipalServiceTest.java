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

package com.vmware.admiral.auth.idm.local;

import static com.vmware.admiral.auth.util.AuthUtil.BASIC_USERS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.CLOUD_ADMINS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.PrincipalUtil.encode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

public class LocalPrincipalServiceTest extends AuthBaseTest {

    @Before
    public void setup() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        waitForServiceAvailability(LocalPrincipalFactoryService.SELF_LINK);
    }

    @Test
    public void testUserSpecificResourceAreCreatedWhenUserIsCreated() throws Throwable {
        // Assert user specific UserGroup, ResourceGroup and Role are created.
        String fritzEmail = "fritz@admiral.com";
        String fritzSelfLink = LocalPrincipalFactoryService.SELF_LINK + "/" + encode(fritzEmail);

        LocalPrincipalState state = getDocumentNoWait(LocalPrincipalState.class, fritzSelfLink);
        assertNotNull(state);

        UserState userState = getDocumentNoWait(UserState.class, buildUserServicePath(fritzEmail));
        assertNotNull(userState);

        ResourceGroupState resourceGroupState = getDocumentNoWait(ResourceGroupState.class,
                UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, encode(fritzEmail)));
        assertNotNull(resourceGroupState);

        UserGroupState userGroupState = getDocumentNoWait(UserGroupState.class,
                UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, encode(fritzEmail)));
        assertNotNull(userGroupState);

        RoleState roleState = getDocumentNoWait(RoleState.class,
                UriUtils.buildUriPath(RoleService.FACTORY_LINK, encode(fritzEmail)));
        assertNotNull(roleState);
        assertEquals(userGroupState.documentSelfLink, roleState.userGroupLink);
        assertEquals(resourceGroupState.documentSelfLink, roleState.resourceGroupLink);
    }

    @Test
    public void testUsersAreCreatedOnInitBoot() throws Throwable {
        String fritzEmail = "fritz@admiral.com";
        String connieEmail = "connie@admiral.com";
        String gloriaEmail = "gloria@admiral.com";

        String fritzSelfLink = LocalPrincipalFactoryService.SELF_LINK + "/" + encode(fritzEmail);
        String connieSelfLink = LocalPrincipalFactoryService.SELF_LINK + "/" + encode(connieEmail);
        String gloriaSelfLink = LocalPrincipalFactoryService.SELF_LINK + "/" + encode(gloriaEmail);

        LocalPrincipalState fritzState = getDocument(LocalPrincipalState.class, fritzSelfLink);
        LocalPrincipalState connieState = getDocument(LocalPrincipalState.class, connieSelfLink);
        LocalPrincipalState gloriaState = getDocument(LocalPrincipalState.class, gloriaSelfLink);

        assertNotNull(fritzState);
        assertEquals(fritzEmail, fritzState.id);
        assertEquals(fritzEmail, fritzState.email);
        assertEquals("Fritz", fritzState.name);

        assertNotNull(connieState);
        assertEquals(connieEmail, connieState.id);
        assertEquals(connieEmail, connieState.email);
        assertEquals("Connie", connieState.name);

        assertNotNull(gloriaState);
        assertEquals(gloriaEmail, gloriaState.id);
        assertEquals(gloriaEmail, gloriaState.email);
        assertEquals("Gloria", gloriaState.name);
    }

    @Test
    public void testDeletePrincipalShouldDeleteUserState() throws Throwable {
        String fritzEmail = "fritz@admiral.com";
        String fritzSelfLink = LocalPrincipalFactoryService.SELF_LINK + "/" + encode(fritzEmail);

        doDelete(UriUtils.buildUri(host, fritzSelfLink), false);

        LocalPrincipalState state = getDocumentNoWait(LocalPrincipalState.class, fritzSelfLink);
        assertNull(state);

        UserState userState = getDocumentNoWait(UserState.class, buildUserServicePath(fritzEmail));
        assertNull(userState);

        ResourceGroupState resourceGroupState = getDocumentNoWait(ResourceGroupState.class,
                UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, encode(fritzEmail)));
        assertNull(resourceGroupState);

        UserGroupState userGroupState = getDocumentNoWait(UserGroupState.class,
                UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, encode(fritzEmail)));
        assertNull(userGroupState);

        RoleState roleState = getDocumentNoWait(RoleState.class,
                UriUtils.buildUriPath(RoleService.FACTORY_LINK, encode(fritzEmail)));
        assertNull(roleState);

    }

    @Test
    public void testPatchUser() throws Throwable {
        String name = "Fritz Fritz";
        LocalPrincipalState fritzState = new LocalPrincipalState();
        fritzState.name = name;

        fritzState = doPatch(fritzState, UriUtils.buildUriPath(
                LocalPrincipalFactoryService.SELF_LINK, encode(USER_EMAIL_ADMIN)));

        assertEquals(name, fritzState.name);
    }

    @Test
    public void testPatchUserIdOrEmailShouldFail() {
        String fritzSelfLink = UriUtils.buildUriPath(LocalPrincipalFactoryService.SELF_LINK,
                encode(USER_EMAIL_ADMIN));
        TestContext ctx = testCreate(1);
        TestContext ctx1 = testCreate(1);

        LocalPrincipalState state = new LocalPrincipalState();
        state.email = "new@email.com";

        host.send(Operation.createPatch(host, fritzSelfLink)
                .setBody(state)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.completeIteration();
                        return;
                    }
                    ctx.failIteration(
                            new RuntimeException("Exception expected when attempt to patch email"));
                }));
        ctx.await();

        state = new LocalPrincipalState();
        state.id = "testId";

        host.send(Operation.createPatch(host, fritzSelfLink)
                .setBody(state)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx1.completeIteration();
                        return;
                    }
                    ctx1.failIteration(
                            new RuntimeException("Exception expected when attempt to patch id"));
                }));
        ctx1.await();
    }

    @Test
    public void testCreateUser() throws Throwable {
        LocalPrincipalState testUser = new LocalPrincipalState();
        testUser.name = "TestUser";
        testUser.email = "test@admiral.com";
        testUser.type = LocalPrincipalType.USER;
        testUser.password = "testPassword";

        LocalPrincipalState createdUser = doPost(testUser, LocalPrincipalFactoryService.SELF_LINK);

        createdUser = getDocument(LocalPrincipalState.class, createdUser.documentSelfLink);

        assertNotNull(createdUser);
        assertNull(createdUser.password);
        assertNotNull(createdUser.documentSelfLink);
        assertEquals(testUser.name, createdUser.name);
        assertEquals(testUser.email, createdUser.email);
        assertEquals(testUser.email, createdUser.id);

        UserState userState = getDocument(UserState.class,
                buildUserServicePath("test@admiral.com"));
        assertNotNull(userState);
        assertEquals(createdUser.email, userState.email);
        // assert user is added to basic user even if he is cloud admin.
        assertTrue(userState.userGroupLinks.contains(BASIC_USERS_USER_GROUP_LINK));
        // in this test case user is also cloud admin, verify it.
        assertTrue(userState.userGroupLinks.contains(CLOUD_ADMINS_USER_GROUP_LINK));
    }

    @Test
    public void testCreateGroup() throws Throwable {
        // Create first user
        LocalPrincipalState testUser = new LocalPrincipalState();
        testUser.name = "TestUser";
        testUser.email = "test@admiral.com";
        testUser.password = "testPassword";
        testUser.type = LocalPrincipalType.USER;
        testUser = doPost(testUser, LocalPrincipalFactoryService.SELF_LINK);

        // Create seconds user
        LocalPrincipalState testUser1 = new LocalPrincipalState();
        testUser1.name = "TestUser1";
        testUser1.email = "test1@admiral.com";
        testUser1.password = "testPassword";
        testUser1.type = LocalPrincipalType.USER;
        testUser1 = doPost(testUser1, LocalPrincipalFactoryService.SELF_LINK);

        // Create group with already created users.
        LocalPrincipalState testGroup = new LocalPrincipalState();
        testGroup.type = LocalPrincipalType.GROUP;
        testGroup.name = "TestGroup@admiral.com";
        testGroup.groupMembersLinks = new ArrayList<>();
        testGroup.groupMembersLinks.add(testUser.documentSelfLink);
        testGroup.groupMembersLinks.add(testUser1.documentSelfLink);
        testGroup = doPost(testGroup, LocalPrincipalFactoryService.SELF_LINK);

        // Verify the LocalPrincipalState is created.
        assertNotNull(testGroup);
        assertEquals(2, testGroup.groupMembersLinks.size());
        assertTrue(testGroup.groupMembersLinks.contains(testUser.documentSelfLink));
        assertTrue(testGroup.groupMembersLinks.contains(testUser1.documentSelfLink));
        assertTrue(testGroup.documentSelfLink.contains(encode(testGroup.id)));

        // Verify UserGroupState is created.
        UserGroupState groupState = getDocument(UserGroupState.class,
                UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, encode(testGroup.name)));
        assertNotNull(groupState);

        // Verify users are patched.
        List<UserState> users = getUsersFromUserGroup(groupState.documentSelfLink);
        assertNotNull(users);
        assertEquals(testGroup.groupMembersLinks.size(), users.size());

        for (UserState userState : users) {
            assertTrue(userState.email.equals(testUser.email)
                    || userState.email.equals(testUser1.email));

            assertTrue(userState.documentSelfLink.endsWith(encode(testUser.email))
                    || userState.documentSelfLink.endsWith(encode(testUser1.email)));
        }

        // Delete the group
        doDelete(UriUtils.buildUri(host, testGroup.documentSelfLink), false);

        // Verify the related LocalPrincipalState is deleted.
        TestContext ctx = testCreate(1);
        host.send(Operation.createGet(host, testGroup.documentSelfLink)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.completeIteration();
                    } else {
                        ctx.failIteration(
                                new RuntimeException("LocalPrincipalState should be deleted."));
                    }
                }));
        ctx.await();

        // Verify the related UserGroupState is deleted.
        TestContext ctx1 = testCreate(1);
        host.send(Operation.createGet(host, groupState.documentSelfLink)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx1.completeIteration();
                    } else {
                        ctx1.failIteration(
                                new RuntimeException("UserGroupState should be deleted."));
                    }
                }));
        ctx1.await();

        // Verify users are modified and no longer present in deleted group.
        for (UserState userState : users) {
            UserState newState = getDocument(UserState.class, userState.documentSelfLink);
            assertTrue(!newState.userGroupLinks.contains(groupState.documentSelfLink));
        }
    }

    @Test
    public void testGroupsAreCreatedOnInitBoot() throws Throwable {
        String developers = "developers@admiral.com";
        String superusers = "superusers@admiral.com";

        String developerSelfLink = UriUtils.buildUriPath(LocalPrincipalFactoryService.SELF_LINK,
                encode(developers));
        String developerUserGroupSelfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                encode(developers));

        String superusersSelfLink = UriUtils.buildUriPath(LocalPrincipalFactoryService.SELF_LINK,
                encode(superusers));
        String superusersUserGroupSelfLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                encode(superusers));

        UserGroupState developersUg = getDocument(UserGroupState.class, developerUserGroupSelfLink);
        assertNotNull(developersUg);
        assertTrue(developersUg.documentSelfLink.endsWith(encode(developers)));

        LocalPrincipalState developersPrincipalState = getDocument(LocalPrincipalState.class,
                developerSelfLink);
        assertNotNull(developersPrincipalState);
        assertTrue(developersPrincipalState.documentSelfLink.endsWith(encode(developers)));

        UserGroupState superusersUg = getDocument(UserGroupState.class,
                superusersUserGroupSelfLink);
        assertNotNull(superusersUg);
        assertTrue(superusersUg.documentSelfLink.endsWith(encode(superusers)));

        LocalPrincipalState superusersPrincipalState = getDocument(LocalPrincipalState.class,
                superusersSelfLink);
        assertNotNull(superusersPrincipalState);
        assertTrue(superusersPrincipalState.documentSelfLink.endsWith(encode(superusers)));
    }
}
