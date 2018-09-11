/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterStatus;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterType;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ClusterServiceIT extends BaseProvisioningOnCoreOsIT {

    static class ClusterDtoWithCertificate extends ClusterDto {
        String certificate;
    }

    @Before
    public void setUp() throws Throwable {
        setupEnvironmentForCluster();

        // clean up
        ServiceDocumentQueryResult allClusterForCleaning = getAllClusters(false);
        for (String clusterDocumentSelfLink : allClusterForCleaning.documentLinks) {
            deleteCluster(clusterDocumentSelfLink);
        }
    }

    @Test
    public void testCreatingCluster() throws Throwable {
        ClusterDto dtoCreated = createCluster();

        verifyCluster(dtoCreated, ClusterType.DOCKER, placementZoneName, projectLink);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateDeleteCreateNoCredentialsCluster() throws Throwable {

        ClusterDto dtoCreated = createCluster();

        deleteCluster(dtoCreated.documentSelfLink);

        hostSpec.hostState.customProperties.remove(ComputeConstants
                .HOST_AUTH_CREDENTIALS_PROP_NAME);
        sendRequest(HttpMethod.POST, ClusterService.SELF_LINK, Utils.toJson
                (hostSpec));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddSecondHostNoCredentials() throws Throwable {

        ClusterDto dtoCreated = createCluster();

        ContainerHostSpec hostSpecDocker = createContainerHostSpec(
                Collections.singletonList(projectLink),
                ContainerHostType.DOCKER, true);
        hostSpecDocker.hostState.address = getDockerHostAddressWithPort2;
        hostSpecDocker.hostState.customProperties.remove(ComputeConstants
                .HOST_AUTH_CREDENTIALS_PROP_NAME);
        String pathHostsInCluster = UriUtils.buildUriPath(ClusterService.SELF_LINK, Service
                .getId(dtoCreated.documentSelfLink), "hosts");

        sendRequest(HttpMethod.POST, pathHostsInCluster, Utils.toJson
                (hostSpecDocker));
    }

    @Test
    public void testCreatingClusterNotAcceptCert() throws Throwable {
        // make sure the certificate was not imported before
        ServiceDocumentQueryResult certificatesResult = getAllCertificates();
        for (String documentSelfLink : certificatesResult.documentLinks) {
            delete(documentSelfLink);
        }

        ClusterDtoWithCertificate dto = createClusterNotAcceptCert();

        // cluster is not created, certificate is returned to accept / cancel it
        assertNotNull(dto);
        assertNotNull("document selflink should not be null", dto.documentSelfLink);
        assertNotNull("certificate should not be null", dto.certificate);
        assertNull("name should be null", dto.name);
        assertNull("status should be null", dto.status);
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

        String pathHostInCluster = UriUtils
                .buildUriPath(ClusterService.SELF_LINK, Service.getId(dtoCreated.documentSelfLink),
                        ClusterService.HOSTS_URI_PATH_SEGMENT,
                        Service.getId(dtoCreated.nodeLinks.get(0)));
        String computeStateRaw = sendRequest(HttpMethod.GET, pathHostInCluster, null);
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

    @Test
    public void testAddRemoveDockerHostInCluster() throws Throwable {

        ClusterDto dtoCreated = createCluster();

        ClusterDto dtoGet = getCluster(dtoCreated.documentSelfLink);

        assertEquals(1, dtoGet.nodeLinks.size());
        assertTrue(dtoGet.nodeLinks.contains(dtoCreated.nodeLinks.get(0)));
        assertEquals(getDockerHostAddressWithPort1, dtoGet.nodes.get(dtoCreated.nodeLinks.get(0))
                .address);

        ContainerHostSpec hostSpecDocker = createContainerHostSpec(
                Collections.singletonList(projectLink),
                ContainerHostType.DOCKER, true);
        hostSpecDocker.hostState.address = getDockerHostAddressWithPort2;
        String pathHostsInCluster = UriUtils.buildUriPath(ClusterService.SELF_LINK, Service
                .getId(dtoCreated.documentSelfLink), "hosts");

        sendRequest(HttpMethod.POST, pathHostsInCluster, Utils.toJson
                (hostSpecDocker));

        dtoGet = getCluster(dtoCreated.documentSelfLink);

        assertEquals(2, dtoGet.nodeLinks.size());
        assertTrue(dtoGet.nodeLinks.contains(dtoCreated.nodeLinks.get(0)));

        String secondComputeLink;
        if (dtoGet.nodeLinks.get(0).equals(dtoCreated.nodeLinks.get(0))) {
            secondComputeLink = dtoGet.nodeLinks.get(1);
        } else {
            secondComputeLink = dtoGet.nodeLinks.get(0);
        }

        assertEquals(2, dtoGet.nodeLinks.size());
        assertEquals(getDockerHostAddressWithPort1, dtoGet.nodes.get(dtoCreated.nodeLinks.get(0))
                .address);
        assertEquals(getDockerHostAddressWithPort2, dtoGet.nodes.get(secondComputeLink)
                .address);

        String pathSecondHostInCluster = UriUtils
                .buildUriPath(ClusterService.SELF_LINK, Service.getId(dtoCreated.documentSelfLink),
                        ClusterService.HOSTS_URI_PATH_SEGMENT,
                        Service.getId(secondComputeLink));
        sendRequest(HttpMethod.DELETE, pathSecondHostInCluster, Utils.toJson
                (hostSpecDocker));

        dtoGet = getCluster(dtoCreated.documentSelfLink);
        assertEquals(1, dtoGet.nodeLinks.size());
        assertTrue(dtoGet.nodeLinks.contains(dtoCreated.nodeLinks.get(0)));
        assertEquals(getDockerHostAddressWithPort1, dtoGet.nodes.get(dtoCreated.nodeLinks.get(0))
                .address);

    }

    @Test
    public void testAddDockerHostWithInvalidUrlShouldFail() throws Throwable {
        ClusterDto dtoCreated = createCluster();

        ContainerHostSpec hostSpec = createContainerHostSpec(
                Collections.singletonList(projectLink),
                ContainerHostType.DOCKER, true);

        hostSpec.hostState.address = "https://vmware.com";

        String pathHostsInCluster = UriUtils.buildUriPath(ClusterService.SELF_LINK,
                Service.getId(dtoCreated.documentSelfLink), "hosts");

        try {
            sendRequest(HttpMethod.POST, pathHostsInCluster, Utils.toJson(hostSpec));
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage()
                    .contains("Error connecting to https://vmware.com: " +
                            "Unexpected error: Invalid docker URL: https://vmware.com."));
        }
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType)
            throws Exception {
        return null;
    }

    private ClusterDtoWithCertificate createClusterNotAcceptCert() throws Exception {
        hostSpec.acceptCertificate = false;
        String dtoRaw = sendRequest(HttpMethod.POST, ClusterService.SELF_LINK, Utils.toJson
                (hostSpec));
        ClusterDtoWithCertificate dto = Utils.fromJson(dtoRaw, ClusterDtoWithCertificate.class);
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

    private ServiceDocumentQueryResult getAllCertificates() throws Exception {
        String queryStr = SslTrustCertificateService.FACTORY_LINK;
        return getDocument(queryStr, ServiceDocumentQueryResult.class);
    }
}
