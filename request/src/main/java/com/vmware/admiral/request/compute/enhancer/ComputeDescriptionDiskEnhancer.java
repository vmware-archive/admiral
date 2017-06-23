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

package com.vmware.admiral.request.compute.enhancer;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.VsphereConstants;
import com.vmware.admiral.compute.profile.InstanceTypeDescription;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageItemExpanded;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfileExpanded;
import com.vmware.admiral.request.compute.StorageProfileUtils;
import com.vmware.admiral.request.compute.TagConstraintUtils;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

/**
 * Disk enhancer will inspect the disk description links, fetches the details of the disk, enhance
 * them by adding any additional data that is available in the profile and then creates new
 * DiskState which will be added as list of custom properties to the compute description.
 */
public class ComputeDescriptionDiskEnhancer extends ComputeDescriptionEnhancer {

    public static final String FIELD_NAME_CUSTOM_PROP_DISK_NAME = "__diskName";
    public static final String FIELD_NAME_CUSTOM_PROP_SUPPORTS_ENCRYPTION = "__supportsEncryption";

    private ServiceHost host;
    private URI referer;

    public ComputeDescriptionDiskEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeDescription> enhance(EnhanceContext context,
            ComputeDescription cd) {
        return getProfileState(host, referer, context)
                .thenCompose(profile -> {
                    context.profile = profile;

                    // Iterate over all the disk desc links to get the disk state, if nothing is
                    // available then create a default disk
                    if (cd.diskDescLinks == null || cd.diskDescLinks.isEmpty()) {
                        return createOsDiskState(context, cd);
                    } else {
                        return enhanceDiskStates(context, cd);
                    }
                });
    }

    /**
     * Create a Boot disk if there are no disks provided as input.
     */
    private DeferredResult<ComputeDescription> createOsDiskState(
            EnhanceContext context, ComputeDescription computeDesc) {
        try {
            DiskState rootDisk = new DiskState();
            rootDisk.id = UUID.randomUUID().toString();
            rootDisk.documentSelfLink = rootDisk.id;
            String diskName = computeDesc.customProperties.get(FIELD_NAME_CUSTOM_PROP_DISK_NAME);
            if (diskName == null) {
                diskName = "boot-disk";
            }
            rootDisk.name = diskName;
            rootDisk.type = DiskService.DiskType.HDD;
            rootDisk.bootOrder = 1;
            InstanceTypeDescription instanceDesc = context.profile.computeProfile.instanceTypeMapping
                    .get(computeDesc.instanceType);
            long diskSizeMbFromProfile = instanceDesc != null ? instanceDesc.diskSizeMb : 0;
            // Default is 8 GB
            rootDisk.capacityMBytes = diskSizeMbFromProfile > 0 ? diskSizeMbFromProfile
                    : (8 * 1024);

            StorageItemExpanded storageItem = findDefaultStorageItem(context.profile);
            if (storageItem != null) {
                updateDiskStateWithStorageItemProperties(rootDisk, storageItem, computeDesc);
            }

            fillInBootConfigContent(context, computeDesc, rootDisk);
            DeferredResult<ComputeDescription> result = this.host
                    .sendWithDeferredResult(createDiskDescriptionState(rootDisk),
                            DiskState.class)
                    .thenApply(diskState -> {
                        this.host.log(Level.INFO, "Resource created: %s",
                                diskState.documentSelfLink);
                        computeDesc.diskDescLinks = Arrays.asList(diskState.documentSelfLink);
                        return computeDesc;
                    });
            return result;
        } catch (Throwable t) {
            return DeferredResult.failed(t);
        }
    }

