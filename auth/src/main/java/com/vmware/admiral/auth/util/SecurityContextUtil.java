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

import java.util.HashSet;

import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.SecurityContext;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.UserService.UserState;

public class SecurityContextUtil {

    /** Gets the {@link SecurityContext} for the currently authenticated user */
    public static DeferredResult<SecurityContext> getSecurityContext(Service requestorService,
            AuthorizationContext authContext) {
        return getSecurityContext(requestorService, AuthUtil.getAuthorizedUserId(authContext));
    }

    /** Gets the {@link SecurityContext} for the denoted user */
    public static DeferredResult<SecurityContext> getSecurityContext(Service requestorService,
            String userId) {
        return buildBasicUserInfo(requestorService, userId, null)
                .thenCompose(
                        (context) -> buildDirectSystemRoles(requestorService, userId, context));
    }

    /**
     * Retrieve basic principal info (id, name, email) and set them to the provided
     * {@link SecurityContext}. If the provided {@link SecurityContext} is <code>null</code>, a new
     * instance will be created, otherwise it will be extended with the new data.
     */
    protected static DeferredResult<SecurityContext> buildBasicUserInfo(Service requestorService,
            String userId, SecurityContext context) {
        if (context == null) {
            return buildBasicUserInfo(requestorService, userId, new SecurityContext());
        }

        Operation getOp = Operation
                .createGet(requestorService,
                        UriUtils.buildUriPath(PrincipalService.SELF_LINK, userId))
                .setReferer(requestorService.getUri());
        authorizeOperation(requestorService, getOp);

        return requestorService.getHost().sendWithDeferredResult(getOp, Principal.class)
                .thenApply((principal) -> {
                    context.id = principal.id;
                    context.email = principal.email;
                    context.name = principal.name;
                    return context;
                });
    }

    /**
     * Retrieve the directly assigned roles to the specified user and store them in the provided
     * {@link SecurityContext}. If the provided {@link SecurityContext} is <code>null</code>, a new
     * instance will be created, otherwise it will be extended with the new data.
     */
    protected static DeferredResult<SecurityContext> buildDirectSystemRoles(
            Service requestorService, String userId, SecurityContext context) {
        if (context == null) {
            return buildDirectSystemRoles(requestorService, userId, new SecurityContext());
        }

        Operation getOp = Operation
                .createGet(requestorService, AuthUtils.getUserStateDocumentLink(userId))
                .setReferer(requestorService.getUri());
        authorizeOperation(requestorService, getOp);

        return requestorService.getHost().sendWithDeferredResult(getOp, UserState.class)
                .thenApply((userState) -> {
                    if (context.roles == null) {
                        context.roles = new HashSet<>();
                    }

                    if (userState.userGroupLinks != null && !userState.userGroupLinks.isEmpty()) {
                        AuthUtil.MAP_ROLE_TO_SYSTEM_USER_GROUP.entrySet().stream()
                                .forEach((entry) -> {
                                    if (userState.userGroupLinks.contains(entry.getValue())) {
                                        context.roles.add(entry.getKey());
                                    }
                                });
                    }
                    return context;
                });
    }

    // TODO this will become obsolete when we make the PrincipleService non-privileged and introduce
    // the session service (privileged) which will be setting the system authorization context
    // before issuing the security context for the current user from the PrincipalService
    private static void authorizeOperation(Service requestorService, Operation op) {
        requestorService.setAuthorizationContext(op,
                requestorService.getSystemAuthorizationContext());
    }
}
