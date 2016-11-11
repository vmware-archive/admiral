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

package com.vmware.admiral.common.util;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;

public class RegistryUtilTest extends BaseTestCase {
    private static final String TENANT = "/tenants/current-tenant";
    private static final String DIFFERENT_TENANT = "/tenants/different-tenant";

    @Before
    public void setUp() throws Throwable {
        host.startFactory(new RegistryService());
        waitForServiceAvailability(RegistryService.FACTORY_LINK);
    }

    @Test
    public void testFindRegistriesByHostnameNoTenant() throws Throwable {
        List<String> expectedLinks = new ArrayList<>();
        expectedLinks.add(createRegistry("https://test.registry.com:5000", null));
        createRegistry("https://test.registry.com:5001", Arrays.asList(TENANT));
        createRegistry("https://test.registry.com:5002", null);

        verifyRegistryLinksByHostname("test.registry.com:5000", null, expectedLinks);
    }

    @Test
    public void testFindRegistriesByHostnameWithTenant() throws Throwable {
        List<String> tenantLinks = Arrays.asList(TENANT, DIFFERENT_TENANT);

        List<String> expectedLinks = new ArrayList<>();
        expectedLinks.add(createRegistry("https://test.registry.com:5000", tenantLinks));
        createRegistry("https://test.registry.com:5001", tenantLinks);
        createRegistry("http://test.registry.com:5002", tenantLinks);

        verifyRegistryLinksByHostname("test.registry.com:5000", TENANT, expectedLinks);
    }

    @Test
    public void testFindRegistriesByHostnameWithDifferentTenant() throws Throwable {
        List<String> expectedLinks = new ArrayList<>();
        expectedLinks.add(createRegistry("https://test.registry.com:5000", Arrays.asList(TENANT)));
        createRegistry("https://test.registry.com:5001", null);
        createRegistry("http://test.registry.com:5002", null);

        verifyRegistryLinksByHostname("test.registry.com:5000", DIFFERENT_TENANT,
                Collections.emptyList());
    }

    @Test
    public void testFindRegistriesByHostnameIncludeGlobals() throws Throwable {
        List<String> tenantLinks = Arrays.asList(TENANT, DIFFERENT_TENANT);

        List<String> expectedLinks = new ArrayList<>();
        expectedLinks.add(createRegistry("https://test.registry.com:5000", null));
        createRegistry("https://test.registry.com:5001", null);
        createRegistry("http://test.registry.com:5002", tenantLinks);

        verifyRegistryLinksByHostname("test.registry.com:5000", TENANT, expectedLinks);
    }

    @Test
    public void testFindRegistriesByHostnameNoFoundLinks() throws Throwable {
        List<String> tenantLinks = Arrays.asList(TENANT, DIFFERENT_TENANT);

        createRegistry("https://test.registry.com:5000", null);
        createRegistry("https://test.registry.com:5001", null);
        createRegistry("http://test.registry.com:5002", tenantLinks);

        verifyRegistryLinksByHostname("test.registry.com:80", TENANT, Collections.emptyList());
    }

    private void verifyRegistryLinksByHostname(String hostname, String tenantLink,
            Collection<String> expectedLinks) {
        assertNotNull(expectedLinks);
        host.testStart(1);

        RegistryUtil.findRegistriesByHostname(host, hostname, tenantLink, (links, errors) -> {
            if (errors != null && !errors.isEmpty()) {
                host.failIteration(errors.iterator().next());
            }

            try {
                assertNotNull(links);
                assertEquals("Different number of links expected.",
                        expectedLinks.size(), links.size());
                for (String link: expectedLinks) {
                    assertThat(links, hasItem(link));
                }
                host.completeIteration();
            } catch (Throwable e) {
                host.failIteration(e);
            }
        });

        host.testWait();
    }

    private String createRegistry(String address, List<String> tenantLinks)
            throws Throwable {

        RegistryState registryState = new RegistryState();
        registryState.address = address;
        registryState.tenantLinks = tenantLinks;
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;
        registryState = doPost(registryState, RegistryService.FACTORY_LINK);
        assertNotNull("Failed to create registry", registryState);

        return registryState.documentSelfLink;
    }
}
