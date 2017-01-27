/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.docker.service;

/*
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class SystemImageRetrievalManagerTest extends BaseTestCase {

    private static final String TEST_IMAGE = "testimage.tar";
    private static final String TEST_IMAGE_RES = "testimageRes.tar";

    private SystemImageRetrievalManager retrievalManager;

    @Before
    public void setup() {
        retrievalManager = new SystemImageRetrievalManager(host);
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    @Test
    public void testGetFromUserResources() throws Throwable {
        Path testXenonImagesPath = Files.createTempDirectory("test-xenon-images");

        HostInitCommonServiceConfig.startServices(host);
        waitForServiceAvailability(ConfigurationFactoryService.SELF_LINK);
        waitForServiceAvailability(UriUtils.buildUriPath(UriUtils.buildUriPath(
                ConfigurationFactoryService.SELF_LINK, FileUtil.USER_RESOURCES_PATH_VARIABLE)));

        // Set expected configuration
        ConfigurationState config = new ConfigurationState();
        config.documentSelfLink = UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                FileUtil.USER_RESOURCES_PATH_VARIABLE);
        config.key = FileUtil.USER_RESOURCES_PATH_VARIABLE;
        config.value = testXenonImagesPath.toAbsolutePath().toString();

        doPost(config, ConfigurationFactoryService.SELF_LINK);

        File imageDir = new File(UriUtils.buildUriPath(testXenonImagesPath.toString(),
                SystemImageRetrievalManager.SYSTEM_IMAGES_PATH));
        imageDir.mkdir();

        byte[] content = IOUtils.toByteArray(Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(TEST_IMAGE));
        // Basically, rename it so it must be loaded from user resources for sure
        File tmpFile = new File(
                UriUtils.buildUriPath(imageDir.getAbsolutePath(), TEST_IMAGE_RES));
        tmpFile.createNewFile();
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            os.write(content);
        }

        AdapterRequest req = new AdapterRequest();
        req.resourceReference = host.getUri();

        AtomicReference<byte[]> retrievedImageRef = new AtomicReference<>();

        TestContext ctx = testCreate(1);
        retrievalManager.retrieveAgentImage(TEST_IMAGE_RES, req, (image) -> {
            retrievedImageRef.set(image);
            ctx.completeIteration();
        });

        ctx.await();

        byte[] image = retrievedImageRef.get();
        Assert.assertEquals("Unexpected content", new String(content), new String(image));
    }

    @Test
    public void testGetFromUserResourcesConcurrent() throws Throwable {
        Path testXenonImagesPath = Files.createTempDirectory("test-xenon-images");

        // Set expected configuration
        ConfigurationState config = new ConfigurationState();
        config.documentSelfLink = UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                FileUtil.USER_RESOURCES_PATH_VARIABLE);
        config.key = FileUtil.USER_RESOURCES_PATH_VARIABLE;
        config.value = testXenonImagesPath.toAbsolutePath().toString();

        MockConfigurationService mockConfigurationService = new MockConfigurationService(config);
        host.startService(Operation.createPost(UriUtils.buildUri(host, UriUtils
                .buildUriPath(ConfigurationFactoryService.SELF_LINK,
                        FileUtil.USER_RESOURCES_PATH_VARIABLE))),
                mockConfigurationService);

        File imageDir = new File(UriUtils.buildUriPath(testXenonImagesPath.toString(),
                SystemImageRetrievalManager.SYSTEM_IMAGES_PATH));
        imageDir.mkdir();

        byte[] content = IOUtils.toByteArray(Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(TEST_IMAGE));
        // Basically, rename it so it must be loaded from user resources for sure
        File tmpFile = new File(
                UriUtils.buildUriPath(imageDir.getAbsolutePath(), TEST_IMAGE_RES));
        tmpFile.createNewFile();
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            os.write(content);
        }

        AdapterRequest req = new AdapterRequest();
        req.resourceReference = host.getUri();

        List<byte[]> retrievedImages = new ArrayList<>();

        int numberOfRequests = 4;

        TestContext ctx = testCreate(numberOfRequests);

        final ExecutorService threadPool = Executors.newFixedThreadPool(numberOfRequests);

        List<Callable<Void>> callables = new ArrayList<>();
        for (int i = 0; i < numberOfRequests; i++) {
            callables.add(() -> {
                host.log("Calling retrieveAgentImage");
                retrievalManager.retrieveAgentImage(TEST_IMAGE_RES, req, (image) -> {
                    retrievedImages.add(image);
                    ctx.completeIteration();
                });
                return null;
            });
        }

        host.log("Invoke all callables to retrieveAgentImage");
        threadPool.invokeAll(callables);

        ctx.await();

        // Assert that all callbacks were called
        assertEquals(numberOfRequests, retrievedImages.size());
        for (int i = 0; i < numberOfRequests; i++) {
            byte[] image = retrievedImages.get(i);
            Assert.assertEquals("Unexpected content", new String(content), new String(image));
        }

        // Assert that service was called only once for all concurrent requests
        assertEquals(1, mockConfigurationService.getNumberOfRequests());
    }

    @Test
    public void testGetFromClassPath() throws Throwable {
        Path testXenonImagesPath = Files.createTempDirectory("test-xenon-images");

        HostInitCommonServiceConfig.startServices(host);
        waitForServiceAvailability(ConfigurationFactoryService.SELF_LINK);
        waitForServiceAvailability(UriUtils.buildUriPath(UriUtils.buildUriPath(
                ConfigurationFactoryService.SELF_LINK, FileUtil.USER_RESOURCES_PATH_VARIABLE)));

        // Set expected configuration
        ConfigurationState config = new ConfigurationState();
        config.documentSelfLink = UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                FileUtil.USER_RESOURCES_PATH_VARIABLE);
        config.key = FileUtil.USER_RESOURCES_PATH_VARIABLE;
        config.value = testXenonImagesPath.toAbsolutePath().toString();

        doPost(config, ConfigurationFactoryService.SELF_LINK);

        File imageDir = new File(UriUtils.buildUriPath(testXenonImagesPath.toString(),
                SystemImageRetrievalManager.SYSTEM_IMAGES_PATH));
        imageDir.mkdir();

        byte[] content = IOUtils.toByteArray(Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(TEST_IMAGE));

        AdapterRequest req = new AdapterRequest();
        req.resourceReference = host.getUri();

        AtomicReference<byte[]> retrievedImageRef = new AtomicReference<>();

        TestContext ctx = testCreate(1);
        retrievalManager.retrieveAgentImage(TEST_IMAGE, req, (image) -> {
            retrievedImageRef.set(image);
            ctx.completeIteration();
        });

        ctx.await();

        byte[] image = retrievedImageRef.get();
        Assert.assertEquals("Unexpected content", new String(content), new String(image));
    }

    private class MockConfigurationService extends StatelessService {

        private int numberOfRequests = 0;
        private ConfigurationState state;

        public MockConfigurationService(ConfigurationState state) {
            this.state = state;
        }

        @Override
        public void handleGet(Operation get) {
            host.log("MockConfigurationService handles get. Current number of requests %s",
                    numberOfRequests);
            numberOfRequests++;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
            host.log("MockConfigurationService completes get. Current number of requests %s",
                    numberOfRequests);
            get.setBody(state).complete();
        }

        public int getNumberOfRequests() {
            return numberOfRequests;
        }
    }
}
