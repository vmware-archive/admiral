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

package com.vmware.admiral.request.compute;

import java.util.Collections;
import java.util.Set;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.resources.LoadBalancerService;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;

/**
 * Task implementing the allocation of a load balancer.
 */
public class LoadBalancerAllocationTaskService extends
        AbstractTaskStatefulService<LoadBalancerAllocationTaskService.LoadBalancerAllocationTaskState, DefaultSubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_LOAD_BALANCER_ALLOCATION_TASKS;

    public static final String DISPLAY_NAME = "Load Balancer Allocation";

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    public static class LoadBalancerAllocationTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<DefaultSubStage> {
        // TODO

        /**
         * Set by a Task with the links of the allocated resources.
         */
        @Documentation(description = "Set by a Task with the links of the allocated resources.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Set<String> resourceLinks;
    }

    public LoadBalancerAllocationTaskService() {
        super(LoadBalancerAllocationTaskState.class, DefaultSubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(LoadBalancerAllocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            proceedTo(DefaultSubStage.COMPLETED, s -> {
                // TODO: needed to wire-up things; should be remove when the actual impl is done
                s.resourceLinks = Collections
                        .singleton(LoadBalancerService.FACTORY_LINK + "/dummy-link");
            });
            break;
        case COMPLETED:
            complete();
            break;
        case ERROR:
            completeWithError();
            break;
        default:
            break;
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            LoadBalancerAllocationTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated resources.");
        }
        return finishedResponse;
    }

    @Override
    protected ServiceTaskCallbackResponse getFailedCallbackResponse(
            LoadBalancerAllocationTaskState state) {
        CallbackCompleteResponse failedResponse = new CallbackCompleteResponse();
        failedResponse.copy(state.serviceTaskCallback.getFailedResponse(state.taskInfo.failure));
        failedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated resources.");
        }
        return failedResponse;
    }
}
