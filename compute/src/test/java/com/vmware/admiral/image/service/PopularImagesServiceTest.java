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

package com.vmware.admiral.image.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.nio.file.Paths;
import java.util.Collection;

import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitImageServicesConfig;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.UriUtils;

public class PopularImagesServiceTest extends BaseTestCase {

    @Test
    public void testGetDefaultPopularImages() throws Throwable {

        // without the 'container.user.resources.path' configuration attribute set
        // the default popular images will be returned

        HostInitImageServicesConfig.startServices(host);
        waitForServiceAvailability(PopularImagesService.SELF_LINK);

        Collection<?> images = getDocument(Collection.class, PopularImagesService.SELF_LINK);
        assertNotNull(images);
        assertEquals(15, images.size());
    }

    @Test
    public void testGetExternalPopularImages() throws Throwable {

        // with the 'container.user.resources.path' configuration attribute set
        // the images of the popular-images.json file there will be returned

        HostInitCommonServiceConfig.startServices(host);

        waitForServiceAvailability(ConfigurationFactoryService.SELF_LINK);
        waitForServiceAvailability(UriUtils.buildUriPath(UriUtils.buildUriPath(
                ConfigurationFactoryService.SELF_LINK, FileUtil.USER_RESOURCES_PATH_VARIABLE)));

        ConfigurationState config = new ConfigurationState();
        config.documentSelfLink = UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK, FileUtil.USER_RESOURCES_PATH_VARIABLE);
        config.key = FileUtil.USER_RESOURCES_PATH_VARIABLE;
        config.value = Paths.get(PopularImagesServiceTest.class.getResource("/containers").toURI())
                .toString();

        ConfigurationState storedConfig = doPut(config);
        assertNotNull(storedConfig);

        HostInitImageServicesConfig.startServices(host);
        waitForServiceAvailability(PopularImagesService.SELF_LINK);

        Collection<?> images = getDocument(Collection.class, PopularImagesService.SELF_LINK);
        assertNotNull(images);
        assertEquals(5, images.size());
    }

}
