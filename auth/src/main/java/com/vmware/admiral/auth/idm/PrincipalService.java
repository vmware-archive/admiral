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

public class PrincipalService implements PrincipalProvider {

    private PrincipalProvider provider;

    public PrincipalService() {
        ServiceLoader<PrincipalProvider> loader = ServiceLoader.load(PrincipalProvider.class);

        for (PrincipalProvider principalProvider : loader) {
            if (provider != null) {
                break;
            }
            provider = principalProvider;
        }

        if (provider == null) {
            throw new IllegalStateException("No PrincipalProvider found!");
        }
    }

    @Override
    public String getPrincipal(String principalId) {
        return provider.getPrincipal(principalId);
    }

    @Override
    public List<String> getPrincipals(String criteria) {
        return provider.getPrincipals(criteria);
    }

}
