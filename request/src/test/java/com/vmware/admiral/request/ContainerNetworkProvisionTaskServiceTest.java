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

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;

public class ContainerNetworkProvisionTaskServiceTest extends RequestBaseTest {

    @Test
    public void testNetworkProvisioningTask() throws Throwable {

        ContainerNetworkDescription networkDesc = TestRequestStateFactory
                .createContainerNetworkDescription("my-net");
        networkDesc.documentSelfLink = UUID.randomUUID().toString();

        // Create ContainerDescription with above network.
        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.name = "container1";
        container1Desc.networks = new HashMap<>();
        container1Desc.networks.put("my-net", new ServiceNetwork());

        // Create ContainerDescription with above network.
        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription();
        container2Desc.name = "container2";
        container2Desc.affinity = new String[] { "!container1:hard" };
        container2Desc.networks = new HashMap<>();
        container2Desc.networks.put("my-net", new ServiceNetwork());

        // Setup Docker host and resource pool.
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        if (dockerHostDesc.customProperties == null) {
            dockerHostDesc.customProperties = new HashMap<>();
        }
        dockerHostDesc.customProperties
                .put(ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store");

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        CompositeDescription compositeDesc = createCompositeDesc(networkDesc, container1Desc,
                container2Desc);
        assertNotNull(compositeDesc);
        assertEquals(3, compositeDesc.descriptionLinks.size());

        // setup Group Placement:
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        // 1. Request a composite container:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);

        request.tenantLinks = groupPlacementState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // wait for request completed state:
        request = waitForRequestToComplete(request);

        CompositeComponent cc = getDocument(CompositeComponent.class, request.resourceLinks.iterator().next());

        assertNotNull(cc.componentLinks);
        assertEquals(cc.componentLinks.size(), 3);

        String networkLink = null;
        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();

        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerNetworkService.FACTORY_LINK)) {
                networkLink = link;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        ContainerState cont1 = getDocument(ContainerState.class, containerLink1);
        ContainerState cont2 = getDocument(ContainerState.class, containerLink2);
        assertNotNull(cont1);
        assertNotNull(cont2);

        assertTrue("Containers should be on different hosts.",
                !cont1.parentLink.equals(cont2.parentLink));

        ContainerNetworkState network = getDocument(ContainerNetworkState.class, networkLink);

        assertNotNull(cont1.networks.get(network.name));
        assertNotNull(cont2.networks.get(network.name));

        List<String> hostLinks = Arrays.asList(cont1.parentLink,
                cont2.parentLink);
        // Network is provisioned on one of the hosts of the containers
        assertTrue(hostLinks.contains(network.originatingHostLink));
    }

    // VBV-685
    @Test
    public void testNetworkWithSpecialNameProvisioningTask() throws Throwable {
        String networkName = "special chars network";

        ContainerNetworkDescription networkDesc = TestRequestStateFactory
                .createContainerNetworkDescription(networkName);
        networkDesc.documentSelfLink = UUID.randomUUID().toString();

        // Create ContainerDescription with above network.
        ContainerDescription container1Desc = TestRequestStateFactory.createContainerDescription();
        container1Desc.name = "container1";
        container1Desc.networks = new HashMap<>();
        container1Desc.networks.put(networkName, new ServiceNetwork());

        // Create ContainerDescription with above network.
        ContainerDescription container2Desc = TestRequestStateFactory.createContainerDescription();
        container2Desc.name = "container2";
        container2Desc.affinity = new String[] { "!container1:hard" };
        container2Desc.networks = new HashMap<>();
        container2Desc.networks.put(networkName, new ServiceNetwork());

        // Setup Docker host and resource pool.
        ResourcePoolState resourcePool = createResourcePool();
        ComputeDescription dockerHostDesc = createDockerHostDescription();
        if (dockerHostDesc.customProperties == null) {
            dockerHostDesc.customProperties = new HashMap<>();
        }
        dockerHostDesc.customProperties
                .put(ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store");

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        CompositeDescription compositeDesc = createCompositeDesc(networkDesc, container1Desc,
                container2Desc);
        assertNotNull(compositeDesc);
        assertEquals(3, compositeDesc.descriptionLinks.size());

        // setup Group Placement:
        GroupResourcePlacementState groupPlacementState = createGroupResourcePlacement(
                resourcePool);

        // 1. Request a composite container:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);

        request.tenantLinks = groupPlacementState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // wait for request completed state:
        request = waitForRequestToComplete(request);

        CompositeComponent cc = getDocument(CompositeComponent.class, request.resourceLinks.iterator().next());

        assertNotNull(cc.componentLinks);
        assertEquals(cc.componentLinks.size(), 3);

        String networkLink = null;
        String containerLink1 = null;
        String containerLink2 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();

        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerNetworkService.FACTORY_LINK)) {
                networkLink = link;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else {
                containerLink2 = link;
            }
        }

