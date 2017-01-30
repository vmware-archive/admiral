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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide host selection in case the
 * {@link ContainerDescription} specifies <code>affinity</code> property. Based on the dependent
 * container spec name specified in <code>affinity</code>, the list of host would be selected based
 * on the affinity constraints.
 */
public class ServiceAffinityHostFilter extends BaseAffinityHostFilter {
    protected static final boolean AFFINITY = true;
    protected final String[] affinityNames;
    private Map<String, AffinityConstraint> affinityConstraints;
    protected String containerDescriptionName;

    public ServiceAffinityHostFilter(final ServiceHost host, final ContainerDescription desc) {
        this(host, desc, desc.affinity, AFFINITY);
    }

    protected ServiceAffinityHostFilter(final ServiceHost host,
            ContainerDescription containerDescription, final String[] affinityNames,
            boolean affinity) {
        super(host, ContainerDescription.FIELD_NAME_AFFINITY);
        this.affinityNames = affinityNames;
        this.containerDescriptionName = containerDescription.name;
    }

    @Override
    public void filter(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        if (!isActive()
                && state.getCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP) == null) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        host.log(
                Level.INFO,
                "Filter for containerDesc property [%s], value: [%s] and contextId: [%s] is active for placement host selection task: %s",
                affinityPropertyName, getAffinity(), state.contextId, state.documentSelfLink);

        findContainerDescriptions(state, hostSelectionMap, callback, getDescQuery());
    }

    @Override
    protected QueryTask getDescQuery() {
        //Get all container descriptions whose names are in the affinity constraints of this one
        //and all container descriptions who have affinity to this one

        return getBidirectionalDescQuery(ContainerDescription.FIELD_NAME_AFFINITY,
                containerDescriptionName + "*", getAffinity());
    }

    @Override
    protected Map<String, HostSelection> applyAffinityConstraints(
            final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> initHostSelectionMap,
            final Map<String, HostSelection> filteredHostSelectionMap) {

        if (filteredHostSelectionMap.isEmpty() && hasOutgoingAffinities()) {
            final String errMsg = String.format(
                    "No containers found for filter [%s] and value of [%s] for contextId [%s].",
                    affinityPropertyName, getAffinity(), state.contextId);
            throw new HostSelectionFilterException(errMsg, "request.service.affinity.filter.no.containers",
                    affinityPropertyName, getAffinity(), state.contextId);

        } else if (filteredHostSelectionMap.isEmpty() && !hasOutgoingAffinities()) {
            return initHostSelectionMap;
        } else {
            final List<Map<String, HostSelection>> result = seperateHostsByAffinitiConstraints(
                    filteredHostSelectionMap, AFFINITY);
            final Map<String, HostSelection> hardAfinitiHosts = result.get(0);
            final Map<String, HostSelection> softAfinitiHosts = result.get(1);

            if (hardAfinitiHosts.size() > 1) {
                final String errMsg = String
                        .format("Container host selection size [%s] based on filter: [links] with values: [%s] and contextId [%s] is not expected to be more than 1.",
                                filteredHostSelectionMap.size(), getAffinity(), state.contextId);
                throw new HostSelectionFilterException(errMsg, "request.service.affinity.filter.many.hosts",
                        filteredHostSelectionMap.size(), getAffinity(), state.contextId);
            } else if (hardAfinitiHosts.size() == 1) {
                return hardAfinitiHosts;
            } else {
                return softAfinitiHosts;
            }
        }
    }

    protected List<Map<String, HostSelection>> seperateHostsByAffinitiConstraints(
            final Map<String, HostSelection> filteredHostSelectionMap, final boolean affinity) {

        Map<String, AffinityConstraint> affinityConstraints = extractAffinityConstraints(
                affinityNames, affinity);
        final Map<String, HostSelection> hardAfinitiHosts = new HashMap<>();
        final Map<String, HostSelection> softAfinitiHosts = new HashMap<>();
        for (HostSelection hostSelection : filteredHostSelectionMap.values()) {
            if (hostSelection.descNames == null) {
                continue;
            }
            for (DescName descName : hostSelection.descNames.values()) {
                if (descName.descriptionName == null) {
                    continue;
                }

                Map<String, AffinityConstraint> inverseConstraints = extractAffinityConstraints(
                        descName.affinities, affinity);

                final AffinityConstraint affinityConst = affinityConstraints
                        .getOrDefault(descName.descriptionName,
                                inverseConstraints.get(containerDescriptionName));

                if (affinityConst == null) {
                    continue;
                }
                if (affinityConst.isHard()) {
                    hardAfinitiHosts.put(hostSelection.hostLink, hostSelection);
                } else {
                    softAfinitiHosts.put(hostSelection.hostLink, hostSelection);
                }
            }
        }

        final List<Map<String, HostSelection>> result = new ArrayList<>(2);
        result.add(hardAfinitiHosts);
        result.add(softAfinitiHosts);
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
            final String[] affinities, final boolean affinity) {
        if (affinities == null || affinities.length == 0) {
            return Collections.emptyMap();
        }
        final Map<String, AffinityConstraint> affinityConstraints = new HashMap<>(
                affinities.length);
        for (String name : affinities) {
            AffinityConstraint constraint = AffinityConstraint.fromString(name);
            if (affinity == !constraint.antiAffinity) {
                affinityConstraints.put(constraint.name, constraint);
            }
        }
        return affinityConstraints;
    }

    @Override
    protected void completeWhenNoContainerDescriptionsFound(
            final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> filteredHostSelectionMap,
            final Map<String, DescName> containerDescLinksWithNames,
            final HostSelectionFilterCompletion callback) {
        final String errMsg = String.format(
                "No container descriptions with %s [%s].",
                affinityPropertyName, getAffinity());
        if (hasOutgoingAffinities()) {
            callback.complete(null, new HostSelectionFilterException(errMsg,
                    "request.service.affinity.filter.no.container-descriptions",
                    affinityPropertyName, getAffinity()));
        } else {
            callback.complete(filteredHostSelectionMap, null);
        }
    }

    protected boolean hasOutgoingAffinities() {
        if (affinityNames == null || affinityNames.length == 0) {
            return false;
        } else {
            return Arrays.stream(affinityNames).anyMatch(affinity -> !affinity.startsWith("!"));
        }
    }
}
