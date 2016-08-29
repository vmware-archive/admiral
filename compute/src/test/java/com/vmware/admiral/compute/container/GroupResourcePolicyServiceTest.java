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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyPoolState;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.ResourcePolicyReservationRequest;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;

public class GroupResourcePolicyServiceTest extends ComputeBaseTest {

    private List<ServiceDocument> documentsForDeletion;
    private ContainerDescription containerDescription;
    private ResourcePoolState resourcePool;
    private static final String TENANT = "/tenants/coke";
    private static final String BUSINESS_GROUP = "/coke/dev";
    private static long CONTAINER_MEMORY;

    @Before
    public void setUp() throws Throwable {
        documentsForDeletion = new ArrayList<>();
        waitForServiceAvailability(GroupResourcePolicyService.FACTORY_LINK);
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        URI requestReservationTaskURI = UriUtils.buildUri(host,
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
    public void testGroupPolicyResourcePoolValidation() throws Throwable {
        Set<String> linksToDelete = new HashSet<>();

        ResourcePoolState resourcePool1 = createResourcePool("resourcePool1", 1000L, 1000L);
        linksToDelete.add(resourcePool1.documentSelfLink);

        // Try to create a policy with more resources than the resource pool
        boolean expectFailure = true;
        // moreMemoryThanRP
        createAndStoreGroupResourcePolicy("moreMemoryThanRP", 2000L, 1000L, 0,
                resourcePool1.documentSelfLink, expectFailure);

        // Create some policies to fill up the resource pool
        expectFailure = false;
        GroupResourcePolicyState firstPolicy = createAndStoreGroupResourcePolicy(
                "firstPolicy",
                300L, 200L, 0, resourcePool1.documentSelfLink, expectFailure);
        linksToDelete.add(firstPolicy.documentSelfLink);

        expectFailure = false;
        GroupResourcePolicyState secondPolicy = createAndStoreGroupResourcePolicy(
                "secondPolicy",
                300L, 200L, 0, resourcePool1.documentSelfLink, expectFailure);
        linksToDelete.add(secondPolicy.documentSelfLink);

        // The remaining resources in the RP shouldn't be enough for these
        expectFailure = true;
        // moreMemoryThanWhatsLeft
        createAndStoreGroupResourcePolicy("moreMemoryThanWhatsLeft",
                500L, 200L, 0, resourcePool1.documentSelfLink, expectFailure);

        linksToDelete.forEach(link -> {
            try {
                doDelete(UriUtils.buildUri(host, link), false);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        });
    }

    @Test
    public void testGroupPolicyValidation() throws Throwable {
        createAndStoreGroupResourcePolicy("negative-memory-limit", -1L, 0L, 0,
                resourcePool.documentSelfLink, true /* expectFailure */);

        createAndStoreGroupResourcePolicy("negative-cpu-shares", 0L, 0L, -1,
                resourcePool.documentSelfLink, true /* expectFailure */);
    }

    @Test
    public void testGroupResourcePolicyService() throws Throwable {
        verifyService(
                FactoryService.create(GroupResourcePolicyService.class),
                GroupResourcePolicyState.class,
                (prefix, index) -> {
                    GroupResourcePolicyState reservationState = new GroupResourcePolicyState();
                    reservationState.name = prefix + "reservation-test";
                    reservationState.tenantLinks = Collections.singletonList("testGroup");
                    reservationState.resourcePoolLink = resourcePool.documentSelfLink;
                    reservationState.maxNumberInstances = 10;
                    reservationState.customProperties = new HashMap<>();

                    return reservationState;
                },
                (prefix, serviceDocument) -> {
                    GroupResourcePolicyState reservationState = (GroupResourcePolicyState) serviceDocument;
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
    public void testGroupResourcePolicyServiceTenantAndGroup() throws Throwable {
        verifyService(
                FactoryService.create(GroupResourcePolicyService.class),
                GroupResourcePolicyState.class,
                (prefix, index) -> {
                    List<String> tenantAndGroup = new LinkedList<String>();
                    tenantAndGroup.add(TENANT);
                    tenantAndGroup.add(BUSINESS_GROUP);
                    GroupResourcePolicyState reservationState = new GroupResourcePolicyState();
                    reservationState.name = prefix + "reservation-test";
                    reservationState.tenantLinks = tenantAndGroup;
                    reservationState.resourcePoolLink = resourcePool.documentSelfLink;
                    reservationState.maxNumberInstances = 10;
                    reservationState.customProperties = new HashMap<>();

                    return reservationState;
                },
                (prefix, serviceDocument) -> {
                    GroupResourcePolicyState reservationState = (GroupResourcePolicyState) serviceDocument;
                    assertEquals(TENANT, reservationState.tenantLinks.get(0));
                    assertEquals(BUSINESS_GROUP, reservationState.tenantLinks.get(1));
                });
    }

    @Test
    public void testGetGroupResourcePolicyState() throws Throwable {
        GroupResourcePolicyState policyState = new GroupResourcePolicyState();
        policyState.name = "reservation-test";
        policyState.tenantLinks = Collections.singletonList("testGroup");
        policyState.maxNumberInstances = 10;
        policyState.resourcePoolLink = resourcePool.documentSelfLink;

        GroupResourcePolicyState outPolicyState = doPost(policyState,
                GroupResourcePolicyService.FACTORY_LINK);

        GroupResourcePolicyState[] result = new GroupResourcePolicyState[] { null };
        Operation getGroupPolicy = Operation.createGet(
                UriUtils.buildUri(host, outPolicyState.documentSelfLink))
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.log("Can't get reservation state.", Utils.toString(e));
                                host.failIteration(e);
                                return;
                            } else {
                                GroupResourcePolicyState rsrvState = o
                                        .getBody(GroupResourcePolicyState.class);
                                result[0] = rsrvState;
                                host.completeIteration();
                            }
                        });

        host.testStart(1);
        host.send(getGroupPolicy);
        host.testWait();

        GroupResourcePolicyState rsrsPolicyState = result[0];
        assertNotNull(rsrsPolicyState);
        assertEquals(policyState.name, rsrsPolicyState.name);
        assertEquals(policyState.tenantLinks, rsrsPolicyState.tenantLinks);
        assertEquals(policyState.resourcePoolLink,
                rsrsPolicyState.resourcePoolLink);
        assertEquals(policyState.maxNumberInstances, rsrsPolicyState.maxNumberInstances);
        assertEquals(policyState.maxNumberInstances, rsrsPolicyState.availableInstancesCount);
        assertEquals(policyState.allocatedInstancesCount, 0);

        Operation getGroupPolicyExpandedResourcePool = Operation.createGet(
                GroupResourcePolicyPoolState.buildUri(UriUtils.buildUri(host,
                        outPolicyState.documentSelfLink)))
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.log("Can't get reservation state.", Utils.toString(e));
                                host.failIteration(e);
                                return;
                            } else {
                                GroupResourcePolicyPoolState outState = o
                                        .getBody(GroupResourcePolicyPoolState.class);
                                result[0] = outState;
                                host.completeIteration();
                            }
                        });

        host.testStart(1);
        host.send(getGroupPolicyExpandedResourcePool);
        host.testWait();

        GroupResourcePolicyPoolState poolState = (GroupResourcePolicyPoolState) result[0];
        assertNotNull(poolState);
        assertEquals(outPolicyState.name, poolState.name);
        assertEquals(outPolicyState.tenantLinks, poolState.tenantLinks);
        assertEquals(resourcePool.documentSelfLink, poolState.resourcePoolLink);
        assertEquals(outPolicyState.maxNumberInstances, poolState.maxNumberInstances);
        assertEquals(outPolicyState.availableInstancesCount, poolState.availableInstancesCount);
        assertEquals(outPolicyState.allocatedInstancesCount, poolState.allocatedInstancesCount);
        assertNotNull(poolState.resourcePool);
        assertEquals(resourcePool.id, poolState.resourcePool.id);
    }

