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

package com.vmware.admiral.compute.container.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.PublicServiceLinkProcessor;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.ServiceAddressConfig;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class ExposeServiceProcessor implements PublicServiceLinkProcessor {
    private final Map<String, String> computeAddresses = new HashMap<>();
    private final List<ContainerState> containers;

    public ExposeServiceProcessor(List<ContainerState> containers) {
        this.containers = containers;
    }

    public void putComputeState(ComputeState computeState) {
        computeAddresses.put(computeState.documentSelfLink,
                UriUtilsExtended.extractHost(computeState.address));
    }

    public Set<String> generatePublicServiceLinks(ServiceAddressConfig config) {
        String hostname = UriUtilsExtended.extractHost(config.address);
        String scheme = UriUtilsExtended.extractScheme(config.address);

        Set<String> serviceLinks = new HashSet<>();
        containers.forEach((container) -> {
            String hostAddress = computeAddresses.get(container.parentLink);

            String hostPort = null;
            for (PortBinding mapping : container.ports) {
                if (mapping.containerPort != null &&
                        mapping.containerPort.equals(config.port)) {
                    hostPort = mapping.hostPort;
                    break;
                }
            }

            if (hostPort == null) {
                // Host port may be null if the container is off, skip in this case
                return;
            }

            serviceLinks.add(String
                    .format("%s:%s:%s:%s", scheme, hostname, hostAddress, hostPort));
        });

        return serviceLinks;
    }
}