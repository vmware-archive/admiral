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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerDescriptionFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.request.ContainerAllocationTaskFactoryService;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.TaskService;

public class UnikernelCreationTaskService
        extends TaskService<UnikernelCreationTaskService.UnikernelCreationTaskServiceState> {

    public static final String FACTORY_LINK = UnikernelManagementURIParts.CREATION;

    private static final int UNIKERNEL_CREATION_RETRY_COUNT = Integer.getInteger(
            "com.vmware.admiral.service.extensibility.notification.retries", 3);
    private static final int UNIKERNEL_CREATION_RETRY_WAIT = Integer.getInteger(
            "com.vmware.admiral.service.extensibility.notification.wait", 15);

    public enum SubStage {
        SEARCH_EXISTING_CONTAINER, SEARCH_EXISTING_PLACEMENT, PROVISION_CONTAINER, FIND_CONTAINER, PUT_CONTAINER_URL, CREATE_UNIKERNEL, HANDLE_CALLBACK
    }

    public static class UnikernelCreationTaskServiceState extends TaskService.TaskServiceState {

        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String compilationURI = "";

        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public SubStage taskSubStage;

        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public CompilationData data;

        @PropertyOptions(usage = { SINGLE_ASSIGNMENT,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String groupResourcePlacementLink;

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
            if (task.taskSubStage != null) {
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
        task.taskSubStage = SubStage.SEARCH_EXISTING_CONTAINER;
    }

    @Override
    public void handlePatch(Operation patch) {
        UnikernelCreationTaskServiceState currentTask = getState(patch);
        UnikernelCreationTaskServiceState patchBody = getBody(patch);

        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }

        updateState(currentTask, patchBody);
        patch.complete();

        switch (currentTask.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleSubstage(currentTask);
            break;
        case CANCELLED:
            logInfo("Task canceled: not implemented, ignoring");
            break;
        case FINISHED:
            logInfo("Task finished successfully");
            break;
        case FAILED:
            logWarning("Task failed: %s", (currentTask.failureMessage == null ? "No reason given"
                    : currentTask.failureMessage));
            break;
        default:
            logWarning("Unexpected stage: %s", currentTask.taskInfo.stage);
            break;
        }
    }

    private void handleSubstage(UnikernelCreationTaskServiceState task) {
        switch (task.taskSubStage) {
        case SEARCH_EXISTING_CONTAINER:
            searchForExistingContainer(task);
            break;
        case SEARCH_EXISTING_PLACEMENT:
            searchForExistingPlacement(task);
            break;
        case PROVISION_CONTAINER:
            provisionContainer(task);
            break;
        case FIND_CONTAINER:
            searchForExistingContainer(task);
            break;
        case PUT_CONTAINER_URL:
            putContainerURL(task);
            break;
        case CREATE_UNIKERNEL:
            createUnikernel(task, UNIKERNEL_CREATION_RETRY_COUNT);
            break;
        case HANDLE_CALLBACK:
            handleCallback(task);
            break;
        default:
            logWarning("Unexpected sub stage: %s", task.taskSubStage);
            break;
        }
    }

    private void searchForExistingContainer(UnikernelCreationTaskServiceState task) {
        logInfo("SEARCHING FOR CONTAINER");
        List<ContainerState> foundOSVContainers = new ArrayList<>();

        String fieldName = QuerySpecification
                .buildCompositeFieldName(ContainerState.FIELD_NAME_CUSTOM_PROPERTIES,
                        OSvInformation.propertyTag);

        QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerState.class,
                fieldName, OSvInformation.propertyValue);
        QueryUtil.addExpandOption(queryTask);

        new ServiceDocumentQuery<>(getHost(), ContainerState.class).query(queryTask, (r) -> {
            if (r.hasException()) {
                logWarning(r.getException().getMessage());
                sendSelfPatch(task, TaskStage.FAILED, SubStage.SEARCH_EXISTING_CONTAINER);
            } else if (r.hasResult()) {
                foundOSVContainers.add(r.getResult());
                task.compilationURI = getFirstAddrplusPort(foundOSVContainers);
                sendSelfPatch(task, TaskStage.STARTED, SubStage.PUT_CONTAINER_URL);
            } else if (foundOSVContainers.isEmpty()) {
                sendSelfPatch(task, TaskStage.STARTED, SubStage.SEARCH_EXISTING_PLACEMENT);
            }
        });
    }

    // TO DO: create a better algorithm for searching through results
    private String getFirstAddrplusPort(List<ContainerState> containers) {
        return ("http://" + containers.get(0).address + ":"
                + containers.get(0).ports.get(0).containerPort).trim();
    }

    private void searchForExistingPlacement(UnikernelCreationTaskServiceState task) {
        logInfo("SEARCHING FOR PLACEMENT");

        QueryTask queryTask = QueryUtil.buildPropertyQuery(GroupResourcePlacementState.class);

        new ServiceDocumentQuery<>(getHost(), ContainerState.class).query(queryTask, (r) -> {
            if (r.hasException()) {
                sendSelfPatch(task, TaskStage.FAILED, SubStage.SEARCH_EXISTING_PLACEMENT);
            } else if (r.getDocumentSelfLink() != null) {
                task.groupResourcePlacementLink = r.getDocumentSelfLink();
                sendSelfPatch(task, TaskStage.STARTED, SubStage.PROVISION_CONTAINER);
            } else {
                if (task.groupResourcePlacementLink == null) {
                    sendSelfPatch(task, TaskStage.FAILED, SubStage.SEARCH_EXISTING_PLACEMENT);
                }
            }
        });
    }

    private void provisionContainer(UnikernelCreationTaskServiceState task) {
        logInfo("PROVISIONING CONTAINER");
        postContainerDescription(task);
    }

    private void postContainerDescription(UnikernelCreationTaskServiceState task) {
        ContainerDescription containerDesc = new ContainerDescription();
        Map<String, String> customProperties = new HashMap<String, String>();
        customProperties.put(OSvInformation.propertyTag, OSvInformation.propertyValue);
        containerDesc.privileged = true;
        containerDesc.customProperties = customProperties;

        containerDesc.name = OSvInformation.name;
        containerDesc.image = OSvInformation.repo;
        containerDesc.publishAll = true;

        Operation request = Operation
                .createPost(getHost(), ContainerDescriptionFactoryService.SELF_LINK)
                .setReferer(getSelfLink())
                .setBody(containerDesc)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        sendSelfPatch(task, TaskStage.FAILED, SubStage.PROVISION_CONTAINER);
                    } else {
                        doProvisionContainer(task, o.getBody(ContainerDescription.class));
                    }
                });

        sendRequest(request);
    }

    private void doProvisionContainer(UnikernelCreationTaskServiceState task,
            ContainerDescription containerDesc) {
        ContainerAllocationTaskState allocationTask = new ContainerAllocationTaskState();
        allocationTask.documentSelfLink = getSelfId();
        allocationTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.FIND_CONTAINER,
                TaskStage.FAILED, SubStage.PROVISION_CONTAINER);

        allocationTask.resourceDescriptionLink = containerDesc.documentSelfLink;
        allocationTask.groupResourcePlacementLink = task.groupResourcePlacementLink;
        allocationTask.resourceType = ResourceType.CONTAINER_TYPE.getName();
        allocationTask.resourceCount = 1L;

        Operation request = Operation
                .createPost(getHost(), ContainerAllocationTaskFactoryService.SELF_LINK)
                .setReferer(getSelfLink())
                .setBody(allocationTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        sendSelfPatch(task, TaskStage.FAILED, SubStage.PROVISION_CONTAINER);
                    } else {
                        logInfo("CONTAINER PROVISIONING");
                    }
                });

        sendRequest(request);
    }

    private void putContainerURL(UnikernelCreationTaskServiceState task) {
        Operation put = Operation
                .createPut(getHost(), UnikernelManagementURIParts.DOWNLOAD)
                .setReferer(getUri())
                .setBody(task.compilationURI + UnikernelManagementURIParts.DOWNLOAD_EXTERNAL);

        sendRequest(put);

        sendSelfPatch(task, TaskStage.STARTED, SubStage.CREATE_UNIKERNEL);
    }

    private void createUnikernel(UnikernelCreationTaskServiceState task, int retriesLeft) {
        logInfo("CREATING UNIKERNEL");
        // No need for self patch here cause the OSv-container responds on final with a Patch
        CompilationData forwardedData = task.data;
        forwardedData.successCB = getHost().getUri().toString()
                + UnikernelManagementURIParts.SUCCESS_CB;
        forwardedData.failureCB = getHost().getUri().toString()
                + UnikernelManagementURIParts.FAILURE_CB;

        URI requestUri = UriUtils
                .buildUri(task.compilationURI + UnikernelManagementURIParts.COMPILATION_EXTERNAL);
        Operation request = Operation
                .createPost(requestUri)
                .setReferer(getUri())
                .setBody(forwardedData)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(
                                "Retrying [%s] . Error: [%s]",
                                retriesLeft, e.getMessage());
                        if (retriesLeft <= 1) {
                            logWarning("UNIKERNEL CREATION FAILED");
                            sendSelfPatch(task, TaskStage.FAILED, SubStage.CREATE_UNIKERNEL);
                        } else {
                            getHost().schedule(() -> {
                                createUnikernel(task, retriesLeft - 1);
                            }, UNIKERNEL_CREATION_RETRY_WAIT, TimeUnit.SECONDS);

                        }
                    }
                });

        sendRequest(request);
    }

    private void handleCallback(UnikernelCreationTaskServiceState task) {
        logInfo("HANDLING CALLBACK");
        // No need for self patch here cause the downloadService responds with a final Patch
        Operation request = Operation.createPost(this, UnikernelManagementURIParts.DOWNLOAD)
                .setReferer(getSelfLink())
                .setBody(task.data.downloadLink);

        sendRequest(request);
    }

    private void sendSelfPatch(UnikernelCreationTaskServiceState task, TaskStage stage,
            SubStage subStage) {
        if (task.taskInfo == null) {
            task.taskInfo = new TaskState();
        }
        task.taskInfo.stage = stage;
        task.taskSubStage = subStage;
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
