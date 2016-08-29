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
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.ResourcePolicyReservationRequest;
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
public class ComputeReservationTaskService extends
        AbstractTaskStatefulService<ComputeReservationTaskService.ComputeReservationTaskState, ComputeReservationTaskService.ComputeReservationTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Reservation";

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_RESERVATION_TASKS;

    public static class ComputeReservationTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeReservationTaskState.SubStage> {

        public static enum SubStage {
            CREATED, SELECTED, QUERYING_GLOBAL, SELECTED_GLOBAL, RESERVATION_SELECTED, COMPLETED, ERROR;

        }

        @Documentation(description = "The description that defines the requested resource.")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public long resourceCount;

        // Service fields:
        @Documentation(description = "Set by task. The link to the selected group policy.")
        @PropertyOptions(usage = SERVICE_USE, indexing = STORE_ONLY)
        public String groupResourcePolicyLink;

        @Documentation(description = "Set by task. Selected group policy links and associated resourcePoolLinks. Ordered by priority asc")
        @PropertyOptions(usage = SERVICE_USE, indexing = STORE_ONLY)
        public LinkedHashMap<String, String> resourcePoolsPerGroupPolicyLinks;
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
            queryGroupResourcePolicies(state, null);
            break;
        case SELECTED:
        case SELECTED_GLOBAL:
            quatasSelected(state, null);
            break;
        case RESERVATION_SELECTED:
            makeReservation(state, state.groupResourcePolicyLink,
                    state.resourcePoolsPerGroupPolicyLinks);
            break;
        case QUERYING_GLOBAL:
            // query again but with global group (group set to null):
            queryGroupResourcePolicies(state, null);
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

        if (SubStage.QUERYING_GLOBAL == patchBody.taskSubStage) {
            // In this case try global group instead of the provided one.
            currentState.tenantLinks = null;
        }

        currentState.groupResourcePolicyLink = mergeProperty(currentState.groupResourcePolicyLink,
                patchBody.groupResourcePolicyLink);
        currentState.resourcePoolsPerGroupPolicyLinks = mergeProperty(
                currentState.resourcePoolsPerGroupPolicyLinks,
                patchBody.resourcePoolsPerGroupPolicyLinks);
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
        finishedResponse.groupResourcePolicyLink = state.groupResourcePolicyLink;
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        String groupResourcePolicyLink;
    }

    private void queryGroupResourcePolicies(ComputeReservationTaskState state,
            ComputeDescription computeDesc) {

        if (computeDesc == null) {
            getComputeDescription(state.resourceDescriptionLink,
                    (retrievedCompDesc) -> queryGroupResourcePolicies(state, retrievedCompDesc));
            return;
        }

        // match on group property:
        QueryTask q = QueryUtil.buildQuery(GroupResourcePolicyState.class, false);
        q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;

        if (state.tenantLinks == null || state.tenantLinks.isEmpty()) {

            logInfo("Quering for global policies for resource description: [%s] and resource count: [%s]...",
                    state.resourceDescriptionLink, state.resourceCount);
        } else {

            logInfo("Quering for group [%s] policies for resource description: [%s] and resource count: [%s]...",
                    state.tenantLinks, state.resourceDescriptionLink, state.resourceCount);
        }

        Query tenantLinksQuery = QueryUtil.addTenantClause(state.tenantLinks);
        q.querySpec.query.addBooleanClause(tenantLinksQuery);

        // match on available number of instances:
        QueryTask.Query numOfInstancesClause = new QueryTask.Query()
                .setTermPropertyName(GroupResourcePolicyState.FIELD_NAME_AVAILABLE_INSTANCES_COUNT)
                .setNumericRange(NumericRange.createLongRange(state.resourceCount,
                        Long.MAX_VALUE, true, false))
                .setTermMatchType(MatchType.TERM);

        q.querySpec.query.addBooleanClause(numOfInstancesClause);

        if (computeDesc.totalMemoryBytes > 0) {
            QueryTask.Query memoryLimitClause = new QueryTask.Query();

            QueryTask.Query moreAvailableMemoryThanRequired = new QueryTask.Query()
                    .setTermPropertyName(GroupResourcePolicyState.FIELD_NAME_AVAILABLE_MEMORY)
                    .setNumericRange(NumericRange
                            .createLongRange(state.resourceCount * computeDesc.totalMemoryBytes,
                                    Long.MAX_VALUE, true, false))
                    .setTermMatchType(MatchType.TERM);

            QueryTask.Query unlimitedPolicies = new QueryTask.Query()
                    .setTermPropertyName(GroupResourcePolicyState.FIELD_NAME_MEMORY_LIMIT)
                    .setNumericRange(NumericRange.createEqualRange(0L))
                    .setTermMatchType(MatchType.TERM);

            memoryLimitClause.addBooleanClause(moreAvailableMemoryThanRequired);
            memoryLimitClause.addBooleanClause(unlimitedPolicies);
            memoryLimitClause.occurance = Occurance.SHOULD_OCCUR;

            q.querySpec.query.addBooleanClause(memoryLimitClause);
            logInfo("Policy query includes memory limit of: [%s]: ", computeDesc.totalMemoryBytes);
        }

        /*
         * TODO Get the policies from the DB ordered by priority. This should work..but it doesn't
         * :)
         * QueryTask.QueryTerm sortTerm = new QueryTask.QueryTerm(); sortTerm.propertyName =
         * GroupResourcePolicyState.FIELD_NAME_PRIORITY; sortTerm.propertyType =
         * ServiceDocumentDescription.TypeName.LONG; q.querySpec.sortTerm = sortTerm;
         * q.querySpec.sortOrder = QueryTask.QuerySpecification.SortOrder.ASC;
         * q.querySpec.options.add(QueryTask.QuerySpecification.QueryOption.SORT);
         */

        QueryUtil.addExpandOption(q);

        ServiceDocumentQuery<GroupResourcePolicyState> query = new ServiceDocumentQuery<>(getHost(),
                GroupResourcePolicyState.class);
        List<GroupResourcePolicyState> policies = new ArrayList<>();
        query.query(q, (r) -> {
            if (r.hasException()) {
                failTask("Exception while quering for policies", r.getException());
            } else if (r.hasResult()) {
                policies.add(r.getResult());
            } else {
                if (policies.isEmpty()) {
                    if (state.tenantLinks != null && !state.tenantLinks.isEmpty()) {
                        sendSelfPatch(createUpdateSubStageTask(state, SubStage.QUERYING_GLOBAL));
                    } else {
                        failTask("No available group policies.", null);
                    }
                    return;
                }

                ComputeReservationTaskState body = createUpdateSubStageTask(state,
                        isGlobal(state) ? SubStage.SELECTED_GLOBAL : SubStage.SELECTED);
                // Use a LinkedHashMap to preserve the order
                body.resourcePoolsPerGroupPolicyLinks = new LinkedHashMap<>();
                // for now sort the policies by priority in memory.
                policies.sort((g1, g2) -> g1.priority - g2.priority);
                for (GroupResourcePolicyState policy : policies) {
                    logInfo("Policies found: [%s] with available instances: [%s] and available memory: [%s].",
                            policy.documentSelfLink, policy.availableInstancesCount,
                            policy.availableMemory);
                    body.resourcePoolsPerGroupPolicyLinks.put(policy.documentSelfLink,
                            policy.resourcePoolLink);
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

        if (state.resourcePoolsPerGroupPolicyLinks == null) {
            state.resourcePoolsPerGroupPolicyLinks = new LinkedHashMap<>();
        }

        List<Operation> queryOperations = new ArrayList<>();
        for (String poolLink : state.resourcePoolsPerGroupPolicyLinks.values()) {
            queryOperations.add(Operation.createGet(this, poolLink));
        }

        if (!queryOperations.isEmpty()) {
            OperationJoin.create(queryOperations.toArray(new Operation[0]))
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            failTask("Failure retrieving ResourcePools: " + Utils.toString(exs),
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

                        state.resourcePoolsPerGroupPolicyLinks = state.resourcePoolsPerGroupPolicyLinks
                                .entrySet().stream().filter((e) -> pools.contains(e.getValue()))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                        (k1, k2) -> k1, LinkedHashMap::new));

                        selectReservation(state, state.resourcePoolsPerGroupPolicyLinks);
                    }).sendWith(this);
        } else {
            selectReservation(state, state.resourcePoolsPerGroupPolicyLinks);
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
            LinkedHashMap<String, String> resourcePoolsPerGroupPolicyLinks) {
        if (resourcePoolsPerGroupPolicyLinks.isEmpty()) {
            failTask("No available group policies.", null);
            return;
        }

        Iterator<String> iter = resourcePoolsPerGroupPolicyLinks.keySet().iterator();
        String policyLink = iter.next();
        iter.remove();

        logInfo("Current selected policy: %s", policyLink);
        ComputeReservationTaskState patchBody = createUpdateSubStageTask(state,
                SubStage.RESERVATION_SELECTED);
        patchBody.resourcePoolsPerGroupPolicyLinks = resourcePoolsPerGroupPolicyLinks;
        patchBody.groupResourcePolicyLink = policyLink;
        sendSelfPatch(patchBody);
    }

    private void makeReservation(ComputeReservationTaskState state,
            String policyLink, LinkedHashMap<String, String> resourcePoolsPerGroupPolicyLinks) {

        // TODO: implement more sophisticated algorithm to pick the right group policy based on
        // availability and current allocation of resources.

        ResourcePolicyReservationRequest reservationRequest = new ResourcePolicyReservationRequest();
        reservationRequest.resourceCount = state.resourceCount;
        reservationRequest.resourceDescriptionLink = state.resourceDescriptionLink;

        logInfo("Reserving instances: %d for descLink: %s and groupPolicyId: %s",
                reservationRequest.resourceCount, reservationRequest.resourceDescriptionLink,
                Service.getId(policyLink));

        sendRequest(Operation.createPatch(this, policyLink)
                .setBody(reservationRequest)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(
                                "Failure reserving group policy: %s. Retrying with the next one...",
                                e.getMessage());
                        selectReservation(state, resourcePoolsPerGroupPolicyLinks);
                        return;
                    }

                    GroupResourcePolicyState policy = o.getBody(GroupResourcePolicyState.class);
                    ComputeReservationTaskState body = createUpdateSubStageTask(state,
                            SubStage.COMPLETED);
                    body.taskInfo.stage = TaskStage.FINISHED;
                    body.customProperties = mergeCustomProperties(state.customProperties,
                            policy.customProperties);
                    body.groupResourcePolicyLink = policy.documentSelfLink;
                    body.resourcePoolsPerGroupPolicyLinks = state.resourcePoolsPerGroupPolicyLinks;

                    sendSelfPatch(body);
                }));
    }

    private void getComputeDescription(String resourceDescriptionLink,
            Consumer<ComputeDescription> callbackFunction) {
        sendRequest(Operation.createGet(this, resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving description state", e);
                        return;
                    }

                    ComputeDescription desc = o.getBody(ComputeDescription.class);
                    callbackFunction.accept(desc);
                }));
    }
}
