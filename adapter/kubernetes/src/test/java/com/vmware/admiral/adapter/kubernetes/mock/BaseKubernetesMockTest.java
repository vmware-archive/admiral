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
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;

import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.adapter.common.service.mock.MockTaskService.MockTaskState;
import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.admiral.host.CompositeComponentInterceptor;
import com.vmware.admiral.host.ComputeInitialBootService;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitKubernetesAdapterServiceConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.host.interceptor.OperationInterceptorRegistry;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
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

    protected ComputeState createKubernetesHostComputeState(String testKubernetesCredentialsLink)
            throws Throwable {
        ComputeDescription computeDescription = new ComputeDescription();
        computeDescription.customProperties = new HashMap<>();
        computeDescription.id = UUID.randomUUID().toString();

        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        String computeDescriptionLink = doPost(computeDescription,
                ComputeDescriptionService.FACTORY_LINK).documentSelfLink;

        ComputeState computeState = new ComputeState();
        computeState.id = "testParentComputeState";
        computeState.descriptionLink = computeDescriptionLink;
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(
                ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME, testKubernetesCredentialsLink);
        computeState.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());
        computeState.customProperties.put(
                ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.KUBERNETES.name());
        computeState.customProperties.put(
                KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME,
                KubernetesHostConstants.KUBERNETES_HOST_DEFAULT_NAMESPACE);
        computeState.address = kubernetesUri.toString();

        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        ComputeState kubernetesHostState = doPost(computeState, ComputeService.FACTORY_LINK);
        return kubernetesHostState;
    }

    protected String createTestKubernetesAuthCredentials()
            throws Throwable {
        String testKubernetesCredentialsLink = doPost(getKubernetesCredentials(),
                AuthCredentialsService.FACTORY_LINK).documentSelfLink;
        SslTrustCertificateState kubernetesServerTrust = getKubernetesServerTrust();
        if (kubernetesServerTrust != null && kubernetesServerTrust.certificate != null
                && !kubernetesServerTrust.certificate.isEmpty()) {
            doPost(kubernetesServerTrust, SslTrustCertificateService.FACTORY_LINK);
        }
        return testKubernetesCredentialsLink;
    }

    protected String createProvisioningTask() throws Throwable {
        MockTaskState provisioningTask = new MockTaskState();
        String provisioningTaskLink = doPost(provisioningTask,
                MockTaskFactoryService.SELF_LINK).documentSelfLink;
        return provisioningTaskLink;
    }

    public void doOperation(String path, Object body) {
        URI uri = UriUtils.buildUri(host, path);

        Operation startContainer = Operation
                .createPatch(uri)
                .setReferer(URI.create("/")).setBody(body)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                    }

                    host.completeIteration();
                });

        host.testStart(1);
        host.send(startContainer);
        host.testWait();
    }

    @After
    public void tearDownMockDockerHost() {
        if (mockKubernetesHost != null) {
            mockKubernetesHost.tearDown();
        }
    }

    @Override
    protected void registerInterceptors(OperationInterceptorRegistry registry) {
        CompositeComponentInterceptor.register(registry);
    }

    protected static AuthCredentialsServiceState getKubernetesCredentials() {
        return kubernetesCredentials;
    }

    protected static SslTrustCertificateState getKubernetesServerTrust() {
        return kubernetesTrust;
    }
}
