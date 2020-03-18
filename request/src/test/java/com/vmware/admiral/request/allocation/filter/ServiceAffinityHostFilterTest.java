/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.allocation.filter;

import static com.vmware.admiral.request.allocation.filter.AffinityConstraint.AffinityConstraintType.HARD;
import static com.vmware.admiral.request.allocation.filter.AffinityConstraint.AffinityConstraintType.SOFT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.xenon.common.UriUtils;

public class ServiceAffinityHostFilterTest extends BaseAffinityHostFilterTest {
    private static final String CONTAINER_NAME = "test-container27";

    @Test
    public void testReturnInitialHostListWhenNoContainerDescWithAfinity() throws Throwable {
        ContainerDescription desc = createDescriptions(CONTAINER_NAME, 1, null).get(0);
        assertNull(desc.affinity);
        filter = new ServiceAffinityHostFilter(host, desc);

        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testErrorWhenNoMatchingContainerDescNameWithAfinity() throws Throwable {
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME, 1, null).get(0);
        createContainer(desc1, initialHostLinks.get(0));

        String[] affinity = new String[] { "not-existing-container-name" };
        ContainerDescription desc = createDescriptions("random-name34", 1, affinity).get(0);

        filter = new ServiceAffinityHostFilter(host, desc);

        Throwable e = filter(expectedLinks);
        if (e == null) {
            fail("Expected an exception that no matching container descriptions found with name specified in lins.");
        }
    }

    @Test
    public void testNoSelectionWhenContainerWithSameNameInAffinityButDifferentContextId()
            throws Throwable {
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME, 1, null).get(0);
        createContainerWithDifferentContextId(desc1, initialHostLinks.get(0));

        String[] affinity = new String[] { CONTAINER_NAME };
        ContainerDescription desc = createDescriptions("random-name35", 1, affinity).get(0);

        filter = new ServiceAffinityHostFilter(host, desc);

        Throwable e = filter(expectedLinks);
        if (e == null) {
            fail("Expected an exception that no containers found.");
        }
    }

    @Test
    public void testSelectContainerHostWhenContainerAlreadyProvisionedAndSameHostAndContextId()
            throws Throwable {
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME, 5, null).get(0);
        ContainerState container = createContainer(desc1, initialHostLinks.get(0));

        assertEquals(UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, state.contextId),
                container.compositeComponentLink);

        String[] affinity = new String[] { CONTAINER_NAME };
        ContainerDescription desc = createDescriptions("random-name36", 1, affinity).get(0);

        filter = new ServiceAffinityHostFilter(host, desc);

        expectedLinks = Arrays.asList(container.parentLink);
        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testSelectContainerHostWhenMoreThanOneContainersInAffinityOnTheSameHost()
            throws Throwable {
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME, 2, null).get(0);
        String hostLink = initialHostLinks.get(0);
        createContainer(desc1, hostLink);

        String secondContainerName = "second_container";
        ContainerDescription desc2 = createDescriptions(secondContainerName, 2, null).get(0);
        createContainer(desc2, hostLink);

        String[] affinity = new String[] { CONTAINER_NAME, secondContainerName };
        ContainerDescription desc = createDescriptions("random-name36", 1, affinity).get(0);

        filter = new ServiceAffinityHostFilter(host, desc);

        expectedLinks = Arrays.asList(hostLink);
        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testErrorWhenMoreThanOneContainersButDifferentHostsInAffinity() throws Throwable {
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME, 2, null).get(0);
        String hostLink1 = initialHostLinks.get(0);
        createContainer(desc1, hostLink1);

        String secondContainerName = "second_container";
        ContainerDescription desc2 = createDescriptions(secondContainerName, 2, null).get(0);
        String hostLink2 = initialHostLinks.get(1);
        createContainer(desc2, hostLink2);

        String[] affinity = new String[] { CONTAINER_NAME, secondContainerName };
        ContainerDescription desc = createDescriptions("random-name36", 1, affinity).get(0);

        filter = new ServiceAffinityHostFilter(host, desc);

        Throwable e = filter(expectedLinks);
        if (e == null) {
            fail("Exception expected that can't be placed on two different hosts.");
        }
    }

    @Test
    public void testProvisionContainerWhenAnotherAlreadyProvisionedAndHasAffinityRules()
            throws Throwable {

        //Create a container A which has affinity to B. Deploy A, then deploy B. B should be placed on the same host as A
        String secondContainerName = "second_container";
        ContainerDescription desc2 = createDescriptions(secondContainerName, 2, null).get(0);

        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME, 2,
                new String[] { secondContainerName + HARD.getValue() }).get(0);
        String hostLink1 = initialHostLinks.get(2);
        createContainer(desc1, hostLink1);

        filter = new ServiceAffinityHostFilter(host, desc2);

        expectedLinks = Arrays.asList(hostLink1);
        state.addCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP, "true");
        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Container should be placed exactly on host " + hostLink1);
        }

    }

    @Test
    public void testDoNotErrorWhenMoreThanOneContainersWithDifferentHostsInAffinityAndSoftAfinity()
            throws Throwable {
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME, 2, null).get(0);
        String hostLink1 = initialHostLinks.get(0);
        createContainer(desc1, hostLink1);

        String secondContainerName = "second_container";
        ContainerDescription desc2 = createDescriptions(secondContainerName, 2, null).get(0);
        String hostLink2 = initialHostLinks.get(1);
        createContainer(desc2, hostLink2);

        String[] affinity = new String[] { CONTAINER_NAME,
                secondContainerName + SOFT.getValue() };
        ContainerDescription desc = createDescriptions("random-name36", 1, affinity).get(0);

        filter = new ServiceAffinityHostFilter(host, desc);

        // since only the first container name is hard constraint (by default)
        // don't throw error and return only the first host (second name is with soft constraint)
        Throwable e = filter(Arrays.asList(hostLink1));
        if (e != null) {
            fail("Unexpected exception: " + e);
        }

        // when both affinities are soft constraints:
        affinity = new String[] { CONTAINER_NAME + SOFT.getValue(),
                secondContainerName + SOFT.getValue() };
        desc = createDescriptions("random-name37", 1, affinity).get(0);

        filter = new ServiceAffinityHostFilter(host, desc);

        // Both hosts evaluated are soft constraints so return both of them.
        e = filter(Arrays.asList(hostLink1, hostLink2));
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    private List<ContainerDescription> createDescriptions(String name, int count,
            String[] affinity) throws Throwable {
        // loop a few times to make sure the right host is not chosen by chance
        List<ContainerDescription> descs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
            desc.documentSelfLink = UUID.randomUUID().toString();
            desc.name = name;
            desc.affinity = affinity;
            desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
            assertNotNull(desc);
            addForDeletion(desc);
            descs.add(desc);
        }

        return descs;
    }
}
