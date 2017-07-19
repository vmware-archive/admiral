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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.network.AddressSpace;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.implementation.SubnetInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;

import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;

public class AzureComputeProvisionWithBootAndAdditionalDiskIT extends AzureComputeProvisionIT {

    private static final long OS_DISK_SIZE = 32 * 1024;
    private static final long ADDITIONAL_DISK_SIZE = 12 * 1024;

    private static final Map<String, String> tagMap = new HashMap<>();
    public static final String OSDISK_NAME = "OSDisk";
    public static final String DATA_DISK_NAME = "DataDisk";
    public static final String RESOURCE_GROUP_FOR_SUBNET = "ittestdefaultrgforsubnet";
    public static final String VIRTUAL_NETWORK = "defaultvNetForIT";
    public static final String SUBNET = "itdefaultsubnet";

    private static AzureSdkClients azureSdkClients;
    public AuthCredentialsService.AuthCredentialsServiceState auth;

    @Override protected String getResourceDescriptionLink() throws Exception {
        return getResourceDescriptionLink(true, null);
    }

    @Override
    protected long getRootDiskSize() {
        return OS_DISK_SIZE;
    }

    @Override
    public void setUp() throws Throwable {

        setupAuthCredentials();

        azureSdkClients = new AzureSdkClients(Executors.newSingleThreadExecutor(), this.auth);

        createResourceGroup(RESOURCE_GROUP_FOR_SUBNET);

        createVirtualNetworkAndSubnet(RESOURCE_GROUP_FOR_SUBNET, VIRTUAL_NETWORK, SUBNET);

        super.setUp();
    }

    private void setupAuthCredentials() throws Exception {

        this.auth = new AuthCredentialsService.AuthCredentialsServiceState();
        this.auth.userEmail = getTestRequiredProp(VM_ADMIN_USERNAME);
        this.auth.privateKey = getTestRequiredProp(VM_ADMIN_PASSWORD);
        this.auth.documentSelfLink = UUID.randomUUID().toString();
        this.auth.privateKeyId = getTestRequiredProp(ACCESS_KEY_PROP);
        this.auth.privateKey = getTestRequiredProp(ACCESS_SECRET_PROP);
        this.auth.userLink = getTestRequiredProp(SUBSCRIPTION_PROP);
        this.auth.customProperties = new HashMap<>();
        this.auth.customProperties.put("azureTenantId", getTestRequiredProp(TENANT_ID_PROP));

        this.auth = postDocument(AuthCredentialsService.FACTORY_LINK, this.auth, documentLifeCycle);
    }

    @Override
    protected void extendComputeDescription(ComputeDescriptionService.ComputeDescription
            computeDescription) throws Exception {
        computeDescription.authCredentialsLink = auth.documentSelfLink;
        computeDescription.name = "mcp";
    }

    @Override
    protected void doSetUp() throws Throwable {

        createProfile(loadComputeProfile(getEndpointType()), createNetworkProfile(
                SUBNET, null, null), createStorageProfile());
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

    private void createVirtualNetworkAndSubnet(String resourceGroupName, String virtualNetworkName,
            String subnetName) {

        NetworkManagementClientImpl client =  azureSdkClients
                .getNetworkManagementClientImpl();

        VirtualNetworkInner virtualNetworkRequest = new VirtualNetworkInner();
        virtualNetworkRequest.withLocation(getTestProp(REGION_ID_PROP, "westus"));

        AddressSpace addressSpace = new AddressSpace();
        ArrayList<String> prefixes = new ArrayList<>();
        prefixes.add("10.10.0.0/16");
        addressSpace.withAddressPrefixes(prefixes);
        virtualNetworkRequest.withAddressSpace(addressSpace);

        client.virtualNetworks().createOrUpdate(resourceGroupName, virtualNetworkName,
                virtualNetworkRequest);

        SubnetInner subnetRequest = new SubnetInner()
                .withAddressPrefix("10.10.10.0/28")
                .withName(subnetName);

        client.subnets().createOrUpdate(resourceGroupName, virtualNetworkName, subnetName ,subnetRequest);
    }

    private void createResourceGroup(String resourceGroupName)
            throws CloudException, IOException {
        ResourceGroupInner resourceGroupToCreate = new ResourceGroupInner()
                .withName(resourceGroupName)
                .withLocation(getTestProp(REGION_ID_PROP, "westus"));

        azureSdkClients.getResourceManagementClientImpl().resourceGroups().createOrUpdate(resourceGroupName, resourceGroupToCreate);
    }


}
