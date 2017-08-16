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

import java.util.Base64;

import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.authn.AuthenticationConstants;
import com.vmware.xenon.services.common.authn.BasicAuthenticationUtils;

public class AuthUtils {

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

    public static void cleanupSessionData(Operation op) {
        String sessionId = BasicAuthenticationUtils.getAuthToken(op);
        if (sessionId != null && !sessionId.isEmpty()) {
            op.addResponseHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, "");

            StringBuilder buf = new StringBuilder()
                    .append(AuthenticationConstants.REQUEST_AUTH_TOKEN_COOKIE).append('=')
                    .append(sessionId).append("; Path=/; Max-Age=0");

            op.addResponseHeader(Operation.SET_COOKIE_HEADER, buf.toString());
        }
    }
}
