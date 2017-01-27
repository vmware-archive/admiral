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
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.adapter.common.service.mock.MockTaskService.MockTaskState;
import com.vmware.admiral.adapter.kubernetes.mock.BaseKubernetesMockTest;
import com.vmware.admiral.adapter.kubernetes.mock.MockKubernetesFailingHostService;
import com.vmware.admiral.adapter.kubernetes.mock.MockKubernetesHostService;
import com.vmware.admiral.adapter.kubernetes.service.AbstractKubernetesAdapterService.KubernetesContext;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.ContainerListCallback;
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

public class KubernetesMockTestHostAdapterServiceTest extends BaseKubernetesMockTest {
    private static final String TASK_INFO_STAGE = TaskServiceDocument.FIELD_NAME_TASK_STAGE;
    private URI kubernetesHostAdapterServiceUri;
    private ComputeState kubernetesHostState;
    // private ContainerState shellContainerState;
    private String testKubernetesCredentialsLink;
    private String provisioningTaskLink;

    private KubernetesContext context;

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
    public void setUp() throws Throwable {
        createTestKubernetesAuthCredentials();
        createKubernetesHostComputeState();
        // createHostShellContainer();
        createProvisioningTask();

        setupKubernetesAdapterService();
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    @After
    public void tearDown() throws Throwable {
        // deleteHostShellContainer();
        deleteKubernetesHostComputeState();
    }

    protected void createTestKubernetesAuthCredentials() throws Throwable {
        testKubernetesCredentialsLink = doPost(getKubernetesCredentials(),
                AuthCredentialsService.FACTORY_LINK).documentSelfLink;
        SslTrustCertificateState kubernetesServerTrust = getKubernetesServerTrust();
        if (kubernetesServerTrust != null && kubernetesServerTrust.certificate != null
                && !kubernetesServerTrust.certificate.isEmpty()) {
            doPost(kubernetesServerTrust, SslTrustCertificateService.FACTORY_LINK);
        }
    }

    protected void createKubernetesHostComputeState() throws Throwable {
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

    /*protected void createHostShellContainer() throws Throwable {
        ContainerState state = new ContainerState();
        String hostId = Service.getId(kubernetesHostState.documentSelfLink);
        state.documentSelfLink = SystemContainerDescriptions.getSystemContainerSelfLink(
                SystemContainerDescriptions.AGENT_CONTAINER_NAME, hostId);
        state.parentLink = kubernetesHostState.documentSelfLink;

        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "80";
        portBinding.hostPort = "80";
        state.ports = new ArrayList<>();
        state.ports.add(portBinding);

        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);
        shellContainerState = doPost(state, ContainerFactoryService.SELF_LINK);
    }*/

    protected void createProvisioningTask() throws Throwable {
        MockTaskState provisioningTask = new MockTaskState();
        provisioningTaskLink = doPost(provisioningTask,
                MockTaskFactoryService.SELF_LINK).documentSelfLink;
    }

    protected void setupKubernetesAdapterService() {
        kubernetesHostAdapterServiceUri = UriUtils.buildUri(host, KubernetesHostAdapterService.class);

        host.startService(Operation.createPost(kubernetesHostAdapterServiceUri),
                new KubernetesHostAdapterService());
    }

    /*protected void deleteHostShellContainer() throws Throwable {
        doDelete(UriUtils.buildUri(host, shellContainerState.documentSelfLink), false);
    }*/

    protected void deleteKubernetesHostComputeState() throws Throwable {
        doDelete(UriUtils.buildUri(host, kubernetesHostState.documentSelfLink), false);
    }

    private URI getKubernetesHostStateUri() {
        return UriUtils.buildUri(host, kubernetesHostState.documentSelfLink);
    }

    protected TaskStage waitForTaskToFinish() throws Throwable {
        MockTaskState stage = waitForPropertyValue(provisioningTaskLink, MockTaskState.class,
                TASK_INFO_STAGE,
                Arrays.asList(TaskStage.FINISHED, TaskStage.FAILED, TaskStage.CANCELLED),
                true, new AtomicInteger(100));
        return stage.taskInfo.stage;
    }

    private AdapterRequest prepareAdapterRequest(ContainerHostOperationType opType) {
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildUri(host, ComputeService.FACTORY_LINK);
        request.operationTypeId = opType.id;
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        addRequestProperties(request);
        return request;
    }

    private AdapterRequest prepareAdapterFailingRequest(ContainerHostOperationType opType) {
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildUri(host, ComputeService.FACTORY_LINK);
        request.operationTypeId = opType.id;
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        addRequestProperties(request);
        request.customProperties.put(ComputeConstants.HOST_URI_PROP_NAME,
                kubernetesFailingUri.toString());
        return request;
    }

    private void addRequestProperties(AdapterRequest request) {
        if (request.customProperties == null) {
            request.customProperties = new HashMap<>();
        }
        request.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                testKubernetesCredentialsLink);
        request.customProperties.put(ComputeConstants.HOST_URI_PROP_NAME,
                kubernetesUri.toString());
        request.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());
        request.customProperties.put(
                ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.KUBERNETES.name());
    }

    private OperationResult sendAdapterRequest(AdapterRequest request, URI computeStateReference)
            throws Throwable {
        request.resourceReference = computeStateReference;
        return sendAdapterRequest(request);
    }

