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

package com.vmware.admiral.auth.idm;

import static com.vmware.admiral.auth.util.AuthUtil.CLOUD_ADMINS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.addReplicationFactor;
import static com.vmware.admiral.auth.util.AuthUtil.buildCloudAdminsRole;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.auth.util.UserGroupsUpdater;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;

public class PrincipalRolesHandler {

    private static final String ROLE_NOT_SUPPORTED_MESSAGE = "Assign/unassign operations for role"
            + " %s not supported.";
    private static final String ROLE_NOT_SUPPORTED_MESSAGE_CODE = "auth.role.not.supported";

    public static final String PRINCIPAL_AT_SIGN = "@";

    public static class PrincipalRoleAssignment {
        public List<String> add;

        public List<String> remove;
    }

    private ServiceHost host;

    private PrincipalRoleAssignment roleAssignment;

    private String principalId;

    private PrincipalRolesHandler() {
    }

    private PrincipalRolesHandler(ServiceHost host,
            PrincipalRoleAssignment roleAssignment, String principalId) {
        this.setHost(host);
        this.setPrincipalId(principalId);
        this.setRoleAssignment(roleAssignment);
    }

    public static PrincipalRolesHandler create() {
        return new PrincipalRolesHandler();
    }

    public static PrincipalRolesHandler create(ServiceHost host,
            PrincipalRoleAssignment roleAssignment, String principalId) {
        return new PrincipalRolesHandler(host, roleAssignment, principalId);
    }

    public static boolean isPrincipalRolesUpdate(Operation op) {
        PrincipalRoleAssignment body = op.getBody(PrincipalRoleAssignment.class);
        if (body == null) {
            return false;
        }
        return (body.add != null && !body.add.isEmpty())
                || (body.remove != null && !body.remove.isEmpty());
    }

    public PrincipalRolesHandler setHost(ServiceHost host) {
        assertNotNull(host, "host");
        this.host = host;
        return this;
    }

    public PrincipalRolesHandler setRoleAssignment(PrincipalRoleAssignment roleAssignment) {
        assertNotNull(roleAssignment, "roleAssignment");
        this.roleAssignment = roleAssignment;
        return this;
    }

    public PrincipalRolesHandler setPrincipalId(String principalId) {
        assertNotNull(principalId, "principalId");
        this.principalId = principalId;
        return this;
    }

    public DeferredResult<Void> update() {
        if ((roleAssignment.add == null || roleAssignment.add.isEmpty())
                && (roleAssignment.remove == null || roleAssignment.remove.isEmpty())) {
            return DeferredResult.completed(null);
        }

        if (principalId.contains(PRINCIPAL_AT_SIGN)) {
            return handleUser();
        }
        return handleUserGroup();
    }

    private DeferredResult<Void> handleUser() {
        List<DeferredResult<Void>> results = new ArrayList<>();
        if (roleAssignment.add != null && !roleAssignment.add.isEmpty()) {
            for (String role : roleAssignment.add) {
                AuthRole authRole = AuthRole.fromSuffixOrName(role);
                results.add(handleUserRoleAssignment(authRole));
            }
        }

        if (roleAssignment.remove != null && !roleAssignment.remove.isEmpty()) {
            for (String role : roleAssignment.remove) {
                AuthRole authRole = AuthRole.fromSuffixOrName(role);
                results.add(handleUserRoleUnassignment(authRole));
            }
        }

        return DeferredResult.allOf(results).thenAccept(ignore -> {
        });
    }

    private DeferredResult<Void> handleUserGroup() {
        List<DeferredResult<Void>> results = new ArrayList<>();
        if (roleAssignment.add != null && !roleAssignment.add.isEmpty()) {
            for (String role : roleAssignment.add) {
                AuthRole authRole = AuthRole.fromSuffixOrName(role);
                results.add(handleUserGroupRoleAssignment(authRole));
            }
        }

        if (roleAssignment.remove != null && !roleAssignment.remove.isEmpty()) {
            for (String role : roleAssignment.remove) {
                AuthRole authRole = AuthRole.fromSuffixOrName(role);
                results.add(handleUserGroupRoleUnassignment(authRole));
            }
        }

        return DeferredResult.allOf(results).thenAccept(ignore -> {
        });
    }

    private DeferredResult<Void> handleUserRoleAssignment(AuthRole role) {

        if (role == AuthRole.CLOUD_ADMINS) {
            return UserGroupsUpdater.create()
                    .setGroupLink(CLOUD_ADMINS_USER_GROUP_LINK)
                    .setHost(host)
                    .setReferrer(host.getUri().toString())
                    .setUsersToAdd(Collections.singletonList(principalId))
                    .update();

        }
        return DeferredResult.failed(new LocalizableValidationException(
                ROLE_NOT_SUPPORTED_MESSAGE, ROLE_NOT_SUPPORTED_MESSAGE_CODE, role.getName()));

    }

