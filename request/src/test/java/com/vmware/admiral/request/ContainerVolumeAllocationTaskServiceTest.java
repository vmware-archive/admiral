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

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.Test;

import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.request.ContainerVolumeAllocationTaskService.ContainerVolumeAllocationTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;

public class ContainerVolumeAllocationTaskServiceTest extends RequestBaseTest {

    @Test
    public void testAllocationTaskServiceLifeCycle() throws Throwable {

        ContainerVolumeAllocationTaskState allocationTask = createContainerVolumeAllocationTask(
                containerVolumeDesc.documentSelfLink, 1);
        allocationTask = allocate(allocationTask);

        ContainerVolumeState volumeState = getDocument(ContainerVolumeState.class,
                allocationTask.resourceLinks.iterator().next());

        assertNotNull(volumeState);
        assertEquals(containerVolumeDesc.documentSelfLink, volumeState.descriptionLink);
        assertEquals(containerVolumeDesc.driver, volumeState.driver);
        assertTrue(volumeState.name.contains(containerVolumeDesc.name));
        assertEquals(allocationTask.resourceLinks.iterator().next(), volumeState.documentSelfLink);

        String volumeDescProp = allocationTask.customProperties.get("volume propKey string");
        assertNotNull(volumeDescProp);
        assertEquals("volume customPropertyValue string", volumeDescProp);
        assertEquals(allocationTask.customProperties, volumeState.customProperties);
    }

    private ContainerVolumeAllocationTaskState createContainerVolumeAllocationTask(
            String volumeDocSelfLink, long resourceCount) {

        ContainerVolumeAllocationTaskState allocationTask = new ContainerVolumeAllocationTaskState();
        allocationTask.resourceDescriptionLink = volumeDocSelfLink;
        allocationTask.resourceCount = resourceCount;
        allocationTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        allocationTask.customProperties = new HashMap<>(1);
        allocationTask.customProperties.put("volume propKey string",
                "volume customPropertyValue string");
        return allocationTask;
    }

    private ContainerVolumeAllocationTaskState allocate(
            ContainerVolumeAllocationTaskState allocationTask)
            throws Throwable {
        allocationTask = startAllocationTask(allocationTask);
        host.log("Start allocation test: " + allocationTask.documentSelfLink);

        allocationTask = waitForTaskSuccess(allocationTask.documentSelfLink,
                ContainerVolumeAllocationTaskState.class);

        assertNotNull("ResourceLinks null for allocation: " + allocationTask.documentSelfLink,
                allocationTask.resourceLinks);
        assertEquals("Resource count not equal for: " + allocationTask.documentSelfLink,
                allocationTask.resourceCount, Long.valueOf(allocationTask.resourceLinks.size()));

        host.log("Finished allocation test: " + allocationTask.documentSelfLink);
        return allocationTask;
    }

    private ContainerVolumeAllocationTaskState startAllocationTask(
            ContainerVolumeAllocationTaskState allocationTask) throws Throwable {
        ContainerVolumeAllocationTaskState outAllocationTask = doPost(
                allocationTask, ContainerVolumeAllocationTaskService.FACTORY_LINK);
        assertNotNull(outAllocationTask);
        return outAllocationTask;
    }

}
