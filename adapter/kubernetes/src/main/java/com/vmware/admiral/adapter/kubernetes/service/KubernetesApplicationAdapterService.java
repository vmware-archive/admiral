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

package com.vmware.admiral.adapter.kubernetes.service;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent.CUSTOM_PROPERTY_HOST_LINK;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.DEPLOYMENT_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.POD_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.REPLICATION_CONTROLLER_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.SERVICE_TYPE;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.ApplicationOperationType;
import com.vmware.admiral.adapter.common.ApplicationRequest;
import com.vmware.admiral.adapter.kubernetes.KubernetesRemoteApiClient;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
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
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService.DeploymentState;
import com.vmware.admiral.compute.kubernetes.service.GenericKubernetesEntityService;
import com.vmware.admiral.compute.kubernetes.service.GenericKubernetesEntityService.GenericKubernetesEntityState;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.service.PodService;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.compute.kubernetes.service.ReplicaSetService;
import com.vmware.admiral.compute.kubernetes.service.ReplicaSetService.ReplicaSetState;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService.ReplicationControllerState;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler.ServiceState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class KubernetesApplicationAdapterService extends AbstractKubernetesAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION;

    private static class RequestContext {
        public ApplicationRequest request;
        public CompositeDescription compositeDescription;
        public CompositeComponent compositeComponent;
        public KubernetesContext kubernetesContext;
        public KubernetesRemoteApiClient client;
    }

    @Override
    public void handlePatch(Operation op) {
        RequestContext context = new RequestContext();
        context.request = op.getBody(ApplicationRequest.class);
        context.request.validate();

        ApplicationOperationType operationType = context.request.getOperationType();

        logInfo("Processing application operation request %s for resource %s", operationType,
                context.request.resourceReference);

        op.complete();

        processRequest(context);
    }

    public void processRequest(RequestContext context) {
        // Get the CompositeComponent.
        sendRequest(Operation
                .createGet(context.request.getCompositeComponentReference())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        handleExceptions(context.request, null, () -> {
                            context.compositeComponent = o.getBody(CompositeComponent.class);
                            createKubernetesContext(context);
                        });
                    }
                }));
    }

    public void createKubernetesContext(RequestContext context) {
        assertNotNull(context.compositeComponent.customProperties,
                "compositeComponent.customProperties.");

        CompositeComponent component = context.compositeComponent;

        if (!component.customProperties.containsKey(CUSTOM_PROPERTY_HOST_LINK)) {
            fail(context.request, new IllegalStateException("Composite component is missing "
                    + "custom property with host link"));
            return;
        }

        String hostLink = component.customProperties.get(CUSTOM_PROPERTY_HOST_LINK);

        // Get the kubernetes context and the api client.
        getComputeHost(
                context.request,
                null,
                context.request.resolve(hostLink),
                (k8sContext) -> {
                    context.kubernetesContext = k8sContext;
                    context.client = getApiClient();
                    handleExceptions(context.request, null,
                            () -> processOperation(context));
                });
    }

    private void processOperation(RequestContext context) {
        try {
            switch (context.request.getOperationType()) {
            case CREATE:
                processCompositeComponent(context);
                break;

            case DELETE:
                processApplicationDelete(context);
                break;

            default:
                fail(context.request, new IllegalArgumentException(
                        "Unexpected request type: " + context.request.getOperationType()));
            }
        } catch (Throwable e) {
            fail(context.request, e);
        }
    }

    public void processCompositeComponent(RequestContext context) {
        // Get the CompositeDescription and process it further.
        sendRequest(Operation
                .createGet(this, context.compositeComponent.compositeDescriptionLink)
                .setCompletion((o2, e2) -> {
                    if (e2 != null) {
                        fail(context.request, e2);
                    } else {
                        handleExceptions(context.request, null, () -> {
                            context.compositeDescription = o2.getBody(CompositeDescription.class);
                            processCreateApplication(context);
                        });
                    }
                }));

    }

    private void processCreateApplication(RequestContext context) {
        List<String> descriptionLinks = context.compositeDescription.descriptionLinks
                .stream()
                .filter(link -> link.startsWith(KubernetesDescriptionService.FACTORY_LINK))
                .collect(Collectors.toList());

        getKubernetesDescriptions(context, descriptionLinks);

    }

    private void getKubernetesDescriptions(RequestContext context, List<String> descriptionLinks) {
        List<Operation> getKubernetesDescriptions = descriptionLinks.stream()
                .map(link -> Operation.createGet(this, link))
                .collect(Collectors.toList());

        OperationJoin.create(getKubernetesDescriptions)
                .setCompletion((ops, errors) -> {
                    if (errors != null) {
                        // Filter not null exceptions, fail the request with first one,
                        // log all others.
                        List<Throwable> throwables = errors.values().stream()
                                .filter(e -> e != null)
                                .collect(Collectors.toList());
                        fail(context.request, throwables.get(0));
                        throwables.stream().skip(1)
                                .forEach(e -> logWarning("%s", e.getMessage()));
                    } else {
                        List<KubernetesDescription> descriptions = ops.entrySet().stream()
                                .map(desc -> desc.getValue().getBody(KubernetesDescription.class))
                                .collect(Collectors.toList());

                        validateKubernetesDescriptionTypes(context, descriptions);

                    }
                }).sendWith(this);
    }

    private void validateKubernetesDescriptionTypes(RequestContext context,
            List<KubernetesDescription> descriptions) {

        String compositeComponentId = UriUtils.getLastPathSegment(context.compositeComponent
                .documentSelfLink);

        descriptions = descriptions.stream()
                .map(desc -> KubernetesUtil.setApplicationLabel(desc, compositeComponentId))
                .collect(Collectors.toList());

        List<KubernetesDescription> serviceDescriptions = descriptions.stream()
                .filter(d -> SERVICE_TYPE.equals(d.type)).collect(Collectors.toList());

        List<KubernetesDescription> otherDescriptions = descriptions.stream()
                .filter(d -> !SERVICE_TYPE.endsWith(d.type)).collect(Collectors.toList());

        try {
            processServiceDescriptions(context, serviceDescriptions,
                    () -> {
                        try {
                            processOtherDescriptions(context, otherDescriptions);
                        } catch (IOException e) {
                            fail(context.request, e);
                        }
                    });
        } catch (IOException e) {
            fail(context.request, e);
        }
    }

    private void processServiceDescriptions(RequestContext context,
            List<KubernetesDescription> serviceDescriptions, Runnable callback) throws IOException {

        if (serviceDescriptions.isEmpty()) {
            callback.run();
        }

        final AtomicInteger counter = new AtomicInteger(serviceDescriptions.size());
        final AtomicBoolean hasError = new AtomicBoolean(false);

        for (KubernetesDescription description : serviceDescriptions) {
            context.client.createEntity(description, context.kubernetesContext, (o, ex) -> {
                if (ex != null) {
                    if (hasError.compareAndSet(false, true)) {
                        fail(context.request, ex);
                    } else {
                        logWarning("Failure creating kubernetes entity: %s", Utils.toString(ex));
                    }
                } else {
                    createSpecificKubernetesState(context, description, o,
                            (o1, ex1) -> {
                                if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                    callback.run();
                                }
                            });

                }
            });
        }
    }

    private void processOtherDescriptions(RequestContext context,
            List<KubernetesDescription> descriptions) throws IOException {

        final AtomicInteger counter = new AtomicInteger(descriptions.size());
        final AtomicBoolean hasError = new AtomicBoolean(false);

        for (KubernetesDescription description : descriptions) {
            context.client.createEntity(description, context.kubernetesContext, (o, ex) -> {
                if (ex != null) {
                    if (hasError.compareAndSet(false, true)) {
                        fail(context.request, ex);
                    } else {
                        logWarning("Failure creating kubernetes entity: %s", Utils.toString(ex));
                    }
                } else {
                    createSpecificKubernetesState(context, description, o,
                            (o1, ex1) -> {
                                if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                    startEntityDiscovery(context);
                                }
                            });

                }
            });
        }
    }

    private void startEntityDiscovery(RequestContext context) {

        AtomicInteger parallelDiscoveryCounter = new AtomicInteger(5);
        AtomicBoolean hasError = new AtomicBoolean(false);

        Consumer<Throwable> failureCallback = (ex) -> {
            if (hasError.compareAndSet(false, true)) {
                fail(context.request, ex);
            }
        };

        Runnable successfulCallback = () -> {
            if (parallelDiscoveryCounter.decrementAndGet() == 0 && !hasError.get()) {
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            }
        };

        discoverDeployments(context, failureCallback, successfulCallback);
        discoverReplicationControllers(context, failureCallback,
                successfulCallback);
        discoverServices(context, failureCallback, successfulCallback);
        discoverReplicaSets(context, failureCallback, successfulCallback);
        discoverPods(context, failureCallback, successfulCallback);

        // TODO discover generic entities
    }

    private void discoverDeployments(RequestContext context, Consumer<Throwable> failureCallback,
            Runnable successfulCallback) {
        String compositeComponentId = UriUtils.getLastPathSegment(context.compositeComponent
                .documentSelfLink);

        context.client.getDeployments(context.kubernetesContext, compositeComponentId, (o, ex) -> {
            if (ex != null) {
                failureCallback.accept(ex);
            } else {
                DeploymentList deployments = o.getBody(DeploymentList.class);
                if (deployments.items == null || deployments.items.isEmpty()) {
                    successfulCallback.run();
                    return;
                }
                AtomicInteger counter = new AtomicInteger(deployments.items.size());
                AtomicBoolean hasError = new AtomicBoolean(false);
                for (Deployment deployment : deployments.items) {
                    DeploymentState deploymentState = new DeploymentState();
                    deploymentState.deployment = deployment;
                    deploymentState.name = deployment.metadata.name;
                    deploymentState.compositeComponentLink = context.compositeComponent.documentSelfLink;
                    deploymentState.parentLink = context.kubernetesContext.host.documentSelfLink;
                    deploymentState.documentSelfLink = deployment.metadata.uid;
                    deploymentState.id = deployment.metadata.uid;
                    deploymentState.kubernetesSelfLink = deployment.metadata.selfLink;
                    sendRequest(Operation.createPost(this, DeploymentService.FACTORY_LINK)
                            .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                            .setBody(deploymentState)
                            .setCompletion((op, err) -> {
                                if (err != null) {
                                    if (hasError.compareAndSet(false, true)) {
                                        failureCallback.accept(err);
                                    } else {
                                        logWarning("Failure creating kubernetes entity: %s",
                                                Utils.toString(err));
                                    }
                                } else {
                                    if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                        successfulCallback.run();
                                    }
                                }
                            }));
                }
            }
        });
    }

    private void discoverReplicationControllers(RequestContext context,
            Consumer<Throwable> failureCallback, Runnable successfulCallback) {
        String compositeComponentId = UriUtils.getLastPathSegment(context.compositeComponent
                .documentSelfLink);

        context.client.getReplicationControllers(context.kubernetesContext, compositeComponentId,
                (o, ex) -> {
                    if (ex != null) {
                        failureCallback.accept(ex);
                    } else {
                        ReplicationControllerList controllers = o
                                .getBody(ReplicationControllerList.class);
                        if (controllers.items == null || controllers.items.isEmpty()) {
                            successfulCallback.run();
                            return;
                        }
                        AtomicInteger counter = new AtomicInteger(controllers.items.size());
                        AtomicBoolean hasError = new AtomicBoolean(false);
                        for (ReplicationController controller : controllers.items) {
                            ReplicationControllerState controllerState = new ReplicationControllerState();
                            controllerState.replicationController = controller;
                            controllerState.name = controller.metadata.name;
                            controllerState.compositeComponentLink = context.compositeComponent.documentSelfLink;
                            controllerState.parentLink = context.kubernetesContext.host.documentSelfLink;
                            controllerState.documentSelfLink = controller.metadata.uid;
                            controllerState.id = controller.metadata.uid;
                            controllerState.kubernetesSelfLink = controller.metadata.selfLink;
                            sendRequest(Operation
                                    .createPost(this, ReplicationControllerService.FACTORY_LINK)
                                    .addPragmaDirective(
                                            Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                                    .setBody(controllerState)
                                    .setCompletion((op, err) -> {
                                        if (err != null) {
                                            if (hasError.compareAndSet(false, true)) {
                                                failureCallback.accept(err);
                                            } else {
                                                logWarning("Failure creating kubernetes entity: %s",
                                                        Utils.toString(err));
                                            }
                                        } else {
                                            if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                                successfulCallback.run();
                                            }
                                        }
                                    }));
                        }
                    }
                });
    }

    private void discoverServices(RequestContext context, Consumer<Throwable> failureCallback,
            Runnable successfulCallback) {
        String compositeComponentId = UriUtils.getLastPathSegment(context.compositeComponent
                .documentSelfLink);

        context.client.getServices(context.kubernetesContext, compositeComponentId, (o, ex) -> {
            if (ex != null) {
                failureCallback.accept(ex);
            } else {
                ServiceList services = o.getBody(ServiceList.class);
                if (services.items == null || services.items.isEmpty()) {
                    successfulCallback.run();
                    return;
                }
                AtomicInteger counter = new AtomicInteger(services.items.size());
                AtomicBoolean hasError = new AtomicBoolean(false);
                for (Service service : services.items) {
                    ServiceState serviceState = new ServiceState();
                    serviceState.service = service;
                    serviceState.name = service.metadata.name;
                    serviceState.compositeComponentLink = context.compositeComponent.documentSelfLink;
                    serviceState.parentLink = context.kubernetesContext.host.documentSelfLink;
                    serviceState.documentSelfLink = service.metadata.uid;
                    serviceState.id = service.metadata.uid;
                    serviceState.kubernetesSelfLink = service.metadata.selfLink;
                    sendRequest(Operation.createPost(this, ServiceEntityHandler.FACTORY_LINK)
                            .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                            .setBody(serviceState)
                            .setCompletion((op, err) -> {
                                if (err != null) {
                                    if (hasError.compareAndSet(false, true)) {
                                        failureCallback.accept(err);
                                    } else {
                                        logWarning("Failure creating kubernetes entity: %s",
                                                Utils.toString(err));
                                    }
                                } else {
                                    if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                        successfulCallback.run();
                                    }
                                }
                            }));
                }
            }
        });
    }

    private void discoverReplicaSets(RequestContext context, Consumer<Throwable> failureCallback,
            Runnable successfulCallback) {
        String compositeComponentId = UriUtils.getLastPathSegment(context.compositeComponent
                .documentSelfLink);

        context.client.getReplicaSets(context.kubernetesContext, compositeComponentId, (o, ex) -> {
            if (ex != null) {
                failureCallback.accept(ex);
            } else {
                ReplicaSetList replicas = o.getBody(ReplicaSetList.class);
                if (replicas.items == null || replicas.items.isEmpty()) {
                    successfulCallback.run();
                    return;
                }
                AtomicInteger counter = new AtomicInteger(replicas.items.size());
                AtomicBoolean hasError = new AtomicBoolean(false);
                for (ReplicaSet replicaSet : replicas.items) {
                    ReplicaSetState replicaSetState = new ReplicaSetState();
                    replicaSetState.replicaSet = replicaSet;
                    replicaSetState.name = replicaSet.metadata.name;
                    replicaSetState.compositeComponentLink = context.compositeComponent.documentSelfLink;
                    replicaSetState.parentLink = context.kubernetesContext.host.documentSelfLink;
                    replicaSetState.documentSelfLink = replicaSet.metadata.uid;
                    replicaSetState.id = replicaSet.metadata.uid;
                    replicaSetState.kubernetesSelfLink = replicaSet.metadata.selfLink;
                    sendRequest(Operation.createPost(this, ReplicaSetService.FACTORY_LINK)
                            .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                            .setBody(replicaSetState)
                            .setCompletion((op, err) -> {
                                if (err != null) {
                                    if (hasError.compareAndSet(false, true)) {
                                        failureCallback.accept(err);
                                    } else {
                                        logWarning("Failure creating kubernetes entity: %s",
                                                Utils.toString(err));
                                    }
                                } else {
                                    if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                        successfulCallback.run();
                                    }
                                }
                            }));
                }
            }
        });
    }

    private void discoverPods(RequestContext context, Consumer<Throwable> failureCallback,
            Runnable successfulCallback) {
        String compositeComponentId = UriUtils.getLastPathSegment(context.compositeComponent
                .documentSelfLink);

        context.client.getPods(context.kubernetesContext, compositeComponentId, (o, ex) -> {
            if (ex != null) {
                failureCallback.accept(ex);
            } else {
                PodList pods = o.getBody(PodList.class);
                if (pods.items == null || pods.items.isEmpty()) {
                    successfulCallback.run();
                    return;
                }
                AtomicInteger counter = new AtomicInteger(pods.items.size());
                AtomicBoolean hasError = new AtomicBoolean(false);
                for (Pod pod : pods.items) {
                    PodState podState = new PodState();
                    podState.pod = pod;
                    podState.name = pod.metadata.name;
                    podState.compositeComponentLink = context.compositeComponent.documentSelfLink;
                    podState.parentLink = context.kubernetesContext.host.documentSelfLink;
                    podState.documentSelfLink = pod.metadata.uid;
                    podState.id = pod.metadata.uid;
                    podState.kubernetesSelfLink = pod.metadata.selfLink;
                    sendRequest(Operation.createPost(this, PodService.FACTORY_LINK)
                            .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                            .setBody(podState)
                            .setCompletion((op, err) -> {
                                if (err != null) {
                                    if (hasError.compareAndSet(false, true)) {
                                        failureCallback.accept(err);
                                    } else {
                                        logWarning("Failure creating kubernetes entity: %s",
                                                Utils.toString(err));
                                    }
                                } else {
                                    if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                        successfulCallback.run();
                                    }
                                }
                            }));
                }
            }
        });
    }

    private void createSpecificKubernetesState(RequestContext context,
            KubernetesDescription description, Operation response, CompletionHandler handler) {

        CompositeComponent component = context.compositeComponent;
        BaseKubernetesState state;
        String factoryLink;
        switch (description.type) {
        case DEPLOYMENT_TYPE:
            Deployment deployment = response.getBody(Deployment.class);
            DeploymentState deploymentState = new DeploymentState();
            deploymentState.deployment = deployment;
            deploymentState.name = deployment.metadata.name;
            state = deploymentState;
            factoryLink = DeploymentService.FACTORY_LINK;
            break;
        case SERVICE_TYPE:
            Service service = response.getBody(Service.class);
            ServiceState serviceState = new ServiceState();
            serviceState.service = service;
            serviceState.name = service.metadata.name;
            state = serviceState;
            factoryLink = ServiceEntityHandler.FACTORY_LINK;
            break;
        case REPLICATION_CONTROLLER_TYPE:
            ReplicationController controller = response.getBody(ReplicationController.class);
            ReplicationControllerState controllerState = new ReplicationControllerState();
            controllerState.replicationController = controller;
            controllerState.name = controller.metadata.name;
            state = controllerState;
            factoryLink = ReplicationControllerService.FACTORY_LINK;
            break;
        case POD_TYPE:
            Pod pod = response.getBody(Pod.class);
            PodState podState = new PodState();
            podState.pod = pod;
            podState.name = pod.metadata.name;
            state = podState;
            factoryLink = PodService.FACTORY_LINK;
            break;
        default:
            BaseKubernetesObject k8sObject = response.getBody(BaseKubernetesObject.class);
            GenericKubernetesEntityState k8sState = new GenericKubernetesEntityState();
            k8sState.entity = k8sObject;
            k8sState.name = k8sObject.metadata != null ? k8sObject.metadata.name : null;
            state = k8sState;
            factoryLink = GenericKubernetesEntityService.FACTORY_LINK;
            return;
        }
        state.documentSelfLink = state.getMetadata().uid;
        state.compositeComponentLink = component.documentSelfLink;
        state.descriptionLink = description.documentSelfLink;
        state.parentLink = context.kubernetesContext.host.documentSelfLink;
        state.id = state.getMetadata().uid;
        state.kubernetesSelfLink = state.getMetadata().selfLink;

        sendRequest(Operation.createPost(this, factoryLink)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(state)
                .setCompletion(handler));
    }

    private void processApplicationDelete(RequestContext context) {
        List<String> deploymentComponents = context.compositeComponent.componentLinks.stream()
                .filter(c -> c.startsWith(DeploymentService.FACTORY_LINK))
                .collect(Collectors.toList());

        List<String> replicationControllerComponents = context.compositeComponent.componentLinks
                .stream().filter(c -> c.startsWith(ReplicationControllerService.FACTORY_LINK))
                .collect(Collectors.toList());

        List<String> serviceComponents = context.compositeComponent.componentLinks.stream()
                .filter(c -> c.startsWith(ServiceEntityHandler.FACTORY_LINK))
                .collect(Collectors.toList());

        List<String> replicaSetComponents = context.compositeComponent.componentLinks.stream()
                .filter(c -> c.startsWith(ReplicaSetService.FACTORY_LINK))
                .collect(Collectors.toList());

        List<String> podComponents = context.compositeComponent.componentLinks.stream()
                .filter(c -> c.startsWith(PodService.FACTORY_LINK))
                .collect(Collectors.toList());

        List<String> genericComponents = context.compositeComponent.componentLinks.stream()
                .filter(c -> c.startsWith(GenericKubernetesEntityService.FACTORY_LINK))
                .collect(Collectors.toList());

        DeferredResult.completed(null)
                // Delete deployments.
                .thenCompose(ignore -> deleteComponents(context, deploymentComponents))
                .exceptionally(ex -> {
                    throw logAndCreateException(ex, "Failed to delete kubernetes deployments");
                })
                // Delete replication controllers.
                .thenCompose(ignore -> deleteComponents(context, replicationControllerComponents))
                .exceptionally(ex -> {
                    throw logAndCreateException(ex,
                            "Failed to delete kubernetes replication controllers.");
                })
                // Delete services.
                .thenCompose(ignore -> deleteComponents(context, serviceComponents))
                .exceptionally(ex -> {
                    throw logAndCreateException(ex, "Failed to delete kubernetes services.");
                })
                // Delete replica sets.
                .thenCompose(ignore -> deleteComponents(context, replicaSetComponents))
                .exceptionally(ex -> {
                    throw logAndCreateException(ex, "Failed to delete kubernetes replica sets.");
                })
                // Delete pods.
                .thenCompose(ignore -> deleteComponents(context, podComponents))
                .exceptionally(ex -> {
                    throw logAndCreateException(ex, "Failed to delete kubernetes pods.");
                })
                // Delete generic components.
                .thenCompose(ignore -> deleteComponents(context, genericComponents))
                .exceptionally(ex -> {
                    throw logAndCreateException(ex,
                            "Failed to delete kubernetes generic entities.");
                })
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                        fail(context.request, cause);
                    } else {
                        patchTaskStage(context.request, TaskStage.FINISHED, null);
                    }
                });
    }

    private CompletionException logAndCreateException(Throwable ex, String message) {
        logSevere(message);
        return ex instanceof CompletionException ? (CompletionException) ex
                : new CompletionException(ex);
    }

    private DeferredResult<Void> deleteComponents(RequestContext context,
            List<String> componentLinks) {
        if (componentLinks == null || componentLinks.isEmpty()) {
            return DeferredResult.completed(null);
        }

        List<DeferredResult<Void>> operations = componentLinks.stream()
                .map(link -> {
                    return getState(context, link)
                            .thenCompose(state -> deleteKubernetesEntity(context, state))
                            .thenCompose(ignore -> deleteState(link));
                }).collect(Collectors.toList());

        return DeferredResult.allOf(operations).thenAccept(ignore -> {
        });
    }

    private DeferredResult<Void> deleteKubernetesEntity(RequestContext context,
            BaseKubernetesState state) {
        DeferredResult<Void> result = new DeferredResult<>();

        context.client.deleteEntity(state.kubernetesSelfLink, context.kubernetesContext,
                (o, ex) -> {
                    if (ex != null) {
                        Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                        logWarning("Failure deleting kubernetes entity: %s", Utils.toString(cause));
                        result.fail(ex);
                    } else {
                        result.complete(null);
                    }
                });

        return result;
    }

    private DeferredResult<Void> deleteState(String selfLink) {
        return sendWithDeferredResult(Operation
                .createDelete(this, selfLink))
                        .thenAccept(ignore -> {
                        });
    }

    private DeferredResult<BaseKubernetesState> getState(RequestContext context, String selfLink) {
        return sendWithDeferredResult(Operation.createGet(this, selfLink))
                .exceptionally(ex -> {

                    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                    logWarning("Unable to get resource state for %s, reason: %s", selfLink,
                            Utils.toString(cause));

                    throw ex instanceof CompletionException
                            ? (CompletionException) ex
                            : new CompletionException(ex);
                }).thenApply(op -> {
                    Class<? extends ResourceState> stateClass = CompositeComponentRegistry
                            .metaByStateLink(selfLink).stateClass;
                    return (BaseKubernetesState) op.getBody(stateClass);
                });

    }

}
