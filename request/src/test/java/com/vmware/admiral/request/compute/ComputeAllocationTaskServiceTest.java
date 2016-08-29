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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class ComputeAllocationTaskServiceTest extends RequestBaseTest {

    private static final String TEST_VM_NAME = "testVM";

    private ComputeState vmGuestCompute;

    @Override
    protected ResourceType policyResourceType() {
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
    public void testAllocationTaskServiceLifeCycle() throws Throwable {
        ComputeDescription computeDescription = doPost(createComputeDescription(),
                ComputeDescriptionService.FACTORY_LINK);

        ComputeAllocationTaskState allocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, true);
        allocationTask = allocate(allocationTask);

        ComputeState computeState = getDocument(ComputeState.class,
                allocationTask.resourceLinks.get(0));
        assertNotNull(computeState.id);

        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(resourcePool.documentSelfLink, computeState.resourcePoolLink);
        assertEquals(vmGuestCompute.documentSelfLink, computeState.parentLink);
    }

    @Test
    public void testComputeAllocationWithFollowingProvisioningRequest() throws Throwable {
        host.log(">>>>>>Start: testComputeAllocationWithFollowingProvisioningRequest <<<<< ");
        ComputeDescription computeDescription = doPost(createComputeDescription(),
                ComputeDescriptionService.FACTORY_LINK);

        ComputeAllocationTaskState allocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, true);
        allocationTask = allocate(allocationTask);

        ComputeState computeState = getDocument(ComputeState.class,
                allocationTask.resourceLinks.get(0));
        assertTrue(computeState.customProperties
                .get(ComputeProperties.CUSTOM_DISPLAY_NAME).startsWith(TEST_VM_NAME));
        assertNotNull(computeState.id);
        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(allocationTask.tenantLinks, computeState.tenantLinks);

        // make sure the host is not update with the new container.
        assertFalse("should not be provisioned container: " + computeState.documentSelfLink,
                MockDockerAdapterService.isContainerProvisioned(computeState.documentSelfLink));

        ComputeAllocationTaskState postAllocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, false);
        postAllocationTask.postAllocation = true;
        postAllocationTask.resourceLinks = allocationTask.resourceLinks;

        // Request provisioning after allocation:
        postAllocationTask = allocate(postAllocationTask);

        // verify container state is provisioned and patched:
        computeState = getDocument(ComputeState.class, postAllocationTask.resourceLinks.get(0));
        assertNotNull(computeState);

        assertNotNull(computeState.id);
        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(vmGuestCompute.documentSelfLink, computeState.parentLink);
    }

    private ComputeDescription createComputeDescription() {
        ComputeDescription cd = new ComputeDescription();
        cd.id = UUID.randomUUID().toString();
        cd.name = "small"; // aws adapter is using name as value for instance type
        cd.customProperties = new HashMap<>();
        cd.customProperties.put(ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_IMAGE_ID_NAME,
                "linux");
        cd.customProperties.put(ComputeProperties.CUSTOM_DISPLAY_NAME, TEST_VM_NAME);
        return cd;
    }

    private ComputeAllocationTaskState allocate(ComputeAllocationTaskState allocationTask)
            throws Throwable {
        allocationTask = startAllocationTask(allocationTask);
        host.log("Start allocation test: " + allocationTask.documentSelfLink);

        allocationTask = waitForTaskSuccess(allocationTask.documentSelfLink,
                ComputeAllocationTaskState.class);
        assertNotNull("ResourceLinks null for allocation: " + allocationTask.documentSelfLink,
                allocationTask.resourceLinks);
        assertEquals("Resource count not equal for: " + allocationTask.documentSelfLink,
                allocationTask.resourceCount, Long.valueOf(allocationTask.resourceLinks.size()));

        host.log("Finished allocation test: " + allocationTask.documentSelfLink);
        return allocationTask;
    }

    private ComputeAllocationTaskState createComputeAllocationTask(String containerDescLink,
            long resourceCount, boolean allocation) {
        ComputeAllocationTaskState allocationTask = new ComputeAllocationTaskState();
        allocationTask.resourceDescriptionLink = containerDescLink;
        allocationTask.groupResourcePolicyLink = computeGroupPolicyState.documentSelfLink;
        allocationTask.resourceType = "Compute";
        allocationTask.resourceCount = resourceCount;
        allocationTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put("compute.docker.host", "true");
        if (allocation) {
            allocationTask.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                    String.valueOf(allocation));
        }
        return allocationTask;
    }

    private ComputeAllocationTaskState startAllocationTask(
            ComputeAllocationTaskState allocationTask) throws Throwable {
        ComputeAllocationTaskState outAllocationTask = doPost(
                allocationTask, ComputeAllocationTaskService.FACTORY_LINK);
        assertNotNull(outAllocationTask);
        return outAllocationTask;
    }

}