/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.closures.drivers.DriverConstants.RUNTIME_POWERSHELL_6;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import junit.framework.TestCase;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.BaseClosureIntegrationTest;
import com.vmware.admiral.SimpleHttpsClient;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ResourceConstraints;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

@SuppressWarnings("unchecked")
@Ignore("VBV-1927")
public class ClosuresPowerShellIT extends BaseClosureIntegrationTest {

    private static final String IMAGE_NAME_PREFIX = "vmware/photon-closure-runner_";

    private static final String IMAGE_NAME = IMAGE_NAME_PREFIX + RUNTIME_POWERSHELL_6;

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

        registerExternalDockerRegistry(serviceClient);
    }

    @Test
    public void executePowerShellHelloWorldTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    Write-Host \"Hello, world!\""
                + "}\n";
        closureDescState.runtime = RUNTIME_POWERSHELL_6;

        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);

        // Create Closuredriverc
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
    public void executePowershellNumberParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedInVar2 = 4;
        int expectedResult = 7;

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    $result = $inputs.a + $inputs.b\n"
                + "    $context.outputs = @{\"result\" = $result} | ConvertTo-JSON\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));

        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
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

    @Test
    public void executePowershellArrayOfNumberParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        Integer[] expectedInVar = { 1, 2, 3 };
        Integer[] expectedResult = { 2, 3, 4 };

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    $inputs.a[0] = $inputs.a[0] + 1\n"
                + "    $inputs.a[1] = $inputs.a[1] + 1\n"
                + "    $inputs.a[2] = $inputs.a[2] + 1\n"
                + "    $result = $inputs.a\n"
                + "    $context.outputs = @{\"result\" = $result} | ConvertTo-JSON\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceClient);

        // Executing the created Closure
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();

        JsonArray jsArray = new JsonArray();
        for (int x : expectedInVar) {
            jsArray.add(new JsonPrimitive(x));
        }
        inputs.put("a", jsArray);
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure finishedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(createdClosure.descriptionLink, finishedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, finishedClosure.state);

        verifyJsonArrayInts(expectedInVar, finishedClosure.inputs.get("a").getAsJsonArray());
        verifyJsonArrayInts(expectedResult, finishedClosure.outputs.get("result").getAsJsonArray());

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePowershellStringParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedResult = "ac";

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    $result = $inputs.a + 'c'\n"
                + "    $context.outputs = @{\"result\" = $result} | ConvertTo-JSON\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
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
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);

        assertEquals(expectedInVar, fetchedClosure.inputs.get("a").getAsString());
        assertEquals(expectedResult, fetchedClosure.outputs.get("result").getAsString());

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePowershellArrayOfStringParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String[] expectedInVar = { "a", "b", "c" };
        String[] expectedResult = { "a_t", "b_t", "c_t" };

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    $inputs.a[0] = $inputs.a[0] + '_t'\n"
                + "    $inputs.a[1] = $inputs.a[1] + '_t'\n"
                + "    $inputs.a[2] = $inputs.a[2] + '_t'\n"
                + "\n"
                + "    $result = $inputs.a\n"
                + "    $context.outputs = @{\"result\" = $result} | ConvertTo-JSON\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);
        assertNotNull(closureDescription);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceClient);
        assertEquals(closureDescription.documentSelfLink, createdClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CREATED, createdClosure.state);

        // Executing the created Closure
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();

        JsonArray jsArray = new JsonArray();
        for (String x : expectedInVar) {
            jsArray.add(new JsonPrimitive(x));
        }
        inputs.put("a", jsArray);
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);
        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);

        verifyJsonArrayStrings(expectedInVar, fetchedClosure.inputs.get("a").getAsJsonArray());
        verifyJsonArrayStrings(expectedResult,
                fetchedClosure.outputs.get("result").getAsJsonArray());

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePowershellBooleanParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "True";
        boolean expectedResult = false;

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    $result = -not $inputs.a\n"
                + "    $context.outputs = @{\"result\" = $result} | ConvertTo-JSON\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);
        assertNotNull(closureDescription);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceClient);
        assertEquals(closureDescription.documentSelfLink, createdClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CREATED, createdClosure.state);

        // Executing the created Closure
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);

        assertEquals(expectedInVar, fetchedClosure.inputs.get("a").getAsString());
        assertEquals(expectedResult, fetchedClosure.outputs.get("result").getAsBoolean());

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePowershellArrayOfBooleanParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String[] expectedInVar = { "True", "True", "True" };
        Boolean[] expectedResult = { false, false, false };

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    $inputs.a[0] = -not $inputs.a[0]\n"
                + "    $inputs.a[1] = -not $inputs.a[1]\n"
                + "    $inputs.a[2] = -not $inputs.a[2]\n"
                + "\n"
                + "    $result = $inputs.a\n"
                + "    $context.outputs = @{\"result\" = $result} | ConvertTo-JSON\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);
        assertNotNull(closureDescription);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceClient);
        assertEquals(closureDescription.documentSelfLink, createdClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CREATED, createdClosure.state);

        // Executing the created Closure
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();

        JsonArray jsArray = new JsonArray();
        for (String x : expectedInVar) {
            jsArray.add(new JsonPrimitive(x));
        }
        inputs.put("a", jsArray);
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);

        verifyJsonArrayBooleans(Arrays.stream(expectedInVar).map(m -> Boolean.parseBoolean(m
                .toLowerCase())).toArray(), fetchedClosure.inputs.get("a").getAsJsonArray());
        verifyJsonArrayBooleans(expectedResult,
                fetchedClosure.outputs.get("result").getAsJsonArray());

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    class TestObject {
        public String strTest;
        public int intTest;
        public String boolTest;
    }

    class TestObjectResult {
        public String strTest;
        public int intTest;
        public Boolean boolTest;
    }

    class NestedTestObject {
        public String strTest;
        public int intTest;
        public String boolTest;
        public NestedTestObject objTest;
    }

    class NestedTestObjectResult {
        public String strTest;
        public int intTest;
        public Boolean boolTest;
        public NestedTestObject objTest;
    }

    @Test
    public void executePowershellObjectParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        TestObject expectedInVar = new TestObject();
        expectedInVar.strTest = "test";
        expectedInVar.intTest = 1;
        expectedInVar.boolTest = "True";
        String expectedResult = expectedInVar.strTest + "_changed";
        Boolean expectedBoolResult = false;

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    $inputs.a.strTest = $inputs.a.strTest + '_changed'\n"
                + "    $inputs.a.intTest = $inputs.a.intTest + 1\n"
                + "    $inputs.a.boolTest = -not $inputs.a.boolTest\n"
                + "    $result = $inputs.a\n"
                + "    $context.outputs = @{\"result\" = $result} | ConvertTo-JSON\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
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
        Gson gson = new Gson();
        inputs.put("a", gson.toJsonTree(expectedInVar));
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);
        JsonObject inObj = fetchedClosure.inputs.get("a").getAsJsonObject();

        Gson json = new Gson();
        TestObject deserialObj = json.fromJson(inObj, TestObject.class);

        assertEquals(expectedInVar.strTest, deserialObj.strTest);
        assertEquals(expectedInVar.intTest, deserialObj.intTest);
        assertEquals(expectedInVar.boolTest, deserialObj.boolTest);

        JsonObject jsonResultObj = fetchedClosure.outputs.get("result").getAsJsonObject();
        TestObjectResult resultObj = json.fromJson(jsonResultObj, TestObjectResult.class);

        assertEquals(expectedResult, resultObj.strTest);
        assertEquals(expectedInVar.intTest + 1, resultObj.intTest);
        assertEquals(expectedBoolResult, resultObj.boolTest);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePowershellArrayOfObjectParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        TestObject expectedInVar = new TestObject();
        expectedInVar.strTest = "test";
        expectedInVar.intTest = 1;
        expectedInVar.boolTest = "True";
        String expectedResult = expectedInVar.strTest + "_changed";
        Boolean expectedBoolResult = false;

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    $inputs.a[0].strTest = $inputs.a[0].strTest + '_changed'\n"
                + "    $inputs.a[0].intTest = $inputs.a[0].intTest + 1\n"
                + "    $inputs.a[0].boolTest = -not $inputs.a[0].boolTest\n"
                + "    $result = $inputs.a\n"
                + "    $context.outputs = @{\"result\" = $result} | ConvertTo-JSON\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
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

        JsonArray jsArray = new JsonArray();
        jsArray.add(new Gson().toJsonTree(expectedInVar));
        inputs.put("a", jsArray);
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);
        JsonObject inObj = fetchedClosure.inputs.get("a").getAsJsonArray().get(0).getAsJsonObject();

        Gson json = new Gson();
        TestObject deserialObj = json.fromJson(inObj, TestObject.class);

        assertEquals(expectedInVar.strTest, deserialObj.strTest);
        assertEquals(expectedInVar.intTest, deserialObj.intTest);
        assertEquals(expectedInVar.boolTest, deserialObj.boolTest);

        JsonObject jsonResultObj = fetchedClosure.outputs.get("result").getAsJsonArray().get(0)
                .getAsJsonObject();
        TestObjectResult resultObj = json.fromJson(jsonResultObj, TestObjectResult.class);

        assertEquals(expectedResult, resultObj.strTest);
        assertEquals(expectedInVar.intTest + 1, resultObj.intTest);
        assertEquals(expectedBoolResult, resultObj.boolTest);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePowershellNestedObjectParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        NestedTestObject expectedInVar = new NestedTestObject();
        expectedInVar.strTest = "test";
        expectedInVar.intTest = 1;
        expectedInVar.boolTest = "True";
        expectedInVar.objTest = new NestedTestObject();
        expectedInVar.objTest.strTest = "child";
        expectedInVar.objTest.intTest = 1;
        expectedInVar.objTest.boolTest = "True";

        String expectedResult = expectedInVar.objTest.strTest + "_changed";
        Boolean expectedBoolResult = false;

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    $inputs.a.boolTest = [boolean]$inputs.a.boolTest\n"
                + "    $inputs.a.objTest.strTest = $inputs.a.objTest.strTest + '_changed'\n"
                + "    $inputs.a.objTest.intTest = $inputs.a.objTest.intTest + 1\n"
                + "    $inputs.a.objTest.boolTest = -not $inputs.a.objTest.boolTest\n"
                + "    $result = $inputs.a\n"
                + "    $context.outputs = @{\"result\" = $result} | ConvertTo-JSON\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
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
        Gson gson = new Gson();
        inputs.put("a", gson.toJsonTree(expectedInVar));
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);

        JsonObject inObj = fetchedClosure.inputs.get("a").getAsJsonObject().get("objTest")
                .getAsJsonObject();

        Gson json = new Gson();
        NestedTestObject deserialObj = json.fromJson(inObj, NestedTestObject.class);
        assertEquals(expectedInVar.objTest.strTest, deserialObj.strTest);
        assertEquals(expectedInVar.objTest.intTest, deserialObj.intTest);
        assertEquals(expectedInVar.objTest.boolTest, deserialObj.boolTest);

        JsonObject jsonChild = fetchedClosure.outputs.get("result").getAsJsonObject().get("objTest")
                .getAsJsonObject();
        NestedTestObjectResult resultObj = json.fromJson(jsonChild, NestedTestObjectResult.class);
        assertEquals(expectedResult, resultObj.strTest);
        assertEquals(expectedInVar.objTest.intTest + 1, resultObj.intTest);
        assertEquals(expectedBoolResult, resultObj.boolTest);

        //        cleanResource(createdClosure.documentSelfLink, serviceClient);
        //        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeTimeoutedPowershellScriptTaskTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        closureDescState.source = "#!/usr/bin/powershell\n"
                + "function test($context)\n"
                + "{\n"
                + "    Write-Host \"Waiting...\"\n"
                + "    Start-Sleep -s 15\n"
                + "    Write-Host \"After sleep\"\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 1;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);
        assertNotNull(closureDescription);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceClient);
        assertEquals(closureDescription.documentSelfLink, createdClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CREATED, createdClosure.state);

        // Executing the created Closure
        executeClosure(createdClosure, new Closure(), serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.CANCELLED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CANCELLED, fetchedClosure.state);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void completeOrFailOutdatedJSScriptTaskTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        closureDescState.source = "#!/usr/bin/powershell\n"
                + "function test($context)\n"
                + "{\n"
                + "    Write-Host \"Waiting...\"\n"
                + "    Start-Sleep -s 60\n"
                + "    Write-Host \"After sleep\"\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 1;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);
        assertNotNull(closureDescription);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceClient);
        assertEquals(closureDescription.documentSelfLink, createdClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CREATED, createdClosure.state);

        // Executing the created Closure
        executeClosure(createdClosure, new Closure(), serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.CANCELLED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CANCELLED, fetchedClosure.state);

        // Request bring new execution of the created Closure.
        executeClosure(createdClosure, fetchedClosure, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.CANCELLED,
                serviceClient);

        fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CANCELLED, fetchedClosure.state);

        // Try to complete outdated Closure
        try {
            fetchedClosure.state = TaskState.TaskStage.FINISHED;
            SimpleHttpsClient.HttpResponse response = SimpleHttpsClient
                    .execute(SimpleHttpsClient.HttpMethod.PATCH, fetchedClosure.documentSelfLink,
                            Utils
                                    .toJson(fetchedClosure));
            if (response != null) {
                assertNotEquals("Closure is not allowed to complete once it is CANCELLED", 200,
                        response.statusCode);
            } else {
                fail("Closure is not allowed to complete once it is cancelled");
            }

        } catch (Exception ignored) {
        }

        // Try to fail outdated cancelled Closure
        try {
            fetchedClosure.state = TaskState.TaskStage.FAILED;
            SimpleHttpsClient.HttpResponse response = SimpleHttpsClient
                    .execute(SimpleHttpsClient.HttpMethod.PATCH, fetchedClosure.documentSelfLink,
                            Utils
                                    .toJson(fetchedClosure));
            if (response != null) {
                assertNotEquals("Closure is not allowed to complete once it is CANCELLED", 200,
                        response.statusCode);
            } else {
                fail("Closure is not allowed to complete once it is cancelled");
            }

        } catch (Exception ignored) {
        }

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void completeFailTimeoutedJSScriptTaskTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        closureDescState.source = "#!/usr/bin/powershell\n"
                + "function test($context)\n"
                + "{\n"
                + "    Write-Host \"Waiting...\"\n"
                + "    Start-Sleep -s 60\n"
                + "    Write-Host \"After sleep\"\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 1;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);
        assertNotNull(closureDescription);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceClient);
        assertEquals(closureDescription.documentSelfLink, createdClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CREATED, createdClosure.state);

        // Executing the created Closure
        executeClosure(createdClosure, new Closure(), serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.CANCELLED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CANCELLED, fetchedClosure.state);

        // Try to complete already cancelled Closure
        fetchedClosure.state = TaskState.TaskStage.FINISHED;

        try {
            SimpleHttpsClient.HttpResponse response = SimpleHttpsClient
                    .execute(SimpleHttpsClient.HttpMethod.PATCH, fetchedClosure.documentSelfLink,
                            Utils
                                    .toJson(fetchedClosure));
            if (response != null) {
                assertNotEquals("Closure is not allowed to complete once it is cancelled", 200,
                        response.statusCode);
            } else {
                fail("Closure is not allowed to complete once it is cancelled");
            }

        } catch (Exception ignored) {
        }

        // Try to fail already cancelled Closure
        fetchedClosure.state = TaskState.TaskStage.FAILED;

        try {
            SimpleHttpsClient.HttpResponse response = SimpleHttpsClient
                    .execute(SimpleHttpsClient.HttpMethod.PATCH, fetchedClosure.documentSelfLink,
                            Utils
                                    .toJson(fetchedClosure));
            if (response != null) {
                assertNotEquals("Closure is not allowed to complete once it is cancelled", 200,
                        response.statusCode);
            } else {
                fail("Closure is not allowed to complete once it is cancelled");
            }
        } catch (Exception ignored) {
        }

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeInvalidPowershellScriptTaskTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    $a = 1\n"
                + "    $a\n"
                + "    a\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);
        assertNotNull(closureDescription);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceClient);
        assertEquals(closureDescription.documentSelfLink, createdClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CREATED, createdClosure.state);

        Closure closureRequest = new Closure();

        // Executing the created Closure
        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FAILED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FAILED, fetchedClosure.state);
        assertTrue(fetchedClosure.errorMsg.length() > 0);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePowershellScriptFailureParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;

        closureDescState.source = "function test($context)\n"
                + "{\n"
                + "    $context.b\n"
                + "    $result = b\n"
                + "    return $result\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
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
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FAILED,
                serviceClient);

        Closure closure = getClosure(createdClosure.documentSelfLink, serviceClient);
        assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FAILED, closure.state);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePowershellExtSourceAsZIPTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/test_script_powershell.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
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
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        String imageRequestLink = waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);

        assertEquals(expectedInVar, fetchedClosure.inputs.get("a").getAsString());
        assertEquals(expectedResult, fetchedClosure.outputs.get("result").getAsString());

        //        cleanResource(imageRequestLink, serviceClient);
        //        cleanResource(createdClosure.documentSelfLink, serviceClient);
        //        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePowershellNonExistingZIPTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/non_existing.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
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
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        try {
            String imageRequestLink = waitForBuildCompletion(IMAGE_NAME, closureDescription);
            fail("Build of the image is expected to fail!");
        } catch (Exception ex) {
            logger.info("Expected to fail");
        }

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FAILED,
                serviceClient, 400);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FAILED, fetchedClosure.state);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePowershellWithEntrypointParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.entrypoint = "index.handler";

        int expectedInVar = 3;
        double expectedResult = 4;

        closureDescState.source = "function handler($context)\n"
                + "{\n"
                + "    $result = $inputs.a + 1\n"
                + "    $context.outputs = @{\"result\" = $result} | ConvertTo-JSON\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
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
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure closure = getClosure(createdClosure.documentSelfLink, serviceClient);
        assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, closure.state);

        assertEquals(expectedInVar, closure.inputs.get("a").getAsInt());
        assertEquals(expectedResult, closure.outputs.get("result").getAsInt(), 0);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePythonWithInvalidEntrypointParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.entrypoint = "invalid.invalid";

        int expectedInVar = 3;
        double expectedResult = 4;

        closureDescState.source = "function handler($context)\n"
                + "{\n"
                + "    $result = $inputs.a + 1\n"
                + "    $context.outputs = @{\"result\" = $result} | ConvertTo-JSON\n"
                + "}\n";

        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
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
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FAILED,
                serviceClient);

        Closure closure = getClosure(createdClosure.documentSelfLink, serviceClient);
        assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FAILED, closure.state);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePythonWithEntrypointAsZIPTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.entrypoint = "main.handler";

        int expectedInVar = 3;
        double expectedResult = 4;

        closureDescState.sourceURL = testWebserverUri + "/test_script_powershell_entrypoint.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_POWERSHELL_6;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
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
        assertEquals(expectedResult, fetchedClosure.outputs.get("result").getAsInt(), 0);

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

}
