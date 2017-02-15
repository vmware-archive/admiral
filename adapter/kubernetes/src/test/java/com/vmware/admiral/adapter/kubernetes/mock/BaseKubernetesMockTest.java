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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;

import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService;
import com.vmware.admiral.compute.kubernetes.service.PodService;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler;
import com.vmware.admiral.host.CompositeComponentNotificationProcessingChain;
import com.vmware.admiral.host.ComputeInitialBootService;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitKubernetesAdapterServiceConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
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

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    @Before
    public void setUpMockKubernetesHost() throws Throwable {
        ServiceHost.Arguments args = new ServiceHost.Arguments();
        args.sandbox = null;
        args.port = 0;
        mockKubernetesHost = createHost();
        mockKubernetesHost.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS
                .toMicros(MAINTENANCE_INTERVAL_MILLIS));
        kubernetesUri = UriUtils.buildUri(mockKubernetesHost,
                KubernetesPathConstants.BASE_PATH);

        kubernetesFailingUri = UriUtils.buildUri(mockKubernetesHost,
                KubernetesPathConstants.BASE_FAILING_PATH);

        kubernetesCredentials = new AuthCredentialsServiceState();
        kubernetesCredentials.type = AuthCredentialsType.Password.name();
        kubernetesCredentials.userEmail = "test@admiral";
        kubernetesCredentials.privateKey = "password";

        HostInitTestDcpServicesConfig.startServices(host);
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitCommonServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, false);
        HostInitKubernetesAdapterServiceConfig.startServices(host, false);
        waitForServiceAvailability(ComputeInitialBootService.SELF_LINK);
        waitForInitialBootServiceToBeSelfStopped(ComputeInitialBootService.SELF_LINK);

        host.log("Using test kubernetes URI: %s", kubernetesUri);

        System.setProperty("dcp.management.container.shell.availability.retry", "0");

        host.startService(
                Operation.createPost(UriUtils.buildUri(host, MockTaskFactoryService.SELF_LINK)),
                new MockTaskFactoryService());

    }

    @After
    public void tearDownMockDockerHost() {
        if (mockKubernetesHost != null) {
            mockKubernetesHost.tearDown();
        }
    }

    @Override
    protected void customizeChains(
            Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains) {
        chains.put(DeploymentService.class, CompositeComponentNotificationProcessingChain.class);
        chains.put(PodService.class, CompositeComponentNotificationProcessingChain.class);
        chains.put(ServiceEntityHandler.class, CompositeComponentNotificationProcessingChain.class);
        chains.put(ReplicationControllerService.class,
                CompositeComponentNotificationProcessingChain.class);
    }

    protected static AuthCredentialsServiceState getKubernetesCredentials() {
        return kubernetesCredentials;
    }

    protected static SslTrustCertificateState getKubernetesServerTrust() {
        return kubernetesTrust;
    }
}
