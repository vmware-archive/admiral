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

package com.vmware.admiral.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import static com.vmware.xenon.common.CommandLineArgumentParser.ARGUMENT_ASSIGNMENT;
import static com.vmware.xenon.common.CommandLineArgumentParser.ARGUMENT_PREFIX;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.LoaderFactoryService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class ManagementHostTest {

    private static final TemporaryFolder SANDBOX = new TemporaryFolder();

    @Before
    public void setup() throws Exception {
        SANDBOX.create();
    }

    @After
    public void tearDown() {
        SANDBOX.delete();
    }

    private static class TestManagementHost extends ManagementHost implements AutoCloseable {

        public TestManagementHost(boolean startMockHostAdapterInstance, String... extraArgs) throws Throwable {
            List<String> args = new ArrayList<>(Arrays.asList(
                    // start mock host adapter instance
                    ARGUMENT_PREFIX + HostInitDockerAdapterServiceConfig.FIELD_NAME_START_MOCK_HOST_ADAPTER_INSTANCE
                    + ARGUMENT_ASSIGNMENT + startMockHostAdapterInstance,
                    // generate a random sandbox
                    ARGUMENT_PREFIX + "sandbox" + ARGUMENT_ASSIGNMENT + SANDBOX.getRoot().toPath(),
                    // ask runtime to pick a random port
                    ARGUMENT_PREFIX + "port" + ARGUMENT_ASSIGNMENT + "8282"));
            for (String extraArg : extraArgs) {
                args.add(extraArg);
            }
            initialize(args.toArray(new String[args.size()]));
        }

        @Override
        public void close() throws IOException {
            log(Level.WARNING, "Host stopping ...");
            stop();
            log(Level.WARNING, "Host is stopped");
        }

        private final TestContext testContext = TestContext.create(1, TimeUnit.MINUTES.toMicros(1));

        public TestContext getTestContext() {
            return testContext;
        }

    } // class TestManagementHost

    @Test
    public void testManagementHostInitializationNoErrors() throws Throwable {
        try (TestManagementHost host = new TestManagementHost(false)) {
            // we're just verifying that no exceptions are thrown
        }
    }

    @Test
    public void testManagementHostInitialization() throws Throwable {
        try (TestManagementHost host = new TestManagementHost(true)) {
            host.start();
            host.startManagementServices();
            AtomicInteger statusCode = new AtomicInteger(0);
            Operation op =
                    Operation.createGet(UriUtils.buildUri(host, MockDockerAdapterService.SELF_LINK))
                    .setReferer(host.getUri())
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            statusCode.set(o.getStatusCode());
                            host.getTestContext().completeIteration();
                        } else {
                            host.getTestContext().failIteration(e);
                        }
                    });
            host.sendRequest(op);
            host.getTestContext().await();
            assertEquals("The MockHostInteractionService didn't start.", 204, statusCode.get());
        }
    }

    @Test
    public void testManagementHostInitializationEnablesDynamicLoading() throws Throwable {
        try (TestManagementHost host = new TestManagementHost(false)) {
            host.start();
            host.enableDynamicServiceLoading();
            AtomicInteger statusCode = new AtomicInteger(0);
            Operation op =
                    Operation.createGet(UriUtils.buildUri(host, LoaderFactoryService.SELF_LINK))
                    .setReferer(host.getUri())
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            statusCode.set(o.getStatusCode());
                            host.getTestContext().completeIteration();
                        } else {
                            host.getTestContext().failIteration(e);
                        }
                    });
            host.sendRequest(op);
            host.getTestContext().await();
            assertEquals("The loader service didn't start", 200, statusCode.get());
        }
    }

    @Test
    public void testManagementHostInitializationNoErrorsWithNodeGroup() throws Throwable {
        try (TestManagementHost host = new TestManagementHost(false,
                ARGUMENT_PREFIX + "publicUri" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8292",
                ARGUMENT_PREFIX + "nodeGroupPublicUri" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8292",
                ARGUMENT_PREFIX + "peerList" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8292")) {
            // we're just verifying that no exceptions are thrown
        }
    }

    @Test
    public void testManagementHostInitializationWithNodeGroup() throws Throwable {
        try (TestManagementHost host = new TestManagementHost(true,
                ARGUMENT_PREFIX + "publicUri" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8292",
                ARGUMENT_PREFIX + "nodeGroupPublicUri" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8292",
                ARGUMENT_PREFIX + "peerList" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8292")) {
            host.start();
            host.startManagementServices();
            AtomicInteger statusCode = new AtomicInteger(0);
            Operation op =
                    Operation.createGet(UriUtils.buildUri(host, MockDockerAdapterService.SELF_LINK))
                    .setReferer(host.getUri())
                    .setCompletion((o, e) -> {
                        if (e == null) {
                            statusCode.set(o.getStatusCode());
                            host.getTestContext().completeIteration();
                        } else {
                            host.getTestContext().failIteration(e);
                        }
                    });
            host.sendRequest(op);
            host.getTestContext().await();
            assertEquals("The MockHostInteractionService didn't start.", 204, statusCode.get());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testManagementHostInitializationErrorNoNodeGroupScheme() throws Throwable {
        try (TestManagementHost host = new TestManagementHost(true,
                ARGUMENT_PREFIX + "publicUri" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8282",
                ARGUMENT_PREFIX + "nodeGroupPublicUri" + ARGUMENT_ASSIGNMENT + "//127.0.0.1:8282",
                ARGUMENT_PREFIX + "peerList" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8282")) {
            // we're just verifying that exception is thrown
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testManagementHostInitializationErrorNoNodeGroupPort() throws Throwable {
        try (TestManagementHost host = new TestManagementHost(true,
                ARGUMENT_PREFIX + "publicUri" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8282",
                ARGUMENT_PREFIX + "nodeGroupPublicUri" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1",
                ARGUMENT_PREFIX + "peerList" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8282")) {
            // we're just verifying that exception is thrown
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testManagementHostInitializationErrorSamePort() throws Throwable {
        try (TestManagementHost host = new TestManagementHost(true,
                ARGUMENT_PREFIX + "publicUri" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8282",
                ARGUMENT_PREFIX + "nodeGroupPublicUri" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8282",
                ARGUMENT_PREFIX + "peerList" + ARGUMENT_ASSIGNMENT + "http://127.0.0.1:8282")) {
            // we're just verifying that exception is thrown
        }
    }

    @Test
    public void testCredentialsDelete() throws Throwable {
        try (TestManagementHost host = new TestManagementHost(true)) {
            host.start();
            host.startFabricServices();
            host.startManagementServices();

            host.registerForServiceAvailability(host.getTestContext().getCompletion(),
                    AuthCredentialsService.FACTORY_LINK,
                    ComputeDescriptionService.FACTORY_LINK,
                    ComputeService.FACTORY_LINK);
            host.getTestContext().await();

            ComputeDescription computeDescription =
                    doPost(host, new ComputeDescription(), ComputeDescriptionService.FACTORY_LINK);
            AuthCredentialsServiceState credentials =
                    doPost(host, new AuthCredentialsServiceState(), AuthCredentialsService.FACTORY_LINK);

            ComputeState compute = new ComputeState();
            compute.customProperties = new HashMap<>();
            compute.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                    credentials.documentSelfLink);
            compute.descriptionLink = computeDescription.documentSelfLink;
            compute = doPost(host, compute, ComputeService.FACTORY_LINK);

            try {
                doDelete(host, UriUtils.buildUri(host, credentials.documentSelfLink));
                fail("expect validation error during deletion");
            } catch (LocalizableValidationException e) {
                assertEquals("Auth Credentials are in use", e.getMessage());
            }
            doDelete(host, UriUtils.buildUri(host, compute.documentSelfLink));
            doDelete(host, UriUtils.buildUri(host, computeDescription.documentSelfLink));
            doDelete(host, UriUtils.buildUri(host, credentials.documentSelfLink));
        }
    }

    @Test
    public void testResourcePoolDelete() throws Throwable {
        try (TestManagementHost host = new TestManagementHost(true)) {
            host.start();
            host.startFabricServices();
            host.startManagementServices();

            host.registerForServiceAvailability(host.getTestContext().getCompletion(),
                    ComputeDescriptionService.FACTORY_LINK,
                    ComputeService.FACTORY_LINK,
                    ResourcePoolService.FACTORY_LINK);
            host.getTestContext().await();

            ComputeDescription computeDescription =
                    doPost(host, new ComputeDescription(), ComputeDescriptionService.FACTORY_LINK);

            ResourcePoolState resourcePool = new ResourcePoolState();
            resourcePool.name = "test-resource-pool";
            resourcePool = doPost(host, resourcePool, ResourcePoolService.FACTORY_LINK);

            ComputeState compute = new ComputeState();
            compute.descriptionLink = computeDescription.documentSelfLink;
            compute.resourcePoolLink = resourcePool.documentSelfLink;
            compute = doPost(host, compute, ComputeService.FACTORY_LINK);

            try {
                doDelete(host, UriUtils.buildUri(host, resourcePool.documentSelfLink));
                fail("expect validation error during deletion");
            } catch (LocalizableValidationException e) {
                assertEquals("Placement zone is in use", e.getMessage());
            }

            resourcePool.name = "test-resource-pool2";
            doPatch(host, resourcePool, resourcePool.documentSelfLink);

            doDelete(host, UriUtils.buildUri(host, compute.documentSelfLink));
            doDelete(host, UriUtils.buildUri(host, computeDescription.documentSelfLink));
            doDelete(host, UriUtils.buildUri(host, resourcePool.documentSelfLink));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T doPatch(TestManagementHost host, T inState, String serviceUrlPath) throws Throwable {
        TestContext ctx = BaseTestCase.testCreate(1);
        AtomicReference<T> result = new AtomicReference<>();
        Operation op =
                Operation.createPatch(UriUtils.buildUri(host, serviceUrlPath))
                .setBody(inState)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e == null) {
                        result.set((T) o.getBody(inState.getClass()));
                        ctx.completeIteration();
                    } else {
                        ctx.failIteration(e);
                    }
                });
        host.sendRequest(op);
        ctx.await();
        return result.get();
    }

    @SuppressWarnings("unchecked")
    private <T> T doPost(TestManagementHost host, T inState, String fabricServiceUrlPath) throws Throwable {
        TestContext ctx = BaseTestCase.testCreate(1);
        AtomicReference<T> result = new AtomicReference<>();
        Operation op =
                Operation.createPost(UriUtils.buildUri(host, fabricServiceUrlPath))
                .setBody(inState)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e == null) {
                        result.set((T) o.getBody(inState.getClass()));
                        ctx.completeIteration();
                    } else {
                        ctx.failIteration(e);
                    }
                });
        host.sendRequest(op);
        ctx.await();
        return result.get();
    }

    private <T> void doDelete(TestManagementHost host, URI uri) throws Throwable {
        TestContext ctx = BaseTestCase.testCreate(1);
        Operation op =
                Operation.createDelete(uri)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e == null) {
                        ctx.completeIteration();
                    } else {
                        ctx.failIteration(e);
                    }
                });
        host.sendRequest(op);
        ctx.await();
    }

}
