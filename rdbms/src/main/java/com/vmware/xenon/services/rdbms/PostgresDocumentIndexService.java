/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.xenon.services.rdbms;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import com.vmware.xenon.common.NamedThreadFactory;
import com.vmware.xenon.common.NodeSelectorService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.QueryFilterUtils;
import com.vmware.xenon.common.RoundRobinOperationQueue;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStatUtils;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.AggregationType;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.config.XenonConfiguration;
import com.vmware.xenon.common.opentracing.TracingExecutor;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryFilter;
import com.vmware.xenon.services.common.QueryFilter.QueryFilterException;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryRuntimeContext;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.SystemUserService;
import com.vmware.xenon.services.common.UpdateIndexRequest;
import com.vmware.xenon.services.rdbms.PostgresSchemaManager.TableDescription;

public class PostgresDocumentIndexService extends StatelessService {

    // TODO: temporary using the default link because QueryTaskService relies on it
    public static final String SELF_LINK = ServiceUriPaths.CORE_DOCUMENT_INDEX;

    public static final int QUERY_THREAD_COUNT = XenonConfiguration.integer(
            PostgresDocumentIndexService.class,
            "QUERY_THREAD_COUNT",
            Utils.DEFAULT_THREAD_COUNT * 2
    );

    public static final int UPDATE_THREAD_COUNT = XenonConfiguration.integer(
            PostgresDocumentIndexService.class,
            "UPDATE_THREAD_COUNT",
            Utils.DEFAULT_THREAD_COUNT * 2
    );

    public static final int QUERY_QUEUE_DEPTH = XenonConfiguration.integer(
            PostgresDocumentIndexService.class,
            "queryQueueDepth",
            10 * Service.OPERATION_QUEUE_DEFAULT_LIMIT
    );

    public static final int UPDATE_QUEUE_DEPTH = XenonConfiguration.integer(
            PostgresDocumentIndexService.class,
            "updateQueueDepth",
            10 * Service.OPERATION_QUEUE_DEFAULT_LIMIT
    );

    public static final int MIN_QUERY_RESULT_LIMIT = 1000;

    public static final int DEFAULT_QUERY_RESULT_LIMIT = 10000;

    public static final int DEFAULT_QUERY_PAGE_RESULT_LIMIT = 10000;

    private static final int QUERY_EXECUTOR_WORK_QUEUE_CAPACITY = XenonConfiguration.integer(
            PostgresDocumentIndexService.class,
            "queryExecutorWorkQueueCapacity",
            QUERY_QUEUE_DEPTH
    );

    private static final int UPDATE_EXECUTOR_WORK_QUEUE_CAPACITY = XenonConfiguration.integer(
            PostgresDocumentIndexService.class,
            "updateExecutorWorkQueueCapacity",
            UPDATE_QUEUE_DEPTH
    );

    private static int expiredDocumentSearchThreshold = 1000;

    static int queryResultLimit = DEFAULT_QUERY_RESULT_LIMIT;

    private static int queryPageResultLimit = DEFAULT_QUERY_PAGE_RESULT_LIMIT;

    private final Runnable queryTaskHandler = this::handleQueryRequest;

    private final Runnable updateRequestHandler = this::handleUpdateRequest;

    public static void setImplicitQueryResultLimit(int limit) {
        queryResultLimit = limit;
    }

    public static int getImplicitQueryResultLimit() {
        return queryResultLimit;
    }

    public static void setImplicitQueryProcessingPageSize(int limit) {
        queryPageResultLimit = limit;
    }

    public static int getImplicitQueryProcessingPageSize() {
        return queryPageResultLimit;
    }

    public static void setExpiredDocumentSearchThreshold(int count) {
        expiredDocumentSearchThreshold = count;
    }

    public static int getExpiredDocumentSearchThreshold() {
        return expiredDocumentSearchThreshold;
    }

    public static final String STAT_NAME_ACTIVE_QUERY_FILTERS = "activeQueryFilterCount";

    public static final String STAT_NAME_COMMIT_COUNT = "commitCount";

    public static final String STAT_NAME_COMMIT_DURATION_MICROS = "commitDurationMicros";

    public static final String STAT_NAME_GROUP_QUERY_COUNT = "groupQueryCount";

    public static final String STAT_NAME_QUERY_DURATION_MICROS = "queryDurationMicros";

    public static final String STAT_NAME_GROUP_QUERY_DURATION_MICROS = "groupQueryDurationMicros";

    public static final String STAT_NAME_QUERY_SINGLE_DURATION_MICROS = "querySingleDurationMicros";

    public static final String STAT_NAME_FORCED_UPDATE_DOCUMENT_DELETE_COUNT = "singleVersionDocumentDeleteCount";

    public static final String STAT_NAME_PAGINATED_SEARCHER_FORCE_DELETION_COUNT = "paginatedIndexSearcherForceDeletionCount";

    public static final String STAT_NAME_WRITER_ALREADY_CLOSED_EXCEPTION_COUNT = "indexWriterAlreadyClosedFailureCount";

    public static final String STAT_NAME_SERVICE_DELETE_COUNT = "serviceDeleteCount";

    public static final String STAT_NAME_DOCUMENT_EXPIRATION_COUNT = "expiredDocumentCount";

