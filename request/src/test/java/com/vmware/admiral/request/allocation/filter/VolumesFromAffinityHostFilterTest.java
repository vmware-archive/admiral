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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class VolumesFromAffinityHostFilterTest extends BaseAffinityHostFilterTest {
    private static final String CONTAINER_NAME = "test-container23";

    @Test
    public void testReturnInitialHostListWhenNoContainerDescWithVolumesFrom() throws Throwable {
        ContainerDescription desc = createContainerDescription();
        assertNull(desc.volumesFrom);
        filter = new VolumesFromAffinityHostFilter(host, desc);

        Throwable e = filter(initialHostLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testErrorWhenNoMatchingContainerDescNameWithVolumesFrom() throws Throwable {
        ContainerDescription desc = createDescriptionsWithVolumesFrom(CONTAINER_NAME, 1, null)
                .get(0);
        createContainer(desc);

        String[] volumesFrom = new String[] { "not-existing-container-name" };
        ContainerDescription volumesFromDesc = createDescriptionsWithVolumesFrom(
                "random-name34", 1, volumesFrom).get(0);

        filter = new VolumesFromAffinityHostFilter(host, volumesFromDesc);

        Throwable e = filter(initialHostLinks);
        if (e == null) {
            fail("Expected an exception that no matching container descriptions found with name specified in volumes_from.");
        }
    }

    @Test
    public void testNoSelectionWhenContainerWithSameNameInVolumesFromButDifferentContextId()
            throws Throwable {
        ContainerDescription desc = createDescriptionsWithVolumesFrom(CONTAINER_NAME, 1, null).get(
                0);
        createContainerWithDifferentContextId(desc);

        String[] volumesFrom = new String[] { CONTAINER_NAME };
        ContainerDescription volumesFromDesc = createDescriptionsWithVolumesFrom(
                "random-name35", 1, volumesFrom).get(0);

        filter = new VolumesFromAffinityHostFilter(host, volumesFromDesc);

        Throwable e = filter(initialHostLinks);
        if (e == null) {
            fail("Expected an exception that no containers found.");
        }
    }

    @Test
    public void testSelectContainerHostWhenContainerAlreadyProvisionedAndSameHostAndContextId()
            throws Throwable {
        ContainerDescription desc = createDescriptionsWithVolumesFrom(CONTAINER_NAME, 5, null)
                .get(0);
        ContainerState container = createContainer(desc);

        assertEquals(UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, state.contextId),
                container.compositeComponentLink);

        String[] volumesFrom = new String[] { CONTAINER_NAME };
        ContainerDescription volumesFromDesc = createDescriptionsWithVolumesFrom(
                "random-name36", 1, volumesFrom).get(0);

        filter = new VolumesFromAffinityHostFilter(host, volumesFromDesc);

        Collection<String> expectedFilteredHostLinks = Arrays.asList(container.parentLink);
        Throwable e = filter(expectedFilteredHostLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testProvisionContainerWhenAnotherAlreadyProvisionedAndHasVolumesFromRules()
            throws Throwable {
        //Deploy containers B (volumes from) A. Then deploy A again, A should be placed on the same host as the other two
        ContainerDescription desc = createDescriptionsWithVolumesFrom(CONTAINER_NAME, 5, null)
                .get(0);
        ContainerState container = createContainer(desc);

        assertEquals(UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, state.contextId),
                container.compositeComponentLink);

        String[] volumesFrom = new String[] { CONTAINER_NAME };
        ContainerDescription volumesFromDesc = createDescriptionsWithVolumesFrom(
                "random-name36", 1, volumesFrom).get(0);
        createContainer(volumesFromDesc, container.parentLink);

        filter = new VolumesFromAffinityHostFilter(host, desc);

        Collection<String> expectedFilteredHostLinks = Arrays.asList(container.parentLink);

        state.addCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP, "true");
        Throwable e = filter(expectedFilteredHostLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testSelectContainerHostWhenMoreThanOneContainersInVolumesFromOnTheSameHost()
            throws Throwable {
        ContainerDescription desc1 = createDescriptionsWithVolumesFrom(CONTAINER_NAME, 2, null)
                .get(
                        0);
        ContainerState container1 = createContainer(desc1);

        String secondContainerName = "second_container";
        ContainerDescription desc2 = createDescriptionsWithVolumesFrom(secondContainerName, 2, null)
                .get(0);
        ContainerState container2 = createContainer(desc2);
        container2.parentLink = container1.parentLink;
        doOperation(container2, UriUtils.buildUri(host, container2.documentSelfLink),
                false, Action.PUT);

        String[] volumesFrom = new String[] { CONTAINER_NAME, secondContainerName };
        ContainerDescription volumesFromDesc = createDescriptionsWithVolumesFrom(
                "random-name36", 1, volumesFrom).get(0);

        filter = new VolumesFromAffinityHostFilter(host, volumesFromDesc);

        Collection<String> expectedFilteredHostLinks = Arrays.asList(container2.parentLink);
        Throwable e = filter(expectedFilteredHostLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testErrorWhenMoreThanOneContainersInVolumesFromButDifferentHost() throws Throwable {
        ContainerDescription desc1 = createDescriptionsWithVolumesFrom(CONTAINER_NAME, 2, null)
                .get(0);
        ContainerState container1 = createContainer(desc1);

        String secondContainerName = "second_container";
        ContainerDescription desc2 = createDescriptionsWithVolumesFrom(secondContainerName, 2, null)
                .get(0);
        ContainerState container2 = createContainer(desc2);

        // make sure the two containers have different hosts
        for (String hostLink : initialHostLinks) {
            if (!container1.parentLink.equals(hostLink)) {
                container2.parentLink = hostLink;
                doOperation(container2, UriUtils.buildUri(host, container2.documentSelfLink),
                        false, Action.PUT);
            }
        }

        String[] volumesFrom = new String[] { CONTAINER_NAME, secondContainerName };
        ContainerDescription volumesFromDesc = createDescriptionsWithVolumesFrom(
                "random-name36", 1, volumesFrom).get(0);

        filter = new VolumesFromAffinityHostFilter(host, volumesFromDesc);

        Throwable e = filter(initialHostLinks);
        if (e == null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testSelectContainerHostVolumesFromContainersReadOnlyIndication() throws Throwable {
        ContainerDescription desc1 = createDescriptionsWithVolumesFrom(CONTAINER_NAME, 2, null)
                .get(0);
        ContainerState container1 = createContainer(desc1);

        String[] volumesFrom = new String[] { CONTAINER_NAME + ":ro" };
        ContainerDescription volumesFromDesc = createDescriptionsWithVolumesFrom(
                "random-name38", 1, volumesFrom).get(0);

        filter = new VolumesFromAffinityHostFilter(host, volumesFromDesc);

        Collection<String> expectedFilteredHostLinks = Arrays.asList(container1.parentLink);
        Throwable e = filter(expectedFilteredHostLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    private List<ContainerDescription> createDescriptionsWithVolumesFrom(String name, int count,
            String[] volumesFrom) throws Throwable {
        // loop a few times to make sure the right host is not chosen by chance
        List<ContainerDescription> descs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
            desc.documentSelfLink = UUID.randomUUID().toString();
            desc.name = name;
            desc.volumesFrom = volumesFrom;
            desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
            assertNotNull(desc);
            addForDeletion(desc);
            descs.add(desc);
        }

        return descs;
    }
}
