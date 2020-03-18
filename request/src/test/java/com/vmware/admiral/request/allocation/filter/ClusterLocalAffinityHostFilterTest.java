/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.allocation.filter;

import static com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.DEFAULT_VOLUME_DRIVER;
import static com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.VMDK_VOLUME_DRIVER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.UriUtils;

public class ClusterLocalAffinityHostFilterTest extends BaseAffinityHostFilterTest {

    @Test
    public void testInactiveWithoutVolumes() throws Throwable {
        ContainerDescription desc = createDescription();
        desc.volumes = null;
        filter = new ClusterLocalAffinityHostFilter(host, desc);
        // filter is not active
        assertFalse(filter.isActive());
    }

    @Test
    public void testFilterWithoutNamedVolumes() throws Throwable {
        ContainerDescription desc = createDescription();
        desc.volumes = new String[] { "/var/some-volume:/var/some-volume" };
        filter = new ClusterLocalAffinityHostFilter(host, desc);
        // filter is not active
        assertFalse(filter.isActive());
    }

    @Test
    public void testFilterWithLocalNamedVolumes() throws Throwable {
        ContainerVolumeDescription volumeDesc = createVolumeDescription("Postgres",
                DEFAULT_VOLUME_DRIVER);
        CompositeDescription compositeDesc = createCompositeDesc(false, false, volumeDesc);
        String[] volumes = new String[] { volumeDesc.name + ":/tmp" };
        ContainerVolumeState volume = createVolumeState(volumeDesc);
        createCompositeComponent(compositeDesc, volume);

        ContainerDescription desc = createDescription();
        desc.volumes = volumes;

        filter = new ClusterLocalAffinityHostFilter(host, desc);
        assertTrue(filter.isActive());
        Map<String, HostSelection> selected = filter();
        // filter selects just one host
        assertEquals(1, selected.size());
    }

    @Test
    public void testFilterWithLocalNonNamedVolumes() throws Throwable {
        ContainerVolumeDescription volumeDesc = createVolumeDescription("Postgres",
                VMDK_VOLUME_DRIVER);
        CompositeDescription compositeDesc = createCompositeDesc(false, false, volumeDesc);
        String[] volumes = new String[] { volumeDesc.name + ":/tmp" };
        ContainerVolumeState volume = createVolumeState(volumeDesc);
        createCompositeComponent(compositeDesc, volume);

        ContainerDescription desc = createDescription();
        desc.volumes = volumes;

        filter = new ClusterLocalAffinityHostFilter(host, desc);
        assertTrue(filter.isActive());
        Map<String, HostSelection> selected = filter();
        // filter selects all the available hosts
        assertEquals(3, selected.size());
    }

    @Test
    public void testFilterInReservation() throws Throwable {
        String serviceLink = UriUtils.buildUriPath(ReservationTaskFactoryService.SELF_LINK,
                UUID.randomUUID().toString());
        state.serviceTaskCallback = ServiceTaskCallback.create(serviceLink);

        ContainerVolumeDescription volumeDesc = createVolumeDescription("Postgres",
                DEFAULT_VOLUME_DRIVER);
        String[] volumes = new String[] { volumeDesc.name + ":/tmp" };
        createVolumeState(volumeDesc);

        ContainerDescription desc = createDescription();
        desc.volumes = volumes;

        filter = new ClusterLocalAffinityHostFilter(host, desc);
        assertTrue(filter.isActive());
        Map<String, HostSelection> selected = filter();
        // filter selects all the available hosts in reservation stage
        assertEquals(3, selected.size());
    }

    @Test
    public void testFilterWithOneHostLeft() throws Throwable {
        String serviceLink = ReservationTaskFactoryService.SELF_LINK;
        state.serviceTaskCallback = ServiceTaskCallback.create(serviceLink);
        ContainerVolumeDescription volumeDesc = createVolumeDescription("Postgres",
                DEFAULT_VOLUME_DRIVER);
        String[] volumes = new String[] { volumeDesc.name + ":/tmp" };
        createVolumeState(volumeDesc);

        ContainerDescription desc = createDescription();
        desc.volumes = volumes;

        // remove all hosts but one
        int initialHostLinksSize = initialHostLinks.size();
        for (int i = 0; i < initialHostLinksSize - 1; i++) {
            initialHostLinks.remove(0);
        }

        filter = new ClusterLocalAffinityHostFilter(host, desc);
        assertTrue(filter.isActive());
        Map<String, HostSelection> selected = filter();
        // filter selects just one host
        assertEquals(1, selected.size());
    }

    private ContainerDescription createDescription() throws Throwable {
        ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc._cluster = 2;
        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc);

        return desc;
    }

    private ContainerVolumeDescription createVolumeDescription(String name, String driver)
            throws Throwable {
        ContainerVolumeDescription desc = TestRequestStateFactory
                .createContainerVolumeDescription(name);
        desc.driver = driver;

        desc = doPost(desc, ContainerVolumeDescriptionService.FACTORY_LINK);
        assertNotNull(desc);

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

        return containerVolume;
    }

}
