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

package com.vmware.admiral.request.compute.allocation.filter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide affinity and network name
 * resolution in case the {@link ContainerDescription} specifies <code>networks</code> property.
 */
public class ComputeToNetworkAffinityHostFilter
        implements HostSelectionFilter<FilterContext> {
    private final List<String> nicDescLinks;
    private final ServiceHost host;
    private List<String> tenantLinks;

    public ComputeToNetworkAffinityHostFilter(ServiceHost host, ComputeDescription desc) {
        this.host = host;
        this.nicDescLinks = desc.networkInterfaceDescLinks;
        this.tenantLinks = desc.tenantLinks;
    }

    @Override
    public void filter(final FilterContext filterContext,
            final Map<String, HostSelection> hostSelectionMap,
            final HostSelectionFilterCompletion callback) {
        // sort the map in order to return consistent result no matter the order
        Map<String, HostSelection> sortedHostSelectionMap;
        if (hostSelectionMap != null) {
            sortedHostSelectionMap = new TreeMap<>(hostSelectionMap);
        } else {
            sortedHostSelectionMap = null;
        }

        if (isActive()) {
            callback.complete(sortedHostSelectionMap, null);
        }
    }

    @Override
    public boolean isActive() {
        return nicDescLinks != null && nicDescLinks.size() > 0;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        if (!isActive()) {
            return Collections.emptyMap();
        }
        CompletableFuture<Map<String, DescName>> f = new CompletableFuture<>();
        loadNicDescs((m, t) -> {
            if (t != null) {
                f.completeExceptionally(t);
            } else {
                f.complete(m);
            }
        });
        try {
            return f.get(5, TimeUnit.SECONDS).entrySet().stream().collect(
                    Collectors.toMap(p -> p.getKey(), p -> new AffinityConstraint(p.getKey())));
        } catch (Exception e) {
            host.log(Level.WARNING, "Error loading network definitions, reason:%s", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private final void loadNicDescs(BiConsumer<Map<String, DescName>, Throwable> callback) {

        Query query = Query.Builder.create()
                .addKindFieldClause(NetworkInterfaceDescription.class)
                .addInClause(ResourceState.FIELD_NAME_SELF_LINK, nicDescLinks)
                .build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.TOP_RESULTS)
                .setQuery(query)
                .setResultLimit(nicDescLinks.size())
                .build();

        host.sendWithDeferredResult(Operation.createPost(host, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setConnectionSharing(true)
                .setReferer(UriUtils.buildUri(host, getClass().getSimpleName())), QueryTask.class)
                .whenComplete((qt, e) -> {
                    if (e != null) {
                        host.log(Level.WARNING,
                                "Exception while filtering network descriptions. Error: [%s]", e);
                        callback.accept(null, e);
                        return;
                    }
                    Map<String, DescName> descLinksWithNames = new HashMap<>();
                    if (qt.results.documents != null) {
                        descLinksWithNames = qt.results.documents.values().stream()
                                .map(json -> Utils.fromJson(json,
                                        NetworkInterfaceDescription.class))
                                .map(nid -> {
                                    final DescName descName = new DescName();
                                    descName.descLink = nid.documentSelfLink;
                                    descName.descriptionName = nid.name;
                                    return descName;
                                })
                                .collect(Collectors.toMap(d -> d.descriptionName, d -> d));
                    }
                    callback.accept(descLinksWithNames, null);
                });
    }
}
