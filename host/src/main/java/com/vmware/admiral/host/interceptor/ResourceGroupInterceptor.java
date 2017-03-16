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

package com.vmware.admiral.host.interceptor;

import com.vmware.admiral.auth.util.ProjectUtil;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Prevent deletion of {@link ResourceGroupState} if its in use by a {@link GroupResourcePlacementState}.
 */
public class ResourceGroupInterceptor {

    public static void register(OperationInterceptorRegistry registry) {
        registry.addServiceInterceptor(
                ResourceGroupService.class, Action.DELETE, ResourceGroupInterceptor::interceptDelete);
    }

    public static DeferredResult<Void> interceptDelete(Service service, Operation op) {
        ResourceGroupState currentState = service.getState(op);

        QueryTask queryTask = ProjectUtil.createQueryTaskForProjectAssociatedWithPlacement(
                currentState, currentState.query);
        return QueryUtils.startQueryTask(service, queryTask).thenAccept(qt -> {
            ServiceDocumentQueryResult result = qt.results;
            long documentCount = result.documentCount;
            if (documentCount != 0) {
                throw new LocalizableValidationException(
                        ProjectUtil.PROJECT_IN_USE_MESSAGE,
                        ProjectUtil.PROJECT_IN_USE_MESSAGE_CODE,
                        documentCount, documentCount > 1 ? "s" : "");
            }
        });
    }
}