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

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescriptionFactoryService;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.BaseTestCase.TestWaitForHandler;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.profile.ComputeProfileService;
import com.vmware.admiral.compute.profile.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.ReservationRemovalTaskFactoryService;
import com.vmware.admiral.request.ReservationRemovalTaskService.ReservationRemovalTaskState;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.test.integration.BaseIntegrationSupportIT;
import com.vmware.admiral.test.integration.SimpleHttpsClient;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;

public abstract class BaseComputeProvisionIT extends BaseIntegrationSupportIT {

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

    private static final long DEFAULT_DISK_SIZE = 8 * 1024;

    public static final String OPERATION_PAYLOAD_PROP_NAME = "__operationPayload";

    public static final String COMPOSITION_CONTEXT_ID_PROP_NAME = "__composition_context_id";
    public static final String CLUSTER_INDEX_PROP_NAME = "__cluster_index";

    public static final String DAY_2_OPERATION_POWER_ON = "Compute.PowerOn";
    public static final String DAY_2_OPERATION_POWER_OFF = "Compute.PowerOff";
    public static final String DAY_2_OPERATION_RESTART = ResourceOperation.RESTART.operation;
    public static final String DAY_2_OPERATION_REBOOT = ResourceOperation.REBOOT.operation;
    public static final String DAY_2_OPERATION_SUSPEND = ResourceOperation.SUSPEND.operation;
    public static final String DAY_2_OPERATION_RESET = ResourceOperation.RESET.operation;

    protected final Map<String, ComputeState> computesToDelete = new HashMap<>();
    private final Set<String> containersToDelete = new HashSet<>();
    private GroupResourcePlacementState groupResourcePlacementState;
    private String endpointType;
    protected final TestDocumentLifeCycle documentLifeCycle = TestDocumentLifeCycle.FOR_DELETE;
    protected ResourcePoolState vmsResourcePool;

    private AuthCredentialsServiceState dockerRemoteApiClientCredentials;
    protected EndpointState endpoint;
    protected String compositionContextId;

    @Before
    public void setUp() throws Throwable {
        endpointType = getEndpointType();
        endpoint = createEndpoint(endpointType, TestDocumentLifeCycle.NO_DELETE);

        triggerAndWaitForEndpointEnumeration(endpoint);

        groupResourcePlacementState = createResourcePlacement(getClass().getSimpleName(),
                endpointType,
                endpoint.resourcePoolLink,
                documentLifeCycle);

        vmsResourcePool = createResourcePoolOfVMs(endpointType, documentLifeCycle);
        doSetUp();
    }

    protected void doSetUp() throws Throwable {
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

        for (ComputeState compute : computesToDelete.values()) {
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

        if (endpoint != null) {
            delete(UriUtils.buildUriPath(EndpointAdapterService.SELF_LINK,
                    endpoint.documentSelfLink));
        }
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
        provision(resourceDescriptionLink);
    }

    protected void provision(String resourceDescriptionLink) throws Throwable {
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
        compositionContextId = UUID.randomUUID().toString();
        RequestBrokerState allocateRequest = requestCompute(resourceDescriptionLink, true, null);

        allocateRequest = getDocument(allocateRequest.documentSelfLink, RequestBrokerState.class);

        assertNotNull(allocateRequest.resourceLinks);
        System.out.println(allocateRequest.resourceLinks);
        for (String link : allocateRequest.resourceLinks) {
            ComputeState computeState = getDocument(link, ComputeState.class);
            assertNotNull(computeState);
            computesToDelete.put(link, computeState);
        }
        logger.info("Allocation of compute request completed successfully");

        RequestBrokerState provisionRequest = requestCompute(resourceDescriptionLink, false,
                allocateRequest.resourceLinks);

        provisionRequest = getDocument(provisionRequest.documentSelfLink, RequestBrokerState.class);
        assertNotNull(provisionRequest);
        assertNotNull(provisionRequest.resourceLinks);
        for (String link : allocateRequest.resourceLinks) {
            computesToDelete.remove(link);
        }
        logger.info("Provisioning of compute request completed successfully");
        return provisionRequest;
    }

    protected void doWithResources(Set<String> resourceLinks) throws Throwable {
        validateHostState(resourceLinks,
                com.vmware.photon.controller.model.resources.ComputeService.PowerState.ON);
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
        requestBrokerState.customProperties.put(COMPOSITION_CONTEXT_ID_PROP_NAME,
                compositionContextId);

        if (allocation) {
            requestBrokerState.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                    "true");
            requestBrokerState.customProperties.put(ComputeConstants.CUSTOM_PROP_CLUSTER_SIZE_KEY,
                    "1");
        } else {
            requestBrokerState.operation = ContainerOperationType.CREATE.id;
            requestBrokerState.customProperties.put(CLUSTER_INDEX_PROP_NAME,
                    "0");
        }

        RequestBrokerState request = postDocument(RequestBrokerFactoryService.SELF_LINK,
                requestBrokerState);

        waitForTaskToComplete(request.documentSelfLink);
        return request;
    }

