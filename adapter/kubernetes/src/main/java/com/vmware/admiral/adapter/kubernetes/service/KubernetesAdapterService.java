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

import java.util.logging.Level;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.Container;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.ContainerStatus;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.Pod;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.PodList;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;

public class KubernetesAdapterService extends AbstractKubernetesAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_KUBERNETES;

    private static class RequestContext {
        public AdapterRequest request;
        public ComputeState computeState;
        public ContainerState containerState;
        public ContainerDescription containerDescription;
        public KubernetesContext k8sContext;
        public KubernetesRemoteApiClient executor;
        /**
         * Flags the request as already failed. Used to avoid patching a FAILED task to FINISHED
         * state after inspecting a container.
         */
        public boolean requestFailed;
        /** Only for direct operations like exec */
        public Operation operation;
    }

    @Override
    public void handlePatch(Operation op) {
        RequestContext context = new RequestContext();
        context.request = op.getBody(AdapterRequest.class);
        context.request.validate();

        String operationType = context.request.operationTypeId;
        if (!ContainerOperationType.STATS.id.equals(operationType)
                && !ContainerOperationType.INSPECT.id.equals(operationType)
                && !ContainerOperationType.FETCH_LOGS.id.equals(operationType)) {
            logInfo("Processing operation request %s for resource %s %s",
                    operationType, context.request.resourceReference,
                    context.request.getRequestTrackingLog());
        }

        if (operationType.equals(ContainerOperationType.EXEC.id)) {
            // Exec is direct operation
            context.operation = op;
        } else {
            op.complete();// TODO: can't return the operation if state not persisted.
        }
        processContainerRequest(context);
    }

    private void processContainerRequest(RequestContext context) {
        Operation getContainerState = Operation
                .createGet(context.request.resourceReference)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                        if (context.operation != null) {
                            context.operation.fail(ex);
                        }
                    } else {
                        handleExceptions(context.request, context.operation, () -> {
                            context.containerState = o.getBody(ContainerState.class);
                            processContainerState(context);
                        });
                    }
                });
        handleExceptions(context.request, context.operation, () -> {
            getHost().log(Level.FINE, "Fetching ContainerState: %s %s",
                    context.request.getRequestTrackingLog(),
                    context.request.resourceReference);
            sendRequest(getContainerState);
        });
    }

    private void processContainerState(RequestContext context) {
        if (context.containerState.parentLink == null) {
            fail(context.request, new IllegalArgumentException("parentLink missing"));
            return;
        }

        getContainerHost(
                context.request,
                context.operation,
                context.request.resolve(context.containerState.parentLink),
                (k8sContext) -> {
                    context.k8sContext = k8sContext;
                    context.executor = getApiClient();
                    context.computeState = k8sContext.host;
                    handleExceptions(context.request, context.operation,
                            () -> processOperation(context));
                });
    }

    private void processOperation(RequestContext context) {
        try {
            if (context.request.operationTypeId.equals(ContainerOperationType.CREATE.id)) {
                // before the container is created the image needs to be pulled
                logInfo("Process create image called");
                // processCreateImage(context);
            } else if (context.request.operationTypeId.equals(ContainerOperationType.DELETE.id)) {
                logInfo("Process delete container called");
                // processDeleteContainer(context);
            } else if (context.request.operationTypeId.equals(ContainerOperationType.START.id)) {
                logInfo("Process start container called");
                // processStartContainer(context);
            } else if (context.request.operationTypeId.equals(ContainerOperationType.STOP.id)) {
                logInfo("Process stop container called");
                // processStopContainer(context);
            } else if (context.request.operationTypeId.equals(ContainerOperationType.FETCH_LOGS.id)) {
                logInfo("Process fetch container logs called");
                // processFetchContainerLog(context);
            } else if (context.request.operationTypeId.equals(ContainerOperationType.INSPECT.id)) {
                logInfo("Process inspect container called");
                inspectContainer(context);
            } else if (context.request.operationTypeId.equals(ContainerOperationType.EXEC.id)) {
                logInfo("Process exec container called");
                // execContainer(context);
            } else if (context.request.operationTypeId.equals(ContainerOperationType.STATS.id)) {
                logInfo("Process container stats called");
                // fetchContainerStats(context);
            } else {
                fail(context.request, new IllegalArgumentException(
                        "Unexpected request type: " + context.request.operationTypeId
                                + context.request.getRequestTrackingLog()));
            }
        } catch (Throwable e) {
            fail(context.request, e);
        }
    }

    private void inspectContainer(RequestContext context) {
        getHost().log(Level.FINE, "Executing inspect container: %s %s",
                context.containerState.documentSelfLink, context.request.getRequestTrackingLog());

        if (context.containerState.id == null) {
            if (!context.requestFailed && (context.containerState.powerState == null
                    || context.containerState.powerState.isUnmanaged())) {
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            } else {
                fail(context.request, new IllegalStateException("container id is required"
                        + context.request.getRequestTrackingLog()));
            }
            return;
        }

        context.executor.getPods(context.k8sContext, (o, ex) -> {
            if (ex != null) {
                fail(context.request, o, ex);
            } else {
                handleExceptions(
                        context.request,
                        context.operation,
                        () -> {
                            PodList podList = o.getBody(PodList.class);
                            boolean foundPod = false;
                            String created = null;
                            ContainerStatus interestStatus = null;
                            Container interestContainer = null;
                            if (podList == null || podList.items == null) {
                                patchTaskStage(context.request, TaskStage.FAILED,
                                        new IllegalStateException("No pods exists on the host"));
                                return;
                            }
                            for (Pod pod: podList.items) {
                                if (pod == null || pod.status == null || pod.spec == null ||
                                        pod.status.containerStatuses == null ||
                                        pod.spec.containers == null) {
                                    continue;
                                }
                                for (ContainerStatus status: pod.status.containerStatuses) {
                                    if (status.containerID.equals(context.containerState.id) ||
                                            KubernetesContainerStateMapper.getId(status.containerID)
                                                    .equals(context.containerState.id)) {
                                        foundPod = true;
                                        interestStatus = status;
                                        created = pod.metadata.creationTimestamp;
                                        break;
                                    }
                                }
                                if (foundPod) {
                                    for (Container container: pod.spec.containers) {
                                        if (container.name.equals(interestStatus.name)) {
                                            interestContainer = container;
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }
                            if (interestContainer == null) {
                                String container = context.containerState.name == null ?
                                        context.containerState.id : context.containerState.name;
                                patchTaskStage(context.request, TaskStage.FAILED,
                                        new IllegalStateException(
                                                String.format(
                                                        "Lookup on container '%s' failed: Missing",
                                                        container)));
                                return;
                            }
                            patchContainerState(context.request, context.containerState,
                                    interestContainer, interestStatus, created, context);
                        });
            }
        });
    }

    private void patchContainerState(AdapterRequest request, ContainerState containerState,
            Container container, ContainerStatus status, String created, RequestContext context) {

        // start with a new ContainerState object because we don't want to overwrite with stale data
        ContainerState newContainerState = new ContainerState();
        newContainerState.documentSelfLink = containerState.documentSelfLink;
        newContainerState.documentExpirationTimeMicros = -1; // make sure the expiration is reset.
        newContainerState.adapterManagementReference = containerState.adapterManagementReference;

        KubernetesContainerStateMapper.mapContainer(newContainerState, container, status);

        newContainerState.created = KubernetesContainerStateMapper.parseDate(created);

        getHost().log(Level.INFO, "Patching ContainerState: %s %s",
                containerState.documentSelfLink, request.getRequestTrackingLog());
        sendRequest(Operation
                .createPatch(request.resourceReference)
                .setBody(newContainerState)
                .setCompletion((o, ex) -> {
                    if (!context.requestFailed) {
                        getHost().log(Level.INFO, "Completing request: %s",
                                request.getRequestTrackingLog());
                        patchTaskStage(request, TaskStage.FINISHED, ex);
                    }
                    if (newContainerState.powerState == PowerState.RUNNING) {
                        AdapterRequest containerRequest = new AdapterRequest();
                        containerRequest.operationTypeId = ContainerOperationType.STATS.id;
                        containerRequest.resourceReference = request.resourceReference;
                        containerRequest.serviceTaskCallback = ServiceTaskCallback.createEmpty();

                        RequestContext newContext = new RequestContext();
                        newContext.containerState = newContainerState;
                        newContext.computeState = context.computeState;
                        newContext.containerDescription = context.containerDescription;
                        newContext.request = containerRequest;
                        newContext.k8sContext = context.k8sContext;
                        newContext.executor = context.executor;
                        newContext.operation = context.operation;

                        processOperation(newContext);
                    }

                }));
    }
}
