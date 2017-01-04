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

package com.vmware.admiral.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService.ContainerHostDataCollectionState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionState;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.HostNetworkListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.HostNetworkListDataCollectionState;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.HostVolumeListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.HostVolumeListDataCollectionState;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.TaskState.TaskStage;

public class ComputeInitialBootServiceTest extends ComputeBaseTest {

    @Test
    public void testCoreAgentContainerCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK);
        ContainerDescription agentDesc = getDocument(ContainerDescription.class,
                SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK);

        assertNotNull(agentDesc);

        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_NAME, agentDesc.name);
        String expectedImageName = String.format("%s:%s",
                SystemContainerDescriptions.AGENT_IMAGE_NAME,
                SystemContainerDescriptions.AGENT_IMAGE_VERSION);
        assertEquals(expectedImageName, agentDesc.image);
    }

    @Test
    public void testCoreAgentContainerUpdatedOnStartUp() throws Throwable {
        waitForServiceAvailability(SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK);
        ContainerDescription agentDesc = getDocument(ContainerDescription.class,
                SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK);

        assertNotNull(agentDesc);

        waitForInitialBootServiceToBeSelfStopped(ComputeInitialBootService.SELF_LINK);

        String newVersion = "new version";
        setFinalStatic(SystemContainerDescriptions.class, "AGENT_IMAGE_VERSION", newVersion);

        //simulate a restart of the service host
        startInitialBootService(ComputeInitialBootService.class,
                ComputeInitialBootService.SELF_LINK);

        waitFor(() -> {
            ContainerDescription updatedDocument = getDocument(ContainerDescription.class,
                    SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK);

            return updatedDocument.documentVersion > agentDesc.documentVersion;
        });

        ContainerDescription updatedAgentDesc = getDocument(ContainerDescription.class,
                SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK);

        String expectedImageName = String.format("%s:%s",
                SystemContainerDescriptions.AGENT_IMAGE_NAME,
                newVersion);
        assertEquals(expectedImageName, updatedAgentDesc.image);

        assertFalse(updatedAgentDesc.image.equals(agentDesc.image));
    }

    @Test
    public void testContainerHostDataCollectionServiceCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK);
        ContainerHostDataCollectionState dataCollectionState = getDocument(
                ContainerHostDataCollectionState.class,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK);

        assertNotNull(dataCollectionState);
    }

    @Test
    public void testHostContainerListDataCollectionServiceCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(HostContainerListDataCollectionFactoryService.DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK);
        HostContainerListDataCollectionState dataCollectionState = getDocument(
                HostContainerListDataCollectionState.class,
                HostContainerListDataCollectionFactoryService.DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK);

        assertNotNull(dataCollectionState);
        assertEquals(TaskStage.STARTED, dataCollectionState.taskInfo.stage);
    }

    @Test
    public void testHostNetworkListDataCollectionServiceCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(HostNetworkListDataCollectionFactoryService.DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK);
        HostNetworkListDataCollectionState dataCollectionState = getDocument(
                HostNetworkListDataCollectionState.class,
                HostNetworkListDataCollectionFactoryService.DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK);

        assertNotNull(dataCollectionState);
        assertEquals(TaskStage.STARTED, dataCollectionState.taskInfo.stage);
    }

    @Test
    public void testHostVolumeListDataCollectionServiceCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(HostVolumeListDataCollectionFactoryService.DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);
        HostVolumeListDataCollectionState dataCollectionState = getDocument(
                HostVolumeListDataCollectionState.class,
                HostVolumeListDataCollectionFactoryService.DEFAULT_HOST_VOLUME_LIST_DATA_COLLECTION_LINK);

        assertNotNull(dataCollectionState);
        assertEquals(TaskStage.STARTED, dataCollectionState.taskInfo.stage);
    }

    @Test
    public void testDefaultGroupPlacementServiceCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK);
        GroupResourcePlacementState groupResourcePlacementState = getDocument(
                GroupResourcePlacementState.class,
                GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK);

        assertNotNull(groupResourcePlacementState);
        assertEquals(GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_ID,
                groupResourcePlacementState.name);
        assertEquals(GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK,
                groupResourcePlacementState.resourcePoolLink);
        assertEquals(1000000, groupResourcePlacementState.maxNumberInstances);
        assertEquals(100, groupResourcePlacementState.priority);
        assertNull(groupResourcePlacementState.tenantLinks);// assert global default group placement
    }

    @Test
    public void testDefaultResourcePoolServiceCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);
        ResourcePoolState resourcePoolState = getDocument(ResourcePoolState.class,
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);

        assertNotNull(resourcePoolState);
        assertEquals(GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_ID, resourcePoolState.name);
        assertEquals(GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_ID, resourcePoolState.id);
    }
}
