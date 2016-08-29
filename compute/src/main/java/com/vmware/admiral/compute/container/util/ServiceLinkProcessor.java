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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.common.util.ValidationUtils;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.InternalServiceLinkProcessor;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

/**
 * Collect data about dependencies (alias, name, ContainerState and parent Compute) and generate
 * environment variables for reaching the services on the dependencies from the container being
 * processed
 */
public class ServiceLinkProcessor implements InternalServiceLinkProcessor {

    /** map from dependency name to assigned alias */
    private final Map<String, String> depAliases = new HashMap<>();

    /** map from ContainerDescription link to its name */
    private final Map<String, String> depContainerDescriptions = new ConcurrentHashMap<>();

    /** map from dependency name to container states */
    private final Map<String, List<ContainerState>> depContainerStates = new ConcurrentHashMap<>();

    /** map from ComputeState link to its address */
    private final Map<String, String> depComputeAddresses = new ConcurrentHashMap<>();

    /** map from ComputeState link to its network agent address */
    private final Map<String, String> hostNetworkAgentAddresses = new ConcurrentHashMap<>();

    public ServiceLinkProcessor(String[] serviceLinks) {
        parseServiceLinks(serviceLinks);
    }

    private void parseServiceLinks(String[] serviceLinks) {
        for (String serviceLink : serviceLinks) {
            String[] parts = serviceLink.split(":", 2);
            String serviceName = parts[0];
            String alias;
            if (parts.length == 1) {
                alias = serviceName;

            } else {
                alias = parts[1];
            }

            ValidationUtils.validateHost(alias);

            depAliases.put(serviceName, alias);
        }
    }

    /**
     * Returns the service names that the currently processed container depends on
     *
     * @return
     */
    public Set<String> getDependencyNames() {
        return depAliases.keySet();
    }

    public Set<String> getDepContainerDescriptionLinks() {
        return depContainerDescriptions.keySet();
    }

    public void putDepContainerDescriptions(ContainerDescription depDesc) {
        depContainerDescriptions.put(depDesc.documentSelfLink, depDesc.name);
    }

    /**
     * Check that we have at least one ContainerDescription for each dependency
     *
     * Note that this is just a fail-fast in case no ContainerDescriptions were found for any
     * dependency. It is still possible that the ones that were found don't belong the same
     * blueprint (further validation is done on the ContainerStates).
     */
    public void validateDepContainerDescriptions() {
        // copy to a Set to remove duplicates
        Set<String> foundDeps = new HashSet<>(depContainerDescriptions.values());
        if (foundDeps.size() != depAliases.size()) {
            // create a set of missing dependencies for the error message by removing the found
            // dependencies from the set of expected dependencies
            Set<String> missingDeps = new HashSet<>(getDependencyNames());
            missingDeps.removeAll(foundDeps);
            throw new IllegalStateException(String.format(
                    "Missing ContainerDescriptions for dependencies: %s", missingDeps));
        }
    }

    public void putDepContainerState(ContainerState depContainerState) {
        String dependencyName = depContainerDescriptions.get(depContainerState.descriptionLink);

        if (depContainerState.ports == null || depContainerState.ports.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "Invalid ContainerState for dependency: %s. Container '%s' may have failed to start, "
                            + "it's not running yet or it hasn't published ports configuration!",
                    dependencyName, dependencyName));
        }

        AssertUtil.assertNotEmpty(depContainerState.parentLink, "containerState.parentLink");

        List<ContainerState> containerStates = depContainerStates.get(dependencyName);
        if (containerStates == null) {
            containerStates = new ArrayList<>();
            depContainerStates.put(dependencyName, containerStates);
        }
        containerStates.add(depContainerState);
    }

    /**
     * Check that all expected dependencies have a matching ContainerState
     */
    public void validateDepContainerStates() {
        if (depContainerStates.size() != depAliases.size()) {
            // create a set of missing dependencies for the error message by removing the found
            // dependencies from the set of expected dependencies
            Set<String> missingDeps = new HashSet<>(getDependencyNames());
            missingDeps.removeAll(depContainerStates.keySet());
            throw new IllegalStateException(String.format("Missing ContainerStates for "
                    + "dependencies: %s", missingDeps));
        }
    }

    public void putDepParentCompute(ComputeState depCompute) {
        AssertUtil.assertNotEmpty(depCompute.address, "computeState.address");
        String host = UriUtilsExtended.extractHost(depCompute.address);
        depComputeAddresses.put(depCompute.documentSelfLink, host);
    }

    public void putHostNetworkAgentAddress(String hostLink, String address) {
        hostNetworkAgentAddresses.put(hostLink, address);
    }

    /**
     * Make sure all the ContainerState's parent computes were found
     */
    public void validateDepParentComputes() {
        if (getDepParentComputeLinks().size() != depComputeAddresses.size()) {
            // create a set of missing Compute links for the error message by removing the found
            // Compute links from the set of expected Compute links
            Set<String> missingComputeLinks = new HashSet<>(getDepParentComputeLinks());
            missingComputeLinks.removeAll(depComputeAddresses.keySet());
            throw new IllegalStateException(String.format(
                    "Missing parent Computes: %s", missingComputeLinks));
        }
    }

    /**
     * For each dependency go over the dependency's exposed port, and for each port generate a set
     * of environment variables
     *
     * @return
     */
    @Override
    public Set<String> generateInternalServiceLinksByContainerName(String containerName) {
        Set<String> serviceLinks = new HashSet<>();

        depAliases.forEach((depName, alias) -> {
            List<ContainerState> list = depContainerStates.get(depName);
            list.forEach((containerState) -> {
                String hostAddress = depComputeAddresses.get(containerState.parentLink);

                for (PortBinding mapping : containerState.ports) {
                    String servicePort = mapping.containerPort;
                    String hostPort = mapping.hostPort;

                    serviceLinks.add(String.format("%s:%s:%s:%s", containerName, servicePort,
                            hostAddress, hostPort));
                }
            });
        });

        return serviceLinks;
    }

    /**
     * For each dependency add host mapping
     *
     * @return
     */
    public Map<String, String> generateExtraHosts(String containerNameParentLink) {
        Map<String, String> extraHosts = new HashMap<>();

        depAliases.forEach((depName, alias) -> {
            String address = hostNetworkAgentAddresses.get(containerNameParentLink);
            extraHosts.put(alias, address);
        });

        return extraHosts;
    }

    public Collection<String> getDepParentComputeLinks() {
        Set<String> depParentComputeLinks = new HashSet<>();
        depAliases.forEach((depName, alias) -> {
            List<ContainerState> list = depContainerStates.get(depName);
            list.forEach((containerState) -> {
                depParentComputeLinks.add(containerState.parentLink);
            });
        });

        return depParentComputeLinks;
    }
}
