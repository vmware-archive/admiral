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
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.admiral.compute.container.ShellContainerExecutorService.ShellContainerExecutorResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ShellContainerExecutorServiceIT extends BaseProvisioningOnCoreOsIT {

    private ClusterDto cluster;

    @Before
    public void setUp() throws Throwable {
        setupEnvironmentForCluster();
        cluster = createCluster();
    }

    @Test
    public void testShellCommandExecution() throws Exception {
        logger.info("testShellCommandExecution");
        URI uri = URI.create(getBaseUrl() + buildServiceUri(
                ShellContainerExecutorService.SELF_LINK));

        String url = UriUtils
                .appendQueryParam(uri, ShellContainerExecutorService.HOST_LINK_URI_PARAM,
                        cluster.nodeLinks.get(0))
                .toString();

        Map<String, Object> command = new HashMap<>();
        command.put(ShellContainerExecutorService.COMMAND_KEY, new String[] { "uname", "-a" });

        SimpleHttpsClient.HttpResponse response = SimpleHttpsClient
                .execute(SimpleHttpsClient.HttpMethod.POST, url,
                        Utils.toJson(command));
        ShellContainerExecutorResult result = Utils.fromJson(response.responseBody,
                ShellContainerExecutorResult.class);
        assertEquals("Expecting exit code 0", Integer.valueOf(0), result.exitCode);
        assertTrue("Expecting starting with 'Linux'", result.output.startsWith("Linux"));
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return null;
    }
}
