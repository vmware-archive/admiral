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

import static com.vmware.admiral.common.util.AssertUtil.assertTrue;
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_ALLOCATION_REQUEST;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.BindingEvaluator;
import com.vmware.admiral.compute.BindingUtils;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.compute.content.Binding;
import com.vmware.admiral.compute.content.NestedState;
import com.vmware.admiral.request.ClosureProvisionTaskService;
import com.vmware.admiral.request.ClosureProvisionTaskService.ClosureProvisionTaskState;
import com.vmware.admiral.request.ContainerAllocationTaskFactoryService;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.ContainerNetworkProvisionTaskService;
import com.vmware.admiral.request.ContainerNetworkProvisionTaskService.ContainerNetworkProvisionTaskState;
import com.vmware.admiral.request.ContainerNetworkRemovalTaskService;
import com.vmware.admiral.request.ContainerVolumeProvisionTaskService;
import com.vmware.admiral.request.ContainerVolumeProvisionTaskService.ContainerVolumeProvisionTaskState;
import com.vmware.admiral.request.ContainerVolumeRemovalTaskService;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.composition.CompositionSubTaskService.CompositionSubTaskState.SubStage;
import com.vmware.admiral.request.compute.ComputeNetworkProvisionTaskService;
import com.vmware.admiral.request.compute.ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService.ComputeProvisionTaskState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Task service tracking the progress of parallel progressing composition tasks. CompositionSubTask
 * could be start executing immediately if they don't have dependency on other CompositionSubTask.
 * Otherwise, wait until all dependent on tasks completes.
 */
