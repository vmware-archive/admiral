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

package com.vmware.admiral.compute.endpoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.endpoint.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class EndpointServiceTest extends ComputeBaseTest {

    private List<String> documentLinksForDeletion;

    @Before
    public void setUp() throws Throwable {
        documentLinksForDeletion = new ArrayList<>();
        waitForServiceAvailability(EndpointService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(AuthCredentialsService.FACTORY_LINK);
    }

    @After
    public void tearDown() throws Throwable {
        for (String selfLink : documentLinksForDeletion) {
            delete(selfLink);
        }
    }

    @Test
    public void testCreateEndpoint() throws Throwable {
        EndpointState endpoint = createEndpoint("ep");

        EndpointState newEndpointState = doPost(endpoint, EndpointService.FACTORY_LINK);

        assertNotNull(newEndpointState);
        assertNotNull(newEndpointState.documentSelfLink);
        assertNotNull(newEndpointState.authCredentialsLink);
        assertNotNull(newEndpointState.computeLink);

        documentLinksForDeletion.add(newEndpointState.documentSelfLink);
    }

    @Test
    public void testPatchEndpoint() throws Throwable {
        EndpointState endpoint = createEndpoint("ep");

        EndpointState newEndpointState = doPost(endpoint, EndpointService.FACTORY_LINK);

        assertNotNull(newEndpointState);

        EndpointState state = new EndpointState();
        state.privateKey = "testPK";

        TestContext ctx = testCreate(1);
        AtomicReference<EndpointState> result = new AtomicReference<>();
        Operation patch = Operation.createPatch(host, newEndpointState.documentSelfLink);
        patch.setBody(state)
                .setReferer(host.getUri())
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                host.log(
                                        Level.WARNING,
                                        "Failed to patch endpoint: %s",
                                        o.getUri());
                                ctx.failIteration(ex);
                            } else {
                                result.set(o.getBody(EndpointState.class));

                                ctx.completeIteration();
                            }
                        });

        host.send(patch);
        ctx.await();
        assertEquals("testPK", result.get().privateKey);
        documentLinksForDeletion.add(newEndpointState.documentSelfLink);
    }

    @Test
    public void testListEndpoints() throws Throwable {
        EndpointState endpoint1 = createEndpoint("ep1");

        EndpointState newEndpointState1 = doPost(endpoint1, EndpointService.FACTORY_LINK);
        documentLinksForDeletion.add(newEndpointState1.documentSelfLink);

        EndpointState endpoint2 = createEndpoint("ep2");

        EndpointState newEndpointState2 = doPost(endpoint2, EndpointService.FACTORY_LINK);
        documentLinksForDeletion.add(newEndpointState2.documentSelfLink);

        TestContext ctx = testCreate(1);
        AtomicReference<ServiceDocumentQueryResult> result = new AtomicReference<>();
        Operation list = Operation.createGet(host, EndpointService.FACTORY_LINK);
        list.setCompletion((o, e) -> {
            if (e != null) {
                host.log("Can't load endpoint state objects. Error: %s", Utils.toString(e));
                ctx.failIteration(e);
            } else {
                result.set(o.getBody(ServiceDocumentQueryResult.class));
                ctx.completeIteration();
            }
        });
        host.send(list);
        ctx.await();

        ServiceDocumentQueryResult queryResult = result.get();
        assertNotNull(queryResult);
        assertNotNull(queryResult.documentLinks);
        assertEquals(2, queryResult.documentLinks.size());
    }

    private EndpointState createEndpoint(String name) {
        EndpointState endpoint = new EndpointState();
        endpoint.endpointType = "aws";
        endpoint.name = name;
        endpoint.privateKey = "aws.access.key";
        endpoint.privateKeyId = "aws.secret.key";
        endpoint.regionId = "us-east-1";
        endpoint.endpointType = "aws";
        return endpoint;
    }

}
