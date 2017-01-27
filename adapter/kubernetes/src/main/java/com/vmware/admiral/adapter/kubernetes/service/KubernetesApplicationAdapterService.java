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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.ApplicationOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.content.kubernetes.deployments.Deployment;
import com.vmware.admiral.compute.content.kubernetes.services.Service;
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

        ApplicationOperationType operationType = context.request.getOperationtype();

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
                            processCompositeComponent(context);
                        });
                    }
                }));
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
                            processCompositeDescription(context);
                        });
                    }
                }));

    }

    public void processCompositeDescription(RequestContext context) {
        if (context.request.getHostReference() == null) {
            fail(context.request, new IllegalArgumentException("hostReference"));
            return;
        }

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
                processCreateApplication(context);
                break;

            case DELETE:
                //processDelete
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

    private void processCreateApplication(RequestContext context) {
        List<String> containerDescriptionLinks = context.compositeDescription.descriptionLinks
                .stream()
                .filter(link -> link.startsWith(ContainerDescriptionService.FACTORY_LINK))
                .collect(Collectors.toList());

        List<Operation> getContainerDescriptionOps = containerDescriptionLinks.stream()
                .map(link -> Operation.createGet(this, link))
                .collect(Collectors.toList());

        OperationJoin.create(getContainerDescriptionOps)
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
}
