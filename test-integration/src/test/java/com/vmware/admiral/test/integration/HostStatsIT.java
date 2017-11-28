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

package com.vmware.admiral.test.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.photon.controller.model.resources.ComputeService;

public class HostStatsIT extends BaseProvisioningOnCoreOsIT {

    private ClusterDto cluster;

    @Before
    public void setUp() throws Throwable {
        setupEnvironmentForCluster();
        cluster = createCluster();
    }

    @Test
    public void testHostStats() throws Exception {
        // The total memory, available memory, cpu usage should be populated in
        // the host's custom properties. We don't know what the exact values will be,
        // so just check if the properties exist
        waitForStateChange(cluster.nodeLinks.get(0), (body) -> {
            JsonObject object = new JsonParser().parse(body).getAsJsonObject();
            JsonObject customProperties = object
                    .get(ComputeService.ComputeState.FIELD_NAME_CUSTOM_PROPERTIES)
                    .getAsJsonObject();

            JsonElement totalMemory = customProperties
                    .get(ContainerHostService.DOCKER_HOST_TOTAL_MEMORY_PROP_NAME);
            boolean totalMemoryCondition = totalMemory != null && !totalMemory.isJsonNull();

            JsonElement availableMemory = customProperties
                    .get(ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME);
            boolean availableMemoryCondition =
                    availableMemory != null && !availableMemory.isJsonNull();

            JsonElement cpuUsage = customProperties
                    .get(ContainerHostService.DOCKER_HOST_CPU_USAGE_PCT_PROP_NAME);
            boolean cpuUsageCondition = cpuUsage != null && !cpuUsage.isJsonNull();

            return totalMemoryCondition && availableMemoryCondition && cpuUsageCondition;
        });
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return null;
    }

}
