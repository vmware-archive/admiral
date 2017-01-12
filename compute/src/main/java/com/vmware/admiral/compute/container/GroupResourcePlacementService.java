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

package com.vmware.admiral.compute.container;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Group resource placement service - reserving resources for a given group.
 * <p>
 * The properties <code>maxNumberInstances</code>, <code>memoryLimit</code> and
 * <code>storageLimit</code> are hard constraints for which the allocation will fail if not
 * satisfied.
 * <p>
 * The properties <code>cpuShares</code> is a very soft constraint of how the CPU resources should
 * be utilized and have minimal affect in the actual placement algorithm. It would be used as a soft
 * indicator that a containers with higher CPU utilization requirements should be placed to a less
 * utilized hosts.
 * <p>
 * The resource placements are inspired by the blog-post:
 * https://goldmann.pl/blog/2014/09/11/resource-management-in-docker/
 */
public class GroupResourcePlacementService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.RESOURCE_GROUP_PLACEMENTS;
    public static final String DEFAULT_RESOURCE_POOL_ID = "default-placement-zone";
    public static final String DEFAULT_RESOURCE_POOL_LINK = UriUtils.buildUriPath(
            ResourcePoolService.FACTORY_LINK, DEFAULT_RESOURCE_POOL_ID);
    public static final String DEFAULT_RESOURCE_PLACEMENT_ID = "default-resource-placement";
    public static final String DEFAULT_RESOURCE_PLACEMENT_LINK = UriUtils.buildUriPath(
            FACTORY_LINK, DEFAULT_RESOURCE_PLACEMENT_ID);

    public static final long UNLIMITED_NUMBER_INSTANCES = 0;
    // Docker minimum memory limit is 4MB
    public static final long MIN_MEMORY_LIMIT = 4_194_304;

    public static ResourcePoolState buildDefaultResourcePool() {
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.documentSelfLink = DEFAULT_RESOURCE_POOL_LINK;
        poolState.name = DEFAULT_RESOURCE_POOL_ID;
        poolState.id = poolState.name;
        poolState.maxCpuCount = 1600L;
        poolState.minCpuCount = 16L;
        poolState.currencyUnit = "USD";
        poolState.maxCpuCostPerMinute = 1.0;
        poolState.maxDiskCostPerMinute = 1.0;
        poolState.minMemoryBytes = 1024L * 1024L * 1024L * 46L;
        poolState.maxMemoryBytes = poolState.minMemoryBytes * 2;
        poolState.minDiskCapacityBytes = poolState.maxDiskCapacityBytes = 1024L * 1024L * 1024L
                * 1024L;

        return poolState;
    }

    public static ServiceDocument buildDefaultStateInstance() {
        GroupResourcePlacementState rsrvState = new GroupResourcePlacementState();
        rsrvState.documentSelfLink = DEFAULT_RESOURCE_PLACEMENT_LINK;
        rsrvState.name = DEFAULT_RESOURCE_PLACEMENT_ID;
        rsrvState.resourcePoolLink = DEFAULT_RESOURCE_POOL_LINK;
        rsrvState.tenantLinks = null; // global default group placement
        rsrvState.maxNumberInstances = 1000000;
        rsrvState.priority = 100;

        return rsrvState;
    }

    public static class GroupResourcePlacementState extends MultiTenantDocument {
        public static final String FIELD_NAME_RESOURCE_POOL_LINK = "resourcePoolLink";
        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_MAX_NUMBER_INSTANCES = "maxNumberInstances";
        public static final String FIELD_NAME_AVAILABLE_INSTANCES_COUNT = "availableInstancesCount";
        public static final String FIELD_NAME_ALLOCATED_INSTANCES_COUNT = "allocatedInstancesCount";
        public static final String FIELD_NAME_AVAILABLE_MEMORY = "availableMemory";
        public static final String FIELD_NAME_MEMORY_LIMIT = "memoryLimit";
        public static final String FIELD_NAME_PRIORITY = "priority";
        public static final String FIELD_NAME_RESOURCE_TYPE = "resourceType";
        public static final String FIELD_NAME_DEPLOYMENT_POLICY_LINK = "deploymentPolicyLink";
        public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";

        /** Name of the reservation. */
        @Documentation(description = "Name of the reservation.")
        public String name;

        /** {@link ResourcePoolState} link */
        @Documentation(description = "The link of the ResourcePoolState associated with this placement")
        @UsageOption(option = PropertyUsageOption.LINK)
        public String resourcePoolLink;

        /** The priority with which the group resource placements will be applied */
        @Documentation(description = "The priority with which the group resource placements will be applied." +
                    " Lower number means higher priority.")
        public int priority;

        @Documentation(description = "The resource type for which the group resource quotas will be applied.")
        public String resourceType;

        /**
         * The maximum number of resource instances for this placement for a group.
         * Value of 0 will be considered unlimited.
         */
        @Documentation(description = "The maximum number of resource instances for this placement for a group.")
        public long maxNumberInstances = UNLIMITED_NUMBER_INSTANCES;

        /** Memory limit in bytes per group for a resource pool. */
        @Documentation(description = "Memory limit in bytes per group for a resource pool.")
        public long memoryLimit;

        /** Storage limit in bytes per group for a resource pool. */
        @Documentation(description = " Storage limit in bytes per group for a resource pool. (not used)")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public long storageLimit;// TODO: Not included in the reservation algorithm yet

        /**
         * Percentages of the relative CPU sharing in a given resource pool. This is not an actual
         * limit but a guideline of how much CPU should be divided among all containers running at a
         * given time.
         */
        @Documentation(description = " Percentages of the relative CPU sharing in a given resource pool. "
                + "This is not an actual limit but a guideline of how much CPU "
                + "should be divided among all containers running at a given time.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public int cpuShares; // TODO: Not included in the reservation algorithm yet

        /**
         * Link to the deployment policy of this placement. If the same policy is set to a container
         * description, then that description should be provisioned from this placement.
         */
        @Documentation(description = " Link to the deployment policy of this placement. If the same policy "
                + "is set to a container description, then that description should be provisioned from this placement.")
        @UsageOption(option = PropertyUsageOption.LINK)
        public String deploymentPolicyLink;

        /** Custom properties. */
        @Documentation(description = "Custom properties.")
        public Map<String, String> customProperties;

        /** Set by Task. The number of resource instances currently available to be allocated */
        @Documentation(description = "The number of resource instances currently available to be allocated")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public long availableInstancesCount;

        /** Set by Task. The number of resource instances currently in use. */
        @Documentation(description = "The number of resource instances currently allocated.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public long allocatedInstancesCount;

        /** Set by Task. The memory currently available to be allocated. */
        @Documentation(description = "The memory currently available to be allocated.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public long availableMemory;

        /** Set by Task. The number of used instances linked to their Resource descriptions. */
        @Documentation(description = "The number of used instances linked to their Resource descriptions.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @PropertyOptions(indexing = { PropertyIndexingOption.STORE_ONLY })
        public Map<String, Long> resourceQuotaPerResourceDesc;

        /** Set by Task. Memory quota per resource desc. */
        @Documentation(description = "Memory quota per resource desc.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @PropertyOptions(indexing = { PropertyIndexingOption.STORE_ONLY })
        public Map<String, Long> memoryQuotaPerResourceDesc;
    }

    /**
     * An DTO used during PATCH operation in order to reserve resources.
     */
    public static class ResourcePlacementReservationRequest {
        public long resourceCount;
        public String resourceDescriptionLink;
        public String referer;
    }

    /**
     * State with in-line, expanded ResourcePoolLink.
     */
    public static class GroupResourcePlacementPoolState extends GroupResourcePlacementState {
        public static URI buildUri(URI reservationServiceLink) {
            return UriUtils.extendUriWithQuery(reservationServiceLink,
                    UriUtils.URI_PARAM_ODATA_EXPAND,
                    GroupResourcePlacementState.FIELD_NAME_RESOURCE_POOL_LINK);
        }

        public ResourcePoolState resourcePool;

        public static GroupResourcePlacementPoolState create(
                ResourcePoolState resourcePool,
                GroupResourcePlacementState groupResourcePlacementState) {
            GroupResourcePlacementPoolState poolState = new GroupResourcePlacementPoolState();
            groupResourcePlacementState.copyTo(poolState);

            poolState.name = groupResourcePlacementState.name;
            poolState.tenantLinks = groupResourcePlacementState.tenantLinks;
            poolState.maxNumberInstances = groupResourcePlacementState.maxNumberInstances;
            poolState.availableInstancesCount = groupResourcePlacementState.availableInstancesCount;
            poolState.allocatedInstancesCount = groupResourcePlacementState.allocatedInstancesCount;
            poolState.resourceQuotaPerResourceDesc = groupResourcePlacementState.resourceQuotaPerResourceDesc;
            poolState.customProperties = groupResourcePlacementState.customProperties;
            poolState.cpuShares = groupResourcePlacementState.cpuShares;
            poolState.memoryLimit = groupResourcePlacementState.memoryLimit;
            poolState.storageLimit = groupResourcePlacementState.storageLimit;

            poolState.resourcePoolLink = resourcePool.documentSelfLink;
            poolState.resourcePool = resourcePool;

            return poolState;
        }
    }

    public GroupResourcePlacementService() {
        super(GroupResourcePlacementState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleGet(Operation get) {
        GroupResourcePlacementState currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null
                && get.getUri().getQuery().contains(UriUtils.URI_PARAM_ODATA_EXPAND);
        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        // retrieve the ResourcePool and include in an augmented version of the current state
        Operation getResourcePool = Operation
                .createGet(this, currentState.resourcePoolLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                get.fail(e);
                                return;
                            }
                            ResourcePoolState resourcePool = o.getBody(ResourcePoolState.class);
                            GroupResourcePlacementPoolState reservationResourcePool = GroupResourcePlacementPoolState
                                    .create(resourcePool, currentState);
                            get.setBody(reservationResourcePool).complete();
                        });
        sendRequest(getResourcePool);

    }

    @Override
    public void handleCreate(Operation start) {
        if (!checkForBody(start)) {
            return;
        }
        GroupResourcePlacementState state = start.getBody(GroupResourcePlacementState.class);
        logFine("Initial name is %s", state.name);

        validateStateOnStart(state, start, (o) -> {
            start.complete();
        });

    }

    @Override
    public void handlePut(Operation put) {
        GroupResourcePlacementState currentState = getState(put);
        GroupResourcePlacementState putBody = put.getBody(GroupResourcePlacementState.class);

        // make sure that the active placements are not overridden before validation
        putBody.resourceQuotaPerResourceDesc = currentState.resourceQuotaPerResourceDesc;

        validateStateOnStart(putBody, put, (a) -> {
            // make sure the current placements are not overridden
            currentState.name = putBody.name;
            currentState.priority = putBody.priority;
            currentState.customProperties = putBody.customProperties;

            long reserved = currentState.allocatedInstancesCount;
            if (putBody.maxNumberInstances != UNLIMITED_NUMBER_INSTANCES
                    && putBody.maxNumberInstances < reserved) {
                put.fail(new LocalizableValidationException("'maxNumberInstances' cannot be less than the"
                        + " currently reserved number of instances: " + reserved,
                        "compute.placements.too.few.max-instances", reserved));
                return;
            }
            currentState.maxNumberInstances = putBody.maxNumberInstances;
            currentState.availableInstancesCount = putBody.maxNumberInstances != UNLIMITED_NUMBER_INSTANCES
                    ? currentState.maxNumberInstances - reserved : UNLIMITED_NUMBER_INSTANCES;

            if (currentState.allocatedInstancesCount > 0) { // there are already active instances
                                                            // for the placement
                if (currentState.cpuShares != putBody.cpuShares
                        || currentState.storageLimit != putBody.storageLimit
                        || !currentState.resourcePoolLink.equals(putBody.resourcePoolLink)) {
                    put.fail(new LocalizableValidationException(
                            "'cpuShares' or 'placement zones' can't be modified while there are active instances for the placement",
                            "compute.placements.active.instances"));
                    return;
                }
            }

            // update only for placements without active placements:
            currentState.cpuShares = putBody.cpuShares;
            currentState.storageLimit = putBody.storageLimit;
            currentState.resourcePoolLink = putBody.resourcePoolLink;
            currentState.deploymentPolicyLink = putBody.deploymentPolicyLink;

            long reservedMemory = currentState.memoryLimit - currentState.availableMemory;
            if (reservedMemory > putBody.memoryLimit) {
                put.fail(new LocalizableValidationException(
                        "'Memory limit cannot be less than the currently reserved memory: "
                                + reserved,
                        "compute.placements.memory.limit.too.little", reserved));
                return;
            }
            currentState.memoryLimit = putBody.memoryLimit;
            currentState.availableMemory = currentState.memoryLimit - reservedMemory;

            currentState.tenantLinks = putBody.tenantLinks;

            setState(put, currentState);
            put.setBody(currentState).complete();
        });

    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            return;
        }

        if (!isReservationServiceTaskAuthorizedRequest(patch)) {
            logWarning("Request not authorized.");
            patch.fail(Operation.STATUS_CODE_FORBIDDEN);
            return;
        }

        ResourcePlacementReservationRequest request = patch
                .getBody(ResourcePlacementReservationRequest.class);

        GroupResourcePlacementState state = getState(patch);
        adjustStat(ResourcePlacementReservationRequest.class.getSimpleName().toString(), 1);

        final long currentCount = state.maxNumberInstances != UNLIMITED_NUMBER_INSTANCES
                ? state.availableInstancesCount - request.resourceCount
                : UNLIMITED_NUMBER_INSTANCES;
        logInfo("%s: reserving resource count [%d] with available count [%d] for desc: %s",
                state.name, request.resourceCount, state.availableInstancesCount,
                request.resourceDescriptionLink);

        if (currentCount < 0) {
            patch.fail(new LocalizableValidationException(
                    "Requested instances are more than the available resource placement: "
                            + state.availableInstancesCount,
                            "compute.placements.requested.too.many.instances", state.availableInstancesCount));
            return;
        } else if (currentCount > state.maxNumberInstances) {
            logWarning(
                    "Releasing the requested resource placement of %d is more than the max %d for the current available %d",
                    request.resourceCount, state.maxNumberInstances, state.availableInstancesCount);
            patch.complete();
            return;
        } else if (request.resourceDescriptionLink == null
                || request.resourceDescriptionLink.isEmpty()) {
            patch.fail(new LocalizableValidationException("'resourceDescriptionLink' is required.", "compute.placements.resource-desc.required"));
            return;
        }

        if (state.resourceQuotaPerResourceDesc == null) {
            state.resourceQuotaPerResourceDesc = new HashMap<>();
        }

        Long countPerDesc = state.resourceQuotaPerResourceDesc
                .get(request.resourceDescriptionLink);
        if (countPerDesc == null) {
            if (request.resourceCount < 0) {
                patch.fail(new LocalizableValidationException(
                        "Releasing placement do not exist for requested resourceDescriptionLink: "
                                + request.resourceDescriptionLink,
                                "compute.placements.resource.not.exists", request.resourceDescriptionLink));
                return;
            }
            state.resourceQuotaPerResourceDesc.put(request.resourceDescriptionLink,
                    request.resourceCount);
        } else {
            long currentCountPerDesc = countPerDesc + request.resourceCount;
            if (currentCountPerDesc < 0) {
                patch.fail(new LocalizableValidationException(
                        "Releasing placement is more than previously requested for the resourceDescriptionLink: "
                                + request.resourceDescriptionLink,
                                "compute.placements.release.too.much", request.resourceDescriptionLink));
                return;
            }
            state.resourceQuotaPerResourceDesc.put(request.resourceDescriptionLink,
                    currentCountPerDesc);
        }

        state.availableInstancesCount = currentCount;
        state.allocatedInstancesCount += request.resourceCount;

        sendRequest(Operation
                .createGet(this, request.resourceDescriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (Operation.STATUS_CODE_NOT_FOUND == o.getStatusCode()
                                    || e instanceof CancellationException) {
                                logWarning(
                                        "Resource description %s not found. There might be some incosistencies with memory allocations",
                                        request.resourceDescriptionLink);
                                patch.setBody(state).complete();
                                return;
                            }
                            if (e != null) {
                                patch.fail(new LocalizableValidationException(
                                            "Unable to get the resource description with link: " + request.resourceDescriptionLink,
                                            "compute.resource-placement.unavailable",
                                            new String[] { request.resourceDescriptionLink }));
                                return;
                            }

                            Long memoryBytes = null;
                            ResourceType resourceType = ResourceType.fromName(state.resourceType);
                            if (resourceType == ResourceType.COMPUTE_TYPE) {
                                ComputeDescription desc = o.getBody(ComputeDescription.class);
                                memoryBytes = desc.totalMemoryBytes;
                            } else {
                                ContainerDescriptionService.ContainerDescription desc = o
                                        .getBody(
                                                ContainerDescriptionService.ContainerDescription.class);
                                memoryBytes = desc.memoryLimit;
                            }

                            if (reserveMemory(patch, request, state, memoryBytes)) {
                                /*
                                 * The reserveMemory method will fail the patch if the requested
                                 * memory is not right
                                 */
                                patch.setBody(state).complete();
                            }
                        }));

    }

    private boolean reserveMemory(Operation patch,
            ResourcePlacementReservationRequest request,
            GroupResourcePlacementState state, Long memoryBytes) {

        // TODO what do we do in this case?
        if (memoryBytes == null) {
            return true;
        }

        long requestedMemory = memoryBytes * request.resourceCount;
        long currentMemory = state.availableMemory - requestedMemory;

        if (state.memoryLimit != 0) {
            if (currentMemory < 0) {
                patch.fail(new LocalizableValidationException(
                        "Requested memory is more than the available memory placement: "
                                + state.availableMemory,
                                "compute.placements.too.much.memory.requested", state.availableMemory));
                return false;
            }

            state.availableMemory = currentMemory;
        }

        if (state.memoryQuotaPerResourceDesc == null) {
            state.memoryQuotaPerResourceDesc = new HashMap<>();
        }

        Long allMemory = state.memoryQuotaPerResourceDesc.get(request.resourceDescriptionLink);

        if (allMemory == null) {
            state.memoryQuotaPerResourceDesc.put(request.resourceDescriptionLink, requestedMemory);
        } else {
            state.memoryQuotaPerResourceDesc
                    .put(request.resourceDescriptionLink, requestedMemory + allMemory);
        }

        return true;
    }

    @Override
    public void handleDelete(Operation delete) {
        GroupResourcePlacementState state = getState(delete);
        if (state == null || state.documentSelfLink == null) {
            delete.complete();
            return;
        }

        if (state.allocatedInstancesCount > 0) {
            throw new LocalizableValidationException(
                    "Can't delete with active reservations: " + state.allocatedInstancesCount,
                    "compute.placements.delete.with.active.reservation", state.allocatedInstancesCount);
        }

        super.handleDelete(delete);
    }

    private QueryTask createGroupResourcePlacementQueryTask(GroupResourcePlacementState state) {
        QueryTask q = QueryUtil.buildQuery(GroupResourcePlacementState.class, false);
        q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;

        QueryTask.Query resourcePoolClause = new QueryTask.Query()
                .setTermPropertyName(GroupResourcePlacementPoolState.FIELD_NAME_RESOURCE_POOL_LINK)
                .setTermMatchValue(state.resourcePoolLink);

        QueryTask.Query notThisGroupClause = new QueryTask.Query()
                .setTermPropertyName(GroupResourcePlacementPoolState.FIELD_NAME_SELF_LINK)
                .setTermMatchValue(getSelfLink());
        notThisGroupClause.occurance = QueryTask.Query.Occurance.MUST_NOT_OCCUR;

        q.querySpec.query.addBooleanClause(resourcePoolClause);

        q.querySpec.query.addBooleanClause(notThisGroupClause);
        QueryUtil.addExpandOption(q);
        return q;
    }

    private void validateStateOnStart(GroupResourcePlacementState state, Operation operation,
            Consumer<Void> callbackFunction) {
        assertNotEmpty(state.name, "name");
        state.name = state.name.trim();

        assertNotEmpty(state.resourcePoolLink, "placement zone");

        if (state.priority < 0) {
            throw new LocalizableValidationException("'priority' must be greater or equal to zero.",
                    "compute.placements.validation.priority");
        }
        if (state.maxNumberInstances < 0) {
            throw new LocalizableValidationException(
                    "'maxNumberInstances' must be greater or eq to zero.", "compute.placements.validation.max-instances");
        }

        if (state.resourceType == null) {
            state.resourceType = ResourceType.CONTAINER_TYPE.getName();
        }

        if (state.memoryLimit != 0 && state.memoryLimit < MIN_MEMORY_LIMIT) {
            throw new LocalizableValidationException(
                    String.format("'memoryLimit' must be 0 (no limit) or at least %s bytes. (%sMB).",
                            MIN_MEMORY_LIMIT, (MIN_MEMORY_LIMIT / 1024) / 1024),
                    "compute.placements.validation.memory", MIN_MEMORY_LIMIT, (MIN_MEMORY_LIMIT / 1024) / 1024);
        }

        if (state.cpuShares < 0) {
            throw new LocalizableValidationException(
                    "'cpuShares' must be greater than or equal to zero.", "compute.placements.validation.cpu");
        }

        validatePlacementSize(state, operation, (o) -> {

            if (state.resourceQuotaPerResourceDesc == null
                    || state.resourceQuotaPerResourceDesc.isEmpty()) {
                state.availableInstancesCount = state.maxNumberInstances;
                state.allocatedInstancesCount = 0;
                state.availableMemory = state.memoryLimit;
            }

            callbackFunction.accept(null);
        });
    }

    private void validatePlacementSize(GroupResourcePlacementState state, Operation operation,
            Consumer<Void> callbackFunction) {
        sendRequest(Operation.createGet(this, state.resourcePoolLink)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                operation.fail(e);
                                return;
                            }

                            ResourcePoolState resourcePool = o.getBody(ResourcePoolState.class);
                            long totalMemory = 0;
                            if (resourcePool.maxMemoryBytes != null) {
                                totalMemory = resourcePool.maxMemoryBytes.longValue();
                            }

                            if (state.memoryLimit > totalMemory) {
                                String errorMesg = String.format("Not enough memory in this placement zone. Total memory in placement zone: "
                                        + "%s, requested: %s",
                                        totalMemory, state.memoryLimit);
                                operation.fail(new LocalizableValidationException(errorMesg,
                                        "compute.placements.not.enough.memory.in.zone", totalMemory, state.memoryLimit));
                                return;
                            }

                            // TODO This query does not depend on the enclosing one. Probably should
                            // be run in parallel
                            getOtherPlacementsInResourcePoolAndValidate(state, operation,
                                    totalMemory,
                                    callbackFunction);

                        }));
    }

    private void getOtherPlacementsInResourcePoolAndValidate(GroupResourcePlacementState state,
            Operation operation,
            long totalMemory, Consumer<Void> callbackFunction) {
        ServiceDocumentQuery<GroupResourcePlacementState> query = new ServiceDocumentQuery<>(
                getHost(),
                GroupResourcePlacementState.class);
        QueryTask q = createGroupResourcePlacementQueryTask(state);
        List<GroupResourcePlacementState> groupResourcePlacementStates = new ArrayList<>();
        query.query(q, (r) -> {
            if (r.hasException()) {
                operation.fail(new RuntimeException(
                        "Exception while querying for GroupResourcePlacementStates",
                        r.getException()));
                return;
            } else if (r.hasResult()) {
                groupResourcePlacementStates.add(r.getResult());
            } else {
                long allPlacementMemory = groupResourcePlacementStates.stream()
                        .mapToLong(groupResourcePlacementState -> {
                            return Long.valueOf(groupResourcePlacementState.memoryLimit);
                        }).sum();

                long availableMemory = totalMemory - allPlacementMemory;
                if (availableMemory > 0 && availableMemory < state.memoryLimit) {
                    String errorMsg = String.format("Memory already reserved by other placements. Available memory: %s, requested: %s",
                                    availableMemory, state.memoryLimit);
                    operation.fail(new LocalizableValidationException(errorMsg,
                            "compute.placement.memory.unavailable", availableMemory, state.memoryLimit));
                    return;
                }

                callbackFunction.accept(null);
            }
        });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        GroupResourcePlacementState template = (GroupResourcePlacementState) super.getDocumentTemplate();
        template.resourceQuotaPerResourceDesc = new HashMap<>();
        template.memoryQuotaPerResourceDesc = new HashMap<>();
        template.customProperties = new HashMap<String, String>(1);
        template.customProperties.put("propKey string", "customPropertyValue string");
        // Having multiple resource descriptions hit the default limit. 1MB should be enough for
        // ~13 760 containers
        template.documentDescription.serializedStateSizeLimit = 1024 * 1024; // 1MB
        template.documentDescription.versionRetentionLimit = 5;

        return template;
    }

    private boolean isReservationServiceTaskAuthorizedRequest(Operation patch) {
        ResourcePlacementReservationRequest request = patch
                .getBody(ResourcePlacementReservationRequest.class);

        return request.referer != null
                && (request.referer
                        .startsWith(ManagementUriParts.REQUEST_RESERVATION_TASKS)
                        || request.referer
                                .startsWith(ManagementUriParts.REQUEST_RESERVATION_REMOVAL_TASKS)
                        || request.referer
                                .startsWith(ManagementUriParts.REQUEST_COMPUTE_RESERVATION_TASKS));

    }
}
