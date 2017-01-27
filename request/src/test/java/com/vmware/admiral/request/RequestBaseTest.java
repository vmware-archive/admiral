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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.RequestProtocol;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.host.CaSigningCertService;
import com.vmware.admiral.host.CompositeComponentNotificationProcessingChain;
import com.vmware.admiral.host.ComputeInitialBootService;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitDockerAdapterServiceConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.host.HostInitRequestServicesConfig;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.composition.CompositionSubTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionTaskFactoryService;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.test.MockComputeHostInstanceAdapter;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.admiral.service.test.MockDockerNetworkAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public abstract class RequestBaseTest extends BaseTestCase {

    protected static final String DEFAULT_GROUP_RESOURCE_POLICY =
            GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK;
    protected static final int HEALTH_CHECK_PORT = 8800;
    protected static final String DEFAULT_HEALTH_CHECK_TIMEOUT = "5000";
    protected static final String DEFAULT_HEALTH_CHECK_DELAY = "1000";

    protected ResourcePoolState resourcePool;
    protected ResourcePoolState computeResourcePool;
    protected EndpointState endpoint;
    protected ComputeDescription hostDesc;
    protected ComputeState computeHost;
    protected ComputeDescription vmGuestComputeDescription;
    protected ComputeState vmGuestComputeState;
    protected ContainerDescription containerDesc;
    protected HostPortProfileService.HostPortProfileState hostPortProfileState;
    protected ContainerNetworkDescription containerNetworkDesc;
    protected ContainerVolumeDescription containerVolumeDesc;
    protected CompositeDescription compositeDescription;
    protected GroupResourcePlacementState groupPlacementState;
    protected GroupResourcePlacementState computeGroupPlacementState;
    protected final Object initializationLock = new Object();
    private final List<ServiceDocument> documentsForDeletion = new ArrayList<>();


    static {
        System.setProperty(ContainerPortsAllocationTaskService.CONTAINER_PORT_ALLOCATION_ENABLED,
                Boolean.TRUE.toString());
    }

    @Before
    public void setUp() throws Throwable {
        startServices(host);
        MockDockerAdapterService.resetContainers();
        MockDockerNetworkAdapterService.resetNetworks();
        setUpDockerHostAuthentication();

        createEndpoint();
        // setup Docker Host:
        createResourcePool();
        // setup Group Placement:
        groupPlacementState = createGroupResourcePlacement(resourcePool);
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        createDockerHost(dockerHostDesc, resourcePool);
        createHostPortProfile();

        // setup Container desc:
        createContainerDescription();

        // setup Container Network description.
        createContainerNetworkDescription(UUID.randomUUID().toString());

        // setup Container Volume description.
        createContainerVolumeDescription(UUID.randomUUID().toString());
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    @Override
    protected void customizeChains(
            Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains) {
        CompositeComponentNotificationProcessingChain.registerOperationProcessingChains(chains);
    }

    protected List<String> getFactoryServiceList() {
        List<String> services = new ArrayList<>();

        // request tasks:
        services.addAll(Arrays.asList(
                RequestBrokerFactoryService.SELF_LINK,
                ContainerAllocationTaskFactoryService.SELF_LINK,
                ContainerNetworkAllocationTaskService.FACTORY_LINK,
                ContainerVolumeAllocationTaskService.FACTORY_LINK,
                ContainerNetworkProvisionTaskService.FACTORY_LINK,
                ReservationTaskFactoryService.SELF_LINK,
                ReservationRemovalTaskFactoryService.SELF_LINK,
                ContainerRemovalTaskFactoryService.SELF_LINK,
                ContainerHostRemovalTaskFactoryService.SELF_LINK,
                CompositionSubTaskFactoryService.SELF_LINK,
                CompositionTaskFactoryService.SELF_LINK,
                RequestStatusFactoryService.SELF_LINK));

        // admiral states:
        services.addAll(Arrays.asList(
                GroupResourcePlacementService.FACTORY_LINK,
                ContainerDescriptionService.FACTORY_LINK,
                ContainerFactoryService.SELF_LINK,
                ClusteringTaskService.FACTORY_LINK,
                RegistryService.FACTORY_LINK,
                ConfigurationFactoryService.SELF_LINK,
                ConfigurationFactoryService.SELF_LINK,
                EventLogService.FACTORY_LINK,
                CounterSubTaskService.FACTORY_LINK,
                ReservationAllocationTaskService.FACTORY_LINK,
                HostPortProfileService.FACTORY_LINK));

        // admiral states:
        services.addAll(Arrays.asList(
                AuthCredentialsService.FACTORY_LINK,
                ComputeDescriptionService.FACTORY_LINK,
                ComputeService.FACTORY_LINK,
                ResourcePoolService.FACTORY_LINK));
        return services;
    }

    protected void startServices(VerificationHost h) throws Throwable {
        // speed up the test (default is 500ms):
        setFinalStatic(QueryUtil.class
                .getDeclaredField("QUERY_RETRY_INTERVAL_MILLIS"), 20L);
        h.registerForServiceAvailability(CaSigningCertService.startTask(h), true,
                CaSigningCertService.FACTORY_LINK);
        HostInitTestDcpServicesConfig.startServices(h);
        HostInitPhotonModelServiceConfig.startServices(h);
        HostInitCommonServiceConfig.startServices(h);
        HostInitComputeServicesConfig.startServices(h, true);
        HostInitRequestServicesConfig.startServices(h);
        HostInitDockerAdapterServiceConfig.startServices(h, true);

        for (String factoryLink : getFactoryServiceList()) {
            waitForServiceAvailability(factoryLink);
        }

        // Default services:
        waitForServiceAvailability(h,
                ResourceNamePrefixService.DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK);
        waitForServiceAvailability(h, DEFAULT_GROUP_RESOURCE_POLICY);
        waitForServiceAvailability(h,
                SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK);
        waitForServiceAvailability(h,
                HostContainerListDataCollectionFactoryService
                        .DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK);
        waitForServiceAvailability(ManagementUriParts.AUTH_CREDENTIALS_CA_LINK);

        waitForInitialBootServiceToBeSelfStopped(ComputeInitialBootService.SELF_LINK);
    }

    protected void addForDeletion(ServiceDocument doc) {
        documentsForDeletion.add(doc);
    }

    protected GroupResourcePlacementState createGroupResourcePlacement(
            ResourcePoolState resourcePool)
            throws Throwable {
        return createGroupResourcePlacement(resourcePool, 10);
    }

    protected GroupResourcePlacementState createGroupResourcePlacement(
            ResourcePoolState resourcePool,
            int numberOfInstances) throws Throwable {
        synchronized (initializationLock) {
            if (groupPlacementState == null) {
                groupPlacementState = TestRequestStateFactory
                        .createGroupResourcePlacementState(placementResourceType());
                groupPlacementState.maxNumberInstances = numberOfInstances;
                groupPlacementState.resourcePoolLink = resourcePool.documentSelfLink;
                groupPlacementState = getOrCreateDocument(groupPlacementState,
                        GroupResourcePlacementService.FACTORY_LINK);
                assertNotNull(groupPlacementState);
            }

            return groupPlacementState;
        }
    }

    protected GroupResourcePlacementState createComputeGroupResourcePlacement(
            ResourcePoolState resourcePool,
            int numberOfInstances) throws Throwable {
        synchronized (initializationLock) {
            if (computeGroupPlacementState == null) {
                computeGroupPlacementState = TestRequestStateFactory
                        .createGroupResourcePlacementState(ResourceType.COMPUTE_TYPE);
                computeGroupPlacementState.maxNumberInstances = numberOfInstances;
                computeGroupPlacementState.resourcePoolLink = resourcePool.documentSelfLink;
                computeGroupPlacementState = getOrCreateDocument(computeGroupPlacementState,
                        GroupResourcePlacementService.FACTORY_LINK);
                assertNotNull(computeGroupPlacementState);
            }

            return computeGroupPlacementState;
        }
    }

    protected ResourceType placementResourceType() {
        return ResourceType.CONTAINER_TYPE;
    }

    protected ContainerDescription createContainerDescription() throws Throwable {
        synchronized (initializationLock) {
            if (containerDesc == null) {
                ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
                desc.documentSelfLink = UUID.randomUUID().toString();
                containerDesc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
                assertNotNull(containerDesc);
            }
            return containerDesc;
        }
    }

    protected ContainerNetworkDescription createContainerNetworkDescription(String name)
            throws Throwable {
        synchronized (initializationLock) {
            if (containerNetworkDesc == null) {
                ContainerNetworkDescription desc = TestRequestStateFactory
                        .createContainerNetworkDescription(name);
                desc.documentSelfLink = UUID.randomUUID().toString();
                containerNetworkDesc = doPost(desc,
                        ContainerNetworkDescriptionService.FACTORY_LINK);
                assertNotNull(containerNetworkDesc);
            }
            return containerNetworkDesc;
        }
    }

    protected ContainerVolumeDescription createContainerVolumeDescription(String name)
            throws Throwable {
        synchronized (initializationLock) {
            if (containerVolumeDesc == null) {
                ContainerVolumeDescription desc = TestRequestStateFactory
                        .createContainerVolumeDescription(name);
                desc.documentSelfLink = UUID.randomUUID().toString();
                containerVolumeDesc = doPost(desc,
                        ContainerVolumeDescriptionService.FACTORY_LINK);
                assertNotNull(containerVolumeDesc);
            }
            return containerVolumeDesc;
        }
    }

    protected ComputeDescription createDockerHostDescription() throws Throwable {
        synchronized (initializationLock) {
            if (hostDesc == null) {
                hostDesc = TestRequestStateFactory.createDockerHostDescription();
                hostDesc.instanceAdapterReference = UriUtils.buildUri(host,
                        MockComputeHostInstanceAdapter.SELF_LINK);
                hostDesc = doPost(hostDesc,
                        ComputeDescriptionService.FACTORY_LINK);
                assertNotNull(hostDesc);
            }
            return hostDesc;
        }
    }

    protected ComputeDescription createComputeDescriptionForVmGuestChildren() throws Throwable {
        synchronized (initializationLock) {
            if (vmGuestComputeDescription == null) {
                vmGuestComputeDescription = TestRequestStateFactory
                        .createComputeDescriptionForVmGuestChildren();
                vmGuestComputeDescription.authCredentialsLink = endpoint.authCredentialsLink;
                vmGuestComputeDescription = getOrCreateDocument(vmGuestComputeDescription,
                        ComputeDescriptionService.FACTORY_LINK);
                assertNotNull(vmGuestComputeDescription);
            }
            return vmGuestComputeDescription;
        }
    }

    protected ComputeDescription createVmGuestComputeDescriptionWithRandomSelfLink()
            throws Throwable {

        ComputeDescription computeDescription = TestRequestStateFactory
                .createVmGuestComputeDescription(true);
        computeDescription.authCredentialsLink = endpoint.authCredentialsLink;
        computeDescription = getOrCreateDocument(computeDescription,
                ComputeDescriptionService.FACTORY_LINK);
        assertNotNull(vmGuestComputeDescription);
        return computeDescription;
    }

    protected ComputeState createDockerHost(ComputeDescription computeDesc,
            ResourcePoolState resourcePool) throws Throwable {
        synchronized (initializationLock) {
            if (computeHost == null) {
                computeHost = createDockerHost(computeDesc, resourcePool, false);
            }
            return computeHost;
        }
    }

    protected ComputeState createDockerHost(ComputeDescription computeDesc,
            ResourcePoolState resourcePool, Long availableMemory, boolean generateId)
            throws Throwable {
        return createDockerHost(computeDesc, resourcePool, computeGroupPlacementState,
                availableMemory,
                null, generateId);
    }

    protected ComputeState createDockerHost(ComputeDescription computeDesc,
            ResourcePoolState resourcePool, GroupResourcePlacementState computePlacement,
            Long availableMemory, Set<String> volumeDrivers, boolean generateId)
            throws Throwable {
        ComputeState containerHost = TestRequestStateFactory.createDockerComputeHost();
        if (generateId) {
            containerHost.id = UUID.randomUUID().toString();
        }
        containerHost.documentSelfLink = containerHost.id;
        containerHost.resourcePoolLink = resourcePool != null ? resourcePool.documentSelfLink
                : null;
        containerHost.descriptionLink = computeDesc.documentSelfLink;
        containerHost.powerState = com.vmware.photon.controller.model.resources.ComputeService
                .PowerState.ON;

        if (containerHost.customProperties == null) {
            containerHost.customProperties = new HashMap<>();
        }

        containerHost.customProperties.put(ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        containerHost.customProperties.put(
                ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME,
                availableMemory.toString());

        if (computePlacement != null) {
            containerHost.customProperties.put(ComputeConstants.GROUP_RESOURCE_PLACEMENT_LINK_NAME,
                    computePlacement.documentSelfLink);
        }

        if (computeDesc.customProperties != null && computeDesc.customProperties
                .containsKey(ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME)) {
            containerHost.customProperties.put(
                    ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME,
                    computeDesc.customProperties.get(
                            ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME));
        }

        if (volumeDrivers == null) {
            volumeDrivers = new HashSet<>();
        }
        containerHost.customProperties.put(ContainerHostService.DOCKER_HOST_PLUGINS_PROP_NAME,
                createSupportedPluginsInfoString(volumeDrivers));

        containerHost = getOrCreateDocument(containerHost, ComputeService.FACTORY_LINK);
        assertNotNull(containerHost);
        if (generateId) {
            documentsForDeletion.add(containerHost);
        }
        return containerHost;
    }

    protected ComputeState createDockerHost(ComputeDescription computeDesc,
            ResourcePoolState resourcePool, boolean generateId) throws Throwable {
        return createDockerHost(computeDesc, resourcePool, Integer.MAX_VALUE - 100L, generateId);
    }

    protected ComputeState createVmHostCompute(boolean generateId) throws Throwable {
        ComputeState vmHostComputeState = TestRequestStateFactory.createVmHostComputeState();
        if (generateId) {
            vmHostComputeState.id = UUID.randomUUID().toString();
        }
        vmHostComputeState.documentSelfLink = vmHostComputeState.id;
        vmHostComputeState.resourcePoolLink = createComputeResourcePool().documentSelfLink;
        vmHostComputeState.descriptionLink = createComputeDescriptionForVmGuestChildren()
                .documentSelfLink;
        vmHostComputeState.type = ComputeType.VM_HOST;
        vmHostComputeState = getOrCreateDocument(vmHostComputeState, ComputeService.FACTORY_LINK);
        assertNotNull(vmHostComputeState);
        if (generateId) {
            documentsForDeletion.add(vmHostComputeState);
        }
        return vmHostComputeState;
    }

    protected void createHostPortProfile() throws Throwable {
        if (hostPortProfileState != null) {
            return;
        }
        hostPortProfileState = new HostPortProfileService.HostPortProfileState();
        hostPortProfileState.hostLink = computeHost.documentSelfLink;
        hostPortProfileState.id = computeHost.id;
        hostPortProfileState.documentSelfLink = hostPortProfileState.id;
        hostPortProfileState = getOrCreateDocument(hostPortProfileState,
                HostPortProfileService.FACTORY_LINK);
        assertNotNull(hostPortProfileState);
        documentsForDeletion.add(hostPortProfileState);
    }

    protected ComputeState createVmComputeWithRandomComputeDescription(boolean generateId,
            ComputeType type) throws Throwable {
        ComputeState vmGuestComputeState = TestRequestStateFactory.createVmHostComputeState();
        if (generateId) {
            vmGuestComputeState.id = UUID.randomUUID().toString();
        }
        vmGuestComputeState.documentSelfLink = vmGuestComputeState.id;
        vmGuestComputeState.resourcePoolLink = createComputeResourcePool().documentSelfLink;
        vmGuestComputeState.descriptionLink = createVmGuestComputeDescriptionWithRandomSelfLink()
                .documentSelfLink;
        vmGuestComputeState.type = type;
        vmGuestComputeState = getOrCreateDocument(vmGuestComputeState, ComputeService.FACTORY_LINK);
        assertNotNull(vmGuestComputeState);
        if (generateId) {
            documentsForDeletion.add(vmGuestComputeState);
        }
        return vmGuestComputeState;
    }

    protected synchronized ResourcePoolState createResourcePool() throws Throwable {
        synchronized (initializationLock) {
            if (resourcePool == null) {
                resourcePool = getOrCreateDocument(TestRequestStateFactory.createResourcePool(),
                        ResourcePoolService.FACTORY_LINK);
                assertNotNull(resourcePool);
            }
            return resourcePool;
        }
    }

    protected synchronized ResourcePoolState createComputeResourcePool() throws Throwable {
        synchronized (initializationLock) {
            if (computeResourcePool == null) {
                computeResourcePool = getOrCreateDocument(
                        TestRequestStateFactory.createResourcePool(),
                        ResourcePoolService.FACTORY_LINK);
                assertNotNull(computeResourcePool);
            }
            return computeResourcePool;
        }
    }

    protected synchronized EndpointState createEndpoint() throws Throwable {
        synchronized (initializationLock) {
            if (endpoint == null) {
                endpoint = getOrCreateDocument(TestRequestStateFactory.createEndpoint(),
                        EndpointAdapterService.SELF_LINK);
                assertNotNull(endpoint);
            }
            return endpoint;
        }
    }

    protected void waitForContainerPowerState(final PowerState expectedPowerState,
            String containerLink) throws Throwable {
        assertNotNull(containerLink);
        waitFor(() -> {
            ContainerState container = getDocument(ContainerState.class, containerLink);
            assertNotNull(container);
            if (container.powerState != expectedPowerState) {
                host.log(
                        "Container PowerState is: %s. Expected: %s. Retrying for container: %s...",
                        container.powerState, expectedPowerState, container.documentSelfLink);
                return false;
            }
            return true;
        });
    }

    protected RequestBrokerState startRequest(RequestBrokerState request) throws Throwable {
        RequestBrokerState requestState = doPost(request, RequestBrokerFactoryService.SELF_LINK);
        assertNotNull(requestState);
        return requestState;
    }

    protected RequestBrokerState waitForRequestToComplete(RequestBrokerState requestState)
            throws Throwable {
        host.log("wait for request: " + requestState.documentSelfLink);
        return waitForTaskSuccess(requestState.documentSelfLink, RequestBrokerState.class);
    }

    protected ContainerRemovalTaskService.ContainerRemovalTaskState waitForRequestToComplete(
            ContainerRemovalTaskService.ContainerRemovalTaskState requestState)
            throws Throwable {
        host.log("wait for request: " + requestState.documentSelfLink);
        return waitForTaskSuccess(requestState.documentSelfLink,
                ContainerRemovalTaskService.ContainerRemovalTaskState.class);
    }

    protected ContainerRemovalTaskService.ContainerRemovalTaskState startRequest(
            ContainerRemovalTaskService.ContainerRemovalTaskState request) throws Throwable {
        ContainerRemovalTaskService.ContainerRemovalTaskState requestState = doPost(request,
                ContainerRemovalTaskFactoryService.SELF_LINK);
        assertNotNull(requestState);
        return requestState;
    }

    protected RequestBrokerState waitForRequestToFail(RequestBrokerState requestState)
            throws Throwable {
        host.log("wait for request to fail: " + requestState.documentSelfLink);
        return waitForTaskError(requestState.documentSelfLink, RequestBrokerState.class);
    }

    /**
     * Use {@link #createCompositeDesc(boolean, ServiceDocument...)} instead!
     */
    @Deprecated
    protected CompositeDescriptionService.CompositeDescription createCompositeDesc(
            boolean isCloned, ResourceType type, ServiceDocument... descs)
            throws Throwable {
        CompositeDescriptionService.CompositeDescription compositeDesc = TestRequestStateFactory
                .createCompositeDescription(isCloned);

        for (ServiceDocument desc : descs) {
            desc.documentSelfLink = UUID.randomUUID().toString();
            if (type == ResourceType.CONTAINER_TYPE) {
                desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
            } else {
                desc = doPost(desc, ComputeDescriptionService.FACTORY_LINK);

            }

            addForDeletion(desc);
            compositeDesc.descriptionLinks.add(desc.documentSelfLink);
        }

        compositeDesc = doPost(compositeDesc, CompositeDescriptionFactoryService.SELF_LINK);
        addForDeletion(compositeDesc);

        return compositeDesc;
    }

    /**
     * Use {@link #createCompositeDesc(ServiceDocument...)} instead!
     */
    @Deprecated
    protected CompositeDescriptionService.CompositeDescription createCompositeDesc(
            ResourceType type,
            ServiceDocument... descs) throws Throwable {

        return createCompositeDesc(false, type, descs);
    }

    protected CompositeDescriptionService.CompositeDescription createCompositeDesc(
            ServiceDocument... descs) throws Throwable {

        return createCompositeDesc(false, descs);
    }

    protected CompositeDescriptionService.CompositeDescription createCompositeDesc(
            boolean isCloned, ServiceDocument... descs) throws Throwable {

        return createCompositeDesc(isCloned, true, descs);
    }

    protected CompositeDescriptionService.CompositeDescription createCompositeDesc(
            boolean isCloned, boolean overrideSelfLink, ServiceDocument... descs) throws Throwable {
        CompositeDescriptionService.CompositeDescription compositeDesc = TestRequestStateFactory
                .createCompositeDescription(isCloned);

        for (ServiceDocument desc : descs) {
            if (overrideSelfLink || desc.documentSelfLink == null) {
                desc.documentSelfLink = UUID.randomUUID().toString();
            }
            if (desc instanceof ContainerDescriptionService.ContainerDescription) {
                desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
            } else if (desc instanceof ContainerNetworkDescriptionService
                    .ContainerNetworkDescription) {
                desc = doPost(desc, ContainerNetworkDescriptionService.FACTORY_LINK);
            } else if (desc instanceof ComputeDescriptionService.ComputeDescription) {
                desc = doPost(desc, ComputeDescriptionService.FACTORY_LINK);
            } else if (desc instanceof ContainerVolumeDescriptionService
                    .ContainerVolumeDescription) {
                desc = doPost(desc, ContainerVolumeDescriptionService.FACTORY_LINK);
            } else {
                throw new IllegalArgumentException(
                        "Unknown description type: " + desc.getClass().getSimpleName());
            }

            addForDeletion(desc);
            compositeDesc.descriptionLinks.add(desc.documentSelfLink);
        }

        compositeDesc = doPost(compositeDesc, CompositeDescriptionFactoryService.SELF_LINK);
        addForDeletion(compositeDesc);

        return compositeDesc;
    }

    protected HealthConfig createHealthConfigTcp() {
        HealthConfig healthConfig = new HealthConfig();
        healthConfig.protocol = RequestProtocol.TCP;
        healthConfig.port = RequestBaseTest.HEALTH_CHECK_PORT;
        healthConfig.healthyThreshold = 2;
        healthConfig.unhealthyThreshold = 2;
        healthConfig.timeoutMillis = 2000;

        return healthConfig;
    }

    protected List<ComputeState> queryComputeByCompositeComponentLink(
            String compositeComponentLink) {
        String contextId = compositeComponentLink
                .replaceAll(CompositeComponentFactoryService.SELF_LINK + "/", "");

        QueryTask q = QueryUtil.buildQuery(ComputeState.class, false);
        QueryTask.Query containerHost = new QueryTask.Query().setTermPropertyName(QuerySpecification
                .buildCompositeFieldName(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        RequestUtils.FIELD_NAME_CONTEXT_ID_KEY))
                .setTermMatchValue(contextId);
        containerHost.occurance = Occurance.MUST_OCCUR;

        q.querySpec.query.addBooleanClause(containerHost);

        QueryUtil.addExpandOption(q);
        ServiceDocumentQuery<ComputeState> query = new ServiceDocumentQuery<>(host,
                ComputeState.class);

        List<ComputeState> result = new ArrayList<>();
        TestContext ctx = testCreate(1);

        query.query(q, (r) -> {
            if (r.hasException()) {
                ctx.failIteration(r.getException());
            } else if (r.hasResult()) {
                result.add(r.getResult());
            } else {
                ctx.completeIteration();
            }
        });

        ctx.await();

        return result;
    }

    protected String createSupportedPluginsInfoString(Set<String> drivers) {
        Map<String, String[]> pluginsInfo = new HashMap<>();
        pluginsInfo.put(ContainerHostService.DOCKER_HOST_PLUGINS_NETWORK_PROP_NAME,
                new String[] { "bridge", "null", "host" });
        // make sure drivers contains 'local' driver
        drivers.add("local");
        pluginsInfo.put(ContainerHostService.DOCKER_HOST_PLUGINS_VOLUME_PROP_NAME,
                drivers.toArray(new String[drivers.size()]));
        return Utils.toJson(pluginsInfo);
    }

    protected void configureHealthCheckTimeout(String timeoutInMs) throws Throwable {
        String healthCheckTimeoutLink = UriUtils.buildUriPath(
                ConfigurationFactoryService.SELF_LINK,
                ContainerAllocationTaskService.HEALTH_CHECK_TIMEOUT_PARAM_NAME);
        ConfigurationState healthCheckTimeout = new ConfigurationState();
        healthCheckTimeout.documentSelfLink = healthCheckTimeoutLink;
        healthCheckTimeout.key = ContainerAllocationTaskService.HEALTH_CHECK_TIMEOUT_PARAM_NAME;
        healthCheckTimeout.value = timeoutInMs;
        doPut(healthCheckTimeout);
        healthCheckTimeout = getDocument(ConfigurationState.class, healthCheckTimeoutLink);
        assertEquals(ContainerAllocationTaskService.HEALTH_CHECK_TIMEOUT_PARAM_NAME,
                healthCheckTimeout.key);
        assertEquals(timeoutInMs, healthCheckTimeout.value);
    }

    protected void configureHealthCheckDelay(String delayInMs) throws Throwable {
        String healthCheckDelayLink = UriUtils.buildUriPath(
                ConfigurationFactoryService.SELF_LINK,
                ContainerAllocationTaskService.HEALTH_CHECK_DELAY_PARAM_NAME);
        ConfigurationState healthCheckDelay = new ConfigurationState();
        healthCheckDelay.documentSelfLink = healthCheckDelayLink;
        healthCheckDelay.key = ContainerAllocationTaskService.HEALTH_CHECK_DELAY_PARAM_NAME;
        healthCheckDelay.value = delayInMs;
        doPut(healthCheckDelay);
        healthCheckDelay = getDocument(ConfigurationState.class, healthCheckDelayLink);
        assertEquals(ContainerAllocationTaskService.HEALTH_CHECK_DELAY_PARAM_NAME,
                healthCheckDelay.key);
        assertEquals(delayInMs, healthCheckDelay.value);
    }
}
