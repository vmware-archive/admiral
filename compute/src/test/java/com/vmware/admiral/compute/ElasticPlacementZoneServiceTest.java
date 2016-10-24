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

package com.vmware.admiral.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState.ResourcePoolProperty;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;

/**
 * Tests for the {@link ElasticPlacementZoneService} class.
 */
public class ElasticPlacementZoneServiceTest extends ComputeBaseTest {
    @Test
    public void testCreateAndDelete() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertEquals(2, rp.query.booleanClauses.size());

        // create EPZ for the RP
        String epzLink = createEpz(rp.documentSelfLink, "tag1", "tag2").documentSelfLink;

        // verify RP is now elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertEquals(3, rp.query.booleanClauses.size());

        // delete EPZ and verify RP is back to non-elastic
        delete(epzLink);
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertEquals(2, rp.query.booleanClauses.size());
    }

    @Test
    public void testCreateNoTags() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertEquals(2, rp.query.booleanClauses.size());

        // create EPZ for the RP with no tags
        createEpz(rp.documentSelfLink);

        // verify RP is not elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertEquals(2, rp.query.booleanClauses.size());
    }

    @Test
    public void testDeleteWithNonExistentRp() throws Throwable {
        // create EPZ for the RP
        ResourcePoolState rp = createRp();
        String epzLink = createEpz(rp.documentSelfLink, "tag1", "tag2").documentSelfLink;

        // delete EPZ and verify RP is back to non-elastic
        delete(rp.documentSelfLink);
        delete(epzLink);
    }

    @Test
    public void testPut() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertEquals(2, rp.query.booleanClauses.size());

        // create EPZ for the RP
        ElasticPlacementZoneState epz = createEpz(rp.documentSelfLink, "tag1", "tag2");

        // verify RP is now elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertEquals(3, rp.query.booleanClauses.size());

        // add more tags through a put request
        epz.tagLinksToMatch.add("tag3");
        epz.tagLinksToMatch.add("tag4");
        doPut(epz);

        // verify RP is updated with the new tags
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertEquals(5, rp.query.booleanClauses.size());
    }

    @Test
    public void testPutNoTags() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertEquals(2, rp.query.booleanClauses.size());

        // create EPZ for the RP
        ElasticPlacementZoneState epz = createEpz(rp.documentSelfLink, "tag1", "tag2");

        // verify RP is now elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertEquals(3, rp.query.booleanClauses.size());

        // add more tags through a put request
        epz.tagLinksToMatch.clear();;
        doPut(epz);

        // verify RP is not elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertEquals(2, rp.query.booleanClauses.size());
    }

    @Test
    public void testPatch() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertEquals(2, rp.query.booleanClauses.size());

        // create EPZ for the RP
        String epzLink = createEpz(rp.documentSelfLink, "tag1", "tag2").documentSelfLink;

        // verify RP is now elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertEquals(3, rp.query.booleanClauses.size());

        // add more tags and verify RP query is updated
        patchEpz(epzLink, "tag3", "tag4");
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertEquals(5, rp.query.booleanClauses.size());
    }

    @Test
    public void testPatchNoTags() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertEquals(2, rp.query.booleanClauses.size());

        // create EPZ for the RP
        String epzLink = createEpz(rp.documentSelfLink, "tag1", "tag2").documentSelfLink;

        // verify RP is now elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertEquals(3, rp.query.booleanClauses.size());

        // add more tags and verify RP query is updated
        Map<String, Collection<Object>> itemsToRemove = new HashMap<>();
        itemsToRemove.put(ElasticPlacementZoneState.FIELD_NAME_TAG_LINKS_TO_MATCH,
                new ArrayList<>(Arrays.asList("tag1", "tag2")));
        ServiceStateCollectionUpdateRequest updateRequest = ServiceStateCollectionUpdateRequest
                .create(null, itemsToRemove);
        verifyOperation(Operation.createPatch(this.host, epzLink).setBody(updateRequest));

        // verify EPZ has no tags
        ElasticPlacementZoneState newEpzState = getDocument(ElasticPlacementZoneState.class,
                epzLink);
        assertNotNull(newEpzState.tagLinksToMatch);
        assertTrue(newEpzState.tagLinksToMatch.isEmpty());

        // verify RP is not elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertEquals(2, rp.query.booleanClauses.size());
    }

    private ElasticPlacementZoneState createEpz(String rpLink, String... tagLinks)
            throws Throwable {
        ElasticPlacementZoneState initialState = new ElasticPlacementZoneState();
        initialState.resourcePoolLink = rpLink;
        initialState.tagLinksToMatch = tagSet(tagLinks);
        return doPost(initialState, ElasticPlacementZoneService.FACTORY_LINK);
    }

    private ElasticPlacementZoneState patchEpz(String epzLink, String... tagLinksToAdd)
            throws Throwable {
        ElasticPlacementZoneState patchState = new ElasticPlacementZoneState();
        patchState.tagLinksToMatch = tagSet(tagLinksToAdd);
        return doPatch(patchState, epzLink);
    }

    private ResourcePoolState createRp() throws Throwable {
        ResourcePoolState initialState = new ResourcePoolState();
        initialState.name = "rp-1";
        return doPost(initialState, ResourcePoolService.FACTORY_LINK);
    }

    private static Set<String> tagSet(String... tagLinks) {
        Set<String> result = new HashSet<>();
        for (String tagLink : tagLinks) {
            result.add(tagLink);
        }
        return result;
    }
}
