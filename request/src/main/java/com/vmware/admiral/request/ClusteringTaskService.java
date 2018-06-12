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

import static com.vmware.admiral.compute.container.SystemContainerDescriptions.isSystemContainer;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_ALLOCATION_REQUEST;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.ClusteringTaskService.ClusteringTaskState.SubStage;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Task for clusterization of resources: handles both increase and decrease of the number of the
 * resources in a cluster.
 */
public class ClusteringTaskService extends
        AbstractTaskStatefulService<ClusteringTaskService.ClusteringTaskState, ClusteringTaskService.ClusteringTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Resource Clustering";

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_RESOURCE_CLUSTERING_TASK;

    public static class ClusteringTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ClusteringTaskState.SubStage> {

        public enum SubStage {
            CREATED,
            CLUSTERING,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(CLUSTERING));
        }

        public String contextId;

        /** The description that defines the requested resource. */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        /** (Required) Type of resource to create. */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public String resourceType;

        /** (Required) Number of resources to provision. */
        public long resourceCount;

        /** Indicating that it is in the second phase after allocation */
        public boolean postAllocation;

        // Service use fields:
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;
    }

    public ClusteringTaskService() {
        super(ClusteringTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected Collection<String> getRelatedResourcesLinks(ClusteringTaskState state) {
        return state.resourceLinks;
    }

    @Override
    protected Class<? extends ResourceState> getRelatedResourceStateType(ClusteringTaskState state) {
        return getStateClass(state.resourceType);
    }

    @Override
    protected BaseExtensibilityCallbackResponse notificationPayload(ClusteringTaskState state) {
        return new BaseExtensibilityCallbackResponse();
    }

    @Override
    protected void validateStateOnStart(ClusteringTaskState state)
            throws IllegalArgumentException {
        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.", "request.resource-count.zero");
        }
    }

    @Override
    protected void handleStartedStagePatch(ClusteringTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            provisionOrRemoveResources(state, null);
            break;
        case CLUSTERING:
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

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ClusteringTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated resources.");
        }
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    private void provisionOrRemoveResources(ClusteringTaskState state,
            Set<ResourceState> resourcesStates) {

        if (resourcesStates == null) {
            retrieveResources(state,
                    (resources) -> this.provisionOrRemoveResources(state, resources));
            return;
        }

        if (!validate(resourcesStates)) {
            return;
        }

        List<ResourceState> sortedResources = sortResourcesByImportance(resourcesStates);

        int desiredResourceCount = (int) state.resourceCount;
        int resourcesToAdd = 0;

        /*
         * Split the sorted list into two based on the desired resources count. The right hand side
         * consists of resources to be deleted. The remaining resources can be further examined to
         * check if they need to be "redeployed".
         */
        List<ResourceState> resourcesToRemove = Collections.emptyList();
        if (desiredResourceCount > sortedResources.size()) {
            resourcesToAdd = desiredResourceCount - sortedResources.size();
        } else if (desiredResourceCount == sortedResources.size()) {
            proceedTo(SubStage.COMPLETED);
            return;
        } else {
            resourcesToRemove = sortedResources
                    .subList(desiredResourceCount, sortedResources.size());
        }

        String groupResourcePlacementLink = null;
        if (sortedResources.size() >= 1) {
            groupResourcePlacementLink = getGroupResourcePlacementLink(sortedResources.get(0));
        }
        if (resourcesToAdd >= 1) {
            createAdditionalResources(state, state.resourceDescriptionLink,
                    groupResourcePlacementLink, resourcesToAdd);
        } else {
            removeResources(state, state.resourceDescriptionLink, resourcesToRemove);
        }
    }

    private String getGroupResourcePlacementLink(ResourceState r) {
        if (r instanceof ContainerState) {
            return ((ContainerState) r).groupResourcePlacementLink;
        } else if (r instanceof ComputeState) {
            ComputeState c = (ComputeState) r;
            return c.customProperties != null
                    ? c.customProperties.get(ComputeConstants.GROUP_RESOURCE_PLACEMENT_LINK_NAME)
                    : null;
        }
        return null;
    }

    private boolean validate(Set<ResourceState> resourcesStates) {
        if (resourcesStates.stream()
                .filter(r -> r instanceof ContainerState)
                .map(r -> ((ContainerState) r))
                .anyMatch(c -> isSystemContainer(c))) {
            failTask(null, new LocalizableValidationException(
                    "Day2 operations are not supported for system container",
                    "request.system.container.day2"));
            return false;
        }
        return true;
    }

    /**
     * "Sort" the resources in a list by their status from most important to least important. The
     * idea is to easily identify the resources to be deleted if we are to decrease the resource
     * count
     */
    private List<ResourceState> sortResourcesByImportance(
            Set<ResourceState> resourcesStates) {

        List<ResourceState> sortedResources = resourcesStates.stream()
                .sorted((l, r) -> powerState(l) - powerState(r))
                .collect(Collectors.toList());

        return sortedResources;
    }

    private void createAdditionalResources(ClusteringTaskState state, String descLink,
            String groupResourcePlacementLink, int resourcesToAdd) {

        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.resourceCount = resourcesToAdd;
        requestBrokerState.resourceDescriptionLink = descLink;
        requestBrokerState.resourceType = state.resourceType;
        requestBrokerState.operation = RequestBrokerState.PROVISION_RESOURCE_OPERATION;
        requestBrokerState.groupResourcePlacementLink = groupResourcePlacementLink;
        requestBrokerState.tenantLinks = state.tenantLinks;
        requestBrokerState.requestTrackerLink = state.requestTrackerLink;
        requestBrokerState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskState.TaskStage.STARTED, SubStage.COMPLETED, TaskState.TaskStage.FAILED,
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
                        logSevere(e);
                        return;
                    }
                    proceedTo(SubStage.CLUSTERING);
                }));
    }

    private void removeResources(ClusteringTaskState state,
            String descLink,
            List<ResourceState> resourcesToRemove) {

        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.resourceType = state.resourceType;
        requestBrokerState.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
        requestBrokerState.tenantLinks = state.tenantLinks;
        requestBrokerState.resourceDescriptionLink = descLink;
        requestBrokerState.resourceLinks = resourcesToRemove.stream().map(c -> c.documentSelfLink)
                .collect(Collectors.toSet());
        requestBrokerState.requestTrackerLink = state.requestTrackerLink;
        requestBrokerState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskState.TaskStage.STARTED, SubStage.COMPLETED, TaskState.TaskStage.FAILED,
                SubStage.ERROR);
        requestBrokerState.addCustomProperty(FIELD_NAME_CONTEXT_ID_KEY, state.contextId);
        requestBrokerState.addCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP,
                Boolean.TRUE.toString());

        sendRequest(Operation.createPost(this, RequestBrokerFactoryService.SELF_LINK)
                .setBody(requestBrokerState).setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(e);
                        return;
                    }
                    proceedTo(SubStage.CLUSTERING);
                }));
    }

    /** Retrieves all compositions (group of templates) and their resources by provided type. */
    private void retrieveResources(ClusteringTaskState state,
            Consumer<Set<ResourceState>> callbackFunction) {
        try {
            Class<? extends ResourceState> stateClass = getStateClass(state.resourceType);
            ServiceDocumentQuery<? extends ResourceState> query = new ServiceDocumentQuery<>(
                    getHost(), stateClass);

            QueryTask queryTask = QueryUtil.buildPropertyQuery(stateClass,
                    ContainerState.FIELD_NAME_DESCRIPTION_LINK, state.resourceDescriptionLink);

            if (state.contextId != null) {
                queryTask.querySpec.query.addBooleanClause(new QueryTask.Query()
                        .setTermPropertyName(getCompositeComponentLinkFieldName(stateClass))
                        .setTermMatchValue(getCompositeComponentId(stateClass, state.contextId)));
            }

            queryTask.querySpec.options = EnumSet.of(QueryOption.EXPAND_CONTENT);

            Set<ResourceState> resourcesStates = new HashSet<>();
            query.query(queryTask, (r) -> {
                if (r.hasException()) {
                    logWarning("Exception during retrieving of resources. Error: [%s]",
                            Utils.toString(r.getException()));
                } else if (r.hasResult()) {
                    resourcesStates.add(r.getResult());
                } else {
                    callbackFunction.accept(resourcesStates);
                }
            });

        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }

    }

    private String getCompositeComponentId(Class<? extends ResourceState> stateClass,
            String contextId) {
        if (ContainerState.class.isAssignableFrom(stateClass)) {
            return contextId;
        }
        return UriUtils.buildUriPath(CompositeComponentFactoryService.SELF_LINK, contextId);
    }

    private String getCompositeComponentLinkFieldName(Class<? extends ResourceState> stateClass) {
        if (ComputeState.class.isAssignableFrom(stateClass)) {
            return QuerySpecification.buildCompositeFieldName(
                    ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                    ComputeConstants.FIELD_NAME_COMPOSITE_COMPONENT_LINK_KEY);
        } else if (ContainerState.class.isAssignableFrom(stateClass)) {
            return QuerySpecification.buildCompositeFieldName(
                    ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                    RequestUtils.FIELD_NAME_CONTEXT_ID_KEY);
        }
        return ContainerState.FIELD_NAME_COMPOSITE_COMPONENT_LINK;
    }

    private Class<? extends ResourceState> getStateClass(String resourceType) {
        ComponentMeta meta = CompositeComponentRegistry.metaByType(resourceType);
        if (ComputeState.class.isAssignableFrom(meta.stateClass)) {
            return ComputeState.class;
        }
        return meta.stateClass;
    }

    private static int powerState(ResourceState r) {
        if (r instanceof ContainerState) {
            return ((ContainerState) r).powerState.ordinal();
        } else if (r instanceof ComputeState) {
            return ((ComputeState) r).powerState.ordinal();
        }

        return 0;
    }
}
