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

package com.vmware.admiral.auth.project;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState.PowerState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;

public class ProjectInterceptorTest extends AuthBaseTest {

    private ProjectState project;
    private ProjectState testProject1;
    private ProjectState testProject2;

    @Before
    public void setup() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        project = createProject("test-project");
        testProject1 = createProject("test-project1");
        testProject2 = createProject("test-project2");

        waitForServiceAvailability(ClusterService.SELF_LINK);
        waitForServiceAvailability(ContainerHostService.SELF_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);

        MockDockerHostAdapterService dockerAdapterService = new MockDockerHostAdapterService();
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerHostAdapterService.class)), dockerAdapterService);

        waitForServiceAvailability(MockDockerHostAdapterService.SELF_LINK);
    }

    @Test
    public void testCreateContainerDescriptionIntercept() throws Throwable {
        ContainerDescription desc = new ContainerDescription();
        desc.name = "test";
        desc.image = "test";

        ContainerDescription newDesc = doPostWithProjectHeader(desc, ContainerDescriptionService
                .FACTORY_LINK, project.documentSelfLink, ContainerDescription.class);

        assertTenantLinks(newDesc, project.documentSelfLink);
        assertEquals(desc.name, newDesc.name);
        assertEquals(desc.image, newDesc.image);
    }

    @Test
    public void testGetContainerDescription() throws Throwable {
        ContainerDescription desc1 = new ContainerDescription();
        desc1.name = "test";
        desc1.image = "test";

        ContainerDescription desc2 = new ContainerDescription();
        desc2.name = "test1";
        desc2.image = "test1";

        desc1 = doPostWithProjectHeader(desc1, ContainerDescriptionService
                .FACTORY_LINK, testProject1.documentSelfLink, ContainerDescription.class);
        assertNotNull(desc1.documentSelfLink);

        desc2 = doPostWithProjectHeader(desc2, ContainerDescriptionService
                .FACTORY_LINK, testProject2.documentSelfLink, ContainerDescription.class);
        assertNotNull(desc2.documentSelfLink);

        ServiceDocumentQueryResult project1Docs = getDocumentsWithinProject(
                ContainerDescriptionService.FACTORY_LINK, testProject1.documentSelfLink);
        assertEquals(1, project1Docs.documentLinks.size());
        assertTrue(project1Docs.documentLinks.contains(desc1.documentSelfLink));

        ServiceDocumentQueryResult project2Docs = getDocumentsWithinProject(
                ContainerDescriptionService.FACTORY_LINK, testProject2.documentSelfLink);
        assertEquals(1, project2Docs.documentLinks.size());
        assertTrue(project2Docs.documentLinks.contains(desc2.documentSelfLink));
    }

    @Test
    public void testCreateContainerServiceIntercept() {
        ContainerState state = new ContainerState();
        state.name = "test";
        state.image = "test";
        ContainerState doc = doPostWithProjectHeader(state, ContainerFactoryService.SELF_LINK,
                project.documentSelfLink, ContainerState.class);

        assertTenantLinks(doc, project.documentSelfLink);
        assertEquals(state.name, doc.name);
        assertEquals(state.image, doc.image);
    }

    @Test
    public void testGetContainerService() {
        ContainerState state1 = new ContainerState();
        state1.name = "test";
        state1.image = "test";
        state1 = doPostWithProjectHeader(state1, ContainerFactoryService.SELF_LINK,
                testProject1.documentSelfLink, ContainerState.class);


        ContainerState state2 = new ContainerState();
        state2.name = "test";
        state2.image = "test";
        state2 = doPostWithProjectHeader(state2, ContainerFactoryService.SELF_LINK,
                testProject2.documentSelfLink, ContainerState.class);

        ServiceDocumentQueryResult project1Docs = getDocumentsWithinProject(
                ContainerFactoryService.SELF_LINK, testProject1.documentSelfLink);
        assertEquals(1, project1Docs.documentLinks.size());
        assertTrue(project1Docs.documentLinks.contains(state1.documentSelfLink));

        ServiceDocumentQueryResult project2Docs = getDocumentsWithinProject(
                ContainerFactoryService.SELF_LINK, testProject2.documentSelfLink);
        assertEquals(1, project2Docs.documentLinks.size());
        assertTrue(project2Docs.documentLinks.contains(state2.documentSelfLink));
    }

    @Test
    public void testCreateContainerNetworkDescriptionIntercept() {
        ContainerNetworkDescription state = new ContainerNetworkDescription();
        state.name = "test";
        state.externalName = "test";
        ContainerNetworkDescription doc = doPostWithProjectHeader(state,
                ContainerNetworkDescriptionService.FACTORY_LINK, project.documentSelfLink,
                ContainerNetworkDescription.class);

        assertTenantLinks(doc, project.documentSelfLink);
        assertEquals(state.name, doc.name);
        assertEquals(state.externalName, doc.externalName);
    }

    @Test
    public void testGetContainerNetworkDescription() {
        ContainerNetworkDescription state1 = new ContainerNetworkDescription();
        state1.name = "test";
        state1.externalName = "test";
        state1 = doPostWithProjectHeader(state1,
                ContainerNetworkDescriptionService.FACTORY_LINK, testProject1.documentSelfLink,
                ContainerNetworkDescription.class);

        ContainerNetworkDescription state2 = new ContainerNetworkDescription();
        state2.name = "test";
        state2.externalName = "test";
        state2 = doPostWithProjectHeader(state2,
                ContainerNetworkDescriptionService.FACTORY_LINK, testProject2.documentSelfLink,
                ContainerNetworkDescription.class);

        ServiceDocumentQueryResult project1Docs = getDocumentsWithinProject(
                ContainerNetworkDescriptionService.FACTORY_LINK, testProject1.documentSelfLink);
        assertEquals(1, project1Docs.documentLinks.size());
        assertTrue(project1Docs.documentLinks.contains(state1.documentSelfLink));

        ServiceDocumentQueryResult project2Docs = getDocumentsWithinProject(
                ContainerNetworkDescriptionService.FACTORY_LINK, testProject2.documentSelfLink);
        assertEquals(1, project2Docs.documentLinks.size());
        assertTrue(project2Docs.documentLinks.contains(state2.documentSelfLink));

    }

    @Test
    public void testCreateContainerNetworkIntercept() {
        ContainerNetworkState state = new ContainerNetworkState();
        state.name = "test";
        state.powerState = PowerState.CONNECTED;

        ContainerNetworkState doc = doPostWithProjectHeader(state, ContainerNetworkService
                .FACTORY_LINK, project.documentSelfLink, ContainerNetworkState.class);

        assertTenantLinks(doc, project.documentSelfLink);
        assertEquals(state.name, doc.name);
        assertEquals(state.powerState, doc.powerState);
    }

    @Test
    public void testGetContainerNetwork() {
        ContainerNetworkState state1 = new ContainerNetworkState();
        state1.name = "test";
        state1.powerState = PowerState.CONNECTED;

        ContainerNetworkState state2 = new ContainerNetworkState();
        state2.name = "test";
        state2.powerState = PowerState.CONNECTED;

        state1 = doPostWithProjectHeader(state1, ContainerNetworkService
                .FACTORY_LINK, testProject1.documentSelfLink, ContainerNetworkState.class);
        assertNotNull(state1.documentSelfLink);

        state2 = doPostWithProjectHeader(state2, ContainerNetworkService
                .FACTORY_LINK, testProject2.documentSelfLink, ContainerNetworkState.class);
        assertNotNull(state2.documentSelfLink);

        ServiceDocumentQueryResult project1Docs = getDocumentsWithinProject(
                ContainerNetworkService.FACTORY_LINK, testProject1.documentSelfLink);
        assertEquals(1, project1Docs.documentLinks.size());
        assertTrue(project1Docs.documentLinks.contains(state1.documentSelfLink));

        ServiceDocumentQueryResult project2Docs = getDocumentsWithinProject(
                ContainerNetworkService.FACTORY_LINK, testProject2.documentSelfLink);
        assertEquals(1, project2Docs.documentLinks.size());
        assertTrue(project2Docs.documentLinks.contains(state2.documentSelfLink));
    }

    @Test
    public void testCreateContainerVolumeDescriptionIntercept() {
        ContainerVolumeDescription state = new ContainerVolumeDescription();
        state.name = "test";
        state.externalName = "test";

        ContainerVolumeDescription doc = doPostWithProjectHeader(state,
                ContainerVolumeDescriptionService.FACTORY_LINK, project.documentSelfLink,
                ContainerVolumeDescription.class);

        assertTenantLinks(doc, project.documentSelfLink);
        assertEquals(state.name, doc.name);
        assertEquals(state.externalName, doc.externalName);
    }

    @Test
    public void testGetContainerVolumeDescription() {
        ContainerVolumeDescription state1 = new ContainerVolumeDescription();
        state1.name = "test";
        state1.externalName = "test";

        ContainerVolumeDescription state2 = new ContainerVolumeDescription();
        state2.name = "test";
        state2.externalName = "test";

        state1 = doPostWithProjectHeader(state1,
                ContainerVolumeDescriptionService.FACTORY_LINK, testProject1.documentSelfLink,
                ContainerVolumeDescription.class);

        state2 = doPostWithProjectHeader(state2,
                ContainerVolumeDescriptionService.FACTORY_LINK, testProject2.documentSelfLink,
                ContainerVolumeDescription.class);

        ServiceDocumentQueryResult project1Docs = getDocumentsWithinProject(
                ContainerVolumeDescriptionService.FACTORY_LINK, testProject1.documentSelfLink);
        assertEquals(1, project1Docs.documentLinks.size());
        assertTrue(project1Docs.documentLinks.contains(state1.documentSelfLink));

        ServiceDocumentQueryResult project2Docs = getDocumentsWithinProject(
                ContainerVolumeDescriptionService.FACTORY_LINK, testProject2.documentSelfLink);
        assertEquals(1, project2Docs.documentLinks.size());
        assertTrue(project2Docs.documentLinks.contains(state2.documentSelfLink));
    }

    @Test
    public void testCreateContainerVolumeIntercept() {
        ContainerVolumeState state = new ContainerVolumeState();
        state.name = "test";
        state.external = true;

        ContainerVolumeState doc = doPostWithProjectHeader(state, ContainerVolumeService
                .FACTORY_LINK, project.documentSelfLink, ContainerVolumeState.class);

        assertTenantLinks(doc, project.documentSelfLink);
        assertEquals(state.name, doc.name);
        assertEquals(state.external, doc.external);
    }

    @Test
    public void testGetContainerVolume() {
        ContainerVolumeState state1 = new ContainerVolumeState();
        state1.name = "test";
        state1.external = true;

        ContainerVolumeState state2 = new ContainerVolumeState();
        state2.name = "test";
        state2.external = true;

        state1 = doPostWithProjectHeader(state1, ContainerVolumeService
                .FACTORY_LINK, testProject1.documentSelfLink, ContainerVolumeState.class);

        state2 = doPostWithProjectHeader(state2, ContainerVolumeService
                .FACTORY_LINK, testProject2.documentSelfLink, ContainerVolumeState.class);

        ServiceDocumentQueryResult project1Docs = getDocumentsWithinProject(
                ContainerVolumeService.FACTORY_LINK, testProject1.documentSelfLink);
        assertEquals(1, project1Docs.documentLinks.size());
        assertTrue(project1Docs.documentLinks.contains(state1.documentSelfLink));

        ServiceDocumentQueryResult project2Docs = getDocumentsWithinProject(
                ContainerVolumeService.FACTORY_LINK, testProject2.documentSelfLink);
        assertEquals(1, project2Docs.documentLinks.size());
        assertTrue(project2Docs.documentLinks.contains(state2.documentSelfLink));
    }

    @Test
    public void testCreateCompositeDescriptionIntercept() {
        CompositeDescription state = new CompositeDescription();
        state.name = "test";
        state.descriptionLinks = Collections.singletonList("test");

        CompositeDescription doc = doPostWithProjectHeader(state,
                CompositeDescriptionFactoryService.SELF_LINK, project.documentSelfLink,
                CompositeDescription.class);

        assertTenantLinks(doc, project.documentSelfLink);
        assertEquals(state.name, doc.name);
        assertEquals(state.descriptionLinks, doc.descriptionLinks);
    }

    @Test
    public void testGetCompositeDescription() {
        CompositeDescription state1 = new CompositeDescription();
        state1.name = "test";
        state1.descriptionLinks = Collections.singletonList("test");

        CompositeDescription state2 = new CompositeDescription();
        state2.name = "test";
        state2.descriptionLinks = Collections.singletonList("test");

        state1 = doPostWithProjectHeader(state1,
                CompositeDescriptionFactoryService.SELF_LINK, testProject1.documentSelfLink,
                CompositeDescription.class);

        state2 = doPostWithProjectHeader(state2,
                CompositeDescriptionFactoryService.SELF_LINK, testProject2.documentSelfLink,
                CompositeDescription.class);

        ServiceDocumentQueryResult project1Docs = getDocumentsWithinProject(
                CompositeDescriptionFactoryService.SELF_LINK, testProject1.documentSelfLink);
        assertEquals(1, project1Docs.documentLinks.size());
        assertTrue(project1Docs.documentLinks.contains(state1.documentSelfLink));

        ServiceDocumentQueryResult project2Docs = getDocumentsWithinProject(
                CompositeDescriptionFactoryService.SELF_LINK, testProject2.documentSelfLink);
        assertEquals(1, project2Docs.documentLinks.size());
        assertTrue(project2Docs.documentLinks.contains(state2.documentSelfLink));
    }

    @Test
    public void testCreateCompositeComponentIntercept() {
        CompositeComponent state = new CompositeComponent();
        state.name = "test";
        state.componentLinks = Collections.singletonList("test");

        CompositeComponent doc = doPostWithProjectHeader(state, CompositeComponentFactoryService
                .SELF_LINK, project.documentSelfLink, CompositeComponent.class);

        assertTenantLinks(doc, project.documentSelfLink);
        assertEquals(state.name, doc.name);
        assertEquals(state.componentLinks, doc.componentLinks);
    }

    @Test
    public void testGetCompositeComponent() {
        CompositeComponent state1 = new CompositeComponent();
        state1.name = "test";
        state1.componentLinks = Collections.singletonList("test");

        CompositeComponent state2 = new CompositeComponent();
        state2.name = "test";
        state2.componentLinks = Collections.singletonList("test");

        state1 = doPostWithProjectHeader(state1, CompositeComponentFactoryService
                .SELF_LINK, testProject1.documentSelfLink, CompositeComponent.class);

        state2 = doPostWithProjectHeader(state2, CompositeComponentFactoryService
                .SELF_LINK, testProject2.documentSelfLink, CompositeComponent.class);

        ServiceDocumentQueryResult project1Docs = getDocumentsWithinProject(
                CompositeComponentFactoryService.SELF_LINK, testProject1.documentSelfLink);
        assertEquals(1, project1Docs.documentLinks.size());
        assertTrue(project1Docs.documentLinks.contains(state1.documentSelfLink));

        ServiceDocumentQueryResult project2Docs = getDocumentsWithinProject(
                CompositeComponentFactoryService.SELF_LINK, testProject2.documentSelfLink);
        assertEquals(1, project2Docs.documentLinks.size());
        assertTrue(project2Docs.documentLinks.contains(state2.documentSelfLink));
    }

    @Test
    public void testCreateDocumentWithoutInterceptorWithoutHeader() throws Throwable {
        CompositeComponent state = new CompositeComponent();
        state.name = "test";
        state.componentLinks = Collections.singletonList("test");

        CompositeComponent doc = doPost(state, CompositeComponentFactoryService.SELF_LINK);

        assertEquals(state.name, doc.name);
        assertEquals(state.componentLinks, doc.componentLinks);
    }

    @Test
    public void testCreateClusterWithIntercept() throws Throwable {
        ContainerHostSpec hostSpec = new ContainerHostSpec();
        hostSpec.hostState = new ComputeState();
        hostSpec.hostState.id = UUID.randomUUID().toString();
        hostSpec.hostState.address = "test";
        hostSpec.hostState.customProperties = new HashMap<>();
        hostSpec.hostState.customProperties.put(ContainerHostService
                        .HOST_DOCKER_ADAPTER_TYPE_PROP_NAME, "API");
        hostSpec.hostState.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                "DOCKER");

        ClusterDto dto = doPostWithProjectHeader(hostSpec, ClusterService.SELF_LINK, project
                .documentSelfLink, ClusterDto.class);

        ComputeState computeState = getDocument(ComputeState.class, dto.nodeLinks.get(0));

        assertTrue(computeState.tenantLinks.contains(project.documentSelfLink));
    }

    @Test
    public void testCreateCluster() throws Throwable {
        ContainerHostSpec hostSpec1 = new ContainerHostSpec();
        hostSpec1.hostState = new ComputeState();
        hostSpec1.hostState.id = UUID.randomUUID().toString();
        hostSpec1.hostState.address = "test1";
        hostSpec1.hostState.customProperties = new HashMap<>();
        hostSpec1.hostState.customProperties.put(ContainerHostService
                .HOST_DOCKER_ADAPTER_TYPE_PROP_NAME, "API");
        hostSpec1.hostState.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                "DOCKER");

        ClusterDto dto1 = doPostWithProjectHeader(hostSpec1, ClusterService.SELF_LINK, testProject1
                .documentSelfLink, ClusterDto.class);

        ContainerHostSpec hostSpec2 = new ContainerHostSpec();
        hostSpec2.hostState = new ComputeState();
        hostSpec2.hostState.id = UUID.randomUUID().toString();
        hostSpec2.hostState.address = "test2";
        hostSpec2.hostState.customProperties = new HashMap<>();
        hostSpec2.hostState.customProperties.put(ContainerHostService
                .HOST_DOCKER_ADAPTER_TYPE_PROP_NAME, "API");
        hostSpec2.hostState.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                "DOCKER");

        ClusterDto dto2 = doPostWithProjectHeader(hostSpec2, ClusterService.SELF_LINK, testProject2
                .documentSelfLink, ClusterDto.class);

        ServiceDocumentQueryResult project1Docs = getDocumentsWithinProject(
                ClusterService.SELF_LINK, testProject1.documentSelfLink);
        assertEquals(1, project1Docs.documentLinks.size());
        assertTrue(project1Docs.documentLinks.contains(dto1.documentSelfLink));

        ServiceDocumentQueryResult project2Docs = getDocumentsWithinProject(
                ClusterService.SELF_LINK, testProject2.documentSelfLink);
        assertEquals(1, project2Docs.documentLinks.size());
        assertTrue(project2Docs.documentLinks.contains(dto2.documentSelfLink));
    }


    private static void assertTenantLinks(ResourceState state, String... projectLinks) {
        for (String projectLink : projectLinks) {
            if (!state.tenantLinks.contains(projectLink)) {
                throw new RuntimeException("Created state should have tenantLink " + projectLink);
            }
        }
    }

    private static void assertTenantLinks(MultiTenantDocument state, String... projectLinks) {
        for (String projectLink : projectLinks) {
            if (!state.tenantLinks.contains(projectLink)) {
                throw new RuntimeException("Created state should have tenantLink " + projectLink);
            }
        }
    }
}
