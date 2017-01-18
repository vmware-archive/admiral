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

import static com.vmware.admiral.request.allocation.filter.AffinityConstraint.AffinityConstraintType.ANTI_AFFINITY_PREFIX;
import static com.vmware.admiral.request.allocation.filter.AffinityConstraint.AffinityConstraintType.SOFT;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.content.TemplateComputeDescription;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class ComputeServiceAntiAffinityHostFilterTest extends BaseComputeAffinityHostFilterTest {
    private static final String COMPUTE_NAME1 = "test-compute1";
    private static final String COMPUTE_NAME2 = "test-compute2";
    private static final String COMPUTE_NAME3 = "test-compute3";

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        throwErrorOnFilter = true;
    }

    @Test
    public void testReturnInitialHostListWhenNoComputeDescWithAntiAfinity() throws Throwable {
        ComputeDescription desc = createDescriptions(COMPUTE_NAME1);
        assertTrue(TemplateComputeDescription.getAffinityNames(desc).isEmpty());
        filter = new ComputeServiceAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testReturnInitialHostListWhenNoMatchingComputeDescNameWithAntiAfinity()
            throws Throwable {
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME1);
        createCompute(desc1, initialHostLinks.get(0));

        String[] anti_affinity = new String[] { ANTI_AFFINITY_PREFIX
                + "not-existing-compute-name" };
        ComputeDescription desc = createDescriptions(anti_affinity);

        filter = new ComputeServiceAntiAffinityHostFilter(
                host, desc);

        filter(initialHostLinks);
    }

    @Test
    public void testReturnInitialHostListWhenComputeWithSameNameInAntiAffinityButDifferentContextId()
            throws Throwable {
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME1);
        createComputeWithDifferentContextId(desc1, initialHostLinks.get(0));

        String[] anti_affinity = new String[] { ANTI_AFFINITY_PREFIX + COMPUTE_NAME1 };
        ComputeDescription desc = createDescriptions(anti_affinity);

        filter = new ComputeServiceAntiAffinityHostFilter(
                host, desc);

        filter(initialHostLinks);
    }

    @Test
    public void testDoNotSelectComputeHostWhenComputeAlreadyProvisionedAndSameHostAndContextId()
            throws Throwable {
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME1);
        ComputeState compute = createCompute(desc1, initialHostLinks.get(0));

        //assertEquals(UriUtils.buildUriPath(
        //        CompositeComponentFactoryService.SELF_LINK, state.contextId),
        //        compute.compositeComponentLink);

        String[] anti_affinity = new String[] { ANTI_AFFINITY_PREFIX + COMPUTE_NAME1 };
        ComputeDescription desc = createDescriptions(anti_affinity);

        filter = new ComputeServiceAntiAffinityHostFilter(host, desc);

        expectedLinks.remove(compute.parentLink);

        filter(expectedLinks);
    }

    @Test
    public void testMoreThanOneComputesWithDifferentHostsInAffinity() throws Throwable {
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME1);
        String hostLink1 = initialHostLinks.get(0);
        createCompute(desc1, hostLink1);

        ComputeDescription desc2 = createDescriptions(COMPUTE_NAME2);
        String hostLink2 = initialHostLinks.get(1);
        createCompute(desc2, hostLink2);

        String[] anti_affinity = new String[] {
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME1,
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME2 };
        ComputeDescription desc = createDescriptions(anti_affinity);

        filter = new ComputeServiceAntiAffinityHostFilter(host, desc);

        expectedLinks.remove(hostLink1);
        expectedLinks.remove(hostLink2);

        filter(expectedLinks);
    }

    @Test
    public void testProvisionComputeWhenAnotherAlreadyProvisionedAndHasAntiAffinityRules()
            throws Throwable {

        //Create a compute A which has anti affinity to B. Deploy A, then deploy B. B should NOT be placed on the same host as A
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME1, new String[] {
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME2 });
        String hostLink2 = initialHostLinks.get(1);
        createCompute(desc1, hostLink2);

        ComputeDescription desc2 = createDescriptions(COMPUTE_NAME2);

        filter = new ComputeServiceAntiAffinityHostFilter(host, desc2);

        expectedLinks.remove(hostLink2);

        state.addCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP, "true");
        filter(expectedLinks);
    }

    @Test
    public void testSoftAntiAfinity() throws Throwable {
        ComputeDescription desc1 = createDescriptions(COMPUTE_NAME1);
        String hostLink1 = initialHostLinks.get(0);
        createCompute(desc1, hostLink1);

        ComputeDescription desc2 = createDescriptions(COMPUTE_NAME2);
        String hostLink2 = initialHostLinks.get(1);
        createCompute(desc2, hostLink2);

        ComputeDescription desc3 = createDescriptions(COMPUTE_NAME3);
        String hostLink3 = initialHostLinks.get(2);
        createCompute(desc3, hostLink3);

        String[] anti_affinity = new String[] {
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME1,
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME2,
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME3 };
        ComputeDescription desc = createDescriptions(anti_affinity);

        filter = new ComputeServiceAntiAffinityHostFilter(
                host, desc);

        // filter(Collections.emptyList());

        // when the last host is soft affinity:
        anti_affinity = new String[] {
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME1,
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME2,
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME3 + SOFT.getValue() };

        desc = createDescriptions(anti_affinity);

        filter = new ComputeServiceAntiAffinityHostFilter(host, desc);

        // The third host is a soft constraint so since it is the only one left disregard the soft
        // constraint in that case
        filter(Arrays.asList(hostLink3));

        // when the last host left is soft compute but there is another compute on the same host
        // with hard constraint.:

        String computeName4 = "computeName4";
        ComputeDescription desc4 = createDescriptions(computeName4);
        createCompute(desc4, hostLink3);

        anti_affinity = new String[] {
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME1,
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME2,
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME3 + SOFT.getValue(),
                ANTI_AFFINITY_PREFIX + computeName4 };

        desc = createDescriptions(anti_affinity);

        filter = new ComputeServiceAntiAffinityHostFilter(host, desc);

        // The third host is a soft constraint but the fourth compute points to the same host and
        // it is a hard constraint
        filter(Collections.emptyList());

        // filter even soft constraints when more than one host available

        anti_affinity = new String[] {
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME1 + SOFT.getValue(),
                ANTI_AFFINITY_PREFIX + COMPUTE_NAME2 + SOFT.getValue() };

        desc = createDescriptions(anti_affinity);

        filter = new ComputeServiceAntiAffinityHostFilter(host, desc);

        filter(Arrays.asList(hostLink3));

    }

    private ComputeDescription createDescriptions(String[] affinity) throws Throwable {
        return createDescriptions("randomName" + Math.random(), affinity);
    }

    private ComputeDescription createDescriptions(String name) throws Throwable {
        return createDescriptions(name, null);
    }

    private ComputeDescription createDescriptions(String name, String[] affinity)
            throws Throwable {
        // loop a few times to make sure the right host is not chosen by chance
        ComputeDescription desc = new ComputeDescription();
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc.name = name;
        TemplateComputeDescription.setAffinityNames(desc, affinity == null ?
                Collections.emptyList() : Arrays.asList(affinity));
        desc = doPost(desc, ComputeDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);

        return desc;
    }
}
