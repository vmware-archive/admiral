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

import java.util.Set;

public class Principal {

    public enum PrincipalType {
        USER, GROUP
    }

    public enum PrincipalSource {
        LOCAL, LDAP
    }

    public String id;

    public String password;

    public String name;

    public String email;

    public Set<String> groups;

    public PrincipalType type;

    public PrincipalSource source;

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.toLowerCase().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !Principal.class.isAssignableFrom(obj.getClass())) {
            return false;
        }

        Principal principal = (Principal) obj;
        if (id == null) {
            if (principal.id != null) {
                return false;
            }
        } else if (!id.equalsIgnoreCase(principal.id)) {
            return false;
        }

        return true;
    }

}
