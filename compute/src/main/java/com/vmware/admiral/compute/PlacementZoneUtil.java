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

import com.vmware.admiral.compute.PlacementZoneConstants.PlacementZoneType;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;

public class PlacementZoneUtil {

    public static final String PLACEMENT_ZONE_TYPE_NOT_SUPPORTED_MESSAGE_FORMAT = "Placement zone type '%s' is not supported";

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
                throw new IllegalArgumentException(error, ex);
            }
        }
    }

    public static boolean isSchedulerPlacementZone(ResourcePoolState placementZone) {
        return getPlacementZoneType(placementZone) == PlacementZoneType.SCHEDULER;
    }

}
