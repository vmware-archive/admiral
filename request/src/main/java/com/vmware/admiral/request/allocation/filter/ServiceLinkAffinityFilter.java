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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Parse ContainerDescription serviceLinks and provide the dependency
 */
public class ServiceLinkAffinityFilter extends BaseAffinityHostFilter {
    private final String[] serviceLinks;
    private Map<String, ServiceNetwork> networks;

    public ServiceLinkAffinityFilter(ServiceHost host, ContainerDescription desc) {
        super(host, ContainerDescription.FIELD_NAME_LINKS);
        this.serviceLinks = desc.links;
        this.networks = desc.networks;
    }

    @Override
    public boolean isActive() {
        return serviceLinks != null && serviceLinks.length > 0;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        if (!isActive()) {
            return Collections.emptyMap();
        }

        return Arrays.stream(serviceLinks)
                .map(this::extractNameFromServiceLink)
                .collect(Collectors.toMap(Function.identity(), AffinityConstraint::new));
    }

    @Override
    protected void findContainers(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> initHostSelectionMap,
            final Map<String, DescName> containerDescLinksWithNames,
            final HostSelectionFilterCompletion callback) {
        QueryTask q = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_COMPOSITE_COMPONENT_LINK, UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, state.contextId));
        q.taskInfo.isDirect = false;
        q.querySpec.resultLimit = ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT;
        QueryUtil.addExpandOption(q);

        // Add componentDescriptions clause:
        QueryUtil.addListValueClause(q,
                ContainerState.FIELD_NAME_DESCRIPTION_LINK, containerDescLinksWithNames.keySet());

        final Map<String, HostSelection> filteredHostSelectionMap = new HashMap<>();

        if (networks != null && !networks.isEmpty()) {
            filteredHostSelectionMap.putAll(initHostSelectionMap);
        }

        new ServiceDocumentQuery<ContainerState>(host, ContainerState.class)
                .query(q,
                        (r) -> {
                            if (r.hasException()) {
                                host.log(
                                        Level.WARNING,
                                        "Exception while selecting containers with contextId [%s]. Error: [%s]",
                                        state.contextId, r.getException().getMessage());
                                callback.complete(null, r.getException());
                            } else if (r.hasResult()) {
                                ContainerState result = r.getResult();

                                DescName descName = containerDescLinksWithNames
                                        .get(result.descriptionLink);
                                descName.addResourceNames(result.names);

                                // When there are user defined networks, the dependent service can be on any host
                                if (networks != null && !networks.isEmpty()) {
                                    for (HostSelection hs : initHostSelectionMap.values()) {
                                        hs.addDesc(descName);
                                    }
                                } else {
                                    HostSelection hostSelection = initHostSelectionMap
                                            .get(result.parentLink);

                                    if (hostSelection != null) {
                                        hostSelection.addDesc(descName);
                                        filteredHostSelectionMap.put(result.parentLink,
                                                hostSelection);
                                    }
                                }
                            } else {
                                try {
                                    callback.complete(filteredHostSelectionMap, null);
                                } catch (Throwable e) {
                                    callback.complete(null, e);
                                }
                            }
                        });
    }

    private String extractNameFromServiceLink(String serviceLink) {
        return serviceLink.replaceAll(":.*$", "");
    }

}
