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
import static com.vmware.admiral.common.util.AssertUtil.assertTrue;
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_ALLOCATION_REQUEST;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService.ContainerHostDataCollectionState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.content.ServiceLinkSerializer;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState.SubStage;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.ResourceNamePrefixTaskService.ResourceNamePrefixTaskState;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Task implementing the provision container request resource work flow.
 */
public class ContainerAllocationTaskService
        extends
        AbstractTaskStatefulService<ContainerAllocationTaskService.ContainerAllocationTaskState, ContainerAllocationTaskService.ContainerAllocationTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Container Allocation";

    // cached container description
    private volatile ContainerDescription containerDescription;

    public static class ContainerAllocationTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerAllocationTaskState.SubStage> {

        /**
         * (Optional) Indicates that a given container linked to a ContainerDescription depends on
         * the allocation of another container depending on another ContainerDescription with the
         * same pod property.
         */
        public static final String FIELD_NAME_CONTEXT_POD_DEPENDENT = "__composition_depend_pod";

        public static enum SubStage {
            CREATED,
            CONTEXT_PREPARED,
            RESOURCES_NAMED,
            RESOURCES_LINKS_BUILT,
            PLACEMENT_HOST_SELECTED,
            PROCESSING_SERVICE_LINKS,
            START_PROVISIONING,
            PROVISIONING,
            PROVISIONING_COMPLETED,
            PROCESSING_PUBLIC_SERVICE_ALIAS,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(PROVISIONING));
        }

        /** The description that defines the requested resource. */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        /** (Required) Type of resource to create. */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public String resourceType;

        /** (Required) the groupResourcePlacementState that links to ResourcePool */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public String groupResourcePlacementLink;

        /** (Required) Number of resources to provision. */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Long resourceCount;

        /** Set by a Task with the links of the provisioned resources. */
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        /** Indicating that it is in the second phase after allocation */
        public boolean postAllocation;

        // Service use fields:

        /** (Internal) Set by task after resource name prefixes requested. */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceNames;

        /** (Internal) Set by task after the ComputeState is found to host the containers */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public List<HostSelection> hostSelections;

        /**
         * (Internal) Set by task after the resource names are determined and ComputeState is found
         * to host the containers.
         */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Map<String, HostSelection> resourceNameToHostSelection;

        /** (Internal) Set by task */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public URI instanceAdapterReference;

        /** (Internal) Set by task with ContainerDescription name. */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String descName;
    }

    public ContainerAllocationTaskService() {
        super(ContainerAllocationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ContainerAllocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            prepareContext(state, null);
            break;
        case CONTEXT_PREPARED:
            if (state.resourceNames == null || state.resourceNames.isEmpty()) {
                createResourcePrefixNameSelectionTask(state, null);
            } else {
                sendSelfPatch(createUpdateSubStageTask(state, SubStage.RESOURCES_NAMED));
            }
            break;
        case RESOURCES_NAMED:
            ContainerAllocationTaskState newState = createUpdateSubStageTask(state,
                    SubStage.RESOURCES_LINKS_BUILT);

            if (!state.postAllocation) {
                newState.resourceLinks = buildResourceLinks(state);
            }

            sendSelfPatch(newState);
            break;
        case RESOURCES_LINKS_BUILT:
            if (state.hostSelections == null || state.hostSelections.isEmpty()) {
                selectPlacementComputeHost(state, null);
            } else {
                // in specific cases when the host is pre-selected
                // (ex: installing agents directly to a host, this step is not needed)
                sendSelfPatch(createUpdateSubStageTask(state, SubStage.PLACEMENT_HOST_SELECTED));
            }
            break;
        case PLACEMENT_HOST_SELECTED:
            proceedAfterHostSelection(state);
            break;
        case START_PROVISIONING:
            provisionOrAllocateContainers(state, null, null);
            break;
        case PROVISIONING:
            break;
        case COMPLETED:
            completeAllocationTask(state);
            break;
        case ERROR:
            completeWithError(state, SubStage.ERROR);
            break;
        default:
            break;
        }
    }

    @Override
    protected void validateStateOnStart(ContainerAllocationTaskState state) {
        assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");
        assertNotEmpty(state.resourceType, "resourceType");
        if (state.postAllocation) {
            assertNotEmpty(state.resourceLinks, "resourceLinks");
            assertTrue(state.resourceCount <= state.resourceLinks.size(),
                    "Resource count must be equal to number of resources during post allocation.");
        } else {
            assertNotEmpty(state.groupResourcePlacementLink, "groupResourcePlacementLink");
        }

        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ContainerAllocationTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated resources.");
        }
        return finishedResponse;
    }

    @Override
    protected ServiceTaskCallbackResponse getFailedCallbackResponse(
            ContainerAllocationTaskState state) {
        CallbackCompleteResponse failedResponse = new CallbackCompleteResponse();
        failedResponse.copy(state.serviceTaskCallback.getFailedResponse(state.taskInfo.failure));
        failedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated resources.");
        }
        return failedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        final TaskStatusState statusTask = super.fromTask(state);
        if (SubStage.CONTEXT_PREPARED == state.taskSubStage) {
            statusTask.name = ((ContainerAllocationTaskState) state).descName;
        }

        return statusTask;
    }

    private void proceedAfterHostSelection(ContainerAllocationTaskState state) {
        if (!state.postAllocation && state.hostSelections == null && state.resourceNames == null) {
            failTask(null, new IllegalStateException(
                    "computeHostLink and resourceNames can't be null at this state"));
        } else if (state.postAllocation
                || (state.hostSelections != null && state.resourceNames != null)) {

            final Map<String, HostSelection> resourceNameToHostSelection = !state.postAllocation
                    ? selectHostPerResourceName(state.resourceNames, state.hostSelections) : null;
            if (state.instanceAdapterReference == null) {
                // reload container description if null
                getContainerDescription(state, (contDesc) -> {
                    ContainerAllocationTaskState body = createUpdateSubStageTask(state,
                            SubStage.START_PROVISIONING);
                    body.instanceAdapterReference = contDesc.instanceAdapterReference;
                    body.resourceNameToHostSelection = resourceNameToHostSelection;
                    body.customProperties = mergeCustomProperties(state.customProperties,
                            contDesc.customProperties);
                    sendSelfPatch(body);
                });
            } else {
                ContainerAllocationTaskState body = createUpdateSubStageTask(state,
                        SubStage.START_PROVISIONING);
                body.resourceNameToHostSelection = resourceNameToHostSelection;
                sendSelfPatch(body);
            }
        }
    }

    private void completeAllocationTask(ContainerAllocationTaskState state) {
        if (state.hostSelections != null) {
            try {
                ContainerHostDataCollectionState body = new ContainerHostDataCollectionState();
                body.computeContainerHostLinks = new HashSet<String>(
                        state.resourceNameToHostSelection.values()
                                .stream().map((r) -> r.hostLink)
                                .collect(Collectors.toList()));
                logInfo("Container Host collection started for: [%s]",
                        body.computeContainerHostLinks);
                sendRequest(Operation.createPatch(this,
                        ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK)
                        .setBody(body)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                logWarning("Container Host [%s] can't be updated. Error: [%s]",
                                        body.computeContainerHostLinks, Utils.toString(e));
                            }
                        }));
            } catch (Throwable e) {
                logSevere(e);
            }
        }

        complete(state, SubStage.COMPLETED);
    }

    private Set<String> buildResourceLinks(ContainerAllocationTaskState state) {
        logInfo("Generate provisioned resourceLinks");
        Set<String> resourceLinks = new HashSet<>(state.resourceNames.size());
        for (String resourceName : state.resourceNames) {
            String containerLink = buildResourceLink(resourceName);
            resourceLinks.add(containerLink);
        }
        return resourceLinks;
    }

    private String buildResourceLink(String resourceName) {
        return UriUtils.buildUriPath(ContainerFactoryService.SELF_LINK,
                buildResourceId(resourceName));
    }

    private void getContainerDescription(ContainerAllocationTaskState state,
            Consumer<ContainerDescription> callbackFunction) {
        if (containerDescription != null) {
            callbackFunction.accept(containerDescription);
            return;
        }
        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving description state", e);
                        return;
                    }

                    ContainerDescription desc = o.getBody(ContainerDescription.class);
                    this.containerDescription = desc;
                    callbackFunction.accept(desc);
                }));
    }

    private void prepareContext(ContainerAllocationTaskState state,
            ContainerDescription containerDesc) {
        if (state.postAllocation) {
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.PLACEMENT_HOST_SELECTED));
            return;
        }

        if (containerDesc == null) {
            getContainerDescription(state, (contDesc) -> this.prepareContext(state, contDesc));
            return;
        }

        ContainerAllocationTaskState body = createUpdateSubStageTask(state,
                SubStage.CONTEXT_PREPARED);
        // merge request/allocation properties over the container description properties
        body.customProperties = mergeCustomProperties(containerDesc.customProperties,
                state.customProperties);

        if (body.getCustomProperty(FIELD_NAME_CONTEXT_ID_KEY) == null) {
            body.addCustomProperty(FIELD_NAME_CONTEXT_ID_KEY, getSelfId());
        }
        body.descName = containerDesc.name;

        sendSelfPatch(body);
    }

    private void selectPlacementComputeHost(ContainerAllocationTaskState state,
            String resourcePoolLink) {
        if (!state.postAllocation && state.resourceNames == null || state.resourceNames.isEmpty()) {
            failTask(null, new IllegalStateException("resource names expected at this stage."));
            return;
        }
        if (resourcePoolLink == null) {
            getResourcePool(state,
                    (rsPoolLink) -> selectPlacementComputeHost(state, rsPoolLink));
            return;
        }

        // create placement selection tasks
        PlacementHostSelectionTaskState placementTask = new PlacementHostSelectionTaskState();
        placementTask.documentSelfLink = getSelfId();
        placementTask.resourceDescriptionLink = state.resourceDescriptionLink;
        placementTask.resourcePoolLinks = new ArrayList<>();
        placementTask.resourcePoolLinks.add(resourcePoolLink);
        placementTask.resourceCount = state.resourceCount;
        placementTask.resourceType = state.resourceType;
        placementTask.tenantLinks = state.tenantLinks;
        placementTask.customProperties = state.customProperties;
        placementTask.contextId = getContextId(state);
        placementTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.PLACEMENT_HOST_SELECTED,
                TaskStage.STARTED, SubStage.ERROR);
        placementTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, PlacementHostSelectionTaskService.FACTORY_LINK)
                .setBody(placementTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating placement task", e);
                        return;
                    }
                }));
    }

    private void getResourcePool(ContainerAllocationTaskState state,
            Consumer<String> callbackFunction) {
        sendRequest(Operation.createGet(this, state.groupResourcePlacementLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving GroupResourcePlacement", e);
                        return;
                    }

                    GroupResourcePlacementState placementState = o
                            .getBody(GroupResourcePlacementState.class);
                    if (placementState.resourcePoolLink == null) {
                        failTask(null, new IllegalStateException(
                                "Placement state has no resourcePoolLink"));
                        return;
                    }
                    callbackFunction.accept(placementState.resourcePoolLink);
                }));
    }

    private void getResourcePlacementState(ContainerAllocationTaskState state,
            Consumer<GroupResourcePlacementState> callbackFunction) {
        sendRequest(Operation.createGet(this, state.groupResourcePlacementLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving GroupResourcePlacement", e);
                        return;
                    }

                    GroupResourcePlacementState placementState = o
                            .getBody(GroupResourcePlacementState.class);

                    callbackFunction.accept(placementState);
                }));
    }

    private void createResourcePrefixNameSelectionTask(ContainerAllocationTaskState state,
            ContainerDescription containerDescription) {
        if (containerDescription == null) {
            getContainerDescription(state,
                    (contDesc) -> this.createResourcePrefixNameSelectionTask(state, contDesc));
            return;
        }
        // create resource prefix name selection tasks
        ResourceNamePrefixTaskState namePrefixTask = new ResourceNamePrefixTaskState();
        namePrefixTask.documentSelfLink = getSelfId();
        namePrefixTask.resourceCount = state.resourceCount;
        namePrefixTask.baseResourceNameFormat = ResourceNamePrefixService
                .getDefaultResourceNameFormat(containerDescription.name);
        namePrefixTask.tenantLinks = state.tenantLinks;

        namePrefixTask.customProperties = state.customProperties;
        namePrefixTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.RESOURCES_NAMED,
                TaskStage.STARTED, SubStage.ERROR);
        namePrefixTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ResourceNamePrefixTaskService.FACTORY_LINK)
                .setBody(namePrefixTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating resource name prefix task", e);
                        return;
                    }
                }));
    }

    private void provisionOrAllocateContainers(ContainerAllocationTaskState state,
            ContainerDescription containerDesc, ServiceTaskCallback taskCallback) {
        final boolean allocationRequest = isAllocationRequest(state);

        if (taskCallback == null) {
            // create a counter subtask link first
            createCounterSubTaskCallback(state, state.resourceCount, !allocationRequest,
                    SubStage.COMPLETED,
                    (serviceTask) -> provisionOrAllocateContainers(state,
                            this.containerDescription, serviceTask));
            return;
        }

        if (containerDesc == null) {
            if (this.containerDescription == null) {
                getContainerDescription(state, (contDesc) -> {
                    provisionOrAllocateContainers(state, contDesc, taskCallback);
                });
            } else {
                containerDesc = this.containerDescription;
            }
        }

        if (allocationRequest) {
            logInfo("Allocation request for %s containers", state.resourceCount);
        } else if (state.postAllocation) {
            logInfo("Post-allocation request for %s containers", state.resourceCount);
        } else {
            logInfo("Provisioning request for %s containers", state.resourceCount);
        }

        if (state.postAllocation) {
            for (String resourceLink : state.resourceLinks) {
                createContainerInstanceRequests(state, taskCallback, resourceLink);
            }
        } else {
            for (String resourceName : state.resourceNames) {
                createContainerState(state, containerDesc, resourceName,
                        allocationRequest, null,
                        state.resourceNameToHostSelection.get(resourceName), taskCallback);
            }
        }

        sendSelfPatch(createUpdateSubStageTask(state, SubStage.PROVISIONING));
    }

    private boolean isAllocationRequest(ContainerAllocationTaskState state) {
        return !state.postAllocation && (state.customProperties != null
                && Boolean.parseBoolean(state.customProperties.get(FIELD_NAME_ALLOCATION_REQUEST)));
    }

    private String buildResourceId(String resourceName) {
        return resourceName.replaceAll(" ", "-");
    }

    private Map<String, HostSelection> selectHostPerResourceName(Collection<String> resourceNames,
            Collection<HostSelection> hostSelections) {
        AssertUtil.assertTrue(resourceNames.size() <= hostSelections.size(),
                "There should be a selected host for each resource");

        Map<String, HostSelection> resourceNameToHostSelection = new HashMap<String, HostSelection>();
        Iterator<String> rnIterator = resourceNames.iterator();
        Iterator<HostSelection> hsIterator = hostSelections.iterator();
        while (rnIterator.hasNext() && hsIterator.hasNext()) {
            resourceNameToHostSelection.put(rnIterator.next(), hsIterator.next());
        }

        return resourceNameToHostSelection;
    }

    private void createContainerState(ContainerAllocationTaskState state,
            ContainerDescription containerDesc,
            String resourceName, boolean allocationRequest,
            GroupResourcePlacementState groupResourcePlacementState, HostSelection hostSelection,
            ServiceTaskCallback taskCallback) {
        try {

            if (groupResourcePlacementState == null) {
                getResourcePlacementState(
                        state,
                        (resourcePlacementState) -> createContainerState(state, containerDesc,
                                resourceName, allocationRequest, resourcePlacementState,
                                hostSelection, taskCallback));
                return;
            }

            final ContainerState containerState = new ContainerState();
            containerState.documentSelfLink = buildResourceId(resourceName);
            containerState.names = new ArrayList<>();
            containerState.names.add(resourceName);
            containerState.tenantLinks = state.tenantLinks;
            containerState.descriptionLink = state.resourceDescriptionLink;
            containerState.groupResourcePlacementLink = state.groupResourcePlacementLink;
            containerState.parentLink = hostSelection.hostLink;
            containerState.powerState = PowerState.PROVISIONING;
            containerState.status = ContainerState.CONTAINER_ALLOCATION_STATUS;
            containerState.adapterManagementReference = state.instanceAdapterReference;
            containerState.customProperties = state.customProperties;
            containerState.image = containerDesc.image;
            containerState.command = containerDesc.command;
            containerState.volumesFrom = hostSelection.mapNames(containerDesc.volumesFrom);
            containerState.volumeDriver = containerDesc.volumeDriver;
            containerState.volumes = mapVolumes(containerDesc, hostSelection);
            containerState.networks = mapNetworks(containerDesc, hostSelection);

            if (containerState.networks != null && !containerState.networks.isEmpty()) {
                // use links in user defined networks. No need to map to specific containers,
                // but to network aliases same for all containers in the service/cluster
                for (String snKey : containerState.networks.keySet()) {
                    if (containerDesc.links != null) {
                        ServiceNetwork sn = containerState.networks.get(snKey);
                        if (sn == null) {
                            sn = new ServiceNetwork();
                            containerState.networks.put(snKey, sn);
                        }

                        if (sn.links == null) {
                            sn.links = containerDesc.links;
                        } else {
                            sn.links = Stream
                                    .concat(Arrays.stream(sn.links),
                                            Arrays.stream(containerDesc.links))
                                    .distinct()
                                    .toArray(String[]::new);
                        }
                    }
                }
            } else {
                // Fallback to legacy links mapped to specific containers
                String[] mapLinks = mapLinks(containerDesc, hostSelection);
                containerState.links = mapLinks;
            }

            containerState.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();

            containerState.memoryLimit = getMinParam(groupResourcePlacementState.memoryLimit,
                    containerDesc.memoryLimit);

            Long cpuShares = getMinParam(groupResourcePlacementState.cpuShares,
                    containerDesc.cpuShares);
            containerState.cpuShares = cpuShares != null ? cpuShares.intValue() : null;

            containerState.extraHosts = containerDesc.extraHosts;
            containerState.env = containerDesc.env;

            String contextId;
            if (state.customProperties != null && (contextId = state.customProperties
                    .get(FIELD_NAME_CONTEXT_ID_KEY)) != null) {
                containerState.compositeComponentLink = UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, contextId);
            }

            sendRequest(OperationUtil
                    .createForcedPost(this, ContainerFactoryService.SELF_LINK)
                    .setBody(containerState)
                    .setCompletion(
                            (o, e) -> {
                                if (e != null) {
                                    completeSubTasksCounter(taskCallback, e);
                                    return;
                                }
                                ContainerState body = o.getBody(ContainerState.class);
                                logInfo("Created ContainerState: %s ", body.documentSelfLink);

                                if (allocationRequest) {
                                    completeSubTasksCounter(taskCallback, null);
                                } else {
                                    createContainerInstanceRequests(state, taskCallback,
                                            body.documentSelfLink);
                                }
                            }));

        } catch (Throwable e) {
            failTask("System failure creating ContainerStates", e);
        }
    }

    private void createContainerInstanceRequests(ContainerAllocationTaskState state,
            ServiceTaskCallback taskCallback, String containerSelfLink) {
        AdapterRequest adapterRequest = new AdapterRequest();
        adapterRequest.resourceReference = UriUtils.buildUri(getHost(), containerSelfLink);
        adapterRequest.serviceTaskCallback = taskCallback;
        adapterRequest.operationTypeId = ContainerOperationType.CREATE.id;
        adapterRequest.customProperties = state.customProperties;

        sendRequest(Operation.createPatch(getHost(), state.instanceAdapterReference.toString())
                .setBody(adapterRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("AdapterRequest failed for container: " + containerSelfLink, e);
                        return;
                    }
                    logInfo("Container provisioning started for: " + containerSelfLink);
                }));
    }

    /**
     * Returns the minimum. Handles nulls and treats 0 as no limit
     */
    public static Long getMinParam(long placementLimit, Number descLimit) {
        // get the defined one. placementLimit will probably be changed to Long
        if (descLimit == null) {
            return placementLimit;
        } else if (placementLimit == 0) { // 0 means no limit, so get the other one
            return descLimit.longValue();
        } else if (descLimit.longValue() == 0) {
            return placementLimit;
        } else {
            return Math.min(placementLimit, descLimit.longValue());
        }
    }

    private static Map<String, ServiceNetwork> mapNetworks(ContainerDescription cd,
            HostSelection hostSelection) {
        if (cd.networks == null) {
            return null;
        }

        Map<String, ServiceNetwork> result = new HashMap<>();

        for (Entry<String, ServiceNetwork> entry : cd.networks.entrySet()) {
            String resolvedNetworkName = hostSelection.mapNames(new String[] { entry.getKey() })[0];

            ServiceNetwork config = entry.getValue();
            if (config == null) {
                config = new ServiceNetwork();
            }

            ServiceNetwork newConfig = new ServiceNetwork();
            newConfig.ipv4_address = config.ipv4_address;
            newConfig.ipv6_address = config.ipv6_address;

            List<String> newAliases = new ArrayList<>();
            if (config.aliases != null) {
                for (String alias : config.aliases) {
                    newAliases.add(alias);
                }
            }
            newAliases.add(cd.name);

            newConfig.aliases = newAliases.toArray(new String[0]);

            result.put(resolvedNetworkName, newConfig);
        }

        return result;
    }

    /**
     * Takes volumes from ContainerDescription in format [/host-directory:/container-directory] or
     * [namedVolume:/container-directory] and puts the suffix for host part of the volume name.
     *
     * @param cd
     *            - ContainerDescription
     * @param hostSelection
     *            - HostSelection for resource.
     * @return new volume name equals to old one, but with suffix for host directory like:
     *         [namedVolume-mcm376:/container-directory]
     */
    private static String[] mapVolumes(ContainerDescription cd, HostSelection hostSelection) {

        if (cd.volumes == null || cd.volumes.length == 0) {
            return null;
        }

        return Arrays.stream(cd.volumes).map((v) -> hostSelection
                .mapNames(new String[] { v })[0])
                .filter(Objects::nonNull).toArray(String[]::new);

    }

    private static String[] mapLinks(ContainerDescription cd,
            HostSelection hostSelection) {
        if (cd.links == null) {
            return null;
        }

        List<String> mappedServices = new ArrayList<>();
        for (String link : cd.links) {
            String[] split = link.split(ServiceLinkSerializer.SPLIT_REGEX);

            String service = split[0];
            String alias = split.length == 2 ? split[1] : null;

            for (String mappedService : hostSelection.mapNames(new String[] { service })) {
                if (alias != null) {
                    mappedServices.add(mappedService + ServiceLinkSerializer.SPLIT_REGEX + alias);
                } else {
                    mappedServices.add(mappedService);
                }
            }
        }

        return mappedServices.toArray(new String[mappedServices.size()]);
    }
}