    public static final String STAT_NAME_MAINTENANCE_SEARCHER_REFRESH_DURATION_MICROS =
            "maintenanceSearcherRefreshDurationMicros";

    public static final String STAT_NAME_MAINTENANCE_DOCUMENT_EXPIRATION_DURATION_MICROS =
            "maintenanceDocumentExpirationDurationMicros";

    public static final String STAT_NAME_DOCUMENT_KIND_QUERY_COUNT_FORMAT = "documentKindQueryCount-%s";

    public static final String STAT_NAME_NON_DOCUMENT_KIND_QUERY_COUNT = "nonDocumentKindQueryCount";

    public static final String STAT_NAME_SINGLE_QUERY_BY_FACTORY_COUNT_FORMAT = "singleQueryByFactoryCount-%s";

    public static final String STAT_NAME_PREFIX_UPDATE_QUEUE_DEPTH = "updateQueueDepth";

    public static final String STAT_NAME_FORMAT_UPDATE_QUEUE_DEPTH =
            STAT_NAME_PREFIX_UPDATE_QUEUE_DEPTH + "-%s";

    public static final String STAT_NAME_PREFIX_QUERY_QUEUE_DEPTH = "queryQueueDepth";

    public static final String STAT_NAME_FORMAT_QUERY_QUEUE_DEPTH =
            STAT_NAME_PREFIX_QUERY_QUEUE_DEPTH + "-%s";

    private static final EnumSet<AggregationType> AGGREGATION_TYPE_AVG_MAX =
            EnumSet.of(AggregationType.AVG, AggregationType.MAX);

    private static final EnumSet<AggregationType> AGGREGATION_TYPE_SUM = EnumSet
            .of(AggregationType.SUM);

    /**
     * Synchronization object used to coordinate index writer update
     */
    protected final Semaphore writerSync = new Semaphore(
            UPDATE_THREAD_COUNT + QUERY_THREAD_COUNT);

    protected Map<String, QueryTask> activeQueries = new ConcurrentHashMap<>();

    private ExecutorService privateIndexingExecutor;
    private ExecutorService privateQueryExecutor;

    private final RoundRobinOperationQueue queryQueue = new RoundRobinOperationQueue(
            "index-service-query", QUERY_QUEUE_DEPTH);

    private final RoundRobinOperationQueue updateQueue = new RoundRobinOperationQueue(
            "index-service-update", UPDATE_QUEUE_DEPTH);

    private URI uri;

    private final DataSource ds;
    private final PostgresServiceDocumentDao dao;

    public static class DeleteQueryRuntimeContextRequest extends ServiceDocument {
        public QueryRuntimeContext context;
        static final String KIND = Utils.buildKind(DeleteQueryRuntimeContextRequest.class);
    }

    /**
     * Special GET request/response body to retrieve lucene related info.
     *
     * Internal usage only mainly for backup/restore.
     */
    public static class InternalDocumentIndexInfo {
        public PostgresDocumentIndexService luceneIndexService;
        public Semaphore writerSync;
    }

    public static class MaintenanceRequest {
        static final String KIND = Utils.buildKind(MaintenanceRequest.class);
    }

    public PostgresDocumentIndexService(ServiceHost host, DataSource ds) {
        super(ServiceDocument.class);
        toggleOption(ServiceOption.CORE, true);
        toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        // TODO: define how often to expire documents
        setMaintenanceIntervalMicros(TimeUnit.SECONDS.toMicros(1));

        setHost(host);
        this.ds = ds;
        this.dao = new PostgresServiceDocumentDao(host, this, ds);
    }

    PostgresServiceDocumentDao getDao() {
        return this.dao;
    }

    @Override
    public void handleStart(final Operation post) {
        super.setMaintenanceIntervalMicros(getHost().getMaintenanceIntervalMicros() * 5);
        // index service getUri() will be invoked on every load and save call for every operation,
        // so its worth caching (plus we only have a very small number of index services
        this.uri = post.getUri();

        ExecutorService es = new ThreadPoolExecutor(QUERY_THREAD_COUNT, QUERY_THREAD_COUNT,
                1, TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(QUERY_EXECUTOR_WORK_QUEUE_CAPACITY),
                new NamedThreadFactory(getUri() + "/queries"));
        this.privateQueryExecutor = TracingExecutor.create(es, getHost().getTracer());

        es = new ThreadPoolExecutor(UPDATE_THREAD_COUNT, UPDATE_THREAD_COUNT,
                1, TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(UPDATE_EXECUTOR_WORK_QUEUE_CAPACITY),
                new NamedThreadFactory(getUri() + "/updates"));
        this.privateIndexingExecutor = TracingExecutor.create(es, getHost().getTracer());

        post.complete();
    }

    private void setTimeSeriesStat(String name, EnumSet<AggregationType> type, double v) {
        if (!hasOption(ServiceOption.INSTRUMENTATION)) {
            return;
        }
        ServiceStat dayStat = ServiceStatUtils.getOrCreateDailyTimeSeriesStat(this, name, type);
        setStat(dayStat, v);

        ServiceStat hourStat = ServiceStatUtils.getOrCreateHourlyTimeSeriesStat(this, name, type);
        setStat(hourStat, v);
    }

