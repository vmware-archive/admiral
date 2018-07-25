/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TenantLinksUtil {

    public static final Pattern PATTERN_TENANT_LINK = Pattern.compile(String
            .format("^%s[^/]+/?$", QueryUtil.TENANT_IDENTIFIER));

    public static final Pattern PATTERN_GROUP_LINK = Pattern.compile(String
            .format("^%s[^/]+%s[^/]+/?$", QueryUtil.TENANT_IDENTIFIER, QueryUtil.GROUP_IDENTIFIER));

    public static final Pattern PATTERN_PROJECT_LINK = Pattern
            .compile(String.format("^%s[^/]+/?$", QueryUtil.PROJECT_IDENTIFIER));

    public static final Pattern PATTERN_USER_LINK = Pattern.compile(String
            .format("^%s[^/]+/?$", QueryUtil.USER_IDENTIFIER));

    public static boolean isTenantLink(String link) {
        return link != null && PATTERN_TENANT_LINK.matcher(link).matches();
    }

    public static boolean isNotTenantLink(String link) {
        return !isTenantLink(link);
    }

    public static boolean isProjectLink(String link) {
        return link != null && PATTERN_PROJECT_LINK.matcher(link).matches();
    }

    public static boolean isNotProjectLink(String link) {
        return !isProjectLink(link);
    }

    public static boolean isGroupLink(String link) {
        return link != null && PATTERN_GROUP_LINK.matcher(link).matches();
    }

    public static boolean isNotGroupLink(String link) {
        return !isGroupLink(link);
    }

    public static boolean isUserLink(String link) {
        return link != null && PATTERN_USER_LINK.matcher(link).matches();
    }

    public static boolean isNotUserLink(String link) {
        return !isUserLink(link);
    }

    public static Set<String> getProjectAndGroupLinks(Collection<String> tenantLinks) {
        if (tenantLinks == null) {
            return new HashSet<>();
        }

        return tenantLinks.stream()
                .filter(link -> isProjectLink(link) || isGroupLink(link))
                .collect(Collectors.toSet());
    }

}
