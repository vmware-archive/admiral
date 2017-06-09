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

import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ShellContainerExecutorServiceIT extends BaseProvisioningOnCoreOsIT {

    @Before
    public void setUp() throws Exception {
        setupCoreOsHost(ContainerHostService.DockerAdapterType.API);
    }

    @Test
    @Ignore("https://jira-hzn.eng.vmware.com/browse/VBV-1364")
    public void testShellCommandExecution() throws Exception {
        logger.info("testShellCommandExecution");
        URI uri = URI.create(getBaseUrl() + buildServiceUri(
                ShellContainerExecutorService.SELF_LINK));

        String url = UriUtils
                .appendQueryParam(uri, ShellContainerExecutorService.HOST_LINK_URI_PARAM,
                        dockerHostCompute.documentSelfLink)
                .toString();

        Map<String, Object> command = new HashMap<>();
        command.put(ShellContainerExecutorService.COMMAND_KEY, new String[] { "uname", "-a" });

        SimpleHttpsClient.HttpResponse response = SimpleHttpsClient
                .execute(SimpleHttpsClient.HttpMethod.POST, url,
                        Utils.toJson(command));

        assertTrue(response.responseBody.startsWith("Linux"));
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return null;
    }
}
