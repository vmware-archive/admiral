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
import com.vmware.admiral.compute.container.GroupResourcePolicyService;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
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
    public void testReservationTaskLifeCycleWhenNoAvailableGroupPolicies() throws Throwable {
        GroupResourcePolicyState groupPolicyState = doPost(
                TestRequestStateFactory.createGroupResourcePolicyState(),
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(groupPolicyState);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = groupPolicyState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = groupPolicyState.maxNumberInstances + 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        waitForTaskError(task.documentSelfLink, ReservationTaskState.class);
    }

    @Test
    public void testReservationTaskLifeCycle() throws Throwable {
        GroupResourcePolicyState groupPolicyState = TestRequestStateFactory
                .createGroupResourcePolicyState();
        groupPolicyState.maxNumberInstances = 10;
        groupPolicyState.resourcePoolLink = resourcePool.documentSelfLink;
        groupPolicyState.customProperties = new HashMap<>();
        groupPolicyState.customProperties.put("key1", "policy-value1");
        groupPolicyState.customProperties.put("key2", "policy-value2");
        groupPolicyState = doPost(groupPolicyState, GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(groupPolicyState);

        GroupResourcePolicyState notEnougInstancesPolicy = TestRequestStateFactory
                .createGroupResourcePolicyState();
        notEnougInstancesPolicy.name = "not available";
        notEnougInstancesPolicy.maxNumberInstances = 4;
        notEnougInstancesPolicy = doPost(notEnougInstancesPolicy,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(notEnougInstancesPolicy);

        GroupResourcePolicyState differentGroupPolicy = TestRequestStateFactory
                .createGroupResourcePolicyState();
        differentGroupPolicy.maxNumberInstances = 10;
        differentGroupPolicy.name = "different group";
        differentGroupPolicy.tenantLinks = Collections.singletonList("different-group");
        differentGroupPolicy = doPost(differentGroupPolicy,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(differentGroupPolicy);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = groupPolicyState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.customProperties = new HashMap<>();
        task.customProperties.put("key1", "reservation-task-value1");

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        groupPolicyState = getDocument(GroupResourcePolicyState.class,
                groupPolicyState.documentSelfLink);
        assertEquals(groupPolicyState.documentSelfLink, task.groupResourcePolicyLink);

        assertEquals(groupPolicyState.allocatedInstancesCount, task.resourceCount);
        assertEquals(1, groupPolicyState.resourceQuotaPerResourceDesc.size());
        Long countPerDesc = groupPolicyState.resourceQuotaPerResourceDesc
                .get(task.resourceDescriptionLink);
        assertEquals(task.resourceCount, countPerDesc.longValue());

        // check custom properties overridden:
        assertEquals(2, task.customProperties.size());
        assertEquals(groupPolicyState.customProperties.get("key1"),
                task.customProperties.get("key1"));
        assertEquals(groupPolicyState.customProperties.get("key2"),
                task.customProperties.get("key2"));
    }

    @Test
    public void testReservationTaskLifeCyclePriorities() throws Throwable {
        GroupResourcePolicyState policyState = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyState.maxNumberInstances = 10;
        policyState.resourcePoolLink = resourcePool.documentSelfLink;
        policyState.priority = 3;
        policyState = doPost(policyState, GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyState);

        GroupResourcePolicyState policyState1 = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyState1.maxNumberInstances = 10;
        policyState1.resourcePoolLink = resourcePool.documentSelfLink;
        policyState1.priority = 1;
        policyState1 = doPost(policyState1, GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyState1);

        GroupResourcePolicyState policyState2 = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyState2.maxNumberInstances = 10;
        policyState2.resourcePoolLink = resourcePool.documentSelfLink;
        policyState2.priority = 2;
        policyState2 = doPost(policyState2, GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyState2);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = policyState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        policyState1 = getDocument(GroupResourcePolicyState.class,
                policyState1.documentSelfLink);
        assertEquals(policyState1.documentSelfLink, task.groupResourcePolicyLink);
    }

    @Test
    public void testReservationTaskLifeCycleUnlimitedMemoryPolicy() throws Throwable {
        GroupResourcePolicyState policyState = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyState.maxNumberInstances = 10;
        policyState.resourcePoolLink = resourcePool.documentSelfLink;
        policyState.priority = 3;
        policyState = doPost(policyState, GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyState);

        GroupResourcePolicyState policyStateNotEnoughMemory = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyStateNotEnoughMemory.maxNumberInstances = 10;
        policyStateNotEnoughMemory.memoryLimit = containerDesc.memoryLimit * 4;
        policyStateNotEnoughMemory.resourcePoolLink = resourcePool.documentSelfLink;
        policyStateNotEnoughMemory.priority = 3;
        policyStateNotEnoughMemory = doPost(policyStateNotEnoughMemory,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyStateNotEnoughMemory);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = policyState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        policyState = getDocument(GroupResourcePolicyState.class,
                policyState.documentSelfLink);
        assertEquals(policyState.documentSelfLink, task.groupResourcePolicyLink);
    }

    @Test
    public void testReservationTaskLifeCycleUnlimitedInstancesPolicy() throws Throwable {
        GroupResourcePolicyState policyState = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyState.maxNumberInstances = 0;
        policyState.resourcePoolLink = resourcePool.documentSelfLink;
        policyState.priority = 3;
        policyState = doPost(policyState, GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyState);

        GroupResourcePolicyState policyStateNotEnoughInstances = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyStateNotEnoughInstances.maxNumberInstances = 4;
        policyStateNotEnoughInstances.resourcePoolLink = resourcePool.documentSelfLink;
        policyStateNotEnoughInstances.priority = 3;
        policyStateNotEnoughInstances = doPost(policyStateNotEnoughInstances,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyStateNotEnoughInstances);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = policyState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        policyState = getDocument(GroupResourcePolicyState.class,
                policyState.documentSelfLink);
        assertEquals(policyState.documentSelfLink, task.groupResourcePolicyLink);
    }

    @Test
    public void testReservationTaskLifeCycleWithNoGroup() throws Throwable {
        GroupResourcePolicyState groupPolicyState = TestRequestStateFactory
                .createGroupResourcePolicyState();
        groupPolicyState.tenantLinks = null;
        groupPolicyState = doPost(groupPolicyState,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(groupPolicyState);

        // create another suitable group policy but with a group that should not be selected
        doPost(TestRequestStateFactory.createGroupResourcePolicyState(),
                GroupResourcePolicyService.FACTORY_LINK);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = null;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        groupPolicyState = getDocument(GroupResourcePolicyState.class,
                groupPolicyState.documentSelfLink);

        assertEquals(groupPolicyState.documentSelfLink, task.groupResourcePolicyLink);

        assertEquals(groupPolicyState.allocatedInstancesCount, task.resourceCount);
        assertEquals(1, groupPolicyState.resourceQuotaPerResourceDesc.size());
        Long countPerDesc = groupPolicyState.resourceQuotaPerResourceDesc
                .get(task.resourceDescriptionLink);
        assertEquals(task.resourceCount, countPerDesc.longValue());
    }

    @Test
    public void testReservationTaskLifeCycleWithGlobalGroup() throws Throwable {
        // create policy with same group but less number of instances:
        GroupResourcePolicyState groupPolicyState = TestRequestStateFactory
                .createGroupResourcePolicyState();
        groupPolicyState.maxNumberInstances = 2;
        groupPolicyState = doPost(groupPolicyState,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(groupPolicyState);

        // create global policy that should be selected since the groupPolicy is not applicable.
        GroupResourcePolicyState globalGroupState = TestRequestStateFactory
                .createGroupResourcePolicyState();
        globalGroupState.tenantLinks = null;
        globalGroupState = doPost(globalGroupState,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(globalGroupState);

        // create another suitable group policy but with a group that should not be selected
        GroupResourcePolicyState differentGroup = TestRequestStateFactory
                .createGroupResourcePolicyState();
        differentGroup.tenantLinks = Collections.singletonList("different-group");
        differentGroup = doPost(differentGroup, GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(differentGroup);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = groupPolicyState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = groupPolicyState.maxNumberInstances + 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        globalGroupState = getDocument(GroupResourcePolicyState.class,
                globalGroupState.documentSelfLink);

        assertEquals(globalGroupState.allocatedInstancesCount, task.resourceCount);
        assertEquals(globalGroupState.documentSelfLink, task.groupResourcePolicyLink);
        assertEquals(1, globalGroupState.resourceQuotaPerResourceDesc.size());
        Long countPerDesc = globalGroupState.resourceQuotaPerResourceDesc
                .get(task.resourceDescriptionLink);
        assertEquals(task.resourceCount, countPerDesc.longValue());
    }

    @Test
    public void testDeploymentPoliciesOnPolicy() throws Throwable {
        DeploymentPolicy policy = createDeploymentPolicy();

        containerDesc.deploymentPolicyId = extractId(policy.documentSelfLink);
        doPut(containerDesc);

        GroupResourcePolicyState policyState = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyState = doPost(policyState, GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyState);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = policyState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        // update the policy and succeed
        policyState.deploymentPolicyLink = policy.documentSelfLink;
        doPut(policyState);

        task = new ReservationTaskState();
        task.tenantLinks = policyState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        policyState = getDocument(GroupResourcePolicyState.class,
                policyState.documentSelfLink);
        assertEquals(policyState.documentSelfLink, task.groupResourcePolicyLink);
    }

    @Test
    public void testDeploymentPoliciesOnHost() throws Throwable {
        DeploymentPolicy policy = createDeploymentPolicy();

        containerDesc.deploymentPolicyId = extractId(policy.documentSelfLink);
        doPut(containerDesc);

        GroupResourcePolicyState policyState = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyState = doPost(policyState, GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyState);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = policyState.tenantLinks;
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
        task.tenantLinks = policyState.tenantLinks;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        policyState = getDocument(GroupResourcePolicyState.class,
                policyState.documentSelfLink);
        assertEquals(policyState.documentSelfLink, task.groupResourcePolicyLink);
    }

    @Test
    public void testDeploymentPoliciesOnMultipleHostsAndPolicies() throws Throwable {
        DeploymentPolicy policy = createDeploymentPolicy();

        final GroupResourcePolicyState policy1 = createPolicy(null);
        final GroupResourcePolicyState policy2 = createPolicy(null);
        final GroupResourcePolicyState policy3 = createPolicy(null);

        createContainerHost(null, policy3.resourcePoolLink);
        createContainerHost(null, policy1.resourcePoolLink);

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

        final ComputeState host3 = createContainerHost(policy, policy1.resourcePoolLink);
        final ComputeState host4 = createContainerHost(policy, policy2.resourcePoolLink);

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

        GroupResourcePolicyState policy4 = createPolicy(policy);
        GroupResourcePolicyState policy5 = createPolicy(policy);

        createContainerHost(null, policy4.resourcePoolLink);
        final ComputeState host6 = createContainerHost(policy, policy5.resourcePoolLink);

        // Provision when policy has been set to both hosts and policies
        task = new ReservationTaskState();
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);
        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        final String expectedPolicyLink = policy5.documentSelfLink;
        assertEquals(expectedPolicyLink, task.groupResourcePolicyLink);

        assertNotNull(task.hostSelections);
        assertEquals(1, task.hostSelections.size());
        assertEquals(host6.documentSelfLink, task.hostSelections.get(0).hostLink);
    }

    // test when the request comes from the Container tab
    // the ReservationTaskState contains tenant links in format "/tenants/{tenant-name}"
    // expected to return all policies for the tenant
    @Test
    public void testReservationTaskLifeCycleWithRequestComingFromTheContainerTab() throws Throwable {

        // create policy without a group
        GroupResourcePolicyState policy = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policy = doPost(policy,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policy);

        // create policy with Development group
        GroupResourcePolicyState policyDevelopment = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyDevelopment.tenantLinks = TestRequestStateFactory.createTenantLinks(
                TestRequestStateFactory.TENANT_NAME,
                TestRequestStateFactory.GROUP_NAME_DEVELOPMENT);
        policyDevelopment = doPost(policyDevelopment,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyDevelopment);

        // create policy with Finance group
        GroupResourcePolicyState policyFinance = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyFinance.tenantLinks = TestRequestStateFactory.createTenantLinks(
                TestRequestStateFactory.TENANT_NAME,
                TestRequestStateFactory.GROUP_NAME_FINANCE);
        policyFinance = doPost(policyFinance,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyFinance);

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

        policyDevelopment = getDocument(GroupResourcePolicyState.class,
                policyDevelopment.documentSelfLink);

        policyFinance = getDocument(GroupResourcePolicyState.class,
                policyFinance.documentSelfLink);

        assertNotNull(task.groupResourcePolicyLink);
        assertEquals(task.resourcePoolsPerGroupPolicyLinks.size(), 2);

    }

    // test when the request comes from Catalog
    // the ReservationTaskState contains tenant links in format
    // "/tenants/{tenant-name}/groups/{group-id}"
    // expected to return the policy that match the {group-id}
    @Test
    public void testReservationTaskLifeCycleWithRequestComingFromTheCatalog() throws Throwable {

        // create policy without a group
        GroupResourcePolicyState policy = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policy = doPost(policy,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policy);

        // create policy with Development group
        GroupResourcePolicyState policyDevelopment = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyDevelopment.tenantLinks = TestRequestStateFactory.createTenantLinks(
                TestRequestStateFactory.TENANT_NAME,
                TestRequestStateFactory.GROUP_NAME_DEVELOPMENT);
        policyDevelopment = doPost(policyDevelopment,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyDevelopment);

        // create policy with Finance group
        GroupResourcePolicyState policyFinance = TestRequestStateFactory
                .createGroupResourcePolicyState();
        policyFinance.tenantLinks = TestRequestStateFactory.createTenantLinks(
                TestRequestStateFactory.TENANT_NAME,
                TestRequestStateFactory.GROUP_NAME_FINANCE);
        policyFinance = doPost(policyFinance,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(policyFinance);

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

        policyDevelopment = getDocument(GroupResourcePolicyState.class,
                policyDevelopment.documentSelfLink);

        policyFinance = getDocument(GroupResourcePolicyState.class,
                policyFinance.documentSelfLink);

        assertEquals(task.groupResourcePolicyLink, policyDevelopment.documentSelfLink);
    }

    @Test
    public void testReservationTaskLifeCycleWithMemoryLimitInRange() throws Throwable {
        // create policy with same group but less available memory:
        GroupResourcePolicyState groupPolicyStateLessMemory = TestRequestStateFactory
                .createGroupResourcePolicyState();
        groupPolicyStateLessMemory.memoryLimit = containerDesc.memoryLimit * 2 - 1;
        groupPolicyStateLessMemory = doPost(groupPolicyStateLessMemory,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(groupPolicyStateLessMemory);

        ReservationTaskState task = new ReservationTaskState();
        task.tenantLinks = groupPolicyStateLessMemory.tenantLinks;
        task.resourceCount = 2;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);

        //not enough memory, request should fail
        task = waitForTaskError(task.documentSelfLink, ReservationTaskState.class);

        // create policy with same group with more available memory:
        GroupResourcePolicyState groupPolicyStateEnoughMemory = TestRequestStateFactory
                .createGroupResourcePolicyState();
        groupPolicyStateEnoughMemory.memoryLimit = containerDesc.memoryLimit * 2 + 1;
        groupPolicyStateEnoughMemory = doPost(groupPolicyStateEnoughMemory,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(groupPolicyStateEnoughMemory);

        task = new ReservationTaskState();
        task.tenantLinks = groupPolicyStateLessMemory.tenantLinks;
        task.resourceCount = 2;
        task.resourceDescriptionLink = containerDesc.documentSelfLink;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ReservationTaskFactoryService.SELF_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ReservationTaskState.class);

        groupPolicyStateEnoughMemory = getDocument(GroupResourcePolicyState.class,
                groupPolicyStateEnoughMemory.documentSelfLink);

        assertEquals(groupPolicyStateEnoughMemory.maxNumberInstances - task.resourceCount,
                groupPolicyStateEnoughMemory.availableInstancesCount);
        assertEquals(groupPolicyStateEnoughMemory.documentSelfLink, task.groupResourcePolicyLink);
        assertEquals(1, groupPolicyStateEnoughMemory.resourceQuotaPerResourceDesc.size());
        Long countPerDesc = groupPolicyStateEnoughMemory.resourceQuotaPerResourceDesc
                .get(task.resourceDescriptionLink);
        assertEquals(task.resourceCount, countPerDesc.longValue());
    }

    private GroupResourcePolicyState createPolicy(DeploymentPolicy policy)
            throws Throwable {

        ResourcePoolState resourcePool = new ResourcePoolState();
        resourcePool.name = ReservationTaskServiceTest.class.toString()
                + System.currentTimeMillis();
        resourcePool = doPost(resourcePool, ResourcePoolService.FACTORY_LINK);
        addForDeletion(resourcePool);

        GroupResourcePolicyState rsrvState = new GroupResourcePolicyState();
        rsrvState.resourcePoolLink = resourcePool.documentSelfLink;
        rsrvState.name = ReservationTaskServiceTest.class.toString() + System.currentTimeMillis();
        rsrvState.maxNumberInstances = 10;
        rsrvState.memoryLimit = 0L;
        rsrvState.cpuShares = 3;
        if (policy != null) {
            rsrvState.deploymentPolicyLink = policy.documentSelfLink;
        }

        rsrvState = doPost(rsrvState, GroupResourcePolicyService.FACTORY_LINK);
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
