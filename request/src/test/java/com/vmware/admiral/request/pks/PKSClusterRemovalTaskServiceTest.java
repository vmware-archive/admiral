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

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_CREATE;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_LAST_ACTION_DELETE;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_MASTER_HOST_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_MASTER_PORT_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_PLAN_NAME_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_WORKER_INSTANCES_FIELD;

import java.lang.reflect.Field;
import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.pks.PKSConstants;
import com.vmware.admiral.adapter.pks.service.PKSClusterConfigService;
import com.vmware.admiral.adapter.pks.test.MockPKSAdapterService;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

public class PKSClusterRemovalTaskServiceTest extends RequestBaseTest {
    public static final String TENANT1 = "/tenants/qe/groups/it-dev";
    private RequestBrokerState request;

    @Before
    @Override
    public void setUp() throws Throwable {
        Field f = PKSClusterProvisioningTaskService.class
                .getDeclaredField("POLL_PKS_ENDPOINT_INTERVAL_MICROS");
        setFinalStatic(f, 3_000_000);
        f = PKSClusterRemovalTaskService.class
                .getDeclaredField("POLL_PKS_ENDPOINT_INTERVAL_MICROS");
        setFinalStatic(f, 3_000_000);

        super.setUp();

        host.startService(Operation.createPost(UriUtils.buildUri(host,
                PKSClusterConfigService.class)), new PKSClusterConfigService());
        waitForServiceAvailability(PKSClusterConfigService.SELF_LINK);

        HashMap<String, String> map = new HashMap<>();
        map.put(PKS_CLUSTER_NAME_PROP_NAME, "unit-test-create-success");
        map.put(PKS_PLAN_NAME_FIELD, "small");

        map.put(PKS_MASTER_HOST_FIELD, "host");
        map.put(PKS_MASTER_PORT_FIELD, "111");
        map.put(PKS_WORKER_INSTANCES_FIELD, "1");

        map.put(PKSConstants.PKS_ENDPOINT_PROP_NAME, "pks-endpoint-link");

        request = TestRequestStateFactory.createPKSClusterRequestState(map);
        request.tenantLinks = groupPlacementState.tenantLinks;
        request.tenantLinks.add(TENANT1);
        request.operation = RequestBrokerState.PROVISION_RESOURCE_OPERATION;
    }

    @After
    public void tearDown() throws Throwable {
        Field f = PKSClusterProvisioningTaskService.class
                .getDeclaredField("POLL_PKS_ENDPOINT_INTERVAL_MICROS");
        setFinalStatic(f, 60_000_000);
        f = PKSClusterRemovalTaskService.class
                .getDeclaredField("POLL_PKS_ENDPOINT_INTERVAL_MICROS");
        setFinalStatic(f, 60_000_000);
    }

    @Test
    public void testPKSClusterRemovalResourceOperation() throws Throwable {
        MockPKSAdapterService.setLastActionState(PKS_LAST_ACTION_CREATE);
        request = startRequest(request);
        waitForRequestToComplete(request);

        request = getDocument(RequestBrokerState.class, request.documentSelfLink);
        assertNotNull(request);

        // verify the resources are provisioned as expected:
        assertEquals(1, request.resourceCount);
        assertEquals(request.resourceCount, request.resourceLinks.size());

        HashMap<String, String> map = new HashMap<>();
        map.put(PKSConstants.PKS_ENDPOINT_PROP_NAME, "pks-endpoint-link");
        RequestBrokerState removalReq = TestRequestStateFactory.createPKSClusterRequestState(map);
        removalReq.tenantLinks = groupPlacementState.tenantLinks;
        removalReq.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
        removalReq.resourceLinks = request.resourceLinks;

        MockPKSAdapterService.setLastActionState(PKS_LAST_ACTION_DELETE);
        MockPKSAdapterService.resetCounter();
        removalReq = startRequest(removalReq);
        waitForRequestToComplete(removalReq);

        String clusterLink = request.resourceLinks.iterator().next();

        ServiceDocument d = getDocumentNoWait(ServiceDocument.class, clusterLink);
        assertNull("Should be null (deleted)", d);
    }

}