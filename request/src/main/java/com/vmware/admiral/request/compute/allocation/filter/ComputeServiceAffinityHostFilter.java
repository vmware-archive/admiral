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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vmware.admiral.compute.content.TemplateComputeDescription;
import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide host selection in case the
 * {@link ComputeDescription} specifies <code>affinity</code> custom property. Based on the dependent
 * compute spec name specified in <code>affinity</code>, the list of host would be selected based
 * on the affinity constraints.
 */
public class ComputeServiceAffinityHostFilter extends ComputeBaseAffinityHostFilter {
    protected static final boolean AFFINITY = true;
    protected final List<String> affinityNames;
    private Map<String, AffinityConstraint> affinityConstraints;
    protected String computeDescriptionName;

    public ComputeServiceAffinityHostFilter(final ServiceHost host, final ComputeDescription desc) {
        this(host, desc, AFFINITY);
    }

    protected ComputeServiceAffinityHostFilter(final ServiceHost host,
            ComputeDescription computeDescription, boolean affinity) {
        super(host);
        this.affinityNames = TemplateComputeDescription.getAffinityNames(computeDescription);
        this.computeDescriptionName = computeDescription.name;
    }

    @Override
    public void filter(FilterContext state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        if (!isActive() && !state.isClustering) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        doFilter(state, hostSelectionMap, callback);
    }

    @Override
    protected QueryTask getDescQuery() {
        //Get all ComputeDescriptions whose names are in the affinity constraints of this one
        //and all ComputeDescriptions who have affinity to this one

        return getBidirectionalDescQuery(
                QueryTask.QuerySpecification.buildCompositeFieldName(new String[] {
                        ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        TemplateComputeDescription.CUSTOM_PROP_NAME_AFFINITY }),
                "*" + computeDescriptionName + "*", getAffinity());
    }

    @Override
    protected Map<String, HostSelection> applyAffinityConstraints(
            final FilterContext state,
            final Map<String, HostSelection> initHostSelectionMap,
            final Map<String, HostSelection> filteredHostSelectionMap) {

        if (filteredHostSelectionMap.isEmpty() && hasOutgoingAffinities()) {
            final String errMsg = String.format(
                    "No computes found for filter and value of [%s] for contextId [%s].",
                    getAffinity(), state.contextId);
            throw new HostSelectionFilterException(errMsg, "request.affinity.computes.not.found",
                    getAffinity(), state.contextId);

        } else if (filteredHostSelectionMap.isEmpty() && !hasOutgoingAffinities()) {
            return initHostSelectionMap;
        } else {
            final List<Map<String, HostSelection>> result = seperateHostsByAffinitiConstraints(
                    filteredHostSelectionMap, AFFINITY);
            final Map<String, HostSelection> hardAffinityHosts = result.get(0);
            final Map<String, HostSelection> softAffinityHosts = result.get(1);

            if (hardAffinityHosts.size() > 1) {
                final String errMsg = String
                        .format("Compute host selection size [%s] based on filter: [links] with values: [%s] and contextId [%s] is not expected to be more than 1.",
                                filteredHostSelectionMap.size(), getAffinity(), state.contextId);
                throw new HostSelectionFilterException(errMsg, "request.affinity.multiple.selections",
                        filteredHostSelectionMap.size(), getAffinity(), state.contextId);
            } else if (hardAffinityHosts.size() == 1) {
                return hardAffinityHosts;
            } else {
                return softAffinityHosts;
            }
        }
    }

    protected List<Map<String, HostSelection>> seperateHostsByAffinitiConstraints(
            final Map<String, HostSelection> filteredHostSelectionMap, final boolean affinity) {

        Map<String, AffinityConstraint> affinityConstraints = extractAffinityConstraints(
                affinityNames, affinity);
        final Map<String, HostSelection> hardAffinityHosts = new HashMap<>();
        final Map<String, HostSelection> softAffinityHosts = new HashMap<>();
        for (HostSelection hostSelection : filteredHostSelectionMap.values()) {
            if (hostSelection.descNames == null) {
                continue;
            }
            for (DescName descName : hostSelection.descNames.values()) {
                if (descName.descriptionName == null) {
                    continue;
                }

                Map<String, AffinityConstraint> inverseConstraints = extractAffinityConstraints(
                        descName.affinities == null ?
                                Collections.emptyList() :
                                Arrays.asList(descName.affinities), affinity);

                final AffinityConstraint affinityConst = affinityConstraints
                        .getOrDefault(descName.descriptionName,
                                inverseConstraints.get(computeDescriptionName));

                if (affinityConst == null) {
                    continue;
                }
                if (affinityConst.isHard()) {
                    hardAffinityHosts.put(hostSelection.hostLink, hostSelection);
                } else {
                    softAffinityHosts.put(hostSelection.hostLink, hostSelection);
                }
            }
        }

        final List<Map<String, HostSelection>> result = new ArrayList<>(2);
        result.add(hardAffinityHosts);
        result.add(softAffinityHosts);
        return result;
    }

    @Override
    public boolean isActive() {
        return !getAffinityConstraints().isEmpty();
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        if (affinityConstraints == null) {
            affinityConstraints = extractAffinityConstraints(affinityNames, AFFINITY);
        }
        return affinityConstraints;
    }

    protected static Map<String, AffinityConstraint> extractAffinityConstraints(
            final List<String> affinities, final boolean affinity) {
        if (affinities == null || affinities.size() == 0) {
            return Collections.emptyMap();
        }
        final Map<String, AffinityConstraint> affinityConstraints = new HashMap<>(
                affinities.size());
        for (String name : affinities) {
            AffinityConstraint constraint = AffinityConstraint.fromString(name);
            if (affinity == !constraint.antiAffinity) {
                affinityConstraints.put(constraint.name, constraint);
            }
        }
        return affinityConstraints;
    }

    @Override
    protected DeferredResult<Map<String, HostSelection>> completeWhenNoComputeDescriptionsFound(
            final FilterContext state,
            final Map<String, HostSelection> filteredHostSelectionMap,
            final Map<String, DescName> containerDescLinksWithNames) {
        final String errMsg = String.format("No compute descriptions with [%s].", getAffinity());
        if (hasOutgoingAffinities()) {
            return DeferredResult.failed(new HostSelectionFilterException(errMsg, "request.affinity.no.compute-doesc",
                    getAffinity()));
        } else {
            return DeferredResult.completed(filteredHostSelectionMap);
        }
    }

    protected boolean hasOutgoingAffinities() {
        if (affinityNames == null || affinityNames.size() == 0) {
            return false;
        } else {
            return affinityNames.stream().anyMatch(affinity -> !affinity.startsWith("!"));
        }
    }
}
