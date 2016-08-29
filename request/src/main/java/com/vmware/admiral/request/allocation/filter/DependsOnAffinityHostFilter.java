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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.request.PlacementHostSelectionTaskService;

/**
 * Dummy affinity filter that is used to influence the order of provisioning/starting containers in
 * a composite description
 */
public class DependsOnAffinityHostFilter implements HostSelectionFilter {

    private String[] dependsOn;

    public DependsOnAffinityHostFilter(ContainerDescriptionService.ContainerDescription desc) {
        dependsOn = desc.dependsOn;
    }

    @Override
    public void filter(
            PlacementHostSelectionTaskService.PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        //do nothing
        callback.complete(hostSelectionMap, null);
    }

    @Override
    public boolean isActive() {
        return dependsOn != null && dependsOn.length > 0;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        return isActive() ?
                Arrays.stream(dependsOn)
                        .collect(Collectors.toMap(Function.identity(), AffinityConstraint::new)) :
                Collections.emptyMap();
    }
}
