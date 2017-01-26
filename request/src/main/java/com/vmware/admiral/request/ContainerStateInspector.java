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

package com.vmware.admiral.request;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.xenon.common.LocalizableValidationException;

/**
 * Inspect the container states and  grouped them by context_id.
 */
public class ContainerStateInspector {
    public static final String CONTAINER_DESCRIPTION_NOT_PROVIDED_MESSAGE = "Container description not provided.";
    public static final String CONTAINER_DESCRIPTION_NOT_PROVIDED_CODE = "request.container-inspector.container-description.not-provided";
    public static final String GROUPED_CONTAINERS_NOT_PROVIDED_MESSAGE = "Grouped containers not provided.";
    public static final String GROUPED_CONTAINERS_NOT_PROVIDED_CODE = "request.container-inspector.grouped-containers.not-provided";

    private ContainerDescription containerDescription;
    private Map<String, List<ContainerState>> actualContainersPerContextId;
    private Map<String, List<ContainerState>> unhealthyContainersPerContextId;

    public static ContainerStateInspector inspect(ContainerDescription containerDescription,
            Map<String, List<ContainerState>> actualContainersPerContextId) {
        if (containerDescription == null) {
            throw new LocalizableValidationException(CONTAINER_DESCRIPTION_NOT_PROVIDED_MESSAGE, CONTAINER_DESCRIPTION_NOT_PROVIDED_CODE);
        }

        if (actualContainersPerContextId == null) {
            throw new LocalizableValidationException(GROUPED_CONTAINERS_NOT_PROVIDED_MESSAGE, GROUPED_CONTAINERS_NOT_PROVIDED_CODE);
        }

        Map<String, List<ContainerState>> unhealtyContainersPerContextId = actualContainersPerContextId
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        map -> map.getValue().stream()
                                .filter(c -> c.powerState == PowerState.ERROR)
                                .collect(Collectors.toList())));

        return new ContainerStateInspector(containerDescription, actualContainersPerContextId,
                unhealtyContainersPerContextId);
    }

    public ContainerStateInspector(ContainerDescription containerDescription,
            Map<String, List<ContainerState>> actualContainersPerContextId,
            Map<String, List<ContainerState>> unhealthyContainersPerContextId) {
        this.containerDescription = containerDescription;
        this.actualContainersPerContextId = actualContainersPerContextId;
        this.unhealthyContainersPerContextId = unhealthyContainersPerContextId;
    }

    public Map<String, List<ContainerState>> getActualContainersPerContextId() {
        return actualContainersPerContextId;
    }

    public Map<String, List<ContainerState>> getUnhealthyContainersPerContextId() {
        return unhealthyContainersPerContextId;
    }

    public ContainerDescription getContainerDescription() {
        return containerDescription;
    }
}
