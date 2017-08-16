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

import com.vmware.admiral.unikernels.common.exceptions.DockerFileFormatException;
import com.vmware.admiral.unikernels.common.service.UnikernelCreationTaskService.UnikernelCreationTaskServiceState;
import com.vmware.admiral.unikernels.common.translator.Parser;
import com.vmware.admiral.unikernels.common.translator.Platform;
import com.vmware.admiral.unikernels.common.translator.Translator;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.test.TestContext;

public class TranslateDockerServiceTest {

    private ServiceHost host;
    private CompilationData response;

    @Before
    public void createAndStartServiceHost() throws Throwable {
        host = ServiceHost.create();
        host.start();
        host.startDefaultCoreServicesSynchronously();
        host.startService(new TranslateDockerService());
        host.startService(new TaskServiceMock());
        waitForServiceAvailability(host,
                TranslateDockerService.SELF_LINK,
                TaskServiceMock.SELF_LINK);
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

    public class TaskServiceMock extends StatelessService {

        public static final String SELF_LINK = UnikernelManagementURIParts.CREATION;

        @Override
        public void handlePost(Operation post) {
            response = post.getBody(UnikernelCreationTaskServiceState.class).data;
            post.complete();
        }

    }

    @Test
    public void translationCheck() {
        TranslationData translationData = new TranslationData();
        translationData.dockerfile = "FROM cloudrouter/osv-builder"
                + "\nCOPY OSv-service.jar ."
                + "\nWORKDIR /"
                + "\nCMD java -jar OSv-service.jar";
        translationData.sources = "github";
      //  translationData.platform = Platform.OSv;
        translationData.successCB = "success";
        translationData.failureCB = "failure";
        translationData.compilationPlatform = "vbox";

        TestContext ctx = TestContext.create(1, Duration.ofSeconds(60).toMillis() * 1000);
        Operation.createPost(host, UnikernelManagementURIParts.TRANSLATION)
                .setBody(translationData)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        fail(e.getMessage());
                    } else {
                        ctx.complete();
                    }
                }).sendWith(host);
        ctx.await();

        assertEquals(response.capstanfile, translateDockerUtil(translationData.dockerfile));
        assertEquals(response.failureCB, "failure");
        assertEquals(response.successCB, "success");
        assertEquals(response.sources, "github");
        assertEquals(response.compilationPlatform, "vbox");
    }

    private String translateDockerUtil(String docker) {
        Parser parser = new Parser();
        parser.readString(docker);
        try {
            Translator translator = new Translator(parser.parseDocker());
            return translator.translate(Platform.OSv).getDocumentString();
        } catch (DockerFileFormatException e) {
            e.printStackTrace();
        }

        return null;
    }
}
