/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.PropertyUtils.mergeLists;
import static com.vmware.admiral.compute.container.SystemContainerDescriptions.isSystemContainer;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_ALLOCATION_REQUEST;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.ContainerClusteringTaskService.ContainerClusteringTaskState.SubStage;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Task for clusterization of containers: handles both increase and decrease of the number of the
 * containers in a cluster.
 */
public class ContainerClusteringTaskService extends
        AbstractTaskStatefulService<ContainerClusteringTaskService.ContainerClusteringTaskState,
        ContainerClusteringTaskService.ContainerClusteringTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Container Clustering";

    public static class ContainerClusteringTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerClusteringTaskState.SubStage> {

        public enum SubStage {
            CREATED,
            CLUSTERING,
            ALLOCATED,
            UPDATED_CLUSTER_SIZE,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(CLUSTERING));
        }

        public String contextId;

        /** The description that defines the requested resource. */
        public String resourceDescriptionLink;

        /** (Required) Type of resource to create. */
        public String resourceType;

        /** (Required) Number of resources to provision. */
        public long resourceCount;

        /** Indicating that it is in the second phase after allocation */
        public boolean postAllocation;

        // Service use fields:

        /** Holder of Containers which will be added or removed. */
        public List<String> containers;

        public List<String> resourceLinks;

    }

    public ContainerClusteringTaskService() {
        super(ContainerClusteringTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(ContainerClusteringTaskState state)
            throws IllegalArgumentException {
        assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");
        assertNotEmpty(state.resourceType, "resourceType");

        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }
    }

    @Override
    protected void handleStartedStagePatch(ContainerClusteringTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            provisionOrRemoveContainers(state, null);
            break;
        case CLUSTERING:
            break;
        case ALLOCATED:
            complete(state, SubStage.COMPLETED);
            break;
        case ERROR:
            completeWithError(state, SubStage.ERROR);
            break;
        default:
            break;
        }
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ContainerClusteringTaskState patchBody, ContainerClusteringTaskState currentState) {
        currentState.resourceLinks = mergeLists(
                currentState.resourceLinks, patchBody.resourceLinks);
        return false;
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ContainerClusteringTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated resources.");
        }
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        List<String> resourceLinks;
    }

    private void provisionOrRemoveContainers(ContainerClusteringTaskState state,
            Set<ContainerState> containerStates) {

        if (containerStates == null) {
            retrieveContainers(state, (containers) -> this.provisionOrRemoveContainers(state, containers));
            return;
        }

        if (containerStates.stream().anyMatch(c -> isSystemContainer(c))) {
            failTask(null, new IllegalArgumentException(
                    "Day2 operations are not supported for system container"));
            return;
        }

        List<ContainerState> sortedContainers = sortContainersByImportance(containerStates);

        int desiredContainerCount = (int) state.resourceCount;
        int containersToAdd = 0;

        /*
         * Split the sorted list into two based on the desired container count. The right hand side
         * consists of containers to be deleted. The remaining containers can be further examined to
         * check if they need to be "redeployed".
         */
        List<ContainerState> containersToRemove;
        if (desiredContainerCount >= sortedContainers.size()) {
            containersToRemove = Collections.emptyList();
            containersToAdd = desiredContainerCount - sortedContainers.size();
        } else {
            containersToRemove = sortedContainers
                    .subList(desiredContainerCount, sortedContainers.size());
        }

        String groupResourcePlacementLink = null;
        if (sortedContainers.size() >= 1) {
            groupResourcePlacementLink = sortedContainers.get(0).groupResourcePlacementLink;
        }
        if (containersToAdd >= 1) {
            createAdditionalContainers(state, state.resourceDescriptionLink,
                    groupResourcePlacementLink,
                    sortedContainers,
                    containersToAdd);
        } else {
            removeContainers(state, state.resourceDescriptionLink, containersToRemove);
        }
    }

    /**
     * "Sort" the containers in a list by their status from most important to least important. The
     * idea is to easily identify the containers to be deleted if we are to decrease the container
     * count
     */
    private List<ContainerState> sortContainersByImportance(
            Set<ContainerState> containerStates) {

        List<ContainerState> sortedContainers = containerStates.stream()
                .sorted((l, r) -> l.powerState.ordinal() - r.powerState.ordinal())
                .collect(Collectors.toList());

        return sortedContainers;
    }

    private void createAdditionalContainers(ContainerClusteringTaskState state, String descLink,
            String groupResourcePlacementLink, List<ContainerState> existingContainers,
            int containersToAdd) {

        if (containersToAdd < 1) {
            return;
        }

        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.resourceCount = containersToAdd;
        requestBrokerState.resourceDescriptionLink = descLink;
        requestBrokerState.resourceType = state.resourceType;
        requestBrokerState.operation = RequestBrokerState.PROVISION_RESOURCE_OPERATION;
        requestBrokerState.groupResourcePlacementLink = groupResourcePlacementLink;
        requestBrokerState.tenantLinks = state.tenantLinks;
        requestBrokerState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskState.TaskStage.STARTED, SubStage.ALLOCATED, TaskState.TaskStage.FAILED,
                SubStage.ERROR);
        requestBrokerState.addCustomProperty(FIELD_NAME_CONTEXT_ID_KEY, state.contextId);
        requestBrokerState.addCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP,
                Boolean.TRUE.toString());
        String allocationRequest = state.getCustomProperty(FIELD_NAME_ALLOCATION_REQUEST);
        if (allocationRequest != null) {
            requestBrokerState.addCustomProperty(FIELD_NAME_ALLOCATION_REQUEST, allocationRequest);
        }

        sendRequest(Operation.createPost(this, RequestBrokerFactoryService.SELF_LINK)
                .setBody(requestBrokerState).setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(Utils.toString(e));
                        return;
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.CLUSTERING));
                }));
    }

    private void removeContainers(ContainerClusteringTaskState state,
            String descLink,
            List<ContainerState> containersToRemove) {
        if (containersToRemove.isEmpty()) {
            return;
        }
        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.resourceType = state.resourceType;
        requestBrokerState.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
        requestBrokerState.tenantLinks = state.tenantLinks;
        requestBrokerState.resourceDescriptionLink = descLink;
        requestBrokerState.resourceLinks = containersToRemove.stream().map(c -> c.documentSelfLink)
                .collect(Collectors.toList());
        requestBrokerState.requestTrackerLink = state.requestTrackerLink;
        requestBrokerState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskState.TaskStage.STARTED, SubStage.ALLOCATED, TaskState.TaskStage.FAILED,
                SubStage.ERROR);
        requestBrokerState.addCustomProperty(FIELD_NAME_CONTEXT_ID_KEY, state.contextId);
        requestBrokerState.addCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP,
                Boolean.TRUE.toString());

        sendRequest(Operation.createPost(this, RequestBrokerFactoryService.SELF_LINK)
                .setBody(requestBrokerState).setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(Utils.toString(e));
                        return;
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.CLUSTERING));
                }));
    }

    /** Retrieves all compositions (group of templates) and their containers by provided type. */
    private void retrieveContainers(ContainerClusteringTaskState state,
            Consumer<Set<ContainerState>> callbackFunction) {
        try {
            ServiceDocumentQuery<ContainerState> query = new ServiceDocumentQuery<>(getHost(),
                    ContainerState.class);

            QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerState.class,
                    ContainerState.FIELD_NAME_DESCRIPTION_LINK, state.resourceDescriptionLink);

            if (state.contextId != null) {
                queryTask.querySpec.query.addBooleanClause(new QueryTask.Query()
                        .setTermPropertyName(ContainerState.FIELD_NAME_COMPOSITE_COMPONENT_LINK)
                        .setTermMatchValue(UriUtils.buildUriPath(
                                CompositeComponentFactoryService.SELF_LINK, state.contextId)));
            }

            queryTask.querySpec.options = EnumSet.of(QueryOption.EXPAND_CONTENT);

            Map<ContainerState, ContainerState> containerStates = new ConcurrentHashMap<>();
            query.query(queryTask, (r) -> {
                if (r.hasException()) {
                    logWarning("Exception during retrieving of containers. Error: [%s]",
                            Utils.toString(r.getException()));
                } else if (r.hasResult()) {
                    containerStates.put(r.getResult(), r.getResult());
                } else {
                    callbackFunction.accept(containerStates.keySet());
                }
            });

        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }

    }
}
