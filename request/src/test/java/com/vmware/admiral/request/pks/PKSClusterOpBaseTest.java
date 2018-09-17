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

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_MASTER_HOST_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_MASTER_PORT_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_PLAN_NAME_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_WORKER_INSTANCES_FIELD;

import java.util.HashMap;

import org.junit.After;
import org.junit.Before;

import com.vmware.admiral.adapter.pks.PKSConstants;
import com.vmware.admiral.adapter.pks.service.PKSClusterConfigService;
import com.vmware.admiral.adapter.pks.test.MockPKSAdapterService;
import com.vmware.admiral.compute.pks.PKSEndpointFactoryService;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class PKSClusterOpBaseTest extends RequestBaseTest {
    protected static final String TEST_GROUP = "/tenants/qe/groups/it-dev";
    protected static final String TEST_PLAN = "small";

    protected RequestBrokerState request;
    protected Endpoint endpoint;

    @Before
    @Override
    public void setUp() throws Throwable {
        tweakConstantsOnStartup();

        super.setUp();

        startServices();
        endpoint = createEndpoint();
        request = prepareRequestBrokerState();
    }

    @After
    public void tearDown() throws Throwable {
        tweakConstantsOnTearDown();

        if (endpoint != null && endpoint.documentSelfLink != null
                && !endpoint.documentSelfLink.isEmpty()) {
            delete(endpoint.documentSelfLink);
        }
    }

    private void tweakConstantsOnStartup() throws Throwable {
        PKSClusterProvisioningTaskService.POLL_PKS_ENDPOINT_INTERVAL_MICROS =  3_000_000;
        PKSClusterRemovalTaskService.POLL_PKS_ENDPOINT_INTERVAL_MICROS = 3_000_000;
        PKSClusterResizeTaskService.POLL_PKS_ENDPOINT_INTERVAL_MICROS = 3_000_000;
    }

    private void tweakConstantsOnTearDown() throws Throwable {
        PKSClusterProvisioningTaskService.POLL_PKS_ENDPOINT_INTERVAL_MICROS =  60_000_000;
        PKSClusterRemovalTaskService.POLL_PKS_ENDPOINT_INTERVAL_MICROS = 60_000_000;
        PKSClusterResizeTaskService.POLL_PKS_ENDPOINT_INTERVAL_MICROS = 60_000_000;
    }

    private void startServices() throws Throwable {
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                PKSClusterConfigService.class)), new PKSClusterConfigService());

        waitForServiceAvailability(
                PKSClusterConfigService.SELF_LINK,
                PKSEndpointFactoryService.SELF_LINK);
    }

    private Endpoint createEndpoint() throws Throwable {
        Endpoint endpoint = TestRequestStateFactory.createPksEndpoint(TEST_GROUP, TEST_PLAN);
        return doPost(endpoint, PKSEndpointFactoryService.SELF_LINK);
    }

    private RequestBrokerState prepareRequestBrokerState() {
        HashMap<String, String> map = new HashMap<>();
        map.put(PKS_CLUSTER_NAME_PROP_NAME, MockPKSAdapterService.CLUSTER_NAME_CREATE_SUCCESS);
        map.put(PKS_PLAN_NAME_FIELD, TEST_PLAN);

        map.put(PKS_MASTER_HOST_FIELD, "host");
        map.put(PKS_MASTER_PORT_FIELD, "111");
        map.put(PKS_WORKER_INSTANCES_FIELD, "1");

        map.put(PKSConstants.PKS_ENDPOINT_PROP_NAME, endpoint.documentSelfLink);

        RequestBrokerState request = TestRequestStateFactory.createPKSClusterRequestState(map);
        request.tenantLinks = groupPlacementState.tenantLinks;
        request.tenantLinks.add(TEST_GROUP);
        request.operation = RequestBrokerState.PROVISION_RESOURCE_OPERATION;

        return request;
    }

}
