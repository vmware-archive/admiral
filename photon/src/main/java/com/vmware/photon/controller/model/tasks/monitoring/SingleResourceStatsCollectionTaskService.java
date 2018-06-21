/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.tasks.monitoring;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeStatsResponse.ComputeStats;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.InMemoryResourceMetricService;
import com.vmware.photon.controller.model.monitoring.InMemoryResourceMetricService.InMemoryResourceMetric;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.tasks.TaskUtils;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.AggregationType;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task service to kick off stats collection at a resource level.
 * The stats adapter associated with this resource can return stats
 * data for a set of resources
 *
 */
public class SingleResourceStatsCollectionTaskService
        extends TaskService<SingleResourceStatsCollectionTaskState> {

    public static final String FACTORY_LINK = UriPaths.MONITORING
            + "/stats-collection-resource-tasks";
    private static final long DEFAULT_EXPIRATION_MINUTES = 60;

    public static final String RESOURCE_METRIC_RETENTION_LIMIT_DAYS = UriPaths.PROPERTY_PREFIX
            + "SingleResourceStatsCollectionTaskService.metric.retentionLimitDays";
    private static final int DEFAULT_RETENTION_LIMIT_DAYS = 56; // 8*7 (8 weeks)

    public static final long EXPIRATION_INTERVAL = Integer
            .getInteger(RESOURCE_METRIC_RETENTION_LIMIT_DAYS, DEFAULT_RETENTION_LIMIT_DAYS);

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(
                SingleResourceStatsCollectionTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new SingleResourceStatsCollectionTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public enum SingleResourceTaskCollectionStage {
        GET_DESCRIPTIONS, UPDATE_STATS
    }

    public static class SingleResourceStatsCollectionTaskState
            extends TaskService.TaskServiceState {
        /**
         * compute resource link
         */
        public String computeLink;

        /**
         * Task state
         */
        public SingleResourceTaskCollectionStage taskStage;

        /**
         * Body to patch back upon task completion
         */
        public Object parentPatchBody;

        /**
         * Task to patch back to
         */
        public URI parentTaskReference;

        /**
         * List of stats; this is maintained as part of the
         * state as the adapter patches this back and we
         * want the patch handler to deserialize it generically.
         * Given that the task is non persistent, the cost
         * is minimal
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public List<ComputeStats> statsList;

        @Documentation(description = "The stats adapter reference")
        public URI statsAdapterReference;

        /**
         * This flag will be used when adapter is sending huge data in multiple batches
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public boolean isFinalBatch = true;

    }

    public SingleResourceStatsCollectionTaskService() {
        super(SingleResourceStatsCollectionTaskState.class);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
    }

    @Override
    protected SingleResourceStatsCollectionTaskState validateStartPost(Operation postOp) {
        SingleResourceStatsCollectionTaskState state = super.validateStartPost(postOp);
        if (state == null) {
            return null;
        }
        if (state.computeLink == null) {
            postOp.fail(new IllegalArgumentException("computeReference needs to be specified"));
            return null;
        }
        if (state.parentTaskReference == null) {
            postOp.fail(new IllegalArgumentException("parentTaskReference needs to be specified"));
            return null;
        }
        if (state.parentPatchBody == null) {
            postOp.fail(new IllegalArgumentException("parentPatchBody needs to be specified"));
            return null;
        }
        return state;
    }

    @Override
    protected void initializeState(SingleResourceStatsCollectionTaskState state,
            Operation postOp) {
        super.initializeState(state, postOp);
        // Override the default expiration of 4 hours to 10 minutes.
        setExpiration(state, DEFAULT_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        state.taskStage = SingleResourceTaskCollectionStage.GET_DESCRIPTIONS;
        state.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
    }

    @Override
    public void handleStart(Operation taskOperation) {
        SingleResourceStatsCollectionTaskState initialState = validateStartPost(taskOperation);
        if (initialState == null) {
            return;
        }

        initializeState(initialState, taskOperation);
        initialState.taskInfo.stage = TaskStage.CREATED;
        taskOperation.setBody(initialState)
                .setStatusCode(Operation.STATUS_CODE_ACCEPTED)
                .complete();

        // self patch to start state machine
        sendSelfPatch(initialState, TaskStage.STARTED, null);
    }

    @Override
    public void handlePatch(Operation patch) {
        SingleResourceStatsCollectionTaskState currentState = getState(patch);
        SingleResourceStatsCollectionTaskState patchState = patch
                .getBody(SingleResourceStatsCollectionTaskState.class);
        updateState(currentState, patchState);
        patch.setBody(currentState);
        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleStagePatch(currentState);
            break;
        case FINISHED:
        case FAILED:
        case CANCELLED:
            // this is a one shot task, self delete
            sendRequest(Operation
                    .createPatch(currentState.parentTaskReference)
                    .setBody(currentState.parentPatchBody)
                    .setCompletion(
                            (patchOp, patchEx) -> {
                                if (patchEx != null) {
                                    logWarning(() -> String.format("Patching parent task failed %s",
                                            Utils.toString(patchEx)));
                                }
                                sendRequest(Operation
                                        .createDelete(getUri()));
                                logFine(() -> "Finished single resource stats collection");
                            }));
            break;
        default:
            break;
        }
    }

    @Override
    public void handlePut(Operation put) {
        PhotonModelUtils.handleIdempotentPut(this, put);
    }

    @Override
    public void updateState(SingleResourceStatsCollectionTaskState currentState,
            SingleResourceStatsCollectionTaskState patchState) {
        if (patchState.taskInfo != null) {
            currentState.taskInfo = patchState.taskInfo;
        }
        if (patchState.taskStage != null) {
            currentState.taskStage = patchState.taskStage;
        }
        if (patchState.statsList != null) {
            currentState.statsList = patchState.statsList;
        }
        if (patchState.statsAdapterReference != null) {
            currentState.statsAdapterReference = patchState.statsAdapterReference;
        }
        currentState.isFinalBatch = patchState.isFinalBatch;
    }

    private void handleStagePatch(SingleResourceStatsCollectionTaskState currentState) {
        switch (currentState.taskStage) {
        case GET_DESCRIPTIONS:
            getDescriptions(currentState);
            break;
        case UPDATE_STATS:
            updateAndPersistStats(currentState);
            break;
        default:
            break;
        }
    }

    private void getDescriptions(SingleResourceStatsCollectionTaskState currentState) {
        URI computeDescUri = ComputeStateWithDescription
                .buildUri(UriUtils.extendUri(ClusterUtil.getClusterUri(getHost(),
                        ServiceTypeCluster.DISCOVERY_SERVICE), currentState.computeLink));
        sendRequest(Operation
                .createGet(computeDescUri)
                .setCompletion(
                        (getOp, getEx) -> {
                            if (getEx != null) {
                                TaskUtils.sendFailurePatch(this, currentState, getEx);
                                return;
                            }

                            ComputeStateWithDescription computeStateWithDesc = getOp
                                    .getBody(ComputeStateWithDescription.class);
                            ComputeStatsRequest statsRequest = new ComputeStatsRequest();
                            URI patchUri = null;
                            Object patchBody = null;

                            ComputeDescription description = computeStateWithDesc.description;
                            URI statsAdapterReference = null;
                            List<String> tenantLinks = new ArrayList<>();
                            if (description != null) {
                                tenantLinks = description.tenantLinks;
                                // Only look in adapter references if statsAdapterReference is
                                // provided
                                if (currentState.statsAdapterReference == null) {
                                    statsAdapterReference = description.statsAdapterReference;
                                } else if (description.statsAdapterReferences != null) {
                                    for (URI uri : description.statsAdapterReferences) {
                                        if (uri.getPath().equals(currentState
                                                .statsAdapterReference.getPath())) {
                                            statsAdapterReference = currentState
                                                    .statsAdapterReference;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (statsAdapterReference != null) {
                                statsRequest.nextStage = SingleResourceTaskCollectionStage.UPDATE_STATS
                                        .name();
                                statsRequest.resourceReference = UriUtils
                                        .extendUri(ClusterUtil.getClusterUri(getHost(),
                                                ServiceTypeCluster.DISCOVERY_SERVICE),
                                                computeStateWithDesc.documentSelfLink);
                                statsRequest.taskReference = getUri();
                                patchUri = statsAdapterReference;
                                populateLastCollectionTimeForMetricsInStatsRequest(currentState,
                                        statsRequest, patchUri, tenantLinks);
                            } else {
                                // no adapter associated with this resource, just patch completion
                                SingleResourceStatsCollectionTaskState nextStageState = new SingleResourceStatsCollectionTaskState();
                                nextStageState.taskInfo = new TaskState();
                                nextStageState.taskInfo.stage = TaskStage.FINISHED;
                                patchUri = getUri();
                                patchBody = nextStageState;
                                sendStatsRequestToAdapter(currentState,
                                        patchUri, patchBody);
                            }

                        }));
    }

    private void updateAndPersistStats(SingleResourceStatsCollectionTaskState currentState) {
        if (currentState.statsAdapterReference == null) {
            throw new IllegalStateException("stats adapter reference should not be null");
        }

        if (currentState.statsList.size() == 0) {
            // If there are no stats reported and if it's a final batch, just finish the task.
            if (currentState.isFinalBatch) {
                SingleResourceStatsCollectionTaskState nextStatePatch = new
                        SingleResourceStatsCollectionTaskState();
                nextStatePatch.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
                TaskUtils.sendPatch(this, nextStatePatch);
            }
            return;
        }

        long expirationTime = Utils.getNowMicrosUtc() + TimeUnit.DAYS.toMicros(EXPIRATION_INTERVAL);
        List<Operation> operations = new ArrayList<>();
        List<ResourceMetrics> metricsList = new ArrayList<>();
        List<InMemoryResourceMetric> inMemoryMetricsList = new ArrayList<>();

        // Push the last collection metric to the in memory stats available at the
        // compute-link/stats URI.
        ServiceStats.ServiceStat minuteStats = new ServiceStats.ServiceStat();
        String statsLink = getAdapterLinkFromURI(currentState.statsAdapterReference);
        minuteStats.name = getLastCollectionMetricKeyForAdapterLink(statsLink, true);
        minuteStats.latestValue = Utils.getNowMicrosUtc();
        minuteStats.sourceTimeMicrosUtc = Utils.getNowMicrosUtc();
        minuteStats.unit = PhotonModelConstants.UNIT_MICROSECONDS;
        URI inMemoryStatsUri = UriUtils.buildStatsUri(UriUtils
                .extendUri(UriUtils.buildUri(ClusterUtil.getClusterUri(getHost(),
                        ServiceTypeCluster.DISCOVERY_SERVICE)), currentState.computeLink));
        operations.add(Operation.createPost(inMemoryStatsUri).setBody(minuteStats));
        populateResourceMetrics(metricsList,
                getLastCollectionMetricKeyForAdapterLink(statsLink, false),
                minuteStats, currentState.computeLink, expirationTime);

        for (ComputeStats stats : currentState.statsList) {
            // TODO: https://jira-hzn.eng.vmware.com/browse/VSYM-330

            String computeId = UriUtils.getLastPathSegment(stats.computeLink);

            InMemoryResourceMetric hourlyMemoryState = new InMemoryResourceMetric();
            hourlyMemoryState.timeSeriesStats = new HashMap<>();
            hourlyMemoryState.documentSelfLink = computeId + StatsConstants.HOUR_SUFFIX;

            inMemoryMetricsList.add(hourlyMemoryState);

            for (Entry<String, List<ServiceStat>> entries : stats.statValues.entrySet()) {
                // sort stats by source time
                entries.getValue().sort(Comparator.comparing(o -> o.sourceTimeMicrosUtc));

                // Persist every data point
                for (ServiceStat serviceStat : entries.getValue()) {
                    String computeLink = stats.computeLink;
                    if (computeLink == null) {
                        computeLink = currentState.computeLink;
                    }
                    // update in-memory stats
                    updateInMemoryStats(hourlyMemoryState, entries.getKey(), serviceStat,
                            StatsConstants.BUCKET_SIZE_HOURS_IN_MILLIS);
                    populateResourceMetrics(metricsList, entries.getKey(),
                            serviceStat, computeLink, expirationTime);
                }
            }
        }
        for (ResourceMetrics metrics : metricsList) {
            operations.add(Operation.createPost(UriUtils.buildUri(
                    ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.METRIC_SERVICE),
                    ResourceMetricsService.FACTORY_LINK)).setBodyNoCloning(metrics));
        }
        for (InMemoryResourceMetric metric : inMemoryMetricsList) {
            operations.add(Operation.createPost(getHost(), InMemoryResourceMetricService.FACTORY_LINK)
                            .setBodyNoCloning(metric));
        }
        // Save each data point sequentially to create time based monotonically increasing sequence.
        batchPersistStats(operations, 0, currentState.isFinalBatch);
    }

    private void batchPersistStats(List<Operation> operations, int batchIndex,
            boolean isFinalBatch) {
        OperationSequence opSequence = null;
        Integer nextBatchIndex = null;
        for (int i = batchIndex; i < operations.size(); i++) {
            final Operation operation = operations.get(i);
            if (opSequence == null) {
                opSequence = OperationSequence.create(operation);
                continue;
            }
            opSequence = opSequence.next(operation);

            // Batch size of 100
            int batchSize = 100;
            int opSequenceSize = i + 1;
            if ((opSequenceSize % batchSize) == 0) {
                nextBatchIndex = opSequenceSize;
                break;
            }
        }

        Integer finalNextBatchIndex = nextBatchIndex;
        opSequence.setCompletion((ops, exc) -> {
            if (exc != null) {
                logWarning(() -> String.format("Failed stats collection: %s",
                        exc.values().iterator().next().getMessage()));
                TaskUtils.sendFailurePatch(this,
                        new SingleResourceStatsCollectionTaskState(), exc.values());
                return;
            }

            if (finalNextBatchIndex == null || finalNextBatchIndex == operations.size()) {
                if (isFinalBatch) {
                    SingleResourceStatsCollectionTaskState nextStatePatch = new
                            SingleResourceStatsCollectionTaskState();
                    nextStatePatch.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
                    TaskUtils.sendPatch(this, nextStatePatch);
                }
                return;
            }

            batchPersistStats(operations, finalNextBatchIndex, isFinalBatch);
        });
        opSequence.sendWith(this);
    }

    private void updateInMemoryStats(InMemoryResourceMetric inMemoryMetric, String metricKey,
            ServiceStat serviceStat, int bucketSize) {
        // update in-memory stats
        if (inMemoryMetric.timeSeriesStats.containsKey(metricKey)) {
            inMemoryMetric.timeSeriesStats.get(metricKey)
                    .add(serviceStat.sourceTimeMicrosUtc, serviceStat.latestValue,
                            serviceStat.latestValue);
        } else {
            TimeSeriesStats tStats = new TimeSeriesStats(2, bucketSize,
                    EnumSet.allOf(AggregationType.class));
            tStats.add(serviceStat.sourceTimeMicrosUtc, serviceStat.latestValue,
                    serviceStat.latestValue);
            inMemoryMetric.timeSeriesStats.put(metricKey, tStats);
        }
    }

    private void populateResourceMetrics(List<ResourceMetrics> metricsList,
            String metricName,
            ServiceStat serviceStat,
            String computeLink, long expirationTime) {
        if (Double.isNaN(serviceStat.latestValue)) {
            return;
        }
        ResourceMetrics metricsObjToUpdate = null;
        for (ResourceMetrics metricsObj : metricsList) {
            if (metricsObj.documentSelfLink.startsWith(UriUtils.getLastPathSegment(computeLink)) &&
                    metricsObj.timestampMicrosUtc.equals(serviceStat.sourceTimeMicrosUtc)) {
                metricsObjToUpdate = metricsObj;
                break;
            }
        }
        if (metricsObjToUpdate == null) {
            metricsObjToUpdate = new ResourceMetrics();
            metricsObjToUpdate.documentSelfLink = StatsUtil.getMetricKey(computeLink, Utils.getNowMicrosUtc());
            metricsObjToUpdate.entries = new HashMap<>();
            metricsObjToUpdate.timestampMicrosUtc = serviceStat.sourceTimeMicrosUtc;
            metricsObjToUpdate.documentExpirationTimeMicros = expirationTime;
            metricsObjToUpdate.customProperties = new HashMap<>();
            metricsObjToUpdate.customProperties
                    .put(ResourceMetrics.PROPERTY_RESOURCE_LINK, computeLink);
            metricsList.add(metricsObjToUpdate);
        }
        metricsObjToUpdate.entries.put(metricName, serviceStat.latestValue);
    }

    /**
     * Gets the last collection for a compute for a given adapter URI.
     * As a first step, the in memory stats for the compute are queried and if the metric
     * for the last collection time is found, then the timestamp for that is returned.
     *
     * Else, the ResoureMetric table is queried and the latest version of the metric is used
     * to determine the last collection time for the stats.
     */
    private void populateLastCollectionTimeForMetricsInStatsRequest(
            SingleResourceStatsCollectionTaskState currentState,
            ComputeStatsRequest computeStatsRequest, URI patchUri, List<String> tenantLinks) {
        URI computeStatsUri = UriUtils
                .buildStatsUri(UriUtils.extendUri(ClusterUtil.getClusterUri(getHost(),
                        ServiceTypeCluster.DISCOVERY_SERVICE), currentState.computeLink));
        Operation.createGet(computeStatsUri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(() -> String.format("Could not get the last collection time from"
                                        + " in memory stats: %s", Utils.toString(e)));
                        // get the value from the persisted store.
                        populateLastCollectionTimeFromPersistenceStore(currentState,
                                computeStatsRequest, patchUri, tenantLinks);
                        return;
                    }
                    ServiceStats serviceStats = o.getBody(ServiceStats.class);
                    String statsAdapterLink = getAdapterLinkFromURI(patchUri);
                    String lastSuccessfulRunMetricKey = getLastCollectionMetricKeyForAdapterLink(
                            statsAdapterLink, true);
                    if (serviceStats.entries.containsKey(lastSuccessfulRunMetricKey)) {
                        ServiceStat lastRunStat = serviceStats.entries
                                .get(lastSuccessfulRunMetricKey);
                        computeStatsRequest.lastCollectionTimeMicrosUtc =
                                lastRunStat.sourceTimeMicrosUtc;
                        sendStatsRequestToAdapter(currentState, patchUri, computeStatsRequest);
                    } else {
                        populateLastCollectionTimeFromPersistenceStore(currentState,
                                computeStatsRequest, patchUri, tenantLinks);
                    }
                })
                .sendWith(this);
    }

    /**
     * Queries the metric for the last successful run and sets that value in the compute stats request.
     * This value is used to determine the window size for which the stats collection happens from the provider.
     */
    private void populateLastCollectionTimeFromPersistenceStore(
            SingleResourceStatsCollectionTaskState currentState,
            ComputeStatsRequest computeStatsRequest, URI patchUri, List<String> tenantLinks) {
        String statsAdapterLink = getAdapterLinkFromURI(patchUri);
        String lastSuccessfulRunMetricKey = getLastCollectionMetricKeyForAdapterLink(
                statsAdapterLink, false);
        Query.Builder builder = Query.Builder.create();
        builder.addKindFieldClause(ResourceMetrics.class);
        builder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK, UriUtils.getLastPathSegment(currentState.computeLink)),
                MatchType.PREFIX);
        builder.addRangeClause( QuerySpecification.buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES, lastSuccessfulRunMetricKey),
                NumericRange.createDoubleRange(Double.MIN_VALUE, Double.MAX_VALUE, true, true));
        QueryTask task = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.SORT)
                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK, TypeName.STRING)
                .addOption(QueryOption.TOP_RESULTS)
                // No-op in photon-model. Required for special handling of immutable documents.
                // This will prevent Lucene from holding the full result set in memory.
                .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                .setResultLimit(1)
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(builder.build()).build();
        task.tenantLinks = tenantLinks;
        QueryUtils.startQueryTask(this, task, ServiceTypeCluster.METRIC_SERVICE)
                .whenComplete((responseTask, e) -> {
                    if (e != null) {
                        logSevere(
                                "Could not get the last collection time from persisted metrics: %s",
                                Utils.toString(e));
                        // Still continue calling into the adapter if the last known time for
                        // successful collection is not known
                        sendStatsRequestToAdapter(currentState,
                                patchUri, computeStatsRequest);
                        return;
                    }
                    // If the persisted metric can be found, use the value of the last successful
                    // collection time otherwise do not set any value for the last collection time
                    // while sending the request to the adapter.
                    if (responseTask.results.documentCount > 0) {
                        Object rawMetricObj = responseTask.results.documents
                                .get(responseTask.results.documentLinks.get(0));
                        ResourceMetrics rawMetrics = Utils.fromJson(rawMetricObj,
                                ResourceMetrics.class);
                        computeStatsRequest.lastCollectionTimeMicrosUtc =
                                rawMetrics.timestampMicrosUtc;
                    }
                    sendStatsRequestToAdapter(currentState, patchUri, computeStatsRequest);
                });
    }

    /**
     * Sends the Stats request to the Stats adapter
     */
    private void sendStatsRequestToAdapter(SingleResourceStatsCollectionTaskState currentState,
            URI patchUri, Object patchBody) {
        sendRequest(Operation.createPatch(patchUri)
                .setBody(patchBody)
                .setCompletion((patchOp, patchEx) -> {
                    if (patchEx != null) {
                        TaskUtils.sendFailurePatch(this, currentState, patchEx);
                    }
                }));
    }

    /**
     * Forms the key to be used for looking up the last collection time for a given stats adapter.
     */
    public String getLastCollectionMetricKeyForAdapterLink(String statsAdapterLink,
            boolean appendBucketSuffix) {
        String lastSuccessfulRunMetricKey = UriUtils.getLastPathSegment(statsAdapterLink) + StatsUtil.SEPARATOR
                + PhotonModelConstants.LAST_SUCCESSFUL_STATS_COLLECTION_TIME;
        if (appendBucketSuffix) {
            lastSuccessfulRunMetricKey = lastSuccessfulRunMetricKey + StatsConstants.MIN_SUFFIX;
        }
        return lastSuccessfulRunMetricKey;
    }

    /**
     * Returns the path from the patchUri.
     */
    private String getAdapterLinkFromURI(URI patchUri) {
        return patchUri.getPath();
    }
}
