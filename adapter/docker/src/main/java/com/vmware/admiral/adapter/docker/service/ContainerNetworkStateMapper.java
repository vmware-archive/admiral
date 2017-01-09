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

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_CONTAINERS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_DRIVER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_ID_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_AUX_ADDRESSES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_GATEWAY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_IP_RANGE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_SUBNET_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_DRIVER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_OPTIONS_PROP_NAME;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState.PowerState;
import com.vmware.admiral.compute.container.network.Ipam;
import com.vmware.admiral.compute.container.network.IpamConfig;

/**
 * Map properties into ContainerNetworkState
 */
public class ContainerNetworkStateMapper {

    private static final Pattern PATTERN_GATEWAY_STRIP_CIDR_SUFFIX = Pattern
            .compile("([^\\/]*)(\\/.*)?");
    private static final int GATEWAY_CAPTURING_GROUP_INDEX = 1;

    /**
     * Convert generic properties from the given {@link Map} to modeled properties in the given
     * {@link ContainerNetworkState}.
     *
     * @param networkState
     * @param properties
     */
    public static void propertiesToContainerNetworkState(ContainerNetworkState networkState,
            Map<String, Object> properties) {

        AssertUtil.assertNotNull(networkState, "networkState");
        AssertUtil.assertNotNull(properties, "properties");

        networkState.id = (String) properties.get(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME);
        networkState.name = (String) properties.get(DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME);
        networkState.driver = (String) properties.get(DOCKER_CONTAINER_NETWORK_DRIVER_PROP_NAME);

        networkState.powerState = PowerState.CONNECTED;

        networkState.options = getMap(properties, DOCKER_CONTAINER_NETWORK_OPTIONS_PROP_NAME);
        Map<String, Object> ipamProperties = getMap(properties,
                DOCKER_CONTAINER_NETWORK_IPAM_PROP_NAME);
        mapIpamConfiguration(networkState, ipamProperties);

        mapConnectedContainers(networkState,
                getMap(properties, DOCKER_CONTAINER_NETWORK_CONTAINERS_PROP_NAME));
    }

    private static void mapIpamConfiguration(ContainerNetworkState networkState,
            Map<String, Object> ipamProperties) {
        if (ipamProperties == null) {
            return;
        }

        if (networkState.ipam == null) {
            networkState.ipam = new Ipam();
        }
        networkState.ipam.driver = (String) ipamProperties
                .get(DOCKER_CONTAINER_NETWORK_IPAM_DRIVER_PROP_NAME);
        networkState.ipam.config = getIpamConfig(ipamProperties);
    }

    private static IpamConfig[] getIpamConfig(Map<String, Object> ipamProperties) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> configEntries = (List<Map<String, Object>>) ipamProperties
                .get(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_PROP_NAME);
        IpamConfig[] result = new IpamConfig[configEntries.size()];
        for (int i = 0; i < configEntries.size(); i++) {
            Map<String, Object> entry = configEntries.get(i);
            result[i] = new IpamConfig();

            result[i].subnet = (String) entry
                    .get(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_SUBNET_PROP_NAME);
            result[i].gateway = stripCidrSuffixFromGateway((String) entry
                    .get(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_GATEWAY_PROP_NAME));
            result[i].ipRange = (String) entry
                    .get(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_IP_RANGE_PROP_NAME);
            result[i].auxAddresses = getMap(ipamProperties,
                    DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_AUX_ADDRESSES_PROP_NAME);
        }

        return result;
    }

    private static void mapConnectedContainers(ContainerNetworkState networkState,
            Map<String, Object> containers) {

        if (containers == null) {
            networkState.connectedContainersCount = 0;
        } else {
            networkState.connectedContainersCount = containers.size();
        }
    }

    /*
     * TODO Docker returns the network gateway in a CIDR notation (like 172.20.0.1/16). This method
     * strips the /16 part. It should be removed (or changed) when this issue gets resolved:
     * https://github.com/docker/docker/issues/26522
     *
     * @param gateway
     *            the gateway address like it was returned from docker
     * @return if <code>gateway</code> is <code>null</code>, then <code>null</code> is returned.
     *         Otherwise, if the <code>gateway</code> matches [^\]/.*, only the text before the
     *         slashed is returned. Otherwise, the <code>gateway</code> is returned unchanged
     * @see https://github.com/docker/docker/issues/26522
     */
    private static String stripCidrSuffixFromGateway(String gateway) {
        if (gateway == null) {
            return gateway;
        }

        Matcher matcher = PATTERN_GATEWAY_STRIP_CIDR_SUFFIX.matcher(gateway);

        if (!matcher.matches()) {
            return gateway;
        } else {
            return matcher.group(GATEWAY_CAPTURING_GROUP_INDEX);
        }
    }

    private static <T> Map<String, T> getMap(Map<String, Object> properties, String propertyName) {
        @SuppressWarnings("unchecked")
        Map<String, T> map = (Map<String, T>) properties.get(propertyName);

        return map;
    }

}
