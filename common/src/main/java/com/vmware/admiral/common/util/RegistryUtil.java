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

package com.vmware.admiral.common.util;

import static com.vmware.admiral.common.util.QueryUtil.createAnyPropertyClause;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class RegistryUtil {

    /**
     * Do something with each registry available to the given group (and global registries)
     *
     * @param tenantLink
     * @param registryLinksConsumer
     * @param failureConsumer
     */
    public static void forEachRegistry(ServiceHost serviceHost, String tenantLink,
            Consumer<Collection<String>> registryLinksConsumer,
            Consumer<Collection<Throwable>> failureConsumer) {

        BiConsumer<Collection<String>, Collection<Throwable>> consumer = (links, failures) -> {
            if (failures != null && !failures.isEmpty()) {
                failureConsumer.accept(failures);
                return;
            }
            registryLinksConsumer.accept(links);
        };

        List<QueryTask> queryTasks = new ArrayList<QueryTask>();

        if (tenantLink != null) {
            // add query for global groups
            queryTasks.add(buildRegistryQueryByGroup(null));
            // add query for registries of a specific tenant
            queryTasks.add(buildRegistryQueryByGroup(tenantLink));
        } else {
            // add query for all registries if no tenant
            queryTasks.add(buildAllRegistriesQuery());
        }

        queryForRegistries(serviceHost, queryTasks, consumer);
    }

    public static void findRegistriesByHostname(ServiceHost serviceHost, String hostname,
            String tenantLink, BiConsumer<Collection<String>, Collection<Throwable>> consumer) {

        List<QueryTask> queryTasks = new ArrayList<QueryTask>();

        if (tenantLink != null) {
            // add query for global groups
            queryTasks.add(buildRegistryQuery(buildQueryByGroup(null),
                    buildQueryByHostname(hostname)));
            // add query for registries of a specific tenant
            queryTasks.add(buildRegistryQuery(buildQueryByGroup(tenantLink),
                    buildQueryByHostname(hostname)));
        } else {
            // add query for all registries if no tenant
            queryTasks.add(buildRegistryQuery(buildQueryByHostname(hostname)));
        }

        queryForRegistries(serviceHost, queryTasks, consumer);
    }

    private static void queryForRegistries(ServiceHost serviceHost, Collection<QueryTask> queryTasks,
            BiConsumer<Collection<String>, Collection<Throwable>> consumer) {

        List<Operation> queryOperations = new ArrayList<>();
        for (QueryTask queryTask : queryTasks) {
            queryOperations.add(Operation
                    .createPost(UriUtils.buildUri(serviceHost, ServiceUriPaths.CORE_QUERY_TASKS))
                    .setBody(queryTask)
                    .setReferer(serviceHost.getUri()));
        }

        if (!queryOperations.isEmpty()) {
            OperationJoin.create(queryOperations.toArray(new Operation[0]))
                    .setCompletion((ops, failures) -> {
                        if (failures != null) {
                            consumer.accept(null, failures.values());
                            return;
                        }

                        // return one registry link for each address (same registry address can be set in different
                        // entries, in the same or different tenants (in case of system admin search))
                        Map<String, String> registryLinks = new HashMap<>();
                        for (Operation o : ops.values()) {
                            QueryTask result = o.getBody(QueryTask.class);

                            for (Map.Entry<String, Object> document : result.results.documents.entrySet()) {
                                RegistryState registryState = Utils.fromJson(document.getValue(), RegistryState.class);
                                // if same registry is repeated, return it only once
                                if (!registryLinks.containsKey(registryState.address)) {
                                    registryLinks.put(registryState.address, document.getKey());
                                }
                            }
                        }

                        consumer.accept(registryLinks.values(), null);
                    })
                    .sendWith(serviceHost);
        } else {
            // no registry links available
            consumer.accept(Collections.emptyList(), null);
        }
    }

    private static Query buildQueryByHostname(String hostname) {
        return createAnyPropertyClause(String.format("*://%s*", hostname),
                RegistryState.FIELD_NAME_ADDRESS);
    }

    /**
     * Create a query to return all RegistryState links within a group or global RegistryState links
     * if the group is null/empty
     *
     * @param tenantLink
     * @return QueryTask
     */
    private static Query buildQueryByGroup(String tenantLink) {
        return QueryUtil.addTenantGroupAndUserClause(tenantLink);
    }

    /**
     * Create a query to return all RegistryState links
     *
     * @return
     */
    private static QueryTask buildAllRegistriesQuery() {
        return buildRegistryQuery();
    }

    /**
     * Create a query to return all RegistryState links within a group or global RegistryState links
     * if the group is null/empty
     *
     * @param tenantLink
     * @return QueryTask
     */
    private static QueryTask buildRegistryQueryByGroup(String tenantLink) {
        Query groupClause = QueryUtil.addTenantGroupAndUserClause(tenantLink);
        return buildRegistryQuery(groupClause);
    }

    private static QueryTask buildRegistryQuery(Query... additionalClauses) {

        List<Query> clauses = new ArrayList<>();
        if (additionalClauses != null) {
            clauses.addAll(Arrays.asList(additionalClauses));
        }
        Query endpointTypeClause = new Query()
                .setTermPropertyName(RegistryState.FIELD_NAME_ENDPOINT_TYPE)
                .setTermMatchValue(RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE);
        clauses.add(endpointTypeClause);

        Query excludeDisabledClause = new Query()
                .setTermPropertyName(RegistryState.FIELD_NAME_DISABLED)
                .setTermMatchValue(Boolean.TRUE.toString());
        excludeDisabledClause.occurance = Occurance.MUST_NOT_OCCUR;
        clauses.add(excludeDisabledClause);

        QueryTask queryTask = QueryUtil.buildQuery(RegistryState.class, true,
                clauses.toArray(new Query[clauses.size()]));
        QueryUtil.addExpandOption(queryTask);

        return queryTask;
    }
}
