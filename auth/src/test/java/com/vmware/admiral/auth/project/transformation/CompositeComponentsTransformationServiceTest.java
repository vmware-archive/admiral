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

package com.vmware.admiral.auth.project.transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.network.ContainerNetworkFactoryService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeFactoryService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

public class CompositeComponentsTransformationServiceTest extends AuthBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerVolumeService.FACTORY_LINK);
        waitForServiceAvailability(ContainerVolumeFactoryService.SELF_LINK);
        waitForServiceAvailability(CompositeComponentsTransformationService.SELF_LINK);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
    }

    @Test
    public void testNoApplications() throws Throwable {
        List<String> links = getDocumentLinksOfType(CompositeComponent.class);
        Assert.assertTrue(links.isEmpty());
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, CompositeComponentsTransformationService.SELF_LINK), false,
                Service.Action.POST);
    }

    @Test
    public void testSingleApplicationOneContainer() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        ContainerState containerState = createContainer(tenantLinks);
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);
        List<String> componentLinks = new ArrayList<>();
        componentLinks.add(containerState.documentSelfLink);
        CompositeComponent application = createCompositeComponent(componentLinks);
        application = doPost(application, CompositeComponentFactoryService.SELF_LINK);

        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, CompositeComponentsTransformationService.SELF_LINK), false,
                Service.Action.POST);
        application = getDocument(CompositeComponent.class, application.documentSelfLink);
        Assert.assertTrue(application.tenantLinks.size() == 1);
        Assert.assertTrue(application.tenantLinks.containsAll(tenantLinks));
    }

    @Test
    public void testSingleApplicationMultipleContainers() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");

        ContainerState containerState1 = createContainer(tenantLinks);
        containerState1 = doPost(containerState1, ContainerFactoryService.SELF_LINK);
        tenantLinks.add("project2");
        ContainerState containerState2 = createContainer(tenantLinks);
        containerState2 = doPost(containerState2, ContainerFactoryService.SELF_LINK);

        List<String> componentLinks = new ArrayList<>();
        componentLinks.add(containerState1.documentSelfLink);
        componentLinks.add(containerState2.documentSelfLink);
        CompositeComponent application = createCompositeComponent(componentLinks);
        application = doPost(application, CompositeComponentFactoryService.SELF_LINK);

        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, CompositeComponentsTransformationService.SELF_LINK), false,
                Service.Action.POST);

        application = getDocument(CompositeComponent.class, application.documentSelfLink);
        Assert.assertTrue(application.tenantLinks.size() == 2);
        Assert.assertTrue(application.tenantLinks.containsAll(tenantLinks));
    }

    @Test
    public void testSingleApplicationDifferentComponents() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        tenantLinks.add("project2");

        ContainerState containerState1 = createContainer(tenantLinks);
        containerState1 = doPost(containerState1, ContainerFactoryService.SELF_LINK);
        ContainerState containerState2 = createContainer(tenantLinks);
        containerState2 = doPost(containerState2, ContainerFactoryService.SELF_LINK);

        tenantLinks.add("project3");
        ContainerNetworkState network = createNetwork(tenantLinks);
        network = doPost(network, ContainerNetworkFactoryService.SELF_LINK);

        ContainerVolumeState volume = createVolume(tenantLinks);
        volume = doPost(volume, ContainerVolumeFactoryService.SELF_LINK);

        List<String> componentLinks = new ArrayList<>();
        componentLinks.add(containerState1.documentSelfLink);
        componentLinks.add(containerState2.documentSelfLink);
        componentLinks.add(network.documentSelfLink);
        componentLinks.add(volume.documentSelfLink);
        CompositeComponent application = createCompositeComponent(componentLinks);
        application = doPost(application, CompositeComponentFactoryService.SELF_LINK);

        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, CompositeComponentsTransformationService.SELF_LINK), false,
                Service.Action.POST);

        application = getDocument(CompositeComponent.class, application.documentSelfLink);
        Assert.assertTrue(application.tenantLinks.size() == 3);
        Assert.assertTrue(application.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(application.tenantLinks.containsAll(containerState1.tenantLinks));
        Assert.assertTrue(application.tenantLinks.containsAll(network.tenantLinks));
        Assert.assertTrue(application.tenantLinks.containsAll(volume.tenantLinks));
    }

    @Test
    public void testMultipleApplications() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        tenantLinks.add("project2");

        ContainerState containerState1 = createContainer(tenantLinks);
        containerState1 = doPost(containerState1, ContainerFactoryService.SELF_LINK);
        ContainerState containerState2 = createContainer(tenantLinks);

        tenantLinks.add("project3");
        containerState2 = doPost(containerState2, ContainerFactoryService.SELF_LINK);

        ContainerNetworkState network = createNetwork(tenantLinks);
        network = doPost(network, ContainerNetworkFactoryService.SELF_LINK);

        ContainerVolumeState volume = createVolume(tenantLinks);
        volume = doPost(volume, ContainerVolumeFactoryService.SELF_LINK);

        List<String> componentLinks = new ArrayList<>();
        componentLinks.add(containerState1.documentSelfLink);

        CompositeComponent application = createCompositeComponent(componentLinks);
        application = doPost(application, CompositeComponentFactoryService.SELF_LINK);

        componentLinks = new ArrayList<>();
        componentLinks.add(containerState2.documentSelfLink);
        componentLinks.add(network.documentSelfLink);
        componentLinks.add(volume.documentSelfLink);

        CompositeComponent application2 = createCompositeComponent(componentLinks);
        application2 = doPost(application2, CompositeComponentFactoryService.SELF_LINK);

        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, CompositeComponentsTransformationService.SELF_LINK), false,
                Service.Action.POST);

        application = getDocument(CompositeComponent.class, application.documentSelfLink);
        application2 = getDocument(CompositeComponent.class, application2.documentSelfLink);
        Assert.assertTrue(application.tenantLinks.size() == 2);
        Assert.assertTrue(application.tenantLinks.containsAll(containerState1.tenantLinks));

        Assert.assertTrue(application2.tenantLinks.size() == 3);
        Assert.assertTrue(application2.tenantLinks.containsAll(containerState2.tenantLinks));
        Assert.assertTrue(application2.tenantLinks.containsAll(network.tenantLinks));
        Assert.assertTrue(application2.tenantLinks.containsAll(volume.tenantLinks));
    }

    private CompositeComponent createCompositeComponent(List<String> componentLinks) {
        CompositeComponent application = new CompositeComponent();
        application.name = UUID.randomUUID().toString();
        application.componentLinks = componentLinks;
        return application;
    }

    private ContainerState createContainer(List<String> tenantLink) {
        ContainerState containerState = new ContainerState();
        containerState.id = UUID.randomUUID().toString();
        containerState.name = UUID.randomUUID().toString();
        containerState.parentLink = "Test";
        containerState.tenantLinks = tenantLink;
        return containerState;
    }

    private ContainerNetworkState createNetwork(List<String> tenantLinks) {
        ContainerNetworkState network = new ContainerNetworkState();
        network.id = UUID.randomUUID().toString();
        network.name = "test";
        network.tenantLinks = tenantLinks;
        return network;
    }

    private ContainerVolumeState createVolume(List<String> tenantLink) {
        ContainerVolumeState volume = new ContainerVolumeState();
        volume.id = UUID.randomUUID().toString();
        volume.tenantLinks = tenantLink;
        return volume;
    }
}
