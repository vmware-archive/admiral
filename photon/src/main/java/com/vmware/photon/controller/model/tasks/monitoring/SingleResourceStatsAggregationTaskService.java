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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.monitoring.InMemoryResourceMetricService;
import com.vmware.photon.controller.model.monitoring.InMemoryResourceMetricService.InMemoryResourceMetric;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.tasks.TaskUtils;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription.TypeName;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.AggregationType;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.TimeBin;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Builder;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task to aggregate resource stats for a single resource. Aggregate stats are backed by ResourceMetricsService
 * instances. Aggregate metrics are identified using a key that is a combination of the resourceId, metricKey and a timestamp.
 * Queries for aggregate metrics need to issue a prefix query on resourceId and metric Key to obtain all documents and filter by date.
 *
 * Aggregation operations can run multiple time within a time window. This will result in multiple documents for the time window.
 * The first document, sorted by documentSelfLink in DESC order, represents the aggregate value after the time interval has closed. All
 * other documents represent point in time aggregates for the collection interval.
 *
 * All aggregate metrics have a timestamp that represents the end of the interval. For example if the aggregation is for hourly
 * data and the interval is 10-11, the aggregate value will have a timestamp of 11
 *
 * Aggregations are based on the the query that is specified. All resources that resolve to the query will be used for aggregation.
 * If no query is specified the aggregation will happen on the resource specified in resourceLink
 */
