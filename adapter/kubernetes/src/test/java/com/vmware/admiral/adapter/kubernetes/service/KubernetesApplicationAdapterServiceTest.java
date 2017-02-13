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

import static org.junit.Assert.assertEquals;

import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.DEPLOYMENT_TYPE;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ApplicationOperationType;
import com.vmware.admiral.adapter.common.ApplicationRequest;
import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.adapter.common.service.mock.MockTaskService.MockTaskState;
import com.vmware.admiral.adapter.kubernetes.mock.BaseKubernetesMockTest;
import com.vmware.admiral.adapter.kubernetes.mock.MockKubernetesHostService;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.admiral.compute.kubernetes.entities.deployments.Deployment;
import com.vmware.admiral.compute.kubernetes.entities.services.Service;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService.DeploymentState;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler.ServiceState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.ServiceOption;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class KubernetesApplicationAdapterServiceTest extends BaseKubernetesMockTest {

    private MockKubernetesHost service;
    private ComputeState kubernetesHostState;
    private String provisioningTaskLink;
    private String testKubernetesCredentialsLink;

    @Before
    public void startServices() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);

        service = new MockKubernetesHost();
        // Set the service to handle all subpaths of its main path
        service.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
        mockKubernetesHost.startService(
                Operation.createPost(UriUtils.buildUri(
                        mockKubernetesHost, MockKubernetesHostService.SELF_LINK)),
                service);

        createTestKubernetesAuthCredentials();
        createKubernetesHostComputeState();
    }

    @After
    public void stopServices() {
        mockKubernetesHost.stopService(service);
    }

    @Test
    public void testValidateServicesAreDeployedFirst() throws Throwable {
        String wordpressTemplate = CommonTestStateFactory
                .getFileContent("WordPress_with_MySQL_kubernetes.yaml");

        String compositeDescriptionLink = importTemplate(wordpressTemplate);

        CompositeDescription compositeDescription = getCompositeDescription(
                compositeDescriptionLink);

        CompositeComponent compositeComponent = new CompositeComponent();
        compositeComponent.name = compositeDescription.name + "-mcm-102";
        compositeComponent.compositeDescriptionLink = compositeDescription.documentSelfLink;
        compositeComponent = doPost(compositeComponent, CompositeComponentFactoryService.SELF_LINK);

        createProvisioningTask();

        ApplicationRequest appRequest = createApplicationRequest(
                compositeComponent.documentSelfLink);

        doOperation(ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION, appRequest);

        // wait for provisioning task stage to change to finish
        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskState.TaskStage.FINISHED);

        assertEquals(4, service.deployedElements.size());

        List<BaseKubernetesObject> kubernetesElements = new ArrayList<>();
        service.deployedElements.forEach(e -> kubernetesElements.add((BaseKubernetesObject) e));

        assertEquals(KubernetesUtil.SERVICE_TYPE, kubernetesElements.get(0).kind);
        assertEquals(KubernetesUtil.SERVICE_TYPE, kubernetesElements.get(1).kind);
        assertEquals(DEPLOYMENT_TYPE, kubernetesElements.get(2).kind);
        assertEquals(DEPLOYMENT_TYPE, kubernetesElements.get(3).kind);

        // Assert that states are created and they have correct compositeComponentLink.
        CompositeComponent finalCompositeComponent = compositeComponent;

        List<String> resourceLinks = getDocumentLinksOfType(ServiceState.class);
        resourceLinks.forEach(link -> doOperation(Operation.createGet(host, link)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                    } else {
                        ServiceState state = o.getBody(ServiceState.class);
                        assertEquals(state.compositeComponentLink,
                                finalCompositeComponent.documentSelfLink);
                        host.completeIteration();
                    }
                })));

        resourceLinks = getDocumentLinksOfType(DeploymentState.class);
        resourceLinks.forEach(link -> doOperation(Operation.createGet(host, link)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                    } else {
                        DeploymentState state = o.getBody(DeploymentState.class);
                        assertEquals(state.compositeComponentLink,
                                finalCompositeComponent.documentSelfLink);
                        host.completeIteration();
                    }
                })));
    }

    @Test
    public void testDeleteApplication() throws Throwable {
        String wordpressTemplate = CommonTestStateFactory
                .getFileContent("WordPress_with_MySQL_kubernetes.yaml");

        String compositeDescriptionLink = importTemplate(wordpressTemplate);

        CompositeDescription compositeDescription = getCompositeDescription(
                compositeDescriptionLink);

        CompositeComponent compositeComponent = createCompositeComponent(compositeDescription);

        createProvisioningTask();

        ApplicationRequest appRequest = createApplicationRequest(
                compositeComponent.documentSelfLink);

        doOperation(ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION, appRequest);

        // wait for provisioning task stage to change to finish
        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskState.TaskStage.FINISHED);

        // Delete the application
        createProvisioningTask();
        appRequest = createApplicationRequest(compositeComponent.documentSelfLink);
        appRequest.operationTypeId = ApplicationOperationType.DELETE.id;

        doOperation(ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION, appRequest);
        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskStage.FINISHED);

        List<String> resourceLinks = getDocumentLinksOfType(DeploymentState.class);
        assertEquals(0, resourceLinks.size());

        resourceLinks = getDocumentLinksOfType(ServiceState.class);
        assertEquals(0, resourceLinks.size());

        assertEquals(0, service.deployedElementsMap.size());

    }

    @Test
    public void testDeleteApplicationShouldFail() throws Throwable {
        String wordpressTemplate = CommonTestStateFactory
                .getFileContent("WordPress_with_MySQL_kubernetes.yaml");

        String compositeDescriptionLink = importTemplate(wordpressTemplate);

        CompositeDescription compositeDescription = getCompositeDescription(
                compositeDescriptionLink);

        CompositeComponent compositeComponent = createCompositeComponent(compositeDescription);

        createProvisioningTask();

        ApplicationRequest appRequest = createApplicationRequest(
                compositeComponent.documentSelfLink);

        doOperation(ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION, appRequest);

        // wait for provisioning task stage to change to finish
        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskState.TaskStage.FINISHED);

        service.failIntentionally = true;

        createProvisioningTask();
        appRequest = createApplicationRequest(compositeComponent.documentSelfLink);
        appRequest.operationTypeId = ApplicationOperationType.DELETE.id;

        doOperation(ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION, appRequest);
        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskStage.FAILED);

        // Assert nothing is deleted after task failed.
        List<String> resourceLinks = getDocumentLinksOfType(DeploymentState.class);
        assertEquals(2, resourceLinks.size());

        resourceLinks = getDocumentLinksOfType(ServiceState.class);
        assertEquals(2, resourceLinks.size());

        assertEquals(4, service.deployedElementsMap.size());

    }

    private ApplicationRequest createApplicationRequest(String resourceReference) {
        ApplicationRequest appRequest = new ApplicationRequest();
        appRequest.hostLink = kubernetesHostState.documentSelfLink;
        appRequest.resourceReference = UriUtils.buildUri(host, resourceReference);
        appRequest.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        appRequest.operationTypeId = ApplicationOperationType.CREATE.id;
        return appRequest;
    }

    private CompositeComponent createCompositeComponent(CompositeDescription compositeDescription)
            throws Throwable {
        // Create CompositeComponent
        CompositeComponent compositeComponent = new CompositeComponent();
        compositeComponent.name = compositeDescription.name + "-mcm-102";
        compositeComponent.compositeDescriptionLink = compositeDescription.documentSelfLink;
        compositeComponent = doPost(compositeComponent, CompositeComponentFactoryService.SELF_LINK);
        return compositeComponent;
    }

    private String importTemplate(String template) throws Throwable {
        OperationResult result = new OperationResult();
        Operation op = Operation
                .createPost(host, CompositeDescriptionContentService.SELF_LINK)
                .setContentType("application/yaml")
                .setReferer(URI.create("/")).setBody(template)
                .setCompletion((o, ex) -> {
                    result.op = o;
                    result.ex = ex;
                    host.completeIteration();
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
        return result.op.getResponseHeader("Location");
    }

    private void doOperation(String path, Object body) {
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

    private void doOperation(Operation op) {
        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    private CompositeDescription getCompositeDescription(String descriptionLink) {
        OperationResult result = new OperationResult();
        Operation op = Operation
                .createGet(host, descriptionLink)
                .setReferer(URI.create("/"))
                .setCompletion((o, ex) -> {
                    result.op = o;
                    result.ex = ex;
                    host.completeIteration();
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
        return result.op.getBody(CompositeDescription.class);
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

    protected void createTestKubernetesAuthCredentials() throws Throwable {
        testKubernetesCredentialsLink = doPost(getKubernetesCredentials(),
                AuthCredentialsService.FACTORY_LINK).documentSelfLink;
        SslTrustCertificateState kubernetesServerTrust = getKubernetesServerTrust();
        if (kubernetesServerTrust != null && kubernetesServerTrust.certificate != null
                && !kubernetesServerTrust.certificate.isEmpty()) {
            doPost(kubernetesServerTrust, SslTrustCertificateService.FACTORY_LINK);
        }
    }

    protected void createProvisioningTask() throws Throwable {
        MockTaskState provisioningTask = new MockTaskState();
        provisioningTaskLink = doPost(provisioningTask,
                MockTaskFactoryService.SELF_LINK).documentSelfLink;
    }

    private static class MockKubernetesHost extends StatelessService {

        private List<Object> deployedElements;

        private Map<String, Object> deployedElementsMap;

        private boolean failIntentionally;

        public MockKubernetesHost() {
            super(ServiceDocument.class);
            deployedElements = Collections.synchronizedList(new ArrayList<>());
            deployedElementsMap = new ConcurrentHashMap<>();
        }

        @Override
        public void handlePost(Operation post) {
            String uri = post.getUri().toString();
            if (uri.endsWith("/services")) {
                callbackRandomly(post, post.getBody(Service.class));
            } else if (uri.endsWith("/deployments")) {
                callbackRandomly(post, post.getBody(Deployment.class));
            } else {
                post.fail(new IllegalArgumentException("Unknown uri " + uri));
            }
        }

        @Override
        public void handleDelete(Operation delete) {
            String uri = delete.getUri().toString();
            String[] splittedUri = uri.split("/");
            String componentName = splittedUri[splittedUri.length - 1];

            if (failIntentionally) {
                delete.fail(404);
            } else {
                deployedElementsMap.remove(componentName);
                delete.complete();
            }
        }

        private void callbackRandomly(Operation post, Object element) {
            String responseBody;
            try {
                responseBody = YamlMapper.fromYamlToJson(post.getBody(String.class));
            } catch (IOException e) {
                post.fail(e);
                return;
            }
            if (Math.random() > 0.5) {
                deployedElements.add(element);
                deployedElementsMap.put(post.getBody(BaseKubernetesObject.class).metadata.name,
                        element);
                post.setBody(responseBody);
                post.complete();
            } else {
                getHost().schedule(() -> {
                    deployedElements.add(element);
                    deployedElementsMap.put(post.getBody(BaseKubernetesObject.class).metadata.name,
                            element);
                    post.setBody(responseBody);
                    post.complete();
                }, 20, TimeUnit.MILLISECONDS);
            }
        }
    }
}
