/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.compute.EpzComputeEnumerationTaskService.EpzComputeEnumerationTaskState;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for the {@link EpzComputeEnumerationTaskService} class.
 */
public class EpzComputeEnumerationTaskServiceTest extends ComputeBaseTest {

    private ElasticPlacementZoneConfigurationState epz1;
    private ElasticPlacementZoneConfigurationState epz2;
    private ElasticPlacementZoneConfigurationState epz3;

    private ComputeState cs1;
    private ComputeState cs2;
    private ComputeState cs3;
    private ComputeState cs4;
    private ComputeState cs5;
    private ComputeState cs6;
    private ComputeState cs7;

    @Before
    public void createTestModel() throws Throwable {
        this.epz1 = createEpz("tag1");
        this.epz2 = createEpz("tag2");
        this.epz3 = createEpz();

        this.cs1 = createComputeState("desc1", "addr-sof-1", null, "tag1");
        this.cs2 = createComputeState("desc1", "addr-sof-2", null, "tag1");
        this.cs3 = createComputeState("desc2", "addr-sof-3", null, "tag2");
        this.cs4 = createComputeState("desc2", "addr-pa-4", null, "tag1", "tag2");
        this.cs5 = createComputeState("desc2", "addr-pa-5", null);
        this.cs6 = createComputeState("desc2", "addr-pa-6", this.epz3.documentSelfLink);
        this.cs7 = createComputeState("desc1", "addr-sof-1", this.epz2.documentSelfLink, "tag1");
    }

    public void setUp() throws Throwable {
        waitForServiceAvailability(EpzComputeEnumerationTaskService.FACTORY_LINK);
    }

    @Test
    public void testInitialEnumeration() throws Throwable {
        enumerateAllEpzs();

        validateCompute(this.cs1, this.epz1);
        validateCompute(this.cs2, this.epz1);
        validateCompute(this.cs3, this.epz2);
        validateCompute(this.cs4, this.epz1, this.epz2);
        validateCompute(this.cs5);
        validateCompute(this.cs6, this.epz3);
        validateCompute(this.cs7, this.epz1, this.epz2);
    }

    @Test
    public void testEpzChanges() throws Throwable {
        enumerateAllEpzs();

        this.epz1.epzState.tagLinksToMatch.add("tag2");
        this.epz2.epzState.tagLinksToMatch.remove("tag2");
        this.epz3.epzState = new ElasticPlacementZoneState();
        this.epz3.epzState.resourcePoolLink = this.epz3.documentSelfLink;
        this.epz3.epzState.tagLinksToMatch = new HashSet<>();
        this.epz3.epzState.tagLinksToMatch.addAll(Arrays.asList("tag1"));
        doPut(this.epz1.epzState);
        doPut(this.epz2.epzState);
        doPost(this.epz3.epzState, ElasticPlacementZoneService.FACTORY_LINK);
        enumerateAllEpzs();

        validateCompute(this.cs1, this.epz3);
        validateCompute(this.cs2, this.epz3);
        validateCompute(this.cs3);
        validateCompute(this.cs4, this.epz3, this.epz1);
        validateCompute(this.cs5);
        validateCompute(this.cs6, this.epz3);
    }

    private void validateCompute(ComputeState compute,
            ElasticPlacementZoneConfigurationState... expectedRps) {
        Collection<String> returnedRps = extractRpLinks(compute);
        assertEquals(expectedRps.length, returnedRps.size());
        for (ElasticPlacementZoneConfigurationState expectedRp : expectedRps) {
            assertTrue(returnedRps.contains(expectedRp.documentSelfLink));
        }
    }

    private Collection<String> extractRpLinks(ComputeState compute) {
        if (compute.customProperties == null) {
            return new ArrayList<>();
        }

        return compute.customProperties.keySet().stream()
                .filter(k -> k.startsWith(EpzComputeEnumerationTaskService.EPZ_CUSTOM_PROP_NAME_PREFIX))
                .map(k -> k.substring(
                        EpzComputeEnumerationTaskService.EPZ_CUSTOM_PROP_NAME_PREFIX.length()))
                .map(id -> UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK, id))
                .collect(Collectors.toList());
    }

    private void enumerateAllEpzs() throws Throwable {
        // run them in parallel
        String task1Link = startTask(this.epz1.documentSelfLink);
        String task2Link = startTask(this.epz2.documentSelfLink);
        String task3Link = startTask(this.epz3.documentSelfLink);

        waitFor(() -> getDocumentNoWait(EpzComputeEnumerationTaskState.class, task1Link) == null);
        waitFor(() -> getDocumentNoWait(EpzComputeEnumerationTaskState.class, task2Link) == null);
        waitFor(() -> getDocumentNoWait(EpzComputeEnumerationTaskState.class, task3Link) == null);

        this.cs1 = getDocument(ComputeState.class, this.cs1.documentSelfLink);
        this.cs2 = getDocument(ComputeState.class, this.cs2.documentSelfLink);
        this.cs3 = getDocument(ComputeState.class, this.cs3.documentSelfLink);
        this.cs4 = getDocument(ComputeState.class, this.cs4.documentSelfLink);
        this.cs5 = getDocument(ComputeState.class, this.cs5.documentSelfLink);
        this.cs6 = getDocument(ComputeState.class, this.cs6.documentSelfLink);
        this.cs7 = getDocument(ComputeState.class, this.cs7.documentSelfLink);
    }

    private ComputeState createComputeState(String desc, String address, String rpLink,
            String... tags) throws Throwable {
        ComputeState cs = new ComputeState();
        cs.address = address;
        cs.descriptionLink = desc;
        cs.resourcePoolLink = rpLink;
        cs.type = ComputeType.VM_GUEST;
        cs.tagLinks = tagSet(tags);
        return doPost(cs, ComputeService.FACTORY_LINK);
    }

    private ElasticPlacementZoneConfigurationState createEpz(String ...tags) throws Throwable {
        ElasticPlacementZoneConfigurationState epz = new ElasticPlacementZoneConfigurationState();
        epz.resourcePoolState = new ResourcePoolState();
        epz.resourcePoolState.name = UUID.randomUUID().toString();
        if (tags.length > 0) {
            epz.epzState = new ElasticPlacementZoneState();
            epz.epzState.tagLinksToMatch = tagSet(tags);
        }

        return doOperation(epz,
                UriUtils.buildUri(host, ElasticPlacementZoneConfigurationService.SELF_LINK),
                ElasticPlacementZoneConfigurationState.class, false, Action.POST);
    }

    private static Set<String> tagSet(String... tagLinks) {
        Set<String> result = new HashSet<>();
        for (String tagLink : tagLinks) {
            result.add(tagLink);
        }
        return result;
    }

    private String startTask(String resourcePoolLink) throws Throwable {
        EpzComputeEnumerationTaskState initialState = new EpzComputeEnumerationTaskState();
        initialState.resourcePoolLink = resourcePoolLink;
        EpzComputeEnumerationTaskState returnState = doOperation(initialState,
                UriUtils.buildUri(this.host, EpzComputeEnumerationTaskService.FACTORY_LINK),
                EpzComputeEnumerationTaskState.class, false, Action.POST);
        return returnState.documentSelfLink;
    }
}
