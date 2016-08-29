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

import java.util.Collections;
import java.util.HashMap;

import org.junit.Test;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.GroupResourcePolicyService;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.ComputeReservationTaskService.ComputeReservationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourcePoolService;

public class ComputeReservationTaskServiceTest extends RequestBaseTest {

    @Override
    public void setUp() throws Throwable {
        startServices(host);
        MockDockerAdapterService.resetContainers();

        setUpDockerHostAuthentication();
        createEndpoint();
        // setup Docker Host:
        ResourcePoolService.ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        createDockerHost(dockerHostDesc, resourcePool);

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
                TestRequestStateFactory
                        .createGroupResourcePolicyState(ResourceType.COMPUTE_TYPE),
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(groupPolicyState);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPolicyState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = groupPolicyState.maxNumberInstances + 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        waitForTaskError(task.documentSelfLink, ComputeReservationTaskState.class);
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

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPolicyState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.customProperties = new HashMap<>();
        task.customProperties.put("key1", "reservation-task-value1");

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);

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

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = policyState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);

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

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = policyState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);

        policyState = getDocument(GroupResourcePolicyState.class,
                policyState.documentSelfLink);
        assertEquals(policyState.documentSelfLink, task.groupResourcePolicyLink);
    }

    @Test
    public void testReservationTaskLifeCycleWithNoGroup() throws Throwable {
        GroupResourcePolicyState groupPolicytState = TestRequestStateFactory
                .createGroupResourcePolicyState();
        groupPolicytState.tenantLinks = null;
        groupPolicytState = doPost(groupPolicytState,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(groupPolicytState);

        // create another suitable group policy but with a group that should not be selected
        doPost(TestRequestStateFactory.createGroupResourcePolicyState(),
                GroupResourcePolicyService.FACTORY_LINK);

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = null;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = 5;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);

        groupPolicytState = getDocument(GroupResourcePolicyState.class,
                groupPolicytState.documentSelfLink);

        assertEquals(groupPolicytState.documentSelfLink, task.groupResourcePolicyLink);

        assertEquals(groupPolicytState.allocatedInstancesCount, task.resourceCount);
        assertEquals(1, groupPolicytState.resourceQuotaPerResourceDesc.size());
        Long countPerDesc = groupPolicytState.resourceQuotaPerResourceDesc
                .get(task.resourceDescriptionLink);
        assertEquals(task.resourceCount, countPerDesc.longValue());
    }

    @Test
    public void testReservationTaskLifeCycleWithGlobalGroup() throws Throwable {
        // create policy with same group but less number of instances:
        GroupResourcePolicyState groupPolicytState = TestRequestStateFactory
                .createGroupResourcePolicyState();
        groupPolicytState.maxNumberInstances = 2;
        groupPolicytState = doPost(groupPolicytState,
                GroupResourcePolicyService.FACTORY_LINK);
        addForDeletion(groupPolicytState);

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

        ComputeReservationTaskState task = new ComputeReservationTaskState();
        task.tenantLinks = groupPolicytState.tenantLinks;
        task.resourceDescriptionLink = hostDesc.documentSelfLink;
        task.resourceCount = groupPolicytState.maxNumberInstances + 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();

        task = doPost(task, ComputeReservationTaskService.FACTORY_LINK);
        assertNotNull(task);

        task = waitForTaskSuccess(task.documentSelfLink, ComputeReservationTaskState.class);

        globalGroupState = getDocument(GroupResourcePolicyState.class,
                globalGroupState.documentSelfLink);

        assertEquals(globalGroupState.allocatedInstancesCount, task.resourceCount);
        assertEquals(globalGroupState.documentSelfLink, task.groupResourcePolicyLink);
        assertEquals(1, globalGroupState.resourceQuotaPerResourceDesc.size());
        Long countPerDesc = globalGroupState.resourceQuotaPerResourceDesc
                .get(task.resourceDescriptionLink);
        assertEquals(task.resourceCount, countPerDesc.longValue());
    }

}
