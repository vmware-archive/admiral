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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.junit.Test;

import com.vmware.admiral.common.util.RegistryUtil;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

/**
 * Test the RegistryState query in RegistryFactoryService
 */
public class DisabledRegistryStateQueryTest extends BaseRegistryStateQueryTest {
    private static final String GROUPED_REGISTRY_ID = DisabledRegistryStateQueryTest.class
            + "-grouped";

    private static final String TEST_GROUP = "test-group";
    private static final String TEST_GROUP_TENANT_LINK = "/tenants/" + TEST_GROUP;

    private RegistryState registryState;

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add(TEST_GROUP_TENANT_LINK);
        // create a global registry and a grouped registry
        registryState = createRegistry(tenantLinks, GROUPED_REGISTRY_ID, "https://1.0.0.0:5001");
    }

    @Test
    public void testDisabledRegistriesExcluded() throws Throwable {
        // initially the registry is enabled so should be included in results
        waitFor("time out waiting for initially included registry.", () -> {
            RegistryUtil.forEachRegistry(host, TEST_GROUP_TENANT_LINK, null,
                    (registryLinks) -> {
                        System.out.println("REGISTRY LINKS ======> " + registryLinks);
                        if (!registryLinks.contains(registryState.documentSelfLink)) {
                            host.log(Level.SEVERE, "Registry %s missing from results",
                                    registryState.documentSelfLink);
                            return;
                        }
                        expectedResultFound.set(true);
                    }, FAIL_ON_ERROR_HANDLER);
            return expectedResultFound.get();
        });

        // disable the registry
        registryState.disabled = Boolean.TRUE;
        doOperation(registryState,
                UriUtils.buildUri(host, registryState.documentSelfLink), false, Action.PUT);

        // this time expect the grouped registry to be excluded
        waitFor("time out waiting to remove a disabled registry from index..", () -> {
            RegistryUtil.forEachRegistry(host, TEST_GROUP_TENANT_LINK, null,
                    (registryLinks) -> {
                        if (registryLinks.contains(registryState.documentSelfLink)) {
                            host.log(Level.SEVERE, "Disabled registry %s included in results",
                                    registryState.documentSelfLink);
                            return;
                        }
                        expectedResultFound.set(true);
                    }, FAIL_ON_ERROR_HANDLER);
            return expectedResultFound.get();
        });
    }
}
