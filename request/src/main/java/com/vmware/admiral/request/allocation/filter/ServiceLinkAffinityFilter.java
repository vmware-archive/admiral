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

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;

/**
 * Parse ContainerDescription serviceLinks and provide the dependency
 */
public class ServiceLinkAffinityFilter implements AffinityFilter {
    private final String[] serviceLinks;

    public ServiceLinkAffinityFilter(ContainerDescription desc) {
        this.serviceLinks = desc.links;
    }

    @Override
    public boolean isActive() {
        return serviceLinks != null && serviceLinks.length > 0;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        if (!isActive()) {
            return Collections.emptyMap();
        }

        return Arrays.stream(serviceLinks)
                .map(this::extractNameFromServiceLink)
                .collect(Collectors.toMap(Function.identity(), AffinityConstraint::new));
    }

    private String extractNameFromServiceLink(String serviceLink) {
        return serviceLink.replaceAll(":.*$", "");
    }

}
