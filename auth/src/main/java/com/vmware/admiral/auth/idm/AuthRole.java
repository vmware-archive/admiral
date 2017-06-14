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

import static com.vmware.admiral.common.util.AssertUtil.assertNotNullOrEmpty;

public enum AuthRole {
    CLOUD_ADMINS("Cloud Admins", "cloud-admins"),
    BASIC_USERS("Basic Users", "basic-users"),
    BASIC_USERS_EXTENDED("Basic Users Extended", "basic-users-extended"),
    PROJECT_ADMINS("Project Admins", "project-admins"),
    PROJECT_MEMBERS("Project Members", "project-members");

    private static final String SUFFIX_SEPARATOR = "_";

    private final String name;
    private final String suffix;

    AuthRole(String name, String prefix) {
        this.name = name;
        this.suffix = prefix;
    }

    public String getName() {
        return name;
    }

    public String getSuffix() {
        return suffix;
    }

    public static AuthRole fromName(String name) {
        assertNotNullOrEmpty(name, "name");
        for (AuthRole r : AuthRole.values()) {
            if (r.name.equals(name)) {
                return r;
            }
        }
        throw new IllegalArgumentException("No matching type for:" + name);
    }

    public static AuthRole fromSuffix(String suffix) {
        assertNotNullOrEmpty(suffix, "suffix");
        for (AuthRole r : AuthRole.values()) {
            if (r.suffix.equals(suffix)) {
                return r;
            }
        }
        throw new IllegalArgumentException("No matching type for:" + suffix);
    }

    public static AuthRole fromSuffixOrName(String suffixOrName) {
        AuthRole result;
        try {
            result = fromName(suffixOrName);
        } catch (IllegalArgumentException ex) {
            try {
                result = fromSuffix(suffixOrName);
            } catch (IllegalArgumentException iae) {
                throw new IllegalArgumentException("No matching type with name or suffix:" +
                        suffixOrName);
            }
        }
        return result;
    }

    public String buildRoleWithSuffix(String identifier) {
        return identifier + SUFFIX_SEPARATOR + getSuffix();
    }
}
