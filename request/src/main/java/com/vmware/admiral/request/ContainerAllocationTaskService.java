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
import static com.vmware.admiral.common.util.PropertyUtils.mergeLists;
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_ALLOCATION_REQUEST;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService.ContainerHostDataCollectionState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState.SubStage;
import com.vmware.admiral.request.ContainerExposeServiceProcessingTaskService.ContainerExposeServiceProcessingTaskState;
import com.vmware.admiral.request.ContainerServiceLinkProcessingTaskService.ContainerServiceLinkProcessingTaskState;
import com.vmware.admiral.request.ContainerServiceLinkProcessingTaskService.ContainerServiceLinksConfig;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.ResourceNamePrefixTaskService.ResourceNamePrefixTaskState;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

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

        private static final String FIELD_NAME_RESOURCE_DESCRIPTION_LINK = "resourceDescriptionLink";
        private static final String FIELD_NAME_RESOURCE_TYPE = "resourceType";
        private static final String FIELD_NAME_TENANT_LINKS = "tenantLinks";
        private static final String FIELD_NAME_GROUP_RESOURCE_POLICY = "groupResourcePolicyLink";
        private static final String FIELD_NAME_RESOURCE_COUNT = "resourceCount";
        private static final String FIELD_NAME_RESOURCE_LINKS = "resourceLinks";
        private static final String FIELD_NAME_RESOURCE_NAMES = "resourceNames";
        private static final String FIELD_NAME_HOST_SELECTIONS = "hostSelections";
        private static final String FIELD_NAME_CONTAINER_SERVICE_LINKS_CONFIGS = "containerServiceLinksConfigs";
        private static final String FIELD_NAME_INSTANCE_ADAPTER_REF = "instanceAdapterReference";

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
        public String resourceDescriptionLink;

        /** (Required) Type of resource to create. */
        public String resourceType;

        /** (Required) the groupResourcePolicyState that links to ResourcePool */
        public String groupResourcePolicyLink;

        /** (Required) Number of resources to provision. */
        public Long resourceCount;

        /** Set by a Task with the links of the provisioned resources. */
        public List<String> resourceLinks;

        /** Indicating that it is in the second phase after allocation */
        public boolean postAllocation;

        // Service use fields:

        /** (Internal) Set by task after resource name prefixes requested. */
        public List<String> resourceNames;

        /** (Internal) Set by task after the ComputeState is found to host the containers */
        public List<HostSelection> hostSelections;

        /**
         * (Internal) Set by task after the resource names are determined and ComputeState is found
         * to host the containers.
         */
        public Map<String, HostSelection> resourceNameToHostSelection;

        /** (Internal) Set by task */
        public URI instanceAdapterReference;

        /** (Internal) Set by task with ContainerDescription name. */
        public String descName;

        /** (Internal) Set by task, a map of container service links configurations */
        public Map<String, ContainerServiceLinksConfig> containerServiceLinksConfigs;
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
        case PROCESSING_SERVICE_LINKS:
            createServiceLinkProcessingTask(state, null, null);
            break;
        case START_PROVISIONING:
            provisionOrAllocateContainers(state, null, null);
            break;
        case PROVISIONING:
            break;
        case PROVISIONING_COMPLETED:
            proceedAfterProvisioning(state, null);
            break;
        case PROCESSING_PUBLIC_SERVICE_ALIAS:
            createPublicServiceAliasProcessingTask(state, null, null);
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
    protected boolean validateStageTransition(Operation patch,
            ContainerAllocationTaskState patchBody, ContainerAllocationTaskState currentState) {

        currentState.hostSelections = mergeProperty(
                currentState.hostSelections, patchBody.hostSelections);

        currentState.resourceNameToHostSelection = mergeProperty(
                currentState.resourceNameToHostSelection, patchBody.resourceNameToHostSelection);

        currentState.instanceAdapterReference = mergeProperty(
                currentState.instanceAdapterReference, patchBody.instanceAdapterReference);

        currentState.resourceLinks = mergeLists(
                currentState.resourceLinks, patchBody.resourceLinks);

        currentState.resourceNames = mergeProperty(
                currentState.resourceNames, patchBody.resourceNames);

        currentState.resourceCount = mergeProperty(currentState.resourceCount,
                patchBody.resourceCount);

        currentState.descName = mergeProperty(currentState.descName, patchBody.descName);

        currentState.containerServiceLinksConfigs = mergeProperty(
                currentState.containerServiceLinksConfigs, patchBody.containerServiceLinksConfigs);

        return false;
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
            assertNotEmpty(state.groupResourcePolicyLink, "groupResourcePolicyLink");
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
        List<String> resourceLinks;
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
            final SubStage subStage = isAllocationRequest(state) ? SubStage.START_PROVISIONING
                    : SubStage.PROCESSING_SERVICE_LINKS;
            if (state.instanceAdapterReference == null) {
                // reload container description if null
                getContainerDescription(state, (contDesc) -> {
                    ContainerAllocationTaskState body = createUpdateSubStageTask(state, subStage);
                    body.instanceAdapterReference = contDesc.instanceAdapterReference;
                    body.resourceNameToHostSelection = resourceNameToHostSelection;
                    body.customProperties = mergeCustomProperties(state.customProperties,
                            contDesc.customProperties);
                    sendSelfPatch(body);
                });
            } else {
                ContainerAllocationTaskState body = createUpdateSubStageTask(state,
                        SubStage.PROCESSING_SERVICE_LINKS);
                body.resourceNameToHostSelection = resourceNameToHostSelection;
                sendSelfPatch(body);
            }
        }
    }

    private void completeAllocationTask(ContainerAllocationTaskState state) {
        if (state.hostSelections != null) {
            try {
                ContainerHostDataCollectionState body = new ContainerHostDataCollectionState();
                body.computeContainerHostLinks = new HashSet<>(state.hostSelections.stream()
                        .map((r) -> r.hostLink).collect(Collectors.toList()));
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

    private List<String> buildResourceLinks(ContainerAllocationTaskState state) {
        logInfo("Generate provisioned resourceLinks");
        List<String> resourceLinks = new ArrayList<>(state.resourceNames.size());
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

    private void getContainerStates(List<String> resourceLinks,
            Consumer<List<ContainerState>> callbackFunction) {

        QueryTask contStateQuery = QueryUtil.buildPropertyQuery(ContainerState.class);
        QueryUtil.addExpandOption(contStateQuery);
        QueryUtil.addListValueClause(contStateQuery, ContainerState.FIELD_NAME_SELF_LINK,
                resourceLinks);

        List<ContainerState> conatinerStates = new ArrayList<>();

        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class).query(
                contStateQuery,
                (r) -> {
                    if (r.hasException()) {
                        failTask("Failure retrieving container states", r.getException());
                    } else if (r.hasResult()) {
                        conatinerStates.add(r.getResult());
                    } else {
                        callbackFunction.accept(conatinerStates);
                    }
                });
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
        body.customProperties = mergeProperty(containerDesc.customProperties,
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
        placementTask.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
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
        sendRequest(Operation.createGet(this, state.groupResourcePolicyLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving GroupResourcePolicy", e);
                        return;
                    }

                    GroupResourcePolicyState policyState = o
                            .getBody(GroupResourcePolicyState.class);
                    if (policyState.resourcePoolLink == null) {
                        failTask(null, new IllegalStateException(
                                "Policy state has no resourcePoolLink"));
                        return;
                    }
                    callbackFunction.accept(policyState.resourcePoolLink);
                }));
    }

    private void getResourcePolicyState(ContainerAllocationTaskState state,
            Consumer<GroupResourcePolicyState> callbackFunction) {
        sendRequest(Operation.createGet(this, state.groupResourcePolicyLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving GroupResourcePolicy", e);
                        return;
                    }

                    GroupResourcePolicyState policyState = o
                            .getBody(GroupResourcePolicyState.class);

                    callbackFunction.accept(policyState);
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
        namePrefixTask.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
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

    private void createServiceLinkProcessingTask(ContainerAllocationTaskState state,
            ContainerDescription containerDescription, List<ContainerState> containerStates) {

        if (isAllocationRequest(state)) {
            logInfo("Skipping service link processing for allocation request");
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.START_PROVISIONING));
            return;
        }

        if (containerDescription == null) {
            getContainerDescription(state, (contDesc) -> createServiceLinkProcessingTask(
                    state, contDesc, containerStates));

            return;
        }

        if (containerDescription.links == null || containerDescription.links.length == 0) {
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.START_PROVISIONING));
            return;
        }

        List<ContainerState> preparedContainerStates = null;

        if (containerStates != null) {
            preparedContainerStates = containerStates;
        } else {
            if (state.postAllocation) {
                getContainerStates(
                        state.resourceLinks,
                        (result) -> createServiceLinkProcessingTask(state, containerDescription,
                                result));
                return;
            } else {
                // Prepare container state model with only the properties needed by
                // ContainerServiceLinkProcessingTaskState
                // Actual container states don't exist at this moment.
                preparedContainerStates = new ArrayList<>();

                for (String resourceName : state.resourceNames) {
                    ContainerState containerState = new ContainerState();
                    containerState.descriptionLink = state.resourceDescriptionLink;
                    containerState.documentSelfLink = buildResourceLink(resourceName);
                    containerState.names = new ArrayList<>();
                    containerState.names.add(resourceName);
                    containerState.parentLink = state.resourceNameToHostSelection
                            .get(resourceName).hostLink;
                    preparedContainerStates.add(containerState);
                }
            }
        }

        try {
            // create service link processing task
            ContainerServiceLinkProcessingTaskState serviceLinkTask = new ContainerServiceLinkProcessingTaskState();
            serviceLinkTask.documentSelfLink = getSelfId();
            serviceLinkTask.containerDescription = containerDescription;
            serviceLinkTask.containerStates = preparedContainerStates;
            serviceLinkTask.contextId = getContextId(state);

            serviceLinkTask.serviceTaskCallback = ServiceTaskCallback.create(
                    state.documentSelfLink,
                    TaskStage.STARTED, SubStage.START_PROVISIONING,
                    TaskStage.STARTED, SubStage.ERROR);

            serviceLinkTask.requestTrackerLink = state.requestTrackerLink;

            sendRequest(Operation
                    .createPost(this, ContainerServiceLinkProcessingTaskService.FACTORY_LINK)
                    .setBody(serviceLinkTask)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failure creating service link processing task", e);
                            return;
                        }
                    }));
        } catch (Throwable x) {
            failTask("Failure creating service link processing task", x);
        }
    }

    private void createPublicServiceAliasProcessingTask(ContainerAllocationTaskState state,
            ContainerDescription containerDescription, List<ContainerState> containerStates) {

        if (isAllocationRequest(state)) {
            logInfo("Skipping public service address processing for allocation request");
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
            return;
        }

        if (containerDescription == null) {
            getContainerDescription(state, (contDesc) -> createPublicServiceAliasProcessingTask(
                    state, contDesc, containerStates));

            return;
        }

        if (containerDescription.exposeService == null
                || containerDescription.exposeService.length == 0) {
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
            return;
        }

        if (containerStates == null && state.resourceLinks != null) {
            getContainerStates(state.resourceLinks,
                    (contStates) -> createPublicServiceAliasProcessingTask(
                            state, containerDescription, contStates));

            return;
        }

        try {
            // create expose service processing task
            ContainerExposeServiceProcessingTaskState serviceLinkTask = new ContainerExposeServiceProcessingTaskState();
            serviceLinkTask.documentSelfLink = getSelfId();
            serviceLinkTask.containerDescription = containerDescription;
            serviceLinkTask.containerStates = containerStates;
            serviceLinkTask.contextId = getContextId(state);

            serviceLinkTask.serviceTaskCallback = ServiceTaskCallback.create(
                    state.documentSelfLink,
                    TaskStage.STARTED, SubStage.COMPLETED,
                    TaskStage.STARTED, SubStage.ERROR);

            serviceLinkTask.requestTrackerLink = state.requestTrackerLink;

            sendRequest(Operation
                    .createPost(this, ContainerExposeServiceProcessingTaskService.FACTORY_LINK)
                    .setBody(serviceLinkTask)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failure creating expose service processing task", e);
                            return;
                        }
                    }));
        } catch (Throwable x) {
            failTask("Failure creating expose service processing task", x);
        }
    }

    private void provisionOrAllocateContainers(ContainerAllocationTaskState state,
            ContainerDescription containerDesc, ServiceTaskCallback taskCallback) {
        final boolean allocationRequest = isAllocationRequest(state);

        if (taskCallback == null) {
            // create a counter subtask link first
            createCounterSubTaskCallback(state, state.resourceCount, !allocationRequest,
                    SubStage.PROVISIONING_COMPLETED,
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
                updateContainerState(state, taskCallback, resourceLink,
                        () -> createContainerInstanceRequests(state, taskCallback, resourceLink));
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

    private void proceedAfterProvisioning(ContainerAllocationTaskState state,
            ContainerDescription containerDesc) {
        if (isAllocationRequest(state)) {
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
            return;
        }

        if (containerDesc == null) {
            if (this.containerDescription == null) {
                getContainerDescription(state, (contDesc) -> {
                    proceedAfterProvisioning(state, contDesc);
                });
                return;
            } else {
                containerDesc = this.containerDescription;
            }
        }

        if (containerDesc.exposeService == null
                || containerDesc.exposeService.length == 0) {
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
        } else {
            sendSelfPatch(createUpdateSubStageTask(state,
                    SubStage.PROCESSING_PUBLIC_SERVICE_ALIAS));
        }
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

    // this is called after only post allocation when the state is already being created
    private void updateContainerState(ContainerAllocationTaskState state,
            ServiceTaskCallback taskCallback, String resourceLink, Runnable callbackFunction) {

        Map<String, ContainerServiceLinksConfig> containerServiceLinksConfigs = state.containerServiceLinksConfigs;
        if (containerServiceLinksConfigs == null
                || containerServiceLinksConfigs.get(resourceLink) == null) {
            callbackFunction.run();
            return;
        }

        ContainerState containerState = new ContainerState();
        containerState.extraHosts = containerServiceLinksConfigs.get(resourceLink).extraHosts;

        sendRequest(Operation.createPatch(this, resourceLink)
                .setBody(containerState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error while updating container: %s", resourceLink);
                        completeSubTasksCounter(taskCallback, e);
                        return;
                    }
                    callbackFunction.run();
                }));

    }

    private void createContainerState(ContainerAllocationTaskState state,
            ContainerDescription containerDesc,
            String resourceName, boolean allocationRequest,
            GroupResourcePolicyState groupResourcePolicyState, HostSelection hostSelection,
            ServiceTaskCallback taskCallback) {
        try {

            if (groupResourcePolicyState == null) {
                getResourcePolicyState(
                        state,
                        (resourcePolicyState) -> createContainerState(state, containerDesc,
                                resourceName, allocationRequest, resourcePolicyState,
                                hostSelection,
                                taskCallback));
                return;
            }

            final ContainerState containerState = new ContainerState();
            containerState.documentSelfLink = buildResourceId(resourceName);
            containerState.names = new ArrayList<>();
            containerState.names.add(resourceName);
            containerState.tenantLinks = state.tenantLinks;
            containerState.descriptionLink = state.resourceDescriptionLink;
            containerState.groupResourcePolicyLink = state.groupResourcePolicyLink;
            containerState.parentLink = hostSelection.hostLink;
            containerState.powerState = PowerState.PROVISIONING;
            containerState.status = ContainerState.CONTAINER_ALLOCATION_STATUS;
            containerState.adapterManagementReference = state.instanceAdapterReference;
            containerState.customProperties = state.customProperties;
            containerState.image = containerDesc.image;
            containerState.command = containerDesc.command;
            containerState.volumesFrom = hostSelection.mapNames(containerDesc.volumesFrom);
            containerState.volumeDriver = containerDesc.volumeDriver;

            containerState.networks = mapNetworks(containerDesc, hostSelection);

            containerState.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();

            containerState.memoryLimit = getMinParam(groupResourcePolicyState.memoryLimit,
                    containerDesc.memoryLimit);

            Long cpuShares = getMinParam(groupResourcePolicyState.cpuShares,
                    containerDesc.cpuShares);
            containerState.cpuShares = cpuShares != null ? cpuShares.intValue() : null;

            String resourceLink = buildResourceLink(resourceName);

            Map<String, ContainerServiceLinksConfig> containerServiceLinksConfigs = state.containerServiceLinksConfigs;
            if (containerServiceLinksConfigs != null
                    && containerServiceLinksConfigs.get(resourceLink) != null) {
                containerState.extraHosts = containerServiceLinksConfigs
                        .get(resourceLink).extraHosts;
            } else {
                containerState.extraHosts = containerDesc.extraHosts;
            }
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
                                    updateNetworksHosts(body, taskCallback);
                                } else {
                                    createContainerInstanceRequests(state, taskCallback,
                                            body.documentSelfLink);
                                }
                            }));

        } catch (Throwable e) {
            failTask("System failure creating ContainerStates", e);
        }
    }

    /**
     * Updates all the container networks states (if needed) to use the same container host for
     * provisioning the networks. Alternatively, instead of doing it during the container end of the
     * allocation phase, it could be done during the network beginning of the provisioning phase.
     */
    private void updateNetworksHosts(ContainerState containerState,
            ServiceTaskCallback taskCallback) {

        Map<String, ServiceNetwork> networks = containerState.networks;
        if ((networks == null) || networks.isEmpty()) {
            completeSubTasksCounter(taskCallback, null);
            return;
        }

        OperationJoin
                .create(networks.keySet().stream().map(
                        (networkId) -> createUpdateNetworkHostLinkOperation(networkId,
                                containerState.parentLink)))
                .setCompletion((os, es) -> {
                    if (es != null) {
                        completeSubTasksCounter(null,
                                new IllegalStateException(Utils.toString(es)));
                        return;
                    }
                    completeSubTasksCounter(taskCallback, null);
                })
                .sendWith(this);
    }

    private Operation createUpdateNetworkHostLinkOperation(String networkId, String parentLink) {

        String networkLink = UriUtils.buildUriPath(ContainerNetworkService.FACTORY_LINK,
                networkId);

        return Operation.createGet(this, networkLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failure retrieving state for '%s': %s", networkLink,
                                Utils.toString(e));
                        return;
                    }

                    String hostLink = o.getBody(ContainerNetworkState.class).originatingHostLink;

                    if ((hostLink != null) && !hostLink.isEmpty()) {
                        logInfo("Container network host already set for '%s'.", networkLink);
                        return;
                    }

                    ContainerNetworkState networkState = new ContainerNetworkState();
                    networkState.documentSelfLink = networkLink;
                    networkState.originatingHostLink = hostLink;

                    sendRequest(Operation.createPatch(this, networkState.documentSelfLink)
                            .setBody(networkState)
                            .setContextId(getSelfId())
                            .setCompletion((o2, e2) -> {
                                if (e2 != null) {
                                    logWarning("Failure updating host link for '%s': %s",
                                            networkLink, Utils.toString(e2));
                                    return;
                                }
                                logInfo("Container network host set for '%s'.", networkLink);
                            }));
                });
    }

    private void createContainerInstanceRequests(ContainerAllocationTaskState state,
            ServiceTaskCallback taskCallback, String containerSelfLink) {
        AdapterRequest adapterRequest = new AdapterRequest();
        adapterRequest.resourceReference = UriUtils.buildUri(getHost(), containerSelfLink);
        adapterRequest.serviceTaskCallback = taskCallback;
        adapterRequest.operationTypeId = ContainerOperationType.CREATE.id;
        adapterRequest.customProperties = state.customProperties;

        sendRequest(Operation.createPatch(state.instanceAdapterReference)
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

    @Override
    public ServiceDocument getDocumentTemplate() {
        ContainerAllocationTaskState template = (ContainerAllocationTaskState) super.getDocumentTemplate();

        setDocumentTemplateIndexingOptions(template, EnumSet.of(PropertyIndexingOption.STORE_ONLY),
                ContainerAllocationTaskState.FIELD_NAME_RESOURCE_DESCRIPTION_LINK,
                ContainerAllocationTaskState.FIELD_NAME_RESOURCE_TYPE,
                ContainerAllocationTaskState.FIELD_NAME_TENANT_LINKS,
                ContainerAllocationTaskState.FIELD_NAME_GROUP_RESOURCE_POLICY,
                ContainerAllocationTaskState.FIELD_NAME_RESOURCE_COUNT,
                ContainerAllocationTaskState.FIELD_NAME_RESOURCE_LINKS,
                ContainerAllocationTaskState.FIELD_NAME_RESOURCE_NAMES,
                ContainerAllocationTaskState.FIELD_NAME_HOST_SELECTIONS,
                ContainerAllocationTaskState.FIELD_NAME_CONTAINER_SERVICE_LINKS_CONFIGS,
                ContainerAllocationTaskState.FIELD_NAME_INSTANCE_ADAPTER_REF);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SINGLE_ASSIGNMENT),
                ContainerAllocationTaskState.FIELD_NAME_RESOURCE_DESCRIPTION_LINK,
                ContainerAllocationTaskState.FIELD_NAME_RESOURCE_TYPE,
                ContainerAllocationTaskState.FIELD_NAME_TENANT_LINKS,
                ContainerAllocationTaskState.FIELD_NAME_GROUP_RESOURCE_POLICY,
                ContainerAllocationTaskState.FIELD_NAME_RESOURCE_COUNT,
                ContainerAllocationTaskState.FIELD_NAME_RESOURCE_NAMES,
                ContainerAllocationTaskState.FIELD_NAME_HOST_SELECTIONS,
                ContainerAllocationTaskState.FIELD_NAME_CONTAINER_SERVICE_LINKS_CONFIGS,
                ContainerAllocationTaskState.FIELD_NAME_INSTANCE_ADAPTER_REF);

        setDocumentTemplateUsageOptions(template, EnumSet.of(PropertyUsageOption.SERVICE_USE),
                ContainerAllocationTaskState.FIELD_NAME_RESOURCE_NAMES,
                ContainerAllocationTaskState.FIELD_NAME_HOST_SELECTIONS,
                ContainerAllocationTaskState.FIELD_NAME_INSTANCE_ADAPTER_REF);

        return template;
    }

    /**
     * Returns the minimum. Handles nulls and treats 0 as no limit
     */
    public static Long getMinParam(long policyLimit, Number descLimit) {
        // get the defined one. policyLimit will probably be changed to Long
        if (descLimit == null) {
            return policyLimit;
        } else if (policyLimit == 0) { // 0 means no limit, so get the other one
            return descLimit.longValue();
        } else if (descLimit.longValue() == 0) {
            return policyLimit;
        } else {
            return Math.min(policyLimit, descLimit.longValue());
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
}
