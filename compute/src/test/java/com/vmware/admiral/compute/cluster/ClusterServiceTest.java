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

package com.vmware.admiral.compute.cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.compute.ContainerHostUtil.PROPERTY_NAME_DRIVER;
import static com.vmware.admiral.compute.ContainerHostUtil.VMWARE_VIC_DRIVER1;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.PlacementZoneUtil;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterStatus;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterType;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.admiral.service.test.MockRequestBrokerService;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTemplate;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class ClusterServiceTest extends ComputeBaseTest {
    private static final String COMPUTE_ADDRESS = "test.host.address";
    private static final String ADAPTER_DOCKER_TYPE_ID = "API";

    private MockDockerHostAdapterService dockerAdapterService;
    private MockRequestBrokerService requestBrokerService;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ClusterService.SELF_LINK);
        waitForServiceAvailability(ContainerHostService.SELF_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);

        dockerAdapterService = new MockDockerHostAdapterService();
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerHostAdapterService.class)), dockerAdapterService);
        requestBrokerService = new MockRequestBrokerService();
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockRequestBrokerService.class)), requestBrokerService);

        waitForServiceAvailability(MockDockerHostAdapterService.SELF_LINK);
        waitForServiceAvailability(MockRequestBrokerService.SELF_LINK);

    }

    @Test
    public void testCreateDockerCluster() throws Throwable {
        final String projectLink = buildProjectLink("test-docker-project");
        final String placementZoneName = PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.DOCKER, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.DOCKER);
        ClusterDto clusterDto = createCluster(hostSpec);
        verifyCluster(clusterDto, ClusterType.DOCKER, placementZoneName, projectLink);

        // create the host with the specified id
        assertNotNull(hostSpec.hostState.id);
        assertNotEquals("", hostSpec.hostState.id);
        ComputeState host = getDocument(ComputeState.class, clusterDto.nodeLinks.get(0));
        assertEquals(hostSpec.hostState.id, host.id);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testCreateDockerClusterWithoutProject() throws Throwable {
        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.emptyList(),
                ContainerHostType.DOCKER);
        createCluster(hostSpec);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testCreateDockerClusterWithoutTenant() throws Throwable {
        final String projectLink = buildProjectLink("test-docker-project");

        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.DOCKER);
        hostSpec.hostState.tenantLinks = Collections.emptyList();
        createCluster(hostSpec);
    }

    @Test
    public void testCreateDockerClusterHostFailure() throws Throwable {
        final String projectLink = buildProjectLink("test-docker-project");

        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.DOCKER);
        hostSpec.hostState.customProperties = new HashMap<>();
        try {
            createCluster(hostSpec);
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof LocalizableValidationException);
            LocalizableValidationException ex = (LocalizableValidationException) e;
            // check there isn't a different cause (like IllegalArgumentException)
            assertNull(ex.getCause());
        }
    }

    @Test
    public void testCreateVchCluster() throws Throwable {
        final String projectLink = buildProjectLink("test-vch-project");
        final String placementZoneName = PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.VCH, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.VCH);
        verifyCluster(createCluster(hostSpec), ClusterType.VCH, placementZoneName, projectLink);
    }

    @Test
    public void testDeleteDockerCluster() throws Throwable {
        final String projectLink = buildProjectLink("test-docker-project");
        final String placementZoneName = PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.DOCKER, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.DOCKER);
        ClusterDto clusterDto = createCluster(hostSpec);
        verifyCluster(clusterDto, ClusterType.DOCKER, placementZoneName, projectLink);

        Map<String, ClusterDto> allClustersExpand = getClustersExpand();
        assertTrue(allClustersExpand.keySet().size() == 2);
        assertTrue(allClustersExpand.keySet().contains(clusterDto.documentSelfLink));
        Map<String, ComputeState> allComputesExpand = getAllComputeExpand();
        assertTrue(allComputesExpand.keySet().size() == 1);

        List<String> placementZonesLinks = getPlacementZonesLinks();
        assertEquals(2, placementZonesLinks.size());
        List<String> placementsLinks = getPlacementsLinks();
        assertEquals(2, placementsLinks.size());

        deleteCluster(Service.getId(clusterDto.documentSelfLink));

        allClustersExpand = getClustersExpand();
        assertTrue(allClustersExpand.keySet().size() == 1);
        assertTrue(!allClustersExpand.keySet().contains(clusterDto.documentSelfLink));
        allComputesExpand = getAllComputeExpand();
        assertTrue(allComputesExpand.keySet().isEmpty());

        placementZonesLinks = getPlacementZonesLinks();
        assertEquals(1, placementZonesLinks.size());
        placementsLinks = getPlacementsLinks();
        assertEquals(1, placementsLinks.size());
    }

    @Test
    public void testDeleteVchCluster() throws Throwable {
        final String projectLink = buildProjectLink("test-vch-project");
        final String placementZoneName = PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.VCH, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.VCH);
        ClusterDto clusterDto = createCluster(hostSpec);
        verifyCluster(clusterDto, ClusterType.VCH, placementZoneName, projectLink);

        Map<String, ClusterDto> allClustersExpand = getClustersExpand();
        assertTrue(allClustersExpand.keySet().size() == 2);
        assertTrue(allClustersExpand.keySet().contains(clusterDto.documentSelfLink));
        Map<String, ComputeState> allComputesExpand = getAllComputeExpand();
        assertTrue(allComputesExpand.keySet().size() == 1);

        List<String> placementZonesLinks = getPlacementZonesLinks();
        assertEquals(2, placementZonesLinks.size());
        List<String> placementsLinks = getPlacementsLinks();
        assertEquals(2, placementsLinks.size());

        deleteCluster(Service.getId(clusterDto.documentSelfLink));

        allClustersExpand = getClustersExpand();
        assertTrue(allClustersExpand.keySet().size() == 1);
        assertTrue(!allClustersExpand.keySet().contains(clusterDto.documentSelfLink));
        allComputesExpand = getAllComputeExpand();
        assertTrue(allComputesExpand.keySet().isEmpty());

        placementZonesLinks = getPlacementZonesLinks();
        assertEquals(1, placementZonesLinks.size());
        placementsLinks = getPlacementsLinks();
        assertEquals(1, placementsLinks.size());
    }

    @Test
    public void testGetVchCluster() throws Throwable {
        final String projectLink = buildProjectLink("test-vch-project");
        PlacementZoneUtil.buildPlacementZoneDefaultName(ContainerHostType.VCH, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.VCH);

        // mark the host as vch
        hostSpec.hostState.customProperties.put(PROPERTY_NAME_DRIVER, VMWARE_VIC_DRIVER1);

        ClusterDto clusterDto = createCluster(hostSpec);

        assertNotNull(clusterDto.nodeLinks);
        assertNotNull(clusterDto.nodes);
        assertEquals(1, clusterDto.nodes.size());

        // verify the whole compute object is returned
        ComputeState vchHost = clusterDto.nodes.values().iterator().next();
        assertNotNull(vchHost.customProperties);
        assertTrue(vchHost.customProperties.size() > 0);
    }

    @Test
    public void testCreateDockerClusterCustomNameAndDetails() throws Throwable {
        final String projectLink = buildProjectLink("test-docker-project");

        final String clusterName = "ClusterTestName";
        final String clusterDetails = "Test cluster details.";

        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.DOCKER, clusterName, clusterDetails);
        ClusterDto clusterDto = createCluster(hostSpec);
        verifyCluster(clusterDto, ClusterType.DOCKER, clusterName, projectLink);
        assertEquals(clusterDetails, clusterDto.details);
        assertNotNull(clusterDto.clusterCreationTimeMicros);

        clusterDto = getOneCluster(Service.getId(clusterDto.documentSelfLink));
        verifyCluster(clusterDto, ClusterType.DOCKER, clusterName, projectLink);
        assertEquals(clusterDetails, clusterDto.details);
        assertNotNull(clusterDto.clusterCreationTimeMicros);
    }

    @Test
    public void testCreateVchClusterCustomNameAndDetails() throws Throwable {
        final String projectLink = buildProjectLink("test-vch-project");

        final String clusterName = "ClusterTestName";
        final String clusterDetails = "Test cluster details.";

        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.VCH, clusterName, clusterDetails);
        ClusterDto clusterDto = createCluster(hostSpec);
        verifyCluster(clusterDto, ClusterType.VCH, clusterName, projectLink);

        clusterDto = getOneCluster(Service.getId(clusterDto.documentSelfLink));
        verifyCluster(clusterDto, ClusterType.VCH, clusterName, projectLink);
        assertEquals(clusterDetails, clusterDto.details);
        assertNotNull(clusterDto.clusterCreationTimeMicros);
    }

    @Test
    public void testListClusters() throws Throwable {
        final String projectLinkDocker = buildProjectLink("test-docker-project");
        final String placementZoneNameDocker = PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.DOCKER, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpecDocker = createContainerHostSpec(
                Collections.singletonList(projectLinkDocker),
                ContainerHostType.DOCKER);

        final String projectLinkVCH = buildProjectLink("test-vch-project");
        final String placementZoneNameVCH = PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.VCH, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpecVCH = createContainerHostSpec(
                Collections.singletonList(projectLinkVCH),
                ContainerHostType.VCH);
        ClusterDto clusterDocker = createCluster(hostSpecDocker);
        ClusterDto clusterVCH = createCluster(hostSpecVCH);
        Map<String, ClusterDto> allClustersExpand = getClustersExpand();

        assertEquals(3, allClustersExpand.size());
        ClusterDto defaultClusterDto = allClustersExpand.get(UriUtils.buildUriPath(
                ClusterService.SELF_LINK,
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_ID));
        assertNotNull(defaultClusterDto);
        assertEquals(0, defaultClusterDto.nodeLinks.size());
        assertEquals(ClusterService.ClusterStatus.DISABLED, defaultClusterDto.status);

        verifyCluster(allClustersExpand.get(clusterDocker.documentSelfLink), ClusterType.DOCKER,
                placementZoneNameDocker, projectLinkDocker);
        verifyCluster(allClustersExpand.get(clusterVCH.documentSelfLink), ClusterType.VCH,
                placementZoneNameVCH, projectLinkVCH);

        Map<String, ClusterDto> filteredClustersExpand = getClustersExpand(
                "?$filter=name eq 'docker:test.host.address'");
        assertEquals(1, filteredClustersExpand.size());
        assertEquals(placementZoneNameDocker,
                filteredClustersExpand.get(clusterDocker.documentSelfLink).name);

        List<String> allClustersList = getClustersLinks();
        assertEquals(3, allClustersList.size());
        assertTrue(allClustersList.contains(UriUtils.buildUriPath(
                ClusterService.SELF_LINK,
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_ID)));
        assertTrue(allClustersList.contains(clusterDocker.documentSelfLink));
        assertTrue(allClustersList.contains(clusterVCH.documentSelfLink));

        List<String> filteredClustersList = getClustersLinks(
                "?$filter=name eq 'docker:test.host.address'");
        assertEquals(1, filteredClustersList.size());
        assertTrue(filteredClustersList.contains(clusterDocker.documentSelfLink));
    }

    @Test
    public void testGetOneCluster() throws Throwable {
        final String projectLinkDocker = buildProjectLink("test-docker-project");
        final String placementZoneNameDocker = PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.DOCKER, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpecDocker = createContainerHostSpec(
                Collections.singletonList(projectLinkDocker),
                ContainerHostType.DOCKER);

        ClusterDto clusterDocker = createCluster(hostSpecDocker);

        clusterDocker = getOneCluster(Service.getId(clusterDocker.documentSelfLink));
        assertNotNull(clusterDocker);
        assertEquals(placementZoneNameDocker,
                clusterDocker.name);
    }

    @Test
    public void testPatchCluster() throws Throwable {
        final String name1 = "name_1";
        final String details1 = "details_1";
        final String name2 = "name_2";
        final String details2 = "details_2";

        final String projectLinkDocker = buildProjectLink("test-docker-project");
        PlacementZoneUtil.buildPlacementZoneDefaultName(ContainerHostType.DOCKER, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpecDocker = createContainerHostSpec(
                Collections.singletonList(projectLinkDocker),
                ContainerHostType.DOCKER, name1, details1);

        ClusterDto clusterDocker = createCluster(hostSpecDocker);

        clusterDocker = getOneCluster(Service.getId(clusterDocker.documentSelfLink));
        assertNotNull(clusterDocker);
        assertEquals(name1, clusterDocker.name);
        assertEquals(details1, clusterDocker.details);

        ClusterDto patchClusterDto = new ClusterDto();
        patchClusterDto.name = name2;
        patchClusterDto.details = details2;
        patchClusterDto.documentSelfLink = clusterDocker.documentSelfLink;
        patchClusterDto = patchCluster(patchClusterDto);

        assertNotNull(patchClusterDto);
        assertEquals(name2, patchClusterDto.name);
        assertEquals(details2, patchClusterDto.details);

        clusterDocker = getOneCluster(Service.getId(clusterDocker.documentSelfLink));
        assertNotNull(clusterDocker);
        assertEquals(name2, clusterDocker.name);
        assertEquals(details2, clusterDocker.details);

    }

    @Test
    public void testPatchSingleHostClusterWithPublicAddress() throws Throwable {
        final String name = "test-cluster";
        final String details = "test-details";
        final String patchedAddress = "test-address";
        final String projectLink = buildProjectLink("test-vch-project");

        ContainerHostSpec hostSpec = createContainerHostSpec(
                Collections.singletonList(projectLink),
                ContainerHostType.VCH, name, details);

        ClusterDto cluster = createCluster(hostSpec);

        cluster = getOneCluster(Service.getId(cluster.documentSelfLink));
        assertNotNull(cluster);
        assertEquals(name, cluster.name);
        assertEquals(details, cluster.details);
        assertNull(cluster.publicAddress);

        ClusterDto patchClusterDto = new ClusterDto();
        patchClusterDto.publicAddress = patchedAddress;
        patchClusterDto.documentSelfLink = cluster.documentSelfLink;

        cluster = patchCluster(patchClusterDto);
        assertNotNull(patchClusterDto);
        assertEquals(patchedAddress, cluster.publicAddress);

        cluster = getOneCluster(Service.getId(cluster.documentSelfLink));
        assertNotNull(cluster);
        assertEquals(patchedAddress, cluster.publicAddress);
    }

    @Test
    public void testListHostsInCluster() throws Throwable {
        final String projectLinkDocker = buildProjectLink("test-docker-project");
        PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.DOCKER, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpecDocker = createContainerHostSpec(
                Collections.singletonList(projectLinkDocker),
                ContainerHostType.DOCKER);

        ClusterDto clusterDocker = createCluster(hostSpecDocker);

        List<String> hostsLinks = getHostsInOneClusterLinks(
                Service.getId(clusterDocker.documentSelfLink));
        assertEquals(1, hostsLinks.size());
        assertEquals(clusterDocker.nodeLinks, hostsLinks);

        Map<String, ComputeState> hostsStates = getHostsInOneClusterExpand(
                Service.getId(clusterDocker.documentSelfLink));
        assertEquals(1, hostsStates.size());
        ComputeState dockerNodeCs = hostsStates.get(clusterDocker.nodeLinks.get(0));
        assertNotNull(dockerNodeCs);
        assertEquals(clusterDocker.nodeLinks.get(0), dockerNodeCs.documentSelfLink);

        hostsLinks = getHostsInOneClusterLinks(
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_ID);
        assertTrue(hostsLinks.isEmpty());
        hostsStates = getHostsInOneClusterExpand(
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_ID);
        assertTrue(hostsStates.isEmpty());

        ContainerHostSpec hostSpecDocker1 = createContainerHostSpec(projectLinkDocker);
        String testHostAddress1 = "test-address-1";
        hostSpecDocker1.hostState.address = testHostAddress1;
        addHostInCluster(Service.getId(clusterDocker.documentSelfLink), hostSpecDocker1);

        ServiceDocumentQueryResult serviceDocumentQueryResult = getHostsInOneClusterQuery(
                Service.getId(clusterDocker.documentSelfLink), null);
        assertEquals(2, serviceDocumentQueryResult.documentLinks.size());
        assertNull(serviceDocumentQueryResult.nextPageLink);

        ServiceDocumentQueryResult serviceDocumentQueryResult1 = getHostsInOneClusterQuery(
                Service.getId(clusterDocker.documentSelfLink), new String[] { "$limit", "1" });
        assertEquals(1, serviceDocumentQueryResult1.documentLinks.size());
        assertNotNull(serviceDocumentQueryResult1.nextPageLink);
        ServiceDocumentQueryResult serviceDocumentQueryResult2 = getHostsInOneClusterQueryNextPage(
                serviceDocumentQueryResult1.nextPageLink);
        assertEquals(1, serviceDocumentQueryResult2.documentLinks.size());
        assertNull(serviceDocumentQueryResult2.nextPageLink);

        assertTrue(!serviceDocumentQueryResult1.documentLinks.get(0)
                .equals(serviceDocumentQueryResult2.documentLinks.get(0)));
        assertTrue(serviceDocumentQueryResult.documentLinks
                .contains(serviceDocumentQueryResult1.documentLinks.get(0)));
        assertTrue(serviceDocumentQueryResult.documentLinks
                .contains(serviceDocumentQueryResult2.documentLinks.get(0)));

        ServiceDocumentQueryResult serviceDocumentQueryResult3 = getHostsInOneClusterQuery(
                Service.getId(clusterDocker.documentSelfLink),
                new String[] { "$filter", "ALL_FIELDS eq '*1111*'" });
        assertEquals(0, serviceDocumentQueryResult3.documentLinks.size());
        serviceDocumentQueryResult3 = getHostsInOneClusterQuery(
                Service.getId(clusterDocker.documentSelfLink), new String[] { "$filter",
                        String.format("ALL_FIELDS eq '*%s*'", testHostAddress1) });
        assertEquals(1, serviceDocumentQueryResult3.documentLinks.size());
        ComputeState cs = Utils.fromJson(
                serviceDocumentQueryResult3.documents.values().iterator().next(),
                ComputeState.class);
        assertEquals(testHostAddress1, cs.address);

    }

    @Test
    public void testGetSingleHostsInCluster() throws Throwable {
        final String projectLinkDocker = buildProjectLink("test-docker-project");
        PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.DOCKER, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpecDocker = createContainerHostSpec(
                Collections.singletonList(projectLinkDocker),
                ContainerHostType.DOCKER);

        final String projectLinkVCH = buildProjectLink("test-vch-project");
        PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.VCH, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpecVCH = createContainerHostSpec(
                Collections.singletonList(projectLinkVCH),
                ContainerHostType.VCH);
        ClusterDto clusterDocker = createCluster(hostSpecDocker);
        ClusterDto clusterVCH = createCluster(hostSpecVCH);

        ComputeState cs = getSingleHostInOneCluster(
                Service.getId(clusterDocker.documentSelfLink),
                Service.getId(clusterDocker.nodeLinks.get(0)), false);

        assertNotNull(cs);
        assertEquals(clusterDocker.nodeLinks.get(0), cs.documentSelfLink);

        cs = getSingleHostInOneCluster(
                Service.getId(clusterVCH.documentSelfLink),
                Service.getId(clusterDocker.nodeLinks.get(0)), true);
        assertTrue(cs == null);
    }

    @Test
    public void testAddRemoveDockerHostInCluster() throws Throwable {
        final String projectLinkDocker = buildProjectLink("test-docker-project");
        PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.DOCKER, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpecDocker = createContainerHostSpec(
                Collections.singletonList(projectLinkDocker),
                ContainerHostType.DOCKER);

        final String projectLinkVCH = buildProjectLink("test-vch-project");
        PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.VCH, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpecVCH = createContainerHostSpec(
                Collections.singletonList(projectLinkVCH),
                ContainerHostType.VCH);
        ClusterDto clusterDocker = createCluster(hostSpecDocker);
        ClusterDto clusterVCH = createCluster(hostSpecVCH);

        clusterDocker = getOneCluster(Service.getId(clusterDocker.documentSelfLink));
        clusterVCH = getOneCluster(Service.getId(clusterVCH.documentSelfLink));

        assertEquals(1, clusterDocker.nodeLinks.size());
        assertEquals(1, clusterVCH.nodeLinks.size());
        assertEquals(0L, clusterDocker.totalMemory);

        ContainerHostSpec hostSpecDocker1 = createContainerHostSpec(projectLinkDocker);

        addHostInCluster(Service.getId(clusterDocker.documentSelfLink), hostSpecDocker1);

        clusterDocker = getOneCluster(Service.getId(clusterDocker.documentSelfLink));
        clusterVCH = getOneCluster(Service.getId(clusterVCH.documentSelfLink));

        assertEquals(2, clusterDocker.nodeLinks.size());
        assertEquals(1, clusterVCH.nodeLinks.size());

        //check host spec id is taken into account when adding host to existing cluster
        String hostSpecId = hostSpecDocker1.hostState.id;
        assertNotNull(hostSpecId);
        assertNotEquals("", hostSpecId);
        ComputeState host1 = getDocument(ComputeState.class, clusterDocker.nodeLinks.get(0));
        ComputeState host2 = getDocument(ComputeState.class, clusterDocker.nodeLinks.get(1));
        assertTrue(hostSpecId.equals(host1.id) || hostSpecId.equals(host2.id));

        deleteHostInCluster(Service.getId(clusterDocker.documentSelfLink),
                Service.getId(clusterDocker.nodeLinks.get(0)));

        clusterDocker = getOneCluster(Service.getId(clusterDocker.documentSelfLink));
        clusterVCH = getOneCluster(Service.getId(clusterVCH.documentSelfLink));

        assertEquals(1, clusterDocker.nodeLinks.size());
        assertEquals(1, clusterVCH.nodeLinks.size());
    }

    @Test(expected = LocalizableValidationException.class)
    public void testAddHostInVCHCluster() throws Throwable {

        final String projectLinkVCH = buildProjectLink("test-vch-project");
        PlacementZoneUtil
                .buildPlacementZoneDefaultName(ContainerHostType.VCH, COMPUTE_ADDRESS);

        ContainerHostSpec hostSpecVCH = createContainerHostSpec(
                Collections.singletonList(projectLinkVCH),
                ContainerHostType.VCH);
        ClusterDto clusterVCH = createCluster(hostSpecVCH);

        ContainerHostSpec hostSpecDocker1 = createContainerHostSpec(projectLinkVCH);

        addHostInCluster(Service.getId(clusterVCH.documentSelfLink), hostSpecDocker1);
    }

    @Test
    public void testFilterClustersByType() throws Throwable {
        final String projectLink = buildProjectLink("test-vch-project");

        ContainerHostSpec hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.VCH, "vch-host");
        createCluster(hostSpec);

        hostSpec = createContainerHostSpec(Collections.singletonList(projectLink),
                ContainerHostType.DOCKER, "docker-host");
        createCluster(hostSpec);

        String query = UriUtils.buildUriQuery("type", ClusterType.VCH.toString(),
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());
        URI clusterUri = UriUtils.buildUri(host, ClusterService.SELF_LINK, query);

        Set<Object> result = new HashSet<>();
        Operation get = Operation.createGet(clusterUri)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                    } else {
                        result.addAll(
                                o.getBody(ServiceDocumentQueryResult.class).documents.values());
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();
        assertEquals(1, result.size());
        ClusterDto resultCluster = (ClusterDto) result.iterator().next();
        assertEquals(ClusterType.VCH, resultCluster.type);
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
        ResourcePoolState placementZone = getDocument(ResourcePoolState.class, placementZoneLink);
        assertNotNull(placementZone);
        assertNotNull(placementZone.tenantLinks);
        assertTrue(placementZone.tenantLinks.contains(projectLink));
        assertEquals(expectedName, placementZone.name);

        // verify placement
        List<GroupResourcePlacementState> placements = getPlacementsForZone(placementZoneLink);
        assertNotNull(placements);
        assertEquals(1, placements.size());
        GroupResourcePlacementState placement = placements.iterator().next();
        assertNotNull(placement);
        assertEquals(placementZoneLink, placement.resourcePoolLink);
        assertNotNull(placement.tenantLinks);
        assertTrue(placement.tenantLinks.contains(projectLink));

        // verify compute state
        ComputeState hostState = getDocument(ComputeState.class,
                clusterDto.nodeLinks.iterator().next());
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

    private ContainerHostSpec createContainerHostSpec(final String projectLinkDocker) {
        ContainerHostSpec hostSpecDocker1 = new ContainerHostSpec();
        hostSpecDocker1.hostState = new ComputeState();
        hostSpecDocker1.hostState.id = UUID.randomUUID().toString();
        hostSpecDocker1.hostState.address = "test-address";
        hostSpecDocker1.hostState.tenantLinks = Collections.singletonList(projectLinkDocker);
        hostSpecDocker1.acceptCertificate = true;
        hostSpecDocker1.hostState.customProperties = new HashMap<>();
        hostSpecDocker1.hostState.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());
        hostSpecDocker1.hostState.customProperties.put(
                ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.DOCKER.name());
        hostSpecDocker1.hostState.customProperties.put(
                ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                "");
        return hostSpecDocker1;
    }

    private List<GroupResourcePlacementState> getPlacementsForZone(String placementZoneLink) {
        QueryTask queryTask = QueryUtil.buildPropertyQuery(GroupResourcePlacementState.class,
                GroupResourcePlacementState.FIELD_NAME_RESOURCE_POOL_LINK, placementZoneLink);

        QueryByPages<GroupResourcePlacementState> pages = new QueryByPages<>(host,
                queryTask.querySpec.query, GroupResourcePlacementState.class, null);

        return QueryTemplate.waitToComplete(pages.collectDocuments(Collectors.toList()));
    }

    private String buildProjectLink(String projectId) {
        return UriUtils.buildUriPath(ManagementUriParts.PROJECTS, projectId);
    }

    private ClusterDto createCluster(ContainerHostSpec hostSpec) {
        ArrayList<ClusterDto> result = new ArrayList<>(1);

        Operation create = Operation.createPost(host, ClusterService.SELF_LINK)
                .setReferer(host.getUri())
                .setBody(hostSpec)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to create cluster: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            result.add(o.getBody(ClusterDto.class));
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve created cluster DTO from response: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(create);
        host.testWait();

        assertEquals(1, result.size());
        ClusterDto dto = result.iterator().next();
        assertNotNull(dto);
        return dto;
    }

    private Map<String, ClusterDto> getClustersExpand() {
        return getClustersExpand("");
    }

    private Map<String, ClusterDto> getClustersExpand(String oDataQuery) {
        Map<String, ClusterDto> result = new HashMap<>();
        URI uri = UriUtils.buildUri(host,
                ClusterService.SELF_LINK, oDataQuery);
        uri = UriUtils.extendUriWithQuery(uri, "?expand", "true");
        Operation get = Operation.createGet(host, uri.getPath() + uri.getQuery())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get cluster: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            o.getBody(ServiceDocumentQueryResult.class).documents
                                    .entrySet().stream()
                                    .forEach(a -> {
                                        result.put(a.getKey(), (ClusterDto) a.getValue());
                                    });
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve created cluster DTO from response: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result;
    }

    private Map<String, ComputeState> getAllComputeExpand() {
        return getAllComputeExpand("");
    }

    private Map<String, ComputeState> getAllComputeExpand(String oDataQuery) {
        Map<String, ComputeState> result = new HashMap<>();
        URI uri = UriUtils.buildUri(host,
                ComputeService.FACTORY_LINK, oDataQuery);
        uri = UriUtils.extendUriWithQuery(uri, "?expand", "true");
        Operation get = Operation.createGet(host, uri.getPath() + uri.getQuery())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get computes: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        o.getBody(ServiceDocumentQueryResult.class).documents
                                .entrySet().stream()
                                .forEach(a -> {
                                    result.put(a.getKey(),
                                            Utils.fromJson(a.getValue(), ComputeState.class));
                                });
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result;
    }

    private List<String> getClustersLinks() {
        return getClustersLinks("");
    }

    private List<String> getClustersLinks(String oDataQuery) {
        List<String> result = new LinkedList<>();
        URI uri = UriUtils.buildUri(host,
                ClusterService.SELF_LINK, oDataQuery);
        Operation get = Operation.createGet(host, uri.getPath() + uri.getQuery())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get cluster: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            result.addAll(
                                    o.getBody(ServiceDocumentQueryResult.class).documentLinks);
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve created cluster DTO from response: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result;
    }

    private List<String> getPlacementZonesLinks() {
        List<String> result = new LinkedList<>();
        URI uri = UriUtils.buildUri(host,
                ResourcePoolService.FACTORY_LINK);
        Operation get = Operation.createGet(host, uri.getPath())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get cluster: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            result.addAll(
                                    o.getBody(ServiceDocumentQueryResult.class).documentLinks);
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve placement zones: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result;
    }

    private List<String> getPlacementsLinks() {
        List<String> result = new LinkedList<>();
        URI uri = UriUtils.buildUri(host,
                GroupResourcePlacementService.FACTORY_LINK);
        Operation get = Operation.createGet(host, uri.getPath())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get cluster: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            result.addAll(
                                    o.getBody(ServiceDocumentQueryResult.class).documentLinks);
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve placements: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result;
    }

    private ClusterDto getOneCluster(String clusterId) {
        List<ClusterDto> result = new LinkedList<>();
        String pathSB = UriUtils.buildUriPath(ClusterService.SELF_LINK, clusterId);
        URI uri = UriUtils.buildUri(host, pathSB);
        Operation get = Operation.createGet(host, uri.getPath())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get cluster: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            result.add(o.getBody(ClusterDto.class));
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve created cluster DTO from response: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result.get(0);
    }

    private void deleteCluster(String clusterId) {

        String pathSB = UriUtils.buildUriPath(ClusterService.SELF_LINK, clusterId);
        URI uri = UriUtils.buildUri(host, pathSB);
        Operation get = Operation.createDelete(host, uri.getPath())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get cluster: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve created cluster DTO from response: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();
    }

    private ClusterDto patchCluster(ClusterDto clusterDto) {
        List<ClusterDto> result = new LinkedList<>();
        URI uri = UriUtils.buildUri(host, clusterDto.documentSelfLink);
        Operation patch = Operation.createPatch(host, uri.getPath())
                .setReferer(host.getUri())
                .setBody(clusterDto)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get cluster: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            result.add(o.getBody(ClusterDto.class));
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve created cluster DTO from response: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(patch);
        host.testWait();

        return result.get(0);
    }

    private List<String> getHostsInOneClusterLinks(String clusterId) {
        List<String> result = new LinkedList<>();
        String pathHostsInCluster = UriUtils.buildUriPath(ClusterService.SELF_LINK, clusterId,
                "hosts");
        URI uri = UriUtils.buildUri(host, pathHostsInCluster);
        Operation get = Operation.createGet(host, uri.getPath())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get hosts in cluster: %s",
                                Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            result.addAll(
                                    o.getBody(ServiceDocumentQueryResult.class).documentLinks);
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve hosts in cluster from response: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result;
    }

    private Map<String, ComputeState> getHostsInOneClusterExpand(String clusterId) {
        Map<String, ComputeState> result = new HashMap<>();
        String pathHostsInCluster = UriUtils.buildUriPath(ClusterService.SELF_LINK, clusterId,
                "hosts");
        URI uri = UriUtils.buildUri(host, pathHostsInCluster);
        uri = UriUtils.extendUriWithQuery(uri, "?expand", "true");
        Operation get = Operation.createGet(host, uri.getPath() + uri.getQuery())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get hosts in cluster: %s",
                                Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            if (o.getBody(ServiceDocumentQueryResult.class).documents != null) {
                                o.getBody(ServiceDocumentQueryResult.class).documents
                                        .entrySet().stream()
                                        .forEach(a -> {
                                            result.put(a.getKey(), Utils.fromJson(a.getValue(),
                                                    ComputeState.class));
                                        });
                            }
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve hosts in cluster from response: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result;
    }

    private ServiceDocumentQueryResult getHostsInOneClusterQuery(String clusterId,
            String[] filter) {
        List<ServiceDocumentQueryResult> result = new LinkedList<>();
        String pathHostsInCluster = UriUtils.buildUriPath(ClusterService.SELF_LINK, clusterId,
                "hosts");
        URI uri = UriUtils.buildUri(host, pathHostsInCluster);
        uri = UriUtils.extendUriWithQuery(uri, "?expand", "true");
        if (filter != null) {
            uri = UriUtils.extendUriWithQuery(uri, filter);
        }
        Operation get = Operation.createGet(host, uri.getPath() + uri.getQuery())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get hosts in cluster: %s",
                                Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        result.add(o.getBody(ServiceDocumentQueryResult.class));
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result.get(0);
    }

    private ServiceDocumentQueryResult getHostsInOneClusterQueryNextPage(String nextPagePath) {
        List<ServiceDocumentQueryResult> result = new LinkedList<>();

        Operation get = Operation.createGet(host, nextPagePath)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get hosts in cluster: %s",
                                Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        result.add(o.getBody(QueryTask.class).results);
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result.get(0);
    }

    private ComputeState getSingleHostInOneCluster(String clusterId, String hostId,
            boolean expectedToFail) {
        List<ComputeState> result = new LinkedList<>();
        String pathHostsInCluster = UriUtils.buildUriPath(ClusterService.SELF_LINK, clusterId,
                ClusterService.HOSTS_URI_PATH_SEGMENT, hostId);
        URI uri = UriUtils.buildUri(host, pathHostsInCluster);
        Operation get = Operation.createGet(host, uri.getPath())
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to get single hosts in cluster: %s",
                                Utils.toString(ex));
                        if (expectedToFail && ex.getMessage().contains(String.format(
                                ClusterService.HOST_NOT_IN_THIS_CLUSTER_EXCEPTION_TEMPLATE, hostId,
                                clusterId))) {
                            result.add(null);
                            host.completeIteration();
                        } else {
                            host.failIteration(ex);
                        }
                    } else {
                        try {
                            result.add(o.getBody(ComputeState.class));
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve single hosts in cluster from response: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();

        return result.get(0);
    }

    private String addHostInCluster(String clusterId, ContainerHostSpec ch) {
        List<String> result = new LinkedList<>();
        String pathHostsInCluster = UriUtils.buildUriPath(ClusterService.SELF_LINK, clusterId,
                "hosts");
        URI uri = UriUtils.buildUri(host, pathHostsInCluster);
        Operation post = Operation.createPost(host, uri.getPath())
                .setReferer(host.getUri())
                .setBody(ch)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to add hosts in cluster: %s",
                                Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        result.add(o.toString());
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(post);
        host.testWait();

        return result.get(0);
    }

    private void deleteHostInCluster(String clusterId, String hostId) {
        String pathSB = UriUtils.buildUriPath(ClusterService.SELF_LINK, clusterId,
                ClusterService.HOSTS_URI_PATH_SEGMENT, hostId);
        Operation get = Operation.createDelete(host, pathSB)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to delete host in cluster: %s",
                                Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(get);
        host.testWait();
    }

    private ContainerHostSpec createContainerHostSpec(List<String> tenantLinks,
            ContainerHostType hostType) throws Throwable {
        return createContainerHostSpec(tenantLinks,
                hostType, null, null);
    }

    private ContainerHostSpec createContainerHostSpec(List<String> tenantLinks,
            ContainerHostType hostType, String clusterName, String clusterDetails)
            throws Throwable {
        ContainerHostSpec ch = new ContainerHostSpec();
        ch.hostState = createComputeState(hostType, ComputeService.PowerState.ON, tenantLinks,
                clusterName, clusterDetails);
        return ch;
    }

    private ContainerHostSpec createContainerHostSpec(List<String> tenantLinks,
            ContainerHostType hostType, String address)
            throws Throwable {
        ContainerHostSpec ch = new ContainerHostSpec();
        ch.hostState = createComputeState(hostType, ComputeService.PowerState.ON, tenantLinks,
                null, null, address);
        return ch;
    }

    private ComputeState createComputeState(ContainerHostType hostType,
            ComputeService.PowerState hostState, List<String> tenantLinks, String clusterName,
            String clusterDetails) throws Throwable {
        return createComputeState(hostType, hostState, tenantLinks, clusterName, clusterDetails,
                COMPUTE_ADDRESS);
    }

    private ComputeState createComputeState(ContainerHostType hostType,
            ComputeService.PowerState hostState, List<String> tenantLinks, String clusterName,
            String clusterDetails, String address

    ) throws Throwable {
        ComputeState cs = new ComputeState();
        cs.id = UUID.randomUUID().toString();
        cs.address = address;
        cs.powerState = hostState;
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ADAPTER_DOCKER_TYPE_ID);
        cs.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                hostType.toString());
        cs.customProperties.put(MockDockerHostAdapterService.CONTAINER_HOST_TYPE_PROP_NAME,
                hostType.toString());
        cs.tenantLinks = new ArrayList<>(tenantLinks);

        if (clusterDetails != null && !clusterDetails.isEmpty()) {
            cs.customProperties.put(
                    ClusterService.CLUSTER_DETAILS_CUSTOM_PROP,
                    clusterDetails);
        }
        if (clusterName != null) {
            cs.customProperties.put(
                    ClusterService.CLUSTER_NAME_CUSTOM_PROP,
                    clusterName);
        }
        return cs;
    }
}
