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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide affinity and network name
 * resolution in case the {@link ComputeDescription} specifies <code>networks</code> property.
 */
public class ComputeToNetworkAffinityHostFilter implements HostSelectionFilter<FilterContext> {
    private final ServiceHost host;
    @SuppressWarnings("unused")
    private List<String> tenantLinks;
    private ComputeDescription desc;

    public ComputeToNetworkAffinityHostFilter(ServiceHost host, ComputeDescription desc) {
        this.host = host;
        this.desc = desc;
        this.tenantLinks = desc.tenantLinks;
    }

    @Override
    public void filter(final FilterContext filterContext,
            final Map<String, HostSelection> hostSelectionMap,
            final HostSelectionFilterCompletion callback) {

        if (!isActive()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        // sort the map in order to return consistent result no matter the order
        Map<String, HostSelection> sortedHostSelectionMap;
        if (hostSelectionMap != null) {
            sortedHostSelectionMap = new TreeMap<>(hostSelectionMap);
        } else {
            sortedHostSelectionMap = null;
        }

        callback.complete(sortedHostSelectionMap, null);
    }

    @Override
    public boolean isActive() {
        return desc.networkInterfaceDescLinks != null && desc.networkInterfaceDescLinks.size() > 0;
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
                host.log(Level.INFO, "Network affinity map component: %s [%s].", desc.name, m);
                f.complete(m);
            }
        });
        try {
            return f.get(120, TimeUnit.SECONDS).entrySet().stream().collect(
                    Collectors.toMap(p -> p.getKey(), p -> new AffinityConstraint(p.getKey())));
        } catch (TimeoutException e) {
            host.log(Level.WARNING, "Timeout loading network definitions.");
            return Collections.emptyMap();
        } catch (Exception e) {
            host.log(Level.WARNING, "Error loading network definitions, reason:%s", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private final void loadNicDescs(BiConsumer<Map<String, DescName>, Throwable> callback) {
        Query query = Query.Builder.create()
                .addKindFieldClause(NetworkInterfaceDescription.class)
                .addInClause(ResourceState.FIELD_NAME_SELF_LINK, desc.networkInterfaceDescLinks)
                .build();

        QueryTop<NetworkInterfaceDescription> queryNids = new QueryTop<>(host, query,
                NetworkInterfaceDescription.class, null)
                        .setMaxResultsLimit(desc.networkInterfaceDescLinks.size());
        queryNids.collectDocuments(Collectors.toMap(d -> d.name, d -> {
            DescName descName = new DescName();
            descName.descLink = d.documentSelfLink;
            descName.descriptionName = d.name;
            return descName;
        })).whenComplete((map, t) -> callback.accept(map, t));

    }
}
