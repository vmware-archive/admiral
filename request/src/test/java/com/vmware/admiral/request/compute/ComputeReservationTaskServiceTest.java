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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.request.compute.ComputeReservationTaskService.ComputeReservationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;

public class ComputeReservationTaskServiceTest extends ComputeRequestBaseTest {

    @Override
    public void setUp() throws Throwable {
        startServices(host);
        createEndpoint();
        createComputeResourcePool();
        createVmHostCompute(true);
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        createDockerHost(dockerHostDesc, null);
    }

    @Test
    public void testReservationTaskLifeCycleWhenNoAvailableGroupPlacements() throws Throwable {
        GroupResourcePlacementState groupPlacementState = doPost(
                TestRequestStateFactory
                        .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE),
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPlacementState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = groupPlacementState.maxNumberInstances + 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        waitForTaskError(task.documentSelfLink, ComputeReservationTaskState.class);
    }

    @Test
    public void testReservationTaskLifeCycle() throws Throwable {
        GroupResourcePlacementState groupPlacementState = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
        groupPlacementState.maxNumberInstances = 10;
        groupPlacementState.resourcePoolLink = computeResourcePool.documentSelfLink;
        groupPlacementState.customProperties = new HashMap<>();
        groupPlacementState.customProperties.put("key1", "placement-value1");
        groupPlacementState.customProperties.put("key2", "placement-value2");
        groupPlacementState = doPost(groupPlacementState,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        GroupResourcePlacementState notEnougInstancesPlacement = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
        notEnougInstancesPlacement.name = "not available";
        notEnougInstancesPlacement.maxNumberInstances = 4;
        notEnougInstancesPlacement = doPost(notEnougInstancesPlacement,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(notEnougInstancesPlacement);

        GroupResourcePlacementState differentGroupPlacement = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
        differentGroupPlacement.maxNumberInstances = 10;
        differentGroupPlacement.name = "different group";
        differentGroupPlacement.tenantLinks = Collections.singletonList("different-group");
        differentGroupPlacement = doPost(differentGroupPlacement,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(differentGroupPlacement);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPlacementState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.customProperties = new HashMap<>();
        task.customProperties.put("key1", "reservation-task-value1");

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);

        groupPlacementState = getDocument(GroupResourcePlacementState.class,
                groupPlacementState.documentSelfLink);

        assertEquals(groupPlacementState.documentSelfLink, task.groupResourcePlacementLink);

        assertEquals(groupPlacementState.allocatedInstancesCount, task.resourceCount);

        // check custom properties overridden:
        assertEquals(2, task.customProperties.size());
        assertEquals(groupPlacementState.customProperties.get("key1"),
                task.customProperties.get("key1"));
        assertEquals(groupPlacementState.customProperties.get("key2"),
                task.customProperties.get("key2"));
    }

    @Test
    public void testReservationTaskLifeCyclePriorities() throws Throwable {
        GroupResourcePlacementState placementState = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
        placementState.maxNumberInstances = 10;
        placementState.resourcePoolLink = computeResourcePool.documentSelfLink;
        placementState.priority = 3;
        placementState = doPost(placementState, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementState);

        GroupResourcePlacementState placementState1 = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
        placementState1.maxNumberInstances = 10;
        placementState1.resourcePoolLink = computeResourcePool.documentSelfLink;

        placementState1.priority = 1;
        placementState1 = doPost(placementState1, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementState1);

        GroupResourcePlacementState placementState2 = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
        placementState2.maxNumberInstances = 10;
        placementState2.resourcePoolLink = computeResourcePool.documentSelfLink;
        placementState2.priority = 2;
        placementState2 = doPost(placementState2, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementState2);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = placementState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);

        placementState1 = getDocument(GroupResourcePlacementState.class,
                placementState1.documentSelfLink);
        assertEquals(placementState1.documentSelfLink, task.groupResourcePlacementLink);
    }

    @Test
    public void testReservationTaskLifeCycleUnlimitedMemoryPlacement() throws Throwable {
        GroupResourcePlacementState placementState = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
        placementState.maxNumberInstances = 10;
        placementState.resourcePoolLink = computeResourcePool.documentSelfLink;
        placementState.priority = 3;
        placementState = doPost(placementState, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementState);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = placementState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);

        placementState = getDocument(GroupResourcePlacementState.class,
                placementState.documentSelfLink);
        assertEquals(placementState.documentSelfLink, task.groupResourcePlacementLink);
    }

