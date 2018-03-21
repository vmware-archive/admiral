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

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.admiral.request.compute.ResourceGroupUtils.COMPUTE_DEPLOYMENT_TYPE_VALUE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.RequestProtocol;
import com.vmware.admiral.compute.container.HostContainerListDataCollection;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerDescriptionService;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerDescriptionService.ContainerLoadBalancerDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.host.CaSigningCertService;
import com.vmware.admiral.host.CompositeComponentInterceptor;
import com.vmware.admiral.host.ComputeInitialBootService;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitDockerAdapterServiceConfig;
import com.vmware.admiral.host.HostInitKubernetesAdapterServiceConfig;
import com.vmware.admiral.host.HostInitLoadBalancerServiceConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.host.HostInitRequestServicesConfig;
import com.vmware.admiral.host.RequestInitialBootService;
import com.vmware.admiral.host.interceptor.OperationInterceptorRegistry;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState.SubStage;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.request.composition.CompositionSubTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionTaskFactoryService;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.test.MockComputeHostInstanceAdapter;
import com.vmware.admiral.service.test.MockDockerContainerToHostService.MockDockerContainerToHostState;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.QueryTask;

public abstract class RequestBaseTest extends BaseTestCase {

    protected static final String DEFAULT_GROUP_RESOURCE_POLICY = GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK;
    protected static final String DEFAULT_HEALTH_CHECK_TIMEOUT = "5000";
    protected static final String DEFAULT_HEALTH_CHECK_DELAY = "1000";

    static {
        System.setProperty(ContainerPortsAllocationTaskService.CONTAINER_PORT_ALLOCATION_ENABLED,
                Boolean.TRUE.toString());
    }

    protected final Object initializationLock = new Object();

    private final List<ServiceDocument> documentsForDeletion = new ArrayList<>();

    protected ResourcePoolState resourcePool;

    protected ComputeDescription hostDesc;

    protected  ComputeDescription dockerHostDesc;

    protected ComputeState computeHost;

    protected ContainerDescription containerDesc;

    protected ContainerState containerState;

    protected HostPortProfileService.HostPortProfileState hostPortProfileState;

    protected ContainerNetworkDescription containerNetworkDesc;

    protected ContainerVolumeDescription containerVolumeDesc;

    protected GroupResourcePlacementState groupPlacementState;

    protected  SslTrustCertificateState sslTrustCert;

    @Before
    public void setUp() throws Throwable {
        startServices(host);
        setUpDockerHostAuthentication();

        // setup Docker Host:
        createResourcePool();
        // setup Group Placement:
        groupPlacementState = createGroupResourcePlacement(resourcePool);
        dockerHostDesc = createDockerHostDescription();
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
    protected void registerInterceptors(OperationInterceptorRegistry registry) {
        CompositeComponentInterceptor.register(registry);
    }

    protected List<String> getFactoryServiceList() {
        List<String> services = new ArrayList<>();

        // request tasks:
        services.addAll(Arrays.asList(
                RequestBrokerFactoryService.SELF_LINK,
                ContainerAllocationTaskFactoryService.SELF_LINK,
                ContainerRedeploymentTaskService.FACTORY_LINK,
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
                RegistryFactoryService.SELF_LINK,
                ConfigurationFactoryService.SELF_LINK,
                ConfigurationFactoryService.SELF_LINK,
                EventLogService.FACTORY_LINK,
                CounterSubTaskService.FACTORY_LINK,
                ReservationAllocationTaskService.FACTORY_LINK,
                HostPortProfileService.FACTORY_LINK,
                ContainerControlLoopService.FACTORY_LINK));

        // admiral states:
        services.addAll(Arrays.asList(
                AuthCredentialsService.FACTORY_LINK,
                ComputeDescriptionService.FACTORY_LINK,
                ComputeService.FACTORY_LINK,
                ResourcePoolService.FACTORY_LINK));

        return services;
    }

    protected void startServices(VerificationHost h) throws Throwable {
        h.registerForServiceAvailability(CaSigningCertService.startTask(h), true,
                CaSigningCertService.FACTORY_LINK);
        HostInitTestDcpServicesConfig.startServices(h);
        HostInitPhotonModelServiceConfig.startServices(h);
        HostInitCommonServiceConfig.startServices(h);
        HostInitComputeServicesConfig.startServices(h, true);
        HostInitRequestServicesConfig.startServices(h);
        HostInitDockerAdapterServiceConfig.startServices(h, true);
        HostInitKubernetesAdapterServiceConfig.startServices(h, true);
        HostInitLoadBalancerServiceConfig.startServices(h);

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
                HostContainerListDataCollection.DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK);
        waitForServiceAvailability(h, ContainerControlLoopService.CONTROL_LOOP_INFO_LINK);
        waitForServiceAvailability(ManagementUriParts.AUTH_CREDENTIALS_CA_LINK);

        waitForInitialBootServiceToBeSelfStopped(ComputeInitialBootService.SELF_LINK);
        waitForInitialBootServiceToBeSelfStopped(RequestInitialBootService.SELF_LINK);
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
                        .createGroupResourcePlacementState(ResourceType.CONTAINER_TYPE);
                groupPlacementState.maxNumberInstances = numberOfInstances;
                groupPlacementState.resourcePoolLink = resourcePool.documentSelfLink;
                groupPlacementState = getOrCreateDocument(groupPlacementState,
                        GroupResourcePlacementService.FACTORY_LINK);
                assertNotNull(groupPlacementState);
            }

