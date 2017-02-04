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
import com.vmware.photon.controller.model.resources.SubnetService;
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
        cd.customProperties = new HashMap<>();
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                "coreos");
        if (attachNic) {
            NetworkInterfaceDescription nid = createNetworkInterface("test-nic");
            cd.networkInterfaceDescLinks = new ArrayList<>();
            cd.networkInterfaceDescLinks.add(nid.documentSelfLink);
        } else {
            cd.customProperties.put("subnetworkLink",
                    UriUtils.buildUriPath(SubnetService.FACTORY_LINK, "my-subnet"));
        }
        return cd;
    }

    private NetworkInterfaceDescription createNetworkInterface(String name) throws Throwable {
        NetworkInterfaceDescription nid = new NetworkInterfaceDescription();
        nid.id = UUID.randomUUID().toString();
        nid.name = name;
        nid.documentSelfLink = nid.id;
        nid.subnetLink = UriUtils.buildUriPath(SubnetService.FACTORY_LINK, "my-subnet");

        NetworkInterfaceDescription returnState = doPost(nid,
                NetworkInterfaceDescriptionService.FACTORY_LINK);
        return returnState;
    }
}
