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

package com.vmware.admiral.compute.container.volume;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;


public class ContainerVolumeServiceTest extends ComputeBaseTest {

    private static final File tempVolumeDir = FileUtils.getTempDirectory();
    private static final String CONTAINER_VOLUME_SCOPE = "local";
    private static final String CONTAINER_VOLUME_FLOCKER_DRIVER = "flocker";
    private static final String CONTAINER_VOLUME_VMDK_DRIVER = "vmdk";

    @SuppressWarnings("serial")
    private static Map<String, String> testCustomProperties = new HashMap<String, String>() {
        {
            put("customKey1", "customValue1");
            put("customKey2", "customValue2");
        }
    };

    @Before
    public void setUp() throws Throwable {

        waitForServiceAvailability(ContainerVolumeService.FACTORY_LINK);
    }

    @After
    public void tearDown() throws Throwable {
        if (tempVolumeDir.exists()) {
            tempVolumeDir.delete();
        }
    }

    @Test
    public void testContainerVolumeServices() throws Throwable {
        verifyService(
                FactoryService.create(ContainerVolumeService.class),
                ContainerVolumeState.class,
                (prefix, index) -> {
                    ContainerVolumeState containerVolumeDesc = new ContainerVolumeState();
                    containerVolumeDesc.name = prefix + "name" + index;
                    containerVolumeDesc.mountpoint = tempVolumeDir;
                    containerVolumeDesc.customProperties = testCustomProperties;
                    containerVolumeDesc.scope = CONTAINER_VOLUME_SCOPE;
                    containerVolumeDesc.driver = CONTAINER_VOLUME_FLOCKER_DRIVER;
                    return containerVolumeDesc;
                },
                (prefix, serviceDocument) -> {
                    ContainerVolumeState contVolumeDesc = (ContainerVolumeState) serviceDocument;
                    assertNotNull(contVolumeDesc);
                    assertTrue(contVolumeDesc.name.startsWith(prefix + "name"));
                    assertEquals(contVolumeDesc.mountpoint.getName(), tempVolumeDir.getName());
                    assertTrue(contVolumeDesc.mountpoint.getAbsolutePath()
                            .contains(tempVolumeDir.getAbsolutePath()));
                    assertEquals(contVolumeDesc.mountpoint.getName(), tempVolumeDir.getName());
                    assertNotNull(contVolumeDesc.customProperties);
                    assertEquals(contVolumeDesc.customProperties, testCustomProperties);
                    assertEquals(contVolumeDesc.scope, CONTAINER_VOLUME_SCOPE);
                    assertEquals(contVolumeDesc.driver, CONTAINER_VOLUME_FLOCKER_DRIVER);

                });
    }

    @Test
    public void testPatchOperation() throws Throwable {
        ContainerVolumeState volume = createVolume("tenant/coke");

        URI volumeUri = UriUtils.buildUri(host, volume.documentSelfLink);

        ContainerVolumeState patch = new ContainerVolumeState();

        // Update driver.
        patch.driver = CONTAINER_VOLUME_VMDK_DRIVER;

        ContainerVolumeState updatedVolume = updateVolume(patch, volumeUri,
                volume.documentSelfLink);

        assertTrue(!volume.driver.equals(updatedVolume.driver));
        assertEquals(updatedVolume.driver, CONTAINER_VOLUME_VMDK_DRIVER);

        // Update name.
        assertNotNull(volume.name);
        patch = new ContainerVolumeState();
        String newName = UUID.randomUUID().toString();
        patch.name = newName;

        updatedVolume = updateVolume(patch, volumeUri, volume.documentSelfLink);

        assertNotNull(updatedVolume.name);
        assertNotEquals(volume.name, updatedVolume.name);
        assertEquals(updatedVolume.name, newName);

    }

    private ContainerVolumeState createVolume(String group)
            throws Throwable {
        ContainerVolumeState volumeState = new ContainerVolumeState();

        volumeState.name = UUID.randomUUID().toString();
        volumeState.driver = CONTAINER_VOLUME_FLOCKER_DRIVER;
        volumeState.tenantLinks = Collections.singletonList(group);

        volumeState = doPost(volumeState, ContainerVolumeService.FACTORY_LINK);

        return volumeState;
    }

    private ContainerVolumeState updateVolume(ContainerVolumeState patch, URI volumeUri,
            String documentSelfLink) throws Throwable {

        doOperation(patch, volumeUri, false, Action.PATCH);

        return getDocument(ContainerVolumeState.class,
                documentSelfLink);
    }

}
