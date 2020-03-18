/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_COMMAND_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_CREATED_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_IMAGE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_INSPECT_NETWORKS_PROPS.DOCKER_CONTAINER_NETWORK_ALIASES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_INSPECT_NETWORKS_PROPS.DOCKER_CONTAINER_NETWORK_IPV4_ADDRESS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_INSPECT_NETWORKS_PROPS.DOCKER_CONTAINER_NETWORK_IPV6_ADDRESS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_INSPECT_NETWORKS_PROPS.DOCKER_CONTAINER_NETWORK_LINKS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_SETTINGS_IP_ADDRESS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_SETTINGS_NETWORKS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_SETTINGS_PORTS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_SETTINGS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_STATE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_STATE_RUNNING_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_STATE_STARTED_PROP_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.ServiceNetwork;

public class ContainerStateMapperTest {

    private static final String HOST_IP_PROP_NAME = "HostIp";
    private static final String HOST_PORT_PROP_NAME = "HostPort";

    private static final String BRIDGE_NETWORK_NAME = "bridge";

    private static final String DEFAULT_CONTAINER_ID = "test-container-id";
    private static final String DEFAULT_CONTAINER_NAME = "test-container-name";
    private static final String DEFAULT_CONTAINER_CREATED_TIME = "2016-11-24T12:01:02.123456789Z";
    private static final boolean DEFAULT_CONTAINER_RUNNING_STATE = true;
    private static final String DEFAULT_CONTAINER_STARTED_TIME = "2016-11-24T12:02:01.987654321Z";
    private static final String DEFAULT_CONTAINER_IMAGE = "alpine";
    private static final String DEFAULT_CONTAINER_BRIDGE_NETWORK_IPV4_ADDRESS = "172.17.0.2";
    private static final String DEFAULT_CONTAINER_BRIDGE_NETWORK_IPV6_ADDRESS = "";
    private static final String DEFAULT_CONTAINER_EXPOSED_CONTAINER_PORT = "80/tcp";
    private static final String DEFAULT_CONTAINER_PUBLISHED_HOST_PORT = "80";
    private static final String DEFAULT_CONTAINER_PUBLISHED_HOST_IP = "0.0.0.0";

    private static final String[] DEFAULT_CONTAINER_BRIDGE_NETWORK_LINKS_ARRAY = new String[] {
            "container:alias" };
    private static final String[] DEFAULT_CONTAINER_BRIDGE_NETWORK_ALIASES_ARRAY = new String[] {
            "alias" };
    private static final String[] DEFAULT_CONTAINER_COMMAND_ARRAY = new String[] { "sh" };

    private ContainerState predefinedState;
    private Map<String, Object> predefinedInspectMap;

    @Before
    public void setUp() {
        predefinedState = getDefaultContainerState();
        predefinedInspectMap = getDefaultContainerInspectResponseAsMap();
    }

    @Test
    public void testParseDate() {
        String iso8601WithMicroseconds = "2015-08-26T20:57:44.715343657Z";
        String iso8601WithMilliseconds = "2015-08-26T20:57:44.715Z";
        String vicDateTime = "2015-08-26 20:57:44 +0000 UTC";

        Long parsed = ContainerStateMapper.parseDate(iso8601WithMicroseconds);
        Instant fromEpochMilli = Instant.ofEpochMilli(parsed);

        // Assert that microseconds are ignored in the context of epoch millisecond
        assertEquals(iso8601WithMilliseconds, fromEpochMilli.toString());

        // test workaround datetime parser for VIC host
        Long vic = ContainerStateMapper.parseDate(vicDateTime);
        assertEquals(715, parsed - vic);
    }

