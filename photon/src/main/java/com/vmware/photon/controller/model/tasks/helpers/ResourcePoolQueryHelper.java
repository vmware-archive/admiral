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

package com.vmware.photon.controller.model.tasks.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.helpers.ResourcePoolQueryHelper.QueryResult.ResourcePoolData;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask.Query;

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
 * (see {@link ResourcePoolQueryHelper#setAdditionalQueryClausesProvider(Consumer)}).
 *
 * <p>By default computes are not expanded and values in {@link QueryResult#computesByLink} are
 * {@code null}. Use {@link ResourcePoolQueryHelper#setExpandComputes(boolean)} to change this.
 */
public class ResourcePoolQueryHelper {
    private static final int PAGE_SIZE = Integer
            .getInteger(UriPaths.PROPERTY_PREFIX + "rp.query.helper.page.size", 1024);

    // input fields
    private final ServiceHost host;
    private Collection<String> resourcePoolLinks;
    private Collection<String> computeLinks;
    private boolean expandComputes = false;
    private Consumer<Query.Builder> additionalQueryClausesProvider;
    private Consumer<Query.Builder> additionalResourcePoolQueryClausesProvider;

    // internal state
    private QueryResult result;

    /**
     * Returned query result.
     */
    public static class QueryResult {
        public static class ResourcePoolData {
            public ResourcePoolState resourcePoolState;
            public Set<String> computeStateLinks;
        }

        public Throwable error;
        public Map<String, ResourcePoolData> resourcesPools = new HashMap<>();
        public Map<String, Set<String>> rpLinksByComputeLink = new HashMap<>();
        public Map<String, ComputeState> computesByLink = new HashMap<>();

        /**
         * Helper method returning Computes per specific Resource Pool. It eases manipulations over
         * {@link #resourcesPools}, {@link ResourcePoolData#computeStateLinks} and
         * {@link computesByLink}.
         */
        public Stream<ComputeState> getComputesByResPool(String resPoolLink) {
            if (!this.resourcesPools.containsKey(resPoolLink) || this.computesByLink.isEmpty()) {
                // Either RP does not exist OR no Computes are loaded at all
                return Stream.empty();
            }

            // Get compute links per RP
            Set<String> computeLinksPerRP = this.resourcesPools.get(resPoolLink).computeStateLinks;

            if (computeLinksPerRP == null) {
                // No Computes are assigned per this RP
                return Stream.empty();
            }

            return computeLinksPerRP.stream()
                    // Get Computes by their links
                    .map(this.computesByLink::get)
                    // And filter 'null' values
                    .filter(Objects::nonNull);
        }

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
     * Allows clients to dynamically add query clauses for narrowing down the list of returned
     * ResourcePools.
     */
    public void setAdditionalResourcePoolQueryClausesProvider(Consumer<Query.Builder> provider) {
        this.additionalResourcePoolQueryClausesProvider = provider;
    }

    /**
     * Whether to expand {@link ComputeState} documents or not. If {@code false}, values in
     * {@link QueryResult#computesByLink} are {@code null}.
     */
    public void setExpandComputes(boolean expandComputes) {
        this.expandComputes = expandComputes;
    }

    /**
     * Perform the actual retrieval and returns to the client DeferredResult with actual QueryResult.
     */
    public DeferredResult<QueryResult> query() {

        this.result = new QueryResult();

        // start by retrieving the requested resource pools
        return retrieveResourcePools()
                .thenCompose(ignore -> executeRpQueries())
                .thenCompose(ignore -> findComputesWithoutPool())
                .thenCompose(this::handleMissingComputes)
                .handle((ignore, exc) -> exc != null ? QueryResult.forError(exc) : this.result);
    }

    /**
     * Perform the actual retrieval and notifies the client through the given completionHandler.
     */
    public void query(Consumer<QueryResult> completionHandler) {
        query().thenAccept(completionHandler);
    }

    /**
     * Retrieves the requested resource pools documents.
     */
    private DeferredResult<Void> retrieveResourcePools() {
        Query.Builder queryBuilder = Query.Builder.create()
                .addKindFieldClause(ResourcePoolState.class);
        if (this.resourcePoolLinks != null && !this.resourcePoolLinks.isEmpty()) {
            queryBuilder.addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, this.resourcePoolLinks);
        } else if (this.additionalResourcePoolQueryClausesProvider != null) {
            this.additionalResourcePoolQueryClausesProvider.accept(queryBuilder);
        }

        return new QueryByPages<>(this.host, queryBuilder.build(), ResourcePoolState.class, null)
                .setMaxPageSize(PAGE_SIZE)
                .queryDocuments(rp -> storeResourcePool(rp));
    }

    /**
     * Executes the resource pool queries in parallel and then collects the result.
     */
    private DeferredResult<Void> executeRpQueries() {
        List<DeferredResult<Void>> rpQueryDRs = new ArrayList<>(this.result.resourcesPools.size());
        Map<String, Map<String, ComputeState>> computeMapByRpLink = new ConcurrentHashMap<>();
        for (ResourcePoolData rpData : this.result.resourcesPools.values()) {
            String rpLink = rpData.resourcePoolState.documentSelfLink;
            Query rpQuery = rpData.resourcePoolState.query;

            Query.Builder queryBuilder = Query.Builder.create().addClause(rpQuery);
            if (this.computeLinks != null && !this.computeLinks.isEmpty()) {
                queryBuilder.addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, this.computeLinks);
            } else if (this.additionalQueryClausesProvider != null) {
                this.additionalQueryClausesProvider.accept(queryBuilder);
            }

            QueryByPages<ComputeState> computeQuery =
                    new QueryByPages<>(this.host, queryBuilder.build(), ComputeState.class, null)
                            .setMaxPageSize(PAGE_SIZE);
            DeferredResult<Map<String, ComputeState>> rpQueryDR;
            if (this.expandComputes) {
                rpQueryDR = computeQuery
                        .collectDocuments(Collectors.toMap(cs -> cs.documentSelfLink, cs -> cs));
            } else {
                // manually collect links since Collectors.toMap() does not allow null values
                Map<String, ComputeState> computesMap = new HashMap<>();
                rpQueryDR = computeQuery
                        .queryLinks(csLink -> computesMap.put(csLink, null))
                        .thenApply(ignore -> computesMap);
            }

            rpQueryDRs.add(rpQueryDR
                    .thenAccept(computesMap -> computeMapByRpLink.put(rpLink, computesMap)));
        }

        return DeferredResult.allOf(rpQueryDRs)
                .thenAccept(ignore -> computeMapByRpLink.forEach(this::storeComputes))
                .thenApply(ignore -> (Void)null);
    }

    /**
     * Finds computes that are not part of any resource pool.
     *
     * - If we have input resource pool(s), don't do anything.
     * - If we have input computeLinks, check them.
     * - Otherwise, get all computes and check which are missing in the already collected result.
     */
    private DeferredResult<Collection<String>> findComputesWithoutPool() {
        if (this.resourcePoolLinks != null && !this.resourcePoolLinks.isEmpty()) {
            return DeferredResult.completed(Collections.emptyList());
        }

        if (this.computeLinks != null && !this.computeLinks.isEmpty()) {
            // remove RPs without computes
            this.result.resourcesPools = this.result.resourcesPools.entrySet().stream()
                    .filter(e -> !e.getValue().computeStateLinks.isEmpty())
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

            return DeferredResult.completed(this.computeLinks);
        }

        // query for all computes (without expanding the documents)
        Query.Builder queryBuilder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class);
        if (this.additionalQueryClausesProvider != null) {
            this.additionalQueryClausesProvider.accept(queryBuilder);
        }

        return new QueryByPages<>(this.host, queryBuilder.build(), ComputeState.class, null)
                .setMaxPageSize(PAGE_SIZE)
                .collectLinks(Collectors.toCollection(ArrayList::new));
    }

    /**
     * With the given compute links, finds which ones are not already retrieved as part of a
     * resource pool, and loads the corresponding ComputeState documents into the result.
     */
    private DeferredResult<Void> handleMissingComputes(Collection<String> allComputeLinks) {
        Collection<String> missingComputeLinks = new HashSet<>(allComputeLinks);
        missingComputeLinks.removeAll(this.result.computesByLink.keySet());
        if (missingComputeLinks.isEmpty()) {
            return DeferredResult.completed(null);
        }

        Query query = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, missingComputeLinks)
                .build();
        return new QueryByPages<>(this.host, query, ComputeState.class, null)
                .setMaxPageSize(PAGE_SIZE)
                .collectDocuments(Collectors.toMap(cs -> cs.documentSelfLink, cs -> cs))
                .thenAccept(computesMap -> storeComputes(null, computesMap));
    }

    /**
     * Stores the retrieved resource pool states into the QueryResult instance.
     */
    private void storeResourcePool(ResourcePoolState rp) {
        ResourcePoolData rpData = new ResourcePoolData();
        rpData.resourcePoolState = rp;
        rpData.computeStateLinks = new HashSet<>();
        this.result.resourcesPools.put(rp.documentSelfLink, rpData);
    }

    /**
     * Stores the retrieved compute states into the QueryResult instance.
     * The rpLink may be null in case the given computes do not fall into any resource pool.
     */
    private void storeComputes(String rpLink, Map<String, ComputeState> computes) {
        if (rpLink != null) {
            ResourcePoolData rpData = this.result.resourcesPools.get(rpLink);
            rpData.computeStateLinks.addAll(computes.keySet());
        }

        for (Map.Entry<String, ComputeState> computeEntry : computes.entrySet()) {
            String computeLink = computeEntry.getKey();
            ComputeState compute = computeEntry.getValue();

            this.result.computesByLink.put(computeLink, compute);

            // make sure rpLinksByComputeLink has an empty item even for computes with no rp link
            Set<String> rpLinks = this.result.rpLinksByComputeLink.get(computeLink);
            if (rpLinks == null) {
                rpLinks = new HashSet<String>();
                this.result.rpLinksByComputeLink.put(computeLink, rpLinks);
            }

            if (rpLink != null) {
                rpLinks.add(rpLink);
            }
        }
    }
}
