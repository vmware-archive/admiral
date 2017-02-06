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

package com.vmware.admiral.compute.endpoint;

import static java.util.EnumSet.of;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.EnumSet;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.SubscriptionManager;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;

public class EndpointHealthCheckTaskService extends
        AbstractTaskStatefulService<EndpointHealthCheckTaskService.EndpointHealthCheckTaskState,
                EndpointHealthCheckTaskService.EndpointHealthCheckTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_ENDPOINT_HEALTHCHECK_TASKS;
    public static final String DISPLAY_NAME = "Endpoint Health Check Service";

    public static class EndpointHealthCheckTaskState
            extends TaskServiceDocument<EndpointHealthCheckTaskState.SubStage> {

        @Documentation(description = "Link to the endpoint to check.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED },
                indexing = STORE_ONLY)
        public String endpointLink;

        @Documentation(description = "The link to the compute state.")
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL },
                indexing = STORE_ONLY)
        public String computeLink;

        @Documentation(description = "The link to the validation task state.")
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL },
                indexing = STORE_ONLY)
        public String endpointAllocationTaskLink;

        @Documentation(description = "The outcome of the validation task.")
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL },
                indexing = STORE_ONLY)
        public TaskStage validationOutcome;

        public enum SubStage {
            CREATED,
            CHECKING_CONNECTIVITY,
            UPDATING_ENDPOINT,
            COMPLETED
        }
    }

    public EndpointHealthCheckTaskService() {
        super(EndpointHealthCheckTaskState.class, EndpointHealthCheckTaskState.SubStage.class,
                DISPLAY_NAME);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.PERSISTENCE, false);
        setSelfDelete(true);
    }

    @Override
    protected void handleStartedStagePatch(EndpointHealthCheckTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            checkEndpoint(state.endpointLink)
                    .whenComplete((taskState, e) -> {
                        if (e != null) {
                            failTask("Error triggering endpoint check", e);
                            return;
                        }

                        proceedTo(EndpointHealthCheckTaskState.SubStage.CHECKING_CONNECTIVITY,
                                s -> {
                                    s.endpointAllocationTaskLink = taskState.documentSelfLink;
                                    s.computeLink = taskState.endpointState.computeLink;
                                });
                    });
            break;
        case CHECKING_CONNECTIVITY:
            SubscriptionManager<EndpointAllocationTaskState> subscriptionManager =
                    new SubscriptionManager<>(getHost(), this.getSelfId(),
                            state.endpointAllocationTaskLink,
                            EndpointAllocationTaskState.class);

            subscriptionManager.start(notification -> {
                EndpointAllocationTaskState eats = notification.getResult();

                EnumSet<TaskStage> terminalStages = of(TaskStage.FINISHED,
                        TaskStage.FAILED, TaskStage.CANCELLED);

                if (terminalStages.contains(eats.taskInfo.stage)) {
                    //TODO is it enough to unsubscribe only here?
                    subscriptionManager.close();

                    proceedTo(EndpointHealthCheckTaskState.SubStage.UPDATING_ENDPOINT,
                            s -> s.validationOutcome = eats.taskInfo.stage);
                }
            }, true);
            break;
        case UPDATING_ENDPOINT:
            switch (state.validationOutcome) {
            case FINISHED:
                patchComputeStateAndProceed(state, PowerState.ON);
                break;
            case FAILED:
                patchComputeStateAndProceed(state, PowerState.OFF);
                break;
            default:
                proceedTo(EndpointHealthCheckTaskState.SubStage.COMPLETED);
            }
            break;
        case COMPLETED:
            complete();
            break;
        default:
            logSevere("Unknown substage");
        }
    }

    public DeferredResult<EndpointAllocationTaskState> checkEndpoint(String endpointLink) {
        return getEndpointState(endpointLink).thenCompose(this::triggerValidateTask);
    }

    public DeferredResult<EndpointAllocationTaskState> triggerValidateTask(EndpointState state) {

        EndpointAllocationTaskState eats = new EndpointAllocationTaskState();
        eats.endpointState = state;
        eats.tenantLinks = state.tenantLinks;
        eats.taskInfo = new TaskState();
        eats.options = of(TaskOption.VALIDATE_ONLY);

        if (DeploymentProfileConfig.getInstance().isTest()) {
            eats.options.add(TaskOption.IS_MOCK);
        }

        Operation operation = Operation.createPost(
                this, EndpointAllocationTaskService.FACTORY_LINK).setBody(eats);

        return this.sendWithDeferredResult(operation, EndpointAllocationTaskState.class);
    }

    public DeferredResult<EndpointState> getEndpointState(String endpointLink) {
        return this.sendWithDeferredResult(
                Operation.createGet(this, endpointLink),
                EndpointState.class);
    }

    public DeferredResult<ComputeState> patchComputeState(String computeLink,
            ComputeState patchBody) {
        return this.sendWithDeferredResult(
                Operation.createPatch(this, computeLink).setBody(patchBody),
                ComputeState.class);
    }

    private void patchComputeStateAndProceed(EndpointHealthCheckTaskState state,
            PowerState powerState) {
        ComputeState cs = new ComputeState();
        cs.powerState = powerState;
        patchComputeState(state.computeLink, cs)
                .whenComplete((res, e) -> {
                    if (e != null) {
                        failTask("Error updating endpoint", e);
                        return;
                    }
                    proceedTo(EndpointHealthCheckTaskState.SubStage.COMPLETED);
                });
    }
}
