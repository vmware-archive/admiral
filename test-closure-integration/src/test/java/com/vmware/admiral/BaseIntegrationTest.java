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

package com.vmware.admiral;

import static org.junit.Assert.assertEquals;

import static com.vmware.admiral.TestPropertiesUtil.getTestRequiredProp;

import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.gson.JsonArray;

import org.junit.Assert;

import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescriptionFactoryService;
import com.vmware.admiral.closures.services.images.DockerImage;
import com.vmware.admiral.closures.services.images.DockerImageFactoryService;
import com.vmware.admiral.closures.util.ClosureUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Base of integration tests.
 */
public class BaseIntegrationTest extends BaseProvisioningOnCoreOsIT {

    public static final int DOCKER_IMAGE_BUILD_TIMEOUT_SECONDS = 30 * 60;

    protected static final String TEST_WEB_SERVER_URL_PROP_NAME = "test.webserver.url";

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType) throws Exception {
        return null;
    }

    protected void cleanResource(String targetLink, ServiceClient serviceClient) throws Exception {
        URI targetUri = URI.create(getBaseUrl() + buildServiceUri(targetLink));
        sendRequest(serviceClient, Operation.createDelete(targetUri));
    }

    protected SimpleHttpsClient.HttpResponse getResource(String targetLink) throws Exception {
        URI targetUri = URI.create(getBaseUrl() + buildServiceUri(targetLink));
        SimpleHttpsClient.HttpResponse response = SimpleHttpsClient
                .execute(SimpleHttpsClient.HttpMethod.GET, targetUri.toString());
        return response;
    }

    protected Closure createClosure(ClosureDescription closureDescription, ServiceClient serviceClient)
            throws InterruptedException, ExecutionException, TimeoutException {
        Closure closureState = new Closure();
        closureState.descriptionLink = closureDescription.documentSelfLink;
        URI targetUri = URI.create(getBaseUrl()
                + buildServiceUri(ClosureFactoryService.FACTORY_LINK));
        Operation op = sendRequest(serviceClient,
                Operation.createPost(targetUri).setBody(closureState));
        return op.getBody(Closure.class);
    }

    protected ClosureDescription createClosureDescription(String taskDefPayload, ServiceClient serviceClient)
            throws InterruptedException, ExecutionException, TimeoutException {
        URI targetUri = URI.create(getBaseUrl()
                + buildServiceUri(ClosureDescriptionFactoryService.FACTORY_LINK));
        Operation op = sendRequest(serviceClient,
                Operation.createPost(targetUri).setBody(taskDefPayload));
        return op.getBody(ClosureDescription.class);
    }

    protected void executeClosure(Closure createdClosure, Closure closureRequest, ServiceClient serviceClient)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException, InterruptedException, ExecutionException, TimeoutException {
        URI targetUri = URI.create(getBaseUrl() + buildServiceUri(createdClosure.documentSelfLink));
        Operation op = sendRequest(serviceClient,
                Operation.createPost(targetUri).setBody(closureRequest));
        Assert.assertNotNull(op);
    }

    protected Closure getClosure(String taskLink, ServiceClient serviceClient)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException, InterruptedException, ExecutionException, TimeoutException {
        URI targetUri = URI.create(getBaseUrl() + buildServiceUri(taskLink));
        Operation op = sendRequest(serviceClient, Operation.createGet(targetUri));

        return op.getBody(Closure.class);
    }

    protected String waitForBuildCompletion(String imagePrefix,
            ClosureDescription closureDescription) throws Exception {
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
        Assert.assertNotNull(imageRequest);

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

    protected void waitForTaskState(String taskLink, TaskState.TaskStage state, ServiceClient serviceClient)
            throws Exception {
        Closure fetchedClosure = getClosure(taskLink, serviceClient);
        long startTime = System.currentTimeMillis();
        while (state != fetchedClosure.state && !isTimeoutElapsed(startTime, 120)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            fetchedClosure = getClosure(taskLink, serviceClient);
        }
    }

    protected void verifyJsonArrayInts(Object[] javaArray, JsonArray jsArray) {
        assertEquals(javaArray.length, jsArray.size());
        for (int i = 0; i < javaArray.length; i++) {
            assertEquals(javaArray[i], jsArray.get(i).getAsInt());
        }
    }

    protected void verifyJsonArrayStrings(Object[] javaArray, JsonArray jsArray) {
        assertEquals(javaArray.length, jsArray.size());
        for (int i = 0; i < javaArray.length; i++) {
            assertEquals(javaArray[i], jsArray.get(i).getAsString());
        }
    }

    protected void verifyJsonArrayBooleans(Object[] javaArray, JsonArray jsArray) {
        assertEquals(javaArray.length, jsArray.size());
        for (int i = 0; i < javaArray.length; i++) {
            assertEquals(javaArray[i], jsArray.get(i).getAsBoolean());
        }
    }

    protected static String getTestWebServerUrl() {
        return getTestRequiredProp(TEST_WEB_SERVER_URL_PROP_NAME);
    }

}
