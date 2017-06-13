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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.request.compute.LoadBalancerRemovalTaskService.LoadBalancerRemovalTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.tasks.ProvisionLoadBalancerTaskService;
import com.vmware.photon.controller.model.tasks.ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback;
import com.vmware.photon.controller.model.tasks.SubTaskService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Task implementing the removal of a load balancer.
 */
public class LoadBalancerRemovalTaskService extends
        AbstractTaskStatefulService<LoadBalancerRemovalTaskService.LoadBalancerRemovalTaskState, SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_LOAD_BALANCER_REMOVAL_TASKS;

    public static final String DISPLAY_NAME = "Load Balancer Removal";

    public static class LoadBalancerRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<LoadBalancerRemovalTaskState
                    .SubStage> {

        /**
         * (Required) The resources on which the given operation will be applied
         */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        public enum SubStage {
            CREATED,
            REMOVING_LOADBALANCERS,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Collections.singletonList(REMOVING_LOADBALANCERS));
        }
    }

    public LoadBalancerRemovalTaskService() {
        super(LoadBalancerRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(LoadBalancerRemovalTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            removeLoadBalancers(state, null);
            break;
        case REMOVING_LOADBALANCERS:
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

    private void removeLoadBalancers(LoadBalancerRemovalTaskState state, String subTaskLink) {
        try {
            if (subTaskLink == null) {
                createSubTaskForRemoveLoadbalancerCallbacks(state,
                        link -> removeLoadBalancers(state, link));
                return;
            }

            boolean isMockRequest = DeploymentProfileConfig.getInstance().isTest();
            ServiceTaskCallback<TaskStage> callback =
                    ServiceTaskCallback.create(UriUtils.buildUri(getHost(), subTaskLink));
            callback.onSuccessFinishTask();
            callback.onErrorFailTask();

            List<DeferredResult<Operation>> ops = state.resourceLinks.stream().map(lbLink -> {

                ProvisionLoadBalancerTaskState task = new ProvisionLoadBalancerTaskState();
                task.isMockRequest = isMockRequest;
                task.requestType = InstanceRequestType.DELETE;
                task.serviceTaskCallback = callback;
                task.tenantLinks = state.tenantLinks;
                task.documentExpirationTimeMicros = ServiceUtils
                        .getDefaultTaskExpirationTimeInMicros();
                task.loadBalancerLink = lbLink;

                return sendWithDeferredResult(
                        Operation.createPost(this, ProvisionLoadBalancerTaskService
                                .FACTORY_LINK)
                                .setBody(task))
                        .exceptionally(e -> {
                            ResourceOperationResponse r = ResourceOperationResponse
                                    .fail(lbLink, e);
                            completeSubTask(subTaskLink, r);
                            return null;
                        });

            }).collect(Collectors.toList());

            DeferredResult.allOf(ops).whenComplete((all, e) -> {
                logInfo("Requested removal of %s load balancers.", state.resourceLinks.size());
                proceedTo(SubStage.REMOVING_LOADBALANCERS);
            });

        } catch (Throwable e) {
            failTask("System failure removing load balancers", e);
        }

    }

    private void createSubTaskForRemoveLoadbalancerCallbacks(LoadBalancerRemovalTaskState
            state, Consumer<String> callbackFunction) {

        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback.create(getUri());
        callback.onSuccessTo(SubStage.COMPLETED);
        SubTaskService.SubTaskState<SubStage> subTaskInitState = new SubTaskService.SubTaskState<>();
        // tell the sub task with what to patch us, on completion
        subTaskInitState.serviceTaskCallback = callback;
        subTaskInitState.completionsRemaining = state.resourceLinks.size();
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
                            SubTaskService.SubTaskState<?> body = o
                                    .getBody(SubTaskService.SubTaskState.class);
                            // continue, passing the sub task link
                            callbackFunction.accept(body.documentSelfLink);
                        });
        sendRequest(startPost);
    }

    private void completeSubTask(String subTaskLink, Object body) {
        Operation.createPatch(this, subTaskLink)
                .setBody(body)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logWarning("Unable to complete subtask: %s, reason: %s",
                                        subTaskLink, Utils.toString(ex));
                            }
                        })
                .sendWith(this);
    }
}
