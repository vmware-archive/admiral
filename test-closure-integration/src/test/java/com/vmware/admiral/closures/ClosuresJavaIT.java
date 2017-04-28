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

package com.vmware.admiral.closures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import junit.framework.TestCase;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.BaseClosureIntegrationTest;
import com.vmware.admiral.SimpleHttpsClient;
import com.vmware.admiral.closures.drivers.DriverConstants;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ResourceConstraints;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

@Ignore
public class ClosuresJavaIT extends BaseClosureIntegrationTest {

    protected static String IMAGE_NAME_PREFIX = "vmware/photon-closure-runner_";

    private static final String IMAGE_NAME =
            IMAGE_NAME_PREFIX + DriverConstants.RUNTIME_JAVA_8;

    private static String testWebserverUri;

    private static String RUNTIME_JAVA = "java";

    private static ServiceClient serviceClient;

    private static String dockerBuildImageLink;
    private static String dockerBuildBaseImageLink;

    @BeforeClass
    public static void beforeClass() throws Exception {
        serviceClient = ServiceClientFactory.createServiceClient(null);
        testWebserverUri = getTestWebServerUrl();

        setupCoreOsHost(ContainerHostService.DockerAdapterType.API, false);
        dockerBuildImageLink = getBaseUrl()
                + createImageBuildRequestUri(IMAGE_NAME + ":1.0", dockerHostCompute
                .documentSelfLink);
        dockerBuildBaseImageLink = getBaseUrl()
                + createImageBuildRequestUri(IMAGE_NAME + "_base:1.0", dockerHostCompute
                .documentSelfLink);
    }

    @AfterClass
    public static void afterClass()
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException,
            KeyManagementException,
            IOException {
        if (dockerBuildImageLink != null) {
            SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.DELETE, dockerBuildImageLink);
        }
        if (dockerBuildBaseImageLink != null) {
            SimpleHttpsClient
                    .execute(SimpleHttpsClient.HttpMethod.DELETE, dockerBuildBaseImageLink);
        }
        serviceClient.stop();
    }

    @Before
    public void init() {
        logger.info("Executing against docker host: %s ", dockerHostCompute.address);
    }

    @Test
    public void executeJavaHelloWorldTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        closureDescState.source = "class test {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"Hello World!\");\n"
                + "    }\n"
                + "}"
                + "\n";
        closureDescState.runtime = RUNTIME_JAVA;

        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 10;
        constraints.ramMB = 200;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceClient);

        // Execute the created Closure
        Closure closureRequest = new Closure();

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure closure = getClosure(createdClosure.documentSelfLink, serviceClient);
        TestCase.assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, closure.state);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeJavaExtSourceAsZIPTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        closureDescState.sourceURL = testWebserverUri + "/test_script_java.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_JAVA;

        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 10;
        constraints.ramMB = 300;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);
        assertNotNull(closureDescription);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceClient);
        assertEquals(closureDescription.documentSelfLink, createdClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CREATED, createdClosure.state);

        // Execute the created Closure
        Closure closureRequest = new Closure();

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        String imageRequestLink = waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }
}
