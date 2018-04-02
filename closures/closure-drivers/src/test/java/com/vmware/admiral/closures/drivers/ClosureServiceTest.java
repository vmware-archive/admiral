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

package com.vmware.admiral.closures.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.closures.drivers.nashorn.EmbeddedNashornJSDriver;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescriptionFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ResourceConstraints;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

@SuppressWarnings("unchecked")
public class ClosureServiceTest extends BasicReusableHostTestCase {

    private static final int TEST_TASK_MAINTANENACE_TIMEOUT_MLS = 10;

    @Before
    public void setUp() throws Exception {
        try {
            if (this.host.getServiceStage(ClosureFactoryService.FACTORY_LINK) != null) {
                return;
            }

            DriverRegistry driverRegistry = new DriverRegistryImpl();
            driverRegistry.register(DriverConstants.RUNTIME_NASHORN,
                    new EmbeddedNashornJSDriver(this.host));

            // Start a closure factory services
            this.host.startServiceAndWait(ClosureDescriptionFactoryService.class,
                    ClosureDescriptionFactoryService.FACTORY_LINK);

            ClosureFactoryService closureFactoryService = new ClosureFactoryService(driverRegistry,
                    TEST_TASK_MAINTANENACE_TIMEOUT_MLS * 1000);
            this.host.startServiceAndWait(closureFactoryService, ClosureFactoryService.FACTORY_LINK,
                    null);

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @AfterClass
    public static void clean() {
        BasicReusableHostTestCase.tearDownOnce();
    }

    @Test
    public void addDefaultTaskTest() throws Throwable {
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";
        closureDefState.source = "var a = 1; print(\"Hello \" + a);";
        closureDefState.runtime = "nashorn";
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> assertNull(e)));
        this.host.send(post);
        this.host.testWait();

        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();
        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void addFailedClosureWithExtCallback() throws Throwable {
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";
        closureDefState.source = "var a = 1; print(\"Hello \" + a);";
        closureDefState.runtime = "nashorn";
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> assertNull(e)));
        this.host.send(post);
        this.host.testWait();

        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = ClosureFactoryService.SELF_LINK + "/" + UUID.randomUUID()
                .toString();

        closureState.serviceTaskCallback = ServiceTaskCallback.create
                ("http://localhost/testcallback");

        Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        ServiceTaskCallback.ServiceTaskCallbackResponse response = closureState.serviceTaskCallback
                .getFailedResponse(new Exception("Build Image Exception"));

