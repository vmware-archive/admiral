/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.service.common.LongURIGetService;
import com.vmware.admiral.service.common.LongURIGetService.LongURIRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.TestRequestSender.FailureResponse;

public class LongURIGetServiceTest extends RequestBaseTest {
    TestRequestSender sender;

    @Override
    @Before
    public void setUp() throws Throwable {
        sender = host.getTestRequestSender();
        startServices(host);
        host.waitForServiceAvailable(LongURIGetService.SELF_LINK,
                ElasticPlacementZoneConfigurationService.SELF_LINK);
    }

    @Test
    public void testGetData() {
        LongURIRequest body = new LongURIRequest();
        body.uri = "/resources/elastic-placement-zones-config?%24filter=documentSelfLink%20eq%20'%2Fresources%2Fpools%2Fdefault-placement-zone'%27&documentType=true&expand=true";
        ServiceDocumentQueryResult result = sender.sendAndWait(Operation
                .createPost(UriUtils.buildUri(host.getUri(), LongURIGetService.SELF_LINK))
                .setBody(body), ServiceDocumentQueryResult.class);
        assertTrue(result.documentCount == 1);
    }

    @Test
    public void testGetDataNotExisting() {
        LongURIRequest body = new LongURIRequest();
        body.uri = "/resources/fake?%24filter=documentSelfLink%20eq%20'%2Fresources%2Fpools%2Fdefault-placement-zone'%27&documentType=true&expand=true";
        host.sendAndWaitExpectFailure(Operation
                .createPost(UriUtils.buildUri(host.getUri(), LongURIGetService.SELF_LINK))
                .setBody(body), Operation.STATUS_CODE_NOT_FOUND);
    }

    @Test
    public void testBadRequest() {

        // implicit content-type (= "application/json")

        String body = "whatever";

        Operation op = Operation
                .createPost(UriUtils.buildUri(host.getUri(), LongURIGetService.SELF_LINK))
                .setBody(body);

        FailureResponse failure = sender.sendAndWaitFailure(op);

        assertNotNull(failure.failure);
        assertEquals(Operation.MEDIA_TYPE_APPLICATION_JSON, failure.op.getContentType());
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, failure.op.getStatusCode());

        // explicit content-type

        op = Operation
                .createPost(UriUtils.buildUri(host.getUri(), LongURIGetService.SELF_LINK))
                .setContentType("application/html")
                .setBody(body);

        failure = sender.sendAndWaitFailure(op);

        assertNotNull(failure.failure);
        assertEquals("", failure.op.getBody(String.class));
        assertEquals(Operation.STATUS_CODE_BAD_REQUEST, failure.op.getStatusCode());

        // invalid target URI and content type

        LongURIRequest request = new LongURIRequest();
        request.uri = "<script>alert(\"surprise!\");</script>";

        op = Operation
                .createPost(UriUtils.buildUri(host.getUri(), LongURIGetService.SELF_LINK))
                .setContentType("application/html")
                .setBody(request);

        failure = sender.sendAndWaitFailure(op);

        assertNotNull(failure.failure);
        assertEquals(Operation.MEDIA_TYPE_APPLICATION_JSON, failure.op.getContentType());
        assertEquals(Operation.STATUS_CODE_NOT_FOUND, failure.op.getStatusCode());
        assertTrue(failure.op.getBody(String.class)
                .startsWith("{\"message\":\"Service not found:"));
    }

}
