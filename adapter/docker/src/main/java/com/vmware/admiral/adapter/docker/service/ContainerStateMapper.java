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

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_COMMAND_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_CREATED_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_IMAGE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_SETTINGS_IP_ADDRESS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_SETTINGS_PORTS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_SETTINGS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_STATE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_STATE_RUNNING_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_STATE_STARTED_PROP_NAME;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.PortBinding;

/**
 * Map properties into ContainerState
 */
public class ContainerStateMapper {
    /**
     * Convert generic properties from the given map to modeled properties in the given
     * ContainerState
     *
     * @param containerState
     * @param properties
     */
    public void propertiesToContainerState(ContainerState containerState,
            Map<String, Object> properties) {

        AssertUtil.assertNotNull(containerState, "containerState");
        AssertUtil.assertNotNull(properties, "properties");

        containerState.id = (String) properties.get(DOCKER_CONTAINER_ID_PROP_NAME);
        String name = (String) properties.get(DOCKER_CONTAINER_NAME_PROP_NAME);
        if (name != null && name.startsWith("/")) {
            name = name.substring(1);
        }
        containerState.names = Collections.singletonList(name);

        containerState.created = parseDate(properties.get(DOCKER_CONTAINER_CREATED_PROP_NAME));

        mapStateProperties(containerState, getMap(properties, DOCKER_CONTAINER_STATE_PROP_NAME));

        mapConfigProperties(containerState, getMap(properties, DOCKER_CONTAINER_CONFIG_PROP_NAME));

        mapPortBindingProperties(containerState,
                getMap(properties, DOCKER_CONTAINER_NETWORK_SETTINGS_PROP_NAME));

        mapContainerIPAddress(containerState,
                getMap(properties, DOCKER_CONTAINER_NETWORK_SETTINGS_PROP_NAME));
    }

    /**
     * Process properties in the State object
     *
     * @param containerState
     * @param state
     */
    private void mapStateProperties(ContainerState containerState, Map<String, Object> state) {
        if (state == null) {
            return;
        }
        containerState.started = parseDate(state.get(DOCKER_CONTAINER_STATE_STARTED_PROP_NAME));

        mapPowerState(containerState, state);
    }

    /**
     * Process properties in the Config object
     *
     * @param containerState
     * @param config
     */
    private void mapConfigProperties(ContainerState containerState, Map<String, Object> config) {
        if (config == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Collection<String> commandList = (Collection<String>) config
                .get(DOCKER_CONTAINER_COMMAND_PROP_NAME);

        if (commandList != null) {
            containerState.command = commandList.toArray(new String[0]);
        }

        String image = (String) config.get(DOCKER_CONTAINER_IMAGE_PROP_NAME);
        if (image != null) {
            containerState.image = image;
        }
    }

    /**
     * Process port binding properties
     *
     * @param containerState
     * @param hostConfig
     * @param networkSettings
     */
    private void mapPortBindingProperties(ContainerState containerState,
            Map<String, Object> networkSettings) {

        if (networkSettings == null) {
            return;
        }

        Map<String, List<Map<String, String>>> portMap = getMap(networkSettings,
                DOCKER_CONTAINER_NETWORK_SETTINGS_PORTS_PROP_NAME);

        if (containerState.ports == null) {
            containerState.ports = new ArrayList<PortBinding>();
        }
        if (portMap != null) {
            List<DockerPortMapping> portMappings = DockerPortMapping.fromMap(portMap);

            containerState.ports = portMappings.stream()
                    .map((m) -> PortBinding.fromDockerPortMapping(m))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Process the NetworSettings.IpAddress
     *
     * @param containerState
     * @param networkSettings
     */
    private void mapContainerIPAddress(ContainerState containerState,
            Map<String, Object> networkSettings) {

        if (networkSettings == null) {
            return;
        }

        Object ipAddress = networkSettings
                .get(DOCKER_CONTAINER_NETWORK_SETTINGS_IP_ADDRESS_PROP_NAME);
        if (ipAddress == null) {
            return;
        }

        containerState.address = ipAddress.toString();
    }

    /*
     * map the State.Running property to the PowerState enum
     */
    private void mapPowerState(ContainerState containerState, Map<String, Object> state) {
        Boolean isRunning = (Boolean) state
                .get(DOCKER_CONTAINER_STATE_RUNNING_PROP_NAME);

        if (isRunning.booleanValue()) {
            containerState.powerState = PowerState.RUNNING;

        } else {
            containerState.powerState = PowerState.STOPPED;
        }
    }

    private <T> Map<String, T> getMap(Map<String, Object> container, String propertyName) {
        @SuppressWarnings("unchecked")
        Map<String, T> map = (Map<String, T>) container.get(propertyName);

        return map;
    }

    /**
     * Convert a string to a Date object
     *
     * @param value
     * @return
     */
    public static Long parseDate(Object value) {
        if (value == null) {
            return null;
        }

        String str = (String) value;
        try {
            // docker uses RFC3339Nano date format
            return Instant.parse(str).toEpochMilli();
        } catch (DateTimeParseException e) {
            //workaround for VIC host until https://github.com/vmware/vic/issues/1874 is fixed
            if (str.length() == 0) {
                return null;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
            try {
                return sdf.parse(str).getTime();
            } catch (ParseException e1) {
                throw new IllegalArgumentException("Invalid datetime '" + str + "'; "
                        + e1.getMessage());
            }
        }

    }
}
