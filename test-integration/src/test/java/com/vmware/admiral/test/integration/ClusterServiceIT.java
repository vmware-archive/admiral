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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

    @Before
    public void setUp() throws Exception {
        setupEnvironmentForCluster();
    }

    @Test
    public void testCreatingCluster() throws Throwable {
        final String projectLink = buildProjectLink(ProjectService.DEFAULT_PROJECT_ID);

        final String placementZoneName = PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.DOCKER,
                        getDockerHostAddressWithPort);

        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.DOCKER, true);

        ClusterDto dtoCreated = createCluster(hostSpec);

        verifyCluster(dtoCreated, ClusterType.DOCKER, placementZoneName, projectLink);

    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType)
            throws Exception {
        return null;
    }

    private ClusterDto createCluster(ContainerHostSpec hostSpec) throws Exception {
        ArrayList<ClusterDto> result = new ArrayList<>(1);

        String dtoRaw = sendRequest(HttpMethod.POST, ClusterService.SELF_LINK, Utils.toJson
                (hostSpec));
        ClusterDto dto = Utils.fromJson(dtoRaw, ClusterDto.class);
        cleanUpAfter(dto);
        return dto;

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
            throws Exception {
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
