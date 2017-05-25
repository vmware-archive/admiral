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

import java.util.Base64;
import java.util.ServiceLoader;
import java.util.logging.Level;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AuthUtil {

    public static final String LOCAL_USERS_FILE = "localUsers";

    public static final String AUTH_CONFIG_FILE = "authConfig";

    private static final String PREFERRED_PROVIDER_PACKAGE = "com.vmware.admiral.auth.idm.psc";

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

    public static boolean isAuthxEnabled(ServiceHost host) {
        return useLocalUsers(host) || useAuthConfig(host);
    }

    public static boolean useLocalUsers(ServiceHost host) {
        String field = getLocalUsersFile(host);
        return (field != null) && (!field.isEmpty());
    }

    public static boolean useAuthConfig(ServiceHost host) {
        String field = getAuthConfigFile(host);
        return (field != null) && (!field.isEmpty());
    }

    public static String getLocalUsersFile(ServiceHost host) {
        try {
            return PropertyUtils.getValue(host, LOCAL_USERS_FILE);
        } catch (Exception e) {
            host.log(Level.SEVERE, Utils.toString(e));
            return null;
        }
    }

    public static String getAuthConfigFile(ServiceHost host) {
        return PropertyUtils.getValue(host, AUTH_CONFIG_FILE);
    }

    public static <T> T getPreferredProvider(Class<T> clazz) {

        ServiceLoader<T> loader = ServiceLoader.load(clazz);

        T provider = null;

        for (T loaderProvider : loader) {
            if (provider != null
                    && provider.getClass().getName().startsWith(PREFERRED_PROVIDER_PACKAGE)) {
                Utils.logWarning("Ignoring provider '%s'.", loaderProvider.getClass().getName());
                continue;
            }

            Utils.logWarning("Using provider '%s'.", loaderProvider.getClass().getName());
            provider = loaderProvider;
        }

        if (provider == null) {
            throw new IllegalStateException("No provider found!");
        }

        return provider;
    }

}
