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

package com.vmware.admiral.unikernels.osv.compilation.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.Duration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.unikernels.osv.compilation.service.CompilationTaskService.CompilationTaskServiceState;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;

public class CompilationTaskServiceTest {

    public static final int timeForTaskServiceCompletionSeconds = 100;

    private ServiceHost host;
    private TestRequestSender sender;
    private String successLink = "";
    private String failureLink = "";

    @Before
    public void createAndStartServiceHost() throws Throwable {
        host = ServiceHost.create();
        host.start();
        host.startDefaultCoreServicesSynchronously();
        host.startService(new FailureMockService());
        host.startService(new SuccessMockService());
        host.startFactory(new CompilationTaskService());

        waitForServiceAvailability(host,
                FailureMockService.SELF_LINK,
                SuccessMockService.SELF_LINK,
                UnikernelManagementURIParts.COMPILE_TASK);

        this.sender = new TestRequestSender(this.host);
    }

    @After
    public void shutDownHost() {
        host.stop();
    }

    public class SuccessMockService extends StatelessService {
        public static final String SELF_LINK = UnikernelManagementURIParts.SAMPLE_SUCCESSCB;

        @Override
        public void handlePost(Operation post) {
            successLink = post.getBody(String[].class)[0];
        }

    }

    public class FailureMockService extends StatelessService {
        public static final String SELF_LINK = UnikernelManagementURIParts.SAMPLE_FAILURECB;

        @Override
        public void handlePost(Operation post) {
            failureLink = post.getBody(String[].class)[0];
        }

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

    @Test
    public void testTaskCompletion() {
        invokeService();
        waitCompilationTaskServiceCompletion(timeForTaskServiceCompletionSeconds);
        assertEquals(successLink, "http://localhost:8000/success");
    }

    private void waitCompilationTaskServiceCompletion(int timeInSeconds) {
        Long startTime = System.currentTimeMillis();

        while (!receivedCB()) {
            try {
                if ((System.currentTimeMillis() - startTime) / 1000 > timeInSeconds) {
                    fail("Could not compute within the specified time.");
                }
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void invokeService() {
        CompilationTaskServiceState stateBody = new CompilationTaskServiceState();
        stateBody.data = new CompilationData();

        stateBody.data.capstanfile = "base: cloudius/osv-openjdk "
                + "\ncmdline: /java.so -jar /app.jar "
                + "\nfiles: "
                + "\n  /app.jar: OSv-service.jar";

        stateBody.data.compilationPlatform = "vbox";
        stateBody.data.sources = "https://github.com/antonOO/JarSourceUnikernel.git";
        stateBody.data.successCB = "http://localhost:8000/success";
        stateBody.data.failureCB = "http://localhost:8000/failure";

        Operation post = Operation.createPost(host, UnikernelManagementURIParts.COMPILE_TASK)
                .setBody(stateBody);
        sender.sendAndWait(post, CompilationTaskServiceState.class);
    }

    private boolean receivedCB() {
        return !successLink.equals("") || !failureLink.equals("");
    }
}
