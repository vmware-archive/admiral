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

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import com.vmware.xenon.common.LocalizableValidationException;
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

        ContainerVolumeDescription volumeDesc = createVolumeDescription("volume1", UNSUPPORTED_DRIVER);
        createVolumeState(volumeDesc);

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

        ContainerVolumeDescription volumeDescription1 = createVolumeDescription("volume1", driver1);
        ContainerVolumeState volumeState1 = createVolumeState(volumeDescription1);

        ContainerVolumeDescription volumeDescription2 = createVolumeDescription("volume2", driver2);
        ContainerVolumeState volumeState2 = createVolumeState(volumeDescription2);

        ContainerDescription desc = createContainerDescription(new String[] {
                volumeDescription1.name + ":/tmp", volumeDescription2.name + ":/tmp" });

        createDockerHostWithVolumeDrivers(driver1, driver2);

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

        ContainerVolumeDescription volumeDescription1 = createVolumeDescription("volume1", driver1);
        createVolumeState(volumeDescription1);

        ContainerVolumeDescription volumeDescription2 = createVolumeDescription("volume2", driver2);
        createVolumeState(volumeDescription2);

        ContainerDescription desc = createContainerDescription(new String[] {
                volumeDescription1.name + ":/tmp", volumeDescription2.name + ":/tmp" });

        createDockerHostWithVolumeDrivers(driver1);
        createDockerHostWithVolumeDrivers(driver2);
        createDockerHostWithVolumeDrivers(driver1, driver2);
        createDockerHostWithVolumeDrivers(driver1, driver2);

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

    @Test
    public void testFilterHostsWhenSingleLocalVolumeShared() throws Throwable {
        ContainerVolumeDescription sharedLocalVolume = createVolumeDescription("shared", "local");
        createVolumeState(sharedLocalVolume);

        ContainerDescription desc1 = createContainerDescription(new String[] { "shared:/tmp" });
        ContainerDescription desc2 = createContainerDescription(new String[] { "shared:/tmp" });

        createDockerHostWithVolumeDrivers("local");
        createDockerHostWithVolumeDrivers("local");
        createDockerHostWithVolumeDrivers("local");
        createDockerHostWithVolumeDrivers("local");
        createDockerHostWithVolumeDrivers("local");
        createDockerHostWithVolumeDrivers("local");
        createDockerHostWithVolumeDrivers("local");
        assertEquals(10, initialHostLinks.size());

        filter = new NamedVolumeAffinityHostFilter(host, desc1);
        Collection<HostSelection> selected = filter().values();
        assertEquals(10, selected.size());

        // select host
        String sharedHostLink = selected.iterator().next().hostLink;
        createContainer(desc1, sharedHostLink);

        filter = new NamedVolumeAffinityHostFilter(host, desc2);
        selected = filter().values();
        assertEquals(1, selected.size());
        assertEquals(sharedHostLink, selected.iterator().next().hostLink);
    }

    @Test
    public void testFailWhenLocalVolumesShared() throws Throwable {
        /* Containers and their respective volumes in order of placement:
                            (1st)   (3rd)   (2nd)
                              C1     C3     C2
                               \     /\     /
                               local1  local2
                                 |       |
                                 H1      H2
         */

        String h1Link = createDockerHostWithVolumeDrivers("local");
        String h2Link = createDockerHostWithVolumeDrivers("local");

        ContainerVolumeDescription local1 = createVolumeDescription("local1", "local");
        createVolumeState(local1);
        ContainerVolumeDescription local2 = createVolumeDescription("local2", "local");
        createVolumeState(local2);

        ContainerDescription c1 = createContainerDescription(new String[] { "local1:/tmp" });
        createContainer(c1, h1Link);
        ContainerDescription c2 = createContainerDescription(new String[] { "local2:/tmp" });
        createContainer(c2, h2Link);
        ContainerDescription c3 = createContainerDescription(new String[] {
                "local1:/etc", "local2:/tmp" });

        filter = new NamedVolumeAffinityHostFilter(host, c3);
        Throwable e = filter(initialHostLinks);
        if (e == null) {
            fail("Expected exception");
        }

        if (e instanceof LocalizableValidationException) {
            LocalizableValidationException le = (LocalizableValidationException) e;
            assertThat(le.getMessage(), startsWith("Detected multiple containers sharing local"));
        } else {
            fail("Expected LocalizableValidationException");
        }
    }

    @Test
    public void testFailWhenLocalVolumesShared2() throws Throwable {
        ContainerVolumeDescription local1 = createVolumeDescription("vol1", "local");
        createVolumeState(local1);

        String h1Link = createDockerHostWithVolumeDrivers("local");

        ContainerDescription c1 = createContainerDescription(new String[] { "vol1:/tmp" });
        // place c1 on host h1 and remove h1 from the list of available hosts for selection
        createContainer(c1, h1Link);
        initialHostLinks.remove(h1Link);
        assertEquals(3, initialHostLinks.size());

        ContainerDescription c2 = createContainerDescription(new String[] { "vol1:/tmp" });

        filter = new NamedVolumeAffinityHostFilter(host, c2);
        Throwable e = filter(initialHostLinks);
        if (e == null) {
            fail("Expected exception");
        }

        if (e instanceof LocalizableValidationException) {
            LocalizableValidationException le = (LocalizableValidationException) e;
            assertThat(le.getMessage(), startsWith("Unable to place containers sharing local volumes"));
        } else {
            fail("Expected LocalizableValidationException");
        }
    }

    @Test
    public void testChooseSameExternalVolumeHost() throws Throwable {
        String h1Link = createDockerHostWithVolumeDrivers("custom");
        String h2Link = createDockerHostWithVolumeDrivers("custom");
        String h3Link = createDockerHostWithVolumeDrivers("custom");

        // create external volume description
        ContainerVolumeDescription volumeDesc = createVolumeDescription("ext-vol", "custom");
        volumeDesc.external = true;
        doPatch(volumeDesc, volumeDesc.documentSelfLink);

        // create external volume state for each host
        ContainerVolumeState volume1 = createVolumeState(volumeDesc);
        volume1.parentLinks = Arrays.asList(h1Link);
        doPatch(volume1, volume1.documentSelfLink);

        ContainerVolumeState volume2 = createVolumeState(volumeDesc);
        volume2.parentLinks = Arrays.asList(h2Link);
        doPatch(volume2, volume2.documentSelfLink);

        ContainerVolumeState volume3 = createVolumeState(volumeDesc);
        volume3.parentLinks = Arrays.asList(h3Link);
        doPatch(volume3, volume3.documentSelfLink);

        // create container attached to the external volume
        ContainerDescription desc1 = createContainerDescription(new String[] { "ext-vol:/tmp" });

        filter = new NamedVolumeAffinityHostFilter(host, desc1);
        assertTrue(filter.isActive());
        Map<String, HostSelection> selectedHosts = filter();
        assertEquals(1, selectedHosts.size());
        String selectedHostLink = selectedHosts.keySet().iterator().next();
        assertThat(Arrays.asList(h1Link, h2Link, h3Link), hasItem(selectedHostLink));

        // create another container so that both containers are attached to the same external volume
        ContainerDescription desc2 = createContainerDescription(new String[] { "ext-vol:/tmp" });

        filter = new NamedVolumeAffinityHostFilter(host, desc2);
        selectedHosts = filter();
        assertEquals(1, selectedHosts.size());

        // assert that both containers chose the same host
        assertEquals(selectedHostLink, selectedHosts.keySet().iterator().next());
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

    private ContainerVolumeDescription createVolumeDescription(String name, String driver)
            throws Throwable {
        ContainerVolumeDescription desc = TestRequestStateFactory
                .createContainerVolumeDescription(name);
        desc.driver = driver;

        desc = doPost(desc, ContainerVolumeDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);

        return desc;
    }

    private ContainerVolumeState createVolumeState(ContainerVolumeDescription desc)
            throws Throwable {
        ContainerVolumeState containerVolume = new ContainerVolumeState();
        containerVolume.descriptionLink = desc.documentSelfLink;
        containerVolume.name = (desc.external != null && desc.external) ? desc.name
                : desc.name + UUID.randomUUID().toString();
        containerVolume.driver = desc.driver;
        containerVolume.compositeComponentLinks = new ArrayList<>();
        containerVolume.compositeComponentLinks.add(UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, state.contextId));
        containerVolume.external = desc.external;
        containerVolume.tenantLinks = desc.tenantLinks;
        containerVolume = doPost(containerVolume, ContainerVolumeService.FACTORY_LINK);
        assertNotNull(containerVolume);
        addForDeletion(containerVolume);
        return containerVolume;
    }

    private String createDockerHostWithVolumeDrivers(String... drivers) throws Throwable {
        String hostLink = createDockerHost(createDockerHostDescription(), createResourcePool(),
                true).documentSelfLink;

        ComputeState csPatch = new ComputeState();

        csPatch.documentSelfLink = hostLink;
        csPatch.customProperties = new HashMap<>();

        csPatch.customProperties.put(ContainerHostService.DOCKER_HOST_PLUGINS_PROP_NAME,
                createSupportedPluginsInfoString(new HashSet<>(Arrays.asList(drivers))));

        doPatch(csPatch, hostLink);

        initialHostLinks.add(hostLink);

        return hostLink;
    }
}
