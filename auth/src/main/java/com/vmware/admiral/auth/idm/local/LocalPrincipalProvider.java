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

package com.vmware.admiral.auth.idm.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.idm.PrincipalProvider;

public class LocalPrincipalProvider implements PrincipalProvider {

    private static final List<String> PRINCIPALS = Arrays.asList("Fritz", "Gloria", "Connie");

    @Override
    public String getPrincipal(String principalId) {

        if ((principalId == null) || (principalId.isEmpty())) {
            return null;
        }

        Optional<String> principal = PRINCIPALS.stream()
                .filter(p -> p.equalsIgnoreCase(principalId))
                .findFirst();

        return principal.orElse(null);
    }

    @Override
    public List<String> getPrincipals(String criteria) {

        if ((criteria == null) || (criteria.isEmpty())) {
            return Collections.emptyList();
        }

        List<String> pricipals = PRINCIPALS.stream()
                .filter(p -> p.toLowerCase().contains(criteria.toLowerCase()))
                .collect(Collectors.toList());

        return pricipals;
    }

}
