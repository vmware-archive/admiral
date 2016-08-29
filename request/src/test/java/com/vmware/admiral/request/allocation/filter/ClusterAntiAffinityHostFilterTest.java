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

import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.request.util.TestRequestStateFactory;

public class ClusterAntiAffinityHostFilterTest extends BaseAffinityHostFilterTest {

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        throwErrorOnFilter = true;
    }

    @Test
    public void testReturnInitialHostListWhenNoPreviousContainers() throws Throwable {
        ContainerDescription desc = createDescription();

        filter = new ClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testReturnDifferntHostLinksWhenPreviousContainers() throws Throwable {
        ContainerDescription desc = createDescription();

        createContainer(desc, expectedLinks.remove(0));

        filter = new ClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testOnlyOneHostLeft() throws Throwable {
        ContainerDescription desc = createDescription();

        for (int i = 0; i < initialHostLinks.size() - 1; i++) {
            String hostLink = expectedLinks.remove(i);
            createContainer(desc, hostLink);
        }
        assertEquals(1, expectedLinks.size());

        filter = new ClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testWhenAllHostsTaken() throws Throwable {
        ContainerDescription desc = createDescription();

        // allocate one container on every host:
        for (int i = 0; i < initialHostLinks.size(); i++) {
            String hostLink = initialHostLinks.get(i);
            createContainer(desc, hostLink);
        }

        assertEquals(initialHostLinks.size(), expectedLinks.size());

        filter = new ClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testWhenAllHostsTakenAndOnlyOneLeftWithLowerCount() throws Throwable {
        ContainerDescription desc = createDescription();

        // allocate one container on every host:
        for (int i = 0; i < initialHostLinks.size(); i++) {
            String hostLink = initialHostLinks.get(i);
            createContainer(desc, hostLink);
        }

        // allocate one more container on every host but the last one:
        for (int i = 0; i < initialHostLinks.size() - 1; i++) {
            String hostLink = expectedLinks.remove(i);
            createContainer(desc, hostLink);
        }
        assertEquals(1, expectedLinks.size());

        filter = new ClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testWhenAllHostsTakenAndOnlyOneLeftWithLowerCountAndResourceCountGreaterThanOne()
            throws Throwable {
        ContainerDescription desc = createDescription();

        // allocate one container on every host:
        for (int i = 0; i < initialHostLinks.size(); i++) {
            String hostLink = initialHostLinks.get(i);
            createContainer(desc, hostLink);
        }

        // allocate one more container on every host but the last one:
        for (int i = 0; i < initialHostLinks.size() - 1; i++) {
            String hostLink = expectedLinks.remove(i);
            createContainer(desc, hostLink);
        }
        assertEquals(1, expectedLinks.size());

        state.resourceCount = 3;
        filter = new ClusterAntiAffinityHostFilter(host, desc);

        filter(initialHostLinks);

        state.resourceCount = 3;
        filter = new ClusterAntiAffinityHostFilter(host, desc);

        filter(initialHostLinks);
    }

    @Test
    public void testOnlyOneHostLeftAndContainerWithSameDescButDifferentContext() throws Throwable {
        ContainerDescription desc = createDescription();

        for (int i = 0; i < initialHostLinks.size() - 1; i++) {
            String hostLink = expectedLinks.remove(i);
            createContainer(desc, hostLink);
        }

        assertEquals(1, expectedLinks.size());

        createContainerWithDifferentContextId(desc, expectedLinks.get(0));

        filter = new ClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testOnlyOneHostLeftButResourceCountGreaterThanOne() throws Throwable {
        ContainerDescription desc = createDescription();

        for (int i = 0; i < initialHostLinks.size() - 1; i++) {
            String hostLink = expectedLinks.remove(i);
            createContainer(desc, hostLink);
        }
        assertEquals(1, expectedLinks.size());

        state.resourceCount = 3;
        filter = new ClusterAntiAffinityHostFilter(host, desc);

        filter(initialHostLinks);
    }

    private ContainerDescription createDescription() throws Throwable {
        ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc.name = "test-name-343";
        desc._cluster = initialHostLinks.size() * 2;
        desc.links = null;
        desc.volumesFrom = null;
        desc.pod = null;
        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);

        return desc;
    }
}
