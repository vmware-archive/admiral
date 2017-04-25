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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.profile.InstanceTypeDescription;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService;
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
                        return createOsDiskState(profile, cd);
                    } else {
                        return enhanceDiskStates(profile, cd);
                    }
                });
    }

    /**
     * Create a Boot disk if there are no disks provided as input.
     */
    private DeferredResult<ComputeDescription> createOsDiskState(
            ProfileService.ProfileStateExpanded profile, ComputeDescription computeDesc) {
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
            InstanceTypeDescription instanceDesc = profile.computeProfile.instanceTypeMapping
                    .get(computeDesc.instanceType);
            long diskSizeMbFromProfile = instanceDesc != null ? instanceDesc.diskSizeMb : 0;
            // Default is 8 GB
            rootDisk.capacityMBytes = diskSizeMbFromProfile > 0 ? diskSizeMbFromProfile : (8 * 1024);

            Map<String, String> values = null;
            if (profile.storageProfile != null && profile.storageProfile.storageItems != null) {
                Optional<StorageProfileService.StorageItem> defaultStorageItem = profile
                        .storageProfile.storageItems.stream().filter(storageItem ->
                        storageItem.defaultItem).findFirst();
                if (defaultStorageItem.isPresent()) {
                    values = defaultStorageItem.get().diskProperties;
                }
            }

            if (values != null) {
                rootDisk.customProperties = new HashMap<>(values);
            }

            fillInBootConfigContent(computeDesc, rootDisk);

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
            ProfileService.ProfileStateExpanded profile,
            ComputeDescription cd) {
        DeferredResult<ComputeDescription> compDescResult = DeferredResult.allOf(
                cd.diskDescLinks.stream()
                        .map(link -> {
                            Operation getOp = Operation
                                    .createGet(this.host, link)
                                    .setReferer(this.referer);
                            return this.host.sendWithDeferredResult(getOp, DiskState.class);
                        })
                        .map(dr -> dr.thenCompose(diskState -> {
                            // TODO This code has to be changes as follows for storage profile:
                            // Every DiskState will have a set of requirements (name, hard/ soft)
                            // Storage Profile will be having a diskPropertyMapping which
                            // will be have a Map of constraint name mapped to set of attributes (key
                            // / value pairs.)
                            // Step 1: Form the DiskState requirements map the name of the requirement
                            // to the diskPropertyMapping key to fetch the map of key / value pairs
                            // what that requirement translates to.
                            // Step 2: Push this as a set of customProperties into the DiskState so
                            // that adapter can use this to create the disks.
                            if (diskState.type != null
                                    && diskState.type == DiskService.DiskType.HDD) {
                                fillInBootConfigContent(cd, diskState);
                            }
                            return this.host
                                    .sendWithDeferredResult(updateDiskDescriptionState(diskState),
                                            DiskState.class);
                        }))
                        .map(dr -> dr.thenApply(diskState -> diskState.documentSelfLink))
                        .collect(Collectors.toList()))
                .thenApply(links -> cd);

        return compDescResult;
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
    private void fillInBootConfigContent(ComputeDescription computeDesc, DiskState diskState) {
        String imageId = computeDesc.customProperties
                .get(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME);

        diskState.sourceImageReference = URI.create(imageId);

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