    private DeferredResult<Void> handleUserRoleUnassignment(AuthRole role) {

        if (role == AuthRole.CLOUD_ADMINS) {
            return UserGroupsUpdater.create()
                    .setGroupLink(CLOUD_ADMINS_USER_GROUP_LINK)
                    .setHost(host)
                    .setReferrer(host.getUri().toString())
                    .setUsersToRemove(Collections.singletonList(principalId))
                    .update();

        }
        return DeferredResult.failed(new LocalizableValidationException(
                ROLE_NOT_SUPPORTED_MESSAGE, ROLE_NOT_SUPPORTED_MESSAGE_CODE, role.getName()));

    }

    private DeferredResult<Void> handleUserGroupRoleAssignment(AuthRole role) {
        if (role == AuthRole.CLOUD_ADMINS) {
            return handleCloudAdminGroupAssignment(principalId);
        }

        return DeferredResult.failed(new LocalizableValidationException(
                ROLE_NOT_SUPPORTED_MESSAGE, ROLE_NOT_SUPPORTED_MESSAGE_CODE, role.getName()));

    }

    private DeferredResult<Void> handleUserGroupRoleUnassignment(AuthRole role) {
        if (role == AuthRole.CLOUD_ADMINS) {
            return handleCloudAdminGroupUnassignment();
        }

        return DeferredResult.failed(new LocalizableValidationException(
                ROLE_NOT_SUPPORTED_MESSAGE, ROLE_NOT_SUPPORTED_MESSAGE_CODE, role.getName()));

    }

    private DeferredResult<Void> handleCloudAdminGroupUnassignment() {
        String roleLink = UriUtils.buildUriPath(RoleService.FACTORY_LINK, AuthRole.CLOUD_ADMINS
                .buildRoleWithSuffix(principalId));
        Operation getRole = Operation.createGet(host, roleLink)
                .setReferer(host.getUri());

        DeferredResult<Void> result = new DeferredResult<>();

        host.sendWithDeferredResult(getRole, RoleState.class)
                .handle((ug, ex) -> {
                    if (ex != null) {
                        if (ex.getCause() instanceof ServiceNotFoundException) {
                            // User group was never assigned, so we don't have to delete any role.
                            DeferredResult.completed(ug);
                        }
                        return DeferredResult.failed(ex);
                    }
                    Operation deleteRoleOp = Operation.createDelete(host, roleLink)
                            .setReferer(host.getUri());
                    addReplicationFactor(deleteRoleOp);
                    return host.sendWithDeferredResult(deleteRoleOp, RoleState.class);
                })
                .thenAccept(ignore -> result.complete(null))
                .exceptionally(ex -> {
                    result.fail(ex);
                    return null;
                });

        return result;
    }

    private DeferredResult<Void> handleCloudAdminGroupAssignment(String principalId) {
        String groupLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, principalId);
        Operation getGroup = Operation.createGet(host, groupLink)
                .setReferer(host.getUri());

        DeferredResult<Void> result = new DeferredResult<>();

        host.sendWithDeferredResult(getGroup, UserGroupState.class)
                .handle((ug, ex) -> {
                    if (ex != null) {
                        if (ex.getCause() instanceof ServiceNotFoundException) {
                            Query groupQuery = AuthUtil.buildQueryForUsers(groupLink);
                            UserGroupState state = UserGroupState.Builder.create()
                                    .withQuery(groupQuery)
                                    .withSelfLink(groupLink).build();
                            Operation createGroupOp = Operation.createPost(host,
                                    UserGroupService.FACTORY_LINK)
                                    .setReferer(host.getUri())
                                    .setBody(state);
                            addReplicationFactor(createGroupOp);
                            return host.sendWithDeferredResult(createGroupOp, UserGroupState.class);
                        }
                        return DeferredResult.failed(ex);
                    }
                    return DeferredResult.completed(ug);
                })
                .thenAccept(deferredResult -> deferredResult
                        .thenCompose(ignore -> {
                            RoleState roleState = buildCloudAdminsRole(principalId, groupLink);
                            Operation createRoleOp = Operation
                                    .createPost(host, RoleService.FACTORY_LINK)
                                    .setReferer(host.getUri())
                                    .setBody(roleState);
                            addReplicationFactor(createRoleOp);
                            return host.sendWithDeferredResult(createRoleOp, RoleState.class);
                        })
                        .thenAccept(ignore -> result.complete(null))
                        .exceptionally(ex -> {
                            result.fail(ex);
                            return null;
                        }));

        return result;
    }

}