        ContainerState cont1 = getDocument(ContainerState.class, containerLink1);
        ContainerState cont2 = getDocument(ContainerState.class, containerLink2);
        assertNotNull(cont1);
        assertNotNull(cont2);

        assertTrue("Containers should be on different hosts.",
                !cont1.parentLink.equals(cont2.parentLink));

        ContainerNetworkState network = getDocument(ContainerNetworkState.class, networkLink);
        assertThat(network.name, startsWith(networkName));

        assertNotNull(cont1.networks.get(network.name));
        assertNotNull(cont2.networks.get(network.name));

        List<String> hostLinks = Arrays.asList(cont1.parentLink,
                cont2.parentLink);
        // Network is provisioned on one of the hosts of the containers
        assertTrue(hostLinks.contains(network.originatingHostLink));
    }

    @Test
    public void testNetworkProvisioningTaskWithProvidedHostIds() throws Throwable {

        ContainerNetworkDescription networkDesc = TestRequestStateFactory
                .createContainerNetworkDescription("my-net");
        networkDesc.documentSelfLink = UUID.randomUUID().toString();
        networkDesc = doPost(networkDesc, ContainerNetworkDescriptionService.FACTORY_LINK);

        ComputeDescription dockerHostDesc = createDockerHostDescription();
        if (dockerHostDesc.customProperties == null) {
            dockerHostDesc.customProperties = new HashMap<>();
        }
        dockerHostDesc.customProperties
                .put(ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME, "my-kv-store");

        ComputeState dockerHost1 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost1);

        ComputeState dockerHost2 = createDockerHost(dockerHostDesc, resourcePool, true);
        addForDeletion(dockerHost2);

        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.CONTAINER_NETWORK_TYPE.getName(), networkDesc.documentSelfLink);

        String hostIds = dockerHost1.id + "," + dockerHost2.id;
        request.customProperties = new HashMap<>();
        request.customProperties
                .put(ReservationAllocationTaskService.CONTAINER_HOST_ID_CUSTOM_PROPERTY, hostIds);

        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // wait for request completed state:
        request = waitForRequestToComplete(request);

        Set<String> networkLinks = request.resourceLinks;
        Iterator<String> networkLinkIterator = networkLinks.iterator();
        ContainerNetworkState net1 = getDocument(ContainerNetworkState.class,
                networkLinkIterator.next());
        ContainerNetworkState net2 = getDocument(ContainerNetworkState.class,
                networkLinkIterator.next());

        assertNotNull(net1);
        assertNotNull(net2);

        List<String> hostLinks = Arrays.asList(dockerHost1.documentSelfLink,
                dockerHost2.documentSelfLink);

        // Networks are provisioned on the provided hosts
        assertTrue(hostLinks.contains(net1.originatingHostLink));
        assertTrue(hostLinks.contains(net2.originatingHostLink));
        assertFalse(net1.originatingHostLink.equals(net2.originatingHostLink));
    }

}
