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

import static com.vmware.admiral.common.test.CommonTestStateFactory.ENDPOINT_ID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.profile.ComputeImageDescription;
import com.vmware.admiral.compute.profile.ComputeProfileService;
import com.vmware.admiral.compute.profile.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.compute.profile.InstanceTypeDescription;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.xenon.common.UriUtils;

public class ComputeRequestBaseTest extends RequestBaseTest {

    static final String TEST_VM_NAME = "testVM";

    protected ComputeState vmHostCompute;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        createComputeResourcePool();
        computeGroupPlacementState = createComputeGroupResourcePlacement(computeResourcePool, 10);
        // create a single powered-on compute available for placement
        vmHostCompute = createVmHostCompute(true);
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
}
