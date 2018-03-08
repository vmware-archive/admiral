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

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_DRIVER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_MOUNTPOINT_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_OPTIONS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_SCOPE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_STATUS_PROP_NAME;
import static com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.VMDK_VOLUME_DRIVER;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState.PowerState;

/**
 * Map properties into ContainerVolumeState
 */
public class ContainerVolumeStateMapper {

    private static final String DATASTORE_PROP_NAME = "datastore";

    /**
     * Convert generic properties from the given {@link Map} to modeled properties in the given
     * {@link ContainerVolumeState}.
     *
     * @param volumeState
     * @param properties
     */
    public static void propertiesToContainerVolumeState(ContainerVolumeState volumeState,
            Map<String, Object> properties) {

        AssertUtil.assertNotNull(volumeState, "volumeState");
        AssertUtil.assertNotNull(properties, "properties");

        volumeState.driver = (String) properties.get(DOCKER_VOLUME_DRIVER_PROP_NAME);
        volumeState.mountpoint = (String) properties.get(DOCKER_VOLUME_MOUNTPOINT_PROP_NAME);
        volumeState.scope = (String) properties.get(DOCKER_VOLUME_SCOPE_PROP_NAME);

        mapDriverOptions(volumeState, properties);

        mapVolumeStatus(volumeState, properties);

        updateVolumeName(volumeState, properties);

        volumeState.powerState = PowerState.CONNECTED;
    }

    public static String getVmdkDatastoreName(Map<String, Object> properties) {
        Map<String, String> status = getMap(properties, DOCKER_VOLUME_STATUS_PROP_NAME);
        if (status != null) {
            return status.get(DATASTORE_PROP_NAME);
        }

        return null;
    }

    private static void mapVolumeStatus(ContainerVolumeState volumeState,
            Map<String, Object> properties) {

        volumeState.status = getMap(properties, DOCKER_VOLUME_STATUS_PROP_NAME);
    }

    private static void mapDriverOptions(ContainerVolumeState volumeState,
            Map<String, Object> properties) {

        volumeState.options = getMap(properties, DOCKER_VOLUME_OPTIONS_PROP_NAME);
    }

    private static <T> Map<String, String> getMap(Map<String, Object> properties, String propertyName) {
        @SuppressWarnings("unchecked")
        Map<String, T> map = (Map<String, T>) properties.get(propertyName);

        if (map != null) {
            Map<String, String> resultMap = new HashMap<String, String>();
            for (Entry<String, T> entry : map.entrySet()) {
                resultMap.put(entry.getKey(), entry.getValue().toString());
            }

            return resultMap;
        }

        return null;
    }

    private static void updateVolumeName(ContainerVolumeState volumeState,
            Map<String, Object> properties) {

        volumeState.name = (String) properties.get(DOCKER_VOLUME_NAME_PROP_NAME);

        if (!VMDK_VOLUME_DRIVER.equals(volumeState.driver)) {
            return;
        }

        String datastore;
        if (volumeState.status != null
                && (datastore = volumeState.status.get(DATASTORE_PROP_NAME)) != null) {
            String nameSuffix = "@" + datastore;
            if (!volumeState.name.endsWith(nameSuffix)) {
                volumeState.name += nameSuffix;
            }
        }

    }
}
