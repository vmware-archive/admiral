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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

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
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide host selection in case the
 * operation is clustering and there are links between containers in an application. In this case,
 * the target container of a link should be placed in the same host where other instances of the same
 * container were placed. I.e. in an application wordpress --> mysql (with link), clustering mysql
 * should place it in the same host where previous mysql instances were placed.
 */
public class ClusterServiceLinkAffinityHostFilter extends BaseAffinityHostFilter {
    private final String containerDescriptionName;

    public ClusterServiceLinkAffinityHostFilter(ServiceHost host, ContainerDescription desc) {
        super(host, ContainerDescription.FIELD_NAME_NAME);
        this.containerDescriptionName = desc.name;
    }

    @Override
    public void filter(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        // filter is applied for all clustering operations
        if (!isActive() || state.getCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP) == null) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        findCompositeDescriptions(state, hostSelectionMap, callback);
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        return Collections.emptyMap();
    }

    protected void findCompositeDescriptions(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> initHostSelectionMap,
            final HostSelectionFilterCompletion callback) {

        // get descriptionLinks for the containers in the same component as the current container
        QueryTask q = QueryUtil.buildQuery(ContainerState.class, false);
        String contextId = state.customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY);
        String contextLink = UriUtils.buildUriPath(CompositeComponentFactoryService.SELF_LINK, contextId);
        QueryTask.Query contextClause = new QueryTask.Query()
                .setTermPropertyName(ContainerState.FIELD_NAME_COMPOSITE_COMPONENT_LINK)
                .setTermMatchValue(contextLink);
        q.querySpec.query.addBooleanClause(contextClause);

        QueryUtil.addExpandOption(q);

        List<String> descriptionLinks = new ArrayList<>();
        new ServiceDocumentQuery<ContainerState>(host, ContainerState.class).query(q,
                (r) -> {
                    if (r.hasException()) {
                        host.log(
                                Level.WARNING,
                                "Exception while selecting containers for composite component [%s]. Error: [%s]",
                                contextLink, r.getException().getMessage());
                        callback.complete(null, r.getException());
                    } else if (r.hasResult()) {
                        descriptionLinks.add(r.getResult().descriptionLink);
                    } else {
                        if (!descriptionLinks.isEmpty()) {
                            findContainerDescriptions(state, initHostSelectionMap, callback,
                                    getDescQuery(descriptionLinks));
                        } else {
                            callback.complete(initHostSelectionMap, null);
                        }
                    }
                });
    }

    protected QueryTask getDescQuery(List<String> descriptionLinks) {
        // get container descriptions for descriptionLinks having a service link to
        // the container description which is being clustered
        QueryTask q = QueryUtil.buildQuery(ContainerDescription.class, false);
        String linksItemField = QueryTask.QuerySpecification.buildCollectionItemName(
                ContainerDescription.FIELD_NAME_LINKS);
        QueryUtil.addListValueClause(q, linksItemField,
                Arrays.asList(containerDescriptionName + ":*", containerDescriptionName),
                MatchType.WILDCARD);
        QueryUtil.addListValueClause(q, ContainerDescription.FIELD_NAME_SELF_LINK,
                descriptionLinks);

        QueryUtil.addExpandOption(q);

        return q;
    }

    @Override
    protected void completeWhenNoContainerDescriptionsFound(
            final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> filteredHostSelectionMap,
            final Map<String, DescName> containerDescLinksWithNames,
            final HostSelectionFilterCompletion callback) {
        // complete the filter with all the hosts available
        callback.complete(filteredHostSelectionMap, null);
    }

    @Override
    protected void findContainers(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> initHostSelectionMap,
            final Map<String, DescName> containerDescLinksWithNames,
            final HostSelectionFilterCompletion callback) {

        if (containerDescLinksWithNames.isEmpty()) {
            // complete the filter with all the hosts available
            callback.complete(initHostSelectionMap, null);
            return;
        }

        super.findContainers(state, initHostSelectionMap, containerDescLinksWithNames, callback);
    }

    @Override
    protected Map<String, HostSelection> applyAffinityConstraints(
            final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> initHostSelectionMap,
            final Map<String, HostSelection> filteredHostSelectionMap) {

        if (filteredHostSelectionMap.isEmpty() || filteredHostSelectionMap.size() > 1) {
            host.log(Level.FINE, "No valid host links found for filter [%s] and value of [%s]. Available hosts are [%s]",
                    affinityPropertyName, getAffinity(), filteredHostSelectionMap.entrySet());
            return Collections.emptyMap();
        } else {
            return filteredHostSelectionMap;
        }
    }
}