    private void adjustTimeSeriesStat(String name, EnumSet<AggregationType> type, double delta) {
        if (!hasOption(ServiceOption.INSTRUMENTATION)) {
            return;
        }

        ServiceStat dayStat = ServiceStatUtils.getOrCreateDailyTimeSeriesStat(this, name, type);
        adjustStat(dayStat, delta);

        ServiceStat hourStat = ServiceStatUtils.getOrCreateHourlyTimeSeriesStat(this, name, type);
        adjustStat(hourStat, delta);
    }

    private void setTimeSeriesHistogramStat(String name, EnumSet<AggregationType> type, double v) {
        if (!this.hasOption(ServiceOption.INSTRUMENTATION)) {
            return;
        }
        ServiceStat dayStat = ServiceStatUtils
                .getOrCreateDailyTimeSeriesHistogramStat(this, name, type);
        setStat(dayStat, v);

        ServiceStat hourStat = ServiceStatUtils
                .getOrCreateHourlyTimeSeriesHistogramStat(this, name, type);
        setStat(hourStat, v);
    }

    private String getQueryStatName(QueryTask.Query query) {
        if (query.term != null) {
            if (query.term.propertyName.equals(ServiceDocument.FIELD_NAME_KIND)) {
                return String
                        .format(STAT_NAME_DOCUMENT_KIND_QUERY_COUNT_FORMAT, query.term.matchValue);
            }
            return STAT_NAME_NON_DOCUMENT_KIND_QUERY_COUNT;
        }

        StringBuilder kindSb = new StringBuilder();
        for (QueryTask.Query clause : query.booleanClauses) {
            if (clause.term == null || clause.term.propertyName == null
                    || clause.term.matchValue == null) {
                continue;
            }
            if (clause.term.propertyName.equals(ServiceDocument.FIELD_NAME_KIND)) {
                if (kindSb.length() > 0) {
                    kindSb.append(", ");
                }
                kindSb.append(clause.term.matchValue);
            }
        }

        if (kindSb.length() > 0) {
            return String.format(STAT_NAME_DOCUMENT_KIND_QUERY_COUNT_FORMAT, kindSb.toString());
        }

        return STAT_NAME_NON_DOCUMENT_KIND_QUERY_COUNT;
    }

    private void handleDeleteRuntimeContext(Operation op) {
        op.complete();

        adjustTimeSeriesStat(STAT_NAME_PAGINATED_SEARCHER_FORCE_DELETION_COUNT,
                AGGREGATION_TYPE_SUM, 1);
    }

    @Override
    public void authorizeRequest(Operation op) {
        op.complete();
    }

    @Override
    public void handleRequest(Operation op) {
        Action a = op.getAction();
        if (a == Action.PUT) {
            Operation.failActionNotSupported(op);
            return;
        }

        if (a == Action.PATCH && op.isRemote()) {
            // PATCH is reserved for in-process QueryTaskService
            Operation.failActionNotSupported(op);
            return;
        }

        try {
            if (a == Action.GET || a == Action.PATCH) {
                if (offerQueryOperation(op)) {
                    this.privateQueryExecutor.submit(this.queryTaskHandler);
                }
            } else {
                if (offerUpdateOperation(op)) {
                    this.privateIndexingExecutor.submit(this.updateRequestHandler);
                }
            }
        } catch (RejectedExecutionException e) {
            op.fail(e);
        }
    }

    private void handleQueryRequest() {
        Operation op = pollQueryOperation();
        if (op == null) {
            return;
        }
        if (op.getExpirationMicrosUtc() > 0 && op.getExpirationMicrosUtc() < Utils
                .getSystemNowMicrosUtc()) {
            op.fail(new RejectedExecutionException("Operation has expired"));
            return;
        }
        OperationContext originalContext = OperationContext.getOperationContext();
        try {
            this.writerSync.acquire();

            OperationContext.setFrom(op);
            switch (op.getAction()) {
            case GET:
                // handle special GET request. Internal call only. Currently from backup/restore services.
                if (!op.isRemote() && op.hasBody() && op
                        .getBodyRaw() instanceof InternalDocumentIndexInfo) {
                    Operation.failActionNotSupported(op);
                } else {
                    handleGetImpl(op);
                }
                break;
            case PATCH:
                ServiceDocument sd = (ServiceDocument) op.getBodyRaw();
                if (sd.documentKind != null) {
                    if (sd.documentKind.equals(QueryTask.KIND)) {
                        QueryTask task = (QueryTask) sd;
                        handleQueryTaskPatch(op, task);
                        break;
                    }
                    if (sd.documentKind.equals(DeleteQueryRuntimeContextRequest.KIND)) {
                        handleDeleteRuntimeContext(op);
                        break;
                    }
                }
                Operation.failActionNotSupported(op);
                break;
            default:
                break;
            }
        } catch (Exception e) {
            checkFailureAndRecover(e);
            op.fail(e);
        } finally {
            OperationContext.setFrom(originalContext);
            this.writerSync.release();
        }
    }

    private void handleUpdateRequest() {
        Operation op = pollUpdateOperation();
        if (op == null) {
            return;
        }
        OperationContext originalContext = OperationContext.getOperationContext();
        try {
            this.writerSync.acquire();
            OperationContext.setFrom(op);
            switch (op.getAction()) {
            case DELETE:
                handleDeleteImpl(op);
                break;
            case POST:
                Object o = op.getBodyRaw();
                if (o != null) {
                    if (o instanceof UpdateIndexRequest) {
                        updateIndex(op);
                        break;
                    }
                    if (o instanceof MaintenanceRequest) {
                        handleMaintenanceImpl(op);
                        break;
                    }
                }
                Operation.failActionNotSupported(op);
                break;
            default:
                break;
            }
        } catch (Exception e) {
            checkFailureAndRecover(e);
            op.fail(e);
        } finally {
            OperationContext.setFrom(originalContext);
            this.writerSync.release();
        }
    }

