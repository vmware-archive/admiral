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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class BinpackAffinityHostFilterTest extends BaseAffinityHostFilterTest {

    private static final String HIDDEN_CUSTOM_PROPERTY_AVAILABLE_MEMORY = "__MemAvailable";
    private Map<String, HostSelection> hostSelection;

    @Test
    public void testFilterDoesNotAffectHosts() throws Throwable {
        filter = new BinpackAffinityHostFilter(host, containerDesc);
        assertTrue(filter.isActive());
        Map<String, HostSelection> selected = filter();
        assertEquals(3, selected.size());
    }

    @Test
    public void testFilterBasedOnMaxLoadedHost() throws Throwable {

        String firstHost = initialHostLinks.get(0);
        String secondHost = initialHostLinks.get(1);
        String thirdHost = initialHostLinks.get(2);

        Map<String, Long> hostToAvailableMem = new HashMap<>();
        hostToAvailableMem.put(firstHost, 6000000000L);
        hostToAvailableMem.put(secondHost, 5000000000L);
        hostToAvailableMem.put(thirdHost, 7000000000L);

        // Second host has lowest available memory
        setHostsStats(hostToAvailableMem);

        uodateEpzWithPlacementPolicy();

        /*
         * H1[6GB], H2[5GB], H3[7GB] BinPack sorting algorithm will return H2 as it has only 5GB
         * available memory.
         */
        filter = new BinpackAffinityHostFilter(host, containerDesc);

        Map<String, HostSelection> selected = filter();

        assertEquals(1, selected.size());

        // Verify that result contains only host-2 (most loaded one)
        assertNotNull(selected.get(secondHost));

        // Shuffle metrics in order to set third host with minimal available memory.
        hostToAvailableMem.clear();
        hostToAvailableMem.put(firstHost, 8000000000L);
        hostToAvailableMem.put(secondHost, 16000000000L);
        hostToAvailableMem.put(thirdHost, 4000000000L);
        setHostsStats(hostToAvailableMem);

        /*
         * H1[8GB], H2[16GB], H3[4GB] BinPack sorting algorithm will return H3 as it has only 4GB
         * available memory.
         */
        filter = new BinpackAffinityHostFilter(host, containerDesc);
        selected.clear();
        selected = filter();

        assertEquals(1, selected.size());

        // Verify that result contains only host-3 (most loaded one)
        assertNotNull(selected.get(thirdHost));

    }

    @Test
    public void testAllHostsAreOverloadedScenario() throws Throwable {

        String firstHost = initialHostLinks.get(0);
        String secondHost = initialHostLinks.get(1);
        String thirdHost = initialHostLinks.get(2);

        Map<String, Long> hostToAvailableMem = new HashMap<>();
        hostToAvailableMem.put(firstHost, 1045004000L);
        hostToAvailableMem.put(secondHost, 1800000000L);
        hostToAvailableMem.put(thirdHost, 1904050000L);

        // All hosts have available memory below minimum required for Binpack policy which is 2GB.
        setHostsStats(hostToAvailableMem);
        uodateEpzWithPlacementPolicy();

        filter = new BinpackAffinityHostFilter(host, containerDesc);

        Throwable e = null;

        try {
            filter();
        } catch (Throwable ex) {
            e = ex;
            assertEquals("All hosts are overloaded.", ex.getMessage());
        }

        assertNotNull(e);
    }

    @Test
    public void testSwitchToNewHostIfAvailableMemoryIsTooLow() throws Throwable {

        String firstHost = initialHostLinks.get(0);
        String secondHost = initialHostLinks.get(1);
        String thirdHost = initialHostLinks.get(2);

        Map<String, Long> hostToAvailableMem = new HashMap<>();
        hostToAvailableMem.put(firstHost, 9000000000L); // 9GB
        hostToAvailableMem.put(secondHost, 1000000000L); // 1GB
        hostToAvailableMem.put(thirdHost, 5000000000L); // 5GB

        setHostsStats(hostToAvailableMem);
        uodateEpzWithPlacementPolicy();

        // In this case host-2 is most loaded, but its memory is lower than required - 3GB. Filter
        // must switch to second most suitable host which is host-3 with 5GB.
        filter = new BinpackAffinityHostFilter(host, containerDesc);

        Map<String, HostSelection> selected = filter();

        assertEquals(1, selected.size());

        // Verify that result contains only host-3, as host-2 is below minimum available memory.
        assertNotNull(selected.get(thirdHost));
    }

    private void setHostsStats(Map<String, Long> hostToAvailableMemory) throws Throwable {
        assertEquals(3, initialHostLinks.size());
        for (String host : initialHostLinks) {
            ComputeState currentHost = getDocument(ComputeState.class, host);
            currentHost.customProperties = new HashMap<>();
            String availableMemory = String
                    .valueOf(hostToAvailableMemory.get(currentHost.documentSelfLink));
            currentHost.customProperties.put(HIDDEN_CUSTOM_PROPERTY_AVAILABLE_MEMORY,
                    availableMemory);
            doPost(currentHost, ComputeService.FACTORY_LINK);

            if (hostSelection == null) {
                hostSelection = prepareHostSelectionMap();
            }
            HostSelection hs = hostSelection.get(host);
            hs.availableMemory = hostToAvailableMemory.get(host);
            hostSelection.put(host, hs);
        }

    }

    @Override
    protected Map<String, HostSelection> prepareHostSelectionMap() throws Throwable {
        return hostSelection == null ? super.prepareHostSelectionMap() : hostSelection;
    }

    private void uodateEpzWithPlacementPolicy() throws Throwable {

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
