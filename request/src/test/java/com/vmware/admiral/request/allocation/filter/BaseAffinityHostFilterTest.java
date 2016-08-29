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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;

import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class BaseAffinityHostFilterTest extends RequestBaseTest {

    protected List<String> initialHostLinks;
    protected HostSelectionFilter filter;
    protected PlacementHostSelectionTaskState state;
    protected List<String> expectedLinks;
    protected boolean throwErrorOnFilter;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        state = new PlacementHostSelectionTaskState();
        state.contextId = UUID.randomUUID().toString();
        state.customProperties = new HashMap<>();
        state.resourceCount = 1;

        initialHostLinks = new ArrayList<>();
        initialHostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), true).documentSelfLink);
        initialHostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), true).documentSelfLink);
        initialHostLinks.add(createDockerHost(
                createDockerHostDescription(), createResourcePool(), true).documentSelfLink);
        filter = new ClusterAntiAffinityHostFilter(host, containerDesc);
        expectedLinks = new ArrayList<>(initialHostLinks);
    }

    protected ContainerState createContainerWithDifferentContextId(ContainerDescription desc)
            throws Throwable {
        return createContainerWithDifferentContextId(desc, initialHostLinks.get(0));

    }

    protected ContainerState createContainerWithDifferentContextId(ContainerDescription desc,
            String hostLink)
            throws Throwable {
        ContainerState container = createContainer(desc, hostLink);
        String differentContextId = UUID.randomUUID().toString();
        container.compositeComponentLink = UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, differentContextId);
        doOperation(container, UriUtils.buildUri(host, container.documentSelfLink),
                false, Action.PUT);
        return container;
    }

    protected ContainerState createContainer(ContainerDescription desc) throws Throwable {
        return createContainer(desc, initialHostLinks.get(0));

    }

    protected ContainerState createContainer(ContainerDescription desc, String hostLink)
            throws Throwable {
        ContainerState container = new ContainerState();
        container.descriptionLink = desc.documentSelfLink;
        container.id = UUID.randomUUID().toString();
        container.ports = desc.portBindings != null ?
                new ArrayList<>(Arrays.asList(desc.portBindings)) : null;
        List<String> hosts = new ArrayList<>(initialHostLinks);
        Collections.shuffle(hosts);
        container.parentLink = hostLink;
        container.powerState = PowerState.RUNNING;
        container.compositeComponentLink = UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, state.contextId);
        container = doPost(container, ContainerFactoryService.SELF_LINK);
        assertNotNull(container);
        addForDeletion(container);
        return container;
    }

    protected Throwable filter(Collection<String> expectedLinks) throws Throwable {
        Throwable[] error = new Throwable[] { null };
        final Map<String, HostSelection> hostSelectionMap = new HashMap<>();
        for (String hostLink : initialHostLinks) {
            HostSelection hostSelection = new HostSelection();
            hostSelection.hostLink = hostLink;
            hostSelectionMap.put(hostLink, hostSelection);
        }
        host.testStart(1);
        filter
                .filter(
                        state,
                        hostSelectionMap,
                        (filteredHostSelectionMap, e) -> {
                            if (e != null) {
                                error[0] = e;
                            } else if (expectedLinks.size() == filteredHostSelectionMap.size()) {
                                HashSet<String> expectedLinksSet = new HashSet<>(expectedLinks);
                                expectedLinksSet.removeAll(filteredHostSelectionMap.keySet());
                                if (!expectedLinksSet.isEmpty()) {
                                    error[0] = new IllegalStateException("Filtered hostLinks:  " +
                                            filteredHostSelectionMap.keySet().toString()
                                            + " - Expected hostlinks: "
                                            + expectedLinks.toString());
                                }
                                ;
                            } else {
                                error[0] = new IllegalStateException("Filtered hostLinks size is: "
                                        + filteredHostSelectionMap.size() + " - Expected size is: "
                                        + expectedLinks.size());
                            }
                            host.completeIteration();
                        });

        host.testWait();
        if (throwErrorOnFilter && error[0] != null && !expectedLinks.isEmpty()) {
            throw error[0];
        }
        return error[0];
    }

}
