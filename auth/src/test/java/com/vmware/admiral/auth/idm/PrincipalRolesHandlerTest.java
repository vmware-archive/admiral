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

import static com.vmware.admiral.auth.util.AuthUtil.CLOUD_ADMINS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.PrincipalUtil.encode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.security.GeneralSecurityException;
import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.PrincipalRolesHandler.PrincipalRoleAssignment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserService.UserState;

public class PrincipalRolesHandlerTest extends AuthBaseTest {

    @Before
    public void setup() throws GeneralSecurityException {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
    }

    @Test
    public void testAssignRoleToUser() throws Throwable {
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = new ArrayList<>();
        roleAssignment.add.add(AuthRole.CLOUD_ADMIN.name());

        doRoleAssignment(roleAssignment, USER_EMAIL_BASIC_USER);

        UserState state = getDocument(UserState.class, buildUserServicePath(USER_EMAIL_BASIC_USER));
        assertNotNull(state);
        assertTrue(state.userGroupLinks.contains(CLOUD_ADMINS_USER_GROUP_LINK));
    }

    @Test
    public void testUnAssignRoleToUser() throws Throwable {
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = new ArrayList<>();
        roleAssignment.add.add(AuthRole.CLOUD_ADMIN.name());

        // Assign.
        doRoleAssignment(roleAssignment, USER_EMAIL_BASIC_USER);

        UserState state = getDocument(UserState.class, buildUserServicePath(USER_EMAIL_BASIC_USER));
        assertNotNull(state);
        assertTrue(state.userGroupLinks.contains(CLOUD_ADMINS_USER_GROUP_LINK));

        // Unassign.
        roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.remove = new ArrayList<>();
        roleAssignment.remove.add(AuthRole.CLOUD_ADMIN.name());

        doRoleAssignment(roleAssignment, USER_EMAIL_BASIC_USER);

        // Verify.
        state = getDocument(UserState.class, buildUserServicePath(USER_EMAIL_BASIC_USER));
        assertNotNull(state);
        assertTrue(!state.userGroupLinks.contains(CLOUD_ADMINS_USER_GROUP_LINK));
    }

    @Test
    public void testAssignRoleToUserTwice() throws Throwable {
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = new ArrayList<>();
        roleAssignment.add.add(AuthRole.CLOUD_ADMIN.name());

        // Assign.
        doRoleAssignment(roleAssignment, USER_EMAIL_BASIC_USER);

        UserState state = getDocument(UserState.class, buildUserServicePath(USER_EMAIL_BASIC_USER));
        assertNotNull(state);
        assertTrue(state.userGroupLinks.contains(CLOUD_ADMINS_USER_GROUP_LINK));

        // Unassign.
        roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.remove = new ArrayList<>();
        roleAssignment.remove.add(AuthRole.CLOUD_ADMIN.name());

        doRoleAssignment(roleAssignment, USER_EMAIL_BASIC_USER);

        state = getDocument(UserState.class, buildUserServicePath(USER_EMAIL_BASIC_USER));
        assertNotNull(state);
        assertTrue(!state.userGroupLinks.contains(CLOUD_ADMINS_USER_GROUP_LINK));

        // Assign again.
        roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = new ArrayList<>();
        roleAssignment.add.add(AuthRole.CLOUD_ADMIN.name());

        doRoleAssignment(roleAssignment, USER_EMAIL_BASIC_USER);

        state = getDocument(UserState.class, buildUserServicePath(USER_EMAIL_BASIC_USER));
        assertNotNull(state);
        assertTrue(state.userGroupLinks.contains(CLOUD_ADMINS_USER_GROUP_LINK));
    }

