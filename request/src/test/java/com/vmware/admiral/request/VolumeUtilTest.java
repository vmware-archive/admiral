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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.VolumeUtil;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.ServiceDocument;

public class VolumeUtilTest {

    @Test
    public void testValidateVolumeName() {
        VolumeUtil.validateLocalVolumeName("Vn");
        VolumeUtil.validateLocalVolumeName("name");
        VolumeUtil.validateLocalVolumeName("34name");
        VolumeUtil.validateLocalVolumeName("volume-name");
        VolumeUtil.validateLocalVolumeName("Volume_Name");
        VolumeUtil.validateLocalVolumeName("v0lum3.Nam3");
        VolumeUtil.validateLocalVolumeName("name_102f9858-9996-4183-aa4c-082769df38f0");

        testInvalidVolumeName("");
        testInvalidVolumeName("   ");
        testInvalidVolumeName(" \t  ");
        testInvalidVolumeName("v");
        testInvalidVolumeName("_volume");
        testInvalidVolumeName("volume@name");
        testInvalidVolumeName("#volume_name45");
        testInvalidVolumeName("втф");
        testInvalidVolumeName("vol name");
        testInvalidVolumeName("(vol name)");
    }

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
        } catch (LocalizableValidationException e) {
            assertEquals("Invalid volume directory.", e.getMessage());
        }
    }

    @Test
    public void testApplyNamedVolumeConstraints1() {
        //       C1  C3  C2   C4  C5   C6
        //        \  /\  /     \  /    |
        //         L1  L2       L3     L4

        ContainerVolumeDescription l1 = createVolumeDesc("L1", "local");
        ContainerVolumeDescription l2 = createVolumeDesc("L2", "local");
        ContainerVolumeDescription l3 = createVolumeDesc("L3", "local");
        ContainerVolumeDescription l4 = createVolumeDesc("L4", "local");

        ContainerDescription c1 = createContainerDesc("C1", "L1:/tmp");
        ContainerDescription c2 = createContainerDesc("C2", "L2:/tmp");
        ContainerDescription c3 = createContainerDesc("C3", "L1:/tmp", "L2:/etc");
        ContainerDescription c4 = createContainerDesc("C4", "L3:/tmp");
        ContainerDescription c5 = createContainerDesc("C5", "L3:/tmp");
        ContainerDescription c6 = createContainerDesc("C6", "L4:/tmp");

        CompositeDescriptionExpanded compositeDesc = createCompositeDesc(
                Arrays.asList(c1, c2, c3, c4, c5, c6, l1, l2, l3, l4));

        VolumeUtil.applyLocalNamedVolumeConstraints(compositeDesc.componentDescriptions);

        assertAffinities(Arrays.asList(c1, c2, c3));
        assertAffinities(Arrays.asList(c4, c5));
        assertNull(c6.affinity);
    }

    @Test
    public void testApplyNamedVolumeConstraints2() {
        //     C1    C2
        //     |    /  \
        //     Local    Custom (requires non-default driver)

        ContainerVolumeDescription local = createVolumeDesc("Local", "local");
        ContainerVolumeDescription custom = createVolumeDesc("Custom", "custom");

        ContainerDescription c1 = createContainerDesc("C1", "Local:/tmp");
        ContainerDescription c2 = createContainerDesc("C2", "Local:/tmp", "Custom:/etc" );

        CompositeDescriptionExpanded compositeDesc = createCompositeDesc(
                Arrays.asList(c1, c2, local, custom));

        VolumeUtil.applyLocalNamedVolumeConstraints(compositeDesc.componentDescriptions);

        assertNotNull(c1.affinity);
        assertEquals(1, c1.affinity.length);
        assertEquals("C2", c1.affinity[0]);
        assertNull(c2.affinity);
    }

    private CompositeDescriptionExpanded createCompositeDesc(
            List<ServiceDocument> components) {
        CompositeDescriptionExpanded compositeDescription = new CompositeDescriptionExpanded();
        compositeDescription.componentDescriptions = components.stream()
                .map(cd -> new ComponentDescription(cd, null, null, null))
                .collect(Collectors.toList());
        return compositeDescription;
    }

    private ContainerDescription createContainerDesc(String name, String... volumes) {
        ContainerDescription cd = new ContainerDescription();
        cd.name = name;
        cd.volumes = volumes;
        return cd;
    }

    private ContainerVolumeDescription createVolumeDesc(String name, String driver) {
        ContainerVolumeDescription vd = new ContainerVolumeDescription();
        vd.name = name;
        vd.driver = driver;
        return vd;
    }

    /**
     * Asserts that only one (whichever) of the containers has no affinities and every other
     * container has an affinity to it.
     */
    private void assertAffinities(List<ContainerDescription> containers) {
        List<ContainerDescription> containerGroup = new ArrayList<>(containers);
        List<ContainerDescription> noAffinities = containerGroup.stream()
                .filter(c -> c.affinity == null).collect(Collectors.toList());
        assertEquals(1, noAffinities.size());
        ContainerDescription noAffinity = noAffinities.get(0);
        containerGroup.remove(noAffinity);
        containerGroup.stream().forEach(cd -> {
            assertNotNull(cd.affinity);
            assertEquals(1, cd.affinity.length);
            assertEquals(noAffinity.name, cd.affinity[0]);
        });
    }

    private void testInvalidVolumeName(String name) {
        try {
            VolumeUtil.validateLocalVolumeName(name);
            fail("Volume name validation for [" + name + "] was expected to fail!");
        } catch (LocalizableValidationException e) {
            // error is expected
        }
    }
}
