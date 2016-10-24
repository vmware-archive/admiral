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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.request.compute.ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

/**
 * Tests for the {@link ComputePlacementSelectionTaskService} service.
 */
public class ComputePlacementSelectionTaskServiceTest extends ComputeRequestBaseTest {

    private ComputeState vmGuestCompute;

    @Override
    protected ResourceType placementResourceType() {
        return ResourceType.COMPUTE_TYPE;
    }

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();

        // create a single powered-on compute available for placement
        vmGuestCompute = createVmGuestCompute(true);
    }

    @Test
    public void testSingleInstanceProvisioning() throws Throwable {
        ComputeDescription computeDescription = doPost(createComputeDescription(),
                ComputeDescriptionService.FACTORY_LINK);

        ComputePlacementSelectionTaskState taskRequestState = new ComputePlacementSelectionTaskState();
        taskRequestState.computeDescriptionLink = computeDescription.documentSelfLink;
        taskRequestState.resourceCount = 1;
        taskRequestState.resourcePoolLink = computeResourcePool.documentSelfLink;

        ComputePlacementSelectionTaskState taskState = doPost(taskRequestState,
                ComputePlacementSelectionTaskService.FACTORY_LINK);
        assertNotNull(taskState);

        taskState = waitForTaskSuccess(taskState.documentSelfLink,
                ComputePlacementSelectionTaskState.class);

        assertNotNull(taskState.selectedComputePlacementLinks);
        assertEquals(taskState.selectedComputePlacementLinks.size(), 1);

        ComputeState selectedComputeState = getDocument(ComputeState.class,
                taskState.selectedComputePlacementLinks.iterator().next());
        assertNotNull(selectedComputeState);
        assertEquals(selectedComputeState.documentSelfLink, vmGuestCompute.documentSelfLink);
    }

    private ComputeDescription createComputeDescription() {
        ComputeDescription cd = new ComputeDescription();
        cd.id = UUID.randomUUID().toString();
        cd.name = "small"; // aws adapter is using name as value for instance type
        cd.customProperties = new HashMap<>();
        return cd;
    }
}