public class SingleResourceStatsAggregationTaskService extends
        TaskService<SingleResourceStatsAggregationTaskService.SingleResourceStatsAggregationTaskState> {

    public static final String FACTORY_LINK = UriPaths.MONITORING
            + "/single-resource-stats-aggregation";

    private static final long DEFAULT_EXPIRATION_MINUTES = 10;

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(
                SingleResourceStatsAggregationTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new SingleResourceStatsAggregationTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public static final String STATS_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX
            + "SingleResourceStatsAggregationTaskService.query.resultLimit";
    private static final int DEFAULT_QUERY_RESULT_LIMIT = 25;

    public static final String RESOURCE_METRIC_RETENTION_LIMIT_DAYS = UriPaths.PROPERTY_PREFIX
            + "SingleResourceStatsAggregationTaskService.metric.retentionLimitDays";
    private static final int DEFAULT_RETENTION_LIMIT_DAYS = 56; // 8*7 (8 weeks)

    private static final long EXPIRATION_INTERVAL = Integer
            .getInteger(RESOURCE_METRIC_RETENTION_LIMIT_DAYS, DEFAULT_RETENTION_LIMIT_DAYS);

    public static final String RAW_METRICS_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX
            + "SingleResourceStatsAggregationTaskService.query.rawMetrics.resultLimit";
    private static final int DEFAULT_RAW_METRICS_RESULT_LIMIT = 10000;
    private static final int RAW_METRICS_LIMIT = Integer
            .getInteger(RAW_METRICS_RESULT_LIMIT, DEFAULT_RAW_METRICS_RESULT_LIMIT);

    public static class SingleResourceStatsAggregationTaskState
            extends TaskService.TaskServiceState {

        @Documentation(description = "Resource to invoke stats aggregation on")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String resourceLink;

        @Documentation(description = "The set of metric names to aggregate on")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public Set<String> metricNames;

        @Documentation(description = "The query to lookup resources for stats aggregation."
                + " If no query is specified, the aggregation is performed on the resource"
                + " identified by the resourceLink parameter")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Query query;

        @Documentation(description = "Metrics to be aggregated on latest value only")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> latestValueOnly;

        @Documentation(description = "Aggregation type per metric name")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Map<String, Set<AggregationType>> aggregations;

        @Documentation(description = "Task to patch back to")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI parentTaskReference;

        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public StatsAggregationStage taskStage;

        // the latest time the metric was rolled up
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Map<String, Long> lastRollupTimeForMetric;

        // aggregated metrics by timestamp and metric key
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Map<String, Map<Long, TimeBin>> aggregatedTimeBinMap;

        //cursor for obtaining compute services - this is set for the first time based on
        //the result of a query task and updated on every patch thereafter based on the result
        //object obtained when a GET is issued on the link
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String queryResultLink;

        // specifies if there are any resources available for aggregation to take place
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        @UsageOption(option = ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public boolean hasResources = true;
    }

    public enum StatsAggregationStage {
        GET_LAST_ROLLUP_TIME, INIT_RESOURCE_QUERY, PROCESS_RESOURCES, PUBLISH_METRICS
    }

    public SingleResourceStatsAggregationTaskService() {
        super(SingleResourceStatsAggregationTaskState.class);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
    }

    @Override
    protected SingleResourceStatsAggregationTaskState validateStartPost(Operation postOp) {
        SingleResourceStatsAggregationTaskState state = super.validateStartPost(postOp);
        if (state == null) {
            return null;
        }
        if (state.resourceLink == null) {
            postOp.fail(new IllegalArgumentException("resourceLink needs to be specified"));
            return null;
        }
        if (state.metricNames == null || state.metricNames.isEmpty()) {
            postOp.fail(new IllegalArgumentException("metricNames needs to be specified"));
            return null;
        }
        return state;
    }

    @Override
    protected void initializeState(SingleResourceStatsAggregationTaskState state,
            Operation postOp) {
        super.initializeState(state, postOp);
        // Override the default expiration of 4 hours to 10 minutes.
        setExpiration(state, DEFAULT_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        state.taskStage = StatsAggregationStage.GET_LAST_ROLLUP_TIME;
        state.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);

        if (state.query == null) {
            state.query = Query.Builder.create().addFieldClause(
                    ServiceDocument.FIELD_NAME_SELF_LINK, state.resourceLink).build();
        }

        if (state.aggregations == null) {
            state.aggregations = Collections.emptyMap();
        }

        if (state.latestValueOnly == null) {
            state.latestValueOnly = Collections.emptySet();
        }
    }

    @Override
    public void handleStart(Operation taskOperation) {
        SingleResourceStatsAggregationTaskState initialState = validateStartPost(taskOperation);
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
        SingleResourceStatsAggregationTaskState currentState = getState(patch);
        SingleResourceStatsAggregationTaskState patchState = getBody(patch);
        validateTransition(patch, currentState, patchState);
        updateState(currentState, patchState);
        patch.setBody(currentState);
        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            logFine(() -> String.format("Starting single resource stats aggregation for [%s] ",
                    currentState.resourceLink));
            handleStagePatch(currentState);
            break;
        case FINISHED:
        case FAILED:
        case CANCELLED:
            if (TaskState.isFailed(currentState.taskInfo) ||
                    TaskState.isCancelled(currentState.taskInfo)) {
                if (currentState.failureMessage != null) {
                    logWarning(() -> currentState.failureMessage);
                }
            }
            if (currentState.parentTaskReference != null) {
                sendRequest(Operation
                        .createPatch(currentState.parentTaskReference)
                        .setBody(currentState)
                        .setCompletion(
                                (patchOp, patchEx) -> {
                                    if (patchEx != null) {
                                        logWarning(() -> String.format("Patching parent task failed"
                                                + " %s", Utils.toString(patchEx)));
                                    }
                                    sendRequest(Operation.createDelete(getUri()));
                                }));
            } else {
                sendRequest(Operation.createDelete(getUri()));
            }
            logFine(() -> String.format("Single resource stats aggregation in [%s] stage",
                    currentState.taskInfo.stage));
            break;
        default:
            break;
        }
    }

    @Override
    public void handlePut(Operation put) {
        PhotonModelUtils.handleIdempotentPut(this, put);
    }

    private void handleStagePatch(SingleResourceStatsAggregationTaskState currentState) {
        switch (currentState.taskStage) {
        case GET_LAST_ROLLUP_TIME:
            getLastRollupTime(currentState);
            break;
        case INIT_RESOURCE_QUERY:
            initializeQuery(currentState);
            break;
        case PROCESS_RESOURCES:
            getResources(currentState);
            break;
        case PUBLISH_METRICS:
            publishMetrics(currentState);
            break;
        default:
            break;
        }
    }

    private void getLastRollupTime(SingleResourceStatsAggregationTaskState currentState) {
        Map<String, Long> lastUpdateMap = new HashMap<>();
        for (String metricName : currentState.metricNames) {
            List<String> rollupKeys = buildRollupKeys(metricName);
            for (String rollupKey : rollupKeys) {
                lastUpdateMap.put(rollupKey, null);
            }
        }

        // Lookup last rollup time from in memory stats - /<resource-link>/stats
        URI statsUri = UriUtils.buildStatsUri(
                UriUtils.extendUri(ClusterUtil.getClusterUri(getHost(),
                        ServiceTypeCluster.DISCOVERY_SERVICE), currentState.resourceLink));

        sendRequest(Operation.createGet(statsUri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Could not get stats for resource: %s,"
                                + " error: %s", currentState.resourceLink, e.getMessage()));
                        // get the value based on a query.
                        getLastRollupTimeFromQuery(currentState, lastUpdateMap);
                        return;
                    }
                    ServiceStats serviceStats = o.getBody(ServiceStats.class);

                    lastUpdateMap.keySet().stream()
                            .filter(rollupKey -> serviceStats.entries.containsKey(rollupKey))
                            .forEach(rollupKey -> lastUpdateMap.put(rollupKey,
                                    (long) serviceStats.entries.get(rollupKey).latestValue));

                    getLastRollupTimeFromQuery(currentState, lastUpdateMap);
                }));
    }

    private void getLastRollupTimeFromQuery(SingleResourceStatsAggregationTaskState currentState,
            Map<String, Long> lastUpdateMap) {
        List<Operation> operations = new ArrayList<>();
        for (String metricName : currentState.metricNames) {
            List<String> rollupKeys = buildRollupKeys(metricName);
            for (String rollupKey : rollupKeys) {
                // if the last update time was computed based on the memory stat, move on
                if (lastUpdateMap.get(rollupKey) != null) {
                    continue;
                }
                String resourceId = UriUtils.getLastPathSegment(currentState.resourceLink);
                String metricSelfLink = UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK,
                        resourceId);

                Query.Builder builder = Query.Builder.create();
                builder.addKindFieldClause(ResourceMetrics.class);
                builder.addFieldClause(ResourceMetrics.FIELD_NAME_SELF_LINK, metricSelfLink,
                        MatchType.PREFIX);
                builder.addRangeClause(QuerySpecification
                        .buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES, rollupKey),
                        NumericRange.createDoubleRange(0.0, Double.MAX_VALUE, true, true));

                Operation op = Operation.createPost(UriUtils.buildUri(
                        ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.METRIC_SERVICE),
                        ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                        .setBody(Builder.createDirectTask()
                                .addOption(QueryOption.SORT)
                                .addOption(QueryOption.TOP_RESULTS)
                                // No-op in photon-model. Required for special handling of immutable documents.
                                // This will prevent Lucene from holding the full result set in memory.
                                .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                                .addOption(QueryOption.EXPAND_CONTENT)
                                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK,
                                        TypeName.STRING)
                                .setResultLimit(1)
                                .setQuery(builder.build()).build())
                        .setConnectionSharing(true);
                logInfo(() -> String.format("Invoking a query to obtain last rollup time for %s ", currentState.resourceLink));
                operations.add(op);
            }
        }

        // TODO VSYM-3148: Need to optimize this. Right now using OperationSequence so we don't
        // flood the system with lot of queries.
        if (operations.size() == 0) {
            SingleResourceStatsAggregationTaskState patchBody = new SingleResourceStatsAggregationTaskState();
            patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
            // setting hasResources for purpose of test cases
            patchBody.hasResources = currentState.hasResources;
            patchBody.taskStage = StatsAggregationStage.INIT_RESOURCE_QUERY;
            patchBody.lastRollupTimeForMetric = lastUpdateMap;
            sendSelfPatch(patchBody);
            return;
        }

        OperationSequence opSequence = null;
        for (Operation operation : operations) {
            if (opSequence == null) {
                opSequence = OperationSequence.create(operation);
                continue;
            }
            opSequence = opSequence.next(operation);
        }

        opSequence.setCompletion((ops, failures) -> {
            if (failures != null) {
                sendSelfFailurePatch(currentState,
                        failures.values().iterator().next().getMessage());
                return;
            }
            for (Operation operation : ops.values()) {
                QueryTask response = operation.getBody(QueryTask.class);
                for (Object obj : response.results.documents.values()) {
                    ResourceMetrics resourceMetrics = Utils
                            .fromJson(obj, ResourceMetrics.class);
                    for (String metricName : resourceMetrics.entries.keySet()) {
                        lastUpdateMap.replace(metricName, resourceMetrics.timestampMicrosUtc);
                    }
                }
            }
            SingleResourceStatsAggregationTaskState patchBody = new SingleResourceStatsAggregationTaskState();
            patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
            patchBody.hasResources = currentState.hasResources;
            patchBody.taskStage = StatsAggregationStage.INIT_RESOURCE_QUERY;
            patchBody.lastRollupTimeForMetric = lastUpdateMap;
            sendSelfPatch(patchBody);
        });
        opSequence.sendWith(this);
    }

    /**
     * Initialize query from the task state.
     */
    private void initializeQuery(SingleResourceStatsAggregationTaskState currentState) {
        int resultLimit = Integer.getInteger(STATS_QUERY_RESULT_LIMIT, DEFAULT_QUERY_RESULT_LIMIT);
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(currentState.query)
                .setResultLimit(resultLimit)
                .build();
        QueryUtils.startQueryTask(this, queryTask, ServiceTypeCluster.DISCOVERY_SERVICE)
                .whenComplete((resultTask, queryEx) -> {
                    if (queryEx != null) {
                        sendSelfFailurePatch(currentState, queryEx.getMessage());
                        return;
                    }

                    SingleResourceStatsAggregationTaskState patchBody = new SingleResourceStatsAggregationTaskState();
                    if (resultTask.results.nextPageLink == null) {
                        patchBody.hasResources = false;
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                        patchBody.taskStage = StatsAggregationStage.PUBLISH_METRICS;
                    } else {
                        patchBody.hasResources = currentState.hasResources;
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                        patchBody.taskStage = StatsAggregationStage.PROCESS_RESOURCES;
                        patchBody.queryResultLink = resultTask.results.nextPageLink;
                    }
                    sendSelfPatch(patchBody);
                });
    }

    /**
     * Gets resources for the given query, fetch raw metrics and compute partial aggregations
     * as one step
     */
    private void getResources(SingleResourceStatsAggregationTaskState currentState) {
        sendRequest(Operation
                .createGet(UriUtils.extendUri(ClusterUtil.getClusterUri(getHost(),
                        ServiceTypeCluster.DISCOVERY_SERVICE), currentState.queryResultLink))
                .setCompletion(
                        (getOp, getEx) -> {
                            if (getEx != null) {
                                sendSelfFailurePatch(currentState, getEx.getMessage());
                                return;
                            }
                            QueryTask queryTask = getOp.getBody(QueryTask.class);
                            if (queryTask.results.documentCount == 0) {
                                // set to false when no resources are found.
                                currentState.hasResources = false;
                                currentState.taskStage = StatsAggregationStage.PUBLISH_METRICS;
                                sendSelfPatch(currentState);
                                return;
                            }
                            getInMemoryMetrics(currentState, queryTask);
                        }));
    }

    /**
     * Get in-memory metrics.
     */
    private void getInMemoryMetrics(SingleResourceStatsAggregationTaskState currentState,
            QueryTask resourceQueryTask) {
        // Lookup in-memory metrics: /<resource-id><bucket> - /compute123(Hourly)
        List<Operation> operations = new ArrayList<>();
        for (String resourceLink : resourceQueryTask.results.documentLinks) {
            String resourceId = UriUtils.getLastPathSegment(resourceLink);
            List<String> rollupKeys = buildRollupKeys(resourceId);
            for (String rollupKey : rollupKeys) {
                String link = UriUtils
                        .buildUriPath(InMemoryResourceMetricService.FACTORY_LINK, rollupKey);
                operations.add(Operation.createGet(this, link));
            }
        }

        OperationJoin.create(operations.stream())
                .setCompletion((ops, exs) -> {
                    // List of metrics we didn't find in memory and need to be queried from disk.
                    Map<String, Set<String>> metricsToBeQueried = new HashMap<>();

                    /*Nested map to store in memory stats
                    {
                        ...
                        "CPUUtilization(Hourly)" : {
                            "1478040373000" : [
                                "TimeBin1",
                                "TimeBin2"
                            ],
                            "1478040505000" : [
                                "TimeBin3",
                                "TimeBin4"
                            ]
                        }
                        ...
                    }*/
                    Map<String, SortedMap<Long, List<TimeBin>>> inMemoryStats = new HashMap<>();

                    for (Operation operation : ops.values()) {
                        if (operation.getStatusCode() != Operation.STATUS_CODE_OK) {
                            URI uri = operation.getUri();
                            Throwable ex = exs.get(operation.getId());
                            String msg = (ex == null) ? String.valueOf(operation.getStatusCode())
                                    : ex.getMessage();
                            logFine(() -> String.format("In-memory metric lookup failed: %s with"
                                    + " error %s", uri, msg));
                            processFailedOperations(currentState, metricsToBeQueried, uri);
                            continue;
                        }

                        InMemoryResourceMetric metric = operation
                                .getBody(InMemoryResourceMetric.class);

                        processInMemoryMetrics(currentState, metricsToBeQueried, inMemoryStats,
                                metric);
                    }
                    getRawMetrics(currentState, resourceQueryTask, metricsToBeQueried,
                            inMemoryStats);
                })
                .sendWith(this);
    }

    /**
     * Process in-memory metrics.
     */
    private void processInMemoryMetrics(SingleResourceStatsAggregationTaskState currentState,
            Map<String, Set<String>> metricsToBeQueried,
            Map<String, SortedMap<Long, List<TimeBin>>> inMemoryStats,
            InMemoryResourceMetric metric) {
        String metricKey = UriUtils.getLastPathSegment(metric.documentSelfLink);
        String resourceId = stripRollupKey(metricKey);

        for (Entry<String, Long> metricEntry : currentState.lastRollupTimeForMetric
                .entrySet()) {
            String rawMetricKey = stripRollupKey(metricEntry.getKey());

            TimeSeriesStats timeSeriesStats = metric.timeSeriesStats.get(rawMetricKey);

            // if there is no in-memory metric for this key then we don't have any
            // raw metrics; move on to the next metric
            if (timeSeriesStats == null) {
                continue;
            }

            // TODO VSYM-3190 - Change normalized interval boundary to beginning of the rollup period
            // Currently, xenon's time interval boundary 1 hour before than photon
            // model's aggregate metrics.
            Long earliestBinId = TimeUnit.MILLISECONDS.toMicros(timeSeriesStats.bins.firstKey());
            earliestBinId += TimeUnit.MILLISECONDS.toMicros(timeSeriesStats.binDurationMillis);

            Long lastRollupTime = metricEntry.getValue();

            // Check if we have any last rollup time or if rollup time is older than what we
            // have in memory.
            if (lastRollupTime == null || lastRollupTime < earliestBinId) {
                Set<String> metricList = metricsToBeQueried.get(resourceId);
                if (metricList == null) {
                    metricList = new HashSet<>();
                    metricsToBeQueried.put(resourceId, metricList);
                }
                metricList.add(rawMetricKey);
                continue;
            }
            processInMemoryTimeBins(currentState, inMemoryStats, metricEntry, timeSeriesStats);
        }
    }

    /**
     * Process in-memory time bins. This method buckets bins from time series stats appropriately
     * in the inMemoryStats data structure.
     */
    private void processInMemoryTimeBins(SingleResourceStatsAggregationTaskState currentState,
            Map<String, SortedMap<Long, List<TimeBin>>> inMemoryStats,
            Entry<String, Long> metricEntry,
            TimeSeriesStats timeSeriesStats) {
        String metricKeyWithRollUp = metricEntry.getKey();
        String rawMetricKey = stripRollupKey(metricEntry.getKey());
        Long lastRollupTime = metricEntry.getValue();

        for (Entry<Long, TimeBin> binEntry : timeSeriesStats.bins.entrySet()) {
            // TODO VSYM-3190 - Change normalized interval boundary to beginning of the rollup period
            // Currently, xenon's time interval boundary 1 hour before than
            // photon model's aggregate metrics.
            Long binId = TimeUnit.MILLISECONDS.toMicros(binEntry.getKey());
            binId += TimeUnit.MILLISECONDS.toMicros(timeSeriesStats.binDurationMillis);

            if (binId < lastRollupTime) {
                continue;
            }

            SortedMap<Long, List<TimeBin>> bins = inMemoryStats.get(metricKeyWithRollUp);

            if (bins == null) {
                bins = new TreeMap<>();
            }

            List<TimeBin> binList = bins.get(binId);
            if (binList == null) {
                binList = new ArrayList<>();
                bins.put(binId, binList);
                inMemoryStats.put(metricKeyWithRollUp, bins);
            }

            TimeBin bin = binEntry.getValue();
            if (currentState.latestValueOnly.contains(rawMetricKey)) {
                // For latest value, we create a new time bin since we are only interested
                // in the latest data point.
                TimeBin latest = new TimeBin();
                latest.avg = latest.min = latest.max = latest.sum = bin.latest;
                latest.count = 1;
                bins.get(binId).add(latest);
            } else {
                bins.get(binId).add(bin);
            }

        }
    }

    /**
     * Process failed operation uris and adds to the list of metrics to be queried.
     */
    private void processFailedOperations(SingleResourceStatsAggregationTaskState currentState,
            Map<String, Set<String>> metricsToBeQueried, URI uri) {
        String metricKey = UriUtils.getLastPathSegment(uri);
        String resourceId = stripRollupKey(metricKey);
        Set<String> metricKeys = new HashSet<>();
        for (Entry<String, Long> metricEntry : currentState.lastRollupTimeForMetric.entrySet()) {
            metricKeys.add(stripRollupKey(metricEntry.getKey()));
        }
        metricsToBeQueried.put(resourceId, metricKeys);
    }

    /**
     *  Class that holds the rollup metric keys of interest and their last rollup time.
     */
    private static class RollupMetricHolder {
        String rollupKey;
        Long beginTimestampMicros;
    }

    private void getRawMetrics(SingleResourceStatsAggregationTaskState currentState,
            QueryTask resourceQueryTask, Map<String, Set<String>> metricsToBeQueried,
            Map<String, SortedMap<Long, List<TimeBin>>> inMemoryStats) {
        if (metricsToBeQueried == null || metricsToBeQueried.isEmpty()) {
            aggregateMetrics(currentState, resourceQueryTask, null, inMemoryStats);
            return;
        }

        Query.Builder overallQueryBuilder = Query.Builder.create();
        for (Entry<String, Set<String>> entry : metricsToBeQueried.entrySet()) {
            String resourceId = entry.getKey();
            for (String metricKey : entry.getValue()) {
                logFine(() -> String.format("Querying raw metrics from disk for %s", metricKey));
                Long range = null;
                int binSize = 0;
                for (Entry<String, Long> metricEntry : currentState.lastRollupTimeForMetric
                        .entrySet()) {
                    if (metricEntry.getKey().startsWith(metricKey)) {
                        if (range == null || range > metricEntry.getValue()) {
                            binSize = lookupBinSize(metricEntry.getKey());
                            range = metricEntry.getValue();
                        }
                    }
                }
                Query.Builder builder = Query.Builder.create(Occurance.SHOULD_OCCUR);
                builder.addKindFieldClause(ResourceMetrics.class);
                builder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                        UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK, resourceId),
                        MatchType.PREFIX);
                builder.addRangeClause(QuerySpecification
                        .buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES, metricKey),
                        NumericRange.createDoubleRange(0.0, Double.MAX_VALUE, true, true));
                if (range != null && range != 0) {
                    builder.addRangeClause(ResourceMetrics.FIELD_NAME_TIMESTAMP,
                            NumericRange.createGreaterThanOrEqualRange(
                                    StatsUtil.computeIntervalBeginMicros(range - 1, binSize)));
                }
                overallQueryBuilder.addClause(builder.build());
            }
        }

        // create a set of rollup metric keys we are interested in and the timestamp
        // to rollup from for each
        Set<RollupMetricHolder> rollupMetricHolder = new HashSet<>();
        for (Entry<String, Long> metricEntry : currentState.lastRollupTimeForMetric
                .entrySet()) {
            RollupMetricHolder metric = new RollupMetricHolder();
            metric.rollupKey = metricEntry.getKey();
            if (metricEntry.getValue() != null && metricEntry.getValue() != 0) {
                metric.beginTimestampMicros = StatsUtil.computeIntervalBeginMicros(
                        metricEntry.getValue() - 1,
                        lookupBinSize(metricEntry.getKey()));
            }
            rollupMetricHolder.add(metric);
        }

        QueryTask task = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .addOption(QueryOption.SORT)
                .orderDescending(ResourceMetrics.FIELD_NAME_TIMESTAMP, TypeName.LONG)
                .setResultLimit(RAW_METRICS_LIMIT)
                .setQuery(overallQueryBuilder.build()).build();

        task.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + QueryUtils.MINUTE_IN_MICROS;

        QueryUtils.startQueryTask(this, task, ServiceTypeCluster.METRIC_SERVICE)
                .whenComplete((response, queryEx) -> {
                    if (queryEx != null) {
                        sendSelfFailurePatch(currentState, queryEx.getMessage());
                        return;
                    }
                    Map<String, List<ResourceMetrics>> rawMetricsForKey = new HashMap<>();
                    for (Object obj : response.results.documents.values()) {
                        ResourceMetrics rawMetric = Utils.fromJson(obj, ResourceMetrics.class);
                        for (RollupMetricHolder metric : rollupMetricHolder) {
                            for (String rawMetricKey : rawMetric.entries.keySet()) {
                                if (!rawMetricKey.contains(stripRollupKey(metric.rollupKey))) {
                                    continue;
                                }
                                // we want to consider raw metrics with the specified key and the appropriate timestamp
                                if ((metric.beginTimestampMicros == null ||
                                        rawMetric.timestampMicrosUtc >= metric.beginTimestampMicros)) {
                                    List<ResourceMetrics> rawMetricResultSet = rawMetricsForKey
                                            .get(metric.rollupKey);
                                    if (rawMetricResultSet == null) {
                                        rawMetricResultSet = new ArrayList<>();
                                        rawMetricsForKey.put(metric.rollupKey, rawMetricResultSet);
                                    }
                                    rawMetricResultSet.add(rawMetric);
                                }
                            }
                        }
                    }
                    aggregateMetrics(currentState, resourceQueryTask, rawMetricsForKey,
                            inMemoryStats);
                });
    }

    private void aggregateMetrics(SingleResourceStatsAggregationTaskState currentState,
            QueryTask resourceQueryTask, Map<String, List<ResourceMetrics>> rawMetricsForKey,
            Map<String, SortedMap<Long, List<TimeBin>>> inMemoryStats) {

        if (inMemoryStats != null) {
            aggregateInMemoryMetrics(currentState, inMemoryStats);
        }

        if (rawMetricsForKey != null) {
            aggregateRawMetrics(currentState, rawMetricsForKey);
        }

        SingleResourceStatsAggregationTaskState patchBody = new SingleResourceStatsAggregationTaskState();
        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
        patchBody.aggregatedTimeBinMap = currentState.aggregatedTimeBinMap;
        patchBody.hasResources = currentState.hasResources;
        if (resourceQueryTask.results.nextPageLink == null) {
            patchBody.taskStage = StatsAggregationStage.PUBLISH_METRICS;
        } else {
            patchBody.taskStage = StatsAggregationStage.PROCESS_RESOURCES;
            patchBody.queryResultLink = resourceQueryTask.results.nextPageLink;
        }
        sendSelfPatch(patchBody);
    }

    private void aggregateRawMetrics(
            SingleResourceStatsAggregationTaskState currentState,
            Map<String, List<ResourceMetrics>> rawMetricsForKey) {
        Map<String, Map<Long, TimeBin>> aggregatedTimeBinMap = currentState.aggregatedTimeBinMap;
        // comparator used to sort resource metric PODOs based on document timestamp
        Comparator<ResourceMetrics> comparator = (o1, o2) -> {
            if (o1.timestampMicrosUtc < o2.timestampMicrosUtc) {
                return -1;
            } else if (o1.timestampMicrosUtc > o2.timestampMicrosUtc) {
                return 1;
            }
            return 0;
        };
        for (Entry<String, List<ResourceMetrics>> rawMetricListEntry : rawMetricsForKey
                .entrySet()) {
            List<ResourceMetrics> rawMetricList = rawMetricListEntry.getValue();

            if (rawMetricList.isEmpty()) {
                continue;
            }
            String metricKeyWithRpllupSuffix = rawMetricListEntry.getKey();
            rawMetricList.sort(comparator);

            if (aggregatedTimeBinMap == null) {
                aggregatedTimeBinMap = new HashMap<>();
                currentState.aggregatedTimeBinMap = aggregatedTimeBinMap;
            }
            Map<Long, TimeBin> timeBinMap = aggregatedTimeBinMap.get(metricKeyWithRpllupSuffix);
            if (timeBinMap == null) {
                timeBinMap = new HashMap<>();
                aggregatedTimeBinMap.put(metricKeyWithRpllupSuffix, timeBinMap);
            }

            String rawMetricKey = stripRollupKey(metricKeyWithRpllupSuffix);

            Collection<ResourceMetrics> metrics = rawMetricList;
            if (currentState.latestValueOnly.contains(rawMetricKey)) {
                metrics = getLatestMetrics(rawMetricList, metricKeyWithRpllupSuffix);
            }

            Set<AggregationType> aggregationTypes;

            // iterate over the raw metric values and place it in the right time bin
            for (ResourceMetrics metric : metrics) {
                Double value = metric.entries.get(rawMetricKey);
                if (value == null) {
                    continue;
                }
                // TODO VSYM-3190 - Change normalized interval boundary to beginning of the rollup period
                long binId = StatsUtil.computeIntervalEndMicros(
                        metric.timestampMicrosUtc,
                        lookupBinSize(metricKeyWithRpllupSuffix));
                TimeBin bin = timeBinMap.get(binId);
                if (bin == null) {
                    bin = new TimeBin();
                }

                // Figure out the aggregation for the given metric
                aggregationTypes = currentState.aggregations.get(rawMetricKey);
                if (aggregationTypes == null) {
                    aggregationTypes = EnumSet.allOf(AggregationType.class);
                }

                updateBin(bin, value, aggregationTypes);
                timeBinMap.put(binId, bin);
            }
        }
    }

    private void aggregateInMemoryMetrics(SingleResourceStatsAggregationTaskState currentState,
            Map<String, SortedMap<Long, List<TimeBin>>> inMemoryStats) {
        Map<String, Map<Long, TimeBin>> aggregatedTimeBinMap = currentState.aggregatedTimeBinMap;
        for (Entry<String, SortedMap<Long, List<TimeBin>>> inMemoryStatEntry : inMemoryStats
                .entrySet()) {
            String metricName = inMemoryStatEntry.getKey();
            SortedMap<Long, List<TimeBin>> timeSeriesStats = inMemoryStatEntry.getValue();

            if (aggregatedTimeBinMap == null) {
                aggregatedTimeBinMap = new HashMap<>();
                currentState.aggregatedTimeBinMap = aggregatedTimeBinMap;
            }
            Map<Long, TimeBin> timeBinMap = aggregatedTimeBinMap.get(metricName);
            if (timeBinMap == null) {
                timeBinMap = new HashMap<>();
                aggregatedTimeBinMap.put(metricName, timeBinMap);
            }

            Set<AggregationType> aggregationTypes;

            for (Entry<Long, List<TimeBin>> bins : timeSeriesStats.entrySet()) {
                Long binId = bins.getKey();

                TimeBin bin = timeBinMap.get(binId);
                if (bin == null) {
                    bin = new TimeBin();
                }

                // Figure out the aggregation for the given metric
                aggregationTypes = currentState.aggregations.get(stripRollupKey(metricName));
                if (aggregationTypes == null) {
                    aggregationTypes = EnumSet.allOf(AggregationType.class);
                }

                for (TimeBin timeBin : bins.getValue()) {
                    updateBin(bin, timeBin, aggregationTypes);
                }

                timeBinMap.put(binId, bin);
            }
        }
    }

    /**
     * Returns the latest metrics from the raw metrics list. Since the list contains all raw metrics
     * across multiple resource we iterate on the list and pick the latest metric for each resource
     * per bin depending on the metric key.
     *
     * TODO VSYM-2481: Add custom mock stats adapter based test for this.
     */
    private Collection<ResourceMetrics> getLatestMetrics(List<ResourceMetrics> metrics,
            String metricKeyWithInterval) {
        if (metrics.isEmpty()) {
            return Collections.emptyList();
        }

        // Metric link to map of latest value per bin. For example:
        // /monitoring/metrics/<resource-id>_<key1> -> 1474070400000000, <latest-value-of-key1-in-this-time-bucket>
        // /monitoring/metrics/<resource-id>_<key2> -> 1474070400000000, <latest-value-of-key2-in-this-time-bucket>
        Map<String, Map<Long, ResourceMetrics>> metricsByLatestValuePerInterval = new HashMap<>();
        for (ResourceMetrics metric : metrics) {
            String metricKey = stripRollupKey(metricKeyWithInterval);
            Map<Long, ResourceMetrics> metricsByIntervalEndTime = metricsByLatestValuePerInterval
                    .get(metricKey);
            Double value = metric.entries.get(metricKey);
            if (value == null) {
                continue;
            }
            // TODO VSYM-3190 - Change normalized interval boundary to beginning of the rollup period
            long binId = StatsUtil.computeIntervalEndMicros(
                    metric.timestampMicrosUtc, lookupBinSize(metricKeyWithInterval));
            if (metricsByIntervalEndTime == null) {
                metricsByIntervalEndTime = new HashMap<>();
                metricsByIntervalEndTime.put(binId, metric);
                metricsByLatestValuePerInterval.put(metricKey, metricsByIntervalEndTime);
                continue;
            }

            ResourceMetrics existingMetric = metricsByIntervalEndTime.get(binId);
            Double existingValue = null;
            if (existingMetric != null) {
                existingValue = existingMetric.entries.get(metricKey);
            }
            if (existingValue == null
                    || existingMetric.timestampMicrosUtc < metric.timestampMicrosUtc) {
                metricsByIntervalEndTime.put(binId, metric);
            }
        }

        // Gather all latest values
        List<ResourceMetrics> result = new ArrayList<>();
        for (Map<Long, ResourceMetrics> metricsByIntervalEndTime : metricsByLatestValuePerInterval
                .values()) {
            result.addAll(metricsByIntervalEndTime.values());
        }

        return result;
    }

    private void addLastRollupTimeForMissingKeys(
            SingleResourceStatsAggregationTaskState currentState,
            Set<String> publishedKeys, List<Operation> operations) {
        // for all those metrics with no aggregate value was computed and the existing rollup time
        // does not exist, publish a value of 0 so that the next invocation of this task
        // does not have to invoke a query to obtain the last rollup time
        for (Entry<String, Long> rollupTime : currentState.lastRollupTimeForMetric.entrySet()) {
            if (publishedKeys != null && publishedKeys.contains(rollupTime.getKey())
                    || (rollupTime.getValue() != null && rollupTime.getValue() > 0)) {
                continue;
            }
            ServiceStats.ServiceStat lastUpdateStat = new ServiceStats.ServiceStat();
            lastUpdateStat.name = rollupTime.getKey();
            lastUpdateStat.latestValue = 0;
            URI inMemoryStatsUri = UriUtils.buildStatsUri(UriUtils.extendUri(
                    ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.DISCOVERY_SERVICE),
                    currentState.resourceLink));
            operations.add(Operation.createPost(inMemoryStatsUri).setBody(lastUpdateStat));
        }
    }

    /**
     * Publish aggregate metric values
     */
    private void publishMetrics(SingleResourceStatsAggregationTaskState currentState) {
        long expirationTime = Utils.getNowMicrosUtc() + TimeUnit.DAYS.toMicros(EXPIRATION_INTERVAL);
        List<Operation> operations = new ArrayList<>();
        Set<String> publishedKeys = new HashSet<>();

        if (!currentState.hasResources) {
            // reset the metrics to default, when no stats endpoint is available for a resource
            operations = setDefaultMetricValue(currentState, operations, publishedKeys);
        } else {
            if (currentState.aggregatedTimeBinMap == null) {
                addLastRollupTimeForMissingKeys(currentState, publishedKeys, operations);
            } else {
                for (Entry<String, Map<Long, TimeBin>> aggregateEntries : currentState.aggregatedTimeBinMap
                        .entrySet()) {
                    Map<Long, TimeBin> aggrValue = aggregateEntries.getValue();
                    List<Long> keys = new ArrayList<>();
                    keys.addAll(aggrValue.keySet());
                    // create list of operations sorted by the timebin
                    Collections.sort(keys);
                    Long latestTimeKey = null;
                    for (Long timeKey : keys) {
                        ResourceMetrics resourceMetrics = new ResourceMetrics();
                        resourceMetrics.entries = new HashMap<>();
                        resourceMetrics.entries.put(aggregateEntries.getKey(),
                                aggrValue.get(timeKey).avg);
                        resourceMetrics.timestampMicrosUtc = timeKey;
                        resourceMetrics.documentSelfLink = StatsUtil
                                .getMetricKey(currentState.resourceLink, Utils.getNowMicrosUtc());
                        resourceMetrics.documentExpirationTimeMicros = expirationTime;

                        operations.add(Operation.createPost(UriUtils.buildUri(
                                ClusterUtil.getClusterUri(getHost(),
                                        ServiceTypeCluster.METRIC_SERVICE),
                                ResourceMetricsService.FACTORY_LINK))
                                .setBody(resourceMetrics));
                        publishedKeys.add(aggregateEntries.getKey());
                        latestTimeKey = timeKey;
                    }
                    // update the last update time as a stat
                    ServiceStats.ServiceStat lastUpdateStat = new ServiceStats.ServiceStat();
                    lastUpdateStat.name = aggregateEntries.getKey();
                    lastUpdateStat.latestValue = latestTimeKey;

                    URI inMemoryStatsUri = UriUtils.buildStatsUri(UriUtils.extendUri(
                            ClusterUtil.getClusterUri(getHost(),
                                    ServiceTypeCluster.DISCOVERY_SERVICE),
                            currentState.resourceLink));
                    operations.add(Operation.createPost(inMemoryStatsUri).setBody(lastUpdateStat));
                }
                addLastRollupTimeForMissingKeys(currentState, publishedKeys, operations);
            }
        }

        if (operations.isEmpty()) {
            // nothing to persist, just finish the task
            sendSelfPatch(currentState, TaskStage.FINISHED, null);
            return;
        }

        batchPublishMetrics(currentState, operations, 0);
    }

    /**
     * Sets metric constants related to a resource to '0' value.
     * Metric constants like 'EstimatedCharges', 'Cost', 'CurrentBurnRatePerHour',
     * 'AverageBurnRatePerHour' stored in 'lastRollupTimeForMetric' map attribute will be
     * set to 0 when project is newly created or has all endpoints removed.
     */
    private List<Operation> setDefaultMetricValue(
            SingleResourceStatsAggregationTaskState currentState,
            List<Operation> operations, Set<String> publishedKeys) {

        Map<String, Long> lastRollTimeMetric = currentState.lastRollupTimeForMetric;
        for (Entry<String, Long> aggregateEntries : lastRollTimeMetric.entrySet()) {
            // value is null when project is newly created and raw metrics are not yet generated.
            // upon stats aggregation value is set to 0. As project resources don't exist,
            // only newly created projects will have aggregate-metrics services generated .

            Long aggregateMetricLastRollUpTime = aggregateEntries.getValue() == null ? 0L
                    : aggregateEntries.getValue();

            ResourceMetrics resourceMetrics = new ResourceMetrics();
            resourceMetrics.entries = new HashMap<>();
            resourceMetrics.entries.put(aggregateEntries.getKey(), 0.0);
            resourceMetrics.timestampMicrosUtc = aggregateMetricLastRollUpTime;
            resourceMetrics.documentSelfLink = StatsUtil.getMetricKey(currentState.resourceLink, Utils.getNowMicrosUtc());

            operations.add(Operation
                    .createPost(UriUtils.buildUri(
                            ClusterUtil.getClusterUri(getHost(),
                                    ServiceTypeCluster.METRIC_SERVICE),
                            ResourceMetricsService.FACTORY_LINK))
                    .setBody(resourceMetrics));
            publishedKeys.add(aggregateEntries.getKey());

            ServiceStats.ServiceStat lastUpdateStat = new ServiceStats.ServiceStat();
            lastUpdateStat.name = aggregateEntries.getKey();
            lastUpdateStat.latestValue = aggregateMetricLastRollUpTime;
            URI inMemoryStatsUri = UriUtils.buildStatsUri(UriUtils.extendUri(
                    ClusterUtil.getClusterUri(getHost(),
                            ServiceTypeCluster.DISCOVERY_SERVICE),
                    currentState.resourceLink));
            operations.add(Operation.createPost(inMemoryStatsUri).setBody(lastUpdateStat));
        }
        return operations;
    }

    public void batchPublishMetrics(SingleResourceStatsAggregationTaskState currentState,
            List<Operation> operations, int batchIndex) {
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
                sendSelfFailurePatch(currentState, exc.values().iterator().next().getMessage());
                return;
            }

            if (finalNextBatchIndex == null || finalNextBatchIndex == operations.size()) {
                sendSelfPatch(currentState, TaskStage.FINISHED, null);
                return;
            }

            batchPublishMetrics(currentState, operations, finalNextBatchIndex);
        });
        opSequence.sendWith(this);
    }

    /**
     * Build the keys used to represent rolled up data. We currently support hourly and daily
     * rollups.
     */
    private List<String> buildRollupKeys(String baseKey) {
        List<String> returnList = new ArrayList<>();
        returnList.add(baseKey + StatsConstants.HOUR_SUFFIX);
        // TODO VSYM-3109: Re-enable this once we fix daily rollup performance.
        // returnList.add(baseKey + StatsConstants.DAILY_SUFFIX);
        return returnList;
    }

    /**
     * Returns the raw metric key by stripping the rollup metric key suffix.
     */
    private String stripRollupKey(String rollupKey) {
        if (rollupKey.contains(StatsConstants.HOUR_SUFFIX)) {
            return rollupKey.replace(StatsConstants.HOUR_SUFFIX, "");
        }
        return rollupKey.replace(StatsConstants.DAILY_SUFFIX, "");
    }

    /**
     * Lookup the size of the time bin based on the metric key
     */
    private int lookupBinSize(String metricKey) {
        if (metricKey.contains(StatsConstants.HOUR_SUFFIX)) {
            return StatsConstants.BUCKET_SIZE_HOURS_IN_MILLIS;
        }
        return StatsConstants.BUCKET_SIZE_DAYS_IN_MILLIS;
    }

    /**
     * Update time bin with raw value.
     */
    private TimeBin updateBin(TimeBin inputBin, double value,
            Set<AggregationType> aggregationTypes) {
        if (aggregationTypes.contains(AggregationType.MAX)) {
            if (inputBin.max == null || inputBin.max < value) {
                inputBin.max = value;
            }
        }

        if (aggregationTypes.contains(AggregationType.MIN)) {
            if (inputBin.min == null || inputBin.min > value) {
                inputBin.min = value;
            }
        }

        if (aggregationTypes.contains(AggregationType.AVG)) {
            if (inputBin.avg == null) {
                inputBin.avg = value;
            } else {
                inputBin.avg = ((inputBin.avg * inputBin.count) + value) / (inputBin.count + 1);
            }
        }

        if (aggregationTypes.contains(AggregationType.SUM)) {
            if (inputBin.sum == null) {
                inputBin.sum = value;
            } else {
                inputBin.sum += value;
            }
        }
        inputBin.count++;
        return inputBin;
    }

    /**
     * Update time bin based on given time bin value.
     */
    private TimeBin updateBin(TimeBin currentBin, TimeBin value,
            Set<AggregationType> aggregationTypes) {
        if (aggregationTypes.contains(AggregationType.MAX)) {
            if (currentBin.max == null || currentBin.max < value.max) {
                currentBin.max = value.max;
            }
        }

        if (aggregationTypes.contains(AggregationType.MIN)) {
            if (currentBin.min == null || currentBin.min > value.min) {
                currentBin.min = value.min;
            }
        }

        if (aggregationTypes.contains(AggregationType.AVG)) {
            if (currentBin.avg == null) {
                currentBin.avg = value.avg;
            } else {
                currentBin.avg = ((currentBin.avg * currentBin.count) + (value.avg * value.count)) /
                        (currentBin.count + value.count);
            }
        }

        if (aggregationTypes.contains(AggregationType.SUM)) {
            if (currentBin.sum == null) {
                currentBin.sum = value.sum;
            } else {
                currentBin.sum += value.sum;
            }
        }
        currentBin.count += value.count;
        return currentBin;
    }
}
