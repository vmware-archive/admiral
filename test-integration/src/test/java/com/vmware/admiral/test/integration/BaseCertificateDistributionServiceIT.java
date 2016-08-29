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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public abstract class BaseCertificateDistributionServiceIT extends BaseProvisioningOnCoreOsIT {

    private static final int RETRY_COUNT = 10;
    private static final int RETRY_TIMEOUT = 3000;

    protected static boolean waitUntilRegistryCertificateExists(String hostLink,
            String registryAddress) throws Exception {
        URI uri = URI.create(getBaseUrl() + buildServiceUri(
                ShellContainerExecutorService.SELF_LINK));

        String url = UriUtils
                .appendQueryParam(uri, ShellContainerExecutorService.HOST_LINK_URI_PARAM,
                        hostLink)
                .toString();

        Map<String, Object> command = new HashMap<>();
        command.put(ShellContainerExecutorService.COMMAND_KEY, new String[] {
                "ls", "/etc/docker/certs.d" });

        for (int i = 0; i < RETRY_COUNT; i++) {
            try {
                SimpleHttpsClient.HttpResponse response = SimpleHttpsClient.execute(
                        SimpleHttpsClient.HttpMethod.POST, url,
                        Utils.toJson(command));

                if (response.responseBody.contains(registryAddress)) {
                    return true;
                }
            } catch (Exception e) {
                //
            }

            Thread.sleep(RETRY_TIMEOUT);
        }

        return false;
    }

    protected static void removeCertificateDirectoryOnCoreOsHost(String hostLink) throws Exception {
        URI uri = URI.create(getBaseUrl() + buildServiceUri(
                ShellContainerExecutorService.SELF_LINK));

        String url = UriUtils
                .appendQueryParam(uri, ShellContainerExecutorService.HOST_LINK_URI_PARAM,
                        hostLink)
                .toString();

        Map<String, Object> command = new HashMap<>();
        command.put(ShellContainerExecutorService.COMMAND_KEY, new String[] {
                "rm", "-rf", "/etc/docker/certs.d" });

        SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.POST, url,
                Utils.toJson(command));
    }
}
