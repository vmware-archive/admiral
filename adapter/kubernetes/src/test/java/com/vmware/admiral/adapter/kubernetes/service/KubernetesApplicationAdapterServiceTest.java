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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ApplicationOperationType;
import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.adapter.common.service.mock.MockTaskService.MockTaskState;
import com.vmware.admiral.adapter.kubernetes.mock.BaseKubernetesMockTest;
import com.vmware.admiral.adapter.kubernetes.mock.MockKubernetesHostService;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.compute.content.kubernetes.CommonKubernetesEntity;
import com.vmware.admiral.compute.content.kubernetes.KubernetesEntityList;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.content.kubernetes.ObjectMeta;
import com.vmware.admiral.compute.content.kubernetes.deployments.Deployment;
import com.vmware.admiral.compute.content.kubernetes.services.Service;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
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
import com.vmware.xenon.common.Utils;
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
    public void testDeployApplicationWithSingleContainer() throws Throwable {
        ContainerDescription containerDescription = createContainerDescription();

        CompositeDescription compositeDescription = createCompositeDescription
                (containerDescription);

        CompositeComponent compositeComponent = createCompositeComponent(compositeDescription);

        createProvisioningTask();

        ApplicationRequest appRequest = createApplicationRequest(
                compositeComponent.documentSelfLink);

        doOperation(ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION, appRequest);

        // wait for provisioning task stage to change to finish
        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskState.TaskStage.FINISHED);

        assertEquals(1, service.deployedElements.size());

        Deployment d = (Deployment) service.deployedElements.get(0);
        assertEquals(containerDescription.name, d.metadata.name);
        assertEquals(compositeComponent.name, d.spec.template.metadata.labels.get("app"));
    }

    @Test
    public void testValidateServicesAreDeployedBeforeDeployments() throws Throwable {
        String wordpressTemplate = CommonTestStateFactory.getFileContent
                ("WordPress_with_MySQL_containers.yaml");

        String compositeDescriptionLink = importTemplate(wordpressTemplate);

        CompositeDescription compositeDescription = getCompositeDescription
                (compositeDescriptionLink);

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

        // Cast the deployedElements to CommonKubernetesEntity so we can assert their kind.
        List<CommonKubernetesEntity> kubernetesElements = new ArrayList<>();
        service.deployedElements.forEach(e -> kubernetesElements.add((CommonKubernetesEntity) e));

        assertEquals(KubernetesUtil.SERVICE, kubernetesElements.get(0).kind);
        assertEquals(KubernetesUtil.SERVICE, kubernetesElements.get(1).kind);
        assertEquals(KubernetesUtil.DEPLOYMENT, kubernetesElements.get(2).kind);
        assertEquals(KubernetesUtil.DEPLOYMENT, kubernetesElements.get(3).kind);

    }

    @Test
    public void testSimpleApplicationDeletion() throws Throwable {
        ContainerState state1 = new ContainerState();
        state1.name = "testState1";
        state1 = doPost(state1, ContainerFactoryService.SELF_LINK);

        ContainerState state2 = new ContainerState();
        state2.name = "testState2";
        state2.ports = new ArrayList<>();
        state2.ports.add(new PortBinding());
        state2 = doPost(state2, ContainerFactoryService.SELF_LINK);

        CompositeComponent compositeComponent = new CompositeComponent();
        compositeComponent.name = "testCompositeComponent";
        compositeComponent.componentLinks = new ArrayList<>();
        compositeComponent.componentLinks.add(state1.documentSelfLink);
        compositeComponent.componentLinks.add(state2.documentSelfLink);
        compositeComponent = doPost(compositeComponent, CompositeComponentFactoryService
                .SELF_LINK);

        Deployment deployment1 = new Deployment();
        deployment1.kind = KubernetesUtil.DEPLOYMENT;
        deployment1.metadata = new ObjectMeta();
        deployment1.metadata.name = state1.name;

        Deployment deployment2 = new Deployment();
        deployment2.metadata = new ObjectMeta();
        deployment2.kind = KubernetesUtil.DEPLOYMENT;
        deployment2.metadata.name = state2.name;

        Service service2 = new Service();
        service2.metadata = new ObjectMeta();
        service2.kind = KubernetesUtil.SERVICE;
        service2.metadata.name = state2.name;

        service.deployedElements.add(deployment1);
        service.deployedElements.add(deployment2);
        service.deployedElements.add(service2);

        createProvisioningTask();

        ApplicationRequest appRequest = new ApplicationRequest();
        appRequest.hostReference = UriUtils.buildUri(host, kubernetesHostState.documentSelfLink);
        appRequest.resourceReference = UriUtils.buildUri(host, compositeComponent.documentSelfLink);
        appRequest.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        appRequest.operationTypeId = ApplicationOperationType.DELETE.id;

        doOperation(ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION, appRequest);

        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskStage.FINISHED);

        assertEquals(0, service.deployedElements.size());

    }

    @Test
    public void testVerifyBeforeDeleteShouldFail() throws Throwable {
        ContainerState state = new ContainerState();
        state.name = "testState";
        state.ports = new ArrayList<>();
        state.ports.add(new PortBinding());
        state = doPost(state, ContainerFactoryService.SELF_LINK);

        CompositeComponent compositeComponent = new CompositeComponent();
        compositeComponent.name = "testCompositeComponent";
        compositeComponent.componentLinks = new ArrayList<>();
        compositeComponent.componentLinks.add(state.documentSelfLink);
        compositeComponent.componentLinks.add(state.documentSelfLink);
        compositeComponent = doPost(compositeComponent, CompositeComponentFactoryService
                .SELF_LINK);

        Deployment deployment1 = new Deployment();
        deployment1.kind = KubernetesUtil.DEPLOYMENT;
        deployment1.metadata = new ObjectMeta();
        deployment1.metadata.name = state.name;

        Service service1 = new Service();
        service1.metadata = new ObjectMeta();
        service1.kind = KubernetesUtil.SERVICE;
        service1.metadata.name = state.name;

        service.deployedElements.add(deployment1);
        service.deployedElements.add(service1);
        // Add the service twice to intentionally make deletion fail.
        // serviceToDelete != servicesOnHost
        service.deployedElements.add(service1);

        createProvisioningTask();

        ApplicationRequest appRequest = new ApplicationRequest();
        appRequest.hostReference = UriUtils.buildUri(host, kubernetesHostState.documentSelfLink);
        appRequest.resourceReference = UriUtils.buildUri(host, compositeComponent.documentSelfLink);
        appRequest.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        appRequest.operationTypeId = ApplicationOperationType.DELETE.id;

        doOperation(ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION, appRequest);

        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskStage.FAILED);

        assertEquals(3, service.deployedElements.size());
    }

    private ApplicationRequest createApplicationRequest(String resourceReference) {
        ApplicationRequest appRequest = new ApplicationRequest();
        appRequest.hostReference = UriUtils.buildUri(host, kubernetesHostState.documentSelfLink);
        appRequest.resourceReference = UriUtils.buildUri(host, resourceReference);
        appRequest.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        appRequest.operationTypeId = ApplicationOperationType.CREATE.id;
        return appRequest;
    }

    private CompositeDescription createCompositeDescription(
            ContainerDescription containerDescription) throws Throwable {
        // Create CompositeDescription
        CompositeDescription compositeDescription = new CompositeDescription();
        compositeDescription.name = "application";
        compositeDescription.descriptionLinks = new ArrayList<>();
        compositeDescription.descriptionLinks.add(containerDescription.documentSelfLink);
        compositeDescription = doPost(compositeDescription, CompositeDescriptionFactoryService
                .SELF_LINK);
        return compositeDescription;
    }

    private ContainerDescription createContainerDescription() throws Throwable {
        // Create ContainerDescription
        ContainerDescription containerDescription = new ContainerDescription();
        containerDescription.name = "new-deployment";
        containerDescription.image = "test";
        containerDescription = doPost(containerDescription,
                ContainerDescriptionService.FACTORY_LINK);
        return containerDescription;
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

        public MockKubernetesHost() {
            super(ServiceDocument.class);
            deployedElements = new ArrayList<>();
        }

        @Override
        public void handleGet(Operation get) {
            String uri = get.getUri().toString();
            if (uri.contains("/services")) {
                KubernetesEntityList services = getServiceList();
                get.setBody(services);
                get.complete();
            } else if (uri.contains("/deployments")) {
                get.setBody(getDeploymentList());
                get.complete();
            } else {
                get.fail(404);
            }
        }

        @Override
        public void handlePost(Operation post) {
            String uri = post.getUri().toString();
            if (uri.endsWith("/services")) {
                callbackRandomly(post, post.getBody(Service.class), "Status: success");
            } else if (uri.endsWith("/deployments")) {
                callbackRandomly(post, post.getBody(Deployment.class), "Status: success");
            } else {
                post.fail(new IllegalArgumentException("Unknown uri " + uri));
            }
        }

        @Override
        public void handleDelete(Operation delete) {
            String uri = delete.getUri().toString();
            String[] splittedUri = uri.split("/");
            String componentName = splittedUri[splittedUri.length - 1];
            int index = -1;
            if (uri.contains("/services")) {
                index = getComponentIndex(componentName, KubernetesUtil.SERVICE);
            } else if (uri.contains("/deployment")) {
                index = getComponentIndex(componentName, KubernetesUtil.DEPLOYMENT);
            }

            if (index == -1) {
                delete.fail(404);
            }
            deployedElements.remove(index);
            delete.complete();
        }

        private void callbackRandomly(Operation post, Object element, Object result) {
            post.setBody(result);
            if (Math.random() > 0.5) {
                deployedElements.add(element);
                post.complete();
            } else {
                getHost().schedule(() -> {
                    deployedElements.add(element);
                    post.complete();
                }, 20, TimeUnit.MILLISECONDS);
            }
        }

        private KubernetesEntityList<Deployment> getDeploymentList() {
            KubernetesEntityList<Deployment> deployments = new KubernetesEntityList<>();
            List<String> serializedDeployments = new ArrayList<>();
            deployedElements.forEach(e -> {
                CommonKubernetesEntity entity = (CommonKubernetesEntity) e;
                if (entity.kind.equals(KubernetesUtil.DEPLOYMENT)) {
                    serializedDeployments.add(Utils.toJson(e));
                }
            });
            deployments.items = serializedDeployments;
            return deployments;
        }

        private KubernetesEntityList<Service> getServiceList() {
            KubernetesEntityList<Service> services = new KubernetesEntityList<>();
            List<String> serializedServices = new ArrayList<>();
            deployedElements.forEach(e -> {
                CommonKubernetesEntity entity = (CommonKubernetesEntity) e;
                if (entity.kind.equals(KubernetesUtil.SERVICE)) {
                    serializedServices.add(Utils.toJson(e));
                }
            });
            services.items = serializedServices;
            return services;
        }

        private int getComponentIndex(String name, String kind) {
            int index = -1;
            for (int i = 0; i < deployedElements.size(); i++) {
                CommonKubernetesEntity entity = (CommonKubernetesEntity) deployedElements.get(i);
                if (entity.metadata.name.equals(name) && entity.kind.equals(kind)) {
                    index = i;
                    return index;
                }
            }
            return index;
        }
    }
}