    /**
     * Enhances the disk state with the properties that are available in the profile.
     */
    private DeferredResult<ComputeDescription> enhanceDiskStates(
            EnhanceContext context, ComputeDescription cd) {
        List<DeferredResult<Pair<DiskState, Throwable>>> diskStateResults = cd.diskDescLinks
                .stream()
                .map(link -> {
                    Operation getOp = Operation
                            .createGet(this.host, link)
                            .setReferer(this.referer);
                    return this.host.sendWithDeferredResult(getOp, DiskState.class);
                })
                .map(dr -> dr.thenCompose(diskState -> {
                    if (diskState.type != null && diskState.type == DiskService.DiskType.HDD
                            && diskState.bootOrder == 1) {
                        fillInBootConfigContent(context, cd, diskState);
                    }
                    // Match the constraints from Disk to the profile to extract the
                    // provider specific properties.
                    StorageItemExpanded storageItem = findStorageItem(context.profile, diskState);
                    if (storageItem == null) {
                        return DeferredResult
                                .failed(new IllegalStateException(String.format(
                                        "No matching storage defined in profile: %s, for requested disk: %s",
                                        context.profile.documentSelfLink, diskState.name)));
                    } else {
                        updateDiskStateWithStorageItemProperties(diskState, storageItem, cd);
                    }
                    return this.host
                            .sendWithDeferredResult(updateDiskDescriptionState(diskState),
                                    DiskState.class);
                }))
                .map(dr -> dr
                        .thenApply(ds -> Pair.of(ds, (Throwable) null))
                        .exceptionally(t -> Pair.of(null, t)))
                .collect(Collectors.toList());
        DeferredResult<ComputeDescription> result = DeferredResult.allOf(diskStateResults)
                .thenCompose(pairs -> {
                    // Collect error messages if any for all the disks.
                    StringJoiner stringJoiner = new StringJoiner(",");
                    pairs.stream().filter(p -> p.left == null).forEach(p -> stringJoiner.add(p
                            .right.getMessage()));
                    if (stringJoiner.length() > 0) {
                        return DeferredResult.failed(new Throwable(stringJoiner
                                .toString()));
                    } else {
                        return DeferredResult.completed(cd);
                    }
                });
        return result;
    }

    /**
     * Update disk state with the chosen storage item properties.
     */
    private void updateDiskStateWithStorageItemProperties(DiskState diskState, StorageItemExpanded storageItem,
            ComputeDescription cd) {
        diskState.storageDescriptionLink = storageItem.storageDescriptionLink;
        if (storageItem.diskProperties != null) {
            diskState.customProperties = new HashMap<>(storageItem.diskProperties);
        }

        //Handling for vSphere specific BP, updating the storage provisioning type attr
        if (VsphereConstants.COMPUTE_VSPHERE_TYPE.equals(cd.customProperties.get(
                VsphereConstants.COMPUTE_COMPONENT_TYPE_ID))) {

            if (diskState.customProperties == null) {
                diskState.customProperties = new HashMap<>();
            }

            String diskProvisionThin = cd.customProperties.get(
                    VsphereConstants.VSPHERE_CUSTOMPROP_STORAGE_PROV_THIN_TYPE);

            boolean thin = false;
            if (diskProvisionThin != null && !"".equals(diskProvisionThin)) {
                thin = Boolean.valueOf(diskProvisionThin).booleanValue();
            }

            if (thin) {
                //Set thin provisioning..
                diskState.customProperties.put(VsphereConstants.VSPHERE_DISK_PROVISION_TYPE,
                        VsphereConstants.VSPHERE_DISK_PROVISION_THIN);
            } else {
                String eagerZeroedThick = cd.customProperties.get(
                        VsphereConstants.VSPHERE_CUSTOMPROP_STORAGE_PROV_THICK_EAGER_ZERO_TYPE);

                if (eagerZeroedThick != null && !"".equals(eagerZeroedThick)) {
                    //set thick zeroed
                    diskState.customProperties.put(VsphereConstants.VSPHERE_DISK_PROVISION_TYPE,
                            VsphereConstants.VSPHERE_DISK_PROVISION_EAGER_ZEROED_THICK);
                } else {
                    String thick = cd.customProperties.get(
                            VsphereConstants.VSPHERE_CUSTOMPROP_STORAGE_PROV_THICK_TYPE);

                    if (thick != null && !"".equals(thick)) {
                        //set thick
                        diskState.customProperties.put(VsphereConstants.VSPHERE_DISK_PROVISION_TYPE,
                                VsphereConstants.VSPHERE_DISK_PROVISION_THICK);
                    }
                }
            }
        } //vSphere type ends here.

        if (storageItem.resourceGroupLink != null) {
            diskState.groupLinks = new HashSet<>(1);
            diskState.groupLinks.add(storageItem.resourceGroupLink);
        }
    }

