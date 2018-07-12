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

import static org.junit.Assert.assertEquals;

import java.util.function.Function;

import org.junit.Test;

public class TenantLinksUtilTest {

    private static final String SAMPLE_TENANT_LINK = "/tenants/some-tenant";
    private static final String SAMPLE_TENANT_LINK_TRAILING_SLASH = SAMPLE_TENANT_LINK + "/";

    private static final String SAMPLE_GROUP_LINK = SAMPLE_TENANT_LINK + "/groups/sample-group";
    private static final String SAMPLE_GROUP_LINK_TRAILING_SLASH = SAMPLE_GROUP_LINK + "/";

    private static final String SAMPLE_PROJECT_LINK = "/projects/some-project";
    private static final String SAMPLE_PROJECT_LINK_TRAILING_SLASH = SAMPLE_PROJECT_LINK + "/";

    private static final String SAMPLE_USER_LINK = "/users/user@admiral.local";
    private static final String SAMPLE_USER_LINK_TRAILING_SLASH = SAMPLE_USER_LINK + "/";

    private static final String SAMPLE_RANDOM_LINK = "/random/link";
    private static final String SAMPLE_RANDOM_LINK_TRAILING_SLASH = SAMPLE_RANDOM_LINK + "/";

    @Test
    public void testIsTenantLink() {
        String functionName = "TenantLinksUtil.isTenantLink";

        assertBooleanOutput(true, functionName, TenantLinksUtil::isTenantLink,
                SAMPLE_TENANT_LINK,
                SAMPLE_TENANT_LINK_TRAILING_SLASH);

        assertBooleanOutput(false, functionName, TenantLinksUtil::isTenantLink,
                SAMPLE_GROUP_LINK,
                SAMPLE_GROUP_LINK_TRAILING_SLASH,
                SAMPLE_PROJECT_LINK,
                SAMPLE_PROJECT_LINK_TRAILING_SLASH,
                SAMPLE_USER_LINK,
                SAMPLE_USER_LINK_TRAILING_SLASH,
                SAMPLE_RANDOM_LINK,
                SAMPLE_RANDOM_LINK_TRAILING_SLASH,
                null);
    }

    @Test
    public void testIsGroupLink() {
        String functionName = "TenantLinksUtil.isGroupLink";

        assertBooleanOutput(true, functionName, TenantLinksUtil::isGroupLink,
                SAMPLE_GROUP_LINK,
                SAMPLE_GROUP_LINK_TRAILING_SLASH);

        assertBooleanOutput(false, functionName, TenantLinksUtil::isGroupLink,
                SAMPLE_TENANT_LINK,
                SAMPLE_TENANT_LINK_TRAILING_SLASH,
                SAMPLE_PROJECT_LINK,
                SAMPLE_PROJECT_LINK_TRAILING_SLASH,
                SAMPLE_USER_LINK,
                SAMPLE_USER_LINK_TRAILING_SLASH,
                SAMPLE_RANDOM_LINK,
                SAMPLE_RANDOM_LINK_TRAILING_SLASH,
                null);
    }

    @Test
    public void testIsProjectLink() {
        String functionName = "TenantLinksUtil.isProjectLink";

        assertBooleanOutput(true, functionName, TenantLinksUtil::isProjectLink,
                SAMPLE_PROJECT_LINK,
                SAMPLE_PROJECT_LINK_TRAILING_SLASH);

        assertBooleanOutput(false, functionName, TenantLinksUtil::isProjectLink,
                SAMPLE_TENANT_LINK,
                SAMPLE_TENANT_LINK_TRAILING_SLASH,
                SAMPLE_GROUP_LINK,
                SAMPLE_GROUP_LINK_TRAILING_SLASH,
                SAMPLE_USER_LINK,
                SAMPLE_USER_LINK_TRAILING_SLASH,
                SAMPLE_RANDOM_LINK,
                SAMPLE_RANDOM_LINK_TRAILING_SLASH,
                null);
    }

    @Test
    public void testIsUserLink() {
        String functionName = "TenantLinksUtil.isUserLink";

        assertBooleanOutput(true, functionName, TenantLinksUtil::isUserLink,
                SAMPLE_USER_LINK,
                SAMPLE_USER_LINK_TRAILING_SLASH);

        assertBooleanOutput(false, functionName, TenantLinksUtil::isUserLink,
                SAMPLE_TENANT_LINK,
                SAMPLE_TENANT_LINK_TRAILING_SLASH,
                SAMPLE_GROUP_LINK,
                SAMPLE_GROUP_LINK_TRAILING_SLASH,
                SAMPLE_PROJECT_LINK,
                SAMPLE_PROJECT_LINK_TRAILING_SLASH,
                SAMPLE_RANDOM_LINK,
                SAMPLE_RANDOM_LINK_TRAILING_SLASH,
                null);
    }

