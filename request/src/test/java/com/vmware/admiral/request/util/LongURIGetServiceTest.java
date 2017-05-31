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

import org.junit.Assert;
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

public class LongURIGetServiceTest extends RequestBaseTest {
    TestRequestSender sender;

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
        Assert.assertTrue(result.documentCount == 1);
    }

    @Test
    public void testGetDataNotExisting() {
        LongURIRequest body = new LongURIRequest();
        body.uri = "/resources/fake?%24filter=documentSelfLink%20eq%20'%2Fresources%2Fpools%2Fdefault-placement-zone'%27&documentType=true&expand=true";
        host.sendAndWaitExpectFailure(Operation
                .createPost(UriUtils.buildUri(host.getUri(), LongURIGetService.SELF_LINK))
                .setBody(body), 404);
    }
}
