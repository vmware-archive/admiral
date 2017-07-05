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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.request.compute.LoadBalancerOperationTaskService.LoadBalancerOperationTaskState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback;
import com.vmware.photon.controller.model.tasks.SubTaskService;
import com.vmware.photon.controller.model.tasks.SubTaskService.SubTaskState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class LoadBalancerOperationTaskService extends
        AbstractTaskStatefulService<
                LoadBalancerOperationTaskState,
                LoadBalancerOperationTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_LOAD_BALANCER_OPERATION_TASKS;

    public static final String DISPLAY_NAME = "Load Balancer Operation";

    public static class LoadBalancerOperationTaskState extends
            TaskServiceDocument<LoadBalancerOperationTaskState.SubStage> {
        public enum SubStage {
            CREATED,
            COMPLETED,
            ERROR
        }

        /**
         * (Required) The identifier of the resource operation to be performed.
         */
        @Documentation(description = "The identifier of the resource operation to be performed.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED })
        public String operation;

        /**
         * (Required) The resources on which the given operation will be applied
         */
        @Documentation(
                description = "The resources on which the given operation will be applied")
        @PropertyOptions(usage = { REQUIRED, SINGLE_ASSIGNMENT })
        public Set<String> resourceLinks;
    }

    public LoadBalancerOperationTaskService() {
        super(LoadBalancerOperationTaskState.class, LoadBalancerOperationTaskState.SubStage.class,
                DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(LoadBalancerOperationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            performResourceOperations(state, null, null);
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

    private void queryLoadBalancers(LoadBalancerOperationTaskState state,
            Consumer<List<LoadBalancerState>> callbackFunction) {

        List<DeferredResult<LoadBalancerState>> LBStatesDRs = state.resourceLinks
                .stream()
                .map(link -> sendWithDeferredResult(
                        Operation.createGet(UriUtils.buildUri(getHost(), link)),
                        LoadBalancerState.class)
                )
                .collect(Collectors.toList());

        DeferredResult.allOf(LBStatesDRs).whenComplete((lbs, e) -> {
            if (e != null) {
                logWarning("Failure querying load balancer states: %s",
                        Utils.toString(e));
                failTask("Failure querying load balancer states", e);
                return;
            }
            callbackFunction.accept(lbs);
        });
    }

    private void createSubTaskForOperationCallbacks(LoadBalancerOperationTaskState state,
            List<LoadBalancerState> resourceStates,
            Consumer<String> callbackConsumer) {

        ServiceTaskCallback<LoadBalancerOperationTaskState.SubStage> callback = ServiceTaskCallback
                .create(getUri());
        callback.onSuccessTo(LoadBalancerOperationTaskState.SubStage.COMPLETED);
        SubTaskState<LoadBalancerOperationTaskState.SubStage> subTaskInitState = new SubTaskState<>();
        // tell the sub task with what to patch us, on completion
        subTaskInitState.serviceTaskCallback = callback;
        subTaskInitState.completionsRemaining = resourceStates.size();
        subTaskInitState.tenantLinks = state.tenantLinks;
        Operation startPost = Operation
                .createPost(this, SubTaskService.FACTORY_LINK)
                .setBody(subTaskInitState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning("Failure creating sub task: %s",
                                        Utils.toString(e));
                                failTask("Failure creating sub task", e);
                                return;
                            }
                            SubTaskState<?> body = o.getBody(SubTaskState.class);
                            // continue, passing the sub task link
                            callbackConsumer.accept(body.documentSelfLink);
                        });
        sendRequest(startPost);
    }

    private void performResourceOperations(LoadBalancerOperationTaskState state,
            List<LoadBalancerState> resourceStates,
            String subTaskLink) {
        try {
            if (resourceStates == null) {
                queryLoadBalancers(state,
                        lbs -> performResourceOperations(state, lbs, subTaskLink));
                return;
            }

            if (subTaskLink == null) {
                createSubTaskForOperationCallbacks(state, resourceStates,
                        link -> performResourceOperations(state, resourceStates, link));
                return;
            }

            ServiceTaskCallback<TaskStage> callback =
                    ServiceTaskCallback.create(UriUtils.buildUri(getHost(), subTaskLink));
            callback.onSuccessFinishTask();
            callback.onErrorFailTask();

            try {
                logInfo("Starting %s of %d load balancer resources", state.operation, resourceStates
                        .size());
                resourceStates.forEach(LoadBalancerState ->
                        invokeAdapter(LoadBalancerState, state, callback));
            } catch (Throwable e) {
                failTask("Unexpected exception while requesting operation: " + state.operation, e);
            }

        } catch (Throwable e) {
            failTask("System failure performing load balancer operation", e);
        }
    }

    private void invokeAdapter(LoadBalancerState resourceState,
            LoadBalancerOperationTaskState state,
            ServiceTaskCallback<TaskStage> callback) {

        LoadBalancerOperationType loadBalancerOperationType = LoadBalancerOperationType
                .instanceById(state.operation);

        if (loadBalancerOperationType == null) {

            logWarning("Target load balancer %s, doesn't support %s operation",
                    resourceState.documentSelfLink,
                    state.operation);

            IllegalArgumentException e = new IllegalArgumentException(
                    String.format("No operation %s, for load balancer: %s",
                            state.operation, resourceState.documentSelfLink));
            callback.sendResponse(this, e);
            return;
        }

        URI adapterReference = loadBalancerOperationType.getAdapterReference(resourceState);

        Object body = loadBalancerOperationType.getBody(state, UriUtils.buildUri(this.getHost(),
                resourceState.documentSelfLink), callback.serviceURI);

        sendRequest(Operation.createPatch(adapterReference)
                .setBody(body)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Error when call adapter: %s to perform operation: %s "
                                        + "for resource: %s. Cause: %s",
                                adapterReference,
                                state.operation,
                                state.documentSelfLink,
                                Utils.toString(e));
                        callback.sendResponse(this, e);
                        return;
                    }
                }));
    }
}
