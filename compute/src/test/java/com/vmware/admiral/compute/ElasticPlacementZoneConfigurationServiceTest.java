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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for the {@link ElasticPlacementZoneConfigurationService} class.
 */
public class ElasticPlacementZoneConfigurationServiceTest extends ComputeBaseTest {
    public void setUp() throws Throwable {
        waitForServiceAvailability(ElasticPlacementZoneConfigurationService.SELF_LINK);
    }

    @Test
    public void testGetNoEpz() throws Throwable {
        String rpLink = createRp().documentSelfLink;

        ElasticPlacementZoneConfigurationState state = getState(rpLink);
        assertNotNull(state.resourcePoolState.query);
        assertNull(state.epzState);
    }

    @Test
    public void testGetWithEpz() throws Throwable {
        String rpLink = createRp().documentSelfLink;
        String epzLink = createEpz(rpLink, "tag1").documentSelfLink;

        ElasticPlacementZoneConfigurationState state = getState(rpLink);
        assertNotNull(state.resourcePoolState.query);
        assertNotNull(state.epzState);
        assertEquals(epzLink, state.epzState.documentSelfLink);
    }

    @Test(expected = ServiceNotFoundException.class)
    public void testGetNonExistent() throws Throwable {
        getState("/resources/pools/invalid-link");
    }

    @Test
    public void testGetAllNoExpand() throws Throwable {
        String rp1Link = createRp().documentSelfLink;
        String rp2Link = createRp().documentSelfLink;

        URI serviceUri = UriUtils.buildUri(host, ElasticPlacementZoneConfigurationService.SELF_LINK);
        ServiceDocumentQueryResult queryResult = doOperation(null,
                serviceUri,
                ServiceDocumentQueryResult.class, false, Action.GET);

        assertNotNull(queryResult);
        assertNull(queryResult.documents);
        assertTrue(queryResult.documentCount >= 2);
        assertTrue(queryResult.documentLinks.contains(rp1Link));
        assertTrue(queryResult.documentLinks.contains(rp2Link));
    }

