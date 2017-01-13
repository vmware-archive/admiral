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

package com.vmware.admiral.test.integration.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.BaseTestCase.TestWaitForHandler;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.compute.env.ComputeProfileService;
import com.vmware.admiral.compute.env.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.compute.env.EnvironmentService;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentState;
import com.vmware.admiral.compute.env.NetworkProfileService;
import com.vmware.admiral.compute.env.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.env.StorageProfileService;
import com.vmware.admiral.compute.env.StorageProfileService.StorageProfile;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.ReservationRemovalTaskFactoryService;
import com.vmware.admiral.request.ReservationRemovalTaskService.ReservationRemovalTaskState;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.test.integration.BaseIntegrationSupportIT;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public abstract class BaseComputeProvisionIT extends BaseIntegrationSupportIT {

    private static final String VMS_RESOURCE_POOL_ID = "vms-resource-pool";
    private static final String RESOURCE_POOL_ID = "hosts-resource-pool";
    private static final String PLACEMENT_ID = "host-placement";

    public static final String CONTAINER_DCP_TEST_LATEST_ID = "dcp-test:latest-id";
    public static final String CONTAINER_DCP_TEST_LATEST_IMAGE = "kitematic/hello-world-nginx";
    public static final String CONTAINER_DCP_TEST_LATEST_NAME = "docker-dcp-test";

    private static final String[] TEST_COMMAND = { "/etc/hosts", "-" };
    private static final String TEST_PORT_BINDINGS = "127.0.0.1::8282/tcp";
    private static final String TEST_ENV_PROP = "TEST_PROP WITH SPACE=testValue with space ' \" \\' attempt injection";
    private static final String[] TEST_ENV = { TEST_ENV_PROP };
    private static final String TEST_RESTART_POLICY_NAME = "on-failure";
    private static final int TEST_RESTART_POLICY_RETRIES = 3;
    private static final String TEST_USER = "root";
    private static final int TEST_CPU_SHARES = 512;
    private static final String[] TEST_DNS = { "8.8.8.8", "9.9.9.9" };
    private static final String[] TEST_DNS_SEARCH = { "eng.vmware.com", "vmware.com" };
    private static final String[] TEST_ENTRY_POINT = { "/bin/cat" }; // more than one elements is
    // ignored in SSH adapter

    private static final String[] TEST_VOLUMES = { "/tmp:/mnt/tmp:ro" };
    private static final String[] TEST_CAP_ADD = { "NET_ADMIN" };
    private static final String[] TEST_CAP_DROP = { "MKNOD" };
    private static final String[] TEST_DEVICES = { "/dev/null:/dev/null2:rwm" };
    private static final String TEST_HOSTNAME = "test-hostname";
    private static final String TEST_DOMAINNAME = "eng.vmware.com";
    private static final String TEST_WORKING_DIR = "/tmp";
    private static final boolean TEST_PRIVILEGED = true;

    protected final Set<ComputeState> computesToDelete = new HashSet<>();
    private final Set<String> containersToDelete = new HashSet<>();
    private GroupResourcePlacementState groupResourcePlacementState;
    private EndpointType endpointType;
    protected final TestDocumentLifeCycle documentLifeCycle = TestDocumentLifeCycle.FOR_DELETE;
    protected ResourcePoolState vmsResourcePool;


    private AuthCredentialsServiceState dockerRemoteApiClientCredentials;
    protected EndpointState endpoint;

    @Before
    public void setUp() throws Throwable {
        endpointType = getEndpointType();
        endpoint = createEndpoint(endpointType, TestDocumentLifeCycle.NO_DELETE);

        triggerAndWaitForEndpointEnumeration(endpoint);

        groupResourcePlacementState = createResourcePlacement(PLACEMENT_ID, endpointType,
                endpoint.resourcePoolLink,
                documentLifeCycle);

        vmsResourcePool = createResourcePoolOfVMs(endpointType, documentLifeCycle);
        doSetUp();
    }

    protected void doSetUp() throws Exception {
    }

    @Override
    @After
    public void baseTearDown() throws Exception {
        Iterator<String> it = containersToDelete.iterator();
        while (it.hasNext()) {
            String containerLink = it.next();
            ContainerState containerState = getDocument(containerLink, ContainerState.class);
            if (containerState == null) {
                logger.warning(String.format("Unable to find container %s", containerLink));
                continue;
            }

            try {
                logger.info("---------- Clean up: Request Delete the container instance. --------");
                requestContainerDelete(Collections.singleton(containerLink), false);
            } catch (Throwable t) {
                logger.warning(String.format("Unable to remove container %s: %s", containerLink,
                        t.getMessage()));
            }
        }

        for (ComputeState compute : computesToDelete) {
            try {
                logger.info("---------- Clean up: Request Delete the compute instance: %s --------",
                        compute.documentSelfLink);
                delete(compute);
                cleanupReservation(compute);
            } catch (Throwable t) {
                logger.warning(
                        String.format("Unable to remove compute %s: %s", compute.documentSelfLink,
                                t.getMessage()));
            }
        }

        super.baseTearDown();

        delete(UriUtils.buildUriPath(EndpointAdapterService.SELF_LINK, endpoint.documentSelfLink));
    }

    private void cleanupReservation(ComputeState compute) throws Exception {
        if (groupResourcePlacementState == null) {
            // no group quata
            return;
        }
        ReservationRemovalTaskState task = new ReservationRemovalTaskState();
        task.resourceDescriptionLink = compute.descriptionLink;
        task.resourceCount = 1;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.groupResourcePlacementLink = groupResourcePlacementState.documentSelfLink;

        task = postDocument(ReservationRemovalTaskFactoryService.SELF_LINK, task);
        assertNotNull(task);

        waitForStateChange(
                task.documentSelfLink,
                (body) -> {
                    ReservationRemovalTaskState state = Utils.fromJson(body,
                            ReservationRemovalTaskState.class);
                    if (state.taskInfo.stage.equals(TaskStage.FINISHED)) {
                        return true;
                    } else if (state.taskInfo.stage.equals(TaskStage.FAILED)) {
                        fail("Reservation clean up error: " + state.taskInfo.failure);
                        return true;
                    } else {
                        return false;
                    }
                });
    }

    protected void extendComputeDescription(ComputeDescription computeDescription)
            throws Exception {
    }

    @Test
    public void testProvision() throws Throwable {
        String resourceDescriptionLink = getResourceDescriptionLink();

        RequestBrokerState provisionRequest = allocateAndProvision(resourceDescriptionLink);

        try {
            doWithResources(provisionRequest.resourceLinks);
        } finally {
            // create a host removal task - RequestBroker
            RequestBrokerState deleteRequest = new RequestBrokerState();
            deleteRequest.resourceType = getResourceType(resourceDescriptionLink);
            deleteRequest.resourceLinks = provisionRequest.resourceLinks;
            deleteRequest.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
            RequestBrokerState cleanupRequest = postDocument(RequestBrokerFactoryService.SELF_LINK,
                    deleteRequest);

            waitForTaskToComplete(cleanupRequest.documentSelfLink);
        }
    }

    protected RequestBrokerState allocateAndProvision(
            String resourceDescriptionLink) throws Exception {
        RequestBrokerState allocateRequest = requestCompute(resourceDescriptionLink, true, null);

        allocateRequest = getDocument(allocateRequest.documentSelfLink, RequestBrokerState.class);

        assertNotNull(allocateRequest.resourceLinks);
        System.out.println(allocateRequest.resourceLinks);
        for (String link : allocateRequest.resourceLinks) {
            ComputeState computeState = getDocument(link, ComputeState.class);
            assertNotNull(computeState);
            computesToDelete.add(computeState);
        }

        RequestBrokerState provisionRequest = requestCompute(resourceDescriptionLink, false,
                allocateRequest.resourceLinks);

        provisionRequest = getDocument(provisionRequest.documentSelfLink, RequestBrokerState.class);
        assertNotNull(provisionRequest);
        assertNotNull(provisionRequest.resourceLinks);
        return provisionRequest;
    }

    protected void doWithResources(Set<String> resourceLinks) throws Throwable {
        validateHostState(resourceLinks);
    }

    protected RequestBrokerState requestCompute(String resourceDescLink,
            boolean allocation, Set<String> resourceLinks)
            throws Exception {
        RequestBrokerState requestBrokerState = new RequestBrokerState();

        requestBrokerState.resourceType = getResourceType(resourceDescLink);
        requestBrokerState.resourceDescriptionLink = resourceDescLink;

        requestBrokerState.resourceCount = 1;
        requestBrokerState.resourceLinks = resourceLinks;
        requestBrokerState.tenantLinks = getTenantLinks();
        requestBrokerState.customProperties = new HashMap<>();
        if (allocation) {
            requestBrokerState.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                    "true");
        } else {
            requestBrokerState.operation = ContainerOperationType.CREATE.id;
        }

        RequestBrokerState request = postDocument(RequestBrokerFactoryService.SELF_LINK,
                requestBrokerState);

        waitForTaskToComplete(request.documentSelfLink);
        return request;
    }

    private String getResourceType(String resourceDescLink) {
        if (resourceDescLink.startsWith(CompositeDescriptionFactoryService.SELF_LINK)) {
            return ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        } else {
            return ResourceType.COMPUTE_TYPE.getName();
        }
    }

    private String getLink(String factoryLink, String name) {
        return UriUtils.buildUriPath(factoryLink, name);
    }

    protected ResourcePoolState createResourcePool(EndpointType endpointType,
            EndpointState endpoint, TestDocumentLifeCycle documentLifeCycle) throws Exception {
        return createResourcePool(endpointType, endpoint, RESOURCE_POOL_ID, documentLifeCycle);
    }

    protected ResourcePoolState createResourcePool(EndpointType endpointType,
            EndpointState endpoint, String poolId, TestDocumentLifeCycle documentLifeCycle)
            throws Exception {
        String name = name(endpointType, poolId, SUFFIX);
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.documentSelfLink = getLink(ResourcePoolService.FACTORY_LINK, name);
        poolState.name = name;
        poolState.id = poolState.name;
        poolState.projectName = endpointType.name();
        poolState.tenantLinks = getTenantLinks();
        poolState.maxCpuCount = 1600L;
        poolState.minCpuCount = 16L;
        poolState.currencyUnit = "USD";
        poolState.maxCpuCostPerMinute = 1.0;
        poolState.maxDiskCostPerMinute = 1.0;
        poolState.minMemoryBytes = 1024L * 1024L * 1024L * 46L;
        poolState.maxMemoryBytes = poolState.minMemoryBytes * 2;
        poolState.minDiskCapacityBytes = poolState.maxDiskCapacityBytes = 1024L * 1024L * 1024L
                * 1024L;
        poolState.customProperties = new HashMap<>();
        if (endpoint != null) {
            poolState.customProperties.put(
                    ComputeProperties.ENDPOINT_LINK_PROP_NAME, endpoint.documentSelfLink);
        }

        ResourcePoolState resourcePoolState = postDocument(ResourcePoolService.FACTORY_LINK,
                poolState, documentLifeCycle);

        assertNotNull(resourcePoolState);

        return resourcePoolState;
    }

    private ResourcePoolState createResourcePoolOfVMs(EndpointType endpointType,
            TestDocumentLifeCycle documentLifeCycle) throws Exception {
        return createResourcePool(endpointType, null, VMS_RESOURCE_POOL_ID, documentLifeCycle);
    }

    protected void createEnvironment(ComputeProfile computeProfile, NetworkProfile networkProfile,
            StorageProfile storageProfile) {
        List<ServiceDocument> docs = new ArrayList<>();
        String id = UUID.randomUUID().toString();
        EnvironmentState env = new EnvironmentState();
        env.name = "wordpressEnv";
        env.documentSelfLink = UriUtils.buildUriPath(EnvironmentService.FACTORY_LINK, id);
        env.endpointLink = endpoint.documentSelfLink;
        docs.add(env);

        if (computeProfile != null) {
            env.computeProfileLink = UriUtils.buildUriPath(ComputeProfileService.FACTORY_LINK, id);
            computeProfile.documentSelfLink = env.computeProfileLink;
            docs.add(computeProfile);
        }
        if (networkProfile != null) {
            env.networkProfileLink = UriUtils.buildUriPath(NetworkProfileService.FACTORY_LINK, id);
            networkProfile.documentSelfLink = env.networkProfileLink;
            docs.add(networkProfile);
        }
        if (storageProfile != null) {
            env.storageProfileLink = UriUtils.buildUriPath(StorageProfileService.FACTORY_LINK, id);
            storageProfile.documentSelfLink = env.storageProfileLink;
            docs.add(storageProfile);
        }

        docs.forEach(d -> {
            try {
                postDocument(UriUtils.getParentPath(d.documentSelfLink), d);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    protected String getResourceDescriptionLink() throws Exception {
        return createComputeDescription().documentSelfLink;
    }

    protected ComputeDescription createComputeDescription()
            throws Exception {

        ComputeDescription computeDesc = prepareComputeDescription();

        ComputeDescription computeDescription = postDocument(ComputeDescriptionService.FACTORY_LINK,
                computeDesc, documentLifeCycle);

        return computeDescription;
    }

    protected ComputeDescription prepareComputeDescription() throws Exception {
        String id = name(getEndpointType(), "test", UUID.randomUUID().toString());
        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.id = id;
        computeDesc.name = "belvm" + String.valueOf(System.currentTimeMillis() / 1000);
        computeDesc.instanceType = "small";
        computeDesc.tenantLinks = getTenantLinks();
        computeDesc.customProperties = new HashMap<>();
        computeDesc.customProperties.put(ComputeProperties.CUSTOM_DISPLAY_NAME, computeDesc.name);
        computeDesc.customProperties
                .put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, "coreos");

        computeDesc.customProperties.put(
                ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK,
                vmsResourcePool.documentSelfLink);

        extendComputeDescription(computeDesc);
        return computeDesc;
    }

    protected GroupResourcePlacementState createResourcePlacement(String name, EndpointType endpointType,
            String resourcePoolLink, TestDocumentLifeCycle documentLifeCycle)
            throws Exception {
        GroupResourcePlacementState placementState = new GroupResourcePlacementState();
        placementState.resourceType = ResourceType.COMPUTE_TYPE.getName();
        placementState.maxNumberInstances = 30;
        placementState.resourcePoolLink = resourcePoolLink;
        placementState.name = name(endpointType, name, SUFFIX);
        placementState.documentSelfLink = placementState.name;
        placementState.availableInstancesCount = 1000000;
        placementState.priority = 1;
        placementState.tenantLinks = getTenantLinks();

        GroupResourcePlacementState currentQuata = getDocument(
                getLink(GroupResourcePlacementService.FACTORY_LINK, placementState.name),
                GroupResourcePlacementState.class);
        if (currentQuata != null) {
            return currentQuata;
        }
        GroupResourcePlacementState resourcePolicyState = postDocument(
                GroupResourcePlacementService.FACTORY_LINK, placementState, documentLifeCycle);

        assertNotNull(resourcePolicyState);

        return resourcePolicyState;
    }

    protected void requestContainerAndDelete(String resourceDescLink) throws Exception {
        logger.info("********************************************************************");
        logger.info("---------- Create RequestBrokerState and start the request --------");
        logger.info("********************************************************************");

        logger.info("---------- 1. Request container instance. --------");
        RequestBrokerState request = requestContainer(resourceDescLink);

        logger.info(
                "---------- 2. Verify the request is successful and container instance is created. --------");
        validateAfterStart(resourceDescLink, request);

        logger.info("---------- 3. Request Delete the container instance. --------");
        requestContainerDelete(request.resourceLinks, true);
    }

    protected RequestBrokerState requestContainer(String resourceDescLink)
            throws Exception {

        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_TYPE.getName();
        request.resourceDescriptionLink = resourceDescLink;
        request.tenantLinks = getTenantLinks();
        request = postDocument(RequestBrokerFactoryService.SELF_LINK, request);

        waitForTaskToComplete(request.documentSelfLink);

        request = getDocument(request.documentSelfLink, RequestBrokerState.class);
        for (String containerLink : request.resourceLinks) {
            containersToDelete.add(containerLink);
        }

        return request;
    }

    protected void requestContainerDelete(Set<String> resourceLinks, boolean verifyDelete)
            throws Exception {

        RequestBrokerState day2DeleteRequest = new RequestBrokerState();
        day2DeleteRequest.resourceType = ResourceType.CONTAINER_TYPE.getName();
        day2DeleteRequest.operation = ContainerOperationType.DELETE.id;
        day2DeleteRequest.resourceLinks = resourceLinks;
        day2DeleteRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, day2DeleteRequest);

        waitForTaskToComplete(day2DeleteRequest.documentSelfLink);

        if (!verifyDelete) {
            return;
        }

        for (String containerLink : resourceLinks) {
            ContainerState conState = getDocument(containerLink, ContainerState.class);
            assertNull(conState);
            String computeStateLink = UriUtils
                    .buildUriPath(ComputeService.FACTORY_LINK, extractId(containerLink));
            ComputeState computeState = getDocument(computeStateLink, ComputeState.class);
            assertNull(computeState);
            containersToDelete.remove(containerLink);
        }
    }

    protected void validateHostState(Collection<String> resourceLinks)
            throws Exception {
        String computeStateLink = resourceLinks.iterator().next();
        ComputeState computeState = getDocument(computeStateLink, ComputeState.class);

        assertNotNull(computeState);
        assertEquals(com.vmware.photon.controller.model.resources.ComputeService.PowerState.ON,
                computeState.powerState);
    }

    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {
        String containerStateLink = request.resourceLinks.iterator().next();
        ContainerState containerState = getDocument(containerStateLink, ContainerState.class);

        assertNotNull(containerState);
        assertEquals(PowerState.RUNNING, containerState.powerState);
        assertEquals(resourceDescLink, containerState.descriptionLink);
    }

    protected ContainerDescription createContainerDescription() throws Exception {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.documentSelfLink = CONTAINER_DCP_TEST_LATEST_ID;

        containerDesc.image = CONTAINER_DCP_TEST_LATEST_IMAGE;

        containerDesc.customProperties = new HashMap<String, String>();

        containerDesc.name = CONTAINER_DCP_TEST_LATEST_NAME;
        containerDesc.command = TEST_COMMAND;
        containerDesc.instanceAdapterReference = URI
                .create(getBaseUrl() + buildServiceUri(ManagementUriParts.ADAPTER_DOCKER));

        containerDesc.portBindings = new PortBinding[] { PortBinding
                .fromDockerPortMapping(DockerPortMapping.fromString(TEST_PORT_BINDINGS)) };

        containerDesc.logConfig = createLogConfig();
        containerDesc.env = TEST_ENV;
        containerDesc.restartPolicy = TEST_RESTART_POLICY_NAME;
        containerDesc.maximumRetryCount = TEST_RESTART_POLICY_RETRIES;
        containerDesc.user = TEST_USER;
        containerDesc.cpuShares = TEST_CPU_SHARES;
        containerDesc.dns = TEST_DNS;
        containerDesc.dnsSearch = TEST_DNS_SEARCH;
        containerDesc.entryPoint = TEST_ENTRY_POINT;
        containerDesc.volumes = TEST_VOLUMES;
        containerDesc.capAdd = TEST_CAP_ADD;
        containerDesc.capDrop = TEST_CAP_DROP;
        containerDesc.device = TEST_DEVICES;
        containerDesc.hostname = TEST_HOSTNAME;
        containerDesc.domainName = TEST_DOMAINNAME;
        containerDesc.workingDir = TEST_WORKING_DIR;
        containerDesc.privileged = TEST_PRIVILEGED;
        containerDesc = postDocument(ContainerDescriptionService.FACTORY_LINK, containerDesc);

        return containerDesc;
    }

    protected void enableContainerHost(ComputeDescription computeDescription) {
        computeDescription.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                dockerRemoteApiClientCredentials.documentSelfLink);

        computeDescription.customProperties
                .put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, "coreos");

        computeDescription.customProperties.put(
                ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        // Set DockerSpecific properties
        computeDescription.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        computeDescription.customProperties.put(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME,
                "2376");
        computeDescription.customProperties.put(ContainerHostService.DOCKER_HOST_SCHEME_PROP_NAME,
                "https");

        String configContent = getConfigContent();
        if (configContent != null) {
            computeDescription.customProperties
                    .put(ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME, configContent);
        }
    }

    protected void doSetupContainerHostPrereq() throws Exception {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.privateKey = CommonTestStateFactory
                .getFileContent(getTestRequiredProp("docker.client.key.file"));
        auth.publicKey = CommonTestStateFactory
                .getFileContent(getTestRequiredProp("docker.client.cert.file"));
        auth.documentSelfLink = UUID.randomUUID().toString();

        dockerRemoteApiClientCredentials = postDocument(AuthCredentialsService.FACTORY_LINK, auth,
                documentLifeCycle);

        createResourcePlacement("vm-placement", getEndpointType(), vmsResourcePool.documentSelfLink,
                documentLifeCycle);
    }

    private String getConfigContent() {
        return CommonTestStateFactory
                .getFileContent(getTestRequiredProp("cloudinit.content.file"));
    }

    private LogConfig createLogConfig() {
        LogConfig logConfig = new LogConfig();
        logConfig.type = "json-file";
        logConfig.config = new HashMap<>();
        logConfig.config.put("max-size", "200k");
        return logConfig;
    }

    protected static void waitFor(TestWaitForHandler handler) throws Throwable {
        waitFor("Failed waiting for condition... ", handler);
    }

    protected static void waitFor(String errorMessage, TestWaitForHandler handler)
            throws Throwable {
        int iterationCount = TASK_CHANGE_WAIT_POLLING_RETRY_COUNT;
        Thread.sleep(STATE_CHANGE_WAIT_POLLING_PERIOD_MILLIS / 5);
        for (int i = 0; i < iterationCount; i++) {
            if (handler.test()) {
                return;
            }

            Thread.sleep(STATE_CHANGE_WAIT_POLLING_PERIOD_MILLIS);
        }
        fail(errorMessage);
    }

}
