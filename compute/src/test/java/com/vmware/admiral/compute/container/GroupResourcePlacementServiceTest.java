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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementPoolState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.ResourcePlacementReservationRequest;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.NumericRange;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class GroupResourcePlacementServiceTest extends ComputeBaseTest {

    private List<ServiceDocument> documentsForDeletion;
    private ContainerDescription containerDescription;
    private URI requestReservationTaskURI;
    private ResourcePoolState resourcePool;
    private static final String TENANT = "/tenants/coke";
    private static final String BUSINESS_GROUP = "/coke/dev";
    private static long CONTAINER_MEMORY;

    @Before
    public void setUp() throws Throwable {
        documentsForDeletion = new ArrayList<>();
        waitForServiceAvailability(GroupResourcePlacementService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        requestReservationTaskURI = UriUtils.buildUri(host,
                ManagementUriParts.REQUEST_RESERVATION_TASKS);
        setPrivateField(VerificationHost.class.getDeclaredField("referer"), host,
                requestReservationTaskURI);

        containerDescription = createAndStoreContainerDescription("test-link");
        resourcePool = createResourcePool();
        CONTAINER_MEMORY = ContainerDescriptionService.getContainerMinMemoryLimit();
    }

    @After
    public void tearDown() throws Throwable {
        for (ServiceDocument doc : documentsForDeletion) {
            try {
                delete(doc.documentSelfLink);
            } catch (Throwable e) {
                host.log("Exception during cleanup for: " + doc.documentSelfLink);
            }
        }
    }

    @Test
    public void testGroupPlacementResourcePoolValidation() throws Throwable {
        Set<String> linksToDelete = new HashSet<>();

        ResourcePoolState resourcePool1 = createResourcePool("resourcePool1", MIN_MEMORY * 2 + 1, 1000L);
        linksToDelete.add(resourcePool1.documentSelfLink);

        // Try to create a placement with more resources than the resource pool
        boolean expectFailure = true;
        // moreMemoryThanRP
        createAndStoreGroupResourcePlacement("moreMemoryThanRP", MIN_MEMORY * 2 + 2, 1000L, 0, 0,
                resourcePool1.documentSelfLink, expectFailure);

        // Create some placements to fill up the resource pool
        expectFailure = false;
        GroupResourcePlacementState firstPlacement = createAndStoreGroupResourcePlacement(
                "firstPlacement",
                MIN_MEMORY, 200L, 0, 0, resourcePool1.documentSelfLink, expectFailure);
        linksToDelete.add(firstPlacement.documentSelfLink);

        expectFailure = false;
        GroupResourcePlacementState secondPlacement = createAndStoreGroupResourcePlacement(
                "secondPlacement",
                MIN_MEMORY, 200L, 0, 0, resourcePool1.documentSelfLink, expectFailure);
        linksToDelete.add(secondPlacement.documentSelfLink);

        // The remaining resources in the RP shouldn't be enough for these
        expectFailure = true;
        // moreMemoryThanWhatsLeft
        createAndStoreGroupResourcePlacement("moreMemoryThanWhatsLeft",
                MIN_MEMORY, 200L, 0, 0, resourcePool1.documentSelfLink, expectFailure);

        linksToDelete.forEach(link -> {
            try {
                doDelete(UriUtils.buildUri(host, link), false);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        });
    }

    @Test
    public void testGroupPlacementValidation() throws Throwable {
        createAndStoreGroupResourcePlacement("negative-memory-limit", -1L, 0L, 0, 0,
                resourcePool.documentSelfLink, true /* expectFailure */);

        createAndStoreGroupResourcePlacement("negative-cpu-shares", 0L, 0L, 0, -1,
                resourcePool.documentSelfLink, true /* expectFailure */);

        createAndStoreGroupResourcePlacement("negative-priority-shares", 0L, 0L, -1, 0,
                resourcePool.documentSelfLink, true /* expectFailure */);
    }

    @Test
    public void testGroupResourcePlacementService() throws Throwable {
        verifyService(
                FactoryService.create(GroupResourcePlacementService.class),
                GroupResourcePlacementState.class,
                (prefix, index) -> {
                    GroupResourcePlacementState reservationState = new GroupResourcePlacementState();
                    reservationState.name = prefix + "reservation-test";
                    reservationState.tenantLinks = Collections.singletonList("testGroup");
                    reservationState.resourcePoolLink = resourcePool.documentSelfLink;
                    reservationState.maxNumberInstances = 10;
                    reservationState.customProperties = new HashMap<>();

                    return reservationState;
                },
                (prefix, serviceDocument) -> {
                    GroupResourcePlacementState reservationState = (GroupResourcePlacementState) serviceDocument;
                    assertTrue(reservationState.name.startsWith(prefix + "reservation-test"));
                    assertEquals(Collections.singletonList("testGroup"),
                            reservationState.tenantLinks);
                    assertEquals(resourcePool.documentSelfLink, reservationState.resourcePoolLink);
                    assertEquals(10, reservationState.maxNumberInstances);
                    assertEquals(10, reservationState.availableInstancesCount);
                    assertEquals(0, reservationState.allocatedInstancesCount);
                });
    }

    @Test
    public void testGroupResourcePlacementServiceTenantAndGroup() throws Throwable {
        verifyService(
                FactoryService.create(GroupResourcePlacementService.class),
                GroupResourcePlacementState.class,
                (prefix, index) -> {
                    List<String> tenantAndGroup = new LinkedList<String>();
                    tenantAndGroup.add(TENANT);
                    tenantAndGroup.add(BUSINESS_GROUP);
                    GroupResourcePlacementState reservationState = new GroupResourcePlacementState();
                    reservationState.name = prefix + "reservation-test";
                    reservationState.tenantLinks = tenantAndGroup;
                    reservationState.resourcePoolLink = resourcePool.documentSelfLink;
                    reservationState.maxNumberInstances = 10;
                    reservationState.customProperties = new HashMap<>();

                    return reservationState;
                },
                (prefix, serviceDocument) -> {
                    GroupResourcePlacementState reservationState = (GroupResourcePlacementState) serviceDocument;
                    assertEquals(TENANT, reservationState.tenantLinks.get(0));
                    assertEquals(BUSINESS_GROUP, reservationState.tenantLinks.get(1));
                });
    }

    @Test
    public void testGetGroupResourcePlacementState() throws Throwable {
        GroupResourcePlacementState placementState = new GroupResourcePlacementState();
        placementState.name = "reservation-test";
        placementState.tenantLinks = Collections.singletonList("testGroup");
        placementState.maxNumberInstances = 10;
        placementState.resourcePoolLink = resourcePool.documentSelfLink;

        GroupResourcePlacementState outPlacementState = doPost(placementState,
                GroupResourcePlacementService.FACTORY_LINK);

        GroupResourcePlacementState[] result = new GroupResourcePlacementState[] { null };
        Operation getGroupPlacement = Operation.createGet(
                UriUtils.buildUri(host, outPlacementState.documentSelfLink))
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.log("Can't get reservation state.", Utils.toString(e));
                                host.failIteration(e);
                                return;
                            } else {
                                GroupResourcePlacementState rsrvState = o
                                        .getBody(GroupResourcePlacementState.class);
                                result[0] = rsrvState;
                                host.completeIteration();
                            }
                        });

        host.testStart(1);
        host.send(getGroupPlacement);
        host.testWait();

        GroupResourcePlacementState rsrsPlacementState = result[0];
        assertNotNull(rsrsPlacementState);
        assertEquals(placementState.name, rsrsPlacementState.name);
        assertEquals(placementState.tenantLinks, rsrsPlacementState.tenantLinks);
        assertEquals(placementState.resourcePoolLink,
                rsrsPlacementState.resourcePoolLink);
        assertEquals(placementState.maxNumberInstances, rsrsPlacementState.maxNumberInstances);
        assertEquals(placementState.maxNumberInstances, rsrsPlacementState.availableInstancesCount);
        assertEquals(placementState.allocatedInstancesCount, 0);

        Operation getGroupPlacementExpandedResourcePool = Operation.createGet(
                GroupResourcePlacementPoolState.buildUri(UriUtils.buildUri(host,
                        outPlacementState.documentSelfLink)))
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.log("Can't get reservation state.", Utils.toString(e));
                                host.failIteration(e);
                                return;
                            } else {
                                GroupResourcePlacementPoolState outState = o
                                        .getBody(GroupResourcePlacementPoolState.class);
                                result[0] = outState;
                                host.completeIteration();
                            }
                        });

        host.testStart(1);
        host.send(getGroupPlacementExpandedResourcePool);
        host.testWait();

        GroupResourcePlacementPoolState poolState = (GroupResourcePlacementPoolState) result[0];
        assertNotNull(poolState);
        assertEquals(outPlacementState.name, poolState.name);
        assertEquals(outPlacementState.tenantLinks, poolState.tenantLinks);
        assertEquals(resourcePool.documentSelfLink, poolState.resourcePoolLink);
        assertEquals(outPlacementState.maxNumberInstances, poolState.maxNumberInstances);
        assertEquals(outPlacementState.availableInstancesCount, poolState.availableInstancesCount);
        assertEquals(outPlacementState.allocatedInstancesCount, poolState.allocatedInstancesCount);
        assertNotNull(poolState.resourcePool);
        assertEquals(resourcePool.id, poolState.resourcePool.id);
    }

    @Test
    public void testGroupResourcePlacementQueryEmptyTenantLinks() throws Throwable {

        GroupResourcePlacementState state = new GroupResourcePlacementState();
        state.name = "reservation-test";
        state.tenantLinks = Collections.singletonList("testGroup");
        state.maxNumberInstances = 10;
        state.resourcePoolLink = resourcePool.documentSelfLink;
        state.documentSelfLink = UUID.randomUUID().toString();

        doPost(state, GroupResourcePlacementService.FACTORY_LINK);

        // match on group property:
        QueryTask q = QueryUtil.buildQuery(GroupResourcePlacementState.class, false);
        q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;

        /**
         * When new policy is created it sets Project id in tenantLinks. In ReservationTaskService
         * we search for available GroupResourcePlacementStates, passing a query clause for
         * tenantLinks which are retrieved from ReservationTaskService itself. In stand alone mode
         * they are equal to null - means only global (default) placements should be returned as
         * result of query. That's why if default placement is missing, the query doesn't find any
         * results. This proves that tenant clause should be added only if
         * ReservationTaskService.tenantLinks are not null.
         */
        Query tenantLinksQuery = QueryUtil.addTenantAndGroupClause(null);
        q.querySpec.query.addBooleanClause(tenantLinksQuery);

        // match on available number of instances:
        QueryTask.Query numOfInstancesClause = new QueryTask.Query();

        QueryTask.Query moreInstancesThanRequired = new QueryTask.Query()
                .setTermPropertyName(
                        GroupResourcePlacementState.FIELD_NAME_AVAILABLE_INSTANCES_COUNT)
                .setNumericRange(NumericRange.createLongRange(Long.valueOf(10),
                        Long.MAX_VALUE, true, false))
                .setTermMatchType(MatchType.TERM);

        QueryTask.Query unlimitedInstances = new QueryTask.Query()
                .setTermPropertyName(GroupResourcePlacementState.FIELD_NAME_MAX_NUMBER_INSTANCES)
                .setNumericRange(NumericRange.createEqualRange(0L))
                .setTermMatchType(MatchType.TERM);

        moreInstancesThanRequired.occurance = Occurance.SHOULD_OCCUR;
        numOfInstancesClause.addBooleanClause(moreInstancesThanRequired);
        unlimitedInstances.occurance = Occurance.SHOULD_OCCUR;
        numOfInstancesClause.addBooleanClause(unlimitedInstances);
        numOfInstancesClause.occurance = Occurance.MUST_OCCUR;

        q.querySpec.query.addBooleanClause(numOfInstancesClause);

        List<GroupResourcePlacementState> placements = new ArrayList<>();

        QueryUtil.addExpandOption(q);

        ServiceDocumentQuery<GroupResourcePlacementState> query = new ServiceDocumentQuery<>(
                host,
                GroupResourcePlacementState.class);

        host.testStart(1);
        query.query(
                q,
                (r) -> {
                    if (r.hasException()) {
                        host.log("Exception while quering for placements:",
                                Utils.toString(r.getException()));
                        host.failIteration(r.getException());
                    } else if (r.hasResult()) {
                        placements.add(r.getResult());
                    } else {
                        // Global placement (empty tenantLinks) should exists in list.
                        if (placements.isEmpty()) {
                            host.log(Level.SEVERE, "No available group placements");
                            host.failIteration(
                                    new RuntimeException("No available group placements"));
                        }
                        host.completeIteration();
                    }
                });
        host.testWait();

        assertEquals(placements.size(), 1);

        // Query retrieves only global placement, because of tenantLinks clause ( tenantLinks=null )
        assertTrue(!placements.get(0).documentSelfLink.equals(state.documentSelfLink));

    }

    @Test
    public void testResourcePlacementReservationRequest() throws Throwable {
        GroupResourcePlacementState placementState = createAndStoreGroupResourcePlacement();
        String descLink = containerDescription.documentSelfLink;
        int count = 8;
        boolean expectFailure = false;

        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState,
                expectFailure);
        assertEquals(2, placementState.availableInstancesCount);
        assertEquals(count, placementState.allocatedInstancesCount);
        assertEquals(1, placementState.resourceQuotaPerResourceDesc.size());
        assertEquals(count, placementState.resourceQuotaPerResourceDesc.get(descLink).longValue());

        // release resource placements:
        count = -5;
        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState,
                expectFailure);
        assertEquals(7, placementState.availableInstancesCount);
        assertEquals(3, placementState.allocatedInstancesCount);
        assertEquals(1, placementState.resourceQuotaPerResourceDesc.size());
        assertEquals(3, placementState.resourceQuotaPerResourceDesc.get(descLink).longValue());

        // try to release resource placements more than max (success with log warning):
        count = (int) -(placementState.maxNumberInstances - placementState.availableInstancesCount
                + 1);
        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState,
                expectFailure);

        // releasing placements within max but more than reserved per desc:
        descLink = createAndStoreContainerDescription("new-desc").documentSelfLink;
        count = 4;
        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState,
                expectFailure);
        doDelete(UriUtils.buildUri(host, descLink), false);

        // in total, the requested placements to be released is ok, but not per desc:
        count = -(count + 1);
        assertTrue(placementState.maxNumberInstances
                - placementState.availableInstancesCount > -count);
        expectFailure = true;
        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState,
                expectFailure);

        // releasing placements within max but no previous reservation for desc:
        descLink = createAndStoreContainerDescription("non-reserved-desc").documentSelfLink;
        count = -1;
        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState,
                expectFailure);

        // release what's left of the provisioned resources
        releasePlacement(placementState);

        doDelete(UriUtils.buildUri(host, descLink), false);
        doDelete(UriUtils.buildUri(host, placementState.documentSelfLink), false);

    }

    @Test
    public void testMemoryPlacementReservationRequest() throws Throwable {
        GroupResourcePlacementState placementState = createAndStoreGroupResourcePlacement();
        String descLink = containerDescription.documentSelfLink;
        int count = 8;

        boolean expectFailure = false;

        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState,
                expectFailure);
        assertEquals(CONTAINER_MEMORY / 2, placementState.availableMemory);
        assertEquals(1, placementState.resourceQuotaPerResourceDesc.size());
        assertEquals(count, placementState.resourceQuotaPerResourceDesc.get(descLink).longValue());

        // release resource placements:
        count = -5;
        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState,
                expectFailure);
        assertEquals(7, placementState.availableInstancesCount);
        assertEquals(3, placementState.allocatedInstancesCount);
        assertEquals(1, placementState.resourceQuotaPerResourceDesc.size());
        assertEquals(3 * CONTAINER_MEMORY,
                placementState.memoryQuotaPerResourceDesc.get(descLink).longValue());

        // try to release resource placements more than max (success with log warning):
        count = (int) -(placementState.maxNumberInstances - placementState.availableInstancesCount
                + 1);
        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState,
                expectFailure);

        // use the max number of resources. they should require more memory than the placement
        // provides
        count = 7;
        expectFailure = true;
        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState,
                expectFailure);

        // create groupResourcePlacement without memory limit
        GroupResourcePlacementState noLimitsGroupResourcePlacement = createAndStoreGroupResourcePlacement(
                "test", 0L, 0L, 0, 0, resourcePool.documentSelfLink, false);
        expectFailure = false;
        noLimitsGroupResourcePlacement = makeResourcePlacementReservationRequest(1, descLink,
                noLimitsGroupResourcePlacement,
                expectFailure);
        assertEquals(0, noLimitsGroupResourcePlacement.availableMemory);
        assertEquals(0, noLimitsGroupResourcePlacement.memoryLimit);
        assertEquals(9, noLimitsGroupResourcePlacement.availableInstancesCount);
        assertEquals(1, noLimitsGroupResourcePlacement.allocatedInstancesCount);

        ContainerDescriptionService.ContainerDescription noLimitsContainerDescription = createAndStoreContainerDescription(
                "no-limits", 0L);
        noLimitsGroupResourcePlacement = makeResourcePlacementReservationRequest(1,
                noLimitsContainerDescription.documentSelfLink, noLimitsGroupResourcePlacement,
                expectFailure);
        assertEquals(0, noLimitsGroupResourcePlacement.availableMemory);
        assertEquals(0, noLimitsGroupResourcePlacement.memoryLimit);
        assertEquals(8, noLimitsGroupResourcePlacement.availableInstancesCount);
        assertEquals(2, noLimitsGroupResourcePlacement.allocatedInstancesCount);

        // release what's left of the requested resources
        releasePlacement(placementState);
        releasePlacement(noLimitsGroupResourcePlacement);

        doDelete(UriUtils.buildUri(host, descLink), false);
        doDelete(UriUtils.buildUri(host, placementState.documentSelfLink), false);
        doDelete(UriUtils.buildUri(host, noLimitsContainerDescription.documentSelfLink), false);
        doDelete(UriUtils.buildUri(host, noLimitsGroupResourcePlacement.documentSelfLink), false);
    }

    private void releasePlacement(GroupResourcePlacementState placementState)
            throws Throwable {

        for (Entry<String, Long> entry : placementState.resourceQuotaPerResourceDesc.entrySet()) {
            makeResourcePlacementReservationRequest(-entry.getValue().intValue(), entry.getKey(),
                    placementState, false);
        }
    }

    @Test
    public void testMemoryPlacementPatchRequest() throws Throwable {
        GroupResourcePlacementState placementState = createAndStoreGroupResourcePlacement();
        String descLink = containerDescription.documentSelfLink;
        int count = 8;

        boolean expectFailure = false;

        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState,
                expectFailure);

        // Set the memory limit to something smaller than what's already reserved
        placementState.memoryLimit = 700;
        expectFailure = true;

        host.testStart(1);
        host.send(Operation
                .createPut(UriUtils.buildUri(host, placementState.documentSelfLink))
                .setBody(placementState)
                .setCompletion(expectFailure ? host.getExpectedFailureCompletion()
                        : host.getCompletion()));
        host.testWait("Asd", (int) TimeUnit.MINUTES.toSeconds(1));

        releasePlacement(placementState);

        doDelete(UriUtils.buildUri(host, placementState.documentSelfLink), false);
    }

    @Test
    public void testUpdateWhenNoActiveReservations() throws Throwable {
        GroupResourcePlacementState placementState = createAndStoreGroupResourcePlacement();
        String newName = "newName";
        int newMaxInstance = 7;
        String newResourcePoolLink = resourcePool.documentSelfLink;
        int newPriority = 23;
        long newMemoryLimit = MIN_MEMORY;
        long newStorageLimit = 5789L;
        int newCpuShares = 45;

        placementState.name = newName;
        placementState.maxNumberInstances = newMaxInstance;
        placementState.priority = newPriority;
        placementState.resourcePoolLink = newResourcePoolLink;
        placementState.memoryLimit = newMemoryLimit;
        placementState.storageLimit = newStorageLimit;
        placementState.cpuShares = newCpuShares;

        doOperation(placementState, UriUtils.buildUri(host, placementState.documentSelfLink),
                false, Action.PUT);

        placementState = getDocument(GroupResourcePlacementState.class,
                placementState.documentSelfLink);

        assertEquals(newName, placementState.name);
        assertEquals(newMaxInstance, placementState.maxNumberInstances);
        assertEquals(newPriority, placementState.priority);
        assertEquals(newResourcePoolLink, placementState.resourcePoolLink);
        assertEquals(newMemoryLimit, placementState.memoryLimit);
        assertEquals(newStorageLimit, placementState.storageLimit);
        assertEquals(newCpuShares, placementState.cpuShares);

        doDelete(UriUtils.buildUri(host, placementState.documentSelfLink), false);
    }

    @Test
    public void testUpdateWhenActiveReservations() throws Throwable {
        GroupResourcePlacementState placementState = createAndStoreGroupResourcePlacement();

        int count = (int) placementState.maxNumberInstances
                - (int) placementState.maxNumberInstances / 2;
        placementState = makeResourcePlacementReservationRequest(placementState, count);

        String newName = "newName";
        int newPriority = 23;
        // make sure the new maxInstances is more the currently reserved instances
        int newMaxInstance = count + 1;

        placementState.name = newName;
        placementState.priority = newPriority;
        placementState.maxNumberInstances = newMaxInstance;

        boolean expectedFailure = false;
        doOperation(placementState, UriUtils.buildUri(host, placementState.documentSelfLink),
                expectedFailure, Action.PUT);

        placementState = getDocument(GroupResourcePlacementState.class,
                placementState.documentSelfLink);

        assertEquals(newName, placementState.name);
        assertEquals(newMaxInstance, placementState.maxNumberInstances);
        assertEquals(newPriority, placementState.priority);

        expectedFailure = true;
        // failure when the current maxInstance count less than the currently reserved instances
        newMaxInstance = count - 1;
        placementState.maxNumberInstances = newMaxInstance;
        try {
            doOperation(placementState, UriUtils.buildUri(host, placementState.documentSelfLink),
                    expectedFailure, Action.PUT);
            fail("expect maxNumberInstances validation error");
        } catch (LocalizableValidationException e) {
            // expected
        }

        placementState = getDocument(GroupResourcePlacementState.class,
                placementState.documentSelfLink);
        placementState.resourcePoolLink = createResourcePool("new", 0L, 0L).documentSelfLink;
        try {
            doOperation(placementState, UriUtils.buildUri(host, placementState.documentSelfLink),
                    expectedFailure, Action.PUT);
            fail("expect resourcePoolLink can't be changed validation error");
        } catch (LocalizableValidationException e) {
            // expected
        } finally {
            doDelete(UriUtils.buildUri(host, placementState.resourcePoolLink), false);
        }

        placementState = getDocument(GroupResourcePlacementState.class,
                placementState.documentSelfLink);
        placementState.storageLimit = 567L;
        try {
            doOperation(placementState, UriUtils.buildUri(host, placementState.documentSelfLink),
                    expectedFailure, Action.PUT);
            fail("expect storageLimit can't be changed validation error");
        } catch (LocalizableValidationException e) {
            // expect resourcePoolLink validation error
        }

        placementState = getDocument(GroupResourcePlacementState.class,
                placementState.documentSelfLink);
        placementState.cpuShares = 34;
        try {
            doOperation(placementState, UriUtils.buildUri(host, placementState.documentSelfLink),
                    expectedFailure, Action.PUT);
            fail("expect cpuShares can't be changed validation error");
        } catch (LocalizableValidationException e) {
            // expect resourcePoolLink validation error
        }

        releasePlacement(placementState);

        doDelete(UriUtils.buildUri(host, placementState.documentSelfLink), false);

    }

    @Test
    public void testDeleteWhenNoActiveReservation() throws Throwable {
        GroupResourcePlacementState placementState = createAndStoreGroupResourcePlacement();
        try {
            DeploymentProfileConfig.getInstance().setTest(false);
            doDelete(UriUtils.buildUri(host, placementState.documentSelfLink), false);
        } finally {
            DeploymentProfileConfig.getInstance().setTest(true);
        }

        ServiceDocumentQuery<GroupResourcePlacementState> query = new ServiceDocumentQuery<>(host,
                GroupResourcePlacementState.class);
        AtomicBoolean deleted = new AtomicBoolean();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        query.queryDocument(placementState.documentSelfLink, (r) -> {
            if (!r.hasException() && !r.hasResult()) {
                deleted.set(true);
                countDownLatch.countDown();
            }
        });
        countDownLatch.await();
        assertTrue(deleted.get());
        documentsForDeletion.remove(placementState);
    }

    @Test
    public void testDeleteWhenActiveReservation() throws Throwable {
        GroupResourcePlacementState placementState = createAndStoreGroupResourcePlacement();
        placementState = makeResourcePlacementReservationRequest(placementState, 5);

        boolean expectedFailure = true;
        try {
            DeploymentProfileConfig.getInstance().setTest(false);
            doDelete(UriUtils.buildUri(host, placementState.documentSelfLink), expectedFailure);
            fail("expect validation error during deletion");
        } catch (LocalizableValidationException e) {
            // expected
        } finally {
            DeploymentProfileConfig.getInstance().setTest(true);
        }
    }

    @Test
    public void testProvisioningWithUnlimitedPlacement() throws Throwable {
        GroupResourcePlacementState unlimitedInstancesPlacement = createPlacement(
                "placement-unlimited-test",
                8 * CONTAINER_MEMORY + CONTAINER_MEMORY / 2,
                0L, 0, 0, resourcePool.documentSelfLink, 0);
        assertEquals(0, unlimitedInstancesPlacement.maxNumberInstances);
        assertEquals(0, unlimitedInstancesPlacement.availableInstancesCount);
        assertEquals(0, unlimitedInstancesPlacement.allocatedInstancesCount);

        GroupResourcePlacementState savedPlacement = savePlacement(unlimitedInstancesPlacement,
                false);
        assertEquals(0, savedPlacement.maxNumberInstances);
        assertEquals(0, savedPlacement.availableInstancesCount);
        assertEquals(0, savedPlacement.allocatedInstancesCount);

        GroupResourcePlacementState placementStateAfterProvisioning = makeResourcePlacementReservationRequest(
                savedPlacement, 1);
        assertEquals(0, placementStateAfterProvisioning.maxNumberInstances);
        assertEquals(0, placementStateAfterProvisioning.availableInstancesCount);
        assertEquals(1, placementStateAfterProvisioning.allocatedInstancesCount);
    }

    private GroupResourcePlacementState makeResourcePlacementReservationRequest(
            GroupResourcePlacementState placementState, int count) throws Throwable {
        return makeResourcePlacementReservationRequest(count, containerDescription.documentSelfLink,
                placementState, false);
    }

    private GroupResourcePlacementState makeResourcePlacementReservationRequest(int count,
            String descLink, GroupResourcePlacementState placementState, boolean expectFailure)
            throws Throwable {
        ResourcePlacementReservationRequest rsrvRequest = new ResourcePlacementReservationRequest();
        rsrvRequest.resourceCount = count;
        rsrvRequest.resourceDescriptionLink = descLink;
        rsrvRequest.referer = requestReservationTaskURI.getPath();

        host.testStart(1);
        host.send(Operation
                .createPatch(UriUtils.buildUri(host, placementState.documentSelfLink))
                .setBody(rsrvRequest)
                .setCompletion(expectFailure ? host.getExpectedFailureCompletion()
                        : host.getCompletion()));
        host.testWait();

        return getDocument(GroupResourcePlacementState.class, placementState.documentSelfLink);
    }

    private ContainerDescriptionService.ContainerDescription createAndStoreContainerDescription(
            String link) throws Throwable {
        return createAndStoreContainerDescription(link, CONTAINER_MEMORY);
    }

    private ContainerDescription createAndStoreContainerDescription(
            String link, Long memoryLimit) throws Throwable {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.documentSelfLink = link + UUID.randomUUID().toString();
        containerDesc.name = "name";
        containerDesc.memoryLimit = memoryLimit;
        containerDesc.image = "image";

        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(containerDesc);
        documentsForDeletion.add(containerDesc);
        return containerDesc;
    }

    private GroupResourcePlacementState createAndStoreGroupResourcePlacement() throws Throwable {
        return createAndStoreGroupResourcePlacement("reservation-test",
                8 * CONTAINER_MEMORY + CONTAINER_MEMORY / 2,
                0L, 0, 0, resourcePool.documentSelfLink, false);
    }

    private GroupResourcePlacementState createAndStoreGroupResourcePlacement(String link,
            Long memoryLimit, Long storageLimit, Integer priority, Integer cpuShares,
            String resourcePoolLink, boolean expectFailure) throws Throwable {
        // create test placement
        GroupResourcePlacementState placementState = createPlacement(link, memoryLimit,
                storageLimit, priority,
                cpuShares, resourcePoolLink, 10);
        // attempt saving the test placement
        return savePlacement(placementState, expectFailure);
    }

    private GroupResourcePlacementState savePlacement(GroupResourcePlacementState placementState,
            boolean expectFailure) throws Throwable {
        GroupResourcePlacementState[] result = new GroupResourcePlacementState[] { null };

        host.testStart(1);
        host.send(OperationUtil
                .createForcedPost(
                        UriUtils.buildUri(host, GroupResourcePlacementService.FACTORY_LINK))
                .setBody(placementState)
                .setCompletion(expectFailure ? host.getExpectedFailureCompletion()
                        : (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                                return;
                            }
                            result[0] = o.getBody(GroupResourcePlacementState.class);
                            host.completeIteration();
                        }));
        host.testWait();

        if (result[0] != null) {
            placementState = result[0];
            documentsForDeletion.add(placementState);
        }

        return placementState;
    }

    private GroupResourcePlacementState createPlacement(String link, Long memoryLimit,
            Long storageLimit, Integer priority, Integer cpuShares, String resourcePoolLink,
            long maxNumInstances) {
        GroupResourcePlacementState placementState = new GroupResourcePlacementState();
        placementState.name = link;
        placementState.documentSelfLink = link + "-" + UUID.randomUUID().toString();
        placementState.tenantLinks = Collections.singletonList("testGroup");
        placementState.maxNumberInstances = maxNumInstances;
        placementState.memoryLimit = memoryLimit;
        placementState.storageLimit = storageLimit;
        placementState.resourcePoolLink = resourcePoolLink;
        placementState.cpuShares = cpuShares;
        placementState.priority = priority;

        return placementState;
    }

    // TODO move these to admiral-common-test?
    private ResourcePoolState createResourcePool(String link, Long maxMemory, Long maxStorage)
            throws Throwable {
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.id = link + "-" + UUID.randomUUID().toString();
        poolState.name = poolState.id;
        poolState.documentSelfLink = poolState.id;
        poolState.maxCpuCount = 1600L;
        poolState.minCpuCount = 16L;
        poolState.currencyUnit = "Bitcoin";
        poolState.maxCpuCostPerMinute = 1.0;
        poolState.maxDiskCostPerMinute = 1.0;
        poolState.minMemoryBytes = maxMemory / 2;
        poolState.maxMemoryBytes = maxMemory;
        poolState.minDiskCapacityBytes = poolState.maxDiskCapacityBytes = maxStorage;
        ResourcePoolState outResPoolState = doPost(poolState, ResourcePoolService.FACTORY_LINK);
        assertNotNull(outResPoolState);
        documentsForDeletion.add(outResPoolState);
        return outResPoolState;
    }

    private ResourcePoolState createResourcePool() throws Throwable {
        return createResourcePool("test-resource-pool",
                1024L * 1024L * 1024L * 46L, 1024L * 1024L * 1024L * 1024L);
    }

}
