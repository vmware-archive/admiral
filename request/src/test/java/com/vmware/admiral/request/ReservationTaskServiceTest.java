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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.DeploymentPolicyService;
import com.vmware.admiral.compute.container.DeploymentPolicyService.DeploymentPolicy;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class ReservationTaskServiceTest extends RequestBaseTest {

    @Override
    public void setUp() throws Throwable {
        startServices(host);
        MockDockerAdapterService.resetContainers();

        setUpDockerHostAuthentication();
        // setup Docker Host:
        ResourcePoolService.ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        createDockerHost(dockerHostDesc, resourcePool);
        createContainerDescription();

        // clean the default reservation for the test below
        try {
            delete(DEFAULT_GROUP_RESOURCE_POLICY);
        } catch (Throwable e) {
            host.log("Exception during cleanup for: " + DEFAULT_GROUP_RESOURCE_POLICY);
        }
    }

    @Test
    public void testReservationTaskLifeCycleWhenNoAvailableGroupPlacements() throws Throwable {
        GroupResourcePlacementState groupPlacementState = doPost(
                TestRequestStateFactory.createGroupResourcePlacementState(),
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = groupPlacementState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = groupPlacementState.maxNumberInstances + 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        waitForTaskError(task.documentSelfLink, ReservationTaskState.class);
    }

    @Test
    public void testReservationTaskLifeCycle() throws Throwable {
        GroupResourcePlacementState groupPlacementState = TestRequestStateFactory
                .createGroupResourcePlacementState();
        groupPlacementState.maxNumberInstances = 10;
        groupPlacementState.resourcePoolLink = resourcePool.documentSelfLink;
        groupPlacementState.customProperties = new HashMap<>();
        groupPlacementState.customProperties.put("key1", "placement-value1");
        groupPlacementState.customProperties.put("key2", "placement-value2");
        groupPlacementState = doPost(groupPlacementState,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        GroupResourcePlacementState notEnougInstancesPlacement = TestRequestStateFactory
                .createGroupResourcePlacementState();
        notEnougInstancesPlacement.name = "not available";
        notEnougInstancesPlacement.maxNumberInstances = 4;
        notEnougInstancesPlacement = doPost(notEnougInstancesPlacement,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(notEnougInstancesPlacement);

        GroupResourcePlacementState differentGroupPlacement = TestRequestStateFactory
                .createGroupResourcePlacementState();
        differentGroupPlacement.maxNumberInstances = 10;
        differentGroupPlacement.name = "different group";
        differentGroupPlacement.tenantLinks = Collections.singletonList("different-group");
        differentGroupPlacement = doPost(differentGroupPlacement,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(differentGroupPlacement);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = groupPlacementState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.customProperties = new HashMap<>();
        task.customProperties.put("key1", "reservation-task-value1");

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        groupPlacementState = getDocument(GroupResourcePlacementState.class,
                groupPlacementState.documentSelfLink);
        assertEquals(groupPlacementState.documentSelfLink, task.groupResourcePlacementLink);

        assertEquals(groupPlacementState.allocatedInstancesCount, task.resourceCount);
        assertEquals(1, groupPlacementState.resourceQuotaPerResourceDesc.size());
        Long countPerDesc = groupPlacementState.resourceQuotaPerResourceDesc
                .get(task.resourceDescriptionLink);
        assertEquals(task.resourceCount, countPerDesc.longValue());

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
                .createGroupResourcePlacementState();
        placementState.maxNumberInstances = 10;
        placementState.resourcePoolLink = resourcePool.documentSelfLink;
        placementState.priority = 3;
        placementState = doPost(placementState, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementState);

        GroupResourcePlacementState placementState1 = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementState1.maxNumberInstances = 10;
        placementState1.resourcePoolLink = resourcePool.documentSelfLink;
        placementState1.priority = 1;
        placementState1 = doPost(placementState1, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementState1);

        GroupResourcePlacementState placementState2 = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementState2.maxNumberInstances = 10;
        placementState2.resourcePoolLink = resourcePool.documentSelfLink;
        placementState2.priority = 2;
        placementState2 = doPost(placementState2, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementState2);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = placementState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        placementState1 = getDocument(GroupResourcePlacementState.class,
                placementState1.documentSelfLink);
        assertEquals(placementState1.documentSelfLink, task.groupResourcePlacementLink);
    }

    @Test
    public void testReservationTaskLifeCycleUnlimitedMemoryPlacement() throws Throwable {
        GroupResourcePlacementState placementState = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementState.maxNumberInstances = 10;
        placementState.resourcePoolLink = resourcePool.documentSelfLink;
        placementState.priority = 3;
        placementState = doPost(placementState, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementState);

        GroupResourcePlacementState placementStateNotEnoughMemory = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementStateNotEnoughMemory.maxNumberInstances = 10;
        placementStateNotEnoughMemory.memoryLimit = containerDesc.memoryLimit * 4;
        placementStateNotEnoughMemory.resourcePoolLink = resourcePool.documentSelfLink;
        placementStateNotEnoughMemory.priority = 3;
        placementStateNotEnoughMemory = doPost(placementStateNotEnoughMemory,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementStateNotEnoughMemory);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = placementState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        placementState = getDocument(GroupResourcePlacementState.class,
                placementState.documentSelfLink);
        assertEquals(placementState.documentSelfLink, task.groupResourcePlacementLink);
    }

    @Test
    public void testReservationTaskLifeCycleUnlimitedInstancesPlacement() throws Throwable {
        GroupResourcePlacementState placementState = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementState.maxNumberInstances = 0;
        placementState.resourcePoolLink = resourcePool.documentSelfLink;
        placementState.priority = 3;
        placementState = doPost(placementState, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementState);

        GroupResourcePlacementState placementStateNotEnoughInstances = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementStateNotEnoughInstances.maxNumberInstances = 4;
        placementStateNotEnoughInstances.resourcePoolLink = resourcePool.documentSelfLink;
        placementStateNotEnoughInstances.priority = 3;
        placementStateNotEnoughInstances = doPost(placementStateNotEnoughInstances,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementStateNotEnoughInstances);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = placementState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        placementState = getDocument(GroupResourcePlacementState.class,
                placementState.documentSelfLink);
        assertEquals(placementState.documentSelfLink, task.groupResourcePlacementLink);
    }

    @Test
    public void testReservationTaskLifeCycleWithNoGroup() throws Throwable {
        GroupResourcePlacementState groupPlacementState = TestRequestStateFactory
                .createGroupResourcePlacementState();
        groupPlacementState.tenantLinks = null;
        groupPlacementState.maxNumberInstances = 100;
        groupPlacementState = doPost(groupPlacementState,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        GroupResourcePlacementState defaultPlacementState = TestRequestStateFactory
                .createGroupResourcePlacementState();
        defaultPlacementState.maxNumberInstances = 100;
        defaultPlacementState = doPost(defaultPlacementState,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(defaultPlacementState);

        // create another suitable group placement but with a group that should not be selected
        doPost(TestRequestStateFactory.createGroupResourcePlacementState(),
                GroupResourcePlacementService.FACTORY_LINK);

        ReservationTaskState taskTemplate = new ReservationTaskState();
        taskTemplate.tenantLinks = null;
        taskTemplate.resourceDescriptionLink = containerDesc.documentSelfLink;
        taskTemplate.resourceCount = 5;
        taskTemplate.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        int totalAllocatedResources = 0;
        int maxRequests = 5;

        for (int i = 0; i < maxRequests; i++) {
            ReservationTaskState task = doPost(taskTemplate,
                    ReservationTaskFactoryService.SELF_LINK);
            assertNotNull(task);

            task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);
            totalAllocatedResources += task.resourceCount;

            groupPlacementState = getDocument(GroupResourcePlacementState.class,
                    groupPlacementState.documentSelfLink);

            assertEquals(groupPlacementState.documentSelfLink, task.groupResourcePlacementLink);
            assertEquals(totalAllocatedResources, groupPlacementState.allocatedInstancesCount);
            assertEquals(1, groupPlacementState.resourceQuotaPerResourceDesc.size());
            Long countPerDesc = groupPlacementState.resourceQuotaPerResourceDesc
                    .get(task.resourceDescriptionLink);
            assertEquals(totalAllocatedResources, countPerDesc.longValue());
        }
    }

    @Test
    public void testReservationTaskLifeCycleWithGlobalGroup() throws Throwable {
        // create placement with same group but less number of instances:
        GroupResourcePlacementState groupPlacementState = TestRequestStateFactory
                .createGroupResourcePlacementState();
        groupPlacementState.maxNumberInstances = 2;
        groupPlacementState = doPost(groupPlacementState,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementState);

        // create global placement that should be selected since the group placement is not applicable.
        GroupResourcePlacementState globalGroupState = TestRequestStateFactory
                .createGroupResourcePlacementState();
        globalGroupState.tenantLinks = null;
        globalGroupState.maxNumberInstances = 100;
        globalGroupState = doPost(globalGroupState,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(globalGroupState);

        // create another suitable group placement but with a group that should not be selected
        GroupResourcePlacementState differentGroup = TestRequestStateFactory
                .createGroupResourcePlacementState();
        differentGroup.tenantLinks = Collections.singletonList("different-group");
        differentGroup.maxNumberInstances = 100;
        differentGroup = doPost(differentGroup, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(differentGroup);

        ReservationTaskState taskTemplate = new ReservationTaskState();
        taskTemplate.tenantLinks = groupPlacementState.tenantLinks;
        taskTemplate.resourceDescriptionLink = containerDesc.documentSelfLink;
        taskTemplate.resourceCount = groupPlacementState.maxNumberInstances + 1;
        taskTemplate.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        int totalAllocatedResources = 0;
        int maxRequests = 5;

        for (int i = 0; i < maxRequests; i++) {
            ReservationTaskState task = doPost(taskTemplate,
                    ReservationTaskFactoryService.SELF_LINK);
            assertNotNull(task);

            task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

            totalAllocatedResources += task.resourceCount;

            globalGroupState = getDocument(GroupResourcePlacementState.class,
                    globalGroupState.documentSelfLink);

            assertEquals(totalAllocatedResources, globalGroupState.allocatedInstancesCount);
            assertEquals(globalGroupState.documentSelfLink, task.groupResourcePlacementLink);
            assertEquals(1, globalGroupState.resourceQuotaPerResourceDesc.size());
            Long countPerDesc = globalGroupState.resourceQuotaPerResourceDesc
                    .get(task.resourceDescriptionLink);
            assertEquals(totalAllocatedResources, countPerDesc.longValue());
        }
    }

    @Test
    public void testDeploymentPoliciesOnPolicy() throws Throwable {
        DeploymentPolicy policy = createDeploymentPolicy();

        containerDesc.deploymentPolicyId = extractId(policy.documentSelfLink);
        doPut(containerDesc);

        GroupResourcePlacementState placementState = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementState = doPost(placementState, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementState);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = placementState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        // update the placement and succeed
        placementState.deploymentPolicyLink = policy.documentSelfLink;
        doPut(placementState);

        task = new ReservationTaskState();
        task.tenantLinks = placementState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        placementState = getDocument(GroupResourcePlacementState.class,
                placementState.documentSelfLink);
        assertEquals(placementState.documentSelfLink, task.groupResourcePlacementLink);
    }

    @Test
    public void testDeploymentPoliciesOnHost() throws Throwable {
        DeploymentPolicy policy = createDeploymentPolicy();

        containerDesc.deploymentPolicyId = extractId(policy.documentSelfLink);
        doPut(containerDesc);

        GroupResourcePlacementState placementState = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementState = doPost(placementState, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementState);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = placementState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        // update the container host and succeed
        computeHost.customProperties.put(ContainerHostService.CUSTOM_PROPERTY_DEPLOYMENT_POLICY,
                policy.documentSelfLink);
        doPut(computeHost);

        task = new ReservationTaskState();
        task.tenantLinks = placementState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        placementState = getDocument(GroupResourcePlacementState.class,
                placementState.documentSelfLink);
        assertEquals(placementState.documentSelfLink, task.groupResourcePlacementLink);
    }

    @Test
    public void testDeploymentPoliciesOnMultipleHostsAndPlacements() throws Throwable {
        DeploymentPolicy policy = createDeploymentPolicy();

        final GroupResourcePlacementState placement1 = createPlacement(null);
        final GroupResourcePlacementState placement2 = createPlacement(null);
        final GroupResourcePlacementState placement3 = createPlacement(null);

        createContainerHost(null, placement3.resourcePoolLink);
        createContainerHost(null, placement1.resourcePoolLink);

        containerDesc.deploymentPolicyId = extractId(policy.documentSelfLink);
        doPut(containerDesc);

        // Provision when deployment policy is set to cont. desc. only
        ReservationTaskState task = new ReservationTaskState();
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        final ComputeState host3 = createContainerHost(policy, placement1.resourcePoolLink);
        final ComputeState host4 = createContainerHost(policy, placement2.resourcePoolLink);

        // Provision when policy has been set to 2 hosts
        task = new ReservationTaskState();
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        assertNotNull(task.hostSelections);
        assertEquals(2, task.hostSelections.size());

        List<String> expectedHostsList = new ArrayList<ComputeState>(
                Arrays.asList(host3, host4)).stream()
                        .map((e) -> e.documentSelfLink).collect(Collectors.toList());
        assertTrue(expectedHostsList.containsAll(
                task.hostSelections.stream().map(h -> h.hostLink)
                        .collect(Collectors.toList())));

        GroupResourcePlacementState placement4 = createPlacement(policy);
        GroupResourcePlacementState placement5 = createPlacement(policy);

        createContainerHost(null, placement4.resourcePoolLink);
        final ComputeState host6 = createContainerHost(policy, placement5.resourcePoolLink);

        // Provision when policy has been set to both hosts and placements
        task = new ReservationTaskState();
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);
        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        final String expectedPlacementLink = placement5.documentSelfLink;
        assertEquals(expectedPlacementLink, task.groupResourcePlacementLink);

        assertNotNull(task.hostSelections);
        assertEquals(1, task.hostSelections.size());
        assertEquals(host6.documentSelfLink, task.hostSelections.get(0).hostLink);
    }

    // test when the request comes from the Container tab
    // the ReservationTaskState contains tenant links in format "/tenants/{tenant-name}"
    // expected to return all placements for the tenant
    @Test
    public void testReservationTaskLifeCycleWithRequestComingFromTheContainerTab()
            throws Throwable {

        // create placement without a group
        GroupResourcePlacementState placement = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placement = doPost(placement,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placement);

        // create placement with Development group
        GroupResourcePlacementState placementDevelopment = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementDevelopment.tenantLinks = TestRequestStateFactory.createTenantLinks(
                TestRequestStateFactory.TENANT_NAME,
                TestRequestStateFactory.GROUP_NAME_DEVELOPMENT);
        placementDevelopment = doPost(placementDevelopment,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementDevelopment);

        // create placement with Finance group
        GroupResourcePlacementState placementFinance = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementFinance.tenantLinks = TestRequestStateFactory.createTenantLinks(
                TestRequestStateFactory.TENANT_NAME,
                TestRequestStateFactory.GROUP_NAME_FINANCE);
        placementFinance = doPost(placementFinance,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementFinance);

        // create a reservation
        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = TestRequestStateFactory
                .createTenantLinks(TestRequestStateFactory.TENANT_NAME);
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 3;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        placementDevelopment = getDocument(GroupResourcePlacementState.class,
                placementDevelopment.documentSelfLink);

        placementFinance = getDocument(GroupResourcePlacementState.class,
                placementFinance.documentSelfLink);

        assertNotNull(task.groupResourcePlacementLink);
        assertEquals(2, task.resourcePoolsPerGroupPlacementLinks.size());

    }

    // test when the request comes from Catalog
    // the ReservationTaskState contains tenant links in format
    // "/tenants/{tenant-name}/groups/{group-id}"
    // expected to return the placement that match the {group-id}
    @Test
    public void testReservationTaskLifeCycleWithRequestComingFromTheCatalog() throws Throwable {

        // create placement without a group
        GroupResourcePlacementState placement = TestRequestStateFactory
                .createGroupResourcePlacementState();

        //NOTE: the userId is also part of the links.
        placement.tenantLinks = TestRequestStateFactory.createTenantLinks(
                TestRequestStateFactory.TENANT_NAME,
                TestRequestStateFactory.GROUP_NAME_FINANCE,
                TestRequestStateFactory.USER_NAME);
        placement = doPost(placement,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placement);

        // create placement with Development group
        GroupResourcePlacementState placementDevelopment = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementDevelopment.tenantLinks = TestRequestStateFactory.createTenantLinks(
                TestRequestStateFactory.TENANT_NAME,
                TestRequestStateFactory.GROUP_NAME_DEVELOPMENT);
        placementDevelopment = doPost(placementDevelopment,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementDevelopment);

        // create placement with Finance group
        GroupResourcePlacementState placementFinance = TestRequestStateFactory
                .createGroupResourcePlacementState();
        placementFinance.tenantLinks = TestRequestStateFactory.createTenantLinks(
                TestRequestStateFactory.TENANT_NAME,
                TestRequestStateFactory.GROUP_NAME_FINANCE);
        placementFinance = doPost(placementFinance,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementFinance);

        // create a reservation
        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = TestRequestStateFactory
                .createTenantLinks(TestRequestStateFactory.TENANT_NAME,
                        TestRequestStateFactory.GROUP_NAME_DEVELOPMENT);
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 3;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        placementDevelopment = getDocument(GroupResourcePlacementState.class,
                placementDevelopment.documentSelfLink);

        placementFinance = getDocument(GroupResourcePlacementState.class,
                placementFinance.documentSelfLink);

        assertEquals(task.groupResourcePlacementLink, placementDevelopment.documentSelfLink);
    }

    // test when the request comes from Catalog
    // the ReservationTaskState contains tenant links in format
    // "/tenants/{tenant-name}/groups/{group-id}"
    // expected to return the placement that match the {group-id}
    @Test
    public void testReservationTaskLifeCycleWithRequestComingFromTheCatalogWithTwoRoles()
            throws Throwable {

        // create placement without a group DEVELOPMENT
        GroupResourcePlacementState placementDevelopment = TestRequestStateFactory
                .createGroupResourcePlacementState();

        //NOTE: the userId is also part of the links.
        placementDevelopment.tenantLinks = TestRequestStateFactory.createTenantLinks(
                TestRequestStateFactory.TENANT_NAME,
                TestRequestStateFactory.GROUP_NAME_DEVELOPMENT,
                TestRequestStateFactory.USER_NAME);
        placementDevelopment = doPost(placementDevelopment,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(placementDevelopment);

        // create a reservation
        //NOTE: the user as part of the catalog request is different.
        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = TestRequestStateFactory
                .createTenantLinks(TestRequestStateFactory.TENANT_NAME,
                        TestRequestStateFactory.GROUP_NAME_DEVELOPMENT,
                        "requestorUserId");

        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 3;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        placementDevelopment = getDocument(GroupResourcePlacementState.class,
                placementDevelopment.documentSelfLink);

        assertEquals(task.groupResourcePlacementLink, placementDevelopment.documentSelfLink);
    }

    @Test
    public void testReservationTaskLifeCycleWithMemoryLimitInRange() throws Throwable {
        // create placement with same group but less available memory:
        GroupResourcePlacementState groupPlacementStateLessMemory = TestRequestStateFactory
                .createGroupResourcePlacementState();
        groupPlacementStateLessMemory.memoryLimit = containerDesc.memoryLimit * 2 - 1;
        groupPlacementStateLessMemory = doPost(groupPlacementStateLessMemory,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementStateLessMemory);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = groupPlacementStateLessMemory.tenantLinks;
        task.resourceCount = 2;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);

        //not enough memory, request should fail
        task = waitForTaskError(task.documentSelfLink, ReservationTaskState.class);

        // create placement with same group with more available memory:
        GroupResourcePlacementState groupPlacementStateEnoughMemory = TestRequestStateFactory
                .createGroupResourcePlacementState();
        groupPlacementStateEnoughMemory.memoryLimit = containerDesc.memoryLimit * 2 + 1;
        groupPlacementStateEnoughMemory = doPost(groupPlacementStateEnoughMemory,
                GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(groupPlacementStateEnoughMemory);

        task = new ReservationTaskState();
        task.tenantLinks = groupPlacementStateLessMemory.tenantLinks;
        task.resourceCount = 2;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        groupPlacementStateEnoughMemory = getDocument(GroupResourcePlacementState.class,
                groupPlacementStateEnoughMemory.documentSelfLink);

        assertEquals(groupPlacementStateEnoughMemory.maxNumberInstances - task.resourceCount,
                groupPlacementStateEnoughMemory.availableInstancesCount);
        assertEquals(groupPlacementStateEnoughMemory.documentSelfLink,
                task.groupResourcePlacementLink);
        assertEquals(1, groupPlacementStateEnoughMemory.resourceQuotaPerResourceDesc.size());
        Long countPerDesc = groupPlacementStateEnoughMemory.resourceQuotaPerResourceDesc
                .get(task.resourceDescriptionLink);
        assertEquals(task.resourceCount, countPerDesc.longValue());
    }

    private GroupResourcePlacementState createPlacement(DeploymentPolicy policy)
            throws Throwable {

        ResourcePoolState resourcePool = new ResourcePoolState();
        resourcePool.name = ReservationTaskServiceTest.class.toString()
                + System.currentTimeMillis();
        resourcePool = doPost(resourcePool, ResourcePoolService.FACTORY_LINK);
        addForDeletion(resourcePool);

        GroupResourcePlacementState rsrvState = new GroupResourcePlacementState();
        rsrvState.resourcePoolLink = resourcePool.documentSelfLink;
        rsrvState.name = ReservationTaskServiceTest.class.toString() + System.currentTimeMillis();
        rsrvState.maxNumberInstances = 10;
        rsrvState.memoryLimit = 0L;
        rsrvState.cpuShares = 3;
        if (policy != null) {
            rsrvState.deploymentPolicyLink = policy.documentSelfLink;
        }

        rsrvState = doPost(rsrvState, GroupResourcePlacementService.FACTORY_LINK);
        addForDeletion(rsrvState);

        return rsrvState;
    }

    private ComputeState createContainerHost(DeploymentPolicy policy, String resourcePoolLink)
            throws Throwable {
        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.DOCKER_CONTAINER.toString()));
        computeDesc.instanceAdapterReference = UriUtils.buildUri(ServiceHost.LOCAL_HOST, 8484,
                "compute-test-adapter", null);
        computeDesc.authCredentialsLink = UriUtils.buildUriPath(
                AuthCredentialsService.FACTORY_LINK,
                CommonTestStateFactory.AUTH_CREDENTIALS_ID);
        computeDesc = doPost(computeDesc, ComputeDescriptionService.FACTORY_LINK);
        addForDeletion(computeDesc);

        ComputeState cs = new ComputeState();
        Random r = new Random();
        cs.address = r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256) + "."
                + r.nextInt(256);
        cs.descriptionLink = computeDesc.documentSelfLink;
        cs.powerState = PowerState.ON;
        cs.resourcePoolLink = resourcePoolLink;
        cs.adapterManagementReference = URI.create("http://localhost:8081");
        cs.customProperties = new HashMap<>();

        if (policy != null) {
            cs.customProperties.put(ContainerHostService.CUSTOM_PROPERTY_DEPLOYMENT_POLICY,
                    policy.documentSelfLink);
        }

        cs = doPost(cs, ComputeService.FACTORY_LINK);
        addForDeletion(cs);
        return cs;
    }

    private DeploymentPolicy createDeploymentPolicy() throws Throwable {
        DeploymentPolicy policy = new DeploymentPolicy();
        policy.name = "deployment policy";
        policy.description = "policy description";
        policy.tenantLinks = TestRequestStateFactory
                .createTenantLinks(TestRequestStateFactory.TENANT_NAME);
        policy = doPost(policy, DeploymentPolicyService.FACTORY_LINK);
        addForDeletion(policy);

        return policy;
    }
}
