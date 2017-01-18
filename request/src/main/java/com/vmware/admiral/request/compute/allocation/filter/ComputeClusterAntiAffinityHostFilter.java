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

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.content.TemplateComputeDescription;
import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide host selection in case the
 * {@link ComputeDescriptionService.ComputeDescription}s has cluster size greater than 1. The goal
 * of this filter is to place the cluster nodes on as many different hosts as possible.
 */
public class ComputeClusterAntiAffinityHostFilter
        implements HostSelectionFilter<FilterContext> {

    private ServiceHost serviceHost;
    private ComputeDescriptionService.ComputeDescription computeDescription;

    private final Integer clusterSize;

    public ComputeClusterAntiAffinityHostFilter(ServiceHost serviceHost,
            ComputeDescriptionService.ComputeDescription computeDescription) {

        this.serviceHost = serviceHost;
        this.computeDescription = computeDescription;
        if (computeDescription.customProperties != null) {
            clusterSize = Integer
                    .valueOf(computeDescription.customProperties
                            .getOrDefault(TemplateComputeDescription.CUSTOM_PROP_NAME_CLUSTER_SIZE,
                                    "1"));
        } else {
            clusterSize = 1;
        }
    }

    @Override
    public boolean isActive() {
        return clusterSize > 1;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        if (!isActive()) {
            return Collections.emptyMap();
        }
        final HashMap<String, AffinityConstraint> affinityConstraints = new HashMap<>(1);
        final AffinityConstraint constraint = new AffinityConstraint(computeDescription.name);
        constraint.antiAffinity = true;
        constraint.type = AffinityConstraint.AffinityConstraintType.SOFT;
        affinityConstraints.put(computeDescription.name, constraint);
        return affinityConstraints;
    }

    protected void findComputes(
            final FilterContext filterContext,
            final Map<String, HostSelection> hostSelectionMap,
            final HostSelectionFilterCompletion callback) {

        QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_DESCRIPTION_LINK,
                        computeDescription.documentSelfLink)
                .addInClause(ComputeState.FIELD_NAME_PARENT_LINK, hostSelectionMap.keySet())
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        FIELD_NAME_CONTEXT_ID_KEY, filterContext.contextId);

        QueryTask q = QueryTask.Builder.create().setQuery(queryBuilder.build()).build();
        q.querySpec.resultLimit = ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT;
        QueryUtil.addExpandOption(q);

        new ServiceDocumentQuery<>(serviceHost, ComputeState.class).query(q, (r) -> {
            if (r.hasException()) {
                serviceHost.log(Level.WARNING,
                        "Exception while selecting computes with contextId [%s] during cluster node"
                                + " filtering. Error: [%s]",
                        filterContext.contextId, r.getException().getMessage());
                callback.complete(null, r.getException());
            } else if (r.hasResult()) {
                HostSelection hostSelection = hostSelectionMap
                        .get(r.getResult().parentLink);
                hostSelection.resourceCount += 1;
            } else {
                completeFilter(filterContext, hostSelectionMap, callback);
            }
        });
    }

    @Override
    public void filter(FilterContext filterContext,
            Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        //In case this is a clustering operation we want to continue filtering even if resourceCount <= 1
        if (!isActive() && filterContext.resourceCount <= 1 && !filterContext.isClustering) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        findComputes(filterContext, hostSelectionMap, callback);
    }

    private void completeFilter(
            final FilterContext filterContext,
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
                    || filteredHostSelectionMap.size() < filterContext.resourceCount) {
                filteredHostSelectionMap.put(hostSelection.hostLink, hostSelection);
            }
        }

        callback.complete(filteredHostSelectionMap, null);
    }
}
