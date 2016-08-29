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

package com.vmware.admiral.image.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitImageServicesConfig;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.UriUtils;

public class SystemImagesServiceTest extends BaseTestCase {

    private static final String DUMMY_IMAGE_TGZ = "dummy_image.tgz";
    private static final String DUMMY_IMAGE_RES_TGZ = "dummy_image_res.tgz";

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
                ManagementUriParts.SYSTEM_IMAGES));
        imageDir.mkdir();

        byte[] content = IOUtils.toByteArray(Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(DUMMY_IMAGE_TGZ));
        // Basically, rename it so it must be loaded from user resources for sure
        File tmpFile = new File(
                UriUtils.buildUriPath(imageDir.getAbsolutePath(), DUMMY_IMAGE_RES_TGZ));
        tmpFile.createNewFile();
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            os.write(content);
        }

        HostInitImageServicesConfig.startServices(host);
        waitForServiceAvailability(SystemImagesService.SELF_LINK);

        byte[] image = getDocument(byte[].class,
                SystemImagesService.buildSystemImageUriPath(DUMMY_IMAGE_RES_TGZ).toString());
        Assert.assertEquals("Unexpected content", new String(content), new String(image));
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
                ManagementUriParts.SYSTEM_IMAGES));
        imageDir.mkdir();

        byte[] content = IOUtils.toByteArray(Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(DUMMY_IMAGE_TGZ));

        HostInitImageServicesConfig.startServices(host);
        waitForServiceAvailability(SystemImagesService.SELF_LINK);

        byte[] image = getDocument(byte[].class,
                SystemImagesService.buildSystemImageUriPath(DUMMY_IMAGE_TGZ).toString());
        Assert.assertEquals("Unexpected content", new String(content), new String(image));
    }
}
