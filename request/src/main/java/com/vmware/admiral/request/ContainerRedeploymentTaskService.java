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

package com.vmware.admiral.request;

import static com.vmware.admiral.request.utils.RequestUtils.CONTAINER_REDEPLOYMENT_CUSTOM_PROP;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption
        .SINGLE_ASSIGNMENT;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.StringUtils;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.admiral.request.ContainerRedeploymentTaskService.ContainerRedeploymentTaskState
        .SubStage;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription
        .ComputeType;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

/**
 * Task implementing the redeploying container request resource work flow.
 */
public class ContainerRedeploymentTaskService
        extends
        AbstractTaskStatefulService<ContainerRedeploymentTaskService
                .ContainerRedeploymentTaskState, ContainerRedeploymentTaskService
                .ContainerRedeploymentTaskState.SubStage> {
    public static final String FACTORY_LINK = ManagementUriParts
            .REQUEST_CONTAINER_REDEPLOYMENT_TASKS;

    public static final String DISPLAY_NAME = "Container Redeployment";

    public static class ContainerRedeploymentTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerRedeploymentTaskState
                    .SubStage> {

        /**
         * The container description link.
         */
        @PropertyOptions(usage = {SINGLE_ASSIGNMENT, REQUIRED}, indexing = STORE_ONLY)
        public String containerDescriptionLink;
        /**
         * The container state links to be redeployed.
         */
        @PropertyOptions(usage = {SINGLE_ASSIGNMENT, REQUIRED}, indexing = STORE_ONLY)
        public Set<String> containerStateLinks;
        /**
         * The desired containers in cluster
         */
        @PropertyOptions(usage = {SINGLE_ASSIGNMENT, REQUIRED}, indexing = STORE_ONLY)
        public int desiredClusterSize;
        /**
         * The context_id in which the container to be redeployed
         */
        @PropertyOptions(usage = {SINGLE_ASSIGNMENT, REQUIRED}, indexing = STORE_ONLY)
        public String contextId;

        public enum SubStage {
            CREATED, REMOVE, CLUSTER, COMPLETED, ERROR;
        }
    }

    public ContainerRedeploymentTaskService() {
        super(ContainerRedeploymentTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(ContainerRedeploymentTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            logFine("Start redeploying containers.");
            proceedTo(SubStage.REMOVE);
            break;
        case REMOVE:
            removeResources(state);
            break;
        case CLUSTER:
            sendClusterRequest(state);
            break;
        case COMPLETED:
            completeTask(state);
            break;
        case ERROR:
            completeTaskWithError(state);
            break;
        default:
            break;
        }
    }

    @Override
    protected void validateStateOnStart(ContainerRedeploymentTaskState state) {
        if (state.containerDescriptionLink == null || state.containerDescriptionLink.isEmpty()) {
            throw new LocalizableValidationException("'containerDescriptionLink' must not be empty",
                    "request.container.redeployment.containerDescriptionLink.empty");
        }

        if (state.containerStateLinks == null || state.containerStateLinks.isEmpty()) {
            throw new LocalizableValidationException("'containerStateLinks' must not be empty",
                    "request.container.redeployment.containerStateLinks.empty");
        }

        if (state.contextId == null || state.contextId.isEmpty()) {
            throw new LocalizableValidationException("'contextId' must not be empty",
                    "request.container.redeployment.contextId.empty");
        }
    }

    private void completeTask(ContainerRedeploymentTaskState state) {
        String redeployedContainers = StringUtils.join(state.containerStateLinks, ", ");
        String description = String.format("Containers [%s] redeployed",
                redeployedContainers);

        sendEventLog(state.tenantLinks, description, (op, ex) -> {
            if (ex != null) {
                op.fail(new LocalizableValidationException("Failed to publish an event log",
                        "request.container.redeployment.event-log.create-fail"));
            }

            complete();
        });
    }

    private void completeTaskWithError(ContainerRedeploymentTaskState state) {
        String redeployedContainers = StringUtils.join(state.containerStateLinks, ", ");
        // TODO: get only the id, not whole self link
        String description = String.format("Containers [%s] failed to redeploy",
                redeployedContainers);

        sendEventLog(state.tenantLinks, description, (op, ex) -> {
            if (ex != null) {
                op.fail(new LocalizableValidationException("Failed to publish an event log",
                        "request.container.redeployment.event-log.create-fail"));
            }

            completeWithError();
        });
    }

    private void removeResources(ContainerRedeploymentTaskState state) {
        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.resourceType = ComputeType.DOCKER_CONTAINER.name().toString();
        requestBrokerState.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
        requestBrokerState.tenantLinks = state.tenantLinks;
        requestBrokerState.resourceDescriptionLink = state.containerDescriptionLink;
        requestBrokerState.resourceLinks = state.containerStateLinks;
        requestBrokerState.requestTrackerLink = state.requestTrackerLink;
        requestBrokerState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.CLUSTER, TaskStage.FAILED,
                SubStage.ERROR);
        requestBrokerState.addCustomProperty(FIELD_NAME_CONTEXT_ID_KEY, state.contextId);
        requestBrokerState.addCustomProperty(CONTAINER_REDEPLOYMENT_CUSTOM_PROP, "container_redeployment");

        sendRequest(Operation.createPost(this, RequestBrokerFactoryService.SELF_LINK)
                .setBody(requestBrokerState).setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(Utils.toString(e));
                        return;
                    }
                }));
    }

    private void sendClusterRequest(ContainerRedeploymentTaskState state) {
        String cdLink = state.containerDescriptionLink;
        String contextId = state.contextId;
        int clusterSize = state.desiredClusterSize;

        logFine("Cluster container with %s description link, from %s context_id with cluster " +
                        "size: %s",
                cdLink, contextId, clusterSize);

        RequestBrokerState rbState = new RequestBrokerState();
        rbState.resourceDescriptionLink = cdLink;
        rbState.resourceCount = clusterSize;
        rbState.customProperties = new HashMap<>();
        rbState.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        rbState.resourceType = ComputeType.DOCKER_CONTAINER.name().toString();
        rbState.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        rbState.tenantLinks = state.tenantLinks;
        rbState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.COMPLETED, TaskStage.STARTED, SubStage.ERROR);

        sendRequest(Operation.createPost(this, RequestBrokerFactoryService.SELF_LINK)
                .setBody(rbState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(Utils.toString(e));
                        return;
                    }
                }));
    }

    private void sendEventLog(List<String> tenantLinks, String description, BiConsumer<Operation,
            Throwable> callback) {
        EventLogState eventLog = new EventLogState();
        eventLog.tenantLinks = tenantLinks;
        eventLog.resourceType = getClass().getName();
        eventLog.eventLogType = EventLogType.INFO;
        eventLog.description = description;

        sendRequest(Operation.createPost(getHost(), EventLogService.FACTORY_LINK)
                .setBody(eventLog)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        callback.accept(op, ex);
                    }

                    callback.accept(op, null);
                }));
    }
}
