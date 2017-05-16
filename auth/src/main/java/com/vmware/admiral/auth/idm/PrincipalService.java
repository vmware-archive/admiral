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

import java.util.List;
import java.util.ServiceLoader;

import com.vmware.xenon.common.Utils;

public class PrincipalService implements PrincipalProvider {

    private static final String PREFERRED_PROVIDER_PACKAGE = "com.vmware.admiral.auth.idm.psc";

    private PrincipalProvider provider;

    public PrincipalService() {
        provider = getPreferredProvider(PrincipalProvider.class);
    }

    @Override
    public String getPrincipal(String principalId) {
        return provider.getPrincipal(principalId);
    }

    @Override
    public List<String> getPrincipals(String criteria) {
        return provider.getPrincipals(criteria);
    }

    private static <T> T getPreferredProvider(Class<T> clazz) {

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
