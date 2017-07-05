/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
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
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;
import static com.vmware.photon.controller.model.data.SchemaField.DATATYPE_STRING;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.VsphereConstants;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.ResourcePlacementReservationRequest;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.compute.ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState;
import com.vmware.admiral.request.compute.ComputeReservationTaskService.ComputeReservationTaskState.SubStage;
import com.vmware.admiral.request.compute.ProfileQueryUtils.ProfileEntry;
import com.vmware.admiral.request.compute.enhancer.ComputeDescriptionDiskEnhancer;
import com.vmware.admiral.request.compute.enhancer.ComputeDescriptionImageEnhancer;
import com.vmware.admiral.request.compute.enhancer.ComputeDescriptionInstanceTypeEnhancer;
import com.vmware.admiral.request.compute.enhancer.ComputeDescriptionProfileEnhancer;
import com.vmware.admiral.request.compute.enhancer.Enhancer.EnhanceContext;
import com.vmware.admiral.request.utils.EventTopicUtils;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.EventTopicDeclarator;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.Constraint.Condition;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.data.SchemaBuilder;
import com.vmware.photon.controller.model.data.SchemaField.Type;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

/**
 * Task implementing the reservation request resource work flow.
 */
public class ComputeReservationTaskService extends
        AbstractTaskStatefulService<ComputeReservationTaskService.ComputeReservationTaskState, ComputeReservationTaskService.ComputeReservationTaskState.SubStage>
        implements EventTopicDeclarator {

    public static final String DISPLAY_NAME = "Reservation";

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_RESERVATION_TASKS;

    // cached compute description
    private transient volatile ComputeDescription computeDescription;

    public static class ComputeReservationTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeReservationTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            NETWORK_CONSTRAINTS_COLLECTED,
            SELECTED,
            PLACEMENT,
            HOSTS_SELECTED,
            QUERYING_GLOBAL,
            SELECTED_GLOBAL,
            PLACEMENT_GLOBAL,
            HOSTS_SELECTED_GLOBAL,
            RESERVATION_SELECTED,
            COMPLETED,
            ERROR;

            static final Set<ComputeReservationTaskState.SubStage> SUBSCRIPTION_SUB_STAGES = new HashSet<>(
                    Arrays.asList(SELECTED));
        }

        @Documentation(description = "The description that defines the requested resource.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public long resourceCount;

        // Service fields:
        @Documentation(description = "Set by task. The link to the selected group placement.")
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String groupResourcePlacementLink;

        @Documentation(description = "Set by task. Selected group placement links and associated resourcePoolLinks. Ordered by priority asc")
        @PropertyOptions(usage = { SERVICE_USE }, indexing = STORE_ONLY)
        public LinkedHashMap<String, String> resourcePoolsPerGroupPlacementLinks;

        @Documentation(description = "Set by task. Selected group placements links and their names. Ordered by priority asc")
        @PropertyOptions(usage = { SERVICE_USE }, indexing = STORE_ONLY)
        public LinkedHashMap<String, Pair<String, String>> groupPlacementsLinksAndNames;

        /** (Internal) Set by task after the ComputeState is found to host the containers */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public List<HostSelection> selectedComputePlacementHosts;

        /** (Internal) Set by task profiles that can be used to create compute networks */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public List<String> profileConstraints;
    }

    public ComputeReservationTaskService() {
        super(ComputeReservationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        super.subscriptionSubStages = EnumSet.copyOf(SubStage.SUBSCRIPTION_SUB_STAGES);
    }

    @Override
    protected void handleStartedStagePatch(ComputeReservationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            collectComputeNicsProfileConstraints(state, null);
            break;
        case NETWORK_CONSTRAINTS_COLLECTED:
            queryGroupResourcePlacements(state, state.tenantLinks, this.computeDescription);
            break;
        case SELECTED:
            selectPlacementComputeHosts(state, state.tenantLinks, new HashSet<>(
                    state.resourcePoolsPerGroupPlacementLinks.values()));
            break;
        case SELECTED_GLOBAL:
            selectPlacementComputeHosts(state, null, new HashSet<>(
                    state.resourcePoolsPerGroupPlacementLinks.values()));
            break;
        case PLACEMENT:
        case PLACEMENT_GLOBAL:
            break;
        case HOSTS_SELECTED:
            hostsSelected(state, state.tenantLinks);
            break;
        case HOSTS_SELECTED_GLOBAL:
            hostsSelected(state, null);
            break;
        case RESERVATION_SELECTED:
            makeReservation(state, state.groupResourcePlacementLink,
                    state.resourcePoolsPerGroupPlacementLinks);
            break;
        case QUERYING_GLOBAL:
            // query again but with global group (group set to null):
            queryGroupResourcePlacements(state, null, this.computeDescription);
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
    protected void customStateValidationAndMerge(Operation patch,
            ComputeReservationTaskState patchBody, ComputeReservationTaskState currentState) {
        // override without merging
        currentState.resourcePoolsPerGroupPlacementLinks = PropertyUtils.mergeProperty(
                currentState.resourcePoolsPerGroupPlacementLinks,
                patchBody.resourcePoolsPerGroupPlacementLinks);

        currentState.groupPlacementsLinksAndNames = PropertyUtils.mergeProperty(
                currentState.groupPlacementsLinksAndNames,
                patchBody.groupPlacementsLinksAndNames);
    }

    @Override
    protected void validateStateOnStart(ComputeReservationTaskState state) {
        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.",
                    "request.resource-count.zero");
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ComputeReservationTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.groupResourcePlacementLink = state.groupResourcePlacementLink;
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        String groupResourcePlacementLink;
    }

    private void collectComputeNicsProfileConstraints(ComputeReservationTaskState state,
            ComputeDescription computeDesc) {
        if (computeDesc == null) {
            getComputeDescription(state.resourceDescriptionLink,
                    (retrievedCompDesc) -> collectComputeNicsProfileConstraints(state,
                            retrievedCompDesc));
            return;
        }

        if (computeDesc.networkInterfaceDescLinks == null || computeDesc.networkInterfaceDescLinks
                .isEmpty()) {
            proceedTo(SubStage.NETWORK_CONSTRAINTS_COLLECTED);
            return;
        }

        String contextId = RequestUtils.getContextId(state);
        NetworkProfileQueryUtils.getProfilesForComputeNics(getHost(),
                UriUtils.buildUri(getHost(), getSelfLink()), state.tenantLinks, contextId,
                computeDesc,
                (profileConstraints, e) -> {
                    if (e != null) {
                        failTask("Error getting network profile constraints: ", e);
                        return;
                    }
                    logInfo("Profile constraints of networks associated with the compute '%s': %s",
                            computeDesc.name,
                            profileConstraints);
                    proceedTo(SubStage.NETWORK_CONSTRAINTS_COLLECTED, s -> {
                        s.profileConstraints = profileConstraints;
                    });
                });
    }

    private void queryGroupResourcePlacements(ComputeReservationTaskState state,
            List<String> tenantLinks, ComputeDescription computeDesc) {

        if (computeDesc == null) {
            getComputeDescription(state.resourceDescriptionLink,
                    (retrievedCompDesc) -> queryGroupResourcePlacements(state, tenantLinks,
                            retrievedCompDesc));
            return;
        }

        // GroupResourcePlacements must be filtered on by tenant and project
        List<String> tgl = QueryUtil.getTenantAndGroupLinks(tenantLinks);
        if (tgl == null || tgl.isEmpty()) {
            logInfo("Querying for global placements for resource description: [%s] and resource"
                            + " count: [%s]..", state.resourceDescriptionLink, state.resourceCount);
        } else {
            logInfo("Querying for group placements in [%s], for resource description: [%s] and"
                            + " resource count: [%s]...",
                    tgl, state.resourceDescriptionLink, state.resourceCount);
        }

        // match on group property:
        QueryTask q = QueryUtil.buildQuery(GroupResourcePlacementState.class, false);
        q.querySpec.query.addBooleanClause(QueryUtil.addTenantAndGroupClause(tgl));
        q.querySpec.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(GroupResourcePlacementState.FIELD_NAME_RESOURCE_TYPE,
                        ResourceType.COMPUTE_TYPE.getName())
                .build());

        // match on available number of instances:
        Query numOfInstancesClause = Query.Builder.create()
                .addRangeClause(GroupResourcePlacementState.FIELD_NAME_AVAILABLE_INSTANCES_COUNT,
                        NumericRange.createLongRange(state.resourceCount, Long.MAX_VALUE, true,
                                false),
                        Occurance.SHOULD_OCCUR)
                .addRangeClause(GroupResourcePlacementState.FIELD_NAME_MAX_NUMBER_INSTANCES,
                        NumericRange.createEqualRange(0L), Occurance.SHOULD_OCCUR)
                .build();
        q.querySpec.query.addBooleanClause(numOfInstancesClause);

        QueryUtil.addExpandOption(q);

        ServiceDocumentQuery<GroupResourcePlacementState> query = new ServiceDocumentQuery<>(
                getHost(),
                GroupResourcePlacementState.class);
        List<GroupResourcePlacementState> placements = new ArrayList<>();
        query.query(q, (r) -> {
            if (r.hasException()) {
                failTask("Exception while quering for placements", r.getException());
            } else if (r.hasResult()) {
                placements.add(r.getResult());
            } else {
                if (placements.isEmpty()) {
                    if (tgl != null && !tgl.isEmpty()) {
                        proceedTo(SubStage.QUERYING_GLOBAL);
                    } else {
                        failTask("No available group placements.", null);
                    }
                    return;
                }

                logInfo("Candidate placements for compute '%s' before filtering: %s",
                        computeDesc.name,
                        placements.stream().map(ps -> ps.documentSelfLink)
                                .collect(Collectors.toList()));

                // pass the final tenantLinks, e.g. if global GroupResourcePlacement is selected,
                // then only global endpoints and deployment profiles must be used.
                filterSelectedByEndpoint(state, placements, tgl, computeDesc);
            }
        });
    }

    private void selectPlacementComputeHosts(ComputeReservationTaskState state,
            List<String> tenantLinks, Set<String> resourcePools) {

        // create placement selection tasks
        ComputePlacementSelectionTaskState placementTask = new ComputePlacementSelectionTaskState();
        placementTask.documentSelfLink = getSelfId() + "-reservation"
                + (isGlobal(state) ? "-global" : "");
        placementTask.computeDescriptionLink = state.resourceDescriptionLink;
        placementTask.resourcePoolLinks = new ArrayList<>(resourcePools);
        placementTask.resourceCount = state.resourceCount;
        placementTask.tenantLinks = tenantLinks;
        placementTask.customProperties = state.customProperties;
        placementTask.contextId = getContextId(state);
        placementTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED,
                isGlobal(state) ? SubStage.HOSTS_SELECTED_GLOBAL : SubStage.HOSTS_SELECTED,
                TaskStage.STARTED, SubStage.ERROR);
        placementTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ComputePlacementSelectionTaskService.FACTORY_LINK)
                .setBody(placementTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating placement task", e);
                        return;
                    }
                    proceedTo(isGlobal(state) ? SubStage.PLACEMENT_GLOBAL : SubStage.PLACEMENT);
                }));
    }

    private void hostsSelected(ComputeReservationTaskState state, List<String> tenantLinks) {
        if (state.selectedComputePlacementHosts == null
                || state.selectedComputePlacementHosts.isEmpty()) {
            if (tenantLinks != null && !tenantLinks.isEmpty()) {
                proceedTo(SubStage.QUERYING_GLOBAL);
            } else {
                failTask("Available compute host can't be selected.", null);
            }
            return;
        }

        final Set<String> resourcePools = state.selectedComputePlacementHosts.stream()
                .flatMap(hs -> hs.resourcePoolLinks.stream())
                .collect(Collectors.toSet());

        if (state.resourcePoolsPerGroupPlacementLinks != null) {
            state.resourcePoolsPerGroupPlacementLinks = state.resourcePoolsPerGroupPlacementLinks
                    .entrySet().stream().filter((e) -> resourcePools.contains(e.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (k1, k2) -> k1, LinkedHashMap::new));
        } else {
            state.resourcePoolsPerGroupPlacementLinks = new LinkedHashMap<>();
        }

        selectReservation(state, state.resourcePoolsPerGroupPlacementLinks);
    }

    private boolean isGlobal(ComputeReservationTaskState state) {
        return state.taskSubStage != null
                && state.taskSubStage.ordinal() >= SubStage.QUERYING_GLOBAL.ordinal();
    }

    private void filterSelectedByEndpoint(ComputeReservationTaskState state,
            List<GroupResourcePlacementState> placements, List<String> tenantLinks,
            ComputeDescription computeDesc) {
        HashMap<String, List<GroupResourcePlacementState>> placementsByRpLink = new HashMap<>();
        placements.forEach(p -> placementsByRpLink
                .computeIfAbsent(p.resourcePoolLink, k -> new ArrayList<>()).add(p));
        String endpointLink = getProp(computeDesc.customProperties,
                ComputeProperties.ENDPOINT_LINK_PROP_NAME);

        String computeEndpointType = getProp(computeDesc.customProperties,
                VsphereConstants.COMPUTE_COMPONENT_TYPE_ID);

        ComputeConstants.EndpointType epType = ComputeConstants.EndpointType
                .resolveEndpointType(computeEndpointType);

        String epTypeStr = epType != null ? epType.type : null;

        ProfileQueryUtils.queryProfiles(getHost(),
                UriUtils.buildUri(getHost(), getSelfLink()), placementsByRpLink.keySet(),
                endpointLink, tenantLinks, state.profileConstraints,
                epTypeStr,
                (profileEntries, e) -> {
                    if (e != null) {
                        failTask("Error retrieving profiles for the selected placements: ", e);
                        return;
                    }

                    logInfo("Found %d endpoints with configured profiles", profileEntries.size());
                    if (profileEntries.isEmpty()) {
                        failTask(null, new IllegalStateException(
                                "No profiles found for the selected candidate placements"));
                        return;
                    }
                    profileEntries.forEach(profileEntry -> logInfo("Endpoint %s, profiles: %s",
                            profileEntry.endpoint.documentSelfLink,
                            profileEntry.profileLinks));

                    URI referer = UriUtils.buildUri(getHost().getPublicUri(), getSelfLink());
                    ComputeDescriptionProfileEnhancer pe = new ComputeDescriptionProfileEnhancer(
                            getHost(), referer);
                    ComputeDescriptionInstanceTypeEnhancer instanceTypeEnhancer =
                            new ComputeDescriptionInstanceTypeEnhancer(getHost(), referer);
                    ComputeDescriptionImageEnhancer imageEnhancer =
                            new ComputeDescriptionImageEnhancer(getHost(), referer);
                    ComputeDescriptionDiskEnhancer diskEnhancer = new
                            ComputeDescriptionDiskEnhancer(getHost(), referer);

                    List<DeferredResult<Triple<ComputeDescription, ProfileEntry, Throwable>>> list = profileEntries
                            .stream()
                            .flatMap(profileEntry -> profileEntry.profileLinks.stream()
                                    .map(profileLink -> {
                                        ComputeDescription cloned = Utils.cloneObject(computeDesc);
                                        EnhanceContext context = new EnhanceContext();
                                        context.profileLink = profileLink;
                                        context.skipNetwork = true;
                                        String regionId = profileEntry.endpoint.regionId;
                                        if (regionId == null) {
                                            // TODO [adimitrov]: Remove this once all adapters set
                                            // endpoint.regionId.

                                            // Try to get region id from endpointProperties for now.
                                            regionId = profileEntry.endpoint.endpointProperties.get(
                                                    EndpointConfigRequest.REGION_KEY);
                                        }
                                        context.regionId = regionId;
                                        return Pair.of(context, cloned);
                                    })
                                    .map(p -> {
                                        DeferredResult<Triple<ComputeDescription, ProfileEntry, Throwable>> r =
                                                new DeferredResult<>();
                                        pe.enhance(p.getLeft(), p.getRight())
                                                .thenCompose(cd -> instanceTypeEnhancer
                                                        .enhance(p.getLeft(), cd))
                                                .thenCompose(cd -> imageEnhancer
                                                        .enhance(p.getLeft(), cd))
                                                .thenCompose(cd -> diskEnhancer
                                                        .enhance(p.getLeft(), cd))
                                                .whenComplete((cd, t) -> {
                                                    if (t != null) {
                                                        logInfo(() -> Utils.toString(t));
                                                        r.complete(Triple.of(cd, null, t));
                                                        return;
                                                    }
                                                    r.complete(Triple.of(cd, profileEntry, null));
                                                });
                                        return r;
                                    }))
                            .collect(Collectors.toList());

                    DeferredResult.allOf(list).whenComplete((all, t) -> {
                        if (t != null) {
                            failTask("Error retrieving profiles for the selected placements: ", t);
                            return;
                        }

                        List<GroupResourcePlacementState> filteredPlacements = all
                                .stream()
                                .filter(p -> p.getMiddle() != null)
                                .flatMap(p -> supportsCD(state, placementsByRpLink, p))
                                .collect(Collectors.toList());

                        logInfo("Remaining candidate placements after endpoint filtering: %s",
                                filteredPlacements);

                        if (filteredPlacements.isEmpty()) {
                            // Now collect the posisble reasons for failure.
                            StringJoiner stringJoiner = new StringJoiner(",");
                            all.stream().filter(p -> p.getRight() != null).forEach(triple -> {
                                stringJoiner.add(triple.getRight().getMessage());
                            });

                            String errorMessage = "No candidate placements left after endpoint filtering";
                            if (stringJoiner.length() > 0) {
                                errorMessage = String.format("%s%s Error: %s", stringJoiner.toString(),
                                        stringJoiner.toString().endsWith(".") ? "" : ".",
                                        errorMessage);
                            }
                            failTask(stringJoiner.toString(), new IllegalStateException(errorMessage));
                            return;
                        }

                        filterPlacementsByRequirements(state, filteredPlacements, tenantLinks,
                                computeDesc);
                    });
                });
    }

    private void filterPlacementsByRequirements(ComputeReservationTaskState state,
            List<GroupResourcePlacementState> placements, List<String> tenantLinks,
            ComputeDescription computeDesc) {
        // retrieve the tag links from constraint conditions
        Map<Condition, String> tagLinkByCondition = TagConstraintUtils
                .extractPlacementTagConditions(
                        computeDesc.constraints, computeDesc.tenantLinks);

        // check if requirements are stated in the compute description
        if (tagLinkByCondition == null) {
            proceedTo(isGlobal(state) ? SubStage.SELECTED_GLOBAL : SubStage.SELECTED, s -> {
                s.resourcePoolsPerGroupPlacementLinks = placements.stream()
                        .sorted((g1, g2) -> g1.priority - g2.priority)
                        .collect(Collectors.toMap(gp -> gp.documentSelfLink,
                                gp -> gp.resourcePoolLink, (k1, k2) -> k1,
                                LinkedHashMap::new));

                s.groupPlacementsLinksAndNames = placements.stream()
                        .sorted((g1, g2) -> g1.priority - g2.priority)
                        .collect(Collectors.toMap(gp -> gp.documentSelfLink,
                                gp -> Pair.of(gp.name, gp.resourcePoolLink), (k1, k2) -> k1,
                                () -> new LinkedHashMap<String, Pair<String, String>>()));
            });
            return;
        }

        // retrieve resource pool instances in order to check which ones satisfy the constraint
        Map<String, ResourcePoolState> resourcePoolsByLink = new HashMap<>();
        List<Operation> getOperations = placements.stream()
                .map(gp -> Operation
                        .createGet(getHost(), gp.resourcePoolLink)
                        .setReferer(getUri())
                        .setCompletion((o, e) -> {
                            if (e == null) {
                                resourcePoolsByLink.put(gp.resourcePoolLink,
                                        o.getBody(ResourcePoolState.class));
                            }
                        }))
                .collect(Collectors.toList());
        OperationJoin.create(getOperations).setCompletion((ops, exs) -> {
            if (exs != null) {
                failTask("Error retrieving resource pools: " + Utils.toString(exs),
                        exs.values().iterator().next());
                return;
            }

            // filter out placements that do not satisfy the HARD constraints, and then sort
            // remaining placements by listing first those with more soft constraints satisfied
            // (placement priority being used as a second criteria)
            LinkedHashMap<String, String> placementsAfterTagFilter = TagConstraintUtils
                    .filterByConstraints(
                            tagLinkByCondition,
                            placements.stream(),
                            p -> getResourcePoolTags(
                                    resourcePoolsByLink.get(p.resourcePoolLink)),
                            (g1, g2) -> g1.priority - g2.priority)
                    .collect(Collectors.toMap(gp -> gp.documentSelfLink,
                            gp -> gp.resourcePoolLink,
                            (k1, k2) -> k1, LinkedHashMap::new));

            LinkedHashMap<String, Pair<String, String>> allPlacementsLinksAndNames =
                    TagConstraintUtils
                            .filterByConstraints(
                                    tagLinkByCondition,
                                    placements.stream(),
                                    p -> getResourcePoolTags(
                                            resourcePoolsByLink.get(p.resourcePoolLink)),
                                    (g1, g2) -> g1.priority - g2.priority)
                            .collect(Collectors.toMap(gp -> gp.documentSelfLink,
                                    gp -> Pair.of(gp.name, gp.resourcePoolLink),
                                    (k1, k2) -> k1,
                                    () -> new LinkedHashMap<String, Pair<String, String>>()));

            if (!placements.isEmpty() && placementsAfterTagFilter.isEmpty()) {
                logInfo("No candidate placements after tag filtering");

                failTask(null, new LocalizableValidationException(
                        "No placement exists that satisfies all of the request requirements. "
                                + "Please check if suitable placements and placement zones exist "
                                + "and they have been properly tagged.",
                        "request.compute.reservation.resource-pools.empty.tags"));
                return;
            } else {
                logInfo("Remaining candidate placements after tag filtering: %s",
                        placementsAfterTagFilter.keySet());
            }

            proceedTo(isGlobal(state) ? SubStage.SELECTED_GLOBAL : SubStage.SELECTED, s -> {
                s.resourcePoolsPerGroupPlacementLinks = placementsAfterTagFilter;
                s.groupPlacementsLinksAndNames = allPlacementsLinksAndNames;
            });
        }).sendWith(getHost());
    }

    private static Set<String> getResourcePoolTags(ResourcePoolState rp) {
        return rp != null && rp.tagLinks != null ? rp.tagLinks : new HashSet<>();
    }

    private Stream<GroupResourcePlacementState> supportsCD(ComputeReservationTaskState state,
            HashMap<String, List<GroupResourcePlacementState>> placementsByRpLink,
            Triple<ComputeDescription, ProfileEntry, Throwable> triple) {
        return placementsByRpLink.get(triple.getMiddle().rpLink).stream()
                .filter(p -> {
                    if (p.memoryLimit == 0) {
                        return true;
                    }
                    if (p.availableMemory >= triple.getLeft().totalMemoryBytes
                            * state.resourceCount) {
                        return true;
                    }
                    return false;
                });
    }

    private String getProp(Map<String, String> customProperties, String key) {
        if (customProperties == null) {
            return null;
        }
        return customProperties.get(key);
    }

    private void selectReservation(ComputeReservationTaskState state,
            LinkedHashMap<String, String> resourcePoolsPerGroupPlacementLinks) {
        if (resourcePoolsPerGroupPlacementLinks.isEmpty()) {
            failTask(null, new LocalizableValidationException(
                    "resourcePoolsPerGroupPlacementLinks must not be empty",
                    "request.compute.reservation.resource-pools.empty"));
            return;
        }

        Iterator<String> iter = resourcePoolsPerGroupPlacementLinks.keySet().iterator();
        String placementLink = iter.next();
        iter.remove();

        logInfo("Current selected placement: %s", placementLink);
        proceedTo(SubStage.RESERVATION_SELECTED, s -> {
            s.resourcePoolsPerGroupPlacementLinks = resourcePoolsPerGroupPlacementLinks;
            s.groupResourcePlacementLink = placementLink;
        });
    }

    private void makeReservation(ComputeReservationTaskState state,
            String placementLink,
            LinkedHashMap<String, String> resourcePoolsPerGroupPlacementLinks) {

        // TODO: implement more sophisticated algorithm to pick the right group placement based on
        // availability and current allocation of resources.

        ResourcePlacementReservationRequest reservationRequest = new ResourcePlacementReservationRequest();
        reservationRequest.resourceCount = state.resourceCount;
        reservationRequest.resourceDescriptionLink = state.resourceDescriptionLink;
        reservationRequest.referer = getSelfLink();

        logInfo("Reserving instances: %d for descLink: %s and groupPlacementId: %s",
                reservationRequest.resourceCount, reservationRequest.resourceDescriptionLink,
                Service.getId(placementLink));

        sendRequest(Operation
                .createPatch(this, placementLink)
                .setBody(reservationRequest)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failure reserving group placement: %s. Retrying with the next"
                                        + " one...", e.getMessage());
                        selectReservation(state, resourcePoolsPerGroupPlacementLinks);
                        return;
                    }

                    GroupResourcePlacementState placement = o
                            .getBody(GroupResourcePlacementState.class);
                    complete(s -> {
                        s.customProperties = mergeCustomProperties(state.customProperties,
                                placement.customProperties);
                        s.groupResourcePlacementLink = placement.documentSelfLink;
                        s.resourcePoolsPerGroupPlacementLinks =
                                state.resourcePoolsPerGroupPlacementLinks;
                    });
                }));
    }

    private void getComputeDescription(String resourceDescriptionLink,
            Consumer<ComputeDescription> callbackFunction) {
        if (this.computeDescription != null) {
            callbackFunction.accept(this.computeDescription);
            return;
        }
        sendRequest(Operation.createGet(this, resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving description state", e);
                        return;
                    }

                    this.computeDescription = o.getBody(ComputeDescription.class);
                    callbackFunction.accept(this.computeDescription);
                }));
    }

    @Override
    public void registerEventTopics(ServiceHost host) {
        computeReservationEventTopic(host);
    }

    //Compute reservation topic
    private static final String COMPUTE_RESERVATION_TOPIC_TASK_SELF_LINK = "compute-reservation";
    private static final String COMPUTE_RESERVATION_TOPIC_ID = "com.vmware.compute.reservation.pre";
    private static final String COMPUTE_RESERVATION_TOPIC_NAME = "Compute reservation";
    private static final String COMPUTE_RESERVATION_TOPIC_TASK_DESCRIPTION = "Pre reservation for "
            + "compute resoures";
    private static final String COMPUTE_RESERVATION_TOPIC_FIELD_PLACEMENTS = "placements";
    private static final String COMPUTE_RESERVATION_TOPIC_FIELD_PLACEMENTS_LABEL = "Placements";
    private static final String COMPUTE_RESERVATION_TOPIC_FIELD_PLACEMENTS_DESCRIPTION =
            "Applicable Placements";

    private void computeReservationEventTopic(ServiceHost host) {
        EventTopicService.TopicTaskInfo taskInfo = new EventTopicService.TopicTaskInfo();
        taskInfo.task = ComputeReservationTaskState.class.getSimpleName();
        taskInfo.stage = TaskStage.STARTED.name();
        taskInfo.substage = SubStage.SELECTED.name();

        EventTopicUtils.registerEventTopic(COMPUTE_RESERVATION_TOPIC_ID,
                COMPUTE_RESERVATION_TOPIC_NAME,
                COMPUTE_RESERVATION_TOPIC_TASK_DESCRIPTION,
                COMPUTE_RESERVATION_TOPIC_TASK_SELF_LINK,
                Boolean.TRUE, computeReservationTopicSchema(), taskInfo, host);
    }

    private SchemaBuilder computeReservationTopicSchema() {

        return new SchemaBuilder()
                .addField(COMPUTE_RESERVATION_TOPIC_FIELD_PLACEMENTS)
                .withType(Type.LIST)
                .withDataType(DATATYPE_STRING)
                .withLabel(COMPUTE_RESERVATION_TOPIC_FIELD_PLACEMENTS_LABEL)
                .withDescription(COMPUTE_RESERVATION_TOPIC_FIELD_PLACEMENTS_DESCRIPTION)
                .done();
    }

    /**
     * Defines fields which are eligible for modification in case of subscription for task.
     */
    protected static class ExtensibilityCallbackResponse extends BaseExtensibilityCallbackResponse {
        public Collection<String> placements;
    }

    @Override
    protected BaseExtensibilityCallbackResponse notificationPayload(ComputeReservationTaskState
            state) {
        return new ExtensibilityCallbackResponse();
    }

    @Override
    protected Collection<String> getRelatedResourcesLinks(ComputeReservationTaskState state) {
        return Arrays.asList(state.resourceDescriptionLink);
    }

    @Override
    protected Class<? extends ResourceState> getRelatedResourceStateType() {
        return ComputeDescription.class;
    }

    @Override
    protected DeferredResult<Void> enhanceNotificationPayload(ComputeReservationTaskState state,
            Collection<ResourceState> relatedStates,
            BaseExtensibilityCallbackResponse notificationPayload) {

        ExtensibilityCallbackResponse payload = (ExtensibilityCallbackResponse)
                notificationPayload;

        payload.placements = state.groupPlacementsLinksAndNames.values().stream()
                .map(p -> p.getLeft())
                .collect(Collectors.toList());

        return DeferredResult.completed(null);
    }

    @Override
    public DeferredResult<Void> enhanceExtensibilityResponse(ComputeReservationTaskState state,
            ServiceTaskCallbackResponse replyPayload) {

        return patchReservations(state, replyPayload);
    }

    private DeferredResult<Void> patchReservations(ComputeReservationTaskState state,
            ServiceTaskCallbackResponse replyPayload) {
        ExtensibilityCallbackResponse response = (ExtensibilityCallbackResponse) replyPayload;

        List<String> statePlacements = state.groupPlacementsLinksAndNames.values().stream()
                .map(p -> p.getLeft())
                .collect(Collectors.toList());

        if (!CollectionUtils.isEqualCollection(response.placements, statePlacements)) {
            ComputeReservationTaskState patch = new ComputeReservationTaskState();
            patch.resourcePoolsPerGroupPlacementLinks = new LinkedHashMap<>();

            for (String placement : response.placements) {
                Optional<Entry<String, Pair<String, String>>> found = state.groupPlacementsLinksAndNames
                        .entrySet()
                        .stream().filter(p -> p.getValue().getLeft().equals(placement))
                        .findFirst();
                if (!found.isPresent()) {
                    return DeferredResult.failed(new IllegalArgumentException("Invalid placement '" + placement +
                            "' " + "specified."));
                } else {
                    Entry<String, Pair<String, String>> entry = found.get();
                    patch.resourcePoolsPerGroupPlacementLinks.put(entry.getKey(), entry.getValue
                            ().getRight());
                }
            }

            patch.taskInfo = state.taskInfo;
            patch.taskSubStage = state.taskSubStage;

            return this.sendWithDeferredResult(Operation.createPatch(this, state.documentSelfLink)
                    .setBody(patch)
                    .setReferer(getHost().getUri())).thenAccept(k -> {
                    });
        } else {
            return DeferredResult.completed(null);
        }
    }
}
