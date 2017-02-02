/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.kubernetes.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.ContainerStatus;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.Pod;
import com.vmware.admiral.adapter.kubernetes.service.apiobject.PodList;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.ContainerListCallback;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class KubernetesHostAdapterService extends AbstractKubernetesAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_KUBERNETES_HOST;

    private static final String HIDDEN_CUSTOM_PROPERTY_PREFIX = "__";

    private static final String NOT_FOUND_EXCEPTION_MESSAGE = "returned error 404";
    private static final String REQUIRED_PROPERTY_MISSING_MESSAGE =
            "Required request property '%s' is missing.";

    @Override
    public void handlePatch(Operation op) {
        AdapterRequest request = op.getBody(AdapterRequest.class);
        request.validate();

        logFine("Processing host operation request %s", request.getRequestTrackingLog());

        if (request.operationTypeId.equals(ContainerHostOperationType.PING.id)
                && ComputeService.FACTORY_LINK.equals(request.resourceReference.getPath())) {
            directPing(request, op);
        } else if (request.operationTypeId.equals(ContainerHostOperationType.INFO.id)
                && ComputeService.FACTORY_LINK.equals(request.resourceReference.getPath())) {
            directInfo(request, op);
        } else if (request.operationTypeId.equals(ContainerHostOperationType.LIST_CONTAINERS.id)
                && request.serviceTaskCallback.isEmpty()) {
            getContainerHost(request, op, request.resourceReference,
                    (context) -> directListContainers(request, context, op));
        } else if (request.operationTypeId.equals(ContainerHostOperationType.LIST_NETWORKS.id)
                && request.serviceTaskCallback.isEmpty()) {
                // direct list networks
        } else {
            getContainerHost(request, op, request.resourceReference,
                    (context) -> processOperation(request, context));
            op.complete();
        }
    }

    private void processOperation(AdapterRequest request, KubernetesContext context) {
        if (request.operationTypeId.equals(ContainerHostOperationType.VERSION.id)) {
            //doVersion(request, computeState, commandInput);
        } else if (request.operationTypeId.equals(ContainerHostOperationType.INFO.id)) {
            doInfo(request, context);
        } else if (request.operationTypeId.equals(ContainerHostOperationType.PING.id)) {
            doPing(request, context);
        } else if (request.operationTypeId.equals(ContainerHostOperationType.LIST_CONTAINERS.id)) {
            doListContainers(request, context);
        } else if (request.operationTypeId.equals(ContainerHostOperationType.LIST_NETWORKS.id)) {
            //doListNetworks(request, computeState, commandInput);
            logInfo("Simulating list networks");
        } else if (request.operationTypeId.equals(ContainerHostOperationType.STATS.id)) {
            //doStats(request, computeState);
            logInfo("Simulating stats");
        }
    }

    private KubernetesContext getDefaultContext(AdapterRequest request) {
        KubernetesContext context = new KubernetesContext();
        context.customProperties = request.customProperties;
        context.customProperties.putIfAbsent(
                KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME,
                KubernetesHostConstants.KUBERNETES_HOST_DEFAULT_NAMESPACE);
        return context;
    }

    private CompletionHandler getHostPatchCompletionHandler(AdapterRequest request) {
        return (o, ex) -> {
            if (ex != null) {
                fail(request, o, ex);
            } else {
                // TODO: Find out what properties can be get from the kubernetes host and patch with
                // TODO: them. The returned operation body must be changed to pass them.
                // TODO: Now it doesn't add anything to the host properties.
                // @SuppressWarnings("unchecked")
                // Map<String, Object> properties = o.getBody(Map.class);
                patchHostState(request, null,
                        (o1, ex1) -> patchTaskStage(request, TaskStage.FINISHED, ex1));
            }
        };
    }

    private void patchHostState(AdapterRequest request, Map<String, Object> properties,
            Operation.CompletionHandler callback) {
        ComputeState computeState = new ComputeState();

        if (properties != null && !properties.isEmpty()) {
            computeState.customProperties = new HashMap<>();

            properties.entrySet().forEach(entry -> {
                if (!entry.getKey().startsWith(HIDDEN_CUSTOM_PROPERTY_PREFIX)) {
                    computeState.customProperties.put(
                            HIDDEN_CUSTOM_PROPERTY_PREFIX + entry.getKey(),
                            Utils.toJson(entry.getValue()));
                } else {
                    computeState.customProperties.put(entry.getKey(),
                            Utils.toJson(entry.getValue()));
                }
            });

            computeState.customProperties.remove(
                    ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);
        }

        sendRequest(Operation
                .createPatch(request.resourceReference)
                .setBody(computeState)
                .setCompletion(callback));
    }

    private void directPing(AdapterRequest request, Operation op) {
        // URI link to the stored credentials
        KubernetesContext context = getDefaultContext(request);
        directWithCredentials(request, op, (credentials) -> {
            context.credentials = credentials;
            directPing(request, context, op);
        });
    }

    private void directPing(AdapterRequest request, KubernetesContext context, Operation op) {
        updateContext(request, context);

        getApiClient().ping(context, (o, ex) -> {
            if (ex != null) {
                op.fail(ex);
            } else {
                op.complete();
            }
        });
    }

    private void doPing(AdapterRequest request, KubernetesContext context) {
        updateContext(request, context);

        getApiClient().ping(context, (op, ex) -> {
            if (ex != null) {
                fail(request, op, ex);
            } else {
                patchTaskStage(request, TaskStage.FINISHED, null);
            }
        });
    }

    private void directInfo(AdapterRequest request, Operation op) {
        KubernetesContext context = getDefaultContext(request);
        directWithCredentials(request, op, (credentials) -> {
            context.credentials = credentials;
            directInfo(request, context, op);
        });
    }

    private void directInfo(AdapterRequest request, KubernetesContext context, Operation op) {
        updateContext(request, context);

        KubernetesRemoteApiClient c = getApiClient();
        c.createNamespaceIfMissing(context, (o, ex) -> {
            if (ex != null) {
                op.fail(ex);
            } else {
                c.doInfo(context, (op1, ex1) -> {
                    if (ex1 != null) {
                        op.fail(ex1);
                    } else {
                        // TODO: Add properties to the compute state
                        ComputeState computeState = new ComputeState();
                        computeState.customProperties = new HashMap<>();
                        op.setBody(computeState);
                        op.complete();
                    }
                });
            }
        });
    }

    private void doInfo(AdapterRequest request, KubernetesContext context) {
        updateContext(request, context);

        KubernetesRemoteApiClient c = getApiClient();
        c.createNamespaceIfMissing(context, (o, ex) -> {
            if (ex != null) {
                fail(request, ex);
            } else {
                c.doInfo(context, getHostPatchCompletionHandler(request));
            }
        });
    }

    private void directWithCredentials (AdapterRequest request, Operation op,
            Consumer<AuthCredentialsServiceState> callback) {
        String credentialsLink = request.customProperties.get(
                ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME);
        if (credentialsLink == null) {
            callback.accept(null);
        } else {
            sendRequest(Operation
                    .createGet(this, credentialsLink)
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            op.fail(ex);
                        } else {
                            callback.accept(o.getBody(AuthCredentialsServiceState.class));
                        }
                    })
            );
        }
    }

    private void directListContainers(AdapterRequest request, KubernetesContext context,
            Operation op) {
        updateContext(request, context);

        getApiClient().getPods(context, (o, ex) -> {
            if (ex != null) {
                op.fail(ex);
            } else {
                PodList podList = o.getBody(PodList.class);
                ContainerListCallback callbackResponse = containerListCallbackFromPodList(podList);
                callbackResponse.containerHostLink = context.host.documentSelfLink;
                callbackResponse.hostAdapterReference = context.host.adapterManagementReference;

                if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
                    logFine("Collection returned container IDs: %s %s",
                            callbackResponse.containerIdsAndNames.keySet().stream()
                                    .collect(Collectors.toList()),
                            request.getRequestTrackingLog());
                }

                op.setBody(callbackResponse);
                op.complete();
            }
        });
    }

    private void doListContainers(AdapterRequest request, KubernetesContext context) {
        updateContext(request, context);

        getApiClient().getPods(context, (op, ex) -> {
            if (ex != null) {
                fail(request, op, ex);
            } else {
                PodList podList = op.getBody(PodList.class);
                ContainerListCallback callbackResponse = containerListCallbackFromPodList(podList);
                callbackResponse.containerHostLink = context.host.documentSelfLink;
                callbackResponse.hostAdapterReference = context.host.adapterManagementReference;

                if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
                    logFine("Collection returned container IDs: %s %s",
                            callbackResponse.containerIdsAndNames.keySet().stream()
                                    .collect(Collectors.toList()),
                            request.getRequestTrackingLog());
                }

                patchTaskStage(request, TaskStage.FINISHED, null, callbackResponse);
            }
        });
    }

    private ContainerListCallback containerListCallbackFromPodList(PodList podList) {
        ContainerListCallback result = new ContainerListCallback();
        String id = null;
        if (podList.items == null) {
            return result;
        }
        for (Pod pod: podList.items) {
            if (pod.status == null || pod.status.containerStatuses == null) {
                continue;
            }
            for (ContainerStatus status: pod.status.containerStatuses) {
                id = KubernetesContainerStateMapper.getId(status.containerID);
                result.containerIdsAndNames.put(id, status.name);
                result.containerIdsAndImage.put(id, status.image);
            }
        }
        return result;
    }

    private void updateContext(AdapterRequest request, KubernetesContext context) {
        if (request.customProperties != null) {
            context.SSLTrustCertificate = request.customProperties.get(ContainerHostService.SSL_TRUST_CERT_PROP_NAME);
            context.SSLTrustAlias = request.customProperties.get(ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME);
            context.host.address = request.customProperties.get(ComputeConstants.HOST_URI_PROP_NAME);
            if (context.host.customProperties == null) {
                context.host.customProperties = request.customProperties;
            }
            if (context.host.address == null || context.host.address.isEmpty()) {
                logWarning(REQUIRED_PROPERTY_MISSING_MESSAGE, ComputeConstants.HOST_URI_PROP_NAME);
            }
        }
    }
}
