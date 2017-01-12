/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.content.TemplateComputeDescription;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

public abstract class ComputeBaseAffinityHostFilter
        implements HostSelectionFilter<FilterContext> {
    protected final ServiceHost host;
    protected Collection<String> affinityNames;

    protected ComputeBaseAffinityHostFilter(ServiceHost host) {
        this.host = host;
    }

    @Override
    public void filter(FilterContext state,
            final Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        if (!isActive()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        host.log(
                Level.INFO,
                "Filter for computeDesc property [%s], value: [%s] and contextId: [%s] is active",
                getAffinity(), state.contextId);

        doFilter(state, hostSelectionMap, callback);
    }

    public void doFilter(FilterContext state, Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {
        DeferredResult<Map<String, DescName>> computeDescriptionDeferredResult = findComputeDescriptions(
                getDescQuery());
        computeDescriptionDeferredResult.thenCompose(computeDescs -> {
            host.log(Level.INFO, "Found [%s] ComputeDescriptions : %s", computeDescs.size(),
                    getAffinity());
            host.log(Level.FINE, "ComputeDescriptions: %s", computeDescs);

            if (computeDescs.isEmpty()) {
                return completeWhenNoComputeDescriptionsFound(state, hostSelectionMap,
                        computeDescs);
            }
            return findComputes(state, hostSelectionMap, computeDescs);
        }).thenApply(filteredHosts -> {
            Map<String, HostSelection> result = applyAffinityConstraints(
                    state, hostSelectionMap, filteredHosts);
            host.log(Level.INFO, "Selected host links for %s:  - %s", getAffinity(),
                    hostSelectionMap.keySet());
            return result;
        }).whenComplete((res, ex) -> {
            if (ex != null) {
                callback.complete(null, ex);
            } else {
                callback.complete(res, null);
            }
        });
    }

    public Collection<String> getAffinity() {
        if (affinityNames == null) {
            affinityNames = getAffinityConstraints().keySet();
        }
        return affinityNames;
    }

    protected abstract QueryTask getDescQuery();

    protected DeferredResult<Map<String, DescName>> findComputeDescriptions(
            final QueryTask descQuery) {

        descQuery.taskInfo.isDirect = false;
        final Map<String, DescName> computeDescLinksWithNames = new HashMap<>();

        DeferredResult<Map<String, DescName>> result = new DeferredResult<>();
        new ServiceDocumentQuery<>(host, ComputeDescription.class)
                .query(descQuery, (r) -> {
                    if (r.hasException()) {
                        result.fail(r.getException());
                    } else if (r.hasResult()) {
                        ComputeDescription desc = r.getResult();
                        DescName descName = new DescName();
                        if (desc == null) {
                            descName.descLink = r.getDocumentSelfLink();
                        } else {
                            descName.descLink = desc.documentSelfLink;
                            descName.descriptionName = desc.name;
                            descName.affinities = TemplateComputeDescription.getAffinityNames(desc)
                                    .toArray(new String[0]);
                        }
                        computeDescLinksWithNames.put(descName.descLink, descName);
                    } else {
                        result.complete(computeDescLinksWithNames);
                    }
                });
        return result;
    }

    protected DeferredResult<Map<String, HostSelection>> findComputes(
            final FilterContext filterContext,
            final Map<String, HostSelection> initHostSelectionMap,
            final Map<String, DescName> computeDescLinksWithNames) {

        QueryTask.Query query = QueryTask.Query.Builder.create()
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        FIELD_NAME_CONTEXT_ID_KEY, filterContext.contextId)
                .addInClause(ComputeState.FIELD_NAME_DESCRIPTION_LINK,
                        computeDescLinksWithNames.keySet())
                .addInClause(ComputeState.FIELD_NAME_PARENT_LINK, initHostSelectionMap.keySet())
                .build();

        QueryTask queryTask = QueryTask.Builder.create()
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .setResultLimit(ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT)
                .setQuery(query).build();

        QueryUtil.addExpandOption(queryTask);


        final Map<String, HostSelection> filteredHostSelectionMap = new HashMap<>();
        DeferredResult<Map<String, HostSelection>> result = new DeferredResult<>();
        new ServiceDocumentQuery<>(host, ComputeState.class)
                .query(queryTask, r -> {
                    if (r.hasException()) {
                        host.log(
                                Level.WARNING,
                                "Exception while selecting computes with contextId [%s]. Error: [%s]",
                                filterContext.contextId, r.getException().getMessage());
                        result.fail(r.getException());
                    } else if (r.hasResult()) {
                        final HostSelection hostSelection = initHostSelectionMap
                                .get(r.getResult().parentLink);
                        filteredHostSelectionMap.put(r.getResult().parentLink,
                                hostSelection);
                        final DescName descName = computeDescLinksWithNames
                                .get(r.getResult().descriptionLink);
                        DescName newDescName = new DescName(descName);
                        newDescName.addResourceNames(Arrays.asList(r.getResult().name));
                        hostSelection.addDesc(newDescName);
                    } else {
                        result.complete(filteredHostSelectionMap);
                    }
                });
        return result;
    }

    protected Map<String, HostSelection> applyAffinityConstraints(
            final FilterContext state,
            final Map<String, HostSelection> initHostSelectionMap,
            final Map<String, HostSelection> filteredHostSelectionMap) {
        if (filteredHostSelectionMap.isEmpty()) {
            final String errMsg = String.format(
                    "No computes found for filter and value of [%s] for contextId [%s].",
                    getAffinity(), state.contextId);
            throw new HostSelectionFilterException(errMsg, "request.affinity.computes.not.found",
                    getAffinity(), state.contextId);
        } else if (filteredHostSelectionMap.size() > 1) {
            final String errMsg = String
                    .format("Compute host selection size [%s] based on filter: [links] with values: [%s] and contextId [%s] is not expected to be more than 1.",
                            filteredHostSelectionMap.size(), getAffinity(), state.contextId);
            throw new HostSelectionFilterException(errMsg, "request.affinity.multiple.selections",
                    filteredHostSelectionMap.size(), getAffinity(), state.contextId);
        }

        return filteredHostSelectionMap;
    }

    protected DeferredResult<Map<String, HostSelection>> completeWhenNoComputeDescriptionsFound(
            final FilterContext state,
            final Map<String, HostSelection> filteredHostSelectionMap,
            final Map<String, DescName> computeDescLinksWithNames) {
        final String errMsg = String.format("No compute descriptions with [%s].", getAffinity());
        return DeferredResult.failed(new HostSelectionFilterException(errMsg, "request.affinity.no.compute-doesc", getAffinity()));
    }

    protected static QueryTask getBidirectionalDescQuery(String fieldName, String value,
            Collection<String> affinity) {
        final QueryTask descQuery = QueryUtil.buildQuery(ComputeDescription.class, false);

        QueryTask.Query otherComputesWithAffinityToThis = new QueryTask.Query()
                .setTermPropertyName(fieldName)
                .setTermMatchType(QueryTask.QueryTerm.MatchType.WILDCARD)
                .setTermMatchValue(value);

        if (affinity != null && !affinity.isEmpty()) {
            QueryTask.Query descClause = new QueryTask.Query();
            otherComputesWithAffinityToThis.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
            descClause.addBooleanClause(otherComputesWithAffinityToThis);

            QueryTask.Query listValueClause = QueryUtil
                    .addListValueClause(ComputeDescription.FIELD_NAME_NAME,
                            affinity, QueryTask.QueryTerm.MatchType.TERM);

            listValueClause.occurance = QueryTask.Query.Occurance.SHOULD_OCCUR;
            descClause.addBooleanClause(listValueClause);

            descClause.occurance = QueryTask.Query.Occurance.MUST_OCCUR;
            descQuery.querySpec.query.addBooleanClause(descClause);

        } else {
            descQuery.querySpec.query.addBooleanClause(otherComputesWithAffinityToThis);
        }

        QueryUtil.addExpandOption(descQuery);

        return descQuery;
    }
}
