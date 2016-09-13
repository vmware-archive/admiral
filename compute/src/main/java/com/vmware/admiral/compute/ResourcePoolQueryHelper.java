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
 * {@code ResourcePoolQueryHelper} performs an asynchronous retrieval of computes participating
 * in a given list of resource pools or all resource pools in the system.
 *
 * <p>The latter is particularly useful if the resource pool of a compute has to be identified -
 * there is no explicit link from the compute to the resource pool (because of the query-driven
 * resource pools) and this helper solves that by providing these links in its result.
 */
public class ResourcePoolQueryHelper {
    private final ServiceHost host;
    private final Collection<String> resourcePoolLinks;
    private Consumer<Query.Builder> additionalQueryClausesProvider;
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
    public ResourcePoolQueryHelper(ServiceHost host) {
        this.host = host;
        this.resourcePoolLinks = new ArrayList<>();
    }

    /**
     * Creates a new instance.
     */
    public ResourcePoolQueryHelper(ServiceHost host, String resourcePoolLink) {
        this.host = host;
        this.resourcePoolLinks = new ArrayList<>();
        this.resourcePoolLinks.add(resourcePoolLink);
    }

    /**
     * Creates a new instance.
     */
    public ResourcePoolQueryHelper(ServiceHost host, Collection<String> resourcePoolLinks) {
        this.host = host;
        this.resourcePoolLinks = resourcePoolLinks;
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
        if (!this.resourcePoolLinks.isEmpty()) {
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
                            task.results.documents.size() < this.resourcePoolLinks.size()) {
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
            if (this.additionalQueryClausesProvider != null) {
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

            this.completionHandler.accept(this.result);
        }).sendWith(this.host);
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
     */
    private void storeComputes(String rpLink, Collection<ComputeState> computes) {
        ResourcePoolData rpData = this.result.resourcesPools.get(rpLink);
        rpData.computeStates.addAll(computes);

        for (ComputeState compute : computes) {
            this.result.computesByLink.put(compute.documentSelfLink, compute);

            Set<String> rpLinks = this.result.rpLinksByComputeLink.get(compute.documentSelfLink);
            if (rpLinks == null) {
                rpLinks = new HashSet<String>();
                this.result.rpLinksByComputeLink.put(compute.documentSelfLink, rpLinks);
            }
            rpLinks.add(rpLink);
        }
    }
}
