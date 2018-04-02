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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;
import static com.vmware.admiral.test.integration.TestPropertiesUtil.getSystemOrTestProp;
import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;
import static com.vmware.admiral.test.integration.data.IntegratonTestStateFactory.CONTAINER_ADMIRAL_IMAGE;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.BeforeClass;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.adapter.common.VolumeOperationType;
import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.PlacementZoneUtil;
import com.vmware.admiral.compute.RegistryHostConfigService;
import com.vmware.admiral.compute.RegistryHostConfigService.RegistryHostSpec;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.image.service.ContainerImageService;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState.SubStage;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.admiral.test.integration.data.IntegratonTestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Base class for test that provision a container
 */
public abstract class BaseProvisioningOnCoreOsIT extends BaseIntegrationSupportIT {
    protected static ServiceClient serviceClient;

    protected static final List<String> TENANT_LINKS = Collections
            .singletonList("/tenants/docker-test");
    private static final List<String> OTHER_TENANT_LINKS = Collections
            .singletonList("/tenants/other-docker-test");
    private static final String TEST_REGISTRY_NAME = "test-registry";
    private static final long DEFAULT_OPERATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final int DOCKER_MAX_BRIDGE_NETWORKS_COUNT = 29;
    private static final String BRIDGE_NETWORK_TYPE_QUERY = "?$filter=driver eq 'bridge'";
    private static final String DEFAULT_REGISTRY_ADDRESS = "https://registry.hub.docker.com";

    protected String dockerHostAddress1;
    protected String dockerHostAddress2;
    protected String hostPort;
    protected String getDockerHostAddressWithPort1;
    protected String getDockerHostAddressWithPort2;
    protected String projectLink;
    protected String placementZoneName;
    protected ContainerHostSpec hostSpec;

    protected ComputeState dockerHostCompute;
    protected List<ComputeState> dockerHostsInCluster;

    private AuthCredentialsServiceState dockerHostAuthCredentials;
    private SslTrustCertificateState dockerHostSslTrust;

    protected final Set<String> containersToDelete = new HashSet<>();
    protected final Set<String> externalNetworksToDelete = new HashSet<>();

    protected String registryAddress;

    public static enum RegistryType {
        V1_HTTP_INSECURE, V1_SSL_SECURE, V2_SSL_SECURE, V2_BASIC_AUTH
    }

    @BeforeClass
    public static void init() throws Exception {
        try {
            SslTrustCertificateState trustCertificateState = IntegratonTestStateFactory
                    .createSslTrustCertificateState(
                            getTestRequiredProp("cert.ci.trust.file"), "CI-trust-cert");
            postDocument(SslTrustCertificateService.FACTORY_LINK, trustCertificateState);
        } catch (Exception e) {
            Utils.logWarning("Cannot create trust certificate using property"
                    + " cert.ci.trust.file=%s. Error: %s",
                    getTestRequiredProp("cert.ci.trust.file"), e.getMessage());
        }
    }

    @After
    public void provisioningTearDown() throws Exception {
        // remove remaining containers created in the current test run if they are still found
        Iterator<String> it = containersToDelete.iterator();
        while (it.hasNext()) {
            String containerLink = it.next();
            ContainerState containerState = getDocument(containerLink, ContainerState.class);
            if (containerState == null) {
                logger.warning(String.format("Unable to find container %s. Skipping.", containerLink));
                it.remove();
            }
        }

        if (containersToDelete != null && !containersToDelete.isEmpty()) {
            try {
                logger.info("---------- Clean up: Request Delete the container instances. --------");
                requestContainerDelete(containersToDelete, false);
            } catch (Throwable t) {
                logger.warning(String.format("Unable to remove containers %s: %s", containersToDelete,
                        t.getMessage()));
            }
        }

        // remove the external networks, if any
        it = externalNetworksToDelete.iterator();
        while (it.hasNext()) {
            String networkLink = it.next();

            ContainerNetworkState network = getDocument(networkLink, ContainerNetworkState.class);
            assertNotNull(String.format("Unable to find network %s", networkLink), network);
            assertTrue(network.connectedContainersCount == 0);

            try {
                logger.info("---------- Clean up: Request Delete the network instance. --------");
                requestExternalNetworkDelete(networkLink);
            } catch (Throwable t) {
                logger.warning(String.format("Unable to remove network %s: %s", networkLink,
                        t.getMessage()));
            }
        }

        // remove the host
        if (dockerHostCompute != null) {
            removeHost(dockerHostCompute);
        }

        if (dockerHostsInCluster != null) {
            for (ComputeState dockerHost : dockerHostsInCluster) {
                removeHost(dockerHost);
            }
        }
    }

