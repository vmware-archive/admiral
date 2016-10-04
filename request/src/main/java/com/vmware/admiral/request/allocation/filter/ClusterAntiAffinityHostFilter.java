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

package com.vmware.admiral.request.allocation.filter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide host selection in case the
 * {@link ContainerDescription}s has <code>clusterSize</code> greater than 1. The goal of this
 * filter is to place the cluster nodes on as many different hosts as possible.
 */
public class ClusterAntiAffinityHostFilter
        implements HostSelectionFilter<PlacementHostSelectionTaskState> {
    protected final ContainerDescription desc;
    protected final ServiceHost host;

    public ClusterAntiAffinityHostFilter(ServiceHost host, ContainerDescription desc) {
        this.host = host;
        this.desc = desc;
    }

    @Override
    public void filter(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> hostSelectionMap,
            final HostSelectionFilterCompletion callback) {

        //In case this is a clustering operation we want to continue even if desc._cluster <= 1
        if (!isActive() && state.resourceCount <= 1
                && state.getCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP) == null) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        findContainers(state, hostSelectionMap, callback);
    }

    @Override
    public boolean isActive() {
        return (desc._cluster != null) && (desc._cluster > 1);
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        if (!isActive()) {
            return Collections.emptyMap();
        }
        final HashMap<String, AffinityConstraint> affinityConstraints = new HashMap<>(1);
        final AffinityConstraint constraint = new AffinityConstraint(desc.name);
        constraint.antiAffinity = true;
        constraint.type = AffinityConstraint.AffinityConstraintType.SOFT;
        affinityConstraints.put(desc.name, constraint);
        return affinityConstraints;
    }

    protected void findContainers(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> hostSelectionMap,
            final HostSelectionFilterCompletion callback) {
        final QueryTask q = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_DESCRIPTION_LINK,
                desc.documentSelfLink,
                ContainerState.FIELD_NAME_COMPOSITE_COMPONENT_LINK,
                UriUtils.buildUriPath(CompositeComponentFactoryService.SELF_LINK, state.contextId));
        QueryUtil.addListValueClause(q, ContainerState.FIELD_NAME_PARENT_LINK, hostSelectionMap.keySet());

        q.querySpec.resultLimit = ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT;
        QueryUtil.addExpandOption(q);

        new ServiceDocumentQuery<ContainerState>(host, ContainerState.class)
                .query(q,
                        (r) -> {
                            if (r.hasException()) {
                                host.log(
                                        Level.WARNING,
                                        "Exception while selecting containers with contextId [%s] during cluster node filtering. Error: [%s]",
                                        state.contextId, r.getException().getMessage());
                                callback.complete(null, r.getException());
                            } else if (r.hasResult()) {
                                HostSelection hostSelection = hostSelectionMap.get(r.getResult().parentLink);
                                hostSelection.resourceCount += 1;
                            } else {
                                completeFilter(state, hostSelectionMap, callback);
                            }
                        });
    }

    private void completeFilter(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> hostSelectionMap,
            final HostSelectionFilterCompletion callback) {

        final boolean noClusterContainers = hostSelectionMap.values().stream()
                .allMatch((h) -> h.resourceCount == 0);
        if (noClusterContainers) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        // sort the host in descending order based on number of containers already allocated
        // per host (lowest to highest number of containers)
        List<HostSelection> sortedEntries = hostSelectionMap
                .values().stream()
                .sorted((o1, o2) -> o1.resourceCount - o2.resourceCount)
                .collect(Collectors.toList());

        int lowestValue = sortedEntries.get(0).resourceCount;

        // get host up to the resourceCounts starting with the ones that have the lowest number
        // of containers already allocated.
        final Map<String, HostSelection> filteredHostSelectionMap = new HashMap<>();
        for (HostSelection hostSelection : sortedEntries) {
            if (hostSelection.resourceCount == lowestValue
                    || filteredHostSelectionMap.size() < state.resourceCount) {
                filteredHostSelectionMap.put(hostSelection.hostLink, hostSelection);
            }
        }

        callback.complete(filteredHostSelectionMap, null);
    }

    @Override
    public boolean hasEffectOnDependency() {
        return false;
    }

}
