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

package com.vmware.admiral.test.integration.compute.aws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.net.URL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.Constraint.Condition;
import com.vmware.photon.controller.model.Constraint.Condition.Enforcement;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

public class AwsComputeProvisionWithRootAndAdditionalDisksIT extends AwsComputeProvisionIT {

    private static final String AWS_DEFAULT_SUBNET_NAME = "subnet1";
    private static final String AWS_SECONDARY_SUBNET_NAME = "subnet2";
    private static final long BOOT_DISK_SIZE = 32 * 1024L;
    private static final long NEW_DISK_SIZE = 16 * 1024L;
    private static final String GENERAL_DISK = "general";
    private static final String FAST_DISK = "fast";
    private static final String VOLUME_TYPE = "volumeType";
    private static final String IOPS = "iops";

    private static final Map<String, String> tagMap = new HashMap<>();

    @Override
    protected void doSetUp() throws Throwable {
        createProfile(loadComputeProfile(getEndpointType()), createNetworkProfile(
                AWS_SECONDARY_SUBNET_NAME, null, null), createStorageProfile(GENERAL_DISK));

        createProfile(loadComputeProfile(getEndpointType()), createNetworkProfile(
                AWS_DEFAULT_SUBNET_NAME, null, null), createStorageProfile(""));
    }

    @Override
    protected void validateDisks(List<String> diskLinks) throws Exception {
        for (String diskLink : diskLinks) {
            DiskService.DiskState diskState = getDocument(diskLink, DiskService.DiskState.class);
            switch (diskState.name) {
            case "disk-1":
                assertEquals("disk-1", diskState.name);
                assertEquals(BOOT_DISK_SIZE, diskState.capacityMBytes);
                assertNotNull(diskState.customProperties);
                assertTrue(diskState.customProperties.containsKey(VOLUME_TYPE));
                assertEquals("standard", diskState.customProperties.get(VOLUME_TYPE));
                break;
            case "disk-2":
                assertEquals("disk-2", diskState.name);
                assertEquals(NEW_DISK_SIZE, diskState.capacityMBytes);
                assertNotNull(diskState.customProperties);
                assertTrue(diskState.customProperties.containsKey(VOLUME_TYPE));
                assertEquals("gp2", diskState.customProperties.get(VOLUME_TYPE));
                break;
            case "disk-3":
                assertEquals("disk-3", diskState.name);
                assertEquals(NEW_DISK_SIZE, diskState.capacityMBytes);
                assertNotNull(diskState.customProperties);
                assertTrue(diskState.customProperties.containsKey(VOLUME_TYPE));
                assertEquals("io1", diskState.customProperties.get(VOLUME_TYPE));
                assertTrue(diskState.customProperties.containsKey(IOPS));
                assertEquals("400", diskState.customProperties.get(IOPS));
                break;
            default:
                break;
            }
        }
    }

    @Override
    protected ComputeDescriptionService.ComputeDescription createComputeDescription(
            boolean withDisks, String imageId)
            throws Exception {
        if (imageId == null) {
            imageId = "ubuntu-1604";
        }

        return super.createComputeDescription(withDisks, imageId);
    }

    private StorageProfileService.StorageProfile createStorageProfile(String deprecatedItemName)
            throws Throwable {
        StorageProfileService.StorageProfile storageProfile = loadStorageProfile(getEndpointType());
        markItemAsDeprecated(storageProfile.storageItems, deprecatedItemName);
        addTagLinks(storageProfile.storageItems);
        return storageProfile;
    }

    private void markItemAsDeprecated(List<StorageProfileService.StorageItem> storageItems,
            String deprecatedItemName) {
        StorageProfileService.StorageItem deprecatedstorageItem = storageItems.stream()
                .filter(item ->
                        item.name.equals(deprecatedItemName))
                .findFirst()
                .orElse(null);

        if (deprecatedstorageItem != null) {
            deprecatedstorageItem.name = "deprecated-" + deprecatedstorageItem.name;
        }
    }

    //storageItem.name is used as tag key
    private void addTagLinks(List<StorageProfileService.StorageItem> storageItems)
            throws Throwable {
        for (StorageProfileService.StorageItem storageItem : storageItems) {
            if (!tagMap.containsKey(storageItem.name)) {
                String tagLink = createTag(storageItem.name, "");
                tagMap.put(storageItem.name, tagLink);
            }
            storageItem.tagLinks.add(tagMap.get(storageItem.name));
        }
    }

    private StorageProfileService.StorageProfile loadStorageProfile(String endpointType) {
        URL r = getClass().getClassLoader().getResource(
                "test-" + endpointType.toLowerCase() + "-storage-profile.yaml");

        try (InputStream is = r.openStream()) {
            return YamlMapper.objectMapper().readValue(is, StorageProfile.class);
        } catch (Exception e) {
            logger.error("Failure reading default environment: %s, reason: %s", r,
                    e.getMessage());
            return null;
        }
    }

    @Override
    protected String getProfileName() {
        return "AWSComputeEnv";
    }

    @Override
    protected String getResourceDescriptionLink() throws Exception {
        return getResourceDescriptionLink(true, null);
    }

    @Override
    protected List<String> createDiskStates() throws Exception {
        List<String> diskLinks = new ArrayList<>();
        diskLinks.addAll(constructDisks());
        return diskLinks;
    }

    /**
     * Construct Disk states for boot disk and two new additional disks.
     */
    private List<String> constructDisks() throws Exception {
        List<String> diskStateLinks = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            DiskService.DiskState disk = new DiskService.DiskState();
            disk.id = UUID.randomUUID().toString();
            disk.documentSelfLink = disk.id;
            disk.name = "disk-" + i;
            disk.type = DiskService.DiskType.HDD;
            if (i == 1) {
                disk.bootOrder = 1;
            }
            disk.capacityMBytes = getDiskSize(i);
            disk.constraint = addConstraint(i);
            disk = postDocument(DiskService.FACTORY_LINK, disk, documentLifeCycle);
            diskStateLinks.add(disk.documentSelfLink);
        }
        return diskStateLinks;
    }

    private Constraint addConstraint(int index) {
        switch (index) {
        case 2:
            //add hard constraint for first additional disk
            return getConstraint(GENERAL_DISK, Enforcement.HARD,
                    Occurance.MUST_OCCUR);
        case 3:
            //add soft constraint for second additional disk
            return getConstraint(FAST_DISK, Enforcement.SOFT,
                    Occurance.SHOULD_OCCUR);
        default:
            break;
        }
        return null;
    }

    private Constraint getConstraint(String tagName, Enforcement enforcement, Occurance
            occurance) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(Condition.forTag(tagName, "", enforcement, occurance));
        Constraint constraint = new Constraint();
        constraint.conditions = conditions;
        return constraint;
    }

    private long getDiskSize(int bootOrder) {
        switch (bootOrder) {
        case 1:
            return BOOT_DISK_SIZE;
        default:
            return NEW_DISK_SIZE;
        }
    }
}
