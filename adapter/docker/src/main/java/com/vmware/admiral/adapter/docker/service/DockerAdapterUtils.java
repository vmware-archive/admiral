/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_AUX_ADDRESSES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_GATEWAY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_IP_RANGE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_SUBNET_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_DRIVER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_PORT_BINDINGS_PROP_NAME;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.Ipam;
import com.vmware.admiral.compute.container.network.IpamConfig;
import com.vmware.admiral.compute.container.volume.VolumeBinding;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;

public class DockerAdapterUtils {

    private DockerAdapterUtils() {
    }

    public static Map<String, Object> ipamToMap(Ipam ipam) {
        HashMap<String, Object> result = new HashMap<>();

        if (ipam.driver != null && !ipam.driver.isEmpty()) {
            result.put(DOCKER_CONTAINER_NETWORK_IPAM_DRIVER_PROP_NAME, ipam.driver);
        }
        if (ipam.config != null) {
            ArrayList<Map<String, Object>> ipamConfig = new ArrayList<>(ipam.config.length);

            for (IpamConfig config : ipam.config) {
                ipamConfig.add(ipamConfigToMap(config));
            }

            result.put(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_PROP_NAME, ipamConfig);
        }

        return result;
    }

    public static Map<String, Object> ipamConfigToMap(IpamConfig config) {
        HashMap<String, Object> result = new HashMap<>();

        if (config.subnet != null && !config.subnet.isEmpty()) {
            result.put(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_SUBNET_PROP_NAME, config.subnet);
        }
        if (config.gateway != null && !config.gateway.isEmpty()) {
            result.put(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_GATEWAY_PROP_NAME, config.gateway);
        }
        if (config.ipRange != null && !config.ipRange.isEmpty()) {
            result.put(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_IP_RANGE_PROP_NAME, config.ipRange);
        }
        if (config.auxAddresses != null && !config.auxAddresses.isEmpty()) {
            result.put(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_AUX_ADDRESSES_PROP_NAME,
                    config.auxAddresses);
        }

        return result;
    }

    public static void mapContainerNetworkToNetworkConfig(ServiceNetwork network,
            Map<String, Object> endpointConfig) {
        Map<String, Object> ipamConfig = new HashMap<>();
        if (network.ipv4_address != null) {
            ipamConfig.put(
                    DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG.IPV4_CONFIG,
                    network.ipv4_address);
        }

        if (network.ipv6_address != null) {
            ipamConfig.put(
                    DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG.IPV6_CONFIG,
                    network.ipv6_address);
        }

        if (!ipamConfig.isEmpty()) {
            endpointConfig.put(
                    DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG_PROP_NAME,
                    ipamConfig);
        }

        if (network.aliases != null) {
            endpointConfig.put(
                    DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.ALIASES,
                    network.aliases);
        }

        if (network.links != null) {
            endpointConfig.put(
                    DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.LINKS,
                    network.links);
        }
    }

    /**
     * Filter out volume bindings without host-src or volume name. Each volume binding is a
     * string in the following form: [volume-name|host-src:]container-dest[:ro] Both host-src,
     * and container-dest must be an absolute path.
     */
    public static List<String> filterVolumeBindings(String[] volumes) {
        List<String> volumeBindings = new ArrayList<>();
        if (volumes != null) {
            for (String volume : volumes) {
                VolumeBinding binding = VolumeBinding.fromString(volume);
                if (binding.getHostPart() != null) {
                    volumeBindings.add(volume);
                }
            }
        }
        return volumeBindings;
    }

    /**
     * Sometimes docker error message can be large and cause serialization errors,
     * this method will extract the lines which contains error information.
     */
    public static String normalizeDockerError(String error) {
        String errorDetailLine = "{\"errorDetail\":";
        String[] splitError = error.split("\n");
        StringBuilder errorToReturn = new StringBuilder();

        for (String line : splitError) {
            if (line.startsWith(errorDetailLine)) {
                errorToReturn.append(line);
                errorToReturn.append("\n");
            }
        }

        String errorLines = errorToReturn.toString().trim();

        // If no error information is extracted, the parameter format
        // is unknown, we better return the original error in order not to lose data.
        if (errorLines.isEmpty()) {
            return error;
        }
        return errorLines;
    }

    /**
     * Constructs an {@link Exception} that contains the Docker failure response as a message and
     * has the original {@link Throwable} as a cause.
     */
    public static Exception exceptionFromFailedDockerOperation(Operation failedOperation,
            Throwable failure) {
        String reason;
        if (failedOperation != null && failedOperation.getBodyRaw() != null) {
            reason = Utils.toJson(normalizeDockerError(failedOperation.getBody(String.class)));
        } else {
            reason = "Unknown reason.";
        }

        String failureMessage = (failure == null) ? "Unknown failure" : failure.getMessage();
        String errMsg = String.format("%s; Reason: %s", failureMessage, reason);
        return new Exception(errMsg, failure);
    }

    /**
     * Constructs a {@link RuntimeException} that contains the Docker failure response as a message
     * and has the original {@link Throwable} as a cause.
     */
    public static RuntimeException runtimeExceptionFromFailedDockerOperation(
            Operation failedOperation, Throwable failure) {
        Exception checkedException = exceptionFromFailedDockerOperation(failedOperation, failure);
        return new RuntimeException(checkedException.getMessage(), checkedException.getCause());
    }

    /**
     * Filters the empty port bindings from the host config section retrieved when inspecting a
     * container deployed on a VCH and connected to a network which uses the "external" driver. See
     * Github issue #228.
     *
     * @param inspectMap
     *            Result of the container INSPECT command
     */
    @SuppressWarnings("unchecked")
    public static void filterHostConfigEmptyPortBindings(Map<String, Object> inspectMap) {
        if (inspectMap == null) {
            return;
        }

        Map<String, Object> hostConfig = (Map<String, Object>) inspectMap
                .get(DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME);
        if (hostConfig == null) {
            return;
        }

        Map<String, List<Map<String, String>>> portBindings = (Map<String, List<Map<String, String>>>) hostConfig
                .get(DOCKER_CONTAINER_PORT_BINDINGS_PROP_NAME);
        if (portBindings == null) {
            return;
        }

        Iterator<Entry<String, List<Map<String, String>>>> portBindingsIt = portBindings.entrySet()
                .iterator();
        while (portBindingsIt.hasNext()) {
            List<Map<String, String>> portBindingValue = portBindingsIt.next().getValue();

            Iterator<Map<String, String>> portBindingValueIt = portBindingValue.listIterator();
            while (portBindingValueIt.hasNext()) {
                Map<String, String> portBinding = portBindingValueIt.next();
                if ("".equals(portBinding.getOrDefault("HostIp", ""))
                        && "".equals(portBinding.getOrDefault("HostPort", ""))) {
                    portBindingValueIt.remove();
                }
            }

            if (portBindingValue.isEmpty()) {
                portBindingsIt.remove();
            }
        }
    }

}
