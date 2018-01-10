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

package com.vmware.admiral.test.integration;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getSystemOrTestProp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.ServiceHost;

public class RegistryServiceIT {

    private static final int TEST_TIMEOUT_SECONDS = 30;
    private static final int TEST_TIMEOUT_SHORT_SECONDS = 10;
    private static final String TEST_REGISTRY_ADDRESS = getSystemOrTestProp("test.registry");
    private static final String TEST_REGISTRY_ADDRESS_INVALID = "https://127.0.0.1:7890";
    private static final String TEST_REGISTRY_ADDRESS_HTTP = "http://127.0.0.1:7809";
    private RegistryState registryState;
    private ServiceHost host = new ServiceHost() {
    };

    @Before
    public void setUp() {
        registryState = new RegistryState();
        ServerX509TrustManager.init(host);
    }

    @Test
    public void fetchRegistryCertificateValid() throws Exception {
        registryState.address = TEST_REGISTRY_ADDRESS;
        final CountDownLatch latch = new CountDownLatch(1);
        final StringBuilder certificate = new StringBuilder();
        RegistryService.fetchRegistryCertificate(registryState, (cert) -> {
            certificate.append(cert);
            latch.countDown();
        }, host);
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Assert.assertNotEquals(0, certificate.length());
    }

    @Test
    public void fetchRegistryCertificateInvalid() throws Exception {
        testInvalid(TEST_REGISTRY_ADDRESS_HTTP);

        testInvalid(TEST_REGISTRY_ADDRESS_INVALID);
    }

    private void testInvalid(String address) throws InterruptedException {
        registryState.address = address;
        final CountDownLatch latch = new CountDownLatch(1);

        RegistryService.fetchRegistryCertificate(registryState, (cert) -> {
            Assert.fail("should not return certificate");
            latch.countDown();
        }, host);
        latch.await(TEST_TIMEOUT_SHORT_SECONDS, TimeUnit.SECONDS);
    }

}
