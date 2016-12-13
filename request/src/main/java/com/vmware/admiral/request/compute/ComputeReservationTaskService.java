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
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.ArrayList;
import java.util.HashSet;
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
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.ResourcePlacementReservationRequest;
import com.vmware.admiral.request.compute.ComputeReservationTaskService.ComputeReservationTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

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
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
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
    protected void validateStateOnStart(ComputeReservationTaskState state) {
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

        if (computeDesc.totalMemoryBytes > 0) {
            Query memoryLimitClause = Query.Builder.create(Occurance.SHOULD_OCCUR)
                    .addRangeClause(GroupResourcePlacementState.FIELD_NAME_AVAILABLE_MEMORY,
                            NumericRange.createLongRange(
                                    state.resourceCount * computeDesc.totalMemoryBytes,
                                    Long.MAX_VALUE, true, false))
                    .addRangeClause(GroupResourcePlacementState.FIELD_NAME_MEMORY_LIMIT,
                            NumericRange.createEqualRange(0L))
                    .build();

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
                        proceedTo(SubStage.QUERYING_GLOBAL);
                    } else {
                        failTask("No available group placements.", null);
                    }
                    return;
                }

                proceedTo(isGlobal(state) ? SubStage.SELECTED_GLOBAL : SubStage.SELECTED, s -> {
                    // Use a LinkedHashMap to preserve the order
                    s.resourcePoolsPerGroupPlacementLinks = new LinkedHashMap<>();
                    // for now sort the placements by priority in memory.
                    placements.sort((g1, g2) -> g1.priority - g2.priority);
                    for (GroupResourcePlacementState placement : placements) {
                        logInfo("Placement found: [%s] with available instances: [%s] and available memory: [%s].",
                                placement.documentSelfLink, placement.availableInstancesCount,
                                placement.availableMemory);
                        s.resourcePoolsPerGroupPlacementLinks.put(placement.documentSelfLink,
                                placement.resourcePoolLink);
                    }
                });
            }
        });
    }

    private boolean isGlobal(ComputeReservationTaskState state) {
        return state.taskSubStage != null
                && state.taskSubStage.ordinal() >= SubStage.QUERYING_GLOBAL.ordinal();
    }

    private void quatasSelected(ComputeReservationTaskState state, ComputeDescription computeDesc) {
        if (state.resourcePoolsPerGroupPlacementLinks == null) {
            failTask(null, new IllegalStateException(
                    "resourcePoolsPerGroupPlacementLinks must not be null"));
            return;
        }
        if (computeDesc == null) {
            getComputeDescription(state.resourceDescriptionLink,
                    (retrievedCompDesc) -> quatasSelected(state, retrievedCompDesc));
            return;
        }

        Builder builder = Query.Builder.create()
                .addKindFieldClause(ResourcePoolState.class)
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        state.resourcePoolsPerGroupPlacementLinks.values());

        String endpointLink = getProp(computeDesc.customProperties,
                ComputeProperties.ENDPOINT_LINK_PROP_NAME);
        if (endpointLink != null) {
            builder.addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                    ComputeProperties.ENDPOINT_LINK_PROP_NAME, endpointLink);
        } else {
            builder.addFieldClause(
                    QuerySpecification.buildCompositeFieldName(
                            ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeProperties.ENDPOINT_LINK_PROP_NAME),
                    "*", MatchType.WILDCARD);
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();

        Operation.createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Error retrieving resource pools for the selected placements: ", e);
                        return;
                    }

                    QueryTask task = o.getBody(QueryTask.class);
                    if (task.results != null && task.results.documentLinks != null &&
                            !task.results.documentLinks.isEmpty()) {
                        Set<String> pools = new HashSet<>();
                        pools.addAll(task.results.documentLinks);
                        state.resourcePoolsPerGroupPlacementLinks = state.resourcePoolsPerGroupPlacementLinks
                                .entrySet().stream()
                                .filter((re) -> pools.contains(re.getValue()))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                        (k1, k2) -> k1, LinkedHashMap::new));

                        selectReservation(state, state.resourcePoolsPerGroupPlacementLinks);
                    } else {
                        failTask(String.format("Could not retrieve any of the selected resource " +
                                "pools (endpointLink: '%s'): %s",
                                endpointLink, state.resourcePoolsPerGroupPlacementLinks.values()),
                                null);
                    }
                })
                .sendWith(this);
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
            failTask(null, new IllegalStateException(
                    "resourcePoolsPerGroupPlacementLinks must not be empty"));
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
            String placementLink, LinkedHashMap<String, String> resourcePoolsPerGroupPlacementLinks) {

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
}
