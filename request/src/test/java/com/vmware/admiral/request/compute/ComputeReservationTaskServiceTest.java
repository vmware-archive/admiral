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

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.request.util.TestRequestStateFactory.createGroupResourcePlacementState;
import static com.vmware.admiral.request.util.TestRequestStateFactory.createTenantLinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.compute.PlacementZoneConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
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
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.QueryTask;

public class ComputeReservationTaskServiceTest extends ComputeRequestBaseTest {

    @Override
    public void setUp() throws Throwable {
        startServices(host);
        createEndpoint();
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
        createVmHostCompute(globalEndpoint, rp.documentSelfLink);
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
    public void testReservationTaskLifeCycleForVsphere() throws Throwable {
        EndpointState vsphereEndpoint = createVsphereEndpoint();
        ResourcePoolState rp = createResourcePoolForEndpoint(vsphereEndpoint);
        createVmHostCompute(vsphereEndpoint, rp.documentSelfLink);
        GroupResourcePlacementState globalGroupState = createGroupPlacementFor(rp);
        globalGroupState.priority = 3;
        addForDeletion(globalGroupState);

        // create another suitable group placement but with a group that should not be selected
        doPost(TestRequestStateFactory.createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE),
                GroupResourcePlacementService.FACTORY_LINK);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = null;
        ComputeDescription cd = createVsphereComputeDescription(false, globalGroupState);
        task.resourceDescriptionLink = cd.documentSelfLink;
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
        createVmHostCompute(globalEndpoint, rp.documentSelfLink);
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
        setConstraintToComputeDesc(descPatch, "cap", "pci", true, true);
        doPatch(descPatch, hostDesc.documentSelfLink);

        kickOffComputeReservationTask(hostDesc);
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
        setConstraintToComputeDesc(descPatch, "cap", "pci", true, false);
        doPatch(descPatch, hostDesc.documentSelfLink);

        kickOffComputeReservationTask(hostDesc);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void testMergedResPoolAndHostsTags() throws Throwable {

        // Helper createXXX functions

        final Function<String, TagState> createTag = tagName -> {
            final TagState tag = new TagState();
            tag.key = tagName + ".key";
            tag.value = tagName + ".value";
            tag.tenantLinks = createTenantLinks(TestRequestStateFactory.TENANT_NAME);
            try {
                return doPost(tag, TagService.FACTORY_LINK);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };

        final Function<List, GroupResourcePlacementState> createPlacement = args -> {
            Object index = args.get(0);
            List<TagState> rpTags = args.subList(1, args.size());
            try {
                ResourcePoolState resPool = TestRequestStateFactory.createResourcePool(
                        "respool" + index, endpoint.documentSelfLink);

                resPool.tagLinks = rpTags.stream()
                        .map(rpTag -> rpTag.documentSelfLink)
                        .collect(Collectors.toSet());

                resPool.customProperties.put(
                        PlacementZoneConstants.RESOURCE_TYPE_CUSTOM_PROP_NAME,
                        ResourceType.COMPUTE_TYPE.getName());

                resPool = doPost(resPool, ResourcePoolService.FACTORY_LINK);

                GroupResourcePlacementState placement = createGroupResourcePlacementState(
                        ResourceType.COMPUTE_TYPE, "placement" + index);

                placement.resourcePoolLink = resPool.documentSelfLink;

                placement = doPost(placement, GroupResourcePlacementService.FACTORY_LINK);

                return placement;

            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };

        final Function<List, ComputeState> createHost = args -> {
            Object index = args.get(0);
            GroupResourcePlacementState placement = (GroupResourcePlacementState) args.get(1);
            List<TagState> hostTags = args.subList(2, args.size());
            try {
                ComputeState host = createVmHostCompute(endpoint, placement.resourcePoolLink);

                host.name = "host" + index;

                host.tagLinks = hostTags.stream()
                        .map(hostTag -> hostTag.documentSelfLink)
                        .collect(Collectors.toSet());

                return doPatch(host, host.documentSelfLink);

            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };

        final Consumer<List> assertReservationTask = (expected) -> {

            GroupResourcePlacementState expectedPlacement = (GroupResourcePlacementState) expected.get(0);
            ComputeState expectedHost = (ComputeState) expected.get(1);

            ComputeReservationTaskState reservationTask;
            try {
                reservationTask = kickOffComputeReservationTask(hostDesc);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }

            Assert.assertEquals("Selected placement mismatch",
                    expectedPlacement.documentSelfLink,
                    reservationTask.groupResourcePlacementLink);

            HostSelection selectedHost = reservationTask.selectedComputePlacementHosts.get(0);

            Assert.assertEquals("Selected placement host mismatch",
                    expectedHost.documentSelfLink, selectedHost.hostLink);

            Assert.assertTrue("Selected placement host's resourcePoolLinks mismatch",
                    selectedHost.resourcePoolLinks.contains(expectedPlacement.resourcePoolLink));
        };

        final TagState rpTag_hard = createTag.apply("rpTag_hard");
        final TagState rpTag_soft = createTag.apply("rpTag_soft");
        final TagState hostTag_hard = createTag.apply("hostTag_hard");
        final TagState hostTag_soft = createTag.apply("hostTag_soft");

        // Configure constraints on CD:
        // - rpTag HARD condition should be satisfied at ResPool level
        // - rpTag SOFT condition might be satisfied at ResPool level
        // - hostTag HARD condition should be satisfied at Host level
        // - hostTag SOFT condition might be satisfied at Host level
        setConstraintToComputeDesc(hostDesc,
                new ConditionSpec(rpTag_hard.key, rpTag_hard.value, /* hard */ true, false),
                new ConditionSpec(rpTag_soft.key, rpTag_soft.value, /* soft */ false, false),
                new ConditionSpec(hostTag_hard.key, hostTag_hard.value, /* hard */ true, false),
                new ConditionSpec(hostTag_soft.key, hostTag_soft.value, /* soft */ false, false));
        doPatch(hostDesc, hostDesc.documentSelfLink);

        // Use-cases under testing

        // This should never be matched cause RP_HARD and HOST_HARD are NOT satisfied
        {
            GroupResourcePlacementState placement0 = createPlacement.apply(asList("0", rpTag_soft));

            createHost.apply(asList("0", placement0, hostTag_soft));

            kickOffComputeReservationTask(hostDesc, false);
        }

        // This matches both RP_HARD and HOST_HARD tags
        {
            GroupResourcePlacementState placement1 = createPlacement.apply(asList("1", rpTag_hard));

            ComputeState host1 = createHost.apply(asList("1", placement1, hostTag_hard));

            assertReservationTask.accept(asList(placement1, host1));
        }

        final ComputeState sharedHost;

        // This matches both RP_HARD and HOST_HARD/SOFT tags
        {
            GroupResourcePlacementState placement2 = createPlacement.apply(asList("2", rpTag_hard));

            sharedHost = createHost.apply(asList("shared", placement2, hostTag_hard, hostTag_soft));

            assertReservationTask.accept(asList(placement2, sharedHost));
        }

        // This matches both RP_HARD/SOFT and HOST_HARD/SOFT tags
        {
            GroupResourcePlacementState placement3 = createPlacement.apply(
                    asList("3", rpTag_hard, rpTag_soft));

            // Elastic PZ which includes all hosts with 'hostTag_hard'
            final ElasticPlacementZoneState elasticPZ = new ElasticPlacementZoneState();
            elasticPZ.resourcePoolLink = placement3.resourcePoolLink;
            elasticPZ.tagLinksToMatch = Sets.newHashSet(hostTag_hard.documentSelfLink);
            doPost(elasticPZ, ElasticPlacementZoneService.FACTORY_LINK);

            assertReservationTask.accept(asList(placement3, sharedHost));
        }
    }

    private ComputeReservationTaskState kickOffComputeReservationTask(ComputeDescription compDesc,
            boolean waitForSuccess)
            throws Throwable {

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = compDesc.tenantLinks;
        task.resourceDescriptionLink = compDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);

        if (waitForSuccess) {
            task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);
        } else {
            task = waitForTaskError(task.documentSelfLink, ComputeReservationTaskState.class);
        }

        return task;
    }

    private ComputeReservationTaskState kickOffComputeReservationTask(ComputeDescription compDesc)
            throws Throwable {

        return kickOffComputeReservationTask(compDesc, true);
    }

    @Test
    public void testDiskSatisfiedHardRequirement() throws Throwable {
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
        setConstraintToComputeDesc(descPatch, "cap", "pci", true, false);
        descPatch.diskDescLinks = buildDiskStateWithHardConstraint();
        doPatch(descPatch, hostDesc.documentSelfLink);

        StorageProfileService.StorageProfile storageProfile = buildStorageProfileWithConstraints(
                groupPlacementState.tenantLinks);
        createProfileWithInstanceType(
                "small", "t1.micro", "coreos", "ami-123456", storageProfile, groupPlacementState);
        createProfileWithInstanceType(
                "small", "t2.micro", "coreos", "ami-234355", storageProfile, groupPlacementState);

        kickOffComputeReservationTask(hostDesc);
    }

    @Test
    public void testDiskUnSatisfiedHardRequirement() throws Throwable {
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
        setConstraintToComputeDesc(descPatch, "cap", "pci", true, false);
        descPatch.diskDescLinks = buildDiskStateWithHardConstraint();
        doPatch(descPatch, hostDesc.documentSelfLink);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPlacementState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        ComputeReservationTaskState taskState = waitForTaskError(task.documentSelfLink,
                ComputeReservationTaskState.class);
        assertTrue(taskState.taskInfo.failure.message
                .contains("No matching storage defined in profile"));
    }

    @Test
    public void testDiskSoftRequirement() throws Throwable {
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
        setConstraintToComputeDesc(descPatch, "cap", "pci", true, false);
        descPatch.diskDescLinks = buildDiskStateWithSoftConstraint();
        doPatch(descPatch, hostDesc.documentSelfLink);

        StorageProfileService.StorageProfile storageProfile = buildStorageProfileWithConstraints(
                groupPlacementState.tenantLinks);
        createProfileWithInstanceType(
                "small", "t1.micro", "coreos", "ami-123456", storageProfile, groupPlacementState);

        kickOffComputeReservationTask(hostDesc);
        ;
    }

    @Test
    public void testUnsatisfiedHardRequirement() throws Throwable {
        GroupResourcePlacementState groupPlacementState = doPost(
                TestRequestStateFactory
                        .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE),
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        ComputeDescription descPatch = new ComputeDescription();
        setConstraintToComputeDesc(descPatch, "cap", "pci", true, false);
        doPatch(descPatch, hostDesc.documentSelfLink);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPlacementState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskError(task.documentSelfLink, ComputeReservationTaskState.class);

        assertTrue(
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
        setConstraintToComputeDesc(descPatch, "cap", "pci", false, false);
        doPatch(descPatch, hostDesc.documentSelfLink);

        kickOffComputeReservationTask(hostDesc);
    }

    @Test
    public void testEnhanceExtensibilityResponse() throws Throwable {
        ComputeReservationTaskService service = new ComputeReservationTaskService();
        service.setHost(host);

        GroupResourcePlacementState groupPlacementState1 = doPost(TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE, "p-1"),
                GroupResourcePlacementService.FACTORY_LINK);
        GroupResourcePlacementState groupPlacementState2 = doPost(TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE,
                        "p-2"),
                GroupResourcePlacementService.FACTORY_LINK);

        addForDeletion(groupPlacementState1);
        addForDeletion(groupPlacementState2);

        ComputeReservationTaskState task = kickOffComputeReservationTask(hostDesc);

        String taskLink = task.documentSelfLink;

        ComputeReservationTaskService.ExtensibilityCallbackResponse payload = (ComputeReservationTaskService.ExtensibilityCallbackResponse) service
                .notificationPayload(task);

        payload.placements = Arrays.asList("p-2");

        TestContext context = new TestContext(1, Duration.ofMinutes(5));

        service.enhanceExtensibilityResponse(task, payload).whenComplete((r, err) -> {
            try {
                ComputeReservationTaskState t = getDocument(ComputeReservationTaskState.class,
                        taskLink);

                assertEquals("Wrong number of placements", 1,
                        t.resourcePoolsPerGroupPlacementLinks.entrySet().size());

                assertTrue("Placement was not properly patched",
                        t.resourcePoolsPerGroupPlacementLinks
                                .containsKey(groupPlacementState2.documentSelfLink));

            } catch (Throwable t) {
                context.failIteration(t);
                return;
            }
            context.completeIteration();
        });

        context.await();
    }

    public static class ConditionSpec {

        final String tagKey;
        final String tagValue;
        final boolean isHard;
        final boolean isAnti;

        public ConditionSpec(String tagKey, String tagValue, boolean isHard, boolean isAnti) {
            this.tagKey = tagKey;
            this.tagValue = tagValue;
            this.isHard = isHard;
            this.isAnti = isAnti;
        }
    }

    private static void setConstraintToComputeDesc(
            ComputeDescription computeDesc, ConditionSpec... constraintSpecs) {

        final Constraint constraint = new Constraint();
        constraint.conditions = Arrays.stream(constraintSpecs)
                .map(constraintSpec -> TestRequestStateFactory.createCondition(
                        constraintSpec.tagKey,
                        constraintSpec.tagValue,
                        constraintSpec.isHard,
                        constraintSpec.isAnti))
                .collect(Collectors.toList());

        computeDesc.constraints = Collections.singletonMap(
                ComputeConstants.COMPUTE_PLACEMENT_CONSTRAINT_KEY, constraint);
    }

    private static void setConstraintToComputeDesc(ComputeDescription computeDesc,
            String tagKey, String tagValue, boolean isHard, boolean isAnti) {

        setConstraintToComputeDesc(computeDesc, new ConditionSpec(
                tagKey, tagValue, isHard, isAnti));
    }

    private ArrayList<String> buildDiskStateWithHardConstraint() throws Throwable {
        ArrayList<String> diskLinks = new ArrayList<>();

        DiskService.DiskState diskState1 = new DiskService.DiskState();
        diskState1.capacityMBytes = 1024;
        diskState1.type = DiskService.DiskType.HDD;
        diskState1.bootOrder = 1;
        diskState1.name = "Disk1";
        diskState1.constraint = new Constraint();

        List<Constraint.Condition> conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("FAST", null,
                Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("HA", null,
                Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState1.constraint.conditions = conditions;
        diskState1 = getOrCreateDocument(diskState1, DiskService.FACTORY_LINK);
        diskLinks.add(diskState1.documentSelfLink);

        return diskLinks;
    }

    private ArrayList<String> buildDiskStateWithSoftConstraint() throws Throwable {
        ArrayList<String> diskLinks = new ArrayList<>();

        DiskService.DiskState diskState1 = new DiskService.DiskState();
        diskState1.capacityMBytes = 1024;
        diskState1.type = DiskService.DiskType.HDD;
        diskState1.bootOrder = 1;
        diskState1.name = "Disk1";
        diskState1.constraint = new Constraint();

        List<Constraint.Condition> conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("CRITICAL", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("NON_REPLICATED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("NORMAL", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState1.constraint.conditions = conditions;
        diskState1 = doPost(diskState1, DiskService.FACTORY_LINK);
        diskLinks.add(diskState1.documentSelfLink);

        DiskService.DiskState diskState2 = new DiskService.DiskState();
        diskState2.capacityMBytes = 512;
        diskState2.type = DiskService.DiskType.FLOPPY;
        diskState2.bootOrder = 2;
        diskState2.name = "Disk2";
        diskState2.constraint = new Constraint();

        conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("NON_REPLICATED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("NORMAL", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState2.constraint.conditions = conditions;
        diskState2 = doPost(diskState2, DiskService.FACTORY_LINK);
        diskLinks.add(diskState2.documentSelfLink);

        return diskLinks;
    }

    private EndpointState createGlobalEndpoint() throws Throwable {
        EndpointState endpoint = TestRequestStateFactory
                .createEndpoint(UUID.randomUUID().toString(), EndpointType.aws);
        endpoint.tenantLinks = null;
        endpoint = getOrCreateDocument(endpoint, EndpointAdapterService.SELF_LINK);
        return endpoint;
    }

    private EndpointState createVsphereEndpoint() throws Throwable {
        EndpointState endpoint = TestRequestStateFactory
                .createEndpoint(UUID.randomUUID().toString(), EndpointType.vsphere);
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

    private ComputeState createVmHostCompute(EndpointState endpoint, String rpLink)
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
        vmHostComputeState.resourcePoolLink = rpLink;
        vmHostComputeState.descriptionLink = cd.documentSelfLink;
        vmHostComputeState.type = ComputeType.VM_HOST;
        vmHostComputeState.powerState = PowerState.ON;
        vmHostComputeState.tenantLinks = endpoint.tenantLinks;
        vmHostComputeState.endpointLink = endpoint.documentSelfLink;

        vmHostComputeState = getOrCreateDocument(vmHostComputeState, ComputeService.FACTORY_LINK);
        assertNotNull(vmHostComputeState);

        addForDeletion(vmHostComputeState);

        return vmHostComputeState;
    }
}
