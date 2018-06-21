/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.tasks;

import java.util.List;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;

/**
 * Provision load balancer task service.
 *
 * TODO pmitrov: create a common utility for task services which only goal is to call an adapter and
 * wait for the response.
 */
public class ProvisionLoadBalancerTaskService
        extends TaskService<ProvisionLoadBalancerTaskService.ProvisionLoadBalancerTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/load-balancer-tasks";

    /**
     * SubStage.
     */
    public enum SubStage {
        CREATED, PROVISIONING, FINISHED, FAILED
    }

    /**
     * Represent state of a provision task.
     */
    public static class ProvisionLoadBalancerTaskState extends TaskService.TaskServiceState {

        /**
         * The type of an instance request. Required
         */
        public InstanceRequestType requestType;

        /**
         * The load balancer state. Required
         */
        public String loadBalancerLink;

        /**
         * For testing. If set, the request will not actuate any computes directly but will patch
         * back success.
         */
        public boolean isMockRequest = false;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;

        /**
         * A callback to the initiating task.
         */
        public ServiceTaskCallback<?> serviceTaskCallback;

        /**
         * Tracks the sub stage. Set by the run-time.
         */
        public SubStage taskSubStage;

        public void validate() throws Exception {
            if (this.requestType == null) {
                throw new IllegalArgumentException("requestType required");
            }

            if (this.loadBalancerLink == null || this.loadBalancerLink.isEmpty()) {
                throw new IllegalArgumentException("loadBalancerLink required");
            }
        }
    }

    public ProvisionLoadBalancerTaskService() {
        super(ProvisionLoadBalancerTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation start) {
        if (!start.hasBody()) {
            start.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ProvisionLoadBalancerTaskState state = start
                .getBody(ProvisionLoadBalancerTaskState.class);
        try {
            state.validate();
        } catch (Exception e) {
            start.fail(e);
        }
        state.taskInfo = new TaskState();
        state.taskInfo.stage = TaskState.TaskStage.CREATED;
        state.taskSubStage = SubStage.CREATED;
        start.complete();

        // start the task
        sendSelfPatch(TaskState.TaskStage.CREATED, null);
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ProvisionLoadBalancerTaskState currState = getState(patch);
        ProvisionLoadBalancerTaskState patchState = patch
                .getBody(ProvisionLoadBalancerTaskState.class);

        if (TaskState.isFailed(patchState.taskInfo)) {
            currState.taskInfo = patchState.taskInfo;
        }

        switch (patchState.taskInfo.stage) {
        case CREATED:
            currState.taskSubStage = nextStage(currState);

            handleSubStages(currState);
            logInfo(() -> String.format("Load balancer %s on %s started",
                    currState.requestType.toString(), currState.loadBalancerLink));
            break;
        case STARTED:
            currState.taskInfo.stage = TaskState.TaskStage.STARTED;
            break;
        case FINISHED:
            SubStage nextStage = nextStage(currState);
            if (nextStage == SubStage.FINISHED) {
                currState.taskInfo.stage = TaskState.TaskStage.FINISHED;
                logInfo(() -> "Task is complete");
                ServiceTaskCallback.sendResponse(currState.serviceTaskCallback, this, currState);
            } else {
                sendSelfPatch(TaskState.TaskStage.CREATED, null);
            }
            break;
        case FAILED:
            logWarning(() -> String.format("Task failed with %s",
                    Utils.toJsonHtml(currState.taskInfo.failure)));
            ServiceTaskCallback.sendResponse(currState.serviceTaskCallback, this, currState);
            break;
        case CANCELLED:
            break;
        default:
            break;
        }

        patch.complete();
    }

    private SubStage nextStage(ProvisionLoadBalancerTaskState state) {
        return state.requestType == InstanceRequestType.CREATE
                ? nextSubStageOnCreate(state.taskSubStage)
                : nextSubstageOnDelete(state.taskSubStage);
    }

    private SubStage nextSubStageOnCreate(SubStage currStage) {
        return SubStage.values()[currStage.ordinal() + 1];
    }

    // deletes follow the inverse order;
    private SubStage nextSubstageOnDelete(SubStage currStage) {
        if (currStage == SubStage.CREATED) {
            return SubStage.PROVISIONING;
        } else if (currStage == SubStage.PROVISIONING) {
            return SubStage.FINISHED;
        } else {
            return SubStage.values()[currStage.ordinal() + 1];
        }
    }

    private void handleSubStages(ProvisionLoadBalancerTaskState currState) {
        switch (currState.taskSubStage) {
        case PROVISIONING:
            patchAdapter(currState);
            break;
        case FINISHED:
            sendSelfPatch(TaskState.TaskStage.FINISHED, null);
            break;
        case FAILED:
            break;
        default:
            break;
        }
    }

    private LoadBalancerInstanceRequest toReq(ProvisionLoadBalancerTaskState taskState) {
        LoadBalancerInstanceRequest req = new LoadBalancerInstanceRequest();
        req.requestType = taskState.requestType;
        req.resourceReference = UriUtils.buildUri(this.getHost(),
                taskState.loadBalancerLink);
        req.taskReference = this.getUri();
        req.isMockRequest = taskState.isMockRequest;

        return req;
    }

    private void patchAdapter(ProvisionLoadBalancerTaskState taskState) {
        sendRequest(Operation
                .createGet(
                        UriUtils.buildUri(this.getHost(), taskState.loadBalancerLink))
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                if (e instanceof ServiceNotFoundException
                                        && InstanceRequestType.DELETE
                                                .equals(taskState.requestType)) {
                                    logWarning("Load balancer not found at %s, nothing to delete",
                                            taskState.loadBalancerLink);
                                    sendSelfPatch(TaskState.TaskStage.FINISHED, null);
                                } else {
                                    sendSelfPatch(TaskState.TaskStage.FAILED, e);
                                }
                                return;
                            }
                            LoadBalancerState loadBalancerState = o
                                    .getBody(LoadBalancerState.class);
                            LoadBalancerInstanceRequest req = toReq(taskState);

                            sendRequest(Operation
                                    .createPatch(loadBalancerState.instanceAdapterReference)
                                    .setBody(req)
                                    .setCompletion((oo, ee) -> {
                                        if (ee != null) {
                                            sendSelfPatch(TaskState.TaskStage.FAILED, ee);
                                        }
                                    }));
                        }));
    }

    private void sendSelfPatch(TaskState.TaskStage stage, Throwable e) {
        ProvisionLoadBalancerTaskState body = new ProvisionLoadBalancerTaskState();
        body.taskInfo = new TaskState();
        if (e == null) {
            body.taskInfo.stage = stage;
        } else {
            body.taskInfo.stage = TaskState.TaskStage.FAILED;
            body.taskInfo.failure = Utils.toServiceErrorResponse(e);
            logWarning(() -> String.format("Patching to failed: %s", Utils.toString(e)));
        }

        sendSelfPatch(body);
    }
}
