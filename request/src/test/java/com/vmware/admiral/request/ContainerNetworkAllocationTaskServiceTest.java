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
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.HashMap;

import org.junit.Test;

import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.network.Ipam;
import com.vmware.admiral.request.ContainerNetworkAllocationTaskService.ContainerNetworkAllocationTaskState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;


public class ContainerNetworkAllocationTaskServiceTest extends RequestBaseTest {

    private static String NETWORK_DRIVER = "bridge";
    private static String IPAM_DRIVER = "overlay";
    private static String DEFAULT_DRIVER = "default";

    @Test
    public void testAllocationTaskServiceLifeCycle() throws Throwable {

        ContainerNetworkAllocationTaskState allocationTask = createContainerNetworkAllocationTask(
                containerNetworkDesc.documentSelfLink, 1);
        allocationTask = allocate(allocationTask);

        ContainerNetworkState networkState = getDocument(ContainerNetworkState.class,
                allocationTask.resourceLinks.get(0));

        assertNotNull(networkState);
        assertEquals(containerNetworkDesc.documentSelfLink, networkState.descriptionLink);
        assertEquals(containerNetworkDesc.driver, networkState.driver);
        assertTrue(networkState.name.contains(containerNetworkDesc.name));
        assertEquals(allocationTask.resourceLinks.get(0), networkState.documentSelfLink);

    }

    @Test
    public void testNetworkDriverFromCustomProperties() throws Throwable {

        ContainerNetworkAllocationTaskState allocationTask = createContainerNetworkAllocationTask(
                containerNetworkDesc.documentSelfLink, 1);

        assertEquals(containerNetworkDesc.driver, null);

        allocationTask.customProperties.put(
                ContainerNetworkAllocationTaskService.CUSTOM_PROPERTY_NETWORK_DRIVER,
                NETWORK_DRIVER);

        allocationTask = allocate(allocationTask);

        ContainerNetworkState networkState = getDocument(ContainerNetworkState.class,
                allocationTask.resourceLinks.get(0));

        assertNotNull(networkState);
        assertEquals(containerNetworkDesc.documentSelfLink, networkState.descriptionLink);
        assertEquals(networkState.driver, NETWORK_DRIVER);
        assertTrue(networkState.name.contains(containerNetworkDesc.name));
        assertEquals(allocationTask.resourceLinks.get(0), networkState.documentSelfLink);

    }

    @Test
    public void testNetworkIpamDriverFromCustomProperties() throws Throwable {

        ContainerNetworkAllocationTaskState allocationTask = createContainerNetworkAllocationTask(
                containerNetworkDesc.documentSelfLink, 1);

        Ipam ipam = new Ipam();
        ipam.driver = DEFAULT_DRIVER;

        URI networkUri = UriUtils.buildUri(host, containerNetworkDesc.documentSelfLink);
        ContainerNetworkDescription patch = new ContainerNetworkDescription();
        patch.ipam = ipam;
        patch.name = containerNetworkDesc.name;
        doOperation(patch, networkUri, false, Action.PATCH);

        allocationTask.customProperties.put(
                ContainerNetworkAllocationTaskService.CUSTOM_PROPERTY_IPAM_DRIVER, IPAM_DRIVER);

        allocationTask = allocate(allocationTask);

        ContainerNetworkState networkState = getDocument(ContainerNetworkState.class,
                allocationTask.resourceLinks.get(0));

        assertNotNull(networkState);
        assertEquals(containerNetworkDesc.documentSelfLink, networkState.descriptionLink);

        assertEquals(networkState.ipam.driver, IPAM_DRIVER);
        assertTrue(networkState.name.contains(containerNetworkDesc.name));
        assertEquals(allocationTask.resourceLinks.get(0), networkState.documentSelfLink);

    }

    private ContainerNetworkAllocationTaskState createContainerNetworkAllocationTask(
            String networkDocSelfLink, long resourceCount) {

        ContainerNetworkAllocationTaskState allocationTask = new ContainerNetworkAllocationTaskState();
        allocationTask.resourceDescriptionLink = networkDocSelfLink;
        allocationTask.resourceCount = resourceCount;
        allocationTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        allocationTask.customProperties = new HashMap<>();
        return allocationTask;
    }

    private ContainerNetworkAllocationTaskState allocate(
            ContainerNetworkAllocationTaskState allocationTask)
            throws Throwable {
        allocationTask = startAllocationTask(allocationTask);
        host.log("Start allocation test: " + allocationTask.documentSelfLink);

        allocationTask = waitForTaskSuccess(allocationTask.documentSelfLink,
                ContainerNetworkAllocationTaskState.class);

        assertNotNull("ResourceLinks null for allocation: " + allocationTask.documentSelfLink,
                allocationTask.resourceLinks);
        assertEquals("Resource count not equal for: " + allocationTask.documentSelfLink,
                allocationTask.resourceCount, Long.valueOf(allocationTask.resourceLinks.size()));

        host.log("Finished allocation test: " + allocationTask.documentSelfLink);
        return allocationTask;
    }

    private ContainerNetworkAllocationTaskState startAllocationTask(
            ContainerNetworkAllocationTaskState allocationTask) throws Throwable {
        ContainerNetworkAllocationTaskState outAllocationTask = doPost(
                allocationTask, ContainerNetworkAllocationTaskService.FACTORY_LINK);
        assertNotNull(outAllocationTask);
        return outAllocationTask;
    }

}
