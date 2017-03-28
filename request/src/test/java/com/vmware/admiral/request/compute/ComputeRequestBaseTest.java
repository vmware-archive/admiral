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

package com.vmware.admiral.request.compute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.UriUtils;

public class ComputeRequestBaseTest extends RequestBaseTest {

    static final String TEST_VM_NAME = "testVM";

    protected ComputeState vmHostCompute;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        createComputeResourcePool();
        computeGroupPlacementState = createComputeGroupResourcePlacement(computeResourcePool, 10);
        // create a single powered-on compute available for placement
        vmHostCompute = createVmHostCompute(true);
    }

    protected ComputeDescription createVMComputeDescription(boolean attachNic) throws Throwable {
        return doPost(createComputeDescription(attachNic),
                ComputeDescriptionService.FACTORY_LINK);
    }

    protected ComputeDescription createComputeDescription(boolean attachNic) throws Throwable {
        ComputeDescription cd = new ComputeDescription();
        cd.id = UUID.randomUUID().toString();
        cd.name = TEST_VM_NAME;
        cd.instanceType = "small";
        cd.tenantLinks = computeGroupPlacementState.tenantLinks;
        cd.customProperties = new HashMap<>();
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                "coreos");

        SubnetState subnet = createSubnet("my-subnet");
        if (attachNic) {
            NetworkInterfaceDescription nid = createNetworkInterface("test-nic",
                    subnet.documentSelfLink);
            cd.networkInterfaceDescLinks = new ArrayList<>();
            cd.networkInterfaceDescLinks.add(nid.documentSelfLink);
        } else {
            cd.customProperties.put("subnetworkLink", subnet.documentSelfLink);
        }
        return cd;
    }

    private SubnetState createSubnet(String name) throws Throwable {
        SubnetState sub = new SubnetState();
        sub.name = name;
        sub.subnetCIDR = "192.168.0.0/24";
        sub.networkLink = UriUtils.buildUriPath(NetworkService.FACTORY_LINK, name);
        sub.documentSelfLink = UriUtils.buildUriPath(SubnetService.FACTORY_LINK, name);
        return doPost(sub, SubnetService.FACTORY_LINK);
    }

    private NetworkInterfaceDescription createNetworkInterface(String name, String subnetLink)
            throws Throwable {
        NetworkInterfaceDescription nid = new NetworkInterfaceDescription();
        nid.id = UUID.randomUUID().toString();
        nid.name = name;
        nid.documentSelfLink = nid.id;
        nid.tenantLinks = computeGroupPlacementState.tenantLinks;
        nid.subnetLink = subnetLink;

        NetworkInterfaceDescription returnState = doPost(nid,
                NetworkInterfaceDescriptionService.FACTORY_LINK);
        return returnState;
    }
}