    private void handleQueryTaskPatch(Operation op, QueryTask task) throws Exception {
        if (task.querySpec.options.contains(QueryOption.CONTINUOUS)) {
            if (handleContinuousQueryTaskPatch(op, task, task.querySpec)) {
                return;
            }
            // intentional fall through for tasks just starting and need to execute a query
        }

        ServiceDocumentQueryResult rsp = dao.queryDocuments(op, task);
        if (rsp == null) {
            rsp = new ServiceDocumentQueryResult();
            rsp.queryTimeMicros = 0L;
            rsp.documentOwner = getHost().getId();
            rsp.documentCount = 0L;
            if (task.querySpec.options.contains(QueryOption.EXPAND_CONTENT)) {
                rsp.documents = Collections.emptyMap();
            }

        }
        op.setBodyNoCloning(rsp).complete();
    }

    private boolean handleContinuousQueryTaskPatch(Operation op, QueryTask task,
            QuerySpecification qs) throws QueryFilterException {
        switch (task.taskInfo.stage) {
        case CREATED:
            logWarning("Task %s is in invalid state: %s", task.taskInfo.stage);
            op.fail(new IllegalStateException("Stage not supported"));
            return true;
        case STARTED:
            QueryTask clonedTask = new QueryTask();
            clonedTask.documentSelfLink = task.documentSelfLink;
            clonedTask.querySpec = task.querySpec;
            clonedTask.querySpec.context.filter = QueryFilter.create(qs.query);
            clonedTask.querySpec.context.subjectLink = getSubject(op);
            this.activeQueries.put(task.documentSelfLink, clonedTask);
            adjustTimeSeriesStat(STAT_NAME_ACTIVE_QUERY_FILTERS, AGGREGATION_TYPE_SUM,
                    1);
            logInfo("Activated continuous query task: %s", task.documentSelfLink);
            break;
        case CANCELLED:
        case FAILED:
        case FINISHED:
            if (this.activeQueries.remove(task.documentSelfLink) != null) {
                adjustTimeSeriesStat(STAT_NAME_ACTIVE_QUERY_FILTERS, AGGREGATION_TYPE_SUM,
                        -1);
            }
            op.complete();
            return true;
        default:
            break;
        }
        return false;
    }

    public void handleGetImpl(Operation get) throws Exception {
        String selfLink;
        ServiceOption serviceOption = ServiceOption.NONE;

        EnumSet<QueryOption> options = EnumSet.noneOf(QueryOption.class);
        if (get.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_INDEX_CHECK)) {
            // fast path for checking if a service exists, and loading its latest state
            serviceOption = ServiceOption.PERSISTENCE;
            // the GET operation URI is set to the service we want to load, not the self link
            // of the index service. This is only possible when the operation was directly
            // dispatched from the local host, on the index service
            selfLink = get.getUri().getPath();
            options.add(QueryOption.INCLUDE_DELETED);
        } else {
            // REST API for loading service state, given a set of URI query parameters
            Map<String, String> params = UriUtils.parseUriQueryParams(get.getUri());
            String cap = params.get(UriUtils.URI_PARAM_CAPABILITY);

            if (cap != null) {
                serviceOption = ServiceOption.valueOf(cap);
            }

            if (serviceOption == ServiceOption.IMMUTABLE) {
                options.add(QueryOption.INCLUDE_ALL_VERSIONS);
                serviceOption = ServiceOption.PERSISTENCE;
            }

            if (params.containsKey(UriUtils.URI_PARAM_INCLUDE_DELETED)) {
                options.add(QueryOption.INCLUDE_DELETED);
            }

            if (params.containsKey(ServiceDocument.FIELD_NAME_VERSION)) {
                // version = Long.parseLong(params.get(ServiceDocument.FIELD_NAME_VERSION));
            }

            selfLink = params.get(ServiceDocument.FIELD_NAME_SELF_LINK);
            String fieldToExpand = params.get(UriUtils.URI_PARAM_ODATA_EXPAND);
            if (fieldToExpand == null) {
                fieldToExpand = params.get(UriUtils.URI_PARAM_ODATA_EXPAND_NO_DOLLAR_SIGN);
            }
            if (fieldToExpand != null
                    && fieldToExpand
                    .equals(ServiceDocumentQueryResult.FIELD_NAME_DOCUMENT_LINKS)) {
                options.add(QueryOption.EXPAND_CONTENT);
            }
        }

        if (selfLink == null) {
            get.fail(new IllegalArgumentException(
                    ServiceDocument.FIELD_NAME_SELF_LINK + " query parameter is required"));
            return;
        }

