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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.Sets;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.compute.profile.ComputeProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.Constraint.Condition;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
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
        referer = UriUtils.buildUri(host, ProfileQueryUtilsTest.class.getSimpleName());
    }

    @Test
    public void testGetComputeNicProfileConstraints() throws Throwable {
        ComputeNetworkDescription networkDescription1 = createNetworkDescription("my net", null);
        String contextId = UUID.randomUUID().toString();
        List<String> subnets = Arrays.asList(
                createSubnet("sub-1", networkDescription1.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription1.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets,
                networkDescription1.tenantLinks, null);
        ProfileState profile1 = createProfile(networkProfile1.documentSelfLink,
                networkProfile1.tenantLinks, null);
        createComputeNetwork(networkDescription1, contextId,
                Arrays.asList(profile1.documentSelfLink));
        // Same name, different context
        ComputeNetworkDescription networkDescription2 = createNetworkDescription("my net", null);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets,
                networkDescription1.tenantLinks, null);
        ProfileState profile2 = createProfile(networkProfile2.documentSelfLink,
                networkProfile2.tenantLinks, null);
        createComputeNetwork(networkDescription2, UUID.randomUUID().toString(), Arrays.asList(
                profile2.documentSelfLink));

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        ComputeDescription computeDescription = createComputeDescription(contextId,
                Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        Set<String> profileLinks = new HashSet<>();
        NetworkProfileQueryUtils.getProfilesForComputeNics(host, referer,
                networkDescription1.tenantLinks,
                contextId, computeDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    profileLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(profileLinks.isEmpty());
        assertEquals(1, profileLinks.size());
        assertEquals(profile1.documentSelfLink, profileLinks.iterator().next());
    }

    @Test
    public void testGetComputeNetworkProfilesForTenant() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net", null);
        List<String> subnets1 = Arrays.asList(
                createSubnet("sub-1", networkDescription.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets1,
                networkDescription.tenantLinks, null);
        ProfileState profile1 = createProfile(networkProfile1.documentSelfLink,
                networkProfile1.tenantLinks, null);
        List<String> subnets2 = Arrays.asList(createSubnet("sub-3", null, null).documentSelfLink);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets2, null, null);
        createProfile(networkProfile2.documentSelfLink, networkProfile2.tenantLinks, null);

        TestContext ctx = testCreate(1);
        Set<String> profileLinks = new HashSet<>();
        NetworkProfileQueryUtils.getProfilesForNetworkDescription(host, referer,
                networkDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    profileLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(profileLinks.isEmpty());
        assertEquals(1, profileLinks.size());
        assertEquals(profile1.documentSelfLink, profileLinks.iterator().next());
    }

    @Test
    public void testGetComputeNetworkProfilesForSystem() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net", null);
        List<String> tenantLinks = Arrays.asList(UUID.randomUUID().toString());
        List<String> subnets1 = Arrays.asList(
                createSubnet("sub-1", tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets1, tenantLinks, null);
        createProfile(networkProfile1.documentSelfLink, networkProfile1.tenantLinks, null);

        SubnetState subnet3 = createSubnet("sub-3", null, null);
        List<String> subnets2 = Arrays.asList(subnet3.documentSelfLink);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets2, subnet3.tenantLinks, null);
        ProfileState profileState = createProfile(networkProfile2.documentSelfLink,
                networkProfile2.tenantLinks, null);

        TestContext ctx = testCreate(1);
        Set<String> profileLinks = new HashSet<>();
        NetworkProfileQueryUtils.getProfilesForNetworkDescription(host, referer,
                networkDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    profileLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(profileLinks.isEmpty());
        assertEquals(1, profileLinks.size());
        assertEquals(profileState.documentSelfLink, profileLinks.iterator().next());
    }

    @Test
    public void testGetComputeNetworkProfilesWithConstraints() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net",
                Arrays.asList(TestRequestStateFactory.createCondition("cap1", "pci1", true, false),
                        TestRequestStateFactory.createCondition("cap2", "pci2", true, false),
                        TestRequestStateFactory.createCondition("cap3", "pci3", true, false)));
        List<String> subnets1 = Arrays.asList(
                createSubnet("sub-1", networkDescription.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription.tenantLinks,
                        Sets.newHashSet(createTag("cap1", "pci1",
                                networkDescription.tenantLinks))).documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets1,
                networkDescription.tenantLinks, Sets.newHashSet(createTag("cap2", "pci2",
                        networkDescription.tenantLinks)));
        ProfileState profile1 = createProfile(networkProfile1.documentSelfLink,
                networkProfile1.tenantLinks, Sets.newHashSet(createTag("cap3", "pci3",
                        networkDescription.tenantLinks)));

        TestContext ctx = testCreate(1);
        Set<String> profileLinks = new HashSet<>();
        NetworkProfileQueryUtils.getProfilesForNetworkDescription(host, referer,
                networkDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    profileLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(profileLinks.isEmpty());
        assertEquals(1, profileLinks.size());
        assertEquals(profile1.documentSelfLink, profileLinks.iterator().next());
    }

    @Test
    public void testGetComputeNetworkProfilesWithConstraintsNotSatisfied() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net",
                Arrays.asList(TestRequestStateFactory.createCondition("cap", "pci", true, false)));
        List<String> subnets1 = Arrays.asList(
                createSubnet("sub-1", networkDescription.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription.tenantLinks,
                        Sets.newHashSet(createTag("cap", "notPci",
                                networkDescription.tenantLinks))).documentSelfLink);
        NetworkProfile networkProfile1 = createNetworkProfile(subnets1,
                networkDescription.tenantLinks, null);
        createProfile(networkProfile1.documentSelfLink, networkProfile1.tenantLinks, null);

        TestContext ctx = testCreate(1);
        Set<String> profileLinks = new HashSet<>();
        Set<Throwable> exceptions = new HashSet<>();
        NetworkProfileQueryUtils.getProfilesForNetworkDescription(host, referer,
                networkDescription,
                (all, e) -> {
                    if (e != null) {
                        exceptions.add(e);
                        ctx.complete();
                        return;
                    }
                    profileLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(exceptions.isEmpty());
        assertEquals(1, exceptions.size());
        assertTrue(exceptions.iterator().next().getMessage()
                .contains("Could not find any profiles"));
    }

    @Test
    public void testGetSubnetStateForComputeNic() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net", null);
        List<String> subnets1 = Arrays.asList(
                createSubnet("sub-1", networkDescription.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile = createNetworkProfile(subnets1,
                networkDescription.tenantLinks, null);
        ProfileStateExpanded profileState = createProfile(
                networkProfile.documentSelfLink, networkProfile.tenantLinks, null);
        List<String> subnets2 = Arrays.asList(
                createSubnet("sub-3", networkDescription.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets2,
                networkDescription.tenantLinks, null);
        createProfile(networkProfile2.documentSelfLink, networkProfile2.tenantLinks, null);

        String contextId = UUID.randomUUID().toString();
        ComputeNetwork computeNetwork = createComputeNetwork(networkDescription, contextId, null);

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        createComputeDescription(contextId, Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        Set<String> subnets = new HashSet<>();
        NetworkProfileQueryUtils.getSubnetForComputeNic(computeNetwork, networkDescription,
                profileState,
                (s, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnets.add(s.documentSelfLink);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnets.isEmpty());
        assertEquals(1, subnets.size());
        assertTrue(subnets1.contains(subnets.iterator().next()));
    }

    @Test
    public void testGetSubnetStateForComputeNicIsolatedNetwork() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net", null,
                NetworkType.ISOLATED);
        List<String> subnets1 = Arrays.asList(
                createSubnet("sub-1", networkDescription.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile = createNetworkProfile(subnets1,
                networkDescription.tenantLinks, null, IsolationSupportType.SUBNET);
        ProfileStateExpanded profileState = createProfile(
                networkProfile.documentSelfLink, networkProfile.tenantLinks, null);

        String contextId = UUID.randomUUID().toString();
        ComputeNetwork computeNetwork = createComputeNetwork(networkDescription, contextId, null);

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        createComputeDescription(contextId, Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        Set<SubnetState> subnets = new HashSet<>();
        NetworkProfileQueryUtils.getSubnetForComputeNic(computeNetwork, networkDescription,
                profileState,
                (s, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnets.add(s);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnets.isEmpty());
        assertEquals(1, subnets.size());
        SubnetState isolatedSubnet = subnets.iterator().next();
        assertNotNull(isolatedSubnet.networkLink);
        assertNotNull(isolatedSubnet.subnetCIDR);
    }

    @Test
    public void testGetSubnetStateForComputeNicNotFound() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net", null);
        NetworkProfile networkProfile1 = createNetworkProfile(null, networkDescription.tenantLinks,
                null);
        ProfileStateExpanded profileState = createProfile(
                networkProfile1.documentSelfLink, networkProfile1.tenantLinks, null);
        List<String> subnets2 = Arrays.asList(
                createSubnet("sub-3", networkDescription.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile2 = createNetworkProfile(subnets2,
                networkDescription.tenantLinks, null);
        createProfile(networkProfile2.documentSelfLink, networkProfile2.tenantLinks, null);

        String contextId = UUID.randomUUID().toString();
        ComputeNetwork computeNetwork = createComputeNetwork(networkDescription, contextId, null);

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        createComputeDescription(contextId, Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        List<String> subnets = new ArrayList<>();
        List<Throwable> exceptions = new ArrayList<>();
        NetworkProfileQueryUtils.getSubnetForComputeNic(computeNetwork, networkDescription,
                profileState,
                (s, e) -> {
                    if (e != null) {
                        exceptions.add(e);
                        ctx.complete();
                        return;
                    }
                    subnets.add(s.documentSelfLink);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(exceptions.isEmpty());
        assertEquals(1, exceptions.size());
        assertTrue(exceptions.iterator().next().getMessage().contains("doesn't satisfy network"));
        assertTrue(subnets.isEmpty());
    }

    @Test
    public void testGetSubnetStateForComputeNicConstraints() throws Throwable {
        ComputeNetworkDescription networkDescription = createNetworkDescription("my net",
                Arrays.asList(TestRequestStateFactory.createCondition("cap", "pci", true, false)));
        SubnetState subnetState1 = createSubnet("sub-1", networkDescription.tenantLinks,
                Sets.newHashSet(createTag("cap", "notPci", networkDescription.tenantLinks)));
        SubnetState subnetState2 = createSubnet("sub-1", networkDescription.tenantLinks,
                Sets.newHashSet(createTag("cap", "pci", networkDescription.tenantLinks)));
        NetworkProfile networkProfile = createNetworkProfile(
                Arrays.asList(subnetState1.documentSelfLink,
                        subnetState2.documentSelfLink),
                networkDescription.tenantLinks, null);
        ProfileStateExpanded profileState = createProfile(
                networkProfile.documentSelfLink,
                networkProfile.tenantLinks, null);

        String contextId = UUID.randomUUID().toString();
        ComputeNetwork computeNetwork = createComputeNetwork(networkDescription, contextId, null);

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        createComputeDescription(contextId, Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        Set<String> subnets = new HashSet<>();
        NetworkProfileQueryUtils.getSubnetForComputeNic(computeNetwork, networkDescription,
                profileState,
                (s, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnets.add(s.documentSelfLink);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnets.isEmpty());
        assertEquals(1, subnets.size());
        assertEquals(subnetState2.documentSelfLink, subnets.iterator().next());
    }

    @Test
    public void testGetContextComputeNetworksByName() throws Throwable {
        ComputeNetworkDescription networkDescription1 = createNetworkDescription("my net", null);
        String contextId = UUID.randomUUID().toString();
        List<String> subnets = Arrays.asList(
                createSubnet("sub-1", networkDescription1.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", networkDescription1.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile = createNetworkProfile(subnets,
                networkDescription1.tenantLinks, null, IsolationSupportType.SECURITY_GROUP);
        ProfileState profile = createProfile(networkProfile.documentSelfLink,
                networkProfile.tenantLinks, null);
        createComputeNetwork(networkDescription1, contextId,
                Arrays.asList(profile.documentSelfLink));
        // Same name, different context
        ComputeNetworkDescription networkDescription2 = createNetworkDescription("my net", null);
        createComputeNetwork(networkDescription2, UUID.randomUUID().toString(), Arrays.asList(
                profile.documentSelfLink));
        // Different name, same context
        ComputeNetworkDescription networkDescription3 = createNetworkDescription("my other net",
                null);
        createComputeNetwork(networkDescription3, UUID.randomUUID().toString(), Arrays.asList(
                profile.documentSelfLink));

        Set<String> networkNames = new HashSet<>();
        networkNames.add("my net");

        TestContext ctx = testCreate(1);
        Set<ComputeNetwork> networks = new HashSet<>();
        NetworkProfileQueryUtils.getContextComputeNetworksByName(host, referer,
                networkDescription1.tenantLinks,
                contextId, networkNames,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    networks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(networks.isEmpty());
        assertEquals(1, networks.size());
    }

    @Test
    public void testCreateNicState() throws Throwable {
        SubnetState subnet = createSubnet("sub",
                TestRequestStateFactory.createTenantLinks("test-group"), null);
        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        List<String> profileSecurityGroupLinks = Collections.singletonList("dummy");
        ComputeDescription cd = createComputeDescription(UUID.randomUUID().toString(),
                Collections.singletonList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        Set<NetworkInterfaceState> networkInterfaceStates = new HashSet<>();
        NetworkProfileQueryUtils.createNicState(subnet, subnet.tenantLinks, null, cd,
                nid, null, profileSecurityGroupLinks)
                .whenComplete((nis, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    networkInterfaceStates.add(nis);
                    ctx.complete();
                });

        ctx.await();

        assertFalse(networkInterfaceStates.isEmpty());
        assertEquals(1, networkInterfaceStates.size());
        NetworkInterfaceState nis = networkInterfaceStates.iterator().next();
        assertNotNull(nis);
        assertEquals(nis.subnetLink, subnet.documentSelfLink);
        assertEquals(nis.name, nid.name);
        assertEquals(nis.networkLink, subnet.networkLink);
        assertEquals(1, nis.securityGroupLinks.size());
    }

    @Test
    public void testCreateNicStateFailure() throws Throwable {
        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        ComputeDescription cd = createComputeDescription(UUID.randomUUID().toString(),
                Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        Set<NetworkInterfaceState> networkInterfaceStates = new HashSet<>();
        List<Throwable> exceptions = new ArrayList<>();
        NetworkProfileQueryUtils.createNicState(null, nid.tenantLinks, null, cd,
                nid, null,null).whenComplete((nis, e) -> {
                    if (e != null) {
                        ctx.complete();
                        exceptions.add(e);
                        return;
                    }
                    networkInterfaceStates.add(nis);
                    ctx.complete();
                });

        ctx.await();

        assertTrue(networkInterfaceStates.isEmpty());
        assertFalse(exceptions.isEmpty());
    }

    @Test
    public void testSelectAnySubnet() throws Throwable {
        ComputeNetworkDescription nd = createNetworkDescription("my net", null,
                NetworkType.EXTERNAL);
        String contextId = UUID.randomUUID().toString();
        List<String> subnets = Arrays.asList(
                createSubnet("sub-1", nd.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", nd.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile = createNetworkProfile(subnets,
                nd.tenantLinks, null, IsolationSupportType.SUBNET);
        ProfileStateExpanded profile = createProfile(networkProfile.documentSelfLink,
                networkProfile.tenantLinks, null);
        ComputeNetwork cn = createComputeNetwork(nd, contextId,
                Arrays.asList(profile.documentSelfLink));

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        ComputeDescription cd = createComputeDescription(UUID.randomUUID().toString(),
                Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        Set<SubnetState> subnetStates = new HashSet<>();
        NetworkProfileQueryUtils.selectSubnet(host, referer,
                nd.tenantLinks, null, cd.regionId, nid, profile, cn, nd, null, false)
                .whenComplete((subnet, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnetStates.add(subnet);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnetStates.isEmpty());
        assertEquals(1, subnetStates.size());
        SubnetState selectedSubnet = subnetStates.iterator().next();
        assertNotNull(selectedSubnet);
        assertTrue(selectedSubnet.name.equals("sub-1") || selectedSubnet.name.equals("sub-2"));
    }

    @Test
    public void testSelectDefaultSubnetForNoNicVM() throws Throwable {
        ComputeNetworkDescription nd = createNetworkDescription("my net", null,
                NetworkType.EXTERNAL);
        String contextId = UUID.randomUUID().toString();
        List<String> subnets = Arrays.asList(
                createSubnet("sub-not-default", nd.tenantLinks, null).documentSelfLink,
                createSubnet("sub-default", nd.tenantLinks, null, true).documentSelfLink);
        NetworkProfile networkProfile = createNetworkProfile(subnets,
                nd.tenantLinks, null, IsolationSupportType.SUBNET);
        ProfileStateExpanded profile = createProfile(networkProfile.documentSelfLink,
                networkProfile.tenantLinks, null);
        ComputeNetwork cn = createComputeNetwork(nd, contextId,
                Arrays.asList(profile.documentSelfLink));

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(NetworkProfileQueryUtils.NO_NIC_VM, "true");
        ComputeDescription cd = createComputeDescription(UUID.randomUUID().toString(),
                Arrays.asList(nid.documentSelfLink), customProperties);

        TestContext ctx = testCreate(1);
        Set<SubnetState> subnetStates = new HashSet<>();
        NetworkProfileQueryUtils.selectSubnet(host, referer,
                nd.tenantLinks, null, cd.regionId, nid, profile, cn, nd, null, true)
                .whenComplete((subnet, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnetStates.add(subnet);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnetStates.isEmpty());
        assertEquals(1, subnetStates.size());
        SubnetState selectedSubnet = subnetStates.iterator().next();
        assertNotNull(selectedSubnet);
        assertTrue(selectedSubnet.name.equals("sub-default"));
    }

    @Test
    public void testSelectIsolatedSubnet() throws Throwable {
        ComputeNetworkDescription nd = createNetworkDescription("my net", null,
                NetworkType.ISOLATED);
        String contextId = UUID.randomUUID().toString();
        List<String> subnets = Arrays.asList(
                createSubnet("sub-1", nd.tenantLinks, null).documentSelfLink,
                createSubnet("sub-2", nd.tenantLinks, null).documentSelfLink);
        NetworkProfile networkProfile = createNetworkProfile(subnets,
                nd.tenantLinks, null, IsolationSupportType.SUBNET);
        ProfileStateExpanded profile = createProfile(networkProfile.documentSelfLink,
                networkProfile.tenantLinks, null);
        ComputeNetwork cn = createComputeNetwork(nd, contextId,
                Arrays.asList(profile.documentSelfLink));

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        ComputeDescription cd = createComputeDescription(UUID.randomUUID().toString(),
                Arrays.asList(nid.documentSelfLink));

        SubnetState isolatedSubnet = createSubnet("isolated-network", nd.tenantLinks, null);

        TestContext ctx = testCreate(1);
        Set<SubnetState> subnetStates = new HashSet<>();
        NetworkProfileQueryUtils.selectSubnet(host, referer,
                nd.tenantLinks, null, cd.regionId, nid, profile, cn, nd, isolatedSubnet, false)
                .whenComplete((subnet, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnetStates.add(subnet);
                    ctx.complete();
                });
        ctx.await();

        NetworkInterfaceDescription updatedNid = getDocument(NetworkInterfaceDescription.class,
                nid.documentSelfLink);

        assertEquals(false, updatedNid.assignPublicIpAddress);
        assertFalse(subnetStates.isEmpty());
        assertEquals(1, subnetStates.size());
        SubnetState selectedSubnet = subnetStates.iterator().next();
        assertNotNull(selectedSubnet);
        assertEquals(selectedSubnet.documentSelfLink, isolatedSubnet.documentSelfLink);
    }

    /**
     * Test if subnet is selected when No nic VM is requested and the network profile
     * is configured with isolation by subnets.
     *
     * Related Jira: VCOM-949
     */
    @Test
    public void testSelectSubnetNoNicVMIsolatedNetworkProfile() throws Throwable {
        List<String> tenantLinks =
                TestRequestStateFactory.createTenantLinks(TestRequestStateFactory.TENANT_NAME);
        NetworkProfile networkProfile = createNetworkProfile(null,
                tenantLinks,
                null,
                IsolationSupportType.SUBNET);
        ProfileStateExpanded profile = createProfile(networkProfile.documentSelfLink,
                networkProfile.tenantLinks, null);

        SubnetState defaultSubnet = createSubnet("sub-default", tenantLinks, null, true);

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        Map<String, String> customProperties = new HashMap<>();
        customProperties.put(NetworkProfileQueryUtils.NO_NIC_VM, "true");
        ComputeDescription cd = createComputeDescription(UUID.randomUUID().toString(),
                Arrays.asList(nid.documentSelfLink), customProperties);

        TestContext ctx = testCreate(1);
        Set<SubnetState> subnetStates = new HashSet<>();
        NetworkProfileQueryUtils.selectSubnet(host, referer,
                tenantLinks, null, cd.regionId, nid, profile, null, null, null, true)
                .whenComplete((subnet, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnetStates.add(subnet);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnetStates.isEmpty());
        assertEquals(1, subnetStates.size());
        SubnetState selectedSubnet = subnetStates.iterator().next();
        assertNotNull(selectedSubnet);
        assertEquals(selectedSubnet.documentSelfLink, defaultSubnet.documentSelfLink);
    }

    @Test
    public void testFindSubnetBy() throws Throwable {
        ComputeNetworkDescription nd = createNetworkDescription("my net", null,
                NetworkType.EXTERNAL);
        String contextId = UUID.randomUUID().toString();
        NetworkProfile networkProfile = createNetworkProfile(null,
                nd.tenantLinks, null);
        ProfileStateExpanded profile = createProfile(networkProfile.documentSelfLink,
                networkProfile.tenantLinks, null);
        ComputeNetwork cn = createComputeNetwork(nd, contextId,
                Arrays.asList(profile.documentSelfLink));

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        ComputeDescription cd = createComputeDescription(UUID.randomUUID().toString(),
                Arrays.asList(nid.documentSelfLink));

        SubnetState existingSubnet = createSubnet("sub-1", nd.tenantLinks, null);

        TestContext ctx = testCreate(1);
        Set<SubnetState> subnetStates = new HashSet<>();
        NetworkProfileQueryUtils.selectSubnet(host, referer,
                nd.tenantLinks, null, cd.regionId, nid, profile, cn, nd, null, false)
                .whenComplete((subnet, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    subnetStates.add(subnet);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(subnetStates.isEmpty());
        assertEquals(1, subnetStates.size());
        SubnetState selectedSubnet = subnetStates.iterator().next();
        assertNotNull(selectedSubnet);
        assertEquals(selectedSubnet.documentSelfLink, existingSubnet.documentSelfLink);
    }

    private ComputeNetworkDescription createNetworkDescription(String name,
            List<Condition> conditions) throws Throwable {
        return createNetworkDescription(name, conditions, null);
    }

    private ComputeNetworkDescription createNetworkDescription(String name,
            List<Condition> conditions, NetworkType networkType) throws Throwable {
        ComputeNetworkDescription desc = TestRequestStateFactory.createComputeNetworkDescription(
                name);
        desc.networkType = networkType;
        desc.documentSelfLink = UUID.randomUUID().toString();
        if (conditions != null) {
            Constraint constraint = new Constraint();
            constraint.conditions = conditions;
            desc.constraints = new HashMap<>();
            desc.constraints.put(ComputeConstants.COMPUTE_PLACEMENT_CONSTRAINT_KEY, constraint);
        }
        desc = doPost(desc, ComputeNetworkDescriptionService.FACTORY_LINK);
        addForDeletion(desc);
        return desc;
    }

    private NetworkInterfaceDescription createComputeNetworkInterfaceDescription(String netName)
            throws Throwable {
        NetworkInterfaceDescription nid = new NetworkInterfaceDescription();
        nid.name = netName;
        nid.documentSelfLink = UUID.randomUUID().toString();
        return doPost(nid, NetworkInterfaceDescriptionService.FACTORY_LINK);
    }

    private ComputeDescription createComputeDescription(String contextId,
            List<String> networkInterfaceDescriptions) throws Throwable {
        return createComputeDescription(contextId, networkInterfaceDescriptions, null);
    }

    private ComputeDescription createComputeDescription(String contextId,
            List<String> networkInterfaceDescriptions,
            Map<String, String> customProperties) throws Throwable {
        ComputeDescription compute = TestRequestStateFactory
                .createComputeDescriptionForVmGuestChildren();
        compute.documentSelfLink = UUID.randomUUID().toString();
        compute.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        if (customProperties != null && !customProperties.isEmpty()) {
            compute.customProperties.putAll(customProperties);
        }
        compute.networkInterfaceDescLinks = networkInterfaceDescriptions;
        return doPost(compute, ComputeDescriptionService.FACTORY_LINK);
    }

    private ProfileStateExpanded createProfile(String networkProfileLink,
            List<String> tenantLinks, Set<String> tagLinks) throws Throwable {
        StorageProfileService.StorageProfile storageProfile = new StorageProfileService.StorageProfile();
        storageProfile = doPost(storageProfile, StorageProfileService.FACTORY_LINK);
        ComputeProfileService.ComputeProfile computeProfile = new ComputeProfileService.ComputeProfile();
        computeProfile = doPost(computeProfile, ComputeProfileService.FACTORY_LINK);

        ProfileState profileState = TestRequestStateFactory.createProfile(
                "profile", networkProfileLink, storageProfile.documentSelfLink,
                computeProfile.documentSelfLink);
        profileState.tenantLinks = tenantLinks;
        profileState.documentSelfLink = UUID.randomUUID().toString();
        profileState.tagLinks = tagLinks;
        profileState = doPost(profileState, ProfileService.FACTORY_LINK);
        return getDocument(ProfileStateExpanded.class,
                ProfileStateExpanded.buildUri(UriUtils.buildUri(host,
                        profileState.documentSelfLink)));
    }

    private NetworkProfile createNetworkProfile(List<String> subnetLinks, List<String> tenantLinks,
            Set<String> tagLinks) throws Throwable {
        return createNetworkProfile(subnetLinks, tenantLinks, tagLinks, IsolationSupportType.NONE);
    }

    private NetworkProfile createNetworkProfile(List<String> subnetLinks, List<String> tenantLinks,
            Set<String> tagLinks, IsolationSupportType isolationSupportType) throws Throwable {
        NetworkProfile networkProfile = TestRequestStateFactory.createNetworkProfile("net-prof");
        networkProfile.documentSelfLink = UUID.randomUUID().toString();
        networkProfile.subnetLinks = subnetLinks;
        networkProfile.tenantLinks = tenantLinks;
        networkProfile.tagLinks = tagLinks;
        networkProfile.isolationType = isolationSupportType;
        return doPost(networkProfile, NetworkProfileService.FACTORY_LINK);
    }

    private ComputeNetwork createComputeNetwork(ComputeNetworkDescription computeNetworkDescription,
            String contextId, List<String> profileLinks) throws Throwable {
        ComputeNetwork net = TestRequestStateFactory.createComputeNetworkState(
                "my-net", computeNetworkDescription.documentSelfLink);
        net.documentSelfLink = UUID.randomUUID().toString();
        net.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        net.profileLinks = profileLinks;
        net.networkType = computeNetworkDescription.networkType;
        return doPost(net, ComputeNetworkService.FACTORY_LINK);
    }

    private SubnetState createSubnet(String name, List<String> tenantLinks, Set<String> tagLinks,
            boolean isDefault)
            throws Throwable {
        SubnetState subnet = TestRequestStateFactory.createSubnetState(
                name, tenantLinks);
        subnet.name = name;
        subnet.documentSelfLink = UUID.randomUUID().toString();
        subnet.tagLinks = tagLinks;
        subnet.networkLink = UUID.randomUUID().toString();
        subnet.defaultForZone = isDefault;
        return doPost(subnet, SubnetService.FACTORY_LINK);
    }

    private SubnetState createSubnet(String name, List<String> tenantLinks, Set<String> tagLinks)
            throws Throwable {
        return createSubnet(name, tenantLinks, tagLinks, false);
    }

    private String createTag(String key, String value, List<String> tenantLinks)
            throws Throwable {
        TagState tag = new TagState();
        tag.key = key;
        tag.value = value;
        tag.tenantLinks = tenantLinks;
        return doPost(tag, TagService.FACTORY_LINK).documentSelfLink;
    }
}