    @Test
    public void testGetAllExpand() throws Throwable {
        String rp1Link = createRp().documentSelfLink;
        String rp2Link = createRp().documentSelfLink;
        String epzLink = createEpz(rp2Link, "tag1").documentSelfLink;

        URI serviceUri = UriUtils.buildUri(host, ElasticPlacementZoneConfigurationService.SELF_LINK);
        ServiceDocumentQueryResult queryResult = doOperation(null,
                UriUtils.buildExpandLinksQueryUri(serviceUri),
                ServiceDocumentQueryResult.class, false, Action.GET);

        assertNotNull(queryResult);
        assertNotNull(queryResult.documents);
        assertTrue(queryResult.documentCount >= 2);

        Map<String, ElasticPlacementZoneConfigurationState> states = QueryUtil.extractQueryResult(
                queryResult, ElasticPlacementZoneConfigurationState.class);

        ElasticPlacementZoneConfigurationState state1 = states.get(rp1Link);
        assertNotNull(state1);
        assertNotNull(state1.resourcePoolState);
        assertNull(state1.epzState);
        assertEquals(rp1Link, state1.documentSelfLink);
        assertEquals(rp1Link, state1.resourcePoolState.documentSelfLink);

        ElasticPlacementZoneConfigurationState state2 = states.get(rp2Link);
        assertNotNull(state2);
        assertNotNull(state2.resourcePoolState);
        assertNotNull(state2.epzState);
        assertEquals(rp2Link, state2.documentSelfLink);
        assertEquals(rp2Link, state2.resourcePoolState.documentSelfLink);
        assertEquals(epzLink, state2.epzState.documentSelfLink);
        assertEquals(state2.resourcePoolState.documentSelfLink, state2.epzState.resourcePoolLink);
        assertEquals(tagSet("tag1"), state2.epzState.tagLinksToMatch);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testDeleteNoLinkInUrl() throws Throwable {
        String rpLink = createRp().documentSelfLink;

        ElasticPlacementZoneConfigurationState state = createState(false);
        state.resourcePoolState.documentSelfLink = rpLink;

        sendState(state, Action.DELETE);
    }

    @Test
    public void testDeleteNoEpz() throws Throwable {
        String rpLink = createRp().documentSelfLink;

        ElasticPlacementZoneConfigurationState state = createState(false);
        state.resourcePoolState.documentSelfLink = rpLink;

        delete(ElasticPlacementZoneConfigurationService.SELF_LINK + rpLink);
        assertNull(searchForDocument(ResourcePoolState.class, rpLink));
    }

    @Test
    public void testDeleteWithEpz() throws Throwable {
        String rpLink = createRp().documentSelfLink;
        String epzLink = createEpz(rpLink, "tag1").documentSelfLink;

        ElasticPlacementZoneConfigurationState state = createState(true);
        state.resourcePoolState.documentSelfLink = rpLink;
        state.epzState.documentSelfLink = epzLink;

        delete(ElasticPlacementZoneConfigurationService.SELF_LINK + rpLink);
        assertNull(searchForDocument(ResourcePoolState.class, rpLink));
        assertNull(searchForDocument(ElasticPlacementZoneState.class, epzLink));
    }

    @Test
    public void testCreateNoEpz() throws Throwable {
        ElasticPlacementZoneConfigurationState state = createState(true);
        state.resourcePoolState = buildRpState();
        ElasticPlacementZoneConfigurationState returnedState = sendState(state, Action.POST);

        assertNotNull(returnedState.resourcePoolState.documentSelfLink);
        assertNotNull(searchForDocument(ResourcePoolState.class,
                returnedState.resourcePoolState.documentSelfLink));
        assertEquals(state.resourcePoolState.name, returnedState.resourcePoolState.name);
    }

    @Test
    public void testCreateWithEpz() throws Throwable {
        ElasticPlacementZoneConfigurationState state = createState(true);
        state.resourcePoolState = buildRpState();
        state.epzState = buildEpzState(null, "tag1", "tag2");
        ElasticPlacementZoneConfigurationState returnedState = sendState(state, Action.POST);

        assertNotNull(returnedState.resourcePoolState.documentSelfLink);
        assertNotNull(searchForDocument(ResourcePoolState.class,
                returnedState.resourcePoolState.documentSelfLink));
        assertNotNull(returnedState.epzState.documentSelfLink);
        assertNotNull(searchForDocument(ElasticPlacementZoneState.class,
                returnedState.epzState.documentSelfLink));
        assertEquals(state.resourcePoolState.name, returnedState.resourcePoolState.name);
        assertEquals(state.epzState.tagLinksToMatch, returnedState.epzState.tagLinksToMatch);
    }

    @Test
    public void testCreateDelete() throws Throwable {
        ElasticPlacementZoneConfigurationState state = createState(true);
        state.resourcePoolState = buildRpState();
        state.epzState = buildEpzState(null, "tag1", "tag2");
        ElasticPlacementZoneConfigurationState createdState = sendState(state, Action.POST);

        delete(ElasticPlacementZoneConfigurationService.SELF_LINK
                + createdState.resourcePoolState.documentSelfLink);

        assertNull(
                searchForDocument(ResourcePoolState.class, createdState.resourcePoolState.documentSelfLink));
        assertNull(searchForDocument(ElasticPlacementZoneState.class,
                createdState.epzState.documentSelfLink));
    }

    @Test
    public void testUpdateNoEpz() throws Throwable {
        // create through the config service
        ElasticPlacementZoneConfigurationState state = createState(false);
        state.resourcePoolState = buildRpState();
        ElasticPlacementZoneConfigurationState createdState = sendState(state, Action.POST);

        // update through the config service
        ElasticPlacementZoneConfigurationState patchState = createState(false);
        patchState.resourcePoolState.documentSelfLink =
                createdState.resourcePoolState.documentSelfLink;
        patchState.resourcePoolState.name = "new-name";
        ElasticPlacementZoneConfigurationState latestState = sendState(patchState, Action.PATCH);

        // validate returned state
        assertEquals("new-name", latestState.resourcePoolState.name);
        assertNotNull(latestState.resourcePoolState.query);

        // validate the actual RP state
        ResourcePoolState rp = getDocument(ResourcePoolState.class,
                createdState.resourcePoolState.documentSelfLink);
        assertEquals("new-name", rp.name);
    }

    @Test
    public void testUpdateWithEpz() throws Throwable {
        // create through the config service
        ElasticPlacementZoneConfigurationState state = createState(true);
        state.resourcePoolState = buildRpState();
        state.epzState = buildEpzState(null, "tag1", "tag2");
        ElasticPlacementZoneConfigurationState createdState = sendState(state, Action.POST);

        // update through the config service
        ElasticPlacementZoneConfigurationState patchState = createState(true);
        patchState.resourcePoolState.documentSelfLink =
                createdState.resourcePoolState.documentSelfLink;
        patchState.resourcePoolState.name = "new-name";
        patchState.epzState = buildEpzState(patchState.resourcePoolState.documentSelfLink,
                "tag3", "tag4");
        patchState.epzState.documentSelfLink = createdState.epzState.documentSelfLink;
        ElasticPlacementZoneConfigurationState latestState = sendState(patchState, Action.PATCH);

        // validate returned state
        assertEquals("new-name", latestState.resourcePoolState.name);
        assertNotNull(latestState.resourcePoolState.query);
        assertEquals(patchState.epzState.tagLinksToMatch, latestState.epzState.tagLinksToMatch);

        // validate the actual RP state
        ResourcePoolState rp = getDocument(ResourcePoolState.class,
                createdState.resourcePoolState.documentSelfLink);
        assertEquals("new-name", rp.name);

        // validate the actual EPZ state
        ElasticPlacementZoneState epz = getDocument(ElasticPlacementZoneState.class,
                createdState.epzState.documentSelfLink);
        assertEquals(patchState.epzState.tagLinksToMatch, epz.tagLinksToMatch);
    }

    @Test
    public void testUpdateAddingEpz() throws Throwable {
        // create through the config service with no EPZ
        ElasticPlacementZoneConfigurationState state = createState(false);
        state.resourcePoolState = buildRpState();
        ElasticPlacementZoneConfigurationState createdState = sendState(state, Action.POST);

        // add EPZ through the config service
        ElasticPlacementZoneConfigurationState patchState = createState(false);
        patchState.resourcePoolState.documentSelfLink =
                createdState.resourcePoolState.documentSelfLink;
        patchState.epzState = buildEpzState(patchState.resourcePoolState.documentSelfLink,
                "tag3", "tag4");
        ElasticPlacementZoneConfigurationState latestState = sendState(patchState, Action.PATCH);

        // validate returned state
        assertNotNull(latestState.epzState);
        assertNotNull(latestState.epzState.documentSelfLink);
        assertEquals(patchState.epzState.tagLinksToMatch, latestState.epzState.tagLinksToMatch);

        // validate the actual EPZ state
        ElasticPlacementZoneState epz = getDocument(ElasticPlacementZoneState.class,
                latestState.epzState.documentSelfLink);
        assertEquals(patchState.epzState.tagLinksToMatch, epz.tagLinksToMatch);
    }

    @Test
    public void testUpdateNoChange() throws Throwable {
        // create through the config service
        ElasticPlacementZoneConfigurationState state = createState(true);
        state.resourcePoolState = buildRpState();
        ElasticPlacementZoneConfigurationState createdState = sendState(state, Action.POST);

        // update through the config service
        ElasticPlacementZoneConfigurationState patchState = createState(false);
        state.resourcePoolState = buildRpState();
        patchState.resourcePoolState.documentSelfLink =
                createdState.resourcePoolState.documentSelfLink;
        ElasticPlacementZoneConfigurationState latestState = sendState(patchState, Action.PATCH);

        // validate returned state
        assertNull(latestState.resourcePoolState);
        assertNull(latestState.epzState);
    }

    private static ResourcePoolState buildRpState() {
        ResourcePoolState state = new ResourcePoolState();
        state.name = "rp-1";
        return state;
    }

    private ResourcePoolState createRp() throws Throwable {
        return doPost(buildRpState(), ResourcePoolService.FACTORY_LINK);
    }

    private ElasticPlacementZoneState buildEpzState(String rpLink, String... tagLinks) {
        ElasticPlacementZoneState state = new ElasticPlacementZoneState();
        state.resourcePoolLink = rpLink;
        state.tagLinksToMatch = tagSet(tagLinks);
        return state;
    }

    private ElasticPlacementZoneState createEpz(String rpLink, String... tagLinks)
            throws Throwable {
        return doPost(buildEpzState(rpLink, tagLinks), ElasticPlacementZoneService.FACTORY_LINK);
    }

    private static Set<String> tagSet(String... tagLinks) {
        Set<String> result = new HashSet<>();
        for (String tagLink : tagLinks) {
            result.add(tagLink);
        }
        return result;
    }

    private ElasticPlacementZoneConfigurationState createState(boolean withEpz) {
        ElasticPlacementZoneConfigurationState state = new ElasticPlacementZoneConfigurationState();
        state.resourcePoolState = new ResourcePoolState();
        if (withEpz) {
            state.epzState = new ElasticPlacementZoneState();
        }
        return state;
    }

    private ElasticPlacementZoneConfigurationState sendState(
            ElasticPlacementZoneConfigurationState inState, Action action) throws Throwable {
        return doOperation(inState,
                UriUtils.buildUri(host, ElasticPlacementZoneConfigurationService.SELF_LINK),
                ElasticPlacementZoneConfigurationState.class, false, action);
    }

    private ElasticPlacementZoneConfigurationState getState(String rpLink) throws Throwable {
        return getDocument(ElasticPlacementZoneConfigurationState.class,
                ElasticPlacementZoneConfigurationService.SELF_LINK + rpLink);
    }
}
