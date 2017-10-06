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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTemplate;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState.ResourcePoolProperty;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Tests for the {@link ElasticPlacementZoneService} class.
 */
public class ElasticPlacementZoneServiceTest extends ComputeBaseTest {
    @Test
    public void testCreateAndDelete() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertTrue(isNonElasticQuery(rp.query));

        // create EPZ for the RP
        String epzLink = createEpz(rp.documentSelfLink, "tag1", "tag2").documentSelfLink;

        // verify RP is now elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertFalse(isNonElasticQuery(rp.query));

        // delete EPZ and verify RP is back to non-elastic
        delete(epzLink);
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertTrue(isNonElasticQuery(rp.query));
    }

    @Test
    public void testCreateNoTags() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertTrue(isNonElasticQuery(rp.query));

        // create EPZ for the RP with no tags
        createEpz(rp.documentSelfLink);

        // verify RP is not elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertTrue(isNonElasticQuery(rp.query));
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
        assertTrue(isNonElasticQuery(rp.query));

        // create EPZ for the RP
        ElasticPlacementZoneState epz = createEpz(rp.documentSelfLink, "tag1", "tag2");

        // verify RP is now elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertFalse(isNonElasticQuery(rp.query));

        // add more tags through a put request
        epz.tagLinksToMatch.add("tag3");
        epz.tagLinksToMatch.add("tag4");
        doPut(epz);

        // verify RP is updated with the new tags
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
    }

    @Test
    public void testPutNoTags() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertTrue(isNonElasticQuery(rp.query));

        // create EPZ for the RP
        ElasticPlacementZoneState epz = createEpz(rp.documentSelfLink, "tag1", "tag2");

        // verify RP is now elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertFalse(isNonElasticQuery(rp.query));

        // add more tags through a put request
        epz.tagLinksToMatch.clear();;
        doPut(epz);

        // verify RP is not elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertTrue(isNonElasticQuery(rp.query));
    }

    @Test
    public void testPatch() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertTrue(isNonElasticQuery(rp.query));

        // create EPZ for the RP
        String epzLink = createEpz(rp.documentSelfLink, "tag1", "tag2").documentSelfLink;

        // verify RP is now elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertFalse(isNonElasticQuery(rp.query));

        // add more tags and verify RP query is updated
        patchEpz(epzLink, "tag3", "tag4");
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
    }

    @Test
    public void testPatchNoChange() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertTrue(isNonElasticQuery(rp.query));

        // create EPZ for the RP
        String epzLink = createEpz(rp.documentSelfLink, "tag1", "tag2").documentSelfLink;

        ElasticPlacementZoneState patchState = new ElasticPlacementZoneState();
        patchState.tagLinksToMatch = tagSet("tag1");

        Operation patchOp = Operation.createPatch(host, epzLink).setBody(patchState).forceRemote();
        Operation returnedOp = ((CompletableFuture<Operation>) host.sendWithDeferredResult(patchOp)
                .toCompletionStage()).get(60, TimeUnit.SECONDS);
        assertEquals(Operation.STATUS_CODE_NOT_MODIFIED, returnedOp.getStatusCode());
    }

    @Test
    public void testPatchNoTags() throws Throwable {
        // create a non-elastic RP
        ResourcePoolState rp = createRp();
        assertEquals(EnumSet.noneOf(ResourcePoolProperty.class), rp.properties);
        assertTrue(isNonElasticQuery(rp.query));

        // create EPZ for the RP
        String epzLink = createEpz(rp.documentSelfLink, "tag1", "tag2").documentSelfLink;

        // verify RP is now elastic
        rp = getDocument(ResourcePoolState.class, rp.documentSelfLink);
        assertEquals(EnumSet.of(ResourcePoolProperty.ELASTIC), rp.properties);
        assertFalse(isNonElasticQuery(rp.query));

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
        assertTrue(isNonElasticQuery(rp.query));
    }

    @SuppressWarnings("unused")
    @Test
    public void testElasticQuery() throws Throwable {
        ResourcePoolState containerRp = createRp(null, Arrays.asList("A", "B"));

        createEpz(containerRp.documentSelfLink, "tag1", "tag2");

        containerRp = getDocument(ResourcePoolState.class, containerRp.documentSelfLink);

        List<ComputeState> matchingContainerHosts = Arrays.asList(
                createCompute(ComputeType.VM_GUEST, null, null, Arrays.asList("tag1", "tag2"),
                        Arrays.asList("A", "B")),
                createCompute(ComputeType.VM_GUEST, null, containerRp.documentSelfLink, null,
                        Arrays.asList("A", "B")),
                createCompute(ComputeType.VM_GUEST, null, null, Arrays.asList("tag1", "tag2", "tag3"),
                        Arrays.asList("A", "B")));
        List<ComputeState> notMatchingContainerHosts = Arrays.asList(
                createCompute(ComputeType.VM_GUEST, null, null, Arrays.asList("tag1"),
                        Arrays.asList("A", "B")),
                createCompute(ComputeType.VM_GUEST, null, null, Arrays.asList("tag1", "tag2"),
                        Arrays.asList("X", "Y", "Z")));

        assertEqualComputes(executeRpQuery(containerRp), matchingContainerHosts);
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
        return createRp(null, null);
    }

    private ResourcePoolState createRp(String endpointLink,List<String> tenantLinks) throws Throwable {
        ResourcePoolState initialState = new ResourcePoolState();
        initialState.name = "rp-1";
        initialState.tenantLinks = correctTenantPrefixes(tenantLinks);
        return doPost(initialState, ResourcePoolService.FACTORY_LINK);
    }

    private ComputeState createCompute(ComputeType type, String endpointLink, String rpLink, List<String> tagLinks,
            List<String> tenantLinks) throws Throwable {
        ComputeState state = new ComputeState();
        state.name = UUID.randomUUID().toString();
        state.descriptionLink = "desc-link";
        state.type = type;
        state.endpointLink = endpointLink;
        state.resourcePoolLink = rpLink;
        state.tagLinks = tagLinks != null ? new HashSet<>(tagLinks) : null;
        state.tenantLinks = correctTenantPrefixes(tenantLinks);
        return doPost(state, ComputeService.FACTORY_LINK);
    }

    private List<String> executeRpQuery(ResourcePoolState rp) {
        return QueryTemplate.waitToComplete(
                new QueryByPages<>(this.host, rp.query, ComputeState.class, rp.tenantLinks)
                        .collectLinks(Collectors.toList()));
    }

    private void assertEqualComputes(List<String> actualLinks, List<ComputeState> expectedComputes) {
        List<String> expectedLinks = expectedComputes.stream().map(cs -> cs.documentSelfLink)
                .collect(Collectors.toList());
        for (String link : actualLinks) {
            assertTrue(expectedLinks.remove(link));
        }
        assertEquals(0, expectedLinks.size());
    }

    private List<String> correctTenantPrefixes(List<String> tenantLinks) {
        if (tenantLinks == null) {
            return null;
        }
        List<String> result = new ArrayList<>(tenantLinks.size());
        if (tenantLinks.size() > 0) {
            result.add(MultiTenantDocument.TENANTS_PREFIX + "/" + tenantLinks.get(0));
        }
        if (tenantLinks.size() > 1) {
            result.add(MultiTenantDocument.GROUP_IDENTIFIER + "/" + tenantLinks.get(1));
        }
        if (tenantLinks.size() > 2) {
            result.add(MultiTenantDocument.USERS_PREFIX + "/" + tenantLinks.get(2));
        }
        return result;
    }

    private static Set<String> tagSet(String... tagLinks) {
        Set<String> result = new HashSet<>();
        for (String tagLink : tagLinks) {
            result.add(tagLink);
        }
        return result;
    }

    private static boolean isNonElasticQuery(Query query) {
        return query.booleanClauses.size() == 2 &&
                query.booleanClauses.get(0).booleanClauses == null &&
                query.booleanClauses.get(1).booleanClauses == null;
    }
}
