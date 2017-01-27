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

package com.vmware.admiral.adapter.registry.mock;

import java.net.URI;

import org.junit.AfterClass;
import org.junit.Before;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

/**
 * Add mock registry host and services
 */
public class BaseMockRegistryTestCase extends BaseTestCase {
    protected static VerificationHost mockRegistryHost;

    protected static URI dockerHubRegistryUri;
    protected static URI defaultRegistryUri;
    protected static URI v2RegistryUri;

    @Before
    public void setUpMockRegistryHost() throws Throwable {
        synchronized (BaseMockRegistryTestCase.class) {
            if (defaultRegistryUri == null || v2RegistryUri == null) {
                startMockRegistryHost();
                dockerHubRegistryUri = UriUtils.buildUri(mockRegistryHost,
                        MockRegistryPathConstants.DOCKER_HUB_BASE_PATH);
                defaultRegistryUri = UriUtils.buildUri(mockRegistryHost,
                        MockRegistryPathConstants.BASE_V1_PATH);
                v2RegistryUri = UriUtils.buildUri(mockRegistryHost,
                        MockRegistryPathConstants.BASE_V2_PATH);
            }
        }

        host.log("Using default test registry URI: %s", defaultRegistryUri);
        host.log("Using V2 test registry URI: %s", v2RegistryUri);
    }

    @AfterClass
    public static void tearDownMockDockerHost() {
        if (mockRegistryHost != null) {
            mockRegistryHost.tearDown();
        }
    }

    private void startMockRegistryHost() throws Throwable {
        ServiceHost.Arguments args = new ServiceHost.Arguments();
        args.sandbox = null;
        args.port = 0;
        mockRegistryHost = VerificationHost.create(args);
        mockRegistryHost.setPeerSynchronizationEnabled(this.getPeerSynchronizationEnabled());
        mockRegistryHost.setMaintenanceIntervalMicros(this.getMaintenanceIntervalMillis());
        mockRegistryHost.start();

        mockRegistryHost.startService(Operation.createPost(UriUtils.buildUri(
                mockRegistryHost, MockRegistryPingService.class)),
                new MockRegistryPingService());

        mockRegistryHost.startService(Operation.createPost(UriUtils.buildUri(
                mockRegistryHost, MockRegistrySearchService.class)),
                new MockRegistrySearchService());

        // V2 Ping and Search are served by the same endpoint.
        mockRegistryHost.startService(Operation.createPost(UriUtils.buildUri(
                mockRegistryHost, MockV2RegistrySearchService.class)),
                new MockV2RegistrySearchService());

        mockRegistryHost.startService(Operation.createPost(UriUtils.buildUri(
                mockRegistryHost, MockRegistryListTagsService.class)),
                new MockRegistryListTagsService());

        mockRegistryHost.startService(Operation.createPost(UriUtils.buildUri(
                mockRegistryHost, MockV1RegistryListTagsService.class)),
                new MockV1RegistryListTagsService());

        mockRegistryHost.startService(Operation.createPost(UriUtils.buildUri(
                mockRegistryHost, MockV2RegistryListTagsService.class)),
                new MockV2RegistryListTagsService());
    }

    public static URI getDockerHubRegistryUri() {
        return dockerHubRegistryUri;
    }

    public static URI getDefaultRegistryUri() {
        return defaultRegistryUri;
    }

    public static URI getV2RegistryUri() {
        return v2RegistryUri;
    }
}
