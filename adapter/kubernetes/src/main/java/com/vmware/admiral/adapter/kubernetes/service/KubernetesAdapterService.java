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

import java.io.IOException;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.KubernetesOperationType;
import com.vmware.admiral.adapter.kubernetes.KubernetesRemoteApiClient;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.content.kubernetes.CommonKubernetesEntity;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.service.KubernetesService.KubernetesState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;

public class KubernetesAdapterService extends AbstractKubernetesAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_KUBERNETES;

    private static class RequestContext {
        public AdapterRequest request;
        public ComputeState computeState;
        public KubernetesState kubernetesState;
        public KubernetesDescription kubernetesDescription;
        public KubernetesContext k8sContext;
        public KubernetesRemoteApiClient executor;
        /**
         * Flags the request as already failed. Used to avoid patching a FAILED task to FINISHED
         * state after inspecting a container.
         */
        public boolean requestFailed;
    }

    @Override
    public void handlePatch(Operation op) {
        RequestContext context = new RequestContext();
        context.request = op.getBody(AdapterRequest.class);
        context.request.validate();

        KubernetesOperationType operationType = KubernetesOperationType.instanceById(context.request
                .operationTypeId);

        op.complete();

        logInfo("Processing kubernetes operation request %s for resource %s.",
                operationType, context.request.resourceReference);

        processKubernetesRequest(context);
    }

    private void processKubernetesRequest(RequestContext context) {
        sendRequest(Operation
                .createGet(context.request.resourceReference)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        context.kubernetesState = o.getBody(KubernetesState.class);
                        processKubernetesState(context);
                    }
                }));
    }

    private void processKubernetesState(RequestContext context) {
        if (context.kubernetesState.parentLink == null) {
            fail(context.request, new IllegalArgumentException("parentLink missing"));
            return;
        }

        getContainerHost(
                context.request,
                null,
                context.request.resolve(context.kubernetesState.parentLink),
                (k8sContext) -> {
                    context.k8sContext = k8sContext;
                    context.executor = getApiClient();
                    context.computeState = k8sContext.host;

                    processOperation(context);
                });
    }

    private void processOperation(RequestContext context) {
        try {
            KubernetesOperationType operationType = KubernetesOperationType.instanceById(context
                    .request.operationTypeId);
            switch (operationType) {
            case CREATE:
                getKubernetesDescription(context);
                break;

            case DELETE:
                processDeleteKubernetesEntity(context);
                break;

            default:
                fail(context.request,
                        new IllegalArgumentException("Unexpected request type: " + operationType));
            }
        } catch (Throwable e) {
            fail(context.request, e);
        }
    }

    private void getKubernetesDescription(RequestContext context) {
        sendRequest(Operation
                .createGet(this, context.kubernetesState.descriptionLink)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        context.kubernetesDescription = o.getBody(KubernetesDescription.class);
                        processCreateKubernetesEntity(context);
                    }
                })
        );
    }

    private void processCreateKubernetesEntity(RequestContext context) {
        try {
            context.executor.createEntity(context.kubernetesDescription, context.k8sContext,
                    (o, ex) -> {
                        if (ex != null) {
                            fail(context.request, ex);
                        } else {
                            String createdKubernetesEntity = o.getBody(String.class);
                            patchKubernetesState(context, createdKubernetesEntity);
                        }
                    });
        } catch (IOException ex) {
            fail(context.request, ex);
        }
    }

    private void patchKubernetesState(RequestContext context, String createdKubernetesEntity) {

        KubernetesState newKubernetesState = new KubernetesState();
        try {
            newKubernetesState.kubernetesEntity = createdKubernetesEntity;
            CommonKubernetesEntity entity = newKubernetesState.getKubernetesEntity
                    (CommonKubernetesEntity.class);
            newKubernetesState.type = entity.kind;
            newKubernetesState.selfLink = entity.metadata.selfLink;
            newKubernetesState.namespace = entity.metadata.namespace;
        } catch (Throwable ex) {
            fail(context.request, ex);
        }

        sendRequest(Operation
                .createPatch(this, context.kubernetesState.documentSelfLink)
                .setBody(newKubernetesState)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        patchTaskStage(context.request, TaskStage.FINISHED, null);
                    }
                }));
    }

    private void processDeleteKubernetesEntity(RequestContext context) {
        context.executor.deleteEntity(context.kubernetesState.selfLink, context.k8sContext,
                (o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        patchTaskStage(context.request, TaskStage.FINISHED, null);
                    }
                });
    }

}
