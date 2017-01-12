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

import java.util.Arrays;
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
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide host selection in case the
 * {@link ComputeDescription}s has <code>affinity</code> definitions. The goal of this filter is
 * to place the cluster nodes on either same or different hosts based on the affinity rules
 * definitions.
 */
public class ComputeServiceAntiAffinityHostFilter extends ComputeServiceAffinityHostFilter {
    protected static final boolean AFFINITY = false;

    public ComputeServiceAntiAffinityHostFilter(final ServiceHost host,
            final ComputeDescription desc) {
        super(host, desc, AFFINITY);
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        return extractAffinityConstraints(affinityNames, AFFINITY);
    }

    @Override
    protected QueryTask getDescQuery() {
        //Get all container descriptions whose names are in the affinity constraints of this one
        //and all container descriptions who have affinity to this one

        return getBidirectionalDescQuery(
                QueryTask.QuerySpecification.buildCompositeFieldName(new String[] {
                        ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        TemplateComputeDescription.CUSTOM_PROP_NAME_AFFINITY }),
                "*" + AffinityConstraint.AffinityConstraintType.ANTI_AFFINITY_PREFIX
                        + computeDescriptionName + "*", getAffinity());
    }

    @Override
    protected Map<String, HostSelection> applyAffinityConstraints(
            final FilterContext state,
            final Map<String, HostSelection> initHostSelectionMap,
            final Map<String, HostSelection> filteredHostSelectionMap) {

        if (filteredHostSelectionMap.isEmpty()) {
            return initHostSelectionMap;
        } else {
            final List<Map<String, HostSelection>> result = seperateHostsByAffinitiConstraints(
                    filteredHostSelectionMap, AFFINITY);
            final Map<String, HostSelection> hardAffinityHosts = result.get(0);
            final Map<String, HostSelection> softAffinityHosts = result.get(1);

            final Map<String, HostSelection> hostSelectionMap = new HashMap<>(initHostSelectionMap);
            for (HostSelection hostSelection : hardAffinityHosts.values()) {
                hostSelectionMap.remove(hostSelection.hostLink);
            }

            if (hostSelectionMap.isEmpty()) {
                final String errMsg = String.format(
                        "No host from %s matches anti-affinity rules %s.",
                        initHostSelectionMap.keySet(), Arrays.asList(affinityNames));
                throw new HostSelectionFilterException(errMsg, "request.anti-affinity.no.host.matches",
                        initHostSelectionMap.keySet(), Arrays.asList(affinityNames));
            }

            for (HostSelection hostSelection : softAffinityHosts.values()) {
                if (hostSelectionMap.size() == 1) {
                    break;
                }

                hostSelectionMap.remove(hostSelection.hostLink);
            }

            return hostSelectionMap;
        }
    }

    @Override
    protected DeferredResult<Map<String, HostSelection>> completeWhenNoComputeDescriptionsFound(
            final FilterContext state,
            final Map<String, HostSelection> filteredHostSelectionMap,
            final Map<String, DescName> containerDescLinksWithNames) {
        Utils.logWarning("No ComputeDescriptions found for anti-affinity with container desc %s",
                computeDescriptionName);
        return DeferredResult.completed(filteredHostSelectionMap);
    }

    @Override
    protected boolean hasOutgoingAffinities() {
        if (affinityNames == null || affinityNames.size() == 0) {
            return false;
        } else {
            return affinityNames.stream().anyMatch(affinity -> affinity.startsWith("!"));
        }
    }

}
