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

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;

import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.compute.ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState;
import com.vmware.admiral.request.compute.ComputeRequestBaseTest;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class BaseComputeAffinityHostFilterTest extends ComputeRequestBaseTest {

    protected List<String> initialHostLinks;
    protected HostSelectionFilter<FilterContext> filter;
    protected ComputePlacementSelectionTaskState state;
    protected List<String> expectedLinks;
    protected boolean throwErrorOnFilter;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        state = new ComputePlacementSelectionTaskState();
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
        vmGuestComputeDescription = TestRequestStateFactory.createComputeDescriptionForVmGuestChildren();
        filter = new ComputeClusterAntiAffinityHostFilter(host, vmGuestComputeDescription);
        expectedLinks = new ArrayList<>(initialHostLinks);
    }

    protected ComputeState createComputeWithDifferentContextId(ComputeDescription desc)
            throws Throwable {
        return createComputeWithDifferentContextId(desc, initialHostLinks.get(0));

    }

    protected ComputeState createComputeWithDifferentContextId(ComputeDescription desc,
            String hostLink)
            throws Throwable {
        ComputeState compute = createCompute(desc, hostLink);
        String differentContextId = UUID.randomUUID().toString();
        compute.customProperties = new HashMap<>();
        compute.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, differentContextId);
        doOperation(compute, UriUtils.buildUri(host, compute.documentSelfLink),
                false, Action.PUT);
        return compute;
    }

    protected ComputeState createCompute(ComputeDescription desc) throws Throwable {
        return createCompute(desc, initialHostLinks.get(0));

    }

    protected ComputeState createCompute(ComputeDescription desc,
            String hostLink)
            throws Throwable {
        ComputeState compute = new ComputeState();
        compute.descriptionLink = desc.documentSelfLink;
        compute.id = UUID.randomUUID().toString();
        List<String> hosts = new ArrayList<>(initialHostLinks);
        Collections.shuffle(hosts);
        compute.parentLink = hostLink;
        compute.name = desc.name + "-" + UUID.randomUUID();
        compute.powerState = ComputeService.PowerState.ON;
        compute.customProperties = new HashMap<>();
        compute.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, state.contextId);
        compute = doPost(compute, ComputeService.FACTORY_LINK);
        assertNotNull(compute);
        addForDeletion(compute);
        return compute;
    }

    protected Throwable filter(Collection<String> expectedLinks) throws Throwable {
        Throwable[] error = new Throwable[] { null };
        final Map<String, HostSelection> hostSelectionMap = prepareHostSelectionMap();

        host.testStart(1);
        filter
                .filter(
                        FilterContext.from(state),
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

    protected Map<String, HostSelection> filter() throws Throwable {
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Map<String, HostSelection> hostSelectionMap = prepareHostSelectionMap();
        final Map<String, HostSelection> hostSelectedMap = new HashMap<>();

        host.testStart(1);
        filter
                .filter(
                        FilterContext.from(state),
                        hostSelectionMap,
                        (filteredHostSelectionMap, e) -> {
                            if (e != null) {
                                error.set(e);
                            } else {
                                hostSelectedMap.putAll(filteredHostSelectionMap);
                            }
                            host.completeIteration();
                        });

        host.testWait();

        if (error.get() != null) {
            throw error.get();
        }

        return hostSelectedMap;
    }

    protected Map<String, HostSelection> prepareHostSelectionMap() throws Throwable {
        Map<String, HostSelection> hostSelectionMap = new HashMap<>();
        for (String hostLink : initialHostLinks) {
            HostSelection hostSelection = new HostSelection();
            hostSelection.hostLink = hostLink;

            ComputeState host = getDocument(ComputeState.class, hostLink);
            if (host.customProperties
                    .containsKey(ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME)) {
                hostSelection.clusterStore = host.customProperties
                        .get(ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME);
            }
            if (host.customProperties
                    .containsKey(ContainerHostService.DOCKER_HOST_PLUGINS_PROP_NAME)) {
                hostSelection.plugins = host.customProperties
                        .get(ContainerHostService.DOCKER_HOST_PLUGINS_PROP_NAME);
            }

            hostSelectionMap.put(hostLink, hostSelection);
        }
        return hostSelectionMap;
    }

}
