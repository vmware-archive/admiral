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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentState;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class EnvironmentQueryUtils {

    public static class EnvEntry {
        public String rpLink;
        public EndpointState endpoint;
        public Set<String> envLinks = new HashSet<>();

        public EnvEntry(String rpLink, EndpointState endpoint) {
            this.rpLink = rpLink;
            this.endpoint = endpoint;
        }

        void addEnvLink(String link) {
            this.envLinks.add(link);
        }
    }

    public static void queryEnvironments(ServiceHost host, URI referer,
            Set<String> resourcePoolsLinks, String endpointLink, List<String> tenantLinks,
            List<String> environmentLinks, BiConsumer<List<EnvEntry>, Throwable> consumer) {
        Builder builder = Query.Builder.create()
                .addKindFieldClause(ResourcePoolState.class)
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, resourcePoolsLinks);
        if (endpointLink != null) {
            builder.addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                    ComputeProperties.ENDPOINT_LINK_PROP_NAME, endpointLink);
        } else {
            builder.addFieldClause(
                    QuerySpecification.buildCompositeFieldName(
                            ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeProperties.ENDPOINT_LINK_PROP_NAME),
                    "*", MatchType.WILDCARD);
        }

        if (tenantLinks == null || tenantLinks.isEmpty()) {
            builder.addClause(QueryUtil.addTenantClause(tenantLinks));
        }

        QueryUtils.QueryByPages<ResourcePoolState> query = new QueryUtils.QueryByPages<>(host,
                builder.build(), ResourcePoolState.class, QueryUtil.getTenantLinks(tenantLinks));

        final Map<String, List<EnvEntry>> entriesPerEndpoint = new HashMap<>();
        query.queryDocuments(rp -> {
            String epl = rp.customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME);

            entriesPerEndpoint.computeIfAbsent(epl, k -> new ArrayList<>())
                    .add(new EnvEntry(rp.documentSelfLink, null));
        }).thenCompose(v -> DeferredResult.allOf(entriesPerEndpoint.keySet().stream()
                .map(epl -> host.sendWithDeferredResult(
                        Operation.createGet(host, epl).setReferer(referer),
                        EndpointState.class))
                .collect(Collectors.toList()))
        ).thenCompose(endpoints -> {

            if (endpoints == null || endpoints.isEmpty()) {
                return DeferredResult.completed(Collections.<EndpointState> emptyList());
            }

            // get the compute states that back the endpoints
            List<String> computeLinks = endpoints.stream().map(ep -> ep.computeLink)
                    .collect(Collectors.toList());

            Builder computeStatesQueryBuilder = Query.Builder.create()
                    .addKindFieldClause(ComputeState.class)
                    .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, computeLinks)
                    .addFieldClause(ComputeState.FIELD_NAME_POWER_STATE,
                            ComputeService.PowerState.ON);

            if (tenantLinks == null || tenantLinks.isEmpty()) {
                builder.addClause(QueryUtil.addTenantClause(tenantLinks));
            }

            QueryUtils.QueryByPages<ComputeState> q = new QueryUtils.QueryByPages<>(host,
                    computeStatesQueryBuilder.build(), ComputeState.class,
                    QueryUtil.getTenantLinks(tenantLinks));

            // return only the endpoints whose computestate's power state is on
            DeferredResult<List<EndpointState>> filteredEndpoints = q
                    .collectLinks(Collectors.toSet())
                    .thenApply(cs -> {
                        List<EndpointState> collect = endpoints.stream()
                                .filter(ep -> cs.contains(ep.computeLink))
                                .collect(Collectors.toList());
                        return collect;
                    });

            return filteredEndpoints;
        }).thenApply(eps -> eps.stream()
                .map(ep -> applyEndpoint(ep, entriesPerEndpoint.get(ep.documentSelfLink)))
        ).thenCompose(entriesStream -> DeferredResult.allOf(entriesStream
                .map(entries -> queryEnvironments(host, entries, tenantLinks, environmentLinks))
                .collect(Collectors.toList()))
        ).whenComplete((all, ex) -> {
            if (ex != null) {
                consumer.accept(null, ex);
            } else {
                consumer.accept(
                        all.stream()
                                .flatMap(l -> l.stream())
                                .filter(env -> !env.envLinks.isEmpty())
                                .collect(Collectors.toList()),
                        null);
            }
        });
    }

    private static List<EnvEntry> applyEndpoint(EndpointState e, List<EnvEntry> entries) {
        if (entries == null) {
            return new ArrayList<>();
        }
        entries.forEach(entry -> entry.endpoint = e);
        return entries;
    }

    private static DeferredResult<List<EnvEntry>> queryEnvironments(ServiceHost host,
            List<EnvEntry> entries, List<String> tenantLinks, List<String> environmentLinks) {

        if (entries == null || entries.isEmpty()) {
            return DeferredResult.completed(new ArrayList<>());
        }

        // Endpoint link and type will be the same for all entries.
        EnvEntry entry = entries.get(0);

        List<String> tl = QueryUtil.getTenantLinks(tenantLinks);
        if (tl == null || tl.isEmpty()) {
            host.log(Level.INFO,
                    "Quering for global environments for endpoint [%s] of type [%s]...",
                    entry.endpoint.documentSelfLink, entry.endpoint.endpointType);
        } else {
            host.log(Level.INFO,
                    "Quering for group [%s] environments for endpoint [%s] of type [%s]...",
                    tl, entry.endpoint.documentSelfLink, entry.endpoint.endpointType);
        }
        Query tenantLinksQuery = QueryUtil.addTenantClause(tl);

        // link=LINK || (link=unset && type=TYPE)
        Builder query = Query.Builder.create()
                .addKindFieldClause(EnvironmentState.class)
                .addClause(tenantLinksQuery)
                .addClause(Query.Builder.create()
                        .addFieldClause(EnvironmentState.FIELD_NAME_ENDPOINT_LINK,
                                entry.endpoint.documentSelfLink, Occurance.SHOULD_OCCUR)
                        .addClause(Query.Builder.create(Occurance.SHOULD_OCCUR)
                                .addFieldClause(EnvironmentState.FIELD_NAME_ENDPOINT_LINK,
                                        "", MatchType.PREFIX, Occurance.MUST_NOT_OCCUR)
                                .addFieldClause(EnvironmentState.FIELD_NAME_ENDPOINT_TYPE,
                                        entry.endpoint.endpointType)
                                .build())
                        .build());

        if (environmentLinks != null && !environmentLinks.isEmpty()) {
            query = query.addInClause(EnvironmentState.FIELD_NAME_SELF_LINK, environmentLinks);
        }

        QueryTask queryTask = QueryUtil.buildQuery(EnvironmentState.class, true, query.build());
        queryTask.tenantLinks = tenantLinks;

        DeferredResult<List<EnvEntry>> result = new DeferredResult<>();
        new ServiceDocumentQuery<>(
                host, EnvironmentState.class).query(queryTask,
                        (r) -> {
                            if (r.hasException()) {
                                result.fail(r.getException());
                                return;
                            } else if (r.hasResult()) {
                                entries.forEach(e -> e.addEnvLink(r.getDocumentSelfLink()));
                            } else {
                                if (entry.envLinks.isEmpty()) {
                                    if (tl != null && !tl.isEmpty()) {
                                        queryEnvironments(host, entries, null, environmentLinks)
                                                .whenComplete((envs, t) -> {
                                                    if (t != null) {
                                                        result.fail(t);
                                                    } else {
                                                        result.complete(envs);
                                                    }
                                                });
                                        return;
                                    }
                                }
                                result.complete(entries);
                            }
                        });
        return result;
    }
}
