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
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.ResourcePolicyReservationRequest;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.ReservationAllocationTaskService.ReservationAllocationTaskState;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState.SubStage;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Task implementing the reservation request resource work flow.
 */
public class ReservationTaskService
        extends
        AbstractTaskStatefulService<ReservationTaskService.ReservationTaskState, ReservationTaskService.ReservationTaskState.SubStage> {

    private static final int QUERY_RETRIES_COUNT = Integer.getInteger(
            "com.vmware.admiral.service.policies.query.retries", 2);

    public static final String DISPLAY_NAME = "Reservation";

    // cached container description
    private volatile ContainerDescription containerDescription;

    public static class ReservationTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ReservationTaskState.SubStage> {
        private static final String FIELD_NAME_RESOURCE_DESC_LINK = "resourceDescriptionLink";
        private static final String FIELD_NAME_TENANT_LINKS = "tenantLinks";
        private static final String FIELD_NAME_RESOURCE_COUNT = "resourceCount";
        private static final String FIELD_NAME_GROUP_RESOURCE_POLICY = "groupResourcePolicyLink";

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
        public String resourceDescriptionLink;

        /** (Required) Type of resource to create. */
        public String resourceType;

        /** (Required) Number of resources to provision. */
        public long resourceCount;

        // Service fields:
        /** (Internal) Set by task. The link to the selected group policy. */
        public String groupResourcePolicyLink;

        /**
         * Set by task. Selected group policy links and associated resourcePoolLinks. Ordered by
         * priority asc
         */
        public LinkedHashMap<String, String> resourcePoolsPerGroupPolicyLinks;

        /** (Internal) Set by task after the ComputeState is found to host the containers */
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
            queryGroupResourcePolicies(state, containerDescription, QUERY_RETRIES_COUNT);
            break;
        case SELECTED:
        case SELECTED_GLOBAL:
            selectPlacementComputeHosts(state, new HashSet<String>(
                    state.resourcePoolsPerGroupPolicyLinks.values()));
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
            makeReservation(state, state.groupResourcePolicyLink,
                    state.resourcePoolsPerGroupPolicyLinks);
            break;
        case QUERYING_GLOBAL:
            // query again but with global group (group set to null):
            queryGroupResourcePolicies(state, containerDescription, QUERY_RETRIES_COUNT);
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
            ReservationTaskState patchBody, ReservationTaskState currentState) {

        if (SubStage.QUERYING_GLOBAL == patchBody.taskSubStage) {
            // In this case try global group instead of the provided one.
            currentState.tenantLinks = null;
        }

        currentState.groupResourcePolicyLink = mergeProperty(currentState.groupResourcePolicyLink,
                patchBody.groupResourcePolicyLink);
        currentState.resourcePoolsPerGroupPolicyLinks = mergeProperty(
                currentState.resourcePoolsPerGroupPolicyLinks,
                patchBody.resourcePoolsPerGroupPolicyLinks);
        currentState.hostSelections = mergeProperty(currentState.hostSelections,
                patchBody.hostSelections);

        return false;
    }

    @Override
    protected void validateStateOnStart(ReservationTaskState state) {
        assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");
        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }
        if (state.resourceType == null || state.resourceType.isEmpty()) {
            state.resourceType = ResourceType.CONTAINER_TYPE.getName();
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(ReservationTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.groupResourcePolicyLink = state.groupResourcePolicyLink;
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        String groupResourcePolicyLink;
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
        reservationAllocationTask.resourcePoolsPerGroupPolicyLinks = reservationTask.resourcePoolsPerGroupPolicyLinks;
        reservationAllocationTask.documentSelfLink = getSelfId();
        reservationAllocationTask.contextId = getContextId(reservationTask);
        reservationAllocationTask.resourceDescriptionLink = reservationTask.resourceDescriptionLink;
        reservationAllocationTask.resourceCount = reservationTask.resourceCount;
        reservationAllocationTask.requestTrackerLink = reservationTask.requestTrackerLink;
        reservationAllocationTask.serviceTaskCallback = ServiceTaskCallback.create(
                reservationTask.documentSelfLink,
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
                            sendSelfPatch(createUpdateSubStageTask(reservationTask,
                                    SubStage.ALLOCATING_RESOURCE_POOL));
                        }));

    }

    private void queryGroupResourcePolicies(ReservationTaskState state,
            ContainerDescription containerDesc, int retriesCount) {

        if (containerDesc == null) {
            getContainerDescription(state.resourceDescriptionLink,
                    (retrievedContDesc) -> queryGroupResourcePolicies(state, retrievedContDesc,
                            retriesCount));
            return;
        }

        // If property __containerHostId exists call ReservationAllocationTaskService
        if (containerDesc.customProperties != null && containerDesc.customProperties
                .containsKey(ReservationAllocationTaskService.CONTAINER_HOST_ID_CUSTOM_PROPERTY)) {
            sendSelfPatch(createUpdateSubStageTask(state,
                    ReservationTaskState.SubStage.RESERVATION_ALLOCATION));
            return;
        }

        // match on group property:
        QueryTask q = QueryUtil.buildQuery(GroupResourcePolicyState.class, false);
        q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;

        if (state.tenantLinks == null || state.tenantLinks.isEmpty()) {

            logInfo("Quering for global policiess for resource description: [%s] and resource count: [%s]...",
                    state.resourceDescriptionLink, state.resourceCount);
        } else {

            logInfo("Quering for group [%s] policies for resource description: [%s] and resource count: [%s]...",
                    state.tenantLinks, state.resourceDescriptionLink, state.resourceCount);
        }

        Query tenantLinksQuery = QueryUtil.addTenantAndGroupClause(state.tenantLinks);
        q.querySpec.query.addBooleanClause(tenantLinksQuery);

        // match on available number of instances:
        QueryTask.Query numOfInstancesClause = new QueryTask.Query();

        QueryTask.Query moreInstancesThanRequired = new QueryTask.Query()
                .setTermPropertyName(GroupResourcePolicyState.FIELD_NAME_AVAILABLE_INSTANCES_COUNT)
                .setNumericRange(NumericRange.createLongRange(state.resourceCount,
                        Long.MAX_VALUE, true, false))
                .setTermMatchType(MatchType.TERM);

        QueryTask.Query unlimitedInstances = new QueryTask.Query()
                .setTermPropertyName(GroupResourcePolicyState.FIELD_NAME_MAX_NUMBER_INSTANCES)
                .setNumericRange(NumericRange.createEqualRange(0L))
                .setTermMatchType(MatchType.TERM);

        moreInstancesThanRequired.occurance = Occurance.SHOULD_OCCUR;
        numOfInstancesClause.addBooleanClause(moreInstancesThanRequired);
        unlimitedInstances.occurance = Occurance.SHOULD_OCCUR;
        numOfInstancesClause.addBooleanClause(unlimitedInstances);
        numOfInstancesClause.occurance = Occurance.MUST_OCCUR;

        q.querySpec.query.addBooleanClause(numOfInstancesClause);

        if (containerDesc.memoryLimit != null) {
            QueryTask.Query memoryLimitClause = new QueryTask.Query();

            QueryTask.Query moreAvailableMemoryThanRequired = new QueryTask.Query()
                    .setTermPropertyName(GroupResourcePolicyState.FIELD_NAME_AVAILABLE_MEMORY)
                    .setNumericRange(NumericRange
                            .createLongRange(state.resourceCount * containerDesc.memoryLimit,
                                    Long.MAX_VALUE, true, false))
                    .setTermMatchType(MatchType.TERM).setOccurance(Occurance.SHOULD_OCCUR);

            QueryTask.Query unlimitedPolicies = new QueryTask.Query()
                    .setTermPropertyName(GroupResourcePolicyState.FIELD_NAME_MEMORY_LIMIT)
                    .setNumericRange(NumericRange.createEqualRange(0L))
                    .setTermMatchType(MatchType.TERM).setOccurance(Occurance.SHOULD_OCCUR);

            memoryLimitClause.addBooleanClause(moreAvailableMemoryThanRequired);
            memoryLimitClause.addBooleanClause(unlimitedPolicies);
            memoryLimitClause.occurance = Occurance.MUST_OCCUR;

            q.querySpec.query.addBooleanClause(memoryLimitClause);
            logInfo("Policy query includes memory limit of: [%s]: ", containerDesc.memoryLimit);
        }

        /*
         * TODO Get the policies from the DB ordered by priority. This should work..but it doesn't :)
         * QueryTask.QueryTerm sortTerm = new QueryTask.QueryTerm(); sortTerm.propertyName =
         * GroupResourcePolicyState.FIELD_NAME_PRIORITY; sortTerm.propertyType =
         * ServiceDocumentDescription.TypeName.LONG; q.querySpec.sortTerm = sortTerm;
         * q.querySpec.sortOrder = QueryTask.QuerySpecification.SortOrder.ASC;
         * q.querySpec.options.add(QueryTask.QuerySpecification.QueryOption.SORT);
         */

        QueryUtil.addExpandOption(q);

        ServiceDocumentQuery<GroupResourcePolicyState> query = new ServiceDocumentQuery<>(
                getHost(),
                GroupResourcePolicyState.class);
        List<GroupResourcePolicyState> policies = new ArrayList<>();
        query.query(
                q,
                (r) -> {
                    if (r.hasException()) {
                        failTask("Exception while quering for policies", r.getException());
                    } else if (r.hasResult()) {
                        policies.add(r.getResult());
                    } else {
                        if (policies.isEmpty()) {
                            if (retriesCount > 0) {
                                getHost().schedule(() -> {
                                    queryGroupResourcePolicies(state,
                                            this.containerDescription, retriesCount - 1);
                                }, QueryUtil.QUERY_RETRY_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
                            } else {
                                if (state.tenantLinks != null && !state.tenantLinks.isEmpty()) {
                                    sendSelfPatch(createUpdateSubStageTask(state,
                                            SubStage.QUERYING_GLOBAL));
                                } else {
                                    failTask("No available group policies.", null);
                                }
                            }
                            return;
                        }

                        ReservationTaskState body = createUpdateSubStageTask(state,
                                isGlobal(state) ? SubStage.SELECTED_GLOBAL : SubStage.SELECTED);
                        /* Use a LinkedHashMap to preserve the order */
                        body.resourcePoolsPerGroupPolicyLinks = new LinkedHashMap<>();
                        body.resourcePoolsPerGroupPolicyLinks.putAll(buildResourcePoolsMap(
                                containerDesc, policies));
                        sendSelfPatch(body);
                    }
                });
    }

    private LinkedHashMap<String, String> buildResourcePoolsMap(ContainerDescription containerDesc,
            List<GroupResourcePolicyState> policies) {
        LinkedHashMap<String, String> resPools = new LinkedHashMap<String, String>();
        List<GroupResourcePolicyState> filteredPolicies = null;
        if (containerDesc.deploymentPolicyId != null && !containerDesc.deploymentPolicyId
                .isEmpty()) {
            filteredPolicies = policies
                    .stream()
                    .filter((e) -> {
                        return e.deploymentPolicyLink != null
                                && e.deploymentPolicyLink
                                        .endsWith(containerDesc.deploymentPolicyId);
                    }).collect(Collectors.toList());
        }

        if (filteredPolicies == null || filteredPolicies.isEmpty()) {
            filteredPolicies = policies;
        }

        /* for now sort the policies by priority in memory. */
        filteredPolicies.sort((g1, g2) -> g1.priority - g2.priority);

        for (GroupResourcePolicyState policy : filteredPolicies) {
            logInfo("Policies found: [%s] with available instances: [%s] and available memory: [%s].",
                    policy.documentSelfLink, policy.availableInstancesCount,
                    policy.availableMemory);
            resPools.put(policy.documentSelfLink, policy.resourcePoolLink);
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
        placementTask.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
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
                    sendSelfPatch(createUpdateSubStageTask(state,
                            isGlobal(state) ? SubStage.PLACEMENT_GLOBAL
                                    : SubStage.PLACEMENT));
                }));
    }

    private void hostsSelected(ReservationTaskState state) {
        if (state.hostSelections == null || state.hostSelections.isEmpty()) {
            if (state.tenantLinks != null && !state.tenantLinks.isEmpty()) {
                sendSelfPatch(createUpdateSubStageTask(state,
                        SubStage.QUERYING_GLOBAL));
            } else {
                failTask("Available compute host can't be selected.", null);
            }
            return;
        }

        final Set<String> resourcePools = new HashSet<>();
        state.hostSelections.forEach(hs -> resourcePools.addAll(hs.resourcePoolLinks));

        if (state.resourcePoolsPerGroupPolicyLinks != null) {
            state.resourcePoolsPerGroupPolicyLinks = state.resourcePoolsPerGroupPolicyLinks
                    .entrySet().stream().filter((e) -> resourcePools.contains(e.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (k1, k2) -> k1, LinkedHashMap::new));
        } else {
            state.resourcePoolsPerGroupPolicyLinks = new LinkedHashMap<>();
        }

        selectReservation(state, state.resourcePoolsPerGroupPolicyLinks);
    }

    private void selectReservation(ReservationTaskState state,
            LinkedHashMap<String, String> resourcePoolsPerGroupPolicyLinks) {
        if (resourcePoolsPerGroupPolicyLinks.isEmpty()) {
            failTask("No available group policies.", null);
            return;
        }

        Iterator<String> iter = resourcePoolsPerGroupPolicyLinks.keySet().iterator();
        String policyLink = iter.next();
        iter.remove();

        logInfo("Current selected policy: %s", policyLink);
        ReservationTaskState patchBody = createUpdateSubStageTask(state,
                SubStage.RESERVATION_SELECTED);
        patchBody.resourcePoolsPerGroupPolicyLinks = resourcePoolsPerGroupPolicyLinks;
        patchBody.groupResourcePolicyLink = policyLink;
        sendSelfPatch(patchBody);
    }

    private void makeReservation(ReservationTaskState state,
            String policyLink, LinkedHashMap<String, String> resourcePoolsPerGroupPolicyLinks) {

        // TODO: implement more sophisticated algorithm to pick the right group policy based on
        // availability and current allocation of resources.

        ResourcePolicyReservationRequest reservationRequest = new ResourcePolicyReservationRequest();
        reservationRequest.resourceCount = state.resourceCount;
        reservationRequest.resourceDescriptionLink = state.resourceDescriptionLink;

        logInfo("Reserving instances: %d for descLink: %s and groupPolicyId: %s",
                reservationRequest.resourceCount, reservationRequest.resourceDescriptionLink,
                Service.getId(policyLink));

        sendRequest(Operation
                .createPatch(this, policyLink)
                .setBody(reservationRequest)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(
                                        "Failure reserving group policy: %s. Retrying with the next one...",
                                        e.getMessage());
                                selectReservation(state, resourcePoolsPerGroupPolicyLinks);
                                return;
                            }

                            GroupResourcePolicyState policy = o
                                    .getBody(GroupResourcePolicyState.class);
                            ReservationTaskState body = createUpdateSubStageTask(state,
                                    SubStage.COMPLETED);
                            body.taskInfo.stage = TaskStage.FINISHED;
                            body.customProperties = mergeCustomProperties(state.customProperties,
                                    policy.customProperties);
                            body.groupResourcePolicyLink = policy.documentSelfLink;
                            body.resourcePoolsPerGroupPolicyLinks = state.resourcePoolsPerGroupPolicyLinks;

                            sendSelfPatch(body);
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

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();

        setDocumentTemplateIndexingOptions(template, EnumSet.of(PropertyIndexingOption.STORE_ONLY),
                ReservationTaskState.FIELD_NAME_RESOURCE_DESC_LINK,
                ReservationTaskState.FIELD_NAME_TENANT_LINKS,
                ReservationTaskState.FIELD_NAME_RESOURCE_COUNT,
                ReservationTaskState.FIELD_NAME_GROUP_RESOURCE_POLICY);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SINGLE_ASSIGNMENT),
                ReservationTaskState.FIELD_NAME_RESOURCE_DESC_LINK,
                ReservationTaskState.FIELD_NAME_TENANT_LINKS,
                ReservationTaskState.FIELD_NAME_RESOURCE_COUNT,
                ReservationTaskState.FIELD_NAME_GROUP_RESOURCE_POLICY);

        setDocumentTemplateUsageOptions(template, EnumSet.of(PropertyUsageOption.SERVICE_USE),
                ReservationTaskState.FIELD_NAME_GROUP_RESOURCE_POLICY);

        return template;
    }

}
