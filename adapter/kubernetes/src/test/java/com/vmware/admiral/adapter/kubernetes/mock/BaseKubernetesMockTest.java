/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.kubernetes.mock;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.host.ComputeInitialBootService;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class BaseKubernetesMockTest extends BaseTestCase {
    protected static VerificationHost mockKubernetesHost;

    protected static URI kubernetesUri;
    protected static URI kubernetesFailingUri;
    protected static AuthCredentialsServiceState kubernetesCredentials;
    protected static SslTrustCertificateState kubernetesTrust;

    protected class OperationResult {
        public Operation op;
        public Throwable ex;

        public OperationResult() {
        }
    }

    @Before
    public void setUpMockKubernetesHost() throws Throwable {
        HostInitTestDcpServicesConfig.startServices(host);
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitCommonServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, false);
        waitForServiceAvailability(ComputeInitialBootService.SELF_LINK);
        waitForInitialBootServiceToBeSelfStopped(ComputeInitialBootService.SELF_LINK);

        host.log("Using test kubernetes URI: %s", kubernetesUri);

        System.setProperty("dcp.management.container.shell.availability.retry", "0");

        host.startService(
                Operation.createPost(UriUtils.buildUri(host, MockTaskFactoryService.SELF_LINK)),
                new MockTaskFactoryService());

    }

    @AfterClass
    public static void tearDownMockDockerHost() {
        if (mockKubernetesHost != null) {
            mockKubernetesHost.tearDown();
        }
    }

    @BeforeClass
    public static void startMockKubernetesHost() throws Throwable {
        ServiceHost.Arguments args = new ServiceHost.Arguments();
        args.sandbox = null;
        args.port = 0;
        mockKubernetesHost = VerificationHost.create(args);
        mockKubernetesHost.start();
        mockKubernetesHost.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS
                .toMicros(MAINTENANCE_INTERVAL_MILLIS));
        kubernetesUri = UriUtils.buildUri(mockKubernetesHost,
                MockKubernetesPathConstants.BASE_PATH);

        kubernetesFailingUri = UriUtils.buildUri(mockKubernetesHost,
                MockKubernetesPathConstants.BASE_FAILING_PATH);

        kubernetesCredentials = new AuthCredentialsServiceState();

        /*mockKubernetesHost.startService(Operation.createPost(UriUtils.buildUri(
                mockKubernetesHost, MockDockerCreateContainerService.class)),
                new MockDockerCreateContainerService());

        mockKubernetesHost.startService(Operation.createPost(UriUtils.buildUri(
                mockKubernetesHost, MockDockerCreateImageService.class)),
                new MockDockerCreateImageService());

        mockKubernetesHost.startService(Operation.createPost(UriUtils.buildUri(
                mockKubernetesHost, MockDockerContainerListService.class)),
                new MockDockerContainerListService());

        mockKubernetesHost.startService(Operation.createPost(UriUtils.buildUri(
                mockKubernetesHost, MockDockerCreateVolumeService.class)),
                new MockDockerCreateVolumeService());

        mockKubernetesHost.startService(Operation.createPost(UriUtils.buildUri(
                mockKubernetesHost, MockDockerVolumeListService.class)),
                new MockDockerVolumeListService());

        mockKubernetesHost.startService(Operation.createPost(UriUtils.buildUri(
                mockKubernetesHost, MockDockerInspectVolumeService.class)),
                new MockDockerInspectVolumeService());

        mockKubernetesHost.startService(Operation.createPost(UriUtils.buildUri(
                mockKubernetesHost, MockDockerNetworkService.class)),
                new MockDockerNetworkService());
        */
    }

    protected static AuthCredentialsServiceState getKubernetesCredentials() {
        return kubernetesCredentials;
    }

    protected static SslTrustCertificateState getKubernetesServerTrust() {
        return kubernetesTrust;
    }
}