    protected void doProvisionDockerContainerOnCoreOS(boolean downloadImage,
            DockerAdapterType adapterType) throws Exception {
        doProvisionDockerContainerOnCoreOS(downloadImage, adapterType, false);
    }

    protected void doProvisionDockerContainerOnCoreOS(boolean downloadImage,
            DockerAdapterType adapterType, boolean setupOnCluster) throws Exception {
        doProvisionDockerContainerOnCoreOS(downloadImage, adapterType, setupOnCluster, 0);
    }

    protected void doProvisionDockerContainerOnCoreOS(boolean downloadImage,
            DockerAdapterType adapterType, boolean setupOnCluster, int networksCount) throws Exception {
        setupCoreOsHost(adapterType, setupOnCluster, null);
        if (networksCount > 0) {
            checkNumberOfNetworks(serviceClient, networksCount);
        }

        logger.info("---------- 5. Create test docker image container description. --------");
        requestContainerAndDelete(getResourceDescriptionLink(downloadImage,
                RegistryType.V1_SSL_SECURE));
    }

    protected void doProvisionDockerContainerOnCoreOS(boolean downloadImage,
            DockerAdapterType adapterType, RegistryType registryType) throws Exception {
        setupCoreOsHost(adapterType);

        logger.info("---------- 5. Create test docker image container description. --------");
        requestContainerAndDelete(getResourceDescriptionLink(downloadImage, registryType));
    }

    protected void setupCoreOsHost(DockerAdapterType adapterType) throws Exception {
        setupCoreOsHost(adapterType, false, null);
    }

