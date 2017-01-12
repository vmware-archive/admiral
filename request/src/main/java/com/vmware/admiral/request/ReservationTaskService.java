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
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.ResourcePlacementReservationRequest;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.ReservationAllocationTaskService.ReservationAllocationTaskState;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState.SubStage;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

/**
 * Task implementing the reservation request resource work flow.
 */
public class ReservationTaskService
        extends
        AbstractTaskStatefulService<ReservationTaskService.ReservationTaskState, ReservationTaskService.ReservationTaskState.SubStage> {

    private static final int QUERY_RETRIES_COUNT = Integer.getInteger(
            "com.vmware.admiral.service.placements.query.retries", 2);

    public static final String DISPLAY_NAME = "Reservation";

    // cached container description
    private volatile ContainerDescription containerDescription;

    public static class ReservationTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ReservationTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            SELECTED,
            PLACEMENT,
            HOSTS_SELECTED,
            QUERYING_GLOBAL,
            SELECTED_GLOBAL,
            PLACEMENT_GLOBAL,
            HOSTS_SELECTED_GLOBAL,
            RESERVATION_ALLOCATION,
            ALLOCATING_RESOURCE_POOL,
            RESERVATION_SELECTED,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(PLACEMENT, PLACEMENT_GLOBAL, ALLOCATING_RESOURCE_POOL));
        }

        /** (Required) The description that defines the requested resource. */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        /** (Required) Type of resource to create. */
        public String resourceType;

        /** (Required) Number of resources to provision. */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public long resourceCount;

        // Service fields:
        /** (Internal) Set by task. The link to the selected group placement. */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String groupResourcePlacementLink;

        /**
         * Set by task. Selected group placement links and associated resourcePoolLinks. Ordered by
         * priority asc
         */
        @PropertyOptions(usage = { SERVICE_USE }, indexing = STORE_ONLY)
        public LinkedHashMap<String, String> resourcePoolsPerGroupPlacementLinks;

        /** (Internal) Set by task after the ComputeState is found to host the containers */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public List<HostSelection> hostSelections;
    }

    public ReservationTaskService() {
        super(ReservationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ReservationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            queryGroupResourcePlacements(state, containerDescription, QUERY_RETRIES_COUNT);
            break;
        case SELECTED:
        case SELECTED_GLOBAL:
            selectPlacementComputeHosts(state, new HashSet<String>(
                    state.resourcePoolsPerGroupPlacementLinks.values()));
            break;
        case PLACEMENT:
        case PLACEMENT_GLOBAL:
            break;
        case HOSTS_SELECTED:
        case HOSTS_SELECTED_GLOBAL:
            hostsSelected(state);
            break;
        case RESERVATION_ALLOCATION:
            createReservationAllocationTask(state, null);
            break;
        case ALLOCATING_RESOURCE_POOL:
            break;
        case RESERVATION_SELECTED:
            makeReservation(state, state.groupResourcePlacementLink,
                    state.resourcePoolsPerGroupPlacementLinks);
            break;
        case QUERYING_GLOBAL:
            // query again but with global group (group set to null):
            queryGroupResourcePlacements(state, containerDescription, QUERY_RETRIES_COUNT);
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
            ReservationTaskState patchBody, ReservationTaskState currentState) {
        if (SubStage.QUERYING_GLOBAL == patchBody.taskSubStage) {
            // In this case try global group instead of the provided one.
            currentState.tenantLinks = null;
        }

        // override without merging
        currentState.resourcePoolsPerGroupPlacementLinks = PropertyUtils.mergeProperty(
                currentState.resourcePoolsPerGroupPlacementLinks,
                patchBody.resourcePoolsPerGroupPlacementLinks);
    }

    @Override
    protected void validateStateOnStart(ReservationTaskState state) {
        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.", "request.resource-count.zero");
        }
        if (state.resourceType == null || state.resourceType.isEmpty()) {
            state.resourceType = ResourceType.CONTAINER_TYPE.getName();
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(ReservationTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.groupResourcePlacementLink = state.groupResourcePlacementLink;
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        String groupResourcePlacementLink;
    }

    private void createReservationAllocationTask(ReservationTaskState reservationTask,
            ContainerDescription containerDesc) {

        if (containerDesc == null) {
            getContainerDescription(
                    reservationTask.resourceDescriptionLink,
                    (retrievedContDesc) -> createReservationAllocationTask(reservationTask,
                            retrievedContDesc));
            return;
        }

        ReservationAllocationTaskState reservationAllocationTask = new ReservationAllocationTaskState();
        reservationAllocationTask.tenantLinks = reservationTask.tenantLinks;
        reservationAllocationTask.customProperties = containerDesc.customProperties;
        reservationAllocationTask.name = containerDesc.name;
        reservationAllocationTask.resourcePoolsPerGroupPlacementLinks = reservationTask.resourcePoolsPerGroupPlacementLinks;
        reservationAllocationTask.documentSelfLink = getSelfId();
        reservationAllocationTask.contextId = getContextId(reservationTask);
        reservationAllocationTask.resourceDescriptionLink = reservationTask.resourceDescriptionLink;
        reservationAllocationTask.resourceCount = reservationTask.resourceCount;
        reservationAllocationTask.requestTrackerLink = reservationTask.requestTrackerLink;
        reservationAllocationTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, SubStage.RESERVATION_SELECTED,
                TaskStage.STARTED, SubStage.ERROR);

        sendRequest(Operation
                .createPost(this, ReservationAllocationTaskService.FACTORY_LINK)
                .setBody(reservationAllocationTask)
                .setContextId(getSelfId())
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                failTask("Failure creating reservation allocation task", e);
                                return;
                            }
                            proceedTo(SubStage.ALLOCATING_RESOURCE_POOL);
                        }));

    }

    private void queryGroupResourcePlacements(ReservationTaskState state,
            ContainerDescription containerDesc, int retriesCount) {

        if (containerDesc == null) {
            getContainerDescription(state.resourceDescriptionLink,
                    (retrievedContDesc) -> queryGroupResourcePlacements(state, retrievedContDesc,
                            retriesCount));
            return;
        }

        // If property __containerHostId exists call ReservationAllocationTaskService
        if (containerDesc.customProperties != null && containerDesc.customProperties
                .containsKey(ReservationAllocationTaskService.CONTAINER_HOST_ID_CUSTOM_PROPERTY)) {
            proceedTo(ReservationTaskState.SubStage.RESERVATION_ALLOCATION);
            return;
        }

        // match on group property:
        QueryTask q = QueryUtil.buildQuery(GroupResourcePlacementState.class, false);
        q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;

        q.querySpec.query.addBooleanClause(Query.Builder.create()
                .addFieldClause(GroupResourcePlacementState.FIELD_NAME_RESOURCE_TYPE,
                        state.resourceType)
                .build());

        if (isGlobal(state)) {
            logInfo("Quering for global placements for resource description: [%s] and resource count: [%s]...",
                    state.resourceDescriptionLink, state.resourceCount);

            Query tenantLinksQuery = QueryUtil.addTenantAndGroupClause(null);
            q.querySpec.query.addBooleanClause(tenantLinksQuery);
        } else {
            logInfo("Quering for group [%s] placements for resource description: [%s] and resource count: [%s]...",
                    state.tenantLinks, state.resourceDescriptionLink, state.resourceCount);

            Query tenantLinksQuery = QueryUtil.addTenantAndGroupClause(state.tenantLinks);
            q.querySpec.query.addBooleanClause(tenantLinksQuery);
        }

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

        if (containerDesc.memoryLimit != null) {
            Query memoryLimitClause = Query.Builder.create()
                    .addRangeClause(GroupResourcePlacementState.FIELD_NAME_AVAILABLE_MEMORY,
                            NumericRange.createLongRange(
                                    state.resourceCount * containerDesc.memoryLimit,
                                    Long.MAX_VALUE, true, false),
                            Occurance.SHOULD_OCCUR)
                    .addRangeClause(GroupResourcePlacementState.FIELD_NAME_MEMORY_LIMIT,
                            NumericRange.createEqualRange(0L),
                            Occurance.SHOULD_OCCUR)
                    .build();

            q.querySpec.query.addBooleanClause(memoryLimitClause);
            logInfo("Placement query includes memory limit of: [%s]: ", containerDesc.memoryLimit);
        }

        /*
         * TODO Get the placements from the DB ordered by priority. This should work..but it doesn't :)
         * QueryTask.QueryTerm sortTerm = new QueryTask.QueryTerm(); sortTerm.propertyName =
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
        query.query(
                q,
                (r) -> {
                    if (r.hasException()) {
                        failTask("Exception while quering for placements", r.getException());
                    } else if (r.hasResult()) {
                        placements.add(r.getResult());
                    } else {
                        if (placements.isEmpty()) {
                            if (retriesCount > 0) {
                                getHost().schedule(() -> {
                                    queryGroupResourcePlacements(state,
                                            this.containerDescription, retriesCount - 1);
                                }, QueryUtil.QUERY_RETRY_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
                            } else {
                                if (state.tenantLinks != null && !state.tenantLinks.isEmpty()) {
                                    proceedTo(SubStage.QUERYING_GLOBAL);
                                } else {
                                    failTask("No available group placements.", null);
                                }
                            }
                            return;
                        }

                        proceedTo(isGlobal(state) ? SubStage.SELECTED_GLOBAL : SubStage.SELECTED, s -> {
                            /* Use a LinkedHashMap to preserve the order */
                            s.resourcePoolsPerGroupPlacementLinks = new LinkedHashMap<>();
                            s.resourcePoolsPerGroupPlacementLinks.putAll(buildResourcePoolsMap(
                                    containerDesc, placements));
                        });
                    }
                });
    }

    private LinkedHashMap<String, String> buildResourcePoolsMap(ContainerDescription containerDesc,
            List<GroupResourcePlacementState> placements) {
        LinkedHashMap<String, String> resPools = new LinkedHashMap<String, String>();
        List<GroupResourcePlacementState> filteredPlacements = null;
        if (containerDesc.deploymentPolicyId != null && !containerDesc.deploymentPolicyId
                .isEmpty()) {
            filteredPlacements = placements
                    .stream()
                    .filter((e) -> {
                        return e.deploymentPolicyLink != null
                                && e.deploymentPolicyLink
                                        .endsWith(containerDesc.deploymentPolicyId);
                    }).collect(Collectors.toList());
        }

        if (filteredPlacements == null || filteredPlacements.isEmpty()) {
            filteredPlacements = placements;
        }

        /* for now sort the placements by priority in memory. */
        filteredPlacements.sort((g1, g2) -> g1.priority - g2.priority);

        for (GroupResourcePlacementState placement : filteredPlacements) {
            logInfo("Placements found: [%s] with available instances: [%s] and available memory: [%s].",
                    placement.documentSelfLink, placement.availableInstancesCount,
                    placement.availableMemory);
            resPools.put(placement.documentSelfLink, placement.resourcePoolLink);
        }
        return resPools;
    }

    private boolean isGlobal(ReservationTaskState state) {
        return state.taskSubStage != null
                && state.taskSubStage.ordinal() >= SubStage.QUERYING_GLOBAL.ordinal();
    }

    private void selectPlacementComputeHosts(ReservationTaskState state,
            Set<String> resourcePools) {

        // create placement selection tasks
        PlacementHostSelectionTaskState placementTask = new PlacementHostSelectionTaskState();
        placementTask.documentSelfLink = getSelfId() + "-reservation"
                + (isGlobal(state) ? "-global" : "");
        placementTask.resourceDescriptionLink = state.resourceDescriptionLink;
        placementTask.resourcePoolLinks = new ArrayList<>(resourcePools);
        placementTask.resourceCount = state.resourceCount;
        placementTask.resourceType = state.resourceType;
        placementTask.tenantLinks = state.tenantLinks;
        placementTask.customProperties = state.customProperties;
        placementTask.contextId = getContextId(state);
        placementTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED,
                isGlobal(state) ? SubStage.HOSTS_SELECTED_GLOBAL : SubStage.HOSTS_SELECTED,
                TaskStage.STARTED, SubStage.ERROR);
        placementTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, PlacementHostSelectionTaskService.FACTORY_LINK)
                .setBody(placementTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating placement task", e);
                        return;
                    }
                    proceedTo(isGlobal(state) ? SubStage.PLACEMENT_GLOBAL : SubStage.PLACEMENT);
                }));
    }

    private void hostsSelected(ReservationTaskState state) {
        if (state.hostSelections == null || state.hostSelections.isEmpty()) {
            if (state.tenantLinks != null && !state.tenantLinks.isEmpty()) {
                proceedTo(SubStage.QUERYING_GLOBAL);
            } else {
                failTask("Available compute host can't be selected.", null);
            }
            return;
        }

        logInfo("Hosts selected " + state.hostSelections);

        final Set<String> resourcePools = new HashSet<>();
        state.hostSelections.forEach(hs -> resourcePools.addAll(hs.resourcePoolLinks));

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

    private void selectReservation(ReservationTaskState state,
            LinkedHashMap<String, String> resourcePoolsPerGroupPlacementLinks) {
        if (resourcePoolsPerGroupPlacementLinks.isEmpty()) {
            failTask("No available group placements.", null);
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

    private void makeReservation(ReservationTaskState state,
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

    private void getContainerDescription(String resourceDescriptionLink,
            Consumer<ContainerDescription> callbackFunction) {
        if (containerDescription != null) {
            callbackFunction.accept(containerDescription);
            return;
        }
        sendRequest(Operation.createGet(this, resourceDescriptionLink)
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
}
