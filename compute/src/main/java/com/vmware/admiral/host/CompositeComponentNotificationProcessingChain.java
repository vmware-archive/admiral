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

package com.vmware.admiral.host;

import java.util.Map;
import java.util.function.Predicate;

import com.vmware.admiral.closures.services.closure.ClosureService;
import com.vmware.admiral.compute.Composable;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService;
import com.vmware.admiral.compute.container.util.CompositeComponentNotifier;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService;
import com.vmware.admiral.compute.kubernetes.service.PodService;
import com.vmware.admiral.compute.kubernetes.service.ReplicaSetService;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.StatefulService;

/**
 * Handles notifications to {@link CompositeComponent} on changes to registered resources.
 */
public class CompositeComponentNotificationProcessingChain extends OperationProcessingChain {

    public CompositeComponentNotificationProcessingChain(FactoryService service) {
        super(service);
        this.add(new Predicate<Operation>() {

            @Override
            public boolean test(Operation op) {
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
                return true;
            }
        });
    }

    public CompositeComponentNotificationProcessingChain(StatefulService service) {
        super(service);
        this.add(new Predicate<Operation>() {

            @Override
            public boolean test(Operation op) {
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

                        CompositeComponentNotifier.notifyCompositionComponentOnChange(service,
                                state, op.getAction(), putComponentLink, compositeComponentLink);
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
                        CompositeComponentNotifier.notifyCompositionComponentOnChange(service,
                                state, op.getAction(), patchComponentLink, compositeComponentLink);
                    });
                }
                return true;
            }
        });
    }

    /**
     * Register {@link OperationProcessingChain}s for specific resources.
     */
    public static void registerOperationProcessingChains(
            Map<Class<? extends Service>, Class<? extends OperationProcessingChain>> chains) {
        chains.put(ComputeService.class, CompositeComponentNotificationProcessingChain.class);
        chains.put(ContainerService.class, CompositeComponentNotificationProcessingChain.class);
        chains.put(ClosureService.class, CompositeComponentNotificationProcessingChain.class);

        chains.put(DeploymentService.class, CompositeComponentNotificationProcessingChain.class);
        chains.put(PodService.class, CompositeComponentNotificationProcessingChain.class);
        chains.put(ServiceEntityHandler.class, CompositeComponentNotificationProcessingChain.class);
        chains.put(ReplicationControllerService.class,
                CompositeComponentNotificationProcessingChain.class);
        chains.put(ReplicaSetService.class,
                CompositeComponentNotificationProcessingChain.class);
    }

    private String retrieveLink(ResourceState state) {
        if (state instanceof Composable) {
            return ((Composable) state).retrieveCompositeComponentLink();
        }
        return Composable.retrieveCompositeComponentLink(state);
    }

    private ResourceState extractState(Operation o) {
        String path = o.getUri().getPath();
        ComponentMeta meta = CompositeComponentRegistry.metaByStateLink(path);
        ResourceState state = o.getBody(meta.stateClass);
        return state;
    }
}
