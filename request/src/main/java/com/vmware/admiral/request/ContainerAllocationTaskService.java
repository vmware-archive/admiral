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

import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_ALLOCATION_REQUEST;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import com.vmware.admiral.compute.container.HealthChecker;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.content.ServiceLinkSerializer;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState.SubStage;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.ResourceNamePrefixTaskService.ResourceNamePrefixTaskState;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
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
    public static final String HEALTH_CHECK_TIMEOUT_PARAM_NAME = "provision.container.health.check.timeout.ms";
    public static final String HEALTH_CHECK_DELAY_PARAM_NAME = "provision.container.health.check.delay.ms";

    // cached container description
    private volatile ContainerDescription containerDescription;

    private long healthCheckDelay;
    private long healthCheckTimeout;

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
            HOST_ALLOCATED,
            ALLOCATE_PORTS,
            START_PROVISIONING,
            PROVISIONING,
            PROVISIONING_COMPLETED,
            WAITING_FOR_HEALTH_CHECK,
            PROCESSING_PUBLIC_SERVICE_ALIAS,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(PROVISIONING));
        }

        /** The description that defines the requested resource. */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        /** (Required) Type of resource to create. */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED }, indexing = STORE_ONLY)
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
                proceedTo(SubStage.RESOURCES_NAMED);
            }
            break;
        case RESOURCES_NAMED:
            proceedTo(SubStage.RESOURCES_LINKS_BUILT, s -> {
                if (!state.postAllocation) {
                    s.resourceLinks = buildResourceLinks(state);
                }
            });
            break;
        case RESOURCES_LINKS_BUILT:
            if (state.hostSelections == null || state.hostSelections.isEmpty()) {
                selectPlacementComputeHost(state, null);
            } else {
                // in specific cases when the host is pre-selected
                // (ex: installing agents directly to a host, this step is not needed)
                proceedTo(SubStage.PLACEMENT_HOST_SELECTED);
            }
            break;
        case PLACEMENT_HOST_SELECTED:
            proceedAfterHostSelection(state);
            break;
        case HOST_ALLOCATED:
            // this is composition provision request, skip directly to provisioning
            if (state.postAllocation) {
                proceedTo(SubStage.START_PROVISIONING);
            } else {
                createContainerStates(state, null, null);
            }
            break;
        case ALLOCATE_PORTS:
            allocatePorts(state);
            break;
        case START_PROVISIONING:
            provisionAllocatedContainers(state, null);
            break;
        case PROVISIONING:
            break;
        case WAITING_FOR_HEALTH_CHECK:
            waitForHealthCheck(state);
            break;
        case COMPLETED:
            completeTask(state);
            break;
        case ERROR:
            completeWithError();
            break;
        default:
            break;
        }
    }

    @Override
    protected void validateStateOnStart(ContainerAllocationTaskState state) {
        if (state.postAllocation) {
            if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
                throw new LocalizableValidationException("'resourceLinks' must not be empty",
                        "request.container.allocation.resources.empty");
            }

            if (state.resourceCount > state.resourceLinks.size()) {
                throw new LocalizableValidationException("Resource count must be equal to number of resources during post allocation.",
                        "request.container.allocation.resource.count");
            }

        } else {
            if (state.groupResourcePlacementLink == null || state.groupResourcePlacementLink.isEmpty()) {
                throw new LocalizableValidationException("'groupResourcePlacementLink' must not be empty",
                        "request.container.allocation.group.empty");
            }
        }

        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.",
                    "request.resource-count.zero");
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
            failTask(null, new LocalizableValidationException(
                    "computeHostLink and resourceNames can't be null at this state", "request.container.allocation.host.missing"));
        } else if (state.postAllocation
                || (state.hostSelections != null && state.resourceNames != null)) {

            final Map<String, HostSelection> resourceNameToHostSelection = !state.postAllocation
                    ? selectHostPerResourceName(state.resourceNames, state.hostSelections) : null;
            if (state.instanceAdapterReference == null) {
                // reload container description if null
                getContainerDescription(state, (contDesc) -> {
                    proceedTo(SubStage.HOST_ALLOCATED, s -> {
                        s.instanceAdapterReference = contDesc.instanceAdapterReference;
                        s.resourceNameToHostSelection = resourceNameToHostSelection;
                        s.customProperties = mergeCustomProperties(state.customProperties,
                                contDesc.customProperties);
                    });
                });
            } else {
                proceedTo(SubStage.HOST_ALLOCATED, s -> {
                    s.resourceNameToHostSelection = resourceNameToHostSelection;
                });
            }
        }
    }

    private void completeTask(ContainerAllocationTaskState state) {
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

        complete();
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
            proceedTo(SubStage.PLACEMENT_HOST_SELECTED);
            return;
        }

        if (containerDesc == null) {
            getContainerDescription(state, (contDesc) -> this.prepareContext(state, contDesc));
            return;
        }

        proceedTo(SubStage.CONTEXT_PREPARED, s -> {
            // merge request/allocation properties over the container description properties
            s.customProperties = mergeCustomProperties(containerDesc.customProperties,
                    state.customProperties);

            if (s.getCustomProperty(FIELD_NAME_CONTEXT_ID_KEY) == null) {
                s.addCustomProperty(FIELD_NAME_CONTEXT_ID_KEY, getSelfId());
            }
            s.descName = containerDesc.name;
        });
    }

    private void selectPlacementComputeHost(ContainerAllocationTaskState state,
            String resourcePoolLink) {
        if (!state.postAllocation && state.resourceNames == null || state.resourceNames.isEmpty()) {
            failTask(null, new LocalizableValidationException("resource names expected at this stage.",
                    "request.container.allocation.resource-names.missing"));
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
                        failTask(null, new LocalizableValidationException(
                                "Placement state has no resourcePoolLink", "request.container.allocation.missing.resource-pool"));
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

    private void getCompositeComponent(ContainerAllocationTaskState state,
            Consumer<Boolean> callbackFunction) {
        String contextId = state.customProperties.get(FIELD_NAME_CONTEXT_ID_KEY);
        String compositeComponentLink = UriUtils
                .buildUriPath(CompositeComponentFactoryService.SELF_LINK, contextId);
        sendRequest(Operation.createGet(this, compositeComponentLink)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        callbackFunction.accept(Boolean.FALSE);
                    } else if (e != null) {
                        failTask("Failure retrieving CompositeComponent", e);
                    } else {
                        callbackFunction.accept(Boolean.TRUE);
                    }
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

    private void createContainerStates(ContainerAllocationTaskState state,
            ContainerDescription containerDesc,
            ServiceTaskCallback taskCallback) {
        final boolean allocationRequest = isAllocationRequest(state);
        if (taskCallback == null) {
            // create a counter subtask link to move to ALLOCATE_PORTS state when finished
            createCounterSubTaskCallback(state, state.resourceCount, !allocationRequest,
                    SubStage.ALLOCATE_PORTS,
                    (serviceTask) -> createContainerStates(state,
                            this.containerDescription, serviceTask));
            return;
        }

        if (containerDesc == null) {
            if (this.containerDescription == null) {
                getContainerDescription(state, (contDesc) -> {
                    createContainerStates(state, contDesc, taskCallback);
                });
            } else {
                containerDesc = this.containerDescription;
            }
        }

        if (allocationRequest) {
            logInfo("Allocate request for %s containers", state.resourceCount);
        } else {
            logInfo("Allocate and provision request for %s containers", state.resourceCount);
        }

        for (String resourceName : state.resourceNames) {
            createContainerState(state, containerDesc, null, resourceName, null,
                    state.resourceNameToHostSelection.get(resourceName), taskCallback);
        }
    }

    private void provisionAllocatedContainers(ContainerAllocationTaskState state,
            ServiceTaskCallback taskCallback) {
        final boolean allocationRequest = isAllocationRequest(state);

        if (taskCallback == null) {
            // create a counter subtask link first
            createCounterSubTaskCallback(state, state.resourceCount, !allocationRequest,
                    !allocationRequest || state.postAllocation ? SubStage.WAITING_FOR_HEALTH_CHECK : SubStage.COMPLETED,
                    (serviceTask) -> provisionAllocatedContainers(state, serviceTask));
            return;
        }

        logInfo("Provision request for %s containers", state.resourceCount);

        for (String resourceLink : state.resourceLinks) {
            createContainerInstanceRequests(state, taskCallback, resourceLink);
        }
        proceedTo(SubStage.PROVISIONING);
    }

    private boolean isAllocationRequest(ContainerAllocationTaskState state) {
        return !state.postAllocation && (state.customProperties != null
                && Boolean.parseBoolean(state.customProperties.get(FIELD_NAME_ALLOCATION_REQUEST)));
    }

    private boolean isClusteringOperation(ContainerAllocationTaskState state) {
        return state.getCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP) != null;
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
            Boolean isFromTemplate,
            String resourceName,
            GroupResourcePlacementState groupResourcePlacementState, HostSelection hostSelection,
            ServiceTaskCallback taskCallback) {
        try {

            if (groupResourcePlacementState == null) {
                getResourcePlacementState(
                        state,
                        (resourcePlacementState) -> createContainerState(state, containerDesc, isFromTemplate,
                                resourceName, resourcePlacementState,
                                hostSelection, taskCallback));
                return;
            }

            // get composite component only for scale operations (needed later)
            if (isFromTemplate == null && isClusteringOperation(state)) {
                getCompositeComponent(state,
                        (isTemplate) -> createContainerState(state, containerDesc, isTemplate,
                                resourceName, groupResourcePlacementState,
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
            if (containerDesc.portBindings != null) {
                containerState.ports = new ArrayList<PortBinding>(
                        Arrays.asList(containerDesc.portBindings));
            }

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

            // set the component link if container is created or scaled from a template
            String contextId = state.getCustomProperty(FIELD_NAME_CONTEXT_ID_KEY);
            if ((contextId != null && !isClusteringOperation(state)
                    && !contextId.equals(getSelfId()))
                    || (isClusteringOperation(state) && isFromTemplate)) {
                containerState.compositeComponentLink = UriUtils
                        .buildUriPath(CompositeComponentFactoryService.SELF_LINK, contextId);
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

                                completeSubTasksCounter(taskCallback, null);
                            }));

        } catch (Throwable e) {
            failTask("System failure creating ContainerStates", e);
        }
    }

    private void allocatePorts(ContainerAllocationTaskState state) {
        final boolean allocationRequest = isAllocationRequest(state);

        // if it is composition allocation request, move to COMPLETED SubStage when complete
        // if it is Admiral provisioning request, move to START_PROVISIONING SubStage when complete
        SubStage completionStage = allocationRequest ?
                SubStage.COMPLETED :
                SubStage.START_PROVISIONING;

        ContainerPortsAllocationTaskService.ContainerPortsAllocationTaskState portsAllocationTask =
                new ContainerPortsAllocationTaskService.ContainerPortsAllocationTaskState();
        portsAllocationTask.containerStateLinks = state.resourceLinks;
        portsAllocationTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, completionStage,
                TaskStage.STARTED, SubStage.ERROR);
        portsAllocationTask.documentSelfLink = getSelfId();
        portsAllocationTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ContainerPortsAllocationTaskService.FACTORY_LINK)
                .setBody(portsAllocationTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating port allocation task", e);
                        return;
                    }
                }));
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

    private void waitForHealthCheck(ContainerAllocationTaskState state) {
        AtomicInteger expectedSuccessfullHealthCheckCount = new AtomicInteger(
                state.resourceLinks.size());
        AtomicBoolean proceededToError = new AtomicBoolean(false);

        if (this.containerDescription.healthConfig == null) {
            logInfo("Skipping health check.");
            proceedTo(SubStage.COMPLETED);

            return;
        }

        DeferredResult.allOf(Arrays.asList(getProperty(HEALTH_CHECK_DELAY_PARAM_NAME),
                getProperty(HEALTH_CHECK_TIMEOUT_PARAM_NAME)))
                .whenComplete((props, ex) -> {
                    if (ex != null) {
                        failTask(
                                "Error retrieving health configuration environment variables: ",
                                ex);
                        return;
                    }

                    Map<String, Long> propsMap = props.stream().collect(
                            Collectors.toMap(s -> s.key, s -> Long.parseLong(s.value)));
                    this.healthCheckTimeout = propsMap.get(HEALTH_CHECK_TIMEOUT_PARAM_NAME);
                    this.healthCheckDelay = propsMap.get(HEALTH_CHECK_DELAY_PARAM_NAME);

                    Iterator<String> it = state.resourceLinks.iterator();
                    while (it.hasNext()) {
                        String resourceLink = it.next();
                        fetchContainerState(resourceLink, (cs) -> {
                            doHealthCheck(state, cs, expectedSuccessfullHealthCheckCount, proceededToError, System.currentTimeMillis());
                        });
                    }
                });
    }

    private void doHealthCheck(ContainerAllocationTaskState state, ContainerState containerState,
            AtomicInteger expectedSuccessfullHealthCheckCount, AtomicBoolean proceededToError, long startTime) {

        if ((System.currentTimeMillis() - startTime) > this.healthCheckTimeout) {
            logWarning("Health check timeout exceeded.");
            if (this.containerDescription.healthConfig.continueProvisioningOnError) {
                if (expectedSuccessfullHealthCheckCount.decrementAndGet() == 0) {
                    proceedTo(SubStage.COMPLETED);
                }

                return;
            }

            if (proceededToError.get()) {
                return;
            }

            proceededToError.set(true);
            proceedTo(SubStage.ERROR, (s) -> {
                s.taskInfo.failure = Utils.toServiceErrorResponse(
                        new Exception(String.format("Health check failed for %s",
                                containerState.documentSelfLink)));
            });

            return;
        }

        new HealthChecker(getHost()).doHealthCheckRequest(containerState,
                this.containerDescription.healthConfig, (containerStats) -> {
                    if (containerStats != null
                            && Boolean.TRUE.equals(containerStats.healthCheckSuccess)) {
                        if (expectedSuccessfullHealthCheckCount.decrementAndGet() == 0) {
                            proceedTo(SubStage.COMPLETED);
                            return;
                        }

                    } else {
                        getHost().schedule(() -> {
                            logInfo("Scheduling health check for: " + containerState.documentSelfLink);
                            doHealthCheck(state, containerState,
                                    expectedSuccessfullHealthCheckCount, proceededToError, startTime);
                        }, this.healthCheckDelay, TimeUnit.MILLISECONDS);
                    }
                });
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

    private void fetchContainerState(String containerSelfLink, Consumer<ContainerState> callback) {

        sendRequest(Operation.createGet(getHost(), containerSelfLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Cannot retrieve the container state: " + containerSelfLink, e);
                        return;
                    }

                    ContainerState containerState = o.getBody(ContainerState.class);
                    callback.accept(containerState);
                }));
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
     * @param cd            - ContainerDescription
     * @param hostSelection - HostSelection for resource.
     * @return new volume name equals to old one, but with suffix for host directory like:
     * [namedVolume-mcm376:/container-directory]
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

    private DeferredResult<ConfigurationState> getProperty(String propName) {
        String propUrl = UriUtils.buildUriPath(
                ConfigurationFactoryService.SELF_LINK,
                propName);
        Operation op = Operation.createGet(this, propUrl).setReferer(this.getUri());

        return getHost()
                .sendWithDeferredResult(op, ConfigurationState.class);
    }
}
