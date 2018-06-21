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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.EXPAND;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.FIXED_ITEM_NAME;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.SecurityGroupInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.tasks.ProvisionSecurityGroupTaskService.ProvisionSecurityGroupTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TaskService;

/**
 * Provision security group task service.
 */
public class ProvisionSecurityGroupTaskService
        extends TaskService<ProvisionSecurityGroupTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/security-group-tasks";

    public static final String NETWORK_STATE_ID_PROP_NAME = "__networkStateId";

    /**
     * Substages of the tasks.
     */
    public enum SubStage {
        CREATED, PROVISIONING_SECURITY_GROUPS, REMOVING_SECURITY_GROUPS, FINISHED, FAILED
    }

    /**
     * Represents state of a security group task.
     */
    public static class ProvisionSecurityGroupTaskState extends TaskService.TaskServiceState {
        public InstanceRequestType requestType;

        /**
         * The descriptions of the security group instances being realized/destroyed.
         */
        public Set<String> securityGroupDescriptionLinks;

        /**
         * Tracks the sub stage (creating network or security group). Set by the run-time.
         */
        public SubStage taskSubStage;

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
         * Custom properties associated with the task
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(usage = { OPTIONAL }, indexing = { EXPAND, FIXED_ITEM_NAME })
        public Map<String, String> customProperties;

        /**
         * A callback to the initiating task.
         */
        public ServiceTaskCallback<?> serviceTaskCallback;

        public void validate() throws Exception {
            if (this.requestType == null) {
                throw new IllegalArgumentException("requestType required");
            }

            if (this.securityGroupDescriptionLinks == null
                    || this.securityGroupDescriptionLinks.isEmpty()) {
                throw new IllegalArgumentException(
                        "securityGroupDescriptionLinks required");
            }
        }
    }

    public ProvisionSecurityGroupTaskService() {
        super(ProvisionSecurityGroupTaskState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation start) {
        if (!start.hasBody()) {
            start.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ProvisionSecurityGroupTaskState state = start
                .getBody(ProvisionSecurityGroupTaskState.class);
        try {
            state.validate();
        } catch (Exception e) {
            start.fail(e);
            return;
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

        ProvisionSecurityGroupTaskState currState = getState(patch);
        ProvisionSecurityGroupTaskState patchState = patch
                .getBody(ProvisionSecurityGroupTaskState.class);

        if (TaskState.isFailed(patchState.taskInfo)) {
            currState.taskInfo = patchState.taskInfo;
        }

        switch (patchState.taskInfo.stage) {
        case CREATED:
            currState.taskSubStage = nextStage(currState);

            handleSubStages(currState);
            logInfo(() -> String.format("Security Group %s on %s started",
                    currState.requestType.toString(), currState.securityGroupDescriptionLinks));
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

    private SubStage nextStage(ProvisionSecurityGroupTaskState state) {
        return state.requestType == InstanceRequestType.CREATE
                ? nextSubStageOnCreate(state.taskSubStage)
                : nextSubstageOnDelete(state.taskSubStage);
    }

    private SubStage nextSubStageOnCreate(SubStage currStage) {
        if (currStage == SubStage.CREATED) {
            return SubStage.PROVISIONING_SECURITY_GROUPS;
        } else if (currStage == SubStage.PROVISIONING_SECURITY_GROUPS) {
            return SubStage.FINISHED;
        } else {
            return SubStage.values()[currStage.ordinal() + 1];
        }
    }

    private SubStage nextSubstageOnDelete(SubStage currStage) {
        if (currStage == SubStage.CREATED) {
            return SubStage.REMOVING_SECURITY_GROUPS;
        } else if (currStage == SubStage.REMOVING_SECURITY_GROUPS) {
            return SubStage.FINISHED;
        } else {
            return SubStage.values()[currStage.ordinal() + 1];
        }
    }

    private void handleSubStages(ProvisionSecurityGroupTaskState currState) {
        switch (currState.taskSubStage) {
        case PROVISIONING_SECURITY_GROUPS:
        case REMOVING_SECURITY_GROUPS:
            patchAdapter(currState, null);
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

    private SecurityGroupInstanceRequest toReq(SecurityGroupState securityGroupState,
            ProvisionSecurityGroupTaskState taskState, String securityGroupDescriptionLink,
            String subTaskLink) {
        SecurityGroupInstanceRequest req = new SecurityGroupInstanceRequest();
        req.requestType = taskState.requestType;
        req.resourceReference = UriUtils.buildUri(this.getHost(),
                securityGroupDescriptionLink);
        req.authCredentialsLink = securityGroupState.authCredentialsLink;
        req.resourcePoolLink = securityGroupState.resourcePoolLink;
        req.taskReference = UriUtils.buildUri(getHost(), subTaskLink);
        req.isMockRequest = taskState.isMockRequest;
        req.customProperties = taskState.customProperties;

        return req;
    }

    private void patchAdapter(ProvisionSecurityGroupTaskState taskState, String subTaskLink) {
        if (subTaskLink == null) {
            createSubTask(taskState, link -> patchAdapter(taskState, link));
            return;
        }

        taskState.securityGroupDescriptionLinks.forEach(sgLink -> sendRequest(Operation
                .createGet(
                        UriUtils.buildUri(this.getHost(), sgLink))
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                // don't fail the task; just update the subtask, which will
                                // handle the failure if necessary
                                ResourceOperationResponse subTaskPatchBody = ResourceOperationResponse
                                        .fail(sgLink, e);
                                updateSubTask(subTaskLink, subTaskPatchBody);
                                return;
                            }
                            SecurityGroupState securityGroupState = o
                                    .getBody(SecurityGroupState.class);
                            SecurityGroupInstanceRequest req = toReq(securityGroupState,
                                    taskState, sgLink, subTaskLink);

                            sendRequest(Operation
                                    .createPatch(
                                            securityGroupState.instanceAdapterReference)
                                    .setBody(req)
                                    .setCompletion(
                                            (oo, ee) -> {
                                                if (ee != null) {
                                                    ResourceOperationResponse subTaskPatchBody = ResourceOperationResponse
                                                            .fail(sgLink, ee);
                                                    updateSubTask(subTaskLink,
                                                            subTaskPatchBody);
                                                }
                                            }));
                        })));
    }

    private void createSubTask(ProvisionSecurityGroupTaskState taskState,
            Consumer<String> subTaskLinkConsumer) {

        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback
                .create(UriUtils.buildPublicUri(getHost(), getSelfLink()));
        callback.onSuccessFinishTask();

        SubTaskService.SubTaskState<SubStage> subTaskInitState = new SubTaskService.SubTaskState<>();

        // tell the sub task with what to patch us, on completion
        subTaskInitState.serviceTaskCallback = callback;
        subTaskInitState.completionsRemaining = taskState.securityGroupDescriptionLinks.size();
        subTaskInitState.tenantLinks = taskState.tenantLinks;
        subTaskInitState.documentExpirationTimeMicros = taskState.documentExpirationTimeMicros;
        Operation startPost = Operation
                .createPost(this, SubTaskService.FACTORY_LINK)
                .setBody(subTaskInitState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(() -> String.format("Failure creating sub task: %s",
                                        Utils.toString(e)));
                                sendSelfPatch(TaskState.TaskStage.FAILED, e);
                                return;
                            }
                            SubTaskService.SubTaskState<?> body = o
                                    .getBody(SubTaskService.SubTaskState.class);

                            subTaskLinkConsumer.accept(body.documentSelfLink);
                        });
        sendRequest(startPost);
    }

    private void sendSelfPatch(TaskState.TaskStage stage, Throwable e) {
        ProvisionSecurityGroupTaskState body = new ProvisionSecurityGroupTaskState();
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

    private void updateSubTask(String link, Object body) {
        Operation patch = Operation
                .createPatch(this, link)
                .setBody(body)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logWarning(() -> String.format("SubTask patch failed: %s",
                                        Utils.toString(ex)));
                            }
                        });
        sendRequest(patch);
    }
}
