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

package com.vmware.admiral.compute.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.UriUtils;

public class ComputeNetworkServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ComputeNetworkService.FACTORY_LINK);
    }

    @Test
    public void testComputeNetworkService() throws Throwable {
        verifyService(
                FactoryService.create(ComputeNetworkService.class),
                ComputeNetwork.class,
                (prefix, index) -> {
                    ComputeNetwork network = new ComputeNetwork();
                    network.id = prefix + "id" + index;
                    network.name = prefix + "name" + index;

                    network.networkType = NetworkType.EXTERNAL;
                    network.assignment = IpAssignment.STATIC.name();
                    network.descriptionLink = UriUtils
                            .buildUriPath(ComputeNetworkDescriptionService.FACTORY_LINK, "my-net");
                    network.securityGroupLinks = new HashSet<>();
                    network.securityGroupLinks.add(UriUtils
                            .buildUriPath(SecurityGroupService.FACTORY_LINK, "my-sec-group"));

                    return network;
                },
                (prefix, serviceDocument) -> {
                    ComputeNetwork networkState = (ComputeNetwork) serviceDocument;
                    assertTrue(networkState.id.startsWith(prefix + "id"));
                    assertTrue(networkState.name.startsWith(prefix + "name"));
                    assertNotNull(networkState.securityGroupLinks);
                    assertEquals(1, networkState.securityGroupLinks.size());
                });
    }
}