    @Test
    public void testReservationTaskLifeCycleWithNoGroup() throws Throwable {
        EndpointState globalEndpoint = createGlobalEndpoint();
        ResourcePoolState rp = createResourcePoolForEndpoint(globalEndpoint);
        createVmHostCompute(globalEndpoint, rp);
        GroupResourcePlacementState globalGroupState = createGroupPlacementFor(rp);
        addForDeletion(globalGroupState);

        // create another suitable group placement but with a group that should not be selected
        doPost(TestRequestStateFactory.createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE),
                GroupResourcePlacementService.FACTORY_LINK);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = null;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);

        globalGroupState = getDocument(GroupResourcePlacementState.class,
                globalGroupState.documentSelfLink);

        assertEquals(globalGroupState.documentSelfLink, task.groupResourcePlacementLink);

        assertEquals(globalGroupState.allocatedInstancesCount, task.resourceCount);
    }

    @Test
    public void testReservationTaskLifeCycleWithGlobalGroup() throws Throwable {
        // create placement with same group but less number of instances:
        GroupResourcePlacementState groupPlacementState = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
        groupPlacementState.maxNumberInstances = 2;
        groupPlacementState = doPost(groupPlacementState,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        // create global placement that should be selected since the group placement is not
        // applicable.
        EndpointState globalEndpoint = createGlobalEndpoint();
        ResourcePoolState rp = createResourcePoolForEndpoint(globalEndpoint);
        createVmHostCompute(globalEndpoint, rp);
        GroupResourcePlacementState globalGroupState = createGroupPlacementFor(rp);
        addForDeletion(globalGroupState);

        // create another suitable group placement but with a group that should not be selected
        GroupResourcePlacementState differentGroup = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
        differentGroup.tenantLinks = Collections
                .singletonList(MultiTenantDocument.TENANTS_PREFIX + "/different-group");
        differentGroup = doPost(differentGroup, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(differentGroup);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPlacementState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = groupPlacementState.maxNumberInstances + 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);

        globalGroupState = getDocument(GroupResourcePlacementState.class,
                globalGroupState.documentSelfLink);

        assertEquals(globalGroupState.allocatedInstancesCount, task.resourceCount);
    }

    @Test
    public void testSatisfiedAntiRequirement() throws Throwable {
        GroupResourcePlacementState groupPlacementState = doPost(
                TestRequestStateFactory
                        .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE),
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        ComputeDescription descPatch = new ComputeDescription();
        addConstraintToComputeDesc(descPatch, "cap", "pci", true, true);
        doPatch(descPatch, hostDesc.documentSelfLink);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPlacementState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);
    }

    @Test
    public void testSatisfiedHardRequirement() throws Throwable {
        GroupResourcePlacementState groupPlacementState = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
        groupPlacementState.resourcePoolLink = computeResourcePool.documentSelfLink;
        groupPlacementState = doPost(groupPlacementState,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        TagState tag = new TagState();
        tag.key = "cap";
        tag.value = "pci";
        tag.tenantLinks = TestRequestStateFactory
                .createTenantLinks(TestRequestStateFactory.TENANT_NAME);
        tag = doPost(tag, TagService.FACTORY_LINK);

        ResourcePoolState rpPatch = new ResourcePoolState();
        rpPatch.tagLinks = Collections.singleton(tag.documentSelfLink);
        doPatch(rpPatch, computeResourcePool.documentSelfLink);

        ComputeDescription descPatch = new ComputeDescription();
        addConstraintToComputeDesc(descPatch, "cap", "pci", true, false);
        doPatch(descPatch, hostDesc.documentSelfLink);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPlacementState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);
    }

    @Test
    public void testUnsatisfiedHardRequirement() throws Throwable {
        GroupResourcePlacementState groupPlacementState = doPost(
                TestRequestStateFactory
                        .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE),
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        ComputeDescription descPatch = new ComputeDescription();
        addConstraintToComputeDesc(descPatch, "cap", "pci", true, false);
        doPatch(descPatch, hostDesc.documentSelfLink);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPlacementState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskError(task.documentSelfLink, ComputeReservationTaskState.class);

        Assert.assertTrue(
                "Expected 'properly tagged' in error msg '" + task.taskInfo.failure.message + "'",
                task.taskInfo.failure.message.contains(" properly tagged"));
    }

    @Test
    public void testUnsatisfiedSoftRequirement() throws Throwable {
        GroupResourcePlacementState groupPlacementState = doPost(
                TestRequestStateFactory
                        .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE),
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        ComputeDescription descPatch = new ComputeDescription();
        addConstraintToComputeDesc(descPatch, "cap", "pci", false, false);
        doPatch(descPatch, hostDesc.documentSelfLink);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPlacementState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);
    }

    private static void addConstraintToComputeDesc(ComputeDescription computeDesc, String tagKey,
            String tagValue, boolean isHard, boolean isAnti) {
        Constraint constraint = new Constraint();
        constraint.conditions = Arrays.asList(TestRequestStateFactory.createCondition(tagKey,
                tagValue, isHard, isAnti));
        computeDesc.constraints = new HashMap<>();
        computeDesc.constraints.put(ComputeConstants.COMPUTE_PLACEMENT_CONSTRAINT_KEY, constraint);
    }

    private EndpointState createGlobalEndpoint() throws Throwable {
        EndpointState endpoint = TestRequestStateFactory
                .createEndpoint(UUID.randomUUID().toString(), EndpointType.aws);
        endpoint.tenantLinks = null;
        endpoint = getOrCreateDocument(endpoint, EndpointAdapterService.SELF_LINK);
        return endpoint;
    }

    private ResourcePoolState createResourcePoolForEndpoint(EndpointState endpoint)
            throws Throwable {
        ResourcePoolState rp = TestRequestStateFactory
                .createResourcePool(UUID.randomUUID().toString(), endpoint.documentSelfLink);
        rp.tenantLinks = endpoint.tenantLinks;
        rp = getOrCreateDocument(rp, ResourcePoolService.FACTORY_LINK);
        return rp;
    }

    private GroupResourcePlacementState createGroupPlacementFor(ResourcePoolState rp)
            throws Throwable {
        GroupResourcePlacementState rgp = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
        rgp.resourcePoolLink = rp.documentSelfLink;
        rgp.tenantLinks = rp.tenantLinks;
        rgp = getOrCreateDocument(rgp, GroupResourcePlacementService.FACTORY_LINK);
        return rgp;
    }

    private ComputeState createVmHostCompute(EndpointState endpoint, ResourcePoolState rp)
            throws Throwable {
        ComputeDescription cd = TestRequestStateFactory
                .createComputeDescriptionForVmGuestChildren();
        cd.documentSelfLink = cd.id;
        cd.tenantLinks = endpoint.tenantLinks;
        cd.authCredentialsLink = endpoint.authCredentialsLink;
        cd = getOrCreateDocument(cd, ComputeDescriptionService.FACTORY_LINK);

        ComputeState vmHostComputeState = TestRequestStateFactory.createVmHostComputeState();
        vmHostComputeState.id = UUID.randomUUID().toString();
        vmHostComputeState.documentSelfLink = vmHostComputeState.id;
        vmHostComputeState.resourcePoolLink = rp.documentSelfLink;
        vmHostComputeState.descriptionLink = cd.documentSelfLink;
        vmHostComputeState.type = ComputeType.VM_HOST;
        vmHostComputeState.powerState = PowerState.ON;
        vmHostComputeState.tenantLinks = endpoint.tenantLinks;
        vmHostComputeState = getOrCreateDocument(vmHostComputeState, ComputeService.FACTORY_LINK);
        assertNotNull(vmHostComputeState);

        addForDeletion(vmHostComputeState);

        return vmHostComputeState;
    }
}
