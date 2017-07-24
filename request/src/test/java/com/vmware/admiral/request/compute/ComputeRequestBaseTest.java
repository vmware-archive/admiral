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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertNotNull;

import static com.vmware.admiral.common.test.CommonTestStateFactory.ENDPOINT_ID;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationState;
import com.vmware.admiral.compute.profile.ComputeImageDescription;
import com.vmware.admiral.compute.profile.ComputeProfileService;
import com.vmware.admiral.compute.profile.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.compute.profile.InstanceTypeDescription;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetRangeService;
import com.vmware.photon.controller.model.resources.SubnetRangeService.SubnetRangeState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.xenon.common.UriUtils;

public class ComputeRequestBaseTest extends RequestBaseTest {

    static final String TEST_VM_NAME = "testVM";
    public static final String NETWORK_NAME = "test-nic";
    public static final String NETWORK_ADDRESS = "192.168.0.0";
    public static final int NETWORK_CIDR_PREFIX = 29;
    public static final String NETWORK_CIDR = NETWORK_ADDRESS + "/" + NETWORK_CIDR_PREFIX;

    protected ComputeState vmHostCompute;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        createComputeResourcePool();
        computeGroupPlacementState = createComputeGroupResourcePlacement(computeResourcePool, 10);
        // create a single powered-on compute available for placement
        StorageDescription datastore = createDatastore(5000);
        vmHostCompute = createVmHostCompute(true, null,
                Collections.singleton(datastore.documentSelfLink));
    }

    protected ComputeDescription createVMComputeDescription(boolean attachNic) throws Throwable {
        return doPost(createComputeDescription(attachNic),
                ComputeDescriptionService.FACTORY_LINK);
    }

    protected ComputeDescription createComputeDescription(boolean attachNic) throws Throwable {
        ComputeDescription cd = new ComputeDescription();
        cd.id = UUID.randomUUID().toString();
        cd.name = TEST_VM_NAME;
        cd.instanceType = "small";
        cd.tenantLinks = computeGroupPlacementState.tenantLinks;
        cd.customProperties = new HashMap<>();
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                "coreos");

        SubnetState subnet = createSubnet("my-subnet");
        if (attachNic) {
            NetworkInterfaceDescription nid = createNetworkInterface(NETWORK_NAME,
                    subnet.documentSelfLink);
            cd.networkInterfaceDescLinks = new ArrayList<>();
            cd.networkInterfaceDescLinks.add(nid.documentSelfLink);
        } else {
            cd.customProperties.put("subnetworkLink", subnet.documentSelfLink);
        }
        return cd;
    }

    protected ComputeDescription createComputeDescriptionWithDisks(List<Long> disksCapacity) throws
            Throwable {

        ComputeDescription desc = createComputeDescription(false);
        desc.diskDescLinks = new ArrayList<>();
        for (Long capacity : disksCapacity) {
            DiskState disk = createDiskState(capacity);
            desc.diskDescLinks.add(disk.documentSelfLink);
        }

        return desc;
    }

    protected ComputeDescription createComputeDescriptionWithNetwork(String networkName) throws
            Throwable {
        ComputeDescription cd = new ComputeDescription();
        cd.id = UUID.randomUUID().toString();
        cd.name = TEST_VM_NAME;
        cd.instanceType = "small";
        cd.tenantLinks = computeGroupPlacementState.tenantLinks;
        cd.customProperties = new HashMap<>();
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                "coreos");

        SubnetState subnet = createSubnet("my-subnet");
        NetworkInterfaceDescription nid = createNetworkInterface(networkName,
                subnet.documentSelfLink);
        cd.networkInterfaceDescLinks = new ArrayList<>();
        cd.networkInterfaceDescLinks.add(nid.documentSelfLink);
        return doPost(cd, ComputeDescriptionService.FACTORY_LINK);
    }

    protected ComputeDescription createVsphereComputeDescription(boolean attachNic,
            GroupResourcePlacementState globalGroupState) throws Throwable {
        return doPost(generateVsphereComputeDescription(attachNic, globalGroupState),
              ComputeDescriptionService.FACTORY_LINK);
    }

    protected ComputeDescription generateVsphereComputeDescription(boolean attachNic,
            GroupResourcePlacementState globalGroupState) throws Throwable {
        ComputeDescription cd = new ComputeDescription();
        cd.id = UUID.randomUUID().toString();
        cd.name = TEST_VM_NAME;
        //cd.instanceType = "small";
        cd.tenantLinks = globalGroupState == null ? computeGroupPlacementState.tenantLinks :
                globalGroupState.tenantLinks;
        cd.customProperties = new HashMap<>();
        //cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
        //      "coreos");
        cd.customProperties.put("__component_type_id", "Compute.vSphere");
        cd.customProperties.put(ComputeConstants.OVA_URI, "http://vSphere");
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_REF_NAME, "test.ova");
        cd.cpuCount = 2;
        cd.totalMemoryBytes = 512 * 1024 * 1024;

        SubnetState subnet = createSubnet("my-subnet");
        if (attachNic) {
            NetworkInterfaceDescription nid = createNetworkInterface("test-nic",
                    subnet.documentSelfLink);
            cd.networkInterfaceDescLinks = new ArrayList<>();
            cd.networkInterfaceDescLinks.add(nid.documentSelfLink);
        } else {
            cd.customProperties.put("subnetworkLink", subnet.documentSelfLink);
        }
        return cd;
    }

    private SubnetState createSubnet(String name) throws Throwable {
        SubnetState sub = new SubnetState();
        sub.name = name;
        sub.subnetCIDR = "192.168.0.0/24";
        sub.networkLink = UriUtils.buildUriPath(NetworkService.FACTORY_LINK, name);
        sub.documentSelfLink = UriUtils.buildUriPath(SubnetService.FACTORY_LINK, name);
        return doPost(sub, SubnetService.FACTORY_LINK);
    }

    protected ComputeState createComputeState(String descLink, String contextId) throws Throwable {
        ComputeState compute = new ComputeState();
        compute.name = UUID.randomUUID().toString();
        compute.descriptionLink = descLink;
        compute.customProperties = new HashMap<>();
        compute.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        compute.tenantLinks = TestRequestStateFactory.getTenantLinks();
        return doPost(compute, ComputeService.FACTORY_LINK);
    }

    private NetworkInterfaceDescription createNetworkInterface(String name, String subnetLink)
            throws Throwable {
        NetworkInterfaceDescription nid = new NetworkInterfaceDescription();
        nid.id = UUID.randomUUID().toString();
        nid.name = name;
        nid.documentSelfLink = nid.id;
        nid.tenantLinks = computeGroupPlacementState.tenantLinks;
        nid.subnetLink = subnetLink;

        NetworkInterfaceDescription returnState = doPost(nid,
                NetworkInterfaceDescriptionService.FACTORY_LINK);
        return returnState;
    }

    private DiskState createDiskState(long capacityMB) throws Throwable {
        DiskState disk = new DiskState();
        disk.id = UUID.randomUUID().toString();
        disk.name = disk.id;
        disk.capacityMBytes = capacityMB;

        DiskState returnState = doPost(disk, DiskService.FACTORY_LINK);
        return returnState;
    }

    protected ProfileStateExpanded createProfileWithInstanceType(String instanceTypeKey,
            String instanceTypeValue, String imageKey, String imageValue,
            StorageProfile storageProfile, GroupResourcePlacementState computeGroupPlacementState) throws Throwable {
        ComputeProfile cp1 = new ComputeProfile();
        cp1.instanceTypeMapping = new HashMap<>();
        InstanceTypeDescription itd = new InstanceTypeDescription();
        itd.instanceType = instanceTypeValue;
        cp1.instanceTypeMapping.put(instanceTypeKey, itd);

        ComputeImageDescription cid = new ComputeImageDescription();
        cid.image = imageValue;
        cp1.imageMapping = new HashMap<>();
        cp1.imageMapping.put(imageKey, cid);

        return createProfile(cp1, storageProfile, null, computeGroupPlacementState.tenantLinks, null);
    }

    protected ProfileStateExpanded createProfile(ComputeProfile computeProfile,
            StorageProfile storageProfile, NetworkProfile networkProfile,
            List<String> tenantLinks, Set<String> tagLinks) throws Throwable {
        if (storageProfile == null) {
            storageProfile = new StorageProfile();
        }
        storageProfile.tenantLinks = tenantLinks;
        storageProfile = doPost(storageProfile, StorageProfileService.FACTORY_LINK);

        if (computeProfile == null) {
            computeProfile = new ComputeProfile();
        }
        computeProfile.tenantLinks = tenantLinks;
        computeProfile = doPost(computeProfile, ComputeProfileService.FACTORY_LINK);

        if (networkProfile == null) {
            networkProfile = new NetworkProfile();
        }
        networkProfile.tenantLinks = tenantLinks;
        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

        ProfileState profileState =
                TestRequestStateFactory.createProfile("profile" + new Random().nextInt(), networkProfile
                        .documentSelfLink,
                        storageProfile.documentSelfLink, computeProfile.documentSelfLink);
        profileState.tenantLinks = tenantLinks;
        profileState.documentSelfLink =
                UriUtils.buildUriPath(ProfileService.FACTORY_LINK, UUID.randomUUID().toString());
        profileState.tagLinks = tagLinks;
        profileState.endpointType = null;
        profileState.endpointLink =
                UriUtils.buildUriPath(EndpointService.FACTORY_LINK, ENDPOINT_ID);
        profileState = doPost(profileState, ProfileService.FACTORY_LINK);
        return getDocument(ProfileStateExpanded.class,
                ProfileStateExpanded.buildUri(UriUtils.buildUri(host,
                        profileState.documentSelfLink)));
    }

    protected StorageProfileService.StorageProfile buildStorageProfileWithConstraints(
            List<String> tenantLinks) throws Throwable {
        ArrayList<String> tags = buildTagLinks(tenantLinks);

        StorageProfileService.StorageItem storageItem1 = new StorageProfileService.StorageItem();
        storageItem1.defaultItem = false;
        storageItem1.name = "fast";
        storageItem1.tagLinks = new HashSet<>(Arrays.asList(tags.get(0), tags.get(1)));
        storageItem1.diskProperties = new HashMap<>();
        storageItem1.diskProperties.put("key1", "value1");

        StorageProfileService.StorageItem storageItem2 = new StorageProfileService.StorageItem();
        storageItem2.defaultItem = true;
        storageItem2.name = "slow";
        storageItem2.tagLinks = new HashSet<>(Arrays.asList(tags.get(2)));
        storageItem2.diskProperties = new HashMap<>();
        storageItem2.diskProperties.put("key1", "value1");
        storageItem2.diskProperties.put("key2", "value2");

        StorageProfileService.StorageItem storageItem3 = new StorageProfileService.StorageItem();
        storageItem3.defaultItem = false;
        storageItem3.name = "temporary";
        storageItem3.tagLinks = new HashSet<>(Arrays.asList(tags.get(0), tags.get(2), tags.get(4)));
        storageItem3.diskProperties = new HashMap<>();
        storageItem3.diskProperties.put("key1", "value1");
        storageItem3.diskProperties.put("key2", "value2");
        storageItem3.diskProperties.put("key3", "value3");

        StorageProfileService.StorageItem storageItem4 = new StorageProfileService.StorageItem();
        storageItem4.defaultItem = false;
        storageItem4.name = "random";
        storageItem4.tagLinks = new HashSet<>(Arrays.asList(tags.get(3), tags.get(4)));
        storageItem4.diskProperties = new HashMap<>();
        storageItem4.diskProperties.put("key1", "value1");
        storageItem4.diskProperties.put("key2", "value2");
        storageItem4.diskProperties.put("key3", "value3");
        storageItem4.diskProperties.put("key4", "value4");

        StorageProfileService.StorageProfile storageProfile = new StorageProfileService.StorageProfile();
        storageProfile.storageItems = new ArrayList<>();
        storageProfile.storageItems.add(storageItem1);
        storageProfile.storageItems.add(storageItem2);
        storageProfile.storageItems.add(storageItem3);
        storageProfile.storageItems.add(storageItem4);

        return storageProfile;
    }

    protected ArrayList<String> buildTagLinks(List<String> tenantLinks) throws Throwable {
        TagService.TagState fastTag = new TagService.TagState();
        fastTag.key = "FAST";
        fastTag.value = "";
        fastTag.tenantLinks = tenantLinks;
        fastTag = doPost(fastTag, TagService.FACTORY_LINK);

        TagService.TagState haTag = new TagService.TagState();
        haTag.key = "HA";
        haTag.value = "";
        haTag.tenantLinks = tenantLinks;
        haTag = doPost(haTag, TagService.FACTORY_LINK);

        TagService.TagState logsTag = new TagService.TagState();
        logsTag.key = "LOGS_OPTIMIZED";
        logsTag.value = "";
        logsTag.tenantLinks = tenantLinks;
        logsTag = doPost(logsTag, TagService.FACTORY_LINK);

        TagService.TagState criticalTag = new TagService.TagState();
        criticalTag.key = "CRITICAL";
        criticalTag.value = "";
        criticalTag.tenantLinks = tenantLinks;
        criticalTag = doPost(criticalTag, TagService.FACTORY_LINK);

        TagService.TagState nonCriticalTag = new TagService.TagState();
        nonCriticalTag.key = "REPLICATED";
        nonCriticalTag.value = "";
        nonCriticalTag.tenantLinks = tenantLinks;
        nonCriticalTag = doPost(nonCriticalTag, TagService.FACTORY_LINK);

        ArrayList<String> tags = new ArrayList<>();
        tags.add(fastTag.documentSelfLink);
        tags.add(haTag.documentSelfLink);
        tags.add(logsTag.documentSelfLink);
        tags.add(criticalTag.documentSelfLink);
        tags.add(nonCriticalTag.documentSelfLink);

        return tags;
    }

    protected ProfileState createProfile()
            throws Throwable {
        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.isolationType = IsolationSupportType.NONE;
        networkProfile.subnetLinks = Arrays.asList(createSubnetState(null).documentSelfLink);
        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);
        ProfileState profile = createProfile(null, null, networkProfile, null, null);
        assertNotNull(profile);

        return profile;
    }

    protected ProfileState createIsolatedSubnetNetworkProfileWithExternalSubnetLink()
            throws Throwable {
        NetworkProfile networkProfile = createNetworkProfile();
        SubnetState subnetState = createSubnet("external-subnet");
        createSubnetRange(subnetState, "192.168.0.10", "192.168.0.20");
        networkProfile.isolationExternalSubnetLink = subnetState.documentSelfLink;
        doPatch(networkProfile, networkProfile.documentSelfLink);
        ProfileState profile = createProfile(null, null, networkProfile, null, null);
        assertNotNull(profile);

        return profile;
    }

    protected SubnetRangeState createSubnetRange(SubnetState subnetState, String startIp, String endIp)
            throws Throwable {
        SubnetRangeState subnetRangeState = new SubnetRangeState();
        subnetRangeState.startIPAddress = startIp;
        subnetRangeState.endIPAddress = endIp;
        subnetRangeState.ipVersion = IPVersion.IPv4;
        subnetRangeState.subnetLink = subnetState.documentSelfLink;
        return doPost(subnetRangeState, SubnetRangeService.FACTORY_LINK);
    }

    protected ProfileState createIsolatedSubnetNetworkProfile() throws Throwable {
        ProfileState profile = createProfile(null, null, createNetworkProfile(), null, null);
        assertNotNull(profile);

        return profile;
    }

    protected ProfileService.ProfileStateExpanded createExpandedIsolatedSubnetNetworkProfile()
            throws Throwable {
        ProfileService.ProfileStateExpanded p1 = createProfileWithInstanceTypeAndNetworkProfile(
                "small", "t2.micro", "coreos", "ami-234355", null, createNetworkProfile(),
                computeGroupPlacementState);
        assertNotNull(p1);
        return p1;
    }

    protected ProfileStateExpanded createProfileWithInstanceTypeAndNetworkProfile(
            String instanceTypeKey,
            String instanceTypeValue, String imageKey, String imageValue,
            StorageProfile storageProfile,
            NetworkProfile np, GroupResourcePlacementState computeGroupPlacementState)
            throws Throwable {
        ComputeProfile cp1 = new ComputeProfile();
        cp1.instanceTypeMapping = new HashMap<>();
        InstanceTypeDescription itd = new InstanceTypeDescription();
        itd.instanceType = instanceTypeValue;
        cp1.instanceTypeMapping.put(instanceTypeKey, itd);

        ComputeImageDescription cid = new ComputeImageDescription();
        cid.image = imageValue;
        cp1.imageMapping = new HashMap<>();
        cp1.imageMapping.put(imageKey, cid);

        return createProfile(cp1, storageProfile, np, computeGroupPlacementState.tenantLinks, null);
    }

    protected NetworkProfile createNetworkProfile() throws Throwable {
        ComputeNetworkCIDRAllocationState cidrAllocation = createNetworkCIDRAllocationState();

        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.name = "networkProfileName";
        networkProfile.isolationType = IsolationSupportType.SUBNET;
        networkProfile.isolationNetworkLink = cidrAllocation.networkLink;
        networkProfile.isolationNetworkCIDR = "192.168.0.0/16";
        networkProfile.isolatedSubnetCIDRPrefix = 16;
        return doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

    }

    protected ComputeNetworkCIDRAllocationState createNetworkCIDRAllocationState() throws
            Throwable {
        EndpointState epState = TestRequestStateFactory.createEndpoint();
        NetworkState network = new NetworkState();
        network.subnetCIDR = NETWORK_CIDR;
        network.name = "IsolatedNetwork";
        network.endpointLink = epState.documentSelfLink;
        network.instanceAdapterReference = UriUtils.buildUri("/instance-adapter-reference");
        network.resourcePoolLink = "/dummy-resource-pool-link";
        network.regionId = "dummy-region-id";
        network = doPost(network, NetworkService.FACTORY_LINK);
        return createNetworkCIDRAllocationState(network.documentSelfLink);
    }

    protected ComputeNetworkCIDRAllocationState createNetworkCIDRAllocationState(String networkLink)
            throws Throwable {
        ComputeNetworkCIDRAllocationState state = new ComputeNetworkCIDRAllocationState();
        state.networkLink = networkLink;
        return doPost(state, ComputeNetworkCIDRAllocationService.FACTORY_LINK);
    }
}
