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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.test.TestRequestSender;

public class PKSEndpointServiceTest extends ComputeBaseTest {

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(PKSEndpointService.FACTORY_LINK);
        sender = host.getTestRequestSender();
    }

    @Test
    public void testCreate() {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        createEndpoint(endpoint);

        endpoint.apiEndpoint = null;
        endpoint.uaaEndpoint = "http://localhost";
        createEndpointExpectFailure(endpoint, ser -> {
            assertEquals(Operation.STATUS_CODE_BAD_REQUEST, ser.statusCode);
            assertEquals("'API endpoint' is required", ser.message);
        });

        endpoint.uaaEndpoint = null;
        endpoint.apiEndpoint = "http://localhost";
        createEndpointExpectFailure(endpoint, ser -> {
            assertEquals(Operation.STATUS_CODE_BAD_REQUEST, ser.statusCode);
            assertEquals("'UAA endpoint' is required", ser.message);
        });

        endpoint.uaaEndpoint = "file://malformed-url";
        endpoint.apiEndpoint = "http://localhost";
        createEndpointExpectFailure(endpoint, ser -> {
            assertEquals(Operation.STATUS_CODE_BAD_REQUEST, ser.statusCode);
            assertTrue(ser.message.startsWith("Unsupported scheme, must be http or https"));
        });
    }

    @Test
    public void testUpdate() {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        final Endpoint endpoint1 = createEndpoint(endpoint);

        final Endpoint patch1 = new Endpoint();
        patch1.documentSelfLink = endpoint1.documentSelfLink;
        patch1.apiEndpoint = "http://localhost";

        updateEndpoint(patch1, (o, r) -> {
            assertEquals(Operation.STATUS_CODE_NOT_MODIFIED, o.getStatusCode());
            assertEquals(patch1.apiEndpoint, r.apiEndpoint);
            assertEquals(endpoint1.uaaEndpoint, r.uaaEndpoint);
        });

        final Endpoint patch2 = new Endpoint();
        patch2.documentSelfLink = endpoint1.documentSelfLink;
        patch2.apiEndpoint = "http://other-host";

        updateEndpoint(patch2, (o, r) -> {
            assertEquals(Operation.STATUS_CODE_OK, o.getStatusCode());
            assertEquals(patch2.apiEndpoint, r.apiEndpoint);
            assertEquals(endpoint1.uaaEndpoint, r.uaaEndpoint);
        });
    }

    @Test
    public void testDelete() throws Throwable {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        endpoint = createEndpoint(endpoint);

        delete(endpoint.documentSelfLink);
        endpoint = getDocumentNoWait(Endpoint.class, endpoint.documentSelfLink);
        assertNull(endpoint);
    }

    private Endpoint createEndpoint(Endpoint endpoint) {
        Operation o = Operation
                .createPost(host, PKSEndpointService.FACTORY_LINK)
                .setBodyNoCloning(endpoint);

        Endpoint result = sender.sendAndWait(o, Endpoint.class);
        assertNotNull(result);
        assertNotNull(result.documentSelfLink);
        assertEquals(endpoint.uaaEndpoint, result.uaaEndpoint);
        assertEquals(endpoint.apiEndpoint, result.apiEndpoint);

        return result;
    }

    private void createEndpointExpectFailure(Endpoint e, Consumer<ServiceErrorResponse> consumer) {
        Operation o = Operation
                .createPost(host, PKSEndpointService.FACTORY_LINK)
                .setBodyNoCloning(e);
        TestRequestSender.FailureResponse failure = sender.sendAndWaitFailure(o);
        assertTrue(failure.failure instanceof LocalizableValidationException);
        ServiceErrorResponse errorResponse = failure.op.getBody(ServiceErrorResponse.class);
        assertNotNull(errorResponse);

        consumer.accept(errorResponse);
    }

    private void updateEndpoint(Endpoint patch, BiConsumer<Operation, Endpoint> consumer) {
        Operation o = Operation
                .createPatch(host, patch.documentSelfLink)
                .setBodyNoCloning(patch);
        o = sender.sendAndWait(o);
        assertNotNull(o);

        Operation get = Operation.createGet(host, patch.documentSelfLink);
        Endpoint e = sender.sendAndWait(get, Endpoint.class);
        assertNotNull(e);

        consumer.accept(o, e);
    }

}