/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common.harbor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.concurrent.CancellationException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.common.test.TestRequestSender.FailureResponse;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class HarborInitRegistryServiceTest {

    private VerificationHost host;
    private TestRequestSender sender;
    private HarborInitRegistryService harborInitRegistryService;

    @Before
    public void setUp() throws Throwable {
        host = VerificationHost.create();
        ServiceHost.Arguments args = VerificationHost.buildDefaultServiceHostArguments(0);
        VerificationHost.initialize(host, args);
        host.start();

        host.startService(new RegistryFactoryService());
        host.waitForServiceAvailable(RegistryFactoryService.SELF_LINK);

        host.startService(new ConfigurationFactoryService());
        host.waitForServiceAvailable(ConfigurationFactoryService.SELF_LINK);

        sender = host.getTestRequestSender();
        harborInitRegistryService = new HarborInitRegistryService();
        harborInitRegistryService.setHost(host);
    }

    @After
    public void after() throws Throwable {
        try {
            host.tearDownInProcessPeers();
            host.tearDown();
        } catch (CancellationException ignore) {
        }
        host = null;
    }

    @Test
    public void testNoHarborProperty() throws Exception {
        TestContext t = new TestContext(1, Duration.ofSeconds(15));
        harborInitRegistryService.handleStart(Operation
                .createGet(null)
                .setCompletion(t.getCompletion()));
        t.await();
        RegistryState harborRegistry = getHarborRegistry(false);
        assertNull(harborRegistry);
    }

    @Test
    public void testWithHarborProperty() throws Exception {
        createHarborConfigurationState("harbor.address");
        TestContext t = new TestContext(1, Duration.ofSeconds(15));
        harborInitRegistryService.handleStart(Operation
                .createGet(null)
                .setCompletion(t.getCompletion()));
        t.await();
        RegistryState harborRegistry = getHarborRegistry(true);
        assertNotNull(harborRegistry);
        assertEquals("harbor.address", harborRegistry.address);
        assertNotNull(harborRegistry.authCredentialsLink);
        AuthCredentialsServiceState credentialsState = getCredentialsState(
                harborRegistry.authCredentialsLink);
        assertNotNull(credentialsState);
        assertNotNull(credentialsState.userEmail);
        assertNotNull(credentialsState.privateKey);
        assertEquals(AuthCredentialsType.Password.name(), credentialsState.type);
        assertTrue(credentialsState.userEmail.startsWith(Harbor.DEFAULT_REGISTRY_USER_PREFIX));
        assertTrue(credentialsState.privateKey.length() > 20);
    }

    @Test
    public void testWithNewHarborProperty() throws Exception {
        testWithHarborProperty();
        RegistryState registryOld = getHarborRegistry(true);

        updateHarborConfigurationState("harbor.address.new");
        TestContext t = new TestContext(1, Duration.ofSeconds(15));
        harborInitRegistryService.handleStart(Operation
                .createGet(null)
                .setCompletion(t.getCompletion()));
        t.await();

        RegistryState registryNew = getHarborRegistry(true);
        assertEquals("harbor.address.new", registryNew.address);
        assertNotNull(registryNew.authCredentialsLink);
        assertNotEquals(registryOld.authCredentialsLink, registryNew.authCredentialsLink);
        AuthCredentialsServiceState credentialsNew = getCredentialsState(
                registryNew.authCredentialsLink);
        assertNotNull(credentialsNew.userEmail);
        assertNotNull(credentialsNew.privateKey);
        assertEquals(AuthCredentialsType.Password.name(), credentialsNew.type);
        assertTrue(credentialsNew.userEmail.startsWith(Harbor.DEFAULT_REGISTRY_USER_PREFIX));
        assertTrue(credentialsNew.privateKey.length() > 20);
    }

    private void createHarborConfigurationState(String value) {
        ConfigurationState state = new ConfigurationState();
        state.documentSelfLink = UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                Harbor.CONFIGURATION_URL_PROPERTY_NAME);
        state.key = Harbor.CONFIGURATION_URL_PROPERTY_NAME;
        state.value = value;

        sender.sendAndWait(Operation
                        .createPost(UriUtils.buildUri(host, ConfigurationFactoryService.SELF_LINK))
                        .setBodyNoCloning(state),
                ConfigurationState.class);
    }

    private void updateHarborConfigurationState(String value) {
        ConfigurationState state = new ConfigurationState();
        state.documentSelfLink = UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK,
                Harbor.CONFIGURATION_URL_PROPERTY_NAME);
        state.key = Harbor.CONFIGURATION_URL_PROPERTY_NAME;
        state.value = value;

        sender.sendAndWait(Operation
                        .createPut(UriUtils.buildUri(host, state.documentSelfLink))
                        .setBodyNoCloning(state),
                ConfigurationState.class);
    }

    private RegistryState getHarborRegistry(boolean exists) {
        if (exists) {
            return sender.sendGetAndWait(UriUtils.buildUri(host, Harbor.DEFAULT_REGISTRY_LINK),
                    RegistryState.class);
        } else {
            FailureResponse failureResponse = sender.sendAndWaitFailure(
                    Operation.createGet(host, Harbor.DEFAULT_REGISTRY_LINK));
            assertEquals(Operation.STATUS_CODE_NOT_FOUND, failureResponse.op.getStatusCode());
            return null;
        }
    }

    private AuthCredentialsServiceState getCredentialsState(String link) {
        return sender.sendGetAndWait(UriUtils.buildUri(host, link),
                AuthCredentialsServiceState.class);
    }

}