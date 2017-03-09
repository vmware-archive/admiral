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

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.KubernetesOperationType;
import com.vmware.admiral.adapter.common.service.mock.MockTaskService.MockTaskState;
import com.vmware.admiral.adapter.kubernetes.mock.BaseKubernetesMockTest;
import com.vmware.admiral.adapter.kubernetes.mock.MockKubernetesHost;
import com.vmware.admiral.adapter.kubernetes.mock.MockKubernetesHostService;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.kubernetes.entities.common.ObjectMeta;
import com.vmware.admiral.compute.kubernetes.entities.pods.Container;
import com.vmware.admiral.compute.kubernetes.entities.pods.Pod;
import com.vmware.admiral.compute.kubernetes.entities.pods.PodSpec;
import com.vmware.admiral.compute.kubernetes.service.PodService;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.LogService.LogServiceState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.ServiceOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

public class KubernetesAdapterServiceTest extends BaseKubernetesMockTest {
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
        kubernetesHostState = createKubernetesHostComputeState(testKubernetesCredentialsLink);
    }

    @After
    public void stopServices() {
        mockKubernetesHost.stopService(service);
    }

    @Test
    public void testFetchLogs() throws Throwable {
        service.containerNamesToLogs.put("container1", "test-log-1");
        service.containerNamesToLogs.put("container2", "test-log-2");
        service.containerNamesToLogs.put("container3", "test-log-3");

        PodState podState = new PodState();
        podState.pod = new Pod();
        podState.pod.spec = new PodSpec();
        podState.pod.spec.containers = new ArrayList<>();
        Container container1 = new Container();
        container1.name = "container1";
        Container container2 = new Container();
        container2.name = "container2";
        Container container3 = new Container();
        container3.name = "container3";
        podState.pod.spec.containers.add(container1);
        podState.pod.spec.containers.add(container2);
        podState.pod.spec.containers.add(container3);
        podState.pod.metadata = new ObjectMeta();
        podState.pod.metadata.selfLink = "/api/v1/namespaces/default/pods/test-pod";
        podState.parentLink = kubernetesHostState.documentSelfLink;

        podState = doPost(podState, PodService.FACTORY_LINK);

        provisioningTaskLink = createProvisioningTask();

        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildUri(host, podState.documentSelfLink);
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        request.operationTypeId = KubernetesOperationType.FETCH_LOGS.id;

        doOperation(KubernetesAdapterService.SELF_LINK, request);

        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskState.TaskStage.FINISHED);

        for (Container container : podState.pod.spec.containers) {
            LogServiceState logState = getDocument(LogServiceState.class, LogService
                    .FACTORY_LINK + podState.documentSelfLink + "-" + container.name);
            assertEquals(service.containerNamesToLogs.get(container.name), new String(logState
                    .logs, "UTF-8"));
        }

    }

    @Test
    public void testInspect() throws Throwable {
        PodState podState = new PodState();
        podState.pod = new Pod();
        podState.pod.spec = new PodSpec();
        podState.pod.spec.containers = new ArrayList<>();
        Container container1 = new Container();
        container1.name = "container1";
        container1.image = "test-image";
        podState.pod.spec.containers.add(container1);
        podState.pod.metadata = new ObjectMeta();
        podState.pod.metadata.selfLink = "/api/v1/namespaces/default/pods/test-pod";
        podState.pod.metadata.name = "test-pod";
        podState.parentLink = kubernetesHostState.documentSelfLink;
        podState.kubernetesSelfLink = podState.pod.metadata.selfLink;
        podState = doPost(podState, PodService.FACTORY_LINK);

        Pod updatedPod = new Pod();
        updatedPod.metadata = new ObjectMeta();
        updatedPod.metadata.name = "test-pod";
        updatedPod.metadata.selfLink = "/api/v1/namespaces/default/pods/test-pod";
        updatedPod.spec = new PodSpec();
        updatedPod.spec.containers = new ArrayList<>();
        Container updatedContainer = new Container();
        updatedContainer.name = "new-container1";
        updatedContainer.image = "new-test-image";
        updatedPod.spec.containers.add(updatedContainer);

        service.inspectMap.put(podState.pod, updatedPod);

        provisioningTaskLink = createProvisioningTask();

        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildUri(host, podState.documentSelfLink);
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        request.operationTypeId = KubernetesOperationType.INSPECT.id;
        doOperation(KubernetesAdapterService.SELF_LINK, request);

        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, "taskInfo.stage",
                TaskState.TaskStage.FINISHED);

        PodState patchedPod = getDocument(PodState.class, podState.documentSelfLink);

        assertEquals(podState.descriptionLink, patchedPod.descriptionLink);
        assertEquals(podState.compositeComponentLink, patchedPod.compositeComponentLink);
        assertEquals(podState.parentLink, patchedPod.parentLink);

        assertEquals(updatedContainer.name, patchedPod.pod.spec.containers.get(0).name);
        assertEquals(updatedContainer.image, patchedPod.pod.spec.containers.get(0).image);


    }
}
