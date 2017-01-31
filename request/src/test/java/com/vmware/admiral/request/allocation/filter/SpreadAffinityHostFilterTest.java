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

package com.vmware.admiral.request.allocation.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;


public class SpreadAffinityHostFilterTest extends BaseAffinityHostFilterTest {

    @Test
    public void testFilterDoesNotAffectHosts() throws Throwable {
        filter = new SpreadAffinityHostFilter(host, containerDesc);
        assertTrue(filter.isActive());
        Map<String, HostSelection> selected = filter();
        assertEquals(3, selected.size());
    }

    @Test
    public void testSpreadFilter() throws Throwable {

        String firstHost = initialHostLinks.get(0);
        String secondHost = initialHostLinks.get(1);
        String thirdHost = initialHostLinks.get(2);

        assignContainersToHost(firstHost, 5);
        assignContainersToHost(secondHost, 9);
        assignContainersToHost(thirdHost, 3);

        // Set Spread policy to EPZS
        uodateEpzWithPlacementPolicy();

        // Filter should return thirdHost as it has lowest amount of containers.
        filter = new SpreadAffinityHostFilter(host, containerDesc);

        Map<String, HostSelection> selected = filter();

        assertTrue(!selected.isEmpty());

        assertEquals(1, selected.size());

        assertTrue(selected.containsKey(thirdHost));

        // Change number of containers per host.
        assignContainersToHost(firstHost, 15);
        // secondHost won't be updated. It will contain smallest amount of containers.
        assignContainersToHost(thirdHost, 23);

        // Clear previous result.
        selected.clear();

        // secontHost should be the result.
        selected = filter();

        assertTrue(!selected.isEmpty());

        assertEquals(1, selected.size());

        assertTrue(selected.containsKey(secondHost));

    }

    private void uodateEpzWithPlacementPolicy() throws Throwable {

        // Create ElasticPlacementZoneState which follows SPREAD deployment policy.
        ElasticPlacementZoneState epzState = new ElasticPlacementZoneState();
        epzState.placementPolicy = ElasticPlacementZoneService.PlacementPolicy.SPREAD;
        epzState.resourcePoolLink = resourcePool.documentSelfLink;

        ElasticPlacementZoneConfigurationState epz = new ElasticPlacementZoneConfigurationState();
        epz.documentSelfLink = resourcePool.documentSelfLink;
        epz.resourcePoolState = resourcePool;
        epz.epzState = epzState;

        epz = doOperation(epz,
                UriUtils.buildUri(host, ElasticPlacementZoneConfigurationService.SELF_LINK),
                ElasticPlacementZoneConfigurationState.class, false, Action.PUT);

        assertEquals(epz.epzState.placementPolicy,
                ElasticPlacementZoneService.PlacementPolicy.SPREAD);

    }

    private void assignContainersToHost(String hostLink, int instances) throws Throwable {
        for (int i = 0; i <= instances; i++) {
            ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
            desc.documentSelfLink = UUID.randomUUID().toString();
            containerDesc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
            createContainerWithDifferentContextId(desc, hostLink);
        }
    }

}
