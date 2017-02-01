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

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.BaseIntegrationTest;
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

public class PythonTest extends BaseIntegrationTest {

    protected static String IMAGE_NAME_PREFIX = "vmware/photon-closure-runner_";

    private static final String IMAGE_NAME =
            IMAGE_NAME_PREFIX + DriverConstants.RUNTIME_PYTHON_3;

    private static String testWebserverUri;

    private static String RUNTIME_PYTHON = "python";

    private static ServiceClient serviceClient;

    private static String dockerBuildImageLink;
    private static String dockerBuildBaseImageLink;

    @BeforeClass
    public static void beforeClass() throws Exception {
        serviceClient = ServiceClientFactory.createServiceClient(null);
        testWebserverUri = getTestWebServerUrl();

        setupCoreOsHost(ContainerHostService.DockerAdapterType.API, false);
        dockerBuildImageLink = getBaseUrl()
                + createImageBuildRequestUri(IMAGE_NAME + ":latest", dockerHostCompute
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
        SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.DELETE, dockerBuildImageLink);
        SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.DELETE, dockerBuildBaseImageLink);
        serviceClient.stop();
    }

    @Before
    public void init() {
        logger.info("Executing against docker host: %s ", dockerHostCompute.address);
    }

    @Test
    public void executePythonNumberParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        double expectedResult = 4;

        closureDescState.source = "def test(context):\n"
                + "    inputs = context.inputs\n"
                + "    print('Hello numbers {}'.format(inputs['a']))\n"
                + "    context.outputs['result'] = inputs['a'] + 1\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));

        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 3;
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
    public void executePythonArrayOfNumberParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        Integer[] expectedInVar = { 1, 2, 3 };
        Integer[] expectedResult = { 2, 3, 4 };

        closureDescState.source = "def test(context):\n"
                + "    x = context.inputs['a']\n"
                + "    print ('Hello array of numbers: {}'.format(x))\n"
                + "    for index, item in enumerate(x):\n"
                + "        x[index] = x[index] + 1\n"
                + "    context.outputs['result'] = x\n"
                + "    print (context.outputs['result'])\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 4;
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
    public void executePythonStringParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.source = "def test(ctx):\n"
                + "    inputs = ctx.inputs\n"
                + "    print('Hello string: {}'.format(inputs['a']))\n"
                + "    ctx.outputs['result'] = inputs['a'] + \"c\"\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 2;
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
    public void executePythonArrayOfStringParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String[] expectedInVar = { "a", "b", "c" };
        String expectedOutVar = "test";
        String[] expectedResult = { "a_t", "b_t", "c_t" };

        closureDescState.source = "def test(context):\n"
                + "     x = context.inputs['a']\n"
                + "     print ('Hello array of strings: {}'.format(x))\n"
                + "     for index, item in enumerate(x):\n"
                + "         x[index] = x[index] + '_t'\n"
                + "     print ('Hello number: {}'.format(x))\n"
                + "     context.outputs['result'] = x\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 4;
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
    public void executePythonBooleanParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        boolean expectedInVar = true;
        boolean expectedResult = false;

        closureDescState.source = "def test(ctx):\n"
                + "     print ('Hello boolean: {}'.format(ctx.inputs['a']))\n"
                + "     ctx.outputs['result'] = not ctx.inputs['a']\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 4;
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

        assertEquals(expectedInVar, fetchedClosure.inputs.get("a").getAsBoolean());
        assertEquals(expectedResult, fetchedClosure.outputs.get("result").getAsBoolean());

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePythonArrayOfBooleanParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        Boolean[] expectedInVar = { true, true, true };
        Boolean[] expectedResult = { false, false, false };

        closureDescState.source = "def test(context):\n"
                + "     x = context.inputs['a']\n"
                + "     print ('Hello array of booleans: {}'.format(x))\n"
                + "     for index, item in enumerate(x):\n"
                + "         x[index] = not x[index]\n"
                + "     context.outputs['result'] = x\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 4;
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
        for (Boolean x : expectedInVar) {
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
        verifyJsonArrayBooleans(expectedInVar, fetchedClosure.inputs.get("a").getAsJsonArray());
        verifyJsonArrayBooleans(expectedResult,
                fetchedClosure.outputs.get("result").getAsJsonArray());

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    class TestObject {
        public String strTest;
        public int intTest;
        public Boolean boolTest;
    }

    class NestedTestObject {
        public String strTest;
        public int intTest;
        public Boolean boolTest;
        public NestedTestObject objTest;
    }

    @Test
    public void executePythonObjectParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        TestObject expectedInVar = new TestObject();
        expectedInVar.strTest = "test";
        expectedInVar.intTest = 1;
        expectedInVar.boolTest = true;
        int expectedOutVar = 3;
        String expectedResult = expectedInVar.strTest + "_changed";

        closureDescState.source = "def test(context):\n"
                + "    inputs = context.inputs\n"
                + "    x = inputs['a']\n"
                + "    print('Hello object: '.format(x['strTest']))\n"
                + "    x['strTest'] = x['strTest'] + '_changed'\n"
                + "    x['intTest'] = x['intTest'] + 1\n"
                + "    x['boolTest'] = not x['boolTest']\n"
                + "    context.outputs['result'] = x\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 2;
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
        TestObject resultObj = json.fromJson(jsonResultObj, TestObject.class);

        assertEquals(expectedResult, resultObj.strTest);
        assertEquals(expectedInVar.intTest + 1, resultObj.intTest);
        assertEquals(!expectedInVar.boolTest, resultObj.boolTest);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePythonArrayOfObjectParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        TestObject expectedInVar = new TestObject();
        expectedInVar.strTest = "test";
        expectedInVar.intTest = 1;
        expectedInVar.boolTest = true;
        int expectedOutVar = 3;
        String expectedResult = expectedInVar.strTest + "_changed";

        closureDescState.source = "def test(ctx):\n"
                + "     x = ctx.inputs['a']\n"
                + "     print('Hello object: '.format(x[0]['strTest']))\n"
                + "     x[0]['strTest'] = x[0]['strTest'] + '_changed'\n"
                + "     x[0]['intTest'] = x[0]['intTest'] + 1\n"
                + "     x[0]['boolTest'] = not x[0]['boolTest']\n"
                + "     ctx.outputs['result'] = x\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 4;
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
        TestObject resultObj = json.fromJson(jsonResultObj, TestObject.class);

        assertEquals(expectedResult, resultObj.strTest);
        assertEquals(expectedInVar.intTest + 1, resultObj.intTest);
        assertEquals(!expectedInVar.boolTest, resultObj.boolTest);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePythonNestedObjectParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        NestedTestObject expectedInVar = new NestedTestObject();
        expectedInVar.strTest = "test";
        expectedInVar.intTest = 1;
        expectedInVar.boolTest = true;
        expectedInVar.objTest = new NestedTestObject();
        expectedInVar.objTest.strTest = "child";
        expectedInVar.objTest.intTest = 1;
        expectedInVar.objTest.boolTest = true;

        int expectedOutVar = 3;
        String expectedResult = expectedInVar.objTest.strTest + "_changed";

        closureDescState.source = "def test(ctx):\n"
                + "     x = ctx.inputs['a']\n"
                + "     print('Hello object: {}'.format(x['objTest']))\n"
                + "     x['objTest']['strTest'] = x['objTest']['strTest'] + '_changed'\n"
                + "     x['objTest']['intTest'] = x['objTest']['intTest'] + 1\n"
                + "     x['objTest']['boolTest'] = not x['objTest']['boolTest']\n"
                + "     ctx.outputs['result'] = x\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 3;
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
        NestedTestObject resultObj = json.fromJson(jsonChild, NestedTestObject.class);
        assertEquals(expectedResult, resultObj.strTest);
        assertEquals(expectedInVar.objTest.intTest + 1, resultObj.intTest);
        assertEquals(!expectedInVar.objTest.boolTest, resultObj.boolTest);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeTimeoutedPythonScriptTaskTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.source = "#!/usr/bin/python\n"
                + "import time\n"
                + "\n"
                + "def test(ctx):\n"
                + "     print('Waiting....')\n"
                + "     time.sleep(10)\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
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
        closureDescState.source = "#!/usr/bin/python\n"
                + "import time\n"
                + "\n"
                + "def test(ctx):\n"
                + "     print('Waiting....')\n"
                + "     time.sleep(60)\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
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
        closureDescState.source = "#!/usr/bin/python\n"
                + "import time\n"
                + "\n"
                + "def test(ctx):\n"
                + "     print('Waiting....')\n"
                + "     time.sleep(10)\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
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
    public void executeInvalidPythonScriptTaskTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.source = "def test(ctx):\n"
                + "     a = 1\n"
                + "     print('Hello ' + invalid)\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 4;
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
    public void executePythonScriptFailureParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4;

        closureDescState.source = "def test(context):\n"
                + "    raise Exception('test exception')\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 3;
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
    public void executePythonExtSourceAsZIPTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/test_script_python.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 2;
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

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePythonNonExistingZIPTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/non_existing.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 2;
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
    public void executePythonWithEntrypointParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.entrypoint = "main.handler";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4;

        closureDescState.source = "def handler(context):\n"
                + "    inputs = context.inputs\n"
                + "    print('Hello numbers {}'.format(inputs['a']))\n"
                + "    context.outputs['result'] = inputs['a'] + 1\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 3;
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
        int expectedOutVar = 3;
        double expectedResult = 4;

        closureDescState.source = "def handler(context):\n"
                + "    inputs = context.inputs\n"
                + "    print('Hello numbers {}'.format(inputs['a']))\n"
                + "    context.outputs['result'] = inputs['a'] + 1\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 2;
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

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/test_script_python_entrypoint.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 2;
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

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    @Ignore
    public void executePythonWithKeyringDependencyTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4;

        closureDescState.source = "import keyring.backend\n"
                + "\n"
                + "\n"
                + "class TestKeyring(keyring.backend.KeyringBackend):\n"
                + "    \"\"\"A test keyring which always outputs same password\n"
                + "    \"\"\"\n"
                + "    priority = 1\n"
                + "\n"
                + "    def set_password(self, servicename, username, password):\n"
                + "        pass\n"
                + "\n"
                + "    def get_password(self, servicename, username):\n"
                + "        return \"password from TestKeyring\"\n"
                + "\n"
                + "    def delete_password(self, servicename, username, password):\n"
                + "        pass\n"
                + "\n"
                + "\n"
                + "def test(context):\n"
                + "    inputs = context.inputs\n"
                + "    print('Hello number  {}'.format(inputs['a']))\n"
                + "    context.outputs['result'] = inputs['a'] + 1\n"
                + "\n"
                + "    # set the keyring for keyring lib\n"
                + "    keyring.set_keyring(TestKeyring())  # invoke the keyring lib\n"
                + "    try:\n"
                + "        keyring.set_password(\"demo-service\", \"tarek\", \"passexample\")\n"
                + "        print(\"password stored sucessfully\")\n"
                + "    except keyring.errors.PasswordSetError as err:\n"
                + "        print(\"failed to store password\")\n"
                + "    print(\"password\", keyring.get_password(\"demo-service\", \"tarek\"))\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.dependencies = "requests >= 2.9.1\nkeyring >= 9.3.1";
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 40;
        constraints.ramMB = 300;
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
        String imageRequestLink = waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure closure = getClosure(createdClosure.documentSelfLink, serviceClient);
        assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, closure.state);

        assertEquals(expectedInVar, closure.inputs.get("a").getAsInt());
        assertEquals(expectedResult, closure.outputs.get("result").getAsInt(), 0);

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    @Ignore
    public void executePythonWithNumpyDependencyTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4;

        closureDescState.source = "import numpy as np\n"
                + "\n"
                + "\n"
                + "a = np.array([1, 2, 3])\n"
                + "a.shape\n"
                + "a[0] = 6\n"
                + "\n"
                + "def test(context):\n"
                + "    inputs = context.inputs\n"
                + "    print('Hello number  {}'.format(inputs['a']))\n"
                + "    context.outputs['result'] = inputs['a'] + 1\n"
                + "\n";

        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.dependencies = "requests >= 2.9.1\nnumpy >= 1.11";
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 40;
        constraints.ramMB = 300;
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
        String imageRequestLink = waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure closure = getClosure(createdClosure.documentSelfLink, serviceClient);
        assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, closure.state);

        assertEquals(expectedInVar, closure.inputs.get("a").getAsInt());
        assertEquals(expectedResult, closure.outputs.get("result").getAsInt(), 0);

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePythonWithBilliardDependencyTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4;

        closureDescState.source = "import billiard\n"
                + "\n"
                + "\n"
                + "def f():\n"
                + "    print('ok!')\n"
                + "\n"
                + "def test(context):\n"
                + "    inputs = context.inputs\n"
                + "    print('Hello number  {}'.format(inputs['a']))\n"
                + "    context.outputs['result'] = inputs['a'] + 1\n"
                + "\n"
                + "    billiard.forking_enable(1)\n"
                + "    p = billiard.Process(target = f)\n"
                + "    p.start()\n"
                + "    while p.is_alive():\n"
                + "        pass\n"
                + "\n";

        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.dependencies = "requests >= 2.9.1\nbilliard >= 3.3.0.13";
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 40;
        constraints.ramMB = 300;
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
        String imageRequestLink = waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure closure = getClosure(createdClosure.documentSelfLink, serviceClient);
        assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, closure.state);

        assertEquals(expectedInVar, closure.inputs.get("a").getAsInt());
        assertEquals(expectedResult, closure.outputs.get("result").getAsInt(), 0);

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePythonWithMissingDependencyTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4;

        closureDescState.source = "import keyring.backend\n"
                + "\n"
                + "\n"
                + "class TestKeyring(keyring.backend.KeyringBackend):\n"
                + "    \"\"\"A test keyring which always outputs same password\n"
                + "    \"\"\"\n"
                + "    priority = 1\n"
                + "\n"
                + "    def set_password(self, servicename, username, password):\n"
                + "        pass\n"
                + "\n"
                + "    def get_password(self, servicename, username):\n"
                + "        return \"password from TestKeyring\"\n"
                + "\n"
                + "    def delete_password(self, servicename, username, password):\n"
                + "        pass\n"
                + "\n"
                + "\n"
                + "def test(context):\n"
                + "    inputs = context.inputs\n"
                + "    print('Hello number  {}'.format(inputs['a']))\n"
                + "    context.outputs['result'] = inputs['a'] + 1\n"
                + "\n"
                + "    # set the keyring for keyring lib\n"
                + "    keyring.set_keyring(TestKeyring())  # invoke the keyring lib\n"
                + "    try:\n"
                + "        keyring.set_password(\"demo-service\", \"tarek\", \"passexample\")\n"
                + "        print(\"password stored sucessfully\")\n"
                + "    except keyring.errors.PasswordSetError:\n"
                + "        print(\"failed to store password\")\n"
                + "    print(\"password\", keyring.get_password(\"demo-service\", \"tarek\"))\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.dependencies = "requests >= 0.11.1\n";
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 7;
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
        String imageRequestLink = waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FAILED,
                serviceClient);

        Closure closure = getClosure(createdClosure.documentSelfLink, serviceClient);
        assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FAILED, closure.state);

        Assert.assertTrue("Not expected error: " + closure.errorMsg,
                closure.errorMsg.contains("ImportError(\"No "
                        + "module named 'keyring'\",)"));

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePythonWithDependencyUsingSourceURLasZIPTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4;

        closureDescState.sourceURL = testWebserverUri + "/test_script_python_dependencies.zip";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.ramMB = 300;
        constraints.timeoutSeconds = 40;
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
        String imageRequestLink = waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure closure = getClosure(createdClosure.documentSelfLink, serviceClient);
        assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, closure.state);

        assertEquals(expectedInVar, closure.inputs.get("a").getAsInt());
        assertEquals(expectedResult, closure.outputs.get("result").getAsInt(), 0);

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePythonNumbersWithWebhookTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4;

        closureDescState.source = "def test(context):\n"
                + "    inputs = context.inputs\n"
                + "    print('Hello numbers {}'.format(inputs['a']))\n"
                + "    context.outputs['result'] = inputs['a'] + 1\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.notifyUrl = "/cmp/task_consumer";
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 3;
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

        // verify test service was notified and consumed the closure state
        SimpleHttpsClient.HttpResponse consumedContent = SimpleHttpsClient
                .execute(SimpleHttpsClient.HttpMethod.GET,
                        getBaseUrl() + closureDescState.notifyUrl + closure.documentSelfLink);
        assertNotNull(consumedContent);
        assertEquals(TaskState.TaskStage.FINISHED, closure.state);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executePythonNumbersWithInvalidWebhookTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4;

        closureDescState.source = "def test(context):\n"
                + "    inputs = context.inputs\n"
                + "    print('Hello numbers {}'.format(inputs['a']))\n"
                + "    context.outputs['result'] = inputs['a'] + 1\n"
                + "\n";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.notifyUrl = "/invalid";
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 3;
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

        SimpleHttpsClient.HttpResponse consumedContent = SimpleHttpsClient
                .execute(SimpleHttpsClient.HttpMethod.GET,
                        getBaseUrl() + closureDescState.notifyUrl + closure.documentSelfLink);
        assertNotNull(consumedContent);
        assertEquals(404, consumedContent.statusCode);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

