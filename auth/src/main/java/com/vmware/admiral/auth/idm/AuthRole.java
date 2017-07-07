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
    CLOUD_ADMIN("cloud-admins"),
    BASIC_USER("basic-users"),
    BASIC_USER_EXTENDED("basic-users-extended"),
    PROJECT_ADMIN("project-admins"),
    PROJECT_MEMBER("project-members"),
    PROJECT_MEMBER_EXTENDED("project-members-extended"),
    PROJECT_VIEWER("project-viewers");

    public static final String SUFFIX_SEPARATOR = "_";

    private final String suffix;

    AuthRole(String prefix) {
        this.suffix = prefix;
    }

    public String getSuffix() {
        return suffix;
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

    public String buildRoleWithSuffix(String... identifiers) {
        return String.join(SUFFIX_SEPARATOR, identifiers) + SUFFIX_SEPARATOR + getSuffix();
    }
}
