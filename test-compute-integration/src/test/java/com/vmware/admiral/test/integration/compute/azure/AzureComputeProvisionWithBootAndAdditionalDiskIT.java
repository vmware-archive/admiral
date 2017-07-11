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

package com.vmware.admiral.test.integration.compute.azure;

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
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.xenon.services.common.QueryTask;

public class AzureComputeProvisionWithBootAndAdditionalDiskIT extends AzureComputeProvisionIT {

    private static final long OS_DISK_SIZE = 32 * 1024;
    private static final long ADDITIONAL_DISK_SIZE = 12 * 1024;

    private static final Map<String, String> tagMap = new HashMap<>();
    public static final String OSDISK_NAME = "OSDisk";
    public static final String DATA_DISK_NAME = "DataDisk";

    @Override protected String getResourceDescriptionLink() throws Exception {
        return getResourceDescriptionLink(true, null);
    }

    @Override
    protected long getRootDiskSize() {
        return OS_DISK_SIZE;
    }

    @Override
    protected void doSetUp() throws Throwable {

        //assuming existing of subnet named "test-subnet0" in test-sharedNetworkRG
        createProfile(loadComputeProfile(getEndpointType()), createNetworkProfile(
                "test-subnet0", null, null), createStorageProfile());
    }

    @Override
    protected void validateDisks(List<String> diskLinks) throws Exception {
        for (String diskLink : diskLinks) {
            DiskService.DiskState diskState = getDocument(diskLink, DiskService.DiskState.class);
            switch (diskState.bootOrder) {
            case 1:
                assertEquals(OSDISK_NAME, diskState.name);
                assertEquals(OS_DISK_SIZE, diskState.capacityMBytes);
                assertNotNull(diskState.customProperties);

                assertTrue(diskState.customProperties.containsKey("azureStorageAccountType"));
                assertEquals("Standard_LRS", diskState.customProperties.get("azureStorageAccountType"));

                assertTrue(diskState.customProperties.containsKey("azureStorageAccountName"));
                assertEquals("azbasicsa", diskState.customProperties.get("azureStorageAccountName"));

                assertTrue(diskState.customProperties.containsKey("azureOsDiskCaching"));
                assertEquals("None", diskState.customProperties.get("azureOsDiskCaching"));

                assertTrue(diskState.customProperties.containsKey("azureDataDiskCaching"));
                assertEquals("ReadWrite", diskState.customProperties.get("azureDataDiskCaching"));
                break;
            case 2:
                assertEquals(DATA_DISK_NAME, diskState.name);
                assertEquals(ADDITIONAL_DISK_SIZE, diskState.capacityMBytes);
                assertNotNull(diskState.customProperties);

                assertTrue(diskState.customProperties.containsKey("azureStorageAccountType"));
                assertEquals("Standard_LRS", diskState.customProperties.get("azureStorageAccountType"));

                assertTrue(diskState.customProperties.containsKey("azureStorageAccountName"));
                assertEquals("azbasicsa", diskState.customProperties.get("azureStorageAccountName"));

                assertTrue(diskState.customProperties.containsKey("azureOsDiskCaching"));
                assertEquals("ReadWrite", diskState.customProperties.get("azureOsDiskCaching"));

                assertTrue(diskState.customProperties.containsKey("azureDataDiskCaching"));
                assertEquals("ReadWrite", diskState.customProperties.get("azureDataDiskCaching"));
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

    @Override
    protected String getProfileName() {
        return "AzureComputeEnv";
    }

    @Override
    protected List<String> createDiskStates() throws Exception {
        List<String> diskLinks = new ArrayList<>();
        diskLinks.addAll(constructDisks());
        return diskLinks;
    }

    private List<String> constructDisks() throws Exception {
        List<String> diskStateLinks = new ArrayList<>();

        DiskService.DiskState bootDisk = new DiskService.DiskState();
        bootDisk.id = UUID.randomUUID().toString();
        bootDisk.documentSelfLink = bootDisk.id;
        bootDisk.name = OSDISK_NAME;
        bootDisk.type = DiskService.DiskType.HDD;
        bootDisk.bootOrder = 1;
        bootDisk.capacityMBytes = OS_DISK_SIZE;

        bootDisk = postDocument(DiskService.FACTORY_LINK, bootDisk, documentLifeCycle);
        diskStateLinks.add(bootDisk.documentSelfLink);

        DiskService.DiskState additionalDisk = new DiskService.DiskState();
        additionalDisk.id = UUID.randomUUID().toString();
        additionalDisk.documentSelfLink = additionalDisk.id;
        additionalDisk.name = DATA_DISK_NAME;
        additionalDisk.type = DiskService.DiskType.HDD;
        additionalDisk.bootOrder = 2;
        additionalDisk.capacityMBytes = ADDITIONAL_DISK_SIZE;
        additionalDisk.constraint = getConstraint("premium", Constraint.Condition.Enforcement.HARD,
                QueryTask.Query.Occurance.MUST_OCCUR );
        additionalDisk = postDocument(DiskService.FACTORY_LINK, additionalDisk, documentLifeCycle);
        diskStateLinks.add(additionalDisk.documentSelfLink);

        return diskStateLinks;
    }

    private Constraint getConstraint(String tagName, Constraint.Condition.Enforcement enforcement, QueryTask.Query.Occurance
            occurance) {
        List<Constraint.Condition> conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag(tagName, "", enforcement, occurance));
        Constraint constraint = new Constraint();
        constraint.conditions = conditions;
        return constraint;
    }

    private StorageProfileService.StorageProfile createStorageProfile()
            throws Throwable {
        StorageProfileService.StorageProfile storageProfile = loadStorageProfile(getEndpointType());
        addTagLinks(storageProfile.storageItems);
        return storageProfile;
    }

    private StorageProfileService.StorageProfile loadStorageProfile(String endpointType) {
        URL r = getClass().getClassLoader().getResource(
                "test-" + endpointType.toLowerCase() + "-storage-profile.yaml");

        try (InputStream is = r.openStream()) {
            return YamlMapper.objectMapper().readValue(is, StorageProfileService.StorageProfile.class);
        } catch (Exception e) {
            logger.error("Failure in reading storage items due to : %s, reason: %s", r,
                    e.getMessage());
            return null;
        }
    }

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


}
