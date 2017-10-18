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

package com.vmware.admiral;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.Claims.Builder;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.ServiceHost.Arguments;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public class UiServiceTest {

    @Before
    public void before() {
        // see comment in UiService's isEmbedded assignment
        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.EMBEDDED_MODE_PROPERTY;
        config.value = Boolean.toString(false);
        ConfigurationUtil.initialize(config);
    }

    @Test
    public void testRedirect() {
        UiService service = new UiService();
        service.setSelfLink("/sample");
        service.setHost(new VerificationHost());

        AtomicBoolean completionCalled = new AtomicBoolean();

        service.handleGet(new Operation().setUri(UriUtils.buildUri("http://localhost/sample"))
                .setCompletion((o, e) -> {
                    assertEquals("/sample/", o.getResponseHeader(Operation.LOCATION_HEADER));
                    completionCalled.set(true);

                }));

        assertTrue(completionCalled.get());
    }

    @Test
    public void testForwardIndexHtmlWithXFrameOptions() {
        UiService service = new UiService();
        service.setSelfLink("/");
        VerificationHost vh = new VerificationHost() {
            @Override
            public void sendRequest(Operation op) {
                if (op.getUri().getPath().equals("/index.html")) {
                    op.setBody("OK");
                    op.complete();
                } else {
                    op.fail(Operation.STATUS_CODE_NOT_FOUND);
                }
            }
        };
        service.setHost(vh);

        AtomicBoolean completionCalled = new AtomicBoolean();

        service.handleGet(new Operation().setUri(UriUtils.buildUri("http://localhost/"))
                .setCompletion((o, e) -> {
                    assertEquals("OK", o.getBodyRaw());
                    assertEquals("DENY",
                            o.getResponseHeader(ConfigurationUtil.UI_FRAME_OPTIONS_HEADER));
                    completionCalled.set(true);

                }));

        assertTrue(completionCalled.get());
    }

    @Test
    public void testForwardIndexHtmlWithoutXFrameOptions() {
        UiService service = new UiService();
        service.setSelfLink("/");
        VerificationHost vh = new VerificationHost() {
            @Override
            public void sendRequest(Operation op) {
                if (op.getUri().getPath().equals("/index.html")) {
                    op.setBody("OK");
                    op.complete();
                } else if (op.getUri().getPath().equals("/config/props/embedded")) {
                    ConfigurationState body = new ConfigurationState();
                    body.key = ConfigurationUtil.EMBEDDED_MODE_PROPERTY;
                    body.value = Boolean.toString(true);
                    op.setBody(body);
                    op.complete();
                } else {
                    op.fail(Operation.STATUS_CODE_NOT_FOUND);
                }
            }
        };
        service.setHost(vh);

        // see comment in UiService's isEmbedded assignment
        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.EMBEDDED_MODE_PROPERTY;
        config.value = Boolean.toString(true);
        ConfigurationUtil.initialize(config);

        AtomicBoolean completionCalled = new AtomicBoolean();

        service.handleGet(new Operation().setUri(UriUtils.buildUri("http://localhost/"))
                .setCompletion((o, e) -> {
                    assertEquals("OK", o.getBodyRaw());
                    assertNull(o.getResponseHeader(ConfigurationUtil.UI_FRAME_OPTIONS_HEADER));
                    completionCalled.set(true);

                }));

        assertTrue(completionCalled.get());
    }

    @Test
    public void testRedirectToLogin() {
        UiService service = new UiService();
        service.setSelfLink("/");
        VerificationHost vh = new VerificationHost();
        vh.setAuthorizationEnabled(true);
        vh.setAuthorizationContext(createAuthorizationContext(false));
        service.setHost(vh);

        AtomicBoolean completionCalled = new AtomicBoolean();

        service.handleGet(new Operation().setUri(UriUtils.buildUri("http://localhost/sample"))
                .setCompletion((o, e) -> {
                    assertEquals("/login/", o.getResponseHeader(Operation.LOCATION_HEADER));
                    completionCalled.set(true);

                }));

        assertTrue(completionCalled.get());
    }

    @Test
    public void testRedirectToBase() {
        UiService service = new UiService();
        service.setSelfLink("/");
        VerificationHost vh = new VerificationHost();
        vh.setAuthorizationEnabled(true);
        vh.setAuthorizationContext(createAuthorizationContext(true));
        service.setHost(vh);

        AtomicBoolean completionCalled = new AtomicBoolean();

        service.handleGet(new Operation().setUri(UriUtils.buildUri("http://localhost/login/"))
                .setCompletion((o, e) -> {
                    assertEquals("/", o.getResponseHeader(Operation.LOCATION_HEADER));
                    completionCalled.set(true);

                }));

        assertTrue(completionCalled.get());
    }

    @Test
    public void testDiscoverFileResources() throws Throwable {
        UiService service = new UiService();
        service.setSelfLink("/");

        Arguments args = new Arguments();
        args.resourceSandbox = Paths
                .get("src/main/resources/ui/com/vmware/admiral/UiService/container-identicons");
        VerificationHost vh = VerificationHost.create(args);
        service.setHost(vh);

        Map<Path, String> discoverUiResources = service.discoverUiResources(
                Paths.get("./"), service);

        assertFalse(discoverUiResources.isEmpty());
    }

    @Test
    public void testDiscoverFileResourcesOnStart() throws Throwable {
        UiService service = new UiService();
        service.setSelfLink("/");

        Arguments args = new Arguments();
        args.resourceSandbox = Paths.get("src/main/resources/");
        VerificationHost vh = VerificationHost.create(args);
        service.setHost(vh);

        Operation start = new Operation().setUri(UriUtils.buildUri("/"));

        service.handleStart(start);

        assertEquals(Operation.STATUS_CODE_OK, start.getStatusCode());
    }

    @Test
    public void testNgRedirect() {
        UiNgService service = new UiNgService();
        AtomicBoolean completionCalled = new AtomicBoolean();

        service.handleGet(new Operation().setUri(UriUtils.buildUri("http://localhost/sample"))
                .setCompletion((o, e) -> {
                    assertEquals("../", o.getResponseHeader(Operation.LOCATION_HEADER));
                    completionCalled.set(true);

                }));

        assertTrue(completionCalled.get());
    }

    private static AuthorizationContext createAuthorizationContext(boolean withValidUser) {
        Builder claimsBuilder = new Builder();
        if (withValidUser) {
            claimsBuilder.setSubject("some-user");
        }
        com.vmware.xenon.common.Operation.AuthorizationContext.Builder ctxBuilder = com.vmware.xenon.common.Operation.AuthorizationContext.Builder
                .create();
        ctxBuilder.setClaims(claimsBuilder.getResult());
        return ctxBuilder.getResult();
    }

}
