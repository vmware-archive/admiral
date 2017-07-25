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

package com.vmware.admiral.unikernels.common.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.test.TestContext;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DownloadRequestServiceTest {

    private ServiceHost host;
    private ArrayList<String> response;

    @Before
    public void createAndStartServiceHost() throws Throwable {
        host = ServiceHost.create();
        host.start();
        host.startDefaultCoreServicesSynchronously();
        host.startService(new TaskServiceMock());
        host.startService(new DownloadRequestService());
        waitForServiceAvailability(host, TaskServiceMock.SELF_LINK,
                DownloadRequestService.SELF_LINK);
    }

    protected void waitForServiceAvailability(ServiceHost h, String... serviceLinks)
            throws Throwable {
        if (serviceLinks == null || serviceLinks.length == 0) {
            throw new IllegalArgumentException("null or empty serviceLinks");
        }
        TestContext ctx = TestContext.create(1, Duration.ofSeconds(60).toMillis() * 1000);
        h.registerForServiceAvailability(ctx.getCompletion(), serviceLinks);
        ctx.await();
    }

    public class TaskServiceMock extends StatelessService {

        public static final String SELF_LINK = UnikernelManagementURIParts.CREATION;

        @Override
        public void handlePatch(Operation patch) {
            patch.complete();
        }
    }

    @After
    public void shutDownHost() {
        host.stop();
    }

    @Test
    public void emptyContentsTest() {
        TestContext ctx = TestContext.create(1, Duration.ofSeconds(60).toMillis() * 1000);
        Operation.createGet(host, UnikernelManagementURIParts.DOWNLOAD)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        fail(e.getMessage());
                    } else {
                        response = o.getBody(ArrayList.class);
                        ctx.complete();
                    }
                }).sendWith(host);
        ctx.await();

        assertEquals(response.size(), 0);
    }

    @Test
    public void nonEmptyTest() {
        postURL();
        TestContext ctx = TestContext.create(1, Duration.ofSeconds(60).toMillis() * 1000);
        Operation.createGet(host, UnikernelManagementURIParts.DOWNLOAD)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        fail(e.getMessage());
                    } else {
                        response = o.getBody(ArrayList.class);
                        ctx.complete();
                    }
                }).sendWith(host);
        ctx.await();

        assertEquals(response.size(), 1);
    }

    private void postURL() {
        TestContext ctx = TestContext.create(1, Duration.ofSeconds(60).toMillis() * 1000);
        Operation.createPost(host, UnikernelManagementURIParts.DOWNLOAD)
                .setBody("TEST_URL.com")
                .setReferer(host.getUri() + UnikernelManagementURIParts.CREATION)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        fail(e.getMessage());
                    } else {
                        ctx.complete();
                    }
                }).sendWith(host);
        ctx.await();
    }
}
