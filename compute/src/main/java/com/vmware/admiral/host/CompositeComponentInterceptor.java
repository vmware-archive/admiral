/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import java.util.logging.Level;

import com.vmware.admiral.closures.services.closure.ClosureService;
import com.vmware.admiral.compute.Composable;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService;
import com.vmware.admiral.compute.container.util.CompositeComponentNotifier;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService;
import com.vmware.admiral.compute.kubernetes.service.PodService;
import com.vmware.admiral.compute.kubernetes.service.ReplicaSetService;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler;
import com.vmware.admiral.host.interceptor.OperationInterceptorRegistry;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Handles notifications to {@link CompositeComponent} on changes to registered resources.
 */
public class CompositeComponentInterceptor {

    public static void register(OperationInterceptorRegistry registry) {
        registerType(registry, ComputeService.class);
        registerType(registry, ContainerService.class);
        registerType(registry, ClosureService.class);
        registerType(registry, ContainerLoadBalancerService.class);

        registerType(registry, DeploymentService.class);
        registerType(registry, PodService.class);
        registerType(registry, ServiceEntityHandler.class);
        registerType(registry, ReplicationControllerService.class);
        registerType(registry, ReplicaSetService.class);
    }

    private static void registerType(OperationInterceptorRegistry registry,
            Class<? extends Service> serviceType) {
        registry.addFactoryServiceInterceptor(serviceType, null,
                CompositeComponentInterceptor::handleFactoryPost);
        registry.addServiceInterceptor(serviceType, null,
                CompositeComponentInterceptor::handleServiceOp);
    }

    public static DeferredResult<Void> handleFactoryPost(Service service, Operation op) {
        if (op.getAction() == Action.POST) {

            op.nestCompletion((o, e) -> {
                if (e != null) {
                    op.fail(e);
                    return;
                }
                o.complete();
                if (!o.hasBody()) {
                    return;
                }
                ResourceState state = extractState(o);
                String compositeComponentLink = retrieveLink(state);
                if (compositeComponentLink == null) {
                    return;
                }
                CompositeComponentNotifier.notifyCompositionComponent(service, state,
                        compositeComponentLink, op.getAction());
            });
        }
        return null;
    }

    public static DeferredResult<Void> handleServiceOp(Service service, Operation op) {
        if (op.getAction() == Action.POST) {

            op.nestCompletion((o, e) -> {
                if (e != null) {
                    op.fail(e);
                    return;
                }
                o.complete();

                ResourceState state = extractState(o);
                String compositeComponentLink = retrieveLink(state);
                if (compositeComponentLink == null) {
                    return;
                }
                CompositeComponentNotifier.notifyCompositionComponent(service, state,
                        compositeComponentLink, op.getAction());
            });
        } else if (op.getAction() == Action.DELETE) {
            ResourceState state = service.getState(op);
            String compositeComponentLink = retrieveLink(state);
            CompositeComponentNotifier.notifyCompositionComponent(service, state,
                    compositeComponentLink, op.getAction());

        } else if (op.getAction() == Action.PUT) {
            ResourceState state = service.getState(op);
            ResourceState put = extractState(op);
            String compositeComponentLink = retrieveLink(state);
            String putComponentLink = retrieveLink(put);

            op.nestCompletion((o, e) -> {
                if (e != null) {
                    op.fail(e);
                    return;
                }
                o.complete();

                CompositeComponentNotifier.notifyCompositionComponentOnChange(
                        (StatefulService)service, state, op.getAction(), putComponentLink,
                        compositeComponentLink);
            });
        } else if (op.getAction() == Action.PATCH) {
            ResourceState state = service.getState(op);

            String compositeComponentLink = retrieveLink(state);

            op.nestCompletion((o, e) -> {
                if (e != null) {
                    op.fail(e);
                    return;
                }
                ResourceState patch = service.getState(o);
                String patchComponentLink = retrieveLink(patch);
                o.complete();

                if (Operation.STATUS_CODE_NOT_MODIFIED == o.getStatusCode()) {
                    return;
                }
                CompositeComponentNotifier.notifyCompositionComponentOnChange(
                        (StatefulService)service, state, op.getAction(), patchComponentLink,
                        compositeComponentLink);
            });
        }
        return null;
    }

    private static String retrieveLink(ResourceState state) {
        if (state instanceof Composable) {
            return ((Composable) state).retrieveCompositeComponentLink();
        }
        return Composable.retrieveCompositeComponentLink(state);
    }

    private static ResourceState extractState(Operation o) {
        String path = o.getUri().getPath();
        ComponentMeta meta = CompositeComponentRegistry.metaByStateLink(path);
        if (meta.stateClass == null) {
            Utils.log(CompositeComponentInterceptor.class,
                    CompositeComponentInterceptor.class.getSimpleName(),
                    Level.WARNING, "Cannot find meta for path = %s", path);
        }
        ResourceState state = o.getBody(meta.stateClass);
        return state;
    }
}
