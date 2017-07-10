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

package com.vmware.admiral.request.compute.allocation.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;

public class ComputeToNetworkAffinityHostFilterTest extends BaseComputeAffinityHostFilterTest {

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        throwErrorOnFilter = true;
    }

    @Test
    public void testGetAffinityConstraints() throws Throwable {
        ComputeDescription desc = createComputeDescription(true);

        filter = new ComputeToNetworkAffinityHostFilter(host, desc);
        Map<String, AffinityConstraint> affinityConstraints = filter.getAffinityConstraints();

        // one nic
        assertEquals(1, affinityConstraints.size());
        assertEquals(NETWORK_NAME, affinityConstraints.keySet().iterator().next());

    }

    /**
     * Make sure that when the conditions are the same the compute lands on the same group of hosts within a request
     */
    @Test
    public void testPickSameGroupWithSameContextIdNetworksInComputeState() throws Throwable {

        NetworkState network1 = createNetwork();
        NetworkState network2 = createNetwork();
        NetworkState network3 = createNetwork();

        String region1 = "region1";

        String zone1Region1 = "zone1region1";
        String zone2Region1 = "zone2region1";

        String region2 = "region2";

        String zone1Region2 = "zone1region2";

        SubnetState subnet1 = createSubnet(network1.documentSelfLink, region1, zone1Region1);
        SubnetState subnet2 = createSubnet(network2.documentSelfLink, region1, zone2Region1);
        SubnetState subnet3 = createSubnet(network3.documentSelfLink, region2, zone1Region2);

        // Create 3 groups of hosts that have connectivity between them (3, 3, 2) hosts
        ComputeState host11 = createVmHostCompute(true,
                Collections.singleton(network1.documentSelfLink), null, null);
        ComputeState host12 = createVmHostCompute(true,
                Collections.singleton(network1.documentSelfLink), null, null);
        ComputeState host13 = createVmHostCompute(true,
                Collections.singleton(network1.documentSelfLink), null, null);

        ComputeState host21 = createVmHostCompute(true,
                Collections.singleton(network2.documentSelfLink), null, null);
        ComputeState host22 = createVmHostCompute(true,
                Collections.singleton(network2.documentSelfLink), null, null);
        ComputeState host23 = createVmHostCompute(true,
                Collections.singleton(network2.documentSelfLink), null, null);

        ComputeState host31 = createVmHostCompute(true,
                Collections.singleton(network3.documentSelfLink), null, null);
        ComputeState host32 = createVmHostCompute(true,
                Collections.singleton(network3.documentSelfLink), null, null);

        NetworkProfile networkProfile = createNetworkProfile(
                Arrays.asList(subnet1.documentSelfLink, subnet2.documentSelfLink,
                        subnet3.documentSelfLink), null, null);

        ProfileStateExpanded profile = createProfile(null, null, networkProfile,
                endpoint.tenantLinks, null);
        createComputeNetwork(createComputeNetworkDescription(NetworkType.EXTERNAL).documentSelfLink,
                Arrays.asList(profile.documentSelfLink));
        ComputeDescription desc = createComputeDescription(true);

        filter = new ComputeToNetworkAffinityHostFilter(host, desc);

        List<ComputeState> computeStates = Arrays
                .asList(host11, host12, host13, host21, host22, host23, host31, host32);

        // filter the same compute desc 10 times
        List<Set<String>> filteredHosts = IntStream.generate(() -> 1)
                .limit(5)
                .mapToObj(i -> {
                    try {
                        Map<String, HostSelection> result = filter(computeStates);
                        return result.keySet();
                    } catch (Throwable throwable) {
                        fail(throwable.getMessage());
                        return null;
                    }
                })
                .distinct()
                .collect(Collectors.toList());

        // expect to get the same group every time
        assertEquals(1, filteredHosts.size());
        // it's not deterministic which one of the groups of size 3 we will pick, but it has to be of size 3
        assertEquals(3, filteredHosts.get(0).size());
    }

    /**
     * Make sure that when the conditions are the same the compute lands on the same group of hosts within a request
     */
    @Test
    public void testPickSameGroupWithSameContextIdSubnetsQuery() throws Throwable {

        String region1 = "region1";

        String zone1Region1 = "zone1region1";
        String zone2Region1 = "zone2region1";

        String region2 = "region2";

        String zone1Region2 = "zone1region2";

        SubnetState subnet1 = createSubnet("does-not-matter", region1, zone1Region1);
        SubnetState subnet2 = createSubnet("does-not-matter", region1, zone2Region1);
        SubnetState subnet3 = createSubnet("does-not-matter", region2, zone1Region2);

        // Create 3 groups of hosts that have connectivity between them (3, 3, 2) hosts
        ComputeState host11 = createVmHostCompute(true, null, region1, zone1Region1);
        ComputeState host12 = createVmHostCompute(true, null, region1, zone1Region1);
        ComputeState host13 = createVmHostCompute(true, null, region1, zone1Region1);

        ComputeState host21 = createVmHostCompute(true, null, region2, zone1Region2);
        ComputeState host22 = createVmHostCompute(true, null, region2, zone1Region2);
        ComputeState host23 = createVmHostCompute(true, null, region2, zone1Region2);

        ComputeState host31 = createVmHostCompute(true, null, region1, zone2Region1);
        ComputeState host32 = createVmHostCompute(true, null, region1, zone2Region1);

        NetworkProfile networkProfile = createNetworkProfile(
                Arrays.asList(subnet1.documentSelfLink, subnet2.documentSelfLink,
                        subnet3.documentSelfLink), null, null);

        ProfileStateExpanded profile = createProfile(null, null, networkProfile,
                endpoint.tenantLinks, null);
        createComputeNetwork(createComputeNetworkDescription(NetworkType.EXTERNAL).documentSelfLink,
                Arrays.asList(profile.documentSelfLink));

        ComputeDescription desc = createComputeDescription(true);

        filter = new ComputeToNetworkAffinityHostFilter(host, desc);

        List<ComputeState> computeStates = Arrays
                .asList(host11, host12, host13, host21, host22, host23, host31, host32);

        // filter the same compute desc 5 times
        List<Set<String>> filteredHosts = IntStream.generate(() -> 1)
                .limit(5)
                .mapToObj(i -> {
                    try {
                        Map<String, HostSelection> result = filter(computeStates);
                        return result.keySet();
                    } catch (Throwable throwable) {
                        fail(throwable.getMessage());
                        return null;
                    }
                })
                .distinct()
                .collect(Collectors.toList());

        // expect to get the same group every time
        assertEquals(1, filteredHosts.size());
        // it's not deterministic which one of the groups of size 3 we will pick, but it has to be of size 3
        assertEquals(3, filteredHosts.get(0).size());
    }

    @Test
    public void testCheckConnectivity() throws Throwable {

        String region1 = "region1";

        String zone1Region1 = "zone1region1";
        String zone2Region1 = "zone2region1";

        String region2 = "region2";

        String zone1Region2 = "zone1region2";

        SubnetState subnet1 = createSubnet("does-not-matter", region1, zone1Region1);
        SubnetState subnet2 = createSubnet("does-not-matter", region1, zone2Region1);
        SubnetState subnet3 = createSubnet("does-not-matter", region2, zone1Region2);

        NetworkProfile networkProfile = createNetworkProfile(
                Arrays.asList(subnet2.documentSelfLink), null, null);

        ProfileStateExpanded profile = createProfile(null, null, networkProfile,
                endpoint.tenantLinks, null);
        createComputeNetwork(createComputeNetworkDescription(NetworkType.EXTERNAL).documentSelfLink,
                Arrays.asList(profile.documentSelfLink));

        // Create 3 groups of hosts that have connectivity between them (3, 3, 2) hosts
        ComputeState host11 = createVmHostCompute(true, null, region1, zone1Region1);
        ComputeState host12 = createVmHostCompute(true, null, region1, zone1Region1);
        ComputeState host13 = createVmHostCompute(true, null, region1, zone1Region1);

        ComputeState host21 = createVmHostCompute(true, null, region2, zone1Region2);
        ComputeState host22 = createVmHostCompute(true, null, region2, zone1Region2);
        ComputeState host23 = createVmHostCompute(true, null, region2, zone1Region2);

        ComputeState host31 = createVmHostCompute(true, null, region1, zone2Region1);
        ComputeState host32 = createVmHostCompute(true, null, region1, zone2Region1);

        ComputeDescription desc = createComputeDescription(true);

        filter = new ComputeToNetworkAffinityHostFilter(host, desc);

        List<ComputeState> computeStates = Arrays
                .asList(host11, host12, host13, host21, host22, host23, host31, host32);

        Set<String> filteredHosts = null;
        try {
            Map<String, HostSelection> result = filter(computeStates);
            filteredHosts = result.keySet();
        } catch (Throwable throwable) {
            fail(throwable.getMessage());
        }

        // expect to get the same group every time
        assertEquals(2, filteredHosts.size());
        assertTrue(filteredHosts.containsAll(Arrays.asList(host31.documentSelfLink, host32.documentSelfLink)));
    }

    @Test
    public void testCheckConnectivityMultipleProfiles() throws Throwable {

        String region1 = "region1";

        String zone1Region1 = "zone1region1";
        String zone2Region1 = "zone2region1";

        String region2 = "region2";

        String zone1Region2 = "zone1region2";

        SubnetState subnet1 = createSubnet("does-not-matter", region1, zone1Region1);
        SubnetState subnet2 = createSubnet("does-not-matter", region1, zone2Region1);
        SubnetState subnet3 = createSubnet("does-not-matter", region2, zone1Region2);

        NetworkProfile networkProfile = createNetworkProfile(
                Arrays.asList(subnet2.documentSelfLink), null, null);

        NetworkProfile networkProfile2 = createNetworkProfile(
                Arrays.asList(subnet1.documentSelfLink), null, null);

        ProfileStateExpanded profile = createProfile(null, null, networkProfile,
                endpoint.tenantLinks, null);

        ProfileStateExpanded profile2 = createProfile(null, null, networkProfile2,
                endpoint.tenantLinks, null);

        createComputeNetwork(createComputeNetworkDescription(NetworkType.EXTERNAL).documentSelfLink,
                Arrays.asList(profile.documentSelfLink, profile2.documentSelfLink));

        // Create 3 groups of hosts that have connectivity between them (3, 3, 2) hosts
        ComputeState host11 = createVmHostCompute(true, null, region1, zone1Region1);
        ComputeState host12 = createVmHostCompute(true, null, region1, zone1Region1);
        ComputeState host13 = createVmHostCompute(true, null, region1, zone1Region1);

        ComputeState host21 = createVmHostCompute(true, null, region2, zone1Region2);
        ComputeState host22 = createVmHostCompute(true, null, region2, zone1Region2);
        ComputeState host23 = createVmHostCompute(true, null, region2, zone1Region2);

        ComputeState host31 = createVmHostCompute(true, null, region1, zone2Region1);
        ComputeState host32 = createVmHostCompute(true, null, region1, zone2Region1);

        ComputeDescription desc = createComputeDescription(true);

        filter = new ComputeToNetworkAffinityHostFilter(host, desc);

        List<ComputeState> computeStates = Arrays
                .asList(host11, host12, host13, host21, host22, host23, host31, host32);

        // filter the same compute desc 10 times
        Set<String> filteredHosts = null;
        try {
            Map<String, HostSelection> result = filter(computeStates);
            filteredHosts = result.keySet();
        } catch (Throwable throwable) {
            fail(throwable.getMessage());
        }

        // the two profiles will match "host groups" 1 and 3 but the filter will pick 1 as there are
        // more hosts there
        assertEquals(3, filteredHosts.size());
        assertTrue(filteredHosts.containsAll(
                Arrays.asList(host11.documentSelfLink, host12.documentSelfLink,
                        host13.documentSelfLink)));
    }

    @Test
    public void testCheckConnectivityIsolatedProfileBySecurityGroup() throws Throwable {

        String region1 = "region1";

        String zone1Region1 = "zone1region1";
        String zone2Region1 = "zone2region1";

        String region2 = "region2";

        String zone1Region2 = "zone1region2";

        SubnetState subnet1 = createSubnet("does-not-matter", region1, zone1Region1);
        SubnetState subnet2 = createSubnet("does-not-matter", region1, zone2Region1);
        SubnetState subnet3 = createSubnet("does-not-matter", region2, zone1Region2);

        NetworkProfile networkProfile = createNetworkProfile(
                Arrays.asList(subnet2.documentSelfLink), null, IsolationSupportType.SECURITY_GROUP);

        ProfileStateExpanded profile = createProfile(null, null, networkProfile,
                endpoint.tenantLinks, null);

        createComputeNetwork(createComputeNetworkDescription(NetworkType.ISOLATED).documentSelfLink,
                Arrays.asList(profile.documentSelfLink));

        // Create 3 groups of hosts that have connectivity between them (3, 3, 2) hosts
        ComputeState host11 = createVmHostCompute(true, null, region1, zone1Region1);
        ComputeState host12 = createVmHostCompute(true, null, region1, zone1Region1);
        ComputeState host13 = createVmHostCompute(true, null, region1, zone1Region1);

        ComputeState host21 = createVmHostCompute(true, null, region2, zone1Region2);
        ComputeState host22 = createVmHostCompute(true, null, region2, zone1Region2);
        ComputeState host23 = createVmHostCompute(true, null, region2, zone1Region2);

        ComputeState host31 = createVmHostCompute(true, null, region1, zone2Region1);
        ComputeState host32 = createVmHostCompute(true, null, region1, zone2Region1);

        ComputeDescription desc = createComputeDescription(true);

        filter = new ComputeToNetworkAffinityHostFilter(host, desc);

        List<ComputeState> computeStates = Arrays
                .asList(host11, host12, host13, host21, host22, host23, host31, host32);

        Set<String> filteredHosts = null;
        try {
            Map<String, HostSelection> result = filter(computeStates);
            filteredHosts = result.keySet();
        } catch (Throwable throwable) {
            fail(throwable.getMessage());
        }

        assertEquals(2, filteredHosts.size());
        assertTrue(filteredHosts
                .containsAll(Arrays.asList(host31.documentSelfLink, host32.documentSelfLink)));
    }

    @Test
    public void testCheckConnectivityIsolatedProfileBySubnet() throws Throwable {

        NetworkState network1 = createNetwork();
        NetworkState network2 = createNetwork();
        NetworkState network3 = createNetwork();

        NetworkProfile networkProfile = createNetworkProfile(null, network1.documentSelfLink,
                IsolationSupportType.SUBNET);

        ProfileStateExpanded profile = createProfile(null, null, networkProfile,
                endpoint.tenantLinks, null);

        createComputeNetwork(createComputeNetworkDescription(NetworkType.ISOLATED).documentSelfLink,
                Arrays.asList(profile.documentSelfLink));

        // Create 3 groups of hosts that have connectivity between them (3, 3, 2) hosts
        ComputeState host11 = createVmHostCompute(true,
                Collections.singleton(network1.documentSelfLink), null, null);
        ComputeState host12 = createVmHostCompute(true,
                Collections.singleton(network1.documentSelfLink), null, null);
        ComputeState host13 = createVmHostCompute(true,
                Collections.singleton(network1.documentSelfLink), null, null);

        ComputeState host21 = createVmHostCompute(true,
                Collections.singleton(network2.documentSelfLink), null, null);
        ComputeState host22 = createVmHostCompute(true,
                Collections.singleton(network2.documentSelfLink), null, null);
        ComputeState host23 = createVmHostCompute(true,
                Collections.singleton(network2.documentSelfLink), null, null);

        ComputeState host31 = createVmHostCompute(true,
                Collections.singleton(network3.documentSelfLink), null, null);
        ComputeState host32 = createVmHostCompute(true,
                Collections.singleton(network3.documentSelfLink), null, null);

        ComputeDescription desc = createComputeDescription(true);

        filter = new ComputeToNetworkAffinityHostFilter(host, desc);

        List<ComputeState> computeStates = Arrays
                .asList(host11, host12, host13, host21, host22, host23, host31, host32);

        // filter the same compute desc 10 times
        Set<String> filteredHosts = null;
        try {
            Map<String, HostSelection> result = filter(computeStates);
            filteredHosts = result.keySet();
        } catch (Throwable throwable) {
            fail(throwable.getMessage());
        }

        assertEquals(3, filteredHosts.size());
        assertTrue(filteredHosts.containsAll(
                Arrays.asList(host11.documentSelfLink, host12.documentSelfLink,
                        host13.documentSelfLink)));
    }

    @Test
    public void testCheckConnectivityIsolatedMixedProfiles() throws Throwable {

        String region1 = "region1";

        String zone1Region1 = "zone1region1";
        String zone2Region1 = "zone2region1";

        String region2 = "region2";

        String zone1Region2 = "zone1region2";

        NetworkState network1 = createNetwork(region1);
        NetworkState network2 = createNetwork(region2);
        NetworkState network3 = createNetwork(region1);

        SubnetState subnet1 = createSubnet(network1.documentSelfLink, region1, zone1Region1);
        SubnetState subnet2 = createSubnet(network3.documentSelfLink, region1, zone2Region1);
        SubnetState subnet3 = createSubnet(network2.documentSelfLink, region2, zone1Region2);

        //this profile should match the third group of hosts
        NetworkProfile networkProfileSubnet = createNetworkProfile(null, network3.documentSelfLink,
                IsolationSupportType.SUBNET);

        //this profile should match the second group of hosts
        NetworkProfile networkProfileSecurityGroup = createNetworkProfile(
                Arrays.asList(subnet3.documentSelfLink), null, IsolationSupportType.SECURITY_GROUP);

        ProfileStateExpanded profile1 = createProfile(null, null, networkProfileSubnet,
                endpoint.tenantLinks, null);

        ProfileStateExpanded profile2 = createProfile(null, null, networkProfileSecurityGroup,
                endpoint.tenantLinks, null);

        createComputeNetwork(createComputeNetworkDescription(NetworkType.ISOLATED).documentSelfLink,
                Arrays.asList(profile1.documentSelfLink, profile2.documentSelfLink));

        // Create 3 groups of hosts that have connectivity between them (3, 3, 2) hosts
        ComputeState host11 = createVmHostCompute(true,
                Collections.singleton(network1.documentSelfLink), region1, zone1Region1);
        ComputeState host12 = createVmHostCompute(true,
                Collections.singleton(network1.documentSelfLink), region1, zone1Region1);
        ComputeState host13 = createVmHostCompute(true,
                Collections.singleton(network1.documentSelfLink), region1, zone1Region1);

        ComputeState host21 = createVmHostCompute(true,
                Collections.singleton(network2.documentSelfLink), region2, zone1Region2);
        ComputeState host22 = createVmHostCompute(true,
                Collections.singleton(network2.documentSelfLink), region2, zone1Region2);
        ComputeState host23 = createVmHostCompute(true,
                Collections.singleton(network2.documentSelfLink), region2, zone1Region2);

        ComputeState host31 = createVmHostCompute(true,
                Collections.singleton(network3.documentSelfLink), region1, zone2Region1);
        ComputeState host32 = createVmHostCompute(true,
                Collections.singleton(network3.documentSelfLink), region1, zone2Region1);

        ComputeDescription desc = createComputeDescription(true);

        filter = new ComputeToNetworkAffinityHostFilter(host, desc);

        List<ComputeState> computeStates = Arrays
                .asList(host11, host12, host13, host21, host22, host23, host31, host32);

        Set<String> filteredHosts = null;
        try {
            Map<String, HostSelection> result = filter(computeStates);
            filteredHosts = result.keySet();
        } catch (Throwable throwable) {
            fail(throwable.getMessage());
        }

        assertEquals(3, filteredHosts.size());
        assertTrue(filteredHosts.containsAll(
                Arrays.asList(host21.documentSelfLink, host22.documentSelfLink,
                        host23.documentSelfLink)));
    }

    private NetworkState createNetwork() throws Throwable {
        return createNetwork("region");
    }

    private NetworkState createNetwork(String region) throws Throwable {
        NetworkState networkState = new NetworkState();

        networkState.subnetCIDR = "10.10.10.10/16";
        networkState.endpointLink = endpoint.documentSelfLink;
        networkState.resourcePoolLink = endpoint.resourcePoolLink;
        networkState.instanceAdapterReference = URI.create("uri");
        networkState.regionId = region;
        networkState.tenantLinks = endpoint.tenantLinks;

        return doPost(networkState, NetworkService.FACTORY_LINK);
    }

    private SubnetState createSubnet(String networkLink, String regionId, String zoneId)
            throws Throwable {

        SubnetState subnetState = new SubnetState();
        subnetState.networkLink = networkLink;
        subnetState.subnetCIDR = "10.10.10.10/24";
        subnetState.supportPublicIpAddress = true;
        subnetState.endpointLink = endpoint.documentSelfLink;
        subnetState.regionId = regionId;
        subnetState.zoneId = zoneId;
        subnetState.tenantLinks = endpoint.tenantLinks;

        return doPost(subnetState, SubnetService.FACTORY_LINK);
    }

    private ComputeNetworkDescription createComputeNetworkDescription(NetworkType networkType)
            throws Throwable {
        ComputeNetworkDescription networkDescription = new ComputeNetworkDescription();

        networkDescription.tenantLinks = endpoint.tenantLinks;
        networkDescription.name = NETWORK_NAME;
        networkDescription.networkType = networkType;

        return doPost(networkDescription, ComputeNetworkDescriptionService.FACTORY_LINK);
    }

    private ComputeNetwork createComputeNetwork(String descLink, List<String> profileLinks)
            throws Throwable {
        ComputeNetwork computeNetwork = new ComputeNetwork();

        computeNetwork.descriptionLink = descLink;
        computeNetwork.name = NETWORK_NAME;
        computeNetwork.customProperties = new HashMap<>();
        computeNetwork.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, state.contextId);
        computeNetwork.tenantLinks = endpoint.tenantLinks;
        computeNetwork.profileLinks = profileLinks;

        return doPost(computeNetwork, ComputeNetworkService.FACTORY_LINK);
    }

    private NetworkProfile createNetworkProfile(List<String> subnetLinks,
            String isolationNetworkLink, IsolationSupportType isolationSupportType)
            throws Throwable {
        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.subnetLinks = subnetLinks;
        networkProfile.isolationType = isolationSupportType;
        networkProfile.tenantLinks = endpoint.tenantLinks;
        networkProfile.isolationNetworkLink = isolationNetworkLink;

        return doPost(networkProfile, NetworkProfileService.FACTORY_LINK);
    }
}