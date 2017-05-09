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

package com.vmware.admiral.compute.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URI;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationState;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfileExpanded;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.UriUtils;

public class NetworkProfileServiceTest extends ComputeBaseTest {

    private static final String NETWORK_ADDRESS = "192.168.0.0";
    private static final int NETWORK_CIDR_PREFIX = 29;
    private static final String NETWORK_CIDR = NETWORK_ADDRESS + "/" + NETWORK_CIDR_PREFIX;
    private static final String NETWORK_LINK = NetworkService.FACTORY_LINK + "/myNetwork";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(NetworkProfileService.FACTORY_LINK);
    }

    @Test
    public void testCreateIsolatedNetworkShouldCreateCIDRAllocation() throws Throwable {
        verifyService(
                FactoryService.create(NetworkProfileService.class),
                NetworkProfile.class,
                (prefix, index) -> {
                    NetworkProfile networkProfile = new NetworkProfile();
                    networkProfile.name = "networkProfileName";
                    networkProfile.isolationNetworkLink = NETWORK_LINK;
                    networkProfile.isolatedSubnetCIDRPrefix = 24;

                    return networkProfile;
                },
                (prefix, serviceDocument) -> {
                    NetworkProfile networkProfile = (NetworkProfile) serviceDocument;
                    assertEquals("networkProfileName", networkProfile.name);
                    assertNotNull(networkProfile.isolationNetworkCIDRAllocationLink);
                });
    }

    @Test
    public void testNoIsolatedNetwork() throws Throwable {
        verifyService(
                FactoryService.create(NetworkProfileService.class),
                NetworkProfile.class,
                (prefix, index) -> {
                    NetworkProfile networkProfile = new NetworkProfile();
                    networkProfile.name = "networkProfileName";
                    networkProfile.isolationNetworkLink = null; // No isolation network set

                    return networkProfile;
                },
                (prefix, serviceDocument) -> {
                    NetworkProfile networkProfile = (NetworkProfile) serviceDocument;

                    assertNull("No CIDRAllocation should be set.",
                            networkProfile.isolationNetworkCIDRAllocationLink);
                });
    }

    @Test
    public void testGetExpandedNetworkProfile() throws Throwable {
        ComputeNetworkCIDRAllocationState cidrAllocation = createNetworkCIDRAllocationState();

        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.name = "networkProfileName";
        networkProfile.isolationNetworkLink = cidrAllocation.networkLink;
        networkProfile.isolationNetworkCIDR = "192.168.0.0/16";

        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

        URI uri = UriUtils.buildUri(host, networkProfile.documentSelfLink);
        URI expandUri = UriUtils.extendUriWithQuery(uri, UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());

        NetworkProfileExpanded expanded = getDocument(NetworkProfileExpanded.class, expandUri);

        assertNotNull(expanded.isolatedNetworkState);
        assertEquals(networkProfile.isolatedSubnetCIDRPrefix, expanded.isolatedSubnetCIDRPrefix);
        assertEquals(networkProfile.isolationNetworkCIDR, expanded.isolationNetworkCIDR);
        assertEquals(networkProfile.isolationNetworkLink, expanded.isolationNetworkLink);
    }

    @Test
    public void testSetIsolatedNetworkWithExistingCIDRAllocation() throws Throwable {
        ComputeNetworkCIDRAllocationState cidrAllocation = createNetworkCIDRAllocationState();

        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.name = "networkProfileName";
        networkProfile.isolationNetworkLink = cidrAllocation.networkLink;

        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

        assertEquals(networkProfile.isolationNetworkCIDRAllocationLink,
                cidrAllocation.documentSelfLink);
    }

    @Test
    public void testChangeIsolatedNetworkShouldChangeCIDRAllocation() throws Throwable {
        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.name = "networkProfileName";
        networkProfile.isolationNetworkLink = NETWORK_LINK;
        networkProfile.isolatedSubnetCIDRPrefix = 24;

        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

        String firstIsolatedCIDRAllocationLink = networkProfile.isolationNetworkCIDRAllocationLink;
        assertNotNull(firstIsolatedCIDRAllocationLink);

        networkProfile.isolationNetworkLink = NETWORK_LINK + "-different";
        networkProfile = doPatch(networkProfile, networkProfile.documentSelfLink);

        String secondIsolatedCIDRAllocationLink = networkProfile.isolationNetworkCIDRAllocationLink;
        assertNotNull(secondIsolatedCIDRAllocationLink);

        assertNotEquals("CIDR Allocation Link should have changed.",
                firstIsolatedCIDRAllocationLink, secondIsolatedCIDRAllocationLink);
    }

    @Test
    public void testRemoveIsolatedNetworkShouldClearCIDRAllocation() throws Throwable {
        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.name = "networkProfileName";
        networkProfile.isolationNetworkLink = NETWORK_LINK;
        networkProfile.isolatedSubnetCIDRPrefix = 24;

        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);
        assertNotNull(networkProfile.isolationNetworkCIDRAllocationLink);

        networkProfile.isolationNetworkLink = ResourceUtils.NULL_LINK_VALUE;
        // Remove isolatedNetworkLink

        networkProfile = doPatch(networkProfile, networkProfile.documentSelfLink);
        assertNull(networkProfile.isolationNetworkCIDRAllocationLink);
    }

    @Test
    public void testChangeIsolatedNetworkCIDRPrefixLength() throws Throwable {
        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.name = "networkProfileName";
        networkProfile.isolationNetworkLink = NETWORK_LINK;
        networkProfile.isolatedSubnetCIDRPrefix = 24;

        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

        networkProfile.isolatedSubnetCIDRPrefix = 20;

        networkProfile = doPatch(networkProfile, networkProfile.documentSelfLink);
        assertEquals((Object)20, networkProfile.isolatedSubnetCIDRPrefix);
    }

    private ComputeNetworkCIDRAllocationState createNetworkCIDRAllocationState() throws Throwable {
        NetworkState network = new NetworkState();
        network.subnetCIDR = NETWORK_CIDR;
        network.name = "IsolatedNetwork";
        network.instanceAdapterReference = UriUtils.buildUri("/instance-adapter-reference");
        network.resourcePoolLink = "/dummy-resource-pool-link";
        network.regionId = "dummy-region-id";
        network = doPost(network, NetworkService.FACTORY_LINK);
        return createNetworkCIDRAllocationState(network.documentSelfLink);
    }

    private ComputeNetworkCIDRAllocationState createNetworkCIDRAllocationState(String networkLink)
            throws Throwable {
        ComputeNetworkCIDRAllocationState state = new ComputeNetworkCIDRAllocationState();
        state.networkLink = networkLink;
        return doPost(state, ComputeNetworkCIDRAllocationService.FACTORY_LINK);
    }
}