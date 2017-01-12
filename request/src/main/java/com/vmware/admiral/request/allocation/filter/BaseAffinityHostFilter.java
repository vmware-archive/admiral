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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

public abstract class BaseAffinityHostFilter
        implements HostSelectionFilter<PlacementHostSelectionTaskState> {
    protected final ServiceHost host;
    protected final String affinityPropertyName;
    protected Collection<String> affinityNames;

    protected BaseAffinityHostFilter(ServiceHost host, String affinityPropertyName) {
        this.host = host;
        this.affinityPropertyName = affinityPropertyName;
    }

    @Override
    public void filter(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        if (!isActive()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        host.log(
                Level.INFO,
                "Filter for containerDesc property [%s], value: [%s] and contextId: [%s] is active for placement host selection task: %s",
                affinityPropertyName, getAffinity(), state.contextId, state.documentSelfLink);

        findContainerDescriptions(state, hostSelectionMap, callback, getDescQuery());
    }

    public Collection<String> getAffinity() {
        if (affinityNames == null) {
            affinityNames = getAffinityConstraints().keySet();
        }
        return affinityNames;
    }

    protected QueryTask getDescQuery() {
        final QueryTask q = QueryUtil.buildQuery(ContainerDescription.class, false);
        QueryUtil.addListValueClause(q, ContainerDescription.FIELD_NAME_NAME, getAffinity());
        QueryUtil.addExpandOption(q);
        return q;
    }

    protected void findContainerDescriptions(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> filteredHostSelectionMap,
            final HostSelectionFilterCompletion callback, final QueryTask descQuery) {

        descQuery.taskInfo.isDirect = false;
        final Map<String, DescName> containerDescLinksWithNames = new HashMap<>();
        new ServiceDocumentQuery<>(host, ContainerDescription.class)
                .query(descQuery,
                        (r) -> {
                            if (r.hasException()) {
                                host.log(
                                        Level.WARNING,
                                        "Exception while filtering container descriptions with %s %s. Error: [%s]",
                                        affinityPropertyName, getAffinity(), r.getException()
                                                .getMessage());
                                callback.complete(null, r.getException());
                            } else if (r.hasResult()) {
                                final ContainerDescription desc = r.getResult();
                                final DescName descName = new DescName();
                                if (desc == null) {
                                    descName.descLink = r.getDocumentSelfLink();
                                } else {
                                    descName.descLink = desc.documentSelfLink;
                                    descName.descriptionName = desc.name;
                                    descName.affinities = desc.affinity;
                                }
                                containerDescLinksWithNames.put(descName.descLink, descName);
                            } else {
                                if (containerDescLinksWithNames.isEmpty()) {
                                    completeWhenNoContainerDescriptionsFound(state,
                                            filteredHostSelectionMap, containerDescLinksWithNames,
                                            callback);
                                } else {
                                    host.log(Level.INFO,
                                            "Found [%s] ContainerDescription with %s: %s",
                                            containerDescLinksWithNames.size(),
                                            affinityPropertyName, getAffinity());
                                    host.log(Level.FINE, "ContainerDescriptions: %s",
                                            containerDescLinksWithNames);
                                    findContainers(state, filteredHostSelectionMap,
                                            containerDescLinksWithNames, callback);
                                }
                            }
                        });
    }

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

        // Add computeHostLinks clause:
        QueryUtil.addListValueClause(q,
                ContainerState.FIELD_NAME_PARENT_LINK, initHostSelectionMap.keySet());

        final Map<String, HostSelection> filteredHostSelectionMap = new HashMap<>();
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
                                final HostSelection hostSelection = initHostSelectionMap
                                        .get(r.getResult().parentLink);
                                filteredHostSelectionMap.put(r.getResult().parentLink,
                                        hostSelection);
                                final DescName descName = containerDescLinksWithNames
                                        .get(r.getResult().descriptionLink);
                                DescName newDescName = new DescName(descName);
                                newDescName.addResourceNames(r.getResult().names);
                                hostSelection.addDesc(newDescName);
                            } else {
                                try {
                                    final Map<String, HostSelection> hostSelectionMap =
                                            applyAffinityConstraints(state,
                                                    initHostSelectionMap, filteredHostSelectionMap);
                                    host.log(Level.INFO, "Selected host links for %s: %s - %s",
                                            affinityPropertyName, getAffinity(),
                                            hostSelectionMap.keySet());
                                    callback.complete(hostSelectionMap, null);
                                } catch (Throwable e) {
                                    callback.complete(null, e);
                                }
                            }
                        });
    }

    protected Map<String, HostSelection> applyAffinityConstraints(
            final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> initHostSelectionMap,
            final Map<String, HostSelection> filteredHostSelectionMap) {
        if (filteredHostSelectionMap.isEmpty()) {
            final String errMsg = String.format(
                    "No containers found for filter [%s] and value of [%s] for contextId [%s].",
                    affinityPropertyName, getAffinity(), state.contextId);
            throw new HostSelectionFilterException(errMsg, "request.base.affinity.filter.no.containers",
                    affinityPropertyName, getAffinity(), state.contextId);
        } else if (filteredHostSelectionMap.size() > 1) {
            final String errMsg = String
                    .format("Container host selection size [%s] based on filter: [links] with values: [%s] and contextId [%s] is not expected to be more than 1.",
                            filteredHostSelectionMap.size(), getAffinity(), state.contextId);
            throw new HostSelectionFilterException(errMsg, "request.base.affinity.filter.many.containers",
                    filteredHostSelectionMap.size(), getAffinity(), state.contextId);
        }

        return filteredHostSelectionMap;
    }

    protected void completeWhenNoContainerDescriptionsFound(
            final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> filteredHostSelectionMap,
            final Map<String, DescName> containerDescLinksWithNames,
            final HostSelectionFilterCompletion callback) {
        final String errMsg = String.format(
                "No container descriptions with %s [%s].",
                affinityPropertyName, getAffinity());
        callback.complete(null, new HostSelectionFilterException(errMsg,
                "request.base.affinity.filter.container-descriptions.unavailable",
                affinityPropertyName, getAffinity()));
    }

    protected static QueryTask getBidirectionalDescQuery(String fieldName, String value,
            Collection<String> affinity) {
        final QueryTask descQuery = QueryUtil.buildQuery(ContainerDescription.class, false);

        QueryTask.Query otherContainersWithAffinityToThis = new QueryTask.Query()
                .setTermPropertyName(QueryTask.QuerySpecification.buildCollectionItemName(
                        fieldName))
                .setTermMatchType(QueryTask.QueryTerm.MatchType.WILDCARD)
                .setTermMatchValue(value);

        if (affinity != null && !affinity.isEmpty()) {
            QueryTask.Query descClause = new QueryTask.Query();
            otherContainersWithAffinityToThis.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
            descClause.addBooleanClause(otherContainersWithAffinityToThis);

            QueryTask.Query listValueClause = QueryUtil
                    .addListValueClause(ContainerDescription.FIELD_NAME_NAME,
                            affinity, QueryTask.QueryTerm.MatchType.TERM);

            listValueClause.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
            descClause.addBooleanClause(listValueClause);

            descClause.occurance = QueryTask.Query.Occurance.MUST_OCCUR;
            descQuery.querySpec.query.addBooleanClause(descClause);

        } else {
            descQuery.querySpec.query.addBooleanClause(otherContainersWithAffinityToThis);
        }

        QueryUtil.addExpandOption(descQuery);

        return descQuery;
    }
}
