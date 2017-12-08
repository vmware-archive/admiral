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

package com.vmware.admiral.adapter.docker.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_AUX_ADDRESSES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_GATEWAY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_IP_RANGE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_SUBNET_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_IPAM_DRIVER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_PORT_BINDINGS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterUtils.normalizeDockerError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG;
import com.vmware.admiral.adapter.docker.service.DockerAdapterUtils;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.Ipam;
import com.vmware.admiral.compute.container.network.IpamConfig;

public class DockerAdapterUtilsTest {

    @Test
    public void testNormalizeDockerErrorWithKnowInput() {

        String sampleInput = "{\"status\":\"Extracting\",\"progressDetail\":{\"current\":20414464,"
                + "\"total\":21686587},\"progress\":\"[=========================================="
                + "=====\\u003e   ]  20.41MB/21.69MB\",\"id\":\"c09556af9686\"}\n"
                + "{\"status\":\"Extracting\",\"progressDetail\":{\"current\":21331968,\"total\""
                + ":21686587},\"progress\":\"[=================================================\\"
                + "u003e ]  21.33MB/21.69MB\",\"id\":\"c09556af9686\"}\n"
                + "{\"status\":\"Extracting\",\"progressDetail\":{\"current\":21561344,\"total\":"
                + "21686587},\"progress\":\"[=================================================\\"
                + "u003e ]  21.56MB/21.69MB\",\"id\":\"c09556af9686\"}\n"
                + "{\"status\":\"Extracting\",\"progressDetail\":{\"current\":21686587,\"total\":"
                + "21686587},\"progress\":\"[==================================================\\"
                + "u003e]  21.69MB/21.69MB\",\"id\":\"c09556af9686\"}\n"
                + "{\"errorDetail\":{\"message\":\"failed to register layer: Error processing tar "
                + "file(exit status 1): write /usr/local/lib/python2.7/lib-dynload/_io.so: no space"
                + " left on device\"},\"error\":\"failed to register layer: Error processing tar "
                + "file(exit status 1): write /usr/local/lib/python2.7/lib-dynload/_io.so: no"
                + " space left on device\"}";

        String expectedOutput = "{\"errorDetail\":{\"message\":\"failed to register layer: Error "
                + "processing tar file(exit status 1): write /usr/local/lib/python2.7/lib-dynload/_"
                + "io.so: no space left on device\"},\"error\":\"failed to register layer: "
                + "Error processing tar file(exit status 1): write /usr/local/lib/python2.7/"
                + "lib-dynload/_io.so: no space left on device\"}";

        String actualOutput = normalizeDockerError(sampleInput);

        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testNormalizeDockerErrorWithUnknownInput() {
        String sampleInput = "Sample string that shouldn't be modified.";
        String expectedOutput = sampleInput;
        String actualOutput = normalizeDockerError(sampleInput);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testIpamToMap() {
        Ipam ipam = new Ipam();
        Map<String, Object> ipamMap = DockerAdapterUtils.ipamToMap(ipam);
        assertTrue(ipamMap.isEmpty());

        ipam.driver = "";
        ipam.config = new IpamConfig[] {};
        ipamMap = DockerAdapterUtils.ipamToMap(ipam);
        assertEquals(1, ipamMap.size());
        assertEquals(Collections.emptyList(),
                ipamMap.get(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_PROP_NAME));

        ipam.driver = "overlay";

        IpamConfig config = new IpamConfig();
        config.subnet = "172.28.0.0/16";
        config.gateway = "172.28.5.254";
        config.ipRange = "172.28.5.0/24";
        config.auxAddresses = new HashMap<>();
        config.auxAddresses.put("my-router", "192.168.1.5");

        ipam.config = new IpamConfig[] { config };
        ipamMap = DockerAdapterUtils.ipamToMap(ipam);
        assertEquals(2, ipamMap.size());
        assertEquals(ipam.driver,
                ipamMap.get(DOCKER_CONTAINER_NETWORK_IPAM_DRIVER_PROP_NAME));
        assertNotEquals(Collections.emptyList(),
                ipamMap.get(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_PROP_NAME));
    }

    @Test
    public void testIpamConfigToMap() {
        IpamConfig config = new IpamConfig();
        Map<String, Object> configMap = DockerAdapterUtils.ipamConfigToMap(config);
        assertTrue(configMap.isEmpty());

        config.subnet = "";
        config.gateway = "";
        config.ipRange = "";
        config.auxAddresses = new HashMap<>();
        configMap = DockerAdapterUtils.ipamConfigToMap(config);
        assertTrue(configMap.isEmpty());

        config.subnet = "172.28.0.0/16";
        config.gateway = "172.28.5.254";
        config.ipRange = "172.28.5.0/24";
        config.auxAddresses.put("my-router", "192.168.1.5");
        configMap = DockerAdapterUtils.ipamConfigToMap(config);
        assertEquals(4, configMap.size());
        assertEquals(config.subnet,
                configMap.get(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_SUBNET_PROP_NAME));
        assertEquals(config.gateway,
                configMap.get(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_GATEWAY_PROP_NAME));
        assertEquals(config.ipRange,
                configMap.get(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_IP_RANGE_PROP_NAME));
        assertEquals(config.auxAddresses,
                configMap.get(DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_AUX_ADDRESSES_PROP_NAME));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMapContainerNetworkToNetworkConfig() {
        ServiceNetwork network = new ServiceNetwork();
        Map<String, Object> endpointConfig = new HashMap<>();
        DockerAdapterUtils.mapContainerNetworkToNetworkConfig(network, endpointConfig);
        assertNull(endpointConfig.get(
                DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG_PROP_NAME));
        assertNull(endpointConfig.get(
                DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.ALIASES));
        assertNull(endpointConfig.get(
                DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.LINKS));

        network.ipv4_address = "foo";
        network.ipv6_address = "bar";
        network.aliases = new String[] { "alias1", "alias2", "alias3" };
        network.links = new String[] { "link1", "link2", "link3" };
        DockerAdapterUtils.mapContainerNetworkToNetworkConfig(network, endpointConfig);
        assertNotNull(endpointConfig.get(
                DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG_PROP_NAME));
        assertEquals(network.ipv4_address, ((Map<String, Object>) endpointConfig
                .get(DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG_PROP_NAME))
                        .get(DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG.IPV4_CONFIG));
        assertEquals(network.ipv6_address, ((Map<String, Object>) endpointConfig
                .get(DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG_PROP_NAME))
                        .get(DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG.IPV6_CONFIG));
        assertEquals(network.aliases, endpointConfig.get(
                DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.ALIASES));
        assertEquals(network.links, endpointConfig.get(
                DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.LINKS));
    }

    @Test
    public void testFilterVolumeBindings() {
        List<String> bindings = DockerAdapterUtils.filterVolumeBindings(null);
        assertEquals(Collections.emptyList(), bindings);

        String[] volumes = new String[] {};
        bindings = DockerAdapterUtils.filterVolumeBindings(volumes);
        assertEquals(Collections.emptyList(), bindings);

        volumes = new String[] { "/foo:/bar", "/foobar" };
        bindings = DockerAdapterUtils.filterVolumeBindings(volumes);
        assertEquals(1, bindings.size());
        assertEquals("/foo:/bar", bindings.get(0));
    }

    @Test
    public void testFilterHostConfigEmptyPortBindings() {

        // nothing to do
        Map<String, Object> inspectMap = null;
        DockerAdapterUtils.filterHostConfigEmptyPortBindings(inspectMap);
        assertNull(inspectMap);

        // nothing to do
        inspectMap = new HashMap<>();
        DockerAdapterUtils.filterHostConfigEmptyPortBindings(inspectMap);
        assertTrue(inspectMap.isEmpty());

        inspectMap.put("foo", "bar");
        DockerAdapterUtils.filterHostConfigEmptyPortBindings(inspectMap);
        assertFalse(inspectMap.isEmpty());
        assertEquals(null, inspectMap.get(DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME));

        // nothing to do
        Map<String, Object> hostConfig = new HashMap<>();
        inspectMap.put(DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME, hostConfig);
        DockerAdapterUtils.filterHostConfigEmptyPortBindings(inspectMap);
        assertTrue(hostConfig.isEmpty());
        assertFalse(inspectMap.isEmpty());

        hostConfig.put("foo", "bar");
        DockerAdapterUtils.filterHostConfigEmptyPortBindings(inspectMap);
        assertFalse(hostConfig.isEmpty());
        assertEquals(null, hostConfig.get(DOCKER_CONTAINER_PORT_BINDINGS_PROP_NAME));
        assertFalse(inspectMap.isEmpty());

        // nothing to do
        Map<String, Object> portBindings = new HashMap<>();
        hostConfig.put(DOCKER_CONTAINER_PORT_BINDINGS_PROP_NAME, portBindings);
        DockerAdapterUtils.filterHostConfigEmptyPortBindings(inspectMap);
        assertTrue(portBindings.isEmpty());
        assertFalse(hostConfig.isEmpty());
        assertFalse(inspectMap.isEmpty());

        // filter empty port bindings
        portBindings.put("pb1", newPortBinding("127.0.0.1", "8484"));
        portBindings.put("pb2", newPortBinding(null, "8484"));
        portBindings.put("pb3", newPortBinding("127.0.0.1", null));
        portBindings.put("pb4", newPortBinding(null, null));
        portBindings.put("pb5", newPortBinding(null, ""));
        portBindings.put("pb6", newPortBinding("", null));
        portBindings.put("pb7", newPortBinding("", ""));
        portBindings.put("pb8", new ArrayList<>());
        DockerAdapterUtils.filterHostConfigEmptyPortBindings(inspectMap);
        assertEquals(3, portBindings.size());
        assertNotNull(portBindings.get("pb1"));
        assertNotNull(portBindings.get("pb2"));
        assertNotNull(portBindings.get("pb3"));
    }

    private List<Map<String, String>> newPortBinding(String ip, String port) {
        Map<String, String> pb = new HashMap<>();
        if (ip != null) {
            pb.put("HostIp", ip);
        }
        if (port != null) {
            pb.put("HostPort", port);
        }
        List<Map<String, String>> list = new ArrayList<>();
        list.add(pb);
        return list;
    }

}
