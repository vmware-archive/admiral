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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.container.volume.VolumeUtil;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.ServiceClient;

/**
 * Test is currently ignored in the name of build stabilization, as it failed rarely on provisioning
 * of containers. It cannot provision containers with same names (its a known issue).
 */
@Ignore
public class ContainerVolumesIT extends BaseProvisioningOnCoreOsIT {

    private static final String TEMPLATE_FILE = "WordPress_with_MySQL_volumes.yaml";
    private static final String WORDPRESS_CONTAINER_NAME = "wordpress";
    private static final String WORDPRESS_VOLUME_NAME = "/tmp";
    private static final String MYSQL_VOLUME_NAME = "/var";

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

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType)
            throws Exception {

        return compositeDescriptionLink;
    }

    @Test
    public void testProvision() throws Exception {
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
    }

    @Override
    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {
        AssertUtil.assertNotNull(compositeDescriptionLink, "'compositeDescriptionLink'");
        CompositeComponent cc = getDocument(request.resourceLinks.iterator().next(), CompositeComponent.class);
        assertEquals("Unexpected number of component links", 4,
                cc.componentLinks.size());

        String volumeLink = null;
        String containerLink1 = null;
        String containerLink2 = null;
        String containerLink3 = null;

        Iterator<String> iterator = cc.componentLinks.iterator();

        while (iterator.hasNext()) {
            String link = iterator.next();
            if (link.startsWith(ContainerVolumeService.FACTORY_LINK)) {
                volumeLink = link;
            } else if (containerLink1 == null) {
                containerLink1 = link;
            } else if (containerLink2 == null) {
                containerLink2 = link;
            } else {
                containerLink3 = link;
            }
        }

        ContainerState cont1 = getDocument(containerLink1, ContainerState.class);
        ContainerState cont2 = getDocument(containerLink2, ContainerState.class);
        ContainerState cont3 = getDocument(containerLink3, ContainerState.class);
        ContainerVolumeState volume = getDocument(volumeLink, ContainerVolumeState.class);

        List<ContainerState> documents = new ArrayList<ContainerState>();
        documents.add(cont1);
        documents.add(cont2);
        documents.add(cont3);

        // Verify that all resources are provisioned and volume is mounted properly.
        verifyVolume(documents, volume);

    }

    private void verifyVolume(List<ContainerState> containers, ContainerVolumeState volume)
            throws Exception {

        if (containers == null || containers.isEmpty()) {
            throw new IllegalArgumentException("No provisioned containers found!");
        }

        Iterator<ContainerState> iterator = containers.iterator();

        while (iterator.hasNext()) {

            ContainerState currentContainer = iterator.next();
            assertTrue(currentContainer.volumes != null);

            assertEquals(currentContainer.volumes.length, 1);

            assertTrue(currentContainer.volumes[0].contains(volume.name));

            String hostPartOfVolume = VolumeUtil
                    .parseVolumeHostDirectory(currentContainer.volumes[0]);

            assertEquals(hostPartOfVolume, volume.name);

            String containerPartOfVolume = currentContainer.volumes[0].split(":")[1];

            if (currentContainer.names.get(0).contains(WORDPRESS_CONTAINER_NAME)) {
                assertEquals(containerPartOfVolume, WORDPRESS_VOLUME_NAME);
            } else {
                assertEquals(containerPartOfVolume, MYSQL_VOLUME_NAME);
            }

            ComputeState host = getDocument(currentContainer.parentLink, ComputeState.class);

            // Assert that volumes & containers are provisioned on same host.
            assertEquals(host.documentSelfLink, volume.originatingHostReference.getPath());

        }

    }

}
