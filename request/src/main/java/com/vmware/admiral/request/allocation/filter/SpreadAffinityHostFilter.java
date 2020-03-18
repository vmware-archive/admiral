/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.allocation.filter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.PlacementHostSelectionTaskService;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
*
*  A filter implementing {@link HostSelectionFilter} aimed to provide host selection based on Placement - SPREAD.
*
*  Algorithm will filter out the docker hosts leaving only the host with smallest number of containers.
*   1) Let h ∈ { h(1)....h(n-1), h(n) }
*   2) P(h) = min{h(1)...h(n)}
*   3) ∃! h: P(h)
*
*   Constraint (1) means that most suitable host belongs to set of hosts which have been filtered from other affinity filters.
*   Constraint (2) means that hosts will be sorted by number of containers in ascending order.
*   Constraint (3) means there is exactly one host such that P(h) is true => Host with smallest number of containers will be returned.
*
*/
public class SpreadAffinityHostFilter implements
        HostSelectionFilter<PlacementHostSelectionTaskService.PlacementHostSelectionTaskState> {

    private final ServiceHost host;

    public SpreadAffinityHostFilter(ServiceHost host, ContainerDescription desc) {
        this.host = host;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        return Collections.emptyMap();
    }

    @Override
    public void filter(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        // Nothing to sort here.
        if (hostSelectionMap.size() == 1) {
            host.log(Level.INFO, "Only one host in selection. Spread filtering will be skipped.");
            callback.complete(hostSelectionMap, null);
            return;
        }

        String serviceLink = state.serviceTaskCallback != null
                ? state.serviceTaskCallback.serviceSelfLink
                : null;
        // Filter should be ignored on Reservation stage.
        if (serviceLink != null
                && serviceLink.startsWith(ReservationTaskFactoryService.SELF_LINK)) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        // On allocation stage only one resource pool exists.
        String resourcePoolLink = state.resourcePoolLinks.get(0);

        URI uri = UriUtils.buildUri(host, String.format("%s/%s",
                ElasticPlacementZoneConfigurationService.SELF_LINK, resourcePoolLink));

        host.sendRequest(Operation.createGet(uri)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {

                    if (ex != null) {
                        host.log(Level.WARNING, Utils.toString(ex));
                        callback.complete(hostSelectionMap, null);
                        return;
                    }

                    ElasticPlacementZoneConfigurationState epz = o
                            .getBody(ElasticPlacementZoneConfigurationState.class);
                    if (epz != null && epz.epzState != null
                            && epz.epzState.placementPolicy == ElasticPlacementZoneService.PlacementPolicy.SPREAD) {
                        // First find how many resources every host has.
                        retrieveContainers(hostSelectionMap, callback);
                    } else {
                        callback.complete(hostSelectionMap, null);
                    }
                }));

    }

    private void retrieveContainers(
            Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(ContainerState.class)
                .addInClause(ContainerState.FIELD_NAME_PARENT_LINK, hostSelectionMap.keySet());

        QueryTask q = QueryTask.Builder.create().setQuery(queryBuilder.build()).build();
        q.querySpec.resultLimit = ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT;
        QueryUtil.addExpandOption(q);

        new ServiceDocumentQuery<>(host, ContainerState.class).query(q, (r) -> {
            if (r.hasException()) {
                host.log(Level.WARNING,
                        "Exception while quering containers during 'Spread' filtering."
                                + "Error: [%s]",
                        r.getException().getMessage());
                callback.complete(null, r.getException());
                return;
            } else if (r.hasResult()) {
                HostSelection hostSelection = hostSelectionMap
                        .get(r.getResult().parentLink);
                hostSelection.resourceCount += 1;
            } else {
                // Return the host with minimum number of containers.
                completeFilter(hostSelectionMap, callback);
            }
        });
    }

    private void completeFilter(Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        // Sort hosts by number of resources.
        List<HostSelection> hostSelections = new ArrayList<>(hostSelectionMap.values());
        // i.e. hosts [A(5), B(3), C(9)] -> [B(3), A(5), C(9)]
        hostSelections.sort(Comparator.comparing(h -> h.resourceCount));

        // Host with smallest number of containers is the first element.
        HostSelection mostLoadedHost = hostSelections.get(0);

        Map<String, HostSelection> result = new HashMap<>();
        result.put(mostLoadedHost.hostLink, mostLoadedHost);

        callback.complete(result, null);
    }

}