    /**
     * Find the storage item that is matching the given set of constraints
     */
    private StorageItemExpanded findStorageItem(ProfileService.ProfileStateExpanded profile,
            DiskState diskState) {
        // if no constraints return default
        if (checkIfNoConstraintAvailable(diskState.constraint)) {
            StorageItemExpanded defItem = findDefaultStorageItem(profile);
            if (diskState.encrypted == null || !diskState.encrypted) {
                return defItem;
            } else {
                // Filter stream based on encryption field
                return storageItemsStream(profile.storageProfile)
                        .filter(si -> storageItemEncryptionFilter(si)).findFirst().orElse(null);
            }
        }
        // Step 1: Find if there are hard constraints. Then all of them will be available in one
        // of the storage item in the profile, as placement would have done this filtering to chose
        // this profile.
        // Step 2: If all are soft, there should be an entry which matches all or at-least some.
        // If all are matched then that storage item is chosen, if not then the max matching item
        // will be chosen.
        // Step 3: If all are soft and nothing matches then default properties are picked.
        return TagConstraintUtils.filterByConstraints(
                StorageProfileUtils.extractStorageTagConditions(diskState.constraint, profile
                        .tenantLinks),
                storageItemsStream(profile.storageProfile),
                si -> collectStorageItemTagLinks(si), (i1, i2) -> {
                    if (i1.defaultItem && i2.defaultItem) {
                        return 0;
                    } else {
                        if (i1.defaultItem) {
                            return -1;
                        } else if (i2.defaultItem) {
                            return 1;
                        } else {
                            return 0;
                        }
                    }
                }).filter(si -> (
                // Disk is not requesting for encryption and is null
                diskState.encrypted == null ||
                // Disk is not requesting for encryption and it is marked false
                !diskState.encrypted ||
                        // Explore Storage Item for encryption support.
                        storageItemEncryptionFilter(si)))
                .findFirst().orElse(null);
    }

    /**
     * Find whether storage Item supports encryption
     *
     * 1. Storage Item itself supports encryption.
     * 2. Storage Description associated with storage item supports encryption or if there is no
     * explicit resource group associated with storage item, then query for resource groups to which
     * this storage description belongs to, to see whether any of it supports encryption.
     * 3. Resource group to which this storage item is associated supports encryption.
     */
    private boolean storageItemEncryptionFilter(StorageItemExpanded si) {
        // Storage Item itself supports encryption
        return (si.supportsEncryption != null && si.supportsEncryption) ||
                // Storage description related to storage item supports encryption
                ((si.storageDescription != null
                        && si.storageDescription.supportsEncryption != null) ?
                        // Storage Description related to storage item supports encryption
                        storageDescriptionEncryptionFilter(si) :
                        // Resource group state related to storage item supports encryption
                        resourceGroupEncryptionFilter(si.resourceGroupState));
    }

    private Stream<StorageItemExpanded> storageItemsStream(StorageProfileExpanded storageProfile) {
        return storageProfile != null && storageProfile.storageItemsExpanded != null
                ? storageProfile.storageItemsExpanded.stream() : Stream.of(new StorageItemExpanded());
    }

    /**
     * Storage item tag Links + Storage Description tag links + Resource group state tag links.
     */
    private Set<String> collectStorageItemTagLinks(StorageItemExpanded siExpanded) {
        Set<String> tagLinks = siExpanded.tagLinks != null ? new HashSet<>(siExpanded.tagLinks) :
                new HashSet<>();
        // Storage description tag links
        if (siExpanded.storageDescription != null
                && siExpanded.storageDescription.tagLinks != null) {
            tagLinks.addAll(siExpanded.storageDescription.tagLinks);
        }
        // Resource group state tag links
        if (siExpanded.resourceGroupState != null
                && siExpanded.resourceGroupState.tagLinks != null) {
            tagLinks.addAll(siExpanded.resourceGroupState.tagLinks);
        }
        return tagLinks;
    }