    @Test
    public void testResourcePolicyReservationRequest() throws Throwable {
        GroupResourcePolicyState policyState = createAndStoreGroupResourcePolicy();
        String descLink = containerDescription.documentSelfLink;
        int count = 8;
        boolean expectFailure = false;

        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState,
                expectFailure);
        assertEquals(2, policyState.availableInstancesCount);
        assertEquals(count, policyState.allocatedInstancesCount);
        assertEquals(1, policyState.resourceQuotaPerResourceDesc.size());
        assertEquals(count, policyState.resourceQuotaPerResourceDesc.get(descLink).longValue());

        // release resource policies:
        count = -5;
        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState,
                expectFailure);
        assertEquals(7, policyState.availableInstancesCount);
        assertEquals(3, policyState.allocatedInstancesCount);
        assertEquals(1, policyState.resourceQuotaPerResourceDesc.size());
        assertEquals(3, policyState.resourceQuotaPerResourceDesc.get(descLink).longValue());

        // try to release resource policies more than max (success with log warning):
        count = (int) -(policyState.maxNumberInstances - policyState.availableInstancesCount + 1);
        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState,
                expectFailure);

        // releasing policies within max but more than reserved per desc:
        descLink = createAndStoreContainerDescription("new-desc").documentSelfLink;
        count = 4;
        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState,
                expectFailure);
        doDelete(UriUtils.buildUri(host, descLink), false);

        // in total, the requested policies to be released is ok, but not per desc:
        count = -(count + 1);
        assertTrue(policyState.maxNumberInstances - policyState.availableInstancesCount > -count);
        expectFailure = true;
        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState,
                expectFailure);

        // releasing policies within max but no previous reservation for desc:
        descLink = createAndStoreContainerDescription("non-reserved-desc").documentSelfLink;
        count = -1;
        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState,
                expectFailure);

        // release what's left of the provisioned resources
        releasePolicy(policyState);

        doDelete(UriUtils.buildUri(host, descLink), false);
        doDelete(UriUtils.buildUri(host, policyState.documentSelfLink), false);

    }

    @Test
    public void testMemoryPolicyReservationRequest() throws Throwable {
        GroupResourcePolicyState policyState = createAndStoreGroupResourcePolicy();
        String descLink = containerDescription.documentSelfLink;
        int count = 8;

        boolean expectFailure = false;

        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState,
                expectFailure);
        assertEquals(CONTAINER_MEMORY / 2, policyState.availableMemory);
        assertEquals(1, policyState.resourceQuotaPerResourceDesc.size());
        assertEquals(count, policyState.resourceQuotaPerResourceDesc.get(descLink).longValue());

        // release resource policies:
        count = -5;
        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState,
                expectFailure);
        assertEquals(7, policyState.availableInstancesCount);
        assertEquals(3, policyState.allocatedInstancesCount);
        assertEquals(1, policyState.resourceQuotaPerResourceDesc.size());
        assertEquals(3 * CONTAINER_MEMORY,
                policyState.memoryQuotaPerResourceDesc.get(descLink).longValue());

        // try to release resource policies more than max (success with log warning):
        count = (int) -(policyState.maxNumberInstances - policyState.availableInstancesCount + 1);
        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState,
                expectFailure);

        // use the max number of resources. they should require more memory than the policy provides
        count = 7;
        expectFailure = true;
        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState,
                expectFailure);

        // create groupResourcePolicy without memory limit
        GroupResourcePolicyState noLimitsGroupResourcePolicy = createAndStoreGroupResourcePolicy(
                "test", 0L, 0L, 0, resourcePool.documentSelfLink, false);
        expectFailure = false;
        noLimitsGroupResourcePolicy = makeResourcePolicyReservationRequest(1, descLink,
                noLimitsGroupResourcePolicy,
                expectFailure);
        assertEquals(0, noLimitsGroupResourcePolicy.availableMemory);
        assertEquals(0, noLimitsGroupResourcePolicy.memoryLimit);
        assertEquals(9, noLimitsGroupResourcePolicy.availableInstancesCount);
        assertEquals(1, noLimitsGroupResourcePolicy.allocatedInstancesCount);

        ContainerDescriptionService.ContainerDescription noLimitsContainerDescription = createAndStoreContainerDescription(
                "no-limits", 0L);
        noLimitsGroupResourcePolicy = makeResourcePolicyReservationRequest(1,
                noLimitsContainerDescription.documentSelfLink, noLimitsGroupResourcePolicy,
                expectFailure);
        assertEquals(0, noLimitsGroupResourcePolicy.availableMemory);
        assertEquals(0, noLimitsGroupResourcePolicy.memoryLimit);
        assertEquals(8, noLimitsGroupResourcePolicy.availableInstancesCount);
        assertEquals(2, noLimitsGroupResourcePolicy.allocatedInstancesCount);

        // release what's left of the requested resources
        releasePolicy(policyState);
        releasePolicy(noLimitsGroupResourcePolicy);

        doDelete(UriUtils.buildUri(host, descLink), false);
        doDelete(UriUtils.buildUri(host, policyState.documentSelfLink), false);
        doDelete(UriUtils.buildUri(host, noLimitsContainerDescription.documentSelfLink), false);
        doDelete(UriUtils.buildUri(host, noLimitsGroupResourcePolicy.documentSelfLink), false);
    }

    private void releasePolicy(GroupResourcePolicyState policyState)
            throws Throwable {

        for (Entry<String, Long> entry : policyState.resourceQuotaPerResourceDesc.entrySet()) {
            makeResourcePolicyReservationRequest(-entry.getValue().intValue(), entry.getKey(), policyState, false);
        }
    }

    @Test
    public void testMemoryPolicyPatchRequest() throws Throwable {
        GroupResourcePolicyState policyState = createAndStoreGroupResourcePolicy();
        String descLink = containerDescription.documentSelfLink;
        int count = 8;

        boolean expectFailure = false;

        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState,
                expectFailure);

        // Set the memory limit to something smaller than what's already reserved
        policyState.memoryLimit = 700;
        expectFailure = true;

        host.testStart(1);
        host.send(Operation
                .createPut(UriUtils.buildUri(host, policyState.documentSelfLink))
                .setBody(policyState)
                .setCompletion(expectFailure ? host.getExpectedFailureCompletion()
                        : host.getCompletion()));
        host.testWait("Asd", (int) TimeUnit.MINUTES.toSeconds(1));

        releasePolicy(policyState);

        doDelete(UriUtils.buildUri(host, policyState.documentSelfLink), false);
    }

    @Test
    public void testUpdateWhenNoActiveReservations() throws Throwable {
        GroupResourcePolicyState policyState = createAndStoreGroupResourcePolicy();
        String newName = "newName";
        int newMaxInstance = 7;
        String newResourcePoolLink = resourcePool.documentSelfLink;
        int newPriority = 23;
        long newMemoryLimit = 567L;
        long newStorageLimit = 5789L;
        int newCpuShares = 45;

        policyState.name = newName;
        policyState.maxNumberInstances = newMaxInstance;
        policyState.priority = newPriority;
        policyState.resourcePoolLink = newResourcePoolLink;
        policyState.memoryLimit = newMemoryLimit;
        policyState.storageLimit = newStorageLimit;
        policyState.cpuShares = newCpuShares;

        doOperation(policyState, UriUtils.buildUri(host, policyState.documentSelfLink),
                false, Action.PUT);

        policyState = getDocument(GroupResourcePolicyState.class, policyState.documentSelfLink);

        assertEquals(newName, policyState.name);
        assertEquals(newMaxInstance, policyState.maxNumberInstances);
        assertEquals(newPriority, policyState.priority);
        assertEquals(newResourcePoolLink, policyState.resourcePoolLink);
        assertEquals(newMemoryLimit, policyState.memoryLimit);
        assertEquals(newStorageLimit, policyState.storageLimit);
        assertEquals(newCpuShares, policyState.cpuShares);

        doDelete(UriUtils.buildUri(host, policyState.documentSelfLink), false);
    }

    @Test
    public void testUpdateWhenActiveReservations() throws Throwable {
        GroupResourcePolicyState policyState = createAndStoreGroupResourcePolicy();

        int count = (int) policyState.maxNumberInstances - (int) policyState.maxNumberInstances / 2;
        policyState = makeResourcePolicyReservationRequest(policyState, count);

        String newName = "newName";
        int newPriority = 23;
        // make sure the new maxInstances is more the currently reserved instances
        int newMaxInstance = count + 1;

        policyState.name = newName;
        policyState.priority = newPriority;
        policyState.maxNumberInstances = newMaxInstance;

        boolean expectedFailure = false;
        doOperation(policyState, UriUtils.buildUri(host, policyState.documentSelfLink),
                expectedFailure, Action.PUT);

        policyState = getDocument(GroupResourcePolicyState.class, policyState.documentSelfLink);

        assertEquals(newName, policyState.name);
        assertEquals(newMaxInstance, policyState.maxNumberInstances);
        assertEquals(newPriority, policyState.priority);

        expectedFailure = true;
        // failure when the current maxInstance count less than the currently reserved instances
        newMaxInstance = count - 1;
        policyState.maxNumberInstances = newMaxInstance;
        try {
            doOperation(policyState, UriUtils.buildUri(host, policyState.documentSelfLink),
                    expectedFailure, Action.PUT);
            fail("expect maxNumberInstances validation error");
        } catch (IllegalArgumentException e) {
            // expected
        }

        policyState = getDocument(GroupResourcePolicyState.class, policyState.documentSelfLink);
        policyState.resourcePoolLink = createResourcePool("new", 0L, 0L).documentSelfLink;
        try {
            doOperation(policyState, UriUtils.buildUri(host, policyState.documentSelfLink),
                    expectedFailure, Action.PUT);
            fail("expect resourcePoolLink can't be changed validation error");
        } catch (IllegalArgumentException e) {
            // expected
        } finally {
            doDelete(UriUtils.buildUri(host, policyState.resourcePoolLink), false);
        }

        policyState = getDocument(GroupResourcePolicyState.class, policyState.documentSelfLink);
        policyState.storageLimit = 567L;
        try {
            doOperation(policyState, UriUtils.buildUri(host, policyState.documentSelfLink),
                    expectedFailure, Action.PUT);
            fail("expect storageLimit can't be changed validation error");
        } catch (IllegalArgumentException e) {
            // expect resourcePoolLink validation error
        }

        policyState = getDocument(GroupResourcePolicyState.class, policyState.documentSelfLink);
        policyState.cpuShares = 34;
        try {
            doOperation(policyState, UriUtils.buildUri(host, policyState.documentSelfLink),
                    expectedFailure, Action.PUT);
            fail("expect cpuShares can't be changed validation error");
        } catch (IllegalArgumentException e) {
            // expect resourcePoolLink validation error
        }

        releasePolicy(policyState);

        doDelete(UriUtils.buildUri(host, policyState.documentSelfLink), false);

    }

    @Test
    public void testDeleteWhenNoActiveReservation() throws Throwable {
        GroupResourcePolicyState policyState = createAndStoreGroupResourcePolicy();
        try {
            DeploymentProfileConfig.getInstance().setTest(false);
            doDelete(UriUtils.buildUri(host, policyState.documentSelfLink), false);
        } finally {
            DeploymentProfileConfig.getInstance().setTest(true);
        }

        ServiceDocumentQuery<GroupResourcePolicyState> query = new ServiceDocumentQuery<>(host,
                GroupResourcePolicyState.class);
        AtomicBoolean deleted = new AtomicBoolean();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        query.queryDocument(policyState.documentSelfLink, (r) -> {
            if (!r.hasException() && !r.hasResult()) {
                deleted.set(true);
                countDownLatch.countDown();
            }
        });
        countDownLatch.await();
        assertTrue(deleted.get());
        documentsForDeletion.remove(policyState);
    }

    @Test
    public void testDeleteWhenActiveReservation() throws Throwable {
        GroupResourcePolicyState policyState = createAndStoreGroupResourcePolicy();
        policyState = makeResourcePolicyReservationRequest(policyState, 5);

        boolean expectedFailure = true;
        try {
            DeploymentProfileConfig.getInstance().setTest(false);
            doDelete(UriUtils.buildUri(host, policyState.documentSelfLink), expectedFailure);
            fail("expect validation error during deletion");
        } catch (IllegalArgumentException e) {
            // expected
        } finally {
            DeploymentProfileConfig.getInstance().setTest(true);
        }
    }

    @Test
    public void testProvisioningWithUnlimitedPolicy() throws Throwable {
        GroupResourcePolicyState unlimitedInstancesPolicy = createPolicy("policy-unlimited-test",
                8 * CONTAINER_MEMORY + CONTAINER_MEMORY / 2,
                0L, 0, resourcePool.documentSelfLink, 0);
        assertEquals(0, unlimitedInstancesPolicy.maxNumberInstances);
        assertEquals(0, unlimitedInstancesPolicy.availableInstancesCount);
        assertEquals(0, unlimitedInstancesPolicy.allocatedInstancesCount);

        GroupResourcePolicyState savedPolicy = savePolicy(unlimitedInstancesPolicy, false);
        assertEquals(0, savedPolicy.maxNumberInstances);
        assertEquals(0, savedPolicy.availableInstancesCount);
        assertEquals(0, savedPolicy.allocatedInstancesCount);

        GroupResourcePolicyState policyStateAfterProvisioning =
                                        makeResourcePolicyReservationRequest(savedPolicy, 1);
        assertEquals(0, policyStateAfterProvisioning.maxNumberInstances);
        assertEquals(0, policyStateAfterProvisioning.availableInstancesCount);
        assertEquals(1, policyStateAfterProvisioning.allocatedInstancesCount);
    }

    private GroupResourcePolicyState makeResourcePolicyReservationRequest(
            GroupResourcePolicyState policyState, int count) throws Throwable {
        return makeResourcePolicyReservationRequest(count, containerDescription.documentSelfLink,
                policyState, false);
    }

    private GroupResourcePolicyState makeResourcePolicyReservationRequest(int count,
            String descLink, GroupResourcePolicyState policyState, boolean expectFailure)
            throws Throwable {
        ResourcePolicyReservationRequest rsrvRequest = new ResourcePolicyReservationRequest();
        rsrvRequest.resourceCount = count;
        rsrvRequest.resourceDescriptionLink = descLink;

        host.testStart(1);
        host.send(Operation
                .createPatch(UriUtils.buildUri(host, policyState.documentSelfLink))
                .setBody(rsrvRequest)
                .setCompletion(expectFailure ? host.getExpectedFailureCompletion()
                        : host.getCompletion()));
        host.testWait();

        return getDocument(GroupResourcePolicyState.class, policyState.documentSelfLink);
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

    private GroupResourcePolicyState createAndStoreGroupResourcePolicy() throws Throwable {
        return createAndStoreGroupResourcePolicy("reservation-test",
                8 * CONTAINER_MEMORY + CONTAINER_MEMORY / 2,
                0L, 0, resourcePool.documentSelfLink, false);
    }

    private GroupResourcePolicyState createAndStoreGroupResourcePolicy(String link,
            Long memoryLimit, Long storageLimit, Integer cpuShares, String resourcePoolLink,
            boolean expectFailure) throws Throwable {
        // create test policy
        GroupResourcePolicyState policyState = createPolicy(link, memoryLimit, storageLimit,
                                                    cpuShares, resourcePoolLink, 10);
        // attempt saving the test policy
        return savePolicy(policyState, expectFailure);
    }

    private GroupResourcePolicyState savePolicy(GroupResourcePolicyState policyState,
            boolean expectFailure) throws Throwable {
        GroupResourcePolicyState[] result = new GroupResourcePolicyState[] { null };

        host.testStart(1);
        host.send(OperationUtil
                .createForcedPost(
                        UriUtils.buildUri(host, GroupResourcePolicyService.FACTORY_LINK))
                .setBody(policyState)
                .setCompletion(expectFailure ? host.getExpectedFailureCompletion()
                        : (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                                return;
                            }
                            result[0] = o.getBody(GroupResourcePolicyState.class);
                            host.completeIteration();
                        }));
        host.testWait();

        if (result[0] != null) {
            policyState = result[0];
            documentsForDeletion.add(policyState);
        }

        return policyState;
    }

    private GroupResourcePolicyState createPolicy(String link, Long memoryLimit, Long storageLimit,
            Integer cpuShares, String resourcePoolLink, long maxNumInstances) {
        GroupResourcePolicyState policyState = new GroupResourcePolicyState();
        policyState.name = link;
        policyState.documentSelfLink = link + "-" + UUID.randomUUID().toString();
        policyState.tenantLinks = Collections.singletonList("testGroup");
        policyState.maxNumberInstances = maxNumInstances;
        policyState.memoryLimit = memoryLimit;
        policyState.storageLimit = storageLimit;
        policyState.resourcePoolLink = resourcePoolLink;
        policyState.cpuShares = cpuShares;

        return policyState;
    }

    // TODO move these to admiral-common-test?
    private ResourcePoolState createResourcePool(String link, Long maxMemory, Long maxStorage)
            throws Throwable {
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.id = link + "-" + UUID.randomUUID().toString();
        poolState.name = poolState.id;
        poolState.documentSelfLink = poolState.id;
        poolState.maxCpuCount = 1600;
        poolState.minCpuCount = 16;
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
