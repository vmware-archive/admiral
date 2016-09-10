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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.ServiceAlreadyStartedException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Monitor service that makes sure {@link ComputeState}s matching tag conditions defined in
 * {@link ElasticPlacementZoneState}s are assigned into the corresponding
 * {@link ResourcePoolState}s.
 *
 * The service can be started through the static {@link #start} and {@link #stop} methods which
 * internally use a scheduled task to make sure there is a single monitor service started at a time.
 *
 * If there are no elastic placement zones found, the service sends itself a {@link #stop} request.
 * On each elastic placement zone creation, the {@link #start} method should be called.
 */
public class ElasticPlacementZoneMonitor extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.ELASTIC_PLACEMENT_ZONE_MONITOR;

    private static final long SCHEDULE_INTERVAL_MILLIS = Long.getLong(
            "com.vmware.admiral.compute.epz.schedule.interval.millis",
            TimeUnit.SECONDS.toMillis(60));
    static final String SCHEDULED_TASK_LINK = "elastic-placement-zone-monitor-task";

    /**
     * Monitoring request body.
     */
    public static class MonitorRequest extends ServiceDocument {
    }

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("Body is required in a post operation"));
            return;
        }

        // start by querying the existing elastic placement zones
        logInfo("Starting elastic placement zone monitoring task");
        queryElasticPlacementZones(e -> {
            logInfo("Elastic placement zone monitoring task completed");
            if (e != null) {
                post.fail(e);
            } else {
                post.complete();
            }
        });
    }

    /**
     * Starts the monitoring service by creating a scheduled task. Consider success if already
     * started.
     *
     * The provided completion callback is called after the task was scheduled and its first
     * execution completed.
     *
     * @param host
     *            service host to use for HTTP operations
     * @param referer
     *            referer to use for HTTP operations
     * @param completionHandler
     *            callback to call upon completion
     */
    public static void start(ServiceHost host, URI referer, CompletionHandler completionHandler) {
        ScheduledTaskState scheduledTaskState = new ScheduledTaskState();
        scheduledTaskState.documentSelfLink = SCHEDULED_TASK_LINK;
        scheduledTaskState.factoryLink = ElasticPlacementZoneMonitor.SELF_LINK;
        scheduledTaskState.initialStateJson = Utils.toJson(new MonitorRequest());
        scheduledTaskState.intervalMicros = TimeUnit.MILLISECONDS
                .toMicros(SCHEDULE_INTERVAL_MILLIS);

        Operation scheduledTaskOp = Operation
                .createPost(host, ScheduledTaskService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(scheduledTaskState)
                .setReferer(referer)
                .setCompletion((o, e) -> {
                    if (ServiceAlreadyStartedException.class.isInstance(e)) {
                        host.log(Level.INFO, "Ignoring start request, monitor already started");
                        e = null; // // swallow the exception
                    } else if (CancellationException.class.isInstance(e)) {
                        host.log(Level.INFO, "Couldn't start monitor, " +
                                "possibly because of no elastic placement zones");
                        e = null; // // swallow the exception
                    }
                    if (completionHandler != null) {
                        completionHandler.handle(o, e);
                    }
                });
        host.sendRequest(scheduledTaskOp);
    }

    /**
     * Stops the monitoring service by removing the scheduled task. Consider success if not started.
     *
     * @param host
     *            service host to use for HTTP operations
     * @param referer
     *            referer to use for HTTP operations
     * @param completionHandler
     *            callback to call upon completion
     */
    public static void stop(ServiceHost host, URI referer, CompletionHandler completionHandler) {
        Operation scheduledTaskOp = Operation
                .createDelete(host, UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK,
                        SCHEDULED_TASK_LINK))
                .setReferer(referer)
                .setCompletion((o, e) -> {
                    if (IllegalStateException.class.isInstance(e)) {
                        // swallow the exception
                        host.log(Level.INFO,
                                "Ignoring stop request, monitor not running: " + e.getMessage());
                        e = null;
                    }
                    if (completionHandler != null) {
                        completionHandler.handle(o, e);
                    }
                });
        host.sendRequest(scheduledTaskOp);
    }

    /**
     * Retrieves elastic placement zone definitions.
     */
    private void queryElasticPlacementZones(Consumer<Throwable> completionCallback) {
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
                        completionCallback.accept(new RuntimeException(
                                "Error querying for elastic placement zones", r.getException()));
                    } else if (r.hasResult()) {
                        elasticPlacementZones.add(r.getResult());
                    } else {
                        if (elasticPlacementZones.isEmpty()) {
                            logInfo("No elastic placement zones found, unscheduling monitor");
                            completionCallback.accept(null);

                            // stops the monitor *after* the operation is completed
                            ElasticPlacementZoneMonitor.stop(getHost(), getUri(), null);
                        } else {
                            queryComputesPerZone(elasticPlacementZones, completionCallback);
                        }
                    }
                });
    }

    /**
     * Retrieves computes for each of the given elastic placement zones.
     */
    private void queryComputesPerZone(List<ElasticPlacementZoneState> elasticPlacementZones,
            Consumer<Throwable> completionCallback) {
        List<Operation> queryOperations = new ArrayList<>(elasticPlacementZones.size());
        Map<Long, ElasticPlacementZoneState> epzByOperationId = new HashMap<>(
                elasticPlacementZones.size());
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
                completionCallback.accept(new RuntimeException(
                        "Errors occured when querying computes: " + Utils.toString(exs),
                        exs.values().iterator().next()));
                return;
            }

            // target RP link -> (compute link -> current RP link)
            // compute -> set of target RPs (should be one but could be more in the case of conflicts)
            Map<String, Set<String>> rpCandidatesPerCompute = new HashMap<>();
            Map<String, String> currentRpPerCompute = new HashMap<>();

            for (Operation op : ops.values()) {
                ServiceDocumentQueryResult r = op.getBody(QueryTask.class).results;
                String newRpLink = epzByOperationId.get(op.getId()).resourcePoolLink;
                if (r != null && r.selectedLinksPerDocument != null) {
                    for (Map.Entry<String, Map<String, String>> entry : r.selectedLinksPerDocument
                            .entrySet()) {
                        String computeLink = entry.getKey();
                        Map<String, String> selectedLinks = entry.getValue();
                        String currentRpLink = selectedLinks != null ? selectedLinks
                                .get(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK) : null;

                        Set<String> rpLinks = rpCandidatesPerCompute.get(computeLink);
                        if (rpLinks == null) {
                            rpLinks = new HashSet<>();
                            rpCandidatesPerCompute.put(computeLink, rpLinks);
                        }
                        rpLinks.add(newRpLink);

                        currentRpPerCompute.put(computeLink, currentRpLink);
                    }
                }
            }

            assignResourcePool(rpCandidatesPerCompute, currentRpPerCompute, completionCallback);
        }).sendWith(this);
    }

    /**
     * Changes compute RPs to match EPZ definitions.
     */
    private void assignResourcePool(Map<String, Set<String>> rpCandidatesPerCompute,
            Map<String, String> currentRpPerCompute,
            Consumer<Throwable> completionCallback) {
        // create patch operation for each compute which RP needs to be changed
        List<Operation> patchOperations = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : rpCandidatesPerCompute.entrySet()) {
            String computeLink = entry.getKey();
            Set<String> rpLinks = entry.getValue();

            // assuming misconfiguration if there are more than one candidate RPs for the compute
            // skipping the compute in this case
            if (rpLinks.size() > 1) {
                logWarning("Compute '%s' matches more than one target resource pools: %s",
                        computeLink, rpLinks);
                continue;
            }

            // check whether the target RP is already assigned
            String currentRpLink = currentRpPerCompute.get(computeLink);
            String newRpLink = rpLinks.iterator().next();
            if (newRpLink.equals(currentRpLink)) {
                continue;
            }

            // create a patch operation to change the RP of this compute
            ComputeState patchComputeState = new ComputeState();
            patchComputeState.documentSelfLink = computeLink;
            patchComputeState.resourcePoolLink = newRpLink;
            patchOperations
                    .add(Operation.createPatch(this, computeLink).setBody(patchComputeState));
        }

        if (patchOperations.isEmpty()) {
            logInfo("%d computes checked, no resource pool change required.",
                    rpCandidatesPerCompute.size());
            completionCallback.accept(null);
            return;
        }

        // execute patch operations in parallel
        OperationJoin.create(patchOperations).setCompletion((ops, exs) -> {
            if (exs != null) {
                completionCallback.accept(
                        new RuntimeException("Errors occured when changing compute resource pools: "
                                + Utils.toString(exs), exs.values().iterator().next()));
                return;
            }
            logInfo("%d computes moved to a different resource pool.", patchOperations.size());
            completionCallback.accept(null);
        }).sendWith(this);
    }
}
