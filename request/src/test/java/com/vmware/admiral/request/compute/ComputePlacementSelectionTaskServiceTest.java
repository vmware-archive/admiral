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
import java.util.HashMap;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.request.compute.ComputePlacementSelectionTaskService.ComputePlacementSelectionTaskState;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;

/**
 * Tests for the {@link ComputePlacementSelectionTaskService} service.
 */
public class ComputePlacementSelectionTaskServiceTest extends ComputeRequestBaseTest {

    private ComputeState vmHostCompute;

    @Override
    protected ResourceType placementResourceType() {
        return ResourceType.COMPUTE_TYPE;
    }

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();

        // create a single powered-on compute available for placement
        vmHostCompute = createVmHostCompute(true);
    }

    @Test
    public void testSingleInstanceProvisioning() throws Throwable {
        ComputeDescription computeDescription = doPost(createComputeDescription(),
                ComputeDescriptionService.FACTORY_LINK);

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

        ComputeDescription computeDescription = doPost(createComputeDescription(),
                ComputeDescriptionService.FACTORY_LINK);

        // Create two more Compute hosts.
        ComputeState computeHost2 = createVmComputeWithRandomComputeDescription(true,
                ComputeType.VM_HOST);
        ComputeState computeHost3 = createVmComputeWithRandomComputeDescription(true,
                ComputeType.VM_HOST);

        // Propagate stats to compute/stats URI
        ServiceStats.ServiceStat compute2Stats = new ServiceStats.ServiceStat();
        compute2Stats.name = "daily.memoryUsedBytes";
        compute2Stats.latestValue = Utils.getNowMicrosUtc();
        compute2Stats.sourceTimeMicrosUtc = Utils.getNowMicrosUtc();
        compute2Stats.unit = PhotonModelConstants.UNIT_MICROSECONDS;
        compute2Stats.accumulatedValue = 5000000000L;

        URI inMemoryStatsUri = UriUtils.buildStatsUri(host, computeHost2.documentSelfLink);

        TestContext waitCompute1Stats = new TestContext(1, Duration.ofSeconds(30));
        Operation.createPost(inMemoryStatsUri).setBody(compute2Stats)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        waitCompute1Stats.fail(e);
                    }
                    waitCompute1Stats.complete();
                }).sendWith(host);
        ;
        waitCompute1Stats.await();

        ServiceStats.ServiceStat compute3Stats = new ServiceStats.ServiceStat();
        compute3Stats.name = "daily.memoryUsedBytes";
        compute3Stats.latestValue = Utils.getNowMicrosUtc();
        compute3Stats.sourceTimeMicrosUtc = Utils.getNowMicrosUtc();
        compute3Stats.unit = PhotonModelConstants.UNIT_MICROSECONDS;
        compute3Stats.accumulatedValue = 9000000000L;

        inMemoryStatsUri = UriUtils.buildStatsUri(host, computeHost3.documentSelfLink);
        TestContext waitCompute3Stats = new TestContext(1, Duration.ofSeconds(30));
        Operation.createPost(inMemoryStatsUri).setBody(compute3Stats)
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

        ComputeDescription computeDescription = doPost(createComputeDescription(),
                ComputeDescriptionService.FACTORY_LINK);

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

    private ComputeDescription createComputeDescription() {
        ComputeDescription cd = new ComputeDescription();
        cd.id = UUID.randomUUID().toString();
        cd.name = "small"; // aws adapter is using name as value for instance type
        cd.customProperties = new HashMap<>();
        return cd;
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
                ElasticPlacementZoneConfigurationState.class, false, Action.PATCH);

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
