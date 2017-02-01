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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class NetworkProfileQueryUtilsTest extends RequestBaseTest {
    private URI referer;

    @Override
    @Before
    public void setUp() throws Throwable {
        startServices(host);
        createEndpoint();
        waitForServiceAvailability(host, ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK);
        referer = UriUtils.buildUri(host, EnvironmentQueryUtilsTest.class.getSimpleName());
    }

    @Test
    public void testExternalNetworkAllocation() throws Throwable {
        String contextId = UUID.randomUUID().toString();
        ComputeNetworkDescription networkDescription1 = createNetworkDescription("my net");
        ComputeNetwork computeNetwork1 = createComputeNetwork(networkDescription1, contextId);
        // Same name, different context
        ComputeNetworkDescription networkDescription2 = createNetworkDescription("my net");
        createComputeNetwork(networkDescription2, UUID.randomUUID().toString());

        NetworkInterfaceDescription nid = createComputeNetworkInterfaceDescription("my net");
        ComputeDescription computeDescription = createComputeDescription(contextId,
                Arrays.asList(nid.documentSelfLink));

        TestContext ctx = testCreate(1);
        Set<String> networkProfileLinks = new HashSet<>();
        NetworkProfileQueryUtils.getComputeNetworkProfileConstraints(host, referer, computeDescription,
                (all, e) -> {
                    if (e != null) {
                        ctx.fail(e);
                        return;
                    }
                    networkProfileLinks.addAll(all);
                    ctx.complete();
                });
        ctx.await();

        assertFalse(networkProfileLinks.isEmpty());
        assertEquals(2, networkProfileLinks.size());
        assertTrue(networkProfileLinks.containsAll(computeNetwork1.networkProfileLinks));
    }

    private ComputeNetworkDescription createNetworkDescription(String name) throws Throwable {
        ComputeNetworkDescription desc = TestRequestStateFactory.createComputeNetworkDescription(
                name);
        desc.documentSelfLink = UUID.randomUUID().toString();
        return doPost(desc, ComputeNetworkDescriptionService.FACTORY_LINK);
    }

    private NetworkInterfaceDescription createComputeNetworkInterfaceDescription(
            String netName) throws Throwable {
        NetworkInterfaceDescription nid = new NetworkInterfaceDescription();
        nid.name = netName;
        nid.documentSelfLink = UUID.randomUUID().toString();
        return doPost(nid, NetworkInterfaceDescriptionService.FACTORY_LINK);
    }

    private ComputeDescription createComputeDescription(String contextId,
            List<String> networkInterfaceDescriptions) throws Throwable {
        ComputeDescription compute = TestRequestStateFactory.createComputeDescriptionForVmGuestChildren();
        compute.documentSelfLink = UUID.randomUUID().toString();
        compute.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        compute.networkInterfaceDescLinks = networkInterfaceDescriptions;
        return doPost(compute, ComputeDescriptionService.FACTORY_LINK);
    }

    private ComputeNetwork createComputeNetwork(ComputeNetworkDescription computeNetworkDescription,
            String contextId)
            throws Throwable {
        ComputeNetwork net = TestRequestStateFactory.createComputeNetworkState(
                "my-net", computeNetworkDescription.documentSelfLink);
        net.documentSelfLink = UUID.randomUUID().toString();
        net.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        net.networkProfileLinks = new HashSet<>();
        net.networkProfileLinks.add("network-profile" + UUID.randomUUID().toString());
        net.networkProfileLinks.add("network-profile" + UUID.randomUUID().toString());
        return doPost(net, ComputeNetworkService.FACTORY_LINK);
    }
}
