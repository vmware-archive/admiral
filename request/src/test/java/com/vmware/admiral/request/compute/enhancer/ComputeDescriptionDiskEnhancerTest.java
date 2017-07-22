/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.compute.enhancer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.profile.ComputeProfileService;
import com.vmware.admiral.compute.profile.ImageProfileService;
import com.vmware.admiral.compute.profile.InstanceTypeService.InstanceTypeFactoryService;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.host.HostInitServiceHelper;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.adapters.vsphere.CustomProperties;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Test various combinations of disk enhancer.
 */
public class ComputeDescriptionDiskEnhancerTest extends BaseComputeDescriptionEnhancerTest {

    @Before
    public void setup() throws Throwable {
        HostInitServiceHelper.startServices(host, TestInitialBootService.class,
                InstanceTypeFactoryService.class);
        HostInitServiceHelper.startServiceFactories(host,
                ProfileService.class,
                ComputeProfileService.class, StorageProfileService.class, ImageProfileService.class,
                NetworkProfileService.class, DiskService.class, StorageDescriptionService.class,
                ResourceGroupService.class);
        host.startFactory(TagService.class, TagFactoryService::new);
        waitForServiceAvailability(ProfileService.FACTORY_LINK);
        host.sendRequest(Operation.createPost(
                UriUtils.buildUri(host, TestInitialBootService.class))
                .setReferer(host.getUri())
                .setBody(new ServiceDocument()));
        waitForInitialBootServiceToBeSelfStopped(TestInitialBootService.SELF_LINK);

        cd = new ComputeDescription();
        cd.customProperties = new HashMap<>();
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, "ubuntu-1604");
        cd.instanceType = "xLarge";
    }

    @Test
    public void testEnhanceDisk() throws Throwable {
        // Build disk description
        cd.diskDescLinks = buildDiskStates();

        createEnhanceContext(buildStorageProfileWithConstraints());
        // Use case 1: CD (Disk1) with all hard constraints
        // Use case 2: CD (Disk2) with all soft constraints & all matching
        // Use case 3: CD (Disk3) with all hard & soft constraints
        // Use case 4: CD (Disk4) with all soft constraints & few matching
        // Use case 5: CD (Disk5) with all soft constraints & nothing match
        // Use case 6: CD (Disk6) with constraint null.

        enhance(new ComputeDescriptionDiskEnhancer(host, host.getReferer()));

        assertDiskStates(diskState -> {
            if (diskState.name.equals("Disk1") || diskState.name.equals("Disk3")) {
                assertNotNull(diskState.customProperties);
                assertEquals(1, diskState.customProperties.size());
            } else if (diskState.name.equals("Disk2")) {
                assertNotNull(diskState.customProperties);
                assertEquals(3, diskState.customProperties.size());
            } else if (diskState.name.equals("Disk4")) {
                assertNotNull(diskState.customProperties);
                assertEquals(4, diskState.customProperties.size());
            } else if (diskState.name.equals("Disk5") || diskState.name.equals("Disk6")) {
                assertNotNull(diskState.customProperties);
                assertEquals(2, diskState.customProperties.size());
            }
        });
    }

    @Test
    public void testEnhanceDiskWithDefaultStorageItem() throws Throwable {
        // Build disk description
        cd.diskDescLinks = Arrays.asList(buildDiskState1(false, false).documentSelfLink);

        createEnhanceContext(buildStorageProfileWithConstraints());
        // Use case 1: CD (Disk1) with no constraints and has default storage item as a match

        enhance(new ComputeDescriptionDiskEnhancer(host, host.getReferer()));

        assertDiskStates(diskState -> {
            if (diskState.name.equals("Disk1")) {
                assertNotNull(diskState.customProperties);
                assertEquals(2, diskState.customProperties.size());
            }
        });
    }

    @Test
    public void testFailureEnhanceEncryptedDiskWithDefaultStorageItem() throws Throwable {
        // Build disk description
        cd.diskDescLinks = Arrays.asList(buildDiskState1(true, false).documentSelfLink);

        createEnhanceContext(buildStorageProfileWithConstraints());
        // Use case 1: CD (Disk1) with no constraints and has default storage item as a match

        enhanceDiskFailure();
    }

    @Test
    public void testEnhanceDiskWithNoConstraintAndEncryption() throws Throwable {
        // Build disk description
        cd.diskDescLinks = Arrays.asList(buildDiskState1(true, false).documentSelfLink);

        createEnhanceContext(buildStorageProfileForEncryption());

        enhance(new ComputeDescriptionDiskEnhancer(host, host.getReferer()));

        assertDiskStates(diskState -> {
            if (diskState.name.equals("Disk1")) {
                assertNotNull(diskState.customProperties);
                assertEquals(1, diskState.customProperties.size());
            }
        });
    }

    @Test
    public void testSuccessEnhanceDiskWithEncryption() throws Throwable {
        // Build disk description
        cd.diskDescLinks = buildDiskStatesForEncryption();

        createEnhanceContext(buildStorageProfileForEncryption(false, true));
        // Use case 1: CD (Disk1) with all hard constraints & encryption
        // Use case 2: CD (Disk2) with all soft constraints & encryption

        enhance(new ComputeDescriptionDiskEnhancer(host, host.getReferer()));

        assertDiskStates(diskState -> {
            if (diskState.name.equals("Disk1") || diskState.name.equals("Disk2")) {
                assertNotNull(diskState.customProperties);
                assertEquals(2, diskState.customProperties.size());
                assertNotNull(diskState.storageDescriptionLink);
            }
        });
    }

    @Test
    public void testFailureEnhanceDiskWithEncryption() throws Throwable {
        // Build disk description
        cd.diskDescLinks = buildDiskStatesForEncryption();

        createEnhanceContext(buildStorageProfileForEncryption(false, false));
        // Both should fail.
        // Use case 1: Disk1 with all hard constraints & encryption.
        // Use case 2: Disk2 with all soft constraints & encryption
        enhanceDiskFailure();

        // Now get all the disk states to find the properties size.
        assertDiskStates(diskState -> assertNull(diskState.customProperties));
    }

    @Test
    public void testEnhanceDiskWithResourceGroup() throws Throwable {
        // Build disk description
        cd.diskDescLinks = Arrays.asList(buildDiskState1(true).documentSelfLink);

        createEnhanceContext(buildStorageProfileForEncryption(createResourceGroupState()
                .documentSelfLink));
        // Use case 1: CD (Disk1) with all hard constraints & encryption for matching SI with RG
        // support of encryption

        enhance(new ComputeDescriptionDiskEnhancer(host, host.getReferer()));

        assertDiskStates(diskState -> {
            assertNotNull(diskState.customProperties);
            assertEquals(1, diskState.customProperties.size());
            assertNotNull(diskState.groupLinks);
            assertEquals(1, diskState.groupLinks.size());
        });
    }

    @Test
    public void testEnhanceDiskWithSDAndResourceGroup() throws Throwable {
        // Build disk description
        cd.diskDescLinks = Arrays.asList(buildDiskState1(true).documentSelfLink);

        createEnhanceContext(buildStorageProfileForEncryption());
        // Use case 1: CD (Disk1) with all hard constraints & encryption for matching SI with RG
        // support of encryption

        enhance(new ComputeDescriptionDiskEnhancer(host, host.getReferer()));

        assertDiskStates(diskState -> {
            assertNotNull(diskState.customProperties);
            assertEquals(1, diskState.customProperties.size());
        });
    }

    @Test
    public void testEnhanceDiskWithDefaultEncryption() throws Throwable {
        // Build disk description
        cd.diskDescLinks = Arrays.asList(buildDiskState1(true).documentSelfLink);

        createEnhanceContext(buildStorageProfileForEncryption((String) null));
        // Use case 1: CD (Disk2) with all soft constraints & encryption for matching SI's
        // support of encryption

        enhance(new ComputeDescriptionDiskEnhancer(host, host.getReferer()));

        assertDiskStates(diskState -> {
            assertNotNull(diskState.customProperties);
            assertEquals(2, diskState.customProperties.size());
        });
    }

    @Test
    public void testEnhanceDiskWithNoStorageItems() throws Throwable {
        createEnhanceContext();
        // Build disk description
        cd.diskDescLinks = buildDiskStatesForNoStorageItems();

        // Use case 1: Disk1 with all hard constraints. It should fail.
        // Use case 2: Disk2 with no constraints.
        ComputeDescriptionDiskEnhancer enhancer = new ComputeDescriptionDiskEnhancer(host,
                host.getReferer());
        DeferredResult<ComputeDescription> result = enhancer.enhance(context, cd);

        TestContext ctx = testCreate(1);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                assertNotNull(t.getMessage());
                assertTrue(t.getMessage().contains("No matching storage defined in profile"));
                ctx.completeIteration();
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        // Now get all the disk states to find the properties size.
        assertDiskStates(diskState -> assertNull(diskState.customProperties));
    }

    @Test
    public void testEnhanceDiskWithNoStorageItemsForSoftConstraint() throws Throwable {
        createEnhanceContext();
        // Build disk description
        ArrayList<String> diskLinks = buildSoftConstraintDisk();
        cd.diskDescLinks = diskLinks;

        // Use case 1: Disk1 with all soft constraints. It shouldn't fail
        enhance(new ComputeDescriptionDiskEnhancer(this.host, this.host.getReferer()));

        // Now get all the disk states to find the properties size.
        assertDiskStates(diskState -> assertNull(diskState.customProperties));
    }

    @Test
    public void testEnhanceDiskCreateOsDisk() throws Throwable {

        cd.diskDescLinks = Collections.emptyList();

        createEnhanceContext();

        enhance(new ComputeDescriptionDiskEnhancer(host, host.getReferer()));

        assertEquals("OS DiskState is not created", 1, cd.diskDescLinks.size());

        // Guarantees the OS Disk State is persisted
        assertDiskStates(diskState -> {
        });
    }

    @Test
    public void testEnhanceDiskCreateOsDisk_skipPersistence() throws Throwable {

        cd.diskDescLinks = Collections.emptyList();

        createEnhanceContext();
        context.skipPersistence = true;

        enhance(new ComputeDescriptionDiskEnhancer(host, host.getReferer()));

        assertEquals("OS DiskState is not created", 1, cd.diskDescLinks.size());

        // Guarantees the OS Disk State is NOT persisted
        List<String> diskStateLinks = getDocumentLinksOfType(DiskState.class);
        assertTrue("OS DiskState should not have been persisted", diskStateLinks.isEmpty());
    }

    private void enhanceDiskFailure() {
        ComputeDescriptionDiskEnhancer enhancer = new ComputeDescriptionDiskEnhancer(host,
                host.getReferer());
        DeferredResult<ComputeDescription> result = enhancer.enhance(context, cd);

        TestContext ctx = testCreate(1);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                assertNotNull(t.getMessage());
                assertTrue(t.getMessage().contains("No matching storage defined in profile"));
                ctx.completeIteration();
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();
    }

    private void createEnhanceContext() {
        String awsEndpointType = PhotonModelConstants.EndpointType.aws.name();
        context = new Enhancer.EnhanceContext();
        context.endpointType = awsEndpointType;
        context.profileLink = UriUtils.buildUriPath(ProfileService.FACTORY_LINK,
                awsEndpointType);
        context.regionId = CommonTestStateFactory.ENDPOINT_REGION_ID;
        context.resolvedImage = "vc://datastore/test.iso";
    }

    private void createEnhanceContext(StorageProfile storageProfile) throws Throwable {
        context = new Enhancer.EnhanceContext();
        context.profile = buildProfileServiceWithStorage(storageProfile);
        context.profileLink = context.profile.documentSelfLink;
        context.resolvedImage = "vc://datastore/test.iso";
    }

    private ProfileService.ProfileStateExpanded buildProfileServiceWithStorage(
            StorageProfile storageProfile) throws Throwable {
        ComputeProfileService.ComputeProfile compute = new ComputeProfileService.ComputeProfile();
        compute = doPost(compute, ComputeProfileService.FACTORY_LINK);
        StorageProfile storage = storageProfile;
        NetworkProfileService.NetworkProfile networkProfile = new NetworkProfileService.NetworkProfile();
        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

        ProfileService.ProfileState profile = new ProfileService.ProfileState();
        profile.name = "test profile";
        profile.endpointType = PhotonModelConstants.EndpointType.vsphere.name();
        profile.computeProfileLink = compute.documentSelfLink;
        profile.storageProfileLink = storage.documentSelfLink;
        profile.networkProfileLink = networkProfile.documentSelfLink;
        profile = doPost(profile, ProfileService.FACTORY_LINK);

        ProfileService.ProfileState retrievedProfile = getDocument(
                ProfileService.ProfileState.class, profile.documentSelfLink);
        assertEquals(storage.documentSelfLink, retrievedProfile.storageProfileLink);

        ProfileService.ProfileStateExpanded retrievedExpandedProfile = getDocument(
                ProfileService.ProfileStateExpanded.class,
                profile.documentSelfLink,
                UriUtils.URI_PARAM_ODATA_EXPAND,
                ServiceDocumentQueryResult.FIELD_NAME_DOCUMENT_LINKS);
        assertEquals(storage.documentSelfLink, retrievedExpandedProfile.storageProfileLink);
        assertEquals(storage.documentSelfLink,
                retrievedExpandedProfile.storageProfile.documentSelfLink);

        return retrievedExpandedProfile;
    }

    private StorageProfile buildStorageProfileWithConstraints()
            throws Throwable {
        ArrayList<String> tags = buildTagLinks();

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

        StorageProfile storageProfile = new StorageProfile();
        storageProfile.storageItems = new ArrayList<>();
        storageProfile.storageItems.add(storageItem1);
        storageProfile.storageItems.add(storageItem2);
        storageProfile.storageItems.add(storageItem3);
        storageProfile.storageItems.add(storageItem4);

        storageProfile = doPost(storageProfile, StorageProfileService.FACTORY_LINK);
        return storageProfile;
    }

    private ArrayList<String> buildTagLinks() throws Throwable {
        TagService.TagState fastTag = new TagService.TagState();
        fastTag.key = "FAST";
        fastTag.value = "";
        fastTag = doPost(fastTag, TagService.FACTORY_LINK);

        TagService.TagState haTag = new TagService.TagState();
        haTag.key = "HA";
        haTag.value = "";
        haTag = doPost(haTag, TagService.FACTORY_LINK);

        TagService.TagState logsTag = new TagService.TagState();
        logsTag.key = "LOGS_OPTIMIZED";
        logsTag.value = "";
        logsTag = doPost(logsTag, TagService.FACTORY_LINK);

        TagService.TagState criticalTag = new TagService.TagState();
        criticalTag.key = "CRITICAL";
        criticalTag.value = "";
        criticalTag = doPost(criticalTag, TagService.FACTORY_LINK);

        TagService.TagState nonCriticalTag = new TagService.TagState();
        nonCriticalTag.key = "REPLICATED";
        nonCriticalTag.value = "";
        nonCriticalTag = doPost(nonCriticalTag, TagService.FACTORY_LINK);

        ArrayList<String> tags = new ArrayList<>();
        tags.add(fastTag.documentSelfLink);
        tags.add(haTag.documentSelfLink);
        tags.add(logsTag.documentSelfLink);
        tags.add(criticalTag.documentSelfLink);
        tags.add(nonCriticalTag.documentSelfLink);

        return tags;
    }

    private StorageDescriptionService.StorageDescription createStorageDescription(ArrayList<String> tags)
            throws Throwable {
        StorageDescriptionService.StorageDescription storageDescription = new StorageDescriptionService.StorageDescription();
        storageDescription.id = storageDescription.name = "datastore1";
        storageDescription.supportsEncryption = false;
        storageDescription.tagLinks = new HashSet<>(Arrays.asList(tags.get(1)));
        storageDescription.groupLinks = new HashSet<>();
        storageDescription.groupLinks.add(createResourceGroupState().documentSelfLink);

        storageDescription = doPost(storageDescription, StorageDescriptionService.FACTORY_LINK);
        return storageDescription;
    }

    private StorageDescriptionService.StorageDescription createStorageDescription(boolean supportEncryption, ArrayList<String> tags)
            throws Throwable {
        StorageDescriptionService.StorageDescription storageDescription = new StorageDescriptionService.StorageDescription();
        storageDescription.id = storageDescription.name = "datastore1";
        storageDescription.supportsEncryption = supportEncryption;
        storageDescription.tagLinks = new HashSet<>(Arrays.asList(tags.get(1)));

        storageDescription = doPost(storageDescription, StorageDescriptionService.FACTORY_LINK);
        return storageDescription;
    }

    private ResourceGroupService.ResourceGroupState createResourceGroupState()
            throws Throwable {
        ResourceGroupService.ResourceGroupState resourceGroupState = new ResourceGroupService.ResourceGroupState();
        resourceGroupState.name = "storage policy1";
        resourceGroupState.id = "unique id";
        CustomProperties.of(resourceGroupState).put(ComputeDescriptionDiskEnhancer
                .FIELD_NAME_CUSTOM_PROP_SUPPORTS_ENCRYPTION, "true");
        resourceGroupState = doPost(resourceGroupState, ResourceGroupService.FACTORY_LINK);
        return resourceGroupState;
    }

    private ArrayList<String> buildDiskStates() throws Throwable {
        ArrayList<String> diskLinks = new ArrayList<>();

        DiskService.DiskState diskState1 = buildDiskState1(false);
        diskLinks.add(diskState1.documentSelfLink);

        DiskService.DiskState diskState2 = new DiskService.DiskState();
        diskState2.capacityMBytes = 2048;
        diskState2.type = DiskService.DiskType.SSD;
        diskState2.bootOrder = 2;
        diskState2.name = "Disk2";
        diskState2.constraint = new Constraint();

        List<Constraint.Condition> conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("LOGS_OPTIMIZED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("REPLICATED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState2.constraint.conditions = conditions;
        diskState2 = doPost(diskState2, DiskService.FACTORY_LINK);
        diskLinks.add(diskState2.documentSelfLink);

        DiskService.DiskState diskState3 = new DiskService.DiskState();
        diskState3.capacityMBytes = 1024;
        diskState3.type = DiskService.DiskType.CDROM;
        diskState3.bootOrder = 3;
        diskState3.name = "Disk3";
        diskState3.constraint = new Constraint();

        conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("FAST", null,
                Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("HA", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState3.constraint.conditions = conditions;
        diskState3 = doPost(diskState3, DiskService.FACTORY_LINK);
        diskLinks.add(diskState3.documentSelfLink);

        DiskService.DiskState diskState4 = new DiskService.DiskState();
        diskState4.capacityMBytes = 1024;
        diskState4.type = DiskService.DiskType.HDD;
        diskState4.bootOrder = 4;
        diskState4.name = "Disk4";
        diskState4.constraint = new Constraint();

        conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("CRITICAL", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("NON_REPLICATED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("NORMAL", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState4.constraint.conditions = conditions;
        diskState4 = doPost(diskState4, DiskService.FACTORY_LINK);
        diskLinks.add(diskState4.documentSelfLink);

        DiskService.DiskState diskState5 = new DiskService.DiskState();
        diskState5.capacityMBytes = 512;
        diskState5.type = DiskService.DiskType.FLOPPY;
        diskState5.bootOrder = 5;
        diskState5.name = "Disk5";
        diskState5.constraint = new Constraint();

        conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("NON_REPLICATED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("NORMAL", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState5.constraint.conditions = conditions;
        diskState5 = doPost(diskState5, DiskService.FACTORY_LINK);
        diskLinks.add(diskState5.documentSelfLink);

        DiskService.DiskState diskState6 = new DiskService.DiskState();
        diskState6.capacityMBytes = 512;
        diskState6.type = DiskService.DiskType.FLOPPY;
        diskState6.bootOrder = 6;
        diskState6.name = "Disk6";
        diskState6.constraint = null;

        diskState6 = doPost(diskState6, DiskService.FACTORY_LINK);
        diskLinks.add(diskState6.documentSelfLink);

        return diskLinks;
    }

    private ArrayList<String> buildDiskStatesForEncryption() throws
            Throwable {
        ArrayList<String> diskLinks = new ArrayList<>();

        DiskService.DiskState diskState1 = buildDiskState1(true);
        diskLinks.add(diskState1.documentSelfLink);

        DiskService.DiskState diskState2 = new DiskService.DiskState();
        diskState2.capacityMBytes = 2048;
        diskState2.type = DiskService.DiskType.SSD;
        diskState2.bootOrder = 2;
        diskState2.name = "Disk2";
        diskState2.encrypted = true;
        diskState2.persistent = true;
        diskState2.constraint = new Constraint();

        List<Constraint.Condition> conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("FAST", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("REPLICATED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState2.constraint.conditions = conditions;
        diskState2 = doPost(diskState2, DiskService.FACTORY_LINK);
        diskLinks.add(diskState2.documentSelfLink);

        return diskLinks;
    }

    private StorageProfile buildStorageProfileForEncryption(String resourceGroupLink)
            throws Throwable {
        ArrayList<String> tags = buildTagLinks();

        StorageProfileService.StorageItem storageItem1 = new StorageProfileService.StorageItem();
        storageItem1.defaultItem = false;
        storageItem1.name = "fast";
        storageItem1.tagLinks = new HashSet<>(Arrays.asList(tags.get(0), tags.get(1)));
        storageItem1.resourceGroupLink = resourceGroupLink;
        storageItem1.diskProperties = new HashMap<>();
        storageItem1.diskProperties.put("key1", "value1");

        StorageProfileService.StorageItem storageItem2 = createStorageItem(tags);
        if (resourceGroupLink == null) {
            storageItem2.supportsEncryption = true;
        }

        StorageProfile storageProfile = new StorageProfile();
        storageProfile.storageItems = new ArrayList<>();
        storageProfile.storageItems.add(storageItem1);
        storageProfile.storageItems.add(storageItem2);

        storageProfile = doPost(storageProfile, StorageProfileService.FACTORY_LINK);
        return storageProfile;
    }

    private StorageProfile buildStorageProfileForEncryption()
            throws Throwable {
        ArrayList<String> tags = buildTagLinks();

        StorageProfileService.StorageItem storageItem1 = new StorageProfileService.StorageItem();
        storageItem1.defaultItem = false;
        storageItem1.name = "fast";
        storageItem1.tagLinks = new HashSet<>(Arrays.asList(tags.get(0)));
        storageItem1.storageDescriptionLink = createStorageDescription(tags).documentSelfLink;
        storageItem1.diskProperties = new HashMap<>();
        storageItem1.diskProperties.put("key1", "value1");

        StorageProfileService.StorageItem storageItem2 = createStorageItem(tags);
        StorageProfile storageProfile = new StorageProfile();
        storageProfile.storageItems = new ArrayList<>();
        storageProfile.storageItems.add(storageItem1);
        storageProfile.storageItems.add(storageItem2);

        storageProfile = doPost(storageProfile, StorageProfileService.FACTORY_LINK);
        return storageProfile;
    }

    /**
     * Create storage item with tags
     */
    private StorageProfileService.StorageItem createStorageItem(ArrayList<String> tags) {
        StorageProfileService.StorageItem storageItem2 = new StorageProfileService.StorageItem();
        storageItem2.defaultItem = true;
        storageItem2.name = "slow";
        storageItem2.tagLinks = new HashSet<>(Arrays.asList(tags.get(0), tags.get(1)));

        storageItem2.diskProperties = new HashMap<>();
        storageItem2.diskProperties.put("key1", "value1");
        storageItem2.diskProperties.put("key2", "value2");

        return storageItem2;
    }

    private StorageProfile buildStorageProfileForEncryption(boolean... supportEncryption)
            throws Throwable {
        ArrayList<String> tags = buildTagLinks();

        StorageProfileService.StorageItem storageItem1 = new StorageProfileService.StorageItem();
        storageItem1.defaultItem = false;
        storageItem1.name = "fast";
        storageItem1.tagLinks = new HashSet<>(Arrays.asList(tags.get(0)));
        storageItem1.storageDescriptionLink = createStorageDescription(supportEncryption[0], tags)
                .documentSelfLink;
        storageItem1.diskProperties = new HashMap<>();
        storageItem1.diskProperties.put("key1", "value1");

        StorageProfileService.StorageItem storageItem2 = new StorageProfileService.StorageItem();
        storageItem2.defaultItem = true;
        storageItem2.name = "slow";
        storageItem2.tagLinks = new HashSet<>(Arrays.asList(tags.get(0)));
        storageItem2.storageDescriptionLink = createStorageDescription(supportEncryption[1], tags)
                .documentSelfLink;
        storageItem2.diskProperties = new HashMap<>();
        storageItem2.diskProperties.put("key1", "value1");
        storageItem2.diskProperties.put("key2", "value2");

        StorageProfile storageProfile = new StorageProfile();
        storageProfile.storageItems = new ArrayList<>();
        storageProfile.storageItems.add(storageItem1);
        storageProfile.storageItems.add(storageItem2);

        storageProfile = doPost(storageProfile, StorageProfileService.FACTORY_LINK);
        return storageProfile;
    }

    private DiskService.DiskState buildDiskState1(boolean isEncrypted) throws Throwable {
        return buildDiskState1(isEncrypted, true);
    }

    private DiskService.DiskState buildDiskState1(boolean isEncrypted, boolean withConstraints)
            throws Throwable {
        DiskService.DiskState diskState1 = new DiskService.DiskState();
        diskState1.capacityMBytes = 1024;
        diskState1.type = DiskService.DiskType.HDD;
        diskState1.bootOrder = 1;
        diskState1.name = "Disk1";
        diskState1.encrypted = isEncrypted;
        diskState1.persistent = true;
        if (withConstraints) {
            diskState1.constraint = new Constraint();

            List<Constraint.Condition> conditions = new ArrayList<>();
            conditions.add(Constraint.Condition.forTag("FAST", null,
                    Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
            conditions.add(Constraint.Condition.forTag("HA", null,
                    Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
            diskState1.constraint.conditions = conditions;
        }
        diskState1 = doPost(diskState1, DiskService.FACTORY_LINK);

        return diskState1;
    }

    private ArrayList<String> buildDiskStatesForNoStorageItems() throws Throwable {
        ArrayList<String> diskLinks = new ArrayList<>();

        DiskService.DiskState diskState1 = new DiskService.DiskState();
        diskState1.capacityMBytes = 1024;
        diskState1.type = DiskService.DiskType.HDD;
        diskState1.bootOrder = 1;
        diskState1.name = "Disk1";
        diskState1.constraint = new Constraint();

        List<Constraint.Condition> conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("FAST", null,
                Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("HA", null,
                Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState1.constraint.conditions = conditions;
        diskState1 = doPost(diskState1, DiskService.FACTORY_LINK);
        diskLinks.add(diskState1.documentSelfLink);

        DiskService.DiskState diskState2 = new DiskService.DiskState();
        diskState2.capacityMBytes = 1024;
        diskState2.type = DiskService.DiskType.CDROM;
        diskState2.bootOrder = 3;
        diskState2.name = "Disk2";
        diskState2.constraint = new Constraint();
        diskState2 = doPost(diskState2, DiskService.FACTORY_LINK);
        diskLinks.add(diskState2.documentSelfLink);

        return diskLinks;
    }

    private ArrayList<String> buildSoftConstraintDisk() throws Throwable {
        ArrayList<String> diskLinks = new ArrayList<>();

        DiskService.DiskState diskState2 = new DiskService.DiskState();
        diskState2.capacityMBytes = 2048;
        diskState2.type = DiskService.DiskType.SSD;
        diskState2.bootOrder = 2;
        diskState2.name = "Disk1";
        diskState2.constraint = new Constraint();

        List<Constraint.Condition> conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("LOGS_OPTIMIZED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("REPLICATED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState2.constraint.conditions = conditions;
        diskState2 = doPost(diskState2, DiskService.FACTORY_LINK);
        diskLinks.add(diskState2.documentSelfLink);

        return diskLinks;
    }
}
