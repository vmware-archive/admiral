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

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class ContentCheckIT {

    private static Properties contentProperties = TestPropertiesUtil
            .loadProperties(new Properties(), Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("content.properties"));
    private static final String CREDENTIALS_ID = contentProperties.getProperty("CREDENTIALS_ID");
    private static final String CREDENTIALS_NAME = contentProperties
            .getProperty("CREDENTIALS_NAME");
    private static final String HOST_ID = contentProperties.getProperty("HOST_ID");

    private static final String AUTH_CREDENTIALS_NAME_KEY = "__authCredentialsName";

    @Test
    public void test() throws Exception {
        // Credentials
        HttpResponse credentialsResponse = SimpleHttpsClient.execute(HttpMethod.GET,
                BaseIntegrationSupportIT.getBaseUrl() + AuthCredentialsService.FACTORY_LINK + "/"
                        + CREDENTIALS_ID);
        Assert.assertEquals("Bad status code for credentials '" + CREDENTIALS_ID + "'",
                Operation.STATUS_CODE_OK, credentialsResponse.statusCode);
        AuthCredentialsServiceState credentials = Utils.fromJson(credentialsResponse.responseBody,
                AuthCredentialsServiceState.class);
        Assert.assertEquals("Wrong name for credentials '" + CREDENTIALS_ID + "'",
                CREDENTIALS_NAME, credentials.customProperties.get(AUTH_CREDENTIALS_NAME_KEY));
        Assert.assertNotNull(credentials.privateKey);
        Assert.assertNotNull(credentials.publicKey);

        // Host
        HttpResponse hostResponse = SimpleHttpsClient.execute(HttpMethod.GET,
                BaseIntegrationSupportIT.getBaseUrl() + ComputeService.FACTORY_LINK + "/"
                        + HOST_ID);
        Assert.assertEquals("Bad status code for host '" + HOST_ID + "'",
                Operation.STATUS_CODE_OK, hostResponse.statusCode);
        ComputeState host = Utils.fromJson(hostResponse.responseBody, ComputeState.class);
        Assert.assertEquals(HOST_ID, host.id);
        Assert.assertEquals(PowerState.ON, host.powerState);
    }
}