    @Test
    public void testPatchUnhealthyContainerState() {

        // When the health check fails for a given container, its power state is marked as 'ERROR'
        // and its status as 'unhealthy'. See ContainerHealthEvaluator.patchContainerStatus.
        predefinedState.powerState = PowerState.ERROR;
        predefinedState.status = ContainerState.CONTAINER_UNHEALTHY_STATUS;

        ContainerState mappedState = new ContainerState();
        mappedState.powerState = predefinedState.powerState;
        mappedState.status = predefinedState.status;

        ContainerStateMapper.propertiesToContainerState(mappedState, predefinedInspectMap);

        assertEquals(PowerState.ERROR, mappedState.powerState);
        assertEquals(ContainerState.CONTAINER_UNHEALTHY_STATUS, mappedState.status);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPatchHealthyContainerState() {

        // Set running state to true
        ((Map<String, Object>) predefinedInspectMap.get(DOCKER_CONTAINER_STATE_PROP_NAME))
                .put(DOCKER_CONTAINER_STATE_RUNNING_PROP_NAME, true);

        ContainerState mappedState = new ContainerState();
        ContainerStateMapper.propertiesToContainerState(mappedState, predefinedInspectMap);

        assertEquals(PowerState.RUNNING, mappedState.powerState);
        assertEquals(null, mappedState.status);

        // Set running state to false
        ((Map<String, Object>) predefinedInspectMap.get(DOCKER_CONTAINER_STATE_PROP_NAME))
                .put(DOCKER_CONTAINER_STATE_RUNNING_PROP_NAME, false);

        mappedState = new ContainerState();
        ContainerStateMapper.propertiesToContainerState(mappedState, predefinedInspectMap);

        assertEquals(PowerState.STOPPED, mappedState.powerState);
        assertEquals(null, mappedState.status);

        // Set running state to true... after the power state has been set to error
        ((Map<String, Object>) predefinedInspectMap.get(DOCKER_CONTAINER_STATE_PROP_NAME))
                .put(DOCKER_CONTAINER_STATE_RUNNING_PROP_NAME, true);

        mappedState = new ContainerState();
        mappedState.status = ContainerState.CONTAINER_DEGRADED_STATUS; // degraded but healthy
        ContainerStateMapper.propertiesToContainerState(mappedState, predefinedInspectMap);

        assertEquals(PowerState.RUNNING, mappedState.powerState);
        assertEquals(ContainerState.CONTAINER_DEGRADED_STATUS, mappedState.status);
    }

    /**
     * Build a {@link ContainerState} instance that has a user-defined network and the
     * system-defined "bridge" network. Also build the inspect command map representation and use
     * the {@link ContainerStateMapper} to produce the mapped {@link ContainerState}. Both states
     * should be identical.
     */
    @Test
    public void testCompareRunningContainerInUserNetworkAndMatchedContainerStateShouldPass() {
        String networkName = "test-network";
        String ipv4 = "172.100.0.100";
        String ipv6 = "fe80::aa";
        String[] aliases = new String[] { "alias" };
        String[] links = new String[] { "service:alias" };

        predefinedState.networks.put(networkName,
                createServiceNetwork(networkName, ipv4, ipv6, aliases, links));
        addNetworkToInspectMap(predefinedInspectMap, networkName, ipv4, ipv6, aliases, links);

        ContainerState mappedState = new ContainerState();
        ContainerStateMapper.propertiesToContainerState(mappedState, predefinedInspectMap);
        assertTrue("predefined and mapped state should be equal",
                areEqualContainerStates(predefinedState, mappedState));
    }

    /**
     * Build a {@link ContainerState} instance that has a user-defined network and the
     * system-defined "bridge" network. Also build the inspect command map representation and use
     * the {@link ContainerStateMapper} to produce the mapped {@link ContainerState}. Both states
     * should be identical.
     *
     * Note: in this test only the mandatory network properties (name and IPv4 address) are set and
     * all the others are set to <code>null</code>. Test should pass again.
     */
    @Test
    public void testCompareRunningContainerInUserNetworkWithDefaultPropertiesAndMatchedContainerStateShouldPass() {
        String networkName = "test-network";
        String ipv4 = "172.100.0.100";

        predefinedState.networks.put(networkName,
                createServiceNetwork(networkName, ipv4));
        addNetworkToInspectMap(predefinedInspectMap, networkName, ipv4);

        ContainerState mappedState = new ContainerState();
        ContainerStateMapper.propertiesToContainerState(mappedState, predefinedInspectMap);
        assertTrue("predefined and mapped state should be equal",
                areEqualContainerStates(predefinedState, mappedState));
    }

    /**
     * Creates a {@link ServiceNetwork} instance with the specified name and IPv4 address. All other
     * properties (IPv6 address, aliases, links) are <code>null</code>
     */
    private ServiceNetwork createServiceNetwork(String name, String ipv4) {
        return createServiceNetwork(name, ipv4, null, null, null);
    }

    /**
     * Creates a {@link ServiceNetwork} instance with the provided properties
     */
    private ServiceNetwork createServiceNetwork(String name, String ipv4, String ipv6,
            String[] aliases, String[] links) {
        ServiceNetwork network = new ServiceNetwork();
        network.name = name;
        network.ipv4_address = ipv4;
        network.ipv6_address = ipv6;
        network.aliases = aliases;
        network.links = links;
        return network;
    }

    /**
     * Returns a part of a docker inspect command output that contains all the info about a single
     * container
     */
    private Map<String, Object> getDefaultContainerInspectResponseAsMap() {
        Map<String, Object> containerInspect = new HashMap<>();

        containerInspect.put(DOCKER_CONTAINER_ID_PROP_NAME, DEFAULT_CONTAINER_ID);
        // container inspect returns this with a forwar slash prefix
        containerInspect.put(DOCKER_CONTAINER_NAME_PROP_NAME, "/" + DEFAULT_CONTAINER_NAME);
        containerInspect.put(DOCKER_CONTAINER_CREATED_PROP_NAME, DEFAULT_CONTAINER_CREATED_TIME);

        Map<String, Object> state = new HashMap<>();
        state.put(DOCKER_CONTAINER_STATE_RUNNING_PROP_NAME, DEFAULT_CONTAINER_RUNNING_STATE);
        state.put(DOCKER_CONTAINER_STATE_STARTED_PROP_NAME, DEFAULT_CONTAINER_STARTED_TIME);
        containerInspect.put(DOCKER_CONTAINER_STATE_PROP_NAME, state);

        Map<String, Object> config = new HashMap<>();
        config.put(DOCKER_CONTAINER_COMMAND_PROP_NAME,
                Arrays.asList(DEFAULT_CONTAINER_COMMAND_ARRAY));
        config.put(DOCKER_CONTAINER_IMAGE_PROP_NAME, DEFAULT_CONTAINER_IMAGE);
        containerInspect.put(DOCKER_CONTAINER_CONFIG_PROP_NAME, config);

        Map<String, Object> networkSettings = new HashMap<>();
        networkSettings.put(DOCKER_CONTAINER_NETWORK_SETTINGS_IP_ADDRESS_PROP_NAME,
                DEFAULT_CONTAINER_BRIDGE_NETWORK_IPV4_ADDRESS);
        networkSettings.put(DOCKER_CONTAINER_NETWORK_SETTINGS_PORTS_PROP_NAME,
                getDefaultContainerPortMappings());
        containerInspect.put(DOCKER_CONTAINER_NETWORK_SETTINGS_PROP_NAME, networkSettings);
        // add connection to bridge network
        addNetworkToInspectMap(containerInspect, BRIDGE_NETWORK_NAME,
                DEFAULT_CONTAINER_BRIDGE_NETWORK_IPV4_ADDRESS,
                DEFAULT_CONTAINER_BRIDGE_NETWORK_IPV6_ADDRESS,
                DEFAULT_CONTAINER_BRIDGE_NETWORK_ALIASES_ARRAY,
                DEFAULT_CONTAINER_BRIDGE_NETWORK_LINKS_ARRAY);

        return containerInspect;
    }

    /**
     * Returns the port mappings for the default container in a {@link Map} instance that is
     * suitable to be plugged in the inspect map.
     */
    private Map<String, List<Map<String, String>>> getDefaultContainerPortMappings() {
        Map<String, List<Map<String, String>>> portMappings = new HashMap<>();

        Map<String, String> mapping = new HashMap<>();
        mapping.put(HOST_IP_PROP_NAME, DEFAULT_CONTAINER_PUBLISHED_HOST_IP);
        mapping.put(HOST_PORT_PROP_NAME, DEFAULT_CONTAINER_PUBLISHED_HOST_PORT);

        portMappings.put(DEFAULT_CONTAINER_EXPOSED_CONTAINER_PORT,
                Collections.singletonList(mapping));
        return portMappings;
    }

    /**
     * Adds a network definition with the provided name and IPv4 address to the specified inspect
     * map. All other properties (IPv6, aliases, links) are <code>null</code>.
     */
    private void addNetworkToInspectMap(Map<String, Object> inspectMap, String networkName,
            String ipv4) {
        addNetworkToInspectMap(inspectMap, networkName, ipv4, null, null, null);
    }

    /**
     * Adds a network definition with the provided properties in the specified inspect map
     */
    private void addNetworkToInspectMap(Map<String, Object> inspectMap, String networkName,
            String ipv4, String ipv6, String[] aliases, String[] links) {
        @SuppressWarnings("unchecked")
        Map<String, Object> networkSettings = (Map<String, Object>) inspectMap
                .get(DOCKER_CONTAINER_NETWORK_SETTINGS_PROP_NAME);
        if (networkSettings == null) {
            networkSettings = new HashMap<>();
            inspectMap.put(DOCKER_CONTAINER_NETWORK_SETTINGS_PROP_NAME, networkSettings);
        }
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> networks = (Map<String, Map<String, Object>>) networkSettings
                .get(DOCKER_CONTAINER_NETWORK_SETTINGS_NETWORKS_PROP_NAME);
        if (networks == null) {
            networks = new HashMap<>();
            networkSettings.put(DOCKER_CONTAINER_NETWORK_SETTINGS_NETWORKS_PROP_NAME, networks);
        }

        Map<String, Object> newNetwork = new HashMap<>();
        newNetwork.put(DOCKER_CONTAINER_NETWORK_LINKS_PROP_NAME,
                links == null ? null : Arrays.asList(links));
        newNetwork.put(DOCKER_CONTAINER_NETWORK_ALIASES_PROP_NAME,
                aliases == null ? null : Arrays.asList(aliases));
        newNetwork.put(DOCKER_CONTAINER_NETWORK_IPV4_ADDRESS_PROP_NAME, ipv4);
        newNetwork.put(DOCKER_CONTAINER_NETWORK_IPV6_ADDRESS_PROP_NAME, ipv6);

        networks.put(networkName, newNetwork);
    }

    /**
     * Creates and returns a {@link ContainerState} instance that represents the default container
     * used in this tests.
     */
    private ContainerState getDefaultContainerState() {
        ContainerState containerState = new ContainerState();

        containerState.id = DEFAULT_CONTAINER_ID;
        containerState.names = Collections.singletonList(DEFAULT_CONTAINER_NAME);
        containerState.created = Instant.parse(DEFAULT_CONTAINER_CREATED_TIME).toEpochMilli();
        containerState.powerState = PowerState.RUNNING;
        containerState.started = Instant.parse(DEFAULT_CONTAINER_STARTED_TIME).toEpochMilli();
        containerState.image = DEFAULT_CONTAINER_IMAGE;
        containerState.address = DEFAULT_CONTAINER_BRIDGE_NETWORK_IPV4_ADDRESS;
        containerState.command = DEFAULT_CONTAINER_COMMAND_ARRAY;

        PortBinding portBinding = new PortBinding();
        portBinding.hostIp = DEFAULT_CONTAINER_PUBLISHED_HOST_IP;
        portBinding.hostPort = DEFAULT_CONTAINER_PUBLISHED_HOST_PORT;
        containerState.ports = Collections.singletonList(portBinding);
        // strip protocol part of the port definition
        int protocolDefIndex = DEFAULT_CONTAINER_EXPOSED_CONTAINER_PORT.indexOf('/');
        portBinding.containerPort = DEFAULT_CONTAINER_EXPOSED_CONTAINER_PORT.substring(0,
                protocolDefIndex);
        portBinding.protocol = DEFAULT_CONTAINER_EXPOSED_CONTAINER_PORT
                .substring(protocolDefIndex + 1);

        // The default container is connected to the bridge network
        containerState.networks = new HashMap<>(0);
        ServiceNetwork bridgeNetwork = createServiceNetwork(BRIDGE_NETWORK_NAME,
                DEFAULT_CONTAINER_BRIDGE_NETWORK_IPV4_ADDRESS,
                DEFAULT_CONTAINER_BRIDGE_NETWORK_IPV6_ADDRESS,
                DEFAULT_CONTAINER_BRIDGE_NETWORK_ALIASES_ARRAY,
                DEFAULT_CONTAINER_BRIDGE_NETWORK_LINKS_ARRAY);
        containerState.networks.put(BRIDGE_NETWORK_NAME, bridgeNetwork);

        return containerState;
    }

    /**
     * Compare the properties of two {@link ContainerState} instances. Returns <code>true</code>
     * only if all properties of interest match for both states.
     */
    private boolean areEqualContainerStates(ContainerState c1, ContainerState c2) {
        if (c1 == null && c2 != null) {
            return false;
        }
        if (c1 != null && c2 == null) {
            return false;
        }

        if (!areEqual(c1.id, c2.id)
                || !areEqualLists(c1.names, c2.names)
                || !areEqual(c1.created, c2.created)
                || c1.powerState != c2.powerState
                || !areEqual(c1.started, c2.started)
                || !areEqual(c1.image, c2.image)
                || !areEqual(c1.address, c2.address)
                || !Arrays.equals(c1.command, c2.command)
                || !areEqualLists(c1.ports, c2.ports)
                || !areEqual(c1.networks, c2.networks)) {
            return false;
        }

        return true;
    }

    /**
     * Check whether two instances of the same class are equal. This will first do a
     * <code>null</code>-check and then will invoke the <code>equals</code> method of the first
     * instance.
     */
    private <T> boolean areEqual(T s1, T s2) {
        if (s1 == null && s2 != null) {
            return false;
        }
        if (s1 != null && s2 == null) {
            return false;
        }

        return s1.equals(s2);
    }

    /**
     * Check whether two lists are consider equal judging by their contents. This will first do a
     * <code>null</code>-check and then will use the <code>containsAll</code> method to compute the
     * result.
     */
    private <T> boolean areEqualLists(List<T> l1, List<T> l2) {
        if (l1 == null && l2 != null) {
            return false;
        }
        if (l1 != null && l2 == null) {
            return false;
        }
        if (l1.size() != l2.size()) {
            return false;
        }
        return l1.containsAll(l2);
    }

}
