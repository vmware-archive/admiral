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

package com.vmware.photon.controller.model.query;

import static java.util.stream.Collector.Characteristics.IDENTITY_FINISH;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Query utilities.
 */
public class QueryUtils {

    public static final String MAX_RESULT_LIMIT_PROPERTY = UriPaths.PROPERTY_PREFIX
            + "query.maxResultLimit";
    public static final int DEFAULT_MAX_RESULT_LIMIT = 10000;
    private static final int MAX_RESULT_LIMIT = Integer
            .getInteger(MAX_RESULT_LIMIT_PROPERTY, DEFAULT_MAX_RESULT_LIMIT);

    public static final String DEFAULT_RESULT_LIMIT_PROPERTY = UriPaths.PROPERTY_PREFIX
            + "query.defaultResultLimit";
    public static final int DEFAULT_RESULT_LIMIT = Integer
            .getInteger(DEFAULT_RESULT_LIMIT_PROPERTY, 100);

    public static final long MINUTE_IN_MICROS = TimeUnit.MINUTES.toMicros(1);
    public static final long TEN_MINUTES_IN_MICROS = TimeUnit.MINUTES.toMicros(10);

    /**
     * Executes the given query task on a Cluster.
     *
     * @param service
     *            The service executing the query task.
     * @param queryTask
     *            The query task.
     * @param cluster
     *            The cluster, the query runs against.
     */
    public static DeferredResult<QueryTask> startQueryTask(Service service, QueryTask queryTask,
            ServiceTypeCluster cluster) {
        // We don't want any unbounded queries. By default, we cap the query results to 10000.
        if (queryTask.querySpec.resultLimit == null) {
            service.getHost().log(Level.WARNING,
                    "No result limit set on the query: %s. Defaulting to %d",
                    Utils.toJson(queryTask), MAX_RESULT_LIMIT);
            queryTask.querySpec.options.add(QueryOption.TOP_RESULTS);
            queryTask.querySpec.resultLimit = MAX_RESULT_LIMIT;
        }
        if (queryTask.documentExpirationTimeMicros == 0) {
            queryTask.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + QueryUtils.TEN_MINUTES_IN_MICROS;
        }

        return service.sendWithDeferredResult(Operation
                .createPost(UriUtils.buildUri(
                        ClusterUtil.getClusterUri(service.getHost(), cluster),
                        ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(queryTask).setConnectionSharing(true), QueryTask.class);
    }

    /**
     * Executes the given query task.
     *
     * @param service
     *            The service executing the query task.
     * @param queryTask
     *            The query task.
     */
    public static DeferredResult<QueryTask> startQueryTask(Service service, QueryTask queryTask) {
        return startQueryTask(service, queryTask, null);
    }

    /**
     * Add {@code endpointLink} constraint to passed query builder depending on document class.
     */
    public static Query.Builder addEndpointLink(
            Query.Builder qBuilder,
            Class<? extends ServiceDocument> stateClass,
            String endpointLink) {

        if (endpointLink == null || endpointLink.isEmpty()) {
            return qBuilder;
        }

        if (PhotonModelUtils.ENDPOINT_LINK_EXPLICIT_SUPPORT.contains(stateClass)) {
            return qBuilder.addFieldClause(
                    PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK,
                    endpointLink);
        }

        if (PhotonModelUtils.ENDPOINT_LINK_CUSTOM_PROP_SUPPORT.contains(stateClass)) {
            return qBuilder.addCompositeFieldClause(
                    ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                    PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK,
                    endpointLink);
        }

        return qBuilder;
    }

    /**
     * Add {@code tenantLinks} constraint to passed query builder, if present.
     */
    public static Query.Builder addTenantLinks(Query.Builder qBuilder, List<String> tenantLinks) {
        if (tenantLinks != null) {
            // all given tenant links must be present in the document
            tenantLinks.forEach(link -> qBuilder
                    .addCollectionItemClause(ResourceState.FIELD_NAME_TENANT_LINKS, link));
        }
        return qBuilder;
    }

    /**
     * Query strategy template.
     *
     * @see {#link {@link QueryTop}
     * @see {#link {@link QueryByPages}
     */
    public abstract static class QueryTemplate<T extends ServiceDocument>
            implements QueryStrategy<T> {

        /**
         * Wait\Block for query logic to complete.
         * <p>
         * <b>Note</b>: Use with care, for example within tests.
         */
        public static <S> S waitToComplete(DeferredResult<S> dr) {
            return ((CompletableFuture<S>) dr.toCompletionStage()).join();
        }

        protected final ServiceHost host;
        protected final Query query;
        protected final Class<T> documentClass;
        protected final List<String> tenantLinks;

        protected URI referer;
        protected ServiceTypeCluster cluster;

        protected Level level = Level.FINE;
        protected String msg;

        /**
         * Default constructor.
         * <p>
         * Note: The client is off-loaded from setting the {@code tenantLinks} to the query.
         *
         * @param host
         *            The host initiating the query.
         * @param query
         *            The query criteria.
         * @param documentClass
         *            The class of documents to query for.
         * @param tenantLinks
         *            The scope of the query.
         */
        public QueryTemplate(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks) {

            this(host, query, documentClass, tenantLinks, null /* endpointLink */);
        }

        /**
         * Default constructor.
         * <p>
         * Note: The client is off-loaded from setting the {@code tenantLinks} and
         * {@code ednpointLink} to the query.
         *
         * @param host
         *            The host initiating the query.
         * @param query
         *            The query criteria.
         * @param documentClass
         *            The class of documents to query for.
         * @param tenantLinks
         *            The scope of the query.
         * @param endpointLink
         *            The scope of the query.
         */
        public QueryTemplate(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks,
                String endpointLink) {

            this.host = host;
            this.documentClass = documentClass;
            this.tenantLinks = tenantLinks;

            setReferer(this.host.getUri());

            {
                // Wrap original query...
                Query.Builder qBuilder = Query.Builder.create().addClause(query);
                // ...and extend with TENANT_LINKS
                QueryUtils.addTenantLinks(qBuilder, this.tenantLinks);
                // ...and extend with ENDPOINT_LINK
                QueryUtils.addEndpointLink(qBuilder, this.documentClass, endpointLink);

                this.query = qBuilder.build();
            }

            this.msg = getClass().getSimpleName() + " for " + documentClass.getSimpleName() + "s";
        }

        /**
         * Get the maximum number of results to return. The value is set to
         * {@code QueryTask.Builder.setResultLimit(int)} by {@link #newQueryTaskBuilder()} while
         * creating the {@link QueryTask}.
         */
        protected abstract int getResultLimit();

        /**
         * Set custom referrer to use for REST operations.
         * <p>
         * Default value, if not set, is {@code host.getUri()}. Callers are recommended to set more
         * pertinent value for better traceability, e.g {@link Service#getUri()}.
         */
        public void setReferer(URI referer) {
            AssertUtil.assertNotNull(referer, "'referer' must be set.");
            this.referer = referer;
        }

        /**
         * Set the cluster URI to query against.
         * <p>
         * Default value, if not set, is {@code null}.
         *
         * @see ClusterUtil#getClusterUri(ServiceHost, ServiceTypeCluster)
         */
        public void setClusterType(ServiceTypeCluster cluster) {
            this.cluster = cluster;
        }

        /**
         * Each query strategy should provide its own {@link QueryTask} processing logic.
         *
         * @param queryTask
         *            The QueryTask instance as returned by post-QueryTask-operation.
         */
        @SuppressWarnings("rawtypes")
        protected abstract DeferredResult<Void> handleQueryTask(
                QueryTask queryTask, Consumer resultConsumer);

        /*
         * (non-Javadoc)
         *
         * @see
         * com.vmware.photon.controller.model.tasks.QueryStrategy#queryDocuments(java.util.function.
         * Consumer)
         */
        @Override
        public DeferredResult<Void> queryDocuments(Consumer<T> documentConsumer) {

            QueryTask.Builder queryTaskBuilder = newQueryTaskBuilder()
                    .addOption(QueryOption.EXPAND_CONTENT);

            return queryImpl(queryTaskBuilder, documentConsumer);
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * com.vmware.photon.controller.model.tasks.QueryStrategy#queryLinks(java.util.function.
         * Consumer)
         */
        @Override
        public DeferredResult<Void> queryLinks(Consumer<String> linkConsumer) {

            QueryTask.Builder queryTaskBuilder = newQueryTaskBuilder();

            return queryImpl(queryTaskBuilder, linkConsumer);
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * com.vmware.photon.controller.model.tasks.QueryStrategy#collectDocuments(java.util.stream.
         * Collector)
         */
        @Override
        @SuppressWarnings("unchecked")
        public <R, A> DeferredResult<R> collectDocuments(Collector<T, A, R> collector) {

            A container = collector.supplier().get();
            BiConsumer<A, T> accumulator = collector.accumulator();

            return queryDocuments(referrerDoc -> {
                accumulator.accept(container, referrerDoc);
            }).thenApply(ignore -> collector.characteristics().contains(IDENTITY_FINISH)
                    ? (R) container
                    : collector.finisher().apply(container));
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * com.vmware.photon.controller.model.tasks.QueryStrategy#collectLinks(java.util.stream.
         * Collector)
         */
        @Override
        @SuppressWarnings("unchecked")
        public <R, A> DeferredResult<R> collectLinks(Collector<String, A, R> collector) {

            A container = collector.supplier().get();
            BiConsumer<A, String> accumulator = collector.accumulator();

            return queryLinks(referrerLink -> {
                accumulator.accept(container, referrerLink);
            }).thenApply(ignore -> collector.characteristics().contains(IDENTITY_FINISH)
                    ? (R) container
                    : collector.finisher().apply(container));
        }

        /**
         * Descendants might override this method to customize the default query task build logic.
         * For example add option such as {@link QueryOption#TOP_RESULTS}.
         */

        protected QueryTask.Builder newQueryTaskBuilder() {
            return QueryTask.Builder.createDirectTask()
                    .setQuery(this.query)
                    .setResultLimit(getResultLimit());
        }

        @SuppressWarnings("rawtypes")
        private DeferredResult<Void> queryImpl(
                QueryTask.Builder queryTaskBuilder,
                Consumer resultConsumer) {

            // Prepare QueryTask
            final QueryTask queryTask = queryTaskBuilder.build();
            queryTask.tenantLinks = this.tenantLinks;
            queryTask.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + QueryUtils.TEN_MINUTES_IN_MICROS;

            // Build PhM URI to query against
            final URI uri = UriUtils.buildUri(
                    ClusterUtil.getClusterUri(this.host, this.cluster),
                    ServiceUriPaths.CORE_LOCAL_QUERY_TASKS);

            // Prepare 'query-task' create/POST request
            final Operation createQueryTaskOp = Operation
                    .createPost(uri)
                    .setReferer(this.referer)
                    .setBody(queryTask);

            this.host.log(this.level,
                    this.msg + ": STARTED with QT = " + Utils.toJsonHtml(queryTask));

            // Initiate the query
            return this.host.sendWithDeferredResult(createQueryTaskOp, QueryTask.class)
                    // Delegate to descendant to actually do QT processing
                    .thenCompose(qt -> handleQueryTask(qt, resultConsumer));
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        protected QueryTask consumeQueryTask(QueryTask qt, Consumer resultConsumer) {

            this.host.log(this.level, "%s: PROCESS %s docs",
                    this.msg, qt.results.documentCount);

            if (qt.results.documentCount == 0) {
                return qt;
            }

            final Stream resultsStream;
            if (qt.results.documents != null && !qt.results.documents.isEmpty()) {
                // Get document states as Stream<T>, if QueryOption.EXPAND_CONTENT
                resultsStream = qt.results.documents.values().stream()
                        .map(json -> Utils.fromJson(json, this.documentClass));
            } else {
                // Get document links as Stream<String>
                resultsStream = qt.results.documentLinks.stream();
            }
            // Delegate to passed callback one-by-one
            resultsStream.forEach(resultConsumer);

            return qt;
        }
    }

    /**
     * Query TOP documents/links which satisfy passed criteria.
     */
    public static class QueryTop<T extends ServiceDocument> extends QueryTemplate<T> {

        private int maxResultsLimit = MAX_RESULT_LIMIT;

        public QueryTop(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks) {
            super(host, query, documentClass, tenantLinks);
        }

        public QueryTop(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks,
                String endpointLink) {
            super(host, query, documentClass, tenantLinks, endpointLink);
        }

        /**
         * Configure the number of top results.
         * <p>
         * If not explicitly specified use the system default.
         */
        public QueryTop<T> setMaxResultsLimit(int maxResultsLimit) {
            this.maxResultsLimit = maxResultsLimit;
            return this;
        }

        /**
         * Return the number of top results.
         */
        @Override
        protected int getResultLimit() {
            return this.maxResultsLimit;
        }

        /**
         * Add {@link QueryOption#TOP_RESULTS} since this is 'query TOP documents/links'.
         */
        @Override
        protected QueryTask.Builder newQueryTaskBuilder() {
            return super.newQueryTaskBuilder().addOption(QueryOption.TOP_RESULTS);
        }

        @Override
        @SuppressWarnings({ "rawtypes" })
        protected DeferredResult<Void> handleQueryTask(
                QueryTask queryTask,
                Consumer resultConsumer) {

            return DeferredResult.completed(queryTask)
                    // Handle TOP results
                    .thenAccept(qt -> consumeQueryTask(qt, resultConsumer));
        }
    }

    /**
     * Query all documents/links which satisfy passed query criteria. The results are processed
     * page-by-page.
     */
    public static class QueryByPages<T extends ServiceDocument> extends QueryTemplate<T> {

        /**
         * Value is {@code photon-model.QueryByPages.maxPageSize}.
         */
        public static final String PROPERTY_NAME_MAX_PAGE_SIZE = UriPaths.PROPERTY_PREFIX
                + QueryByPages.class.getSimpleName()
                + ".maxPageSize";
        public static final int DEFAULT_MAX_PAGE_SIZE = DEFAULT_RESULT_LIMIT;

        /**
         * Get system default max number of documents per page from
         * {@link #PROPERTY_NAME_MAX_PAGE_SIZE} property. If not specified fallback to
         * {@link #DEFAULT_MAX_PAGE_SIZE}.
         */
        public static int getDefaultMaxPageSize() {
            return Integer.getInteger(PROPERTY_NAME_MAX_PAGE_SIZE, DEFAULT_MAX_PAGE_SIZE);
        }

        private int maxPageSize = getDefaultMaxPageSize();

        public QueryByPages(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks) {

            super(host, query, documentClass, tenantLinks);
        }

        public QueryByPages(ServiceHost host,
                Query query,
                Class<T> documentClass,
                List<String> tenantLinks,
                String endpointLink) {

            super(host, query, documentClass, tenantLinks, endpointLink);
        }

        /**
         * Configure the number of max documents per page.
         * <p>
         * If not explicitly specified use the system default as returned by
         * {@link #getDefaultMaxPageSize()}.
         */
        public QueryByPages<T> setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
            return this;
        }

        /**
         * Return the number of max documents per page.
         */
        @Override
        protected int getResultLimit() {
            return this.maxPageSize;
        }

        /**
         * Get the next page of results and pass them to resultConsumer.
         */
        @Override
        @SuppressWarnings({ "rawtypes" })
        protected DeferredResult<Void> handleQueryTask(
                QueryTask queryTask,
                Consumer resultConsumer) {

            final String pageLink = queryTask.results.nextPageLink;

            if (pageLink == null) {
                this.host.log(this.level, this.msg + ": FINISHED");

                return DeferredResult.completed(null);
            }

            this.host.log(this.level, this.msg + ": PAGE %s", pageLink);

            Operation getQueryTaskOp = Operation
                    .createGet(this.host, pageLink)
                    .setReferer(super.referer);

            return this.host.sendWithDeferredResult(getQueryTaskOp, QueryTask.class)
                    // Handle current page of results
                    .thenApply(qt -> consumeQueryTask(qt, resultConsumer))
                    // Handle next page of results
                    .thenCompose(qt -> handleQueryTask(qt, resultConsumer));
        }
    }

    /**
     * Query for all "referrer" documents which refer a "referred" document.
     *
     * @param referredDocumentSelfLink
     *            All referrers point to this document.
     * @param referrerClass
     *            The class of referrers that we want to query for.
     * @param referrerLinkFieldName
     *            The name of the "foreign key link" field of the referrer class.
     */
    public static Query queryForReferrers(
            String referredDocumentSelfLink,
            Class<? extends ServiceDocument> referrerClass,
            String referrerLinkFieldName) {

        return Query.Builder.create()
                .addKindFieldClause(referrerClass)
                .addFieldClause(referrerLinkFieldName, referredDocumentSelfLink)
                .build();
    }

}