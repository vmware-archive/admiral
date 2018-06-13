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

package com.vmware.admiral.compute.pks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.function.Consumer;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.pks.PKSCreateEndpointService.EndpointSpec;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.test.TestRequestSender;

public class PKSCreateEndpointServiceTest extends ComputeBaseTest {

    private TestRequestSender sender;

    @BeforeClass
    public static void beforeClass() {
        DeploymentProfileConfig.getInstance().setTest(false);
    }

    @AfterClass
    public static void afterClass() {
        DeploymentProfileConfig.getInstance().setTest(true);
    }

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(PKSEndpointService.FACTORY_LINK,
                PKSCreateEndpointService.SELF_LINK);
        sender = host.getTestRequestSender();
    }

    @Test
    public void testCreate() throws Throwable {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "https://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        EndpointSpec endpointSpec = new EndpointSpec();
        endpointSpec.acceptHostAddress = true;
        endpointSpec.acceptCertificate = true;
        endpointSpec.endpoint = endpoint;

        Endpoint createdEndpoint = createEndpoint(endpointSpec);

        endpoint = new Endpoint();
        endpoint.apiEndpoint = "https://localhost";
        endpoint.uaaEndpoint = "https://localhost";
        createEndpointExpectFailure(endpointSpec, e -> {
            e.getErrorCode();
        });
    }

    @Test
    public void testUpdate() throws Throwable {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "https://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        EndpointSpec endpointSpec = new EndpointSpec();
        endpointSpec.acceptHostAddress = true;
        endpointSpec.acceptCertificate = true;
        endpointSpec.endpoint = endpoint;

        createEndpoint(endpointSpec);

        endpoint.uaaEndpoint = "http://some-other-host";
        endpointSpec.isUpdateOperation = true;

        Endpoint updatedEndpoint = createEndpoint(endpointSpec);
        assertEquals(endpoint.uaaEndpoint, updatedEndpoint.uaaEndpoint);
    }

    private Endpoint createEndpoint(EndpointSpec endpointSpec) throws Throwable {
        Operation o = Operation
                .createPut(host, PKSCreateEndpointService.SELF_LINK)
                .setBodyNoCloning(endpointSpec);

        o = sender.sendAndWait(o);
        assertNotNull(o);
        String locationHeader = o.getResponseHeader(Operation.LOCATION_HEADER);
        assertNotNull(locationHeader);

        Endpoint result = getDocumentNoWait(Endpoint.class, locationHeader);

        assertNotNull(result);
        assertNotNull(result.documentSelfLink);
        assertEquals(endpointSpec.endpoint.uaaEndpoint, result.uaaEndpoint);
        assertEquals(endpointSpec.endpoint.apiEndpoint, result.apiEndpoint);

        return result;
    }

    private void createEndpointExpectFailure(EndpointSpec e, Consumer<ServiceErrorResponse> consumer) {
        Operation o = Operation
                .createPut(host, PKSCreateEndpointService.SELF_LINK)
                .setBodyNoCloning(e);
        TestRequestSender.FailureResponse failure = sender.sendAndWaitFailure(o);
        assertTrue(failure.failure instanceof LocalizableValidationException);
        ServiceErrorResponse errorResponse = failure.op.getBody(ServiceErrorResponse.class);
        assertNotNull(errorResponse);

        consumer.accept(errorResponse);
    }

}