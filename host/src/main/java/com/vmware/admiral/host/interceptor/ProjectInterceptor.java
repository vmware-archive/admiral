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

package com.vmware.admiral.host.interceptor;

import java.util.ArrayList;

import com.vmware.admiral.auth.util.SecurityContextUtil;
import com.vmware.admiral.closures.services.closure.ClosureService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescriptionService;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.container.CompositeComponentService;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;

public class ProjectInterceptor {

    public static void register(OperationInterceptorRegistry registry) {

        registerType(registry, ContainerDescriptionService.class);
        registerType(registry, ContainerService.class);

        registerType(registry, ContainerNetworkDescriptionService.class);
        registerType(registry, ContainerNetworkService.class);

        registerType(registry, ClosureDescriptionService.class);
        registerType(registry, ClosureService.class);

        registerType(registry, ClusterService.class);

        registerType(registry, ContainerVolumeDescriptionService.class);
        registerType(registry, ContainerVolumeService.class);

        registerType(registry, CompositeDescriptionService.class);
        registerType(registry, CompositeComponentService.class);

        registerType(registry, RegistryService.class);
    }

    private static void registerType(OperationInterceptorRegistry registry,
            Class<? extends Service> serviceType) {
        registry.addServiceInterceptor(serviceType, null,
                ProjectInterceptor::handleServiceOp);
        registry.addFactoryServiceInterceptor(serviceType, null,
                ProjectInterceptor::handleFactoryOp);
    }

    public static DeferredResult<Void> handleFactoryOp(Service service, Operation op) {
        if (op.getAction() != Action.POST) {
            return DeferredResult.completed(null);
        }
        return handleClusterServiceOp(service, op)
                .thenCompose(ignore -> setProjectLinkAsTenantLink(service, op));
    }

    public static DeferredResult<Void> handleServiceOp(Service service, Operation op) {
        if (op.getAction() == Action.GET) {
            return DeferredResult.completed(null);
        }

        return handleClusterServiceOp(service, op)
                .thenCompose(ignore -> setProjectLinkAsTenantLink(service, op));
    }

    private static DeferredResult<Void> setProjectLinkAsTenantLink(Service service, Operation op) {
        String projectLink = OperationUtil.extractProjectFromHeader(op);
        ResourceState state = extractResourceState(service, op);
        if (state != null) {
            handleResourceState(state, projectLink, op);
            return DeferredResult.completed(null);
        }

        MultiTenantDocument multiTenantDocument = extractMultiTenantState(service, op);
        if (multiTenantDocument != null) {
            handleMultiTenantState(multiTenantDocument, projectLink, op);
            return DeferredResult.completed(null);
        }

        ContainerHostSpec hostSpec = extractContainerHostSpec(op);
        if (hostSpec != null) {
            handleContainerHostSpec(hostSpec, projectLink, op);
        }
        return DeferredResult.completed(null);
    }

    private static void handleResourceState(ResourceState state, String projectLink, Operation op) {
        if (projectLink == null || projectLink.isEmpty()) {
            return;
        }
        if (state.tenantLinks == null) {
            state.tenantLinks = new ArrayList<>();
        }
        if (!state.tenantLinks.contains(projectLink)) {
            state.tenantLinks.add(projectLink);
            op.setBody(state);
        }
    }

    private static void handleMultiTenantState(MultiTenantDocument state, String projectLink,
            Operation op) {
        if (projectLink == null || projectLink.isEmpty()) {
            return;
        }
        if (state.tenantLinks == null) {
            state.tenantLinks = new ArrayList<>();
        }
        if (!state.tenantLinks.contains(projectLink)) {
            state.tenantLinks.add(projectLink);
            op.setBody(state);
        }
    }

    private static void handleContainerHostSpec(ContainerHostSpec state, String projectLink,
            Operation op) {
        if (projectLink == null || projectLink.isEmpty() || state.hostState == null) {
            return;
        }
        if (state.hostState.tenantLinks == null) {
            state.hostState.tenantLinks = new ArrayList<>();
        }
        if (!state.hostState.tenantLinks.contains(projectLink)) {
            state.hostState.tenantLinks.add(projectLink);
            op.setBody(state);
        }
    }

    private static ResourceState extractResourceState(Service service, Operation o) {
        if (!o.hasBody()) {
            return null;
        }

        ServiceDocument state = o.getBody(service.getStateType());

        if (state instanceof ResourceState) {
            return (ResourceState) state;
        }
        return null;
    }

    private static MultiTenantDocument extractMultiTenantState(Service service, Operation o) {
        if (!o.hasBody()) {
            return null;
        }

        ServiceDocument state = o.getBody(service.getStateType());

        if (state instanceof MultiTenantDocument) {
            return (MultiTenantDocument) state;
        }
        return null;
    }

    private static ContainerHostSpec extractContainerHostSpec(Operation o) {
        if (!o.hasBody()) {
            return null;
        }

        return o.getBody(ContainerHostSpec.class);
    }

    private static DeferredResult<Void> handleClusterServiceOp(Service service, Operation op) {
        if (!(service instanceof ClusterService)) {
            return DeferredResult.completed(null);
        }

        String projectLink = OperationUtil.extractProjectFromHeader(op);

        if (projectLink == null || projectLink.isEmpty()) {
            return DeferredResult.completed(null);
        }

        return SecurityContextUtil.getSecurityContextForCurrentUser(service)
                .thenCompose(sc -> {
                    if (sc.isCloudAdmin()) {
                        return DeferredResult.completed(null);
                    } else {
                        if (op.getAction() == Action.GET && sc.isProjectAdmin(projectLink)) {
                            return DeferredResult.completed(null);
                        }
                    }
                    return DeferredResult.failed(new IllegalAccessError("forbidden"));
                })
                .thenAccept(ignore -> {
                });
    }
}
