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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.xenon.common.ServiceClient;

public class ContainerMultiNetworkingIT extends BaseProvisioningOnCoreOsIT {
    private static final String TEMPLATE_FILE = "MySQL_with_multiple_networks.yaml";
    private static final String MYSQL_NAME = "mysql";

    private static final int CONTAINER_SIZE = 1;
    private static final int NETWORK_SIZE = 3;
    private static final int ALL_RESOURCES_SIZE = CONTAINER_SIZE + NETWORK_SIZE;

    private static ServiceClient serviceClient;

    private String compositeDescriptionLink;

    @BeforeClass
    public static void beforeClass() {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @AfterClass
    public static void afterClass() {
        serviceClient.stop();
    }

    @Before
    public void setUp() throws Exception {
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE);
    }

    @Test
    @Ignore("VBV-941")
    public void testProvision() throws Exception {
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return compositeDescriptionLink;
    }

    @Override
    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {

        assertEquals("Unexpected number of resource links", 1,
                request.resourceLinks.size());

        CompositeComponent cc = getDocument(request.resourceLinks.iterator().next(),
                CompositeComponent.class);
        assertEquals("Unexpected number of component links", ALL_RESOURCES_SIZE,
                cc.componentLinks.size());

        String mysqlContainerLink = cc.componentLinks.stream()
                .filter((l) -> l.contains(MYSQL_NAME))
                .collect(Collectors.toList()).get(0);

        List<String> networkLinks = cc.componentLinks.stream()
                .filter((l) -> !l.contains(MYSQL_NAME))
                .collect(Collectors.toList());
        assertEquals(NETWORK_SIZE, networkLinks.size());

        ContainerState mysqlContainer = getDocument(mysqlContainerLink, ContainerState.class);

        assertEquals(NETWORK_SIZE, mysqlContainer.networks.size());

        for (String networkLink : networkLinks) {
            ContainerNetworkState network = getDocument(networkLink, ContainerNetworkState.class);

            assertFalse(network.id.isEmpty());

            ServiceNetwork serviceNetwork = mysqlContainer.networks.get(network.name);
            assertNotNull(serviceNetwork);

            assertTrue(Arrays.asList(serviceNetwork.aliases).contains(MYSQL_NAME));
        }
    }
}
