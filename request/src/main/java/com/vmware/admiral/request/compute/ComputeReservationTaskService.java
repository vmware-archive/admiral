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
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.ResourcePlacementReservationRequest;
import com.vmware.admiral.request.compute.ComputeReservationTaskService.ComputeReservationTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

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
            QUERYING_GLOBAL,
            SELECTED_GLOBAL,
            RESERVATION_SELECTED,
            COMPLETED,
            ERROR;

        }

        @Documentation(description = "The description that defines the requested resource.")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public long resourceCount;

        // Service fields:
        @Documentation(description = "Set by task. The link to the selected group placement.")
        @PropertyOptions(usage = SERVICE_USE, indexing = STORE_ONLY)
        public String groupResourcePlacementLink;

        @Documentation(description = "Set by task. Selected group placement links and associated resourcePoolLinks. Ordered by priority asc")
        @PropertyOptions(usage = SERVICE_USE, indexing = STORE_ONLY)
        public LinkedHashMap<String, String> resourcePoolsPerGroupPlacementLinks;
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
        case SELECTED_GLOBAL:
            quatasSelected(state, this.computeDescription);
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
            ComputeReservationTaskState patchBody, ComputeReservationTaskState currentState) {

        currentState.groupResourcePlacementLink = mergeProperty(currentState.groupResourcePlacementLink,
                patchBody.groupResourcePlacementLink);
        currentState.resourcePoolsPerGroupPlacementLinks = mergeProperty(
                currentState.resourcePoolsPerGroupPlacementLinks,
                patchBody.resourcePoolsPerGroupPlacementLinks);
        return false;
    }

    @Override
    protected void validateStateOnStart(ComputeReservationTaskState state) {
        assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");
        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
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

        // match on group property:
        QueryTask q = QueryUtil.buildQuery(GroupResourcePlacementState.class, false);
        q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;

        if (tenantLinks == null || tenantLinks.isEmpty()) {

            logInfo("Quering for global placements for resource description: [%s] and resource count: [%s]...",
                    state.resourceDescriptionLink, state.resourceCount);
        } else {

            logInfo("Quering for group placements in [%s], for resource description: [%s] and resource count: [%s]...",
                    tenantLinks, state.resourceDescriptionLink, state.resourceCount);
        }

        Query tenantLinksQuery = QueryUtil.addTenantAndGroupClause(tenantLinks);
        q.querySpec.query.addBooleanClause(tenantLinksQuery);

        // match on available number of instances:
        QueryTask.Query numOfInstancesClause = new QueryTask.Query()
                .setTermPropertyName(GroupResourcePlacementState.FIELD_NAME_AVAILABLE_INSTANCES_COUNT)
                .setNumericRange(NumericRange.createLongRange(state.resourceCount,
                        Long.MAX_VALUE, true, false))
                .setTermMatchType(MatchType.TERM);

        q.querySpec.query.addBooleanClause(numOfInstancesClause);

        if (computeDesc.totalMemoryBytes > 0) {
            QueryTask.Query memoryLimitClause = new QueryTask.Query();

            QueryTask.Query moreAvailableMemoryThanRequired = new QueryTask.Query()
                    .setTermPropertyName(GroupResourcePlacementState.FIELD_NAME_AVAILABLE_MEMORY)
                    .setNumericRange(NumericRange
                            .createLongRange(state.resourceCount * computeDesc.totalMemoryBytes,
                                    Long.MAX_VALUE, true, false))
                    .setTermMatchType(MatchType.TERM);

            QueryTask.Query unlimitedPlacements = new QueryTask.Query()
                    .setTermPropertyName(GroupResourcePlacementState.FIELD_NAME_MEMORY_LIMIT)
                    .setNumericRange(NumericRange.createEqualRange(0L))
                    .setTermMatchType(MatchType.TERM);

            memoryLimitClause.addBooleanClause(moreAvailableMemoryThanRequired);
            memoryLimitClause.addBooleanClause(unlimitedPlacements);
            memoryLimitClause.occurance = Occurance.SHOULD_OCCUR;

            q.querySpec.query.addBooleanClause(memoryLimitClause);
            logInfo("Placement query includes memory limit of: [%s]: ", computeDesc.totalMemoryBytes);
        }

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
                        sendSelfPatch(createUpdateSubStageTask(state, SubStage.QUERYING_GLOBAL));
                    } else {
                        failTask("No available group placements.", null);
                    }
                    return;
                }

                ComputeReservationTaskState body = createUpdateSubStageTask(state,
                        isGlobal(state) ? SubStage.SELECTED_GLOBAL : SubStage.SELECTED);
                // Use a LinkedHashMap to preserve the order
                body.resourcePoolsPerGroupPlacementLinks = new LinkedHashMap<>();
                // for now sort the placements by priority in memory.
                placements.sort((g1, g2) -> g1.priority - g2.priority);
                for (GroupResourcePlacementState placement : placements) {
                    logInfo("Placements found: [%s] with available instances: [%s] and available memory: [%s].",
                            placement.documentSelfLink, placement.availableInstancesCount,
                            placement.availableMemory);
                    body.resourcePoolsPerGroupPlacementLinks.put(placement.documentSelfLink,
                            placement.resourcePoolLink);
                }
                sendSelfPatch(body);
            }
        });
    }

    private boolean isGlobal(ComputeReservationTaskState state) {
        return state.taskSubStage != null
                && state.taskSubStage.ordinal() >= SubStage.QUERYING_GLOBAL.ordinal();
    }

    private void quatasSelected(ComputeReservationTaskState state, ComputeDescription computeDesc) {
        if (computeDesc == null) {
            getComputeDescription(state.resourceDescriptionLink,
                    (retrievedCompDesc) -> quatasSelected(state, retrievedCompDesc));
            return;
        }

        boolean hasEndpointLink = hasProp(computeDesc.customProperties,
                ComputeConstants.ENDPOINT_LINK_PROP_NAME);

        if (state.resourcePoolsPerGroupPlacementLinks == null) {
            state.resourcePoolsPerGroupPlacementLinks = new LinkedHashMap<>();
        }

        List<Operation> queryOperations = new ArrayList<>();
        for (String poolLink : state.resourcePoolsPerGroupPlacementLinks.values()) {
            queryOperations.add(Operation.createGet(this, poolLink));
        }

        if (!queryOperations.isEmpty()) {
            OperationJoin
                    .create(queryOperations.toArray(new Operation[0]))
                    .setCompletion(
                            (ops, exs) -> {
                                if (exs != null) {
                                    failTask(
                                            "Failure retrieving ResourcePools: "
                                                    + Utils.toString(exs),
                                            null);
                                    return;
                                }

                                Set<String> pools = ops.values().stream()
                                        .map((v) -> v.getBody(ResourcePoolState.class))
                                        .filter((r) -> r != null)
                                        .filter((r) -> hasProp(r.customProperties,
                                                ComputeConstants.ENDPOINT_LINK_PROP_NAME)
                                                || hasEndpointLink)
                                        .map((r) -> r.documentSelfLink).collect(Collectors.toSet());

                                state.resourcePoolsPerGroupPlacementLinks = state.resourcePoolsPerGroupPlacementLinks
                                        .entrySet()
                                        .stream()
                                        .filter((e) -> pools.contains(e.getValue()))
                                        .collect(
                                                Collectors.toMap(Map.Entry::getKey,
                                                        Map.Entry::getValue,
                                                        (k1, k2) -> k1, LinkedHashMap::new));

                                selectReservation(state, state.resourcePoolsPerGroupPlacementLinks);
                            })
                    .sendWith(this);
        } else {
            selectReservation(state, state.resourcePoolsPerGroupPlacementLinks);
        }
    }

    /**
     * @param customProperties
     * @param key
     * @return
     */
    private boolean hasProp(Map<String, String> customProperties, String key) {
        return customProperties != null && customProperties.containsKey(key);
    }

    private void selectReservation(ComputeReservationTaskState state,
            LinkedHashMap<String, String> resourcePoolsPerGroupPlacementLinks) {
        if (resourcePoolsPerGroupPlacementLinks.isEmpty()) {
            failTask("No available group placements.", null);
            return;
        }

        Iterator<String> iter = resourcePoolsPerGroupPlacementLinks.keySet().iterator();
        String placementLink = iter.next();
        iter.remove();

        logInfo("Current selected placement: %s", placementLink);
        ComputeReservationTaskState patchBody = createUpdateSubStageTask(state,
                SubStage.RESERVATION_SELECTED);
        patchBody.resourcePoolsPerGroupPlacementLinks = resourcePoolsPerGroupPlacementLinks;
        patchBody.groupResourcePlacementLink = placementLink;
        sendSelfPatch(patchBody);
    }

    private void makeReservation(ComputeReservationTaskState state,
            String placementLink, LinkedHashMap<String, String> resourcePoolsPerGroupPlacementLinks) {

        // TODO: implement more sophisticated algorithm to pick the right group placement based on
        // availability and current allocation of resources.

        ResourcePlacementReservationRequest reservationRequest = new ResourcePlacementReservationRequest();
        reservationRequest.resourceCount = state.resourceCount;
        reservationRequest.resourceDescriptionLink = state.resourceDescriptionLink;

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
                            ComputeReservationTaskState body = createUpdateSubStageTask(state,
                                    SubStage.COMPLETED);
                            body.taskInfo.stage = TaskStage.FINISHED;
                            body.customProperties = mergeCustomProperties(state.customProperties,
                                    placement.customProperties);
                            body.groupResourcePlacementLink = placement.documentSelfLink;
                            body.resourcePoolsPerGroupPlacementLinks = state.resourcePoolsPerGroupPlacementLinks;

                            sendSelfPatch(body);
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
