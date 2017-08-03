/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.internal.ComparisonCriteria;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.request.ContainerAllocationTaskService.AllocationExtensibilityCallbackResponse;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState.SubStage;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class ContainerAllocationTaskServiceTest extends RequestBaseTest {

    @Test
    public void testAllocationTaskServiceLifeCycle() throws Throwable {
        containerDesc.customProperties = new HashMap<>();
        containerDesc.customProperties.put("customPropA", "valueA");

        doOperation(containerDesc, UriUtils.buildUri(host, containerDesc.documentSelfLink),
                false, Action.PUT);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put("customPropB", "valueB");
        allocationTask = allocate(allocationTask);

        ContainerState containerState = getDocument(ContainerState.class,
                allocationTask.resourceLinks.iterator().next());
        assertTrue(containerState.names.get(0).startsWith(containerDesc.name));
        assertNotNull(containerState.id);
        assertTrue(containerState.documentSelfLink.contains(containerDesc.name));

        validatePorts(containerDesc, containerState);
        assertArrayEquals(containerDesc.command, containerState.command);
        assertEquals(containerDesc.image, containerState.image);
        assertEquals(containerDesc.volumeDriver, containerState.volumeDriver);
        assertEquals(containerDesc.documentSelfLink, containerState.descriptionLink);
        assertEquals(containerDesc.instanceAdapterReference.getPath(),
                containerState.adapterManagementReference.getPath());
        assertNotNull(containerState.created);
        assertEquals(groupPlacementState.documentSelfLink,
                containerState.groupResourcePlacementLink);

        assertEquals(ContainerAllocationTaskService
                .getMinParam(groupPlacementState.cpuShares, containerDesc.cpuShares.longValue())
                .longValue(),
                containerState.cpuShares.longValue());
        assertEquals(ContainerAllocationTaskService
                .getMinParam(groupPlacementState.memoryLimit, containerDesc.memoryLimit),
                containerState.memoryLimit);

        assertNotNull(containerState.env);
        assertTrue(Arrays.equals(containerDesc.env, containerState.env));

        assertNotNull(containerState.extraHosts);
        assertTrue(Arrays.equals(containerDesc.extraHosts, containerState.extraHosts));

        assertEquals("valueA", containerState.customProperties.get("customPropA"));
        assertEquals("valueB", containerState.customProperties.get("customPropB"));

        waitForContainerPowerState(PowerState.RUNNING, containerState.documentSelfLink);

        waitFor(() -> {
            ComputeState cs = getDocument(ComputeState.class, containerState.parentLink);
            String containers = cs.customProperties == null ? null : cs.customProperties
                    .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);
            return containers != null && Integer.parseInt(containers) >= 1;
        });

    }

    @Test
    public void testAllocationTaskServiceLifeCycleFailed() throws Throwable {
        // create allocation task:
        ContainerAllocationTaskState allocationTask = createContainerAllocationTask();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put(MockDockerAdapterService.FAILURE_EXPECTED,
                Boolean.TRUE.toString());

        allocationTask = startAllocationTask(allocationTask);

        waitForTaskError(allocationTask.documentSelfLink, ContainerAllocationTaskState.class);
    }

    @Test
    public void testAllocationTaskServiceLifeSelectCorrectHostComputeState() throws Throwable {
        ComputeDescription noMatchingHostDesc = TestRequestStateFactory
                .createDockerHostDescription();
        noMatchingHostDesc.documentSelfLink = UUID.randomUUID().toString();
        noMatchingHostDesc.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.OS_ON_PHYSICAL.toString()));

        noMatchingHostDesc = doPost(noMatchingHostDesc, ComputeDescriptionService.FACTORY_LINK);
        assertNotNull(noMatchingHostDesc);
        // create a host ComputeState from the same resourcePool but ComputeDesc that doesn't
        // support containers. Make sure the right one is selected.
        createDockerHost(noMatchingHostDesc, resourcePool);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask();
        allocate(allocationTask);
    }

    @Test
    public void testContainerAllocationWithFollowingProvisioningRequest() throws Throwable {
        host.log(">>>>>>Start: testContainerAllocationWithFollowingProvisioningRequest <<<<< ");
        doOperation(containerDesc, UriUtils.buildUri(host, containerDesc.documentSelfLink),
                false, Action.PUT);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                Boolean.TRUE.toString());
        allocationTask = allocate(allocationTask);

        assertContainerStateAfterAllocation(allocationTask);

        // Request provisioning after allocation:
        RequestBrokerState provisioningRequest = new RequestBrokerState();
        provisioningRequest.resourceType = allocationTask.resourceType;
        provisioningRequest.resourceLinks = allocationTask.resourceLinks;
        provisioningRequest.resourceDescriptionLink = containerDesc.documentSelfLink;
        provisioningRequest.operation = ContainerOperationType.CREATE.id;

        provisioningRequest = doPost(provisioningRequest, RequestBrokerFactoryService.SELF_LINK);
        assertNotNull(provisioningRequest);

        waitForTaskSuccess(allocationTask.documentSelfLink, ContainerAllocationTaskState.class);
    }

    @Test
    public void testContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncluded() throws Throwable {
        host.log(">>>>>>Start: testContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncluded <<<<< ");

        ServerSocket serverSocket = new ServerSocket(0);
        containerDesc.healthConfig = createHealthConfigTcp(serverSocket.getLocalPort());
        doOperation(containerDesc, UriUtils.buildUri(host, containerDesc.documentSelfLink),
                false, Action.PUT);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                Boolean.TRUE.toString());
        allocationTask = allocate(allocationTask);

        assertContainerStateAfterAllocation(allocationTask);

        // Request provisioning after allocation:
        RequestBrokerState provisioningRequest = new RequestBrokerState();
        provisioningRequest.resourceType = allocationTask.resourceType;
        provisioningRequest.resourceLinks = allocationTask.resourceLinks;
        provisioningRequest.resourceDescriptionLink = containerDesc.documentSelfLink;
        provisioningRequest.operation = ContainerOperationType.CREATE.id;

        // start listener
        try  {
            provisioningRequest = doPost(provisioningRequest, RequestBrokerFactoryService.SELF_LINK);
            assertNotNull(provisioningRequest);

            waitForTaskSuccess(allocationTask.documentSelfLink, ContainerAllocationTaskState.class);
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }

    @Test
    public void testContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncludedShouldFail() throws Throwable {
        host.log(">>>>>>Start: testContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncludedShouldFail <<<<< ");
        containerDesc.healthConfig = createHealthConfigTcp(2);
        containerDesc.healthConfig.ignoreOnProvision = false;
        doOperation(containerDesc, UriUtils.buildUri(host, containerDesc.documentSelfLink),
                false, Action.PUT);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                Boolean.TRUE.toString());
        allocationTask = allocate(allocationTask);

        assertContainerStateAfterAllocation(allocationTask);

        // Request provisioning after allocation:
        RequestBrokerState provisioningRequest = new RequestBrokerState();
        provisioningRequest.resourceType = allocationTask.resourceType;
        provisioningRequest.resourceLinks = allocationTask.resourceLinks;
        provisioningRequest.resourceDescriptionLink = containerDesc.documentSelfLink;
        provisioningRequest.operation = ContainerOperationType.CREATE.id;

        configureHealthCheckTimeout(DEFAULT_HEALTH_CHECK_TIMEOUT);

        configureHealthCheckDelay(DEFAULT_HEALTH_CHECK_DELAY);

        // should fail because there is no listener to handle the health check request
        provisioningRequest = doPost(provisioningRequest, RequestBrokerFactoryService.SELF_LINK);
        assertNotNull(provisioningRequest);

        waitForRequestToFail(provisioningRequest);
    }

    @Test
    public void testContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncludedWhichFailsContinueProvisioning() throws Throwable {
        host.log(">>>>>>Start: testContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncludedWhichFailsContinueProvisioning <<<<< ");
        containerDesc.healthConfig = createHealthConfigTcp(2);

        doOperation(containerDesc, UriUtils.buildUri(host, containerDesc.documentSelfLink),
                false, Action.PUT);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                Boolean.TRUE.toString());
        allocationTask = allocate(allocationTask);

        assertContainerStateAfterAllocation(allocationTask);

        // Request provisioning after allocation:
        RequestBrokerState provisioningRequest = new RequestBrokerState();
        provisioningRequest.resourceType = allocationTask.resourceType;
        provisioningRequest.resourceLinks = allocationTask.resourceLinks;
        provisioningRequest.resourceDescriptionLink = containerDesc.documentSelfLink;
        provisioningRequest.operation = ContainerOperationType.CREATE.id;

        configureHealthCheckTimeout(DEFAULT_HEALTH_CHECK_TIMEOUT);

        configureHealthCheckDelay(DEFAULT_HEALTH_CHECK_DELAY);

        // should fail because there is no listener to handle the health check request
        provisioningRequest = doPost(provisioningRequest, RequestBrokerFactoryService.SELF_LINK);
        assertNotNull(provisioningRequest);

        waitForRequestToComplete(provisioningRequest);
    }

    @Test
    public void testContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncludedWhichFailsContinueProvisioningImmediately()
            throws Throwable {
        host.log(
                ">>>>>>Start: testContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncludedWhichFailsContinueProvisioningImmediately <<<<< ");
        containerDesc.healthConfig = createHealthConfigTcp(2);

        doOperation(containerDesc, UriUtils.buildUri(host, containerDesc.documentSelfLink),
                false, Action.PUT);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                Boolean.TRUE.toString());
        allocationTask = allocate(allocationTask);

        assertContainerStateAfterAllocation(allocationTask);

        // Request provisioning after allocation:
        RequestBrokerState provisioningRequest = new RequestBrokerState();
        provisioningRequest.resourceType = allocationTask.resourceType;
        provisioningRequest.resourceLinks = allocationTask.resourceLinks;
        provisioningRequest.resourceDescriptionLink = containerDesc.documentSelfLink;
        provisioningRequest.operation = ContainerOperationType.CREATE.id;

        configureHealthCheckTimeout("60000"); // exaggerate the timeout

        configureHealthCheckDelay(DEFAULT_HEALTH_CHECK_DELAY);

        // should fail because there is no listener to handle the health check request
        final RequestBrokerState provisioningRequestOut = doPost(provisioningRequest,
                RequestBrokerFactoryService.SELF_LINK);
        assertNotNull(provisioningRequestOut);

        // the request should complete 'right away' despite of the health check taking some time
        runWithTimeout(5, () -> {
            return waitForRequestToComplete(provisioningRequestOut);
        });
    }

    @Test
    public void testClusteredContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncluded() throws Throwable {
        host.log(">>>>>>Start: testClusteredContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncluded <<<<< ");
        final int clusterSize = 5;

        ServerSocket serverSocket = new ServerSocket(0);
        containerDesc.healthConfig = createHealthConfigTcp(serverSocket.getLocalPort());
        containerDesc._cluster = clusterSize;
        doOperation(containerDesc, UriUtils.buildUri(host, containerDesc.documentSelfLink),
                false, Action.PUT);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask(containerDesc.documentSelfLink, clusterSize);
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                Boolean.TRUE.toString());
        allocationTask = allocate(allocationTask);

        assertContainerStateAfterAllocation(allocationTask);

        // Request provisioning after allocation:
        RequestBrokerState provisioningRequest = new RequestBrokerState();
        provisioningRequest.resourceType = allocationTask.resourceType;
        provisioningRequest.resourceLinks = allocationTask.resourceLinks;
        provisioningRequest.resourceCount = clusterSize;
        provisioningRequest.resourceDescriptionLink = containerDesc.documentSelfLink;
        provisioningRequest.operation = ContainerOperationType.CREATE.id;

        // start listener
        try  {
            provisioningRequest = doPost(provisioningRequest, RequestBrokerFactoryService.SELF_LINK);
            assertNotNull(provisioningRequest);

            waitForTaskSuccess(allocationTask.documentSelfLink, ContainerAllocationTaskState.class);
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }

    @Test
    public void testClusteredContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncludedShoudFail() throws Throwable {
        host.log(">>>>>>Start: testClusteredContainerAllocationWithFollowingProvisioningRequestWithHeathCheckIncludedShoudFail <<<<< ");
        final int clusterSize = 5;
        containerDesc.healthConfig = createHealthConfigTcp(2);
        containerDesc.healthConfig.ignoreOnProvision = false;
        containerDesc._cluster = clusterSize;
        doOperation(containerDesc, UriUtils.buildUri(host, containerDesc.documentSelfLink),
                false, Action.PUT);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask(containerDesc.documentSelfLink, clusterSize);
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                Boolean.TRUE.toString());
        allocationTask = allocate(allocationTask);

        assertContainerStateAfterAllocation(allocationTask);

        configureHealthCheckTimeout(DEFAULT_HEALTH_CHECK_TIMEOUT);

        configureHealthCheckDelay(DEFAULT_HEALTH_CHECK_DELAY);

        // Request provisioning after allocation:
        RequestBrokerState provisioningRequest = new RequestBrokerState();
        provisioningRequest.resourceType = allocationTask.resourceType;
        provisioningRequest.resourceLinks = allocationTask.resourceLinks;
        provisioningRequest.resourceCount = clusterSize;
        provisioningRequest.resourceDescriptionLink = containerDesc.documentSelfLink;
        provisioningRequest.operation = ContainerOperationType.CREATE.id;

        // should fail because there is no listener to handle the health check request
        provisioningRequest = doPost(provisioningRequest, RequestBrokerFactoryService.SELF_LINK);
        assertNotNull(provisioningRequest);
        waitForRequestToFail(provisioningRequest);
    }

    @Test
    public void testAllocationOfContainersWithSameHostPodConstraint() throws Throwable {
        createDockerHost(createDockerHostDescription(), createResourcePool(), true);
        createDockerHost(createDockerHostDescription(), createResourcePool(), true);
        createDockerHost(createDockerHostDescription(), createResourcePool(), true);

        String pod = "host-pod1";

        // create a description with a pod defined:
        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription();
        desc1.documentSelfLink = UUID.randomUUID().toString();
        desc1.name = "linked-container1";
        desc1.pod = pod;
        desc1.portBindings = null;
        desc1 = doPost(desc1, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc1);
        addForDeletion(desc1);

        String contextId = UUID.randomUUID().toString();
        // all instances of this request should be allocated on the same hosts because of the pod.
        ContainerAllocationTaskState allocationTask1 = createContainerAllocationTask(
                desc1.documentSelfLink, 1);
        allocationTask1.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        allocationTask1 = allocate(allocationTask1);
        ContainerState container = getDocument(ContainerState.class,
                allocationTask1.resourceLinks.iterator().next());

        String hostLink = container.parentLink;

        // loop a few times to make sure the right host is not chosen by a chance
        for (int i = 0; i < 5; i++) {
            ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription();
            desc2.documentSelfLink = UUID.randomUUID().toString();
            desc2.name = "linked-container" + i;
            desc2.pod = pod;
            desc2.portBindings = null;
            desc2 = doPost(desc2, ContainerDescriptionService.FACTORY_LINK);
            assertNotNull(desc2);
            addForDeletion(desc2);

            // all instances of this request should be allocated on the same host as the one already
            // selected by the previous request since the desc1.pod == desc2.pod
            ContainerAllocationTaskState allocationTask2 = createContainerAllocationTask(
                    desc2.documentSelfLink, 2);
            allocationTask2.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
            allocationTask2 = allocate(allocationTask2);
            for (String resourceLink : allocationTask2.resourceLinks) {
                ContainerState currContainer = getDocument(ContainerState.class, resourceLink);
                assertEquals("Same host not selected for allocation request: "
                        + allocationTask2.documentSelfLink + " - in iteration: " + i, hostLink,
                        currContainer.parentLink);
            }
        }
    }

    @Test
    public void testAllocationOfContainerLinksWithNetworks() throws Throwable {
        Map<String, ServiceNetwork> networks = new HashMap<>();
        networks.put("my-net", null);

        createDockerHost(createDockerHostDescription(), createResourcePool(), true);
        createDockerHost(createDockerHostDescription(), createResourcePool(), true);

        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription();
        desc1.documentSelfLink = UUID.randomUUID().toString();
        desc1.name = "linked-service";
        desc1._cluster = 2;
        desc1.portBindings = null;
        desc1.networks = networks;
        desc1 = doPost(desc1, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc1);
        addForDeletion(desc1);

        String contextId = UUID.randomUUID().toString();
        // all instances of this request should be allocated on the same hosts because of the pod.
        ContainerAllocationTaskState allocationTask1 = createContainerAllocationTask(
                desc1.documentSelfLink, 1);
        allocationTask1.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        allocationTask1 = allocate(allocationTask1);

        ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription();
        desc2.documentSelfLink = UUID.randomUUID().toString();
        desc2.name = "service";
        desc2.networks = networks;
        desc2.portBindings = null;
        desc2.links = new String[] { "linked-service:my-service" };
        desc2 = doPost(desc2, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc2);
        addForDeletion(desc2);

        // all instances of this request should be allocated on the same host as the one already
        // selected by the previous request since the desc1.pod == desc2.pod
        ContainerAllocationTaskState allocationTask2 = createContainerAllocationTask(
                desc2.documentSelfLink, 2);
        allocationTask2.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        allocationTask2 = allocate(allocationTask2);
        ContainerState cont2 = getDocument(ContainerState.class,
                allocationTask2.resourceLinks.iterator().next());

        assertNull(cont2.links);
        assertEquals(1, cont2.networks.get("my-net").links.length);
        assertEquals("linked-service:my-service", cont2.networks.get("my-net").links[0]);
    }

    @Test
    public void testAllocationOfContainerLinksWithFallbackToLegacyLinks() throws Throwable {
        createDockerHost(createDockerHostDescription(), createResourcePool(), true);
        createDockerHost(createDockerHostDescription(), createResourcePool(), true);

        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription();
        desc1.documentSelfLink = UUID.randomUUID().toString();
        desc1.name = "linked-service";
        desc1.portBindings = null;
        desc1._cluster = 2;
        desc1 = doPost(desc1, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc1);
        addForDeletion(desc1);

        String contextId = UUID.randomUUID().toString();
        // all instances of this request should be allocated on the same hosts because of the pod.
        ContainerAllocationTaskState allocationTask1 = createContainerAllocationTask(
                desc1.documentSelfLink, 1);
        allocationTask1.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        allocationTask1.resourceCount = desc1._cluster.longValue();
        allocationTask1 = allocate(allocationTask1);

        Iterator<String> resourceLinkIterator = allocationTask1.resourceLinks.iterator();
        ContainerState cont1A = getDocument(ContainerState.class,
                resourceLinkIterator.next());
        ContainerState cont1B = getDocument(ContainerState.class,
                resourceLinkIterator.next());

        ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription();
        desc2.documentSelfLink = UUID.randomUUID().toString();
        desc2.name = "service";
        desc2.portBindings = null;
        desc2.links = new String[] { "linked-service:my-service" };
        desc2 = doPost(desc2, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc2);
        addForDeletion(desc2);

        // all instances of this request should be allocated on the same host as the one already
        // selected by the previous request since the desc1.pod == desc2.pod
        ContainerAllocationTaskState allocationTask2 = createContainerAllocationTask(
                desc2.documentSelfLink, 2);
        allocationTask2.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        allocationTask2 = allocate(allocationTask2);
        ContainerState cont2 = getDocument(ContainerState.class,
                allocationTask2.resourceLinks.iterator().next());

        assertNull(cont2.networks);
        List<String> links = Arrays.asList(cont2.links);
        assertEquals(2, links.size());
        assertTrue(links.contains(cont1A.names.get(0) + ":my-service"));
        assertTrue(links.contains(cont1B.names.get(0) + ":my-service"));
    }

    @Test
    public void testAllocationOfContainersWithAffinityAndVolumeFrom() throws Throwable {
        createDockerHost(createDockerHostDescription(), createResourcePool(), true);
        createDockerHost(createDockerHostDescription(), createResourcePool(), true);
        createDockerHost(createDockerHostDescription(), createResourcePool(), true);

        // create first container:
        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription();
        desc1.documentSelfLink = UUID.randomUUID().toString();
        desc1.name = "name1";
        desc1.portBindings = null;
        desc1 = doPost(desc1, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc1);
        addForDeletion(desc1);

        String contextId = UUID.randomUUID().toString();
        // all instances of this request should be allocated on the same hosts because of the pod.
        ContainerAllocationTaskState allocationTask1 = createContainerAllocationTask(
                desc1.documentSelfLink, 1);
        allocationTask1.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        allocationTask1 = allocate(allocationTask1);
        ContainerState container1 = getDocument(ContainerState.class,
                allocationTask1.resourceLinks.iterator().next());

        // create second container with afinity dependent on the first container:
        ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription();
        desc2.documentSelfLink = UUID.randomUUID().toString();
        desc2.name = "name2-links";
        desc2.portBindings = null;
        desc2.affinity = new String[] { desc1.name };
        desc2 = doPost(desc2, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc2);
        addForDeletion(desc2);

        // all instances of this request should be allocated on the same host as the one already
        // selected by the previous request since the desc1.pod == desc2.pod
        ContainerAllocationTaskState allocationTask2 = createContainerAllocationTask(
                desc2.documentSelfLink, 2);
        allocationTask2.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        allocationTask2 = allocate(allocationTask2);
        ContainerState container2 = getDocument(ContainerState.class,
                allocationTask2.resourceLinks.iterator().next());

        assertEquals(container1.parentLink, container2.parentLink);

        // create a third container with volumes_from dependent on the first container:
        ContainerDescription desc3 = TestRequestStateFactory.createContainerDescription();
        desc3.documentSelfLink = UUID.randomUUID().toString();
        desc3.name = "name3-volumens-from";
        desc3.volumesFrom = new String[] { desc1.name + ":ro" };
        desc3.portBindings = null;
        desc3 = doPost(desc3, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc3);
        addForDeletion(desc3);

        // all instances of this request should be allocated on the same host as the one already
        // selected by the previous request since the desc1.pod == desc2.pod
        ContainerAllocationTaskState allocationTask3 = createContainerAllocationTask(
                desc3.documentSelfLink, 2);
        allocationTask3.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        allocationTask3 = allocate(allocationTask3);
        ContainerState container3 = getDocument(ContainerState.class,
                allocationTask3.resourceLinks.iterator().next());

        assertEquals(container1.parentLink, container3.parentLink);
        assertEquals(container1.names.get(0) + ":ro", container3.volumesFrom[0]);
    }

    @Test
    public void testGetMin() {
        assertEquals(1, ContainerAllocationTaskService.getMinParam(0, 1L).longValue());
        assertEquals(1, ContainerAllocationTaskService.getMinParam(1, null).longValue());
        assertEquals(1, ContainerAllocationTaskService.getMinParam(2, 1L).longValue());
        assertEquals(1, ContainerAllocationTaskService.getMinParam(1, 2L).longValue());
        assertEquals(0, ContainerAllocationTaskService.getMinParam(0, null).longValue());
        assertEquals(1, ContainerAllocationTaskService.getMinParam(1, 0L).longValue());
    }

    @Test
    public void testAllocationForSystemContainersDirectlyToSelectedHost() throws Throwable {
        ComputeState dockerHost = createDockerHost(createDockerHostDescription(),
                createResourcePool(), true);

        ContainerDescription shellContainerDesc = getDocument(ContainerDescription.class,
                SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK);
        assertNotNull(shellContainerDesc);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask(
                shellContainerDesc.documentSelfLink, 1);

        // Preset resource names and host selection
        String containerName = SystemContainerDescriptions.AGENT_CONTAINER_NAME;

        allocationTask.resourceNames = new HashSet<>(Arrays.asList(containerName));

        HostSelection hostSelection = new HostSelection();
        hostSelection.resourceCount = 1;
        hostSelection.hostLink = dockerHost.documentSelfLink;
        hostSelection.resourcePoolLinks = new ArrayList<>(
                Arrays.asList(dockerHost.resourcePoolLink));
        allocationTask.hostSelections = new ArrayList<>(Arrays.asList(hostSelection));

        allocationTask = allocate(allocationTask);

        assertNotNull(allocationTask.resourceLinks);
        assertEquals(1, allocationTask.resourceLinks.size());

        ContainerState containerState = getDocument(ContainerState.class,
                allocationTask.resourceLinks.iterator().next());
        assertEquals(containerName, containerState.names.get(0));
        assertTrue(containerState.documentSelfLink.contains(containerName));

        assertEquals(shellContainerDesc.image, containerState.image);
        assertEquals(shellContainerDesc.documentSelfLink, containerState.descriptionLink);
        assertEquals(shellContainerDesc.instanceAdapterReference.getPath(),
                containerState.adapterManagementReference.getPath());

        waitForContainerPowerState(PowerState.RUNNING, containerState.documentSelfLink);
    }

    @Test
    public void testAllocationOfContainerLinksWithPorts() throws Throwable {
        ContainerDescription desc1 = TestRequestStateFactory.createContainerDescription();
        desc1.documentSelfLink = UUID.randomUUID().toString();
        desc1.name = "linked-service";
        desc1._cluster = 2;
        desc1.portBindings = Arrays.stream(new String[] {
                "80" })
                .map((s) -> PortBinding.fromDockerPortMapping(DockerPortMapping.fromString(s)))
                .collect(Collectors.toList())
                .toArray(new PortBinding[0]);
        desc1 = doPost(desc1, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc1);
        addForDeletion(desc1);

        String contextId = UUID.randomUUID().toString();
        ContainerAllocationTaskState allocationTask1 = createContainerAllocationTask(
                desc1.documentSelfLink, 2);
        allocationTask1.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        allocationTask1 = allocate(allocationTask1);

        ContainerDescription desc2 = TestRequestStateFactory.createContainerDescription();
        desc2.documentSelfLink = UUID.randomUUID().toString();
        desc2.name = "service";
        desc2.portBindings = Arrays.stream(new String[] {
                "5008:80",
                "443" })
                .map((s) -> PortBinding.fromDockerPortMapping(DockerPortMapping.fromString(s)))
                .collect(Collectors.toList())
                .toArray(new PortBinding[0]);
        desc2 = doPost(desc2, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc2);
        addForDeletion(desc2);

        ContainerAllocationTaskState allocationTask2 = createContainerAllocationTask(
                desc2.documentSelfLink, 1);
        allocationTask2.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        allocationTask2 = allocate(allocationTask2);

        Iterator<String> resourceIterator = allocationTask1.resourceLinks.iterator();
        validatePorts(desc1, getDocument(ContainerState.class, resourceIterator.next()));
        validatePorts(desc1, getDocument(ContainerState.class, resourceIterator.next()));
        validatePorts(desc2,
                getDocument(ContainerState.class, allocationTask2.resourceLinks.iterator().next()));
    }

    @SuppressWarnings("static-access")
    @Test
    public void testContainerAllocationSubscriptionSubStages() throws Throwable {

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put("customPropB", "valueB");
        allocationTask = allocate(allocationTask);

        assertNotNull(allocationTask);
        Set<SubStage> subscriptionSubStages = allocationTask.taskSubStage.SUBSCRIPTION_SUB_STAGES;

        assertNotNull(subscriptionSubStages);
        assertTrue(subscriptionSubStages.contains(allocationTask.taskSubStage.BUILD_RESOURCES_LINKS));
    }


    @Test
    public void testEnhanceExtensibilityResponse() throws Throwable {

        /**
         * Initially the placement is:
         *  H(1)    H(2)
         *   |
         *   c1
         *
         * After patch from client:
         *  H(2)    H(1)
         *   |
         *   c1
         *
         */

        //Create second host for host selections.
        createDockerHost(createDockerHostDescription(), resourcePool, true);

        containerDesc.customProperties = new HashMap<>();
        containerDesc.customProperties.put("customPropA", "valueA");

        doOperation(containerDesc, UriUtils.buildUri(host, containerDesc.documentSelfLink),
                false, Action.PUT);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put("customPropB", "valueB");

        allocationTask = allocate(allocationTask);

        final String selfLink = allocationTask.documentSelfLink;
        assertNotNull(selfLink);

        assertNotNull(allocationTask);
        assertEquals(1, allocationTask.resourceLinks.size());

        ContainerAllocationTaskService service = new ContainerAllocationTaskService();
        service.setHost(host);

        allocationTask.taskSubStage = null;

        AllocationExtensibilityCallbackResponse payload =
                (AllocationExtensibilityCallbackResponse) service
                        .notificationPayload(allocationTask);

        List<HostSelection> beforeExtensibility = new ArrayList<>(
                allocationTask.hostSelections);

        payload.hosts = beforeExtensibility.stream()
                .map(hs -> hs.name)
                .collect(Collectors.toList());

        Collections.reverse(payload.hosts);

        assertNotEquals(beforeExtensibility, payload.hosts);

        TestContext context = new TestContext(1, Duration.ofMinutes(1));

        service.enhanceExtensibilityResponse(allocationTask, payload).whenComplete((r, err) -> {
            try {
                ContainerAllocationTaskState document = getDocument(
                        ContainerAllocationTaskState.class,
                        selfLink);

                assertNotNull(document);

                List<HostSelection> patchedHosts = new ArrayList<>(
                        document.hostSelections);

                assertNotNull(patchedHosts);

                assertNotEquals(beforeExtensibility, patchedHosts);

                //Task is in complete state and won't remove initial host selections, just add newfo
                //ones to the existing which have been assigned when document has been initialized.
                if (patchedHosts.size() == 4) {
                    //Assert that new newly added host selections are in proper order (reversed
                    // of original)
                    assertEquals(beforeExtensibility.get(1).name, patchedHosts.get(2).name);
                    assertEquals(beforeExtensibility.get(0).name, patchedHosts.get(3).name);
                } else {
                    context.failIteration(new Throwable("Expected 4 host selections."));
                }
            } catch (Throwable throwable) {
                context.failIteration(throwable);
            }
            context.completeIteration();
        });
        context.await();
    }

    @Test
    public void testEmptyHostSelections() throws Throwable {

        //Create second host for host selections.
        createDockerHost(createDockerHostDescription(), resourcePool, true);

        containerDesc.customProperties = new HashMap<>();
        containerDesc.customProperties.put("customPropA", "valueA");

        doOperation(containerDesc, UriUtils.buildUri(host, containerDesc.documentSelfLink),
                false, Action.PUT);

        ContainerAllocationTaskState allocationTask = createContainerAllocationTask();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put("customPropB", "valueB");

        allocationTask = allocate(allocationTask);

        final String selfLink = allocationTask.documentSelfLink;
        assertNotNull(selfLink);

        assertNotNull(allocationTask);
        assertEquals(1, allocationTask.resourceLinks.size());

        ContainerAllocationTaskService service = new ContainerAllocationTaskService();
        service.setHost(host);

        allocationTask.taskSubStage = null;

        AllocationExtensibilityCallbackResponse payload =
                (AllocationExtensibilityCallbackResponse) service
                        .notificationPayload(allocationTask);

        List<HostSelection> beforeExtensibility = new ArrayList<>(
                allocationTask.hostSelections);

        payload.hosts = null;

        TestContext context = new TestContext(1, Duration.ofMinutes(1));

        service.enhanceExtensibilityResponse(allocationTask, payload).whenComplete((r, err) -> {
            try {
                ContainerAllocationTaskState document = getDocument(
                        ContainerAllocationTaskState.class,
                        selfLink);

                assertNotNull(document);

                List<HostSelection> patchedHosts = new ArrayList<>(
                        document.hostSelections);

                assertNotNull(patchedHosts);

                assertEquals(beforeExtensibility.get(0).name, patchedHosts.get(0).name);
                assertEquals(beforeExtensibility.get(1).name, patchedHosts.get(1).name);
                context.completeIteration();

            } catch (Throwable throwable) {
                context.failIteration(throwable);
            }

        });
        context.await();
    }

    private void validatePorts(ContainerDescription containerDescription,
            ContainerState containerState) throws Throwable {
        // get latest ports
        hostPortProfileState = getDocument(HostPortProfileService.HostPortProfileState.class,
                hostPortProfileState.documentSelfLink);
        Set<String> reservedPorts = hostPortProfileState.reservedPorts == null ? new HashSet<>() :
                hostPortProfileState.reservedPorts
                        .entrySet()
                        .stream()
                        .filter(p -> containerState.documentSelfLink.equals(p.getValue()))
                        .map(p -> p.getKey().toString())
                        .collect(Collectors.toSet());

        assertEquals(containerDescription.portBindings.length, reservedPorts.size());
        assertPortBindingsEquals(containerDescription.portBindings, containerState.ports);

        for (PortBinding requestedPort : containerState.ports) {
            assertTrue(reservedPorts.contains(requestedPort.hostPort));
        }
    }

    private ContainerAllocationTaskState allocate(ContainerAllocationTaskState allocationTask)
            throws Throwable {
        allocationTask = startAllocationTask(allocationTask);
        host.log("Start allocation test: " + allocationTask.documentSelfLink);

        allocationTask = waitForTaskSuccess(allocationTask.documentSelfLink,
                ContainerAllocationTaskState.class);

        assertNotNull("ResourceLinks null for allocation: " + allocationTask.documentSelfLink,
                allocationTask.resourceLinks);
        assertEquals("Resource count not equal for: " + allocationTask.documentSelfLink,
                allocationTask.resourceCount, Long.valueOf(allocationTask.resourceLinks.size()));

        host.log("Finished allocation test: " + allocationTask.documentSelfLink);
        return allocationTask;
    }

    private ContainerAllocationTaskState createContainerAllocationTask() {
        return createContainerAllocationTask(containerDesc.documentSelfLink, 1);
    }

    private ContainerAllocationTaskState createContainerAllocationTask(String containerDescLink,
            long resourceCount) {
        ContainerAllocationTaskState allocationTask = new ContainerAllocationTaskState();
        allocationTask.resourceDescriptionLink = containerDescLink;
        allocationTask.groupResourcePlacementLink = groupPlacementState.documentSelfLink;
        allocationTask.resourceType = ResourceType.CONTAINER_TYPE.getName();
        allocationTask.resourceCount = resourceCount;
        allocationTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        allocationTask.customProperties = new HashMap<>();
        return allocationTask;
    }

    private ContainerAllocationTaskState startAllocationTask(
            ContainerAllocationTaskState allocationTask) throws Throwable {
        ContainerAllocationTaskState outAllocationTask = doPost(
                allocationTask, ContainerAllocationTaskFactoryService.SELF_LINK);
        assertNotNull(outAllocationTask);
        return outAllocationTask;
    }

    private void assertPortBindingsEquals(PortBinding[] expecteds,
            List<PortBinding> actuals) {

        assertPortBindingsEquals(Arrays.asList(expecteds), actuals);
    }

    private void assertPortBindingsEquals(List<PortBinding> expecteds,
            List<PortBinding> actuals) {
        new ComparisonCriteria() {
            @Override
            protected void assertElementsEqual(Object expected, Object actual) {
                PortBinding expectedMapping = (PortBinding) expected;
                PortBinding actualMapping = (PortBinding) actual;

                assertEquals("protocol", expectedMapping.protocol, actualMapping.protocol);

                assertEquals("container port", expectedMapping.containerPort,
                        actualMapping.containerPort);

                assertEquals("host ip", expectedMapping.hostIp,
                        actualMapping.hostIp);

                // if the host port is not specified in the description it's ok that it is different
                // in the ContainerState (since it was bound to some random port)
                String expectedHostPort = expectedMapping.hostPort;
                if (expectedHostPort != null && !expectedHostPort.isEmpty()) {
                    assertEquals("host port", expectedHostPort, actualMapping.hostPort);
                }
            }
        }.arrayEquals(null, expecteds.toArray(), actuals.toArray());
    }

    private void assertContainerStateAfterAllocation(ContainerAllocationTaskState allocationTask) throws Throwable {
        assertContainerStateAfterAllocation(allocationTask, null);
    }

    private void assertContainerStateAfterAllocation(ContainerAllocationTaskState allocationTask, ContainerDescription containerDesc) throws Throwable {
        ContainerState containerState = getDocument(ContainerState.class,
                    allocationTask.resourceLinks.iterator().next());

        if (containerDesc == null) {
            containerDesc = this.containerDesc;
        }

        assertTrue(containerState.names.get(0).startsWith(containerDesc.name));
        assertNull(containerState.id);
        assertTrue(containerState.documentSelfLink.contains(containerDesc.name));
        assertEquals(containerDesc.documentSelfLink, containerState.descriptionLink);
        assertNull(containerState.created);
        assertEquals(allocationTask.hostSelections.get(0).hostLink, containerState.parentLink);
        assertEquals(groupPlacementState.documentSelfLink,
                containerState.groupResourcePlacementLink);
        assertEquals(allocationTask.tenantLinks, containerState.tenantLinks);
        assertEquals(containerDesc.instanceAdapterReference.getPath(),
                containerState.adapterManagementReference.getPath());
        assertEquals(ContainerState.CONTAINER_ALLOCATION_STATUS, containerState.status);
        assertArrayEquals(containerDesc.command, containerState.command);
        assertEquals(containerDesc.image, containerState.image);
        assertTrue(containerState.documentExpirationTimeMicros > 0);
        waitForContainerPowerState(PowerState.PROVISIONING, containerState.documentSelfLink);

        // make sure the host is not update with the new container.
        Set<ContainerState> containerStates = getExistingContainersInAdapter();
        for (ContainerState containerInAdapterState : containerStates) {
            if (containerInAdapterState.documentSelfLink
                    .endsWith(containerState.documentSelfLink)) {
                fail("Container State not removed with link: " + containerState.documentSelfLink);
            }
        }
    }
}