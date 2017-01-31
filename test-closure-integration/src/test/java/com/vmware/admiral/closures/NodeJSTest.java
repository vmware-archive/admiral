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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.BaseIntegrationTest;
import com.vmware.admiral.SimpleHttpsClient;
import com.vmware.admiral.closures.drivers.DriverConstants;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ResourceConstraints;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.service.common.LogService;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class NodeJSTest extends BaseIntegrationTest {

    protected static String IMAGE_NAME_PREFIX = "vmware/photon-closure-runner_";

    private static final String IMAGE_NAME = IMAGE_NAME_PREFIX
            + DriverConstants.RUNTIME_NODEJS_4;

    private static String testWebserverUri;

    private static String RUNTIME_NODEJS = "nodejs";

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
    public void addDefaultTaskTest() throws Exception {
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.source = "module.exports = function test(context) {"
                + " var a = 1;"
                + " console.log(\"Hello test \" + a);"
                + " };";
        closureDescState.runtime = RUNTIME_NODEJS;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);
        Closure createdClosure = createClosure(closureDescription, serviceClient);
        assertEquals(TaskState.TaskStage.CREATED, createdClosure.state);
    }

    @Test
    public void executeJSNumberParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        double expectedResult = 4.0; // TODO: fix types

        closureDescState.source = "module.exports = function test(context) {"
                + " console.log('Hello number: ' + context.inputs.a);"
                + " context.outputs.result=context.inputs.a + 1;"
                + " }; ";
        closureDescState.runtime = RUNTIME_NODEJS;
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
        assertEquals(expectedResult, closure.outputs.get("result").getAsDouble(), 0);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeJSDependenciesTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        double expectedResult = 4.0; // TODO: fix types
        closureDescState.source = "var _ = require('lodash');"
                + "var moment = require('moment');"
                + "module.exports = function test(context) {"
                + " console.log('Executed at : ' + moment().valueOf());"
                + " context.outputs.result = context.inputs.a + 1;"
                + "}; ";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDescState.dependencies = "{\"lodash\" : \"4.11.1\", \"moment\" : \"2.12.0\"}";

        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 6;
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
        assertEquals(expectedResult, closure.outputs.get("result").getAsDouble(), 0);

        cleanResource(closure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeJSArrayOfNumberParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        Integer[] expectedInVar = { 1, 2, 3 };
        Integer[] expectedResult = { 2, 3, 4 };

        closureDescState.source = "module.exports = function increment(context) {"
                + " var x = context.inputs.a;"
                + " console.log('Hello array of numbers: ' + x);"
                + " for(var i = 0; i < x.length; i++) {"
                + " x[i] = x[i] + 1;" + "}"
                + " context.outputs.result = x;"
                + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 4;
        closureDescState.resources = constraints;
        ClosureDescription[] responses = new ClosureDescription[1];

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
    public void executeJSArrayOfStringParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String[] expectedInVar = { "a", "b", "c" };
        String expectedOutVar = "test";
        String[] expectedResult = { "a_t", "b_t", "c_t" };

        closureDescState.source = "module.exports = function appnd(context) {"
                + " var x = context.inputs.a;"
                + " console.log('Hello array of strings: ' + x);"
                + " for(var i = 0; i < x.length; i++) {" + "x[i] = x[i] + '_t';" + "}"
                + " console.log('Hello number: ' + x);"
                + " context.outputs.result = x;"
                + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
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
    public void executeJSArrayOfBooleanParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        Boolean[] expectedInVar = { true, true, true };
        Boolean expectedOutVar = true;
        Boolean[] expectedResult = { false, false, false };

        closureDescState.source = "module.exports = function appl(context) {"
                + " var x = context.inputs.a;"
                + " console.log('Hello array of booleans: ' + x);"
                + " for(var i = 0; i < x.length; i++) {" + "x[i] = !x[i];" + "}"
                + " context.outputs.result = x;"
                + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
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

    @Test
    public void executeJSDateParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        Instant instant = Instant.now();

        ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, ZoneId.of("UTC"));

        Instant expectedInVar = instant;
        Instant expectedOutVar = instant;
        Instant expectedResult = instant;

        Gson gson = new Gson();
        closureDescState.source = "module.exports = function test(context) {"
                + " console.log('Hello Date: ' + new Date(context.inputs.a));"
                + " console.log('Year of the date: ' + new Date(context.inputs.a).getFullYear());"
                + " context.outputs.result = context.inputs.a;"
                + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
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

        // Execute the created Closure
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();

        inputs.put("a", gson.toJsonTree(zdt.format(DateTimeFormatter.ISO_INSTANT)));
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest, serviceClient);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED,
                serviceClient);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, fetchedClosure.state);

        assertEquals(expectedInVar.toString(),
                fetchedClosure.inputs.get("a").getAsString());
        assertEquals(expectedResult.toString(), fetchedClosure.outputs.get("result").getAsString());

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeJSArrayOfDateParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        Instant instant = Instant.now();

        Instant expectedInVar = instant;
        Instant expectedOutVar = instant;
        Instant expectedResult = instant;

        Gson gson = new Gson();
        closureDescState.source =
                "module.exports = function test(context) {"
                        + " var x = context.inputs.a;"
                        + " console.log('Hello array of dates: ' + new Date(x[0]));"
                        + " console.log('Year of the date: ' + new Date(x[0]).getFullYear());"
                        + " context.outputs.result = [x[0]];"
                        + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
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
        jsArray.add(gson.toJsonTree(expectedInVar.toString()));
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

        assertEquals(expectedInVar.toString(),
                fetchedClosure.inputs.get("a").getAsJsonArray().get(0).getAsString());
        assertEquals(expectedResult.toString(),
                fetchedClosure.outputs.get("result").getAsJsonArray().get(0).getAsString
                        ());

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
    public void executeJSObjectParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        TestObject expectedInVar = new TestObject();
        expectedInVar.strTest = "test";
        expectedInVar.intTest = 1;
        expectedInVar.boolTest = true;
        int expectedOutVar = 3;
        String expectedResult = expectedInVar.strTest + "_changed";

        closureDescState.source = "module.exports = function test(context) {"
                + " var x = context.inputs.a;"
                + " console.log('Hello object: ' + x.strTest);"
                + " x.strTest = x.strTest + '_changed';"
                + " x.intTest = x.intTest + 1; x.boolTest = !x.boolTest;"
                + " context.outputs.result = x;"
                + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
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
    public void executeJSNestedObjectParametersTest() throws Throwable {
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

        closureDescState.source = "module.exports = function test(ctx) {"
                + " var x = ctx.inputs.a;"
                + " console.log('Hello object: ' + x.objTest);"
                + " x.objTest.strTest = x.objTest.strTest + '_changed';"
                + " x.objTest.intTest = x.objTest.intTest + 1; x.objTest.boolTest = !x.objTest.boolTest;"
                + " ctx.outputs.result = x;"
                + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
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
    public void executeJSArrayOfObjectParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        TestObject expectedInVar = new TestObject();
        expectedInVar.strTest = "test";
        expectedInVar.intTest = 1;
        expectedInVar.boolTest = true;
        int expectedOutVar = 3;
        String expectedResult = expectedInVar.strTest + "_changed";

        closureDescState.source = "module.exports = function test(ctx) {"
                + " var x = ctx.inputs.a;"
                + " console.log('Hello object: ' + x[0].strTest);"
                + " x[0].strTest = x[0].strTest + '_changed';"
                + " x[0].intTest = x[0].intTest + 1; x[0].boolTest = !x[0].boolTest;"
                + " ctx.outputs.result= x;"
                + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
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
    public void executeJSStringParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.source = "module.exports = function test(ctx) {"
                + " console.log('Hello string: ' + ctx.inputs.a);"
                + " ctx.outputs.result = ctx.inputs.a.concat(\"c\");"
                + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
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
    public void executeJSExternalCodeSourceAsZIPWithPackageJsonTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/test_script_packagejson.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 20;
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
    public void executeJSExtSourceAsObjAsZIPWithPackageJsonTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/test_script_as_obj_packagejson.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 20;
        constraints.ramMB = 300;
        closureDescState.resources = constraints;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));

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
    public void executeJSExtSourceAsZIPNoPackageJsonEntrypointTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.entrypoint = "index.test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/test_script_no_packagejson.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 20;
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
    public void executeJSExtSourceAsObjAsZIPNoPackageJsonEntrypointTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.entrypoint = "index.test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/test_script_as_obj_no_packagejson.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 20;
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
    public void executeJSExtSourceAsZIPNoPackageJsonNoEntrypointTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/test_script_no_packagejson.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 20;
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
    public void executeJSExtSourceAsObjAsZIPNoPackageJsonNoEntrypointTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/test_script_as_obj_no_packagejson.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 20;
        constraints.ramMB = 300;
        closureDescState.resources = constraints;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));

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
    public void executeNegativeInvalidHandlerNameJSExtSourceAsZIPTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "invalid";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/test_script_as_obj_no_packagejson.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 20;
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

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FAILED,
                serviceClient);
        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FAILED, fetchedClosure.state);

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeNegativeInvalidEntrypointJSExtSourceAsZIPTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.entrypoint = "invalid.invalid";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDescState.sourceURL = testWebserverUri + "/test_script_as_obj_no_packagejson.zip";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 20;
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

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FAILED,
                serviceClient);
        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FAILED, fetchedClosure.state);

        cleanResource(imageRequestLink, serviceClient);
        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeJSBooleanParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        boolean expectedInVar = true;
        int expectedOutVar = 1;
        boolean expectedResult = false;

        closureDescState.source = "module.exports = function test(ctx) {"
                + " console.log('Hello boolean: ' + ctx.inputs.a);"
                + " ctx.outputs.result = !ctx.inputs.a;"
                + "}";
        closureDescState.runtime = RUNTIME_NODEJS;
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
    public void executeJSNameAsHandlerNameParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        boolean expectedInVar = true;
        int expectedOutVar = 1;
        boolean expectedResult = false;

        closureDescState.source = "exports.test= function test(ctx) {"
                + " console.log('Hello boolean: ' + ctx.inputs.a);"
                + " ctx.outputs.result = !ctx.inputs.a;"
                + "}";
        closureDescState.runtime = RUNTIME_NODEJS;
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
    public void executeJSWithEntrypointParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "random";

        boolean expectedInVar = true;
        int expectedOutVar = 1;
        boolean expectedResult = false;

        closureDescState.entrypoint = "moduleName.test";
        closureDescState.source = "exports.test = function test(ctx) {"
                + " console.log('Hello boolean: ' + ctx.inputs.a);"
                + " ctx.outputs.result = !ctx.inputs.a;"
                + "}";
        closureDescState.runtime = RUNTIME_NODEJS;
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
    public void executeInvalidJSScriptTaskTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.source = "module.exports = function test(ctx) {"
                + " var a = 1;"
                + " console.log(\"Hello \" + invalid);"
                + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
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
    public void executeTimeoutedJSScriptTaskTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.source = "function sleep(delay) {"
                + " var start = new Date().getTime();"
                + " while (new Date().getTime() < start + delay) {"
                + " console.log('Waiting....');"
                + " }}"
                + " module.exports = function(ctx) {"
                + " sleep(10000);"
                + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
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
    public void completeFailTimeoutedJSScriptTaskTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.source = "function sleep(delay) {"
                + " var start = new Date().getTime();"
                + " while (new Date().getTime() < start + delay) {"
                + " console.log('Waiting...');"
                + "}}"
                + "module.exports = function(ctx) {"
                + " sleep(10000);"
                + "};";
        closureDescState.runtime = RUNTIME_NODEJS;
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

        // Try to complete already cancelled Closure
        fetchedClosure.state = TaskState.TaskStage.FINISHED;

        try {
            SimpleHttpsClient.HttpResponse response = SimpleHttpsClient
                    .execute(SimpleHttpsClient.HttpMethod.PATCH,
                            serviceClient + fetchedClosure.documentSelfLink, Utils
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
                    .execute(SimpleHttpsClient.HttpMethod.PATCH,
                            serviceClient + fetchedClosure.documentSelfLink, Utils
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
    public void completeOrFailOutdatedJSScriptTaskTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";
        closureDescState.source = "function sleep(delay) {"
                + " var start = new Date().getTime();"
                + " while (new Date().getTime() < start + delay) {"
                + "     console.log('Waiting...');"
                + " }}"
                + " module.exports = function(ctx) {"
                + " sleep(60000);"
                + "}";
        closureDescState.runtime = RUNTIME_NODEJS;
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

        fetchedClosure = getClosure(createdClosure.documentSelfLink, serviceClient);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CANCELLED, fetchedClosure.state);

        // Try to complete outdated Closure
        try {
            fetchedClosure.state = TaskState.TaskStage.FINISHED;
            SimpleHttpsClient.HttpResponse response = SimpleHttpsClient
                    .execute(SimpleHttpsClient.HttpMethod.PATCH,
                            serviceClient + fetchedClosure.documentSelfLink, Utils
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
                    .execute(SimpleHttpsClient.HttpMethod.PATCH,
                            serviceClient + fetchedClosure.documentSelfLink, Utils
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
    public void invalidNegativeTest() throws Throwable {
        Closure initialState = new Closure();
        // Create Closure
        try {
            SimpleHttpsClient.HttpResponse taskResponse = SimpleHttpsClient
                    .execute(SimpleHttpsClient.HttpMethod
                                    .POST, serviceClient + ClosureFactoryService.FACTORY_LINK,
                            Utils.toJson(initialState));
            if (taskResponse != null) {
                assertNotEquals("Closure is not allowed to complete once it is cancelled", 200,
                        taskResponse.statusCode);
            } else {
                fail("Closure is not allowed to complete once it is cancelled");
            }
        } catch (Exception ignored) {
        }
    }

    @Override protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return super.getResourceDescriptionLink(downloadImage, registryType);
    }

    @Test
    public void executeJSLogConfigurationTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4.0; // TODO: fix types

        closureDescState.source = "module.exports = function test(context) {"
                + " console.log('Hello number: ' + context.inputs.a);"
                + " context.outputs.result=context.inputs.a + 1;"
                + " }; ";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 4;
        closureDescState.resources = constraints;

        closureDescState.logConfiguration = new JsonObject();
        closureDescState.logConfiguration.addProperty("type", "json-file");
        JsonObject logConfig = new JsonObject();
        logConfig.addProperty("max-size", "300k");
        closureDescState.logConfiguration.add("config", logConfig);

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
        assertEquals(expectedResult, closure.outputs.get("result").getAsDouble(), 0);

        String resourceLink = closure.resourceLinks.iterator().next();
        String containerId = UriUtils.getLastPathSegment(resourceLink);
        String logsURI = "/resources/container-logs?id=" + containerId;
        logger.info("Fetching logs from: " + logsURI);
        SimpleHttpsClient.HttpResponse response = getResource(logsURI);
        assertNotNull(response);
        LogService.LogServiceState logState = Utils
                .fromJson(response.responseBody, LogService.LogServiceState
                        .class);

        assertNotNull(logState.logs);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    @Ignore("Ingnored until external dependent test service is available.")
    public void executeJSNumbersWithWebHookTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        int expectedResult = 4;

        closureDescState.source = "module.exports = function test(context) {"
                + " console.log('Hello number: ' + context.inputs.a);"
                + " context.outputs.result=context.inputs.a + 1;"
                + " }; ";
        closureDescState.runtime = RUNTIME_NODEJS;
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
        assertEquals(200, consumedContent.statusCode);
        Closure fetchedFromUrl = Utils.fromJson(consumedContent.responseBody, Closure.class);
        assertEquals(closure.state, fetchedFromUrl.state);
        assertEquals(expectedResult, fetchedFromUrl.outputs.get("result").getAsInt(), 0);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

    @Test
    public void executeJSNumbersWithNotAvailableWebHookTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        int expectedResult = 4;

        closureDescState.source = "module.exports = function test(context) {"
                + " console.log('Hello number: ' + context.inputs.a);"
                + " context.outputs.result=context.inputs.a + 1;"
                + " }; ";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.notifyUrl = "/invalid";
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

    @Test
    public void executeJSSelfDefinedParametersTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        double expectedResult = 6;

        closureDescState.source = "module.exports = function test(ctx, a, b) {"
                + "     console.log('Hello numbers: a=' + a + ' b=' + b);"
                + "     ctx.outputs.result = a + b;"
                + " }; ";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 10;
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
        inputs.put("b", new JsonPrimitive(expectedInVar));
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
        assertEquals(expectedInVar, closure.inputs.get("b").getAsInt());
        assertEquals(expectedResult, closure.outputs.get("result").getAsInt(), 0);

        cleanResource(createdClosure.documentSelfLink, serviceClient);
        cleanResource(closureDescription.documentSelfLink, serviceClient);
    }

/*
    @Test
    public void executeJSStringWithExternalCodeSourceNoDependenciesTest() throws Throwable {
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
        closureSource.source = "module.exports = function test(ctx) {"
                + " console.log('Hello string: ' + ctx.inputs.a);"
                + " ctx.outputs.result = ctx.inputs.a.concat(\"c\");};";
        SimpleHttpsClient.HttpResponse taskSourceResponse = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod
                .POST, serviceHostUri + ClosureSourceFactoryService.FACTORY_LINK, Utils.toJson(closureSource));
        assertNotNull(taskSourceResponse);

        closureDescState.sourceURL = serviceHostUri + closureSource.documentSelfLink + "?contentValue=true";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 5;
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
    public void executeJSStringWithExternalCodeSourceWithDependenciesTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        // Cretae external closure source
        ClosureSource closureSource = new ClosureSource();
        closureSource.documentSelfLink = UriUtils
                .buildUriPath(ClosureSourceFactoryService.FACTORY_LINK, UUID.randomUUID().toString());
        closureSource.source = "var moment = require('moment'); module.exports = function test(ctx) {"
                + " console.log('Hello string: ' + ctx.inputs.a + ' time: ' + moment().valueOf());"
                + " ctx.outputs.result = ctx.inputs.a.concat(\"c\");};";
        SimpleHttpsClient.HttpResponse taskSourceResponse = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod
                .POST, serviceHostUri + ClosureSourceFactoryService.FACTORY_LINK, Utils.toJson(closureSource));
        assertNotNull(taskSourceResponse);

        closureDescState.dependencies = "{\"moment\" : \"2.12.0\"}";
        closureDescState.sourceURL = serviceHostUri + closureSource.documentSelfLink + "?contentValue=true";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 10;
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
    public void executeJSStringWithExternalCodeSourceUsingTaskNameAsHandlerTest() throws Throwable {
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
        closureSource.source = "exports.test = function test(ctx) {"
                + " console.log('Hello string: ' + ctx.inputs.a);"
                + " ctx.outputs.result = ctx.inputs.a.concat(\"c\");};";
        SimpleHttpsClient.HttpResponse taskSourceResponse = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod
                .POST, serviceHostUri + ClosureSourceFactoryService.FACTORY_LINK, Utils.toJson(closureSource));
        assertNotNull(taskSourceResponse);

        closureDescState.sourceURL = serviceHostUri + closureSource.documentSelfLink + "?contentValue=true";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 5;
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
    public void executeJSStringWithExternalCodeSourceUsingEntrypointAsHandlerTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "custom_name";
        closureDescState.entrypoint = "modulename.test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        // Create external closure source
        ClosureSource closureSource = new ClosureSource();
        closureSource.documentSelfLink = UriUtils
                .buildUriPath(ClosureSourceFactoryService.FACTORY_LINK, UUID.randomUUID().toString());
        closureSource.source = "exports.test = function test(ctx) {"
                + " console.log('Hello string: ' + ctx.inputs.a);"
                + " ctx.outputs.result = ctx.inputs.a.concat(\"c\");};";
        SimpleHttpsClient.HttpResponse taskSourceResponse = SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod
                .POST, serviceHostUri + ClosureSourceFactoryService.FACTORY_LINK, Utils.toJson(closureSource));
        assertNotNull(taskSourceResponse);

        closureDescState.sourceURL = serviceHostUri + closureSource.documentSelfLink + "?contentValue=true";
        closureDescState.source = "should not be used";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 5;
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
*/
}
