/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
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
import java.util.Objects;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.ApplicationOperationType;
import com.vmware.admiral.adapter.common.ApplicationRequest;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.entities.deployments.Deployment;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService;
import com.vmware.admiral.service.test.BaseMockAdapterService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class MockKubernetesApplicationAdapterService extends BaseMockAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION;

    private static final List<CompositeComponent> PROVISIONED_COMPONENTS = Collections
            .synchronizedList(new ArrayList<>());
    private static final List<DeploymentService.DeploymentState> CREATED_DEPLOYMENT_STATES = Collections
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
        PROVISIONED_COMPONENTS.add(cc);
        CompositeComponent toPatch = new CompositeComponent();
        toPatch.created = Utils.getSystemNowMicrosUtc();
        sendRequest(Operation.createPatch(state.resourceReference)
                .setBody(toPatch)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(e);
                        patchTaskStage(state, e);
                    } else {
                        createDeploymentStates(state, cc);
                    }
                }));
    }

    private void createDeploymentStates(MockAdapterRequest state, CompositeComponent compositeComponent) {
        URI uri = UriUtils.buildUri(getHost(), compositeComponent.compositeDescriptionLink);
        sendRequest(Operation
                .createGet(uri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failed getting composite component.", e);
                        patchTaskStage(state, e);
                    } else {
                        CompositeDescription compositeDescription = o.getBody(CompositeDescription.class);
                        List<String> descriptionLinks = compositeDescription.descriptionLinks;
                        getKubernetesDescriptions(state, compositeComponent, descriptionLinks);
                    }
                }));
    }

    private void getKubernetesDescriptions(MockAdapterRequest state, CompositeComponent compositeComponent,
                                           List<String> descriptionLinks) {

        List<Operation> getCompositeDescriptions = descriptionLinks.stream()
                .map(link -> Operation.createGet(this, link))
                .collect(Collectors.toList());
        OperationJoin.create(getCompositeDescriptions)
                .setCompletion((ops, errors) -> {
                    if (errors != null) {
                        // Filter not null exceptions, fail the request with first one,
                        // log all others.
                        List<Throwable> throwables = errors.values().stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                        patchTaskStage(state, throwables.get(0));
                        throwables.stream().skip(1)
                                .forEach(e -> logWarning("%s", e.getMessage()));
                    } else {
                        List<KubernetesDescriptionService.KubernetesDescription> descriptions = ops.entrySet().stream()
                                .map(desc -> desc.getValue().getBody(KubernetesDescriptionService.KubernetesDescription.class))
                                .collect(Collectors.toList());

                        validateKubernetesDescriptionTypes(state, compositeComponent, descriptions);

                    }
                }).sendWith(this);
    }

    private void validateKubernetesDescriptionTypes(MockAdapterRequest state, CompositeComponent compositeComponent,
                                                    List<KubernetesDescriptionService.KubernetesDescription> descriptions) {

        String compositeComponentId = UriUtils.getLastPathSegment(compositeComponent.documentSelfLink);

        descriptions = descriptions.stream()
                .map(desc -> KubernetesUtil.setApplicationLabel(desc, compositeComponentId))
                .collect(Collectors.toList());

        processDescriptions(state, compositeComponent, descriptions);
    }

    private void processDescriptions(MockAdapterRequest state, CompositeComponent compositeComponent,
                                          List<KubernetesDescriptionService.KubernetesDescription> descriptions) {

        List<Operation> operations = descriptions.stream()
                .map(description -> createOperationForKubernetesState(compositeComponent, description))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        OperationJoin.create(operations)
                .setCompletion((ops, errors) -> {
                    if (errors != null) {
                        // Filter not null exceptions, fail the request with first one,
                        // log all others.
                        List<Throwable> throwables = errors.values().stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                        patchTaskStage(state, throwables.get(0));
                        throwables.stream().skip(1)
                                .forEach(e -> logWarning("%s", e.getMessage()));
                    } else {
                        patchTaskStage(state, null, null);
                    }
                }).sendWith(this);
    }

    private Operation createOperationForKubernetesState(CompositeComponent compositeComponent,
                                               KubernetesDescriptionService.KubernetesDescription description) {

        if (!description.type.equals(KubernetesUtil.DEPLOYMENT_TYPE)) {
            return null;
        }

        Deployment deployment = new Deployment();
        DeploymentService.DeploymentState deploymentState = new DeploymentService.DeploymentState();
        deploymentState.deployment = deployment;
        deploymentState.name = compositeComponent.name;
        deploymentState.compositeComponentLink = compositeComponent.documentSelfLink;
        deploymentState.tenantLinks = compositeComponent.tenantLinks;
        CREATED_DEPLOYMENT_STATES.add(deploymentState);

        return Operation.createPost(this, ManagementUriParts.KUBERNETES_DEPLOYMENTS)
                .setBody(deploymentState);

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

    public static void addCompositeComponent(CompositeComponent component) {
        PROVISIONED_COMPONENTS.add(component);
    }

    public static List<DeploymentService.DeploymentState> getCreatedDeploymentStates() {
        return new ArrayList<>(CREATED_DEPLOYMENT_STATES);
    }

    public static void addDeploymentState(DeploymentService.DeploymentState state) {
        CREATED_DEPLOYMENT_STATES.add(state);
    }

    public static void clear() {
        PROVISIONED_COMPONENTS.clear();
    }
}
