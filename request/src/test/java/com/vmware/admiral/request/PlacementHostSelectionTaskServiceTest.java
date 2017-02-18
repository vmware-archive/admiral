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

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class PlacementHostSelectionTaskServiceTest extends RequestBaseTest {
    private String contextId;
    private List<String> initialHostLinks;
    private int resourceCount;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();

        initialHostLinks = new ArrayList<>();

        long firstHostAvailableMemory = 4_500_000;
        initialHostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), firstHostAvailableMemory,
                true).documentSelfLink);

        long secondHostAvailableMemory = 4_700_000;
        initialHostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), secondHostAvailableMemory,
                true).documentSelfLink);

        long thirdHostAvailableMemory = 4_950_000;
        initialHostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), thirdHostAvailableMemory,
                true).documentSelfLink);

        // Add a host that is powered off. Nothing should be placed on it
        long poweredOffHostAvailableMemory = Integer.MAX_VALUE;
        ComputeService.ComputeState poweredOffHost = createDockerHost(
                createDockerHostDescription(), createResourcePool(), poweredOffHostAvailableMemory,
                true);

        initialHostLinks.add(poweredOffHost.documentSelfLink);
        poweredOffHost.powerState = ComputeService.PowerState.OFF;
        doOperation(poweredOffHost, UriUtils.buildUri(host, poweredOffHost.documentSelfLink),
                false,
                Service.Action.PUT);

        contextId = UUID.randomUUID().toString();
        resourceCount = 1;
    }

    @Test
    public void testSameHostSelectionWhenPodVolumeFromAndAffinity() throws Throwable {
        String pod = "test-pod";
        String hostLink = initialHostLinks.get(0);

        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription();
        desc1.pod = pod;
        desc1 = storeDescription(desc1);
        createContainer(desc1, hostLink);

        ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
        desc.pod = pod;
        desc.volumesFrom = new String[] { desc1.name };
        desc.affinity = new String[] { desc1.name };
        desc = storeDescription(desc);

        boolean expectError = false;
        PlacementHostSelectionTaskState placementTask = createHostPlacementTask(
                desc.documentSelfLink, resourceCount, expectError);

        assertEquals(resourceCount, placementTask.hostSelections.size());
        assertEquals(hostLink, placementTask.hostSelections.iterator().next().hostLink);

        // multiple containers with same host:
        ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription();
        desc2.name = "name2";
        desc2 = storeDescription(desc2);
        createContainer(desc2, hostLink);

        ContainerDescription desc3 = TestRequestStateFactory.createContainerDescription();
        desc3.name = "name3";
        desc3 = storeDescription(desc3);
        createContainer(desc3, hostLink);

        desc = TestRequestStateFactory.createContainerDescription();
        desc.pod = pod;
        desc.volumesFrom = new String[] { desc2.name };
        desc.affinity = new String[] { desc3.name };
        desc = storeDescription(desc);

        placementTask = createHostPlacementTask(desc.documentSelfLink, resourceCount, expectError);

        assertEquals(resourceCount, placementTask.hostSelections.size());
        assertEquals(hostLink, placementTask.hostSelections.iterator().next().hostLink);
    }

    @Test
    public void testHostSelectionMemoryLimit() throws Throwable {

        /*
        There are four hosts with free memory 4_500_000, 4_700_000, 4_950_000 and
        Integer.MAX_VALUE - 100 (created in the base class). We vary the container description
        memory limit to influence the actual memory requirements.
         */

        //Multiple hosts satisfy the requirements
        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription();
        desc1.memoryLimit = 4_600_000L;
        desc1 = storeDescription(desc1);
        createContainer(desc1, null);

        boolean expectedError = false;
        PlacementHostSelectionTaskState placementTask1 = createHostPlacementTask(
                desc1.documentSelfLink, resourceCount, expectedError);

        String selectedHostLink1 = placementTask1.hostSelections.iterator().next().hostLink;
        assertThat(selectedHostLink1,
                anyOf(equalTo(initialHostLinks.get(1)), equalTo(initialHostLinks.get(2)),
                        equalTo(computeHost.documentSelfLink)));

        //Only one host satisfies the requirements
        ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription();
        desc2.memoryLimit = 4_960_000L;
        desc2 = storeDescription(desc2);
        createContainer(desc2, null);

        expectedError = false;
        PlacementHostSelectionTaskState placementTask2 = createHostPlacementTask(
                desc2.documentSelfLink, resourceCount, expectedError);

        String selectedHostLink2 = placementTask2.hostSelections.iterator().next().hostLink;
        assertEquals(computeHost.documentSelfLink, selectedHostLink2);

        //No hosts satisfy the requirements
        ContainerDescription desc3 = TestRequestStateFactory.createContainerDescription();
        desc3.memoryLimit = (long) Integer.MAX_VALUE;
        desc3 = storeDescription(desc3);
        createContainer(desc3, null);

        expectedError = true;
        PlacementHostSelectionTaskState placementTask3 = createHostPlacementTask(
                desc3.documentSelfLink, resourceCount, expectedError);

        assertThat(placementTask3.hostSelections, is(nullValue()));
    }

    @Test
    public void testErrorWhenSameHostConditionCannotBeSatisfied() throws Throwable {
        String hostLink1 = initialHostLinks.get(0);
        String hostLink2 = initialHostLinks.get(1);
        String hostLink3 = initialHostLinks.get(2);
        String pod = "test-pod";

        // 1. pod and volumes_from not matching:

        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription();
        desc1.pod = pod;
        desc1 = storeDescription(desc1);
        createContainer(desc1, hostLink1);

        ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription();
        desc2.name = "name2";
        desc2 = storeDescription(desc2);
        createContainer(desc2, hostLink2);

        ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
        desc.pod = pod;
        desc.volumesFrom = new String[] { desc2.name };
        desc = storeDescription(desc);

        boolean expecteError = true;
        createHostPlacementTask(desc.documentSelfLink, resourceCount, expecteError);

        // 2. pod and afinity not matching:
        ContainerDescription desc3 = TestRequestStateFactory.createContainerDescription();
        desc3.name = "name3";
        desc3 = storeDescription(desc3);
        createContainer(desc3, hostLink3);

        desc = TestRequestStateFactory.createContainerDescription();
        desc.pod = pod;
        desc.affinity = new String[] { desc3.name };
        desc = storeDescription(desc);

        createHostPlacementTask(desc.documentSelfLink, resourceCount, expecteError);

        // 3. affinity and volume_from matching:
        desc = TestRequestStateFactory.createContainerDescription();
        desc.volumesFrom = new String[] { desc2.name };
        desc.affinity = new String[] { desc3.name };
        desc = storeDescription(desc);

        createHostPlacementTask(desc.documentSelfLink, resourceCount, expecteError);
    }

    @Test
    public void testNumberOfSelectedHostShouldBeSameAsResourceCount() throws Throwable {
        boolean expectError = false;
        String deploymentPolicyId = "testDeploymentPolicy11";

        ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
        desc.deploymentPolicyId = deploymentPolicyId;
        desc = storeDescription(desc);

        String hostLink1 = initialHostLinks.get(0);
        String hostLink2 = initialHostLinks.get(1);
        resourceCount = 4;

        PlacementHostSelectionTaskState placementTask = createHostPlacementTask(
                desc.documentSelfLink, resourceCount, expectError);

        // verify all host will be used when neither one of them has deploymentPolicyId set
        assertEquals(4, placementTask.hostSelections.size());

        long count = placementTask.hostSelections.stream().map((r) -> r.hostLink)
                .filter((l) -> l.equals(hostLink1) || l.equals(hostLink2))
                .count();

        // make sure the host are equally distributed when no other constraints
        // in this case all 4 host are selected for clustering use case and
        // only 2 are with the first and the second hostLink
        assertEquals(2, count);

        // Patch 2 out of 4 hosts with deploymentPolicyId:
        ComputeState patchBody = new ComputeState();
        patchBody.customProperties = new HashMap<>();
        patchBody.customProperties.put(ContainerHostService.CUSTOM_PROPERTY_DEPLOYMENT_POLICY,
                deploymentPolicyId);

        doOperation(patchBody, UriUtils.buildUri(host, hostLink1), expectError, Action.PATCH);
        doOperation(patchBody, UriUtils.buildUri(host, hostLink2), expectError, Action.PATCH);

        placementTask = createHostPlacementTask(desc.documentSelfLink, resourceCount, expectError);
        // verify only the host with deploymentPolicyId will be returned:
        assertEquals(4, placementTask.hostSelections.size());

        count = placementTask.hostSelections.stream().map((r) -> r.hostLink)
                .filter((l) -> l.equals(hostLink1) || l.equals(hostLink2))
                .count();
        // In this case, only the two host with deploymentPolicyId are
        // selected and repeated for the 3 and 4 resource.
        assertEquals(4, count);
    }

    @Test
    public void testDeploymentPolicyShouldFilterOnlyHostsWithMatchingDeploymentPolicy()
            throws Throwable {
        String hostLink = initialHostLinks.get(0);

        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription();
        desc1 = storeDescription(desc1);
        createContainer(desc1, hostLink);

        ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
        desc.affinity = new String[] { desc1.name };
        desc = storeDescription(desc);

        resourceCount = 3;
        boolean expectError = false;
        PlacementHostSelectionTaskState placementTask = createHostPlacementTask(
                desc.documentSelfLink, resourceCount, expectError);

        assertEquals(resourceCount, placementTask.hostSelections.size());
        for (String currentHostLinks : placementTask.hostSelections.stream().map((r) -> r.hostLink)
                .collect(Collectors.toList())) {
            assertEquals(hostLink, currentHostLinks);
        }
    }

    private PlacementHostSelectionTaskState createHostPlacementTask(String containerDescLink,
            int resourceCount, boolean expectError) throws Throwable {
        PlacementHostSelectionTaskState placementTask = new PlacementHostSelectionTaskState();
        placementTask.documentSelfLink = UUID.randomUUID().toString();
        placementTask.resourceDescriptionLink = containerDescLink;
        placementTask.resourcePoolLinks = new ArrayList<>();
        placementTask.resourcePoolLinks.add(resourcePool.documentSelfLink);
        placementTask.resourceCount = resourceCount;
        placementTask.resourceType = ResourceType.CONTAINER_TYPE.getName();
        placementTask.contextId = contextId;
        placementTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        placementTask.documentExpirationTimeMicros = ServiceUtils
                .getDefaultTaskExpirationTimeInMicros();

        return placeTask(placementTask, expectError);
    }

    private PlacementHostSelectionTaskState startPlacementTask(
            PlacementHostSelectionTaskState placementTask, boolean expectError) throws Throwable {

        PlacementHostSelectionTaskState[] result = new PlacementHostSelectionTaskState[] { null };
        host.testStart(1);

        host.sendRequest(Operation.createPost(host, PlacementHostSelectionTaskService.FACTORY_LINK)
                .setBody(placementTask)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    result[0] = o.getBody(PlacementHostSelectionTaskState.class);
                    host.completeIteration();
                }));
        host.testWait();

        return result[0];
    }

    private PlacementHostSelectionTaskState placeTask(
            PlacementHostSelectionTaskState placementTask, boolean expectError)
            throws Throwable {
        placementTask = startPlacementTask(placementTask, expectError);
        host.log("Start placement host selection test: " + placementTask.documentSelfLink);

        if (!expectError) {
            placementTask = waitForTaskSuccess(placementTask.documentSelfLink,
                    PlacementHostSelectionTaskState.class);
        } else {
            placementTask = waitForTaskError(placementTask.documentSelfLink,
                    PlacementHostSelectionTaskState.class);
        }

        host.log("Finished placement host selection test: " + placementTask.documentSelfLink);
        return placementTask;
    }

    private ContainerState createContainer(ContainerDescription desc, String hostLink)
            throws Throwable {
        ContainerState container = new ContainerState();
        container.descriptionLink = desc.documentSelfLink;
        container.id = UUID.randomUUID().toString();
        container.parentLink = hostLink;
        container.compositeComponentLink = UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, contextId);
        container = doPost(container, ContainerFactoryService.SELF_LINK);
        assertNotNull(container);
        addForDeletion(container);
        return container;
    }

    private ContainerDescription storeDescription(ContainerDescription desc) throws Throwable {
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);

        return desc;
    }
}
