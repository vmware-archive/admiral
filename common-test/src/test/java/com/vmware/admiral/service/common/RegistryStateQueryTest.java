/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import static org.junit.Assert.assertFalse;

import java.util.Collections;
import java.util.UUID;
import java.util.logging.Level;

import org.junit.Test;

import com.vmware.admiral.common.util.RegistryUtil;
import com.vmware.admiral.service.common.RegistryService.RegistryState;

/**
 * Test the RegistryState query in RegistryFactoryService
 */
public class RegistryStateQueryTest extends BaseRegistryStateQueryTest {
    private static final String GLOBAL_REGISTRY_ID = RegistryStateQueryTest.class.getSimpleName()
            + "-global-";
    private static final String GROUPED_REGISTRY_ID = RegistryStateQueryTest.class.getSimpleName()
            + "-grouped-";

    private static final String TEST_GROUP = "test-group";
    private static final String TEST_GROUP_TENANT_LINK = "/tenants/" + TEST_GROUP;
    private static final String OTHER_GROUP = "other-test-group";
    private static final String OTHER_GROUP_TENANT_LINK = "/tenants/" + OTHER_GROUP;

    private RegistryState globalRegistryState;
    private RegistryState groupedRegistryState;

    private String globalRegistryId;
    private String groupedRegistryId;

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        // create a global registry and a grouped registry
        globalRegistryId = GLOBAL_REGISTRY_ID + UUID.randomUUID().toString();
        groupedRegistryId = GROUPED_REGISTRY_ID + UUID.randomUUID().toString();

        globalRegistryState = createRegistry(null, globalRegistryId, "https://registry.hub.docker.com");
        groupedRegistryState = createRegistry(Collections.singletonList(TEST_GROUP_TENANT_LINK),
                groupedRegistryId, "https://1.0.0.0:5001");
    }

    /**
     * When querying in TEST_GROUP, both the grouped and global registries should be returned
     *
     * @throws Throwable
     */
    @Test
    public void testGroupedRegistryQueryIncludesGroupAndGlobal() throws Throwable {
        waitFor("Search for registry when global and group are included timed out.", () -> {
            verifyIncludedRegistries(TEST_GROUP_TENANT_LINK, true);
            return expectedResultFound.get();
        });

        assertFalse("Exception during Search for registry when global and group are included.",
                errors.get());
    }

    /**
     * When querying in a group other than TEST_GROUP, the TEST_GROUP registry shouldn't be returned
     * but the global one should still be in the results
     *
     * @throws Throwable
     */
    @Test
    public void testResultsFromOtherGroupsExcluded() throws Throwable {
        waitFor("Search for registry when group exluded timed out.", () -> {
            verifyIncludedRegistries(OTHER_GROUP_TENANT_LINK, false);
            return expectedResultFound.get();
        });

        assertFalse("Exception during search for registry when group exluded", errors.get());
    }

    /**
     * When querying without a group, all registries are returned (global and grouped)
     *
     * @throws Throwable
     */
    @Test
    public void testWithoutGroupIncludesGlobal() throws Throwable {
        waitFor("Search for registry without group timed out.", () -> {
            verifyIncludedRegistries(null, true);
            return expectedResultFound.get();
        });

        assertFalse("Exception during search for registry without group", errors.get());
    }

    private void verifyIncludedRegistries(String tenantLink, boolean shouldIncludeGrouped) {
        RegistryUtil.forEachRegistry(host, tenantLink, null,
                (registryLinks) -> {
                    if (!registryLinks.contains(globalRegistryState.documentSelfLink)) {
                        host.log(Level.SEVERE, "Global registry %s missing",
                                globalRegistryState.documentSelfLink);
                        return;
                    }

                    if (shouldIncludeGrouped ? !registryLinks
                            .contains(groupedRegistryState.documentSelfLink) : registryLinks
                                    .contains(groupedRegistryState.documentSelfLink)) {
                        host.log(Level.SEVERE, "Grouped registry %s should%s be included",
                                groupedRegistryState.documentSelfLink, shouldIncludeGrouped ? ""
                                        : "n't");
                        return;
                    }
                    expectedResultFound.set(true);
                }, FAIL_ON_ERROR_HANDLER);
    }
}
