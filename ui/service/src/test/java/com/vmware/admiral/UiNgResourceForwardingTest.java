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
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.vmware.admiral.BaseUiService.UiNgResourceForwarding;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

public class UiNgResourceForwardingTest {

    @Test
    public void testAuthorizeRequest() {
        UiNgResourceForwarding service = new UiNgResourceForwarding("/sample", "/sample-new");

        AtomicBoolean completionCalled = new AtomicBoolean();

        service.authorizeRequest(new Operation().setCompletion((o, e) -> {
            completionCalled.set(true);
        }));

        assertTrue(completionCalled.get());
    }

    @Test
    public void testRedirect() {
        UiNgResourceForwarding service = new UiNgResourceForwarding("/sample", "/sample-new", true);
        service.setHost(VerificationHost.create());

        AtomicBoolean completionCalled = new AtomicBoolean();

        service.handleGet(new Operation().setUri(UriUtils.buildUri("http://localhost/sample"))
                .setCompletion((o, e) -> {
                    assertEquals("/sample-new", o.getResponseHeader(Operation.LOCATION_HEADER));
                    completionCalled.set(true);

                }));

        assertTrue(completionCalled.get());
    }

    @Test
    public void testForward() {
        UiNgResourceForwarding service = new UiNgResourceForwarding("/sample", "/sample-new") {
            @Override
            public void sendRequest(Operation op) {
                if (op.getUri().getPath().equals("/sample-new")) {
                    op.setBody("OK");
                    op.complete();
                }
            }
        };
        service.setHost(VerificationHost.create());

        AtomicBoolean completionCalled = new AtomicBoolean();

        service.handleGet(new Operation().setUri(UriUtils.buildUri("http://localhost/sample"))
                .setCompletion((o, e) -> {
                    assertEquals("OK", o.getBodyRaw());
                    completionCalled.set(true);

                }));

        assertTrue(completionCalled.get());
    }

    @Test
    public void testForwardIndexHtml() {
        UiNgResourceForwarding service = new UiNgResourceForwarding("/sample/", "/sample-new/") {
            @Override
            public void sendRequest(Operation op) {
                if (op.getUri().getPath().equals("/sample-new/index.html")) {
                    op.setBody("OK");
                    op.complete();
                }
            }
        };
        service.setHost(VerificationHost.create());

        AtomicBoolean completionCalled = new AtomicBoolean();

        service.handleGet(new Operation().setUri(UriUtils.buildUri("http://localhost/sample/"))
                .setCompletion((o, e) -> {
                    assertEquals("OK", o.getBodyRaw());
                    completionCalled.set(true);

                }));

        assertTrue(completionCalled.get());
    }

}
