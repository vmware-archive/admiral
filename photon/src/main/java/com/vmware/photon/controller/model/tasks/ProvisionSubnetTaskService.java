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

import java.util.EnumSet;
import java.util.List;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisionSubnetTaskService.ProvisionSubnetTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;

/**
 * Provision subnet task service.
 */
public class ProvisionSubnetTaskService extends TaskService<ProvisionSubnetTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/sub-network-tasks";

    /**
     * SubStage.
     */
    public enum SubStage {
        CREATED, PROVISIONING_SUBNET, REMOVING_SUBNET, FINISHED, FAILED
    }

    /**
     * Represent state of a provision task.
     */
    public static class ProvisionSubnetTaskState extends TaskService.TaskServiceState {

        /**
         * The type of an instance request. Required
         */
        public InstanceRequestType requestType;

        /**
         * The description of the subnet instance being realized. Required
         *
         * Deprecated in favor of {@link #subnetLink}.
         */
        @Deprecated
        public String subnetDescriptionLink;

        /**
         * The subnet instance being realized. Required
         */
        public String subnetLink;

        /**
         * Tracks the sub stage. Set by the run-time.
         */
        public SubStage taskSubStage;

        /**
         * Task options
         */
        public EnumSet<TaskOption> options;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;

        /**
         * A callback to the initiating task.
         */
        public ServiceTaskCallback<?> serviceTaskCallback;

        public void validate() throws Exception {
            if (this.requestType == null) {
                throw new IllegalArgumentException("requestType required");
            }

            if (this.subnetLink == null || this.subnetLink.isEmpty()) {
                if (this.subnetDescriptionLink != null) {
                    this.subnetLink = this.subnetDescriptionLink;
                } else {
                    throw new IllegalArgumentException("subnetLink required");
                }
            }

            if (this.options == null) {
                this.options = EnumSet.noneOf(TaskOption.class);
            }
        }
    }

    public ProvisionSubnetTaskService() {
        super(ProvisionSubnetTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation start) {
        if (!start.hasBody()) {
            start.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ProvisionSubnetTaskState state = start.getBody(ProvisionSubnetTaskState.class);
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

        ProvisionSubnetTaskState currentState = getState(patch);
        ProvisionSubnetTaskState patchState = patch
                .getBody(ProvisionSubnetTaskState.class);

        if (TaskState.isFailed(patchState.taskInfo)) {
            currentState.taskInfo = patchState.taskInfo;
        }

        switch (patchState.taskInfo.stage) {
        case CREATED:
            currentState.taskSubStage = nextStage(currentState);

            handleSubStages(currentState);
            logInfo(() -> String.format("%s %s on %s started", "Subnet",
                    currentState.requestType.toString(), currentState.subnetLink));
            break;
        case STARTED:
            currentState.taskInfo.stage = TaskState.TaskStage.STARTED;
            break;
        case FINISHED:
            SubStage nextStage = nextStage(currentState);
            if (nextStage == SubStage.FINISHED) {
                currentState.taskInfo.stage = TaskState.TaskStage.FINISHED;
                logInfo(() -> "Task is complete");
                ServiceTaskCallback.sendResponse(currentState.serviceTaskCallback, this,
                        currentState);
            } else {
                sendSelfPatch(TaskState.TaskStage.CREATED, null);
            }
            break;
        case FAILED:
            logWarning(() -> String.format("Task failed with %s",
                    Utils.toJsonHtml(currentState.taskInfo.failure)));
            ServiceTaskCallback.sendResponse(currentState.serviceTaskCallback, this, currentState);
            break;
        case CANCELLED:
            break;
        default:
            break;
        }

        patch.complete();
    }

    private SubStage nextStage(ProvisionSubnetTaskState state) {
        return state.requestType == InstanceRequestType.CREATE
                ? nextSubStageOnCreate(state.taskSubStage)
                : nextSubstageOnDelete(state.taskSubStage);
    }

    private SubStage nextSubStageOnCreate(SubStage currStage) {
        switch (currStage) {
        case CREATED:
            return SubStage.PROVISIONING_SUBNET;
        case PROVISIONING_SUBNET:
            return SubStage.FINISHED;
        default:
            return SubStage.values()[currStage.ordinal() + 1];
        }
    }

    private SubStage nextSubstageOnDelete(SubStage currStage) {
        switch (currStage) {
        case CREATED:
            return SubStage.REMOVING_SUBNET;
        case REMOVING_SUBNET:
            return SubStage.FINISHED;
        default:
            return SubStage.values()[currStage.ordinal() + 1];
        }
    }

    private void handleSubStages(ProvisionSubnetTaskState currState) {
        switch (currState.taskSubStage) {
        case PROVISIONING_SUBNET:
        case REMOVING_SUBNET:
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

    private SubnetInstanceRequest toReq(ProvisionSubnetTaskState taskState) {
        SubnetInstanceRequest req = new SubnetInstanceRequest();
        req.requestType = taskState.requestType;
        req.resourceReference = UriUtils.buildUri(this.getHost(),
                taskState.subnetLink);
        req.taskReference = this.getUri();
        req.isMockRequest = taskState.options.contains(TaskOption.IS_MOCK);

        return req;
    }

    private void patchAdapter(ProvisionSubnetTaskState taskState) {
        sendRequest(Operation
                .createGet(UriUtils.buildUri(this.getHost(), taskState.subnetLink))
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                sendSelfPatch(TaskState.TaskStage.FAILED, e);
                                return;
                            }
                            SubnetState subnetState = o.getBody(SubnetState.class);
                            if (subnetState.instanceAdapterReference == null) {
                                sendSelfPatch(TaskState.TaskStage.FAILED,
                                        new IllegalArgumentException(String.format(
                                                "instanceAdapterReference required (%s)",
                                                taskState.subnetLink)));
                                return;
                            }

                            SubnetInstanceRequest req = toReq(taskState);

                            sendRequest(Operation
                                    .createPatch(subnetState.instanceAdapterReference)
                                    .setBody(req)
                                    .setCompletion(
                                            (oo, ee) -> {
                                                if (ee != null) {
                                                    sendSelfPatch(
                                                            TaskState.TaskStage.FAILED,
                                                            ee);
                                                }
                                            }));
                        }));
    }

    private void sendSelfPatch(TaskState.TaskStage stage, Throwable e) {
        ProvisionSubnetTaskState body = new ProvisionSubnetTaskState();
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
