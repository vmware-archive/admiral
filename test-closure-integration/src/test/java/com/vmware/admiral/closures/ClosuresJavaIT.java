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

import static com.vmware.admiral.closures.drivers.DriverConstants.RUNTIME_JAVA_8;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonPrimitive;
import junit.framework.TestCase;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.BaseClosureIntegrationTest;
import com.vmware.admiral.SimpleHttpsClient;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ResourceConstraints;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

@SuppressWarnings("unchecked")
public class ClosuresJavaIT extends BaseClosureIntegrationTest {

    private static final String IMAGE_NAME_PREFIX = "vmware/photon-closure-runner_";

    private static final String IMAGE_NAME = IMAGE_NAME_PREFIX + RUNTIME_JAVA_8;

    private static ServiceClient serviceClient;
    private static String dockerBuildImageLink;
    private static String dockerBuildBaseImageLink;

    @BeforeClass
    public static void beforeClass() throws Exception {
        serviceClient = ServiceClientFactory.createServiceClient(null);
        setupClosureEnv();
        dockerBuildImageLink = getBaseUrl()
                + createImageBuildRequestUri(IMAGE_NAME + ":2.0", dockerHostCompute
                .documentSelfLink);
        dockerBuildBaseImageLink = getBaseUrl()
                + createImageBuildRequestUri(IMAGE_NAME + "_base:2.0", dockerHostCompute
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
    public void init() throws Throwable {
        logger.info("Executing against docker host: %s ", dockerHostCompute.address);

        try {
            registerExternalDockerRegistry(serviceClient);
        } catch (Exception e) {
            // registry already added
            if (!e.getMessage().contains(RegistryFactoryService.REGISTRY_ALREADY_EXISTS)) {
                throw e;
            }
        }
    }

    @Test
    public void executeJavaHelloWorldTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        closureDescState.source = "import com.vmware.admiral.closure.runtime.Context;\n"
                + "\n"
                + "public class Test {\n"
                + "    public void test(Context context) {\n"
                + "        System.out.println(\"Hello World!\");\n"
                + "    }\n"
                + "}"
                + "\n";
        closureDescState.runtime = RUNTIME_JAVA_8;

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

        int expectedInVar = 3;
        int expectedInVar2 = 4;
        int expectedResult = 7;

        closureDescState.sourceURL = testWebserverUri + "/test_script_java.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_JAVA_8;
        closureDescState.entrypoint = "testpackage.Test.test";

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
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        inputs.put("b", new JsonPrimitive(expectedInVar2));
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        String imageRequestLink = waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);

        assertEquals(expectedInVar, fetchedClosure.inputs.get("a").getAsInt());
        assertEquals(expectedInVar2, fetchedClosure.inputs.get("b").getAsInt());
        assertEquals(expectedResult, fetchedClosure.outputs.get("result").getAsInt(), 0);

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeJavaExtSourceAsJARTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedInVar2 = 4;
        int expectedResult = 7;

        closureDescState.sourceURL = testWebserverUri + "/test_script_java_classes.jar";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_JAVA_8;
        closureDescState.entrypoint = "testpackage.Test.test";

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
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        inputs.put("b", new JsonPrimitive(expectedInVar2));
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        String imageRequestLink = waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);

        assertEquals(expectedInVar, fetchedClosure.inputs.get("a").getAsInt());
        assertEquals(expectedInVar2, fetchedClosure.inputs.get("b").getAsInt());
        assertEquals(expectedResult, fetchedClosure.outputs.get("result").getAsInt(), 0);

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeJavaNumberParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedInVar2 = 4;
        int expectedResult = 7;

        // @formatter:off
        closureDescState.source = "import com.google.gson.JsonObject;\n"
                + "import com.google.gson.JsonPrimitive;\n"
                + "import com.vmware.admiral.closure.runtime.Context;\n"
                + "import java.util.Map;\n"
                + "\n"
                + "public class Test {\n"
                + "    public void test(Context context) {\n"
                + "        Map<String, Object> inputs = context.getInputs();\n"
                + "        int a = ((JsonPrimitive) inputs.get(\"a\")).getAsInt();\n"
                + "        int b = ((JsonPrimitive) inputs.get(\"b\")).getAsInt();\n"
                + "        int result = a + b;\n"
                + "        System.out.println(result);\n"
                + "        context.setOutput(\"result\", result);\n"
                + "    }\n"
                + "}\n";
        // @formatter:on

        closureDescState.runtime = RUNTIME_JAVA_8;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));

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
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        inputs.put("b", new JsonPrimitive(expectedInVar2));
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure closure = getClosure(createdClosure.documentSelfLink, serviceClient);
        TestCase.assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, closure.state);

        assertEquals(expectedInVar, closure.inputs.get("a").getAsInt());
        assertEquals(expectedInVar2, closure.inputs.get("b").getAsInt());
        assertEquals(expectedResult, closure.outputs.get("result").getAsInt(), 0);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

}
