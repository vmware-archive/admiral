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
import com.vmware.admiral.compute.container.GroupResourcePolicyService;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionState;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

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
        assertEquals(
                UriUtils.buildUri(host, SystemContainerDescriptions.AGENT_IMAGE_REFERENCE),
                agentDesc.imageReference);
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
        waitForServiceAvailability(HostContainerListDataCollectionFactoryService.DEFAULT_HOST_CONAINER_LIST_DATA_COLLECTION_LINK);
        HostContainerListDataCollectionState dataCollectionState = getDocument(
                HostContainerListDataCollectionState.class,
                HostContainerListDataCollectionFactoryService.DEFAULT_HOST_CONAINER_LIST_DATA_COLLECTION_LINK);

        assertNotNull(dataCollectionState);
        assertEquals(TaskStage.STARTED, dataCollectionState.taskInfo.stage);
    }

    @Test
    public void testDefaultGroupPolicyServiceCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(GroupResourcePolicyService.DEFAULT_RESOURCE_POLICY_LINK);
        GroupResourcePolicyState groupResourcePolicyState = getDocument(
                GroupResourcePolicyState.class,
                GroupResourcePolicyService.DEFAULT_RESOURCE_POLICY_LINK);

        assertNotNull(groupResourcePolicyState);
        assertEquals(GroupResourcePolicyService.DEFAULT_RESOURCE_POLICY_ID,
                groupResourcePolicyState.name);
        assertEquals(GroupResourcePolicyService.DEFAULT_RESOURCE_POOL_LINK,
                groupResourcePolicyState.resourcePoolLink);
        assertEquals(1000000, groupResourcePolicyState.maxNumberInstances);
        assertEquals(100, groupResourcePolicyState.priority);
        assertNull(groupResourcePolicyState.tenantLinks);// assert global default group policy
    }

    @Test
    public void testDefaultResourcePoolServiceCreatedOnStartUp() throws Throwable {
        waitForServiceAvailability(GroupResourcePolicyService.DEFAULT_RESOURCE_POOL_LINK);
        ResourcePoolState resourcePoolState = getDocument(ResourcePoolState.class,
                GroupResourcePolicyService.DEFAULT_RESOURCE_POOL_LINK);

        assertNotNull(resourcePoolState);
        assertEquals(GroupResourcePolicyService.DEFAULT_RESOURCE_POOL_ID, resourcePoolState.name);
        assertEquals(GroupResourcePolicyService.DEFAULT_RESOURCE_POOL_ID, resourcePoolState.id);
    }
}
