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

import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.DEPLOYMENT_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.POD_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.REPLICATION_CONTROLLER_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.REPLICA_SET_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.SERVICE_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.createEntityData;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.adapter.kubernetes.KubernetesRemoteApiClient;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.KubernetesEntityDataCollection.EntityListCallback;
import com.vmware.admiral.compute.kubernetes.KubernetesEntityDataCollection.KubernetesEntityData;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.admiral.compute.kubernetes.entities.deployments.Deployment;
import com.vmware.admiral.compute.kubernetes.entities.deployments.DeploymentList;
import com.vmware.admiral.compute.kubernetes.entities.pods.Pod;
import com.vmware.admiral.compute.kubernetes.entities.pods.PodList;
import com.vmware.admiral.compute.kubernetes.entities.replicaset.ReplicaSet;
import com.vmware.admiral.compute.kubernetes.entities.replicaset.ReplicaSetList;
import com.vmware.admiral.compute.kubernetes.entities.replicationcontrollers.ReplicationController;
import com.vmware.admiral.compute.kubernetes.entities.replicationcontrollers.ReplicationControllerList;
import com.vmware.admiral.compute.kubernetes.entities.services.Service;
import com.vmware.admiral.compute.kubernetes.entities.services.ServiceList;
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

    private static final String REQUIRED_PROPERTY_MISSING_MESSAGE = "Required request property '%s' is missing.";

    private static final String DASHBOARD_SERVICE_NAME = "kubernetes-dashboard";
    public static final String DASHBOARD_LINK_PROP_NAME = "__dashboardLink";
    public static final String DASHBOARD_INSTALLED_PROP_NAME = "__dashboardInstalled";

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
        } else if (request.operationTypeId.equals(ContainerHostOperationType.LIST_ENTITIES.id)
                && request.serviceTaskCallback.isEmpty()) {
            getComputeHost(request, op, request.resourceReference,
                    context -> listEntities(request, context, op, direct));
        } else {
            getComputeHost(request, op, request.resourceReference,
                    context -> processOperation(request, context));
            op.complete();
        }
    }

    private void processOperation(AdapterRequest request, KubernetesContext context) {
        if (request.operationTypeId.equals(ContainerHostOperationType.INFO.id)) {
            doInfo(request, context);
        } else if (request.operationTypeId.equals(ContainerHostOperationType.PING.id)) {
            doPing(request, context);
        } else if (request.operationTypeId.equals(ContainerHostOperationType.LIST_ENTITIES.id)) {
            listEntities(request, context, null, withCallback);
        } else {
            logWarning("Operation [%s] not supported.", request.operationTypeId);
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
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (o != null) ? o.getBody(Map.class)
                        : Collections.emptyMap();
                patchHostState(request, properties,
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

    private CallbackHandler direct = new CallbackHandler() {
        @Override
        public void complete(AdapterRequest request, Operation op, EntityListCallback body) {
            op.setBody(body);
            op.complete();
        }

        @Override
        public void fail(AdapterRequest request, Operation op, Throwable ex) {
            op.fail(ex);
        }
    };

    private CallbackHandler withCallback = new CallbackHandler() {
        @Override
        public void complete(AdapterRequest request, Operation op, EntityListCallback callback) {
            patchTaskStage(request, TaskStage.FINISHED, null, callback);
        }

        @Override
        public void fail(AdapterRequest request, Operation op, Throwable ex) {
            KubernetesHostAdapterService.this.fail(request, op, ex);
        }
    };

    private void directPing(AdapterRequest request, Operation op) {
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
                        ComputeState computeState = new ComputeState();
                        if (op1 != null) {
                            computeState.customProperties = op1.getBody(Map.class);
                            op.setBody(computeState);
                        }
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

    private void directWithCredentials(AdapterRequest request, Operation op,
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
                    }));
        }
    }

    private void listEntities(AdapterRequest request, KubernetesContext context, Operation op,
            CallbackHandler callbackHandler) {
        updateContext(request, context);

        EntityListCallback callbackResponse = new EntityListCallback();
        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicBoolean allStarted = new AtomicBoolean(false);
        AtomicInteger resultCount = new AtomicInteger(0);
        ResultHandler resultHandler = (logic) -> {
            resultCount.incrementAndGet();
            return (o, ex) -> {
                if (ex != null) {
                    logWarning("Listing operation [%s] failed: %s",
                            o == null ? "null" : o.getUri().toString(), ex.toString());
                    if (hasError.compareAndSet(false, true)) {
                        callbackHandler.fail(request, op, ex);
                    }
                } else {
                    logic.accept(o);
                    if (resultCount.decrementAndGet() == 0 && allStarted.get()) {
                        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
                            logFine("Collection returned entity IDs: %s %s",
                                    callbackResponse.idToEntityData.keySet().stream()
                                            .collect(Collectors.toList()),
                                    request.getRequestTrackingLog());
                        }

                        callbackHandler.complete(request, op, callbackResponse);
                    }
                }
            };
        };

        callbackResponse.computeHostLink = context.host.documentSelfLink;
        KubernetesRemoteApiClient client = getApiClient();

        client.getPods(context, null, resultHandler.appendResult((o) -> {
            PodList podList = o.getBody(PodList.class);
            if (podList.items != null) {
                for (Pod pod : podList.items) {
                    if (validateKubernetesObject(pod)) {
                        KubernetesEntityData data = createEntityData(pod, POD_TYPE);
                        callbackResponse.idToEntityData.put(pod.metadata.uid, data);
                    }
                }
            }
        }));
        client.getServices(context, null, resultHandler.appendResult(o -> {
            ServiceList serviceList = o.getBody(ServiceList.class);
            if (serviceList.items != null) {
                for (Service service : serviceList.items) {
                    if (validateKubernetesObject(service)) {
                        KubernetesEntityData data = createEntityData(service, SERVICE_TYPE);
                        callbackResponse.idToEntityData.put(service.metadata.uid, data);
                    }
                }

            }
        }));
        client.getSystemServices(context, null, resultHandler.appendResult(o -> {
            ServiceList serviceList = o.getBody(ServiceList.class);
            if (serviceList.items != null) {
                List<Service> dashboardServices = serviceList.items.stream()
                        .filter(s -> DASHBOARD_SERVICE_NAME.equals(s.metadata.name))
                        .collect(Collectors.toList());

                updateDashboardLink(context.host,
                        dashboardServices.isEmpty() ? null : dashboardServices.get(0));

            }
        }));
        client.getDeployments(context, null, resultHandler.appendResult(o -> {
            DeploymentList deploymentList = o.getBody(DeploymentList.class);
            if (deploymentList.items != null) {
                for (Deployment deployment : deploymentList.items) {
                    if (validateKubernetesObject(deployment)) {
                        KubernetesEntityData data = createEntityData(deployment,
                                DEPLOYMENT_TYPE);
                        callbackResponse.idToEntityData.put(deployment.metadata.uid, data);
                    }
                }
            }
        }));
        client.getReplicationControllers(context, null, resultHandler.appendResult(o -> {
            ReplicationControllerList rcList = o.getBody(ReplicationControllerList.class);
            if (rcList.items != null) {
                for (ReplicationController rc : rcList.items) {
                    if (validateKubernetesObject(rc)) {
                        KubernetesEntityData data = createEntityData(rc,
                                REPLICATION_CONTROLLER_TYPE);
                        callbackResponse.idToEntityData.put(rc.metadata.uid, data);
                    }
                }
            }
        }));
        client.getReplicaSets(context, null, resultHandler.appendResult(o -> {
            ReplicaSetList rsList = o.getBody(ReplicaSetList.class);
            if (rsList.items != null) {
                for (ReplicaSet rs : rsList.items) {
                    if (validateKubernetesObject(rs)) {
                        KubernetesEntityData data = createEntityData(rs, REPLICA_SET_TYPE);
                        callbackResponse.idToEntityData.put(rs.metadata.uid, data);
                    }
                }
            }
        }));
        allStarted.set(true);
    }

    private void updateDashboardLink(ComputeState clusterHost, Service dashboardService) {
        ComputeState patchState = new ComputeState();
        patchState.customProperties = new HashMap<>();

        if (dashboardService != null) {
            patchState.customProperties.put(DASHBOARD_LINK_PROP_NAME,
                    KubernetesUtil.constructDashboardLink(clusterHost, dashboardService));
            patchState.customProperties.put(DASHBOARD_INSTALLED_PROP_NAME,
                    Boolean.TRUE.toString());
        } else {
            patchState.customProperties.put(DASHBOARD_INSTALLED_PROP_NAME,
                    Boolean.FALSE.toString());
        }

        Operation.createPatch(this, clusterHost.documentSelfLink)
                .setBody(patchState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failed to patch compute state with dashboard link: %s",
                                Utils.toString(e));
                    }
                }).sendWith(this);
    }

    private void updateContext(AdapterRequest request, KubernetesContext context) {
        if (request.customProperties != null) {
            context.SSLTrustCertificate = request.customProperties
                    .get(ContainerHostService.SSL_TRUST_CERT_PROP_NAME);
            context.SSLTrustAlias = request.customProperties
                    .get(ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME);
            context.host.address = request.customProperties
                    .get(ComputeConstants.HOST_URI_PROP_NAME);
            if (context.host.customProperties == null) {
                context.host.customProperties = request.customProperties;
            }
            if (context.host.address == null || context.host.address.isEmpty()) {
                logWarning(REQUIRED_PROPERTY_MISSING_MESSAGE, ComputeConstants.HOST_URI_PROP_NAME);
            }
        }
    }

    /**
     * This interface is used to synchronize the result of asynchronous GET requests.
     */
    private interface ResultHandler {
        CompletionHandler appendResult(Consumer<Operation> param);
    }

    /**
     * This interface is used to pass different completion functionality as a parameter.
     */
    private interface CallbackHandler {
        void complete(AdapterRequest r, Operation o, EntityListCallback c);

        void fail(AdapterRequest r, Operation o, Throwable e);
    }

    private boolean validateKubernetesObject(BaseKubernetesObject object) {
        if (object.metadata == null || object.metadata.selfLink == null
                || object.metadata.name == null) {
            return false;
        }
        return true;
    }
}
