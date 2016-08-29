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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.GroupResourcePolicyService;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.request.ReservationAllocationTaskService.ReservationAllocationTaskState;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.xenon.common.FactoryService;

public class ReservationAllocationTaskServiceTest extends RequestBaseTest {

    @Override
    public void setUp() throws Throwable {
        startServices(host);
        MockDockerAdapterService.resetContainers();

        setUpDockerHostAuthentication();

        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(ReservationAllocationTaskService.FACTORY_LINK);
        waitForServiceAvailability(GroupResourcePolicyService.FACTORY_LINK);

        // setup Docker Host:
        ResourcePoolService.ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        createDockerHost(dockerHostDesc, resourcePool);
        createContainerDescription();
        Map<String, String> customProperties = containerDesc.customProperties;
        if (customProperties == null) {
            customProperties = new ConcurrentHashMap<String, String>();
        }

        customProperties.put(ReservationAllocationTaskService.CONTAINER_HOST_ID_CUSTOM_PROPERTY,
                computeHost.id);

        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);
    }

    @Test
    public void testReservationAllocationTask() throws Throwable {

        verifyService(FactoryService.create(ReservationAllocationTaskService.class),
                ReservationAllocationTaskState.class,
                (prefix, index) -> {
                    ReservationAllocationTaskState reservationState = new ReservationAllocationTaskState();
                    reservationState.tenantLinks = Collections.singletonList("testGroup");
                    reservationState.resourceDescriptionLink = prefix + "test";
                    reservationState.customProperties = containerDesc.customProperties;
                    reservationState.name = containerDesc.name;
                    reservationState.resourceCount = 1;
                    Assert.assertNull(reservationState.groupResourcePolicyLink);

                    return reservationState;

                } ,
                (prefix, serviceDocument) -> {
                    ReservationAllocationTaskState reservationAllocationState = (ReservationAllocationTaskState) serviceDocument;

                    waitFor(() -> {

                        return getDocument(ReservationAllocationTaskState.class,
                                reservationAllocationState.documentSelfLink).groupResourcePolicyLink != null;

                    });

                    ReservationAllocationTaskState result = getDocument(
                            ReservationAllocationTaskState.class,
                            reservationAllocationState.documentSelfLink);
                    assertEquals(Collections.singletonList("testGroup"), result.tenantLinks);
                    assertNotNull(result.groupResourcePolicyLink);
                    assertTrue(result.groupResourcePolicyLink.contains(containerDesc.name));

                    // Get GroupResourcePolicy that has been created.
                    GroupResourcePolicyState groupResourcePolicy = getDocument(
                            GroupResourcePolicyState.class, result.groupResourcePolicyLink);
                    assertNotNull(groupResourcePolicy);
                    assertNotNull(groupResourcePolicy.resourcePoolLink);

                    // Verify that the name of container exists in resource pool.
                    assertTrue(groupResourcePolicy.resourcePoolLink.contains(containerDesc.name));

                });
    }

    @Test
    public void testReservationAllocationThroughReservationTask() throws Throwable {
        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = containerDesc.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.groupResourcePolicyLink = null;
        task.resourcePoolsPerGroupPolicyLinks = null;

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        ReservationTaskState result = waitForTaskSuccess(task.documentSelfLink,
                ReservationTaskState.class);

        assertNotNull(result.groupResourcePolicyLink);
        assertNotNull(result.resourcePoolsPerGroupPolicyLinks);
        assertTrue(result.resourcePoolsPerGroupPolicyLinks.size() == 1);
        assertTrue(result.groupResourcePolicyLink.contains(containerDesc.name));
        assertTrue(result.resourcePoolsPerGroupPolicyLinks.keySet()
                .contains(result.groupResourcePolicyLink));

        ReservationAllocationTaskState rsvAllocation = getDocument(
                ReservationAllocationTaskState.class,
                task.documentSelfLink);
        assertNotNull(rsvAllocation);
        assertEquals(result.groupResourcePolicyLink, rsvAllocation.groupResourcePolicyLink);
        assertEquals(result.resourcePoolsPerGroupPolicyLinks,
                rsvAllocation.resourcePoolsPerGroupPolicyLinks);

        GroupResourcePolicyState groupResourcePolicy = getDocument(
                GroupResourcePolicyState.class, result.groupResourcePolicyLink);
        assertNotNull(groupResourcePolicy);
        assertNotNull(groupResourcePolicy.resourcePoolLink);

        assertEquals(groupResourcePolicy.documentSelfLink, rsvAllocation.groupResourcePolicyLink);

    }

}
