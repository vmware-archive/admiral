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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;

import org.junit.Test;

import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public class HbrApiProxyServiceTest {

    private static final String SAMPLE_API_PATH = "/sample";

    private static final URI SAMPLE_PROXY_URI = UriUtils
            .buildUri("http://localhost" + HbrApiProxyService.SELF_LINK + SAMPLE_API_PATH);
    private static final String SAMPLE_HEADER = "new-header";
    private static final String SAMPLE_HEADER_VALUE = "new-value";
    private static final String SAMPLE_REQUEST_BODY = "request body";
    private static final String SAMPLE_RESPONSE_BODY = "response body";
    private static final String SAMPLE_HARBOR_URL = "http://hbr-test.local";

    private static final String HBR_API_BASE_ENDPOINT = "/api";
    private static final String I18N_RESOURCE_SUBPATH = "/i18n/lang";

    @Test
    public void testForwardRequest() {
        testResponse(Action.POST, false);
        testResponse(Action.PUT, false);
        testResponse(Action.PATCH, false);
        testResponse(Action.OPTIONS, false);
        testResponse(Action.GET, false);
        testResponse(Action.DELETE, false);

        testResponse(Action.POST, true);
        testResponse(Action.PUT, true);
        testResponse(Action.PATCH, true);
        testResponse(Action.OPTIONS, true);
        testResponse(Action.GET, true);
        testResponse(Action.DELETE, true);
    }

    @Test
    public void testNoHbrUrlProvided() {
        AtomicBoolean completed = new AtomicBoolean();

        HbrApiProxyService service = new HbrApiProxyService();
        Operation actualOp = Operation.createGet(SAMPLE_PROXY_URI)
                .setCompletion((o, e) -> {
                    assertNotNull(e);
                    assertTrue(e.getMessage()
                            .contains("Configuration property harbor.tab.url not provided"));

                    completed.set(true);
                });
        service.handleRequest(actualOp);

        assertTrue(completed.get());
    }

    @Test
    public void testWrongSelfLink() {
        AtomicBoolean completed = new AtomicBoolean();

        HbrApiProxyService service = new HbrApiProxyService();
        Field field = ReflectionUtils.getField(HbrApiProxyService.class, "harborUrl");
        try {
            field.set(service, SAMPLE_HARBOR_URL);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        Operation actualOp = Operation.createGet(UriUtils
                .buildUri("http://localhost/some-path"))
                .setCompletion((o, e) -> {
                    assertNotNull(e);
                    assertTrue(e.getMessage()
                            .contains("Invalid target URI"));

                    completed.set(true);
                });
        service.handleRequest(actualOp);

        assertTrue(completed.get());
    }

    @Test
    public void testNoPath() {
        AtomicBoolean completed = new AtomicBoolean();

        HbrApiProxyService service = new HbrApiProxyService();
        Field field = ReflectionUtils.getField(HbrApiProxyService.class, "harborUrl");
        try {
            field.set(service, SAMPLE_HARBOR_URL);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        Operation actualOp = Operation.createGet(UriUtils
                .buildUri("http://localhost" + HbrApiProxyService.SELF_LINK))
                .setCompletion((o, e) -> {
                    assertNotNull(e);
                    assertTrue(e.getMessage()
                            .contains("Invalid target URI"));

                    completed.set(true);
                });
        service.handleRequest(actualOp);

        assertTrue(completed.get());
    }

    @Test
    public void testI18nPath() {

        String resourcePath = I18N_RESOURCE_SUBPATH + "/messages-en.json";

        HbrApiProxyService service = new HbrApiProxyService();
        service.setHost(VerificationHost.create());
        ServiceClient client = new MockServiceClient() {
            @Override
            public void sendRequest(Operation op) {
                URI targetUri = UriUtils.buildUri(SAMPLE_HARBOR_URL + resourcePath);
                assertEquals(targetUri, op.getUri());
                op.complete();
            }
        };

        Field field = ReflectionUtils.getField(HbrApiProxyService.class, "harborUrl");
        try {
            field.set(service, SAMPLE_HARBOR_URL);
        } catch (Throwable e) {
        }

        field = ReflectionUtils.getField(HbrApiProxyService.class, "client");
        try {
            field.set(service, client);
        } catch (Throwable e) {
        }

        AtomicBoolean completed = new AtomicBoolean();

        Operation actualOp = new Operation();
        actualOp.setAction(Action.GET);
        actualOp.setUri(UriUtils
                .buildUri("http://localhost" + HbrApiProxyService.SELF_LINK + resourcePath));
        actualOp.setCompletion((o, e) -> {
            assertNull(e);
            completed.set(true);
        });
        service.handleRequest(actualOp);

        assertTrue(completed.get());
    }

    @Test
    public void testRedirect() {
        String movedLocation = "moved";
        URI targetUri = UriUtils.buildUri(SAMPLE_HARBOR_URL + SAMPLE_API_PATH);

        HbrApiProxyService service = new HbrApiProxyService();
        service.setHost(VerificationHost.create());
        ServiceClient client = new MockServiceClient() {
            @Override
            public void sendRequest(Operation op) {
                op.setStatusCode(Operation.STATUS_CODE_MOVED_PERM);
                op.addResponseHeader(Operation.LOCATION_HEADER, movedLocation);
                op.setUri(targetUri);
                op.complete();
            }
        };

        Field field = ReflectionUtils.getField(HbrApiProxyService.class, "harborUrl");
        try {
            field.set(service, SAMPLE_HARBOR_URL);
        } catch (Throwable e) {
        }

        field = ReflectionUtils.getField(HbrApiProxyService.class, "client");
        try {
            field.set(service, client);
        } catch (Throwable e) {
        }

        AtomicBoolean completed = new AtomicBoolean();

        Operation actualOp = new Operation();
        actualOp.setAction(Action.GET);
        actualOp.setUri(SAMPLE_PROXY_URI);
        actualOp.setCompletion((o, e) -> {
            assertNull(e);
            String newLocation = o.getResponseHeader(Operation.LOCATION_HEADER);

            String expectedLocation = UriUtilsExtended.getReverseProxyLocation(movedLocation,
                    targetUri, SAMPLE_PROXY_URI);

            assertEquals(expectedLocation, newLocation);
            completed.set(true);
        });
        service.handleRequest(actualOp);

        assertTrue(completed.get());
    }

    private void testResponse(Action action, boolean testFailure) {
        HbrApiProxyService service = new HbrApiProxyService();
        service.setHost(VerificationHost.create());

        ServiceClient client = new MockServiceClient() {
            @Override
            public void sendRequest(Operation op) {

                assertEquals(action, op.getAction());
                assertEquals(SAMPLE_REQUEST_BODY, op.getBodyRaw());

                URI targetUri = UriUtils
                        .buildUri(SAMPLE_HARBOR_URL + HBR_API_BASE_ENDPOINT + SAMPLE_API_PATH);
                assertEquals(targetUri, op.getUri());

                op.setBodyNoCloning(SAMPLE_RESPONSE_BODY);

                if (testFailure) {
                    op.setStatusCode(Operation.STATUS_CODE_INTERNAL_ERROR);
                } else {
                    op.addResponseHeader(SAMPLE_HEADER, SAMPLE_HEADER_VALUE);
                }
                op.complete();
            }
        };

        Field field = ReflectionUtils.getField(HbrApiProxyService.class, "harborUrl");
        try {
            field.set(service, SAMPLE_HARBOR_URL);
        } catch (Throwable e) {
        }

        field = ReflectionUtils.getField(HbrApiProxyService.class, "client");
        try {
            field.set(service, client);
        } catch (Throwable e) {
        }

        AtomicBoolean completed = new AtomicBoolean();

        Operation actualOp = new Operation();
        actualOp.setAction(action);
        actualOp.setUri(
                SAMPLE_PROXY_URI);
        actualOp.setBodyNoCloning(SAMPLE_REQUEST_BODY);
        actualOp.setCompletion((o, e) -> {
            assertNull(e);

            if (testFailure) {
                assertEquals(Operation.STATUS_CODE_INTERNAL_ERROR, o.getStatusCode());
            } else {
                assertEquals(SAMPLE_HEADER_VALUE, o.getResponseHeaderAsIs(SAMPLE_HEADER));
            }

            assertEquals(SAMPLE_RESPONSE_BODY, o.getBodyRaw());
            completed.set(true);
        });
        service.handleRequest(actualOp);

        assertTrue(completed.get());
    }

    private static class MockServiceClient implements ServiceClient {
        @Override
        public void sendRequest(Operation op) {
        }

        @Override
        public ServiceClient setSSLContext(SSLContext context) {
            return null;
        }

        @Override
        public SSLContext getSSLContext() {
            return null;
        }

        @Override
        public void start() {
        }

        @Override
        public void handleMaintenance(Operation op) {
        }

        @Override
        public void stop() {
        }

        @Override
        public void send(Operation op) {
            this.sendRequest(op);
        }

        @Override
        public ServiceClient setConnectionLimitPerTag(String connectionTag, int limit) {
            return null;
        }

        @Override
        public int getConnectionLimitPerTag(String connectionTag) {
            return 0;
        }

        @Override
        public ServiceClient setPendingRequestQueueLimit(int limit) {
            return null;
        }

        @Override
        public int getPendingRequestQueueLimit() {
            return 0;
        }

        @Override
        public ServiceClient setRequestPayloadSizeLimit(int limit) {
            return null;
        }

        @Override
        public int getRequestPayloadSizeLimit() {
            return 0;
        }

        @Override
        public ConnectionPoolMetrics getConnectionPoolMetrics(String connectionTag) {
            return null;
        }
    }
}
