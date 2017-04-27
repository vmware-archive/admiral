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

import static com.vmware.admiral.compute.ComputeConstants.CUSTOM_PROP_PROFILE_LINK_NAME;

import java.util.HashMap;
import java.util.Set;

import org.junit.Test;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState.SubStage;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService.ComputeProvisionTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class ComputeAllocationTaskServiceTest extends ComputeRequestBaseTest {

    @Override
    protected ResourceType placementResourceType() {
        return ResourceType.COMPUTE_TYPE;
    }

    @Test
    public void testAllocationTaskServiceLifeCycle() throws Throwable {
        ComputeDescription computeDescription = createVMComputeDescription(false);

        ComputeAllocationTaskState allocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, true);
        allocationTask = allocate(allocationTask);

        ComputeState computeState = getDocument(ComputeState.class,
                allocationTask.resourceLinks.iterator().next());
        assertNotNull(computeState.id);

        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(resourcePool.documentSelfLink, computeState.resourcePoolLink);
        // assertEquals(vmHostCompute.documentSelfLink, computeState.parentLink);
    }

    @Test
    public void testAllocationTaskServiceLifeCycleWithTwoProfilesByInstanceType() throws Throwable {
        ComputeDescription computeDescription = createVMComputeDescription(false);

        ProfileStateExpanded p1 = createProfileWithInstanceType(
                "small", "t2.micro", "coreos", "ami-234355");
        ProfileStateExpanded p2 =
                createProfileWithInstanceType("large", "t2.large", "coreos", "ami-234355");

        ComputeAllocationTaskState allocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, true);
        allocationTask = allocate(allocationTask);

        ComputeState computeState = getDocument(ComputeState.class,
                allocationTask.resourceLinks.iterator().next());
        assertNotNull(computeState.id);

        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(resourcePool.documentSelfLink, computeState.resourcePoolLink);
        assertNotNull(computeState.customProperties);
        String profileLink = computeState.customProperties.get(CUSTOM_PROP_PROFILE_LINK_NAME);
        assertNotNull(profileLink);
        assertEquals(profileLink, p1.documentSelfLink);
    }

    @Test
    public void testAllocationTaskServiceLifeCycleWithTwoProfilesByImage() throws Throwable {
        ComputeDescription computeDescription = createVMComputeDescription(false);

        ProfileStateExpanded p1 = createProfileWithInstanceType(
                "small", "t2.micro", "linux", "ami-234355");
        ProfileStateExpanded p2 =
                createProfileWithInstanceType("small", "t2.large", "coreos", "ami-234355");

        ComputeAllocationTaskState allocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, true);
        allocationTask = allocate(allocationTask);

        ComputeState computeState = getDocument(ComputeState.class,
                allocationTask.resourceLinks.iterator().next());
        assertNotNull(computeState.id);

        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(resourcePool.documentSelfLink, computeState.resourcePoolLink);
        assertNotNull(computeState.customProperties);
        String profileLink = computeState.customProperties.get(CUSTOM_PROP_PROFILE_LINK_NAME);
        assertNotNull(profileLink);
        assertEquals(profileLink, p2.documentSelfLink);
    }

    @Test
    public void testComputeAllocationWithFollowingProvisioningRequest() throws Throwable {
        host.log(">>>>>>Start: testComputeAllocationWithFollowingProvisioningRequest <<<<< ");
        ComputeDescription computeDescription = createVMComputeDescription(false);

        ComputeAllocationTaskState allocationTask = createComputeAllocationTask(
                computeDescription.documentSelfLink, 1, true);
        allocationTask = allocate(allocationTask);

        ComputeState computeState = getDocument(ComputeState.class,
                allocationTask.resourceLinks.iterator().next());
        assertTrue(computeState.name.startsWith(TEST_VM_NAME));
        assertNotNull(computeState.id);
        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        assertEquals(allocationTask.tenantLinks, computeState.tenantLinks);

        // make sure the host is not update with the new container.
        assertFalse("should not be provisioned container: " + computeState.documentSelfLink,
                MockDockerAdapterService.isContainerProvisioned(computeState.documentSelfLink));

        ComputeProvisionTaskState provisionTask = createComputeProvisionTask(
                allocationTask.resourceLinks);

        // Request provisioning after allocation:
        provisionTask = provision(provisionTask);

        // verify container state is provisioned and patched:
        computeState =
                getDocument(ComputeState.class, provisionTask.resourceLinks.iterator().next());
        assertNotNull(computeState);

        assertNotNull(computeState.id);
        assertEquals(computeDescription.documentSelfLink, computeState.descriptionLink);
        // assertEquals(vmHostCompute.documentSelfLink, computeState.parentLink);
    }

    @SuppressWarnings("static-access")
    @Test
    public void testContainerAllocationSubscriptionSubStages() throws Throwable {

        ComputeDescription computeDescription = createVMComputeDescription(false);
        ComputeAllocationTaskState allocationTask = createComputeAllocationTask
                (computeDescription.documentSelfLink, 1, true);
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put("customPropB", "valueB");
        allocationTask = allocate(allocationTask);

        assertNotNull(allocationTask);
        Set<SubStage> subscriptionSubStages = allocationTask.taskSubStage
                .SUBSCRIPTION_SUB_STAGES;

        assertNotNull(subscriptionSubStages);
        assertEquals(1, subscriptionSubStages.size());
        assertTrue(subscriptionSubStages
                .contains(allocationTask.taskSubStage.RESOURCES_NAMES));
    }


    private ProfileStateExpanded createProfileWithInstanceType(String instanceTypeKey,
            String instanceTypeValue, String imageKey, String imageValue) throws Throwable {
        return createProfileWithInstanceType(instanceTypeKey, instanceTypeValue, imageKey, imageValue,
                null, computeGroupPlacementState);
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

    private ComputeProvisionTaskState provision(ComputeProvisionTaskState provisionTask)
            throws Throwable {
        provisionTask = startProvisionTask(provisionTask);
        host.log("Start allocation test: " + provisionTask.documentSelfLink);

        provisionTask = waitForTaskSuccess(provisionTask.documentSelfLink,
                ComputeProvisionTaskState.class);
        assertNotNull("ResourceLinks null for allocation: " + provisionTask.documentSelfLink,
                provisionTask.resourceLinks);

        host.log("Finished allocation test: " + provisionTask.documentSelfLink);
        return provisionTask;
    }

    private ComputeAllocationTaskState createComputeAllocationTask(String computeDescriptionLink,
            long resourceCount, boolean allocation) {
        ComputeAllocationTaskState allocationTask = new ComputeAllocationTaskState();
        allocationTask.resourceDescriptionLink = computeDescriptionLink;
        allocationTask.groupResourcePlacementLink = computeGroupPlacementState.documentSelfLink;
        allocationTask.tenantLinks = computeGroupPlacementState.tenantLinks;
        allocationTask.resourceType = "Compute";
        allocationTask.resourceCount = resourceCount;
        allocationTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put("compute.docker.host", "true");
        allocationTask.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                String.valueOf(allocation));
        return allocationTask;
    }

    private ComputeProvisionTaskState createComputeProvisionTask(Set<String> resourceLinks) {
        ComputeProvisionTaskState provisionTask = new ComputeProvisionTaskState();
        provisionTask.resourceLinks = resourceLinks;
        provisionTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        provisionTask.customProperties = new HashMap<>();
        provisionTask.customProperties.put("compute.docker.host", "true");
        return provisionTask;
    }

    private ComputeAllocationTaskState startAllocationTask(
            ComputeAllocationTaskState allocationTask) throws Throwable {
        ComputeAllocationTaskState outAllocationTask = doPost(
                allocationTask, ComputeAllocationTaskService.FACTORY_LINK);
        assertNotNull(outAllocationTask);
        return outAllocationTask;
    }

    private ComputeProvisionTaskState startProvisionTask(
            ComputeProvisionTaskState provisionTask) throws Throwable {
        ComputeProvisionTaskState outprovisionTask = doPost(
                provisionTask, ComputeProvisionTaskService.FACTORY_LINK);
        assertNotNull(outprovisionTask);
        return outprovisionTask;
    }

}