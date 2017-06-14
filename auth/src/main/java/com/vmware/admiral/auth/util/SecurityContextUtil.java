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

import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.idm.PrincipalService;
import com.vmware.admiral.auth.idm.SecurityContext;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class SecurityContextUtil {

    /** Gets the {@link SecurityContext} for the currently authenticated user */
    public static DeferredResult<SecurityContext> getSecurityContext(Service requestorService,
            AuthorizationContext authContext) {
        return getSecurityContext(requestorService.getHost(), requestorService.getSelfLink(),
                authContext);
    }

    /** Gets the {@link SecurityContext} for the denoted user */
    public static DeferredResult<SecurityContext> getSecurityContext(Service requestorService,
            String userId) {
        return getSecurityContext(requestorService.getHost(), requestorService.getSelfLink(),
                userId);
    }

    /** Gets the {@link SecurityContext} for the currently authenticated user */
    public static DeferredResult<SecurityContext> getSecurityContext(ServiceHost serviceHost,
            String referer, AuthorizationContext authContext) {
        return getSecurityContext(serviceHost, referer, AuthUtil.getAuthorizedUserId(authContext));
    }

    /** Gets the {@link SecurityContext} for the denoted user */
    public static DeferredResult<SecurityContext> getSecurityContext(ServiceHost serviceHost,
            String referer, String userId) {
        return buildBasicUserInfo(serviceHost, referer, userId, null);
    }

    /**
     * Retrieve basic principal info (id, name, email) and set them to the provided
     * {@link SecurityContext}. If the provided {@link SecurityContext} is <code>null</code>, a new
     * instance will be created, otherwise it will be extended with the new data.
     */
    protected static DeferredResult<SecurityContext> buildBasicUserInfo(ServiceHost serviceHost,
            String referer, String userId, SecurityContext context) {
        if (referer == null || referer.isEmpty()) {
            referer = serviceHost.getUri().toString();
        }

        if (context == null) {
            return buildBasicUserInfo(serviceHost, referer, userId, new SecurityContext());
        }

        return serviceHost.sendWithDeferredResult(
                Operation.createGet(serviceHost,
                        UriUtils.buildUriPath(PrincipalService.SELF_LINK, userId))
                        .setReferer(referer), Principal.class)
                .thenApply((principal) -> {
                    context.id = principal.id;
                    context.email = principal.email;
                    context.name = principal.name;
                    return context;
                });
    }
}