    protected void setupCoreOsHost(DockerAdapterType adapterType, boolean setupOnCluster,
            String resourcePoolName)
            throws Exception {
        logger.info("********************************************************************");
        logger.info("----------  Setup: Add CoreOS VM as DockerHost ComputeState --------");
        logger.info("********************************************************************");
        logger.info("---------- 1. Create a Docker Host Container Description. --------");

        logger.info(
                "---------- 2. Setup auth credentials for the CoreOS VM (Container Host). --------");
        dockerHostAuthCredentials = IntegratonTestStateFactory.createAuthCredentials(true);
        dockerHostAuthCredentials.type = AuthCredentialsType.PublicKey.name();

        switch (adapterType) {
        case API:
            dockerHostAuthCredentials.privateKey = IntegratonTestStateFactory
                    .getFileContent(getTestRequiredProp("docker.client.key.file"));
            dockerHostAuthCredentials.publicKey = IntegratonTestStateFactory
                    .getFileContent(getTestRequiredProp("docker.client.cert.file"));
            break;

        default:
            throw new IllegalArgumentException("Unexpected adapter type: " + adapterType);
        }

        if (resourcePoolName == null) {
            resourcePoolName = UUID.randomUUID().toString();
        }

        dockerHostAuthCredentials = postDocument(AuthCredentialsService.FACTORY_LINK,
                dockerHostAuthCredentials);
        ResourcePoolState resourcePoolState = postDocument(ResourcePoolService.FACTORY_LINK,
                GroupResourcePlacementService.buildResourcePool(resourcePoolName),
                TestDocumentLifeCycle.FOR_DELETE_AFTER_CLASS);
        postDocument(GroupResourcePlacementService.FACTORY_LINK,
                GroupResourcePlacementService.buildStateInstance(Service.getId(resourcePoolState
                        .documentSelfLink), resourcePoolState.documentSelfLink),
                TestDocumentLifeCycle.FOR_DELETE_AFTER_CLASS);

        assertNotNull("Failed to create host credentials", dockerHostAuthCredentials);

        String hostPort = getTestRequiredProp("docker.host.port." + adapterType.name());
        String credLink = dockerHostAuthCredentials.documentSelfLink;

        if (!setupOnCluster) {
            logger.info("---------- 3. Create Docker Host ComputeState for CoreOS VM. --------");
            dockerHostCompute = createDockerHost(getTestRequiredProp("docker.host.address"),
                    hostPort, credLink, adapterType, null, resourcePoolState.documentSelfLink);
        } else {
            dockerHostsInCluster = new ArrayList<>();
            logger.info(
                    "---------- 3. Create Docker Hosts ComputeState Node 1 and Node 2 of cluster for CoreOS VM. --------");
            String node1Address = getTestRequiredProp("docker.host.cluster.node1.address");
            dockerHostsInCluster.add(createDockerHost(node1Address,
                    hostPort, credLink, adapterType, UriUtilsExtended.extractHost(node1Address),
                    resourcePoolState.documentSelfLink));

            String node2Address = getTestRequiredProp("docker.host.cluster.node2.address");
            dockerHostsInCluster.add(createDockerHost(node2Address,
                    hostPort, credLink, adapterType, UriUtilsExtended.extractHost(node2Address),
                    resourcePoolState.documentSelfLink));
        }

        logger.info("---------- 4. Add the Docker Host SSL Trust Certificate. --------");
        dockerHostSslTrust = IntegratonTestStateFactory.createSslTrustCertificateState(
                getTestRequiredProp("docker.host.ssl.trust.file"),
                CommonTestStateFactory.REGISTRATION_DOCKER_ID);

        postDocument(SslTrustCertificateService.FACTORY_LINK, dockerHostSslTrust);
    }