        if (!selfLink.endsWith(UriUtils.URI_WILDCARD_CHAR)) {

            // Enforce auth check for the returning document for remote GET requests.
            // This is mainly for the direct client requests to the index-service such as
            // "/core/document-index?documentSelfLink=...".
            // Some other core services also perform remote GET (e.g.: NodeSelectorSynchronizationService),
            // but they populate appropriate auth context such as system-user.
            // For non-wildcard selfLink request, auth check is performed as part of queryIndex().
            if (get.isRemote() && getHost().isAuthorizationEnabled()) {
                get.nestCompletion((op, ex) -> {
                    if (ex != null) {
                        get.fail(ex);
                        return;
                    }

                    if (get.getAuthorizationContext().isSystemUser() || !op.hasBody()) {
                        // when there is no matching document, we cannot evaluate the auth, thus simply complete.
                        get.complete();
                        return;
                    }

                    // evaluate whether the matched document is authorized for the user
                    QueryFilter queryFilter = get.getAuthorizationContext()
                            .getResourceQueryFilter(Action.GET);
                    if (queryFilter == null) {
                        // do not match anything
                        queryFilter = QueryFilter.FALSE;
                    }
                    // This completion handler is called right after it retrieved the document from lucene and
                    // deserialized it to its state type.
                    // Since calling "op.getBody(ServiceDocument.class)" changes(down cast) the actual document object
                    // to an instance of ServiceDocument, it will lose the additional data which might be required in
                    // authorization filters; Therefore, here uses "op.getBodyRaw()" and just cast to ServiceDocument
                    // which doesn't convert the document object.
                    ServiceDocument doc = (ServiceDocument) op.getBodyRaw();
                    if (!QueryFilterUtils.evaluate(queryFilter, doc, getHost())) {
                        get.fail(Operation.STATUS_CODE_FORBIDDEN);
                        return;
                    }
                    get.complete();
                });
            }

            // Most basic query is retrieving latest document at latest version for a specific link
            queryIndexSingle(selfLink, get);
            return;
        }

        // Self link prefix query, returns all self links with the same prefix. A GET on a
        // factory translates to this query.
        selfLink = selfLink.substring(0, selfLink.length() - 1);
        ServiceDocumentQueryResult rsp = dao.queryBySelfLinkPrefix(get, selfLink, options);
        if (rsp != null) {
            rsp.documentOwner = getHost().getId();
            get.setBodyNoCloning(rsp).complete();
            return;
        }

        if (serviceOption == ServiceOption.PERSISTENCE) {
            // specific index requested but no results, return empty response
            rsp = new ServiceDocumentQueryResult();
            rsp.documentLinks = new ArrayList<>();
            if (options.contains(QueryOption.EXPAND_CONTENT)) {
                rsp.documents = new HashMap<>();
            }
            rsp.documentOwner = getHost().getId();
            rsp.documentCount = 0L;
            get.setBodyNoCloning(rsp).complete();
            return;
        }

