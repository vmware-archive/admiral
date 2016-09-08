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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Task that assigns {@link ComputeState}s to {@link ResourcePoolState}s based on tag conditions
 * defined in {@link ElasticPlacementZoneState}s.
 */
public class ElasticPlacementZoneAssignmentTaskService extends
        AbstractTaskStatefulService<
                ElasticPlacementZoneAssignmentTaskService.ElasticPlacementZoneAssignmentTaskState,
                DefaultSubStage> {

    public static final String FACTORY_LINK =
            ManagementUriParts.REQUEST_PROVISION_ELASTIC_PLACEMENT_TASKS;
    public static final String DISPLAY_NAME = "Elastic Placement Zone Assignment";

    /**
     * The state associated with a {@link ElasticPlacementZoneAssignmentTaskService}.
     */
    public static class ElasticPlacementZoneAssignmentTaskState
            extends TaskServiceDocument<DefaultSubStage> {
    }

    // cached reference to the patch state
    private ElasticPlacementZoneAssignmentTaskState state;

    /**
     * Constructs a new instance.
     */
    public ElasticPlacementZoneAssignmentTaskService() {
        super(ElasticPlacementZoneAssignmentTaskState.class, DefaultSubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(ElasticPlacementZoneAssignmentTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            this.state = state;
            queryElasticPlacementZones();
            break;
        case COMPLETED:
            complete(state, DefaultSubStage.COMPLETED);
            break;
        case ERROR:
            completeWithError(state, DefaultSubStage.ERROR);
            break;
        default:
            break;
        }
    }

    @Override
    protected void validateStateOnStart(ElasticPlacementZoneAssignmentTaskState state)
            throws IllegalArgumentException {
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ElasticPlacementZoneAssignmentTaskState patchBody,
            ElasticPlacementZoneAssignmentTaskState currentState) {
        return false;
    }

    /**
     * Retrieves elastic placement zone definitions.
     */
    private void queryElasticPlacementZones() {
        Query query = Query.Builder.create()
                .addKindFieldClause(ElasticPlacementZoneState.class)
                .build();
        QueryTask queryTask = QueryTask.Builder.create()
                .setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        ServiceDocumentQuery<ElasticPlacementZoneState> queryHelper =
                new ServiceDocumentQuery<ElasticPlacementZoneState>(
                        getHost(), ElasticPlacementZoneState.class);
        List<ElasticPlacementZoneState> elasticPlacementZones = new ArrayList<>();
        queryHelper.query(
                queryTask,
                (r) -> {
                    if (r.hasException()) {
                        failTask("Error querying for elastic placement zones.", r.getException());
                    } else if (r.hasResult()) {
                        elasticPlacementZones.add(r.getResult());
                    } else {
                        if (elasticPlacementZones.isEmpty()) {
                            logInfo("No elastic placement zones found, exiting task.");
                            sendSelfPatch(createUpdateSubStageTask(this.state,
                                    DefaultSubStage.COMPLETED));
                        } else {
                            queryComputesPerZone(elasticPlacementZones);
                        }
                    }
                });
    }

    /**
     * Retrieves computes for each of the given elastic placement zones.
     */
    private void queryComputesPerZone(List<ElasticPlacementZoneState> elasticPlacementZones) {
        List<Operation> queryOperations = new ArrayList<>(elasticPlacementZones.size());
        Map<Long, ElasticPlacementZoneState> epzByOperationId =
                new HashMap<>(elasticPlacementZones.size());
        for (ElasticPlacementZoneState zone : elasticPlacementZones) {
            Query.Builder queryBuilder = Query.Builder.create()
                    .addKindFieldClause(ComputeState.class);
            for (String tagLink : zone.tagLinksToMatch) {
                // all tagLinksToMatch must be set on the compute
                queryBuilder.addCollectionItemClause(ResourceState.FIELD_NAME_TAG_LINKS, tagLink);
            }

            QueryTask task = QueryTask.Builder.createDirectTask()
                    .setQuery(queryBuilder.build())
                    .addOption(QueryOption.SELECT_LINKS)
                    .addLinkTerm(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK)
                    .build();

            Operation queryOperation = Operation.createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                    .setBody(task);
            epzByOperationId.put(queryOperation.getId(), zone);
            queryOperations.add(queryOperation);
        }

        OperationJoin.create(queryOperations).setCompletion((ops, exs) -> {
            if (exs != null) {
                failTask("Errors occured when querying computes: " + Utils.toString(exs),
                        exs.values().iterator().next());
                return;
            }

            // target RP link -> (compute link -> current RP link)
            Map<String, Map<String, String>> currentComputeRpPerTargetRp = new HashMap<>();

            for (Operation op : ops.values()) {
                ServiceDocumentQueryResult r = op.getBody(QueryTask.class).results;
                Map<String, String> rpPerCompute = new HashMap<>();
                if (r != null && r.selectedLinksPerDocument != null) {
                    for (Map.Entry<String, Map<String, String>> entry :
                            r.selectedLinksPerDocument.entrySet()) {
                        String computeLink = entry.getKey();
                        Map<String, String> selectedLinks = entry.getValue();
                        String currentRpLink = selectedLinks != null ? selectedLinks
                                .get(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK) : null;
                        rpPerCompute.put(computeLink, currentRpLink);
                    }
                }
                currentComputeRpPerTargetRp.put(epzByOperationId.get(op.getId()).resourcePoolLink,
                        rpPerCompute);
            }

            assignResourcePool(currentComputeRpPerTargetRp);
        }).sendWith(this);
    }

    /**
     * Changes compute RPs to match EPZ definitions.
     */
    private void assignResourcePool(Map<String, Map<String, String>> currentComputeRpPerTargetRp) {
        // compute -> set of target RPs (should be one but could be more in the case of conflicts)
        Map<String, Set<String>> newRpPerCompute = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> newRpEntry :
                currentComputeRpPerTargetRp.entrySet()) {
            String newRpLink = newRpEntry.getKey();
            for (Map.Entry<String, String> computeEntry : newRpEntry.getValue().entrySet()) {
                String computeLink = computeEntry.getKey();
                String currentRpLink = computeEntry.getValue();
                if (newRpLink.equals(currentRpLink)) {
                    continue;
                }

                Set<String> rpLinks = newRpPerCompute.get(computeLink);
                if (rpLinks == null) {
                    rpLinks = new HashSet<>();
                    newRpPerCompute.put(computeLink, rpLinks);
                }
                rpLinks.add(newRpLink);
            }
        }

        // create patch operation for each compute which RP needs to be changed
        List<Operation> patchOperations = new ArrayList<>(newRpPerCompute.size());
        for (Map.Entry<String, Set<String>> entry : newRpPerCompute.entrySet()) {
            String computeLink = entry.getKey();
            Set<String> rpLinks = entry.getValue();
            if (rpLinks.size() > 1) {
                logWarning("Compute '%s' matches more than one target resource pools: %s",
                        computeLink, rpLinks);
                continue;
            }

            ComputeState patchComputeState = new ComputeState();
            patchComputeState.documentSelfLink = computeLink;
            patchComputeState.resourcePoolLink = rpLinks.iterator().next();
            patchOperations.add(Operation.createPatch(this, computeLink).setBody(patchComputeState));
        }

        if (patchOperations.isEmpty()) {
            logInfo("%d computes checked, no resource pool change required.",
                    newRpPerCompute.size());
            sendSelfPatch(createUpdateSubStageTask(this.state, DefaultSubStage.COMPLETED));
            return;
        }

        // execute patch operations in parallel
        OperationJoin.create(patchOperations).setCompletion((ops, exs) -> {
            if (exs != null) {
                failTask("Errors occured when changing compute resource pools: "
                        + Utils.toString(exs), exs.values().iterator().next());
                return;
            }
            logInfo("%d computes moved to a different resource pool.", patchOperations.size());
            sendSelfPatch(createUpdateSubStageTask(this.state, DefaultSubStage.COMPLETED));
        }).sendWith(this);
    }
}
