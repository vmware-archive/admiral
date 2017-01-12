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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class ConfigurationServiceTest extends ComputeBaseTest {
    private ConfigurationState configurationState;
    private final String key1 = "key1";
    private final String value1 = "value1";

    @Before
    public void setUp() throws Throwable {
        configurationState = new ConfigurationState();
        configurationState.key = key1;
        configurationState.value = value1;
        waitForServiceAvailability(ConfigurationFactoryService.SELF_LINK);
    }

    @Test
    public void testPOSTandGET() throws Throwable {
        verifyService(ConfigurationFactoryService.class, ConfigurationState.class,
                (prefix, index) -> {
                    return configurationState;
                },
                (prefix, serviceDocument) -> {
                    ConfigurationState state = (ConfigurationState) serviceDocument;
                    assertEquals(key1, state.key);
                    assertEquals(value1, state.value);
                });
    }

    @Test
    public void testValidateOnStart() throws Throwable {
        configurationState.key = null;
        validateLocalizableException(() -> {
            postForValidation(configurationState);
        }, "key must not be null.");

        configurationState.key = key1;
        configurationState.value = null;
        validateLocalizableException(() -> {
            postForValidation(configurationState);
        }, "value is not valid.");
    }

    @Test
    public void testPUT() throws Throwable {
        configurationState = doPost(configurationState, ConfigurationFactoryService.SELF_LINK);

        String value2 = "value2";
        configurationState.value = value2;
        doOperation(configurationState,
                UriUtils.buildUri(host, configurationState.documentSelfLink), false, Action.PUT);

        ConfigurationState updatedConfigState = getDocument(ConfigurationState.class,
                configurationState.documentSelfLink);

        assertEquals(value2, updatedConfigState.value);
    }

    @Test
    public void testIdempotentPOST() throws Throwable {
        String selfLinkId = UUID.randomUUID().toString();

        ConfigurationState configState1 = new ConfigurationState();
        configState1.documentSelfLink = selfLinkId;
        configState1.key = key1;
        configState1.value = value1;
        configState1 = doPost(configState1, ConfigurationFactoryService.SELF_LINK);

        String value2 = "value2";
        ConfigurationState configState2 = new ConfigurationState();
        configState2.documentSelfLink = selfLinkId;
        configState2.key = key1;
        configState2.value = value2;
        configState2 = doPost(configState2, ConfigurationFactoryService.SELF_LINK);

        configurationState = getDocument(ConfigurationState.class,
                UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK, selfLinkId));

        assertEquals(value2, configurationState.value);
    }

    private void postForValidation(ConfigurationState state) throws Throwable {
        URI uri = UriUtils.buildUri(host, SslTrustCertificateService.FACTORY_LINK);
        doOperation(state, uri, true, Action.POST);
    }

}
