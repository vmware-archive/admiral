/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionFactoryService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.Ipam;
import com.vmware.admiral.compute.container.network.IpamConfig;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerNetworkDescriptionServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ContainerNetworkDescriptionService.FACTORY_LINK);
    }

    @Test
    public void testPatchWithSameContainerNetworkDescription() throws Throwable {
        ContainerNetworkDescription containerNetworkDescription = createContainerNetworkDescription();

        containerNetworkDescription = doPost(containerNetworkDescription,
                ContainerNetworkDescriptionService.FACTORY_LINK);

        ContainerNetworkDescription patch = doPatch(containerNetworkDescription,
                containerNetworkDescription.documentSelfLink);

        Assert.assertEquals(containerNetworkDescription.name, patch.name);
    }

    @Test
    public void testPatchRemovesParentLink() throws Throwable {
        ContainerNetworkDescription containerNetworkDescription = createContainerNetworkDescription();
        containerNetworkDescription.parentDescriptionLink = "parent";

        containerNetworkDescription = doPost(containerNetworkDescription,
                ContainerNetworkDescriptionService.FACTORY_LINK);
        containerNetworkDescription.parentDescriptionLink = "";

        ContainerNetworkDescription patch = doPatch(containerNetworkDescription,
                containerNetworkDescription.documentSelfLink);

        Assert.assertEquals(null, patch.parentDescriptionLink);
    }

    @Test
    public void testContainerNetworkDescriptionService() throws Throwable {
        verifyService(
                FactoryService.create(ContainerNetworkDescriptionService.class),
                ContainerNetworkDescription.class,
                (prefix, index) -> {
                    ContainerNetworkDescription containerNetworkDesc = new ContainerNetworkDescription();
                    containerNetworkDesc.name = prefix + "name" + index;
                    containerNetworkDesc.customProperties = new HashMap<>();

                    return containerNetworkDesc;
                },
                (prefix, serviceDocument) -> {
                    ContainerNetworkDescription contDesc = (ContainerNetworkDescription) serviceDocument;
                    assertTrue(contDesc.name.startsWith(prefix + "name"));
                });
    }

    @Test
    public void testValidateValidIPam() throws Throwable {
        ContainerNetworkDescription contNetworkDesc = createContainerNetworkDescription();
        contNetworkDesc.ipam = new Ipam();
        IpamConfig ipamConfig = new IpamConfig();
        ipamConfig.subnet = "10.23.12.0/24";
        ipamConfig.ipRange = "127.0.0.1/32";
        ipamConfig.gateway = "10.23.12.1";
        ipamConfig.auxAddresses = Collections.singletonMap("router", "10.23.12.2");
        contNetworkDesc.ipam.config = new IpamConfig[] { ipamConfig };

        Operation op = Operation.createPost(getContainerNetworkDescriptionUri())
                .setBody(contNetworkDesc)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (o.getStatusCode() != Operation.STATUS_CODE_CREATED) {
                            host.log("Unexpected exception: %s", Utils.toString(e));
                            host.failIteration(new IllegalStateException(
                                    "Validation exception happened", e));
                            return;
                        }
                    }
                    host.completeIteration();
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    /**
     * Test case for bug 2461433. Editing a composite description component through UI loses the
     * tenant links.
     */
    @Test
    public void testTenantLinksAfterPut() throws Throwable {
        ContainerNetworkDescription desc = createContainerNetworkDescription();
        desc = doPost(desc, ContainerNetworkDescriptionFactoryService.SELF_LINK);
        assertNotNull(desc);

        ContainerNetworkDescription newDesc = new ContainerNetworkDescription();
        newDesc.documentSelfLink = desc.documentSelfLink;
        newDesc.name = desc.name;
        newDesc.customProperties = new HashMap<>();
        newDesc.customProperties.put("key", "value");

        newDesc = doPut(newDesc);

        assertNotNull("tenantLinks should not be lost", newDesc.tenantLinks);
        assertEquals("tenantLinks count should be the same", desc.tenantLinks.size(),
                newDesc.tenantLinks.size());
        assertTrue("tenantLinks should be the same",
                newDesc.tenantLinks.containsAll(desc.tenantLinks));
    }

    private URI getContainerNetworkDescriptionUri() {
        return UriUtils.buildUri(host, ContainerNetworkDescriptionService.FACTORY_LINK);
    }

    private ContainerNetworkDescription createContainerNetworkDescription() {
        ContainerNetworkDescription containerNetworkDesc = new ContainerNetworkDescription();
        containerNetworkDesc.name = "networkDesc";

        containerNetworkDesc.tenantLinks = new LinkedList<>();
        containerNetworkDesc.tenantLinks.add("/tenants/qe");
        containerNetworkDesc.tenantLinks.add("/user/fritz@sdfdsf.dsf");
        containerNetworkDesc.tenantLinks.add("/tenants/qe/groups/dftyguhijokpl");

        return containerNetworkDesc;
    }

}