    /**
     * Filter storage item based on storage description supports encryption field it is not null
     * and true. If this is false, then it will iterate over all the resource group states that
     * are associated with this SD, if any one of them returns true, then this storage item will
     * be supporting encryption. Otherwise it will be false, which means no encryption support.
     */
    private boolean storageDescriptionEncryptionFilter(StorageItemExpanded si) {
        if (si.storageDescription.supportsEncryption) {
            return true;
        } else if (si.resourceGroupState == null && si.storageDescription.resourceGroupStates != null) {
            // If there is a chosen resource group state already, then that should be honored
            // instead of picking something from the list of resource group states that this
            // storage description is related to.
            return si.storageDescription.resourceGroupStates.stream().filter(rg ->
                    resourceGroupEncryptionFilter(rg)).findFirst().isPresent();
        }
        return false;
    }

    /**
     * Filter storage item based on resource group state's support of encryption if it is
     * non-null. If the resource group state is null, then rely on the default storage item's
     * field.
     */
    private boolean resourceGroupEncryptionFilter(ResourceGroupState resourceGroupState) {
        if (resourceGroupState != null && resourceGroupState.customProperties != null) {
            String encryption = resourceGroupState.customProperties
                    .get(FIELD_NAME_CUSTOM_PROP_SUPPORTS_ENCRYPTION);
            if (encryption != null) {
                return Boolean.valueOf(encryption);
            }
        }
        return false;
    }

    /**
     * Check if constraint is present or not.
     */
    private boolean checkIfNoConstraintAvailable(Constraint constraint) {
        return constraint == null || constraint.conditions == null || constraint.conditions
                .isEmpty();
    }

    /**
     * Check if there are storage items present or not.
     */
    private boolean checkIfNoStorageItemsAvailable(ProfileService.ProfileStateExpanded profile) {
        return profile.storageProfile == null
                || profile.storageProfile.storageItemsExpanded == null
                || profile.storageProfile.storageItemsExpanded.isEmpty();
    }

    /**
     * Get default storage item if present, else null.
     */
    private StorageItemExpanded findDefaultStorageItem(ProfileService.ProfileStateExpanded profile) {
        if (checkIfNoStorageItemsAvailable(profile)) {
            return new StorageItemExpanded();
        }

        return profile.storageProfile.storageItemsExpanded.stream()
                .filter(si -> si.defaultItem)
                .findFirst()
                .orElse(new StorageItemExpanded());
    }

    /**
     * Construct create disk operation
     */
    private Operation createDiskDescriptionState(DiskState diskState) {
        return Operation.createPost(UriUtils.buildUri(this.host, DiskService.FACTORY_LINK))
                .setReferer(this.referer)
                .setBody(diskState);
    }

    /**
     * Construct update (put) disk operation
     */
    private Operation updateDiskDescriptionState(DiskState diskState) {
        return Operation.createPut(UriUtils.buildUri(this.host, diskState.documentSelfLink))
                .setReferer(this.referer)
                .setBody(diskState);
    }

    /**
     * If there is boot config content in custom properties then fill it into the boot disk.
     */
    private void fillInBootConfigContent(EnhanceContext context, ComputeDescription computeDesc,
            DiskState diskState) {

        if (context.resolvedImageLink != null) {
            diskState.imageLink = context.resolvedImageLink;
        } else if (context.resolvedImage != null) {
            diskState.sourceImageReference = URI.create(context.resolvedImage);
        }

        if (diskState.bootConfig == null) {
            diskState.bootConfig = new DiskState.BootConfig();
            diskState.bootConfig.label = "cidata";
        }

        if (diskState.bootConfig.files == null) {
            String content = computeDesc.customProperties
                    .get(ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME);
            DiskState.BootConfig.FileEntry file = new DiskState.BootConfig.FileEntry();
            file.path = "user-data";
            file.contents = content;
            diskState.bootConfig.files = new DiskState.BootConfig.FileEntry[] { file };
        }
    }
}