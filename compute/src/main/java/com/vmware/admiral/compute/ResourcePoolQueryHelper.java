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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.ResourcePoolQueryHelper.QueryResult.ResourcePoolData;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * {@code ResourcePoolQueryHelper} aims to simplify the retrieval of computes per resource pool
 * and vice-versa. Resource pools are query-driven and there is no explicit link from the compute
 * to the resource pool it participates in, and this helper hides the complexity of dealing with
 * this.
 *
 * <p>Three types of operations are supported:
 * <ul>
 * <li>Querying all resource pools and their associated computes. Computes without a resource pool
 * are also returned.
 * <li>Querying specific resource pool(s). Only computes participating in the given resource pools
 * are returned.
 * <li>Querying specific computes. The resource pools of the given computes are returned.
 * </ul>
 *
 * <p>In the first two operation types, clients of the helper can restrict the list of computes that
 * are included in the result. This is done by adding additional query clauses to the ones already
 * defined in the resource pool queries
 * (see {@link ResourcePoolQueryHelper#setAdditionalQueryClausesProvider()}).
 */
public class ResourcePoolQueryHelper {
    // input fields
    private final ServiceHost host;
    private Collection<String> resourcePoolLinks;
    private Collection<String> computeLinks;
    private Consumer<Query.Builder> additionalQueryClausesProvider;

    // internal state
    private Consumer<QueryResult> completionHandler;
    private QueryResult result;

    /**
     * Returned query result.
     */
    public static class QueryResult {
        public static class ResourcePoolData {
            public ResourcePoolState resourcePoolState;
            public Set<ComputeState> computeStates;
        }

        public Throwable error;
        public Map<String, ResourcePoolData> resourcesPools = new HashMap<>();
        public Map<String, Set<String>> rpLinksByComputeLink = new HashMap<>();
        public Map<String, ComputeState> computesByLink = new HashMap<>();

        /**
         * Creates a new QueryResult for the given error.
         */
        public static QueryResult forError(Throwable error) {
            QueryResult result = new QueryResult();
            result.error = error;
            return result;
        }
    }

    /**
     * Creates a new instance.
     */
    private ResourcePoolQueryHelper(ServiceHost host) {
        this.host = host;
    }

    public static ResourcePoolQueryHelper create(ServiceHost host) {
        return new ResourcePoolQueryHelper(host);
    }

    public static ResourcePoolQueryHelper createForResourcePool(ServiceHost host,
            String resourcePoolLink) {
        ResourcePoolQueryHelper helper = new ResourcePoolQueryHelper(host);
        helper.resourcePoolLinks = new ArrayList<>();
        helper.resourcePoolLinks.add(resourcePoolLink);
        return helper;
    }

    public static ResourcePoolQueryHelper createForResourcePools(ServiceHost host,
            Collection<String> resourcePoolLinks) {
        ResourcePoolQueryHelper helper = new ResourcePoolQueryHelper(host);
        helper.resourcePoolLinks = new ArrayList<>(resourcePoolLinks);
        return helper;
    }

    public static ResourcePoolQueryHelper createForComputes(ServiceHost host,
            Collection<String> computeLinks) {
        ResourcePoolQueryHelper helper = new ResourcePoolQueryHelper(host);
        helper.computeLinks = new ArrayList<>(computeLinks);
        return helper;
    }

    /**
     * Allows clients to dynamically add query clauses for narrowing down the list of returned
     * computes.
     */
    public void setAdditionalQueryClausesProvider(Consumer<Query.Builder> provider) {
        this.additionalQueryClausesProvider = provider;
    }

    /**
     * Perform the actual retrieval and notifies the client through the given completionHandler.
     */
    public void query(Consumer<QueryResult> completionHandler) {
        this.completionHandler = completionHandler;
        this.result = new QueryResult();

        // start by retrieving the requested resource pools
        retrieveResourcePools();
    }

    /**
     * Retrieves the requested resource pools documents.
     */
    private void retrieveResourcePools() {
        Query.Builder queryBuilder = Query.Builder.create()
                .addKindFieldClause(ResourcePoolState.class);
        if (this.resourcePoolLinks != null && !this.resourcePoolLinks.isEmpty()) {
            queryBuilder.addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, this.resourcePoolLinks);
        }

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(queryBuilder.build())
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        host.sendRequest(Operation.createPost(host, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setReferer(this.host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        completionHandler.accept(QueryResult.forError(e));
                        return;
                    }

                    QueryTask task = o.getBody(QueryTask.class);
                    if (task.results.documents == null ||
                            (this.resourcePoolLinks != null && task.results.documents.size() <
                                    this.resourcePoolLinks.size())) {
                        completionHandler.accept(QueryResult.forError(new IllegalStateException(
                                "Couldn't retrieve the requested resource pools")));
                        return;
                    }

                    storeResourcePools(task.results.documents.values().stream()
                            .map(json -> Utils.fromJson(json, ResourcePoolState.class))
                            .collect(Collectors.toSet()));

                    // continue by executing the resource pool queries
                    executeRpQueries();
                }));
    }

    /**
     * Executes the resource pool queries in parallel and then collects the result.
     */
    private void executeRpQueries() {
        List<Operation> queryOperations = new ArrayList<>(this.result.resourcesPools.size());
        Map<Long, String> rpLinkByOperationId = new HashMap<>();
        for (ResourcePoolData rpData : this.result.resourcesPools.values()) {
            String rpLink = rpData.resourcePoolState.documentSelfLink;
            Query rpQuery = rpData.resourcePoolState.query;

            Query.Builder queryBuilder = Query.Builder.create().addClause(rpQuery);
            if (this.computeLinks != null && !this.computeLinks.isEmpty()) {
                queryBuilder.addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, this.computeLinks);
            } else if (this.additionalQueryClausesProvider != null) {
                this.additionalQueryClausesProvider.accept(queryBuilder);
            }

            QueryTask queryTask = QueryTask.Builder.createDirectTask()
                    .setQuery(queryBuilder.build())
                    .addOption(QueryOption.EXPAND_CONTENT)
                    .build();

            Operation queryOperation =
                    Operation.createPost(host, ServiceUriPaths.CORE_QUERY_TASKS)
                    .setBody(queryTask)
                    .setReferer(this.host.getUri());
            rpLinkByOperationId.put(queryOperation.getId(), rpLink);
            queryOperations.add(queryOperation);
        }

        OperationJoin.create(queryOperations).setCompletion((ops, exs) -> {
            if (exs != null) {
                this.completionHandler.accept(QueryResult.forError(exs.values().iterator().next()));
                return;
            }
            for (Operation op : ops.values()) {
                String rpLink = rpLinkByOperationId.get(op.getId());

                QueryTask task = op.getBody(QueryTask.class);
                if (task.results.documents == null) {
                    continue;
                }

                storeComputes(rpLink, task.results.documents.values().stream()
                        .map(json -> Utils.fromJson(json, ComputeState.class))
                        .collect(Collectors.toSet()));
            }

            // last step is to find computes that are not part of any resource pool
            findComputesWithoutPool();
        }).sendWith(this.host);
    }

    /**
     * Finds computes that are not part of any resource pool.
     *
     * - If we have input resource pool(s), don't do anything.
     * - If we have input computeLinks, check them.
     * - Otherwise, get all computes and check which are missing in the already collected result.
     */
    private void findComputesWithoutPool() {
        if (this.resourcePoolLinks != null && !this.resourcePoolLinks.isEmpty()) {
            this.completionHandler.accept(this.result);
            return;

        }

        if (this.computeLinks != null && !this.computeLinks.isEmpty()) {
            handleMissingComputes(this.computeLinks);
            return;
        }

        // query for all computes (without expanding the documents)
        Query.Builder queryBuilder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class);
        if (this.additionalQueryClausesProvider != null) {
            this.additionalQueryClausesProvider.accept(queryBuilder);
        }
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(queryBuilder.build())
                .build();

        host.sendRequest(Operation
                .createPost(host, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setReferer(this.host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.completionHandler.accept(QueryResult.forError(e));
                        return;
                    }

                    QueryTask task = o.getBody(QueryTask.class);
                    handleMissingComputes(task.results.documentLinks);
                }));
    }

    /**
     * With the given compute links, finds which ones are not already retrieved as part of a
     * resource pool, and loads the corresponding ComputeState documents into the result.
     */
    private void handleMissingComputes(Collection<String> allComputeLinks) {
        Collection<String> missingComputeLinks = new HashSet<>(allComputeLinks);
        missingComputeLinks.removeAll(this.result.computesByLink.keySet());
        if (missingComputeLinks.isEmpty()) {
            this.completionHandler.accept(this.result);
            return;
        }

        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, missingComputeLinks)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();

        host.sendRequest(Operation
                .createPost(host, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setReferer(this.host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.completionHandler.accept(QueryResult.forError(e));
                        return;
                    }

                    QueryTask task = o.getBody(QueryTask.class);
                    if (task.results != null && task.results.documents != null) {
                        storeComputes(null, task.results.documents.values().stream()
                                .map(json -> Utils.fromJson(json, ComputeState.class))
                                .collect(Collectors.toSet()));
                    }

                    this.completionHandler.accept(this.result);
                }));
    }

    /**
     * Stores the retrieved resource pool states into the QueryResult instance.
     */
    private void storeResourcePools(Collection<ResourcePoolState> resourcePools) {
        for (ResourcePoolState rp : resourcePools) {
            ResourcePoolData rpData = new ResourcePoolData();
            rpData.resourcePoolState = rp;
            rpData.computeStates = new HashSet<>();
            this.result.resourcesPools.put(rp.documentSelfLink, rpData);
        }
    }

    /**
     * Stores the retrieved compute states into the QueryResult instance.
     * The rpLink may be null in case the given computes do not fall into any resource pool.
     */
    private void storeComputes(String rpLink, Collection<ComputeState> computes) {
        if (rpLink != null) {
            ResourcePoolData rpData = this.result.resourcesPools.get(rpLink);
            rpData.computeStates.addAll(computes);
        }

        for (ComputeState compute : computes) {
            this.result.computesByLink.put(compute.documentSelfLink, compute);

            // make sure rpLinksByComputeLink has an empty item even for computes with no rp link
            Set<String> rpLinks = this.result.rpLinksByComputeLink.get(compute.documentSelfLink);
            if (rpLinks == null) {
                rpLinks = new HashSet<String>();
                this.result.rpLinksByComputeLink.put(compute.documentSelfLink, rpLinks);
            }

            if (rpLink != null) {
                rpLinks.add(rpLink);
            }
        }
    }
}
