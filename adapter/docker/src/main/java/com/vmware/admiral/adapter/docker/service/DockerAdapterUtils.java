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

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_AUX_ADDRESSES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_GATEWAY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_IP_RANGE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_SUBNET_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_DRIVER_PROP_NAME;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.compute.container.network.Ipam;
import com.vmware.admiral.compute.container.network.IpamConfig;

public class DockerAdapterUtils {

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

}
