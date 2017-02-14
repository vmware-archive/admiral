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

import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromContainerDescriptionToDeployment;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesConverter.fromContainerDescriptionToService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.KubernetesOperationType;
import com.vmware.admiral.adapter.kubernetes.ApplicationRequest;
import com.vmware.admiral.adapter.kubernetes.KubernetesRemoteApiClient;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.content.kubernetes.CommonKubernetesEntity;
import com.vmware.admiral.compute.content.kubernetes.KubernetesEntityList;
import com.vmware.admiral.compute.content.kubernetes.deployments.Deployment;
import com.vmware.admiral.compute.content.kubernetes.services.Service;
import com.vmware.admiral.compute.kubernetes.KubernetesDescriptionService;
import com.vmware.admiral.compute.kubernetes.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.KubernetesService.KubernetesState;
import com.vmware.xenon.common.Operation;
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

        KubernetesOperationType operationType = context.request.getOperationtype();

        logInfo("Processing application operation request %s for resource %s on host %s",
                operationType, context.request.resourceReference, context.request.hostReference);

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
        // Get the kubernetes context and the api client.
        getContainerHost(
                context.request,
                null,
                context.request.getHostReference(),
                (k8sContext) -> {
                    context.kubernetesContext = k8sContext;
                    context.client = getApiClient();
                    handleExceptions(context.request, null,
                            () -> processOperation(context));
                }
        );
    }

    private void processOperation(RequestContext context) {
        try {
            switch (context.request.getOperationtype()) {
            case CREATE:
                processCompositeComponent(context);
                break;

            case DELETE:
                validateApplicationBeforeDelete(context);
                break;

            default:
                fail(context.request, new IllegalArgumentException(
                        "Unexpected request type: " + context.request.getOperationtype()
                ));
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
                            context.compositeDescription = o2.getBody
                                    (CompositeDescription.class);
                            processCreateApplication(context);
                        });
                    }
                }));

    }

    private void processCreateApplication(RequestContext context) {
        List<String> descriptionLinks = context.compositeDescription.descriptionLinks
                .stream()
                .filter(link -> link.startsWith(ContainerDescriptionService.FACTORY_LINK))
                .collect(Collectors.toList());

        try {
            if (isKubernetesTemplate(descriptionLinks)) {
                getKubernetesDescriptions(context, descriptionLinks);
            } else {
                getContainerDescriptions(context, descriptionLinks);
            }
        } catch (Throwable e) {
            fail(context.request, e);
        }

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

                        try {
                            processKubernetesDescriptions(context, descriptions);
                        } catch (IOException e) {
                            fail(context.request, e);
                        }

                    }
                }).sendWith(this);
    }

    private void processKubernetesDescriptions(RequestContext context,
            List<KubernetesDescription> descriptions) throws IOException {

        final AtomicInteger counter = new AtomicInteger(descriptions.size());
        final AtomicBoolean hasError = new AtomicBoolean(false);
        // descriptionLink to kubernetesEntity map.
        Map<String, String> kubernetesEntities = new HashMap<>();
        for (KubernetesDescription description : descriptions) {
            context.client.createEntity(description, context.kubernetesContext, (o, ex) -> {
                if (ex != null) {
                    if (hasError.compareAndSet(false, true)) {
                        fail(context.request, ex);
                    } else {
                        logWarning("Failure creating kubernetes entity: %s", Utils.toString(ex));
                    }
                } else {
                    String kubernetesEntity = o.getBody(String.class);
                    kubernetesEntities.put(description.documentSelfLink, kubernetesEntity);
                    if (counter.decrementAndGet() == 0 && !hasError.get()) {
                        processKubernetesEntities(context, kubernetesEntities);
                    }
                }
            });
        }
    }

    private void processKubernetesEntities(RequestContext context,
            Map<String, String> kubernetesEntities) {

        List<Operation> getKubernetesStates = context.compositeComponent.componentLinks.stream()
                .map(link -> Operation.createGet(this, link))
                .collect(Collectors.toList());

        OperationJoin.create(getKubernetesStates)
                .setCompletion((ops, errors) -> {
                    if (errors != null) {
                        List<Throwable> throwables = errors.values().stream()
                                .filter(e -> e != null)
                                .collect(Collectors.toList());
                        fail(context.request, throwables.get(0));
                        throwables.stream().skip(1)
                                .forEach(e -> logWarning("%s", e.getMessage()));
                    } else {
                        List<KubernetesState> states = ops.entrySet().stream()
                                .map(desc -> desc.getValue().getBody(KubernetesState.class))
                                .collect(Collectors.toList());

                        try {
                            patchKubernetesStates(context, kubernetesEntities, states);
                        } catch (IOException e) {
                            fail(context.request, e);
                        }

                    }
                }).sendWith(this);
    }

    private void patchKubernetesStates(RequestContext context, Map<String, String>
            kubernetesEntities, List<KubernetesState> kubernetesStates) throws IOException {

        List<Operation> patchStatesOps = new ArrayList<>();

        for (KubernetesState state : kubernetesStates) {
            KubernetesState newState = new KubernetesState();
            newState.kubernetesEntity = kubernetesEntities.get(state.descriptionLink);
            CommonKubernetesEntity entity = state.getKubernetesEntity(CommonKubernetesEntity.class);
            newState.selfLink = entity.metadata.selfLink;
            newState.namespace = entity.metadata.namespace;
            newState.type = entity.kind;
            patchStatesOps.add(Operation.createPatch(this, state.documentSelfLink));
        }

        OperationJoin.create(patchStatesOps)
                .setCompletion((ops, errors) -> {
                    if (errors != null) {
                        List<Throwable> throwables = errors.values().stream()
                                .filter(e -> e != null)
                                .collect(Collectors.toList());
                        fail(context.request, throwables.get(0));
                        throwables.stream().skip(1)
                                .forEach(e -> logWarning("%s", e.getMessage()));
                    } else {
                        patchTaskStage(context.request, TaskStage.FINISHED, null);
                    }
                }).sendWith(this);

    }

    private void getContainerDescriptions(RequestContext context, List<String> descriptionLinks) {
        List<Operation> getDescriptions = descriptionLinks.stream()
                .map(link -> Operation.createGet(this, link))
                .collect(Collectors.toList());

        OperationJoin.create(getDescriptions)
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
                        List<ContainerDescription> descriptions = ops.entrySet().stream()
                                .map(desc -> desc.getValue().getBody(ContainerDescription.class))
                                .collect(Collectors.toList());

                        processContainerDescriptions(context, descriptions);

                    }
                }).sendWith(this);
    }

    private void processContainerDescriptions(RequestContext context, List<ContainerDescription>
            descriptions) {

        Runnable successfulCallback = () -> processCreateDeployments(context, descriptions);

        processCreateServices(context, descriptions, successfulCallback);
    }

    private void processCreateServices(RequestContext context, List<ContainerDescription>
            descriptions, Runnable callBack) {

        final AtomicInteger counter = new AtomicInteger(descriptions.size());
        final AtomicBoolean hasError = new AtomicBoolean(false);

        for (ContainerDescription description : descriptions) {
            // Do not create service if no ports are exposed
            if (description.portBindings == null || description.portBindings.length < 1) {
                if (counter.decrementAndGet() == 0 && !hasError.get()) {
                    callBack.run();
                }
                return;
            }
            Service service = fromContainerDescriptionToService(description, context
                    .compositeComponent.name);

            context.client.createService(service, context.kubernetesContext, (o, ex) -> {
                if (ex != null) {
                    if (hasError.compareAndSet(false, true)) {
                        fail(context.request, ex);
                    } else {
                        logWarning("Failure creating service: %s", Utils.toString(ex));
                    }
                } else {
                    if (counter.decrementAndGet() == 0 && !hasError.get()) {
                        callBack.run();
                    }
                }
            });
        }
    }

    private void processCreateDeployments(RequestContext context, List<ContainerDescription>
            descriptions) {

        final AtomicInteger counter = new AtomicInteger(descriptions.size());
        final AtomicBoolean hasError = new AtomicBoolean(false);

        for (ContainerDescription description : descriptions) {

            Deployment deployment = fromContainerDescriptionToDeployment(description, context
                    .compositeComponent.name);

            context.client.createDeployment(deployment, context.kubernetesContext, (o, ex) -> {
                if (ex != null) {
                    if (hasError.compareAndSet(false, true)) {
                        fail(context.request, ex);
                    } else {
                        logWarning("Failure creating deployment: %s", Utils.toString(ex));
                    }
                } else {
                    if (counter.decrementAndGet() == 0 && !hasError.get()) {
                        patchTaskStage(context.request, TaskStage.FINISHED, null);
                    }
                }
            });
        }
    }

    private void validateApplicationBeforeDelete(RequestContext context) {
        List<String> containerStateLinks = context.compositeComponent.componentLinks.stream()
                .filter(link -> link.startsWith(ContainerFactoryService.SELF_LINK))
                .collect(Collectors.toList());

        List<Operation> getContainerStates = containerStateLinks.stream()
                .map(link -> Operation.createGet(this, link))
                .collect(Collectors.toList());

        OperationJoin.create(getContainerStates)
                .setCompletion((ops, errors) -> {
                    if (errors != null) {
                        // Filter not null exceptions, fail the request with first one,
                        // log all others.
                        List<Throwable> throwables = errors.entrySet().stream()
                                .filter(e -> e.getValue() != null)
                                .map(e -> e.getValue())
                                .collect(Collectors.toList());
                        fail(context.request, throwables.get(0));
                        throwables.stream().skip(1)
                                .forEach(e -> logWarning("%s", e.getMessage()));
                    } else {
                        List<ContainerService.ContainerState> states = ops.entrySet().stream()
                                .map(desc -> desc.getValue()
                                        .getBody(ContainerService.ContainerState.class))
                                .collect(Collectors.toList());

                        validateApplicationComponents(context, states);

                    }
                }).sendWith(this);
    }

    @SuppressWarnings("unchecked")
    private void validateApplicationComponents(RequestContext context, List<ContainerState>
            states) {
        long servicesToDelete = states.stream()
                .filter(c -> c.ports != null && c.ports.size() > 0)
                .count();

        long deploymentsToDelete = states.size();

        // Validate the services
        context.client.getServices(context.compositeComponent.name, context.kubernetesContext,
                (o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        KubernetesEntityList<Service> services = o
                                .getBody(KubernetesEntityList.class);
                        if (validateServicesToDelete(context, servicesToDelete, services)) {
                            // Validate the deployments
                            context.client.getDeployments(context.compositeComponent.name,
                                    context.kubernetesContext,
                                    (op, exc) -> {
                                        if (exc != null) {
                                            fail(context.request, exc);
                                        } else {
                                            KubernetesEntityList<Deployment> deployments = op
                                                    .getBody(KubernetesEntityList.class);
                                            if (validateDeploymentsToDelete(context,
                                                    deploymentsToDelete, deployments)) {
                                                // Proceed to deletion.
                                                processDeleteApplication(context, services,
                                                        deployments);
                                            }
                                        }
                                    });
                        }
                    }
                });
    }

    private boolean validateServicesToDelete(RequestContext context, long servicesToDelete,
            KubernetesEntityList<Service> services) {
        if (servicesToDelete != services.getItems(Service.class).size()) {
            fail(context.request,
                    new IllegalStateException(String.format(
                            "The count of services to delete differs from the count of services with the same application label "
                                    + "on Kubernetes host: %s. Services to delete: %d, services on the host: %d",
                            context.kubernetesContext.host.address, servicesToDelete,
                            services.getItems(Service.class).size())));
            return false;
        }
        return true;
    }

    private boolean validateDeploymentsToDelete(RequestContext context, long deploymentsToDelete,
            KubernetesEntityList<Deployment> deployments) {
        if (deploymentsToDelete != deployments.getItems(Deployment.class).size()) {
            fail(context.request,
                    new IllegalStateException(String.format(
                            "The count of deployments to delete differs from the count of "
                                    + "deployments with the same application label on Kubernetes "
                                    + "host: %s. Deployments to delete: %d, deployments on the host: %d",
                            context.kubernetesContext.host.address, deploymentsToDelete,
                            deployments.getItems(Deployment.class).size())));
            return false;
        }
        return true;
    }

    private void processDeleteApplication(RequestContext context,
            KubernetesEntityList<Service> services, KubernetesEntityList<Deployment> deployments) {
        Runnable callback = () -> processDeleteDeployments(context, deployments,
                () -> patchTaskStage(context.request, TaskStage.FINISHED, null));

        processDeleteServices(context, services, callback);
    }

    private void processDeleteServices(RequestContext context,
            KubernetesEntityList<Service> services, Runnable callback) {
        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicInteger counter = new AtomicInteger(services.getItems(Service.class).size());
        for (Service service : services.getItems(Service.class)) {
            context.client.deleteService(service.metadata.name, context.kubernetesContext,
                    (o, ex) -> {
                        if (ex != null) {
                            if (hasError.compareAndSet(false, true)) {
                                fail(context.request, ex);
                            } else {
                                logWarning("Failure deleting service: %s", Utils.toString(ex));
                            }
                        } else {
                            if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                callback.run();
                            }
                        }
                    });
        }
    }

    private void processDeleteDeployments(RequestContext context, KubernetesEntityList<Deployment>
            deployments, Runnable callback) {
        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicInteger counter = new AtomicInteger(deployments.getItems(Deployment.class).size());
        for (Deployment deployment : deployments.getItems(Deployment.class)) {
            context.client.deleteDeployment(deployment.metadata.name, context.kubernetesContext,
                    (o, ex) -> {
                        if (ex != null) {
                            if (hasError.compareAndSet(false, true)) {
                                fail(context.request, ex);
                            } else {
                                logWarning("Failure deleting deployment: %s", Utils.toString(ex));
                            }
                        } else {
                            if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                callback.run();
                            }
                        }
                    });
        }
    }

    private boolean isKubernetesTemplate(List<String> descriptionLinks) {
        int kubernetesDescriptionCounter = 0;
        int otherDescriptionCounter = 0;
        for (String link : descriptionLinks) {
            if (!link.startsWith(KubernetesDescriptionService.FACTORY_LINK)) {
                otherDescriptionCounter++;
            } else {
                kubernetesDescriptionCounter++;
            }
        }

        if (kubernetesDescriptionCounter > 0 && otherDescriptionCounter > 0) {
            throw new IllegalStateException("Template with mixed descriptions is not supported.");
        } else {
            return kubernetesDescriptionCounter > 0;
        }
    }
}
