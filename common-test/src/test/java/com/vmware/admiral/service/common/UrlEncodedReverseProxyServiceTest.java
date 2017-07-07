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

import static com.vmware.xenon.common.Operation.STATUS_CODE_FORBIDDEN;
import static com.vmware.xenon.common.Operation.STATUS_CODE_OK;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class UrlEncodedReverseProxyServiceTest extends BaseTestCase {
    private final Logger logger = Logger.getLogger(getClass().getName());

    @Before
    public void setUp() throws Throwable {
        this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(10));

        this.host.startService(new TestEchoService());
        this.host.startService(new TestStaticContentService());
        this.host.startService(new UrlEncodedReverseProxyService());

        this.host.setTimeoutSeconds(300);

        this.host.waitForServiceAvailable(
                TestEchoService.SELF_LINK,
                TestStaticContentService.SELF_LINK,
                UrlEncodedReverseProxyService.SELF_LINK);
    }

    @Test
    public void testGet() {

        String verificationToken = UUID.randomUUID().toString();
        this.logger.info("verificationToken: " + verificationToken);

        Operation response = super.host.waitForResponse(createGetOperation(
                this.host.getUri().toASCIIString(), verificationToken));
        Assert.assertEquals(STATUS_CODE_OK, response.getStatusCode());
        Object bodyRaw = response.getBodyRaw();
        this.logger.info("bodyRaw: " + bodyRaw);
        Assert.assertTrue(bodyRaw instanceof String);
        Assert.assertEquals(verificationToken, bodyRaw);
    }

    @Test
    public void testGetWithHostUriPlaceholder() throws UnsupportedEncodingException {

        String verificationToken = UUID.randomUUID().toString();
        this.logger.info("verificationToken: " + verificationToken);

        String fwHostUri = "/step/" + URLEncoder.encode("{host.uri}", Utils.CHARSET);
        Operation get = createGetOperation(fwHostUri, verificationToken);
        Operation response = super.host.waitForResponse(get);
        Assert.assertEquals(STATUS_CODE_OK, response.getStatusCode());
        Object bodyRaw = response.getBodyRaw();
        this.logger.info("bodyRaw: " + bodyRaw);
        Assert.assertTrue(bodyRaw instanceof String);
        Assert.assertEquals(verificationToken, bodyRaw);
    }

    @Test
    public void testGetWithHostAdapterSharedPlaceholder() throws UnsupportedEncodingException {

        String fwHostUri = "/step/" + URLEncoder.encode("{host.adapter.shared.uri}", Utils.CHARSET);

        String proxyLocation = UrlEncodedReverseProxyService
                .createReverseProxyLocation(fwHostUri + TestStaticContentService.SUFFIX);

        String hostUri = this.host.getUri().toASCIIString();
        String location = hostUri + proxyLocation;

        Operation get = Operation.createGet(URI.create(location));
        Operation response = super.host.waitForResponse(get);
        Assert.assertEquals(STATUS_CODE_OK, response.getStatusCode());
        Object bodyRaw = response.getBodyRaw();
        this.logger.info("bodyRaw: " + bodyRaw);
        Assert.assertTrue(bodyRaw instanceof String);
        Assert.assertEquals(TestStaticContentService.RESPONSE, bodyRaw);
    }

    @Test
    public void testAuthorizeRequest_pos() {

        final ServiceHost dummyHost = new ServiceHost() {
            @Override
            public boolean isAuthorized(Service service, ServiceDocument document, Operation op) {
                return false;
            }
        };
        UrlEncodedReverseProxyService service = new UrlEncodedReverseProxyService() {
            @Override
            public ServiceHost getHost() {
                return dummyHost;
            }
        };

        String hostUri = this.host.getUri().toASCIIString();

        String proxyLocation = UrlEncodedReverseProxyService
                .createReverseProxyLocation("lib/main.js");
        String location = hostUri + proxyLocation;
        this.logger.info("location: " + location);
        URI uri = URI.create(location);
        Operation get = Operation.createGet(uri);

        service.authorizeRequest(get);
        Assert.assertEquals(STATUS_CODE_OK, get.getStatusCode());
    }

    @Test
    public void testAuthorizeRequest_neg() {

        final ServiceHost dummyHost = new ServiceHost() {
            @Override
            public boolean isAuthorized(Service service, ServiceDocument document, Operation op) {
                return false;
            }
        };
        UrlEncodedReverseProxyService service = new UrlEncodedReverseProxyService() {
            @Override
            public ServiceHost getHost() {
                return dummyHost;
            }
        };

        String verificationToken = UUID.randomUUID().toString();
        this.logger.info("verificationToken: " + verificationToken);

        Operation get = createGetOperation(this.host.getUri().toASCIIString(), verificationToken);
        service.authorizeRequest(get);
        Assert.assertEquals(STATUS_CODE_FORBIDDEN, get.getStatusCode());
    }

    @Test
    public void testPost() {
        testWithBody(Operation::createPost);
    }

    @Test
    public void testPut() {
        testWithBody(Operation::createPut);
    }

    @Test
    public void testPatch() {
        testWithBody(Operation::createPatch);
    }

    public void testWithBody(Function<URI, Operation> createOp) {
        String verificationToken = UUID.randomUUID().toString();
        this.logger.info("verificationToken: " + verificationToken);

        String hostUri = this.host.getUri().toASCIIString();

        String rawProxyLocation = hostUri + TestEchoService.SELF_LINK;
        String proxyLocation = UrlEncodedReverseProxyService
                .createReverseProxyLocation(rawProxyLocation);
        String location = hostUri + proxyLocation;
        this.logger.info("location: " + location);
        URI uri = URI.create(location);

        Operation response = super.host
                .waitForResponse(createOp.apply(uri).setBody(verificationToken));
        Assert.assertEquals(STATUS_CODE_OK, response.getStatusCode());
        Object bodyRaw = response.getBodyRaw();
        this.logger.info("bodyRaw: " + bodyRaw);
        Assert.assertTrue(bodyRaw instanceof String);
        Assert.assertEquals(verificationToken, bodyRaw);
    }

    private Operation createGetOperation(String forwardHostUri, String verificationToken) {

        String hostUri = this.host.getUri().toASCIIString();
        String rawProxyLocation = forwardHostUri + TestEchoService.SELF_LINK
                + "?" + TestEchoService.QUERY_PARAM_RESPONSE + "=" + verificationToken;
        String proxyLocation = UrlEncodedReverseProxyService
                .createReverseProxyLocation(rawProxyLocation);
        String location = hostUri + proxyLocation;
        this.logger.info("location: " + location);
        URI uri = URI.create(location);
        return Operation.createGet(uri);
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

        @Override
        public void handlePost(Operation post) {
            handleOpWithBody(post);
        }

        @Override
        public void handlePut(Operation post) {
            handleOpWithBody(post);
        }

        @Override
        public void handlePatch(Operation post) {
            handleOpWithBody(post);
        }

        private void handleOpWithBody(Operation post) {
            post.setBody(post.getBody(String.class)).complete();
        }

    }

    public static class TestStaticContentService extends StatelessService {

        public static final String SUFFIX = "/lib/main.js";
        public static final String SELF_LINK = "/adapter/shared-content" + SUFFIX;
        public static final String RESPONSE = "OK";

        @Override
        public void handleGet(Operation get) {
            get.setBody(RESPONSE).complete();
        }

    }

}