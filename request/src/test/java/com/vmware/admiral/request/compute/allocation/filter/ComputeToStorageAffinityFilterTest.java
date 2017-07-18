/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.fail;

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.STORAGE_AVAILABLE_BYTES;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
import com.vmware.xenon.common.Utils;

/**
 * Unit tests for {@link ComputeToStorageAffinityFilter} class.
 */
public class ComputeToStorageAffinityFilterTest extends BaseComputeAffinityHostFilterTest {
    private static final long _4_GB_IN_MB = 4 * 1024;
    private static final long _7_GB_IN_MB = 7 * 1024;
    private static final long _5_GB_IN_B = 5 * 1024 * 1024;
    private static final long _10_GB_IN_B = 10 * 1024 * 1024;

    @Override
    protected synchronized EndpointState createEndpoint() throws Throwable {
        synchronized (initializationLock) {
            if (endpoint == null) {
                endpoint = TestRequestStateFactory.createEndpoint();
                endpoint.endpointType = getEndpointType().name();
                endpoint.endpointProperties.put("endpointHost", "https://somehost");
                endpoint = getOrCreateDocument(endpoint, EndpointAdapterService.SELF_LINK);
                assertNotNull(endpoint);
            }
            return endpoint;
        }
    }

    private EndpointType getEndpointType() {
        return EndpointType.vsphere;
    }

    @Test
    public void testFilterHostsWithDatastoresWithoutAvailableCapacity() throws Throwable {
        // Host A -> Datastore1 (5GB free)
        StorageDescription insufficientCapacityDS = createDatastore(3000 * 1024);
        addStorageStats(insufficientCapacityDS.documentSelfLink, _5_GB_IN_B);

        ComputeState insufficientCapacityHost = createVmHostCompute(true, null,
                Collections.singleton(insufficientCapacityDS.documentSelfLink), endpoint.regionId,
                null);

        // Host B -> Datastore2 (10GB free)
        StorageDescription sufficientCapacityDS = createDatastore(3000 * 1024);
        addStorageStats(sufficientCapacityDS.documentSelfLink, _10_GB_IN_B);

        ComputeState sufficientCapacityHost = createVmHostCompute(true, null,
                Collections.singleton(sufficientCapacityDS.documentSelfLink), endpoint.regionId,
                null);

        // VM with 7 GB disk.
        ComputeDescription desc = createComputeDescriptionWithDisks(
                Collections.singletonList(_7_GB_IN_MB));

        filter = new ComputeToStorageAffinityFilter(host, desc);

        List<ComputeState> hostSelection =
                Arrays.asList(insufficientCapacityHost, sufficientCapacityHost);

        // filter the same compute desc 10 times
        List<Set<String>> filteredHosts = IntStream.generate(() -> 1)
                .limit(10)
                .mapToObj(i -> {
                    try {
                        Map<String, HostSelection> result = filter(hostSelection);
                        return result.keySet();
                    } catch (Throwable throwable) {
                        fail(throwable.getMessage());
                        return null;
                    }
                })
                .distinct()
                .collect(Collectors.toList());

        // expect to get the same group every time
        assertEquals(1, filteredHosts.size());
        // it's not deterministic which one of the groups of size 1 we will pick, but it has to
        // be of size 1
        assertEquals(1, filteredHosts.get(0).size());

        assertEquals(sufficientCapacityHost.documentSelfLink,
                filteredHosts.get(0).iterator().next());
    }


    @Test
    public void testFilterHostsWithTwoDatastoresWithGreedyAlgorithm() throws Throwable {
        // 5 GB free Datastore.
        StorageDescription firstDS = createDatastore(3000 * 1024);
        addStorageStats(firstDS.documentSelfLink, _5_GB_IN_B);

        // 10 GB free Datastore.
        StorageDescription secondDS = createDatastore(3000 * 1024);
        addStorageStats(secondDS.documentSelfLink, _10_GB_IN_B);

        Set<String> dsLinks = new HashSet<>(
                Arrays.asList(firstDS.documentSelfLink, secondDS.documentSelfLink));

        // Host with two datastores - 5GB and 10GB available.
        ComputeState vmHost = createVmHostCompute(true, null, dsLinks,
                endpoint.regionId,null);

        // VM with two disks - 7 GB and 4GB.
        ComputeDescription desc = createComputeDescriptionWithDisks(
                Arrays.asList(_4_GB_IN_MB, _7_GB_IN_MB));


        filter = new ComputeToStorageAffinityFilter(host, desc);

        List<ComputeState> hostSelection = Collections.singletonList(vmHost);

        // filter the same compute desc 10 times
        List<Set<String>> filteredHosts = IntStream.generate(() -> 1)
                .limit(10)
                .mapToObj(i -> {
                    try {
                        Map<String, HostSelection> result = filter(hostSelection);
                        return result.keySet();
                    } catch (Throwable throwable) {
                        fail(throwable.getMessage());
                        return null;
                    }
                })
                .distinct()
                .collect(Collectors.toList());

        // expect to get the same group every time
        assertEquals(1, filteredHosts.size());
        // it's not deterministic which one of the groups of size 1 we will pick, but it has to
        // be of size 1
        assertEquals(1, filteredHosts.get(0).size());

        assertEquals(vmHost.documentSelfLink,
                filteredHosts.get(0).iterator().next());

    }


    private void addStorageStats(String selfLink, double availableCapacityBytes) throws Throwable {
        ResourceMetrics metrics = new ResourceMetrics();
        metrics.timestampMicrosUtc = Utils.getNowMicrosUtc();
        metrics.documentSelfLink = StatsUtil.getMetricKey(selfLink, metrics.timestampMicrosUtc);
        metrics.entries = new HashMap<>();
        metrics.entries.put(STORAGE_AVAILABLE_BYTES, availableCapacityBytes);
        metrics.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + TimeUnit.DAYS.toMicros(
                SingleResourceStatsCollectionTaskService.EXPIRATION_INTERVAL);

        metrics.customProperties = new HashMap<>();
        metrics.customProperties
                .put(ResourceMetrics.PROPERTY_RESOURCE_LINK, selfLink);

        doPost(metrics, ResourceMetricsService.FACTORY_LINK);
    }

}