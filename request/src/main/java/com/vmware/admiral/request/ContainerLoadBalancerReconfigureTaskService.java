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

package com.vmware.admiral.request;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancers
        .CONTAINER_LOAD_BALANCER_DESCRIPTION_LINK;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.admiral.compute.container.ShellContainerExecutorService
        .ShellContainerExecutorState;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerBackendDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerDescriptionService
        .ContainerLoadBalancerDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerFrontendDescription;
import com.vmware.admiral.request.ContainerLoadBalancerReconfigureTaskService
        .ContainerLoadBalancerReconfigureTaskState;
import com.vmware.admiral.request.ContainerLoadBalancerReconfigureTaskService
        .ContainerLoadBalancerReconfigureTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Task implementing the provision of a container load balancer.
 */
public class ContainerLoadBalancerReconfigureTaskService extends
        AbstractTaskStatefulService<ContainerLoadBalancerReconfigureTaskState,
                ContainerLoadBalancerReconfigureTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts
            .REQUEST_CONTAINER_LOAD_BALANCER_RECONFIG_TASKS;

    public static final String DISPLAY_NAME = "Load Balancer Reconfigure";
    //cached load balancer description
    private volatile Map<ContainerState, ContainerLoadBalancerDescription> loadBalancers;
    private volatile CompositeComponent compositeComponent;

    public static class ContainerLoadBalancerReconfigureTaskState extends
            com.vmware.admiral.service.common
                    .TaskServiceDocument<ContainerLoadBalancerReconfigureTaskState.SubStage> {

        /**
         * Addresses according to which the service links in ContainerLoadBalancerDescription will
         * be resolved and expanded in every load balancer of the composite component
         */
        @Documentation(
                description = "Addresses according to which the service links in " +
                        "ContainerLoadBalancerDescription will be resolved and expanded in every " +
                        "load balancer of the composite component")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL})
        public Map<String, List<String>> serviceLinksExpanded;

        /**
         * Will reconfigure all load balancer containers present in the composite component
         */

        public static enum SubStage {
            CREATED,
            RECONFIGURE,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(RECONFIGURE));
        }

    }

    static class LBConfig {
        List<ContainerLoadBalancerFrontendDescription> frontends;

        public LBConfig(
                List<ContainerLoadBalancerFrontendDescription> frontends) {
            this.frontends = frontends;
        }
    }

    public ContainerLoadBalancerReconfigureTaskService() {
        super(ContainerLoadBalancerReconfigureTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(ContainerLoadBalancerReconfigureTaskState state) {
        Utils.validateState(getStateDescription(), state);
    }

    @Override
    protected void handleStartedStagePatch(ContainerLoadBalancerReconfigureTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            reconfigure(state);
            break;
        case RECONFIGURE:
            break;
        case COMPLETED:
            complete();
            break;
        case ERROR:
            completeWithError();
            break;
        default:
            break;
        }
    }

    private void reconfigure(ContainerLoadBalancerReconfigureTaskState state) {
        state.serviceLinksExpanded = new HashMap<>();
        loadBalancers = new HashMap<>();

        getCompositeComponent(state, (component) -> {
            List<String> containerLinks = component.componentLinks.stream()
                    .filter(link -> link.startsWith(ContainerFactoryService.SELF_LINK))
                    .collect(Collectors.toList());

            QueryTask containerQueryTask = QueryUtil.buildQuery(ContainerState.class, true);

            QueryUtil.addExpandOption(containerQueryTask);
            QueryUtil.addListValueClause(containerQueryTask, ContainerState.FIELD_NAME_SELF_LINK,
                    containerLinks);

            new ServiceDocumentQuery<>(getHost(), ContainerState.class)
                    .query(containerQueryTask,
                            (r) -> {
                                if (r.hasResult()) {
                                    processContainer(state, r.getResult());
                                } else {
                                    if (r.hasException()) {
                                        failTask("Failed to find container states of composite "
                                                + "component", r.getException());
                                    }
                                    if (loadBalancers.isEmpty()) {
                                        //nothing to reconfigure
                                        proceedTo(SubStage.COMPLETED);
                                    } else {
                                        reconfigureLoadBalancers(state, loadBalancers, null);
                                    }
                                }
                            });
        });
        proceedTo(SubStage.RECONFIGURE);
    }

    private void processContainer(ContainerLoadBalancerReconfigureTaskState state, ContainerState
            container) {
        fetchContainerDescription(container.descriptionLink, (description) -> {
            String loadBalancerDescription = description.customProperties ==
                    null ?
                    null :
                    description.customProperties
                            .get(CONTAINER_LOAD_BALANCER_DESCRIPTION_LINK);
            if (loadBalancerDescription != null) {
                sendRequest(Operation.createGet(this, loadBalancerDescription)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                failTask("Failure retrieving container load balancer "
                                        + "description", e);
                                return;
                            }
                            loadBalancers.put(container, o.getBody
                                    (ContainerLoadBalancerDescription.class));
                        }));
            } else {
                List<String> serviceInstances = state.serviceLinksExpanded.get(description
                        .name);
                if (serviceInstances == null) {
                    serviceInstances = new ArrayList<>();
                    state.serviceLinksExpanded.put(description.name,
                            serviceInstances);
                }
                assertNotEmpty(container.names, "names");
                serviceInstances.add(container.names.get(0));
            }
        });
    }

    private void reconfigureLoadBalancers(ContainerLoadBalancerReconfigureTaskState state,
                                          Map<ContainerState, ContainerLoadBalancerDescription>
                                                  loadBalancers,
                                          ServiceTaskCallback taskCallback) {
        if (taskCallback == null) {
            createCounterSubTaskCallback(state, loadBalancers.size(), false,
                    SubStage.COMPLETED,
                    (serviceTask) -> reconfigureLoadBalancers(state, loadBalancers, serviceTask));
            return;
        }

        loadBalancers.forEach((containerState, description) -> {
            reconfigureLoadBalancer(state, containerState, description, taskCallback);
        });
    }

    private void reconfigureLoadBalancer(ContainerLoadBalancerReconfigureTaskState state,
                                         ContainerState loadBalancer,
                                         ContainerLoadBalancerDescription lbDescription,
                                         ServiceTaskCallback taskCallback) {
        try {
            List<ContainerLoadBalancerFrontendDescription> expandedFrontends = expandLinks
                    (lbDescription.frontends, state.serviceLinksExpanded);
            ShellContainerExecutorState executorState = new ShellContainerExecutorState();

            executorState.command = new String[]{"proxy-config", "--config",
                    serializeFrontends(expandedFrontends)};
            executorState.attachStdOut = true;

            URI executeUri = UriUtils.buildUri(getHost(), ShellContainerExecutorService.SELF_LINK);

            executeUri = UriUtils.extendUriWithQuery(executeUri,
                    ShellContainerExecutorService.CONTAINER_LINK_URI_PARAM,
                    loadBalancer.documentSelfLink);
            sendRequest(Operation
                    .createPost(executeUri)
                    .setReferer(getHost().getUri())
                    .setBody(executorState)
                    .setCompletion((o, e) -> {
                        completeSubTasksCounter(taskCallback, e);
                    }));
        } catch (Throwable t) {
            failTask("Error while reconfiguring load balancers", t);
        }
    }

    private String serializeFrontends(List<ContainerLoadBalancerFrontendDescription>
                                              frontends) {
        return new GsonBuilder().registerTypeAdapter(ContainerLoadBalancerBackendDescription.class,
                (JsonSerializer<ContainerLoadBalancerBackendDescription>) (backendDescription,
                                                                           type,
                                                                           jsonSerializationContext) -> {
                    JsonObject backend = new JsonObject();
                    backend.addProperty("host", backendDescription.service);
                    backend.addProperty("port", backendDescription.port);
                    return backend;
                }).create()
                .toJson(new LBConfig(frontends));
    }

    private List<ContainerLoadBalancerFrontendDescription> expandLinks(
            List<ContainerLoadBalancerFrontendDescription> frontends,
            Map<String, List<String>> names) {
        List<ContainerLoadBalancerFrontendDescription> expandedFrontends = new ArrayList<>();
        for (ContainerLoadBalancerFrontendDescription frontend : frontends) {
            ContainerLoadBalancerFrontendDescription expandedFrontend = new
                    ContainerLoadBalancerFrontendDescription();
            expandedFrontend.port = frontend.port;
            expandedFrontend.healthConfig = frontend.healthConfig;
            expandedFrontend.backends = new ArrayList<>();
            List<ContainerLoadBalancerBackendDescription> newBackends = new ArrayList<>();
            for (ContainerLoadBalancerBackendDescription backend : frontend.backends) {
                if (names.containsKey(backend.service)) {
                    for (String name : names.get(backend.service)) {
                        ContainerLoadBalancerBackendDescription newBackend = new
                                ContainerLoadBalancerBackendDescription();
                        newBackend.port = backend.port;
                        newBackend.service = name;
                        newBackends.add(newBackend);
                    }
                }
            }
            expandedFrontend.backends.addAll(newBackends);
            expandedFrontends.add(expandedFrontend);
        }
        return expandedFrontends;
    }

    private void getCompositeComponent(ContainerLoadBalancerReconfigureTaskState state,
                                       Consumer<CompositeComponent> callbackFunction) {
        if (compositeComponent != null) {
            callbackFunction.accept(compositeComponent);
            return;
        }

        String contextId = state.customProperties.get(FIELD_NAME_CONTEXT_ID_KEY);
        String compositeComponentLink = UriUtils
                .buildUriPath(CompositeComponentFactoryService.SELF_LINK, contextId);
        sendRequest(Operation.createGet(this, compositeComponentLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving CompositeComponent", e);
                    } else {
                        CompositeComponent compositeComponent = o.getBody(CompositeComponent.class);
                        this.compositeComponent = compositeComponent;
                        callbackFunction.accept(compositeComponent);
                    }
                }));
    }

    private void fetchContainerDescription(String link, Consumer<ContainerDescription>
            callbackFunction) {
        sendRequest(Operation.createGet(this, link)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving container description", e);
                        return;
                    }
                    callbackFunction.accept(o.getBody(ContainerDescription.class));
                }));
    }

}
