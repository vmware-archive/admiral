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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.request.allocation.filter.AffinityConstraint.AffinityConstraintType.HARD;
import static com.vmware.admiral.request.allocation.filter.AffinityConstraint.AffinityConstraintType.SOFT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.content.TemplateComputeDescription;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class ComputeServiceAffinityHostFilterTest extends BaseComputeAffinityHostFilterTest {
    private static final String COMPUTE_NAME = "test-compute27";

    @Test
    public void testReturnInitialHostListWhenNoComputeDescWithAfinity() throws Throwable {
        ComputeDescription desc = createDescriptions(COMPUTE_NAME, 1, null).get(0);
        assertTrue(TemplateComputeDescription.getAffinityNames(desc).isEmpty());
        filter = new ComputeServiceAffinityHostFilter(host, desc);

        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testErrorWhenNoMatchingComputeDescNameWithAfinity() throws Throwable {
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME, 1, null).get(0);
        createCompute(desc1, initialHostLinks.get(0));

        String[] affinity = new String[] { "not-existing-compute-name" };
        ComputeDescription desc = createDescriptions("random-name34", 1, affinity).get(0);

        filter = new ComputeServiceAffinityHostFilter(host, desc);

        Throwable e = filter(expectedLinks);
        if (e == null) {
            fail("Expected an exception that no matching compute descriptions found with name specified in lins.");
        }
    }

    @Test
    public void testNoSelectionWhenComputeWithSameNameInAffinityButDifferentContextId()
            throws Throwable {
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME, 1, null).get(0);
        createComputeWithDifferentContextId(desc1, initialHostLinks.get(0));

        String[] affinity = new String[] { COMPUTE_NAME };
        ComputeDescription desc = createDescriptions("random-name35", 1, affinity).get(0);

        filter = new ComputeServiceAffinityHostFilter(host, desc);

        Throwable e = filter(expectedLinks);
        if (e == null) {
            fail("Expected an exception that no computes found.");
        }
    }

    @Test
    public void testSelectComputeHostWhenComputeAlreadyProvisionedAndSameHostAndContextId()
            throws Throwable {
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME, 5, null).get(0);
        ComputeState compute = createCompute(desc1, initialHostLinks.get(0));

        //assertEquals(UriUtils.buildUriPath(
        //        CompositeComponentFactoryService.SELF_LINK, state.contextId),
        //        compute.compositeComponentLink);

        String[] affinity = new String[] { COMPUTE_NAME };
        ComputeDescription desc = createDescriptions("random-name36", 1, affinity).get(0);

        filter = new ComputeServiceAffinityHostFilter(host, desc);

        expectedLinks = Arrays.asList(compute.parentLink);
        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testSelectComputeHostWhenMoreThanOneComputesInAffinityOnTheSameHost()
            throws Throwable {
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME, 2, null).get(0);
        String hostLink = initialHostLinks.get(0);
        createCompute(desc1, hostLink);

        String secondComputeName = "second_compute";
        ComputeDescription desc2 = createDescriptions(secondComputeName, 2, null).get(0);
        createCompute(desc2, hostLink);

        String[] affinity = new String[] { COMPUTE_NAME, secondComputeName };
        ComputeDescription desc = createDescriptions("random-name36", 1, affinity).get(0);

        filter = new ComputeServiceAffinityHostFilter(host, desc);

        expectedLinks = Arrays.asList(hostLink);
        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testErrorWhenMoreThanOneComputesButDifferentHostsInAffinity() throws Throwable {
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME, 2, null).get(0);
        String hostLink1 = initialHostLinks.get(0);
        createCompute(desc1, hostLink1);

        String secondComputeName = "second_compute";
        ComputeDescription desc2 = createDescriptions(secondComputeName, 2, null).get(0);
        String hostLink2 = initialHostLinks.get(1);
        createCompute(desc2, hostLink2);

        String[] affinity = new String[] { COMPUTE_NAME, secondComputeName };
        ComputeDescription desc = createDescriptions("random-name36", 1, affinity).get(0);

        filter = new ComputeServiceAffinityHostFilter(host, desc);

        Throwable e = filter(expectedLinks);
        if (e == null) {
            fail("Exception expected that can't be placed on two different hosts.");
        }
    }

    @Test
    public void testProvisionComputeWhenAnotherAlreadyProvisionedAndHasAffinityRules()
            throws Throwable {

        //Create a compute A which has affinity to B. Deploy A, then deploy B. B should be placed on the same host as A
        String secondComputeName = "second_compute";
        ComputeDescription desc2 = createDescriptions(secondComputeName, 2, null).get(0);

        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME, 2,
                new String[] { secondComputeName + HARD.getValue() }).get(0);
        String hostLink1 = initialHostLinks.get(2);
        createCompute(desc1, hostLink1);

        filter = new ComputeServiceAffinityHostFilter(host, desc2);

        expectedLinks = Arrays.asList(hostLink1);
        state.addCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP, "true");
        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Compute should be placed exactly on host " + hostLink1);
        }

    }

    @Test
    public void testDoNotErrorWhenMoreThanOneComputesWithDifferentHostsInAffinityAndSoftAfinity()
            throws Throwable {
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME, 2, null).get(0);
        String hostLink1 = initialHostLinks.get(0);
        createCompute(desc1, hostLink1);

        String secondComputeName = "second_compute";
        ComputeDescription desc2 = createDescriptions(secondComputeName, 2, null).get(0);
        String hostLink2 = initialHostLinks.get(1);
        createCompute(desc2, hostLink2);

        String[] affinity = new String[] { COMPUTE_NAME,
                secondComputeName + SOFT.getValue() };
        ComputeDescription desc = createDescriptions("random-name36", 1, affinity).get(0);

        filter = new ComputeServiceAffinityHostFilter(host, desc);

        // since only the first compute name is hard constraint (by default)
        // don't throw error and return only the first host (second name is with soft constraint)
        Throwable e = filter(Arrays.asList(hostLink1));
        if (e != null) {
            fail("Unexpected exception: " + e);
        }

        // when both affinities are soft constraints:
        affinity = new String[] { COMPUTE_NAME + SOFT.getValue(),
                secondComputeName + SOFT.getValue() };
        desc = createDescriptions("random-name37", 1, affinity).get(0);

        filter = new ComputeServiceAffinityHostFilter(host, desc);

        // Both hosts evaluated are soft constraints so return both of them.
        e = filter(Arrays.asList(hostLink1, hostLink2));
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    private List<ComputeDescription> createDescriptions(String name, int count,
            String[] affinity) throws Throwable {
        // loop a few times to make sure the right host is not chosen by chance
        List<ComputeDescription> descs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ComputeDescription desc = new ComputeDescription();
            desc.documentSelfLink = UUID.randomUUID().toString();
            desc.name = name;
            TemplateComputeDescription.setAffinityNames(desc, affinity == null ?
                    Collections.emptyList() : Arrays.asList(affinity));
            desc = doPost(desc, ComputeDescriptionService.FACTORY_LINK);
            assertNotNull(desc);
            addForDeletion(desc);
            descs.add(desc);
        }

        return descs;
    }
}
