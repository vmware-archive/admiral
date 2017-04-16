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

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Prevent deletion of {@link ResourcePoolState} if its in use by a {@link ComputeState}.
 */
public class InUsePlacementZoneInterceptor {
    public static final String PLACEMENT_ZONE_IN_USE_MESSAGE = "Placement zone is in use";
    public static final String PLACEMENT_ZONE_IN_USE_MESSAGE_CODE = "host.resource-pool.in.use";

    public static void register(OperationInterceptorRegistry registry) {
        registry.addServiceInterceptor(
                ResourcePoolService.class, Action.DELETE, InUsePlacementZoneInterceptor::handleDelete);
    }

    public static DeferredResult<Void> handleDelete(Service service, Operation op) {
        ResourcePoolState currentState = service.getState(op);

        QueryTask queryTask;
        if (currentState.query != null) {
            queryTask = QueryTask.Builder.createDirectTask().setQuery(currentState.query).build();
        } else if (currentState.documentSelfLink != null) {
            queryTask = QueryUtil.buildPropertyQuery(ComputeState.class,
                    ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, currentState.documentSelfLink);
        } else {
            return null;
        }

        QueryUtil.addCountOption(queryTask);

        return QueryUtils.startQueryTask(service, queryTask)
                .thenAccept(qt -> {
                    ServiceDocumentQueryResult result = qt.results;
                    if (result.documentCount != 0) {
                        throw new LocalizableValidationException(
                                PLACEMENT_ZONE_IN_USE_MESSAGE,
                                PLACEMENT_ZONE_IN_USE_MESSAGE_CODE);
                    }
                });
    }
}
