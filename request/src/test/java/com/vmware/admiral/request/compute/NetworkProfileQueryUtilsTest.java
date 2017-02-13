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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.env.ComputeProfileService;
import com.vmware.admiral.compute.env.EnvironmentService;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentState;
import com.vmware.admiral.compute.env.NetworkProfileService;
import com.vmware.admiral.compute.env.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.env.StorageProfileService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class NetworkProfileQueryUtilsTest extends RequestBaseTest {
    private URI referer;

    @Override
    @Before
    public void setUp() throws Throwable {
        startServices(host);
        createEndpoint();
        waitForServiceAvailability(host, ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK);
        referer = UriUtils.buildUri(host, EnvironmentQueryUtilsTest.class.getSimpleName());
    }

    @Test
    public void testGetComputeNicConstraints() throws Throwable {
        ComputeNetworkDescription networkDescription1 = createNetworkDescription("my net");
        String contextId = UUID.randomUUID().toString();
        List<String> subnets1 = Arrays.asList(createSubnet("sub-1").documentSelfLink,
                createSubnet("sub-2").documentSelfLink);
        NetworkProfile networkProfile = createNetworkProfile(subnets1, networkDescription1.tenantLinks);
        @SuppressWarnings("unused")
        ComputeNetwork computeNetwork1 = createComputeNetwork(networkDescription1, contextId, subnets1);
        // Same name, different context
        ComputeNetworkDescription networkDescription2 = createNetworkDescription("my net");
        List<String> subnets2 = Arrays.asList(createSubnet("sub-3").documentSelfLink);
        createComputeNetwork(networkDescription2, UUID.randomUUID().toString(), subnets2);

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        ComputeDescription computeDescription = createComputeDescription(contextId,
                Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        List<String> networkProfileLinks = new ArrayList<>();
        NetworkProfileQueryUtils.getNetworkProfileConstraintsForComputeNics(host, referer,
                contextId, computeDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    networkProfileLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(networkProfileLinks.isEmpty());
        assertEquals(1, networkProfileLinks.size());
        assertEquals(networkProfile.documentSelfLink, networkProfileLinks.iterator().next());
    }

    @Test
    public void testGetComputeNetworkSubnetStatesForTenant() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net");
        List<String> subnets1 = Arrays.asList(createSubnet("sub-1").documentSelfLink,
                createSubnet("sub-2").documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets1, networkDescription.tenantLinks);
        createEnvironment(networkProfile1.documentSelfLink, networkProfile1.tenantLinks);
        List<String> subnets2 = Arrays.asList(createSubnet("sub-3").documentSelfLink);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets2, null);
        createEnvironment(networkProfile2.documentSelfLink, networkProfile2.tenantLinks);

        TestContext ctx = testCreate(1);
        List<String> subnetLinks = new ArrayList<>();
        NetworkProfileQueryUtils.getSubnetsForNetworkDescription(host, referer,
                networkDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnetLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnetLinks.isEmpty());
        assertEquals(2, subnetLinks.size());
        assertTrue(subnetLinks.containsAll(subnets1));
    }

    @Test
    public void testGetComputeNetworkSubnetStatesForSystem() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net");
        List<String> subnets1 = Arrays.asList(createSubnet("sub-1").documentSelfLink,
                createSubnet("sub-2").documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets1, Arrays.asList(UUID.randomUUID().toString()));
        createEnvironment(networkProfile1.documentSelfLink, networkProfile1.tenantLinks);
        List<String> subnets2 = Arrays.asList(createSubnet("sub-3").documentSelfLink);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets2, null);
        createEnvironment(networkProfile2.documentSelfLink, networkProfile2.tenantLinks);

        TestContext ctx = testCreate(1);
        List<String> subnetLinks = new ArrayList<>();
        NetworkProfileQueryUtils.getSubnetsForNetworkDescription(host, referer,
                networkDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnetLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnetLinks.isEmpty());
        assertEquals(1, subnetLinks.size());
        assertTrue(subnetLinks.containsAll(subnets2));
    }

    @Test
    public void testGetSubnetStateForComputeNic() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net");
        List<String> subnets1 = Arrays.asList(createSubnet("sub-1").documentSelfLink,
                createSubnet("sub-2").documentSelfLink);
        NetworkProfile networkProfile = createNetworkProfile(subnets1,
                networkDescription.tenantLinks);
        List<String> subnets2 = Arrays.asList(createSubnet("sub-3").documentSelfLink);
        createNetworkProfile(subnets2, networkDescription.tenantLinks);

        String contextId = UUID.randomUUID().toString();
        createComputeNetwork(networkDescription, contextId,
                subnets1);

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        createComputeDescription(contextId, Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        List<String> subnets = new ArrayList<>();
        NetworkProfileQueryUtils.getSubnetForComputeNic(host, referer, contextId,
                nid, createEnvironment(networkProfile.documentSelfLink, networkProfile.tenantLinks),
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnets.add(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnets.isEmpty());
        assertEquals(1, subnets.size());
        assertTrue(networkProfile.subnetLinks
                .contains(subnets.iterator().next()));
    }

    @Test
    public void testGetSubnetStateForComputeNicNotFound() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net");
        List<String> subnets1 = Arrays.asList(createSubnet("sub-1").documentSelfLink,
                createSubnet("sub-2").documentSelfLink);
        @SuppressWarnings("unused")
        NetworkProfile networkProfile1 = createNetworkProfile(subnets1,
                networkDescription.tenantLinks);
        List<String> subnets2 = Arrays.asList(createSubnet("sub-3").documentSelfLink);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets2,
                networkDescription.tenantLinks);

        String contextId = UUID.randomUUID().toString();
        createComputeNetwork(networkDescription, contextId,
                subnets1);

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        createComputeDescription(contextId, Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        List<String> subnets = new ArrayList<>();
        List<Throwable> exceptions = new ArrayList<>();
        NetworkProfileQueryUtils.getSubnetForComputeNic(host, referer, contextId, nid,
                createEnvironment(networkProfile2.documentSelfLink, networkProfile2.tenantLinks),
                (all, e) -> {
                    if (e != null) {
                        exceptions.add(e);
                        ctx.complete();
                        return;
                    }
                    subnets.add(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(exceptions.isEmpty());
        assertEquals(1, exceptions.size());
        assertTrue(exceptions.iterator().next().getMessage().contains("doesn't satisfy network"));
        assertTrue(subnets.isEmpty());
    }

    private ComputeNetworkDescription createNetworkDescription(String name) throws Throwable {
        ComputeNetworkDescription desc = TestRequestStateFactory.createComputeNetworkDescription(
                name);
        desc.documentSelfLink = UUID.randomUUID().toString();
        return doPost(desc, ComputeNetworkDescriptionService.FACTORY_LINK);
    }

    private NetworkInterfaceDescription createComputeNetworkInterfaceDescription(
            String netName) throws Throwable {
        NetworkInterfaceDescription nid = new NetworkInterfaceDescription();
        nid.name = netName;
        nid.documentSelfLink = UUID.randomUUID().toString();
        return doPost(nid, NetworkInterfaceDescriptionService.FACTORY_LINK);
    }

    private ComputeDescription createComputeDescription(String contextId,
            List<String> networkInterfaceDescriptions) throws Throwable {
        ComputeDescription compute = TestRequestStateFactory.createComputeDescriptionForVmGuestChildren();
        compute.documentSelfLink = UUID.randomUUID().toString();
        compute.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        compute.networkInterfaceDescLinks = networkInterfaceDescriptions;
        return doPost(compute, ComputeDescriptionService.FACTORY_LINK);
    }

    private EnvironmentService.EnvironmentStateExpanded createEnvironment(String networkProfileLink, List<String> tenantLinks)
            throws Throwable {
        StorageProfileService.StorageProfile storageProfile = new StorageProfileService.StorageProfile();
        storageProfile = doPost(storageProfile, StorageProfileService.FACTORY_LINK);
        ComputeProfileService.ComputeProfile computeProfile = new ComputeProfileService.ComputeProfile();
        computeProfile = doPost(computeProfile, ComputeProfileService.FACTORY_LINK);

        EnvironmentState environmentState = TestRequestStateFactory.createEnvironment(
                "env", networkProfileLink, storageProfile.documentSelfLink,
                computeProfile.documentSelfLink);
        environmentState.tenantLinks = tenantLinks;
        environmentState.documentSelfLink = UUID.randomUUID().toString();
        environmentState = doPost(environmentState, EnvironmentService.FACTORY_LINK);
        return getDocument(EnvironmentService.EnvironmentStateExpanded.class,
                EnvironmentService.EnvironmentStateExpanded.buildUri(UriUtils.buildUri(host,
                        environmentState.documentSelfLink)));
    }

    private NetworkProfile createNetworkProfile(List<String> subnetLinks, List<String> tenantLinks)
            throws Throwable {
        NetworkProfile networkProfile = TestRequestStateFactory.createNetworkProfile("net-prof");
        networkProfile.documentSelfLink = UUID.randomUUID().toString();
        networkProfile.subnetLinks = subnetLinks;
        networkProfile.tenantLinks = tenantLinks;
        return doPost(networkProfile, NetworkProfileService.FACTORY_LINK);
    }

    private ComputeNetwork createComputeNetwork(ComputeNetworkDescription computeNetworkDescription,
            String contextId, List<String> subnetLinks)
            throws Throwable {
        ComputeNetwork net = TestRequestStateFactory.createComputeNetworkState(
                "my-net", computeNetworkDescription.documentSelfLink);
        net.documentSelfLink = UUID.randomUUID().toString();
        net.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        net.subnetLinks = subnetLinks;
        return doPost(net, ComputeNetworkService.FACTORY_LINK);
    }

    private SubnetState createSubnet(String name)
            throws Throwable {
        SubnetState subnet = TestRequestStateFactory.createSubnetState(
                name);
        subnet.documentSelfLink = UUID.randomUUID().toString();
        subnet.networkLink = UUID.randomUUID().toString();
        return doPost(subnet, SubnetService.FACTORY_LINK);
    }
}
