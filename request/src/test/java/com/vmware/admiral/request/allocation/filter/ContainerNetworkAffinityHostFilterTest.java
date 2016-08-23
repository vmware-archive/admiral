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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

import org.junit.Test;

import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class ContainerNetworkAffinityHostFilterTest extends BaseAffinityHostFilterTest {

    private ContainerNetworkDescription containerNetworkDesc;

    @Override
    public void setUp() throws Throwable {
        super.setUp();
        throwErrorOnFilter = true;
        containerNetworkDesc = createContainerNetworkDescription("network1");
    }

    @Test
    public void testFilterHostsWhenFilterIsDisabled() throws Throwable {
        filter = new ContainerNetworkAffinityHostFilter(host, null);
        assertFalse(filter.isActive());

        filter(expectedLinks);

        initialHostLinks = Collections.emptyList();
        expectedLinks = Collections.emptyList();
        filter(expectedLinks);
    }

    @Test
    public void testFilterHostsWhenNoHostsAvailable() throws Throwable {
        filter = new ContainerNetworkAffinityHostFilter(host, containerNetworkDesc);
        assertTrue(filter.isActive());

        initialHostLinks = Collections.emptyList();
        expectedLinks = Collections.emptyList();
        filter(expectedLinks);
    }

    @Test
    public void testFilterHostsWhenNoClustersAvailable() throws Throwable {
        filter = new ContainerNetworkAffinityHostFilter(host, containerNetworkDesc);
        assertTrue(filter.isActive());

        // One host is selected randomly from the initialHostLinks
        filter(1);
    }

    @Test
    public void testFilterHostsWhenAlsoAClusterAvailable() throws Throwable {
        filter = new ContainerNetworkAffinityHostFilter(host, containerNetworkDesc);
        assertTrue(filter.isActive());

        // Add 3 hostLinks with hosts creating a KV store cluster
        expectedLinks = new ArrayList<>();
        expectedLinks.add(createDockerHostWithKVStore("kvstore1"));
        expectedLinks.add(createDockerHostWithKVStore("kvstore1"));
        expectedLinks.add(createDockerHostWithKVStore("kvstore1"));

        initialHostLinks.addAll(expectedLinks);

        // The cluster is selected from the initialHostLinks
        filter(expectedLinks);
    }

    @Test
    public void testFilterHostsWhenAlsoMultipleClustersOfTheSameSizeAvailable() throws Throwable {
        filter = new ContainerNetworkAffinityHostFilter(host, containerNetworkDesc);
        assertTrue(filter.isActive());

        // Add 2 sets of 3 hostLinks with hosts creating 2 KV store clusters
        initialHostLinks.add(createDockerHostWithKVStore("kvstore1"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore1"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore1"));

        initialHostLinks.add(createDockerHostWithKVStore("kvstore2"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore2"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore2"));

        // One cluster is selected randomly from the initialHostLinks
        Collection<HostSelection> selected = filter(3).values();

        // And all the nodes from the cluster use the same KV store
        Iterator<HostSelection> it = selected.iterator();
        String cs = it.next().clusterStore;
        assertTrue(it.next().clusterStore.equals(cs));
        assertTrue(it.next().clusterStore.equals(cs));
    }

    @Test
    public void testFilterHostsWhenAlsoMultipleClustersOfDifferentSizesAvailable()
            throws Throwable {
        filter = new ContainerNetworkAffinityHostFilter(host, containerNetworkDesc);
        assertTrue(filter.isActive());

        // Add 3 sets of 3, 4 and 5 hostLinks with hosts creating 3 KV store clusters
        initialHostLinks.add(createDockerHostWithKVStore("kvstore1"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore1"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore1"));

        initialHostLinks.add(createDockerHostWithKVStore("kvstore2"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore2"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore2"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore2"));

        expectedLinks = new ArrayList<>();
        expectedLinks.add(createDockerHostWithKVStore("kvstore3"));
        expectedLinks.add(createDockerHostWithKVStore("kvstore3"));
        expectedLinks.add(createDockerHostWithKVStore("kvstore3"));
        expectedLinks.add(createDockerHostWithKVStore("kvstore3"));
        expectedLinks.add(createDockerHostWithKVStore("kvstore3"));

        initialHostLinks.addAll(expectedLinks);

        // The biggest cluster is selected from the initialHostLinks
        filter(expectedLinks);
    }

    @Test
    public void testSelectHostsWhenOnlyClustersAvailable() throws Throwable {
        filter = new ContainerNetworkAffinityHostFilter(host, containerNetworkDesc);
        assertTrue(filter.isActive());

        initialHostLinks = new ArrayList<>();

        // Add 3 sets of 3, 4 and 5 hostLinks with hosts creating 3 KV store clusters
        initialHostLinks.add(createDockerHostWithKVStore("kvstore1"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore1"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore1"));

        initialHostLinks.add(createDockerHostWithKVStore("kvstore2"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore2"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore2"));
        initialHostLinks.add(createDockerHostWithKVStore("kvstore2"));

        expectedLinks = new ArrayList<>();
        expectedLinks.add(createDockerHostWithKVStore("kvstore3"));
        expectedLinks.add(createDockerHostWithKVStore("kvstore3"));
        expectedLinks.add(createDockerHostWithKVStore("kvstore3"));
        expectedLinks.add(createDockerHostWithKVStore("kvstore3"));
        expectedLinks.add(createDockerHostWithKVStore("kvstore3"));

        initialHostLinks.addAll(expectedLinks);

        // The biggest cluster is selected from the initialHostLinks
        filter(expectedLinks);
    }

    private String createDockerHostWithKVStore(String kvStore) throws Throwable {
        String hostLink = createDockerHost(createDockerHostDescription(), createResourcePool(),
                true).documentSelfLink;

        ComputeState csPatch = new ComputeState();

        csPatch.documentSelfLink = hostLink;
        csPatch.customProperties = new HashMap<>();
        csPatch.customProperties.put(ContainerHostService.DOCKER_HOST_CLUSTER_STORE_PROP_NAME,
                kvStore);

        doPatch(csPatch, hostLink);

        return hostLink;
    }

}
