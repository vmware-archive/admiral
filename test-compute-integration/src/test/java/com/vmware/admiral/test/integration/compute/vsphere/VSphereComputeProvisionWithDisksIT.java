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

package com.vmware.admiral.test.integration.compute.vsphere;

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
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

public class VSphereComputeProvisionWithDisksIT extends VsphereComputeProvisionIT {

    private static final String DEFAULT_SUBNET_NAME = "VM Network";
    private static final long BOOT_DISK_SIZE = 62 * 1024L;
    private static final long NEW_DISK_SIZE = 3 * 1024L;
    private static final String GENERAL_DISK = "general";
    private static final String FAST_DISK = "fast";
    private static final String PROVISIONING_TYPE = "provisioningType";
    private static final String SHARES_LEVEL = "sharesLevel";
    private static final String INDEPENDENT = "independent";
    private static final String LIMIT_IOPS = "limitIops";

    private static final Map<String, String> tagMap = new HashMap<>();

    @Override
    public void doSetUp() throws Exception {
        createProfile(loadComputeProfile(getEndpointType()), createNetworkProfile(
                DEFAULT_SUBNET_NAME, null, null), createStorageProfile(""));
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
                assertTrue(diskState.customProperties.containsKey(PROVISIONING_TYPE));
                assertEquals("thin", diskState.customProperties.get(PROVISIONING_TYPE));
                assertTrue(diskState.customProperties.containsKey(SHARES_LEVEL));
                assertEquals("normal", diskState.customProperties.get(SHARES_LEVEL));
                break;
            case "disk-2":
                assertEquals("disk-2", diskState.name);
                assertEquals(NEW_DISK_SIZE, diskState.capacityMBytes);
                assertNotNull(diskState.customProperties);
                assertTrue(diskState.customProperties.containsKey(SHARES_LEVEL));
                assertEquals("high", diskState.customProperties.get(SHARES_LEVEL));
                assertTrue(diskState.customProperties.containsKey(INDEPENDENT));
                assertEquals("true", diskState.customProperties.get(INDEPENDENT));
                break;
            case "disk-3":
                assertEquals("disk-3", diskState.name);
                assertEquals(NEW_DISK_SIZE, diskState.capacityMBytes);
                assertNotNull(diskState.customProperties);
                assertTrue(diskState.customProperties.containsKey(LIMIT_IOPS));
                assertEquals("500", diskState.customProperties.get(LIMIT_IOPS));
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
            throws Exception {
        StorageProfileService.StorageProfile storageProfile = loadStorageProfile(getEndpointType());
        markItemAsDeprecated(storageProfile.storageItems, deprecatedItemName);
        addTagLinks(storageProfile.storageItems);
        createStorageDescription(storageProfile.storageItems);
        return storageProfile;
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
            throws Exception {
        for (StorageProfileService.StorageItem storageItem : storageItems) {
            if (!tagMap.containsKey(storageItem.name)) {
                String tagLink = createTag(storageItem.name, "");
                tagMap.put(storageItem.name, tagLink);
            }
            storageItem.tagLinks.add(tagMap.get(storageItem.name));
        }
    }

    private void createStorageDescription(List<StorageProfileService.StorageItem> storageItems)
            throws Exception {
        StorageDescriptionService.StorageDescription sd = new StorageDescriptionService.StorageDescription();
        String datastoreId = getTestRequiredProp("test.vsphere.datastore.path");
        sd.id = sd.name = datastoreId.substring(datastoreId.lastIndexOf("/") + 1, datastoreId.length());
        sd = postDocument(StorageDescriptionService.FACTORY_LINK, sd, documentLifeCycle);

        for (StorageProfileService.StorageItem storageItem : storageItems) {
            storageItem.storageDescriptionLink = sd.documentSelfLink;
        }
    }

    @Override
    protected String getProfileName() {
        return "VSphereComputeEnv";
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
            disk.persistent = true;
            disk.constraint = addConstraint(i);
            disk = postDocument(DiskService.FACTORY_LINK, disk, documentLifeCycle);
            diskStateLinks.add(disk.documentSelfLink);
        }
        return diskStateLinks;
    }

    private long getDiskSize(int index) {
        switch (index) {
        case 1:
            return BOOT_DISK_SIZE;
        default:
            return NEW_DISK_SIZE;
        }
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
}
