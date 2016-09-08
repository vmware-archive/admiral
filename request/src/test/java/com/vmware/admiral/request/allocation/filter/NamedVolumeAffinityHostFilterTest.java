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

package com.vmware.admiral.request.allocation.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.UriUtils;

public class NamedVolumeAffinityHostFilterTest extends BaseAffinityHostFilterTest {
    private static final String UNSUPPORTED_DRIVER = "unsupported-driver";

    @Test
    public void testFilterDoesNotAffectHosts() throws Throwable {
        assertEquals(3, initialHostLinks.size());

        ContainerDescription desc = createContainerDescription(new String[] {});
        createContainer(desc, initialHostLinks.get(0));
        createContainer(desc, initialHostLinks.get(1));

        filter = new NamedVolumeAffinityHostFilter(host, desc);
        assertFalse(filter.isActive());
        Map<String, HostSelection> selected = filter();
        assertEquals(3, selected.size());
    }

    @Test
    public void testFilterHostsWhenNoSupportedDriversAvailable() throws Throwable {
        assertEquals(3, initialHostLinks.size());

        ContainerVolumeDescription volumeDesc = createVolumeDescription(UNSUPPORTED_DRIVER);
        createVolumeState(volumeDesc, volumeDesc.name);
        String[] volumes = new String[] { volumeDesc.name + ":/tmp" };
        ContainerDescription desc = createContainerDescription(volumes);

        filter = new NamedVolumeAffinityHostFilter(host, desc);
        assertTrue(filter.isActive());

        Throwable e = filter(Collections.emptyList());
        if (e == null) {
            fail("Expected exception for no hosts found.");
        }
    }

    @Test
    public void testResolveActualVolumeStateNames() throws Throwable {
        String driver1 = "my-test-volume";
        String driver2 = "my-other-test-volume";

        ContainerVolumeDescription volumeDescription1 = createVolumeDescription(driver1);
        String randomName = volumeDescription1.name + "-name35";
        ContainerVolumeState volumeState1 = createVolumeState(volumeDescription1, randomName);

        ContainerVolumeDescription volumeDescription2 = createVolumeDescription(driver2);
        randomName = volumeDescription2.name + "-name270";
        ContainerVolumeState volumeState2 = createVolumeState(volumeDescription2, randomName);

        ContainerDescription desc = createContainerDescription(new String[] {
                volumeDescription1.name + ":/tmp", volumeDescription2.name + ":/tmp" });

        initialHostLinks.add(createDockerHostWithVolumeDrivers(
                new HashSet<String>(Arrays.asList(driver1, driver2))));

        filter = new NamedVolumeAffinityHostFilter(host, desc);
        Map<String, HostSelection> selected = filter();
        assertEquals(1, selected.size());

        for (HostSelection hs : selected.values()) {
            String mappedName = hs.mapNames(new String[] { volumeDescription1.name })[0];
            assertEquals(volumeState1.name, mappedName);

            mappedName = hs.mapNames(new String[] { volumeDescription2.name })[0];
            assertEquals(volumeState2.name, mappedName);
        }
    }

    @Test
    public void testFilterHostsWithSupportedVolumeDriver() throws Throwable {
        String driver1 = "my-test-driver-1";
        String driver2 = "my-test-driver-2";

        ContainerVolumeDescription volumeDescription1 = createVolumeDescription(driver1);
        String randomName = volumeDescription1.name + "-name35";
        createVolumeState(volumeDescription1, randomName);

        ContainerVolumeDescription volumeDescription2 = createVolumeDescription(driver2);
        randomName = volumeDescription2.name + "-name270";
        createVolumeState(volumeDescription2, randomName);

        ContainerDescription desc = createContainerDescription(new String[] {
                volumeDescription1.name + ":/tmp", volumeDescription2.name + ":/tmp" });

        initialHostLinks.add(createDockerHostWithVolumeDrivers(
                new HashSet<String>(Arrays.asList(driver1))));
        initialHostLinks.add(createDockerHostWithVolumeDrivers(
                new HashSet<String>(Arrays.asList(driver2))));
        initialHostLinks.add(createDockerHostWithVolumeDrivers(
                new HashSet<String>(Arrays.asList(driver1, driver2))));
        initialHostLinks.add(createDockerHostWithVolumeDrivers(
                new HashSet<String>(Arrays.asList(driver1, driver2, "another-custom-driver"))));

        filter = new NamedVolumeAffinityHostFilter(host, desc);
        Map<String, HostSelection> selected = filter();
        assertEquals(2, selected.size());
    }

    @Test
    public void testAffinityConstraintsToVolumes() throws Throwable {
        ContainerDescription desc = createContainerDescription(new String[]
                { "volume1:/tmp", "volume2:/tmp", "volume3:/tmp" });
        filter = new NamedVolumeAffinityHostFilter(host, desc);
        assertTrue(filter.isActive());

        Set<String> affinityConstraintsKeys = filter.getAffinityConstraints().keySet();

        Set<Object> expectedVolumes = new HashSet<>();
        expectedVolumes.add("volume1");
        expectedVolumes.add("volume2");
        expectedVolumes.add("volume3");
        assertEquals(expectedVolumes, affinityConstraintsKeys);
    }

    private ContainerDescription createContainerDescription(String[] volumes)
            throws Throwable {
        ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc.name = desc.documentSelfLink;
        desc.volumes = volumes;

        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);

        return desc;
    }

    private ContainerVolumeDescription createVolumeDescription(String driver)
            throws Throwable {
        ContainerVolumeDescription desc = new ContainerVolumeDescription();
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc.name = UUID.randomUUID().toString();
        desc.driver = driver;

        desc = doPost(desc, ContainerVolumeDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);

        return desc;
    }

    private ContainerVolumeState createVolumeState(ContainerVolumeDescription desc, String name)
            throws Throwable {
        ContainerVolumeState containerVolume = new ContainerVolumeState();
        containerVolume.descriptionLink = desc.documentSelfLink;
        containerVolume.name = name;
        containerVolume.driver = desc.driver;
        containerVolume.compositeComponentLink = UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, state.contextId);
        containerVolume = doPost(containerVolume, ContainerVolumeService.FACTORY_LINK);
        assertNotNull(containerVolume);
        addForDeletion(containerVolume);
        return containerVolume;
    }

    private String createDockerHostWithVolumeDrivers(Set<String> drivers) throws Throwable {
        String hostLink = createDockerHost(createDockerHostDescription(), createResourcePool(),
                true).documentSelfLink;

        ComputeState csPatch = new ComputeState();

        csPatch.documentSelfLink = hostLink;
        csPatch.customProperties = new HashMap<>();

        csPatch.customProperties.put(ContainerHostService.DOCKER_HOST_PLUGINS_PROP_NAME,
                createSupportedPluginsInfoString(drivers));

        doPatch(csPatch, hostLink);

        return hostLink;
    }
}
