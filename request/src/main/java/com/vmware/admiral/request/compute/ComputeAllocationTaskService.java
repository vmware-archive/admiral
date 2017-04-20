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

import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINKS;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.request.ResourceNamePrefixTaskService;
import com.vmware.admiral.request.ResourceNamePrefixTaskService.ResourceNamePrefixTaskState;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState.SubStage;
import com.vmware.admiral.request.compute.ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState;
import com.vmware.admiral.request.compute.enhancer.ComputeDescriptionEnhancers;
import com.vmware.admiral.request.compute.enhancer.Enhancer.EnhanceContext;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

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

        public static final String FIELD_NAME_CUSTOM_PROP_ZONE = "__zoneId";
        public static final String FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK = "__resourcePoolLink";
        public static final String FIELD_NAME_CUSTOM_PROP_REGION_ID = "__regionId";
        @Documentation(description = "The description that defines the requested resource.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED, LINK }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;
        @Documentation(description = "Type of resource to create")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String resourceType;
        @Documentation(description = "(Required) the groupResourcePlacementState that links to ResourcePool")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL, LINK }, indexing = STORE_ONLY)
        public String groupResourcePlacementLink;
        @Documentation(description = "(Optional) the resourcePoolLink to ResourcePool")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String resourcePoolLink;
        @Documentation(description = "(Required) Number of resources to provision. ")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public Long resourceCount;
        @Documentation(description = "Set by the task with the links of the provisioned resources.")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL, OPTIONAL }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;
        // links to placement computes where to provision the requested resources
        // the size of the collection equals the requested resource count
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL,
                LINKS }, indexing = STORE_ONLY)
        public Collection<HostSelection> selectedComputePlacementHosts;

        // Service use fields:
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String endpointLink;
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String endpointComputeStateLink;
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public List<String> profileLinks;
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String endpointType;

        /** (Internal) Set by task after resource name prefixes requested. */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceNames;

        public static enum SubStage {
            CREATED,
            CONTEXT_PREPARED,
            RESOURCES_NAMES,
            SELECT_PLACEMENT_COMPUTES,
            START_COMPUTE_ALLOCATION,
            COMPUTE_ALLOCATION_COMPLETED,
            COMPLETED,
            ERROR;
        }
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    public ComputeAllocationTaskService() {
        super(ComputeAllocationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    static boolean enableContainerHost(Map<String, String> customProperties) {
        return customProperties
                .containsKey(ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME);
    }

    @Override
    protected void handleStartedStagePatch(ComputeAllocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            prepareContext(state, this.computeDescription, null, null, null);
            break;
        case CONTEXT_PREPARED:
            configureComputeDescription(state, this.computeDescription, null);
            break;
        case RESOURCES_NAMES:
            createResourcePrefixNameSelectionTask(state, this.computeDescription);
            break;
        case SELECT_PLACEMENT_COMPUTES:
            selectPlacement(state);
            break;
        case START_COMPUTE_ALLOCATION:
            allocateComputeState(state, this.computeDescription, null, null);
            break;
        case COMPUTE_ALLOCATION_COMPLETED:
            queryForAllocatedResources(state);
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
    protected void validateStateOnStart(ComputeAllocationTaskState state)
            throws IllegalArgumentException {
        if (state.groupResourcePlacementLink == null && state.resourcePoolLink == null) {
            throw new LocalizableValidationException(
                    "'groupResourcePlacementLink' and 'resourcePoolLink' cannot be both empty.",
                    "request.compute.allocation.links.empty");
        }

        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.",
                    "request.resource-count.zero");
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

    private void prepareContext(ComputeAllocationTaskState state,
            ComputeDescription computeDesc, ResourcePoolState resourcePool,
            EndpointState endpoint, List<String> profileLinks) {

        if (resourcePool == null) {
            getResourcePool(state,
                    (pool) -> prepareContext(state, computeDesc, pool, endpoint, profileLinks));
            return;
        }

        if (computeDesc == null) {
            getComputeDescription(state.resourceDescriptionLink, (compDesc) -> prepareContext(state,
                    compDesc, resourcePool, endpoint, profileLinks));
            return;
        }

        // merge compute description properties over the resource pool description properties and
        // request/allocation properties
        Map<String, String> customProperties = mergeCustomProperties(
                mergeCustomProperties(resourcePool.customProperties, computeDesc.customProperties),
                state.customProperties);

        String endpointLink = customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME);

        if (endpoint == null) {
            getServiceState(endpointLink, EndpointState.class,
                    (ep) -> prepareContext(state, computeDesc, resourcePool, ep, profileLinks));
            return;
        }

        if (profileLinks == null) {
            String contextId = RequestUtils.getContextId(state);
            NetworkProfileQueryUtils.getProfilesForComputeNics(getHost(),
                    UriUtils.buildUri(getHost(), getSelfLink()), state.tenantLinks, contextId,
                    computeDesc,
                    (networkProfileLinks, e) -> {
                        if (e != null) {
                            failTask("Error getting profile constraints: ", e);
                            return;
                        }
                        queryProfile(state, endpoint,
                                QueryUtil.getTenantLinks(state.tenantLinks), networkProfileLinks,
                                (links) -> prepareContext(state, computeDesc, resourcePool,
                                        endpoint, links));
                    });
            return;
        }

        proceedTo(SubStage.CONTEXT_PREPARED, s -> {
            s.customProperties = customProperties;

            s.endpointLink = endpointLink;
            s.endpointComputeStateLink = endpoint.computeLink;
            s.profileLinks = profileLinks;
            s.endpointType = endpoint.endpointType;
            s.resourcePoolLink = resourcePool.documentSelfLink;

            if (s.getCustomProperty(FIELD_NAME_CONTEXT_ID_KEY) == null) {
                s.addCustomProperty(FIELD_NAME_CONTEXT_ID_KEY, getSelfId());
            }
            if (s.getCustomProperty(
                    ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK) == null) {
                s.addCustomProperty(
                        ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK,
                        resourcePool.documentSelfLink);
            }
        });
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

        Set<String> computeResourceLinks = new HashSet<>(state.resourceCount.intValue());
        new ServiceDocumentQuery<>(
                getHost(), ComputeState.class).query(q, (r) -> {
                    if (r.hasException()) {
                        failTask("Failed to query for provisioned resources", r.getException());
                        return;
                    } else if (r.hasResult()) {
                        computeResourceLinks.add(r.getDocumentSelfLink());
                    } else {
                        proceedTo(SubStage.COMPLETED, s -> {
                            s.resourceLinks = computeResourceLinks;
                        });
                    }
                });
    }

    private void configureComputeDescription(ComputeAllocationTaskState state,
            ComputeDescription computeDesc,
            ComputeStateWithDescription expandedEndpointComputeState) {

        if (computeDesc == null) {
            getServiceState(state.resourceDescriptionLink, ComputeDescription.class,
                    (compDesc) -> configureComputeDescription(state, compDesc,
                            expandedEndpointComputeState));
            return;
        }
        if (expandedEndpointComputeState == null) {
            getServiceState(state.endpointComputeStateLink, ComputeStateWithDescription.class,
                    true,
                    (compState) -> configureComputeDescription(state, computeDesc, compState));
            return;
        }

        final ComputeDescription endpointComputeDescription = expandedEndpointComputeState.description;
        computeDesc.instanceAdapterReference = endpointComputeDescription.instanceAdapterReference;
        computeDesc.bootAdapterReference = endpointComputeDescription.bootAdapterReference;
        computeDesc.powerAdapterReference = endpointComputeDescription.powerAdapterReference;
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

        EnhanceContext context = new EnhanceContext();
        context.endpointLink = state.endpointLink;
        context.resourcePoolLink = state.resourcePoolLink;
        context.regionId = endpointComputeDescription.regionId;
        context.zoneId = endpointComputeDescription.zoneId;
        context.endpointType = state.endpointType;
        context.imageType = computeDesc.customProperties
                .remove(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME);

        enhanceComputeDescription(computeDesc, context, state, new ArrayList<>(state.profileLinks));

    }

    private void enhanceComputeDescription(ComputeDescription computeDesc, EnhanceContext context,
            ComputeAllocationTaskState state, List<String> profileLinks) {
        context.profileLink = profileLinks.remove(0);
        context.profile = null;
        ComputeDescriptionEnhancers
                .build(getHost(), UriUtils.buildUri(getHost().getPublicUri(), getSelfLink()))
                .enhance(context, Utils.cloneObject(computeDesc))
                .whenComplete((cd, t) -> {
                    if (t != null) {
                        if (profileLinks.isEmpty()) {
                            failTask("Failed patching compute description : "
                                    + Utils.toString(t), t);
                        } else {
                            enhanceComputeDescription(computeDesc, context, state, profileLinks);
                        }
                        return;
                    }
                    cd.customProperties.put(ComputeConstants.CUSTOM_PROP_PROFILE_LINK_NAME,
                            context.profileLink);
                    Operation.createPut(this, state.resourceDescriptionLink)
                            .setBody(cd)
                            .setCompletion((o, e) -> {
                                if (e != null) {
                                    failTask("Failed patching compute description : "
                                            + Utils.toString(e),
                                            null);
                                    return;
                                }
                                this.computeDescription = o.getBody(ComputeDescription.class);
                                proceedTo(SubStage.RESOURCES_NAMES, s -> {
                                    s.customProperties = this.computeDescription.customProperties;
                                });
                            })
                            .sendWith(this);
                });
    }

    private void createResourcePrefixNameSelectionTask(ComputeAllocationTaskState state,
            ComputeDescription computeDescription) {
        if (computeDescription == null) {
            getComputeDescription(state.resourceDescriptionLink,
                    (desc) -> this.createResourcePrefixNameSelectionTask(state, desc));
            return;
        }

        // create resource prefix name selection tasks
        ResourceNamePrefixTaskState namePrefixTask = new ResourceNamePrefixTaskState();
        namePrefixTask.documentSelfLink = getSelfId();
        namePrefixTask.resourceCount = state.resourceCount;
        namePrefixTask.baseResourceNameFormat = ResourceNamePrefixService
                .getDefaultResourceNameFormat(computeDescription.name);
        namePrefixTask.tenantLinks = state.tenantLinks;

        namePrefixTask.customProperties = state.customProperties;
        namePrefixTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.SELECT_PLACEMENT_COMPUTES,
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

    private void selectPlacement(ComputeAllocationTaskState state) {
        String placementLink = state.customProperties.get(ComputeProperties.PLACEMENT_LINK);
        if (placementLink != null) {
            ArrayList<HostSelection> placementLinks = new ArrayList<>(
                    state.resourceCount.intValue());
            for (int i = 0; i < state.resourceCount; i++) {
                HostSelection hostSelection = new HostSelection();
                hostSelection.hostLink = placementLink;
                placementLinks.add(hostSelection);
            }
            proceedTo(SubStage.START_COMPUTE_ALLOCATION, s -> {
                s.selectedComputePlacementHosts = placementLinks;
            });
            return;
        }

        ComputePlacementSelectionTaskState computePlacementSelection = new ComputePlacementSelectionTaskState();

        computePlacementSelection.documentSelfLink = getSelfId();
        computePlacementSelection.computeDescriptionLink = state.resourceDescriptionLink;
        computePlacementSelection.resourceCount = state.resourceCount;
        computePlacementSelection.resourcePoolLinks = new ArrayList<>();
        computePlacementSelection.resourcePoolLinks.add(state.resourcePoolLink);
        computePlacementSelection.endpointLink = state.endpointLink;
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
            ComputeDescription computeDescription, ProfileStateExpanded profile,
            ServiceTaskCallback taskCallback) {

        if (computeDescription == null) {
            getComputeDescription(state.resourceDescriptionLink,
                    (compDesc) -> allocateComputeState(state, compDesc, profile, taskCallback));
            return;
        }
        if (profile == null) {
            String profileLink = computeDescription.customProperties
                    .get(ComputeConstants.CUSTOM_PROP_PROFILE_LINK_NAME);
            getServiceState(profileLink, ProfileStateExpanded.class, true,
                    p -> allocateComputeState(state, computeDescription, p, taskCallback));
            return;
        }
        if (taskCallback == null) {
            // recurse after creating a sub task
            createCounterSubTaskCallback(state, state.resourceCount, false,
                    SubStage.COMPUTE_ALLOCATION_COMPLETED,
                    (serviceTask) -> allocateComputeState(state, computeDescription, profile,
                            serviceTask));
            return;
        }

        logInfo("Allocation request for %s machines", state.resourceCount);

        if (state.selectedComputePlacementHosts.size() < state.resourceCount) {
            failTask(String.format(
                    "Not enough placement links provided (%d) for the requested resource count (%d)",
                    state.selectedComputePlacementHosts.size(), state.resourceCount), null);
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

        logInfo("Creating %d provision tasks, reporting through sub task %s",
                state.resourceCount, taskCallback.serviceSelfLink);

        Iterator<HostSelection> placementComputeLinkIterator = state.selectedComputePlacementHosts
                .iterator();
        Iterator<String> namesIterator = state.resourceNames.iterator();
        for (int i = 0; i < state.resourceCount; i++) {
            String name = namesIterator.next();
            String computeResourceId = buildResourceId(name);

            createComputeResource(state, computeDescription, profile,
                    state.endpointComputeStateLink, placementComputeLinkIterator.next().hostLink,
                    computeResourceId, name, null, null, taskCallback);
        }
    }

    private String buildResourceId(String resourceName) {
        return resourceName.replaceAll(" ", ID_DELIMITER_CHAR);
    }

    private void createComputeResource(ComputeAllocationTaskState state, ComputeDescription cd,
            ProfileStateExpanded profile, String parentLink, String placementLink,
            String computeResourceId, String computeName,
            List<String> diskLinks,
            List<String> networkLinks, ServiceTaskCallback taskCallback) {
        if (diskLinks == null) {
            createDiskResources(state, cd, taskCallback, dl -> createComputeResource(
                    state, cd, profile, parentLink, placementLink, computeResourceId, computeName,
                    dl, networkLinks, taskCallback));
            return;
        }

        if (networkLinks == null) {
            createNetworkResources(state, cd, profile, placementLink, taskCallback,
                    nl -> createComputeResource(state, cd, profile, parentLink, placementLink,
                            computeResourceId, computeName, diskLinks, nl, taskCallback));
            return;
        }

        createComputeHost(state, cd, parentLink, placementLink, computeResourceId, computeName,
                diskLinks, networkLinks, taskCallback);
    }

    private void createComputeHost(ComputeAllocationTaskState state, ComputeDescription cd,
            String parentLink, String placementLink, String computeResourceId, String computeName,
            List<String> diskLinks, List<String> networkLinks, ServiceTaskCallback taskCallback) {
        ComputeService.ComputeState resource = new ComputeService.ComputeState();
        resource.id = computeResourceId;
        resource.parentLink = parentLink;
        resource.name = computeName;
        resource.type = ComputeType.VM_GUEST;
        resource.powerState = ComputeService.PowerState.ON;
        resource.lifecycleState = LifecycleState.PROVISIONING;
        resource.descriptionLink = state.resourceDescriptionLink;
        resource.resourcePoolLink = state.getCustomProperty(
                ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK);
        resource.endpointLink = state.endpointLink;
        resource.diskLinks = diskLinks;
        resource.networkInterfaceLinks = networkLinks;
        resource.customProperties = new HashMap<>(state.customProperties);
        if (state.groupResourcePlacementLink != null) {
            resource.customProperties.put(ComputeConstants.GROUP_RESOURCE_PLACEMENT_LINK_NAME,
                    state.groupResourcePlacementLink);
        }
        resource.customProperties.put(ComputeProperties.PLACEMENT_LINK, placementLink);
        // TODO pmitrov: get rid of the __computeType custom prop
        resource.customProperties.put("__computeType", "VirtualMachine");
        resource.tenantLinks = state.tenantLinks;
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
    private void createDiskResources(ComputeAllocationTaskState state, ComputeDescription cd,
            ServiceTaskCallback taskCallback, Consumer<List<String>> diskLinksConsumer) {
        List<String> diskDescLinks = cd.diskDescLinks;
        if (diskDescLinks == null || diskDescLinks.isEmpty()) {
            diskLinksConsumer.accept(new ArrayList<>());
            return;
        }

        DeferredResult<List<String>> result = DeferredResult.allOf(diskDescLinks.stream()
                .map(link -> {
                    Operation op = Operation.createGet(this, link);
                    return this.sendWithDeferredResult(op, DiskState.class);
                })
                .map(dr -> dr.thenCompose(d -> {
                    String link = d.documentSelfLink;
                    // create a new disk based off the template but use a
                    // unique ID
                    d.id = UUID.randomUUID().toString();
                    d.documentSelfLink = null;
                    d.tenantLinks = state.tenantLinks;
                    if (d.customProperties == null) {
                        d.customProperties = new HashMap<>();
                    }
                    d.customProperties.put("__templateDiskLink", link);

                    return this.sendWithDeferredResult(
                            Operation.createPost(this, DiskService.FACTORY_LINK).setBody(d),
                            DiskState.class);
                }))
                .map(dsr -> dsr.thenCompose(ds -> {
                    return DeferredResult.completed(ds.documentSelfLink);
                }))
                .collect(Collectors.toList()));

        result.whenComplete((all, e) -> {
            if (e != null) {
                completeSubTasksCounter(taskCallback, e);
                return;
            }
            diskLinksConsumer.accept(all);
        });
    }

    private void createNetworkResources(ComputeAllocationTaskState state, ComputeDescription cd,
            ProfileStateExpanded profile, String placementLink, ServiceTaskCallback taskCallback,
            Consumer<List<String>> networkLinksConsumer) {
        if (cd.networkInterfaceDescLinks == null
                || cd.networkInterfaceDescLinks.isEmpty()) {
            networkLinksConsumer.accept(new ArrayList<>());
            return;
        }

        // get all network descriptions first, then create new network interfaces using the
        // description/template
        List<DeferredResult<String>> drs = cd.networkInterfaceDescLinks.stream()
                .map(nicDescLink -> this
                        .sendWithDeferredResult(
                                Operation.createGet(this, nicDescLink),
                                NetworkInterfaceDescription.class)
                        .thenCompose(nid -> createNicState(state, cd, nid, profile))
                        .thenCompose(nic -> this.sendWithDeferredResult(
                                Operation.createPost(this, NetworkInterfaceService.FACTORY_LINK)
                                        .setBody(nic),
                                NetworkInterfaceState.class))
                        .thenCompose(nis -> DeferredResult.completed(nis.documentSelfLink)))
                .collect(Collectors.toList());

        DeferredResult.allOf(drs).whenComplete((all, e) -> {
            if (e != null) {
                completeSubTasksCounter(taskCallback, e);
                return;
            }
            networkLinksConsumer.accept(all);
        });
    }

    private DeferredResult<NetworkInterfaceState> createNicState(ComputeAllocationTaskState state,
            ComputeDescription cd, NetworkInterfaceDescription nid, ProfileStateExpanded profile) {
        String subnetLink = nid.subnetLink;

        boolean noNicVM = nid.customProperties != null
                && nid.customProperties.containsKey(NetworkProfileQueryUtils.NO_NIC_VM);
        DeferredResult<SubnetState> subnet = null;
        boolean isIsolatedNetworkEnvironment = profile.networkProfile != null &&
                profile.networkProfile.isolationType == IsolationSupportType.SUBNET;
        boolean hasSubnetStates = profile.networkProfile != null
                && profile.networkProfile.subnetStates != null
                && !profile.networkProfile.subnetStates.isEmpty();
        if (hasSubnetStates || isIsolatedNetworkEnvironment) {
            if (!noNicVM) {
                DeferredResult<SubnetState> subnetDeferred = new DeferredResult<>();
                NetworkProfileQueryUtils.getSubnetForComputeNic(getHost(),
                        UriUtils.buildUri(getHost(), getSelfLink()), state.tenantLinks,
                        RequestUtils.getContextId(state), nid, profile,
                        (networkAndSubnet, ex) -> {
                            if (ex != null) {
                                subnetDeferred.fail(ex);
                                return;
                            }

                            patchComputeNetwork(networkAndSubnet.left, profile)
                                    .whenComplete((operation, t) -> {
                                        if (t != null) {
                                            subnetDeferred.fail(t);
                                            return;
                                        }

                                        if (networkAndSubnet.left.networkType == NetworkType.PUBLIC) {
                                            nid.assignPublicIpAddress = true;

                                            this.sendWithDeferredResult(
                                                    Operation
                                                            .createPatch(this, nid.documentSelfLink)
                                                            .setBody(nid))
                                                    .thenAccept(v -> subnetDeferred
                                                            .complete(networkAndSubnet.right));
                                            return;
                                        }

                                        subnetDeferred.complete(networkAndSubnet.right);
                                    });
                        });
                subnet = subnetDeferred;
            } else {
                subnet = DeferredResult.completed(profile.networkProfile.subnetStates.get(0));
            }
        } else if (noNicVM && nid.networkLink != null) {
            subnet = DeferredResult.completed(null);
        } else if (subnetLink == null) {
            // TODO: filter also by NetworkProfile
            subnet = findSubnetBy(state, cd.regionId, nid);
        } else {
            subnet = sendWithDeferredResult(Operation.createGet(this, subnetLink),
                    SubnetState.class);
        }

        DeferredResult<NetworkInterfaceState> n = subnet.thenCompose(s -> {
            if (s == null && nid.networkLink == null) {
                return DeferredResult.failed(
                        new IllegalStateException("No matching network found for VM:" + cd.name));
            }
            NetworkInterfaceState nic = new NetworkInterfaceState();
            nic.id = UUID.randomUUID().toString();
            nic.documentSelfLink = nic.id;
            nic.name = nid.name;
            nic.deviceIndex = nid.deviceIndex;
            nic.address = nid.address;
            nic.networkLink = nid.networkLink != null ? nid.networkLink : s.networkLink;
            nic.subnetLink = s != null ? s.documentSelfLink : null;
            nic.networkInterfaceDescriptionLink = nid.documentSelfLink;
            nic.securityGroupLinks = nid.securityGroupLinks;
            nic.groupLinks = nid.groupLinks;
            nic.tagLinks = nid.tagLinks;
            nic.tenantLinks = state.tenantLinks;
            nic.endpointLink = state.endpointLink;
            nic.customProperties = nid.customProperties;

            return DeferredResult.completed(nic);
        });
        return n;
    }

    private DeferredResult<Operation> patchComputeNetwork(ComputeNetwork computeNetwork,
            ProfileStateExpanded profile) {

        computeNetwork.provisionProfileLink = profile.documentSelfLink;
        return this.sendWithDeferredResult(
                Operation.createPatch(this, computeNetwork.documentSelfLink)
                        .setBody(computeNetwork));
    }

    private DeferredResult<SubnetState> findSubnetBy(ComputeAllocationTaskState state,
            String regionId, NetworkInterfaceDescription nid) {
        Builder builder = Query.Builder.create().addKindFieldClause(SubnetState.class);
        if (state.tenantLinks == null || state.tenantLinks.isEmpty()) {
            builder.addClause(QueryUtil.addTenantClause(state.tenantLinks));
        }
        builder.addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                ComputeProperties.INFRASTRUCTURE_USE_PROP_NAME, Boolean.TRUE.toString(),
                Occurance.MUST_NOT_OCCUR);
        if (regionId != null) {
            builder.addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                    ComputeProperties.REGION_ID, regionId);
        } else {
            Query clause = new Query()
                    .setTermPropertyName(QuerySpecification.buildCompositeFieldName(
                            ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeProperties.REGION_ID))
                    .setTermMatchType(MatchType.WILDCARD)
                    .setTermMatchValue(UriUtils.URI_WILDCARD_CHAR);
            clause.occurance = Occurance.MUST_NOT_OCCUR;
            builder.addClause(clause);
        }

        QueryByPages<SubnetState> querySubnetStates = new QueryByPages<>(getHost(), builder.build(),
                SubnetState.class, QueryUtil.getTenantLinks(state.tenantLinks), state.endpointLink);

        ArrayList<SubnetState> links = new ArrayList<>();
        ArrayList<SubnetState> prefered = new ArrayList<>();
        ArrayList<SubnetState> supportPublic = new ArrayList<>();
        return querySubnetStates.queryDocuments(s -> {
            boolean supportsPublic = s.supportPublicIpAddress != null && s.supportPublicIpAddress;
            boolean defaultForZone = s.defaultForZone != null && s.defaultForZone;
            if (supportsPublic && defaultForZone) {
                prefered.add(s);
            } else if (supportsPublic) {
                supportPublic.add(s);
            } else {
                links.add(s);
            }
        }).thenCompose(ignore -> {
            if (!prefered.isEmpty()) {
                return DeferredResult.<SubnetState> completed(prefered.get(0));
            }
            if (!supportPublic.isEmpty()) {
                return DeferredResult.<SubnetState> completed(supportPublic.get(0));
            }
            Optional<SubnetState> subnetState = links.stream().findFirst();
            if (subnetState.isPresent()) {
                return DeferredResult.<SubnetState> completed(subnetState.get());
            } else {
                return regionId != null ? findSubnetBy(state, null, nid)
                        : (DeferredResult<SubnetState>) null;
            }
        });
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
                        failTask(null, new LocalizableValidationException(
                                "Placement state has no resourcePoolLink",
                                "request.compute.allocation.resource-pool.missing"));
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

    private void queryProfile(ComputeAllocationTaskState state,
            EndpointState endpoint, List<String> tenantLinks, List<String> profileLinks,
            Consumer<List<String>> callbackFunction) {

        if (tenantLinks == null || tenantLinks.isEmpty()) {
            logInfo("Quering for global profiles for endpoint [%s] of type [%s]...",
                    endpoint.documentSelfLink, endpoint.endpointType);
        } else {
            logInfo("Quering for group [%s] profiles for endpoint [%s] of type [%s]...",
                    tenantLinks, endpoint.documentSelfLink, endpoint.endpointType);
        }
        // link=LINK || (link=unset && type=TYPE)
        Query.Builder query = Query.Builder.create()
                .addKindFieldClause(ProfileState.class)
                .addClause(Query.Builder.create()
                        .addFieldClause(ProfileState.FIELD_NAME_ENDPOINT_LINK,
                                endpoint.documentSelfLink, Occurance.SHOULD_OCCUR)
                        .addClause(Query.Builder.create(Occurance.SHOULD_OCCUR)
                                .addFieldClause(ProfileState.FIELD_NAME_ENDPOINT_LINK,
                                        UriUtils.URI_WILDCARD_CHAR, MatchType.WILDCARD,
                                        Occurance.MUST_NOT_OCCUR)
                                .addFieldClause(ProfileState.FIELD_NAME_ENDPOINT_TYPE,
                                        endpoint.endpointType)
                                .build())
                        .build());

        if (tenantLinks == null || tenantLinks.isEmpty()) {
            query.addClause(QueryUtil.addTenantClause(tenantLinks));
        }

        if (profileLinks != null && !profileLinks.isEmpty()) {
            query = query.addInClause(ProfileState.FIELD_NAME_SELF_LINK, profileLinks);
        }

        QueryByPages<ProfileState> queryProfs = new QueryByPages<>(getHost(), query.build(),
                ProfileState.class, QueryUtil.getTenantLinks(tenantLinks));

        queryProfs.collectDocuments(Collectors.toList())
                .whenComplete((foundProfiles, e) -> {
                    if (e != null) {
                        failTask("Failure while quering for profiles", e);
                        return;
                    }
                    if (foundProfiles.isEmpty()) {
                        if (tenantLinks != null && !tenantLinks.isEmpty()) {
                            queryProfile(state, endpoint, null,
                                    profileLinks, callbackFunction);
                        } else {
                            failTask(String.format(
                                    "No available profiles for endpoint %s of type %s",
                                    endpoint.documentSelfLink, endpoint.endpointType),
                                    null);
                        }
                        return;
                    }
                    // Sort profiles based on order of profileLinks
                    Stream<ProfileState> sortedProfiles = foundProfiles.stream();
                    if (profileLinks != null && !profileLinks.isEmpty()) {
                        sortedProfiles = foundProfiles.stream()
                                .sorted((e1, e2) -> profileLinks
                                        .indexOf(e1.documentSelfLink)
                                        - profileLinks.indexOf(e2.documentSelfLink));
                    }
                    Map<Boolean, List<ProfileState>> map = sortedProfiles.collect(Collectors
                            .partitioningBy(p -> endpoint.documentSelfLink.equals(p.endpointLink)));

                    List<ProfileState> endpointProfs = map.get(Boolean.TRUE);
                    callbackFunction
                            .accept((endpointProfs.isEmpty() ? map.get(Boolean.FALSE)
                                    : endpointProfs).stream()
                                            .map(p -> p.documentSelfLink)
                                            .collect(Collectors.toList()));
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