            return groupPlacementState;
        }
    }

    protected ResourceType placementResourceType() {
        return ResourceType.CONTAINER_TYPE;
    }

    protected ContainerDescription createContainerDescription() throws Throwable {
        return createContainerDescription(true);
    }

    protected ContainerDescription createContainerDescription(boolean singleton) throws Throwable {
        synchronized (initializationLock) {
            if (singleton && containerDesc == null) {
                ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
                desc.documentSelfLink = UUID.randomUUID().toString();
                containerDesc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
                assertNotNull(containerDesc);
            } else if (!singleton) {
                ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
                desc.documentSelfLink = UUID.randomUUID().toString();
                return doPost(desc, ContainerDescriptionService.FACTORY_LINK);
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
        return createDockerHost(computeDesc, resourcePool,
                availableMemory,
                null, generateId, true);
    }

    protected ComputeState createDockerHost(ComputeDescription computeDesc,
            ResourcePoolState resourcePool, Long availableMemory, Set<String> volumeDrivers,
            boolean generateId, boolean generateTrustCert) throws Throwable {
        ComputeState containerHost = TestRequestStateFactory.createDockerComputeHost();
        if (generateId) {
            containerHost.id = UUID.randomUUID().toString();
        }
        containerHost.documentSelfLink = containerHost.id;
        containerHost.resourcePoolLink = resourcePool != null ? resourcePool.documentSelfLink
                : null;
        containerHost.tenantLinks = computeDesc.tenantLinks;
        containerHost.descriptionLink = computeDesc.documentSelfLink;
        containerHost.endpointLink = computeDesc.endpointLink;
        containerHost.powerState = com.vmware.photon.controller.model.resources.ComputeService.PowerState.ON;

        if (containerHost.customProperties == null) {
            containerHost.customProperties = new HashMap<>();
        }

        containerHost.customProperties.put(ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        // This property is used for checking if the host is VCH.
        // Forcing the __Driver prop with some value, in order to install the system container.
        containerHost.customProperties.put(ContainerHostUtil.PROPERTY_NAME_DRIVER,
                "overlay");
        containerHost.customProperties.put(
                ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME,
                availableMemory.toString());

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

        if (generateTrustCert) {
            containerHost.customProperties.put(ComputeConstants.HOST_TRUST_CERTS_PROP_NAME,
                    createSslTrustCert().documentSelfLink);
        }

        containerHost = getOrCreateDocument(containerHost, ComputeService.FACTORY_LINK);
        assertNotNull(containerHost);
        if (generateId) {
            documentsForDeletion.add(containerHost);
        }
        return containerHost;
    }

    private SslTrustCertificateState createSslTrustCert() throws Throwable {
        if (sslTrustCert == null) {
            String sslTrust1 = CommonTestStateFactory.getFileContent("test_ssl_trust.PEM").trim();
            // String sslTrust2 = CommonTestStateFactory.getFileContent("test_ssl_trust2.PEM").trim();
            sslTrustCert = new SslTrustCertificateState();
            sslTrustCert.certificate = sslTrust1;

            sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);
        }
        return sslTrustCert;
    }

    protected ComputeState createDockerHost(ComputeDescription computeDesc,
            ResourcePoolState resourcePool, boolean generateId) throws Throwable {
        return createDockerHost(computeDesc, resourcePool, Integer.MAX_VALUE - 100L, generateId);
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

        RequestBrokerState rbState = waitForTaskSuccess(requestState.documentSelfLink,
                RequestBrokerState.class);

        // Verify request status
        RequestStatus rs = getDocument(RequestStatus.class, requestState.requestTrackerLink);
        assertNotNull(rs);

        waitForPropertyValue(rs.documentSelfLink, RequestStatus.class,
                RequestStatus.FIELD_NAME_TASK_INFO_STAGE, rbState.taskInfo.stage);

        waitForPropertyValue(rs.documentSelfLink, RequestStatus.class,
                RequestStatus.FIELD_NAME_PROGRESS, 100);

        return rbState;
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

        RequestBrokerState rbState = waitForTaskError(requestState.documentSelfLink,
                RequestBrokerState.class);

        RequestStatus rs = getDocument(RequestStatus.class, rbState.requestTrackerLink);
        assertNotNull(rs);

        waitForPropertyValue(rs.documentSelfLink, RequestStatus.class,
                RequestStatus.FIELD_NAME_TASK_INFO_STAGE, TaskStage.FAILED);

        waitForPropertyValue(rs.documentSelfLink, RequestStatus.class,
                RequestStatus.FIELD_NAME_SUB_STAGE, SubStage.ERROR.name());

        return rbState;
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
            } else if (desc instanceof ContainerNetworkDescriptionService.ContainerNetworkDescription) {
                desc = doPost(desc, ContainerNetworkDescriptionService.FACTORY_LINK);
            } else if (desc instanceof ComputeDescriptionService.ComputeDescription) {
                desc = doPost(desc, ComputeDescriptionService.FACTORY_LINK);
            } else if (desc instanceof ContainerVolumeDescriptionService.ContainerVolumeDescription) {
                desc = doPost(desc, ContainerVolumeDescriptionService.FACTORY_LINK);
            } else if (desc instanceof LoadBalancerDescriptionService.LoadBalancerDescription) {
                desc = doPost(desc, LoadBalancerDescriptionService.FACTORY_LINK);
            } else if (desc instanceof ContainerLoadBalancerDescription) {
                desc = doPost(desc, ContainerLoadBalancerDescriptionService.FACTORY_LINK);
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

    protected HealthConfig createHealthConfigTcp(int port) {
        HealthConfig healthConfig = new HealthConfig();
        healthConfig.protocol = RequestProtocol.TCP;
        healthConfig.port = port;
        healthConfig.healthyThreshold = 2;
        healthConfig.unhealthyThreshold = 2;
        healthConfig.timeoutMillis = 2000;

        return healthConfig;
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

    protected ContainerState provisionContainer(String descriptionLink) throws Throwable {
        RequestBrokerState request = TestRequestStateFactory
                .createRequestState(ResourceType.CONTAINER_TYPE.getName(), descriptionLink);
        request = startRequest(request);
        request = waitForRequestToComplete(request);

        Iterator<String> iterator = request.resourceLinks.iterator();

        ContainerState containerState = null;

        while (iterator.hasNext()) {
            containerState = searchForDocument(ContainerState.class, iterator.next());
            assertNotNull(containerState);
        }

        return containerState;
    }

    protected ResourceGroupState createResourceGroup(String contextId,
            List<String> tenantLinks) throws Throwable {
        ResourceGroupState resGroup = new ResourceGroupState();
        resGroup.name = contextId;
        resGroup.documentSelfLink = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                contextId);
        resGroup.tenantLinks = tenantLinks;
        resGroup.customProperties = new HashMap<>();
        resGroup.customProperties.put(ComputeProperties.RESOURCE_TYPE_KEY,
                COMPUTE_DEPLOYMENT_TYPE_VALUE);
        return getOrCreateDocument(resGroup, ResourceGroupService.FACTORY_LINK);
    }

    protected Set<ContainerState> getExistingContainersInAdapter() {
        host.testStart(1);
        QueryTask q = QueryUtil.buildPropertyQuery(MockDockerContainerToHostState.class);
        QueryUtil.addExpandOption(q);

        Set<ContainerState> containerStates = new HashSet<>();
        new ServiceDocumentQuery<>(host, MockDockerContainerToHostState.class).query(q,
                (r) -> {
                    if (r.hasException()) {
                        host.failIteration(r.getException());
                    } else if (r.hasResult()) {
                        containerStates.add(buildContainer(r.getResult()));
                    } else {
                        host.completeIteration();
                    }
                });
        host.testWait();
        return containerStates;
    }

    private ContainerState buildContainer(MockDockerContainerToHostState containerToHostState) {
        ContainerState containerState = new ContainerState();
        containerState.parentLink = containerToHostState.parentLink;
        containerState.id = containerToHostState.id;
        containerState.name = containerToHostState.name;
        containerState.image = containerToHostState.image;
        containerState.powerState = containerToHostState.powerState;
        return containerState;
    }
}