        // no results in the index, search the service host started services
        queryServiceHost(selfLink + UriUtils.URI_WILDCARD_CHAR, options, get);
    }

    /**
     * retrieves the next available operation given the fairness scheme
     */
    private Operation pollQueryOperation() {
        return this.queryQueue.poll();
    }

    private Operation pollUpdateOperation() {
        return this.updateQueue.poll();
    }

    /**
     * Queues operation in a multi-queue that uses the subject as the key per queue
     */
    private boolean offerQueryOperation(Operation op) {
        String subject = getSubject(op);
        return this.queryQueue.offer(subject, op);
    }

    private boolean offerUpdateOperation(Operation op) {
        String subject = getSubject(op);
        return this.updateQueue.offer(subject, op);
    }

    private String getSubject(Operation op) {
        if (op.getAuthorizationContext() != null
                && op.getAuthorizationContext().isSystemUser()) {
            return SystemUserService.SELF_LINK;
        }

        if (getHost().isAuthorizationEnabled()) {
            return op.getAuthorizationContext().getClaims().getSubject();
        }

        return GuestUserService.SELF_LINK;
    }

    private void queryIndexSingle(String selfLink, Operation op)
            throws Exception {
        try {
            ServiceDocument sd = getDocument(selfLink);
            if (sd == null) {
                op.complete();
                return;
            }
            op.setBodyNoCloning(sd).complete();
        } catch (CancellationException e) {
            op.fail(e);
        }
    }

    private ServiceDocument getDocument(String selfLink) throws Exception {
        long startNanos = System.nanoTime();
        ServiceDocument doc = dao.loadDocument(selfLink);

        if (hasOption(ServiceOption.INSTRUMENTATION)) {
            long durationNanos = System.nanoTime() - startNanos;
            setTimeSeriesHistogramStat(STAT_NAME_QUERY_SINGLE_DURATION_MICROS,
                    AGGREGATION_TYPE_AVG_MAX, TimeUnit.NANOSECONDS.toMicros(durationNanos));
            String factoryLink = UriUtils.getParentPath(selfLink);
            if (factoryLink != null) {
                String statKey = String
                        .format(STAT_NAME_SINGLE_QUERY_BY_FACTORY_COUNT_FORMAT, factoryLink);
                adjustStat(statKey, 1);
            }
        }

        return doc;
    }

    private void queryServiceHost(String selfLink, EnumSet<QueryOption> options, Operation op) {
        if (options.contains(QueryOption.EXPAND_CONTENT)) {
            // the index writers had no results, ask the host a simple prefix query
            // for the services, and do a manual expand
            op.nestCompletion(o -> {
                expandLinks(o, op);
            });
        }
        getHost().queryServiceUris(selfLink, op);
    }

    private void expandLinks(Operation o, Operation get) {
        ServiceDocumentQueryResult r = o.getBody(ServiceDocumentQueryResult.class);
        if (r.documentLinks == null || r.documentLinks.isEmpty()) {
            get.setBodyNoCloning(r).complete();
            return;
        }

        r.documents = new HashMap<>();

        AtomicInteger i = new AtomicInteger(r.documentLinks.size());
        CompletionHandler c = (op, e) -> {
            try {
                if (e != null) {
                    logWarning("failure expanding %s: %s", op.getUri().getPath(), e);
                    return;
                }
                synchronized (r.documents) {
                    r.documents.put(op.getUri().getPath(), op.getBodyRaw());
                }
            } finally {
                if (i.decrementAndGet() == 0) {
                    get.setBodyNoCloning(r).complete();
                }
            }
        };

        for (String selfLink : r.documentLinks) {
            sendRequest(Operation.createGet(this, selfLink)
                    .setCompletion(c));
        }
    }

    public void handleDeleteImpl(Operation delete) throws Exception {
        setProcessingStage(ProcessingStage.STOPPED);

        this.privateIndexingExecutor.shutdown();
        this.privateQueryExecutor.shutdown();
        getHost().stopService(this);
        delete.complete();
    }

    protected void updateIndex(Operation updateOp) throws Exception {
        UpdateIndexRequest r = updateOp.getBody(UpdateIndexRequest.class);
        ServiceDocument s = r.document;
        ServiceDocumentDescription desc = r.description;

        if (updateOp.isRemote()) {
            updateOp.fail(new IllegalStateException("Remote requests not allowed"));
            return;
        }

        if (s == null) {
            updateOp.fail(new IllegalArgumentException("document is required"));
            return;
        }

        String link = s.documentSelfLink;
        if (link == null) {
            updateOp.fail(new IllegalArgumentException(
                    "documentSelfLink is required"));
            return;
        }

        if (s.documentUpdateAction == null) {
            updateOp.fail(new IllegalArgumentException(
                    "documentUpdateAction is required"));
            return;
        }

        if (desc == null) {
            updateOp.fail(new IllegalArgumentException("description is required"));
            return;
        }

        boolean forceIndexUpdate = updateOp.getAction() == Action.POST
                && updateOp.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);

        s.documentDescription = null;
        dao.saveDocument(s, desc, forceIndexUpdate);

        if (forceIndexUpdate) {
            // Delete all previous versions from the index.
            adjustStat(STAT_NAME_FORCED_UPDATE_DOCUMENT_DELETE_COUNT, 1);
        }

        // Use time AFTER index was updated to be sure that it can be compared
        // against the time the searcher was updated and have this change
        // be reflected in the new searcher. If the start time would be used,
        // it is possible to race with updating the searcher and NOT have this
        // change be reflected in the searcher.
        updateOp.setBodyNoCloning(null).complete();
        applyActiveQueries(updateOp, s, desc);
    }

    /**
     * Will attempt to re-open index writer to recover from a specific exception. The method
     * assumes the caller has acquired the writer semaphore
     */
    private void checkFailureAndRecover(Exception e) {
        if (getHost().isStopping()) {
            logInfo("Exception after host stop, on index service thread: %s", e);
            return;
        }

        logSevere("Exception on index service thread: %s", Utils.toString(e));
        adjustStat(STAT_NAME_WRITER_ALREADY_CLOSED_EXCEPTION_COUNT, 1);
    }

    private void deleteAllDocumentsForSelfLink(Connection conn, String tableName,
            Operation postOrDelete, String link, ServiceDocument state)
            throws Exception {
        dao.deleteDocument(conn, tableName, link);
        postOrDelete.complete();
        adjustTimeSeriesStat(STAT_NAME_SERVICE_DELETE_COUNT, AGGREGATION_TYPE_SUM, 1);
        logFine("%s expired", link);
        if (state == null) {
            return;
        }

        applyActiveQueries(postOrDelete, state, null);

        // remove service, if its running
        // Broadcasting delete to all nodes, to make sure owner node stop the service
        // TODO: Find better solution, all nodes query for expiration and stop if service owner?
        // TODO: Consider skipping delete for IMMUTABLE and non-periodic services, since they will
        // stop on idle
        // TODO: Why handleDelete is not called? Noticed same behavior with lucene
        Operation delete = Operation.createDelete(this, state.documentSelfLink)
                .setBodyNoCloning(state)
                .disableFailureLogging(true)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_INDEX_UPDATE)
                .setReferer(getUri());
        getHost().broadcastRequest(ServiceUriPaths.DEFAULT_NODE_SELECTOR, false, delete);
    }

    @Override
    public URI getUri() {
        return this.uri;
    }

    @Override
    public void handleMaintenance(Operation post) {
        Operation maintenanceOp = Operation
                .createPost(getUri())
                .setBodyNoCloning(new MaintenanceRequest())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        post.fail(ex);
                        return;
                    }
                    post.complete();
                });

        setAuthorizationContext(maintenanceOp, getSystemAuthorizationContext());
        handleRequest(maintenanceOp);
    }

    private void handleMaintenanceImpl(Operation op) {
        try {
            long startNanos = System.nanoTime();

            // TODO: need to make sure deadline is far enough, we might not have enough time to expire
            // services under system load

            long deadline = Utils.fromNowMicrosUtc(getMaintenanceIntervalMicros());
            op.nestCompletion((o, e) -> {
                if (e != null) {
                    logSevere(e);
                }

                long endNanos = System.nanoTime();
                setTimeSeriesHistogramStat(STAT_NAME_MAINTENANCE_DOCUMENT_EXPIRATION_DURATION_MICROS,
                        AGGREGATION_TYPE_AVG_MAX,
                        TimeUnit.NANOSECONDS.toMicros(endNanos - startNanos));

                if (hasOption(ServiceOption.INSTRUMENTATION)) {
                    logQueueDepthStat(this.updateQueue, STAT_NAME_FORMAT_UPDATE_QUEUE_DEPTH);
                    logQueueDepthStat(this.queryQueue, STAT_NAME_FORMAT_QUERY_QUEUE_DEPTH);
                }

                op.complete();
            });

            // Need to make sure only one node is expiring services
            // Logic is taken from FactoryService
            expireServicesIfOwner(op, deadline);
        } catch (Exception e) {
            if (getHost().isStopping()) {
                op.fail(new CancellationException("Host is stopping"));
                return;
            }
            logWarning("Attempting recovery due to error: %s", Utils.toString(e));
            op.fail(e);
        }
    }

    private void expireServicesIfOwner(Operation maintOp, long deadline) {
        OperationContext opContext = OperationContext.getOperationContext();
        // Only one node is responsible for expiring services.
        // Ask the runtime if this is the owner node, using the self link as the key.
        Operation selectOwnerOp = maintOp.clone().setExpiration(Utils.fromNowMicrosUtc(
                getHost().getOperationTimeoutMicros()));
        selectOwnerOp.setCompletion((o, e) -> {
            OperationContext.restoreOperationContext(opContext);
            if (e != null) {
                logWarning("owner selection failed: %s", e);
                maintOp.fail(e);
                return;
            }
            NodeSelectorService.SelectOwnerResponse rsp = o
                    .getBody(NodeSelectorService.SelectOwnerResponse.class);
            if (!rsp.isLocalHostOwner) {
                // We do not need to do anything
                maintOp.complete();
                return;
            }

            // TODO: This seems like an overhead, since it might be ok to have duplicate DELETES
            // from multiple nodes when this a node group change
            if (rsp.availableNodeCount > 1) {
                verifyFactoryOwnership(maintOp, deadline);
                return;
            }

            expireServicesAsOwner(maintOp, deadline);
        });

        getHost().selectOwner(ServiceUriPaths.DEFAULT_NODE_SELECTOR, getSelfLink(),
                selectOwnerOp);
    }

    private void expireServicesAsOwner(Operation maintOp, long deadline) {
        try {
            applyDocumentExpirationPolicy(deadline);
        } catch (Throwable e) {
            logWarning("Expiration failed: %s", e);
        }
        maintOp.complete();
    }

    private void verifyFactoryOwnership(Operation maintOp, long deadline) {
        // Local node thinks it's the owner. Let's confirm that
        // majority of the nodes in the node-group
        NodeSelectorService.SelectAndForwardRequest request =
                new NodeSelectorService.SelectAndForwardRequest();
        request.key = getSelfLink();

        Operation broadcastSelectOp = Operation
                .createPost(this, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
                .setReferer(getHost().getUri())
                .setBodyNoCloning(request)
                .setCompletion((op, t) -> {
                    if (t != null) {
                        logWarning("owner selection failed: %s", t);
                        maintOp.fail(t);
                        return;
                    }

                    NodeGroupBroadcastResponse response = op.getBody(NodeGroupBroadcastResponse.class);
                    for (Map.Entry<URI, String> r : response.jsonResponses.entrySet()) {
                        NodeSelectorService.SelectOwnerResponse rsp;
                        try {
                            rsp = Utils.fromJson(r.getValue(), NodeSelectorService.SelectOwnerResponse.class);
                        } catch (Exception e) {
                            logWarning("Exception thrown in de-serializing json response: %s", e);

                            // Ignore if the remote node returned a bad response. Most likely this is because
                            // the remote node is offline and if so, ownership check for the remote node is
                            // irrelevant.
                            continue;
                        }
                        if (rsp == null || rsp.ownerNodeId == null) {
                            logWarning("%s responded with '%s'", r.getKey(), r.getValue());
                        } else if (!rsp.ownerNodeId.equals(getHost().getId())) {
                            logWarning("SelectOwner response from %s does not indicate that " +
                                            "local node %s is the owner for factory %s. JsonResponse: %s",
                                    r.getKey(), getHost().getId(), getSelfLink(), r.getValue());
                            maintOp.complete();
                            return;
                        }
                    }

                    logFine("%s elected as owner for %s. Starting expire ...",
                            getHost().getId(), getSelfLink());
                    expireServicesAsOwner(maintOp, deadline);
                });

        getHost().broadcastRequest(ServiceUriPaths.DEFAULT_NODE_SELECTOR, getSelfLink(), true, broadcastSelectOp);
    }

    private void logQueueDepthStat(RoundRobinOperationQueue queue, String format) {
        Map<String, Integer> sizes = queue.sizesByKey();
        for (Entry<String, Integer> e : sizes.entrySet()) {
            String statName = String.format(format, e.getKey());
            setTimeSeriesStat(statName, AGGREGATION_TYPE_AVG_MAX, e.getValue());
        }
    }

    private void applyDocumentExpirationPolicy(long deadline) throws Exception {
        // TODO: need better solution to expire documents, this can be very slow to have
        // deletion in batches across tables
        int limit = expiredDocumentSearchThreshold;
        long now = Utils.getNowMicrosUtc();
        for (TableDescription tableDescription : this.dao.getPostgresSchemaManager().getTableDescriptions()) {
            if (Utils.getSystemNowMicrosUtc() >= deadline || limit <= 0) {
                break;
            }
            int expired = applyDocumentExpirationPolicyForTable(tableDescription, now, deadline, limit);
            limit -= expired;
        }
    }

    private int applyDocumentExpirationPolicyForTable(TableDescription tableDescription,
            long now, long deadline, int limit) throws Exception {
        int expired = 0;
        String tableName = tableDescription.getTableName();
        String sql = String.format("SELECT data,documentexpirationtimemicros FROM %s WHERE documentexpirationtimemicros BETWEEN 1 AND ? ORDER BY documentexpirationtimemicros LIMIT ?",
                tableName);

        try (Connection conn = this.ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, now);
                stmt.setInt(2, limit);
                // Use cursor
                stmt.setFetchSize(PostgresServiceDocumentDao.FETCH_SIZE);

                try (ResultSet rs = stmt.executeQuery()) {
                    Operation dummyDelete = null;
                    PostgresDocumentStoredFieldVisitor visitor = null;
                    while (rs.next() && Utils.getSystemNowMicrosUtc() < deadline) {
                        if (dummyDelete == null) {
                            dummyDelete = Operation.createDelete(null);
                            visitor = new PostgresDocumentStoredFieldVisitor();
                        } else {
                            visitor.reset();
                        }

                        dao.loadDoc(visitor, rs);

                        String documentSelfLink = visitor.documentSelfLink;
                        ServiceDocument serviceDocument = null;
                        try {
                            serviceDocument = this.dao
                                    .getStateFromPostgresDocument(tableDescription,
                                            visitor, documentSelfLink);
                        } catch (Exception e) {
                            logWarning("Error getting state for %s: %s", documentSelfLink,
                                    e);
                        }

                        expired++;

                        deleteAllDocumentsForSelfLink(conn, tableName, dummyDelete,
                                documentSelfLink, serviceDocument);

                        adjustTimeSeriesStat(STAT_NAME_DOCUMENT_EXPIRATION_COUNT,
                                AGGREGATION_TYPE_SUM,
                                1);
                    }
                }
            }
            conn.commit();
            try {
                conn.setAutoCommit(true);
            } catch (Exception ignore) {
                // Ignore
            }
        }
        return expired;
    }

    private void applyActiveQueries(Operation op, ServiceDocument latestState,
            ServiceDocumentDescription desc) {
        if (this.activeQueries.isEmpty()) {
            return;
        }

        if (op.getAction() == Action.DELETE) {
            // This code path is reached for document expiration, but the last update action for
            // expired documents is usually a PATCH or PUT. Dummy up a document body with a last
            // update action of DELETE for the purpose of providing notifications.
            latestState = Utils.clone(latestState);
            latestState.documentUpdateAction = Action.DELETE.name();
        }

        // set current context from the operation so all active query task notifications carry the
        // same context as the operation that updated the index
        OperationContext.setFrom(op);

        // TODO Optimize. We currently traverse each query independently. We can collapse the queries
        // and evaluate clauses keeping track which clauses applied, then skip any queries accordingly.

        for (Entry<String, QueryTask> taskEntry : this.activeQueries.entrySet()) {
            if (getHost().isStopping()) {
                break;
            }

            QueryTask activeTask = taskEntry.getValue();
            QueryFilter filter = activeTask.querySpec.context.filter;
            if (desc == null) {
                if (!QueryFilterUtils.evaluate(filter, latestState, getHost())) {
                    continue;
                }
            } else {
                if (!filter.evaluate(latestState, desc)) {
                    continue;
                }
            }

            QueryTask patchBody = new QueryTask();
            patchBody.taskInfo.stage = TaskStage.STARTED;
            patchBody.querySpec = null;
            patchBody.results = new ServiceDocumentQueryResult();
            patchBody.results.documentLinks.add(latestState.documentSelfLink);
            if (activeTask.querySpec.options.contains(QueryOption.EXPAND_CONTENT) ||
                    activeTask.querySpec.options.contains(QueryOption.COUNT)) {
                patchBody.results.documents = new HashMap<>();
                patchBody.results.documents.put(latestState.documentSelfLink, latestState);
            }

            // Send PATCH to continuous query task with document that passed the query filter.
            // Any subscribers will get notified with the body containing just this document
            Operation patchOperation = Operation.createPatch(this, activeTask.documentSelfLink)
                    .setBodyNoCloning(patchBody);
            // Set the authorization context to the user who created the continous query.
            OperationContext currentContext = OperationContext.getOperationContext();
            if (activeTask.querySpec.context.subjectLink != null) {
                setAuthorizationContext(patchOperation,
                        getAuthorizationContextForSubject(
                                activeTask.querySpec.context.subjectLink));
            }
            sendRequest(patchOperation);
            OperationContext.restoreOperationContext(currentContext);
        }
    }

}
