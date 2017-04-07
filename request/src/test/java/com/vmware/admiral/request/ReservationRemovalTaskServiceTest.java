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

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.ResourcePlacementReservationRequest;
import com.vmware.admiral.request.ReservationRemovalTaskService.ReservationRemovalTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public class ReservationRemovalTaskServiceTest extends RequestBaseTest {

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
    }

    // This test is failing occasionally. Added additional logging for monitoring and debugging.
    @Test
    public void testReservationRemovalTaskLife() throws Throwable {
        GroupResourcePlacementState placementState = doPost(TestRequestStateFactory
                .createGroupResourcePlacementState(), GroupResourcePlacementService.FACTORY_LINK);

        String descLink = containerDesc.documentSelfLink;
        int count = 5;
        boolean expectFailure = false;
        placementState = makeResourcePlacementReservationRequest(count, descLink, placementState, expectFailure);
        assertEquals(placementState.allocatedInstancesCount, count);

        ReservationRemovalTaskState task = new ReservationRemovalTaskState();
        task.resourceDescriptionLink = descLink;
        task.resourceCount = count;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.groupResourcePlacementLink = placementState.documentSelfLink;

        task = doPost(task, ReservationRemovalTaskFactoryService.SELF_LINK);
        assertNotNull(task);
        waitForTaskSuccess(task.documentSelfLink, ReservationRemovalTaskState.class);

        placementState = getDocument(GroupResourcePlacementState.class, placementState.documentSelfLink);
        assertEquals(placementState.allocatedInstancesCount, 0);

        host.log("second reservation removal starting:");
        // it should not fail (just warning) if try to remove more than the max:
        task = new ReservationRemovalTaskState();
        task.resourceDescriptionLink = descLink;
        task.resourceCount = count;
        task.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        task.groupResourcePlacementLink = placementState.documentSelfLink;
        task = doPost(task, ReservationRemovalTaskFactoryService.SELF_LINK);

        host.log("second reservation removal started");
        waitForTaskSuccess(task.documentSelfLink, ReservationRemovalTaskState.class);

        placementState = getDocument(GroupResourcePlacementState.class, placementState.documentSelfLink);
        assertEquals(placementState.allocatedInstancesCount, 0);
    }

    private GroupResourcePlacementState makeResourcePlacementReservationRequest(int count,
            String descLink, GroupResourcePlacementState placementState, boolean expectFailure)
            throws Throwable {
        ResourcePlacementReservationRequest rsrvRequest = new ResourcePlacementReservationRequest();
        rsrvRequest.resourceCount = count;
        rsrvRequest.resourceDescriptionLink = descLink;

        // simulated caller from reservation task:
        URI requestReservationTaskURI = UriUtils.buildUri(host,
                ManagementUriParts.REQUEST_RESERVATION_TASKS);
        rsrvRequest.referer = requestReservationTaskURI.getPath();

        host.testStart(1);
        host.send(Operation
                .createPatch(UriUtils.buildUri(host, placementState.documentSelfLink))
                .setBody(rsrvRequest)
                .setCompletion(expectFailure ? host.getExpectedFailureCompletion()
                        : host.getCompletion()));
        host.testWait();

        setPrivateField(VerificationHost.class.getDeclaredField("referer"), host,
                UriUtils.buildUri(host, "test-client-send"));

        return getDocument(GroupResourcePlacementState.class, placementState.documentSelfLink);
    }
}
