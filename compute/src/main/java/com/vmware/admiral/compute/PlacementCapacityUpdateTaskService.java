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

package com.vmware.admiral.compute;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.PlacementCapacityUpdateTaskService.PlacementCapacityUpdateTaskState;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.QueryUtils.QueryByPages;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Task updating placement capacity and usage figures based on the metrics of the computes
 * participating in the placement.
 */
public class PlacementCapacityUpdateTaskService extends
        AbstractTaskStatefulService<PlacementCapacityUpdateTaskState,
                PlacementCapacityUpdateTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.PLACEMENT_UPDATE_TASKS;
    public static final String DISPLAY_NAME = "Placement Capacity Update";

    // lower log level by default
    private static final Level DEFAULT_LOG_LEVEL = Level.parse(System.getProperty(
            "com.vmware.admiral.service.EpzComputeEnumerationTaskService.log.level",
            Level.FINE.getName()));

    private static final int COMPUTE_PAGE_SIZE = 16;

    /**
     * Task parameters and internal state.
     */
    public static class PlacementCapacityUpdateTaskState
            extends TaskServiceDocument<PlacementCapacityUpdateTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            QUERY_COMPUTES,
            ACCUMMULATE_COMPUTE_FIGURES,
            UPDATE_RESOURCE_POOL,
            UPDATE_PLACEMENTS,
            COMPLETED,
            ERROR;
        }

        @Documentation(description = "Link to the resource pool which capacity to update.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED }, indexing = STORE_ONLY)
        public String resourcePoolLink;

        @Documentation(description = "Link to the next page of computes to retrieve.")
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL },
                indexing = STORE_ONLY)
        public String nextPageLink;

        @Documentation(description = "Aggregated stats for completed compute pages.")
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL },
                indexing = STORE_ONLY)
        public AggregatedComputeStats aggregatedStats;
    }

    /**
     * Represents collected statistics for a given compute.
     */
    private static class ComputeStats {
        public long totalMemoryBytes;
        public long cpuCoreCount;
        public long cpuMhzPerCore;

        public double cpuUsage;
        public long availableMemoryBytes;
    }

    /**
     * Represents aggregated statistics over multiple computes.
     */
    private static class AggregatedComputeStats {
        @SuppressWarnings("unused")
        public long computeCount;
        public long totalMemoryBytes;
        public long cpuCoreCount;
        @SuppressWarnings("unused")
        public long totalCpuMhz;

        public double cpuUsageSumAllCores;
        public long availableMemoryBytes;
    }

    /**
     * Triggers the capacity update task for the given resource pool. Makes sure no multiple tasks
     * are run in parallel for the same resource pool.
     */
    public static void triggerForResourcePool(Service sender, String resourcePoolLink) {
        PlacementCapacityUpdateTaskState task = new PlacementCapacityUpdateTaskState();
        task.resourcePoolLink = resourcePoolLink;
        task.documentSelfLink = extractRpId(task);

        Operation.createPost(sender.getHost(), PlacementCapacityUpdateTaskService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(task)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_MODIFIED) {
                        sender.getHost().log(Level.FINE,
                                "Capacity update task already running for " + resourcePoolLink);
                        return;
                    }

                    if (e != null) {
                        sender.getHost().log(Level.WARNING,
                                "Failed to start capacity update task for %s: %s", resourcePoolLink,
                                e.getMessage());
                        return;
                    }

                    sender.getHost().log(Level.INFO,
                            "Started capacity update task for " + resourcePoolLink);
                }).sendWith(sender);
    }

    /**
     * Triggers a capacity update task for each resource pool. Makes sure no multiple tasks are
     * run in parallel for the same resource pool.
     */
    public static void triggerForAllResourcePools(Service sender) {
        // TODO pmitrov: add support for tenant links
        Query rpQuery = Query.Builder.create().addKindFieldClause(ResourcePoolState.class).build();
        new QueryByPages<ResourcePoolState>(sender.getHost(), rpQuery, ResourcePoolState.class, null)
                .queryLinks(rpLink ->
                        PlacementCapacityUpdateTaskService.triggerForResourcePool(sender, rpLink))
                .exceptionally(e -> {
                    sender.getHost().log(Level.WARNING,
                            "Failed to start capacity update task for all resource pools: %s",
                            e.getMessage());
                    return null;
                });
    }

    /**
     * Constructs a new instance.
     */
    public PlacementCapacityUpdateTaskService() {
        super(PlacementCapacityUpdateTaskState.class,
                PlacementCapacityUpdateTaskState.SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);

        // these are one-off tasks that are not needed upon completion
        this.setSelfDelete(true);

        this.setLogLevel(DEFAULT_LOG_LEVEL);
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            put.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            put.complete();
            return;
        }

        // unsupported op
        put.fail(Operation.STATUS_CODE_BAD_METHOD);
    }

    @Override
    protected void handleStartedStagePatch(PlacementCapacityUpdateTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            proceedTo(PlacementCapacityUpdateTaskState.SubStage.QUERY_COMPUTES);
            break;
        case QUERY_COMPUTES:
            startComputeQuery(state, null);
            break;
        case ACCUMMULATE_COMPUTE_FIGURES:
            handleComputePage(state);
            break;
        case UPDATE_RESOURCE_POOL:
            updateResourcePool(state);
            break;
        case UPDATE_PLACEMENTS:
            retrieveAndUpdatePlacements(state);
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

    private void startComputeQuery(PlacementCapacityUpdateTaskState state,
            ResourcePoolState resourcePoolState) {
        if (resourcePoolState == null) {
            sendRequest(Operation.createGet(getHost(), state.resourcePoolLink)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask(String.format("Error retrieving resource pool %s",
                                    state.resourcePoolLink), e);
                            return;
                        }

                        startComputeQuery(state, o.getBody(ResourcePoolState.class));
                    }));
            return;
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(resourcePoolState.query)
                .setResultLimit(COMPUTE_PAGE_SIZE)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Error quering for computes", e);
                        return;
                    }

                    ServiceDocumentQueryResult result = o.getBody(QueryTask.class).results;
                    if (result.nextPageLink == null) {
                        logFine("No computes found in resource pool %s", state.resourcePoolLink);
                        proceedTo(PlacementCapacityUpdateTaskState.SubStage.UPDATE_RESOURCE_POOL, s -> {
                            s.aggregatedStats = new AggregatedComputeStats();
                        });
                    } else {
                        proceedTo(PlacementCapacityUpdateTaskState.SubStage.ACCUMMULATE_COMPUTE_FIGURES, s -> {
                            s.aggregatedStats = new AggregatedComputeStats();
                            s.nextPageLink = result.nextPageLink;
                        });
                    }
                }));
    }

    private void handleComputePage(PlacementCapacityUpdateTaskState state) {
        sendRequest(Operation
                .createGet(getHost(), state.nextPageLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Error retrieving computes", e);
                        return;
                    }

                    ServiceDocumentQueryResult result = o.getBody(QueryTask.class).results;
                    List<ComputeState> computes = result.documents.values().stream()
                            .map(json -> Utils.fromJson(json, ComputeState.class))
                            .collect(Collectors.toList());

                    if (computes.isEmpty()) {
                        proceedTo(PlacementCapacityUpdateTaskState.SubStage.UPDATE_RESOURCE_POOL);
                    } else {
                        queryComputeDescriptions(state, computes, result.nextPageLink);
                    }
                }));
    }

    private void queryComputeDescriptions(PlacementCapacityUpdateTaskState state,
            List<ComputeState> computes,
            String nextPageLink) {
        Collection<String> computeDescriptionLinks = computes.stream()
                .map(c -> c.descriptionLink).collect(Collectors.toSet());

        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeDescription.class)
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, computeDescriptionLinks)
                .build();
        QueryTask queryTask = QueryTask.Builder.create().setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT).build();

        final Map<String, ComputeDescription> computeDescriptions = new HashMap<>();
        new ServiceDocumentQuery<ComputeDescription>(getHost(), ComputeDescription.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        failTask("Error querying for compute descriptions.", r.getException());
                    } else if (r.hasResult()) {
                        computeDescriptions.put(r.getDocumentSelfLink(), r.getResult());
                    } else {
                        AggregatedComputeStats aggregatedStats = accummulateComputeFigures(
                                state, computes, computeDescriptions);

                        if (nextPageLink == null) {
                            proceedTo(PlacementCapacityUpdateTaskState.SubStage.UPDATE_RESOURCE_POOL, s -> {
                                s.aggregatedStats = aggregatedStats;
                            });
                        } else {
                            proceedTo(state.taskSubStage, s -> {
                                s.aggregatedStats = aggregatedStats;
                                s.nextPageLink = nextPageLink;
                            });
                        }
                    }
                });
    }

    private AggregatedComputeStats accummulateComputeFigures(
            PlacementCapacityUpdateTaskState state,
            List<ComputeState> computes, Map<String, ComputeDescription> computeDescriptions) {
        AggregatedComputeStats aggregatedStats = state.aggregatedStats != null
                ? state.aggregatedStats : new AggregatedComputeStats();

        for (ComputeState compute : computes) {
            ComputeDescription computeDescription = computeDescriptions.get(compute.descriptionLink);
            if (computeDescription == null) {
                logWarning("No description found for compute '%s', skipping it",
                        compute.documentSelfLink);
                continue;
            }

            ComputeStats stats = getComputeStats(state, compute, computeDescription);
            if (stats == null) {
                continue;
            }

            aggregatedStats.computeCount++;
            aggregatedStats.totalMemoryBytes += stats.totalMemoryBytes;
            aggregatedStats.cpuCoreCount += stats.cpuCoreCount;
            aggregatedStats.totalCpuMhz += stats.cpuCoreCount * stats.cpuMhzPerCore;

            aggregatedStats.availableMemoryBytes += stats.availableMemoryBytes;
            aggregatedStats.cpuUsageSumAllCores += stats.cpuCoreCount * stats.cpuUsage;
        }

        return aggregatedStats;
    }

    private void updateResourcePool(PlacementCapacityUpdateTaskState state) {
        // calculate average cpu usage per core
        double totalCpuUsage = 0.0;
        if (state.aggregatedStats.cpuCoreCount > 0) {
            totalCpuUsage = state.aggregatedStats.cpuUsageSumAllCores
                    / state.aggregatedStats.cpuCoreCount;
        }

        ResourcePoolState rpPatchState = new ResourcePoolState();
        rpPatchState.customProperties = new HashMap<>();
        rpPatchState.customProperties.put(
                ContainerHostDataCollectionService.RESOURCE_POOL_CPU_USAGE_CUSTOM_PROP,
                Double.toString(totalCpuUsage));
        rpPatchState.customProperties.put(
                ContainerHostDataCollectionService.RESOURCE_POOL_AVAILABLE_MEMORY_CUSTOM_PROP,
                Long.toString(state.aggregatedStats.availableMemoryBytes));
        rpPatchState.maxMemoryBytes = state.aggregatedStats.totalMemoryBytes;
        sendRequest(Operation.createPatch(this, state.resourcePoolLink)
                .setBody(rpPatchState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask(String.format("Unable to update resource pool '%s'",
                                state.resourcePoolLink), e);
                        return;
                    }

                    proceedTo(PlacementCapacityUpdateTaskState.SubStage.UPDATE_PLACEMENTS);
                }));
    }

    private void retrieveAndUpdatePlacements(PlacementCapacityUpdateTaskState state) {
        Query query = Query.Builder.create()
                .addKindFieldClause(GroupResourcePlacementState.class)
                .addFieldClause(GroupResourcePlacementState.FIELD_NAME_RESOURCE_POOL_LINK,
                        state.resourcePoolLink)
                .build();
        QueryTask queryTask = QueryTask.Builder.create()
                .setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        List<GroupResourcePlacementState> placements = new ArrayList<>();
        new ServiceDocumentQuery<>(getHost(), GroupResourcePlacementState.class).query(
                queryTask, (r) -> {
                    if (r.hasException()) {
                        failTask(String.format("Error quering placements for resource pool '%s'",
                                state.resourcePoolLink), r.getException());
                    } else if (r.hasResult()) {
                        placements.add(r.getResult());
                    } else {
                        updatePlacements(state, placements);
                    }
                });
    }

    private void updatePlacements(PlacementCapacityUpdateTaskState state,
            List<GroupResourcePlacementState> placements) {
        long diff = placements.stream().map(q -> q.memoryLimit).reduce(0L, (a, b) -> a + b)
                - state.aggregatedStats.totalMemoryBytes;
        if (diff <= 0) {
            logFine("No placement update needed for resource pool '%s'", state.resourcePoolLink);
            proceedTo(PlacementCapacityUpdateTaskState.SubStage.COMPLETED);
            return;
        }

        // Sort the placements by their "normalized" priority (priority divided by the sum of
        // all priorities in the group). We do that because the priorities are relative within
        // the group. E.g. Group A has two placements with priorities 1 and 2; group B has two
        // placements with priorities 100 and 200 thus the normalized priorities will be:
        // 0.33; 0.66 for A and 0.33 and 0.66 for B
        Map<String, Integer> sumOfPrioritiesByGroup = placements
                .stream().collect(Collectors.groupingBy(
                        PlacementCapacityUpdateTaskService::getTenantAndGroupIdentifier,
                        Collectors.summingInt(placement -> placement.priority)));

        Comparator<GroupResourcePlacementService.GroupResourcePlacementState> comparator = (q1,
                q2) -> Double.compare(
                        ((double) q2.priority) /
                                sumOfPrioritiesByGroup.get(getTenantAndGroupIdentifier(q2)),
                        ((double) q1.priority) /
                                sumOfPrioritiesByGroup.get(getTenantAndGroupIdentifier(q1)));

        placements.sort(comparator);
        Set<GroupResourcePlacementService.GroupResourcePlacementState> placementsToUpdate =
                new HashSet<>();
        for (GroupResourcePlacementService.GroupResourcePlacementState placement : placements) {
            if (placement.availableMemory == 0 || placement.memoryLimit == 0) {
                continue;
            }

            placementsToUpdate.add(placement);
            if (diff > placement.availableMemory) {
                placement.memoryLimit -= placement.availableMemory;
                diff -= placement.availableMemory;
            } else {
                placement.memoryLimit -= diff;
                break;
            }
        }

        Collection<Operation> placementUpdateOps = placementsToUpdate.stream()
                .map(p -> Operation.createPut(this, p.documentSelfLink)
                        .setBody(p)
                        .setReferer(getUri()))
                .collect(Collectors.toList());
        OperationJoin.create(placementUpdateOps).setCompletion((ops, exs) -> {
            if (exs != null) {
                failTask("Error updating placements", exs.values().iterator().next());
            } else {
                proceedTo(PlacementCapacityUpdateTaskState.SubStage.COMPLETED);
            }
        }).sendWith(this);
    }

    /**
     * Retrieves a tenant+group string identifier for the given placement.
     */
    private static String getTenantAndGroupIdentifier(GroupResourcePlacementState placement) {
        List<String> tenantAndGroupLinks = QueryUtil.getTenantAndGroupLinks(placement.tenantLinks);
        return tenantAndGroupLinks != null ? String.join("/", tenantAndGroupLinks) : "";
    }

    private ComputeStats getComputeStats(PlacementCapacityUpdateTaskState state,
            ComputeState compute,
            ComputeDescription computeDescription) {
        if (computeDescription.supportedChildren != null) {
            if (computeDescription.supportedChildren.contains(ComputeType.DOCKER_CONTAINER.name())) {
                return getContainerHostStats(compute, computeDescription);
            } else if (computeDescription.supportedChildren.contains(ComputeType.VM_GUEST.name())) {
                return getComputeHostStats(compute, computeDescription);
            }
        }

        return null;
    }

    private ComputeStats getContainerHostStats(ComputeState compute,
            ComputeDescription description) {
        ComputeStats stats = new ComputeStats();

        stats.totalMemoryBytes = PropertyUtils.getPropertyLong(compute.customProperties,
                ContainerHostService.DOCKER_HOST_TOTAL_MEMORY_PROP_NAME).orElse(0L);
        stats.cpuCoreCount = PropertyUtils.getPropertyLong(compute.customProperties,
                ContainerHostService.DOCKER_HOST_NUM_CORES_PROP_NAME).orElse(1L);

        stats.availableMemoryBytes = PropertyUtils.getPropertyLong(compute.customProperties,
                ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME).orElse(
                        stats.totalMemoryBytes);
        stats.cpuUsage = PropertyUtils.getPropertyDouble(compute.customProperties,
                ContainerHostService.DOCKER_HOST_CPU_USAGE_PCT_PROP_NAME).orElse(0.0);

        return stats;
    }

    private ComputeStats getComputeHostStats(ComputeState compute, ComputeDescription description) {
        ComputeStats stats = new ComputeStats();

        stats.totalMemoryBytes = description.totalMemoryBytes;
        stats.cpuCoreCount = description.cpuCount;
        stats.cpuMhzPerCore = description.cpuMhzPerCore;

        // TODO pmitrov: populate usage figures
        stats.availableMemoryBytes = stats.totalMemoryBytes;
        stats.cpuUsage = 0.0;

        return stats;
    }

    private static String extractRpId(PlacementCapacityUpdateTaskState state) {
        return UriUtils.getLastPathSegment(state.resourcePoolLink);
    }
}