    private OperationResult sendAdapterRequest(AdapterRequest request) throws Throwable {
        OperationResult result = new OperationResult();
        Operation op = Operation
                .createPatch(kubernetesHostAdapterServiceUri)
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

    @Test
    public void testHostDirectPing() throws Throwable {
        AdapterRequest request = prepareAdapterRequest(ContainerHostOperationType.PING);
        mockKubernetesHost.waitForServiceAvailable(MockKubernetesHostService.SELF_LINK);
        OperationResult r = sendAdapterRequest(request);
        Assert.assertNull(r.ex);
        Assert.assertEquals(200, r.op.getStatusCode());

        request.customProperties.remove(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME);
        r = sendAdapterRequest(request);
        Assert.assertNull(r.ex);
        Assert.assertEquals(200, r.op.getStatusCode());
    }

    @Test
    public void testHostDirectInfo() throws Throwable {
        AdapterRequest request = prepareAdapterRequest(ContainerHostOperationType.INFO);
        mockKubernetesHost.waitForServiceAvailable(MockKubernetesHostService.SELF_LINK);
        OperationResult r = sendAdapterRequest(request);
        Assert.assertNull(r.ex);
        Assert.assertEquals(200, r.op.getStatusCode());

        request.customProperties.remove(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME);
        r = sendAdapterRequest(request);
        Assert.assertNull(r.ex);
        Assert.assertEquals(200, r.op.getStatusCode());
    }

    @Test
    public void testPingWithResourceReference() throws Throwable {
        AdapterRequest request = prepareAdapterRequest(ContainerHostOperationType.PING);
        mockKubernetesHost.waitForServiceAvailable(MockKubernetesHostService.SELF_LINK);
        OperationResult r = sendAdapterRequest(request, getKubernetesHostStateUri());
        TaskStage stage = waitForTaskToFinish();
        Assert.assertEquals(TaskStage.FINISHED, stage);
        Assert.assertNull(r.ex);
        Assert.assertEquals(200, r.op.getStatusCode());
    }

    @Test
    public void testInfoWithResourceReference() throws Throwable {
        AdapterRequest request = prepareAdapterRequest(ContainerHostOperationType.INFO);
        mockKubernetesHost.waitForServiceAvailable(MockKubernetesHostService.SELF_LINK);
        OperationResult r = sendAdapterRequest(request, getKubernetesHostStateUri());
        TaskStage stage = waitForTaskToFinish();
        Assert.assertEquals(TaskStage.FINISHED, stage);
        Assert.assertNull(r.ex);
        Assert.assertEquals(200, r.op.getStatusCode());
    }

    @Test
    public void testListContainers() throws Throwable {
        AdapterRequest request = prepareAdapterRequest(ContainerHostOperationType.LIST_CONTAINERS);
        mockKubernetesHost.waitForServiceAvailable(MockKubernetesHostService.SELF_LINK);
        OperationResult r = sendAdapterRequest(request, getKubernetesHostStateUri());
        Assert.assertNull(r.ex);
        Assert.assertEquals(200, r.op.getStatusCode());
        Assert.assertNotNull(r.op.getBody(ContainerListCallback.class));

        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        r = sendAdapterRequest(request, getKubernetesHostStateUri());
        Assert.assertNull(r.ex);
        Assert.assertEquals(200, r.op.getStatusCode());
        Assert.assertNotNull(r.op.getBody(ContainerListCallback.class));
    }

    @Test
    public void testOperationsWithFailingHost() throws Throwable {
        OperationResult r;
        TaskStage stage;
        AdapterRequest request;

        // Direct ping
        request = prepareAdapterFailingRequest(ContainerHostOperationType.PING);
        r = sendAdapterRequest(request);
        Assert.assertEquals(500, r.op.getStatusCode());
        Assert.assertNotNull(r.ex);

        // Ping with resource reference
        request = prepareAdapterFailingRequest(ContainerHostOperationType.PING);
        r = sendAdapterRequest(request, getKubernetesHostStateUri());
        stage = waitForTaskToFinish();
        Assert.assertEquals(TaskStage.FAILED, stage);
        Assert.assertEquals(200, r.op.getStatusCode());
        Assert.assertNull(r.ex);

        // Direct info
        request = prepareAdapterFailingRequest(ContainerHostOperationType.INFO);
        r = sendAdapterRequest(request);
        Assert.assertEquals(500, r.op.getStatusCode());
        Assert.assertNotNull(r.ex);

        // Info with resource reference
        request = prepareAdapterFailingRequest(ContainerHostOperationType.INFO);
        r = sendAdapterRequest(request, getKubernetesHostStateUri());
        stage = waitForTaskToFinish();
        Assert.assertEquals(TaskStage.FAILED, stage);
        Assert.assertEquals(200, r.op.getStatusCode());
        Assert.assertNull(r.ex);

        // Direct list containers
        request = prepareAdapterFailingRequest(ContainerHostOperationType.LIST_CONTAINERS);
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        r = sendAdapterRequest(request, getKubernetesHostStateUri());
        Assert.assertEquals(500, r.op.getStatusCode());
        Assert.assertNotNull(r.ex);

        // List containers with callback reference
        request = prepareAdapterFailingRequest(ContainerHostOperationType.LIST_CONTAINERS);
        r = sendAdapterRequest(request, getKubernetesHostStateUri());
        stage = waitForTaskToFinish();
        Assert.assertEquals(TaskStage.FAILED, stage);
        Assert.assertEquals(200, r.op.getStatusCode());
        Assert.assertNull(r.ex);
    }
}
