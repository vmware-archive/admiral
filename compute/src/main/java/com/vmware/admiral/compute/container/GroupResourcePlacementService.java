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
import com.vmware.admiral.common.util.ServiceDocumentQuery.ServiceDocumentQueryElementResult;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.service.common.MultiTenantDocument;
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

    public static final int DEFAULT_PLACEMENT_PRIORITY = 100;

    public static final long UNLIMITED_NUMBER_INSTANCES = 0;
    // Docker minimum memory limit is 4MB
    public static final long MIN_MEMORY_LIMIT_BYTES = 4_194_304;

    public static ResourcePoolState buildDefaultResourcePool() {
        return buildResourcePool(DEFAULT_RESOURCE_POOL_ID);
    }

    public static ResourcePoolState buildResourcePool(String resourcePoolName) {
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.documentSelfLink = UriUtils.buildUriPath(
                ResourcePoolService.FACTORY_LINK, resourcePoolName);
        poolState.name = resourcePoolName;
        poolState.id = poolState.name;

        return poolState;
    }

    public static ServiceDocument buildDefaultStateInstance() {
        return buildStateInstance(DEFAULT_RESOURCE_PLACEMENT_ID, DEFAULT_RESOURCE_POOL_LINK);
    }

    public static ServiceDocument buildStateInstance(String resourcePlacementName,
            String resourcePoolDocumentSelfLink) {
        GroupResourcePlacementState rsrvState = new GroupResourcePlacementState();
        rsrvState.documentSelfLink = UriUtils.buildUriPath(
                FACTORY_LINK, resourcePlacementName);
        rsrvState.name = resourcePlacementName;
        rsrvState.resourcePoolLink = resourcePoolDocumentSelfLink;
        rsrvState.tenantLinks = null; // global default group placement
        rsrvState.maxNumberInstances = 1000000;
        rsrvState.priority = DEFAULT_PLACEMENT_PRIORITY;

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

        /**
         * This property should be set only on placements that were automatically generated for a
         * scheduler host. If set to true, this placement should be automatically deleted after the host
         * removal.
         */
        public static final String AUTOGENERATED_PLACEMENT_PROP_NAME = "__autogeneratedPlacement";

        /** Name of the reservation. */
        @Documentation(description = "Name of the reservation.")
        public String name;

        /** {@link ResourcePoolState} link */
        @Documentation(description = "The link of the ResourcePoolState associated with this placement")
        @UsageOption(option = PropertyUsageOption.LINK)
        public String resourcePoolLink;

        /** The priority with which the group resource placements will be applied */
        @Documentation(description = "The priority with which the group resource placements will be applied."
                +
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
        @PropertyOptions(indexing = { PropertyIndexingOption.CASE_INSENSITIVE,
                PropertyIndexingOption.EXPAND, PropertyIndexingOption.FIXED_ITEM_NAME })
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
        @Documentation(description = "Deprecated, not in use.The number of used instances linked to their Resource descriptions.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @PropertyOptions(indexing = { PropertyIndexingOption.STORE_ONLY })
        @Deprecated
        public Map<String, Long> resourceQuotaPerResourceDesc;

        /** Set by Task. Memory quota per resource desc. */
        @Documentation(description = "Deprecated, not in use.Memory quota per resource desc.")
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @PropertyOptions(indexing = { PropertyIndexingOption.STORE_ONLY })
        @Deprecated
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
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
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
                            GroupResourcePlacementPoolState reservationResourcePool =
                                    GroupResourcePlacementPoolState
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
            state.availableInstancesCount = state.maxNumberInstances;
            state.allocatedInstancesCount = 0;
            start.complete();
        });
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            logFine("Ignoring converted PUT.");
            put.complete();
            return;
        }

        GroupResourcePlacementState currentState = getState(put);
        GroupResourcePlacementState putBody = put.getBody(GroupResourcePlacementState.class);

        validateStateOnStart(putBody, put, (a) -> {
            // make sure the current placements are not overridden
            currentState.name = putBody.name;
            currentState.priority = putBody.priority;
            currentState.customProperties = putBody.customProperties;

            long reserved = currentState.allocatedInstancesCount;
            if (putBody.maxNumberInstances != UNLIMITED_NUMBER_INSTANCES
                    && putBody.maxNumberInstances < reserved) {
                put.fail(new LocalizableValidationException("'maxNumberInstances' cannot be less "
                        + "than the currently reserved number of instances: " + reserved,
                        "compute.placements.too.few.max-instances", reserved));
                return;
            }
            currentState.maxNumberInstances = putBody.maxNumberInstances;
            currentState.availableInstancesCount =
                    putBody.maxNumberInstances != UNLIMITED_NUMBER_INSTANCES
                            ? currentState.maxNumberInstances - reserved
                            : UNLIMITED_NUMBER_INSTANCES;

            if (currentState.allocatedInstancesCount > 0) { // there are already active instances
                                                            // for the placement
                if (currentState.cpuShares != putBody.cpuShares
                        || currentState.storageLimit != putBody.storageLimit
                        || !currentState.resourcePoolLink.equals(putBody.resourcePoolLink)) {
                    put.fail(new LocalizableValidationException(
                            "'cpuShares' or 'placement zones' can't be modified while there are "
                                    + "active instances for the placement",
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
        adjustStat(ResourcePlacementReservationRequest.class.getSimpleName(), 1);

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
                    "compute.placements.requested.too.many.instances",
                    state.availableInstancesCount));
            return;
        } else if (currentCount > state.maxNumberInstances) {
            logWarning(
                    "Releasing the requested resource placement of %d is more than the max %d "
                            + "for the current available %d",
                    request.resourceCount, state.maxNumberInstances, state.availableInstancesCount);
            patch.complete();
            return;
        } else if (request.resourceDescriptionLink == null
                || request.resourceDescriptionLink.isEmpty()) {
            patch.fail(new LocalizableValidationException("'resourceDescriptionLink' is required.",
                    "compute.placements.resource-desc.required"));
            return;
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
                                        "Resource description %s not found. There might be "
                                                + "some inconsistencies with memory allocations",
                                        request.resourceDescriptionLink);
                                patch.setBody(state).complete();
                                return;
                            }
                            if (e != null) {
                                patch.fail(new LocalizableValidationException(
                                        "Unable to get the resource description with link: "
                                                + request.resourceDescriptionLink,
                                        "compute.resource-placement.unavailable",
                                        request.resourceDescriptionLink));
                                return;
                            }

                            ContainerDescriptionService.ContainerDescription desc = o.getBody(
                                    ContainerDescriptionService.ContainerDescription.class);
                            Long memoryBytes = desc.memoryLimit;

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

        return true;
    }

    @Override
    public void handleDelete(Operation delete) {
        GroupResourcePlacementState state = getState(delete);
        if (state == null || state.documentSelfLink == null) {
            delete.complete();
            return;
        }

        countResourcesForPlacement(state, (r) -> {
            if (r.hasException()) {
                delete.fail(r.getException());
            } else if (r.getCount() > 0) {
                long count = r.getCount();

                if (state.allocatedInstancesCount != count) {
                    logWarning("Reservation mismatch detected for placement %s!! "
                            + "allocatedInstancesCount=%d actual=%d",
                            state.documentSelfLink, state.allocatedInstancesCount, count);
                }

                Exception e = new LocalizableValidationException(
                        "Can't delete with active reservations: " + count,
                        "compute.placements.delete.with.active.reservation", count);
                delete.fail(e);
                return;
            }

            super.handleDelete(delete);
        });
    }

    private QueryTask createGroupResourcePlacementQueryTask(GroupResourcePlacementState state) {
        QueryTask q = QueryUtil.buildQuery(GroupResourcePlacementState.class, true);

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
                    "'maxNumberInstances' must be greater or eq to zero.",
                    "compute.placements.validation.max-instances");
        }

        if (state.resourceType == null) {
            state.resourceType = ResourceType.CONTAINER_TYPE.getName();
        }

        if (state.memoryLimit != 0 && state.memoryLimit < MIN_MEMORY_LIMIT_BYTES) {
            long minMemoryLimitMb = MIN_MEMORY_LIMIT_BYTES / (1024 * 1024);
            throw new LocalizableValidationException(
                    String.format("'memoryLimit' must be 0 (no limit) or at least %s bytes (%sMB).",
                            MIN_MEMORY_LIMIT_BYTES, minMemoryLimitMb),
                    "compute.placements.validation.memory", MIN_MEMORY_LIMIT_BYTES,
                    minMemoryLimitMb);
        }

        if (state.cpuShares < 0) {
            throw new LocalizableValidationException(
                    "'cpuShares' must be greater than or equal to zero.",
                    "compute.placements.validation.cpu");
        }

        validatePlacementSize(state, operation, (o) -> {

            state.availableMemory = state.memoryLimit;
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

                            if (totalMemory > 0 && state.memoryLimit > totalMemory) {
                                String errorMesg = String.format(
                                        "Not enough memory in this placement zone. "
                                                + "Total memory in placement zone: %s, "
                                                + "requested: %s",
                                        totalMemory, state.memoryLimit);
                                operation.fail(new LocalizableValidationException(errorMesg,
                                        "compute.placements.not.enough.memory.in.zone",
                                        totalMemory, state.memoryLimit));
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
                    String errorMsg = String.format("Memory already reserved by other placements. "
                            + "Available memory: %s, requested: %s",
                            availableMemory, state.memoryLimit);
                    operation.fail(new LocalizableValidationException(errorMsg,
                            "compute.placement.memory.unavailable", availableMemory,
                            state.memoryLimit));
                    return;
                }

                callbackFunction.accept(null);
            }
        });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        GroupResourcePlacementState template =
                (GroupResourcePlacementState) super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        template.customProperties = new HashMap<>(1);
        template.customProperties.put("propKey string", "customPropertyValue string");
        // Having multiple resource descriptions hit the default limit. 1MB should be enough for
        // ~13 760 containers
        template.documentDescription.serializedStateSizeLimit = 1024 * 1024; // 1MB
        template.resourceQuotaPerResourceDesc = new HashMap<>();
        template.memoryQuotaPerResourceDesc = new HashMap<>();

        return template;
    }

    private boolean isReservationServiceTaskAuthorizedRequest(Operation patch) {
        ResourcePlacementReservationRequest request = patch
                .getBody(ResourcePlacementReservationRequest.class);

        return request.referer != null
                && (request.referer
                        .startsWith(ManagementUriParts.REQUEST_RESERVATION_TASKS)
                        || request.referer
                                .startsWith(ManagementUriParts.REQUEST_RESERVATION_REMOVAL_TASKS));

    }

    @SuppressWarnings("unchecked")
    private <T extends ServiceDocument> void countResourcesForPlacement(
            GroupResourcePlacementState state,
            Consumer<ServiceDocumentQueryElementResult<T>> completionHandler) {
        QueryTask queryTask;
        Class<T> resourceClass;

        if (ResourceType.CONTAINER_TYPE.getName().equals(state.resourceType)) {
            resourceClass = (Class<T>) ContainerState.class;
            queryTask = QueryUtil.buildPropertyQuery(resourceClass,
                    ContainerState.FIELD_NAME_GROUP_RESOURCE_PLACEMENT_LINK,
                    state.documentSelfLink);
        } else {
            throw new LocalizableValidationException("Unsupported placement resourceType "
                    + state.resourceType,
                    "compute.placements.invalid.resource.type");
        }

        QueryUtil.addCountOption(queryTask);

        new ServiceDocumentQuery<>(getHost(), resourceClass)
                .query(queryTask, completionHandler);
    }

}
