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
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;


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

        setBinpackPolicyToEPZS();

        ComputeDescription computeDescription = doPost(createComputeDescription(),
                ComputeDescriptionService.FACTORY_LINK);

        // Create two more Compute hosts.
        ComputeState computeHost2 = createVmGuestComputeWithRandomComputeDescription(true);
        ComputeState computeHost3 = createVmGuestComputeWithRandomComputeDescription(true);

        // Update descriptions with memory.
        ComputeDescription computeDesc1 = getDocument(ComputeDescription.class,
                computeHost2.descriptionLink);
        computeDesc1.totalMemoryBytes = 9000000000L;
        doPatch(computeDesc1, computeDesc1.documentSelfLink);

        ComputeDescription computeDesc3 = getDocument(ComputeDescription.class,
                computeHost3.descriptionLink);
        computeDesc3.totalMemoryBytes = 5000000000L;
        doPatch(computeDesc3, computeDesc3.documentSelfLink);

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
        assertEquals(computeHost3.documentSelfLink,
                taskState.selectedComputePlacementHosts.stream().findFirst().get().hostLink);
    }

    private ComputeDescription createComputeDescription() {
        ComputeDescription cd = new ComputeDescription();
        cd.id = UUID.randomUUID().toString();
        cd.name = "small"; // aws adapter is using name as value for instance type
        cd.customProperties = new HashMap<>();
        return cd;
    }

    private void setBinpackPolicyToEPZS() throws Throwable {

        // Create ElasticPlacementZoneState which follows BINPACK deployment policy.
        ElasticPlacementZoneState epzState = new ElasticPlacementZoneState();
        epzState.placementPolicy = ElasticPlacementZoneService.PlacementPolicy.BINPACK;
        epzState.resourcePoolLink = resourcePool.documentSelfLink;

        ElasticPlacementZoneConfigurationState epz = new ElasticPlacementZoneConfigurationState();
        epz.documentSelfLink = resourcePool.documentSelfLink;
        epz.resourcePoolState = resourcePool;
        epz.epzState = epzState;

        epz = doOperation(epz,
                UriUtils.buildUri(host, ElasticPlacementZoneConfigurationService.SELF_LINK),
                ElasticPlacementZoneConfigurationState.class, false, Action.PATCH);

        assertEquals(epz.epzState.placementPolicy,
                ElasticPlacementZoneService.PlacementPolicy.BINPACK);

    }

}
