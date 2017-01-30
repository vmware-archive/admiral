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
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.ResourcePlacementReservationRequest;
import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.compute.ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState;
import com.vmware.admiral.request.compute.ComputeReservationTaskService.ComputeReservationTaskState.SubStage;
import com.vmware.admiral.request.compute.EnvironmentQueryUtils.EnvEntry;
import com.vmware.admiral.request.compute.enhancer.Enhancer.EnhanceContext;
import com.vmware.admiral.request.compute.enhancer.EnvironmentComputeDescriptionEnhancer;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
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
public class ComputeReservationTaskService
        extends
        AbstractTaskStatefulService<ComputeReservationTaskService.ComputeReservationTaskState, ComputeReservationTaskService.ComputeReservationTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Reservation";

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_RESERVATION_TASKS;

    // cached compute description
    private transient volatile ComputeDescription computeDescription;

    public static class ComputeReservationTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeReservationTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
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

        /** (Internal) Set by task after the ComputeState is found to host the containers */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public List<HostSelection> selectedComputePlacementHosts;
    }

    public ComputeReservationTaskService() {
        super(ComputeReservationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    protected void handleStartedStagePatch(ComputeReservationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            queryGroupResourcePlacements(state, state.tenantLinks, this.computeDescription);
            break;
        case SELECTED:
            selectPlacementComputeHosts(state, state.tenantLinks, new HashSet<String>(
                    state.resourcePoolsPerGroupPlacementLinks.values()));
            break;
        case SELECTED_GLOBAL:
            selectPlacementComputeHosts(state, null, new HashSet<String>(
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
    }

    @Override
    protected void validateStateOnStart(ComputeReservationTaskState state) {
        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.", "request.resource-count.zero");
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

    private void queryGroupResourcePlacements(ComputeReservationTaskState state,
            List<String> tenantLinks,
            ComputeDescription computeDesc) {

        if (computeDesc == null) {
            getComputeDescription(state.resourceDescriptionLink,
                    (retrievedCompDesc) -> queryGroupResourcePlacements(state, tenantLinks,
                            retrievedCompDesc));
            return;
        }

        if (tenantLinks == null || tenantLinks.isEmpty()) {
            logInfo("Quering for global placements for resource description: [%s] and resource count: [%s]...",
                    state.resourceDescriptionLink, state.resourceCount);
        } else {
            logInfo("Quering for group placements in [%s], for resource description: [%s] and resource count: [%s]...",
                    tenantLinks, state.resourceDescriptionLink, state.resourceCount);
        }

        // match on group property:
        QueryTask q = QueryUtil.buildQuery(GroupResourcePlacementState.class, false);
        q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;

        q.querySpec.query.addBooleanClause(QueryUtil.addTenantAndGroupClause(tenantLinks));
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

        /*
         * TODO Get the placements from the DB ordered by priority. This should work..but it doesn't
         * :) QueryTask.QueryTerm sortTerm = new QueryTask.QueryTerm(); sortTerm.propertyName =
         * GroupResourcePlacementState.FIELD_NAME_PRIORITY; sortTerm.propertyType =
         * ServiceDocumentDescription.TypeName.LONG; q.querySpec.sortTerm = sortTerm;
         * q.querySpec.sortOrder = QueryTask.QuerySpecification.SortOrder.ASC;
         * q.querySpec.options.add(QueryTask.QuerySpecification.QueryOption.SORT);
         */

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
                    if (tenantLinks != null && !tenantLinks.isEmpty()) {
                        proceedTo(SubStage.QUERYING_GLOBAL);
                    } else {
                        failTask("No available group placements.", null);
                    }
                    return;
                }

                filterSelectedByEndpoint(state, placements, tenantLinks, computeDesc);
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

        final Set<String> resourcePools = new HashSet<>();
        state.selectedComputePlacementHosts
                .forEach(hs -> resourcePools.addAll(hs.resourcePoolLinks));

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
        if (placements == null) {
            failTask(null, new LocalizableValidationException("No placements found", "request.compute.reservation.placements.missing"));
            return;
        }

        HashMap<String, List<GroupResourcePlacementState>> placementsByRpLink = new HashMap<>();
        placements.forEach(p -> placementsByRpLink
                .computeIfAbsent(p.resourcePoolLink, k -> new ArrayList<>()).add(p));
        String endpointLink = getProp(computeDesc.customProperties,
                ComputeProperties.ENDPOINT_LINK_PROP_NAME);

        EnvironmentQueryUtils.queryEnvironments(getHost(),
                UriUtils.buildUri(getHost(), getSelfLink()), placementsByRpLink.keySet(),
                endpointLink, tenantLinks, (envs, e) -> {
                    if (e != null) {
                        failTask("Error retrieving environments for the selected placements: ", e);
                        return;
                    }

                    EnvironmentComputeDescriptionEnhancer enhancer =
                            new EnvironmentComputeDescriptionEnhancer(getHost(),
                                    UriUtils.buildUri(getHost().getPublicUri(), getSelfLink()));
                    List<DeferredResult<Pair<ComputeDescription, EnvEntry>>> list = envs
                            .stream()
                            .flatMap(envEntry -> envEntry.envLinks.stream().map(envLink -> {
                                ComputeDescription cloned = Utils.cloneObject(computeDesc);
                                EnhanceContext context = new EnhanceContext();
                                context.environmentLink = envLink;
                                context.imageType = cloned.customProperties
                                        .remove(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME);
                                context.skipNetwork = true;
                                context.regionId = envEntry.endpoint.endpointProperties
                                        .get(EndpointConfigRequest.REGION_KEY);

                                DeferredResult<Pair<ComputeDescription, EnvEntry>> r =
                                        new DeferredResult<>();
                                enhancer.enhance(context, cloned).whenComplete((cd, t) -> {
                                    if (t != null) {
                                        r.complete(Pair.of(cd, null));
                                        return;
                                    }
                                    String enhancedImage = cd.customProperties
                                            .get(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME);
                                    if (enhancedImage != null
                                            && context.imageType.equals(enhancedImage)) {
                                        r.complete(Pair.of(cd, null));
                                        return;
                                    }
                                    r.complete(Pair.of(cd, envEntry));
                                });
                                return r;
                            })).collect(Collectors.toList());

                    DeferredResult.allOf(list).whenComplete((all, t) -> {
                        if (t != null) {
                            failTask("Error retrieving environments for the selected placements: ",
                                    t);
                            return;
                        }

                        List <GroupResourcePlacementState> filteredPlacements = all
                                .stream()
                                .filter(p -> p.getRight() != null)
                                .flatMap(p -> supportsCD(state, placementsByRpLink, p))
                                .collect(Collectors.toList());

                        logInfo("Remaining candidate placements after endpoint filtering: "
                                + filteredPlacements);

                        filterPlacementsByRequirements(state, filteredPlacements, tenantLinks,
                                computeDesc);
                    });
                });
    }

    private void filterPlacementsByRequirements(ComputeReservationTaskState state,
            List<GroupResourcePlacementState> placements, List<String> tenantLinks,
            ComputeDescription computeDesc) {
        if (placements == null) {
            failTask(null, new IllegalStateException("No placements found"));
            return;
        }

        // check if requirements are stated in the compute description
        String requirementsString = getProp(computeDesc.customProperties,
                ComputeConstants.CUSTOM_PROP_PROVISIONING_REQUIREMENTS);
        if (requirementsString == null) {
            proceedTo(isGlobal(state) ? SubStage.SELECTED_GLOBAL : SubStage.SELECTED, s -> {
                s.resourcePoolsPerGroupPlacementLinks = placements.stream()
                        .sorted((g1, g2) -> g1.priority - g2.priority)
                        .collect(Collectors.toMap(gp -> gp.documentSelfLink,
                                gp -> gp.resourcePoolLink, (k1, k2) -> k1,
                                LinkedHashMap::new));
            });
            return;
        }

        // parse requirements and retrieve the tag links from the affinity constraints
        @SuppressWarnings("unchecked")
        List<String> affinitiesAsString = Utils.fromJson(requirementsString, List.class);
        Map<AffinityConstraint, String> tagLinkByConstraint = new HashMap<>();
        for (String affinityAsString : affinitiesAsString) {
            AffinityConstraint constraint = AffinityConstraint.fromString(affinityAsString);
            String tagLink = getTagLinkForConstraint(constraint, computeDesc.tenantLinks);
            tagLinkByConstraint.put(constraint, tagLink);
        }

        // retrieve resource pool instances in order to check which ones satisfy the reqs
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
                        })).collect(Collectors.toList());
        OperationJoin.create(getOperations).setCompletion((ops, exs) -> {
            if (exs != null) {
                failTask("Error retrieving resource pools: " + Utils.toString(exs),
                        exs.values().iterator().next());
                return;
            }

            // filter out placements that do not satisfy the HARD constraints, and then sort
            // remaining placements by listing first those with more soft constraints satisfied
            // (placement priority being used as a second criteria)
            proceedTo(isGlobal(state) ? SubStage.SELECTED_GLOBAL : SubStage.SELECTED, s -> {
                s.resourcePoolsPerGroupPlacementLinks = placements.stream()
                        .filter(gp -> checkRpSatisfyHardConstraints(
                                resourcePoolsByLink.get(gp.resourcePoolLink), tagLinkByConstraint))
                        .sorted((gp1, gp2) -> {
                            int softCount1 = getNumberOfSatisfiedSoftConstraints(
                                    resourcePoolsByLink.get(gp1.resourcePoolLink),
                                    tagLinkByConstraint);
                            int softCount2 = getNumberOfSatisfiedSoftConstraints(
                                    resourcePoolsByLink.get(gp2.resourcePoolLink),
                                    tagLinkByConstraint);
                            return softCount1 != softCount2 ? softCount1 - softCount2
                                    : gp1.priority - gp2.priority;
                        }).collect(Collectors.toMap(gp -> gp.documentSelfLink,
                                gp -> gp.resourcePoolLink, (k1, k2) -> k1,
                                LinkedHashMap::new));
            });
        }).sendWith(getHost());
    }

    private static String getTagLinkForConstraint(AffinityConstraint constraint,
            List<String> tenantLinks) {
        String[] tagParts = constraint.name.split(":");

        TagState tag = new TagState();
        tag.key = tagParts[0];
        tag.value = tagParts.length > 1 ? tagParts[1] : "";
        tag.tenantLinks = tenantLinks;

        return TagFactoryService.generateSelfLink(tag);
    }

    private static boolean checkRpSatisfyHardConstraints(ResourcePoolState rp,
            Map<AffinityConstraint, String> tagLinkByConstraint) {
        return tagLinkByConstraint.entrySet().stream()
                .filter(e -> e.getKey().isHard())
                .allMatch(e -> (rp != null && rp.tagLinks != null
                        && rp.tagLinks.contains(e.getValue())) == !e.getKey().antiAffinity);
    }

    private static int getNumberOfSatisfiedSoftConstraints(ResourcePoolState rp,
            Map<AffinityConstraint, String> tagLinkByConstraint) {
        return (int)tagLinkByConstraint.entrySet().stream()
                .filter(e -> e.getKey().isSoft())
                .filter(e -> (rp != null && rp.tagLinks != null
                        && rp.tagLinks.contains(e.getValue())) == !e.getKey().antiAffinity)
                .count();
    }

    private Stream<GroupResourcePlacementState> supportsCD(ComputeReservationTaskState state,
            HashMap<String, List<GroupResourcePlacementState>> placementsByRpLink,
            Pair<ComputeDescription, EnvEntry> pair) {
        return placementsByRpLink.get(pair.getRight().rpLink).stream()
                .filter(p -> {
                    if (p.memoryLimit == 0) {
                        return true;
                    }
                    if (p.availableMemory >= pair.getLeft().totalMemoryBytes
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
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(
                                        "Failure reserving group placement: %s. Retrying with the next one...",
                                        e.getMessage());
                                selectReservation(state, resourcePoolsPerGroupPlacementLinks);
                                return;
                            }

                            GroupResourcePlacementState placement = o
                                    .getBody(GroupResourcePlacementState.class);
                            complete(s -> {
                                s.customProperties = mergeCustomProperties(state.customProperties,
                                        placement.customProperties);
                                s.groupResourcePlacementLink = placement.documentSelfLink;
                                s.resourcePoolsPerGroupPlacementLinks = state.resourcePoolsPerGroupPlacementLinks;
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
}
