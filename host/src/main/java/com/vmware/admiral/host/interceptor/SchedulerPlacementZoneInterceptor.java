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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.PlacementZoneConstants;
import com.vmware.admiral.compute.PlacementZoneUtil;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Prevents REST calls that will result in having scheduler {@link ResourcePoolState} instances with
 * tags set
 */
public class SchedulerPlacementZoneInterceptor {

    public static final String MULTIPLE_HOSTS_IN_PLACEMENT_ZONE_MESSAGE = "Cannot convert to "
            + "scheduler placement zone: placement zone contains multiple hosts";
    public static final String NON_SCHEDULER_HOST_IN_PLACEMENT_ZONE_MESSAGE = "Cannot convert to "
            + "Docker placement zone: placement zone contains a non-scheduler host";
    public static final String SCHEDULER_HOSTS_IN_PLACEMENT_ZONE_MESSAGE = "Cannot convert to "
            + "Docker placement zone: placement zone contains scheduler host(s)";

    public static final String MULTIPLE_HOSTS_IN_PLACEMENT_ZONE_MESSAGE_CODE = "host.placement-zone.contains.multiple.hosts";
    public static final String NON_SCHEDULER_HOST_IN_PLACEMENT_ZONE_MESSAGE_CODE = "host.placement-zone.contains.non-scheduler.host";
    public static final String SCHEDULER_HOSTS_IN_PLACEMENT_ZONE_MESSAGE_CODE = "host.placement-zone.contains.scheduler.hosts";

    public static void register(OperationInterceptorRegistry registry) {
        registry.addFactoryServiceInterceptor(
                ResourcePoolService.class, Action.POST, SchedulerPlacementZoneInterceptor::handlePostOrPut);

        registry.addServiceInterceptor(
                ResourcePoolService.class, Action.PUT, SchedulerPlacementZoneInterceptor::handlePostOrPut);
        registry.addServiceInterceptor(
                ResourcePoolService.class, Action.PATCH, SchedulerPlacementZoneInterceptor::handlePatch);
    }

    private static boolean isComputeZone(ResourcePoolState currentState) {
        return currentState.customProperties != null && ResourceType.COMPUTE_TYPE.getName()
                .equalsIgnoreCase(currentState.customProperties
                        .get(PlacementZoneConstants.RESOURCE_TYPE_CUSTOM_PROP_NAME));
    }

    public static DeferredResult<Void> handlePostOrPut(Service service, Operation op) {
        ResourcePoolState placementZone = op.getBody(ResourcePoolState.class);
        if (isComputeZone(placementZone)) {
            return DeferredResult.completed(null);
        }

        if (PlacementZoneUtil.isSchedulerPlacementZone(placementZone)) {
            try {
                AssertUtil.assertEmpty(placementZone.tagLinks, "tagLinks");
            } catch (LocalizableValidationException ex) {
                return DeferredResult.failed(ex);
            }
            return verifyZoneContainsSingleSchedulerOrNoHost(placementZone.documentSelfLink, op,
                    service);
        } else {
            return verifyZoneContainsNoSchedulers(placementZone.documentSelfLink, op, service);
        }
    }

    public static DeferredResult<Void> handlePatch(Service service, Operation op) {
        ResourcePoolState patchState = op.getBody(ResourcePoolState.class);
        Operation getOp = Operation.createGet(op.getUri()).setReferer(service.getUri());
        return service.getHost().sendWithDeferredResult(getOp, ResourcePoolState.class)
                .thenCompose(currentState -> {
                    AssertUtil.assertNotNull(currentState, "currentState");

                    if (isComputeZone(currentState)) {
                        return DeferredResult.completed(null);
                    }

                    ResourcePoolState rp = new ResourcePoolState();
                    rp.customProperties = PropertyUtils.mergeCustomProperties(
                            currentState.customProperties, patchState.customProperties);

                    // Now check whether the unified state is a scheduler
                    if (PlacementZoneUtil.isSchedulerPlacementZone(rp)) {
                        try {
                            // shcedulers can have no tags
                            AssertUtil.assertEmpty(currentState.tagLinks, "tagLinks");
                            AssertUtil.assertEmpty(patchState.tagLinks, "tagLinks");
                        } catch (LocalizableValidationException ex) {
                            return DeferredResult.failed(ex);
                        }
                        // schedulers can have a single scheduler host at most
                        return verifyZoneContainsSingleSchedulerOrNoHost(
                                currentState.documentSelfLink, op, service);
                    } else {
                        // docker placement zones can have only docker hosts
                        return verifyZoneContainsNoSchedulers(currentState.documentSelfLink, op,
                                service);
                    }
                }).thenAccept(ignore -> {
                });
    }

    private static DeferredResult<Void> verifyZoneContainsSingleSchedulerOrNoHost(
            String resourcePoolLink, Operation op, Service service) {
        if (resourcePoolLink == null) {
            // there is no placement zone to verify
            return null;
        }

        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, resourcePoolLink)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME, "true")
                .build();

        QueryUtils.QueryByPages<ComputeState> queryHelper = new QueryUtils.QueryByPages<>(
                service.getHost(), query, ComputeState.class, null);
        return queryHelper.collectDocuments(Collectors.toList()).thenAccept(computes -> {
            if (computes.size() > 1) {
                throw new LocalizableValidationException(
                        MULTIPLE_HOSTS_IN_PLACEMENT_ZONE_MESSAGE,
                        MULTIPLE_HOSTS_IN_PLACEMENT_ZONE_MESSAGE_CODE);
            }

            if (!computes.isEmpty()
                    && !ContainerHostUtil.isTreatedLikeSchedulerHost(computes.get(0))) {
                throw new LocalizableValidationException(
                        NON_SCHEDULER_HOST_IN_PLACEMENT_ZONE_MESSAGE,
                        NON_SCHEDULER_HOST_IN_PLACEMENT_ZONE_MESSAGE_CODE);
            }
        });
    }

    private static DeferredResult<Void> verifyZoneContainsNoSchedulers(String resourcePoolLink,
            Operation op, Service service) {
        if (resourcePoolLink == null) {
            // there is no placement zone to verify
            return null;
        }

        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, resourcePoolLink)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME, "true")
                .addFieldClause(
                        QuerySpecification.buildCompositeFieldName(
                                ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                                ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME),
                        "*", MatchType.WILDCARD)
                .build();

        QueryUtils.QueryByPages<ComputeState> queryHelper = new QueryUtils.QueryByPages<>(
                service.getHost(), query, ComputeState.class, null);
        AtomicBoolean schedulerFound = new AtomicBoolean(false);
        return queryHelper.queryDocuments(compute -> {
            if (ContainerHostUtil.isTreatedLikeSchedulerHost(compute)) {
                schedulerFound.set(true);
            }
        }).thenAccept(ignore -> {
            if (schedulerFound.get()) {
                throw new LocalizableValidationException(
                        SCHEDULER_HOSTS_IN_PLACEMENT_ZONE_MESSAGE,
                        SCHEDULER_HOSTS_IN_PLACEMENT_ZONE_MESSAGE_CODE);
            }
        });
    }
}
