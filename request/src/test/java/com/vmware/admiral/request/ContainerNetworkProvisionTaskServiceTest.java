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

import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.compute.container.ServiceNetwork;
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

        // setup Group Policy:
        GroupResourcePolicyState groupPolicyState = createGroupResourcePolicy(resourcePool);

        // 1. Request a composite container:
        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);

        request.tenantLinks = groupPolicyState.tenantLinks;
        host.log("########  Start of request ######## ");
        request = startRequest(request);

        // wait for request completed state:
        request = waitForRequestToComplete(request);

        CompositeComponent cc = getDocument(CompositeComponent.class, request.resourceLinks.get(0));

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

        AssertUtil.assertTrue(!cont1.parentLink.equals(cont2.parentLink),
                "Containers should be on different hosts.");

        ContainerNetworkState network = getDocument(ContainerNetworkState.class, networkLink);

        assertNotNull(cont1.networks.get(network.name));
        assertNotNull(cont2.networks.get(network.name));
    }

}
