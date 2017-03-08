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

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationState;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.ResourceUtils;
import com.vmware.xenon.common.FactoryService;

public class NetworkProfileServiceTest extends ComputeBaseTest {

    private static final String NETWORK_LINK = NetworkService.FACTORY_LINK + "/myNetwork";

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
    public void testSetIsolatedNetworkWithExistingCIDRAllocation() throws Throwable {

        ComputeNetworkCIDRAllocationState cidrAllocationState = new
                ComputeNetworkCIDRAllocationState();
        cidrAllocationState.networkLink = NETWORK_LINK;
        cidrAllocationState = doPost(cidrAllocationState,
                ComputeNetworkCIDRAllocationService.FACTORY_LINK);

        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.name = "networkProfileName";
        networkProfile.isolationNetworkLink = NETWORK_LINK;

        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

        assertEquals(networkProfile.isolationNetworkCIDRAllocationLink, cidrAllocationState
                .documentSelfLink);
    }

    @Test
    public void testChangeIsolatedNetworkShouldChangeCIDRAllocation() throws Throwable {
        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.name = "networkProfileName";
        networkProfile.isolationNetworkLink = NETWORK_LINK;

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

        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);
        assertNotNull(networkProfile.isolationNetworkCIDRAllocationLink);

        networkProfile.isolationNetworkLink = ResourceUtils.NULL_LINK_VALUE;
        // Remove isolatedNetworkLink

        networkProfile = doPatch(networkProfile, networkProfile.documentSelfLink);
        assertNull(networkProfile.isolationNetworkCIDRAllocationLink);
    }
}