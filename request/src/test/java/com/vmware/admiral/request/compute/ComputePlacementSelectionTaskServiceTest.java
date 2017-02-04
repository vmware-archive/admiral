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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.request.compute.ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState;
import com.vmware.photon.controller.model.monitoring.InMemoryResourceMetricService;
import com.vmware.photon.controller.model.monitoring.InMemoryResourceMetricService.InMemoryResourceMetric;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsConstants;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.AggregationType;
import com.vmware.xenon.common.ServiceStats.TimeSeriesStats.TimeBin;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

/**
 * Tests for the {@link ComputePlacementSelectionTaskService} service.
 */
public class ComputePlacementSelectionTaskServiceTest extends ComputeRequestBaseTest {

    @Override
    protected ResourceType placementResourceType() {
        return ResourceType.COMPUTE_TYPE;
    }

    @Test
    public void testSingleInstanceProvisioning() throws Throwable {
        ComputeDescription computeDescription = createVMComputeDescription(true);

        ComputePlacementSelectionTaskState taskRequestState = new ComputePlacementSelectionTaskState();
        taskRequestState.computeDescriptionLink = computeDescription.documentSelfLink;
        taskRequestState.resourceCount = 1;
        taskRequestState.resourcePoolLinks = new ArrayList<>();
        taskRequestState.resourcePoolLinks.add(computeResourcePool.documentSelfLink);

        ComputePlacementSelectionTaskState taskState = doPost(taskRequestState,
                ComputePlacementSelectionTaskService.FACTORY_LINK);
        assertNotNull(taskState);

        taskState = waitForTaskSuccess(taskState.documentSelfLink,
                ComputePlacementSelectionTaskState.class);

        assertNotNull(taskState.selectedComputePlacementHosts);
        assertEquals(taskState.selectedComputePlacementHosts.size(), 1);

        ComputeState selectedComputeState = getDocument(ComputeState.class,
                taskState.selectedComputePlacementHosts.iterator().next().hostLink);
        assertNotNull(selectedComputeState);
        assertEquals(selectedComputeState.documentSelfLink, vmHostCompute.documentSelfLink);
    }

