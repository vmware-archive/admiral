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

package com.vmware.admiral.host.interceptor;

import com.vmware.admiral.compute.PlacementZoneConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;

/**
 * Validates that compute placement zones always have an endpoint link set.
 */
public class ComputePlacementZoneInterceptor {
    public static final String ENDPOINT_REQUIRED_FOR_PLACEMENT_ZONE_MESSAGE =
            "Endpoint is required for the placement zone";
    public static final String ENDPOINT_REQUIRED_FOR_PLACEMENT_ZONE_MESSAGE_CODE =
            "host.resource-pool.endpoint.required";

    public static void register(OperationInterceptorRegistry registry) {
        registry.addFactoryServiceInterceptor(ResourcePoolService.class,
                Action.POST, ComputePlacementZoneInterceptor::handleChange);
        registry.addServiceInterceptor(ResourcePoolService.class,
                Action.PATCH, ComputePlacementZoneInterceptor::handleChange);
        registry.addServiceInterceptor(ResourcePoolService.class,
                Action.PUT, ComputePlacementZoneInterceptor::handleChange);
    }

    public static DeferredResult<Void> handleChange(Service service, Operation op) {
        if (Action.POST.equals(op.getAction())) {
            // check if this is a create op (ServiceHost.isServiceCreate depends on a pragma
            // that is not yet set, so we cannot use it here)
            // also, for a POST action, a nested completion cannot do the job because it cannot
            // cause the op to fail
            if (!op.isSynchronize()) {
                validate(op.getBody(ResourcePoolState.class));
            }
        } else {
            // nest the validation so that it happens after the service handler
            op.nestCompletion(o -> {
                validate(((ResourcePoolService)service).getState(op));
                op.complete();
            });
        }
        return null;
    }

    private static boolean isComputeZone(ResourcePoolState currentState) {
        return currentState.customProperties != null && ResourceType.COMPUTE_TYPE.getName()
                .equalsIgnoreCase(currentState.customProperties
                        .get(PlacementZoneConstants.RESOURCE_TYPE_CUSTOM_PROP_NAME));
    }

    private static String getEndpointLinkProperty(ResourcePoolState currentState) {
        return currentState.customProperties != null
                ? currentState.customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME)
                : null;
    }

    private static void validate(ResourcePoolState state) {
        if (isComputeZone(state) && getEndpointLinkProperty(state) == null) {
            throw new LocalizableValidationException(
                    ENDPOINT_REQUIRED_FOR_PLACEMENT_ZONE_MESSAGE,
                    ENDPOINT_REQUIRED_FOR_PLACEMENT_ZONE_MESSAGE_CODE);
        }
    }
}
