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

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getSystemOrTestProp;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.ServiceHost;

public class RegistryServiceIT {

    private static final String TEST_REGISTRY_ADDRESS = getSystemOrTestProp("test.registry");
    private static final String TEST_REGISTRY_ADDRESS_INVALID = "https://127.0.0.1:7890";
    private static final String TEST_REGISTRY_ADDRESS_HTTP = "http://127.0.0.1:7809";
    private RegistryState registryState;

    @Before
    public void setUp() {
        registryState = new RegistryState();
        ServerX509TrustManager.init(new ServiceHost() { });
    }

    @Test
    public void fetchRegistryCertificateValid() throws Exception {

        registryState.address = TEST_REGISTRY_ADDRESS;
        final StringBuilder certificate = new StringBuilder();
        RegistryService.fetchRegistryCertificate(registryState, certificate::append);
        Assert.assertNotEquals(0, certificate.length());
    }

    @Test
    public void fetchRegistryCertificateInvalid() throws Exception {
        testInvalid(TEST_REGISTRY_ADDRESS_HTTP);

        testInvalid(TEST_REGISTRY_ADDRESS_INVALID);
    }

    private void testInvalid(String address) {
        registryState.address = address;

        RegistryService.fetchRegistryCertificate(registryState,
                (cert) -> Assert.fail("should not return certificate"));
    }

}
