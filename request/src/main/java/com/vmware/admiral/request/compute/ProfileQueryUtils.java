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
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
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

public class ProfileQueryUtils {

    public static class ProfileEntry {
        public String rpLink;
        public EndpointState endpoint;
        public Set<String> profileLinks = new HashSet<>();

        public ProfileEntry(String rpLink, EndpointState endpoint) {
            this.rpLink = rpLink;
            this.endpoint = endpoint;
        }

        void addProfileLink(String link) {
            this.profileLinks.add(link);
        }
    }

    public static void queryProfiles(ServiceHost host, URI referer,
            Set<String> resourcePoolsLinks, String endpointLink, List<String> tenantLinks,
            List<String> profileLinks, BiConsumer<List<ProfileEntry>, Throwable> consumer) {
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

        final Map<String, List<ProfileEntry>> entriesPerEndpoint = new HashMap<>();
        query.queryDocuments(rp -> {
            String epl = rp.customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME);

            entriesPerEndpoint.computeIfAbsent(epl, k -> new ArrayList<>())
                    .add(new ProfileEntry(rp.documentSelfLink, null));
        }).thenCompose(v -> {
            return DeferredResult.allOf(entriesPerEndpoint.keySet().stream()
                    .map(epl -> host.sendWithDeferredResult(
                            Operation.createGet(host, epl).setReferer(referer),
                            EndpointState.class))
                    .collect(Collectors.toList()));
        }
        ).thenCompose(endpoints -> {

            if (endpoints == null || endpoints.isEmpty()) {
                host.log(Level.INFO,
                        () -> String.format(
                                "No applicable endpoints found for resource pools %s and endpointLink '%s'",
                                resourcePoolsLinks, endpointLink));
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
                        List<EndpointState> poweredOnEndpoints = endpoints.stream()
                                .filter(ep -> cs.contains(ep.computeLink))
                                .collect(Collectors.toList());

                        if (poweredOnEndpoints.size() < endpoints.size()) {
                            host.log(Level.INFO,
                                    () -> String.format(
                                            "%d powered-off endpoints filtered out; remaining: %s",
                                            endpoints.size() - poweredOnEndpoints.size(),
                                            poweredOnEndpoints.stream()
                                                    .map(es -> es.documentSelfLink)
                                                    .collect(Collectors.toList())));
                        }

                        return poweredOnEndpoints;
                    });

            return filteredEndpoints;
        }).thenApply(eps -> eps.stream()
                .map(ep -> applyEndpoint(ep, entriesPerEndpoint.get(ep.documentSelfLink)))
        ).thenCompose(entriesStream -> {
            return DeferredResult.allOf(entriesStream
                .map(entries -> queryProfiles(host, entries, tenantLinks, profileLinks))
                    .collect(Collectors.toList()));
        }
        ).whenComplete((all, ex) -> {
            if (ex != null) {
                consumer.accept(null, ex);
            } else {
                List<String> endpointsWithNoProfile = new ArrayList<>();
                List<ProfileEntry> profileEntries = all.stream()
                        .flatMap(l -> l.stream())
                        .filter(profileEntry -> {
                            if (profileEntry.profileLinks.isEmpty()) {
                                endpointsWithNoProfile.add(profileEntry.endpoint.documentSelfLink);
                                return false;
                            }
                            return true;
                        })
                        .collect(Collectors.toList());

                if (!endpointsWithNoProfile.isEmpty()) {
                    host.log(Level.INFO,
                            () -> String.format("Endpoints without profiles filtered out: %s",
                                    endpointsWithNoProfile));
                }

                consumer.accept(profileEntries, null);
            }
        });
    }

    private static List<ProfileEntry> applyEndpoint(EndpointState e, List<ProfileEntry> entries) {
        if (entries == null) {
            return new ArrayList<>();
        }
        entries.forEach(entry -> entry.endpoint = e);
        return entries;
    }

    private static DeferredResult<List<ProfileEntry>> queryProfiles(ServiceHost host,
            List<ProfileEntry> entries, List<String> tenantLinks, List<String> profileLinks) {

        if (entries == null || entries.isEmpty()) {
            return DeferredResult.completed(new ArrayList<>());
        }

        // Endpoint link and type will be the same for all entries.
        ProfileEntry entry = entries.get(0);

        List<String> tl = QueryUtil.getTenantLinks(tenantLinks);
        if (tl == null || tl.isEmpty()) {
            host.log(Level.INFO,
                    "Quering for global profiles for endpoint [%s] of type [%s]...",
                    entry.endpoint.documentSelfLink, entry.endpoint.endpointType);
        } else {
            host.log(Level.INFO,
                    "Quering for group [%s] profiles for endpoint [%s] of type [%s]...",
                    tl, entry.endpoint.documentSelfLink, entry.endpoint.endpointType);
        }
        Query tenantLinksQuery = QueryUtil.addTenantClause(tl);

        // link=LINK || (link=unset && type=TYPE)
        Builder query = Query.Builder.create()
                .addKindFieldClause(ProfileState.class)
                .addClause(tenantLinksQuery)
                .addClause(Query.Builder.create()
                        .addFieldClause(ProfileState.FIELD_NAME_ENDPOINT_LINK,
                                entry.endpoint.documentSelfLink, Occurance.SHOULD_OCCUR)
                        .addClause(Query.Builder.create(Occurance.SHOULD_OCCUR)
                                .addFieldClause(ProfileState.FIELD_NAME_ENDPOINT_LINK,
                                        "", MatchType.PREFIX, Occurance.MUST_NOT_OCCUR)
                                .addFieldClause(ProfileState.FIELD_NAME_ENDPOINT_TYPE,
                                        entry.endpoint.endpointType)
                                .build())
                        .build());

        if (profileLinks != null && !profileLinks.isEmpty()) {
            query = query.addInClause(ProfileState.FIELD_NAME_SELF_LINK, profileLinks);
        }

        QueryTask queryTask = QueryUtil.buildQuery(ProfileState.class, true, query.build());
        queryTask.tenantLinks = tenantLinks;

        DeferredResult<List<ProfileEntry>> result = new DeferredResult<>();
        new ServiceDocumentQuery<>(
                host, ProfileState.class).query(queryTask,
                        (r) -> {
                            if (r.hasException()) {
                                result.fail(r.getException());
                                return;
                            } else if (r.hasResult()) {
                                entries.forEach(e -> e.addProfileLink(r.getDocumentSelfLink()));
                            } else {
                                if (entry.profileLinks.isEmpty()) {
                                    if (tl != null && !tl.isEmpty()) {
                                        queryProfiles(host, entries, null, profileLinks)
                                                .whenComplete((profileEntries, t) -> {
                                                    if (t != null) {
                                                        result.fail(t);
                                                    } else {
                                                        result.complete(profileEntries);
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
