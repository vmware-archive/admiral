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

package com.vmware.admiral.service.kubernetes.test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.vmware.admiral.adapter.common.ApplicationOperationType;
import com.vmware.admiral.adapter.common.ApplicationRequest;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.service.test.BaseMockAdapterService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

public class MockKubernetesApplicationAdapterService extends BaseMockAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION;

    private static final List<CompositeComponent> PROVISIONED_COMPONENTS = Collections
            .synchronizedList(new ArrayList<>());

    private static class MockAdapterRequest extends ApplicationRequest {

        public boolean isProvisioning() {
            return ApplicationOperationType.CREATE.id.equals(operationTypeId);
        }

        public boolean isDeprovisioning() {
            return ApplicationOperationType.DELETE.id.equals(operationTypeId);
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
            }
            if (op.hasBody()) {
                MockAdapterRequest state = op.getBody(MockAdapterRequest.class);
                removeCompositeComponent(state.resourceReference);
                op.complete();
                return;
            } else {
                op.complete();
                return;
            }
        }

        if (op.getAction() == Action.GET) {
            op.setStatusCode(204);
            op.complete();
            return;
        }

        if (op.getAction() != Action.PATCH) {
            op.fail(new IllegalArgumentException("action not supported"));
            return;
        }

        op.setStatusCode(Operation.STATUS_CODE_ACCEPTED).complete();

        MockAdapterRequest state = op.getBody(MockAdapterRequest.class);

        TaskState taskInfo = state.validateMock();

        logInfo("Request accepted for resource: %s", state.resourceReference);
        if (TaskStage.FAILED == taskInfo.stage) {
            logInfo("Failed request for resource:  %s", state.resourceReference);
            patchTaskStage(state, taskInfo.failure);
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

    private void processRequest(MockAdapterRequest state, TaskState taskInfo,
            CompositeComponent compositeComponent, CompositeDescription compositeDescription) {
        if (TaskStage.FAILED == taskInfo.stage) {
            logInfo("Failed request based on compositeComponent resource:  %s",
                    state.resourceReference);
            patchTaskStage(state, taskInfo.failure);
            return;
        }

        if (compositeComponent == null) {
            getDocument(CompositeComponent.class, state.resourceReference, taskInfo,
                    (cc) -> processRequest(state, taskInfo, cc, compositeDescription));
            return;
        }

        if (compositeDescription == null) {
            getDocument(CompositeDescription.class,
                    state.resolve(compositeComponent.compositeDescriptionLink), taskInfo,
                    (cd) -> processRequest(state, taskInfo, compositeComponent, cd));
            return;
        }

        // define expected failure dynamically for every request
        if (compositeDescription.customProperties != null
                && compositeDescription.customProperties.remove(FAILURE_EXPECTED) != null) {
            patchTaskStage(state, new IllegalStateException("Simulated failure"));
            return;
        }

        if (state.isProvisioning()) {
            provisionCompositeComponent(state, compositeComponent);
        } else if (state.isDeprovisioning()) {
            deprovisionCompositeComponent(state);
        }
    }

    private void provisionCompositeComponent(MockAdapterRequest state, CompositeComponent cc) {
        CompositeComponent toPatch = new CompositeComponent();
        toPatch.created = Utils.getNowMicrosUtc();

        PROVISIONED_COMPONENTS.add(cc);

        sendRequest(Operation.createPatch(state.resourceReference)
                .setBody(toPatch)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    Throwable patchException = null;
                    if (e != null) {
                        logSevere(e);
                        patchException = e;
                    }
                    patchTaskStage(state, patchException);
                }));
    }

    private synchronized void deprovisionCompositeComponent(MockAdapterRequest state) {

        removeCompositeComponent(state.getCompositeComponentReference());

        patchTaskStage(state, (Throwable) null);

    }

    private synchronized void removeCompositeComponent(URI compositeComponentReference) {
        Iterator<CompositeComponent> it = PROVISIONED_COMPONENTS.iterator();
        while (it.hasNext()) {
            CompositeComponent cc = it.next();
            if (cc.documentSelfLink.equals(compositeComponentReference.getPath())) {
                it.remove();
            }
        }
    }

    public static List<CompositeComponent> getProvisionedComponents() {
        return new ArrayList<>(PROVISIONED_COMPONENTS);
    }

    public static synchronized void addCompositeComponent(CompositeComponent component) {
        PROVISIONED_COMPONENTS.add(component);
    }

    public static void clear() {
        PROVISIONED_COMPONENTS.clear();
    }
}
