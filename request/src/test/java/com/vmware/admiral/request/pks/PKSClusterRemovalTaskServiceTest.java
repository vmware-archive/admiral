/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.pks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_CREATE;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_DELETE;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_UPDATE;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_WORKER_INSTANCES_FIELD;

import java.util.HashMap;

import org.junit.Test;

import com.vmware.admiral.adapter.pks.PKSConstants;
import com.vmware.admiral.adapter.pks.test.MockPKSAdapterService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.ServiceDocument;

public class PKSClusterRemovalTaskServiceTest extends PKSClusterOpBaseTest {


    @Test
    public void testPKSClusterRemovalResourceOperation() throws Throwable {
        MockPKSAdapterService.setLastAction(PKS_LAST_ACTION_CREATE);
        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);

        // verify the resources are provisioned as expected:
        assertEquals(1, request.resourceCount);
        assertEquals(request.resourceCount, request.resourceLinks.size());

        HashMap<String, String> map = new HashMap<>();
        map.put(PKSConstants.PKS_ENDPOINT_PROP_NAME, endpoint.documentSelfLink);
        RequestBrokerState resizeReq = TestRequestStateFactory.createPKSClusterRequestState(map);
        resizeReq.tenantLinks = groupPlacementState.tenantLinks;
        resizeReq.operation = RequestBrokerState.RESIZE_RESOURCE;
        resizeReq.resourceLinks = request.resourceLinks;
        resizeReq.customProperties = new HashMap<>();
        resizeReq.customProperties.put(PKS_WORKER_INSTANCES_FIELD, "1");

        MockPKSAdapterService.setLastAction(PKS_LAST_ACTION_UPDATE);
        MockPKSAdapterService.resetCounter();
        resizeReq = startRequest(resizeReq);
        waitForRequestToComplete(resizeReq);

        map = new HashMap<>();
        map.put(PKSConstants.PKS_ENDPOINT_PROP_NAME, endpoint.documentSelfLink);
        RequestBrokerState removalReq = TestRequestStateFactory.createPKSClusterRequestState(map);
        removalReq.tenantLinks = groupPlacementState.tenantLinks;
        removalReq.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
        removalReq.resourceLinks = request.resourceLinks;

        MockPKSAdapterService.setLastAction(PKS_LAST_ACTION_DELETE);
        MockPKSAdapterService.resetCounter();
        removalReq = startRequest(removalReq);
        waitForRequestToComplete(removalReq);

        String clusterLink = request.resourceLinks.iterator().next();

        ServiceDocument d = getDocumentNoWait(ServiceDocument.class, clusterLink);
        assertNull("Should be null (deleted)", d);
    }

}
