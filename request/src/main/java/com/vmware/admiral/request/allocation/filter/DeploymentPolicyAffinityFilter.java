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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.xenon.common.ServiceHost;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide host selection in case the
 * {@link ContainerDescription} specifies <code>affinity</code> property. Based on the dependent
 * container spec name specified in <code>affinity</code>, the list of host would be selected based
 * on the affinity constraints.
 */
public class DeploymentPolicyAffinityFilter implements HostSelectionFilter {
    protected static final boolean AFFINITY = true;
    private String deploymentPolicyId;
    private ServiceHost host;

    public DeploymentPolicyAffinityFilter(final ServiceHost host, final ContainerDescription desc) {
        this.host = host;
        this.deploymentPolicyId = desc.deploymentPolicyId;
    }

    @Override
    public boolean isActive() {
        return deploymentPolicyId != null && !deploymentPolicyId.isEmpty();
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        if (!isActive()) {
            return Collections.emptyMap();
        }
        HashMap<String, AffinityConstraint> affinityConstraints = new HashMap<>(1);
        affinityConstraints.put(deploymentPolicyId, new AffinityConstraint(deploymentPolicyId));
        return affinityConstraints;
    }

    @Override
    public void filter(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {
        if (!isActive()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        host.log(Level.INFO,
                "Filter for containerDesc property deploymentPolicyId, value: [%s] and contextId: [%s] is active for placement host selection task: %s",
                deploymentPolicyId, state.contextId, state.documentSelfLink);

        final Map<String, HostSelection> filteredHostSelectionMap = new LinkedHashMap<>(
                hostSelectionMap.size());
        for (HostSelection hostSelection : hostSelectionMap.values()) {
            if (hostSelection.deploymentPolicyLink == null
                    || !hostSelection.deploymentPolicyLink.endsWith(deploymentPolicyId)) {
                continue;
            }
            filteredHostSelectionMap.put(hostSelection.hostLink, hostSelection);
        }

        if (filteredHostSelectionMap.isEmpty()) {
            callback.complete(hostSelectionMap, null);
        } else {
            callback.complete(filteredHostSelectionMap, null);
        }

    }

}
