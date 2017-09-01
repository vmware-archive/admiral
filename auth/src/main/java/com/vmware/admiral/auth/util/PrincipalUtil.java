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

import static com.vmware.admiral.auth.util.AuthUtil.addReplicationFactor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.Principal.PrincipalSource;
import com.vmware.admiral.auth.idm.Principal.PrincipalType;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
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
    public static final String ENCODE_MARKER = "@";

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
        principal.source = PrincipalSource.LOCAL;

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

    public static DeferredResult<Principal> getPrincipal(Service requestorService,
            Operation requestorOperation, String principalId) {
        Operation getOp = Operation.createGet(requestorService,
                UriUtils.buildUriPath(PrincipalService.SELF_LINK, encode(principalId)));

        requestorService.setAuthorizationContext(getOp,
                requestorOperation.getAuthorizationContext());

        return requestorService.sendWithDeferredResult(getOp, Principal.class);
    }

    public static Pair<String, String> toNameAndDomain(String principalId) {
        String decodedPrincipalId = decode(principalId);
        // UPN format: NAME@DOMAIN
        String[] parts = decodedPrincipalId.split("@");
        if (parts.length == 2) {
            return new Pair<>(parts[0], parts[1]);
        }

        // NETBIOS format: DOMAIN\NAME
        parts = decodedPrincipalId.split("\\\\");
        if (parts.length == 2) {
            return new Pair<>(parts[1], parts[0]);
        }

        throw new IllegalArgumentException("Invalid principalId format: '" + decodedPrincipalId + "'");
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

    private static String toPrincipalName(String firstName, String lastName) {
        StringBuilder sb = new StringBuilder();
        if ((firstName != null) && (!firstName.trim().isEmpty())) {
            sb.append(firstName.trim());
        }
        if ((lastName != null) && (!lastName.trim().isEmpty())) {
            sb.append(" ").append(lastName.trim());
        }
        return sb.toString();
    }

    public static String toPrincipalName(String firstName, String lastName, String defaultName) {
        String name = toPrincipalName(firstName, lastName).trim();
        return (!name.isEmpty()) ? name : defaultName;
    }

    private static String toPrincipalName(LocalPrincipalState state) {
        if ((state.name != null) && (!state.name.trim().isEmpty())) {
            return state.name;
        }
        return state.id.split("@")[0];
    }

    public static DeferredResult<Principal> getPrincipal(Service service, String principalId) {
        Operation getPrincipalOp = Operation.createGet(service, UriUtils.buildUriPath(
                PrincipalService.SELF_LINK, encode(principalId)));

        return service.sendWithDeferredResult(getPrincipalOp, Principal.class);
    }

    public static DeferredResult<UserState> getOrCreateUser(Service service, String principalId) {
        String encodedPrincipalId = encode(principalId);
        Operation getUser = Operation.createGet(service,
                AuthUtil.buildUserServicePathFromPrincipalId(encodedPrincipalId));

        return getPrincipal(service, encodedPrincipalId)
                .thenCompose(principal -> {
                    if (principal.type != PrincipalType.USER) {
                        String message = String.format("Principal %s is not of type USER, user "
                                + "state cannot be created.", principal.id);
                        return DeferredResult.failed(new IllegalStateException(message));
                    }
                    return service.sendWithDeferredResult(getUser, UserState.class);
                })
                .thenApply(userState -> new Pair<>(userState, (Throwable) null))
                .exceptionally(ex -> new Pair<>(null, ex))
                .thenCompose(pair -> {
                    if (pair.right != null) {
                        if (pair.right.getCause() instanceof ServiceNotFoundException) {
                            // Create the user and assign basic user role.
                            return createUser(service, principalId)
                                    .thenCompose(user -> UserGroupsUpdater.create()
                                            .setService(service)
                                            .setGroupLink(AuthUtil.BASIC_USERS_USER_GROUP_LINK)
                                            .setUsersToAdd(Collections.singletonList(encodedPrincipalId))
                                            .update())
                                    .thenCompose(ignore -> service.sendWithDeferredResult(getUser,
                                            UserState.class));
                        }
                        return DeferredResult.failed(pair.right);
                    }
                    return DeferredResult.completed(pair.left);
                });
    }

    public static DeferredResult<UserGroupState> getOrCreateUserGroup(Service service,
            String principalId) {
        Operation getUserGroup = Operation.createGet(service,
                UriUtils.buildUriPath(UserGroupService.FACTORY_LINK, encode(principalId)));

        return getPrincipal(service, principalId)
                .thenCompose(principal -> {
                    if (principal.type != PrincipalType.GROUP) {
                        String message = String.format("Principal %s is not of type GROUP, user "
                                + "group cannot be created.", principal.id);
                        return DeferredResult.failed(new IllegalStateException(message));
                    }
                    return service.sendWithDeferredResult(getUserGroup, UserGroupState.class);
                })
                .thenApply(userGroupState -> new Pair<>(userGroupState, (Throwable) null))
                .exceptionally(ex -> new Pair<>(null, ex))
                .thenCompose(pair -> {
                    if (pair.right != null) {
                        if (pair.right.getCause() instanceof ServiceNotFoundException) {
                            return createUserGroup(service, principalId)
                                    .thenCompose(userGroup -> assignUserGroupToBasicUsers(service,
                                            userGroup))
                                    .thenCompose(ignore -> service.sendWithDeferredResult(
                                            getUserGroup, UserGroupState.class));
                        }
                        return DeferredResult.failed(pair.right);
                    }
                    return DeferredResult.completed(pair.left);
                });
    }

    private static DeferredResult<UserState> createUser(Service service, String principalId) {
        UserState user = new UserState();
        user.email = decode(principalId);
        user.documentSelfLink = encode(principalId);

        Operation postUser = Operation
                .createPost(service, AuthUtil.buildUserServicePathFromPrincipalId(""))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(user);

        addReplicationFactor(postUser);
        return service.sendWithDeferredResult(postUser, UserState.class);
    }

    private static DeferredResult<UserGroupState> createUserGroup(Service service,
            String principalId) {
        String userGroupSelfLink = UriUtils
                .buildUriPath(UserGroupService.FACTORY_LINK, encode(principalId));
        Query userGroupQuery = AuthUtil.buildQueryForUsers(userGroupSelfLink);

        UserGroupState userGroupState = UserGroupState.Builder.create()
                .withSelfLink(userGroupSelfLink)
                .withQuery(userGroupQuery)
                .build();

        Operation postGroup = Operation.createPost(service, ServiceUriPaths.CORE_AUTHZ_USER_GROUPS)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(userGroupState);

        addReplicationFactor(postGroup);
        return service.sendWithDeferredResult(postGroup, UserGroupState.class);
    }

    private static DeferredResult<Void> assignUserGroupToBasicUsers(Service service,
            UserGroupState state) {

        String userGroupId = Service.getId(state.documentSelfLink);

        RoleState basicUserRole = AuthUtil.buildBasicUsersRole(userGroupId, state.documentSelfLink);

        RoleState basicUserExtendedRole = AuthUtil.buildBasicUsersExtendedRole(userGroupId,
                state.documentSelfLink);

        Operation postRole = Operation.createPost(service, ServiceUriPaths.CORE_AUTHZ_ROLES)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(basicUserRole);

        Operation postExtendedRole = Operation.createPost(service, ServiceUriPaths.CORE_AUTHZ_ROLES)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(basicUserExtendedRole);

        addReplicationFactor(postRole);
        addReplicationFactor(postExtendedRole);

        return DeferredResult.allOf(
                service.sendWithDeferredResult(postRole),
                service.sendWithDeferredResult(postExtendedRole));
    }

    public static String encode(String principalId) {
        if (principalId == null || principalId.isEmpty()) {
            return principalId;
        }

        if (!principalId.contains(ENCODE_MARKER)) {
            return principalId;
        }

        return new String(Base64.getUrlEncoder().encode(
                principalId.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    public static String decode(String principalId) {
        if (principalId == null || principalId.isEmpty()) {
            return principalId;
        }

        if (principalId.contains(ENCODE_MARKER)) {
            return principalId;
        }

        try {
            return new String(Base64.getUrlDecoder().decode(
                    principalId.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException iae) {
            if (iae.getMessage().contains("Illegal base64 character")) {
                // In this case principal id is not encoded string without @ sign in it
                // so the decoding is failing, we return the same string.
                return principalId;
            }
            throw new RuntimeException(iae);
        }
    }
}
