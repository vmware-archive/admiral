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

package com.vmware.admiral.compute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.PlacementZoneConstants.PlacementZoneType;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;

public class PlacementZoneUtil {

    public static final String PLACEMENT_ZONE_TYPE_NOT_SUPPORTED_MESSAGE_FORMAT = "Placement zone type '%s' is not supported";
    public static final String PLACEMENT_ZONE_TYPE_NOT_SUPPORTED_MESSAGE_CODE = "compute.placement-zone.type.not.supported";

    public static PlacementZoneType getPlacementZoneType(ResourcePoolState placementZone) {
        if (placementZone.customProperties == null) {
            return PlacementZoneType.getDefaultPlacementZoneType();
        }

        String placementZoneType = placementZone.customProperties
                .get(PlacementZoneConstants.PLACEMENT_ZONE_TYPE_CUSTOM_PROP_NAME);
        if (placementZoneType == null) {
            return PlacementZoneType.getDefaultPlacementZoneType();
        } else {
            try {
                return PlacementZoneType.valueOf(placementZoneType);
            } catch (IllegalArgumentException ex) {
                String error = String.format(PLACEMENT_ZONE_TYPE_NOT_SUPPORTED_MESSAGE_FORMAT,
                        placementZone);
                throw new LocalizableValidationException(ex, error,
                        PLACEMENT_ZONE_TYPE_NOT_SUPPORTED_MESSAGE_CODE, placementZone);
            }
        }
    }

    public static boolean isSchedulerPlacementZone(ResourcePoolState placementZone) {
        return getPlacementZoneType(placementZone) == PlacementZoneType.SCHEDULER;
    }

    public static String buildPlacementZoneDefaultName(ContainerHostType hostType, String hostAddress) {
        StringBuilder sb = new StringBuilder(hostType.toString().toLowerCase());
        sb.append(":");
        sb.append(hostAddress.replaceAll("^https?:\\/\\/", ""));
        return sb.toString();
    }

    public static DeferredResult<ResourcePoolState> generatePlacementZone(ServiceHost serviceHost,
            ComputeState hostState) {
        try {
            AssertUtil.assertNotNull(hostState, "hostState");
        } catch (LocalizableValidationException ex) {
            serviceHost.log(Level.WARNING, "Failed to generate placement zone for host: %s",
                    Utils.toString(ex));
            return DeferredResult.failed(ex);
        }

        ContainerHostType hostType = ContainerHostUtil.getDeclaredContainerHostType(hostState);
        ResourcePoolState resourcePool = new ResourcePoolState();
        resourcePool.name = buildPlacementZoneDefaultName(hostType, hostState.address);
        if (hostType != ContainerHostType.DOCKER) {
            // mark the placement zone as a scheduler
            resourcePool.customProperties = new HashMap<>();
            resourcePool.customProperties.put(
                    PlacementZoneConstants.PLACEMENT_ZONE_TYPE_CUSTOM_PROP_NAME,
                    PlacementZoneType.SCHEDULER.toString());
        }
        if (hostState.tenantLinks != null) {
            resourcePool.tenantLinks = new ArrayList<>(hostState.tenantLinks);
        }

        // create the placement zone
        ElasticPlacementZoneConfigurationState placementZone = new ElasticPlacementZoneConfigurationState();
        placementZone.resourcePoolState = resourcePool;
        return serviceHost.sendWithDeferredResult(
                Operation
                        .createPost(serviceHost, ElasticPlacementZoneConfigurationService.SELF_LINK)
                        .setReferer(serviceHost.getUri())
                        .setBody(placementZone),
                ElasticPlacementZoneConfigurationState.class)
                .thenApply((epzState) -> epzState.resourcePoolState);
    }

    public static DeferredResult<GroupResourcePlacementState> generatePlacement(
            ServiceHost serviceHost, ResourcePoolState placementZone) {

        try {
            AssertUtil.assertNotNull(placementZone, "vchplacementZone");
        } catch (LocalizableValidationException ex) {
            serviceHost.log(Level.WARNING, "Failed to generate placement for placement zone: %s",
                    Utils.toString(ex));
            return DeferredResult.failed(ex);
        }

        GroupResourcePlacementState placement = new GroupResourcePlacementState();
        placement.name = placementZone.name;
        placement.resourcePoolLink = placementZone.documentSelfLink;
        placement.resourceType = ResourceType.CONTAINER_TYPE.getName();
        placement.priority = GroupResourcePlacementService.DEFAULT_PLACEMENT_PRIORITY;
        placement.customProperties = new HashMap<>();
        placement.customProperties.put(
                GroupResourcePlacementState.AUTOGENERATED_PLACEMENT_PROP_NAME,
                Boolean.toString(true));
        if (placementZone.tenantLinks != null) {
            placement.tenantLinks = new ArrayList<>(placementZone.tenantLinks);
        }

        return serviceHost.sendWithDeferredResult(
                Operation.createPost(serviceHost, GroupResourcePlacementService.FACTORY_LINK)
                        .setReferer(serviceHost.getUri())
                        .setBody(placement),
                GroupResourcePlacementState.class);
    }

}
