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

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class EndpointAdapterServiceTest extends ComputeBaseTest {

    private List<String> documentLinksForDeletion;

    @Before
    public void setUp() throws Throwable {
        documentLinksForDeletion = new ArrayList<>();
        waitForServiceAvailability(EndpointService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(AuthCredentialsService.FACTORY_LINK);
        DeploymentProfileConfig.getInstance().setTest(true);
    }

    @After
    public void tearDown() throws Throwable {
        for (String selfLink : documentLinksForDeletion) {
            delete(selfLink);
        }
        DeploymentProfileConfig.getInstance().setTest(false);
    }

    @Test
    public void testListEndpoints() throws Throwable {
        EndpointState endpoint1 = createEndpoint("ep1");

        EndpointAllocationTaskState newEndpointState1 = allocateEndpoint(endpoint1);
        documentLinksForDeletion.add(newEndpointState1.endpointState.documentSelfLink);

        EndpointState endpoint2 = createEndpoint("ep2");

        EndpointAllocationTaskState newEndpointState2 = allocateEndpoint(endpoint2);
        documentLinksForDeletion.add(newEndpointState2.endpointState.documentSelfLink);

        ServiceDocumentQueryResult queryResult = getDocument(ServiceDocumentQueryResult.class,
                EndpointAdapterService.SELF_LINK);
        assertNotNull(queryResult);
        assertNotNull(queryResult.documentLinks);
        assertEquals(2, queryResult.documentLinks.size());
    }

    @Test
    public void testListEndpointsExpanded() throws Throwable {
        EndpointState endpoint1 = createEndpoint("ep1");

        EndpointAllocationTaskState newEndpointState1 = allocateEndpoint(endpoint1);
        documentLinksForDeletion.add(newEndpointState1.endpointState.documentSelfLink);

        EndpointState endpoint2 = createEndpoint("ep2");

        EndpointAllocationTaskState newEndpointState2 = allocateEndpoint(endpoint2);
        documentLinksForDeletion.add(newEndpointState2.endpointState.documentSelfLink);

        TestContext ctx = testCreate(1);
        AtomicReference<ServiceDocumentQueryResult> result = new AtomicReference<>();
        URI uri = UriUtils
                .buildExpandLinksQueryUri(UriUtils.buildUri(host, EndpointAdapterService.SELF_LINK));

        Operation list = Operation.createGet(uri);
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
        assertNotNull(queryResult.documents);
        assertEquals(2, queryResult.documents.size());
    }

    @Test
    public void testGetEndpoint() throws Throwable {
        EndpointState endpoint1 = createEndpoint("ep1");

        EndpointAllocationTaskState newEndpointState1 = allocateEndpoint(endpoint1);
        documentLinksForDeletion.add(newEndpointState1.endpointState.documentSelfLink);

        String uriPath = UriUtils.buildUriPath(EndpointAdapterService.SELF_LINK,
                newEndpointState1.endpointState.documentSelfLink);

        EndpointState endpointState = getDocument(EndpointState.class, uriPath);

        assertNotNull(endpointState);
        assertEquals(newEndpointState1.endpointState.documentSelfLink,
                endpointState.documentSelfLink);
    }

    @Test
    public void testCreateEndpoint() throws Throwable {
        EndpointState endpoint = createEndpoint("ep");

        EndpointState newEndpointState = doPost(endpoint,
                EndpointAdapterService.SELF_LINK);

        assertNotNull(newEndpointState);
        assertNotNull(newEndpointState.documentSelfLink);
        assertNotNull(newEndpointState.authCredentialsLink);
        assertNotNull(newEndpointState.computeLink);

        documentLinksForDeletion.add(UriUtils.buildUriPath(EndpointAdapterService.SELF_LINK,
                newEndpointState.documentSelfLink));
    }

    @Test
    public void testValidateEndpoint() throws Throwable {
        EndpointState endpoint = createEndpoint("ep");

        DeploymentProfileConfig.getInstance().setTest(true);
        host.testStart(1);
        URI validateUri = UriUtils.buildUri(this.host, EndpointAdapterService.SELF_LINK,
                ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);
        Operation op = Operation
                .createPut(validateUri)
                .setBody(endpoint)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    if (o.getStatusCode() != HttpURLConnection.HTTP_NO_CONTENT) {
                        host.failIteration(new IllegalStateException(
                                "Expected status code 204 when validation is successful. Status: "
                                        + o.getStatusCode()));
                        return;
                    }

                    if (o.getBodyRaw() != null) {
                        host.failIteration(new IllegalStateException(
                                "No body expected when validation is successful."));
                        return;
                    }

                    host.completeIteration();
                });

        host.send(op);
        host.testWait();
    }

    @Test
    public void testDeleteEndpoint() throws Throwable {
        EndpointState endpoint = createEndpoint("ep");

        EndpointAllocationTaskState endpointAllocationTask = allocateEndpoint(endpoint);
        assertNotNull(endpointAllocationTask);
        assertNotNull(endpointAllocationTask.endpointState);
        assertNotNull(endpointAllocationTask.endpointState.documentSelfLink);
        delete(UriUtils.buildUriPath(EndpointAdapterService.SELF_LINK,
                endpointAllocationTask.endpointState.documentSelfLink));

        documentLinksForDeletion.add(endpointAllocationTask.endpointState.documentSelfLink);
    }

    private EndpointAllocationTaskState allocateEndpoint(EndpointState endpoint) throws Throwable {
        EndpointAllocationTaskState state = new EndpointAllocationTaskState();
        state.endpointState = endpoint;
        state.options = EnumSet.of(TaskOption.IS_MOCK);
        state.taskInfo = new TaskState();
        state.taskInfo.isDirect = true;

        EndpointAllocationTaskState result = doPost(state,
                EndpointAllocationTaskService.FACTORY_LINK);
        return result;
    }

    private EndpointState createEndpoint(String name) {
        EndpointState endpoint = new EndpointState();
        endpoint.endpointType = "aws";
        endpoint.name = name;
        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put("privateKey", "aws.access.key");
        endpoint.endpointProperties.put("privateKeyId", "aws.secret.key");
        endpoint.endpointProperties.put("regionId", "us-east-1");
        return endpoint;
    }

}
