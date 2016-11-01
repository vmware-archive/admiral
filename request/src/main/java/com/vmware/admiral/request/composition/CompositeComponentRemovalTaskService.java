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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.request.ContainerRemovalTaskService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.composition.CompositeComponentRemovalTaskService.CompositeComponentRemovalTaskState.SubStage;
import com.vmware.admiral.request.composition.CompositionSubTaskService.CompositionSubTaskState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
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

        @Documentation(description = "(Required) The composites on which the given operation will be applied.")
        public Set<String> resourceLinks;
    }

    // Order of batch remove of resources, grouped by resource type.
    private static final List<ResourceType> PREFERED_ORDER_OF_REMOVAL_PER_TYPE = Arrays.asList(
            ResourceType.CONTAINER_TYPE, ResourceType.COMPUTE_TYPE, ResourceType.NETWORK_TYPE,
            ResourceType.VOLUME_TYPE
            );

    public CompositeComponentRemovalTaskService() {
        super(CompositeComponentRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(CompositeComponentRemovalTaskState state)
            throws IllegalArgumentException {
        AssertUtil.assertNotEmpty(state.resourceLinks, "resourceLinks");
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
            complete(state, SubStage.COMPLETED);
            break;
        case ERROR:
            completeWithError(state, SubStage.ERROR);
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
        new ServiceDocumentQuery<CompositeComponent>(getHost(), CompositeComponent.class)
                .query(compositeQueryTask,
                        (r) -> {
                            if (r.hasException()) {
                                logSevere("Failed to create operation task for %s - %s",
                                        r.getDocumentSelfLink(), r.getException());
                            } else if (r.hasResult()) {
                                List<String> componentLinks = r.getResult().componentLinks;
                                if (componentLinks != null) {
                                    resourceLinks.addAll(componentLinks);
                                }
                            } else {
                                if (resourceLinks.isEmpty()) {
                                    logWarning("Composite component's resource links are empty");
                                    sendSelfPatch(createUpdateSubStageTask(state,
                                            SubStage.COMPOSITE_REMOVING));
                                    return;
                                }

                                performResourceRemovalOperations(state, resourceLinks);
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
                        failTask("Failed removing composite states: " + Utils.toString(exs), null);
                        return;
                    }

                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
                })
                .sendWith(this);
    }

    private void performResourceRemovalOperations(CompositeComponentRemovalTaskState state,
            List<String> resourceLinks) {
        Map<ResourceType, Set<String>> resourceLinksByResourceType = new HashMap<>();

        for (String link : resourceLinks) {
            ComponentMeta metaByStateLink = CompositeComponentRegistry.metaByStateLink(link);
            ResourceType rt = ResourceType.fromName(metaByStateLink.resourceType);
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

        if (!resourceLinksByResourceType.isEmpty()) {
            failTask(
                    "Unknown order of removal for resource types: "
                            + resourceLinksByResourceType.keySet(), null);
        }

        performResourceRemovalOperations(state, resourceLinksByNodeOrder, resourceLinks.size(),
                null);
    }

    private void performResourceRemovalOperations(CompositeComponentRemovalTaskState state,
            List<ResourceTypeRemovalNode> resourceLinksByNodeOrder, int resourceCount,
            ServiceTaskCallback taskCallback) {
        if (taskCallback == null) {
            createCounterSubTaskCallback(
                    state,
                    resourceLinksByNodeOrder.size(),
                    false,
                    true,
                    SubStage.COMPOSITE_REMOVING,
                    (serviceTask) -> performResourceRemovalOperations(state,
                            resourceLinksByNodeOrder, resourceCount, serviceTask));
            return;
        }

        final AtomicBoolean error = new AtomicBoolean();

        try {
            logInfo("Starting removal of %d resources", resourceCount);
            for (ResourceTypeRemovalNode node : resourceLinksByNodeOrder) {
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

    private static class ResourceTypeRemovalNode {
        private String name;
        private ResourceType type;
        private String prevNode;
        private String nextNode;
        private Set<String> resourceLinks;
    }
}
