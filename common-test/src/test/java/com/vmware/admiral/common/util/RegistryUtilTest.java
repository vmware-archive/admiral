/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;

public class RegistryUtilTest extends BaseTestCase {
    private static final String TENANT = "/tenants/current-tenant";
    private static final String DIFFERENT_TENANT = "/tenants/different-tenant";

    @Before
    public void setUp() throws Throwable {
        host.startService(new RegistryFactoryService());
        waitForServiceAvailability(RegistryFactoryService.SELF_LINK);
    }

    @Test
    public void testFindRegistriesByHostnameNoTenant() throws Throwable {
        List<RegistryState> expectedRegistries = new ArrayList<>();
        expectedRegistries.add(createRegistry("https://test.registry.com:5000", null));
        createRegistry("https://test.registry.com:5001", Arrays.asList(TENANT));
        createRegistry("https://test.registry.com:5002", null);

        verifyRegistryLinksByHostname("test.registry.com:5000", (String) null, expectedRegistries);
    }

    @Test
    public void testFindRegistriesByHostnameWithTenant() throws Throwable {
        List<String> tenantLinks = Arrays.asList(TENANT, DIFFERENT_TENANT);

        List<RegistryState> expectedRegistries = new ArrayList<>();
        expectedRegistries.add(createRegistry("https://test.registry.com:5000", tenantLinks));
        createRegistry("https://test.registry.com:5001", tenantLinks);
        createRegistry("http://test.registry.com:5002", tenantLinks);

        verifyRegistryLinksByHostname("test.registry.com:5000", TENANT, expectedRegistries);
    }

    @Test
    public void testFindRegistriesByHostnameWithDifferentTenant() throws Throwable {
        List<RegistryState> expectedRegistries = new ArrayList<>();
        expectedRegistries.add(createRegistry("https://test.registry.com:5000", Arrays.asList(TENANT)));
        createRegistry("https://test.registry.com:5001", null);
        createRegistry("http://test.registry.com:5002", null);

        verifyRegistryLinksByHostname("test.registry.com:5000", DIFFERENT_TENANT,
                Collections.emptyList());
    }

    @Test
    public void testFindRegistriesByHostnameIncludeGlobals() throws Throwable {
        List<String> tenantLinks = Arrays.asList(TENANT, DIFFERENT_TENANT);

        List<RegistryState> expectedRegistries = new ArrayList<>();
        expectedRegistries.add(createRegistry("https://test.registry.com:5000", null));
        createRegistry("https://test.registry.com:5001", null);
        createRegistry("http://test.registry.com:5002", tenantLinks);

        verifyRegistryLinksByHostname("test.registry.com:5000", TENANT, expectedRegistries);
    }

    @Test
    public void testFindRegistriesByHostnameNoFoundLinks() throws Throwable {
        List<String> tenantLinks = Arrays.asList(TENANT, DIFFERENT_TENANT);

        createRegistry("https://test.registry.com:5000", null);
        createRegistry("https://test.registry.com:5001", null);
        createRegistry("http://test.registry.com:5002", tenantLinks);

        verifyRegistryLinksByHostname("test.registry.com:80", TENANT, Collections.emptyList());
    }

    @Test
    public void testFilterRegistriesByPath() throws Throwable {
        List<RegistryState> registries = Arrays.asList(
                createRegistry("https://test.registry.com:5000/vmware", null),
                createRegistry("https://test.registry.com:5000/vmware/test", null),
                createRegistry("https://test.registry.com:5000/test", null),
                createRegistry("https://test.registry.com:5001/vmware", null),
                createRegistry("https://test.registry.com:5001", null)
        );

        host.log("Test same path different hosts");
        DockerImage image = DockerImage.fromParts("test.registry.com:5000", "vmware",
                "admiral", "latest");
        List<RegistryState> filteredRegistries = RegistryUtil.filterRegistriesByPath(host, registries, image);
        assertNotNull(filteredRegistries);
        assertEquals(1, filteredRegistries.size());
        assertEquals("https://test.registry.com:5000/vmware", filteredRegistries.get(0).address);

        host.log("Test image no path");
        image = DockerImage.fromParts("test.registry.com:5001", "",
                "admiral", "latest");
        filteredRegistries = RegistryUtil.filterRegistriesByPath(host, registries, image);
        assertNotNull(filteredRegistries);
        assertEquals(1, filteredRegistries.size());
        assertEquals("https://test.registry.com:5001", filteredRegistries.get(0).address);

        host.log("Test registry no path");
        image = DockerImage.fromParts("test.registry.com:5001", "vmware",
                "admiral", "latest");
        filteredRegistries = RegistryUtil.filterRegistriesByPath(host, registries, image);
        assertNotNull(filteredRegistries);
        assertEquals(2, filteredRegistries.size());
        filteredRegistries.stream().forEach(r -> {
            assertTrue(r.address.contains("test.registry.com:5001"));
        });

        host.log("Test registry multiple paths");
        image = DockerImage.fromParts("test.registry.com:5000", "vmware/test", "vmware", "latest");
        filteredRegistries = RegistryUtil.filterRegistriesByPath(host, registries, image);
        assertNotNull(filteredRegistries);
        assertEquals(2, filteredRegistries.size());
        filteredRegistries.stream().forEach(r -> {
            assertTrue(r.address.contains("test.registry.com:5000/vmware"));
        });
    }


    private void verifyRegistryLinksByHostname(String hostname, String tenantLink,
            Collection<RegistryState> expectedRegistries) {
        verifyRegistryLinksByHostname(hostname,
                tenantLink == null ? null : Collections.singletonList(tenantLink), expectedRegistries);
    }

    private void verifyRegistryLinksByHostname(String hostname, Collection<String> tenantLinks,
            Collection<RegistryState> expectedRegistries) {
        assertNotNull(expectedRegistries);
        host.testStart(1);

        RegistryUtil.findRegistriesByHostname(host, hostname, tenantLinks, (registries, errors) -> {
            if (errors != null && !errors.isEmpty()) {
                host.failIteration(errors.iterator().next());
            }

            try {
                assertNotNull(registries);
                assertEquals("Different number of registries expected.",
                        expectedRegistries.size(), registries.size());
                Set<String> registriesLinks = registries.stream().map(r -> r.documentSelfLink).collect(Collectors.toSet());
                for (RegistryState rs : expectedRegistries) {
                    assertThat(registriesLinks, hasItem(rs.documentSelfLink));
                }
                host.completeIteration();
            } catch (Throwable e) {
                host.failIteration(e);
            }
        });

        host.testWait();
    }

    private RegistryState createRegistry(String address, List<String> tenantLinks)
            throws Throwable {

        RegistryState registryState = new RegistryState();
        registryState.address = address;
        registryState.tenantLinks = tenantLinks;
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;
        registryState = doPost(registryState, RegistryFactoryService.SELF_LINK);
        assertNotNull("Failed to create registry", registryState);

        return registryState;
    }
}
