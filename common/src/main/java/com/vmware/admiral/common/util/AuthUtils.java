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

package com.vmware.admiral.common.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.authn.AuthenticationConstants;
import com.vmware.xenon.services.common.authn.BasicAuthenticationUtils;

public class AuthUtils {

    /*
     * Cache of cleaned up sessions (i.e. sessions whose user has been logged out).
     * This approach works just fine in the VIC context since it's (still) a single node,
     * and in case of restart the first attempt to use a cleaned up session won't find the
     * session state itself and the authorization process will fail in the same way.
     * In cluster scenarios this approach should be revisited, or Xenon should provide an
     * out of the box solution.
     */
    private static final Cache<String, String> cleanedupSessionsCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(90L, TimeUnit.MINUTES)
            .build();

    private static final Field AUTH_CTX_FIELD = ReflectionUtils.getField(Operation.class,
            "authorizationCtx");

    public static String createAuthorizationHeader(AuthCredentialsServiceState authState) {
        if (authState == null) {
            return null;
        }

        AuthCredentialsType authCredentialsType = AuthCredentialsType.valueOf(authState.type);
        if (AuthCredentialsType.Password.equals(authCredentialsType)) {
            String username = authState.userEmail;
            String password = EncryptionUtils.decrypt(authState.privateKey);

            String code = new String(Base64.getEncoder().encode(
                    new StringBuffer(username).append(":").append(password).toString().getBytes()));
            String headerValue = new StringBuffer("Basic ").append(code).toString();

            return headerValue;
        }

        return null;
    }

    /**
     * Validates whether the provided operation relies on some session data which has been
     * marked as cleaned up. In that case, operation's authorization context is replaced
     * with the guest user authentication.
     *
     * @param op
     *            Operation
     * @param guestCtx
     *            AuthorizationContext for the guest user
     */
    public static void validateSessionData(ServiceHost host, Operation op, AuthorizationContext guestCtx, AuthorizationContext authCtx) {
        if (op == null) {
            return;
        }

        if (authCtx == null) {
            try {
                Method getAuthorizationContext = ServiceHost.class
                        .getDeclaredMethod("getAuthorizationContext", Operation.class, Consumer.class);
                getAuthorizationContext.setAccessible(true);

                Consumer<AuthorizationContext> con = (authorizationContext) -> {
                    if (authorizationContext == null || authorizationContext.isSystemUser()) {
                        return;
                    }

                    op.setAuthorizationContext(authorizationContext);
                    validateSessionData(host, op, guestCtx, authorizationContext);
                };

                getAuthorizationContext.invoke(host, op, con);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e ) {
                op.fail(e);
            }
        } else {
            if (authCtx.isSystemUser()) {
                return;
            }

            if (cleanedupSessionsCache.getIfPresent(authCtx.getToken()) != null) {

                Utils.log(AuthUtils.class, AuthUtils.class.getSimpleName(), Level.FINE, "Invalid session '%s'!",
                        authCtx.getToken());

                try {
                    AUTH_CTX_FIELD.set(op, guestCtx);
                } catch (Exception e) {
                    Utils.log(AuthUtils.class, AuthUtils.class.getSimpleName(), Level.WARNING,
                            "Error handling invalid session '%s': %s", authCtx.getToken(), e.getMessage());
                }
            }
        }
    }

    /**
     * Adds the proper headers to the operation response to clean up the session data in
     * the client side. Also, marks the session as cleaned up thus further attempts to use
     * it will default to unauthenticated or guest user authentication.
     *
     * @param op
     *            Operation
     */
    public static void cleanupSessionData(Operation op) {

        String sessionId = BasicAuthenticationUtils.getAuthToken(op);

        if (sessionId != null && !sessionId.isEmpty()) {

            Utils.log(AuthUtils.class, AuthUtils.class.getSimpleName(), Level.FINE,
                    "Cleaning up session '%s'...", sessionId);

            cleanedupSessionsCache.put(sessionId, sessionId);

            op.addResponseHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, "");

            StringBuilder buf = new StringBuilder()
                    .append(AuthenticationConstants.REQUEST_AUTH_TOKEN_COOKIE).append('=')
                    .append(sessionId).append("; Path=/; Max-Age=0");

            op.addResponseHeader(Operation.SET_COOKIE_HEADER, buf.toString());
        }
    }
}
