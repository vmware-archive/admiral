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

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.compute.container.CompositeComponentService.FIELD_NAME_HOST_LINK;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.DEPLOYMENT_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.POD_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.REPLICATION_CONTROLLER_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.SERVICE_TYPE;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
import com.vmware.admiral.compute.kubernetes.entities.deployments.Deployment;
import com.vmware.admiral.compute.kubernetes.entities.pods.Pod;
import com.vmware.admiral.compute.kubernetes.entities.replicationcontrollers.ReplicationController;
import com.vmware.admiral.compute.kubernetes.entities.services.Service;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService.DeploymentState;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.service.PodService;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService.ReplicationControllerState;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler.ServiceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.TaskState.TaskStage;
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

        if (!component.customProperties.containsKey(FIELD_NAME_HOST_LINK)) {
            fail(context.request, new IllegalStateException("Composite component is missing "
                    + "custom property with host link"));
            return;
        }

        String hostLink = component.customProperties.get(FIELD_NAME_HOST_LINK);

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
        HashSet<String> supportedTypes = new HashSet<>(
                Arrays.asList(DEPLOYMENT_TYPE, SERVICE_TYPE, REPLICATION_CONTROLLER_TYPE,
                        POD_TYPE));

        for (KubernetesDescription description : descriptions) {
            if (!supportedTypes.contains(description.type)) {
                fail(context.request, new IllegalArgumentException(
                        "Unsupported kubernetes type: " + description.type));
                return;
            }
        }

        String affix = getApplicationUniqueAffix(context.compositeComponent,
                context.compositeDescription);

        descriptions = descriptions.stream()
                .map(desc -> KubernetesUtil.mapApplicationAffix(desc, affix))
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
                                    patchTaskStage(context.request, TaskStage.FINISHED, null);
                                }
                            });

                }
            });
        }
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
            fail(context.request, new IllegalArgumentException(
                    "Unsupported kubernetes type: " + description.type));
            return;
        }

        state.compositeComponentLink = component.documentSelfLink;
        state.descriptionLink = description.documentSelfLink;
        state.parentLink = context.kubernetesContext.host.documentSelfLink;

        sendRequest(Operation.createPost(this, factoryLink)
                .setBody(state)
                .setCompletion(handler));
    }

    private void processApplicationDelete(RequestContext context) {

        final AtomicInteger counter = new AtomicInteger(
                context.compositeComponent.componentLinks.size());
        final AtomicBoolean hasError = new AtomicBoolean(false);

        Runnable successfulCallback = () -> {
            if (counter.decrementAndGet() == 0 && !hasError.get()) {
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            }
        };

        Consumer<Throwable> failureCallback = (ex) -> {
            if (hasError.compareAndSet(false, true)) {
                fail(context.request, ex);
            } else {
                logWarning("Failure creating kubernetes entity: %s", Utils.toString(ex));
            }
        };

        for (String componentLink : context.compositeComponent.componentLinks) {
            getState(context, componentLink,
                    (state) -> context.client.deleteEntity(state.getKubernetesSelfLink(),
                            context.kubernetesContext, getDeleteStateHandler(componentLink,
                                    successfulCallback, failureCallback)));
        }
    }

    private CompletionHandler getDeleteStateHandler(String selfLink, Runnable successfulCallback,
            Consumer<Throwable> failureCallback) {
        CompletionHandler handler = (o, ex) -> {
            if (ex != null) {
                failureCallback.accept(ex);
            } else {
                deleteState(selfLink, (o1, ex1) -> {
                    if (ex1 != null) {
                        failureCallback.accept(ex1);
                    } else {
                        successfulCallback.run();
                    }
                });
            }
        };
        return handler;
    }

    private void deleteState(String selfLink, CompletionHandler handler) {
        sendRequest(Operation
                .createDelete(this, selfLink)
                .setCompletion(handler));
    }

    @SuppressWarnings("unchecked")
    private void getState(RequestContext context, String selfLink,
            Consumer<BaseKubernetesState> callBack) {
        sendRequest(Operation
                .createGet(this, selfLink)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, new IllegalStateException(String.format("Unable to "
                                        + "get resource state for %s, reason: %s", selfLink,
                                Utils.toString(ex))));
                    } else {
                        Class stateClass = CompositeComponentRegistry
                                .metaByStateLink(selfLink).stateClass;
                        BaseKubernetesState entity = (BaseKubernetesState) o.getBody(stateClass);
                        callBack.accept(entity);
                    }
                }));
    }

    private static String getApplicationUniqueAffix(CompositeComponent cc,
            CompositeDescription cd) {
        return cc.name.replace(cd.name, "");
    }
}
