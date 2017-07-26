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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.unikernels.common.service.UnikernelCreationTaskService.SubStage;
import com.vmware.admiral.unikernels.common.service.UnikernelCreationTaskService.UnikernelCreationTaskServiceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;

public class UnikernelCreationTaskServiceTest {

    private ServiceHost host;
    private TestRequestSender sender;
    private String receivedLink;
    private boolean storedReceivedLink = false;

    @Before
    public void createAndStartServiceHost() throws Throwable {
        host = ServiceHost.create();
        host.start();
        host.startDefaultCoreServicesSynchronously();
        host.startService(new CompilationMockService());
        host.startService(new DownloadMockService());
        host.startFactory(new UnikernelCreationTaskService());

        waitForServiceAvailability(host,
                UnikernelCreationTaskService.FACTORY_LINK,
                DownloadMockService.SELF_LINK,
                CompilationMockService.SELF_LINK);
        this.sender = new TestRequestSender(this.host);
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

    @After
    public void shutDownHost() {
        host.stop();
    }

    public class CompilationMockService extends StatelessService {
        public static final String SELF_LINK = UnikernelManagementURIParts.COMPILATION_EXTERNAL;

        @Override
        public void handlePost(Operation post) {

            UnikernelCreationTaskServiceState state = new UnikernelCreationTaskServiceState();
            CompilationData data = post.getBody(CompilationData.class);
            data.downloadLink = "download";
            state.data = data;
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskStage.STARTED;
            state.subStage = SubStage.HANDLE_CALLBACK;

            Operation request = Operation.createPatch(post.getReferer())
                    .setReferer(getSelfLink())
                    .setBody(state);

            sendRequest(request);
        }
    }

    public class DownloadMockService extends StatelessService {
        public static final String SELF_LINK = UnikernelManagementURIParts.DOWNLOAD;

        @Override
        public void handlePost(Operation post) {
            receivedLink = post.getBody(String.class);

            UnikernelCreationTaskServiceState state = new UnikernelCreationTaskServiceState();
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskStage.FINISHED;

            Operation request = Operation.createPatch(post.getReferer())
                    .setReferer(getSelfLink())
                    .setBody(state);

            storedReceivedLink = true;
            sendRequest(request);
        }
    }

    @Test
    public void testDataFlow() {
        invokeService();
        waitCompilationTaskServiceCompletion(60);
        assertEquals(receivedLink, "download");
    }

    public void invokeService() {
        CompilationData data = new CompilationData();
        data.capstanfile = "capstan";
        data.compilationPlatform = "vbox";
        data.sources = "git";
        data.successCB = "success";
        data.failureCB = "failure";

        UnikernelCreationTaskServiceState state = new UnikernelCreationTaskServiceState();
        state.data = data;

        Operation post = Operation.createPost(host, UnikernelManagementURIParts.CREATION)
                .setBody(state);
        sender.sendAndWait(post, UnikernelCreationTaskServiceState.class);
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

    public boolean receivedCB() {
        return !(receivedLink == null) && storedReceivedLink;
    }

}