    @Test
    public void testIsNotTenantLink() {
        String functionName = "TenantLinksUtil.isNotTenantLink";

        assertBooleanOutput(false, functionName, TenantLinksUtil::isNotTenantLink,
                SAMPLE_TENANT_LINK,
                SAMPLE_TENANT_LINK_TRAILING_SLASH);

        assertBooleanOutput(true, functionName, TenantLinksUtil::isNotTenantLink,
                SAMPLE_GROUP_LINK,
                SAMPLE_GROUP_LINK_TRAILING_SLASH,
                SAMPLE_PROJECT_LINK,
                SAMPLE_PROJECT_LINK_TRAILING_SLASH,
                SAMPLE_USER_LINK,
                SAMPLE_USER_LINK_TRAILING_SLASH,
                SAMPLE_RANDOM_LINK,
                SAMPLE_RANDOM_LINK_TRAILING_SLASH,
                null);
    }

    @Test
    public void testIsNotGroupLink() {
        String functionName = "TenantLinksUtil.isNotGroupLink";

        assertBooleanOutput(false, functionName, TenantLinksUtil::isNotGroupLink,
                SAMPLE_GROUP_LINK,
                SAMPLE_GROUP_LINK_TRAILING_SLASH);

        assertBooleanOutput(true, functionName, TenantLinksUtil::isNotGroupLink,
                SAMPLE_TENANT_LINK,
                SAMPLE_TENANT_LINK_TRAILING_SLASH,
                SAMPLE_PROJECT_LINK,
                SAMPLE_PROJECT_LINK_TRAILING_SLASH,
                SAMPLE_USER_LINK,
                SAMPLE_USER_LINK_TRAILING_SLASH,
                SAMPLE_RANDOM_LINK,
                SAMPLE_RANDOM_LINK_TRAILING_SLASH,
                null);
    }

    @Test
    public void testIsNotProjectLink() {
        String functionName = "TenantLinksUtil.isNotProjectLink";

        assertBooleanOutput(false, functionName, TenantLinksUtil::isNotProjectLink,
                SAMPLE_PROJECT_LINK,
                SAMPLE_PROJECT_LINK_TRAILING_SLASH);

        assertBooleanOutput(true, functionName, TenantLinksUtil::isNotProjectLink,
                SAMPLE_TENANT_LINK,
                SAMPLE_TENANT_LINK_TRAILING_SLASH,
                SAMPLE_GROUP_LINK,
                SAMPLE_GROUP_LINK_TRAILING_SLASH,
                SAMPLE_USER_LINK,
                SAMPLE_USER_LINK_TRAILING_SLASH,
                SAMPLE_RANDOM_LINK,
                SAMPLE_RANDOM_LINK_TRAILING_SLASH,
                null);
    }

    @Test
    public void testIsNotUserLink() {
        String functionName = "TenantLinksUtil.isNotUserLink";

        assertBooleanOutput(false, functionName, TenantLinksUtil::isNotUserLink,
                SAMPLE_USER_LINK,
                SAMPLE_USER_LINK_TRAILING_SLASH);

        assertBooleanOutput(true, functionName, TenantLinksUtil::isNotUserLink,
                SAMPLE_TENANT_LINK,
                SAMPLE_TENANT_LINK_TRAILING_SLASH,
                SAMPLE_GROUP_LINK,
                SAMPLE_GROUP_LINK_TRAILING_SLASH,
                SAMPLE_PROJECT_LINK,
                SAMPLE_PROJECT_LINK_TRAILING_SLASH,
                SAMPLE_RANDOM_LINK,
                SAMPLE_RANDOM_LINK_TRAILING_SLASH,
                null);
    }

    private void assertBooleanOutput(boolean expectedOutput, String functionName,
            Function<String, Boolean> function, String... input) {
        for (String entry : input) {
            String errorMessage = String.format(
                    "Expected %s('%s') to equal %s.",
                    functionName,
                    entry, expectedOutput);
            assertEquals(errorMessage, expectedOutput, function.apply(entry));
        }
    }

}
