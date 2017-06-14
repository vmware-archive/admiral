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
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.vmware.xenon.common.Claims.Builder;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.ServiceHost.Arguments;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public class UiServiceTest {

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
    public void testForwardIndexHtml() {
        UiService service = new UiService();
        service.setSelfLink("/");
        VerificationHost vh = new VerificationHost() {
            @Override
            public void sendRequest(Operation op) {
                if (op.getUri().getPath().equals("/index.html")) {
                    op.setBody("OK");
                    op.complete();
                }
            }
        };
        service.setHost(vh);

        AtomicBoolean completionCalled = new AtomicBoolean();

        service.handleGet(new Operation().setUri(UriUtils.buildUri("http://localhost/"))
                .setCompletion((o, e) -> {
                    assertEquals("OK", o.getBodyRaw());
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
        AuthorizationContext ctx = new AuthorizationContext();

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
