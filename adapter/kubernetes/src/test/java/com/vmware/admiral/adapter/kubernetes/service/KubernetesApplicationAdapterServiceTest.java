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

import static com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent.CUSTOM_PROPERTY_HOST_LINK;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.DEPLOYMENT_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.KUBERNETES_LABEL_APP_ID;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.POD_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.REPLICA_SET_TYPE;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ApplicationOperationType;
import com.vmware.admiral.adapter.common.ApplicationRequest;
import com.vmware.admiral.adapter.common.service.mock.MockTaskService.MockTaskState;
import com.vmware.admiral.adapter.kubernetes.mock.BaseKubernetesMockTest;
import com.vmware.admiral.adapter.kubernetes.mock.MockKubernetesHost;
import com.vmware.admiral.adapter.kubernetes.mock.MockKubernetesHostService;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.admiral.compute.kubernetes.entities.common.ObjectMeta;
import com.vmware.admiral.compute.kubernetes.entities.pods.Pod;
import com.vmware.admiral.compute.kubernetes.entities.replicaset.ReplicaSet;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService.DeploymentState;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.compute.kubernetes.service.ReplicaSetService.ReplicaSetState;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler.ServiceState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.ServiceOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

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

        testKubernetesCredentialsLink = createTestKubernetesAuthCredentials();
        kubernetesHostState = createKubernetesHostComputeState(testKubernetesCredentialsLink
        );
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
        compositeComponent.customProperties = new HashMap<>();
        compositeComponent.customProperties.put(CUSTOM_PROPERTY_HOST_LINK,
                kubernetesHostState.documentSelfLink);
        compositeComponent = doPost(compositeComponent, CompositeComponentFactoryService.SELF_LINK);

        addPodsAndRSForWordpressApp(extractId(compositeComponent.documentSelfLink));

        provisioningTaskLink = createProvisioningTask();

        ApplicationRequest appRequest = createApplicationRequest(
                compositeComponent.documentSelfLink);

        doOperation(ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION, appRequest);

        // wait for provisioning task stage to change to finish
        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskState.TaskStage.FINISHED);

        assertEquals(8, service.deployedElements.size());

        List<BaseKubernetesObject> kubernetesElements = new ArrayList<>();
        service.deployedElements.forEach(e -> kubernetesElements.add(e));

        // Assert that services are deployed first.
        // Checking on 4, 5, 6, 7 index because pods and replica sets are
        // added manually before everything else, in the real case k8s
        // will create them once deployments are created.
        assertEquals(KubernetesUtil.SERVICE_TYPE, kubernetesElements.get(4).kind);
        assertEquals(KubernetesUtil.SERVICE_TYPE, kubernetesElements.get(5).kind);
        assertEquals(DEPLOYMENT_TYPE, kubernetesElements.get(6).kind);
        assertEquals(DEPLOYMENT_TYPE, kubernetesElements.get(7).kind);

        // Assert that states are created and they have correct compositeComponentLink.
        CompositeComponent finalCompositeComponent = compositeComponent;

        List<String> resourceLinks = getDocumentLinksOfType(ServiceState.class);
        assertEquals(2, resourceLinks.size());
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
        assertEquals(2, resourceLinks.size());
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

        resourceLinks = getDocumentLinksOfType(PodState.class);
        assertEquals(2, resourceLinks.size());
        resourceLinks.forEach(link -> doOperation(Operation.createGet(host, link)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                    } else {
                        PodState state = o.getBody(PodState.class);
                        assertEquals(state.compositeComponentLink,
                                finalCompositeComponent.documentSelfLink);
                        host.completeIteration();
                    }
                })));

        resourceLinks = getDocumentLinksOfType(ReplicaSetState.class);
        assertEquals(2, resourceLinks.size());
        resourceLinks.forEach(link -> doOperation(Operation.createGet(host, link)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                    } else {
                        ReplicaSetState state = o.getBody(ReplicaSetState.class);
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

        provisioningTaskLink = createProvisioningTask();

        ApplicationRequest appRequest = createApplicationRequest(
                compositeComponent.documentSelfLink);

        doOperation(ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION, appRequest);

        // wait for provisioning task stage to change to finish
        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskState.TaskStage.FINISHED);

        // Delete the application
        provisioningTaskLink = createProvisioningTask();
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

        provisioningTaskLink = createProvisioningTask();

        ApplicationRequest appRequest = createApplicationRequest(
                compositeComponent.documentSelfLink);

        doOperation(ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION, appRequest);

        // wait for provisioning task stage to change to finish
        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskState.TaskStage.FINISHED);

        service.failIntentionally = true;

        provisioningTaskLink = createProvisioningTask();
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
        compositeComponent.customProperties = new HashMap<>();
        compositeComponent.customProperties.put(CUSTOM_PROPERTY_HOST_LINK,
                kubernetesHostState.documentSelfLink);
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

    private void addPodsAndRSForWordpressApp(String compositeComponentId) {
        Pod wpPod = new Pod();
        wpPod.kind = POD_TYPE;
        wpPod.metadata = new ObjectMeta();
        wpPod.metadata.labels = new HashMap<>();
        wpPod.metadata.labels.put(KUBERNETES_LABEL_APP_ID, compositeComponentId);

        Pod mysqlPod = new Pod();
        mysqlPod.kind = POD_TYPE;
        mysqlPod.metadata = new ObjectMeta();
        mysqlPod.metadata.labels = new HashMap<>();
        mysqlPod.metadata.labels.put(KUBERNETES_LABEL_APP_ID, compositeComponentId);

        ReplicaSet wpRs = new ReplicaSet();
        wpRs.kind = REPLICA_SET_TYPE;
        wpRs.metadata = new ObjectMeta();
        wpRs.metadata.labels = new HashMap<>();
        wpRs.metadata.labels.put(KUBERNETES_LABEL_APP_ID, compositeComponentId);

        ReplicaSet mysqlRs = new ReplicaSet();
        mysqlRs.kind = REPLICA_SET_TYPE;
        mysqlRs.metadata = new ObjectMeta();
        mysqlRs.metadata.labels = new HashMap<>();
        mysqlRs.metadata.labels.put(KUBERNETES_LABEL_APP_ID, compositeComponentId);

        service.deployedElements.add(wpPod);
        service.deployedElements.add(mysqlPod);
        service.deployedElements.add(wpRs);
        service.deployedElements.add(mysqlRs);

    }

}
