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

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.ElasticPlacementZoneAssignmentTaskService.ElasticPlacementZoneAssignmentTaskState;
import com.vmware.photon.controller.model.resources.ComputeService;

/**
 * Tests for the {@link ElasticPlacementZoneService} class.
 */
public class ElasticPlacementZoneAssignmentTaskServiceTest extends RequestBaseTest {
    @Test
    public void testEpz() throws Throwable {
        // create computes
        String compute1Link = createCompute("rp0Link", "tag1Link").documentSelfLink;
        String compute2Link = createCompute(null, "tag2Link").documentSelfLink;
        String compute3Link = createCompute(null, "tag3Link", "tag4Link").documentSelfLink;
        String compute4Link = createCompute("rp1link", "tag5Link", "tag6Link").documentSelfLink;

        // create EPZ
        createEpz("rp1Link", "tag1Link", "tag2Link"); // no matching computes
        createEpz("rp2Link", "tag3Link", "tag4Link"); // compute3 should match
        createEpz("rp3Link", "tag5Link");             // compute4 should match

        // execute assignment task
        String taskLink = doPost(new ElasticPlacementZoneAssignmentTaskState(),
                ElasticPlacementZoneAssignmentTaskService.FACTORY_LINK).documentSelfLink;
        waitForTaskSuccess(taskLink, ElasticPlacementZoneAssignmentTaskState.class);

        ComputeService.ComputeState compute1 =
                getDocument(ComputeService.ComputeState.class, compute1Link);
        ComputeService.ComputeState compute2 =
                getDocument(ComputeService.ComputeState.class, compute2Link);
        ComputeService.ComputeState compute3 =
                getDocument(ComputeService.ComputeState.class, compute3Link);
        ComputeService.ComputeState compute4 =
                getDocument(ComputeService.ComputeState.class, compute4Link);

        assertEquals("rp0Link", compute1.resourcePoolLink);
        assertEquals(null, compute2.resourcePoolLink);
        assertEquals("rp2Link", compute3.resourcePoolLink);
        assertEquals("rp3Link", compute4.resourcePoolLink);
    }

    @Test
    public void testEpzConflict() throws Throwable {
        // create computes
        String compute1Link = createCompute(null, "tag1Link", "tag2Link").documentSelfLink;
        String compute2Link = createCompute("rp0Link", "tag1Link", "tag2Link", "tag3Link")
                .documentSelfLink;

        // create EPZ
        createEpz("rp1Link", "tag1Link");
        createEpz("rp2Link", "tag2Link");

        // execute assignment task
        String taskLink = doPost(new ElasticPlacementZoneAssignmentTaskState(),
                ElasticPlacementZoneAssignmentTaskService.FACTORY_LINK).documentSelfLink;
        waitForTaskSuccess(taskLink, ElasticPlacementZoneAssignmentTaskState.class);

        ComputeService.ComputeState compute1 =
                getDocument(ComputeService.ComputeState.class, compute1Link);
        ComputeService.ComputeState compute2 =
                getDocument(ComputeService.ComputeState.class, compute2Link);

        // make sure no RP changes were done
        assertEquals(null, compute1.resourcePoolLink);
        assertEquals("rp0Link", compute2.resourcePoolLink);
    }

    private ElasticPlacementZoneState createEpz(String rpLink, String... tagLinks)
            throws Throwable {
        ElasticPlacementZoneState initialState = new ElasticPlacementZoneState();
        initialState.resourcePoolLink = rpLink;
        initialState.tagLinksToMatch = tagSet(tagLinks);
        return doPost(initialState, ElasticPlacementZoneService.FACTORY_LINK);
    }

    private ComputeService.ComputeState createCompute(String rpLink, String... tagLinks)
            throws Throwable {
        ComputeService.ComputeState initialState = new ComputeService.ComputeState();
        initialState.descriptionLink = "dummy-desc";
        initialState.resourcePoolLink = rpLink;
        initialState.tagLinks = tagSet(tagLinks);
        return doPost(initialState, ComputeService.FACTORY_LINK);
    }

    private static Set<String> tagSet(String... tagLinks) {
        Set<String> result = new HashSet<>();
        for (String tagLink : tagLinks) {
            result.add(tagLink);
        }
        return result;
    }
}
