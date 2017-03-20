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

import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.fromResourceStateToBaseKubernetesState;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.KubernetesOperationType;
import com.vmware.admiral.adapter.kubernetes.KubernetesRemoteApiClient;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.entities.pods.Container;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.service.PodService;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.LogService.LogServiceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.TaskState.TaskStage;

public class KubernetesAdapterService extends AbstractKubernetesAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_KUBERNETES;

    private static final String LOG_FETCH_FAILED_FORMAT = "Unable to fetch logs for container: %s"
            + " error: %s";

    private static class RequestContext {
        public AdapterRequest request;
        public BaseKubernetesState kubernetesState;
        public KubernetesDescription kubernetesDescription;
        public KubernetesContext k8sContext;
        public KubernetesRemoteApiClient executor;
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
                        Class<? extends BaseKubernetesState> stateType = null;
                        try {
                            stateType = fromResourceStateToBaseKubernetesState(
                                    CompositeComponentRegistry.metaByStateLink(
                                            context.request.resourceReference
                                                    .getPath()).stateClass);
                            context.kubernetesState = o.getBody(stateType);
                            processKubernetesState(context);
                        } catch (IllegalArgumentException iae) {
                            fail(context.request, iae);
                        }
                    }
                }));
    }

    private void processKubernetesState(RequestContext context) {
        if (context.kubernetesState.parentLink == null) {
            fail(context.request, new IllegalArgumentException("parentLink missing"));
            return;
        }

        getComputeHost(
                context.request,
                null,
                context.request.resolve(context.kubernetesState.parentLink),
                (k8sContext) -> {
                    context.k8sContext = k8sContext;
                    context.executor = getApiClient();

                    processOperation(context);
                });
    }

    private void processOperation(RequestContext context) {
        try {
            KubernetesOperationType operationType = KubernetesOperationType.instanceById(context
                    .request.operationTypeId);
            switch (operationType) {
            case CREATE:
                fail(context.request,
                        new IllegalArgumentException("Unsupported request type: " + operationType));
                break;

            case DELETE:
                processDeleteKubernetesEntity(context);
                break;

            case FETCH_LOGS:
                processFetchPodLogs(context);
                break;

            case INSPECT:
                inspectKubernetesEntity(context);
                break;

            default:
                fail(context.request,
                        new IllegalArgumentException("Unexpected request type: " + operationType));
            }
        } catch (Throwable e) {
            fail(context.request, e);
        }
    }

    private void processDeleteKubernetesEntity(RequestContext context) {
        context.executor
                .deleteEntity(context.kubernetesState.kubernetesSelfLink, context.k8sContext,
                        (o, ex) -> {
                            if (ex != null) {
                                fail(context.request, ex);
                            } else {
                                patchTaskStage(context.request, TaskStage.FINISHED, null);
                            }
                        });
    }

    private void processFetchPodLogs(RequestContext context) {
        if (!context.kubernetesState.documentSelfLink.startsWith(PodService.FACTORY_LINK)) {
            throw new IllegalArgumentException("Cannot fetch logs for types that are not pods.");
        }

        PodState podState = (PodState) context.kubernetesState;

        Map<String, String> containerNamesToLogLinks = new HashMap<>();
        for (Container container : podState.pod.spec.containers) {
            String logLink = podState.kubernetesSelfLink + "/log?container=" + container.name;
            containerNamesToLogLinks.put(container.name, logLink);
        }

        Map<String, String> containerNameToLogOutput = new ConcurrentHashMap<>();

        AtomicInteger counter = new AtomicInteger(containerNamesToLogLinks.size());

        for (Entry<String, String> containerNameToLogLink : containerNamesToLogLinks.entrySet()) {
            String name = containerNameToLogLink.getKey();
            String logLink = containerNameToLogLink.getValue();
            context.executor.fetchLogs(logLink, context.k8sContext, (o, ex) -> {
                if (ex != null) {
                    containerNameToLogOutput.put(name, String.format(LOG_FETCH_FAILED_FORMAT,
                            name, ex.getMessage()));
                } else {
                    String log = o.getBody(String.class);
                    if (log == null || log.isEmpty()) {
                        log = "--";
                    }
                    containerNameToLogOutput.put(name, log);
                }
                if (counter.decrementAndGet() == 0) {
                    processFetchedLogs(context, containerNameToLogOutput);
                }
            });
        }

    }

    private void processFetchedLogs(RequestContext context, Map<String, String> logs) {
        AtomicInteger counter = new AtomicInteger(logs.size());
        AtomicBoolean hasError = new AtomicBoolean(false);

        for (Entry<String, String> log : logs.entrySet()) {
            LogServiceState logServiceState = new LogServiceState();
            logServiceState.documentSelfLink = KubernetesUtil.buildLogUriPath(context
                    .kubernetesState, log.getKey());
            logServiceState.logs = log.getValue().getBytes();
            logServiceState.tenantLinks = context.kubernetesState.tenantLinks;

            sendRequest(Operation.createPost(this, LogService.FACTORY_LINK)
                    .setBody(logServiceState)
                    .setContextId(context.request.getRequestId())
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            if (hasError.compareAndSet(false, true)) {
                                fail(context.request, ex);
                            }
                        } else {
                            if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                patchTaskStage(context.request, TaskStage.FINISHED, null);
                            }
                        }
                    }));
        }
    }

    private void inspectKubernetesEntity(RequestContext context) {
        context.executor.inspectEntity(context.kubernetesState.kubernetesSelfLink,
                context.k8sContext, (o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        patchKubernetesEntity(context, o, (op, err) -> {
                            if (err != null) {
                                fail(context.request, err);
                            } else {
                                patchTaskStage(context.request, TaskStage.FINISHED, null);
                            }
                        });
                    }
                });
    }

    private void patchKubernetesEntity(RequestContext context, Operation inspectResponse,
            CompletionHandler handler) {
        String jsonResponse = inspectResponse.getBody(String.class);
        BaseKubernetesState newState = null;
        try {
            newState = context.kubernetesState.getClass().newInstance();
            newState.setKubernetesEntityFromJson(jsonResponse);
        } catch (Throwable ex) {
            fail(context.request, ex);
        }
        sendRequest(Operation.createPatch(this, context.kubernetesState.documentSelfLink)
                .setBody(newState)
                .setCompletion(handler));
    }
}
