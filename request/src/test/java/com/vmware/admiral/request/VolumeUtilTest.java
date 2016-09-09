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

import org.junit.Test;

import com.vmware.admiral.compute.container.volume.VolumeUtil;

public class VolumeUtilTest {

    @Test
    public void testParseOfHostDirectory() {

        String namedVolume = "named-test-volume";
        String containerDir = String.format("%s:/some/container/dir", namedVolume);

        String hostNamedDir = VolumeUtil.parseVolumeHostDirectory(containerDir);

        assertEquals(namedVolume, hostNamedDir);

        // Parse of named volumes should return named volume itself.
        assertEquals(namedVolume, VolumeUtil.parseVolumeHostDirectory(containerDir));

    }

    @Test
    public void testParseOfHostDirhWrongInput() {
        String invalidVolumeName = "host-dir:/container-dir:/some-other-dir";
        try {
            VolumeUtil.parseVolumeHostDirectory(invalidVolumeName);
        } catch (Exception e) {
            assertEquals("Invalid volume directory.", e.getMessage());
        }
    }

}
