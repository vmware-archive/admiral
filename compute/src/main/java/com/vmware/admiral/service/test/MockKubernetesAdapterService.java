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

package com.vmware.admiral.service.test;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.KubernetesOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.service.KubernetesService.KubernetesState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class MockKubernetesAdapterService extends BaseMockAdapterService {

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_KUBERNETES;

    public static final String FAILURE_EXPECTED = "FAILURE_EXPECTED";
    public boolean isFailureExpected;

    // kubernetesComponentName -> kubernetesComponentType
    private static final Map<String, String> KUBERNETES_COMPONENTS = new ConcurrentHashMap<>();

    private static final Map<String, KubernetesState> KUBERNETES_ENTITIES = new ConcurrentHashMap<>();

    private static class MockAdapterRequest extends AdapterRequest {

        public boolean isProvisioning() {
            return KubernetesOperationType.CREATE.id.equals(operationTypeId);
        }

        public boolean isDeprovisioning() {
            return KubernetesOperationType.DELETE.id.equals(operationTypeId);
        }

        public TaskState validateMock() {
            TaskState taskInfo = new TaskState();
            try {
                validate();
            } catch (Exception e) {
                taskInfo.stage = TaskStage.FAILED;
                taskInfo.failure = Utils.toServiceErrorResponse(e);
            }

            return taskInfo;
        }
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.DELETE) {
            if (ServiceHost.isServiceStop(op)) {
                handleDeleteCompletion(op);
                return;
            } else {
                op.complete();
                return;
            }
        }

        if (op.getAction() == Action.GET) {
            op.setBody(KUBERNETES_COMPONENTS);
            op.complete();
            return;
        }

        if (op.getAction() != Action.PATCH) {
            op.fail(new IllegalArgumentException("action not supported"));
            return;
        }

        op.setStatusCode(Operation.STATUS_CODE_ACCEPTED).complete();

        MockKubernetesAdapterService.MockAdapterRequest state = op
                .getBody(MockKubernetesAdapterService.MockAdapterRequest.class);

        TaskState taskInfo = state.validateMock();

        logInfo("Request accepted for resource: %s", state.resourceReference);
        if (TaskStage.FAILED == taskInfo.stage) {
            logInfo("Failed request for resource:  %s", state.resourceReference);
            patchTaskStage(state, taskInfo.failure);
            return;
        }

        // static way to define expected failure
        if (this.isFailureExpected) {
            logInfo("Expected failure request for resource:  %s", state.resourceReference);
            patchTaskStage(state, new IllegalStateException("Simulated failure"));
            return;
        }

        // define expected failure dynamically for every request
        if (state.customProperties != null
                && state.customProperties.containsKey(FAILURE_EXPECTED)) {
            logInfo("Expected failure request from custom props for resource:  %s",
                    state.resourceReference);
            patchTaskStage(state, new IllegalStateException("Simulated failure"));
            return;
        }

        processRequest(state, taskInfo, null, null);
    }

    private void processRequest(MockKubernetesAdapterService.MockAdapterRequest state,
            TaskState taskInfo,
            KubernetesState kubernetesState, KubernetesDescription kubernetesDesc) {
        if (TaskStage.FAILED == taskInfo.stage) {
            logInfo("Failed request based on volume resource:  %s",
                    state.resourceReference);
            patchTaskStage(state, taskInfo.failure);
            return;
        }

        if (kubernetesState == null) {
            getDocument(KubernetesState.class, state.resourceReference, taskInfo,
                    (k8sState) -> processRequest(state, taskInfo, k8sState, kubernetesDesc));
            return;
        }

        if (kubernetesDesc == null && !state.isDeprovisioning()) {
            getDocument(KubernetesDescription.class,
                    UriUtils.buildUri(getHost(), kubernetesState.descriptionLink), taskInfo,
                    (desc) -> processRequest(state, taskInfo, kubernetesState, desc));
            return;
        }

        if (state.isProvisioning()) {
            addKubernetesComponent(kubernetesState.name, kubernetesDesc.type);
            patchTaskStage(state, (Throwable) null);
        } else if (state.isDeprovisioning()) {
            removeKubernetesComponent(kubernetesState.name);
            patchTaskStage(state, (Throwable) null);
        }
    }

    public static synchronized void addKubernetesComponent(String name, String type) {
        KUBERNETES_COMPONENTS.put(name, type);
    }

    public static synchronized void removeKubernetesComponent(String name) {
        KUBERNETES_COMPONENTS.remove(name);
    }

    public static synchronized Map<String, String> getKubernetesComponents() {
        return KUBERNETES_COMPONENTS;
    }

    public static void addEntity(KubernetesState entity) {
        KUBERNETES_ENTITIES.put(entity.id, entity);
    }

    public static Collection<KubernetesState> getKubernetesEntities() {
        return KUBERNETES_ENTITIES.values();
    }

    public static void clearKubernetesEntities() {
        KUBERNETES_ENTITIES.clear();
    }
}