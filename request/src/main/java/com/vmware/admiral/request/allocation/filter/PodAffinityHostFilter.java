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

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide host selection in case the
 * {@link ContainerDescription}s of two or more containers have the same <code>pod</code> property.
 */
public class PodAffinityHostFilter extends BaseAffinityHostFilter {
    private final String pod;

    public PodAffinityHostFilter(ServiceHost host, ContainerDescription desc) {
        super(host, ContainerDescription.FIELD_NAME_POD);
        this.pod = desc.pod;
    }

    @Override
    protected QueryTask getDescQuery() {
        return QueryUtil.buildPropertyQuery(ContainerDescription.class,
                ContainerDescription.FIELD_NAME_POD, pod);
    }

    @Override
    public boolean isActive() {
        return this.pod != null && !this.pod.isEmpty();
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        if (!isActive()) {
            return Collections.emptyMap();
        }
        HashMap<String, AffinityConstraint> affinityConstraints = new HashMap<>(1);
        affinityConstraints.put(pod, new AffinityConstraint(pod));
        return affinityConstraints;
    }

    @Override
    protected Map<String, HostSelection> applyAffinityConstraints(
            final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> initHostSelectionMap,
            final Map<String, HostSelection> filteredHostSelectionMap) {
        if (filteredHostSelectionMap.isEmpty()) {
            final String podDependent = state.customProperties != null ? state.customProperties
                    .get(ContainerAllocationTaskState.FIELD_NAME_CONTEXT_POD_DEPENDENT) : "";
            if (pod.equals(podDependent)) {
                // Pod dependent means that this is not the first container
                // in the pod/host, so an already provision container in the
                // host is expected
                return super.applyAffinityConstraints(state, initHostSelectionMap,
                        filteredHostSelectionMap);
            }

            // first container to be provisioned in the pod/host.
            // No host selection needed.
            host.log(
                    Level.INFO,
                    "Allocating first container in the pod [%s] and contextId [%s] with initial host size: [%s].",
                    pod, state.contextId, initHostSelectionMap.size());
            host.log(Level.FINE, "Selected hosts: %s.", initHostSelectionMap.keySet());
            return initHostSelectionMap;
        }

        return filteredHostSelectionMap;
    }
}
