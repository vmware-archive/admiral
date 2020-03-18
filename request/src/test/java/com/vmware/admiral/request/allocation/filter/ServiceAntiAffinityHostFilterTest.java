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

import static com.vmware.admiral.request.allocation.filter.AffinityConstraint.AffinityConstraintType.ANTI_AFFINITY_PREFIX;
import static com.vmware.admiral.request.allocation.filter.AffinityConstraint.AffinityConstraintType.SOFT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.xenon.common.UriUtils;

public class ServiceAntiAffinityHostFilterTest extends BaseAffinityHostFilterTest {
    private static final String CONTAINER_NAME1 = "test-container1";
    private static final String CONTAINER_NAME2 = "test-container2";
    private static final String CONTAINER_NAME3 = "test-container3";

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        throwErrorOnFilter = true;
    }

    @Test
    public void testReturnInitialHostListWhenNoContainerDescWithAntiAfinity() throws Throwable {
        ContainerDescription desc = createDescriptions(CONTAINER_NAME1);
        assertNull(desc.affinity);
        filter = new ServiceAntiAffinityHostFilter(host, desc);

        filter(expectedLinks);
    }

    @Test
    public void testReturnInitialHostListWhenNoMatchingContainerDescNameWithAntiAfinity()
            throws Throwable {
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME1);
        createContainer(desc1, initialHostLinks.get(0));

        String[] anti_affinity = new String[] { ANTI_AFFINITY_PREFIX
                + "not-existing-container-name" };
        ContainerDescription desc = createDescriptions(anti_affinity);

        filter = new ServiceAntiAffinityHostFilter(host, desc);

        filter(initialHostLinks);
    }

    @Test
    public void testReturnInitialHostListWhenContainerWithSameNameInAntiAffinityButDifferentContextId()
            throws Throwable {
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME1);
        createContainerWithDifferentContextId(desc1, initialHostLinks.get(0));

        String[] anti_affinity = new String[] { ANTI_AFFINITY_PREFIX + CONTAINER_NAME1 };
        ContainerDescription desc = createDescriptions(anti_affinity);

        filter = new ServiceAntiAffinityHostFilter(host, desc);

        filter(initialHostLinks);
    }

    @Test
    public void testDoNotSelectContainerHostWhenContainerAlreadyProvisionedAndSameHostAndContextId()
            throws Throwable {
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME1);
        ContainerState container = createContainer(desc1, initialHostLinks.get(0));

        assertEquals(UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, state.contextId),
                container.compositeComponentLink);

        String[] anti_affinity = new String[] { ANTI_AFFINITY_PREFIX + CONTAINER_NAME1 };
        ContainerDescription desc = createDescriptions(anti_affinity);

        filter = new ServiceAntiAffinityHostFilter(host, desc);

        expectedLinks.remove(container.parentLink);

        filter(expectedLinks);
    }

    @Test
    public void testMoreThanOneContainersWithDifferentHostsInAffinity() throws Throwable {
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME1);
        String hostLink1 = initialHostLinks.get(0);
        createContainer(desc1, hostLink1);

        ContainerDescription desc2 = createDescriptions(CONTAINER_NAME2);
        String hostLink2 = initialHostLinks.get(1);
        createContainer(desc2, hostLink2);

        String[] anti_affinity = new String[] {
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME1,
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME2 };
        ContainerDescription desc = createDescriptions(anti_affinity);

        filter = new ServiceAntiAffinityHostFilter(host, desc);

        expectedLinks.remove(hostLink1);
        expectedLinks.remove(hostLink2);

        filter(expectedLinks);
    }

    @Test
    public void testProvisionContainerWhenAnotherAlreadyProvisionedAndHasAntiAffinityRules()
            throws Throwable {

        //Create a container A which has anti affinity to B. Deploy A, then deploy B. B should NOT be placed on the same host as A
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME1, new String[] {
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME2 });
        String hostLink2 = initialHostLinks.get(1);
        createContainer(desc1, hostLink2);

        ContainerDescription desc2 = createDescriptions(CONTAINER_NAME2);

        filter = new ServiceAntiAffinityHostFilter(host, desc2);

        expectedLinks.remove(hostLink2);

        state.addCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP, "true");
        filter(expectedLinks);
    }

    @Test
    public void testSoftAntiAfinity() throws Throwable {
        ContainerDescription desc1 = createDescriptions(CONTAINER_NAME1);
        String hostLink1 = initialHostLinks.get(0);
        createContainer(desc1, hostLink1);

        ContainerDescription desc2 = createDescriptions(CONTAINER_NAME2);
        String hostLink2 = initialHostLinks.get(1);
        createContainer(desc2, hostLink2);

        ContainerDescription desc3 = createDescriptions(CONTAINER_NAME3);
        String hostLink3 = initialHostLinks.get(2);
        createContainer(desc3, hostLink3);

        String[] anti_affinity = new String[] {
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME1,
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME2,
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME3 };
        ContainerDescription desc = createDescriptions(anti_affinity);

        filter = new ServiceAntiAffinityHostFilter(host, desc);

        // filter(Collections.emptyList());

        // when the last host is soft affinity:
        anti_affinity = new String[] {
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME1,
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME2,
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME3 + SOFT.getValue() };

        desc = createDescriptions(anti_affinity);

        filter = new ServiceAntiAffinityHostFilter(host, desc);

        // The third host is a soft constraint so since it is the only one left disregard the soft
        // constraint in that case
        filter(Arrays.asList(hostLink3));

        // when the last host left is soft container but there is another container on the same host
        // with hard constraint.:

        String containerName4 = "containerName4";
        ContainerDescription desc4 = createDescriptions(containerName4);
        createContainer(desc4, hostLink3);

        anti_affinity = new String[] {
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME1,
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME2,
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME3 + SOFT.getValue(),
                ANTI_AFFINITY_PREFIX + containerName4 };

        desc = createDescriptions(anti_affinity);

        filter = new ServiceAntiAffinityHostFilter(host, desc);

        // The third host is a soft constraint but the fourth container points to the same host and
        // it is a hard constraint
        filter(Collections.emptyList());

        // filter even soft constraints when more than one host available

        anti_affinity = new String[] {
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME1 + SOFT.getValue(),
                ANTI_AFFINITY_PREFIX + CONTAINER_NAME2 + SOFT.getValue() };

        desc = createDescriptions(anti_affinity);

        filter = new ServiceAntiAffinityHostFilter(host, desc);

        filter(Arrays.asList(hostLink3));

    }

    private ContainerDescription createDescriptions(String[] affinity) throws Throwable {
        return createDescriptions("randomName" + Math.random(), affinity);
    }

    private ContainerDescription createDescriptions(String name) throws Throwable {
        return createDescriptions(name, null);
    }

    private ContainerDescription createDescriptions(String name, String[] affinity)
            throws Throwable {
        // loop a few times to make sure the right host is not chosen by chance
        ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc.name = name;
        desc.affinity = affinity;
        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);

        return desc;
    }
}
