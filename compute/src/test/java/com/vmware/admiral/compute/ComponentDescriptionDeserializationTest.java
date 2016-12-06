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

package com.vmware.admiral.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.Ipam;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.Utils;



public class ComponentDescriptionDeserializationTest extends ComputeBaseTest {

    @Test
    public void testToContainerDescription() {
        ComponentDescription cd = new ComponentDescription();
        cd.name = "toContainer";

        ContainerDescription container = new ContainerDescription();
        container.name = "name1";
        container.env = new String[] { "hello" };
        container.image = "library/hello-world";
        container.networks = new HashMap<>();
        container.networks.put("net1", new ServiceNetwork());

        cd.type = ResourceType.CONTAINER_TYPE.getName();
        cd.updateServiceDocument(container);

        ComponentDescription serializedCd = Utils.fromJson(Utils.toJson(cd),
                ComponentDescription.class);

        assertEquals(cd.name, serializedCd.name);
        assertTrue(serializedCd.getServiceDocument() instanceof ContainerDescription);

        ContainerDescription serializedContainer = (ContainerDescription) serializedCd
                .getServiceDocument();

        assertEquals(container.name, serializedContainer.name);
        assertEquals(container.env[0], serializedContainer.env[0]);
        assertEquals(container.image, serializedContainer.image);
        assertEquals(container.networks, serializedContainer.networks);
    }

    @Test
    public void testToComputeDescription() {
        ComponentDescription cd = new ComponentDescription();
        cd.name = "toCompute";

        ComputeDescription compute = new ComputeDescription();
        compute.name = "name1";
        compute.cpuMhzPerCore = 2400;

        cd.type = ResourceType.COMPUTE_TYPE.getName();
        cd.updateServiceDocument(compute);

        ComponentDescription serializedCd = Utils.fromJson(Utils.toJson(cd),
                ComponentDescription.class);

        assertEquals(cd.name, serializedCd.name);
        assertTrue(serializedCd.getServiceDocument() instanceof ComputeDescription);

        ComputeDescription serializedCompute = (ComputeDescription) serializedCd
                .getServiceDocument();

        assertEquals(compute.name, serializedCompute.name);
        assertEquals(compute.cpuMhzPerCore, serializedCompute.cpuMhzPerCore);
    }

    @Test
    public void testToNetworkDescription() {
        ComponentDescription cd = new ComponentDescription();
        cd.name = "toContainerState";

        ContainerNetworkDescription network = new ContainerNetworkDescription();
        network.name = "name1";
        network.ipam = new Ipam();
        network.ipam.driver = "customIpam";

        cd.type = ResourceType.CONTAINER_NETWORK_TYPE.getName();
        cd.updateServiceDocument(network);

        ComponentDescription serializedCd = Utils.fromJson(Utils.toJson(cd),
                ComponentDescription.class);

        assertEquals(cd.name, serializedCd.name);
        assertTrue(serializedCd.getServiceDocument() instanceof ContainerNetworkDescription);

        ContainerNetworkDescription serializedNetwork = (ContainerNetworkDescription) serializedCd
                .getServiceDocument();

        assertEquals(network.name, serializedNetwork.name);
        assertEquals(network.ipam, serializedNetwork.ipam);
    }
}