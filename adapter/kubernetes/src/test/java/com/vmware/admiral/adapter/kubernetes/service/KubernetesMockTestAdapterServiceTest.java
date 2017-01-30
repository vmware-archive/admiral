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

package com.vmware.admiral.adapter.kubernetes.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.adapter.common.service.mock.MockTaskService.MockTaskState;
import com.vmware.admiral.adapter.kubernetes.mock.BaseKubernetesMockTest;
import com.vmware.admiral.adapter.kubernetes.mock.MockKubernetesFailingHostService;
import com.vmware.admiral.adapter.kubernetes.mock.MockKubernetesHostService;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.ServiceOption;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class KubernetesMockTestAdapterServiceTest extends BaseKubernetesMockTest {
    private static final String TASK_INFO_STAGE = TaskServiceDocument.FIELD_NAME_TASK_STAGE;
    private static final String CONTAINER_NAME = "test-container-name";
    private static final String CONTAINER_GROUP = "test-group";
    private static final String[] ENV = {"name=value"};
    private static final String CUSTOM_PROP_NAME = "test-prop";
    private static final String CUSTOM_PROP_VALUE = "test-value";

    private String testKubernetesCredentialsLink;
    private String containerDescriptionLink;
    private String provisioningTaskLink;
    private ComputeState kubernetesHostState;
    private URI kubernetesAdapterServiceUri;
    private URI containerStateReference;

    private ComputeState kubernetesFailingHostState;
    private URI containerWithFailingParentState;

    @BeforeClass
    public static void startServices() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);

        MockKubernetesHostService service = new MockKubernetesHostService();
        // Set the service to handle all subpaths of its main path
        service.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
        mockKubernetesHost.startService(
                Operation.createPost(UriUtils.buildUri(
                        mockKubernetesHost, MockKubernetesHostService.SELF_LINK)),
                service);

        MockKubernetesFailingHostService failingService = new MockKubernetesFailingHostService();
        failingService.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
        mockKubernetesHost.startService(
                Operation.createPost(UriUtils.buildUri(
                        mockKubernetesHost, MockKubernetesFailingHostService.SELF_LINK)),
                failingService);
    }

    @Before
    public void setupContainerState() throws Throwable {
        createTestKubernetesAuthCredentials();
        createKubernetesHostComputeState();
        createContainerDescription();
        createContainerState();
        createFailing();
        createProvisioningTask();

        setupKubernetesAdapterService();
    }

    @After
    public void tearDownContainerState() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
        deleteKubernetesHostComputeState();
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    private void createTestKubernetesAuthCredentials() throws Throwable {
        testKubernetesCredentialsLink = doPost(getKubernetesCredentials(),
                AuthCredentialsService.FACTORY_LINK).documentSelfLink;
        SslTrustCertificateState kubernetesServerTrust = getKubernetesServerTrust();
        if (kubernetesServerTrust != null && kubernetesServerTrust.certificate != null
                && !kubernetesServerTrust.certificate.isEmpty()) {
            doPost(kubernetesServerTrust, SslTrustCertificateService.FACTORY_LINK);
        }
    }

    private void createKubernetesHostComputeState() throws Throwable {
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
        kubernetesHostState = doPost(computeState, ComputeService.FACTORY_LINK);
    }

    private void createContainerDescription() throws Throwable {
        ContainerDescription containerDescription = new ContainerDescription();

        containerDescription.image = "test-image";
        containerDescription.command = new String[] { "test-command" };

        PortBinding portBinding = new PortBinding();
        portBinding.protocol = "tcp";
        portBinding.containerPort = "8080";
        portBinding.hostIp = "0.0.0.0";
        portBinding.hostPort = "9999";
        containerDescription.portBindings = new PortBinding[] { portBinding };

        containerDescription.env = ENV;

        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        containerDescriptionLink = doPost(containerDescription,
                ContainerDescriptionService.FACTORY_LINK).documentSelfLink;
    }

    private void createContainerState() throws Throwable {
        ContainerState containerState = new ContainerState();
        // This id is returned by the kubernetes host mock
        containerState.id = "48896e7338126c05b0ccc9f73cda870f01563a4de8cb214f206dc8b4ff3f7230";
        containerState.parentLink = kubernetesHostState.documentSelfLink;
        containerState.descriptionLink = containerDescriptionLink;
        // containerState.names = new ArrayList<>(1);
        // containerState.names.add(CONTAINER_NAME);
        List<String> tenantLinks = new ArrayList<>();
        tenantLinks.add(CONTAINER_GROUP);
        containerState.tenantLinks = tenantLinks;
        // containerState.env = ENV;

        // add a custom property
        // containerState.customProperties = new HashMap<>();
        // containerState.customProperties.put(CUSTOM_PROP_NAME, CUSTOM_PROP_VALUE);

        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
        ContainerState container = doPost(containerState, ContainerFactoryService.SELF_LINK);
        containerStateReference = UriUtils.buildUri(host, container.documentSelfLink);
    }

    private void createFailing() throws Throwable {
        ComputeDescription computeDescription = new ComputeDescription();
        computeDescription.customProperties = new HashMap<>();
        computeDescription.id = UUID.randomUUID().toString();

        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        String computeDescriptionLink = doPost(computeDescription,
                ComputeDescriptionService.FACTORY_LINK).documentSelfLink;

        ComputeState computeState = new ComputeState();
        computeState.id = "testFailingComputeState";
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
        computeState.address = kubernetesFailingUri.toString();

        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        kubernetesFailingHostState = doPost(computeState, ComputeService.FACTORY_LINK);

        ContainerState containerState = new ContainerState();
        containerState.id = "test-failing-container-id";
        containerState.parentLink = kubernetesFailingHostState.documentSelfLink;
        containerState.descriptionLink = containerDescriptionLink;
        List<String> tenantLinks = new ArrayList<>();
        tenantLinks.add(CONTAINER_GROUP);
        containerState.tenantLinks = tenantLinks;

        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
        ContainerState container = doPost(containerState, ContainerFactoryService.SELF_LINK);
        containerWithFailingParentState = UriUtils.buildUri(host, container.documentSelfLink);
    }

    private void createProvisioningTask() throws Throwable {
        MockTaskState provisioningTask = new MockTaskState();
        provisioningTaskLink = doPost(provisioningTask,
                MockTaskFactoryService.SELF_LINK).documentSelfLink;
    }

    private void setupKubernetesAdapterService() {
        kubernetesAdapterServiceUri = UriUtils.buildUri(host, KubernetesAdapterService.class);

        host.startService(Operation.createPost(kubernetesAdapterServiceUri),
                new KubernetesAdapterService());
    }

    private void deleteKubernetesHostComputeState() throws Throwable {
        doDelete(UriUtils.buildUri(host, kubernetesHostState.documentSelfLink), false);
    }

    private AdapterRequest prepareAdapterRequest(ContainerOperationType opType) {
        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = opType.id;
        request.resourceReference = containerStateReference;
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        return request;
    }

    private AdapterRequest prepareFailingRequest(ContainerOperationType opType) {
        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = opType.id;
        request.resourceReference = containerWithFailingParentState;
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        return request;
    }

    private OperationResult sendAdapterRequest(AdapterRequest request) throws Throwable {
        OperationResult result = new OperationResult();
        Operation op = Operation
                .createPatch(kubernetesAdapterServiceUri)
                .setReferer(URI.create("/")).setBody(request)
                .setCompletion((o, ex) -> {
                    result.op = o;
                    result.ex = ex;
                    host.completeIteration();
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
        return result;
    }

    protected TaskStage waitForTaskToFinish() throws Throwable {
        MockTaskState stage = waitForPropertyValue(provisioningTaskLink, MockTaskState.class,
                TASK_INFO_STAGE,
                Arrays.asList(TaskStage.FINISHED, TaskStage.FAILED, TaskStage.CANCELLED),
                true, new AtomicInteger(100));
        return stage.taskInfo.stage;
    }

    @Test
    public void testInspectContainer() throws Throwable {
        AdapterRequest request = prepareAdapterRequest(ContainerOperationType.INSPECT);
        mockKubernetesHost.waitForServiceAvailable(MockKubernetesHostService.SELF_LINK);
        OperationResult r = sendAdapterRequest(request);
        TaskStage stage = waitForTaskToFinish();

        Assert.assertEquals(TaskStage.FINISHED, stage);
        Assert.assertNull(r.ex);
        Assert.assertNotNull(r.op);
        Assert.assertEquals(200, r.op.getStatusCode());
    }

    @Test
    public void testOperationWithFailingHost() throws Throwable {
        AdapterRequest request = prepareFailingRequest(ContainerOperationType.INSPECT);
        mockKubernetesHost.waitForServiceAvailable(MockKubernetesFailingHostService.SELF_LINK);
        OperationResult r = sendAdapterRequest(request);
        TaskStage stage = waitForTaskToFinish();

        Assert.assertEquals(TaskStage.FAILED, stage);
        Assert.assertNull(r.ex);
        Assert.assertNotNull(r.op);
        Assert.assertEquals(200, r.op.getStatusCode());
    }
}
