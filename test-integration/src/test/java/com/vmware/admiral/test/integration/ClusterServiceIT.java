/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.PlacementZoneUtil;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterStatus;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterType;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.data.IntegratonTestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class ClusterServiceIT extends BaseProvisioningOnCoreOsIT {

    private String dockerHostAddress;
    private String hostPort;
    private String getDockerHostAddressWithPort;
    private AuthCredentialsServiceState dockerHostAuthCredentials;
    private SslTrustCertificateState dockerHostSslTrust;

    private String projectLink;
    private String placementZoneName;
    private ContainerHostSpec hostSpec;

    @Before
    public void setUp() throws Throwable {
        setupEnvironmentForCluster();
    }

    @Test
    public void testCreatingCluster() throws Throwable {

        ClusterDto dtoCreated = createCluster();

        verifyCluster(dtoCreated, ClusterType.DOCKER, placementZoneName, projectLink);

    }

    @Test
    public void testPatchingCluster() throws Throwable {

        final String name1 = "name_1";
        final String details1 = "details_1";
        final String name2 = "name_2";
        final String details2 = "details_2";

        ClusterDto dtoCreated = createCluster();

        ClusterDto patchClusterDto = new ClusterDto();
        patchClusterDto.name = name1;
        patchClusterDto.details = details1;
        patchClusterDto = patchCluster(dtoCreated.documentSelfLink, patchClusterDto);
        assertEquals(name1, patchClusterDto.name);
        assertEquals(details1, patchClusterDto.details);

        patchClusterDto = new ClusterDto();
        patchClusterDto.name = name2;
        patchClusterDto.details = details2;
        patchClusterDto = patchCluster(dtoCreated.documentSelfLink, patchClusterDto);
        assertEquals(name2, patchClusterDto.name);
        assertEquals(details2, patchClusterDto.details);

    }

    @Test
    public void testGettingAllClusters() throws Throwable {

        ServiceDocumentQueryResult allClustersResult = getAllClusters(false);

        assertEquals(0, allClustersResult.documentCount.longValue());
        assertEquals(0, allClustersResult.documentLinks.size());
        assertNull(allClustersResult.documents);

        ClusterDto dtoCreated = createCluster();

        allClustersResult = getAllClusters(false);

        assertEquals(1, allClustersResult.documentCount.longValue());
        assertEquals(1, allClustersResult.documentLinks.size());
        assertEquals(dtoCreated.documentSelfLink, allClustersResult.documentLinks.get(0));
        assertNull(allClustersResult.documents);

        allClustersResult = getAllClusters(true);

        assertEquals(1, allClustersResult.documentCount.longValue());
        assertEquals(1, allClustersResult.documentLinks.size());
        assertEquals(1, allClustersResult.documents.size());
        assertEquals(dtoCreated.documentSelfLink, allClustersResult.documentLinks.get(0));

        String dtoCollectedRaw = allClustersResult.documents.get(dtoCreated.documentSelfLink)
                .toString();
        ClusterDto dtoCollected = Utils.fromJson(dtoCollectedRaw, ClusterDto.class);
        verifyCluster(dtoCollected, ClusterType.DOCKER, placementZoneName, projectLink);

    }

    @Test
    public void testGettingCluster() throws Throwable {

        ClusterDto dtoCreated = createCluster();

        ClusterDto dtoGet = getCluster(dtoCreated.documentSelfLink);
        verifyCluster(dtoGet, ClusterType.DOCKER, placementZoneName, projectLink);
    }

    @Test
    public void testGettingAllHostsInCluster() throws Throwable {

        ClusterDto dtoCreated = createCluster();

        String pathHostsInCluster = UriUtils
                .buildUriPath(ClusterService.SELF_LINK, Service.getId(dtoCreated.documentSelfLink),
                        ClusterService.HOSTS_URI_PATH_SEGMENT);
        ServiceDocumentQueryResult allHostsResult =
                getDocument(pathHostsInCluster, ServiceDocumentQueryResult.class);

        assertEquals(1, allHostsResult.documentCount.longValue());
        assertEquals(1, allHostsResult.documentLinks.size());
        assertEquals(dtoCreated.nodeLinks.get(0), allHostsResult.documentLinks.get(0));
        assertEquals(1, allHostsResult.documents.size());
        assertEquals(dtoCreated.nodeLinks.get(0), allHostsResult.documentLinks.get(0));
    }

    @Test
    public void testGettingSingleHostInCluster() throws Throwable {

        ClusterDto dtoCreated = createCluster();

        String pathHostsInCluster = UriUtils
                .buildUriPath(ClusterService.SELF_LINK, Service.getId(dtoCreated.documentSelfLink),
                        ClusterService.HOSTS_URI_PATH_SEGMENT,
                        Service.getId(dtoCreated.nodeLinks.get(0)));
        String computeStateRaw = sendRequest(HttpMethod.GET, pathHostsInCluster, null);
        ComputeState computeState = Utils.fromJson(computeStateRaw, ComputeState.class);

        assertNotNull(computeState);
        assertNotNull(computeState.tenantLinks);
        assertTrue(computeState.tenantLinks.contains(projectLink));
        assertEquals(ContainerHostType.DOCKER,
                ContainerHostUtil.getDeclaredContainerHostType(computeState));
        assertEquals(ComputeService.PowerState.ON, computeState.powerState);
    }

    @Test
    public void testDeletingCluster() throws Throwable {

        ClusterDto dtoCreated = createCluster();

        String placementZoneLink = ResourcePoolService.FACTORY_LINK + "/" + Service.getId
                (dtoCreated.documentSelfLink);
        String groupPlacementLink = getPlacementsForZone(placementZoneLink).get(0);

        deleteCluster(dtoCreated.documentSelfLink);

        assertServiceDeleted(dtoCreated.documentSelfLink);
        assertServiceDeleted(dtoCreated.nodeLinks.get(0));
        assertServiceDeleted(placementZoneLink);
        assertServiceDeleted(groupPlacementLink);

    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType)
            throws Exception {
        return null;
    }

    private ClusterDto createCluster() throws Exception {
        String dtoRaw = sendRequest(HttpMethod.POST, ClusterService.SELF_LINK, Utils.toJson
                (hostSpec));
        ClusterDto dto = Utils.fromJson(dtoRaw, ClusterDto.class);
        cleanUpAfter(dto);
        return dto;
    }

    private ClusterDto patchCluster(String clusterLink, ClusterDto clusterBody) throws Exception {
        String dtoRaw = sendRequest(HttpMethod.PATCH, clusterLink, Utils.toJson
                (clusterBody));
        return Utils.fromJson(dtoRaw, ClusterDto.class);
    }

    private ClusterDto getCluster(String clusterDocumentSelfLink) throws Exception {
        String dtoRaw = sendRequest(HttpMethod.GET, clusterDocumentSelfLink, null);
        ClusterDto dto = Utils.fromJson(dtoRaw, ClusterDto.class);
        return dto;
    }

    private ServiceDocumentQueryResult getAllClusters(boolean expand) throws Exception {
        String queryStr = ClusterService.SELF_LINK;

        if (expand) {
            queryStr += "?expand";
        }

        return getDocument(queryStr, ServiceDocumentQueryResult.class);
    }

    private void deleteCluster(String clusterDocumentSelfLink) throws Exception {
        sendRequest(HttpMethod.DELETE, clusterDocumentSelfLink, null);
    }

    private void assertServiceDeleted(String documentSelfLink) {
        boolean serviceFoundExceptionDetected = false;
        try {
            String result = sendRequest(HttpMethod.GET, documentSelfLink, null);
            serviceFoundExceptionDetected = (result == null);
        } catch (Exception e) {
            serviceFoundExceptionDetected = (e instanceof IllegalArgumentException) && e
                    .getMessage().contains
                            ("Service not found:");
        }
        assertTrue(serviceFoundExceptionDetected);
    }

    private String buildProjectLink(String projectId) {
        return UriUtils.buildUriPath(ManagementUriParts.PROJECTS, projectId);
    }

    private ContainerHostSpec createContainerHostSpec(List<String> tenantLinks,
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
        cs.address = getDockerHostAddressWithPort;
        // cs.powerState = hostState;
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        cs.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                hostType.toString());
        cs.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                dockerHostAuthCredentials.documentSelfLink);
        cs.customProperties.put(MockDockerHostAdapterService.CONTAINER_HOST_TYPE_PROP_NAME,
                hostType.toString());
        cs.tenantLinks = new ArrayList<>(tenantLinks);

        return cs;
    }

    protected void setupEnvironmentForCluster()
            throws Throwable {
        dockerHostAddress = getTestRequiredProp("docker.host.address");
        hostPort = getTestRequiredProp("docker.host.port." + DockerAdapterType.API.name());
        getDockerHostAddressWithPort = "https://" + dockerHostAddress + ":" + hostPort;

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

        postDocument(SslTrustCertificateService.FACTORY_LINK, dockerHostSslTrust);

        projectLink = buildProjectLink(ProjectService.DEFAULT_PROJECT_ID);
        placementZoneName = PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.DOCKER,
                        getDockerHostAddressWithPort);
        hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.DOCKER, true);
    }

    private void verifyCluster(ClusterDto clusterDto, ClusterType clusterType, String expectedName,
            String projectLink) throws Throwable {
        // verify cluster creation
        assertNotNull(clusterDto);
        assertNotNull(clusterDto.documentSelfLink);
        assertEquals(clusterType, clusterDto.type);
        assertNotNull(clusterDto.nodeLinks);
        assertEquals(1, clusterDto.nodeLinks.size());
        assertEquals(ClusterStatus.ON, clusterDto.status);
        assertEquals(expectedName, clusterDto.name);

        // verify placement zone
        final String placementZoneLink = UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK,
                Service.getId(clusterDto.documentSelfLink));
        ResourcePoolState placementZone = getDocument(placementZoneLink, ResourcePoolState.class);
        assertNotNull(placementZone);
        assertNotNull(placementZone.tenantLinks);
        assertTrue(placementZone.tenantLinks.contains(projectLink));
        assertEquals(expectedName, placementZone.name);

        // verify placement
        List<String> placements = getPlacementsForZone(placementZoneLink);
        assertNotNull(placements);
        assertEquals(1, placements.size());
        String placementDoccumentSelfLink = placements.iterator().next();
        assertNotNull(placementDoccumentSelfLink);
        GroupResourcePlacementState placement = getDocument(placementDoccumentSelfLink,
                GroupResourcePlacementState.class);
        assertEquals(placementZoneLink, placement.resourcePoolLink);
        assertNotNull(placement.tenantLinks);
        assertTrue(placement.tenantLinks.contains(projectLink));

        // verify compute state
        ComputeState hostState = getDocument(clusterDto.nodeLinks.iterator().next(),
                ComputeState.class);

        assertNotNull(hostState);
        assertNotNull(hostState.tenantLinks);
        assertTrue(hostState.tenantLinks.contains(projectLink));
        if (clusterType == ClusterType.DOCKER) {
            assertEquals(ContainerHostType.DOCKER,
                    ContainerHostUtil.getDeclaredContainerHostType(hostState));
        } else {
            assertEquals(ContainerHostType.VCH,
                    ContainerHostUtil.getDeclaredContainerHostType(hostState));
        }
        assertEquals(ComputeService.PowerState.ON, hostState.powerState);
    }

    private List<String> getPlacementsForZone(String placementZoneLink)
            throws Exception {
        String query = "/resources/group-placements?%24filter=resourcePoolLink%20eq%20%27"
                + placementZoneLink + "*%27";
        ServiceDocumentQueryResult result =
                getDocument(query, ServiceDocumentQueryResult.class);

        return result.documentLinks;
    }

}
