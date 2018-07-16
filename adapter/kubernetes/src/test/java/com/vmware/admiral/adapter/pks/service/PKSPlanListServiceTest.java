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

package com.vmware.admiral.adapter.pks.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_ENDPOINT_QUERY_PARAM_NAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.pks.entities.PKSPlan;
import com.vmware.admiral.adapter.pks.test.MockPKSAdapterService;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.pks.PKSEndpointFactoryService;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint;
import com.vmware.admiral.host.HostInitKubernetesAdapterServiceConfig;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;

public class PKSPlanListServiceTest extends ComputeBaseTest {

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        HostInitKubernetesAdapterServiceConfig.startServices(host, true);
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                PKSPlanListService.class)), new PKSPlanListService());

        waitForServiceAvailability(MockPKSAdapterService.SELF_LINK);
        waitForServiceAvailability(PKSPlanListService.SELF_LINK);
        waitForServiceAvailability(PKSEndpointFactoryService.SELF_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);

        sender = host.getTestRequestSender();
    }

    @Test
    public void testListPKSPlans() {
        String endpointLink = createEndpoint().documentSelfLink;
        List<PKSPlan> discoveredPlans = sendListRequest(endpointLink);

        assertNotNull(discoveredPlans);
        assertEquals(2, discoveredPlans.size());
    }

    @Test
    public void testPost() {
        String endpointLink = createEndpoint().documentSelfLink;
        URI serviceUri = UriUtils.buildUri(host, PKSPlanListService.SELF_LINK,
                UriUtils.buildUriQuery(PKS_ENDPOINT_QUERY_PARAM_NAME, endpointLink));

        TestContext ctx = testCreate(1);

        List<PKSPlan> plans = new ArrayList<>();
        Operation get = Operation.createPost(serviceUri)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        assertEquals(Operation.STATUS_CODE_BAD_METHOD, op.getStatusCode());
                        ctx.complete();
                        return;
                    }
                    ctx.fail(null);
                });

        sender.sendRequest(get);
        ctx.await();
    }

    @Test
    public void testEmptyEndpoint() {
        TestContext ctx = testCreate(1);

        List<PKSPlan> plans = new ArrayList<>();
        Operation get = Operation.createGet(host, PKSPlanListService.SELF_LINK)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, op.getStatusCode());
                        ctx.complete();
                        return;
                    }
                    ctx.fail(null);
                });

        sender.sendRequest(get);
        ctx.await();
    }

    private List<PKSPlan> sendListRequest(String endpointLink) {
        URI serviceUri = UriUtils.buildUri(host, PKSPlanListService.SELF_LINK,
                UriUtils.buildUriQuery(PKS_ENDPOINT_QUERY_PARAM_NAME, endpointLink));

        TestContext ctx = testCreate(1);

        List<PKSPlan> plans = new ArrayList<>();
        Operation get = Operation.createGet(serviceUri)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        ctx.fail(ex);
                        return;
                    }

                    plans.addAll(Arrays.asList(op.getBody(PKSPlan[].class)));
                    ctx.complete();
                });

        sender.sendRequest(get);
        ctx.await();
        return plans;
    }

    private Endpoint createEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        Operation o = Operation
                .createPost(host, PKSEndpointFactoryService.SELF_LINK)
                .setBodyNoCloning(endpoint);

        return sender.sendAndWait(o, Endpoint.class);
    }

}
