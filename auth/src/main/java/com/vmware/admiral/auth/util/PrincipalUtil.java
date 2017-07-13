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

import static com.vmware.admiral.auth.util.AuthUtil.BASIC_USERS_USER_GROUP_LINK;
import static com.vmware.admiral.auth.util.AuthUtil.addReplicationFactor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.Principal.PrincipalType;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

public class PrincipalUtil {

    public static Principal fromLocalPrincipalToPrincipal(LocalPrincipalState state) {

        if (state == null) {
            return null;
        }

        Principal principal = new Principal();
        principal.email = state.email;
        principal.name = toPrincipalName(state);
        principal.id = state.id;
        principal.password = state.password;
        principal.type = PrincipalType.valueOf(state.type.name());

        return principal;
    }

    public static List<Principal> fromQueryResultToPrincipalList(
            ServiceDocumentQueryResult queryResult) {

        List<Principal> principals = new ArrayList<>();

        for (Object serializedState : queryResult.documents.values()) {
            LocalPrincipalState state = Utils.fromJson(serializedState, LocalPrincipalState.class);
            Principal principal = fromLocalPrincipalToPrincipal(state);
            principals.add(principal);
        }

        return principals;

    }

    public static LocalPrincipalState fromPrincipalToLocalPrincipal(Principal principal) {
        if (principal == null) {
            return null;
        }

        LocalPrincipalState state = new LocalPrincipalState();
        state.email = principal.email;
        state.id = principal.id;
        state.password = principal.password;
        state.name = principal.name;
        state.type = LocalPrincipalType.valueOf(principal.type.name());

        return state;
    }

    public static Principal copyPrincipalData(Principal src, Principal dst) {
        if (src == null) {
            return null;
        }
        if (dst == null) {
            dst = new Principal();
        }
        dst.id = src.id;
        dst.email = src.email;
        dst.type = src.type;
        dst.name = src.name;
        dst.password = src.password;

        return dst;
    }

    public static DeferredResult<Principal> getPrincipal(Service service, String principalId) {
        Operation getPrincipalOp = Operation.createGet(service, UriUtils.buildUriPath(
                PrincipalService.SELF_LINK, principalId));

        ProjectUtil.authorizeOperationIfProjectService(service, getPrincipalOp);
        return service.sendWithDeferredResult(getPrincipalOp, Principal.class);
    }

    public static Pair<String, String> toNameAndDomain(String principalId) {

        // UPN format: NAME@DOMAIN
        String[] parts = principalId.split("@");
        if (parts.length == 2) {
            return new Pair<>(parts[0], parts[1]);
        }

        // NETBIOS format: DOMAIN\NAME
        parts = principalId.split("\\\\");
        if (parts.length == 2) {
            return new Pair<>(parts[1], parts[0]);
        }

        throw new IllegalArgumentException("Invalid principalId format: '" + principalId + "'");
    }

    public static String toPrincipalId(String name, String domain) {
        if ((name == null) || (name.isEmpty())) {
            throw new IllegalArgumentException("Invalid principal name: '" + name + "'");
        }
        StringBuilder sb = new StringBuilder(name);
        if (domain != null) {
            sb.append("@").append(domain);
        }
        return sb.toString().toLowerCase();
    }

    public static String toPrincipalName(String firstName, String lastName) {
        StringBuilder sb = new StringBuilder();
        if ((firstName != null) && (!firstName.trim().isEmpty())) {
            sb.append(firstName.trim());
        }
        if ((lastName != null) && (!lastName.trim().isEmpty())) {
            sb.append(" ").append(lastName.trim());
        }
        return sb.toString();
    }

    private static String toPrincipalName(LocalPrincipalState state) {
        if ((state.name != null) && (!state.name.trim().isEmpty())) {
            return state.name;
        }
        return state.id.split("@")[0];
    }

    public static DeferredResult<Principal> getPrincipal(ServiceHost host, String principalId) {
        Operation getPrincipalOp = Operation.createGet(host, UriUtils.buildUriPath(
                PrincipalService.SELF_LINK, principalId));

        return host.sendWithDeferredResult(getPrincipalOp, Principal.class);
    }

