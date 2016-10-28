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

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.dataformat.yaml.snakeyaml.util.UriEncoder;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.BindingEvaluator;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.content.Binding;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.request.composition.CompositeComponentRemovalTaskService.CompositeComponentRemovalTaskState;
import com.vmware.admiral.request.composition.CompositionGraph.ResourceNode;
import com.vmware.admiral.request.composition.CompositionSubTaskService.CompositionSubTaskState;
import com.vmware.admiral.request.composition.CompositionTaskService.CompositionTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Task implementing the provision multi-container request life-cycle.
 */
public class CompositionTaskService
        extends
        AbstractTaskStatefulService<CompositionTaskService.CompositionTaskState, CompositionTaskService.CompositionTaskState.SubStage> {
    public static final String DISPLAY_NAME = "Composition";

    public static class CompositionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<CompositionTaskState.SubStage> {

        private static final String FIELD_NAME_RESOURCE_DESCRIPTION_LINK = "resourceDescriptionLink";
        private static final String FIELD_NAME_RESOURCE_NODES = "resourceNodes";
        private static final String FIELD_NAME_COMPOSITE_COMPONENT_LINK = "compositeComponentLink";

        public static enum SubStage {
            CREATED,
            CONTEXT_PREPARED,
            DEPENDENCY_GRAPH,
            DISTRIBUTING,
            ALLOCATING,
            ERROR_ALLOCATING,
            ALLOCATED,
            DISTRIBUTE_TASKS,
            PROVISIONING,
            ERROR_PROVISIONING,
            COMPLETED,
            ERROR,
            FAILED;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(DISTRIBUTING, DISTRIBUTE_TASKS));
        }

        /** The description that defines the requested resource. */
        public String resourceDescriptionLink;

        // Service use fields:
        /** ResourceNodes by CompositionSubTask links */
        public Map<String, ResourceNode> resourceNodes;

        /** Set by Task. Link to the CompositeComponent */
        public String compositeComponentLink;

        /** Set by Task. The count of the current allocations completed. */
        public Long remainingCount;

        /** Set by Task. Error count of the current allocations. */
        public long errorCount;

        /** (Internal) Set by task with ContainerDescription name. */
        public String descName;

    }

    public CompositionTaskService() {
        super(CompositionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(CompositionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            prepareContext(state, null);
            break;
        case CONTEXT_PREPARED:
            calculateResourceDependencyGraph(state, null);
            break;
        case DEPENDENCY_GRAPH:
            distributeTasks(state);
            break;
        case DISTRIBUTING:
            break;
        case ALLOCATING:
            counting(state, true);
            break;
        case ERROR_ALLOCATING:
            transitionToErrorIfNoRemaining(state);
            break;
        case ALLOCATED:
            patchSubTask(state);
            break;
        case DISTRIBUTE_TASKS:
            break;
        case PROVISIONING:
            counting(state, false);
            break;
        case ERROR_PROVISIONING:
            transitionToErrorIfNoRemaining(state);
            break;
        case COMPLETED:
            complete(state, SubStage.COMPLETED);
            break;
        case ERROR:
            cleanResource(state);
            break;
        default:
            break;
        }
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            CompositionTaskState patchBody, CompositionTaskState currentState) {
        currentState.compositeComponentLink = mergeProperty(currentState.compositeComponentLink,
                patchBody.compositeComponentLink);
        currentState.remainingCount = mergeProperty(currentState.remainingCount,
                patchBody.remainingCount);
        currentState.resourceNodes = mergeProperty(currentState.resourceNodes,
                patchBody.resourceNodes);
        currentState.descName = mergeProperty(currentState.descName, patchBody.descName);

        if (currentState.taskInfo != null
                && TaskStage.STARTED == currentState.taskInfo.stage
                && (SubStage.ALLOCATING == patchBody.taskSubStage
                        || SubStage.ERROR_ALLOCATING == patchBody.taskSubStage
                        || SubStage.PROVISIONING == patchBody.taskSubStage
                        || SubStage.ERROR_PROVISIONING == patchBody.taskSubStage)
                && currentState.remainingCount != null
                && currentState.remainingCount > 0
                && patch.getReferer() != null
                && patch.getReferer().getPath() != null) {
            String patchSelfLink = patch.getReferer().getPath();
            // count down how many of the subTask have callback by referrer link:
            final ResourceNode resourceNode = currentState.resourceNodes.get(patchSelfLink);
            if (resourceNode != null) {
                currentState.remainingCount--;
                logInfo("Remaining count: [%s]. Stage: [%s]. Completion of resource name: [%s] composition sub-task [%s] patched.",
                        currentState.remainingCount, patchBody.taskSubStage, resourceNode.name,
                        patchSelfLink);
            } else {
                logWarning(
                        "Remaining count: [%s]. Completion of composition sub-task [%s] patched but not found in the list.",
                        currentState.remainingCount, patchSelfLink);
            }
        } else if (SubStage.ERROR_ALLOCATING == patchBody.taskSubStage) {
            logWarning("No remaining count: %s", currentState.remainingCount);
        }

        if (TaskStage.STARTED == patchBody.taskInfo.stage) {
            if (SubStage.ERROR_ALLOCATING == patchBody.taskSubStage) {
                if (currentState.remainingCount != null && currentState.remainingCount > 0) {
                    currentState.taskSubStage = SubStage.ALLOCATING;
                    currentState.errorCount = currentState.errorCount + 1;
                }
            } else if (SubStage.ERROR_PROVISIONING == patchBody.taskSubStage) {
                if (currentState.remainingCount != null && currentState.remainingCount > 0) {
                    currentState.taskSubStage = SubStage.PROVISIONING;
                    currentState.errorCount = currentState.errorCount + 1;
                }
            }
        }

        return false;
    }

    @Override
    protected void validateStateOnStart(CompositionTaskState state) {
        assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            CompositionTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = Collections.singletonList(state.compositeComponentLink);
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        List<String> resourceLinks;
    }

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        final TaskStatusState statusTask = super.fromTask(state);
        if (SubStage.CONTEXT_PREPARED == state.taskSubStage) {
            CompositionTaskState currentState = (CompositionTaskState) state;
            statusTask.name = currentState.descName;
            statusTask.resourceLinks = new ArrayList<>();
            statusTask.resourceLinks.add(currentState.compositeComponentLink);
        }

        return statusTask;
    }

    private void calculateResourceDependencyGraph(final CompositionTaskState state,
            final CompositeDescriptionExpanded compositeDesc) {

        if (compositeDesc == null) {
            getCompositeDescription(state, true,
                    (compDesc) -> this.calculateResourceDependencyGraph(state, compDesc));
            return;
        }

        CompositionGraph compositionGraph = new CompositionGraph();

        try {
            state.resourceNodes = compositionGraph
                    .calculateGraph(compositeDesc)
                    .stream().collect(Collectors.toMap(
                            (r) -> buildCompositionSubTaskLink(r.name), Function.identity()));

            CompositionTaskState body = createUpdateSubStageTask(state, SubStage.DEPENDENCY_GRAPH);
            body.resourceNodes = state.resourceNodes;
            body.remainingCount = (long) state.resourceNodes.size();

            sendSelfPatch(body);

        } catch (Exception e) {
            state.taskInfo.failure = Utils.toServiceErrorResponse(e);
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.ERROR));
        }
    }

    private void updateComponentsInRequestTracker(CompositionTaskState state) {
        try {
            // update resource tracker with number of components
            RequestStatus requestStatus = new RequestStatus();
            requestStatus.components = new ArrayList<>(state.resourceNodes.values());

            sendRequest(Operation
                    .createPatch(this, state.requestTrackerLink)
                    .setBody(requestStatus)
                    .setCompletion(
                            (o, ex) -> {
                                if (ex != null) {
                                    logSevere(
                                            "Failed to update components in request tracker [%s], progress will be innacurate: %s",
                                            state.requestTrackerLink, Utils.toString(ex));
                                }
                            }));

        } catch (Throwable x) {
            logSevere(
                    "Failed to update components in request tracker [%s], progress will be innacurate: %s",
                    state.requestTrackerLink, Utils.toString(x));
        }
    }

    private void distributeTasks(final CompositionTaskState state) {
        if (state.requestTrackerLink != null) {
            updateComponentsInRequestTracker(state);
        }

        final AtomicBoolean error = new AtomicBoolean();
        for (final Map.Entry<String, ResourceNode> entry : state.resourceNodes.entrySet()) {
            final ResourceNode resourceNode = entry.getValue();
            final String subTaskSelfLink = entry.getKey();
            createCompositionSubTask(state, resourceNode, subTaskSelfLink, (o, e) -> {
                if (e != null) {
                    if (error.compareAndSet(false, true)) {
                        failTask("Failure creating composition subTask: " + subTaskSelfLink, e);
                    } else {
                        logWarning(// task already failed
                                "Failure creating composition subTask: [%s]. Error: %s",
                                subTaskSelfLink, Utils.toString(e));
                    }
                    return;
                }
                logFine("Composition subTask created: " + subTaskSelfLink);
                if (!error.get()) {
                    logFine("Composition subTask creation completed successfully.");
                }
            });
        }

        sendSelfPatch(createUpdateSubStageTask(state, SubStage.DISTRIBUTING));
    }

    private void createCompositionSubTask(final CompositionTaskState state,
            final ResourceNode resourceNode, String subTaskSelfLink,
            final CompletionHandler completionHandler) {
        final CompositionSubTaskState compositionSubTask = new CompositionSubTaskState();
        compositionSubTask.documentSelfLink = subTaskSelfLink;
        compositionSubTask.requestId = getSelfId();
        compositionSubTask.name = resourceNode.name;
        compositionSubTask.resourceType = resourceNode.resourceType;
        compositionSubTask.tenantLinks = state.tenantLinks;
        compositionSubTask.resourceDescriptionLink = resourceNode.resourceDescLink;
        compositionSubTask.requestTrackerLink = state.requestTrackerLink;
        compositionSubTask.customProperties = state.customProperties;
        compositionSubTask.allocationRequest = true;
        compositionSubTask.operation = RequestBrokerState.PROVISION_RESOURCE_OPERATION;
        compositionSubTask.compositeDescriptionLink = state.resourceDescriptionLink;

        if (resourceNode.dependsOn != null && !resourceNode.dependsOn.isEmpty()) {
            compositionSubTask.dependsOnLinks = resourceNode.dependsOn
                    .stream().map((r) -> buildCompositionSubTaskLink(r))
                    .collect(Collectors.toSet());
        }

        if (resourceNode.dependents != null && !resourceNode.dependents.isEmpty()) {
            compositionSubTask.dependentLinks = resourceNode.dependents
                    .stream().map((r) -> buildCompositionSubTaskLink(r))
                    .collect(Collectors.toSet());
        }

        compositionSubTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.ALLOCATING,
                TaskStage.STARTED, SubStage.ERROR_ALLOCATING);

        sendRequest(Operation.createPost(this, CompositionSubTaskFactoryService.SELF_LINK)
                .setBody(compositionSubTask)
                .setContextId(compositionSubTask.requestId)
                .setCompletion(completionHandler));
    }

    private void patchSubTask(CompositionTaskState state) {
        final AtomicBoolean error = new AtomicBoolean();
        final AtomicInteger countDown = new AtomicInteger(state.resourceNodes.size());
        for (final Map.Entry<String, ResourceNode> entry : state.resourceNodes.entrySet()) {
            final ResourceNode resourceNode = entry.getValue();
            final String subTaskSelfLink = entry.getKey();
            // patch each subtask to PREPARE_EXECUTE, and set new callback and dependsOn
            patchCompositionSubTask(state, resourceNode, subTaskSelfLink, (o, e) -> {
                if (e != null) {
                    if (error.compareAndSet(false, true)) {
                        failTask("Failure patching composition subTask: " + subTaskSelfLink, e);
                    } else {
                        logWarning(// task already failed
                                "Failure patching composition subTask: [%s]. Error: %s",
                                subTaskSelfLink, Utils.toString(e));
                    }
                    return;
                }
                logFine("Composition subTask patched: " + subTaskSelfLink);
                if (!error.get()) {
                    logFine("Composition subTask patch completed successfully.");
                }
                // patch all subtasks to execute when all of them are prepared
                if (countDown.decrementAndGet() == 0 && !error.get()) {
                    patchSubTaskToExecute(state);
                }
            });
        }
    }

    private void patchCompositionSubTask(final CompositionTaskState state,
            final ResourceNode resourceNode, String subTaskSelfLink,
            final CompletionHandler completionHandler) {
        final CompositionSubTaskState compositionSubTask = new CompositionSubTaskState();
        compositionSubTask.documentSelfLink = subTaskSelfLink;
        if (resourceNode.dependsOn != null && !resourceNode.dependsOn.isEmpty()) {
            compositionSubTask.dependsOnLinks = resourceNode.dependsOn
                    .stream().map((r) -> buildCompositionSubTaskLink(r))
                    .collect(Collectors.toSet());
        }
        compositionSubTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.PROVISIONING,
                TaskStage.STARTED, SubStage.ERROR_PROVISIONING);
        compositionSubTask.taskInfo = new TaskState();
        compositionSubTask.taskInfo.stage = TaskStage.STARTED;
        compositionSubTask.taskSubStage = CompositionSubTaskState.SubStage.PREPARE_EXECUTE;

        sendRequest(Operation.createPatch(this, subTaskSelfLink)
                .setBody(compositionSubTask)
                .setContextId(compositionSubTask.requestId)
                .setCompletion(completionHandler));
    }

    private void patchSubTaskToExecute(CompositionTaskState state) {
        final AtomicBoolean error = new AtomicBoolean();
        // patch each subtask to EXECUTE
        for (final String subTaskSelfLink : state.resourceNodes.keySet()) {
            patchCompositionSubTaskToExecute(subTaskSelfLink, (o, e) -> {
                if (e != null) {
                    if (error.compareAndSet(false, true)) {
                        failTask("Failure patching composition subTask: " + subTaskSelfLink, e);
                    } else {
                        logWarning(// task already failed
                                "Failure patching composition subTask: [%s]. Error: %s",
                                subTaskSelfLink, Utils.toString(e));
                    }
                    return;
                }
                logFine("Composition subTask patched: " + subTaskSelfLink);
                if (!error.get()) {
                    logFine("Composition subTask patch completed successfully.");
                }
            });
        }

        sendSelfPatch(createUpdateSubStageTask(state, SubStage.DISTRIBUTE_TASKS));
    }

    private void patchCompositionSubTaskToExecute(
            String subTaskSelfLink,
            CompletionHandler completionHandler) {
        final CompositionSubTaskState compositionSubTask = new CompositionSubTaskState();
        compositionSubTask.documentSelfLink = subTaskSelfLink;
        compositionSubTask.taskInfo = new TaskState();
        compositionSubTask.taskInfo.stage = TaskStage.STARTED;
        compositionSubTask.taskSubStage = CompositionSubTaskState.SubStage.EXECUTE;

        sendRequest(Operation.createPatch(this, subTaskSelfLink)
                .setBody(compositionSubTask)
                .setContextId(compositionSubTask.requestId)
                .setCompletion(completionHandler));
    }

    private String buildCompositionSubTaskLink(String name) {
        final String compositionSubTaskId = getSelfId() + "-" + UriEncoder.encode(name);
        return UriUtils.buildUriPath(CompositionSubTaskFactoryService.SELF_LINK,
                compositionSubTaskId);
    }

    private void counting(CompositionTaskState state, boolean allocate) {
        if (state.remainingCount == 0) {
            if (state.errorCount > 0) {
                sendSelfPatch(createUpdateSubStageTask(state, SubStage.ERROR));
            } else {
                CompositionTaskState body = createUpdateSubStageTask(state, SubStage.ALLOCATED);
                if (allocate) {
                    body.remainingCount = (long) state.resourceNodes.size();
                } else {
                    body.taskSubStage = SubStage.COMPLETED;
                }
                sendSelfPatch(body);
            }
        } else {
            logFine("CompositeTask patched - remaining subTasks in progress : %s",
                    state.remainingCount);
        }
    }

    private void transitionToErrorIfNoRemaining(CompositionTaskState state) {
        if (state.remainingCount == null || state.remainingCount == 0) {
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.ERROR));
        }
    }

    private void prepareContext(final CompositionTaskState state,
            final CompositeDescription compositeDesc) {
        if (compositeDesc == null) {
            getCompositeDescription(state, false, (compDesc) -> prepareContext(state, compDesc));
            return;
        }

        state.customProperties = mergeProperty(compositeDesc.customProperties,
                state.customProperties);
        if (state.customProperties == null) {
            state.customProperties = new HashMap<>();
        }

        // add contextId if not added
        String contextId;
        if ((contextId = state.customProperties.get(FIELD_NAME_CONTEXT_ID_KEY)) == null) {
            contextId = getSelfId();
            state.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        }

        final CompositeComponent component = new CompositeComponent();
        component.documentSelfLink = contextId;
        component.name = compositeDesc.name;
        component.compositeDescriptionLink = compositeDesc.documentSelfLink;
        component.tenantLinks = compositeDesc.tenantLinks;

        sendRequest(Operation
                .createPost(this, CompositeComponentFactoryService.SELF_LINK)
                .setBody(component)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                failTask("Failure creating CompositeComponent: "
                                        + component.documentSelfLink, e);
                                return;
                            }

                            CompositeComponent compComponent = o.getBody(CompositeComponent.class);
                            logInfo("CompositeComponent created [%s]",
                                    compComponent.documentSelfLink);

                            final CompositionTaskState body = createUpdateSubStageTask(state,
                                    SubStage.CONTEXT_PREPARED);
                            body.customProperties = state.customProperties;
                            body.compositeComponentLink = compComponent.documentSelfLink;
                            body.descName = compositeDesc.name;
                            sendSelfPatch(body);
                        }));
    }

    private void getCompositeDescription(CompositionTaskState state, boolean expanded,
            Consumer<CompositeDescriptionExpanded> callbackFunction) {
        URI uri = UriUtils.buildUri(this.getHost(), state.resourceDescriptionLink);
        sendRequest(Operation.createGet(uri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving composite description state", e);
                        return;
                    }
                    try {
                        CompositeDescriptionExpanded desc = o
                                .getBody(CompositeDescriptionExpanded.class);
                        if (expanded) {
                            retrieveComponentDescriptions(desc, (descExpanded) -> {
                                if (descExpanded.bindings != null) {
                                    BindingEvaluator.evaluateBindings(descExpanded);
                                    Operation.createPut(this, descExpanded.documentSelfLink)
                                            .setBody(descExpanded)
                                            .setCompletion((op, ex) -> {
                                                if (ex != null) {
                                                    failTask(
                                                            "Failure updating evaluated composite description",
                                                            ex);
                                                    return;
                                                }
                                                callbackFunction.accept(descExpanded);
                                            }).sendWith(this);
                                } else {
                                    callbackFunction.accept(descExpanded);
                                }
                            });
                        } else {
                            callbackFunction.accept(desc);
                        }
                    } catch (Exception ex) {
                        failTask("Failure updating evaluated composite description", ex);
                    }
                }));
    }

    private void cleanResource(CompositionTaskState state) {
        boolean cleanUpComposite = state.compositeComponentLink != null;

        if (!cleanUpComposite) {
            logInfo("Error count: [%s]. No resources to clean.", state.errorCount);
            completeWithError(state, SubStage.FAILED);
            return;
        }

        CompositeComponentRemovalTaskState removalTaskState = new CompositeComponentRemovalTaskState();
        removalTaskState.documentSelfLink = getSelfId() + "-cleanup";
        removalTaskState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.FAILED, SubStage.FAILED, TaskStage.FAILED, SubStage.FAILED);
        removalTaskState.customProperties = state.customProperties;
        removalTaskState.resourceLinks = Collections.singletonList(state.compositeComponentLink);
        removalTaskState.tenantLinks = state.tenantLinks;
        removalTaskState.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation
                .createPost(this, CompositeComponentRemovalTaskService.FACTORY_LINK)
                .setBody(removalTaskState)
                .setContextId(getSelfId())
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(
                                        "Failure creating composite component removal task. Error: [%s]",
                                        Utils.toString(e));
                                completeWithError(state, SubStage.FAILED);
                            }
                        }));
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();

        setDocumentTemplateIndexingOptions(template, EnumSet.of(PropertyIndexingOption.STORE_ONLY),
                CompositionTaskState.FIELD_NAME_RESOURCE_DESCRIPTION_LINK,
                CompositionTaskState.FIELD_NAME_TENANT_LINKS,
                CompositionTaskState.FIELD_NAME_RESOURCE_NODES,
                CompositionTaskState.FIELD_NAME_COMPOSITE_COMPONENT_LINK);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SINGLE_ASSIGNMENT),
                CompositionTaskState.FIELD_NAME_RESOURCE_DESCRIPTION_LINK,
                CompositionTaskState.FIELD_NAME_TENANT_LINKS,
                CompositionTaskState.FIELD_NAME_COMPOSITE_COMPONENT_LINK);

        setDocumentTemplateUsageOptions(template, EnumSet.of(PropertyUsageOption.SERVICE_USE),
                CompositionTaskState.FIELD_NAME_RESOURCE_NODES,
                CompositionTaskState.FIELD_NAME_COMPOSITE_COMPONENT_LINK);

        return template;
    }

    private void retrieveComponentDescriptions(CompositeDescriptionExpanded cdExpanded,
            Consumer<CompositeDescriptionExpanded> consumer) {
        List<String> componentDescriptionLinks = getComponentDescriptionLinks(cdExpanded);
        if (componentDescriptionLinks != null && !componentDescriptionLinks.isEmpty()) {
            QueryTask componentDescriptionQueryTask = new QueryTask();
            componentDescriptionQueryTask.querySpec = new QueryTask.QuerySpecification();
            componentDescriptionQueryTask.taskInfo.isDirect = true;
            componentDescriptionQueryTask.documentExpirationTimeMicros = ServiceDocumentQuery
                    .getDefaultQueryExpiration();

            QueryUtil.addExpandOption(componentDescriptionQueryTask);

            QueryUtil.addListValueClause(componentDescriptionQueryTask,
                    ServiceDocument.FIELD_NAME_SELF_LINK,
                    componentDescriptionLinks);

            sendRequest(Operation
                    .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                    .setBody(componentDescriptionQueryTask)
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            logSevere("Quering component descriptions failed: ",
                                    Utils.toString(ex));
                            consumer.accept(cdExpanded);
                        } else {
                            QueryTask resultTask = o.getBody(QueryTask.class);
                            ServiceDocumentQueryResult result = resultTask.results;

                            if (result == null || result.documents == null) {
                                return;
                            }

                            List<ComponentDescription> componentDescriptions = new ArrayList<>();
                            result.documents.forEach((link, document) -> {
                                ComponentMeta meta = CompositeComponentRegistry
                                        .metaByDescriptionLink(link);
                                ResourceState description = Utils.fromJson(document,
                                        meta.descriptionClass);
                                if (description != null) {
                                    ComponentDescription cd = new ComponentDescription(
                                            description,
                                            meta.resourceType,
                                            description.name,
                                            getBindingsForComponent(description.name, cdExpanded));
                                    componentDescriptions.add(cd);
                                } else {
                                    logWarning("Unexpected result type: %s", link);
                                }
                            });

                            cdExpanded.componentDescriptions = componentDescriptions;
                            consumer.accept(cdExpanded);
                        }
                    }));
        } else {
            consumer.accept(cdExpanded);
        }

    }

    private List<Binding> getBindingsForComponent(String componentName,
            CompositeDescriptionExpanded compositeDescriptionExpanded) {
        if (compositeDescriptionExpanded.bindings == null) {
            return Collections.emptyList();
        }
        return compositeDescriptionExpanded.bindings.stream()
                .filter(cb -> cb.componentName.equals(componentName))
                .flatMap(cb -> cb.bindings.stream()).collect(Collectors.toList());
    }

    private List<String> getComponentDescriptionLinks(CompositeDescription compositeDescription) {
        List<String> descriptionLinks = new ArrayList<String>(
                compositeDescription.descriptionLinks);

        return descriptionLinks;
    }

}
