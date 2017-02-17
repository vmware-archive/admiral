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

package com.vmware.admiral.compute;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.PlacementCapacityUpdateTaskService.PlacementCapacityUpdateTaskState;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for the {@link PlacementCapacityUpdateTaskService} class.
 */
public class PlacementCapacityUpdateTaskServiceTest extends ComputeBaseTest {

    public void setUp() throws Throwable {
        waitForServiceAvailability(PlacementCapacityUpdateTaskService.FACTORY_LINK);
    }

    @Test
    public void testContainerCalculations() throws Throwable {
        ResourcePoolState rp = createResourcePool();
        ComputeDescription cd = createComputeDescription(ComputeType.DOCKER_CONTAINER.toString(),
                0L, 0L, 0L);
        createContainerHost(cd.documentSelfLink, rp.documentSelfLink, 4_000_000L, 2_000_000L, 2L,
                0.10);
        createContainerHost(cd.documentSelfLink, rp.documentSelfLink, 4_000_000L, null, 4L, 0.50);
        ComputeState c3 = createContainerHost(cd.documentSelfLink, rp.documentSelfLink, 4_000_000L,
                1_000_000L, null, null);

        startAndWaitForTask(rp.documentSelfLink);
        validateFigures(rp.documentSelfLink, 12_000_000L, null, 7_000_000L,
                (2 * 0.10 + 4 * 0.50 + 1 * 0.0) / 7);

        delete(c3.documentSelfLink);
        startAndWaitForTask(rp.documentSelfLink);
        validateFigures(rp.documentSelfLink, 8_000_000L, null, 6_000_000L, (2 * 0.10 + 4 * 0.50) / 6);
    }

    @Test
    public void testComputeCalculations() throws Throwable {
        ResourcePoolState rp = createResourcePool();
        ComputeDescription cd1 = createComputeDescription(ComputeType.VM_GUEST.toString(),
                1_000_000L, 2L, 1_000L);
        ComputeDescription cd2 = createComputeDescription(ComputeType.VM_GUEST.toString(),
                2_000_000L, 4L, 2_000L);
        ComputeDescription cd3 = createComputeDescription(ComputeType.VM_GUEST.toString(),
                3_000_000L, 8L, 4_000L);
        createComputeState(cd1.documentSelfLink, rp.documentSelfLink);
        createComputeState(cd2.documentSelfLink, rp.documentSelfLink);
        ComputeState c3 = createComputeState(cd3.documentSelfLink, rp.documentSelfLink);

        startAndWaitForTask(rp.documentSelfLink);
        validateFigures(rp.documentSelfLink, 6_000_000L, null, 6_000_000L, 0.0);

        delete(c3.documentSelfLink);
        startAndWaitForTask(rp.documentSelfLink);
        validateFigures(rp.documentSelfLink, 3_000_000L, null, 3_000_000L, 0.0);
    }

    private ComputeState createContainerHost(String descriptionLink, String rpLink,
            Long totalMemoryBytes,
            Long availableMemoryBytes, Long cpuCores, Double cpuUsage) throws Throwable {
        ComputeState cs = new ComputeState();
        cs.descriptionLink = descriptionLink;
        cs.resourcePoolLink = rpLink;
        cs.customProperties = new HashMap<>();
        if (totalMemoryBytes != null) {
            cs.customProperties.put(ContainerHostService.DOCKER_HOST_TOTAL_MEMORY_PROP_NAME,
                    totalMemoryBytes.toString());
        }
        if (availableMemoryBytes != null) {
            cs.customProperties.put(ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME,
                    availableMemoryBytes.toString());
        }
        if (cpuCores != null) {
            cs.customProperties.put(ContainerHostService.DOCKER_HOST_NUM_CORES_PROP_NAME,
                    cpuCores.toString());
        }
        if (cpuUsage != null) {
            cs.customProperties.put(ContainerHostService.DOCKER_HOST_CPU_USAGE_PCT_PROP_NAME,
                    cpuUsage.toString());
        }
        return doPost(cs, ComputeService.FACTORY_LINK);
    }

    private ComputeState createComputeState(String descriptionLink, String rpLink)
            throws Throwable {
        ComputeState cs = new ComputeState();
        cs.descriptionLink = descriptionLink;
        cs.resourcePoolLink = rpLink;
        return doPost(cs, ComputeService.FACTORY_LINK);
    }

    private ResourcePoolState createResourcePool() throws Throwable {
        ResourcePoolState rp = new ResourcePoolState();
        rp.name = UUID.randomUUID().toString();
        return doPost(rp, ResourcePoolService.FACTORY_LINK);
    }

    private ComputeDescription createComputeDescription(String supportedChildren,
            long totalMemoryBytes, long cpuCoreCount, long cpuMhzPerCore) throws Throwable {
        ComputeDescription cd = new ComputeDescription();
        cd.totalMemoryBytes = totalMemoryBytes;
        cd.cpuCount = cpuCoreCount;
        cd.cpuMhzPerCore = cpuMhzPerCore;
        cd.instanceAdapterReference = new URI("http://instanceAdapterReference");
        if (supportedChildren != null) {
            cd.supportedChildren = new ArrayList<>();
            cd.supportedChildren.add(supportedChildren);
        }
        return doPost(cd, ComputeDescriptionService.FACTORY_LINK);
    }

    private void startAndWaitForTask(String resourcePoolLink) throws Throwable {
        PlacementCapacityUpdateTaskState initialState = new PlacementCapacityUpdateTaskState();
        initialState.resourcePoolLink = resourcePoolLink;
        PlacementCapacityUpdateTaskState returnState = doOperation(initialState,
                UriUtils.buildUri(this.host, PlacementCapacityUpdateTaskService.FACTORY_LINK),
                PlacementCapacityUpdateTaskState.class, false, Action.POST);
        waitFor(() -> getDocumentNoWait(PlacementCapacityUpdateTaskState.class,
                returnState.documentSelfLink) == null);
    }

    private void validateFigures(String resourcePoolLink, long maxMemoryBytes, Long minMemoryBytes,
            long availableMemoryBytes, double cpuUsage) throws Throwable {
        ResourcePoolState rp = getDocument(ResourcePoolState.class, resourcePoolLink);
        assertEquals(maxMemoryBytes, rp.maxMemoryBytes.longValue());
        assertEquals(minMemoryBytes, rp.minMemoryBytes);
        assertEquals(availableMemoryBytes,
                Double.parseDouble(rp.customProperties.get(
                        ContainerHostDataCollectionService.RESOURCE_POOL_AVAILABLE_MEMORY_CUSTOM_PROP)),
                0.01);
        assertEquals(cpuUsage,
                Double.parseDouble(rp.customProperties.get(
                        ContainerHostDataCollectionService.RESOURCE_POOL_CPU_USAGE_CUSTOM_PROP)),
                0.01);
    }
}
