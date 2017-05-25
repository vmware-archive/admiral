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

package com.vmware.admiral.auth.idm.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;

public class LocalPrincipalServiceTest extends AuthBaseTest {

    @Before
    public void setup() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USERNAME_ADMIN));
        waitForServiceAvailability(LocalPrincipalFactoryService.SELF_LINK);
    }

    @Test
    public void testUsersAreCreatedOnInitBoot() throws Throwable {
        String fritzEmail = "fritz@admiral.com";
        String connieEmail = "connie@admiral.com";
        String gloriaEmail = "gloria@admiral.com";

        String fritzSelfLink = LocalPrincipalFactoryService.SELF_LINK + "/" + fritzEmail;
        String connieSelfLink = LocalPrincipalFactoryService.SELF_LINK + "/" + connieEmail;
        String gloriaSelfLink = LocalPrincipalFactoryService.SELF_LINK + "/" + gloriaEmail;

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
    public void testDeletePrincipalShouldDeleteUserState() {
        String fritzEmail = "fritz@admiral.com";
        String fritzSelfLink = LocalPrincipalFactoryService.SELF_LINK + "/" + fritzEmail;

        TestContext ctx = testCreate(1);
        Operation delete = Operation
                .createDelete(host, fritzSelfLink)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                        return;
                    }
                    ctx.completeIteration();
                });
        host.send(delete);
        ctx.await();

        TestContext ctx1 = testCreate(1);
        Operation get = Operation
                .createGet(host, UserService.FACTORY_LINK + "/fritz@admiral.com")
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (Operation.STATUS_CODE_NOT_FOUND == o.getStatusCode()) {
                            ctx1.completeIteration();
                            return;
                        }
                        ctx1.failIteration(ex);
                        return;
                    }
                    ctx1.failIteration(new RuntimeException("UserState fritz@admiral.com "
                            + "should've been deleted."));
                });
        host.send(get);
        ctx1.await();
    }

    @Test
    public void testPatchUser() throws Throwable {
        String name = "Fritz Fritz";
        LocalPrincipalState fritzState = new LocalPrincipalState();
        fritzState.name = name;

        fritzState = doPatch(fritzState, LocalPrincipalFactoryService.SELF_LINK +
                "/fritz@admiral.com");

        assertEquals(name, fritzState.name);
    }

    @Test
    public void testPatchUserIdOrEmailShouldFail() {
        String fritzSelfLink = LocalPrincipalFactoryService.SELF_LINK + "/fritz@admiral.com";
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
        assertTrue(createdUser.documentSelfLink.endsWith(testUser.email));

        UserState userState = getDocument(UserState.class, UserService.FACTORY_LINK +
                "/test@admiral.com");
        assertNotNull(userState);
        assertEquals(createdUser.email, userState.email);
    }

    @Test
    public void testCreateGroup() throws Throwable {
        LocalPrincipalState testUser = new LocalPrincipalState();
        testUser.name = "TestUser";
        testUser.email = "test@admiral.com";
        testUser.password = "testPassword";
        testUser.type = LocalPrincipalType.USER;
        testUser = doPost(testUser, LocalPrincipalFactoryService.SELF_LINK);

        LocalPrincipalState testUser1 = new LocalPrincipalState();
        testUser1.name = "TestUser1";
        testUser1.email = "test1@admiral.com";
        testUser1.password = "testPassword";
        testUser1.type = LocalPrincipalType.USER;
        testUser1 = doPost(testUser1, LocalPrincipalFactoryService.SELF_LINK);

        LocalPrincipalState testGroup = new LocalPrincipalState();
        testGroup.type = LocalPrincipalType.GROUP;
        testGroup.name = "TestGroup";
        testGroup.groupMembersLinks = new ArrayList<>();
        testGroup.groupMembersLinks.add(testUser.documentSelfLink);
        testGroup.groupMembersLinks.add(testUser1.documentSelfLink);
        testGroup = doPost(testGroup, LocalPrincipalFactoryService.SELF_LINK);

        assertNotNull(testGroup);
        assertEquals(2, testGroup.groupMembersLinks.size());
        assertTrue(testGroup.groupMembersLinks.contains(testUser.documentSelfLink));
        assertTrue(testGroup.groupMembersLinks.contains(testUser1.documentSelfLink));
        assertTrue(testGroup.documentSelfLink.contains(testGroup.id));
    }

}
