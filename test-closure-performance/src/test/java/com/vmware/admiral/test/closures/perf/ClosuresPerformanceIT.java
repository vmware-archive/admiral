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

package com.vmware.admiral.test.closures.perf;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonPrimitive;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import com.vmware.admiral.closures.drivers.DriverConstants;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ResourceConstraints;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.test.closures.BaseClosurePerformanceTest;
import com.vmware.admiral.test.closures.SimpleHttpsClient;
import com.vmware.admiral.test.closures.TestPropertiesUtil;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

public class ClosuresPerformanceIT extends BaseClosurePerformanceTest {

    protected static String IMAGE_NAME_PREFIX = "vmware/photon-closure-runner_";
    private static String RUNTIME_NODEJS = "nodejs";
    private static String CLOSURES_NUMBER_PROP = "closures.number";
    private static String CLOSURES_TIMEOUT_PROP = "closures.timeout";

    private static final String IMAGE_NAME = IMAGE_NAME_PREFIX
            + DriverConstants.RUNTIME_NODEJS_4;
    public static final String BASE_IMAGE = IMAGE_NAME + "_base:2.0";
    public static final String RUNTIME_IMAGE = IMAGE_NAME + ":2.0";

    private Pattern createdPattern = Pattern.compile("CREATED");
    private Pattern finishedPattern = Pattern.compile("FINISHED");

    private static ServiceClient serviceClient;

    protected static ComputeService.ComputeState node1;
    protected static ComputeService.ComputeState node2;

    private static long totalClosureNumber = 0;
    private static int totalClosureTimeoutSeconds = 0;

    private static String dockerBuildImageLink_1;
    private static String dockerBuildBaseImageLink_1;
    private static String dockerBuildImageLink_2;
    private static String dockerBuildBaseImageLink_2;

    private ExecutorService executor;

    @Rule
    public Timeout globalTimeout = Timeout.seconds(TimeUnit.MINUTES.toSeconds(30));

    @BeforeClass
    public static void beforeClass() throws Exception {
        totalClosureNumber = Long.parseLong(TestPropertiesUtil.getTestRequiredProp
                (CLOSURES_NUMBER_PROP));
        totalClosureTimeoutSeconds = Integer.parseInt(TestPropertiesUtil.getTestRequiredProp
                (CLOSURES_TIMEOUT_PROP));

        serviceClient = ServiceClientFactory.createServiceClient(null);

        setupCoreOsHost(ContainerHostService.DockerAdapterType.API, true);

        node1 = dockerHostsInCluster.get(0);
        node2 = dockerHostsInCluster.get(1);

        dockerBuildImageLink_1 = getBaseUrl()
                + createImageBuildRequestUri(RUNTIME_IMAGE, node1.documentSelfLink);
        dockerBuildBaseImageLink_1 = getBaseUrl()
                + createImageBuildRequestUri(BASE_IMAGE, node1.documentSelfLink);
        dockerBuildImageLink_2 = getBaseUrl()
                + createImageBuildRequestUri(RUNTIME_IMAGE, node2.documentSelfLink);
        dockerBuildBaseImageLink_2 = getBaseUrl()
                + createImageBuildRequestUri(BASE_IMAGE, node2.documentSelfLink);
    }

    @Before
    public void init() {
        executor = Executors.newFixedThreadPool(10);
        logger.info("Executing against docker hosts: %s, %s", node1.address, node2.address);
    }

