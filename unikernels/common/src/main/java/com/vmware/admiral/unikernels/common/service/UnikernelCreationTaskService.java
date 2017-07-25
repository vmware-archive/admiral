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

package com.vmware.admiral.unikernels.common.service;

import java.net.URI;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

import com.vmware.xenon.services.common.TaskService;

public class UnikernelCreationTaskService
        extends TaskService<UnikernelCreationTaskService.UnikernelCreationTaskServiceState> {

    public static final String FACTORY_LINK = UnikernelManagementURIParts.CREATION;
    private String compilationURI;

    public enum SubStage {
        PROVISION_CONTAINER, CREATE_UNIKERNEL, HANDLE_CALLBACK
    }

    public static class UnikernelCreationTaskServiceState extends TaskService.TaskServiceState {

        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public SubStage subStage;

        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public CompilationData data;
    }

    public UnikernelCreationTaskService() {
        super(UnikernelCreationTaskServiceState.class);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.INSTRUMENTATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    protected UnikernelCreationTaskServiceState validateStartPost(Operation taskOperation) {

        UnikernelCreationTaskServiceState task = super.validateStartPost(taskOperation);

        CompilationData data = task.data;
        if (data != null) {
            if (!data.isSet()) {
                taskOperation.fail(
                        new IllegalArgumentException(
                                "Not all the required data is supplied for completing the task"));
                return null;
            }
        } else {
            taskOperation.fail(
                    new IllegalArgumentException(
                            "Not all the required data is supplied for completing the task"));
        }
        if (ServiceHost.isServiceCreate(taskOperation)) {
            if (task.subStage != null) {
                taskOperation.fail(
                        new IllegalArgumentException("Do not specify subStage: internal use only"));
                return null;
            }
        }
        return task;
    }

    @Override
    protected void initializeState(UnikernelCreationTaskServiceState task,
            Operation taskOperation) {
        super.initializeState(task, taskOperation);
        task.subStage = SubStage.PROVISION_CONTAINER;
    }

    @Override
    public void handlePatch(Operation patch) {
        UnikernelCreationTaskServiceState currentTask = getState(patch);
        UnikernelCreationTaskServiceState patchBody = getBody(patch);
        System.out.println(getUri());
        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }

        updateState(currentTask, patchBody);
        patch.complete();

        switch (patchBody.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleSubstage(patchBody);
            break;
        case CANCELLED:
            logInfo("Task canceled: not implemented, ignoring");
            break;
        case FINISHED:
            System.out.println("Task finished successfully");
            logInfo("Task finished successfully");
            break;
        case FAILED:
            logWarning("Task failed: %s", (patchBody.failureMessage == null ? "No reason given"
                    : patchBody.failureMessage));
            break;
        default:
            logWarning("Unexpected stage: %s", patchBody.taskInfo.stage);
            break;
        }
    }

    private void handleSubstage(UnikernelCreationTaskServiceState task) {
        switch (task.subStage) {
        case PROVISION_CONTAINER:
            provisionContainer(task);
            break;
        case CREATE_UNIKERNEL:
            createUnikernel(task);
            break;
        case HANDLE_CALLBACK:
            handleCallback(task);
            break;
        default:
            logWarning("Unexpected sub stage: %s", task.subStage);
            break;
        }
    }

    private void provisionContainer(UnikernelCreationTaskServiceState task) {
        // During the provisioning patch an IP would be allocated for callback for now it is hardcoded
       // compilationURI =  UnikernelManagementURIParts.COMPILATION_EXTERNAL;
        compilationURI = getHost().getUri() + UnikernelManagementURIParts.COMPILATION_TEST;
        logInfo("PROVISIONING CONTAINER");

        sendSelfPatch(task, TaskStage.STARTED, SubStage.CREATE_UNIKERNEL);
    }

    private void createUnikernel(UnikernelCreationTaskServiceState task) {
        // No need for self patch here cause the OSv-container responds on final with a Patch
        CompilationData forwardedData = task.data;
        forwardedData.successCB = getHost().getUri().toString()
                + UnikernelManagementURIParts.SUCCESS_CB;
        forwardedData.failureCB = getHost().getUri().toString()
                + UnikernelManagementURIParts.FAILURE_CB;

        URI requestUri = UriUtils.buildUri(compilationURI);
        Operation request = Operation
                .createPost(requestUri)
                .setReferer(getUri())
                .setBody(forwardedData);

        logInfo("CREATING UNIKERNEL");

        sendRequest(request);
    }

    private void handleCallback(UnikernelCreationTaskServiceState task) {
        // No need for self patch here cause the downloadService responds with a final Patch
        Operation request = Operation.createPost(this, UnikernelManagementURIParts.DOWNLOAD)
                .setReferer(getSelfLink())
                .setBody(task.data.downloadLink);

        logInfo("");

        sendRequest(request);
    }

    private void sendSelfPatch(UnikernelCreationTaskServiceState task, TaskStage stage,
            SubStage subStage) {
        if (task.taskInfo == null) {
            task.taskInfo = new TaskState();
        }
        task.taskInfo.stage = stage;
        task.subStage = subStage;
        sendTaskPatch(task);
    }

    private void sendTaskPatch(UnikernelCreationTaskServiceState task) {
        Operation patch = Operation.createPatch(getUri())
                .setBody(task)
                .setCompletion(
                        (op, ex) -> {
                            if (ex != null) {
                                logWarning("Failed to send patch, task has failed: %s",
                                        ex.getMessage());
                            }
                        });
        sendRequest(patch);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        UnikernelCreationTaskServiceState template = new UnikernelCreationTaskServiceState();
        template.data = new CompilationData();
        template.data.capstanfile = "Capstanfile";
        template.data.sources = "Sources";
        template.data.compilationPlatform = "Platform";
        template.data.successCB = "";
        template.data.failureCB = "";
        return template;
    }

}
