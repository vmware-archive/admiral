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

package com.vmware.admiral.request.compute;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.common.util.PropertyUtils.mergeLists;
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;
import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_DISPLAY_NAME;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINKS;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.EnvironmentMappingService.EnvironmentMappingState;
import com.vmware.admiral.compute.PropertyMapping;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState.SubStage;
import com.vmware.admiral.request.compute.ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState;
import com.vmware.admiral.request.compute.enhancer.ComputeDescriptionEnhancers;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.QueryTaskClientHelper;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;

public class ComputeAllocationTaskService
        extends
        AbstractTaskStatefulService<ComputeAllocationTaskService.ComputeAllocationTaskState, ComputeAllocationTaskService.ComputeAllocationTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_ALLOCATION_TASKS;

    public static final String DISPLAY_NAME = "Compute Allocation";

    private static final String ID_DELIMITER_CHAR = "-";

    // cached compute description
    private transient volatile ComputeDescription computeDescription;

    public static class ComputeAllocationTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeAllocationTaskState.SubStage> {

        public static final String ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME = "compute.container.host";

        public static final String FIELD_NAME_CUSTOM_PROP_ENV = "__env";
        public static final String FIELD_NAME_CUSTOM_PROP_ZONE = "__zoneId";
        public static final String FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK = "__resourcePoolLink";
        public static final String FIELD_NAME_CUSTOM_PROP_REGION_ID = "__regionId";
        private static final String FIELD_NAME_CUSTOM_PROP_DISK_NAME = "__diskName";
        private static final String FIELD_NAME_CUSTOM_PROP_NETWORK_LINK = "__networkStateLink";

        public static enum SubStage {
            CREATED,
            CONTEXT_PREPARED,
            COMPUTE_DESCRIPTION_RECONFIGURED,
            SELECT_PLACEMENT_COMPUTES,
            START_COMPUTE_ALLOCATION,
            COMPUTE_ALLOCATION_COMPLETED,
            COMPLETED,
            ERROR;
        }

        @Documentation(description = "The description that defines the requested resource.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        @Documentation(description = "Type of resource to create")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String resourceType;

        @Documentation(description = "(Required) the groupResourcePlacementState that links to ResourcePool")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL, LINK }, indexing = STORE_ONLY)
        public String groupResourcePlacementLink;

        @Documentation(description = "(Optional) the resourcePoolLink to ResourcePool")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL, LINK }, indexing = STORE_ONLY)
        public String resourcePoolLink;

        @Documentation(description = "(Required) Number of resources to provision. ")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public Long resourceCount;

        @Documentation(description = "Set by the task with the links of the provisioned resources.")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL, OPTIONAL }, indexing = STORE_ONLY)
        public List<String> resourceLinks;

        // Service use fields:

        // links to placement computes where to provision the requested resources
        // the size of the collection equals the requested resource count
        @PropertyOptions(usage = { SERVICE_USE, LINKS }, indexing = STORE_ONLY)
        public Collection<String> selectedComputePlacementLinks;

        @PropertyOptions(usage = { SERVICE_USE, LINK }, indexing = STORE_ONLY)
        public String endpointLink;

        @PropertyOptions(usage = { SERVICE_USE, LINK }, indexing = STORE_ONLY)
        public String endpointComputeStateLink;

        @PropertyOptions(usage = { SERVICE_USE, LINK }, indexing = STORE_ONLY)
        public String environmentLink;

        @PropertyOptions(usage = { SERVICE_USE }, indexing = STORE_ONLY)
        public String endpointType;
    }

    public ComputeAllocationTaskService() {
        super(ComputeAllocationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    protected void handleStartedStagePatch(ComputeAllocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            prepareContext(state, this.computeDescription, null, null, null);
            break;
        case CONTEXT_PREPARED:
            configureComputeDescription(state, this.computeDescription, null, null);
            break;
        case COMPUTE_DESCRIPTION_RECONFIGURED:
            createOsDiskState(state, SubStage.SELECT_PLACEMENT_COMPUTES, null,
                    this.computeDescription);
            break;
        case SELECT_PLACEMENT_COMPUTES:
            selectPlacement(state);
            break;
        case START_COMPUTE_ALLOCATION:
            allocateComputeState(state, this.computeDescription, null);
            break;
        case COMPUTE_ALLOCATION_COMPLETED:
            queryForAllocatedResources(state);
            break;
        case COMPLETED:
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
            ComputeAllocationTaskState patchBody,
            ComputeAllocationTaskState currentState) {

        currentState.resourceLinks = mergeLists(
                currentState.resourceLinks, patchBody.resourceLinks);

        currentState.selectedComputePlacementLinks = mergeProperty(
                currentState.selectedComputePlacementLinks,
                patchBody.selectedComputePlacementLinks);

        currentState.endpointLink = mergeProperty(
                currentState.endpointLink,
                patchBody.endpointLink);

        currentState.resourcePoolLink = mergeProperty(
                currentState.resourcePoolLink,
                patchBody.resourcePoolLink);

        currentState.endpointComputeStateLink = mergeProperty(
                currentState.endpointComputeStateLink,
                patchBody.endpointComputeStateLink);

        currentState.environmentLink = mergeProperty(
                currentState.environmentLink,
                patchBody.environmentLink);

        currentState.endpointType = mergeProperty(
                currentState.endpointType,
                patchBody.endpointType);

        return false;
    }

    @Override
    protected void validateStateOnStart(ComputeAllocationTaskState state)
            throws IllegalArgumentException {

        assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");
        if (state.groupResourcePlacementLink == null && state.resourcePoolLink == null) {
            throw new IllegalArgumentException("'groupResourcePlacementLink' cannot be empty.");
        }

        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ComputeAllocationTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated compute resources.");
        }
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        List<String> resourceLinks;
    }

    private void prepareContext(ComputeAllocationTaskState state,
            ComputeDescription computeDesc, ResourcePoolState resourcePool,
            EndpointState endpoint, EnvironmentMappingState environment) {

        if (resourcePool == null) {
            getResourcePool(state,
                    (pool) -> prepareContext(state, computeDesc, pool, endpoint, environment));
            return;
        }

        if (computeDesc == null) {
            getComputeDescription(state.resourceDescriptionLink, (compDesc) -> prepareContext(state,
                    compDesc, resourcePool, endpoint, environment));
            return;
        }

        // merge compute description properties over the resource pool description properties
        Map<String, String> customProperties = mergeCustomProperties(resourcePool.customProperties,
                computeDesc.customProperties);

        // merge request/allocation properties over the previously merged properties
        customProperties = mergeCustomProperties(customProperties, state.customProperties);

        String endpointLink = customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME);

        if (endpoint == null) {
            getServiceState(endpointLink, EndpointState.class,
                    (ep) -> prepareContext(state, computeDesc, resourcePool, ep, environment));
            return;
        }

        if (environment == null) {
            queryEnvironment(state, endpoint.endpointType, state.tenantLinks,
                    (env) -> prepareContext(state, computeDesc, resourcePool, endpoint, env));
            return;
        }

        ComputeAllocationTaskState body = createUpdateSubStageTask(state,
                SubStage.CONTEXT_PREPARED);

        body.customProperties = customProperties;

        body.endpointLink = endpointLink;
        body.endpointComputeStateLink = endpoint.computeLink;
        body.environmentLink = environment.documentSelfLink;
        body.endpointType = endpoint.endpointType;
        body.resourcePoolLink = resourcePool.documentSelfLink;

        if (body.getCustomProperty(FIELD_NAME_CONTEXT_ID_KEY) == null) {
            body.addCustomProperty(FIELD_NAME_CONTEXT_ID_KEY, getSelfId());
        }
        if (body.getCustomProperty(
                ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK) == null) {
            body.addCustomProperty(
                    ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK,
                    resourcePool.documentSelfLink);
        }

        sendSelfPatch(body);
    }

    private void createOsDiskState(ComputeAllocationTaskState state,
            SubStage nextStage, EnvironmentMappingState mapping, ComputeDescription computeDesc) {
        if (state.customProperties.containsKey(ComputeConstants.CUSTOM_PROP_DISK_LINK)) {
            sendSelfPatch(createUpdateSubStageTask(state, nextStage));
            return;
        }
        if (mapping == null) {
            getServiceState(state.environmentLink, EnvironmentMappingState.class,
                    (envMapping) -> createOsDiskState(state, nextStage, envMapping, computeDesc));
            return;
        }
        if (computeDesc == null) {
            getServiceState(state.resourceDescriptionLink, ComputeDescription.class,
                    (compDesc) -> createOsDiskState(state, nextStage, mapping, compDesc));
            return;
        }

        try {
            DiskState rootDisk = new DiskState();
            rootDisk.id = UUID.randomUUID().toString();
            rootDisk.documentSelfLink = rootDisk.id;
            String diskName = state
                    .getCustomProperty(
                            ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_DISK_NAME);
            if (diskName == null) {
                diskName = "Default disk";
            }
            rootDisk.name = diskName;
            rootDisk.type = DiskType.HDD;
            rootDisk.bootOrder = 1;

            String absImageId = state.getCustomProperty(
                    ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME);
            String imageId = mapping.getMappingValue("imageType", absImageId);

            rootDisk.sourceImageReference = URI.create(imageId);
            rootDisk.bootConfig = new DiskState.BootConfig();
            rootDisk.bootConfig.label = "cidata";

            PropertyMapping values = mapping.properties.get("bootDiskProperties");
            if (values != null) {
                rootDisk.customProperties = new HashMap<>();
                for (Entry<String, String> entry : values.mappings.entrySet()) {
                    rootDisk.customProperties.put(entry.getKey(), entry.getValue());
                }
            }

            String content = computeDesc.customProperties
                    .get(ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME);
            logInfo("Cloud config file to use [%s]", content);
            DiskState.BootConfig.FileEntry file = new DiskState.BootConfig.FileEntry();
            file.path = "user-data";
            file.contents = content;
            rootDisk.bootConfig.files = new DiskState.BootConfig.FileEntry[] { file };

            sendRequest(Operation
                    .createPost(UriUtils.buildUri(getHost(), DiskService.FACTORY_LINK))
                    .setBody(rootDisk)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Resource can't be created: " + rootDisk.documentSelfLink,
                                    e);
                            return;
                        }
                        DiskState diskState = o.getBody(DiskState.class);
                        logInfo("Resource created: %s", diskState.documentSelfLink);
                        ComputeAllocationTaskState patch = createUpdateSubStageTask(state,
                                nextStage);
                        patch.addCustomProperty(
                                ComputeConstants.CUSTOM_PROP_DISK_LINK,
                                diskState.documentSelfLink);
                        sendSelfPatch(patch);
                    }));

        } catch (Throwable t) {
            failTask("Failure creating DiskState", t);
        }
    }

    private void queryForAllocatedResources(ComputeAllocationTaskState state) {
        // TODO pmitrov: try to remove this and retrieve the newly created ComputeState links
        // directly from the POST request response

        String contextId = state.getCustomProperty(FIELD_NAME_CONTEXT_ID_KEY);

        Query.Builder queryBuilder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_DESCRIPTION_LINK,
                        state.resourceDescriptionLink)
                // TODO: Right now the adapters assume the parentLink is pointing to endpoint
                // compute. We have to design how to assign placement compute.
                // .addInClause(ComputeState.FIELD_NAME_PARENT_LINK,
                // state.selectedComputePlacementLinks)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK,
                        state.endpointComputeStateLink)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        FIELD_NAME_CONTEXT_ID_KEY, contextId);

        QueryTask q = QueryTask.Builder.create().setQuery(queryBuilder.build()).build();

        List<String> computeResourceLinks = new ArrayList<>(state.resourceCount.intValue());
        QueryTaskClientHelper
                .create(ComputeState.class)
                .setQueryTask(q)
                .setResultHandler((r, e) -> {
                    if (e != null) {
                        failTask("Failed to query for provisioned resources", e);
                        return;
                    } else if (r.hasResult()) {
                        computeResourceLinks.add(r.getDocumentSelfLink());
                    } else {
                        ComputeAllocationTaskState body = createUpdateSubStageTask(
                                state, SubStage.COMPLETED);
                        body.resourceLinks = computeResourceLinks;
                        sendSelfPatch(body);
                    }

                }).sendWith(getHost());
    }

    private void configureComputeDescription(ComputeAllocationTaskState state,
            ComputeDescription computeDesc, EnvironmentMappingState mapping,
            ComputeStateWithDescription expandedEndpointComputeState) {

        if (computeDesc == null) {
            getServiceState(state.resourceDescriptionLink, ComputeDescription.class,
                    (compDesc) -> configureComputeDescription(state, compDesc, mapping,
                            expandedEndpointComputeState));
            return;
        }
        if (mapping == null) {
            getServiceState(state.environmentLink, EnvironmentMappingState.class,
                    (envMapping) -> configureComputeDescription(state, computeDesc, envMapping,
                            expandedEndpointComputeState));
            return;
        }
        if (expandedEndpointComputeState == null) {
            getServiceState(state.endpointComputeStateLink, ComputeStateWithDescription.class,
                    true,
                    (compState) -> configureComputeDescription(state, computeDesc, mapping,
                            compState));
            return;
        }

        String value = mapping.getMappingValue("instanceType", computeDesc.instanceType);
        if (value != null) {
            computeDesc.instanceType = value;
        }

        if (computeDesc.dataStoreId == null) {
            computeDesc.dataStoreId = mapping.getMappingValue("placement", "dataStoreId");
        }

        if (computeDesc.authCredentialsLink == null) {
            computeDesc.authCredentialsLink = mapping.getMappingValue("authentication",
                    "guestAuthLink");
        }
        if (computeDesc.zoneId == null) {
            computeDesc.zoneId = mapping.getMappingValue("placement", "zoneId");
        }

        final ComputeDescription endpointComputeDescription = expandedEndpointComputeState.description;
        computeDesc.instanceAdapterReference = endpointComputeDescription.instanceAdapterReference;
        computeDesc.bootAdapterReference = endpointComputeDescription.bootAdapterReference;
        computeDesc.powerAdapterReference = endpointComputeDescription.powerAdapterReference;
        if (computeDesc.zoneId == null) {
            computeDesc.zoneId = endpointComputeDescription.zoneId;
        }
        computeDesc.regionId = endpointComputeDescription.regionId;
        computeDesc.environmentName = endpointComputeDescription.environmentName;

        if (enableContainerHost(state.customProperties)) {
            computeDesc.supportedChildren = new ArrayList<>(
                    Arrays.asList(ComputeType.DOCKER_CONTAINER.toString()));
            if (!state.customProperties
                    .containsKey(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME)) {
                state.customProperties.put(
                        ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                        DockerAdapterType.API.name());
            }
        }

        computeDesc.customProperties = mergeCustomProperties(computeDesc.customProperties,
                state.customProperties);
        computeDesc.customProperties.put(ComputeConstants.CUSTOM_PROP_ENDPOINT_TYPE_NAME,
                state.endpointType);

        SubStage nextStage = computeDesc.customProperties
                .containsKey(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME)
                        ? SubStage.COMPUTE_DESCRIPTION_RECONFIGURED
                        : SubStage.SELECT_PLACEMENT_COMPUTES;



        ComputeDescriptionEnhancers.build(this).enhance(computeDesc, (cd, t) -> {
            if (t != null) {
                failTask("Failed patching compute description : "
                        + Utils.toString(t), t);
                return;
            }
            Operation.createPut(this, state.resourceDescriptionLink)
                    .setBody(computeDesc)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failed patching compute description : " + Utils.toString(e),
                                    null);
                            return;
                        }
                        this.computeDescription = o.getBody(ComputeDescription.class);
                        ComputeAllocationTaskState patchState = createUpdateSubStageTask(state,
                                nextStage);
                        patchState.customProperties = this.computeDescription.customProperties;
                        sendSelfPatch(patchState);
                    })
                    .sendWith(this);
        });
    }

    static boolean enableContainerHost(Map<String, String> customProperties) {
        return customProperties
                .containsKey(ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME);
    }

    private void selectPlacement(ComputeAllocationTaskState state) {

        ComputePlacementSelectionTaskState computePlacementSelection = new ComputePlacementSelectionTaskState();

        computePlacementSelection.documentSelfLink = getSelfId();
        computePlacementSelection.computeDescriptionLink = state.resourceDescriptionLink;
        computePlacementSelection.resourceCount = state.resourceCount;
        computePlacementSelection.resourcePoolLink = state.resourcePoolLink;
        computePlacementSelection.tenantLinks = state.tenantLinks;
        computePlacementSelection.contextId = getContextId(state);
        computePlacementSelection.customProperties = state.customProperties;
        computePlacementSelection.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, SubStage.START_COMPUTE_ALLOCATION,
                TaskStage.STARTED, SubStage.ERROR);
        computePlacementSelection.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ComputePlacementSelectionTaskService.FACTORY_LINK)
                .setBody(computePlacementSelection)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating compute placement selection task", e);
                        return;
                    }
                }));
    }

    private void allocateComputeState(ComputeAllocationTaskState state,
            ComputeDescription computeDescription, ServiceTaskCallback taskCallback) {

        if (computeDescription == null) {
            getComputeDescription(state.resourceDescriptionLink,
                    (compDesc) -> allocateComputeState(state, compDesc, taskCallback));
            return;
        }
        if (taskCallback == null) {
            // recurse after creating a sub task
            createCounterSubTaskCallback(state, state.resourceCount, false,
                    SubStage.COMPUTE_ALLOCATION_COMPLETED,
                    (serviceTask) -> allocateComputeState(state, computeDescription, serviceTask));
            return;
        }

        logInfo("Allocation request for %s machines", state.resourceCount);

        if (state.selectedComputePlacementLinks.size() < state.resourceCount) {
            failTask(String.format(
                    "Not enough placement links provided (%d) for the requested resource count (%d)",
                    state.selectedComputePlacementLinks.size(), state.resourceCount), null);
            return;
        }

        if (state.customProperties == null) {
            state.customProperties = new HashMap<>();
        }

        String contextId = state.getCustomProperty(FIELD_NAME_CONTEXT_ID_KEY);
        state.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        state.customProperties.put(ComputeConstants.FIELD_NAME_COMPOSITE_COMPONENT_LINK_KEY,
                UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, contextId));
        state.customProperties.put(ComputeConstants.COMPUTE_HOST_PROP_NAME, "true");

        // for human debugging reasons only, prefix the compute host resource id
        // with the allocation
        // task id
        String taskId = getSelfId();

        logInfo("Creating %d provision tasks, reporting through sub task %s",
                state.resourceCount, taskCallback.serviceSelfLink);
        String name = computeDescription.name;
        if (state.customProperties.get(CUSTOM_DISPLAY_NAME) != null) {
            name = state.customProperties.get(CUSTOM_DISPLAY_NAME);
        }

        Iterator<String> placementComputeLinkIterator =
                state.selectedComputePlacementLinks.iterator();
        for (int i = 0; i < state.resourceCount; i++) {
            String computeResourceId = taskId + ID_DELIMITER_CHAR + i;
            String computeResourceLink = UriUtils.buildUriPath(
                    ComputeService.FACTORY_LINK, computeResourceId);

            createComputeResource(
                    state,
                    computeDescription,
                    state.endpointComputeStateLink,
                    placementComputeLinkIterator.next(),
                    computeResourceId,
                    computeResourceLink, name + i,
                    null, null, taskCallback);
        }
    }

    private void createComputeResource(ComputeAllocationTaskState state, ComputeDescription cd,
            String parentLink, String placementLink,
            String computeResourceId, String computeResourceLink, String computeName,
            List<String> diskLinks,
            List<String> networkLinks, ServiceTaskCallback taskCallback) {
        if (diskLinks == null) {
            createDiskResources(state, taskCallback, dl -> createComputeResource(
                    state, cd, parentLink, placementLink, computeResourceId, computeResourceLink,
                    computeName, dl, networkLinks, taskCallback));
            return;
        }

        if (networkLinks == null) {
            createNetworkResources(state, taskCallback, nl -> createComputeResource(
                    state, cd, parentLink, placementLink, computeResourceId, computeResourceLink,
                    computeName, diskLinks, nl, taskCallback));
            return;
        }

        if (cd.tagLinks == null) {
            createTags(state, cd, tl -> {
                cd.tagLinks = tl;
                createComputeResource(state, cd, parentLink, placementLink, computeResourceId,
                        computeResourceLink, computeName, diskLinks, networkLinks, taskCallback);
            });
            return;
        }

        createComputeHost(state, cd, parentLink, placementLink, computeResourceId, computeResourceLink,
                computeName, diskLinks, networkLinks, taskCallback);
    }

    private void createTags(ComputeAllocationTaskState state, ComputeDescription cd,
            Consumer<Set<String>> tagLinksConsumer) {
        String tagsString = cd.customProperties.get(ComputeConstants.CUSTOM_PROP_TAGS_KEY);
        String[] parts = tagsString != null ? tagsString.split(",") : new String[0];
        if (parts.length == 0) {
            tagLinksConsumer.accept(new HashSet<>());
            return;
        }

        List<TagState> tags = new ArrayList<>();
        Set<String> tagLinks = new HashSet<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            // we want only the first "~", if there are more we assume it's part of the value
            String[] keyValue = part.split("~", 2);
            TagState tag = new TagState();
            if (keyValue.length > 0) {
                tag.key = keyValue[0];
            }
            if (keyValue.length == 2) {
                tag.value = keyValue[1];
            }
            tag.tenantLinks = cd.tenantLinks;
            tag.documentSelfLink = TagFactoryService.generateSelfLink(tag);
            tagLinks.add(tag.documentSelfLink);
            tags.add(tag);
        }
        tagLinksConsumer.accept(tagLinks);

        if (tags.isEmpty()) {
            return;
        }

        Stream<Operation> tagsOps = tags.stream()
                .map(t -> Operation.createPost(this, TagService.FACTORY_LINK)
                        .setBody(t));
        OperationJoin.create(tagsOps).setCompletion((ops, exs) -> {
            if (exs != null && !exs.isEmpty()) {
                logWarning("Unable to create tags, reason %s", Utils.toString(exs));
                return;
            }
        }).sendWith(this);
    }

    private void createComputeHost(ComputeAllocationTaskState state, ComputeDescription cd,
            String parentLink, String placementLink,
            String computeResourceId, String computeResourceLink, String computeName,
            List<String> diskLinks, List<String> networkLinks, ServiceTaskCallback taskCallback) {
        ComputeService.ComputeState resource = new ComputeService.ComputeState();
        resource.id = computeResourceId;
        resource.parentLink = parentLink;
        resource.name = computeName;
        resource.descriptionLink = state.resourceDescriptionLink;
        resource.resourcePoolLink = state.getCustomProperty(
                ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK);
        resource.diskLinks = diskLinks;
        resource.networkInterfaceLinks = networkLinks;
        resource.customProperties = new HashMap<>(state.customProperties);
        if (state.groupResourcePlacementLink != null) {
            resource.customProperties.put(ComputeConstants.GROUP_RESOURCE_PLACEMENT_LINK_NAME,
                    state.groupResourcePlacementLink);
        }
        resource.customProperties.put(ComputeProperties.PLACEMENT_LINK, placementLink);
        resource.customProperties.put("computeType", "VirtualMachine");
        resource.tenantLinks = state.tenantLinks;
        resource.documentSelfLink = computeResourceLink;
        resource.powerState = ComputeService.PowerState.ON;
        resource.tagLinks = cd.tagLinks;

        sendRequest(Operation
                .createPost(this, ComputeService.FACTORY_LINK)
                .setBody(resource)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logSevere(
                                        "Failure creating compute resource: %s",
                                        Utils.toString(e));
                                completeSubTasksCounter(taskCallback, e);
                                return;
                            }
                            logInfo("Compute under %s, was created",
                                    o.getBody(ComputeState.class).documentSelfLink);
                            completeSubTasksCounter(taskCallback, null);
                        }));
    }

    /**
     * Create disks to attach to the compute resource. Use the disk description links to figure out
     * what type of disks to create.
     *
     * @param state
     * @param taskCallback
     * @param diskLinksConsumer
     */
    private void createDiskResources(ComputeAllocationTaskState state,
            ServiceTaskCallback taskCallback, Consumer<List<String>> diskLinksConsumer) {
        String diskDescLink = state.getCustomProperty(ComputeConstants.CUSTOM_PROP_DISK_LINK);
        if (diskDescLink == null) {
            diskLinksConsumer.accept(new ArrayList<>());
            return;
        }

        ConcurrentLinkedQueue<String> diskLinks = new ConcurrentLinkedQueue<>();
        int expected = 1;
        AtomicInteger counter = new AtomicInteger();
        CompletionHandler diskCreateCompletion = (o, e) -> {
            if (e != null) {
                logWarning("Failure creating disk: %s", e.toString());
                completeSubTasksCounter(taskCallback, e);
                return;
            }

            DiskService.DiskState newDiskState = o.getBody(DiskService.DiskState.class);

            diskLinks.add(newDiskState.documentSelfLink);
            if (counter.incrementAndGet() >= expected) {
                // we have created all the disks. Now create the compute host
                // resource
                diskLinksConsumer.accept(diskLinks.stream().collect(Collectors.toList()));
            }
        };

        // get all disk descriptions first, then create new disk using the
        // description/template
        sendRequest(Operation.createGet(this, diskDescLink).setCompletion(
                (o, e) -> {
                    if (e != null) {
                        logWarning(
                                "Failure getting disk description %s: %s",
                                diskDescLink, e.toString());
                        completeSubTasksCounter(taskCallback, e);
                        return;
                    }

                    DiskService.DiskState templateDisk = o.getBody(DiskService.DiskState.class);
                    String link = templateDisk.documentSelfLink;
                    // create a new disk based off the template but use a
                    // unique ID
                    templateDisk.id = UUID.randomUUID().toString();
                    templateDisk.documentSelfLink = null;
                    templateDisk.tenantLinks = state.tenantLinks;
                    if (templateDisk.customProperties == null) {
                        templateDisk.customProperties = new HashMap<>();
                    }
                    templateDisk.customProperties.put("__templateDiskLink", link);
                    sendRequest(Operation
                            .createPost(this, DiskService.FACTORY_LINK)
                            .setBody(templateDisk)
                            .setCompletion(diskCreateCompletion));
                }));
    }

    private void createNetworkResources(ComputeAllocationTaskState state,
            ServiceTaskCallback taskCallback, Consumer<List<String>> networkLinksConsumer) {
        String netDescLink = state
                .getCustomProperty(ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_NETWORK_LINK);
        if (netDescLink == null) {
            networkLinksConsumer.accept(new ArrayList<>());
            return;
        }

        ConcurrentLinkedQueue<String> networkLinks = new ConcurrentLinkedQueue<>();
        int expected = 1;
        AtomicInteger counter = new AtomicInteger();
        CompletionHandler networkInterfaceCreateCompletion = (o, e) -> {
            if (e != null) {
                logWarning("Failure creating network interfaces: %s", e.toString());
                completeSubTasksCounter(taskCallback, e);
                return;
            }

            NetworkInterfaceService.NetworkInterfaceState newInterfaceState = o
                    .getBody(NetworkInterfaceService.NetworkInterfaceState.class);

            networkLinks.add(newInterfaceState.documentSelfLink);
            if (counter.incrementAndGet() >= expected) {
                // we have created all the disks. Now create the compute host
                // resource
                networkLinksConsumer.accept(networkLinks.stream().collect(Collectors.toList()));
            }
        };

        // get all network descriptions first, then create new network
        // interfaces using the
        // description/template
        sendRequest(Operation
                .createGet(this, netDescLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(
                                        "Failure getting network description %s: %s",
                                        netDescLink, e.toString());
                                completeSubTasksCounter(taskCallback, e);
                                return;
                            }

                            NetworkInterfaceService.NetworkInterfaceState templateNetwork = o
                                    .getBody(
                                            NetworkInterfaceService.NetworkInterfaceState.class);
                            templateNetwork.id = UUID.randomUUID().toString();
                            templateNetwork.documentSelfLink = templateNetwork.id;
                            templateNetwork.tenantLinks = state.tenantLinks;
                            // create a new network based off the template
                            // but use a unique ID
                            sendRequest(Operation
                                    .createPost(
                                            this,
                                            NetworkInterfaceService.FACTORY_LINK)
                                    .setBody(templateNetwork)
                                    .setCompletion(
                                            networkInterfaceCreateCompletion));
                        }));
    }

    private void getResourcePool(ComputeAllocationTaskState state,
            Consumer<ResourcePoolState> callbackFunction) {
        if (state.resourcePoolLink != null) {
            Operation.createGet(this, state.resourcePoolLink)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failure retrieving ResourcePool: " + state.resourcePoolLink,
                                    e);
                            return;
                        }
                        ResourcePoolState resourcePool = o.getBody(ResourcePoolState.class);
                        callbackFunction.accept(resourcePool);
                    })
                    .sendWith(this);
            return;
        }
        Operation opRQL = Operation.createGet(this, state.groupResourcePlacementLink);
        Operation opRP = Operation.createGet(this, null);
        OperationSequence.create(opRQL)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        failTask(
                                "Failure retrieving GroupResourcePlacement: " + Utils.toString(exs),
                                null);
                        return;
                    }
                    Operation o = ops.get(opRQL.getId());

                    GroupResourcePlacementState placementState = o
                            .getBody(GroupResourcePlacementState.class);
                    if (placementState.resourcePoolLink == null) {
                        failTask(null, new IllegalStateException(
                                "Placement state has no resourcePoolLink"));
                        return;
                    }
                    opRP.setUri(UriUtils.buildUri(getHost(), placementState.resourcePoolLink));
                }).next(opRP).setCompletion((ops, exs) -> {
                    if (exs != null) {
                        failTask("Failure retrieving ResourcePool: " + Utils.toString(exs), null);
                        return;
                    }
                    Operation o = ops.get(opRP.getId());
                    ResourcePoolState resourcePool = o.getBody(ResourcePoolState.class);
                    callbackFunction.accept(resourcePool);
                }).sendWith(this);
    }

    private void queryEnvironment(ComputeAllocationTaskState state,
            String endpointType, List<String> tenantLinks,
            Consumer<EnvironmentMappingState> callbackFunction) {

        QueryTask.Query endpointTypeClause = new QueryTask.Query()
                .setTermPropertyName(EnvironmentMappingState.FIELD_NAME_ENDPOINT_TYPE_NAME)
                .setTermMatchValue(endpointType);

        if (tenantLinks == null || tenantLinks.isEmpty()) {
            logInfo("Quering for global environments for endpoint type: [%s]...", endpointType);
        } else {
            logInfo("Quering for group [%s] environments for endpoint type: [%s]...",
                    tenantLinks, endpointType);
        }
        Query tenantLinksQuery = QueryUtil.addTenantAndGroupClause(tenantLinks);

        QueryTask q = QueryUtil.buildQuery(EnvironmentMappingState.class, false,
                endpointTypeClause,
                tenantLinksQuery);
        q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;
        QueryUtil.addExpandOption(q);

        ServiceDocumentQuery<EnvironmentMappingState> query = new ServiceDocumentQuery<>(getHost(),
                EnvironmentMappingState.class);
        List<EnvironmentMappingState> environments = new ArrayList<>();
        query.query(q, (r) -> {
            if (r.hasException()) {
                failTask("Exception while quering for enviroment mappings", r.getException());
            } else if (r.hasResult()) {
                environments.add(r.getResult());
            } else {
                if (environments.isEmpty()) {
                    if (tenantLinks != null && !tenantLinks.isEmpty()) {
                        ArrayList<String> subLinks = new ArrayList<>(tenantLinks);
                        subLinks.remove(tenantLinks.size() - 1);
                        queryEnvironment(state, endpointType, subLinks, callbackFunction);
                    } else {
                        failTask("No available environment mappings for endpoint type: "
                                + endpointType, null);
                    }
                    return;
                }
                callbackFunction.accept(environments.get(0));
            }
        });
    }

    private void getComputeDescription(String uriLink,
            Consumer<ComputeDescription> callbackFunction) {
        if (this.computeDescription != null) {
            callbackFunction.accept(computeDescription);
            return;
        }
        getServiceState(uriLink, ComputeDescription.class, cd -> {
            this.computeDescription = cd;
            callbackFunction.accept(cd);
        });
    }

    private <T extends ServiceDocument> void getServiceState(String uriLink, Class<T> type,
            Consumer<T> callbackFunction) {
        getServiceState(uriLink, type, false, callbackFunction);
    }

    private <T extends ServiceDocument> void getServiceState(String uriLink, Class<T> type,
            boolean expand,
            Consumer<T> callbackFunction) {
        logInfo("Loading state for %s", uriLink);
        URI uri = UriUtils.buildUri(this.getHost(), uriLink);
        if (expand) {
            uri = UriUtils.buildExpandLinksQueryUri(uri);
        }
        sendRequest(Operation.createGet(uri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure state for " + uriLink, e);
                        return;
                    }

                    T state = o.getBody(type);
                    callbackFunction.accept(state);
                }));
    }
}
