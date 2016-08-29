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
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class PodAffinityHostFilterTest extends BaseAffinityHostFilterTest {
    private static final String POD_ID = "host-pod-test1";

    @Test
    public void testReturnInitialHostListWhenNoContainerDescWithPod() throws Throwable {
        ContainerDescription desc = createContainerDescription();
        assertNull(desc.pod);
        filter = new PodAffinityHostFilter(host, desc);

        Throwable e = filter(initialHostLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testSelectHostWhenOnlyOneContainerDescWithPodDefined() throws Throwable {
        ContainerDescription desc = createDescriptionsWithPodDefined("linked-container", 1).get(0);

        filter = new PodAffinityHostFilter(host, desc);

        Throwable e = filter(initialHostLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testSelectHostWhenContainerDescHasSamePodAndFirstContainer() throws Throwable {
        ContainerDescription desc = createDescriptionsWithPodDefined("linked-container", 5).get(0);

        filter = new PodAffinityHostFilter(host, desc);

        Throwable e = filter(initialHostLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testErrorWhenContainerDescHasSamePodAndSecondContainerButNoContainersFound()
            throws Throwable {
        ContainerDescription desc = createDescriptionsWithPodDefined("linked-container", 5).get(0);

        // marked as dependent, which means not the first container associated with a given pod
        state.customProperties.put(ContainerAllocationTaskState.FIELD_NAME_CONTEXT_POD_DEPENDENT,
                POD_ID);
        filter = new PodAffinityHostFilter(host, desc);

        Throwable e = filter(initialHostLinks);
        if (e == null) {
            fail("Expected exception for no container found.");
        }
    }

    @Test
    public void testNoSelectionWhenContainerWithSamePodButDifferentContextId() throws Throwable {
        ContainerDescription desc = createDescriptionsWithPodDefined("linked-container", 1).get(0);

        ContainerState container = createContainer(desc);
        String differentContextId = UUID.randomUUID().toString();
        container.compositeComponentLink = UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, differentContextId);
        doOperation(container, UriUtils.buildUri(host, container.documentSelfLink),
                false, Action.PUT);

        // marked as dependent, which means not the first container associated with a given pod
        state.customProperties.put(ContainerAllocationTaskState.FIELD_NAME_CONTEXT_POD_DEPENDENT,
                POD_ID);
        filter = new PodAffinityHostFilter(host, desc);

        Throwable e = filter(Arrays.asList(container.parentLink));
        if (e == null) {
            fail("Expected exception for no container found.");
        }
    }

    @Test
    public void testSelectContainerHostWhenContainerAlreadyProvisionedAndSamePodAndContextId()
            throws Throwable {
        List<ContainerDescription> descs = createDescriptionsWithPodDefined("linked-container", 5);
        ContainerDescription desc = descs.get(0);

        ContainerState container = createContainer(desc);
        container.compositeComponentLink = UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, state.contextId);
        doOperation(container, UriUtils.buildUri(host, container.documentSelfLink),
                false, Action.PUT);

        filter = new PodAffinityHostFilter(host, desc);

        Collection<String> expectedFilteredHostLinks = Arrays.asList(container.parentLink);
        Throwable e = filter(expectedFilteredHostLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    private List<ContainerDescription> createDescriptionsWithPodDefined(String name, int count)
            throws Throwable {
        // loop a few times to make sure the right host is not chosen by chance
        List<ContainerDescription> descs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
            desc.documentSelfLink = UUID.randomUUID().toString();
            desc.name = name;
            desc.pod = POD_ID;
            desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
            assertNotNull(desc);
            addForDeletion(desc);
            descs.add(desc);
        }

        return descs;
    }

}
