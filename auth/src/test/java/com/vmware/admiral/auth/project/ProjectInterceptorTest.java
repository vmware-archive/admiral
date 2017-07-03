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
import com.vmware.xenon.common.UriUtils;

public class ProjectInterceptorTest extends AuthBaseTest {

    private ProjectState project;

    @Before
    public void setup() throws Throwable {
        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
        project = createProject("test-project");

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
    public void testCreateContainerDescriptionIntercept() {
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