/*
    @Test
    public void executePythonStringWithExternalCodeSourceNoDependenciesTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        // Create external closure source
        ClosureSource closureSource = new ClosureSource();
        closureSource.documentSelfLink = UriUtils
                .buildUriPath(ClosureSourceFactoryService.FACTORY_LINK, UUID.randomUUID().toString());
        closureSource.source = "def test(ctx):\n"
                + "    inputs = ctx['inputs']\n"
                + "    print('Hello string: '.format(inputs['a']))\n"
                + "    ctx['outputs']['result'] = inputs['a'] + \"c\"\n"
                + "\n";
        SimpleHttpsClient.HttpResponse taskSourceResponse = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod
                .POST, serviceHostUri + ClosureSourceFactoryService.FACTORY_LINK, Utils.toJson(closureSource));
        assertNotNull(taskSourceResponse);

        closureDescState.sourceURL = serviceHostUri + closureSource.documentSelfLink + "?contentValue=true";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 2;
        constraints.ramMB = 300;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload, serviceHostUri);
        assertNotNull(closureDescription);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceHostUri);
        assertEquals(closureDescription.documentSelfLink, createdClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CREATED, createdClosure.state);

        // Execute the created Closure
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        closureRequest.inputs = inputs;

        executeTask(createdClosure, closureRequest, serviceHostUri);

        // Wait for the completion timeout
        String imageRequestLink = waitForBuildCompletion(serviceHostUri, IMAGE_NAME, closureDescription,
                TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        waitForTaskState(createdClosure.documentSelfLink, serviceHostUri, TaskState.TaskStage.FINISHED);
        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceHostUri);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);

        assertEquals(expectedInVar, fetchedClosure.inputs.get("a").getAsString());
        assertEquals(expectedResult, fetchedClosure.outputs.get("result").getAsString());

        cleanResource(serviceHostUri + imageRequestLink);
        cleanResource(serviceHostUri + closureDescription.documentSelfLink);
        cleanResource(serviceHostUri + createdClosure.documentSelfLink);
    }

    @Test
    public void executePythonWithDependencyUsingSourceURLTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4;

        // Create external closure source
        ClosureSource closureSource = new ClosureSource();
        closureSource.documentSelfLink = UriUtils
                .buildUriPath(ClosureSourceFactoryService.FACTORY_LINK, UUID.randomUUID().toString());
        closureSource.source = "import keyring.backend\n"
                + "\n"
                + "\n"
                + "class TestKeyring(keyring.backend.KeyringBackend):\n"
                + "    \"\"\"A test keyring which always outputs same password\n"
                + "    \"\"\"\n"
                + "    priority = 1\n"
                + "\n"
                + "    def set_password(self, servicename, username, password):\n"
                + "        pass\n"
                + "\n"
                + "    def get_password(self, servicename, username):\n"
                + "        return \"password from TestKeyring\"\n"
                + "\n"
                + "    def delete_password(self, servicename, username, password):\n"
                + "        pass\n"
                + "\n"
                + "\n"
                + "def test(context):\n"
                + "    inputs = context['inputs']\n"
                + "    print('Hello DEMO  {}'.format(inputs['a']))\n"
                + "    context['outputs']['result'] = inputs['a'] + 1\n"
                + "\n"
                + "    # set the keyring for keyring lib\n"
                + "    keyring.set_keyring(TestKeyring())  # invoke the keyring lib\n"
                + "    try:\n"
                + "        keyring.set_password(\"demo-service\", \"tarek\", \"passexample\")\n"
                + "        print(\"password stored sucessfully\")\n"
                + "    except keyring.errors.PasswordSetError:\n"
                + "        print(\"failed to store password\")\n"
                + "    print(\"password\", keyring.get_password(\"demo-service\", \"tarek\"))\n"
                + "\n";
        SimpleHttpsClient.HttpResponse taskSourceResponse = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod
                .POST, serviceHostUri + ClosureSourceFactoryService.FACTORY_LINK, Utils.toJson(closureSource));
        assertNotNull(taskSourceResponse);

        closureDescState.sourceURL = serviceHostUri + closureSource.documentSelfLink + "?contentValue=true";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_PYTHON;
        closureDescState.dependencies = "requests >= 0.11.1\nkeyring >= 4.1.1";
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.ramMB = 200;
        constraints.timeoutSeconds = 30;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload, serviceHostUri);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription, serviceHostUri);

        // Execute the created Closure
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        closureRequest.inputs = inputs;

        executeTask(createdClosure, closureRequest, serviceHostUri);

        // Wait for the completion timeout
        String imageRequestLink = waitForBuildCompletion(serviceHostUri, IMAGE_NAME, closureDescription,
                TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        waitForTaskState(createdClosure.documentSelfLink, serviceHostUri, TaskState.TaskStage.FINISHED);
        Closure closure = getClosure(createdClosure.documentSelfLink, serviceHostUri);
        assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, closure.state);

        assertEquals(expectedInVar, closure.inputs.get("a").getAsInt());
        assertEquals(expectedResult, closure.outputs.get("result").getAsInt(), 0);

        cleanResource(serviceHostUri + imageRequestLink);
        cleanResource(serviceHostUri + closureDescription.documentSelfLink);
        cleanResource(serviceHostUri + createdClosure.documentSelfLink);
    }
*/
}