    public static DeferredResult<UserState> getOrCreateUser(ServiceHost host, String principalId) {

        Operation getUser = Operation.createGet(host,
                AuthUtil.buildUserServicePathFromPrincipalId(principalId))
                .setReferer(host.getUri());

        return getPrincipal(host, principalId)
                .thenCompose(principal -> {
                    if (principal.type != PrincipalType.USER) {
                        String message = String.format("Principal %s is not of type USER, user "
                                + "state cannot be created.", principal.id);
                        return DeferredResult.failed(new IllegalStateException(message));
                    }
                    return host.sendWithDeferredResult(getUser, UserState.class);
                })
                .thenApply(userState -> new Pair<>(userState, (Throwable) null))
                .exceptionally(ex -> new Pair<>(null, ex))
                .thenCompose(pair -> {
                    if (pair.right != null) {
                        if (pair.right.getCause() instanceof ServiceNotFoundException) {
                            // Create the user and assign basic user role.
                            return createUser(host, principalId)
                                    .thenCompose(user -> UserGroupsUpdater.create()
                                            .setHost(host)
                                            .setGroupLink(BASIC_USERS_USER_GROUP_LINK)
                                            .setUsersToAdd(Collections.singletonList(principalId))
                                            .update())
                                    .thenCompose(ignore -> host.sendWithDeferredResult(getUser,
                                            UserState.class));
                        }
                        return DeferredResult.failed(pair.right);
                    }
                    return DeferredResult.completed(pair.left);
                });
    }

    public static DeferredResult<UserGroupState> getOrCreateUserGroup(ServiceHost host, String
            principalId) {
        Operation getUserGroup = Operation.createGet(host,
                UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, principalId))
                .setReferer(host.getUri());

        return getPrincipal(host, principalId)
                .thenCompose(principal -> {
                    if (principal.type != PrincipalType.GROUP) {
                        String message = String.format("Principal %s is not of type GROUP, user "
                                + "group cannot be created.", principal.id);
                        return DeferredResult.failed(new IllegalStateException(message));
                    }
                    return host.sendWithDeferredResult(getUserGroup, UserGroupState.class);
                })
                .thenApply(userGroupState -> new Pair<>(userGroupState, (Throwable) null))
                .exceptionally(ex -> new Pair<>(null, ex))
                .thenCompose(pair -> {
                    if (pair.right != null) {
                        if (pair.right.getCause() instanceof ServiceNotFoundException) {
                            return createUserGroup(host, principalId)
                                    .thenCompose(userGroup -> assignUserGroupToBasicUsers(host,
                                            userGroup))
                                    .thenCompose(ignore -> host.sendWithDeferredResult(
                                            getUserGroup, UserGroupState.class));
                        }
                        return DeferredResult.failed(pair.right);
                    }
                    return DeferredResult.completed(pair.left);
                });
    }

    private static DeferredResult<UserState> createUser(ServiceHost host, String principalId) {
        UserState user = new UserState();
        user.email = principalId;
        user.documentSelfLink = principalId;

        URI userFactoryUri = UriUtils.buildUri(host,
                AuthUtil.buildUserServicePathFromPrincipalId(""));
        Operation postUser = Operation.createPost(userFactoryUri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(user)
                .setReferer(host.getUri());

        addReplicationFactor(postUser);
        return host.sendWithDeferredResult(postUser, UserState.class);
    }

    private static DeferredResult<UserGroupState> createUserGroup(ServiceHost host, String
            principalId) {
        String userGroupSelfLink = UriUtils
                .buildUriPath(UserGroupService.FACTORY_LINK, principalId);
        Query userGroupQuery = AuthUtil.buildQueryForUsers(userGroupSelfLink);

        UserGroupState userGroupState = UserGroupState.Builder.create()
                .withSelfLink(userGroupSelfLink)
                .withQuery(userGroupQuery)
                .build();

        URI userGroupFactoryUri = UriUtils.buildUri(host, ServiceUriPaths.CORE_AUTHZ_USER_GROUPS);
        Operation postGroup = Operation.createPost(userGroupFactoryUri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(userGroupState)
                .setReferer(host.getUri());

        addReplicationFactor(postGroup);
        return host.sendWithDeferredResult(postGroup, UserGroupState.class);
    }

    private static DeferredResult<Void> assignUserGroupToBasicUsers(ServiceHost host,
            UserGroupState state) {

        String userGroupId = Service.getId(state.documentSelfLink);

        RoleState basicUserRole = AuthUtil.buildBasicUsersRole(userGroupId, state.documentSelfLink);

        RoleState basicUserExtendedRole = AuthUtil.buildBasicUsersExtendedRole(userGroupId,
                state.documentSelfLink);

        URI roleFactoryUri = UriUtils.buildUri(host, ServiceUriPaths.CORE_AUTHZ_ROLES);
        Operation postRole = Operation.createPost(roleFactoryUri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(basicUserRole)
                .setReferer(host.getUri());

        Operation postExtendedRole = Operation.createPost(roleFactoryUri)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(basicUserExtendedRole)
                .setReferer(host.getUri());

        addReplicationFactor(postRole);
        addReplicationFactor(postExtendedRole);

        return DeferredResult.allOf(
                host.sendWithDeferredResult(postRole),
                host.sendWithDeferredResult(postExtendedRole));
    }
}
