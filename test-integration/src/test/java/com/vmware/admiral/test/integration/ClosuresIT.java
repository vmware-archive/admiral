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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.closures.drivers.DriverConstants;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescriptionFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ResourceConstraints;
import com.vmware.admiral.closures.services.images.DockerImage;
import com.vmware.admiral.closures.services.images.DockerImageFactoryService;
import com.vmware.admiral.closures.util.ClosureUtils;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ClosuresIT extends BaseProvisioningOnCoreOsIT {

    public static final int DOCKER_IMAGE_BUILD_TIMEOUT_SECONDS = 30 * 60;
    private static final long TEST_TASK_MAINTANENACE_TIMEOUT_MLS = 4000;

    protected static String IMAGE_NAME_PREFIX = "vmware/photon-closure-runner_";

    private static final String IMAGE_NAME = IMAGE_NAME_PREFIX
            + DriverConstants.RUNTIME_NODEJS_4;

    private static String RUNTIME_NODEJS = "nodejs";

    private static String dockerBuildImageLink;
    private static String dockerBuildBaseImageLink;

    @BeforeClass
    public static void beforeClass() {
        serviceClient = ServiceClientFactory.createServiceClient(null);
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

    @Override
    @After
    public void provisioningTearDown() throws Exception {
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return null;
    }

    @Before
    public void setup() throws Exception {
        setupCoreOsHost(ContainerHostService.DockerAdapterType.API, false);
        triggerExecutionImageBuildWithoutDependencies();
        dockerBuildImageLink = getBaseUrl()
                + createImageBuildRequestUri(IMAGE_NAME + ":latest", dockerHostCompute
                        .documentSelfLink);
        dockerBuildBaseImageLink = getBaseUrl()
                + createImageBuildRequestUri(IMAGE_NAME + "_base:1.0", dockerHostCompute
                        .documentSelfLink);
    }

    @Test
    public void executeJSNumberParametersTest() throws Throwable {
        logger.info("Executing  against: " + getBaseUrl());

        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedResult = 4;

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
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription);

        // Execute the created Closure
        Closure closureRequest = new Closure();
        Map<String, JsonElement> inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest);

        // Wait for the completion timeout
        Thread.sleep(closureDescState.resources.timeoutSeconds * 1000
                + TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        Closure closure = getClosure(createdClosure.documentSelfLink);
        assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, closure.state);

        assertEquals(expectedInVar, closure.inputs.get("a").getAsInt());
        assertEquals(expectedResult, closure.outputs.get("result").getAsInt(), 0);

        cleanResourceUri(closureDescription.documentSelfLink);
        cleanResourceUri(createdClosure.documentSelfLink);
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
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload);
        assertNotNull(closureDescription);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription);
        assertEquals(closureDescription.documentSelfLink, createdClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.CREATED, createdClosure.state);

        Closure closureRequest = new Closure();
        // Executing the created Closure
        executeClosure(createdClosure, closureRequest);

        // Wait for the completion timeout
        Thread.sleep(closureDescState.resources.timeoutSeconds * 1000
                + TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        Closure fetchedClosure = getClosure(createdClosure.documentSelfLink);

        assertEquals(closureDescription.documentSelfLink, fetchedClosure.descriptionLink);
        assertEquals(TaskState.TaskStage.FAILED, fetchedClosure.state);
        assertTrue(fetchedClosure.errorMsg.length() > 0);

        cleanResourceUri(closureDescription.documentSelfLink);
        cleanResourceUri(createdClosure.documentSelfLink);
    }

    protected void cleanResourceUri(String resUri) throws Exception {
        URI targetUri = URI.create(getBaseUrl() + buildServiceUri(resUri));
        sendRequest(serviceClient, Operation.createDelete(targetUri));
    }

    protected Closure createClosure(ClosureDescription closureDescription)
            throws InterruptedException, ExecutionException, TimeoutException {
        Closure closureState = new Closure();
        closureState.descriptionLink = closureDescription.documentSelfLink;
        URI targetUri = URI.create(getBaseUrl()
                + buildServiceUri(ClosureFactoryService.FACTORY_LINK));
        Operation op = sendRequest(serviceClient,
                Operation.createPost(targetUri).setBody(closureState));
        return op.getBody(Closure.class);
    }

    protected ClosureDescription createClosureDescription(String taskDefPayload)
            throws InterruptedException, ExecutionException, TimeoutException {
        URI targetUri = URI.create(getBaseUrl()
                + buildServiceUri(ClosureDescriptionFactoryService.FACTORY_LINK));
        Operation op = sendRequest(serviceClient,
                Operation.createPost(targetUri).setBody(taskDefPayload));
        return op.getBody(ClosureDescription.class);
    }

    protected void executeClosure(Closure createdClosure, Closure closureRequest)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException, InterruptedException, ExecutionException, TimeoutException {
        URI targetUri = URI.create(getBaseUrl() + buildServiceUri(createdClosure.documentSelfLink));
        Operation op = sendRequest(serviceClient,
                Operation.createPost(targetUri).setBody(closureRequest));
        assertNotNull(op);
    }

    protected Closure getClosure(String taskLink)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException, InterruptedException, ExecutionException, TimeoutException {
        URI targetUri = URI.create(getBaseUrl() + buildServiceUri(taskLink));
        Operation op = sendRequest(serviceClient, Operation.createGet(targetUri));

        return op.getBody(Closure.class);
    }

    private void triggerExecutionImageBuildWithoutDependencies() throws Exception {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;
        int expectedResult = 4;

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
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload);

        // Create Closure
        Closure createdClosure = createClosure(closureDescription);

        // Execute the created Closure
        Closure closureRequest = new Closure();
        Map<String, JsonElement> inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        closureRequest.inputs = inputs;

        executeClosure(createdClosure, closureRequest);

        // Wait for the completion timeout
        waitForBuildCompletion(IMAGE_NAME, closureDescription, TEST_TASK_MAINTANENACE_TIMEOUT_MLS);

        waitForTaskState(createdClosure.documentSelfLink, TaskState.TaskStage.FINISHED);

        Closure closure = getClosure(createdClosure.documentSelfLink);
        assertNotNull(closure);

        assertEquals(createdClosure.descriptionLink, closure.descriptionLink);
        assertEquals(TaskState.TaskStage.FINISHED, closure.state);

        assertEquals(expectedInVar, closure.inputs.get("a").getAsInt());
        assertEquals(expectedResult, closure.outputs.get("result").getAsInt(), 0);

        cleanResourceUri(closureDescription.documentSelfLink);
        cleanResourceUri(createdClosure.documentSelfLink);
    }

    protected String waitForBuildCompletion(String imagePrefix,
            ClosureDescription closureDescription,
            long timeout) throws Exception {
        String imageName = prepareImageName(imagePrefix, closureDescription);
        logger.info(
                "Build for docker execution image: " + imageName + " on host: " + dockerHostCompute
                        .documentSelfLink);
        String dockerBuildImageLink = createImageBuildRequestUri(imageName,
                dockerHostCompute.documentSelfLink);
        long startTime = System.currentTimeMillis();
        logger.info("Waiting for docker image build: " + dockerBuildImageLink);
        while (!isImageReady(getBaseUrl(), dockerBuildImageLink) && !isTimeoutElapsed(startTime,
                DOCKER_IMAGE_BUILD_TIMEOUT_SECONDS)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        logger.info("Docker image " + imageName + " build on host: "
                + dockerHostCompute.documentSelfLink);
        Thread.sleep(closureDescription.resources.timeoutSeconds.intValue() * 1000 + timeout);

        return dockerBuildImageLink;
    }

    private boolean isImageReady(String serviceHostUri, String dockerBuildImageLink)
            throws Exception {
        SimpleHttpsClient.HttpResponse imageRequestResponse = SimpleHttpsClient.execute(
                SimpleHttpsClient.HttpMethod
                .GET, serviceHostUri + dockerBuildImageLink, null);
        if (imageRequestResponse == null || imageRequestResponse.responseBody == null) {
            return false;
        }
        DockerImage imageRequest = Utils.fromJson(imageRequestResponse.responseBody,
                DockerImage.class);
        assertNotNull(imageRequest);

        if (TaskState.isFailed(imageRequest.taskInfo)
                || TaskState.isCancelled(imageRequest.taskInfo)) {
            throw new Exception("Unable to build docker execution image: " + dockerBuildImageLink);
        }

        return TaskState.isFinished(imageRequest.taskInfo);
    }

    private static String prepareImageName(String imagePrefix, ClosureDescription taskDef) {
        return imagePrefix + ":" + prepareImageTag(taskDef);
    }

    private static String prepareImageTag(ClosureDescription closureDescription) {
        if (ClosureUtils.isEmpty(closureDescription.sourceURL)) {
            if (ClosureUtils.isEmpty(closureDescription.dependencies)) {
                // no dependencies
                return "latest";
            }
            return ClosureUtils.calculateHash(new String[] { closureDescription.dependencies });
        } else {
            return ClosureUtils.calculateHash(new String[] { closureDescription.sourceURL });
        }
    }

    protected static String createImageBuildRequestUri(String imageName, String computeStateLink) {
        String imageBuildRequestId = ClosureUtils.calculateHash(new String[] { imageName, "/",
                computeStateLink });

        return UriUtils.buildUriPath(DockerImageFactoryService.FACTORY_LINK, imageBuildRequestId);
    }

    private boolean isTimeoutElapsed(long startTime, int timeout) {
        return System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(timeout);
    }

    protected void waitForTaskState(String taskLink, TaskState.TaskStage state)
            throws Exception {
        Closure fetchedClosure = getClosure(taskLink);
        long startTime = System.currentTimeMillis();
        while (state != fetchedClosure.state && !isTimeoutElapsed(startTime, 120)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