        this.host.testStart(1);
        Operation closurePatch = Operation
                .createPatch(UriUtils.buildUri(this.host, closureState.documentSelfLink))
                .setBody(response)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.FAILED, closureResponses[0].state);
                }));
        this.host.send(closurePatch);
        this.host.testWait();

        clean(UriUtils.buildUri(this.host, closureState.documentSelfLink));
        clean(closureDefChildURI);
    }

    @Test
    public void addClosureWithWebhook() throws Throwable {
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";
        closureDefState.source = "var a = 1; print(\"Hello \" + a);";
        closureDefState.runtime = "nashorn";
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        closureDefState.notifyUrl = UriUtils.buildFactoryUri(this.host, ClosureFactoryService
                .class).toString();

        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> assertNull(e)));
        this.host.send(post);
        this.host.testWait();

        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = ClosureFactoryService.SELF_LINK + "/" + UUID.randomUUID()
                .toString();

        closureState.serviceTaskCallback = ServiceTaskCallback.create
                ("http://localhost/testcallback");

        Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        closureResponses[0].state = TaskStage.STARTED;

        this.host.testStart(1);
        Operation closurePatch = Operation
                .createPatch(UriUtils.buildUri(this.host, closureState.documentSelfLink))
                .setBody(closureResponses[0])
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.STARTED, closureResponses[0].state);
                }));
        this.host.send(closurePatch);
        this.host.testWait();

        closureResponses[0].state = TaskStage.FINISHED;

        this.host.testStart(1);
        closurePatch = Operation
                .createPatch(UriUtils.buildUri(this.host, closureState.documentSelfLink))
                .setBody(closureResponses[0])
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, closureResponses[0].state);
                }));
        this.host.send(closurePatch);
        this.host.testWait();

        // give sometime to webhook
        Thread.sleep(1000);

        factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(UriUtils.buildExpandLinksQueryUri(factoryTaskUri))
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    ServiceDocumentQueryResult result = o.getBody(ServiceDocumentQueryResult.class);
                    Object jsonProject = result.documents.values().iterator().next();
                    Closure closure = Utils
                            .fromJson(jsonProject, Closure.class);
                    assertNotNull(closure);
                    assertEquals(closure.name, closureDefState.name);
                    assertTrue("Webhook not invoked!", closure.documentVersion > 2);

                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(UriUtils.buildUri(this.host, closureState.documentSelfLink));
        clean(closureDefChildURI);
    }

    @Test
    public void executeJSNumberParametersTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";

        int expectedInVar = 3;
        int expectedOutVar = 3;
        double expectedResult = 4.0;

        closureDefState.source =
                "function test(x) {print('Hello number: ' + x); return x + 1;} var b = " +
                        expectedOutVar
                        + "; result = test(inputs.a);";
        closureDefState.runtime = "nashorn";
        closureDefState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 10;
        closureDefState.resources = constraints;
        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();
        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);

                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] finalClosureResponse = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    finalClosureResponse[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            finalClosureResponse[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, finalClosureResponse[0].state);

                    assertEquals(expectedInVar, finalClosureResponse[0].inputs.get("a").getAsInt());
                    assertEquals(expectedResult,
                            finalClosureResponse[0].outputs.get("result").getAsDouble(), 0);
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void executeJSOverwriteDefaultNumberParametersTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDesc = new ClosureDescription();
        closureDesc.name = "test";

        int defaultInVar = 3;
        double expectedResult = 7.0;

        closureDesc.source = "function test(a, b) {print('Hello number: ' + a + ' b ' + b);"
                + " return a + b;} "
                + "result = test(inputs.a, inputs.b);";
        closureDesc.runtime = "nashorn";
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(defaultInVar));
        inputs.put("b", new JsonPrimitive(defaultInVar));
        closureDesc.inputs = inputs;
        closureDesc.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDesc.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 10;
        closureDesc.resources = constraints;
        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/" + closureDesc.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDesc)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/" + closureDesc.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();
        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);

                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Closure closureRequest = new Closure();
        inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(defaultInVar + 1));
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] finalClosureResponse = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    finalClosureResponse[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            finalClosureResponse[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, finalClosureResponse[0].state);

                    assertEquals(defaultInVar + 1, finalClosureResponse[0].inputs.get("a").getAsInt
                            ());
                    assertEquals(defaultInVar, finalClosureResponse[0].inputs.get("b").getAsInt());
                    assertEquals(expectedResult,
                            finalClosureResponse[0].outputs.get("result").getAsDouble(), 0);
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void executeJSArrayOfNumberParametersTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";

        Integer[] expectedInVar = { 1, 2, 3 };
        Integer expectedOutVar = 1;
        Integer[] expectedResult = { 2, 3, 4 };

        closureDefState.source =
                "function increment(x) {" + "print('Hello array of numbers: ' + x);"
                        + "for(var i = 0; i < x.length; i++) {" + "x[i] = x[i] + 1;" + "}"
                        + " return x;}" + " var b = ["
                        + expectedOutVar + "]; result = increment(inputs.a);";
        closureDefState.runtime = "nashorn";
        closureDefState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 2;
        closureDefState.resources = constraints;
        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();
        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();

        JsonArray jsArray = new JsonArray();
        for (int x : expectedInVar) {
            jsArray.add(new JsonPrimitive(x));
        }
        inputs.put("a", jsArray);
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] finalClosureResponse = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    finalClosureResponse[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            finalClosureResponse[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, finalClosureResponse[0].state);

                    verifyJsonArrayInts(expectedInVar, finalClosureResponse[0].inputs.get("a")
                            .getAsJsonArray());
                    verifyJsonArrayInts(expectedResult,
                            finalClosureResponse[0].outputs.get("result").getAsJsonArray());
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void executeJSArrayOfStringParametersTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";

        String[] expectedInVar = { "a", "b", "c" };
        String expectedOutVar = "test";
        String[] expectedResult = { "a_t", "b_t", "c_t" };

        closureDefState.source = "function appnd(x) {" + "print('Hello array of strings: ' + x);"
                + "for(var i = 0; i < x.length; i++) {" + "x[i] = x[i] + '_t';" + "}"
                + "print('Hello number: ' + x); return x;}" + " var b = ['" + expectedOutVar
                + "']; result = appnd(inputs.a);";
        closureDefState.runtime = "nashorn";
        closureDefState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 4;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();
        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();

        JsonArray jsArray = new JsonArray();
        for (String x : expectedInVar) {
            jsArray.add(new JsonPrimitive(x));
        }
        inputs.put("a", jsArray);
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] finalClosureResponse = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    finalClosureResponse[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            finalClosureResponse[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, finalClosureResponse[0].state);

                    verifyJsonArrayStrings(expectedInVar, finalClosureResponse[0].inputs.get("a")
                            .getAsJsonArray());
                    verifyJsonArrayStrings(expectedResult,
                            finalClosureResponse[0].outputs.get("result").getAsJsonArray());
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void executeJSArrayOfBooleanParametersTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";

        Boolean[] expectedInVar = { true, true, true };
        Boolean expectedOutVar = true;
        Boolean[] expectedResult = { false, false, false };

        closureDefState.source = "function appl(x) {" + "print('Hello array of booleans: ' + x);"
                + "for(var i = 0; i < x.length; i++) {" + "x[i] = !x[i];" + "}" + "return x;}"
                + " var b = ["
                + expectedOutVar + "]; result = appl(inputs.a);";
        closureDefState.runtime = "nashorn";
        closureDefState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 1;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();

        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();

        JsonArray jsArray = new JsonArray();
        for (Boolean x : expectedInVar) {
            jsArray.add(new JsonPrimitive(x));
        }
        inputs.put("a", jsArray);
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] finalClosureResponse = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    finalClosureResponse[0] = o.getBody(Closure.class);

                    assertEquals(closureState.descriptionLink,
                            finalClosureResponse[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, finalClosureResponse[0].state);
                    verifyJsonArrayBooleans(expectedInVar, finalClosureResponse[0].inputs.get("a")
                            .getAsJsonArray());
                    verifyJsonArrayBooleans(expectedResult,
                            finalClosureResponse[0].outputs.get("result").getAsJsonArray());
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
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
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";

        TestObject expectedInVar = new TestObject();
        expectedInVar.strTest = "test";
        expectedInVar.intTest = 1;
        expectedInVar.boolTest = true;
        int expectedOutVar = 3;
        String expectedResult = expectedInVar.strTest + "_changed";

        closureDefState.source = "function test(x) {print('Hello object: ' + x.strTest);"
                + " x.strTest = x.strTest + '_changed';"
                + " x.intTest = x.intTest + 1; x.boolTest = !x.boolTest; return x;" + "}"
                + " var b = " + expectedOutVar
                + "; result = test(inputs.a);";
        closureDefState.runtime = "nashorn";
        closureDefState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 1;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();

        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();
        Gson gson = new Gson();
        inputs.put("a", gson.toJsonTree(expectedInVar));
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] finalClosureResponse = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    finalClosureResponse[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            finalClosureResponse[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, finalClosureResponse[0].state);
                    JsonObject inObj = finalClosureResponse[0].inputs.get("a").getAsJsonObject();

                    Gson json = new Gson();
                    TestObject deserialObj = json.fromJson(inObj, TestObject.class);

                    assertEquals(expectedInVar.strTest, deserialObj.strTest);
                    assertEquals(expectedInVar.intTest, deserialObj.intTest);
                    assertEquals(expectedInVar.boolTest, deserialObj.boolTest);

                    JsonObject jsonResultObj = finalClosureResponse[0].outputs.get("result")
                            .getAsJsonObject();
                    TestObject resultObj = json.fromJson(jsonResultObj, TestObject.class);

                    assertEquals(expectedResult, resultObj.strTest);
                    assertEquals(expectedInVar.intTest + 1, resultObj.intTest);
                    assertEquals(!expectedInVar.boolTest, resultObj.boolTest);
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void executeJSNestedObjectParametersTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";

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

        closureDefState.source = "function test(x) {print('Hello object: ' + x.objTest);"
                + " x.objTest.strTest = x.objTest.strTest + '_changed';"
                + " x.objTest.intTest = x.objTest.intTest + 1; x.objTest.boolTest = !x.objTest.boolTest; return x;"
                + "}" + " var b = " + expectedOutVar + "; result = test(inputs.a);";
        closureDefState.runtime = "nashorn";
        closureDefState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 2;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();

        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();
        Gson gson = new Gson();
        inputs.put("a", gson.toJsonTree(expectedInVar));
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] finalClosureResponse = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    finalClosureResponse[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            finalClosureResponse[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, finalClosureResponse[0].state);

                    JsonObject inObj = finalClosureResponse[0].inputs.get("a").getAsJsonObject().get
                            ("objTest")
                            .getAsJsonObject();

                    Gson json = new Gson();
                    NestedTestObject deserialObj = json.fromJson(inObj, NestedTestObject.class);
                    assertEquals(expectedInVar.objTest.strTest, deserialObj.strTest);
                    assertEquals(expectedInVar.objTest.intTest, deserialObj.intTest);
                    assertEquals(expectedInVar.objTest.boolTest, deserialObj.boolTest);

                    JsonObject jsonChild = finalClosureResponse[0].outputs.get("result")
                            .getAsJsonObject()
                            .get("objTest").getAsJsonObject();
                    NestedTestObject resultObj = json.fromJson(jsonChild, NestedTestObject.class);
                    assertEquals(expectedResult, resultObj.strTest);
                    assertEquals(expectedInVar.objTest.intTest + 1, resultObj.intTest);
                    assertEquals(!expectedInVar.objTest.boolTest, resultObj.boolTest);
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void executeJSArrayOfObjectParametersTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";

        TestObject expectedInVar = new TestObject();
        expectedInVar.strTest = "test";
        expectedInVar.intTest = 1;
        expectedInVar.boolTest = true;
        int expectedOutVar = 3;
        String expectedResult = expectedInVar.strTest + "_changed";

        closureDefState.source = "function test(x) { print('Hello object: ' + x[0].strTest);"
                + " x[0].strTest = x[0].strTest + '_changed';"
                + " x[0].intTest = x[0].intTest + 1; x[0].boolTest = !x[0].boolTest; return x;"
                + "}" + " var b = "
                + expectedOutVar + "; result = test(inputs.a);";
        closureDefState.runtime = "nashorn";
        closureDefState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 4;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();

        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();

        JsonArray jsArray = new JsonArray();
        jsArray.add(new Gson().toJsonTree(expectedInVar));
        inputs.put("a", jsArray);
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] finalClosureResponse = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    finalClosureResponse[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            finalClosureResponse[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, finalClosureResponse[0].state);
                    JsonObject inObj = finalClosureResponse[0].inputs.get("a").getAsJsonArray()
                            .get(0)
                            .getAsJsonObject();

                    Gson json = new Gson();
                    TestObject deserialObj = json.fromJson(inObj, TestObject.class);

                    assertEquals(expectedInVar.strTest, deserialObj.strTest);
                    assertEquals(expectedInVar.intTest, deserialObj.intTest);
                    assertEquals(expectedInVar.boolTest, deserialObj.boolTest);

                    JsonObject jsonResultObj = finalClosureResponse[0].outputs.get("result")
                            .getAsJsonArray().get(0)
                            .getAsJsonObject();
                    TestObject resultObj = json.fromJson(jsonResultObj, TestObject.class);

                    assertEquals(expectedResult, resultObj.strTest);
                    assertEquals(expectedInVar.intTest + 1, resultObj.intTest);
                    assertEquals(!expectedInVar.boolTest, resultObj.boolTest);
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void executeJSStringParametersTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";

        String expectedInVar = "a";
        String expectedOutVar = "b";
        String expectedResult = "ac";

        closureDefState.source =
                "function test(x) {print('Hello string: ' + x); return x.concat(\"c\");} var b = '"
                        + expectedOutVar + "'; result = test(inputs.a);";
        closureDefState.runtime = "nashorn";
        closureDefState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 2;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();

        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] finalClosureResponse = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    finalClosureResponse[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            finalClosureResponse[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, finalClosureResponse[0].state);

                    assertEquals(expectedInVar,
                            finalClosureResponse[0].inputs.get("a").getAsString());
                    assertEquals(expectedResult,
                            finalClosureResponse[0].outputs.get("result").getAsString());
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void executeJSBooleanParametersTest() throws Throwable {
        this.host.setTimeoutSeconds(1000);
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";

        boolean expectedInVar = true;
        int expectedOutVar = 1;
        boolean expectedResult = false;

        closureDefState.source =
                "function test(x) {print('Hello boolean: ' + x); return !x;} var b = "
                        + expectedOutVar
                        + "; result = test(inputs.a);";
        closureDefState.runtime = "nashorn";
        closureDefState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 1;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();

        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] finalClosureResponse = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    finalClosureResponse[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            finalClosureResponse[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, finalClosureResponse[0].state);

                    assertEquals(expectedInVar,
                            finalClosureResponse[0].inputs.get("a").getAsBoolean());
                    assertEquals(expectedResult,
                            finalClosureResponse[0].outputs.get("result").getAsBoolean());
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }


    @Test
    public void executeAlreadyExecutedClosure() throws Throwable {
        this.host.setTimeoutSeconds(1000);
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";

        boolean expectedInVar = true;
        int expectedOutVar = 1;
        boolean expectedResult = false;

        closureDefState.source =
                "function test(x) {print('Hello boolean: ' + x); return !x;} var b = "
                        + expectedOutVar
                        + "; result = test(inputs.a);";
        closureDefState.runtime = "nashorn";
        closureDefState.outputNames = new ArrayList<>(Collections.singletonList("result"));
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 1;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();

        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        closureRequest.inputs = inputs;
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] finalClosureResponse = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    finalClosureResponse[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            finalClosureResponse[0].descriptionLink);
                    assertEquals(TaskStage.FINISHED, finalClosureResponse[0].state);

                    assertEquals(expectedInVar,
                            finalClosureResponse[0].inputs.get("a").getAsBoolean());
                    assertEquals(expectedResult,
                            finalClosureResponse[0].outputs.get("result").getAsBoolean());
                }));
        this.host.send(closureGet);
        this.host.testWait();


        // Executing the already executed Closure
        this.host.testStart(1);
        closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(closureRequest)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(e);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        clean(closureChildURI);
        clean(closureDefChildURI);
    }


    @Test
    public void executeInvalidJSScriptTaskTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";
        closureDefState.source = "var a = 1; print(\"Hello \" + invalid);";
        closureDefState.runtime = "nashorn";
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 2;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();
        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();

        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(new Closure())
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] endStateClosureResponses = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    endStateClosureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            endStateClosureResponses[0].descriptionLink);
                    assertEquals(TaskStage.FAILED, endStateClosureResponses[0].state);
                    assertTrue(endStateClosureResponses[0].errorMsg.length() > 0);
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void executeTimeoutedJSScriptTaskTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";
        closureDefState.source = "function sleep(delay) {var start = new Date().getTime();while (new Date().getTime() < start + delay) {}} sleep(60000);";
        closureDefState.runtime = "nashorn";
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 1;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();

        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();
        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(new Closure())
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] endStateClosureResponses = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    endStateClosureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            endStateClosureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CANCELLED, endStateClosureResponses[0].state);
                }));
        this.host.send(closureGet);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void completeFailTimeoutedJSScriptTaskTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";
        closureDefState.source = "function sleep(delay) {var start = new Date().getTime();while (new Date().getTime() < start + delay) {}} sleep(60000);";
        closureDefState.runtime = "nashorn";
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 1;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();

        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();
        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(new Closure())
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] endStateClosureResponses = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    endStateClosureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            endStateClosureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CANCELLED, endStateClosureResponses[0].state);
                }));
        this.host.send(closureGet);
        this.host.testWait();

        // Try to complete already cancelled Closure
        endStateClosureResponses[0].state = TaskStage.FINISHED;
        this.host.testStart(1);
        post = Operation
                .createPatch(factoryUri)
                .setBody(endStateClosureResponses[0])
                .setCompletion(BasicReusableHostTestCase.getSafeHandler(
                        (o, e) -> assertNotNull(
                                "Closure is not allowed to complete once it is cancelled", e)));
        this.host.send(post);
        this.host.testWait();

        // Try to fail already cancelled Closure
        endStateClosureResponses[0].state = TaskStage.FAILED;
        this.host.testStart(1);
        post = Operation
                .createPatch(factoryUri)
                .setBody(endStateClosureResponses[0])
                .setCompletion(BasicReusableHostTestCase.getSafeHandler(
                        (o, e) -> assertNotNull(
                                "Closure is not allowed to fail once it is cancelled", e)));
        this.host.send(post);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void completeOrFailOutdatedJSScriptTaskTest() throws Throwable {
        // Create Closure Definition
        URI factoryUri = UriUtils
                .buildFactoryUri(this.host, ClosureDescriptionFactoryService.class);
        this.host.testStart(1);
        ClosureDescription closureDefState = new ClosureDescription();
        closureDefState.name = "test";
        closureDefState.source = "function sleep(delay) {var start = new Date().getTime();while (new Date().getTime() < start + delay) {}} sleep(60000);";
        closureDefState.runtime = "nashorn";
        closureDefState.documentSelfLink = UUID.randomUUID().toString();
        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 1;
        closureDefState.resources = constraints;

        ClosureDescription[] responses = new ClosureDescription[1];
        URI closureDefChildURI = UriUtils.buildUri(this.host,
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink);
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(closureDefState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    assertNull(e);
                    responses[0] = o.getBody(ClosureDescription.class);
                    assertNotNull(responses[0]);
                }));
        this.host.send(post);
        this.host.testWait();

        // Create Closure
        URI factoryTaskUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure closureState = new Closure();

        closureState.descriptionLink =
                ClosureDescriptionFactoryService.FACTORY_LINK + "/"
                        + closureDefState.documentSelfLink;
        closureState.documentSelfLink = UUID.randomUUID().toString();
        URI closureChildURI = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureState.documentSelfLink);
        final Closure[] closureResponses = new Closure[1];
        Operation closurePost = Operation
                .createPost(factoryTaskUri)
                .setBody(closureState)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink, closureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CREATED, closureResponses[0].state);
                }));
        this.host.send(closurePost);
        this.host.testWait();

        // Executing the created Closure
        this.host.testStart(1);
        Operation closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(new Closure())
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Wait for the completion timeout
        waitForCompletion(closureState.documentSelfLink, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        final Closure[] endStateClosureResponses = new Closure[1];
        this.host.testStart(1);
        Operation closureGet = Operation
                .createGet(closureChildURI)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    endStateClosureResponses[0] = o.getBody(Closure.class);
                    assertEquals(closureState.descriptionLink,
                            endStateClosureResponses[0].descriptionLink);
                    assertEquals(TaskStage.CANCELLED, endStateClosureResponses[0].state);
                }));
        this.host.send(closureGet);
        this.host.testWait();

        // Request bring new execution of the created Closure.
        this.host.testStart(1);
        closureExecPost = Operation
                .createPost(closureChildURI)
                .setBody(new Closure())
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, e) -> {
                    closureResponses[0] = o.getBody(Closure.class);
                    assertNotNull(closureResponses[0]);
                }));
        this.host.send(closureExecPost);
        this.host.testWait();

        // Try to complete outdated Closure
        endStateClosureResponses[0].state = TaskStage.FINISHED;
        this.host.testStart(1);
        post = Operation
                .createPatch(factoryUri)
                .setBody(endStateClosureResponses[0])
                .setCompletion(BasicReusableHostTestCase.getSafeHandler(
                        (o, e) -> assertNotNull(
                                "Closure is not allowed to complete once it is CANCELLED", e)));
        this.host.send(post);
        this.host.testWait();

        // Try to fail outdated cancelled Closure
        endStateClosureResponses[0].state = TaskStage.FAILED;
        this.host.testStart(1);
        post = Operation
                .createPatch(factoryUri)
                .setBody(endStateClosureResponses[0])
                .setCompletion(BasicReusableHostTestCase.getSafeHandler(
                        (o, e) -> assertNotNull(
                                "Closure is not allowed to fail once it is CANCELLED", e)));
        this.host.send(post);
        this.host.testWait();

        clean(closureChildURI);
        clean(closureDefChildURI);
    }

    @Test
    public void invalidNegativeTest() throws Throwable {
        URI factoryUri = UriUtils.buildFactoryUri(this.host, ClosureFactoryService.class);
        this.host.testStart(1);
        Closure initialState = new Closure();
        initialState.documentSelfLink = UUID.randomUUID().toString();
        Operation post = Operation
                .createPost(factoryUri)
                .setBody(initialState)
                .setCompletion(
                        BasicReusableHostTestCase.getSafeHandler((o, e) -> assertNotNull(e)));
        this.host.send(post);
        this.host.testWait();
    }

    // HELPER METHODS

    private void verifyJsonArrayStrings(Object[] javaArray, JsonArray jsArray) {
        assertEquals(javaArray.length, jsArray.size());
        for (int i = 0; i < javaArray.length; i++) {
            assertEquals(javaArray[i], jsArray.get(i).getAsString());
        }
    }

    private void verifyJsonArrayInts(Object[] javaArray, JsonArray jsArray) {
        assertEquals(javaArray.length, jsArray.size());
        for (int i = 0; i < javaArray.length; i++) {
            assertEquals(javaArray[i], jsArray.get(i).getAsInt());
        }
    }

    private void verifyJsonArrayBooleans(Object[] javaArray, JsonArray jsArray) {
        assertEquals(javaArray.length, jsArray.size());
        for (int i = 0; i < javaArray.length; i++) {
            assertEquals(javaArray[i], jsArray.get(i).getAsBoolean());
        }
    }

    private void clean(URI childURI) throws Throwable {
        this.host.testStart(1);
        CompletableFuture<Operation> c = new CompletableFuture<>();
        Operation delete = Operation
                .createDelete(childURI)
                .setCompletion(BasicReusableHostTestCase
                        .getSafeHandler(
                                (o, ex) -> {
                                    if (ex != null) {
                                        c.completeExceptionally(ex);
                                    } else {
                                        c.complete(o);
                                    }
                                }
                        ));

        this.host.send(delete);
        this.host.testWait();

        c.get(5000, TimeUnit.MILLISECONDS);
    }

    private Closure getClosure(String closureLink) throws InterruptedException, ExecutionException,
            TimeoutException {

        CompletableFuture<Operation> c = new CompletableFuture<>();

        URI closureUri = UriUtils.buildUri(this.host,
                ClosureFactoryService.FACTORY_LINK + "/" + closureLink);
        Operation closureGet = Operation
                .createGet(closureUri)
                .setCompletion(BasicReusableHostTestCase.getSafeHandler((o, ex) -> {
                    if (ex != null) {
                        c.completeExceptionally(ex);

                    } else {
                        c.complete(o);
                    }

                }));

        this.host.testStart(1);
        this.host.send(closureGet);
        this.host.testWait();

        return c.get(2000, TimeUnit.MILLISECONDS).getBody(Closure.class);
    }

    private void waitForCompletion(String closureLink, int timeout)
            throws Exception {
        Closure fetchedClosure = getClosure(closureLink);
        long startTime = System.currentTimeMillis();
        while (!isCompleted(fetchedClosure) && !isTimeoutElapsed(startTime, timeout)) {
            try {
                Thread.sleep(500);
                fetchedClosure = getClosure(closureLink);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isCompleted(Closure fetchedClosure) {
        return TaskStage.CREATED != fetchedClosure.state
                && TaskStage.STARTED != fetchedClosure.state;
    }

    private boolean isTimeoutElapsed(long startTime, int timeout) {
        return System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(timeout);
    }

}
