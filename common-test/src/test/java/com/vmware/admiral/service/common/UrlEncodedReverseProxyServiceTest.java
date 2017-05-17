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

package com.vmware.admiral.service.common;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class UrlEncodedReverseProxyServiceTest extends BaseTestCase {
    private final Logger logger = Logger.getLogger(getClass().getName());

    @Before
    public void setUp() throws Throwable {
        this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(10));

        this.host.startService(new TestEchoService());
        this.host.startService(new UrlEncodedReverseProxyService());

        this.host.setTimeoutSeconds(300);

        this.host.waitForServiceAvailable(TestEchoService.SELF_LINK,
                UrlEncodedReverseProxyService.SELF_LINK);
    }

    @Test
    public void testGet() {

        String verificationToken = UUID.randomUUID().toString();
        this.logger.info("verificationToken: " + verificationToken);

        String hostUri = this.host.getUri().toASCIIString();

        String rawProxyLocation = hostUri + TestEchoService.SELF_LINK
                + "?" + TestEchoService.QUERY_PARAM_RESPONSE + "=" + verificationToken;
        String proxyLocation = UrlEncodedReverseProxyService
                .createReverseProxyLocation(rawProxyLocation);
        String location = hostUri + proxyLocation;
        this.logger.info("location: " + location);
        URI uri = URI.create(location);
        Operation response = super.host.waitForResponse(Operation.createGet(uri));
        Assert.assertEquals(Operation.STATUS_CODE_OK, response.getStatusCode());
        Object bodyRaw = response.getBodyRaw();
        this.logger.info("bodyRaw: " + bodyRaw);
        Assert.assertTrue(bodyRaw instanceof String);
        Assert.assertEquals(verificationToken, bodyRaw);
    }

    public static class TestEchoService extends StatelessService {

        public static final String SELF_LINK = UriPaths.RESOURCES + "/test-backend-service";

        public static final String QUERY_PARAM_RESPONSE = "response";

        @Override
        public void handleGet(Operation get) {
            Map<String, String> params = UriUtils.parseUriQueryParams(get.getUri());
            String response = params.get(QUERY_PARAM_RESPONSE);
            get.setBody(response != null ? response : getClass().getSimpleName()).complete();
        }
    }
}