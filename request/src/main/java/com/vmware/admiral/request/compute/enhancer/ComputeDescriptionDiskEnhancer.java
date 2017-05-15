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
import com.vmware.admiral.compute.profile.InstanceTypeDescription;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageItem;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageItemExpanded;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfileExpanded;
import com.vmware.admiral.request.compute.StorageProfileUtils;
import com.vmware.admiral.request.compute.TagConstraintUtils;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
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
                diskName = "Default disk";
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

            StorageItem storageItem = findDefaultStorageItem(context.profile);
            if (storageItem != null && storageItem.diskProperties != null) {
                rootDisk.customProperties = new HashMap<>(storageItem.diskProperties);
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
                    if (diskState.type != null
                            && diskState.type == DiskService.DiskType.HDD) {
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
                        diskState.storageDescriptionLink = storageItem.storageDescriptionLink;
                        if (storageItem.diskProperties != null) {
                            diskState.customProperties = new HashMap<>(
                                    storageItem.diskProperties);
                        }
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
     * Find the storage item that is matching the given set of constraints
     */
    private StorageItemExpanded findStorageItem(ProfileService.ProfileStateExpanded profile,
            DiskState diskState) {
        // if no constraints return default
        if (checkIfNoConstraintAvailable(diskState.constraint)) {
            return findDefaultStorageItem(profile);
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
                si -> storageItemTagLinks(si), (i1, i2) -> {
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
                }).filter(si -> (si.storageDescription == null || !diskState.encrypted) ?
                true : diskState.encrypted == si.storageDescription.supportsEncryption)
                .findFirst().orElse(null);
    }

    private Stream<StorageItemExpanded> storageItemsStream(StorageProfileExpanded storageProfile) {
        return storageProfile != null && storageProfile.storageItemsExpanded != null
                ? storageProfile.storageItemsExpanded.stream() : Stream.of(new StorageItemExpanded());
    }

    /**
     * Merge tagLinks if any from the storage description to the storage item tagLinks.
     */
    private Set<String> storageItemTagLinks(StorageItemExpanded siExpanded) {
        Set<String> tagLinks = siExpanded.tagLinks != null ? siExpanded.tagLinks : new HashSet<>();
        if (siExpanded.storageDescription != null
                && siExpanded.storageDescription.tagLinks != null) {
            tagLinks.addAll(siExpanded.storageDescription.tagLinks);
        }
        return tagLinks;
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