    protected String getResourceType(String resourceDescLink) {
        if (resourceDescLink.startsWith(CompositeDescriptionFactoryService.SELF_LINK)) {
            return ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        } else {
            return ResourceType.COMPUTE_TYPE.getName();
        }
    }

    protected ResourcePoolState createResourcePool(String endpointType,
            EndpointState endpoint, TestDocumentLifeCycle documentLifeCycle) throws Exception {
        return createResourcePool(endpointType, endpoint, getClass().getSimpleName(),
                documentLifeCycle);
    }

    protected ResourcePoolState createResourcePool(String endpointType,
            EndpointState endpoint, String poolId, TestDocumentLifeCycle documentLifeCycle)
            throws Exception {
        String name = name(endpointType, poolId, SUFFIX);
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.documentSelfLink = getExistingLink(ResourcePoolService.FACTORY_LINK, name);
        poolState.name = name;
        poolState.id = poolState.name;
        poolState.projectName = endpointType;
        poolState.tenantLinks = getTenantLinks();
        poolState.maxCpuCount = 1600L;
        poolState.minCpuCount = 16L;
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

    private ResourcePoolState createResourcePoolOfVMs(String endpointType,
            TestDocumentLifeCycle documentLifeCycle) throws Exception {
        return createResourcePool(endpointType, null, "vms-" + getClass().getSimpleName(),
                documentLifeCycle);
    }

    protected void createProfile(ComputeProfile computeProfile, NetworkProfile networkProfile,
            StorageProfile storageProfile) {
        String id = UUID.randomUUID().toString();
        ProfileState profile = new ProfileState();
        profile.name = getProfileName();
        profile.documentSelfLink = UriUtils.buildUriPath(ProfileService.FACTORY_LINK, id);
        profile.endpointLink = endpoint.documentSelfLink;
        profile.tenantLinks = getTenantLinks();

        List<ServiceDocument> docs = new ArrayList<>();
        if (computeProfile != null) {
            profile.computeProfileLink = UriUtils.buildUriPath(ComputeProfileService.FACTORY_LINK,
                    id);
            computeProfile.documentSelfLink = profile.computeProfileLink;
            computeProfile.tenantLinks = profile.tenantLinks;
            docs.add(computeProfile);
        }
        if (networkProfile != null) {
            profile.networkProfileLink = UriUtils.buildUriPath(NetworkProfileService.FACTORY_LINK,
                    id);
            networkProfile.documentSelfLink = profile.networkProfileLink;
            networkProfile.tenantLinks = profile.tenantLinks;
            docs.add(networkProfile);
        }
        if (storageProfile != null) {
            profile.storageProfileLink = UriUtils.buildUriPath(StorageProfileService.FACTORY_LINK,
                    id);
            storageProfile.documentSelfLink = profile.storageProfileLink;
            storageProfile.tenantLinks = profile.tenantLinks;
            docs.add(storageProfile);
        }

        // Post compute, network and storage profiles.
        docs.forEach(d -> {
            try {
                postDocument(UriUtils.getParentPath(d.documentSelfLink), d);
                logger.info("%s created successfully", d.documentSelfLink);
            } catch (Exception e) {
                logger.info("%s failed to create", d.documentSelfLink);
                throw new RuntimeException(e);
            }
        });
        // Now post profile as this invokes update of its child profiles, so that the subsequent
        // patch on the child profiles succeed.
        try {
            postDocument(UriUtils.getParentPath(profile.documentSelfLink), profile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected String getProfileName() {
        return "wordpressEnv";
    }

    protected ComputeProfile loadComputeProfile(String endpointType) {
        URL r = getClass().getClassLoader().getResource(
                "test-" + endpointType.toLowerCase() + "-compute-profile.yaml");

        try (InputStream is = r.openStream()) {
            return YamlMapper.objectMapper().readValue(is, ComputeProfile.class);
        } catch (Exception e) {
            logger.error("Failure reading default environment: %s, reason: %s", r,
                    e.getMessage());
            return null;
        }
    }

    protected String getResourceDescriptionLink() throws Exception {
        return createComputeDescription().documentSelfLink;
    }

    protected String getResourceDescriptionLink(boolean withDisks, String imageId)
            throws Exception {
        return createComputeDescription(withDisks, imageId).documentSelfLink;
    }

    protected ComputeDescription createComputeDescription()
            throws Exception {
        return createComputeDescription(false, "coreos");
    }

    protected ComputeDescription createComputeDescription(boolean withDisks, String imageId)
            throws Exception {
        if (imageId == null) {
            imageId = "coreos";
        }
        ComputeDescription computeDesc = prepareComputeDescription(imageId);
        if (withDisks) {
            computeDesc.diskDescLinks = createDiskStates();
        }

        ComputeDescription computeDescription = postDocument(ComputeDescriptionService.FACTORY_LINK,
                computeDesc, documentLifeCycle);

        return computeDescription;
    }

    protected List<String> createDiskStates() throws Exception {
        return Collections.emptyList();
    }

    protected ComputeDescription prepareComputeDescription(String imageId) throws Exception {
        String id = name(getEndpointType(), "test", UUID.randomUUID().toString());
        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.id = id;
        computeDesc.name = nextName("mcp");
        computeDesc.instanceType = "small";
        computeDesc.tenantLinks = getTenantLinks();
        computeDesc.customProperties = new HashMap<>();
        computeDesc.customProperties.put(ComputeProperties.CUSTOM_DISPLAY_NAME, computeDesc.name);
        computeDesc.customProperties
                .put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, imageId);

        computeDesc.customProperties.put(
                ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_RESOURCE_POOL_LINK,
                vmsResourcePool.documentSelfLink);

        extendComputeDescription(computeDesc);
        return computeDesc;
    }

    protected GroupResourcePlacementState createResourcePlacement(String name, String endpointType,
            String resourcePoolLink, TestDocumentLifeCycle documentLifeCycle)
            throws Exception {
        GroupResourcePlacementState placementState = new GroupResourcePlacementState();
        placementState.resourceType = ResourceType.COMPUTE_TYPE.getName();
        placementState.maxNumberInstances = 30;
        placementState.resourcePoolLink = resourcePoolLink;
        placementState.name = name(endpointType, name, SUFFIX);
        placementState.documentSelfLink = getExistingLink(
                GroupResourcePlacementService.FACTORY_LINK, placementState.name);
        placementState.availableInstancesCount = 1000000;
        placementState.priority = 1;
        placementState.tenantLinks = getTenantLinks();

        GroupResourcePlacementState groupResourcePlacementState = postDocument(
                GroupResourcePlacementService.FACTORY_LINK, placementState, documentLifeCycle);

        assertNotNull(groupResourcePlacementState);

        return groupResourcePlacementState;
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

    protected void validateHostState(Collection<String> resourceLinks,
            com.vmware.photon.controller.model.resources.ComputeService.PowerState powerState)
            throws Exception {
        String computeStateLink = resourceLinks.iterator().next();
        ComputeState computeState = getDocument(computeStateLink, ComputeState.class);

        assertNotNull(computeState);
        assertEquals(powerState, computeState.powerState);
        validateDisks(computeState.diskLinks);
    }

    protected void validateDisks(List<String> diskLinks) throws Exception {
        // The actual validations are added in the Disk related tests
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

        containerDesc.customProperties = new HashMap<>();

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

    protected String importTemplate(String filePath) throws Exception {
        String template = CommonTestStateFactory.getFileContent(filePath);

        URI uri = URI.create(getBaseUrl()
                + buildServiceUri(CompositeDescriptionContentService.SELF_LINK));

        Map<String, String> headers = Collections
                .singletonMap(Operation.CONTENT_TYPE_HEADER,
                        UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML);

        SimpleHttpsClient.HttpResponse httpResponse = SimpleHttpsClient
                .execute(SimpleHttpsClient.HttpMethod.POST, uri.toString(), template, headers,
                        null);
        String location = httpResponse.headers.get(Operation.LOCATION_HEADER).get(0);
        assertNotNull("Missing location header", location);
        String compositeDescriptionLink = URI.create(location).getPath();
        CompositeDescription description = getDocument(compositeDescriptionLink,
                CompositeDescription.class);
        description.tenantLinks = getTenantLinks();
        patchDocument(description);

        for (String descriptionLink : description.descriptionLinks) {
            ResourceState state;
            if (descriptionLink.startsWith(ContainerNetworkDescriptionService.FACTORY_LINK)) {
                state = new ContainerNetworkDescription();
            } else if (descriptionLink.startsWith(ContainerVolumeDescriptionService.FACTORY_LINK)) {
                state = new ContainerVolumeDescription();
            } else if (descriptionLink.startsWith(ComputeNetworkDescriptionService.FACTORY_LINK)) {
                state = new ComputeNetworkDescription();
            } else if (descriptionLink.startsWith(LoadBalancerDescriptionService.FACTORY_LINK)) {
                state = new LoadBalancerDescriptionService.LoadBalancerDescription();
            } else if (descriptionLink.startsWith(ComputeDescriptionService.FACTORY_LINK)) {
                state = new ComputeDescription();
            } else if (descriptionLink.startsWith(ContainerDescriptionService.FACTORY_LINK)) {
                state = new ContainerDescription();
            } else if (descriptionLink.startsWith(ClosureDescriptionFactoryService.FACTORY_LINK)) {
                state = new ClosureDescription();
            } else {
                throw new IllegalStateException("Unknown link found:" + descriptionLink);
            }
            state.documentSelfLink = descriptionLink;
            state.tenantLinks = getTenantLinks();
            patchDocument(state);
        }

        return compositeDescriptionLink;
    }

    protected NetworkProfile createNetworkProfile(String subnetName, String securityGroupName,
            Set<String> tagLinks) throws Exception {

        String subnetLink = loadResource(SubnetState.class, subnetName);
        NetworkProfile np = new NetworkProfile();
        np.subnetLinks = Collections.singletonList(subnetLink);
        if (securityGroupName != null) {
            np.securityGroupLinks = Collections.singletonList(
                    loadResource(SecurityGroupState.class, securityGroupName));
        }
        np.tagLinks = tagLinks;
        return np;
    }

    protected NetworkProfile createIsolatedSubnetNetworkProfile(String isolatedNetworkName, int cidrPrefix) throws Exception {
        return createIsolatedSubnetWithOutboundAccessNetworkProfile(isolatedNetworkName, cidrPrefix, null);
    }

    protected NetworkProfile createIsolatedSubnetWithOutboundAccessNetworkProfile(
            String isolatedNetworkName, int cidrPrefix,
            String isolationExternalSubnetName) throws Exception {
        String networkLink = loadResource(NetworkState.class, isolatedNetworkName);

        NetworkProfile np = new NetworkProfile();
        np.subnetLinks = new ArrayList<>();
        np.isolationType = IsolationSupportType.SUBNET;
        np.isolationNetworkLink = networkLink;
        np.isolatedSubnetCIDRPrefix = cidrPrefix;
        if (isolationExternalSubnetName != null) {
            String subnetLink = loadResource(SubnetState.class, isolationExternalSubnetName);
            np.isolationExternalSubnetLink = subnetLink;
        }
        return np;
    }

    protected String loadResource(Class<? extends ServiceDocument> resourceClass, String name)
            throws Exception {
        QueryTask.Query query = QueryTask.Query.Builder.create()
                .addKindFieldClause(resourceClass)
                .addCaseInsensitiveFieldClause(ResourceState.FIELD_NAME_NAME, name,
                        QueryTask.QueryTerm.MatchType.TERM, QueryTask.Query.Occurance.MUST_OCCUR)
                .addFieldClause(PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK,
                        endpoint.documentSelfLink)
                .addClause(QueryUtil.addTenantAndGroupClause(getTenantLinks()))
                .build();
        QueryTask qt = QueryTask.Builder.createDirectTask().setQuery(query).build();
        String responseJson = sendRequest(SimpleHttpsClient.HttpMethod.POST,
                ServiceUriPaths.CORE_QUERY_TASKS,
                Utils.toJson(qt));
        QueryTask result = Utils.fromJson(responseJson, QueryTask.class);

        if (result.results.documentLinks.size() == 0) {
            throw new IllegalArgumentException(String.format("Cannot find %s '%s'",
                    resourceClass.getSimpleName(), name));
        } else if (result.results.documentLinks.size() > 1) {
            logger.warning("There are more then one %s with name '%s'",
                    resourceClass.getSimpleName(), name);
        }

        return result.results.documentLinks.get(0);
    }

    protected NetworkProfile createIsolatedSecurityGroupNetworkProfile(String subnetName,
            Set<String> tagLinks) throws Exception {
        QueryTask.Query query = QueryTask.Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addCaseInsensitiveFieldClause(SubnetState.FIELD_NAME_NAME, subnetName,
                        QueryTask.QueryTerm.MatchType.TERM, QueryTask.Query.Occurance.MUST_OCCUR)
                .addFieldClause(SubnetState.FIELD_NAME_ENDPOINT_LINK, endpoint.documentSelfLink)
                .addClause(QueryUtil.addTenantAndGroupClause(getTenantLinks()))
                .build();
        QueryTask qt = QueryTask.Builder.createDirectTask().setQuery(query).build();
        String responseJson = sendRequest(SimpleHttpsClient.HttpMethod.POST,
                ServiceUriPaths.CORE_QUERY_TASKS,
                Utils.toJson(qt));
        QueryTask result = Utils.fromJson(responseJson, QueryTask.class);

        String subnetLink = result.results.documentLinks.get(0);
        NetworkProfile np = new NetworkProfile();
        np.subnetLinks = Collections.singletonList(subnetLink);
        np.isolationType = IsolationSupportType.SECURITY_GROUP;
        np.tagLinks = tagLinks;
        return np;
    }

    protected String createTag(String key, String value) throws Exception {
        TagState tag = new TagState();
        tag.key = key;
        tag.value = value;
        tag.tenantLinks = getTenantLinks();
        return postDocument(TagService.FACTORY_LINK, tag).documentSelfLink;
    }

    protected void doDay2Operation(Set<String> resourceLinks, String operation, String payLoad)
            throws Throwable {
        RequestBrokerState d2oRequest = new RequestBrokerState();
        d2oRequest.resourceType = ResourceType.COMPUTE_TYPE.getName();
        d2oRequest.resourceLinks = resourceLinks;
        d2oRequest.operation = operation;
        d2oRequest.customProperties = new HashMap<>();
        d2oRequest.customProperties.put(OPERATION_PAYLOAD_PROP_NAME, payLoad);
        d2oRequest.tenantLinks = getTenantLinks();
        d2oRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, d2oRequest);

        waitForTaskToComplete(d2oRequest.documentSelfLink);
    }

    protected Set<String> testScaleOperations(String resourceDescriptionLink) throws Throwable {
        // First scale out from 1 to 3
        // scale out is two phase - allocate and provision
        RequestBrokerState d2oRequest = new RequestBrokerState();
        d2oRequest.resourceType = ResourceType.COMPUTE_TYPE.getName();
        d2oRequest.resourceDescriptionLink = resourceDescriptionLink;
        d2oRequest.operation = RequestBrokerState.CLUSTER_RESOURCE_OPERATION;
        int newResourceCount = 3;
        d2oRequest.resourceCount = newResourceCount;
        d2oRequest.customProperties = new HashMap<>();
        d2oRequest.customProperties.put("__allocation_request", "true");
        d2oRequest.customProperties.put(COMPOSITION_CONTEXT_ID_PROP_NAME, compositionContextId);
        d2oRequest.tenantLinks = getTenantLinks();
        d2oRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, d2oRequest);
        waitForTaskToComplete(d2oRequest.documentSelfLink);

        RequestBrokerState d2oResponse = getDocument(d2oRequest.documentSelfLink,
                RequestBrokerState.class);
        assertNotNull(d2oResponse.resourceLinks);
        assertEquals(newResourceCount, d2oResponse.resourceLinks.size());
        for (String link : d2oResponse.resourceLinks) {
            ComputeState computeState = getDocument(link, ComputeState.class);
            assertNotNull(computeState);
            computesToDelete.put(link, computeState);
        }

        List<String> resourceLinks = new ArrayList<>(d2oResponse.resourceLinks.size());
        resourceLinks.addAll(d2oResponse.resourceLinks);

        Integer clusterIndex = 1;
        RequestBrokerState d2oRequest2 = new RequestBrokerState();
        d2oRequest2.resourceType = ResourceType.COMPUTE_TYPE.getName();
        d2oRequest2.resourceDescriptionLink = resourceDescriptionLink;
        d2oRequest2.operation = ContainerOperationType.CREATE.id;
        d2oRequest2.resourceCount = 0;
        d2oRequest2.customProperties = new HashMap<>();
        d2oRequest2.customProperties.put(CLUSTER_INDEX_PROP_NAME, clusterIndex.toString());
        d2oRequest2.customProperties.put(COMPOSITION_CONTEXT_ID_PROP_NAME, compositionContextId);
        d2oRequest2.tenantLinks = getTenantLinks();
        d2oRequest2.resourceLinks = new HashSet<String>(1);
        d2oRequest2.resourceLinks.add(resourceLinks.get(clusterIndex));
        d2oRequest2 = postDocument(RequestBrokerFactoryService.SELF_LINK, d2oRequest2);

        clusterIndex = 2;
        RequestBrokerState d2oRequest3 = new RequestBrokerState();
        d2oRequest3.resourceType = ResourceType.COMPUTE_TYPE.getName();
        d2oRequest3.resourceDescriptionLink = resourceDescriptionLink;
        d2oRequest3.operation = ContainerOperationType.CREATE.id;
        d2oRequest3.resourceCount = 0;
        d2oRequest3.customProperties = new HashMap<>();
        d2oRequest3.customProperties.put(CLUSTER_INDEX_PROP_NAME, clusterIndex.toString());
        d2oRequest3.customProperties.put(COMPOSITION_CONTEXT_ID_PROP_NAME, compositionContextId);
        d2oRequest3.tenantLinks = getTenantLinks();
        d2oRequest3.resourceLinks = new HashSet<String>(1);
        d2oRequest3.resourceLinks.add(resourceLinks.get(clusterIndex));
        d2oRequest3 = postDocument(RequestBrokerFactoryService.SELF_LINK, d2oRequest3);

        waitForTaskToComplete(d2oRequest2.documentSelfLink);
        waitForTaskToComplete(d2oRequest3.documentSelfLink);

        List<String> actualComputeStateLinks = queryComputeByDescriptionLink(
                d2oRequest3.resourceDescriptionLink);
        assertEquals(newResourceCount, actualComputeStateLinks.size());

        // Scale-in is one step delete operation, LIFO seq
        // based on cluster index last resource is selected by composition for deletion
        clusterIndex = 2;
        newResourceCount = 2;
        RequestBrokerState d2oRequest4 = new RequestBrokerState();
        d2oRequest4.resourceType = ResourceType.COMPUTE_TYPE.getName();
        d2oRequest4.resourceDescriptionLink = resourceDescriptionLink;
        d2oRequest4.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
        d2oRequest4.resourceCount = 0;
        d2oRequest4.customProperties = new HashMap<>();
        d2oRequest4.customProperties.put(CLUSTER_INDEX_PROP_NAME, clusterIndex.toString());
        d2oRequest4.tenantLinks = getTenantLinks();
        d2oRequest4.resourceLinks = new HashSet<String>(1);
        d2oRequest4.resourceLinks.add(resourceLinks.get(clusterIndex));
        d2oRequest4 = postDocument(RequestBrokerFactoryService.SELF_LINK, d2oRequest4);

        waitForTaskToComplete(d2oRequest4.documentSelfLink);

        actualComputeStateLinks = queryComputeByDescriptionLink(
                d2oRequest4.resourceDescriptionLink);
        assertEquals(newResourceCount, actualComputeStateLinks.size());

        for (String link : d2oResponse.resourceLinks) {
            computesToDelete.remove(link);
        }
        return new HashSet<String>(actualComputeStateLinks);
    }

    protected List<String> queryComputeByDescriptionLink(
            String descriptionLink) throws Exception {
        Query.Builder queryBuilder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_DESCRIPTION_LINK,
                        descriptionLink);

        QueryTask qt = QueryTask.Builder.createDirectTask().setQuery(queryBuilder.build()).build();
        String responseJson = sendRequest(SimpleHttpsClient.HttpMethod.POST,
                ServiceUriPaths.CORE_QUERY_TASKS,
                Utils.toJson(qt));
        QueryTask result = Utils.fromJson(responseJson, QueryTask.class);
        assertNotNull(result.results);
        assertNotNull(result.results.documentCount);
        return result.results.documentLinks;
    }

}
