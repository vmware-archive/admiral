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

package com.vmware.admiral.service.common.harbor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;

import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.SslTrustImportService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public class HarborApiProxyServiceTest {

    private static final String HARBOR_URI_FIELD_NAME = "harborUri";
    private static final String HARBOR_CLIENT_FIELD_NAME = "client";
    private static final String HARBOR_USER_FIELD_NAME = "harborUser";
    private static final String HARBOR_PASS_FIELD_NAME = "harborPassword";

    private static final String SAMPLE_API_PATH = "/sample";

    private static final URI SAMPLE_PROXY_URI = UriUtils
            .buildUri("http://localhost" + HarborApiProxyService.SELF_LINK + SAMPLE_API_PATH);
    private static final String SAMPLE_HEADER = "new-header";
    private static final String SAMPLE_HEADER_VALUE = "new-value";
    private static final String SAMPLE_REQUEST_BODY = "request body";
    private static final String SAMPLE_RESPONSE_BODY = "response body";
    private static final URI SAMPLE_HARBOR_URI = UriUtils.buildUri("http://hbr-test.local");
    private static final URI SAMPLE_HARBOR_HTTPS_URI = UriUtils.buildUri("https://hbr-test.local");

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

        HarborApiProxyService service = new HarborApiProxyService();
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

        HarborApiProxyService service = new HarborApiProxyService();

        setPrivateField(service, HARBOR_URI_FIELD_NAME, SAMPLE_HARBOR_URI);

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

        HarborApiProxyService service = new HarborApiProxyService();

        setPrivateField(service, HARBOR_URI_FIELD_NAME, SAMPLE_HARBOR_URI);

        Operation actualOp = Operation.createGet(UriUtils
                .buildUri("http://localhost" + HarborApiProxyService.SELF_LINK))
                .setCompletion((o, e) -> {
                    assertNotNull(e);
                    assertTrue(e.getMessage().contains("Invalid target URI"));

                    completed.set(true);
                });
        service.handleRequest(actualOp);

        assertTrue(completed.get());
    }

    @Test
    public void testI18nPath() {

        String resourcePath = "/" + Harbor.I18N_RESOURCE_SUBPATH + "/messages-en.json";

        HarborApiProxyService service = new HarborApiProxyService();
        service.setHost(VerificationHost.create());
        ServiceClient client = new MockServiceClient() {
            @Override
            public void sendRequest(Operation op) {
                URI targetUri = UriUtils.buildUri(SAMPLE_HARBOR_URI, resourcePath);
                assertEquals(targetUri, op.getUri());
                op.complete();
            }
        };

        setPrivateField(service, HARBOR_URI_FIELD_NAME, SAMPLE_HARBOR_URI);
        setPrivateField(service, HARBOR_CLIENT_FIELD_NAME, client);

        AtomicBoolean completed = new AtomicBoolean();

        Operation actualOp = new Operation();
        actualOp.setAction(Action.GET);
        actualOp.setUri(UriUtils
                .buildUri("http://localhost" + HarborApiProxyService.SELF_LINK + resourcePath));
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
        URI targetUri = UriUtils.buildUri(SAMPLE_HARBOR_URI, SAMPLE_API_PATH);

        HarborApiProxyService service = new HarborApiProxyService();
        service.setHost(VerificationHost.create());
        ServiceClient client = new MockServiceClient() {
            @Override
            public void sendRequest(Operation op) {
                String authHeader = op.getRequestHeader(Operation.AUTHORIZATION_HEADER);
                assertNull("Authorization header should be empty", authHeader);
                op.setStatusCode(Operation.STATUS_CODE_MOVED_PERM);
                op.addResponseHeader(Operation.LOCATION_HEADER, movedLocation);
                op.setUri(targetUri);
                op.complete();
            }
        };

        setPrivateField(service, HARBOR_URI_FIELD_NAME, SAMPLE_HARBOR_URI);
        setPrivateField(service, HARBOR_CLIENT_FIELD_NAME, client);

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

    @Test
    public void testBasicAuth() {
        HarborApiProxyService service = new HarborApiProxyService();
        service.setHost(VerificationHost.create());
        ServiceClient client = new MockServiceClient() {
            @Override
            public void sendRequest(Operation op) {
                String authHeader = op.getRequestHeader(Operation.AUTHORIZATION_HEADER);
                assertNotNull("Authorization header must not be empty", authHeader);
                assertEquals("Basic YWRtaW46cGFzcw==", authHeader);
                op.complete();
            }
        };

        setPrivateField(service, HARBOR_URI_FIELD_NAME, SAMPLE_HARBOR_URI);
        setPrivateField(service, HARBOR_CLIENT_FIELD_NAME, client);
        setPrivateField(service, HARBOR_USER_FIELD_NAME, "admin");
        setPrivateField(service, HARBOR_PASS_FIELD_NAME, "pass");

        Operation actualOp = Operation.createGet(SAMPLE_PROXY_URI);
        service.handleRequest(actualOp);
    }

    private void setPrivateField(HarborApiProxyService service, String fieldName, Object value) {
        Field field = ReflectionUtils.getField(HarborApiProxyService.class, fieldName);
        try {
            field.set(service, value);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void testResponse(Action action, boolean testFailure) {
        HarborApiProxyService service = new HarborApiProxyService();
        service.setHost(VerificationHost.create());

        ServiceClient client = new MockServiceClient() {
            @Override
            public void sendRequest(Operation op) {

                assertEquals(action, op.getAction());
                assertEquals(SAMPLE_REQUEST_BODY, op.getBodyRaw());

                URI targetUri = UriUtils
                        .buildUri(SAMPLE_HARBOR_URI, Harbor.API_BASE_ENDPOINT, SAMPLE_API_PATH);
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

        setPrivateField(service, HARBOR_URI_FIELD_NAME, SAMPLE_HARBOR_URI);
        setPrivateField(service, HARBOR_CLIENT_FIELD_NAME, client);

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

    @Test
    public void testSkipImportSSLCertificateOnStart() {
        AtomicBoolean certificateImportCalled = new AtomicBoolean();

        HarborApiProxyService service = new HarborApiProxyService() {
            @Override
            public void sendRequest(Operation op) {
                String path = op.getUri().getPath();
                if (path.equals(UriUtils.buildUriPath(ManagementUriParts.CONFIG_PROPS,
                        Harbor.CONFIGURATION_URL_PROPERTY_NAME))) {
                    ConfigurationState state = new ConfigurationState();
                    state.key = Harbor.CONFIGURATION_URL_PROPERTY_NAME;
                    state.value = SAMPLE_HARBOR_URI.toString();
                    op.setBody(state);
                    op.complete();
                } else if (path.equals(SslTrustImportService.SELF_LINK)) {
                    certificateImportCalled.set(true);
                }
            }
        };
        service.setHost(VerificationHost.create());
        service.handleStart(new Operation());

        assertFalse(certificateImportCalled.get());
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
        public ConnectionPoolMetrics getConnectionPoolMetrics(boolean http2) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public ConnectionPoolMetrics getConnectionPoolMetricsPerTag(String connectionTag) {
            // TODO Auto-generated method stub
            return null;
        }
    }

}