    @Test
    public void testComputePlacementWithBinpackPolicy() throws Throwable {

        setAdvancedPlacementPolicyToEPZS(ElasticPlacementZoneService.PlacementPolicy.BINPACK);

        ComputeDescription computeDescription = createVMComputeDescription(true);

        // Create two more Compute hosts.
        ComputeState computeHost2 = createVmComputeWithRandomComputeDescription(true,
                ComputeType.VM_HOST);
        ComputeState computeHost3 = createVmComputeWithRandomComputeDescription(true,
                ComputeType.VM_HOST);

        // Create TimeSeriesStats to represent Hourly metrics.
        TimeSeriesStats timeStats = new TimeSeriesStats((int) TimeUnit.DAYS.toHours(1),
                TimeUnit.HOURS.toMillis(1),
                EnumSet.of(AggregationType.AVG));

        TimeSeriesStats.TimeBin bin = new TimeSeriesStats.TimeBin();
        bin.avg = 7.6779843E10;
        SortedMap<Long, TimeBin> bins = new TreeMap<>();
        bins.put(5000000000L, bin);
        timeStats.bins = bins;
        Map<String, TimeSeriesStats> stats = new HashMap<>();
        stats.put("daily.memoryUsedBytes", timeStats);

        InMemoryResourceMetric hourlyMemoryState = new InMemoryResourceMetric();
        hourlyMemoryState.timeSeriesStats = stats;
        hourlyMemoryState.documentSelfLink = UriUtils
                .getLastPathSegment(computeHost2.documentSelfLink)
                .concat(StatsConstants.HOUR_SUFFIX);

        URI inMemoryStatsUri = UriUtils.buildUri(host, InMemoryResourceMetricService.FACTORY_LINK);

        TestContext waitCompute1Stats = new TestContext(1, Duration.ofSeconds(30));
        Operation.createPost(inMemoryStatsUri).setBody(hourlyMemoryState)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        waitCompute1Stats.fail(e);
                    }
                    waitCompute1Stats.complete();
                }).sendWith(host);
        ;
        waitCompute1Stats.await();

        TimeSeriesStats timeStats2 = new TimeSeriesStats((int) TimeUnit.DAYS.toHours(1),
                TimeUnit.HOURS.toMillis(1),
                EnumSet.of(AggregationType.AVG));
        TimeSeriesStats.TimeBin bin2 = new TimeSeriesStats.TimeBin();
        bin.avg = 8.6779843E10;
        SortedMap<Long, TimeSeriesStats.TimeBin> bins2 = new TreeMap<>();
        bins2.put(9000000000L, bin2);
        timeStats2.bins = bins2;

        Map<String, TimeSeriesStats> stats2 = new HashMap<>();
        stats.put("daily.memoryUsedBytes", timeStats2);

        InMemoryResourceMetric hourlyMemoryState2 = new InMemoryResourceMetric();
        hourlyMemoryState2.timeSeriesStats = stats2;
        hourlyMemoryState2.documentSelfLink = UriUtils
                .getLastPathSegment(computeHost3.documentSelfLink)
                .concat(StatsConstants.HOUR_SUFFIX);

        URI inMemoryStatsUri2 = UriUtils.buildUri(host, InMemoryResourceMetricService.FACTORY_LINK);

        TestContext waitCompute3Stats = new TestContext(1, Duration.ofSeconds(30));
        Operation.createPost(inMemoryStatsUri2).setBody(hourlyMemoryState2)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        waitCompute3Stats.fail(e);
                    }
                    waitCompute3Stats.complete();
                }).sendWith(host);

        waitCompute3Stats.await();

        ComputePlacementSelectionTaskState taskRequestState = new ComputePlacementSelectionTaskState();
        taskRequestState.computeDescriptionLink = computeDescription.documentSelfLink;
        taskRequestState.resourceCount = 1;
        taskRequestState.resourcePoolLinks = new ArrayList<>();
        taskRequestState.resourcePoolLinks.add(computeResourcePool.documentSelfLink);

        ComputePlacementSelectionTaskState taskState = doPost(taskRequestState,
                ComputePlacementSelectionTaskService.FACTORY_LINK);
        assertNotNull(taskState);

        taskState = waitForTaskSuccess(taskState.documentSelfLink,
                ComputePlacementSelectionTaskState.class);

        assertNotNull(taskState.selectedComputePlacementHosts);
        assertEquals(1, taskState.selectedComputePlacementHosts.size());

        // Verify that placement has happened on most loaded host - computeHost2.
        assertEquals(computeHost2.documentSelfLink,
                taskState.selectedComputePlacementHosts.stream().findFirst().get().hostLink);
    }

    @Test
    public void testComputePlacementWithSpreadPolicy() throws Throwable {

        setAdvancedPlacementPolicyToEPZS(ElasticPlacementZoneService.PlacementPolicy.SPREAD);

        ComputeDescription computeDescription = createVMComputeDescription(true);

        // Create two more Compute hosts of type VM_HOST.
        ComputeState computeHost1 = createVmComputeWithRandomComputeDescription(true,
                ComputeType.VM_HOST);
        ComputeState computeHost2 = createVmComputeWithRandomComputeDescription(true,
                ComputeType.VM_HOST);

        // Create multiple Compute hosts of type VM_GUEST.
        assignComputesToHost(computeHost1.documentSelfLink, 6);
        assignComputesToHost(computeHost2.documentSelfLink, 2);
        assignComputesToHost(vmHostCompute.documentSelfLink, 4);

        ComputePlacementSelectionTaskState taskRequestState = new ComputePlacementSelectionTaskState();
        taskRequestState.computeDescriptionLink = computeDescription.documentSelfLink;
        taskRequestState.resourceCount = 1;
        taskRequestState.resourcePoolLinks = new ArrayList<>();
        taskRequestState.resourcePoolLinks.add(computeResourcePool.documentSelfLink);

        ComputePlacementSelectionTaskState taskState = doPost(taskRequestState,
                ComputePlacementSelectionTaskService.FACTORY_LINK);
        assertNotNull(taskState);

        taskState = waitForTaskSuccess(taskState.documentSelfLink,
                ComputePlacementSelectionTaskState.class);

        assertNotNull(taskState.selectedComputePlacementHosts);
        assertEquals(taskState.selectedComputePlacementHosts.size(), 1);

        // Verify that placement has happened on most loaded host - computeHost3.
        assertEquals(computeHost2.documentSelfLink,
                taskState.selectedComputePlacementHosts.stream().findFirst().get().hostLink);
    }

    private void setAdvancedPlacementPolicyToEPZS(ElasticPlacementZoneService.PlacementPolicy policy)
            throws Throwable {

        // Create ElasticPlacementZoneState which follows BINPACK deployment policy.
        ElasticPlacementZoneState epzState = new ElasticPlacementZoneState();
        epzState.placementPolicy = policy;
        epzState.resourcePoolLink = resourcePool.documentSelfLink;

        ElasticPlacementZoneConfigurationState epz = new ElasticPlacementZoneConfigurationState();
        epz.documentSelfLink = resourcePool.documentSelfLink;
        epz.resourcePoolState = resourcePool;
        epz.epzState = epzState;

        epz = doOperation(epz,
                UriUtils.buildUri(host, ElasticPlacementZoneConfigurationService.SELF_LINK),
                ElasticPlacementZoneConfigurationState.class, false, Action.PUT);

        assertEquals(epz.epzState.placementPolicy,
                policy);

    }

    private void assignComputesToHost(String hostLink, int instances) throws Throwable {
        for (int i = 0; i <= instances; i++) {
            ComputeState computeHost = createVmComputeWithRandomComputeDescription(true, ComputeType.VM_GUEST);
            computeHost.parentLink = hostLink;
            computeHost = doPost(computeHost, ComputeService.FACTORY_LINK);
            assertEquals(hostLink, computeHost.parentLink);
        }
    }

}