    @Test
    public void testAssignRoleToUserGroup() throws Throwable {
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = new ArrayList<>();
        roleAssignment.add.add(AuthRole.CLOUD_ADMIN.name());

        doRoleAssignment(roleAssignment, USER_GROUP_DEVELOPERS);

        RoleState roleState = getDocument(RoleState.class,
                UriUtils.buildUriPath(RoleService.FACTORY_LINK, AuthRole.CLOUD_ADMIN
                        .buildRoleWithSuffix(encode(USER_GROUP_DEVELOPERS))));
        assertNotNull(roleState);
        assertEquals(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                encode(USER_GROUP_DEVELOPERS)), roleState.userGroupLink);
    }

    @Test
    public void testUnassignRoleToUserGroup() throws Throwable {
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = new ArrayList<>();
        roleAssignment.add.add(AuthRole.CLOUD_ADMIN.name());

        // Assign.
        doRoleAssignment(roleAssignment, USER_GROUP_DEVELOPERS);

        RoleState roleState = getDocument(RoleState.class,
                UriUtils.buildUriPath(RoleService.FACTORY_LINK, AuthRole.CLOUD_ADMIN
                        .buildRoleWithSuffix(encode(USER_GROUP_DEVELOPERS))));
        assertNotNull(roleState);
        assertEquals(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                encode(USER_GROUP_DEVELOPERS)), roleState.userGroupLink);

        // Unassign.
        roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.remove = new ArrayList<>();
        roleAssignment.remove.add(AuthRole.CLOUD_ADMIN.name());

        doRoleAssignment(roleAssignment, USER_GROUP_DEVELOPERS);

        // Verify.
        String developersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.CLOUD_ADMIN.buildRoleWithSuffix(encode(USER_GROUP_DEVELOPERS)));
        TestContext ctx2 = testCreate(1);
        Operation getSuperusersRole = Operation.createGet(host, developersRoleLink)
                .setReferer(host.getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        if (ex instanceof ServiceNotFoundException) {
                            ctx2.completeIteration();
                            return;
                        }
                        ctx2.failIteration(ex);
                        return;
                    }
                    ctx2.failIteration(new RuntimeException("After unassign user group, role "
                            + "should be deleted."));
                });
        host.send(getSuperusersRole);
        ctx2.await();
    }

    @Test
    public void testAssignRoleToUserGroupTwice() throws Throwable {
        PrincipalRoleAssignment roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = new ArrayList<>();
        roleAssignment.add.add(AuthRole.CLOUD_ADMIN.name());

        // Assign.
        doRoleAssignment(roleAssignment, USER_GROUP_DEVELOPERS);

        RoleState roleState = getDocument(RoleState.class,
                UriUtils.buildUriPath(RoleService.FACTORY_LINK, AuthRole.CLOUD_ADMIN
                        .buildRoleWithSuffix(encode(USER_GROUP_DEVELOPERS))));
        assertNotNull(roleState);
        assertEquals(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                encode(USER_GROUP_DEVELOPERS)), roleState.userGroupLink);

        // Unassign.
        roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.remove = new ArrayList<>();
        roleAssignment.remove.add(AuthRole.CLOUD_ADMIN.name());

        doRoleAssignment(roleAssignment, USER_GROUP_DEVELOPERS);

        String developersRoleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.CLOUD_ADMIN.buildRoleWithSuffix(encode(USER_GROUP_DEVELOPERS)));
        TestContext ctx2 = testCreate(1);
        Operation getSuperusersRole = Operation.createGet(host, developersRoleLink)
                .setReferer(host.getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        if (ex instanceof ServiceNotFoundException) {
                            ctx2.completeIteration();
                            return;
                        }
                        ctx2.failIteration(ex);
                        return;
                    }
                    ctx2.failIteration(new RuntimeException("After unassign user group, role "
                            + "should be deleted."));
                });
        host.send(getSuperusersRole);
        ctx2.await();

        // Assign again.
        roleAssignment = new PrincipalRoleAssignment();
        roleAssignment.add = new ArrayList<>();
        roleAssignment.add.add(AuthRole.CLOUD_ADMIN.name());

        doRoleAssignment(roleAssignment, USER_GROUP_DEVELOPERS);

        roleState = getDocument(RoleState.class,
                UriUtils.buildUriPath(RoleService.FACTORY_LINK, AuthRole.CLOUD_ADMIN
                        .buildRoleWithSuffix(encode(USER_GROUP_DEVELOPERS))));
        assertNotNull(roleState);
        assertEquals(UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                encode(USER_GROUP_DEVELOPERS)), roleState.userGroupLink);
    }

    private void doRoleAssignment(PrincipalRoleAssignment roleAssignment, String principalId) {
        TestContext ctx = testCreate(1);
        PrincipalRolesHandler.create()
                .setService(privilegedTestService)
                .setPrincipalId(principalId)
                .setRoleAssignment(roleAssignment)
                .update()
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        ctx.failIteration(ex);
                        return;
                    }
                    ctx.completeIteration();
                });
        ctx.await();
    }
}
