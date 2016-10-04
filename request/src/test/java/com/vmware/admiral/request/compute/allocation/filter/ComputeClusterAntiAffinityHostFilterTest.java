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

package com.vmware.admiral.request.compute.allocation.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.content.TemplateComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;

public class ComputeClusterAntiAffinityHostFilterTest extends BaseComputeAffinityHostFilterTest {

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        throwErrorOnFilter = true;
    }

    @Test
    public void testReturnInitialHostListWhenNoPreviousContainers() throws Throwable {
        ComputeDescription desc = createDescription();

        filter = new ComputeClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testReturnDifferentHostLinksWhenPreviousContainers() throws Throwable {
        ComputeDescription desc = createDescription();

        createCompute(desc, expectedLinks.remove(0));

        filter = new ComputeClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testOnlyOneHostLeft() throws Throwable {
        ComputeDescription desc = createDescription();

        for (int i = 0; i < initialHostLinks.size() - 1; i++) {
            String hostLink = expectedLinks.remove(i);
            createCompute(desc, hostLink);
        }
        assertEquals(1, expectedLinks.size());

        filter = new ComputeClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testWhenAllHostsTaken() throws Throwable {
        ComputeDescription desc = createDescription();

        // allocate one compute on every host:
        for (int i = 0; i < initialHostLinks.size(); i++) {
            String hostLink = initialHostLinks.get(i);
            createCompute(desc, hostLink);
        }

        assertEquals(initialHostLinks.size(), expectedLinks.size());

        filter = new ComputeClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testWhenAllHostsTakenAndOnlyOneLeftWithLowerCount() throws Throwable {
        ComputeDescription desc = createDescription();

        // allocate one compute on every host:
        for (int i = 0; i < initialHostLinks.size(); i++) {
            String hostLink = initialHostLinks.get(i);
            createCompute(desc, hostLink);
        }

        // allocate one more compute on every host but the last one:
        for (int i = 0; i < initialHostLinks.size() - 1; i++) {
            String hostLink = expectedLinks.remove(i);
            createCompute(desc, hostLink);
        }
        assertEquals(1, expectedLinks.size());

        filter = new ComputeClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testWhenAllHostsTakenAndOnlyOneLeftWithLowerCountAndResourceCountGreaterThanOne()
            throws Throwable {
        ComputeDescription desc = createDescription();

        // allocate one compute on every host:
        for (int i = 0; i < initialHostLinks.size(); i++) {
            String hostLink = initialHostLinks.get(i);
            createCompute(desc, hostLink);
        }

        // allocate one more compute on every host but the last one:
        for (int i = 0; i < initialHostLinks.size() - 1; i++) {
            String hostLink = expectedLinks.remove(i);
            createCompute(desc, hostLink);
        }
        assertEquals(1, expectedLinks.size());

        state.resourceCount = 3;
        filter = new ComputeClusterAntiAffinityHostFilter(host, desc);

        filter(initialHostLinks);

        state.resourceCount = 3;
        filter = new ComputeClusterAntiAffinityHostFilter(host, desc);

        filter(initialHostLinks);
    }

    @Test
    public void testOnlyOneHostLeftAndContainerWithSameDescButDifferentContext() throws Throwable {
        ComputeDescription desc = createDescription();

        for (int i = 0; i < initialHostLinks.size() - 1; i++) {
            String hostLink = expectedLinks.remove(i);
            createCompute(desc, hostLink);
        }

        assertEquals(1, expectedLinks.size());

        createComputeWithDifferentContextId(desc, expectedLinks.get(0));

        filter = new ComputeClusterAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testOnlyOneHostLeftButResourceCountGreaterThanOne() throws Throwable {
        ComputeDescription desc = createDescription();

        for (int i = 0; i < initialHostLinks.size() - 1; i++) {
            String hostLink = expectedLinks.remove(i);
            createCompute(desc, hostLink);
        }
        assertEquals(1, expectedLinks.size());

        state.resourceCount = 3;
        filter = new ComputeClusterAntiAffinityHostFilter(host, desc);

        filter(initialHostLinks);
    }

    private ComputeDescription createDescription() throws Throwable {
        ComputeDescription desc = new ComputeDescription();
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc.name = "test-name-343";
        desc.customProperties = new HashMap<>();
        desc.customProperties
                .put(TemplateComputeDescription.CUSTOM_PROP_NAME_CLUSTER_SIZE,
                        String.valueOf(initialHostLinks.size() * 2));
        desc = doPost(desc, ComputeDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);

        return desc;
    }
}

