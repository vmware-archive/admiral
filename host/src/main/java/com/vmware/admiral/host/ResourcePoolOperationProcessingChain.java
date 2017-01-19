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

package com.vmware.admiral.host;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Predicate;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.PlacementZoneUtil;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Prevent deletion of {@link ResourcePoolState} if its in use by a {@link ComputeState}. Also
 * prevent REST calls that will result in having scheduler {@link ResourcePoolState} instances with
 * tags set
 */
public class ResourcePoolOperationProcessingChain extends OperationProcessingChain {

    public static final String PLACEMENT_ZONE_IN_USE_MESSAGE = "Placement zone is in use";

    public ResourcePoolOperationProcessingChain(FactoryService service) {
        super(service);
        this.add(new Predicate<Operation>() {
            @Override
            public boolean test(Operation op) {
                if (op.getAction() == Action.POST) {
                    return handlePostOrPut(op);
                }
                return true;
            }
        });
    }

    public ResourcePoolOperationProcessingChain(ResourcePoolService service) {
        super(service);
        this.add(new Predicate<Operation>() {

            @Override
            public boolean test(Operation op) {
                switch (op.getAction()) {
                case DELETE:
                    return handleDelete(service, op, this);
                case PUT:
                    return handlePostOrPut(op);
                case PATCH:
                    return handlePatch(service, op, this);
                default:
                    return true;
                }
            }

        });
    }

    private boolean handleDelete(ResourcePoolService service, Operation op,
            Predicate<Operation> invokingFilter) {
        ResourcePoolState currentState = service.getState(op);
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(currentState.query).addOption(QueryOption.COUNT).build();

        service.sendRequest(Operation.createPost(service, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        service.logWarning(Utils.toString(e));
                        op.fail(e);
                        return;
                    } else {
                        ServiceDocumentQueryResult result = o.getBody(QueryTask.class).results;
                        if (result.documentCount != 0) {
                            op.fail(new IllegalStateException(PLACEMENT_ZONE_IN_USE_MESSAGE));
                        } else {
                            resumeProcessingRequest(op, invokingFilter);
                        }
                    }
                }));

        return false;
    }

    private boolean handlePostOrPut(Operation op) {
        ResourcePoolState placementZone = op.getBody(ResourcePoolState.class);
        if (!PlacementZoneUtil.isSchedulerPlacementZone(placementZone)) {
            return true;
        }

        try {
            AssertUtil.assertEmpty(placementZone.tagLinks, "tagLinks");
            return true;
        } catch (IllegalArgumentException ex) {
            op.fail(ex);
            return false;
        }
    }

    private boolean handlePatch(ResourcePoolService service, Operation op,
            Predicate<Operation> invokingFilter) {
        ResourcePoolState patchState = op.getBody(ResourcePoolState.class);
        Operation.createGet(op.getUri()).setCompletion((o, e) -> {
            if (e != null) {
                op.fail(e);
            } else {
                ResourcePoolState currentState = o.getBody(ResourcePoolState.class);
                AssertUtil.assertNotNull(currentState, "currentState");

                ResourcePoolState unifiedState = new ResourcePoolState();
                unifiedState.customProperties = new HashMap<>();
                unifiedState.tagLinks = new HashSet<>();

                // Unify the custom properties of both states
                if (currentState.customProperties != null) {
                    unifiedState.customProperties.putAll(currentState.customProperties);
                }
                if (patchState.customProperties != null) {
                    unifiedState.customProperties.putAll(patchState.customProperties);
                }

                // Unify the tag links of both states
                if (currentState.tagLinks != null) {
                    unifiedState.tagLinks.addAll(currentState.tagLinks);
                }
                if (patchState.tagLinks != null) {
                    unifiedState.tagLinks.addAll(patchState.tagLinks);
                }

                // Now check whether the unified state is a scheduler
                if (PlacementZoneUtil.isSchedulerPlacementZone(unifiedState)) {
                    try {
                        // If there are no tags release the request
                        AssertUtil.assertEmpty(unifiedState.tagLinks, "tagLinks");
                        resumeProcessingRequest(op, invokingFilter);
                    } catch (IllegalArgumentException ex) {
                        op.fail(ex);
                    }
                } else {
                    resumeProcessingRequest(op, invokingFilter);
                }
            }
        }).sendWith(service);

        return false;
    }

}
