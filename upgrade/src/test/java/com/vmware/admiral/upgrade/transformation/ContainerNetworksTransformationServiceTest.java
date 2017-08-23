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

package com.vmware.admiral.upgrade.transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.network.ContainerNetworkFactoryService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.upgrade.UpgradeBaseTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

public class ContainerNetworksTransformationServiceTest extends UpgradeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ContainerNetworkService.FACTORY_LINK);
        waitForServiceAvailability(ContainerNetworkFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerNetworksTransformationService.SELF_LINK);
    }

    @Test
    public void testNoNetworksNoHosts() throws Throwable {
        List<String> links = getDocumentLinksOfType(ComputeState.class);
        Assert.assertTrue(links.isEmpty());
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainerNetworksTransformationService.SELF_LINK), false,
                Service.Action.POST);
    }

    @Test
    public void testHostWithoutNetworks() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        ComputeState cs = createComputeState("TestID1", tenantLinks);
        cs = doPost(cs, ComputeService.FACTORY_LINK);
        List<String> links = getDocumentLinksOfType(ContainerNetworkState.class);
        Assert.assertTrue(links.isEmpty());
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainerNetworksTransformationService.SELF_LINK), false,
                Service.Action.POST);
    }

    @Test
    public void testSingleHostSingleProject() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        ComputeState cs = createComputeState("TestID1", tenantLinks);
        cs = doPost(cs, ComputeService.FACTORY_LINK);

        ContainerNetworkState containerNetwork1 = createNetwork(cs.documentSelfLink);
        containerNetwork1 = doPost(containerNetwork1, ContainerNetworkFactoryService.SELF_LINK);
        ContainerNetworkState containerNetwork2 = createNetwork(cs.documentSelfLink);
        containerNetwork2 = doPost(containerNetwork2, ContainerNetworkFactoryService.SELF_LINK);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainerNetworksTransformationService.SELF_LINK), false,
                Service.Action.POST);

        containerNetwork1 = getDocument(ContainerNetworkState.class,
                containerNetwork1.documentSelfLink);
        containerNetwork2 = getDocument(ContainerNetworkState.class,
                containerNetwork2.documentSelfLink);

        Assert.assertTrue(containerNetwork1.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerNetwork2.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerNetwork2.tenantLinks.equals(containerNetwork1.tenantLinks));
    }

    @Test
    public void testSingleHostMultipleProjects() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        tenantLinks.add("project2");
        ComputeState cs = createComputeState("TestID1", tenantLinks);
        cs = doPost(cs, ComputeService.FACTORY_LINK);

        ContainerNetworkState containerNetwork1 = createNetwork(cs.documentSelfLink);
        containerNetwork1 = doPost(containerNetwork1, ContainerNetworkFactoryService.SELF_LINK);
        ContainerNetworkState containerNetwork2 = createNetwork(cs.documentSelfLink);
        containerNetwork2 = doPost(containerNetwork2, ContainerNetworkFactoryService.SELF_LINK);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainerNetworksTransformationService.SELF_LINK), false,
                Service.Action.POST);

        containerNetwork1 = getDocument(ContainerNetworkState.class,
                containerNetwork1.documentSelfLink);
        containerNetwork2 = getDocument(ContainerNetworkState.class,
                containerNetwork2.documentSelfLink);

        Assert.assertTrue(containerNetwork1.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerNetwork2.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerNetwork2.tenantLinks.equals(containerNetwork1.tenantLinks));
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

        ContainerNetworkState firstNetworkHost1 = createNetwork(cs.documentSelfLink);
        // Add multiple parent links
        firstNetworkHost1.parentLinks.add("NoExisting");
        firstNetworkHost1 = doPost(firstNetworkHost1, ContainerNetworkFactoryService.SELF_LINK);

        ContainerNetworkState secondNetworkHost1 = createNetwork(cs.documentSelfLink);
        secondNetworkHost1 = doPost(secondNetworkHost1,
                ContainerNetworkFactoryService.SELF_LINK);

        ContainerNetworkState firstNetworkHost2 = createNetwork(cs2.documentSelfLink);
        firstNetworkHost2 = doPost(firstNetworkHost2, ContainerNetworkFactoryService.SELF_LINK);
        ContainerNetworkState secondNetworkHost2 = createNetwork(cs2.documentSelfLink);
        // set tenant links to the container to check that the old tenant links are not overwritten
        secondNetworkHost2.tenantLinks = new ArrayList<>();
        String networkTenantLink = "test-business-group";
        secondNetworkHost2.tenantLinks.add(networkTenantLink);
        secondNetworkHost2 = doPost(secondNetworkHost2,
                ContainerNetworkFactoryService.SELF_LINK);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainerNetworksTransformationService.SELF_LINK), false,
                Service.Action.POST);

        firstNetworkHost1 = getDocument(ContainerNetworkState.class,
                firstNetworkHost1.documentSelfLink);
        secondNetworkHost1 = getDocument(ContainerNetworkState.class,
                secondNetworkHost1.documentSelfLink);
        firstNetworkHost2 = getDocument(ContainerNetworkState.class,
                firstNetworkHost2.documentSelfLink);
        secondNetworkHost2 = getDocument(ContainerNetworkState.class,
                secondNetworkHost2.documentSelfLink);

        Assert.assertTrue(firstNetworkHost1.tenantLinks.containsAll(tenantLinksHost1));
        Assert.assertTrue(secondNetworkHost1.tenantLinks.containsAll(tenantLinksHost1));
        Assert.assertTrue(secondNetworkHost1.tenantLinks.equals(firstNetworkHost1.tenantLinks));

        Assert.assertTrue(firstNetworkHost2.tenantLinks.containsAll(tenantLinksHost2));
        Assert.assertTrue(secondNetworkHost2.tenantLinks.containsAll(tenantLinksHost2));
        Assert.assertTrue(secondNetworkHost2.tenantLinks.contains(networkTenantLink));
    }

    @Test
    public void testNetworkHasTenantLinks() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        String tenant = "project1";
        tenantLinks.add(tenant);
        ComputeState cs = createComputeState("TestID1", tenantLinks);
        cs = doPost(cs, ComputeService.FACTORY_LINK);

        ContainerNetworkState containerNetwork1 = createNetwork(cs.documentSelfLink);
        containerNetwork1.tenantLinks = new ArrayList<>();
        containerNetwork1.tenantLinks.add(tenant);
        containerNetwork1 = doPost(containerNetwork1, ContainerNetworkFactoryService.SELF_LINK);
        ContainerNetworkState containerNetwork2 = createNetwork(cs.documentSelfLink);
        containerNetwork2 = doPost(containerNetwork2, ContainerNetworkFactoryService.SELF_LINK);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainerNetworksTransformationService.SELF_LINK), false,
                Service.Action.POST);

        containerNetwork1 = getDocument(ContainerNetworkState.class,
                containerNetwork1.documentSelfLink);
        containerNetwork2 = getDocument(ContainerNetworkState.class,
                containerNetwork2.documentSelfLink);

        Assert.assertTrue(containerNetwork1.tenantLinks.size() == 1);
        Assert.assertTrue(containerNetwork2.tenantLinks.size() == 1);
        Assert.assertTrue(containerNetwork1.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerNetwork2.tenantLinks.containsAll(tenantLinks));
    }

    private ContainerNetworkState createNetwork(String parentLink) {
        ContainerNetworkState network = new ContainerNetworkState();
        network.id = UUID.randomUUID().toString();
        network.name = "test";
        network.parentLinks = new ArrayList<>();
        network.parentLinks.add(parentLink);
        return network;
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
