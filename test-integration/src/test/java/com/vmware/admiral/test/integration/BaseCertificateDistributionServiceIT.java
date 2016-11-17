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

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;

import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public abstract class BaseCertificateDistributionServiceIT extends BaseProvisioningOnCoreOsIT {

    private static final int RETRY_COUNT = 10;
    private static final int RETRY_TIMEOUT = 3000;

    protected String registryHostAndPort;

    @Before
    public void setUpRegistryAddress() {
        registryAddress = getTestRequiredProp("docker.v2.registry.host.address");
        registryHostAndPort = UriUtilsExtended.extractHostAndPort(registryAddress);
    }

    protected boolean waitUntilRegistryCertificateExists(String hostLink,
            String registryAddress) throws Exception {
        logger.info("Waiting until registry certificate with CN [%s] exists on host [%s].",
                registryAddress, hostLink);
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
                logger.info("[%s] Remote command [ls /etc/docker/certs.d] returned %s",
                        i, response.responseBody);

                if (response.responseBody.contains(registryAddress)) {
                    logger.info("[%s] found in remote command response body", registryAddress);
                    return true;
                }
            } catch (Exception e) {
                //
            }

            Thread.sleep(RETRY_TIMEOUT);
        }

        logger.info("Failed to find registry cert with CN [%s] on host [%s] after max retries",
                registryAddress, hostLink);
        return false;
    }

    protected void removeCertificateDirectoryOnCoreOsHost(String hostLink,
            String registryAddress) throws Exception {

        logger.info("Removing registry certificate with CN [%s] from host [%s]", registryAddress,
                hostLink);
        URI uri = URI.create(getBaseUrl() + buildServiceUri(
                ShellContainerExecutorService.SELF_LINK));

        String url = UriUtils
                .appendQueryParam(uri, ShellContainerExecutorService.HOST_LINK_URI_PARAM,
                        hostLink)
                .toString();

        Map<String, Object> command = new HashMap<>();
        command.put(ShellContainerExecutorService.COMMAND_KEY, new String[] {
                "rm", "-rf", "/etc/docker/certs.d/" +  registryAddress });

        SimpleHttpsClient.execute(SimpleHttpsClient.HttpMethod.POST, url,
                Utils.toJson(command));
    }
}
