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
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

public class ContainersTransformationServiceTest extends AuthBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainersTransformationService.SELF_LINK);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
    }

    @Test
    public void testNoContainersNoHosts() throws Throwable {
        List<String> links = getDocumentLinksOfType(ComputeState.class);
        Assert.assertTrue(links.isEmpty());
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainersTransformationService.SELF_LINK), false,
                Service.Action.POST);
    }

    @Test
    public void testHostwithoutContainers() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        ComputeState cs = createComputeState("TestID1", tenantLinks);
        cs = doPost(cs, ComputeService.FACTORY_LINK);
        List<String> links = getDocumentLinksOfType(ContainerState.class);
        Assert.assertTrue(links.isEmpty());
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainersTransformationService.SELF_LINK), false,
                Service.Action.POST);
    }

    @Test
    public void testSingleHostSingleProject() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        ComputeState cs = createComputeState("TestID1", tenantLinks);
        cs = doPost(cs, ComputeService.FACTORY_LINK);

        ContainerState containerState1 = createContainer(cs.documentSelfLink);
        containerState1 = doPost(containerState1, ContainerFactoryService.SELF_LINK);
        ContainerState containerState2 = createContainer(cs.documentSelfLink);
        containerState2 = doPost(containerState2, ContainerFactoryService.SELF_LINK);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainersTransformationService.SELF_LINK), false,
                Service.Action.POST);

        containerState1 = getDocument(ContainerState.class, containerState1.documentSelfLink);
        containerState2 = getDocument(ContainerState.class, containerState2.documentSelfLink);

        Assert.assertTrue(containerState1.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerState2.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerState2.tenantLinks.equals(containerState1.tenantLinks));
    }

    @Test
    public void testSingleHostMultipleProjects() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        tenantLinks.add("project2");
        ComputeState cs = createComputeState("TestID1", tenantLinks);
        cs = doPost(cs, ComputeService.FACTORY_LINK);

        ContainerState containerState1 = createContainer(cs.documentSelfLink);
        containerState1 = doPost(containerState1, ContainerFactoryService.SELF_LINK);
        ContainerState containerState2 = createContainer(cs.documentSelfLink);
        containerState2 = doPost(containerState2, ContainerFactoryService.SELF_LINK);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainersTransformationService.SELF_LINK), false,
                Service.Action.POST);

        containerState1 = getDocument(ContainerState.class, containerState1.documentSelfLink);
        containerState2 = getDocument(ContainerState.class, containerState2.documentSelfLink);

        Assert.assertTrue(containerState1.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerState2.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerState2.tenantLinks.equals(containerState1.tenantLinks));
    }

    @Test
    public void testMultipleHosts() throws Throwable {
        List<String> tenantLinksHost1 = new ArrayList<String>();
        tenantLinksHost1.add("project1");
        tenantLinksHost1.add("project2");
        List<String> tenantLinksHost2 = new ArrayList<String>();
        tenantLinksHost2.add("host2-project");

        ComputeState cs = createComputeState("TestID1", tenantLinksHost1);
        cs = doPost(cs, ComputeService.FACTORY_LINK);
        ComputeState cs2 = createComputeState("TestID2", tenantLinksHost2);
        cs2 = doPost(cs2, ComputeService.FACTORY_LINK);

        ContainerState firstContainerHost1 = createContainer(cs.documentSelfLink);
        firstContainerHost1 = doPost(firstContainerHost1, ContainerFactoryService.SELF_LINK);
        ContainerState secondContainerHost1 = createContainer(cs.documentSelfLink);
        secondContainerHost1 = doPost(secondContainerHost1, ContainerFactoryService.SELF_LINK);

        ContainerState firstContainerHost2 = createContainer(cs2.documentSelfLink);
        firstContainerHost2 = doPost(firstContainerHost2, ContainerFactoryService.SELF_LINK);
        ContainerState secondContainerHost2 = createContainer(cs2.documentSelfLink);
        // set tenant links to the container to check that the old tenant links are not overwritten
        secondContainerHost2.tenantLinks = new ArrayList<>();
        String containerTenantLink = "test-business-group";
        secondContainerHost2.tenantLinks.add(containerTenantLink);
        secondContainerHost2 = doPost(secondContainerHost2, ContainerFactoryService.SELF_LINK);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainersTransformationService.SELF_LINK), false,
                Service.Action.POST);

        firstContainerHost1 = getDocument(ContainerState.class,
                firstContainerHost1.documentSelfLink);
        secondContainerHost1 = getDocument(ContainerState.class,
                secondContainerHost1.documentSelfLink);
        firstContainerHost2 = getDocument(ContainerState.class,
                firstContainerHost2.documentSelfLink);
        secondContainerHost2 = getDocument(ContainerState.class,
                secondContainerHost2.documentSelfLink);

        Assert.assertTrue(firstContainerHost1.tenantLinks.containsAll(tenantLinksHost1));
        Assert.assertTrue(secondContainerHost1.tenantLinks.containsAll(tenantLinksHost1));
        Assert.assertTrue(secondContainerHost1.tenantLinks.equals(firstContainerHost1.tenantLinks));

        Assert.assertTrue(firstContainerHost2.tenantLinks.containsAll(tenantLinksHost2));
        Assert.assertTrue(secondContainerHost2.tenantLinks.containsAll(tenantLinksHost2));
        Assert.assertTrue(secondContainerHost2.tenantLinks
                .size() == firstContainerHost2.tenantLinks.size() + 1);
        Assert.assertTrue(secondContainerHost2.tenantLinks.contains(containerTenantLink));
    }

    private ContainerState createContainer(String parentLink) {
        ContainerState containerState = new ContainerState();
        containerState.id = UUID.randomUUID().toString();
        containerState.parentLink = parentLink;
        containerState.powerState = PowerState.STOPPED;
        containerState.system = Boolean.TRUE;
        return containerState;
    }

    private ComputeState createComputeState(String hostId, List<String> tenantLinks) {
        ComputeState cs = new ComputeState();
        cs.id = hostId;
        cs.descriptionLink = "test";
        cs.documentSelfLink = cs.id;
        cs.address = "test-address";
        cs.tenantLinks = tenantLinks;
        return cs;
    }
}
