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

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Iterator;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;

public class ContainerVolumeProvisionTaskServiceTest extends RequestBaseTest {

    @Test
    public void testVolumeProvisioningTask() throws Throwable {

        ContainerVolumeDescription volumeDesc = TestRequestStateFactory
                .createContainerVolumeDescription("postgres");
        volumeDesc.documentSelfLink = UUID.randomUUID().toString();

        // Create ContainerDescription with above volume.
        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.name = "container1";
        container1Desc.volumes = new String[] { "postgres:/etc/pgdata/postgres" };


        // Create another ContainerDescription without volume and placed it in different host.
        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription();
        container2Desc.name = "container2";
        container2Desc.affinity = new String[] { "!container1:hard" };
        container2Desc.volumes = new String[] {};

        String container1DescLink = container1Desc.documentSelfLink;

        // Setup Docker host and resource pool.
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        CompositeDescription compositeDesc = createCompositeDesc(volumeDesc, container1Desc,
                container2Desc);
        assertNotNull(compositeDesc);
        assertEquals(3, compositeDesc.descriptionLinks.size());

        // setup Group Placement:
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(resourcePool);

        // 1. Request a composite container:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);

        request.tenantLinks = groupPlacementState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // wait for request completed state:
        request = waitForRequestToComplete(request);

        CompositeComponent cc = getDocument(CompositeComponent.class, request.resourceLinks.get(0));

        assertNotNull(cc.componentLinks);
        assertEquals(cc.componentLinks.size(), 3);

        String volumeLink = null;
        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();

        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerVolumeService.FACTORY_LINK)) {
                volumeLink = link;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        ContainerState cont1 = getDocument(ContainerState.class, containerLink1);
        ContainerState cont2 = getDocument(ContainerState.class, containerLink2);

        AssertUtil.assertTrue(!cont1.parentLink.equals(cont2.parentLink),
                "Containers should be on different hosts.");

        ContainerVolumeState volume = getDocument(ContainerVolumeState.class, volumeLink);

        // Volume must be provisioned on same host where ContainerDesc with volume is.
        if (cont1.descriptionLink.equals(container1DescLink)) {
            AssertUtil.assertTrue(
                    volume.originatingHostReference.getPath().equals(cont1.parentLink),
                    "Volume is provisioned on wrong host.");

            assertEquals(cont1.volumes.length, 1);
            AssertUtil.assertTrue(cont1.volumes[0].contains(volume.name), "Host volume name is different than Container volume name.");

        } else {
            AssertUtil.assertTrue(
                    volume.originatingHostReference.getPath().equals(cont2.parentLink),
                    "Volume is provisioned on wrong host.");
            assertEquals(cont2.volumes.length, 1);
            AssertUtil.assertTrue(cont2.volumes[0].contains(volume.name), "Host volume name is different than Container volume name.");
        }

    }

}
