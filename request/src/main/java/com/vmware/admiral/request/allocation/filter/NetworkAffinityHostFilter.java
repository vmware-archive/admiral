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
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.PlacementHostSelectionTaskService;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide affinity and network name
 * resolution in case the {@link ContainerDescription} specifies <code>networks</code> property.
 */
public class NetworkAffinityHostFilter implements HostSelectionFilter {
    private final Map<String, ServiceNetwork> networks;
    private final ServiceHost host;

    public NetworkAffinityHostFilter(ServiceHost host, ContainerDescription desc) {
        this.host = host;
        this.networks = desc.networks;
    }

    @Override
    public void filter(
            PlacementHostSelectionTaskService.PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {
        if (isActive()) {
            findComponentDescriptions(state, hostSelectionMap, callback);
        } else {
            callback.complete(hostSelectionMap, null);
        }
    }

    protected void findComponentDescriptions(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> hostSelectionMap,
            final HostSelectionFilterCompletion callback) {

        final QueryTask q = QueryUtil.buildQuery(ContainerNetworkDescription.class, false);

        QueryUtil.addListValueClause(q, ContainerNetworkDescription.FIELD_NAME_NAME,
                networks.keySet());
        QueryUtil.addExpandOption(q);

        final Map<String, DescName> descLinksWithNames = new HashMap<>();
        new ServiceDocumentQuery<>(host, ContainerNetworkDescription.class)
                .query(q, (r) -> {
                    if (r.hasException()) {
                        host.log(Level.WARNING,
                                "Exception while filtering network descriptions. Error: [%s]",
                                r.getException().getMessage());
                        callback.complete(null, r.getException());
                    } else if (r.hasResult()) {
                        final ContainerNetworkDescription desc = r.getResult();
                        final DescName descName = new DescName();
                        descName.descLink = desc.documentSelfLink;
                        descName.descriptionName = desc.name;
                        descLinksWithNames.put(descName.descLink, descName);
                    } else {
                        if (descLinksWithNames.isEmpty()) {
                            callback.complete(hostSelectionMap, null);
                        } else {
                            findNetworks(state, hostSelectionMap, descLinksWithNames, callback);
                        }
                    }
                });
    }

    protected void findNetworks(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> hostSelectionMap,
            Map<String, DescName> descLinksWithNames,
            final HostSelectionFilterCompletion callback) {
        QueryTask q = QueryUtil.buildPropertyQuery(ContainerNetworkState.class,
                ContainerNetworkState.FIELD_NAME_COMPOSITE_COMPONENT_LINK, UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, state.contextId));
        q.taskInfo.isDirect = false;
        q.querySpec.resultLimit = ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT;
        QueryUtil.addExpandOption(q);

        QueryUtil.addListValueClause(q,
                ContainerNetworkState.FIELD_NAME_DESCRIPTION_LINK, descLinksWithNames.keySet());

        new ServiceDocumentQuery<ContainerNetworkState>(host, ContainerNetworkState.class)
                .query(q,
                        (r) -> {
                            if (r.hasException()) {
                                host.log(
                                        Level.WARNING,
                                        "Exception while selecting container networks with contextId [%s]. Error: [%s]",
                                        state.contextId, r.getException().getMessage());
                                callback.complete(null, r.getException());
                            } else if (r.hasResult()) {
                                final DescName descName = descLinksWithNames.get(r.getResult().descriptionLink);
                                descName.addContainerNames(Collections.singletonList(r.getResult().name));

                                for (HostSelection hs : hostSelectionMap.values()) {
                                    hs.addDesc(descName);
                                }
                            } else {
                                try {
                                    callback.complete(hostSelectionMap, null);
                                } catch (Throwable e) {
                                    host.log(Level.WARNING,
                                            "Exception when completing callback. Error: [%s]",
                                            e.getMessage());
                                    callback.complete(null, e);
                                }
                            }
                        });
    }

    @Override
    public boolean isActive() {
        return networks != null && networks.size() > 0;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        return isActive() ?
                networks.entrySet().stream()
                        .collect(Collectors.toMap(p -> p.getKey(), p -> new AffinityConstraint())) :
                Collections.emptyMap();
    }
}
