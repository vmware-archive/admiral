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

package com.vmware.admiral.host;

import java.util.function.Predicate;

import com.vmware.admiral.auth.util.ProjectUtil;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Prevent deletion of {@link ResourceGroupState} if its in use by a {@link GroupResourcePlacementState}.
 */
public class ResourceGroupOperationProcessingChain extends OperationProcessingChain {

    public ResourceGroupOperationProcessingChain(ResourceGroupService service) {
        super(service);
        this.add(new Predicate<Operation>() {

            @Override
            public boolean test(Operation op) {
                switch (op.getAction()) {
                case DELETE:
                    return handleDelete(service, op, this);
                default:
                    return true;
                }
            }
        });
    }

    private boolean handleDelete(ResourceGroupService service, Operation op,
            Predicate<Operation> invokingFilter) {
        ResourceGroupState currentState = service.getState(op);

        QueryTask queryTask = ProjectUtil.createQueryTaskForProjectAssociatedWithPlacement(currentState, currentState.query);

        service.sendRequest(Operation.createPost(service, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        service.logWarning(Utils.toString(e));
                        op.fail(e);
                        return;
                    } else {
                        ServiceDocumentQueryResult result = o.getBody(QueryTask.class).results;
                        long documentCount = result.documentCount;
                        if (documentCount != 0) {
                            op.fail(new LocalizableValidationException(
                                    ProjectUtil.PROJECT_IN_USE_MESSAGE,
                                    ProjectUtil.PROJECT_IN_USE_MESSAGE_CODE,
                                    documentCount, documentCount > 1 ? "s" : ""));
                        } else {
                            resumeProcessingRequest(op, invokingFilter);
                        }
                    }
                }));

        return false;
    }
}