    @After
    public void after() throws Exception {
        // Clean-up
        cleanClosuresResources();
        SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.DELETE, dockerBuildImageLink_1);
        SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.DELETE, dockerBuildBaseImageLink_1);
        SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.DELETE, dockerBuildImageLink_2);
        SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.DELETE, dockerBuildBaseImageLink_2);
        serviceClient.stop();
        executor.shutdownNow();
    }

    @Test
    public void executeJSPerformanceTest() throws Throwable {
        // Create Closure Definition
        ClosureDescription closureDescState = new ClosureDescription();
        closureDescState.name = "test";

        int expectedInVar = 3;

        closureDescState.source = "module.exports = function test(ctx, a, b) {"
                + " console.log('Hello number: a:' + a + ' b: ' + b);"
                + " ctx.outputs.result=a + b;"
                + " }; ";
        closureDescState.runtime = RUNTIME_NODEJS;
        closureDescState.outputNames = new ArrayList<>(Collections.singletonList("result"));

        ResourceConstraints constraints = new ResourceConstraints();
        constraints.timeoutSeconds = 60;
        closureDescState.resources = constraints;

        String taskDefPayload = Utils.toJson(closureDescState);
        ClosureDescription closureDescription = createClosureDescription(taskDefPayload,
                serviceClient);

        // Execute the created Closure
        Closure closureRequest = new Closure();
        Map inputs = new HashMap<>();
        inputs.put("a", new JsonPrimitive(expectedInVar));
        inputs.put("b", new JsonPrimitive(expectedInVar));
        closureRequest.inputs = inputs;

        List<Future<?>> tasks = new LinkedList<>();
        long startRequestTime = System.currentTimeMillis();
        for (int i = 0; i < totalClosureNumber; i++) {
            final int j = i;
            tasks.add(executor.submit(() -> {
                try {
                    // Create Closure
                    Closure createdClosure = createClosure(closureDescription, serviceClient);
                    executeClosure(createdClosure, closureRequest, serviceClient);
                } catch (Exception e) {
                    logger.warning("Unable to create closure n: %d reason: %s", j, e
                            .getMessage());
                }
            }));
        }
        for (Future<?> future : tasks) {
            future.get();
        }
        long elapsedPushTime = (System.currentTimeMillis() - startRequestTime) / 1000;
        long startExecuteTime = System.currentTimeMillis();
        logger.info("Requested number closures: %s for %s seconds.",
                totalClosureNumber, elapsedPushTime);

        // Wait for closures executions
        waitForCompletion(startExecuteTime);

        Operation op = fetchClosuresState();
        if (!verifySuccessfullyExecuted(totalClosureNumber, op)) {
            dumpFailedClosures(op);
        }
    }

    // HELPER methods

    private void cleanClosuresResources() throws Exception {
        Operation op = fetchClosuresState();
        ServiceDocumentQueryResult result = op.getBody(ServiceDocumentQueryResult.class);
        for (String link : result.documentLinks) {
            Closure closure = Utils
                    .fromJson(result.documents.get(link), Closure.class);
            try {
                cleanResource(closure.documentSelfLink, serviceClient);
            } catch (Exception ex) {
                logger.warning("Unable to clean resource: " + closure.documentSelfLink, ex);
            }
            try {
                cleanResource(closure.descriptionLink, serviceClient);
            } catch (Exception ex) {
                logger.warning("Unable to clean resource: " + closure.descriptionLink, ex);
            }
        }
    }

    private void dumpFailedClosures(Operation op) {
        ServiceDocumentQueryResult result = op.getBody(ServiceDocumentQueryResult.class);
        for (String link : result.documentLinks) {
            Closure closure = Utils.fromJson(result.documents.get(link), Closure.class);
            if (closure.state != TaskState.TaskStage.FINISHED) {
                logger.info("Closure state: %s, documentSelfLink: %s, errorMsg: %s", closure.state,
                        closure.documentSelfLink, closure.errorMsg);
            }
        }

    }

    private boolean verifySuccessfullyExecuted(long numberOfClosures, Operation op)
            throws InterruptedException, ExecutionException, TimeoutException {
        Matcher matcher = finishedPattern.matcher((String) op.getBodyRaw());
        int finishedCount = 0;
        while (matcher.find()) {
            finishedCount++;
        }
        if (finishedCount != numberOfClosures) {
            logger.info("Unexpected number of finished closures: %s, expected: %s",
                    finishedCount, numberOfClosures);
            return false;
        }

        return true;
    }

    private void waitForCompletion(long startTime)
            throws InterruptedException, ExecutionException, TimeoutException {
        Operation op = fetchClosuresState();
        String rawResponse = (String) op.getBodyRaw();

        Matcher matcher = createdPattern.matcher(rawResponse);
        while (matcher.find() && !isTimeoutElapsed(startTime,
                totalClosureTimeoutSeconds)) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Refreshing closures state...");
            op = fetchClosuresState();
            rawResponse = (String) op.getBodyRaw();
            matcher = createdPattern.matcher(rawResponse);
        }
        long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
        double avgTimePerClosure = (double) elapsedTime / (double) totalClosureNumber;
        logger.info("Total time of execution: %s, Average time per container: %s", elapsedTime,
                avgTimePerClosure);
    }

    private Operation fetchClosuresState()
            throws InterruptedException, ExecutionException, TimeoutException {
        URI targetUri = URI.create(getBaseUrl() + buildServiceUri("/resources/closures?expand"));
        Operation op = sendRequest(serviceClient, Operation.createGet(targetUri));
        return op;
    }

}