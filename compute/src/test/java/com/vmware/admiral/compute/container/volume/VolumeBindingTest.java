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
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.vmware.xenon.common.LocalizableValidationException;

@RunWith(Parameterized.class)
public class VolumeBindingTest {

    private String volume;
    private String hostPart;
    private String containerPart;
    private boolean readOnly;
    private boolean expectError;

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "/", null, "/", false, false },
                { "/:/", "/", "/", false, false },
                { "/tmp/var1", null, "/tmp/var1", false, false },
                { "/tmp/var1/", null, "/tmp/var1/", false, false },
                { "/tmp/var1:ro", null, "/tmp/var1", true, false },
                { "/var/log:/tmp/var1", "/var/log", "/tmp/var1", false, false },
                { "/var/log/:/tmp/var1/", "/var/log/", "/tmp/var1/", false, false },
                { "/var/log:/tmp/var1:ro", "/var/log", "/tmp/var1", true, false },
                { "named_volume:/tmp/var1", "named_volume", "/tmp/var1", false, false },
                { "named_volume:/tmp/var1:ro", "named_volume", "/tmp/var1", true, false },
                // negative test cases
                { "//", null, null, false, true },
                { "/tmp//", null, null, false, true },
                { ":/tmp", null, null, false, true },
                { "/tmp/var1:ro:", null, null, false, true },
                { ":/tmp/var1:rw", null, null, false, true },
                { "/var/log:/tmp/var1::", null, null, false, true },
                { "named_volume:named_volume", null, null, false, true },
                { "named_volume:named_volume:ro", null, null, false, true },
                { "named_volume:named_volume:named_volume", null, null, false, true },
                { "::", null, null, false, true },
                { "/var/log:/tmp/var1:rodent", null, null, false, true },
                { "/var/log:/tmp/var1:/tmp", null, null, false, true },
                { "./", null, null, false, true },
                { "../", null, null, false, true }
        });
    }

    public VolumeBindingTest(String volume, String hostPart, String containerPart,
            boolean readOnly, boolean expectError) {

        this.volume = volume;
        this.hostPart = hostPart;
        this.containerPart = containerPart;
        this.readOnly = readOnly;
        this.expectError = expectError;
    }

    @Test
    public void testVolumeString() {
        if (expectError) {
            try {
                VolumeBinding.fromString(volume);
                fail("Validation should have failed!");
            } catch (LocalizableValidationException e) {
                // Test succeeded!
            }
        } else {
            VolumeBinding volumeString = VolumeBinding.fromString(volume);
            assertEquals(hostPart, volumeString.getHostPart());
            assertEquals(containerPart, volumeString.getContainerPart());
            assertEquals(readOnly, volumeString.isReadOnly());
        }
    }
}