    protected void setupEnvironmentForCluster()
            throws Throwable {
        dockerHostAddress1 = getTestRequiredProp("docker.host.cluster.node1.address");
        dockerHostAddress2 = getSystemOrTestProp("docker.host.cluster.node2.address");
        hostPort = getTestRequiredProp("docker.host.port." + DockerAdapterType.API.name());
        getDockerHostAddressWithPort1 = "https://" + dockerHostAddress1 + ":" + hostPort;
        if (dockerHostAddress2 != null) {
            getDockerHostAddressWithPort2 = "https://" + dockerHostAddress2 + ":" + hostPort;
        }

        dockerHostAuthCredentials = IntegratonTestStateFactory.createAuthCredentials(true);
        dockerHostAuthCredentials.type = AuthCredentialsType.PublicKey.name();

        dockerHostAuthCredentials.privateKey = IntegratonTestStateFactory
                .getFileContent(getTestRequiredProp("docker.client.key.file"));
        dockerHostAuthCredentials.publicKey = IntegratonTestStateFactory
                .getFileContent(getTestRequiredProp("docker.client.cert.file"));

        dockerHostAuthCredentials = postDocument(AuthCredentialsService.FACTORY_LINK,
                dockerHostAuthCredentials);

        assertNotNull("Failed to create host credentials", dockerHostAuthCredentials);

        dockerHostSslTrust = IntegratonTestStateFactory.createSslTrustCertificateState(
                getTestRequiredProp("docker.host.ssl.trust.file"),
                CommonTestStateFactory.REGISTRATION_DOCKER_ID);

        dockerHostSslTrust = postDocument(SslTrustCertificateService.FACTORY_LINK, dockerHostSslTrust);

        projectLink = buildProjectLink(ProjectService.DEFAULT_PROJECT_ID);
        placementZoneName = PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.DOCKER,
                        getDockerHostAddressWithPort1);
        hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.DOCKER, true);
    }

    protected ContainerHostSpec createContainerHostSpec(List<String> tenantLinks,
            ContainerHostType hostType, boolean acceptCertificate)
            throws Throwable {
        ContainerHostSpec ch = new ContainerHostSpec();
        ch.acceptCertificate = acceptCertificate;
        ch.hostState = createComputeState(hostType, tenantLinks);
        return ch;
    }

    private ComputeState createComputeState(ContainerHostType hostType,
            List<String> tenantLinks) throws Throwable {
        ComputeState cs = new ComputeState();
        cs.id = UUID.randomUUID().toString();
        cs.address = getDockerHostAddressWithPort1;
        // cs.powerState = hostState;
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        // This property is used for checking if the host is VCH.
        // Forcing the __Driver prop with some value, in order to install the system container.
        cs.customProperties.put(ContainerHostUtil.PROPERTY_NAME_DRIVER,
                "overlay");
        cs.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                hostType.toString());
        cs.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                dockerHostAuthCredentials.documentSelfLink);
        cs.customProperties.put(MockDockerHostAdapterService.CONTAINER_HOST_TYPE_PROP_NAME,
                hostType.toString());
        cs.tenantLinks = new ArrayList<>(tenantLinks);

        return cs;
    }

    protected ClusterDto createCluster() throws Exception {
        String dtoRaw = sendRequest(HttpMethod.POST, ClusterService.SELF_LINK, Utils.toJson
                (hostSpec));
        ClusterDto dto = Utils.fromJson(dtoRaw, ClusterDto.class);
        cleanUpAfter(dto);
        return dto;
    }

    private String buildProjectLink(String projectId) {
        return UriUtils.buildUriPath(ManagementUriParts.PROJECTS, projectId);
    }

    protected ComputeState getDockerHost() {
        return (dockerHostCompute != null) ? dockerHostCompute : dockerHostsInCluster.get(0);
    }

    protected abstract String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType)
            throws Exception;

    protected String getImageName(RegistryType registryType) throws Exception {
        String registry = getRegistryHostname(registryType);
        configureRegistries(registry, registryType);

        // to make it interesting, drop the first letter of the image name in the query
        String queryStr = CONTAINER_ADMIRAL_IMAGE.substring(1);

        String imageSearchResult = searchForImage(queryStr);

        return imageSearchResult;
    }

    protected String getRegistryHostname(RegistryType registryType) {
        switch (registryType) {

        case V1_HTTP_INSECURE:
            return getTestRequiredProp("docker.insecure.registry.host.address");

        case V1_SSL_SECURE:
            return getTestRequiredProp("docker.registry.host.address");

        case V2_SSL_SECURE:
            return getTestRequiredProp("docker.v2.registry.host.address");

        case V2_BASIC_AUTH:
            return getTestRequiredProp("docker.secure.v2.registry.host.address");

        default:
            throw new IllegalArgumentException(
                    String.format("Unsupported registry type '%s'", registryType));
        }
    }

    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {
        String containerStateLink = request.resourceLinks.iterator().next();
        ContainerState containerState = getDocument(containerStateLink, ContainerState.class);

        assertNotNull(containerState);
        assertEquals(PowerState.RUNNING, containerState.powerState);
        assertEquals(resourceDescLink, containerState.descriptionLink);
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

    protected RequestBrokerState requestContainer(String resourceDescLink) throws Exception {

        RequestBrokerState request = new RequestBrokerState();
        if (resourceDescLink.startsWith(CompositeDescriptionFactoryService.SELF_LINK)) {
            request.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        } else {
            ComponentMeta meta = CompositeComponentRegistry
                    .metaByDescriptionLink(resourceDescLink);
            request.resourceType = meta.resourceType;
        }
        request.resourceDescriptionLink = resourceDescLink;
        request.tenantLinks = TENANT_LINKS;
        request = postDocument(RequestBrokerFactoryService.SELF_LINK, request);

        waitForTaskToComplete(request.documentSelfLink);

        request = getDocument(request.documentSelfLink, RequestBrokerState.class);
        for (String containerLink : request.resourceLinks) {
            containersToDelete.add(containerLink);
        }

        return request;
    }

    protected RequestBrokerState requestVolume(String resourceDescLink) throws Exception {

        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.VOLUME_TYPE.getName();
        request.resourceDescriptionLink = resourceDescLink;
        request.tenantLinks = TENANT_LINKS;
        request = postDocument(RequestBrokerFactoryService.SELF_LINK, request);

        waitForTaskToComplete(request.documentSelfLink);

        request = getDocument(request.documentSelfLink, RequestBrokerState.class);
        for (String volumeLink : request.resourceLinks) {
            containersToDelete.add(volumeLink);
        }

        return request;
    }

    protected void requestVolumeDelete(Set<String> resourceLinks, boolean verifyDelete)
            throws Exception {

        RequestBrokerState day2DeleteRequest = new RequestBrokerState();
        day2DeleteRequest.resourceType = ResourceType.VOLUME_TYPE.getName();
        day2DeleteRequest.operation = VolumeOperationType.DELETE.id;
        day2DeleteRequest.resourceLinks = resourceLinks;
        day2DeleteRequest.tenantLinks = TENANT_LINKS;
        day2DeleteRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, day2DeleteRequest,
                TestDocumentLifeCycle.NO_DELETE);

        waitForTaskToComplete(day2DeleteRequest.documentSelfLink);

        day2DeleteRequest = getDocument(day2DeleteRequest.documentSelfLink, RequestBrokerState.class);

        assertEquals(SubStage.COMPLETED, day2DeleteRequest.taskSubStage);
        assertEquals(TaskStage.FINISHED, day2DeleteRequest.taskInfo.stage);

        if (!verifyDelete) {
            return;
        }

        for (String containerLink : resourceLinks) {
            ContainerVolumeState conState = getDocument(containerLink, ContainerVolumeState.class);
            assertNull(conState);
            String computeStateLink = UriUtils
                    .buildUriPath(ComputeService.FACTORY_LINK, extractId(containerLink));
            ComputeState computeState = getDocument(computeStateLink, ComputeState.class);
            assertNull(computeState);
            logger.info("[requestContainerDelete] Deleting container: %s", containerLink);
            containersToDelete.remove(containerLink);
        }
    }

    protected void requestContainerDelete(Set<String> resourceLinks, boolean verifyDelete)
            throws Exception {

        RequestBrokerState day2DeleteRequest = new RequestBrokerState();
        String resourceLink = resourceLinks.iterator().next();
        if (resourceLink.startsWith(CompositeComponentFactoryService.SELF_LINK)) {
            day2DeleteRequest.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        } else {
            ComponentMeta metaByStateLink = CompositeComponentRegistry
                    .metaByStateLink(resourceLink);
            day2DeleteRequest.resourceType = metaByStateLink.resourceType;
        }
        day2DeleteRequest.operation = ContainerOperationType.DELETE.id;
        day2DeleteRequest.resourceLinks = resourceLinks;
        day2DeleteRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, day2DeleteRequest,
                TestDocumentLifeCycle.NO_DELETE);

        waitForTaskToComplete(day2DeleteRequest.documentSelfLink);

        day2DeleteRequest = getDocument(day2DeleteRequest.documentSelfLink, RequestBrokerState.class);

        assertEquals(SubStage.COMPLETED, day2DeleteRequest.taskSubStage);
        assertEquals(TaskStage.FINISHED, day2DeleteRequest.taskInfo.stage);

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
            logger.info("[requestContainerDelete] Deleting container: %s", containerLink);
            containersToDelete.remove(containerLink);
        }
    }

    protected RequestBrokerState requestExternalNetwork(String networkDescLink) throws Exception {

        RequestBrokerState request = new RequestBrokerState();
        ComponentMeta meta = CompositeComponentRegistry.metaByDescriptionLink(networkDescLink);

        request.resourceType = meta.resourceType;
        request.resourceDescriptionLink = networkDescLink;
        request.tenantLinks = TENANT_LINKS;
        request = postDocument(RequestBrokerFactoryService.SELF_LINK, request);

        waitForTaskToComplete(request.documentSelfLink);

        request = getDocument(request.documentSelfLink, RequestBrokerState.class);
        for (String networkLink : request.resourceLinks) {
            externalNetworksToDelete.add(networkLink);
        }

        return request;
    }

    protected void requestExternalNetworkDelete(String resourceLink) throws Exception {

        RequestBrokerState day2DeleteRequest = new RequestBrokerState();
        ComponentMeta metaByStateLink = CompositeComponentRegistry.metaByStateLink(resourceLink);

        day2DeleteRequest.resourceType = metaByStateLink.resourceType;
        day2DeleteRequest.operation = NetworkOperationType.DELETE.id;
        day2DeleteRequest.resourceLinks = new HashSet<>(Arrays.asList(resourceLink));
        day2DeleteRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, day2DeleteRequest);

        waitForTaskToComplete(day2DeleteRequest.documentSelfLink);
    }

    /**
     * Perform an image search on the given registry URI and the given query string
     *
     * @param query
     * @return
     * @throws Exception
     */
    private String searchForImage(String query) throws Exception {
        // search in different group - no results expected;
        RegistrySearchResponse globalSearchResponse = searchForImages(query, OTHER_TENANT_LINKS);
        // if there are any search results, they should come only from the default registry
        if (globalSearchResponse.numResults > 0) {
            globalSearchResponse.results
                    .forEach((r) -> assertEquals("expected results only from the default registry",
                            DEFAULT_REGISTRY_ADDRESS, r.registry));
        }

        // search in group - expect one result + the results from the default (global) registry
        RegistrySearchResponse registrySearchResponse = searchForImages(query, TENANT_LINKS);

        // we may have got similar results, so filter them out
        String expectedResult = UriUtilsExtended.extractHostAndPort(registryAddress) + "/"
                + CONTAINER_ADMIRAL_IMAGE;
        List<String> imageNames = registrySearchResponse.results.stream()
                .filter((r) -> registryAddress.equals(r.registry))
                .map((r) -> r.name)
                .filter(expectedResult::equals)
                .collect(Collectors.toList());

        assertEquals("Expected only 1 result from tenanted registy for " + expectedResult, 1,
                imageNames.size());

        return imageNames.get(0);
    }

    private RegistrySearchResponse searchForImages(String query, List<String> tenantLinks)
            throws Exception {
        URI searchUri = URI.create(getBaseUrl() + buildServiceUri(ManagementUriParts.IMAGES));
        if (tenantLinks == null) {
            tenantLinks = Collections.singletonList("");
        }
        searchUri = UriUtils.extendUriWithQuery(searchUri,
                RegistryAdapterService.SEARCH_QUERY_PROP_NAME, query,
                ContainerImageService.TENANT_LINKS_PARAM_NAME, tenantLinks.get(0));

        HttpResponse response = SimpleHttpsClient.execute(HttpMethod.GET, searchUri.toString());
        assertEquals("Unexpected response code", HttpURLConnection.HTTP_OK, response.statusCode);
        assertNotNull("response body is null", response.responseBody);
        RegistrySearchResponse registrySearchResponse = Utils.fromJson(response.responseBody,
                RegistrySearchResponse.class);

        assertNotNull("search result is null", registrySearchResponse);

        return registrySearchResponse;
    }

    protected void configureRegistries(String registry, RegistryType registryType)
            throws Exception {
        // add admiral registry to current tenant
        RegistryState registryState = new RegistryState();
        registryState.address = registryAddress = registry;
        registryState.name = TEST_REGISTRY_NAME;
        registryState.documentSelfLink = TEST_REGISTRY_NAME;
        registryState.tenantLinks = TENANT_LINKS;
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;

        registryState.authCredentialsLink = configureRegistryAuthCredentials(registryType);

        RegistryHostSpec registryHostSpec = new RegistryHostSpec();
        registryHostSpec.hostState = registryState;
        registryHostSpec.acceptCertificate = true;
        createOrUpdateRegistry(registryHostSpec);
    }

    private String configureRegistryAuthCredentials(RegistryType registryType) throws Exception {
        if (RegistryType.V2_BASIC_AUTH.equals(registryType)) {
            AuthCredentialsServiceState authState = new AuthCredentialsServiceState();
            authState.type = AuthCredentialsType.Password.toString();
            authState.userEmail = getTestRequiredProp("docker.secure.v2.registry.username");
            authState.privateKey = getTestRequiredProp("docker.secure.v2.registry.password");

            HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.POST,
                    getBaseUrl() + buildServiceUri(AuthCredentialsService.FACTORY_LINK),
                    Utils.toJson(authState));

            if (HttpURLConnection.HTTP_OK != httpResponse.statusCode) {
                throw new IllegalArgumentException(
                        "Add registry auth state failed with status code: "
                                + httpResponse.statusCode);
            }

            String documentSelfLink = Utils.fromJson(httpResponse.responseBody,
                    AuthCredentialsServiceState.class).documentSelfLink;

            return documentSelfLink;
        }

        return null;
    }

    private void createOrUpdateRegistry(RegistryHostSpec hostSpec) throws Exception {
        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.PUT,
                getBaseUrl() + buildServiceUri(RegistryHostConfigService.SELF_LINK),
                Utils.toJson(hostSpec));

        if (HttpURLConnection.HTTP_NO_CONTENT != httpResponse.statusCode) {
            throw new IllegalArgumentException("Add registry host failed with status code: "
                    + httpResponse.statusCode);
        }

        List<String> headers = httpResponse.headers.get(Operation.LOCATION_HEADER);
        String documentSelfLink = headers.get(0);
        cleanUpAfter(getDocument(documentSelfLink, RegistryState.class));
    }

    protected String importTemplate(ServiceClient serviceClient, String filePath) throws Exception {
        String template = CommonTestStateFactory.getFileContent(filePath);

        URI uri = URI.create(getBaseUrl()
                + buildServiceUri(CompositeDescriptionContentService.SELF_LINK));

        Operation op = sendRequest(serviceClient, Operation.createPost(uri)
                .setContentType(MEDIA_TYPE_APPLICATION_YAML)
                .setBody(template));

        String location = op.getResponseHeader(Operation.LOCATION_HEADER);
        assertNotNull("Missing location header", location);
        return URI.create(location).getPath();
    }

    protected void restartContainer(String containerLink) throws Exception {
        stopContainer(containerLink);
        startContainer(containerLink);
    }

    protected void restartContainerDay2(String containerLink) throws Exception {
        stopContainerDay2(containerLink);
        startContainerDay2(containerLink);
    }

    protected void stopContainer(String containerLink) throws Exception {
        AdapterRequest adapterRequest = new AdapterRequest();
        adapterRequest.resourceReference = URI.create(getBaseUrl() + containerLink);
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        adapterRequest.operationTypeId = ContainerOperationType.STOP.id;
        sendRequest(HttpMethod.PATCH, ManagementUriParts.ADAPTER_DOCKER,
                Utils.toJson(adapterRequest));

        waitForStateChange(
                containerLink,
                (body) -> {
                    ContainerState containerState = Utils.fromJson(body, ContainerState.class);
                    return containerState.powerState == PowerState.STOPPED;
                });
    }

    protected void startContainer(String containerLink) throws Exception {
        AdapterRequest adapterRequest = new AdapterRequest();
        adapterRequest.resourceReference = URI.create(getBaseUrl() + containerLink);
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        adapterRequest.operationTypeId = ContainerOperationType.START.id;
        sendRequest(HttpMethod.PATCH, ManagementUriParts.ADAPTER_DOCKER,
                Utils.toJson(adapterRequest));

        waitForStateChange(
                containerLink,
                (body) -> {
                    ContainerState containerState = Utils.fromJson(body, ContainerState.class);
                    return containerState.powerState == PowerState.RUNNING;
                });
    }

    protected void stopContainerDay2(String containerLink) throws Exception {
        RequestBrokerState day2DeleteRequest = new RequestBrokerState();
        day2DeleteRequest.resourceType = ResourceType.CONTAINER_TYPE.getName();
        day2DeleteRequest.operation = ContainerOperationType.STOP.id;
        day2DeleteRequest.resourceLinks = Collections.singleton(containerLink);
        day2DeleteRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, day2DeleteRequest);

        waitForTaskToComplete(day2DeleteRequest.documentSelfLink);
    }

    protected void startContainerDay2(String containerLink) throws Exception {
        RequestBrokerState day2DeleteRequest = new RequestBrokerState();
        day2DeleteRequest.resourceType = ResourceType.CONTAINER_TYPE.getName();
        day2DeleteRequest.operation = ContainerOperationType.START.id;
        day2DeleteRequest.resourceLinks = Collections.singleton(containerLink);
        day2DeleteRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, day2DeleteRequest);

        waitForTaskToComplete(day2DeleteRequest.documentSelfLink);
    }

    protected Operation sendRequest(ServiceClient serviceClient, Operation op)
            throws InterruptedException, ExecutionException,
            TimeoutException {
        return sendRequest(serviceClient, op, DEFAULT_OPERATION_TIMEOUT_MILLIS);
    }

    // https://github.com/docker/libnetwork/issues/1101
    protected void checkNumberOfNetworks(ServiceClient serviceClient, int networkToCreate) throws Exception {
        // get only bridge networks
        String encodedQuery = URLEncoder.encode(BRIDGE_NETWORK_TYPE_QUERY, "UTF-8");
        String requestLink = getBaseUrl() + buildServiceUri(ContainerNetworkService.FACTORY_LINK, encodedQuery);
        URI uri = URI.create(requestLink);

        Operation op = sendRequest(serviceClient, Operation.createGet(uri));
        ServiceDocumentQueryResult doc = op.getBody(ServiceDocumentQueryResult.class);

        if (doc != null &&
                doc.documentCount + networkToCreate > DOCKER_MAX_BRIDGE_NETWORKS_COUNT) {
            throw new Exception("Max number of networks exceeded.");
        }
    }

    protected String getHostnameOfComputeHost(String hostLink) throws Exception {
        String address = getDocument(hostLink, ComputeState.class).address;
        return UriUtilsExtended.extractHost(address);
    }

    protected void runContainerCommand(String containerLink, String[] command) throws Exception {

        logger.info("Running command [%s] in container [%s]", Arrays.toString(command),
                containerLink);
        URI uri = URI
                .create(getBaseUrl() + buildServiceUri(ShellContainerExecutorService.SELF_LINK));

        String url = UriUtils.appendQueryParam(uri,
                ShellContainerExecutorService.CONTAINER_LINK_URI_PARAM, containerLink).toString();

        Map<String, Object> params = new HashMap<>();
        params.put(ShellContainerExecutorService.COMMAND_KEY, command);

        SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.POST, url, Utils.toJson(params));
    }

    private Operation sendRequest(ServiceClient serviceClient, Operation op, long timeoutMilis)
            throws InterruptedException, ExecutionException,
            TimeoutException {

        CompletableFuture<Operation> c = new CompletableFuture<>();
        serviceClient.send(op
                .setReferer(URI.create("/"))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        c.completeExceptionally(ex);

                    } else {
                        c.complete(o);
                    }
                }));

        return c.get(timeoutMilis, TimeUnit.MILLISECONDS);
    }

    private static ComputeState createDockerHost(String address, String port, String credLink,
            DockerAdapterType adapterType, String id, String resourcePoolLink) throws Exception {
        ComputeState compute = IntegratonTestStateFactory.createDockerComputeHost();
        if (id != null) {
            compute.id = id;
            compute.documentSelfLink = id;
        }
        compute.address = address;
        compute.customProperties.put(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME, port);

        compute.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                adapterType.name());

        // link credentials to host
        compute.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME, credLink);

        // link to pool link
        compute.resourcePoolLink = resourcePoolLink;
        return addHost(compute);
    }

    public AuthCredentialsServiceState getDockerHostAuthCredentials() {
        return dockerHostAuthCredentials;
    }
}
