/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.docker.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;

public class ContainerVolumeStateMapperTest {

    private static final String DATASTORE_NAME = "datastore1";
    private static final String VOLUME_NAME = "test-vol1";

    @Test
    public void testGetVmdkDatastoreName() {
        assertNull(ContainerVolumeStateMapper.getVmdkDatastoreName(Collections.emptyMap()));

        Map<String, String> status = new HashMap<>();
        status.put("datastore", DATASTORE_NAME);
        Map<String, Object> properties = new HashMap<>();
        properties.put("Status", status);
        assertEquals(DATASTORE_NAME, ContainerVolumeStateMapper.getVmdkDatastoreName(properties));
    }

    @Test
    public void testUpdateVmdkVolumeName() {
        ContainerVolumeState volumeState = new ContainerVolumeState();
        volumeState.name = VOLUME_NAME;
        volumeState.status = new HashMap<>();

        Map<String, String> status = new HashMap<>();
        status.put("datastore", DATASTORE_NAME);
        Map<String, Object> properties = new HashMap<>();
        properties.put("Name", VOLUME_NAME);
        properties.put("Driver", "vmdk");
        properties.put("Status", status);

        ContainerVolumeStateMapper.propertiesToContainerVolumeState(volumeState, properties);

        assertEquals("test-vol1@datastore1", volumeState.name);
    }
}
