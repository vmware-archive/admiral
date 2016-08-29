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

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.GroupResourcePolicyService;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.GroupResourcePolicyState;
import com.vmware.admiral.compute.container.GroupResourcePolicyService.ResourcePolicyReservationRequest;
import com.vmware.admiral.request.ReservationRemovalTaskService.ReservationRemovalTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public class ReservationRemovalTaskServiceTest extends RequestBaseTest {
    private List<GroupResourcePolicyState> policiesForDeletion;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        policiesForDeletion = new ArrayList<>();
    }

    @After
    public void tearDown() throws Throwable {
        for (GroupResourcePolicyState groupResourcePolicyState : policiesForDeletion) {
            delete(groupResourcePolicyState.documentSelfLink);
        }
    }

    // This test is failing occasionally. Added additional logging for monitoring and debugging.
    @Test
    public void testReservationRemovalTaskLife() throws Throwable {
        GroupResourcePolicyState policyState = doPost(TestRequestStateFactory
                .createGroupResourcePolicyState(), GroupResourcePolicyService.FACTORY_LINK);
        policiesForDeletion.add(policyState);

        String descLink = containerDesc.documentSelfLink;
        int count = 5;
        boolean expectFailure = false;
        policyState = makeResourcePolicyReservationRequest(count, descLink, policyState, expectFailure);
        assertEquals(policyState.allocatedInstancesCount, count);
        assertEquals(count, policyState.resourceQuotaPerResourceDesc.get(descLink).intValue());

        ReservationRemovalTaskState task = new ReservationRemovalTaskState();
        task.resourceDescriptionLink = descLink;
        task.resourceCount = count;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.groupResourcePolicyLink = policyState.documentSelfLink;

        task = doPost(task, ReservationRemovalTaskFactoryService.SELF_LINK);
        assertNotNull(task);
        waitForTaskSuccess(task.documentSelfLink, ReservationRemovalTaskState.class);

        policyState = getDocument(GroupResourcePolicyState.class, policyState.documentSelfLink);
        assertEquals(policyState.allocatedInstancesCount, 0);
        assertEquals(0, policyState.resourceQuotaPerResourceDesc.get(descLink).intValue());

        host.log("second reservation removal starting:");
        // it should not fail (just warning) if try to remove more than the max:
        task = new ReservationRemovalTaskState();
        task.resourceDescriptionLink = descLink;
        task.resourceCount = count;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.groupResourcePolicyLink = policyState.documentSelfLink;
        task = doPost(task, ReservationRemovalTaskFactoryService.SELF_LINK);

        host.log("second reservation removal started");
        waitForTaskSuccess(task.documentSelfLink, ReservationRemovalTaskState.class);

        policyState = getDocument(GroupResourcePolicyState.class, policyState.documentSelfLink);
        assertEquals(policyState.allocatedInstancesCount, 0);
        assertEquals(0, policyState.resourceQuotaPerResourceDesc.get(descLink).intValue());
    }

    private GroupResourcePolicyState makeResourcePolicyReservationRequest(int count,
            String descLink, GroupResourcePolicyState policyState, boolean expectFailure)
            throws Throwable {
        ResourcePolicyReservationRequest rsrvRequest = new ResourcePolicyReservationRequest();
        rsrvRequest.resourceCount = count;
        rsrvRequest.resourceDescriptionLink = descLink;

        // simulated caller from reservation task:
        URI requestReservationTaskURI = UriUtils.buildUri(host,
                ManagementUriParts.REQUEST_RESERVATION_TASKS);
        setPrivateField(VerificationHost.class.getDeclaredField("referer"), host,
                requestReservationTaskURI);

        host.testStart(1);
        host.send(Operation
                .createPatch(UriUtils.buildUri(host, policyState.documentSelfLink))
                .setBody(rsrvRequest)
                .setCompletion(expectFailure ? host.getExpectedFailureCompletion()
                        : host.getCompletion()));
        host.testWait();

        setPrivateField(VerificationHost.class.getDeclaredField("referer"), host,
                UriUtils.buildUri(host, "test-client-send"));

        return getDocument(GroupResourcePolicyState.class, policyState.documentSelfLink);
    }
}