public class CompositionSubTaskService
        extends
        AbstractTaskStatefulService<CompositionSubTaskService.CompositionSubTaskState, CompositionSubTaskService.CompositionSubTaskState.SubStage> {

    public static final String REFERER = "__referer";
    public static final String DISPLAY_NAME = "Composition Component";
    public static final String ALLOC_SUFFIX = "-alloc";
    public static final String DESCRIPTION_LINK_FIELD_NAME = "descriptionLink";

    public static class CompositionSubTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<CompositionSubTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            ALLOCATING,
            ALLOCATED,
            NOTIFY,
            PREPARE_EXECUTE,
            EXECUTE,
            EXECUTING,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(ALLOCATING, EXECUTING));

        }

        public String compositeDescriptionLink;

        /** (Required) The description that defines the requested resource. */
        public String resourceDescriptionLink;

        /** (Required) Type of resource to create. */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public String resourceType;

        /** (Required) The operation name/id to be performed */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public String operation;

        /** The unique name per context that defines the requested resource. */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public String name;

        /** Flag indicating that it is only allocation request */
        public boolean allocationRequest;

        /**
         * The the list of task links that this task depends on and can't start before those tasks
         * complete.
         */
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> dependsOnLinks;

        /**
         * Link to the current completed task that this task is dependent on. Patch parameter only.
         */
        public String currentDependsOnLink;

        /**
         * The the list of task links that depends on the completion of the current task. All of
         * those tasks will be patched once the current task completes.
         */
        public Set<String> dependentLinks;

        /** The current composition request Id transferred as context through the tasks */
        public String requestId;

        /**
         * Set by the Task with the links of the provisioned resources. If the task is not
         * provisionining, the resource links needs to be set from outside.
         */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        /** Set by Task. Error count of the dependent tasks. */
        public long errorCount;

        /** Set by Task. Indicating that it is in the second phase after allocation */
        public boolean postAllocation;
    }

    public CompositionSubTaskService() {
        super(CompositionSubTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected boolean validateStageTransitionAndState(Operation patch,
            CompositionSubTaskState patchBody, CompositionSubTaskState currentState) {
        if (currentState.taskSubStage.ordinal() > patchBody.taskSubStage.ordinal()
                && patchBody.taskSubStage == SubStage.EXECUTE) {
            // ignore out-of-order EXECUTE from parent task: task was moved to EXECUTE by a
            // dependent task
            logFine("Ignoring subStage move from %s(%s) to %s(%s). Caller: [%s]",
                    currentState.taskInfo.stage, currentState.taskSubStage,
                    patchBody.taskInfo.stage, patchBody.taskSubStage, patch.getReferer());
            patch.complete();
            return true;
        }
        return super.validateStageTransitionAndState(patch, patchBody, currentState);
    }

    @Override
    protected void handleStartedStagePatch(CompositionSubTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            checkDependencies(state);
            break;
        case ALLOCATING:
            break;
        case ALLOCATED:
            notifyDependentTasks(state, SubStage.CREATED, () -> {
                if (state.allocationRequest) {
                    proceedTo(SubStage.NOTIFY);
                } else {
                    complete();
                }
            });
            break;
        case NOTIFY:
            notifyCaller(state);
            break;
        case PREPARE_EXECUTE:
            break;
        case EXECUTE:
            checkDependencies(state);
            break;
        case EXECUTING:
            break;
        case COMPLETED:
            notifyDependentTasks(state, SubStage.EXECUTE, () -> {
                complete(s -> {
                    s.addCustomProperty(REFERER, getSelfLink());
                });
            });
            break;
        case ERROR:
            if (!hasDependencies(state)) {
                notifyDependentTasks(state, SubStage.ERROR, () -> {
                    completeWithError(s -> {
                        s.addCustomProperty(REFERER, getSelfLink());
                    });
                });
            }
            break;
        default:
            break;
        }
    }

    private void notifyCaller(CompositionSubTaskState state) {
        ServiceTaskCallbackResponse callbackResponse = getFinishedCallbackResponse(state);
        callbackResponse.customProperties = mergeCustomProperties(
                callbackResponse.customProperties, state.customProperties);
        callbackResponse.addProperty(REFERER, this.getSelfLink());
        sendRequest(Operation.createPatch(this, state.serviceTaskCallback.serviceSelfLink)
                .setBody(callbackResponse)
                // Pragma needed because the service might be still in creation state (asynch
                // creation)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Notifying parent task %s from composition failed: %s",
                                o.getUri(), Utils.toString(e));
                    }
                }));
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            CompositionSubTaskState state) {
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            String errMsg = "No resourceLinks found for allocated resources.";
            logWarning(errMsg);
            return state.serviceTaskCallback
                    .getFailedResponse(new LocalizableValidationException(errMsg,
                            "request.composition.resource-links.missing"));
        } else {
            CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
            finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
            finishedResponse.resourceLinks = state.resourceLinks;
            return finishedResponse;
        }
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    @Override
    protected void validateStateOnStart(CompositionSubTaskState state)
            throws IllegalArgumentException {
        boolean descAndResourcesEmpty = (state.resourceDescriptionLink == null
                || state.resourceDescriptionLink
                        .isEmpty())
                &&
                (state.resourceLinks == null || state.resourceLinks.isEmpty());

        assertTrue(!descAndResourcesEmpty, "resourceDescriptionLink and resourceLinks are empty");
    }

    @Override
    protected void customStateValidationAndMerge(Operation patch, CompositionSubTaskState patchBody,
            CompositionSubTaskState currentState) {
        if (patchBody.currentDependsOnLink != null && currentState.dependsOnLinks != null) {
            boolean removed = currentState.dependsOnLinks.remove(patchBody.currentDependsOnLink);
            if (removed) {
                logFine("Completion of depends on task [%s] patched.",
                        patchBody.currentDependsOnLink);
            } else {
                logWarning("Completion of depends on task [%s] patched but not found in the list.",
                        patchBody.currentDependsOnLink);
            }
        }

        if (TaskStage.STARTED == patchBody.taskInfo.stage
                && SubStage.ERROR == patchBody.taskSubStage) {
            if (hasDependencies(currentState)) {
                currentState.errorCount = currentState.errorCount + 1;
                currentState.taskSubStage = currentState.postAllocation ? SubStage.EXECUTE
                        : SubStage.CREATED;
            }
        }

        if (SubStage.PREPARE_EXECUTE == patchBody.taskSubStage) {
            currentState.postAllocation = true; // second phase of provisioning
        }
    }

    private void notifyDependentTasks(final CompositionSubTaskState state,
            final SubStage taskSubStage, final Runnable callback) {
        if (state.dependentLinks == null || state.dependentLinks.isEmpty()) {
            logFine("No dependent task to notify for completion.");
            callback.run();
            return;
        }

        final AtomicInteger countDown = new AtomicInteger(state.dependentLinks.size());
        final AtomicBoolean error = new AtomicBoolean();
        for (final String dependentTaskLink : state.dependentLinks) {
            final CompositionSubTaskState task = new CompositionSubTaskState();
            task.currentDependsOnLink = getSelfLink();
            task.taskInfo = state.taskInfo;
            task.taskSubStage = taskSubStage;
            task.addCustomProperty(REFERER, getSelfLink());
            sendRequest(Operation.createPatch(this, dependentTaskLink)
                    .setBody(task)
                    .setContextId(state.requestId)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            if (error.compareAndSet(false, true)) {
                                failTask("Failure patching dependent task: "
                                        + dependentTaskLink, e);
                            } else {
                                logWarning(// task already failed
                                        "Failure patching dependent task: [%s]. Error: %s",
                                        dependentTaskLink, Utils.toString(e));
                            }
                            return;
                        }
                        if (countDown.decrementAndGet() == 0 && !error.get()) {
                            callback.run();
                        }
                    }));
        }
    }

    private void allocate(CompositionSubTaskState state) {
        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.documentSelfLink = state.allocationRequest ? getSelfId() + ALLOC_SUFFIX
                : getSelfId();
        requestBrokerState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.ALLOCATED, TaskStage.STARTED, SubStage.ERROR);
        requestBrokerState.resourceDescriptionLink = state.resourceDescriptionLink;
        requestBrokerState.resourceType = state.resourceType;
        requestBrokerState.operation = RequestBrokerState.PROVISION_RESOURCE_OPERATION;
        requestBrokerState.tenantLinks = state.tenantLinks;
        requestBrokerState.requestTrackerLink = state.requestTrackerLink;
        requestBrokerState.customProperties = state.customProperties;
        if (state.allocationRequest) {
            if (requestBrokerState.customProperties == null) {
                requestBrokerState.customProperties = new HashMap<>();
            }
            requestBrokerState.customProperties.put(FIELD_NAME_ALLOCATION_REQUEST,
                    Boolean.TRUE.toString());
        }

        sendRequest(Operation.createPost(this, RequestBrokerFactoryService.SELF_LINK)
                .setBody(requestBrokerState)
                .setContextId(state.requestId)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating request broker task", e);
                        return;
                    }
                }));

        proceedTo(SubStage.ALLOCATING);
    }

    private void executeTask(CompositionSubTaskState state) {
        if (isProvisionOperation(state)) {
            evaluateBindings(state.compositeDescriptionLink, state.resourceDescriptionLink,
                    () -> executeProvisionTask(state));
        } else {
            createOperationTaskState(state);
        }
    }

    private void executeProvisionTask(CompositionSubTaskState state) {
        if (ResourceType.CONTAINER_TYPE.getName().equalsIgnoreCase(state.resourceType)) {
            createContainerAllocationTaskState(state);
        } else if (ResourceType.CONTAINER_NETWORK_TYPE.getName()
                .equalsIgnoreCase(state.resourceType)) {
            createContainerNetworkProvisionTaskState(state);
        } else if (ResourceType.CONTAINER_VOLUME_TYPE.getName()
                .equalsIgnoreCase(state.resourceType)) {
            createContainerVolumeProvisionTaskState(state);
        } else if (ResourceType.COMPUTE_TYPE.getName().equalsIgnoreCase(state.resourceType)) {
            createComputeProvisionTaskState(state);
        } else if (ResourceType.COMPUTE_NETWORK_TYPE.getName()
                .equalsIgnoreCase(state.resourceType)) {
            createComputeNetworkProvisionTaskState(state);
        } else if (ResourceType.CLOSURE_TYPE.getName().equalsIgnoreCase(state.resourceType)) {
            createClosureProvisionTask(state);
        } else {
            String exMsg = String.format("Unsupported type. Must be: %s, %s, %s, %s or %s",
                    ResourceType.CONTAINER_TYPE, ResourceType.COMPUTE_TYPE,
                    ResourceType.CONTAINER_NETWORK_TYPE, ResourceType.COMPUTE_NETWORK_TYPE,
                    ResourceType.CLOSURE_TYPE);
            throw new LocalizableValidationException(exMsg,
                    "request.composition.unsupported.type",
                    ResourceType.CONTAINER_TYPE, ResourceType.COMPUTE_TYPE,
                    ResourceType.CONTAINER_NETWORK_TYPE,
                    ResourceType.COMPUTE_NETWORK_TYPE, ResourceType.CLOSURE_TYPE);
        }
    }

    private void createClosureProvisionTask(CompositionSubTaskState state) {
        ClosureProvisionTaskState provisionTask = new ClosureProvisionTaskState();
        provisionTask.documentSelfLink = getSelfId();
        provisionTask.serviceTaskCallback = ServiceTaskCallback.create(
                state.documentSelfLink, TaskStage.STARTED, SubStage.COMPLETED,
                TaskStage.STARTED, SubStage.ERROR);
        provisionTask.customProperties = state.customProperties;
        provisionTask.resourceDescriptionLink = state.resourceDescriptionLink;
        provisionTask.resourceLinks = state.resourceLinks;
        provisionTask.tenantLinks = state.tenantLinks;
        provisionTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation
                .createPost(this, ClosureProvisionTaskService.FACTORY_LINK)
                .setBody(provisionTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating resource provision task", e);
                        return;
                    }
                }));

        proceedTo(SubStage.EXECUTING);
    }

    private void createContainerAllocationTaskState(CompositionSubTaskState state) {
        ContainerAllocationTaskState allocationTask = new ContainerAllocationTaskState();
        allocationTask.documentSelfLink = getSelfId();
        allocationTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.COMPLETED, TaskStage.STARTED, SubStage.ERROR);
        allocationTask.customProperties = state.customProperties;
        allocationTask.resourceDescriptionLink = state.resourceDescriptionLink;
        allocationTask.resourceCount = Long.valueOf(state.resourceLinks.size());
        allocationTask.resourceType = state.resourceType;
        allocationTask.tenantLinks = state.tenantLinks;
        allocationTask.requestTrackerLink = state.requestTrackerLink;
        allocationTask.resourceLinks = state.resourceLinks;
        allocationTask.postAllocation = state.postAllocation;

        sendRequest(Operation.createPost(this, ContainerAllocationTaskFactoryService.SELF_LINK)
                .setBody(allocationTask)
                .setContextId(state.requestId)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating container allocation task", e);
                        return;
                    }
                }));

        proceedTo(SubStage.EXECUTING);
    }

    private void createContainerNetworkProvisionTaskState(CompositionSubTaskState state) {
        ContainerNetworkProvisionTaskState task = new ContainerNetworkProvisionTaskState();
        task.documentSelfLink = getSelfId();
        task.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.COMPLETED, TaskStage.STARTED, SubStage.ERROR);
        task.customProperties = state.customProperties;
        task.resourceCount = Long.valueOf(state.resourceLinks.size());
        task.tenantLinks = state.tenantLinks;
        task.requestTrackerLink = state.requestTrackerLink;
        task.resourceLinks = state.resourceLinks;
        task.resourceDescriptionLink = state.resourceDescriptionLink;

        sendRequest(Operation.createPost(this, ContainerNetworkProvisionTaskService.FACTORY_LINK)
                .setBody(task)
                .setContextId(state.requestId)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating container network provision task", e);
                        return;
                    }
                }));

        proceedTo(SubStage.EXECUTING);
    }

    private void createContainerVolumeProvisionTaskState(CompositionSubTaskState state) {
        ContainerVolumeProvisionTaskState task = new ContainerVolumeProvisionTaskState();
        task.documentSelfLink = getSelfId();
        task.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.COMPLETED, TaskStage.STARTED, SubStage.ERROR);
        task.customProperties = state.customProperties;
        task.resourceCount = Long.valueOf(state.resourceLinks.size());
        task.resourceType = state.resourceType;
        task.tenantLinks = state.tenantLinks;
        task.requestTrackerLink = state.requestTrackerLink;
        task.resourceLinks = state.resourceLinks;
        task.resourceDescriptionLink = state.resourceDescriptionLink;

        sendRequest(Operation.createPost(this, ContainerVolumeProvisionTaskService.FACTORY_LINK)
                .setBody(task)
                .setContextId(state.requestId)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating container volume provision task", e);
                        return;
                    }
                }));

        proceedTo(SubStage.EXECUTING);
    }

    private void createComputeProvisionTaskState(CompositionSubTaskState state) {
        ComputeProvisionTaskState ps = new ComputeProvisionTaskState();
        ps.documentSelfLink = getSelfId();
        ps.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.COMPLETED, TaskStage.STARTED, SubStage.ERROR);
        ps.customProperties = state.customProperties;
        ps.tenantLinks = state.tenantLinks;
        ps.requestTrackerLink = state.requestTrackerLink;
        ps.resourceLinks = state.resourceLinks;

        sendRequest(Operation
                .createPost(this, ComputeProvisionTaskService.FACTORY_LINK)
                .setBody(ps)
                .setContextId(state.requestId)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating compute provision task", e);
                        return;
                    }
                }));

        proceedTo(SubStage.EXECUTING);
    }

    private void createComputeNetworkProvisionTaskState(CompositionSubTaskState state) {
        ComputeNetworkProvisionTaskState task = new ComputeNetworkProvisionTaskState();
        task.documentSelfLink = getSelfId();
        task.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.COMPLETED, TaskStage.STARTED, SubStage.ERROR);
        task.customProperties = state.customProperties;
        task.resourceCount = Long.valueOf(state.resourceLinks.size());
        task.tenantLinks = state.tenantLinks;
        task.requestTrackerLink = state.requestTrackerLink;
        task.resourceLinks = state.resourceLinks;
        task.resourceDescriptionLink = state.resourceDescriptionLink;

        sendRequest(Operation.createPost(this, ComputeNetworkProvisionTaskService.FACTORY_LINK)
                .setBody(task)
                .setContextId(state.requestId)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating compute network provision task", e);
                        return;
                    }
                }));

        proceedTo(SubStage.EXECUTING);
    }

    private void createOperationTaskState(CompositionSubTaskState state) {
        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.documentSelfLink = getSelfId() + "-" + state.operation;
        requestBrokerState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.COMPLETED, TaskStage.STARTED, SubStage.ERROR);
        requestBrokerState.resourceLinks = state.resourceLinks;
        requestBrokerState.resourceType = state.resourceType;
        requestBrokerState.operation = state.operation;
        requestBrokerState.tenantLinks = state.tenantLinks;
        requestBrokerState.requestTrackerLink = state.requestTrackerLink;
        requestBrokerState.customProperties = state.customProperties;

        if (RequestBrokerState.REMOVE_RESOURCE_OPERATION.equals(requestBrokerState.operation)
                && ResourceType.CONTAINER_NETWORK_TYPE.getName()
                        .equals(requestBrokerState.resourceType)) {
            if (requestBrokerState.customProperties == null) {
                requestBrokerState.customProperties = new HashMap<>();
            }
            requestBrokerState.customProperties.put(
                    ContainerNetworkRemovalTaskService.EXTERNAL_INSPECT_ONLY_CUSTOM_PROPERTY,
                    "true");
        }

        if (RequestBrokerState.REMOVE_RESOURCE_OPERATION.equals(requestBrokerState.operation)
                && ResourceType.CONTAINER_VOLUME_TYPE.getName()
                        .equals(requestBrokerState.resourceType)) {
            if (requestBrokerState.customProperties == null) {
                requestBrokerState.customProperties = new HashMap<>();
            }
            requestBrokerState.customProperties.put(
                    ContainerVolumeRemovalTaskService.EXTERNAL_INSPECT_ONLY_CUSTOM_PROPERTY,
                    "true");
        }

        sendRequest(Operation.createPost(this, RequestBrokerFactoryService.SELF_LINK)
                .setBody(requestBrokerState)
                .setContextId(state.requestId)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating request broker task", e);
                        return;
                    }
                }));

        proceedTo(SubStage.EXECUTING);
    }

    private void checkDependencies(CompositionSubTaskState state) {
        if (!hasDependencies(state)) {
            if (state.errorCount > 0) {
                proceedTo(SubStage.ERROR);
            } else {
                if (SubStage.ALLOCATING.ordinal() > state.taskSubStage.ordinal()
                        && isProvisionOperation(state)) {
                    allocate(state);
                } else {
                    executeTask(state);
                }
            }

        }
    }

    private void evaluateBindings(String compositeDescriptionLink,
            String resourceDescriptionLink, Runnable callback) {

        URI uri = UriUtils.buildUri(this.getHost(), compositeDescriptionLink);
        URI expandUri = UriUtils.extendUriWithQuery(uri, UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.TRUE.toString());
        Operation.createGet(expandUri).setCompletion((o, e) -> {
            if (e != null) {
                failTask("Error retrieving composite description with link "
                        + compositeDescriptionLink, e);
                return;
            }

            CompositeDescriptionExpanded compositeDescription = o
                    .getBody(CompositeDescriptionExpanded.class);

            if (compositeDescription.bindings == null) {
                callback.run();
                return;
            }

            ComponentDescription description = compositeDescription.componentDescriptions.stream()
                    .filter(c -> c.getServiceDocument().documentSelfLink
                            .equals(resourceDescriptionLink))
                    .findFirst().get();

            List<Binding> provisioningTimeBindings = description.bindings.stream()
                    .filter(b -> b.isProvisioningTimeBinding()).collect(Collectors.toList());

            if (provisioningTimeBindings.isEmpty()) {
                callback.run();
                return;
            }

            Set<String> dependsOnDescriptionLinks = new HashSet<>();

            Map<String, ComponentDescription> nameToComponent = compositeDescription.componentDescriptions
                    .stream()
                    .collect(Collectors.toMap(c -> c.name, c -> c));

            for (Binding binding : provisioningTimeBindings) {
                String sourceComponentName = BindingUtils
                        .extractComponentNameFromBindingExpression(
                                binding.placeholder.bindingExpression);
                ComponentDescription sourceDescription = nameToComponent.get(sourceComponentName);

                dependsOnDescriptionLinks
                        .add(sourceDescription.getServiceDocument().documentSelfLink);
            }

            getDependsOnProvisionedResources(compositeDescription, dependsOnDescriptionLinks,
                    description.getServiceDocument().documentSelfLink, provisioningTimeBindings,
                    callback);

        }).sendWith(this);
    }

    private void getDependsOnProvisionedResources(
            CompositeDescriptionExpanded compositeDescription,
            Set<String> dependsOnDescriptionLinks, String descLink,
            List<Binding> provisioningTimeBindings, Runnable callback) {
        QueryTask componentDescriptionQueryTask = new QueryTask();
        componentDescriptionQueryTask.querySpec = new QueryTask.QuerySpecification();
        componentDescriptionQueryTask.taskInfo.isDirect = true;
        componentDescriptionQueryTask.documentExpirationTimeMicros = ServiceDocumentQuery
                .getDefaultQueryExpiration();

        // QueryUtil.addExpandOption(componentDescriptionQueryTask);

        dependsOnDescriptionLinks.add(descLink);
        QueryUtil.addListValueClause(componentDescriptionQueryTask,
                DESCRIPTION_LINK_FIELD_NAME,
                dependsOnDescriptionLinks);

        Map<String, ComponentDescription> selfLinkToComponent = compositeDescription.componentDescriptions
                .stream()
                .collect(Collectors.toMap(c -> c.getServiceDocument().documentSelfLink, c -> c));

        // TODO Is this enough to get _only_ the provisioned stuff we need? ContainerStates have a
        // contextId, but ComputeStates don't. Descriptions are cloned, so it looks like this should
        // be enough
        Operation query = Operation.createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(componentDescriptionQueryTask);
        DeferredResult<QueryTask> queryTaskDeferredResult = this
                .sendWithDeferredResult(query, QueryTask.class);

        queryTaskDeferredResult
                // Get all provisioned resources recursively
                .thenCompose(resultTask -> {
                    ServiceDocumentQueryResult result = resultTask.results;

                    if (result == null || result.documentLinks == null || result.documentLinks
                            .isEmpty()) {
                        return DeferredResult.completed(new ArrayList<NestedState>());
                    }

                    List<DeferredResult<NestedState>> nestedResources = new ArrayList<>();
                    for (String link : result.documentLinks) {
                        ComponentMeta meta = CompositeComponentRegistry.metaByStateLink(link);
                        if (meta == null) {
                            logWarning("Unexpected result type: %s", link);
                            continue;
                        }
                        nestedResources.add(NestedState.get(this, link, meta.stateTemplateClass));
                    }

                    return DeferredResult.allOf(nestedResources);
                })
                // evaluate the bindings
                .thenCompose(nestedStates -> {
                    Map<String, NestedState> nameToResource = new HashMap<>();
                    Map<String, NestedState> statesToUpdate = new HashMap<>();

                    for (NestedState nestedState : nestedStates) {
                        String descriptionLink = PropertyUtils.getValue(nestedState.object,
                                DESCRIPTION_LINK_FIELD_NAME);
                        ComponentDescription componentDescription = selfLinkToComponent
                                .get(descriptionLink);
                        nameToResource.put(componentDescription.name, nestedState);

                        if (descriptionLink.equals(descLink)) {
                            statesToUpdate.put(nestedState.object.documentSelfLink, nestedState);
                        }

                    }

                    List<DeferredResult<Operation>> updates = new ArrayList<>();
                    for (Map.Entry<String, NestedState> entry : statesToUpdate.entrySet()) {
                        NestedState evaluated = BindingEvaluator.evaluateProvisioningTimeBindings(
                                entry.getValue(),
                                provisioningTimeBindings,
                                nameToResource);

                        // This will do a PUT on the whole tree
                        evaluated.sendRequest(this, Action.PUT);
                    }
                    return DeferredResult.allOf(updates);
                })
                .whenComplete((o, e) -> {
                    if (e != null) {
                        failTask("Failure evaluating bindings", e);
                        return;
                    }
                    callback.run();
                });
    }

    private boolean hasDependencies(CompositionSubTaskState state) {
        return state.dependsOnLinks != null && !state.dependsOnLinks.isEmpty();
    }

    private boolean isProvisionOperation(CompositionSubTaskState state) {
        return RequestBrokerState.PROVISION_RESOURCE_OPERATION.equals(state.operation);
    }
}