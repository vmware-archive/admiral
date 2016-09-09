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

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.GroupResourcePolicyService;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionFactoryService;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.endpoint.EndpointService;
import com.vmware.admiral.compute.endpoint.EndpointService.EndpointState;
import com.vmware.admiral.host.CompositeComponentNotificationProcessingChain;
import com.vmware.admiral.host.HostInitAdapterServiceConfig;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.host.HostInitRequestServicesConfig;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.composition.CompositionSubTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionTaskFactoryService;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public abstract class RequestBaseTest extends BaseTestCase {

    protected ResourcePoolState resourcePool;
    protected ResourcePoolState computeResourcePool;
    protected EndpointState endpoint;
    protected ComputeDescription hostDesc;
    protected ComputeState computeHost;
    protected ComputeDescription vmGuestComputeDescription;
    protected ComputeState vmGuestComputeState;
    protected ContainerDescription containerDesc;
    protected ContainerNetworkDescription containerNetworkDesc;
    protected ContainerVolumeDescription containerVolumeDesc;
    protected GroupResourcePolicyState groupPolicyState;
    protected GroupResourcePolicyState computeGroupPolicyState;
    private final List<ServiceDocument> documentsForDeletion = new ArrayList<>();
    protected final Object initializationLock = new Object();

    protected static final String DEFAULT_GROUP_RESOURCE_POLICY = GroupResourcePolicyService.DEFAULT_RESOURCE_POLICY_LINK;

    @Before
    public void setUp() throws Throwable {
        startServices(host);
        MockDockerAdapterService.resetContainers();
        setUpDockerHostAuthentication();

        createEndpoint();
        // setup Docker Host:
        createResourcePool();
        createComputeResourcePool();
        // setup Group Policy:
        groupPolicyState = createGroupResourcePolicy(resourcePool);
        computeGroupPolicyState = createGroupResourcePolicy(computeResourcePool);
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        createDockerHost(dockerHostDesc, resourcePool);

        // setup Container desc:
        createContainerDescription();

        // setup Container Volume description.
        createContainerVolumeDescription(UUID.randomUUID().toString());

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
                ContainerClusteringTaskFactoryService.SELF_LINK,
                RequestStatusFactoryService.SELF_LINK));

        // admiral states:
        services.addAll(Arrays.asList(
                GroupResourcePolicyService.FACTORY_LINK,
                ContainerDescriptionService.FACTORY_LINK,
                ContainerFactoryService.SELF_LINK,
                RegistryService.FACTORY_LINK,
                ConfigurationFactoryService.SELF_LINK,
                ConfigurationFactoryService.SELF_LINK,
                ContainerHostNetworkConfigFactoryService.SELF_LINK,
                EventLogService.FACTORY_LINK,
                CounterSubTaskService.FACTORY_LINK,
                ReservationAllocationTaskService.FACTORY_LINK));

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

        HostInitTestDcpServicesConfig.startServices(h);
        HostInitPhotonModelServiceConfig.startServices(h);
        HostInitCommonServiceConfig.startServices(h);
        HostInitComputeServicesConfig.startServices(h);
        HostInitRequestServicesConfig.startServices(h);
        HostInitAdapterServiceConfig.startServices(h, true);

        for (String factoryLink : getFactoryServiceList()) {
            waitForServiceAvailability(factoryLink);
        }

        // Default services:
        waitForServiceAvailability(h,
                ResourceNamePrefixService.DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK);
        waitForServiceAvailability(h, DEFAULT_GROUP_RESOURCE_POLICY);
        waitForServiceAvailability(h,
                SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK);
        waitForServiceAvailability(
                h,
                HostContainerListDataCollectionFactoryService.DEFAULT_HOST_CONAINER_LIST_DATA_COLLECTION_LINK);
    }

    protected void addForDeletion(ServiceDocument doc) {
        documentsForDeletion.add(doc);
    }

    protected GroupResourcePolicyState createGroupResourcePolicy(ResourcePoolState resourcePool)
            throws Throwable {
        return createGroupResourcePolicy(resourcePool, 10);
    }

    protected GroupResourcePolicyState createGroupResourcePolicy(ResourcePoolState resourcePool,
            int numberOfInstances) throws Throwable {
        synchronized (initializationLock) {
            if (groupPolicyState == null) {
                groupPolicyState = TestRequestStateFactory
                        .createGroupResourcePolicyState(policyResourceType());
                groupPolicyState.maxNumberInstances = numberOfInstances;
                groupPolicyState.resourcePoolLink = resourcePool.documentSelfLink;
                groupPolicyState = getOrCreateDocument(groupPolicyState,
                        GroupResourcePolicyService.FACTORY_LINK);
                assertNotNull(groupPolicyState);
            }

            return groupPolicyState;
        }
    }

    protected GroupResourcePolicyState createComputeGroupResourcePolicy(
            ResourcePoolState resourcePool,
            int numberOfInstances) throws Throwable {
        synchronized (initializationLock) {
            if (computeGroupPolicyState == null) {
                computeGroupPolicyState = TestRequestStateFactory
                        .createGroupResourcePolicyState(ResourceType.COMPUTE_TYPE);
                computeGroupPolicyState.maxNumberInstances = numberOfInstances;
                computeGroupPolicyState.resourcePoolLink = resourcePool.documentSelfLink;
                computeGroupPolicyState = getOrCreateDocument(computeGroupPolicyState,
                        GroupResourcePolicyService.FACTORY_LINK);
                assertNotNull(computeGroupPolicyState);
            }

            return computeGroupPolicyState;
        }
    }

    protected ResourceType policyResourceType() {
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
                hostDesc.instanceAdapterReference = UriUtilsExtended.buildUri(host,
                        ManagementUriParts.ADAPTER_DOCKER);
                hostDesc = doPost(hostDesc,
                        ComputeDescriptionService.FACTORY_LINK);
                assertNotNull(hostDesc);
            }
            return hostDesc;
        }
    }

    protected ComputeDescription createVmGuestComputeDescription() throws Throwable {
        synchronized (initializationLock) {
            if (vmGuestComputeDescription == null) {
                vmGuestComputeDescription = TestRequestStateFactory
                        .createVmGuestComputeDescription();
                vmGuestComputeDescription.authCredentialsLink = endpoint.authCredentialsLink;
                vmGuestComputeDescription = getOrCreateDocument(vmGuestComputeDescription,
                        ComputeDescriptionService.FACTORY_LINK);
                assertNotNull(vmGuestComputeDescription);
            }
            return vmGuestComputeDescription;
        }
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
        return createDockerHost(computeDesc, resourcePool, computeGroupPolicyState, availableMemory,
                generateId);
    }

    protected ComputeState createDockerHost(ComputeDescription computeDesc,
            ResourcePoolState resourcePool, GroupResourcePolicyState computePolicy,
            Long availableMemory, boolean generateId)
            throws Throwable {
        ComputeState containerHost = TestRequestStateFactory.createDockerComputeHost();
        if (generateId) {
            containerHost.id = UUID.randomUUID().toString();
        }
        containerHost.documentSelfLink = containerHost.id;
        containerHost.resourcePoolLink = resourcePool.documentSelfLink;
        containerHost.descriptionLink = computeDesc.documentSelfLink;

        if (containerHost.customProperties == null) {
            containerHost.customProperties = new HashMap<>();
        }

        containerHost.customProperties.put(
                ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME,
                availableMemory.toString());

        if (computePolicy != null) {
            containerHost.customProperties.put(ComputeConstants.GROUP_RESOURCE_POLICY_LINK_NAME,
                    computePolicy.documentSelfLink);
        }

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

    protected ComputeState createVmGuestCompute(boolean generateId) throws Throwable {
        ComputeState vmGuestComputeState = TestRequestStateFactory.createVmGuestComputeState();
        if (generateId) {
            vmGuestComputeState.id = UUID.randomUUID().toString();
        }
        vmGuestComputeState.documentSelfLink = vmGuestComputeState.id;
        vmGuestComputeState.resourcePoolLink = createComputeResourcePool().documentSelfLink;
        vmGuestComputeState.descriptionLink = createVmGuestComputeDescription().documentSelfLink;
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
                        EndpointService.FACTORY_LINK);
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
                        "Container PowerState is: %s. Expected powerState: %s. Retrying for container: %s...",
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
            boolean isCloned, ServiceDocument... descs) throws Throwable {
        CompositeDescriptionService.CompositeDescription compositeDesc = TestRequestStateFactory
                .createCompositeDescription(isCloned);

        for (ServiceDocument desc : descs) {
            desc.documentSelfLink = UUID.randomUUID().toString();
            if (desc instanceof ContainerDescriptionService.ContainerDescription) {
                desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
            } else if (desc instanceof ContainerNetworkDescriptionService.ContainerNetworkDescription) {
                desc = doPost(desc, ContainerNetworkDescriptionService.FACTORY_LINK);
            } else if (desc instanceof ComputeDescriptionService.ComputeDescription) {
                desc = doPost(desc, ComputeDescriptionService.FACTORY_LINK);
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

    protected CompositeDescriptionService.CompositeDescription createCompositeDesc(
            ServiceDocument... descs) throws Throwable {

        return createCompositeDesc(false, descs);
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

}
