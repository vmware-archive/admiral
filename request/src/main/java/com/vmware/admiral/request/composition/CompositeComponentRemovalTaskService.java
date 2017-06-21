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

package com.vmware.admiral.request.composition;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.request.ContainerRemovalTaskService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.composition.CompositeComponentRemovalTaskService.CompositeComponentRemovalTaskState.SubStage;
import com.vmware.admiral.request.composition.CompositionSubTaskService.CompositionSubTaskState;
import com.vmware.admiral.request.kubernetes.CompositeKubernetesRemovalTaskService;
import com.vmware.admiral.request.kubernetes.CompositeKubernetesRemovalTaskService.CompositeKubernetesRemovalTaskState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class CompositeComponentRemovalTaskService
        extends
        AbstractTaskStatefulService<CompositeComponentRemovalTaskService.CompositeComponentRemovalTaskState,
                CompositeComponentRemovalTaskService.CompositeComponentRemovalTaskState.SubStage> {
    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPOSITION_REMOVAL_TASK;

    public static final String DISPLAY_NAME = ContainerRemovalTaskService.DISPLAY_NAME;

    public static class CompositeComponentRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<CompositeComponentRemovalTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            INSTANCES_REMOVING,
            COMPOSITE_REMOVING,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(INSTANCES_REMOVING));
        }

        @Documentation(
                description = "(Required) The composites on which the given operation will be applied.")
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;
    }

    // Order of batch remove of resources, grouped by resource type.
    private static final List<ResourceType> PREFERED_ORDER_OF_REMOVAL_PER_TYPE = Arrays.asList(
            ResourceType.CONTAINER_TYPE, ResourceType.LOAD_BALANCER_TYPE, ResourceType.COMPUTE_TYPE,
            ResourceType.NETWORK_TYPE, ResourceType.COMPUTE_NETWORK_TYPE,
            ResourceType.VOLUME_TYPE, ResourceType.CLOSURE_TYPE);

    @SuppressWarnings("unused")
    private static final List<ResourceType> TYPES_SUPPORTING_PARALLEL_REMOVAL = Arrays.asList(
            ResourceType.JOIN_COMPOSITE_COMPONENT_TYPE);

    public CompositeComponentRemovalTaskService() {
        super(CompositeComponentRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(CompositeComponentRemovalTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            queryComponentResources(state);
            break;
        case INSTANCES_REMOVING:
            break;
        case COMPOSITE_REMOVING:
            removeCompositeComponents(state);
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

    private void queryComponentResources(CompositeComponentRemovalTaskState state) {
        QueryTask compositeQueryTask = QueryUtil.buildQuery(CompositeComponent.class, true);

        QueryUtil.addExpandOption(compositeQueryTask);
        QueryUtil.addListValueClause(compositeQueryTask, CompositeComponent.FIELD_NAME_SELF_LINK,
                state.resourceLinks);

        List<String> resourceLinks = new ArrayList<String>();
        Map<String, String> externalSchedulerTaskPerResourceLink = new HashMap<>();

        new ServiceDocumentQuery<CompositeComponent>(getHost(), CompositeComponent.class)
                .query(compositeQueryTask,
                        (r) -> {
                            if (r.hasException()) {
                                logSevere("Failed to create operation task for %s - %s",
                                        r.getDocumentSelfLink(), Utils.toString(r.getException()));
                            } else if (r.hasResult()) {
                                CompositeComponent cc = r.getResult();
                                if (isKubernetesComposite(cc)) {
                                    resourceLinks.add(cc.documentSelfLink);
                                    externalSchedulerTaskPerResourceLink.put(cc.documentSelfLink,
                                            CompositeKubernetesRemovalTaskService.FACTORY_LINK);
                                } else {
                                    List<String> componentLinks = r.getResult().componentLinks;
                                    if (componentLinks != null) {
                                        resourceLinks.addAll(componentLinks);
                                    }
                                }
                            } else {
                                if (resourceLinks.isEmpty()) {
                                    logWarning("Composite component's resource links are empty");
                                    proceedTo(SubStage.COMPOSITE_REMOVING);
                                    return;
                                }

                                performResourceRemovalOperations(state, resourceLinks,
                                        externalSchedulerTaskPerResourceLink);
                            }
                        });
    }

    private void removeCompositeComponents(CompositeComponentRemovalTaskState state) {
        List<Operation> operations = state.resourceLinks.stream()
                .map((selfLink) -> Operation.createDelete(this, selfLink))
                .collect(Collectors.toList());

        OperationJoin.create(operations)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        // ignore CancellationException when removing composite component
                        exs = exs.entrySet().stream()
                                .filter((e) -> !(e.getValue() instanceof CancellationException))
                                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
                    }
                    if (exs != null && !exs.isEmpty()) {
                        failTask("Failed removing composite states: " + Utils.toString(exs), null);
                        return;
                    }

                    proceedTo(SubStage.COMPLETED);
                })
                .sendWith(this);
    }

    private void performResourceRemovalOperations(CompositeComponentRemovalTaskState state,
            List<String> resourceLinks, Map<String, String> externalSchedulerTaskPerResourceLink) {

        Map<ResourceType, Set<String>> resourceLinksByResourceType = new HashMap<>();

        for (String link : resourceLinks) {
            ResourceType rt;

            if (externalSchedulerTaskPerResourceLink.containsKey(link)) {
                // Will handle the composite component at once, instead of separating on it's
                // sub components
                rt = ResourceType.JOIN_COMPOSITE_COMPONENT_TYPE;
            } else {
                ComponentMeta metaByStateLink = CompositeComponentRegistry.metaByStateLink(link);
                rt = ResourceType.fromName(metaByStateLink.resourceType);
            }

            Set<String> list = resourceLinksByResourceType.get(rt);
            if (list == null) {
                list = new HashSet<>();
                resourceLinksByResourceType.put(rt, list);
            }
            list.add(link);
        }

        List<ResourceTypeRemovalNode> resourceLinksByNodeOrder = new ArrayList<>();

        for (ResourceType rt : PREFERED_ORDER_OF_REMOVAL_PER_TYPE) {
            Set<String> links = resourceLinksByResourceType.remove(rt);
            if (links == null) {
                continue;
            }

            ResourceTypeRemovalNode currNode = new ResourceTypeRemovalNode();
            currNode.name = rt.getName();
            currNode.type = rt;
            currNode.resourceLinks = links;

            if (!resourceLinksByNodeOrder.isEmpty()) {
                ResourceTypeRemovalNode lastNode = resourceLinksByNodeOrder
                        .get(resourceLinksByNodeOrder.size() - 1);
                currNode.prevNode = lastNode.name;
                lastNode.nextNode = currNode.name;
            }

            resourceLinksByNodeOrder.add(currNode);
        }

        Map<String, ResourceTypeRemovalNode> resourceLinksForParallelRemoval = new HashMap<>();

        Set<String> joinedCompositeComponentLinks = resourceLinksByResourceType.remove
                (ResourceType.JOIN_COMPOSITE_COMPONENT_TYPE);
        if (joinedCompositeComponentLinks != null) {
            for (String link : joinedCompositeComponentLinks) {
                String externalSchedulerLink = externalSchedulerTaskPerResourceLink.get(link);
                ResourceTypeRemovalNode node = resourceLinksForParallelRemoval.get
                        (externalSchedulerLink);
                if (node == null) {
                    node = new ResourceTypeRemovalNode();
                    node.name = externalSchedulerLink;
                    node.type = ResourceType.JOIN_COMPOSITE_COMPONENT_TYPE;
                    node.resourceLinks = new HashSet<>();
                    node.externalSchedulerRemovalTaskLink = externalSchedulerLink;
                    resourceLinksForParallelRemoval.put(externalSchedulerLink, node);
                }
                node.resourceLinks.add(link);
            }
        }

        if (!resourceLinksByResourceType.isEmpty()) {
            failTask(
                    "Unknown order of removal for resource types: "
                            + resourceLinksByResourceType.keySet(), null);
        }

        List<ResourceTypeRemovalNode> resourceLinksForRemoval = new ArrayList<>(
                resourceLinksByNodeOrder);
        resourceLinksForRemoval.addAll(resourceLinksForParallelRemoval.values());

        performResourceRemovalOperations(state, resourceLinksForRemoval, resourceLinks.size(),
                null);
    }

    private void performResourceRemovalOperations(CompositeComponentRemovalTaskState state,
            List<ResourceTypeRemovalNode> resourceLinksForRemoval, int resourceCount,
            ServiceTaskCallback taskCallback) {
        if (taskCallback == null) {
            createCounterSubTaskCallback(
                    state,
                    resourceLinksForRemoval.size(),
                    false,
                    true,
                    SubStage.COMPOSITE_REMOVING,
                    (serviceTask) -> performResourceRemovalOperations(state,
                            resourceLinksForRemoval,
                            resourceCount, serviceTask));
            return;
        }

        final AtomicBoolean error = new AtomicBoolean();

        try {
            logInfo("Starting removal of %d resources", resourceCount);
            for (ResourceTypeRemovalNode node : resourceLinksForRemoval) {
                sendResourceRemovalRequest(state, node, taskCallback, (o, e) -> {
                    if (e != null) {
                        if (error.compareAndSet(false, true)) {
                            failTask("Failure creating composition subTask", e);
                        } else {
                            logWarning("Failure creating composition subTask. Error: %s",
                                    Utils.toString(e));
                        }
                    }
                });
            }
        } catch (Throwable e) {
            failTask("Unexpected exception while requesting removal.", e);
        }
    }

    private void sendResourceRemovalRequest(final CompositeComponentRemovalTaskState state,
            ResourceTypeRemovalNode removalNode,
            ServiceTaskCallback taskCallback,
            final CompletionHandler completionHandler) {
        if (CompositeKubernetesRemovalTaskService.FACTORY_LINK.equals(removalNode
                .externalSchedulerRemovalTaskLink)) {
            sendToKubernetesScheduler(state, removalNode, taskCallback,
                    completionHandler);
        } else {
            sendResourceRemovalRequestForComponent(state, removalNode, taskCallback,
                    completionHandler);
        }
    }

    private void sendResourceRemovalRequestForComponent(final CompositeComponentRemovalTaskState
            state,
            ResourceTypeRemovalNode removalNode,
            ServiceTaskCallback taskCallback,
            final CompletionHandler completionHandler) {
        final CompositionSubTaskState compositionSubTask = new CompositionSubTaskState();
        compositionSubTask.documentSelfLink = buildCompositionSubTaskLink(removalNode.name);
        compositionSubTask.requestId = getSelfId();
        compositionSubTask.name = removalNode.name;
        compositionSubTask.resourceType = removalNode.type.getName();
        compositionSubTask.tenantLinks = state.tenantLinks;
        compositionSubTask.resourceLinks = removalNode.resourceLinks;
        compositionSubTask.requestTrackerLink = state.requestTrackerLink;
        compositionSubTask.serviceTaskCallback = taskCallback;
        compositionSubTask.customProperties = state.customProperties;
        compositionSubTask.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;

        if (removalNode.prevNode != null) {
            compositionSubTask.dependsOnLinks = new HashSet<>(Arrays.asList(
                    buildCompositionSubTaskLink(removalNode.prevNode)));
        }

        if (removalNode.nextNode != null) {
            compositionSubTask.dependentLinks = Collections
                    .singleton(buildCompositionSubTaskLink(removalNode.nextNode));
        }

        sendRequest(Operation.createPost(this, CompositionSubTaskFactoryService.SELF_LINK)
                .setBody(compositionSubTask)
                .setContextId(compositionSubTask.requestId)
                .setCompletion(completionHandler));
    }

    private String buildCompositionSubTaskLink(String name) {
        final String compositionSubTaskId = getSelfId() + "-" + name;
        return UriUtils.buildUriPath(CompositionSubTaskFactoryService.SELF_LINK,
                compositionSubTaskId);
    }

    private void sendToKubernetesScheduler(CompositeComponentRemovalTaskState state,
            ResourceTypeRemovalNode removalNode,
            ServiceTaskCallback taskCallback,
            final CompletionHandler completionHandler) {

        if (removalNode.resourceLinks.size() == 0) {
            throw new IllegalStateException(
                    "Kubernetes composite component with 0 resource links.");
        }

        CompositeKubernetesRemovalTaskState task = new CompositeKubernetesRemovalTaskState();
        task.documentSelfLink = getSelfId();
        task.serviceTaskCallback = taskCallback;
        task.customProperties = state.customProperties;
        task.resourceLinks = removalNode.resourceLinks;
        task.tenantLinks = state.tenantLinks;
        task.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation
                .createPost(this, CompositeKubernetesRemovalTaskService.FACTORY_LINK)
                .setBody(task)
                .setContextId(getSelfId())
                .setCompletion(completionHandler));
    }

    private boolean isKubernetesComposite(CompositeComponent component) {
        if (component.componentLinks == null || component.componentLinks.isEmpty()) {
            return false;
        }
        for (String link : component.componentLinks) {
            Class<? extends ResourceState> componentClass = CompositeComponentRegistry
                    .metaByStateLink(link).stateClass;
            if (!BaseKubernetesState.class.isAssignableFrom(componentClass)) {
                return false;
            }
        }
        return true;
    }

    private static class ResourceTypeRemovalNode {
        private String name;
        private ResourceType type;
        private String prevNode;
        private String nextNode;
        private Set<String> resourceLinks;
        private String externalSchedulerRemovalTaskLink;
    }
}
