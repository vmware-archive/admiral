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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.composition.CompositeComponentRemovalTaskService.CompositeComponentRemovalTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.TaskState.TaskStage;
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
        public List<String> resourceLinks;
    }

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

    @Override
    protected boolean validateStageTransition(Operation patch,
            CompositeComponentRemovalTaskState patchBody,
            CompositeComponentRemovalTaskState currentState) {
        return false;
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
        Map<ResourceType, List<String>> resourceLinksByResourceType = new HashMap<>();

        for (String link : resourceLinks) {
            ComponentMeta metaByStateLink = CompositeComponentRegistry.metaByStateLink(link);
            ResourceType rt = ResourceType.fromName(metaByStateLink.resourceType);
            List<String> list = resourceLinksByResourceType.get(rt);
            if (list == null) {
                list = new ArrayList<>();
                resourceLinksByResourceType.put(rt, list);
            }
            list.add(link);
        }

        performResourceRemovalOperations(state, resourceLinksByResourceType, resourceLinks.size(), null);
    }

    private void performResourceRemovalOperations(CompositeComponentRemovalTaskState state,
            Map<ResourceType, List<String>> resourceLinksByResourceType, int resourceCount, ServiceTaskCallback taskCallback) {
        if (taskCallback == null) {
            ServiceTaskCallback.create(state.documentSelfLink,
                    TaskStage.STARTED, SubStage.COMPOSITE_REMOVING, TaskStage.FAILED, SubStage.ERROR);

            createCounterSubTaskCallback(state, resourceLinksByResourceType.size(), false, true,
                    SubStage.COMPOSITE_REMOVING,
                    (serviceTask) -> performResourceRemovalOperations(state, resourceLinksByResourceType, resourceCount, serviceTask));
            return;
        }

        try {
            logInfo("Starting removal of %d resources", resourceCount);
            for (Entry<ResourceType, List<String>> e : resourceLinksByResourceType.entrySet()) {
                sendResourceRemovalRequest(state, e.getKey(), e.getValue(), taskCallback);
            }
        } catch (Throwable e) {
            failTask("Unexpected exception while requesting removal.", e);
        }
    }

    private void sendResourceRemovalRequest(CompositeComponentRemovalTaskState state,
            ResourceType resourceType, List<String> resourceLinks, ServiceTaskCallback taskCallback) {
        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.documentSelfLink = String.format("%s-%s-removal", getSelfId(), resourceType.getName());
        requestBrokerState.serviceTaskCallback = taskCallback;
        requestBrokerState.customProperties = state.customProperties;

        requestBrokerState.resourceType = resourceType.getName();
        requestBrokerState.resourceLinks = resourceLinks;
        requestBrokerState.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
        requestBrokerState.tenantLinks = state.tenantLinks;
        requestBrokerState.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation
                .createPost(this, RequestBrokerFactoryService.SELF_LINK)
                .setBody(requestBrokerState)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failure creating request broker task. Error: [%s]",
                                Utils.toString(e));
                        completeSubTasksCounter(taskCallback, e);
                    }

                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.INSTANCES_REMOVING));
                }));
    }
}
