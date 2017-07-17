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
import com.vmware.admiral.compute.container.volume.ContainerVolumeFactoryService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

public class ContainerVolumesTransformationServiceTest extends AuthBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ContainerVolumeService.FACTORY_LINK);
        waitForServiceAvailability(ContainerVolumeFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerVolumesTransformationService.SELF_LINK);

        host.assumeIdentity(buildUserServicePath(USER_EMAIL_ADMIN));
    }

    @Test
    public void testNoVolumesNoHosts() throws Throwable {
        List<String> links = getDocumentLinksOfType(ComputeState.class);
        Assert.assertTrue(links.isEmpty());
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainerVolumesTransformationService.SELF_LINK), false,
                Service.Action.POST);
    }

    @Test
    public void testHostWithoutVolumes() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        ComputeState cs = createComputeState("TestID1", tenantLinks);
        cs = doPost(cs, ComputeService.FACTORY_LINK);
        List<String> links = getDocumentLinksOfType(ContainerVolumeState.class);
        Assert.assertTrue(links.isEmpty());
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainerVolumesTransformationService.SELF_LINK), false,
                Service.Action.POST);
    }

    @Test
    public void testSingleHostSingleProject() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        ComputeState cs = createComputeState("TestID1", tenantLinks);
        cs = doPost(cs, ComputeService.FACTORY_LINK);

        ContainerVolumeState containerVolume1 = createVolume(cs.documentSelfLink);
        containerVolume1 = doPost(containerVolume1, ContainerVolumeFactoryService.SELF_LINK);
        ContainerVolumeState containerVolume2 = createVolume(cs.documentSelfLink);
        containerVolume2 = doPost(containerVolume2, ContainerVolumeFactoryService.SELF_LINK);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainerVolumesTransformationService.SELF_LINK), false,
                Service.Action.POST);

        containerVolume1 = getDocument(ContainerVolumeState.class,
                containerVolume1.documentSelfLink);
        containerVolume2 = getDocument(ContainerVolumeState.class,
                containerVolume2.documentSelfLink);

        Assert.assertTrue(containerVolume1.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerVolume2.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerVolume2.tenantLinks.equals(containerVolume1.tenantLinks));
    }

    @Test
    public void testSingleHostMultipleProjects() throws Throwable {
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add("project1");
        tenantLinks.add("project2");
        ComputeState cs = createComputeState("TestID1", tenantLinks);
        cs = doPost(cs, ComputeService.FACTORY_LINK);

        ContainerVolumeState containerVolume1 = createVolume(cs.documentSelfLink);
        containerVolume1 = doPost(containerVolume1, ContainerVolumeFactoryService.SELF_LINK);
        ContainerVolumeState containerVolume2 = createVolume(cs.documentSelfLink);
        containerVolume2 = doPost(containerVolume2, ContainerVolumeFactoryService.SELF_LINK);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainerVolumesTransformationService.SELF_LINK), false,
                Service.Action.POST);

        containerVolume1 = getDocument(ContainerVolumeState.class, containerVolume1.documentSelfLink);
        containerVolume2 = getDocument(ContainerVolumeState.class, containerVolume2.documentSelfLink);

        Assert.assertTrue(containerVolume1.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerVolume2.tenantLinks.containsAll(tenantLinks));
        Assert.assertTrue(containerVolume2.tenantLinks.equals(containerVolume1.tenantLinks));
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

        ContainerVolumeState firstVolumeHost1 = createVolume(cs.documentSelfLink);
        // Add multiple parent links
        firstVolumeHost1.parentLinks.add("NoExisting");
        firstVolumeHost1 = doPost(firstVolumeHost1, ContainerVolumeFactoryService.SELF_LINK);

        ContainerVolumeState seconVolumeHost1 = createVolume(cs.documentSelfLink);
        seconVolumeHost1 = doPost(seconVolumeHost1,
                ContainerVolumeFactoryService.SELF_LINK);

        ContainerVolumeState firstVolumekHost2 = createVolume(cs2.documentSelfLink);
        firstVolumekHost2 = doPost(firstVolumekHost2, ContainerVolumeFactoryService.SELF_LINK);
        ContainerVolumeState secondVolumeHost2 = createVolume(cs2.documentSelfLink);
        // set tenant links to the container to check that the old tenant links are not overwritten
        secondVolumeHost2.tenantLinks = new ArrayList<>();
        String volumeTenantLink = "test-business-group";
        secondVolumeHost2.tenantLinks.add(volumeTenantLink);
        secondVolumeHost2 = doPost(secondVolumeHost2,
                ContainerVolumeFactoryService.SELF_LINK);
        doOperation(new ServiceDocument(),
                UriUtils.buildUri(host, ContainerVolumesTransformationService.SELF_LINK), false,
                Service.Action.POST);

        firstVolumeHost1 = getDocument(ContainerVolumeState.class,
                firstVolumeHost1.documentSelfLink);
        seconVolumeHost1 = getDocument(ContainerVolumeState.class,
                seconVolumeHost1.documentSelfLink);
        firstVolumekHost2 = getDocument(ContainerVolumeState.class,
                firstVolumekHost2.documentSelfLink);
        secondVolumeHost2 = getDocument(ContainerVolumeState.class,
                secondVolumeHost2.documentSelfLink);

        Assert.assertTrue(firstVolumeHost1.tenantLinks.containsAll(tenantLinksHost1));
        Assert.assertTrue(seconVolumeHost1.tenantLinks.containsAll(tenantLinksHost1));
        Assert.assertTrue(seconVolumeHost1.tenantLinks.equals(firstVolumeHost1.tenantLinks));

        Assert.assertTrue(firstVolumekHost2.tenantLinks.containsAll(tenantLinksHost2));
        Assert.assertTrue(secondVolumeHost2.tenantLinks.containsAll(tenantLinksHost2));
        Assert.assertTrue(secondVolumeHost2.tenantLinks.contains(volumeTenantLink));
    }

    private ContainerVolumeState createVolume(String parentLink) {
        ContainerVolumeState volume = new ContainerVolumeState();
        volume.id = UUID.randomUUID().toString();
        volume.parentLinks = new ArrayList<>();
        volume.parentLinks.add(parentLink);
        return volume;
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
