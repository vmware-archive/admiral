/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container;

import static org.junit.Assert.fail;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService.ContainerHostDataCollectionState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.admiral.service.test.MockDockerContainerToHostService;
import com.vmware.admiral.service.test.MockDockerContainerToHostService.MockDockerContainerToHostState;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

public class ContainerHostDataCollectionServiceTest extends ComputeBaseTest {

    private static final String TEST_PREEXISTING_CONTAINER_ID = "preexisting-container";

    private ContainerState missingContainerState;
    private MockDockerAdapterService mockAdapterService;
    private String preexistingContainerId;
    private String preexistingContainerName = "PreexistingName";
    private String createdContainerName = "ContainerName";
    private List<String> preexistingContainerNames;
    private List<String> containerNames;

    @Before
    public void setUp() throws Throwable {
        preexistingContainerId = TEST_PREEXISTING_CONTAINER_ID
                + UUID.randomUUID().toString();

        preexistingContainerNames = new ArrayList<>();
        preexistingContainerNames.add(preexistingContainerName);

        containerNames = new ArrayList<>();
        containerNames.add(createdContainerName);

        host.startFactory(new MockDockerContainerToHostService());
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerHostAdapterService.class)), new MockDockerHostAdapterService());
        mockAdapterService = new MockDockerAdapterService();
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerAdapterService.class)), mockAdapterService);

        waitForServiceAvailability(ContainerHostDataCollectionService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);

        waitForServiceAvailability(MockDockerContainerToHostService.FACTORY_LINK);
        waitForServiceAvailability(MockDockerHostAdapterService.SELF_LINK);
    }

    @Test
    public void testContainersCountOnHostWithContainersNoSystem() throws Throwable {
        String hostId = UUID.randomUUID().toString();
        String hostLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, hostId);
        // add preexisting container
        addContainerToMockAdapter(hostLink, preexistingContainerId, preexistingContainerNames);

        ComputeDescription hostDescription = createComputeDescription();
        hostDescription = doPost(hostDescription, ComputeDescriptionService.FACTORY_LINK);

        ComputeState cs = createComputeState(hostId, hostDescription);

        cs = doPost(cs, ComputeService.FACTORY_LINK);

        ContainerState containerState = new ContainerState();
        containerState.id = UUID.randomUUID().toString();
        containerState.names = containerNames;
        containerState.parentLink = UriUtils.buildUriPath(
                ComputeService.FACTORY_LINK,
                hostId);
        containerState.powerState = PowerState.STOPPED;
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);
        addContainerToMockAdapter(hostLink, containerState.id, containerState.names);

        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        String csLink = cs.documentSelfLink;
        waitFor(() -> {
            ComputeState computeState = getDocument(ComputeState.class, csLink);
            String containers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);
            String systemContainers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_SYSTEM_CONTAINERS_PROP_NAME);
            //the test container created above and the missing container coming from the host.
            host.log("testContainersCountOnHostWithContainer - countainer count: %s", containers);
            return "2".equals(containers) && "0".equals(systemContainers);
        });
    }

    @Test
    public void testContainersCountOnHostWithoutContainers() throws Throwable {
        ComputeDescription hostDescription = createComputeDescription();
        hostDescription = doPost(hostDescription, ComputeDescriptionService.FACTORY_LINK);

        String hostId = UUID.randomUUID().toString();
        ComputeState cs = createComputeState(hostId, hostDescription);

        cs = doPost(cs, ComputeService.FACTORY_LINK);

        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        String csLink = cs.documentSelfLink;
        waitFor(() -> {
            ComputeState computeState = getDocument(ComputeState.class, csLink);
            String containers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);
            String systemContainers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_SYSTEM_CONTAINERS_PROP_NAME);
            return "0".equals(containers) && "0".equals(systemContainers);
        });
    }

    @Test
    public void testContainersCountSystemContainerOnly() throws Throwable {
        ComputeDescription hostDescription = createComputeDescription();
        hostDescription = doPost(hostDescription, ComputeDescriptionService.FACTORY_LINK);

        String hostId = UUID.randomUUID().toString();
        String hostLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, hostId);
        ComputeState cs = createComputeState(hostId, hostDescription);

        cs = doPost(cs, ComputeService.FACTORY_LINK);

        ContainerState containerState = new ContainerState();
        containerState.id = UUID.randomUUID().toString();
        containerState.names = containerNames;
        containerState.parentLink = UriUtils.buildUriPath(
                ComputeService.FACTORY_LINK,
                hostId);
        containerState.powerState = PowerState.STOPPED;
        containerState.system = Boolean.TRUE;
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);
        addContainerToMockAdapter(hostLink, containerState.id, containerState.names);

        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        String csLink = cs.documentSelfLink;
        waitFor(() -> {
            ComputeState computeState = getDocument(ComputeState.class, csLink);
            String containers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);
            String systemContainers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_SYSTEM_CONTAINERS_PROP_NAME);
            host.log("testContainersCountOnHostWithContainer - countainer count: %s", containers);
            return "1".equals(containers) && "1".equals(systemContainers);
        });
    }

    @Test
    public void testContainersCountSystemAndNotSystem() throws Throwable {
        String hostId = UUID.randomUUID().toString();
        String hostLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, hostId);
        // add preexisting container
        addContainerToMockAdapter(hostLink, preexistingContainerId, preexistingContainerNames);

        ComputeDescription hostDescription = createComputeDescription();
        hostDescription = doPost(hostDescription, ComputeDescriptionService.FACTORY_LINK);

        ComputeState cs = createComputeState(hostId, hostDescription);

        cs = doPost(cs, ComputeService.FACTORY_LINK);

        ContainerState containerState = new ContainerState();
        containerState.id = UUID.randomUUID().toString();
        containerState.names = containerNames;
        containerState.parentLink = UriUtils.buildUriPath(
                ComputeService.FACTORY_LINK, hostId);
        containerState.powerState = PowerState.STOPPED;
        containerState.system = Boolean.TRUE;
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);
        addContainerToMockAdapter(hostLink, containerState.id, containerState.names);

        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        String csLink = cs.documentSelfLink;
        waitFor(() -> {
            ComputeState computeState = getDocument(ComputeState.class, csLink);
            String containers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);
            String systemContainers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_SYSTEM_CONTAINERS_PROP_NAME);
            // the test container created above and the missing container coming from the host.
            host.log("testContainersCountOnHostWithContainer - countainer count: %s", containers);
            return "2".equals(containers) && "1".equals(systemContainers);
        });
    }

    // jira issue https://jira-hzn.eng.vmware.com/browse/VSYM-199
    @Test
    public void testDataCollectionDuringProvisioning() throws Throwable {
        // stop the mock adapter service and start the mock inspector adapter service:
        stopService(mockAdapterService);
        mockAdapterService = null;
        final MockInspectAdapterService mockInspectAdapterService = new MockInspectAdapterService();
        String containerBeingProvisioned = "containerBeingProvisioned";
        try {
            String hostId = UUID.randomUUID().toString();
            String hostLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, hostId);
            // add preexisting container
            addContainerToMockAdapter(hostLink, preexistingContainerId, preexistingContainerNames);

            URI adapterServiceUri = UriUtils.buildUri(host, ManagementUriParts.ADAPTER_DOCKER);
            host.startService(Operation.createPost(adapterServiceUri), mockInspectAdapterService);
            waitForServiceAvailability(ManagementUriParts.ADAPTER_DOCKER);

            ComputeDescription hostDescription = createComputeDescription();
            hostDescription = doPost(hostDescription, ComputeDescriptionService.FACTORY_LINK);

            ComputeState cs = createComputeState(hostId, hostDescription);

            cs = doPost(cs, ComputeService.FACTORY_LINK);

            // container being provisioned should not be discovered by the data collection
            // Do not set id - the container is still being provisioned
            ContainerState containerState = new ContainerState();
            containerState.names = containerNames;
            containerState.parentLink = UriUtils.buildUriPath(
                    ComputeService.FACTORY_LINK,
                    hostId);
            containerState.powerState = PowerState.PROVISIONING;
            containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);
            addContainerToMockAdapter(hostLink, containerBeingProvisioned, containerState.names);

            doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                    ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                    false,
                    Service.Action.PATCH);

            host.log(">>>> testDiscoverCreateAndInspectContainer: Container Host %s created."
                            + " Waiting for data collection...", cs.documentSelfLink);
            String csLink = cs.documentSelfLink;
            waitFor(() -> {
                ComputeState computeState = getDocument(ComputeState.class, csLink);
                String containers = computeState.customProperties == null ? null
                        : computeState.customProperties
                                .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);

                if (containers != null) {
                    host.log(">>>> # of containers per host %s is %s",
                            computeState.documentSelfLink, containers);
                }
                // The container being provisioned should not be discovered
                return containers != null && Integer.parseInt(containers) == 2;
            });

        } finally {
            stopService(mockInspectAdapterService);
        }
    }

    @Test
    public void testDataCollectionWhenAHostIsMarkedForDeletion() throws Throwable {
        String hostId = UUID.randomUUID().toString();

        ComputeDescription hostDescription = createComputeDescription();
        hostDescription = doPost(hostDescription, ComputeDescriptionService.FACTORY_LINK);

        ComputeState cs = createComputeState(hostId, hostDescription);
        cs.lifecycleState = LifecycleState.SUSPEND;
        cs = doPost(cs, ComputeService.FACTORY_LINK);

        // create a dummy ContainerState on the ComputeState (that will be marked as missing by the
        // collection)
        missingContainerState = new ContainerState();
        missingContainerState.id = UUID.randomUUID().toString();
        missingContainerState.names = containerNames;
        missingContainerState.parentLink = UriUtils.buildUriPath(
                ComputeService.FACTORY_LINK,
                hostId);
        missingContainerState.powerState = PowerState.STOPPED;
        missingContainerState.system = false;
        missingContainerState = doPost(missingContainerState, ContainerFactoryService.SELF_LINK);

        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        String csLink = cs.documentSelfLink;
        final long timoutInMillis = 5000; // 5sec
        long startTime = System.currentTimeMillis();

        waitFor(() -> {
            ComputeState computeState = getDocument(ComputeState.class, csLink);
            String containers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);

            if (containers != null && Integer.parseInt(containers) >= 1) {
                fail("Should not have any containers.");
            }

            return System.currentTimeMillis() - startTime > timoutInMillis;
        });
    }

    @Test
    public void testDataCollection() throws Throwable {
        String hostId = UUID.randomUUID().toString();
        String hostLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, hostId);
        // add preexisting container
        addContainerToMockAdapter(hostLink, preexistingContainerId, preexistingContainerNames);

        host.log(">>>> DataCollection test start <<<<<<<");

        // create a dummy ContainerState on the ComputeState (that will be marked as missing by the
        // collection)
        missingContainerState = new ContainerState();
        missingContainerState.id = UUID.randomUUID().toString();
        missingContainerState.names = containerNames;
        missingContainerState.parentLink = UriUtils.buildUriPath(
                ComputeService.FACTORY_LINK,
                hostId);
        missingContainerState.powerState = PowerState.STOPPED;
        missingContainerState.system = false;
        missingContainerState = doPost(missingContainerState, ContainerFactoryService.SELF_LINK);

        ComputeDescription hostDescription = createComputeDescription();
        hostDescription = doPost(hostDescription, ComputeDescriptionService.FACTORY_LINK);

        ComputeState cs = createComputeState(hostId, hostDescription);
        cs = doPost(cs, ComputeService.FACTORY_LINK);

        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        String csLink = cs.documentSelfLink;
        waitFor(() -> {
            ComputeState computeState = getDocument(ComputeState.class, csLink);
            String containers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);
            return containers != null && Integer.parseInt(containers) >= 1;
        });

        // wait for the ContainerState of the discovered container to be created
        // since we don't know the documentSelfLink of the new container we have to query by the ID
        waitFor(() -> {
            QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerState.class,
                    ContainerState.FIELD_NAME_ID, preexistingContainerId);

            queryTask.setDirect(true);

            AtomicBoolean found = new AtomicBoolean(false);

            host.testStart(1);
            new ServiceDocumentQuery<>(host, ContainerState.class).query(
                    queryTask, (
                            r) -> {
                        if (r.hasException()) {
                            if (found.get()) {
                                return;
                            }
                            host.failIteration(r.getException());
                        } else if (r.hasResult()) {
                            found.set(true);
                        } else {
                            host.completeIteration();
                        }
                    });

            host.testWait();
            return found.get();
        });

        // verify that the missing container is marked as missing

        waitFor(() -> {
            ContainerState missingContainer = getDocument(ContainerState.class,
                    missingContainerState.documentSelfLink);
            if (missingContainer.documentVersion == 0) {
                return false;
            } else if (missingContainer.documentVersion == 1) {
                return PowerState.RETIRED == missingContainer.powerState
                        && missingContainer.documentExpirationTimeMicros > 0;
            } else {
                AtomicBoolean found = new AtomicBoolean(false);
                host.testStart(1);
                //in cases other asynch actions update the state after "RETIRED":
                ServiceDocumentQuery<ContainerState> queryHelper = new ServiceDocumentQuery<>(
                        host,
                        ContainerState.class);
                queryHelper.queryUpdatedDocumentSince(0, missingContainer.documentSelfLink,
                        (r) -> {
                            if (r.hasException()) {
                                host.failIteration(r.getException());
                            } else if (r.hasResult()) {
                                if (PowerState.RETIRED == r.getResult().powerState
                                        && r.getResult().documentExpirationTimeMicros > 0) {
                                    found.set(true);
                                }
                                host.completeIteration();
                            } else {
                                host.completeIteration();
                            }
                        });
                host.log("Missing container document version: ", missingContainer.documentVersion);
                host.testWait();

                return found.get();
            }
        });

        host.log(">>>> DataCollection test end <<<<<<<");
    }

    @Test
    public void testResourcePoolsDataCollection() throws Throwable {
        host.log(">>>> ResourcePool data collection test start <<<<<<<");
        ComputeDescription hostDescription = createComputeDescription();
        hostDescription = doPost(hostDescription, ComputeDescriptionService.FACTORY_LINK);

        ResourcePoolService.ResourcePoolState resourcePoolState = createAndStoreResourcePool();

        //Create a host with 1000 memory and 1000 storage
        ComputeState first = createAndStoreComputeState(hostDescription, resourcePoolState,
                1000L, 1000L, 500L, 100.0, 8);

        //Force data collection. (The one that happens every 5 minutes)
        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        //The resource pool should be updated accordingly
        waitFor(() -> {
            ResourcePoolService.ResourcePoolState resourcePoolStateUpdated = getDocument(
                    ResourcePoolService.ResourcePoolState.class,
                    resourcePoolState.documentSelfLink);
            Long availableMemory = PropertyUtils
                    .getPropertyLong(resourcePoolStateUpdated.customProperties,
                            ContainerHostDataCollectionService
                                    .RESOURCE_POOL_AVAILABLE_MEMORY_CUSTOM_PROP)
                    .orElse(0L);
            Long cpuUsage = PropertyUtils
                    .getPropertyLong(resourcePoolStateUpdated.customProperties,
                            ContainerHostDataCollectionService.RESOURCE_POOL_CPU_USAGE_CUSTOM_PROP)
                    .orElse(0L);
            return resourcePoolStateUpdated.maxMemoryBytes == 1000 && availableMemory == 500L
                    && cpuUsage == 100;
        });

        //Create another host with 500 memory and 500 storage
        ComputeState anotherOne = createAndStoreComputeState(hostDescription,
                resourcePoolState,
                500L, 500L, 100L, 50.0, 2);

        //Force a data collection just for that specific host
        ContainerHostDataCollectionState patch = new ContainerHostDataCollectionState();
        patch.computeContainerHostLinks = Collections.singleton(anotherOne.documentSelfLink);
        doOperation(patch, UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        //The resource pool should have 500 more memory and storage and 100 more available memory
        waitFor(() -> {
            ResourcePoolService.ResourcePoolState resourcePoolStateUpdated = getDocument(
                    ResourcePoolService.ResourcePoolState.class,
                    resourcePoolState.documentSelfLink);
            return resourcePoolStateUpdated.maxMemoryBytes == 1500 && PropertyUtils
                    .getPropertyLong(resourcePoolStateUpdated.customProperties,
                            ContainerHostDataCollectionService
                                    .RESOURCE_POOL_AVAILABLE_MEMORY_CUSTOM_PROP)
                    .get() == 600L;
        });

        //CPU usage is updated only when doing "full" data collection.
        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        waitFor(() -> {
            ResourcePoolService.ResourcePoolState resourcePoolStateUpdated = getDocument(
                    ResourcePoolService.ResourcePoolState.class,
                    resourcePoolState.documentSelfLink);
            //8 cores with 100% usage and 2 with 50% usage => 90% overall
            return PropertyUtils
                    .getPropertyLong(resourcePoolStateUpdated.customProperties,
                            ContainerHostDataCollectionService.RESOURCE_POOL_CPU_USAGE_CUSTOM_PROP)
                    .get() == 90;
        });


        //Send a patch to remove the second host
        patch.computeContainerHostLinks = Collections.singleton(anotherOne.documentSelfLink);
        patch.remove = true;
        doOperation(patch, UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        waitFor(() -> {
            ResourcePoolService.ResourcePoolState resourcePoolStateUpdated = getDocument(
                    ResourcePoolService.ResourcePoolState.class,
                    resourcePoolState.documentSelfLink);
            return resourcePoolStateUpdated.maxMemoryBytes == 1000 && PropertyUtils
                    .getPropertyLong(resourcePoolStateUpdated.customProperties,
                            ContainerHostDataCollectionService
                                    .RESOURCE_POOL_AVAILABLE_MEMORY_CUSTOM_PROP)
                    .get() == 500L;
        });
        delete(anotherOne.documentSelfLink);

        delete(first.documentSelfLink);
        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        waitFor(() -> {
            ResourcePoolService.ResourcePoolState resourcePoolStateUpdated = getDocument(
                    ResourcePoolService.ResourcePoolState.class,
                    resourcePoolState.documentSelfLink);
            Long availableMemory = PropertyUtils
                    .getPropertyLong(resourcePoolStateUpdated.customProperties,
                            ContainerHostDataCollectionService
                                    .RESOURCE_POOL_AVAILABLE_MEMORY_CUSTOM_PROP)
                    .orElse(0L);
            Long cpuUsage = PropertyUtils
                    .getPropertyLong(resourcePoolStateUpdated.customProperties,
                            ContainerHostDataCollectionService.RESOURCE_POOL_CPU_USAGE_CUSTOM_PROP)
                    .orElse(0L);
            return resourcePoolStateUpdated.maxMemoryBytes == 0 && availableMemory == 0
                    && cpuUsage == 0;
        });

        //Expect the RP to have Long.MAX_VALUE memory if there's a host with no memory data
        createAndStoreComputeState(hostDescription,
                resourcePoolState,
                null, null, 0L, 0.0, 1);

        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);
        waitFor(() -> {
            ResourcePoolService.ResourcePoolState resourcePoolStateUpdated = getDocument(
                    ResourcePoolService.ResourcePoolState.class,
                    resourcePoolState.documentSelfLink);
            return resourcePoolStateUpdated.maxMemoryBytes == Long.MAX_VALUE;
        });

        host.log(">>>> ResourcePool data collection test end <<<<<<<");
    }

    @Test
    public void testPlacementUpdates() throws Throwable {
        host.log(">>>> ResourcePool data collection test start <<<<<<<");
        ComputeDescription hostDescription = createComputeDescription();
        hostDescription = doPost(hostDescription, ComputeDescriptionService.FACTORY_LINK);

        ResourcePoolService.ResourcePoolState resourcePoolState = createAndStoreResourcePool();

        //Create a host with 1000 memory and 1000 storage
        createAndStoreComputeState(hostDescription, resourcePoolState, MIN_MEMORY, 1000L, 0L, 0.0,
                1);

        //Create a host with 1000 memory and 1000 storage
        ComputeState second = createAndStoreComputeState(hostDescription, resourcePoolState,
                MIN_MEMORY, 1000L, 0L, 0.0, 1);

        //Force data collection. (The one that happens every 5 minutes)
        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        //The resource pool should be updated accordingly
        waitFor(() -> {
            ResourcePoolService.ResourcePoolState resourcePoolStateUpdated = getDocument(
                    ResourcePoolService.ResourcePoolState.class,
                    resourcePoolState.documentSelfLink);
            return resourcePoolStateUpdated.maxMemoryBytes == MIN_MEMORY * 2;
        });

        //Create two placements with different priorities
        GroupResourcePlacementService.GroupResourcePlacementState a100 =
                createGroupResourcePlacementState(resourcePoolState.documentSelfLink, "A", 100,
                        MIN_MEMORY, 700);
        GroupResourcePlacementService.GroupResourcePlacementState a200 =
                createGroupResourcePlacementState(resourcePoolState.documentSelfLink, "A", 200,
                        MIN_MEMORY, 800);

        doDelete(UriUtils.buildUri(host, second.documentSelfLink), false);

        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        //The memory limit of the placement with the lower priority should be decreased
        waitFor(() -> {
            GroupResourcePlacementService.GroupResourcePlacementState placementStateA200 =
                    getDocument(GroupResourcePlacementService.GroupResourcePlacementState.class,
                            a200.documentSelfLink);
            GroupResourcePlacementService.GroupResourcePlacementState placementStateA100 =
                    getDocument(GroupResourcePlacementService.GroupResourcePlacementState.class,
                            a100.documentSelfLink);
            return placementStateA200.memoryLimit == 0
                    && placementStateA100.memoryLimit == MIN_MEMORY;
        });

        //Create a host with 1000 memory and 1000 storage
        second = createAndStoreComputeState(hostDescription, resourcePoolState,
                MIN_MEMORY, 1000L, 0L, 0.0, 1);

        //Create another two placements for a different group
        GroupResourcePlacementService.GroupResourcePlacementState b1 =
                createGroupResourcePlacementState(resourcePoolState.documentSelfLink, "B", 1,
                        MIN_MEMORY, 800);

        GroupResourcePlacementService.GroupResourcePlacementState b12 =
                createGroupResourcePlacementState(resourcePoolState.documentSelfLink, "B", 1,
                        MIN_MEMORY, 800);

        doDelete(UriUtils.buildUri(host, second.documentSelfLink), false);
        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        //The normalized priorities will be B: 0.5, 0.5; A: 0.33, 0.66. Because the 0.33 one is
        //already empty we expect to decrease the 1000 from the two 0.5
        waitFor(() -> {
            GroupResourcePlacementService.GroupResourcePlacementState placementState =
                    getDocument(GroupResourcePlacementService.GroupResourcePlacementState.class,
                            b1.documentSelfLink);
            GroupResourcePlacementService.GroupResourcePlacementState placementState2 =
                    getDocument(GroupResourcePlacementService.GroupResourcePlacementState.class,
                            b12.documentSelfLink);
            return placementState.memoryLimit == 0 && placementState2.memoryLimit == 0;
        });

        host.log(">>>> ResourcePool data collection test end <<<<<<<");
    }

    @Test
    public void testDiscoverCreateAndInspectContainer() throws Throwable {
        //stop the mock adapter service and start the mock inspector adapter service:
        stopService(mockAdapterService);
        mockAdapterService = null;
        final MockInspectAdapterService mockInspectAdapterService = new MockInspectAdapterService();

        try {
            String hostId = UUID.randomUUID().toString();
            String hostLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, hostId);
            // add preexisting container
            addContainerToMockAdapter(hostLink, preexistingContainerId, preexistingContainerNames);

            URI adapterServiceUri = UriUtils.buildUri(host, ManagementUriParts.ADAPTER_DOCKER);
            host.startService(Operation.createPost(adapterServiceUri), mockInspectAdapterService);
            waitForServiceAvailability(ManagementUriParts.ADAPTER_DOCKER);

            ComputeDescription hostDescription = createComputeDescription();
            hostDescription = doPost(hostDescription, ComputeDescriptionService.FACTORY_LINK);

            ComputeState cs = createComputeState(hostId, hostDescription);

            cs = doPost(cs, ComputeService.FACTORY_LINK);

            doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                    ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                    false,
                    Service.Action.PATCH);

            host.log(">>>> testDiscoverCreateAndInspectContainer: Container Host %s created."
                            + " Waiting for data collection...", cs.documentSelfLink);
            String csLink = cs.documentSelfLink;
            waitFor(() -> {
                ComputeState computeState = getDocument(ComputeState.class, csLink);
                String containers = computeState.customProperties == null ? null
                        : computeState.customProperties
                                .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);

                if (containers != null) {
                    host.log(">>>> # of containers per host %s is %s",
                            computeState.documentSelfLink, containers);
                }

                return containers != null && Integer.parseInt(containers) >= 1;
            });

            host.log(">>>> wait for the ContainerState of the discovered container to be"
                    + " created...");
            AtomicReference<String> containerStateSelfLink = new AtomicReference<>();
            // wait for the ContainerState of the discovered container to be created
            // since we don't know the documentSelfLink of the new container we have to query by ID
            waitFor(() -> {
                QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerState.class,
                        ContainerState.FIELD_NAME_ID, preexistingContainerId);

                queryTask.setDirect(true);

                host.testStart(1);
                new ServiceDocumentQuery<>(host, ContainerState.class).query(
                        queryTask, (r) -> {
                            if (r.hasException()) {
                                if (containerStateSelfLink.get() != null) {
                                    return;
                                }
                                host.failIteration(r.getException());
                            } else if (r.hasResult()) {
                                containerStateSelfLink.set(r.getDocumentSelfLink());
                            } else {
                                host.completeIteration();
                            }
                        });

                host.testWait();
                return containerStateSelfLink.get() != null;
            });

            final URI containerStateUri = UriUtils.buildPublicUri(host,
                    containerStateSelfLink.get());
            host.log(">>>> Created ContainerState with uri: " + containerStateUri);

            DeploymentProfileConfig.getInstance().setTest(false);

            AtomicInteger count = new AtomicInteger();
            waitFor("Inspect not called. Timed out waiting...", () -> {
                if (count.incrementAndGet() > 5) {
                    host.log(">>>> Current resourcesInvokedInspect: %s ",
                            mockInspectAdapterService.resourcesInvokedInspect.size());
                }
                return mockInspectAdapterService.isInspectInvokedForResource(containerStateUri);
            });
        } finally {
            stopService(mockInspectAdapterService);
        }
    }

    // jira issue VBV-652
    @Test
    public void testContainersCountWhenHostIsAddedTwice() throws Throwable {
        String hostId = UUID.randomUUID().toString();

        ComputeDescription hostDescription = createComputeDescription();
        hostDescription = doPost(hostDescription, ComputeDescriptionService.FACTORY_LINK);

        //Add the same host for different tenants
        ComputeState cs1 = createComputeState("qe::" + hostId, hostDescription);
        ComputeState cs2 = createComputeState("test::" + hostId, hostDescription);

        cs1 = doPost(cs1, ComputeService.FACTORY_LINK);
        cs2 = doPost(cs2, ComputeService.FACTORY_LINK);

        ContainerState containerState = new ContainerState();
        containerState.id = UUID.randomUUID().toString();
        containerState.names = containerNames;
        containerState.parentLink = UriUtils.buildUriPath(
                ComputeService.FACTORY_LINK, hostId);
        containerState.powerState = PowerState.STOPPED;
        containerState.system = Boolean.TRUE;
        // Add the container to both hosts
        addContainerToMockAdapter(cs1.parentLink, containerState.id, SystemContainerDescriptions
                .getSystemContainerNames());
        addContainerToMockAdapter(cs2.parentLink, containerState.id, SystemContainerDescriptions
                .getSystemContainerNames());

        doOperation(new ContainerHostDataCollectionState(), UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        // Verify that the containers count is set for both hosts
        String csLink1 = cs1.documentSelfLink;
        waitFor(() -> {
            ComputeState computeState = getDocument(ComputeState.class, csLink1);
            String containers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);
            String systemContainers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_SYSTEM_CONTAINERS_PROP_NAME);
            return containers != null && systemContainers != null;
        });

        String csLink2 = cs2.documentSelfLink;
        waitFor(() -> {
            ComputeState computeState = getDocument(ComputeState.class, csLink2);
            String containers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);
            String systemContainers = computeState.customProperties == null ? null
                    : computeState.customProperties
                            .get(ContainerHostService.NUMBER_OF_SYSTEM_CONTAINERS_PROP_NAME);
            return containers != null && systemContainers != null;
        });
    }

    private GroupResourcePlacementService.GroupResourcePlacementState createGroupResourcePlacementState(
            String resourcePoolLink, String group, int priority, long memoryLimit,
            long availableMemory) throws Throwable {
        GroupResourcePlacementService.GroupResourcePlacementState rsrvState =
                new GroupResourcePlacementService.GroupResourcePlacementState();
        rsrvState.resourcePoolLink = resourcePoolLink;
        rsrvState.name = UUID.randomUUID().toString();
        rsrvState.maxNumberInstances = 10;
        rsrvState.memoryLimit = memoryLimit;
        rsrvState.availableMemory = availableMemory;
        rsrvState.cpuShares = 3;
        rsrvState.tenantLinks = Collections.singletonList(group);
        rsrvState.priority = priority;

        return doPost(rsrvState, GroupResourcePlacementService.FACTORY_LINK);
    }

    private ResourcePoolService.ResourcePoolState createAndStoreResourcePool() throws Throwable {
        ResourcePoolService.ResourcePoolState poolState =
                new ResourcePoolService.ResourcePoolState();
        poolState.name = "resource-pool";
        poolState.id = poolState.name;
        poolState.documentSelfLink = poolState.id;
        poolState.maxCpuCount = 1600L;
        poolState.minCpuCount = 16L;
        poolState.minMemoryBytes = MIN_MEMORY;
        poolState.maxMemoryBytes = poolState.minMemoryBytes * 2;
        poolState.minDiskCapacityBytes = poolState.maxDiskCapacityBytes = 1024L * 1024L * 1024L
                * 1024L;
        return doPost(poolState,
                ResourcePoolService.FACTORY_LINK);
    }

    private ComputeState createAndStoreComputeState(
            ComputeDescription hostDescription,
            ResourcePoolService.ResourcePoolState resourcePoolState, Long memory, Long storage,
            Long availableMemory, Double cpuUsage, Integer numCores)
            throws Throwable {
        String hostId = UUID.randomUUID().toString();

        ComputeState cs = new ComputeState();
        cs.id = hostId;
        //        cs.documentSelfLink = cs.id;
        cs.documentSelfLink = UUID.randomUUID().toString();
        cs.address = UUID.randomUUID().toString();
        cs.descriptionLink = hostDescription.documentSelfLink;
        cs.resourcePoolLink = resourcePoolState.documentSelfLink;
        cs.customProperties = new HashMap<>();
        cs.customProperties
                .put(ContainerHostService.DOCKER_HOST_TOTAL_MEMORY_PROP_NAME,
                        memory == null ? null : memory + "");
        cs.customProperties
                .put(ContainerHostService.DOCKER_HOST_TOTAL_STORAGE_PROP_NAME,
                        storage == null ? null : storage + "");
        cs.customProperties
                .put(ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME,
                        availableMemory == null ? null : availableMemory + "");
        cs.customProperties
                .put(ContainerHostService.DOCKER_HOST_CPU_USAGE_PCT_PROP_NAME,
                        cpuUsage == null ? null : cpuUsage + "");
        cs.customProperties
                .put(ContainerHostService.DOCKER_HOST_NUM_CORES_PROP_NAME,
                        numCores == null ? null : numCores + "");
        cs.customProperties.put(ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME, "true");

        cs = doPost(cs, ComputeService.FACTORY_LINK);

        return cs;
    }

    private void addContainerToMockAdapter(String hostLink, String containerId, List<String> containerNames) throws Throwable {
        MockDockerContainerToHostState mockContainerToHostState = new MockDockerContainerToHostState();
        String containerName = createdContainerName;
        if (containerNames != null && !containerNames.isEmpty()) {
            containerName = containerNames.get(0);
        }
        mockContainerToHostState.documentSelfLink = UriUtils.buildUriPath(
                MockDockerContainerToHostService.FACTORY_LINK, UUID.randomUUID().toString());
        mockContainerToHostState.parentLink = hostLink;
        mockContainerToHostState.id = containerId;
        mockContainerToHostState.name = containerName;
        host.sendRequest(Operation.createPost(host, MockDockerContainerToHostService.FACTORY_LINK)
                .setBody(mockContainerToHostState)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log("Cannot create mock container to host state. Error: %s", e.getMessage());
                    }
                }));
        // wait until container to host is created in the mock adapter
        waitFor(() -> {
            getDocument(MockDockerContainerToHostState.class, mockContainerToHostState.documentSelfLink);
            return true;
        });
    }

    private ComputeState createComputeState(String hostId, ComputeDescription hostDescription) {
        ComputeState cs = new ComputeState();
        cs.id = hostId;
        cs.documentSelfLink = cs.id;
        cs.address = "test-address";
        cs.powerState = com.vmware.photon.controller.model.resources.ComputeService.PowerState.ON;
        cs.descriptionLink = hostDescription.documentSelfLink;
        cs.resourcePoolLink = UriUtils.buildUriPath(
                ResourcePoolService.FACTORY_LINK,
                UUID.randomUUID().toString());
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME, "true");
        return cs;
    }

    private ComputeDescription createComputeDescription() {
        ComputeDescription cd = new ComputeDescription();
        cd.id = UUID.randomUUID().toString();
        cd.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        cd.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.DOCKER_CONTAINER.toString()));

        return cd;
    }

    public static class MockInspectAdapterService extends StatelessService {
        public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER;

        private final Set<URI> resourcesInvokedInspect = new ConcurrentSkipListSet<>();

        public boolean isInspectInvokedForResource(URI resource) {
            return resourcesInvokedInspect.contains(resource);
        }

        @Override
        public void handlePatch(Operation op) {
            AdapterRequest state = op.getBody(AdapterRequest.class);
            if (ContainerOperationType.INSPECT.id.equals(state.operationTypeId)) {
                logInfo(">>>> Invoking MockInspectAdapterService handlePatch for Inspect for: %s ",
                        state.resourceReference);
                resourcesInvokedInspect.add(state.resourceReference);
            }
            op.complete();
        }
    }
}
