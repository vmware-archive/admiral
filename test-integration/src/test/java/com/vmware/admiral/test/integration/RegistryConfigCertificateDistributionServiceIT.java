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

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ContainerHostService;

public class RegistryConfigCertificateDistributionServiceIT extends
        BaseCertificateDistributionServiceIT {

    @Before
    public void setUp() throws Exception {
        setupCoreOsHost(ContainerHostService.DockerAdapterType.API);
        removeCertificateDirectoryOnCoreOsHost(dockerHostCompute.documentSelfLink);
    }

    @Test
    public void testUploadRegistryCertificateOnDockerHostConfig() throws Exception {
        configureRegistries(getTestRequiredProp("docker.registry.host.address"), null);

        String registryAddress = UriUtilsExtended.extractHostAndPort(
                getTestRequiredProp("docker.registry.host.address"));

        boolean exists = waitUntilRegistryCertificateExists(dockerHostCompute.documentSelfLink,
                registryAddress);

        assertTrue("Cert does not exist.", exists);
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return null;
    }
}
