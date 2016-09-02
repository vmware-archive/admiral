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

package com.vmware.admiral.request;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.ContainerClusteringTaskService.ContainerClusteringTaskState;
import com.vmware.admiral.request.ContainerHostRemovalTaskService.ContainerHostRemovalTaskState;
import com.vmware.admiral.request.ContainerNetworkAllocationTaskService.ContainerNetworkAllocationTaskState;
import com.vmware.admiral.request.ContainerNetworkProvisionTaskService.ContainerNetworkProvisionTaskState;
import com.vmware.admiral.request.ContainerOperationTaskService.ContainerOperationTaskState;
import com.vmware.admiral.request.ContainerRemovalTaskService.ContainerRemovalTaskState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState.SubStage;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.request.ReservationRemovalTaskService.ReservationRemovalTaskState;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState;
import com.vmware.admiral.request.composition.CompositeComponentRemovalTaskService;
import com.vmware.admiral.request.composition.CompositeComponentRemovalTaskService.CompositeComponentRemovalTaskState;
import com.vmware.admiral.request.composition.CompositionTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionTaskService.CompositionTaskState;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.compute.ComputeOperationTaskService;
import com.vmware.admiral.request.compute.ComputeOperationTaskService.ComputeOperationTaskState;
import com.vmware.admiral.request.compute.ComputeOperationType;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService.ComputeProvisionTaskState;
import com.vmware.admiral.request.compute.ComputeRemovalTaskService;
import com.vmware.admiral.request.compute.ComputeRemovalTaskService.ComputeRemovalTaskState;
import com.vmware.admiral.request.compute.ComputeReservationTaskService;
import com.vmware.admiral.request.compute.ComputeReservationTaskService.ComputeReservationTaskState;
import com.vmware.admiral.request.compute.aws.ProvisionContainerHostsTaskService;
import com.vmware.admiral.request.compute.aws.ProvisionContainerHostsTaskService.ProvisionContainerHostsTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Request Broker service implementing the task of provision resource work flow. Utilizes sub tasks
 * and services provided in the resource description to perform various sub stages
 */
public class RequestBrokerService extends
        AbstractTaskStatefulService<RequestBrokerService.RequestBrokerState, RequestBrokerService.RequestBrokerState.SubStage> {

    public static final String DISPLAY_NAME = "Request";

    public static class RequestBrokerState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<RequestBrokerState.SubStage> {
        public static final String PROVISION_RESOURCE_OPERATION = "PROVISION_RESOURCE";
        public static final String REMOVE_RESOURCE_OPERATION = "REMOVE_RESOURCE";
        public static final String CLUSTER_RESOURCE_OPERATION = "CLUSTER_RESOURCE";

        private static final String FIELD_NAME_RESOURCE_TYPE = "resourceType";
        private static final String FIELD_NAME_OPERATION = "operation";
        private static final String FIELD_RESOURCE_DESC_LINK = "resourceDescriptionLink";
        private static final String FIELD_RESOURCE_TENANT_LINKS = "tenantLinks";
        private static final String FIELD_RESOURCE_COUNT = "resourceCount";
        private static final String FIELD_RESOURCE_LINKS = "resourceLinks";
        private static final String FIELD_RESOURCE_POLICY_LINK = "groupResourcePolicyLink";

        public static enum SubStage {
            CREATED,
            RESERVING,
            RESERVED,
            ALLOCATING,
            ALLOCATED,
            COMPLETED,
            REQUEST_FAILED,
            RESERVATION_CLEANUP,
            RESERVATION_CLEANED_UP,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(RESERVING, ALLOCATING, RESERVATION_CLEANUP));
        }

        /** (Required) Type of resource to create. */
        public String resourceType;

        /** (Required) The operation name/id to be performed */
        public String operation;

        /** (Required) The description that defines the requested resource. */
        public String resourceDescriptionLink;

        /** (Optional- default 1) Number of resources to provision. */
        public long resourceCount;

        /** Set by Task when resources are provisioned. */
        public List<String> resourceLinks;

        public String groupResourcePolicyLink;
    }

    public RequestBrokerService() {
        super(RequestBrokerState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(RequestBrokerState state) throws IllegalArgumentException {
        assertNotEmpty(state.resourceType, "resourceType");

        if (state.operation == null) {
            state.operation = RequestBrokerState.PROVISION_RESOURCE_OPERATION;
        }

        if (isProvisionOperation(state) || isContainerClusteringOperation(state)
                || isProvisioningContainerHostsOperation(state)) {
            assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");
        } else {
            assertNotEmpty(state.resourceLinks, "resourceLinks");
        }

        if (!(isContainerType(state) || isContainerHostType(state) || isContainerNetworkType(state)
                || isComputeType(state) || isCompositeComponentType(state))) {
            throw new IllegalArgumentException(
                    "Only 'DOCKER_CONTAINER', 'CONTAINER_HOST', 'CONTAINER_NETWORK', 'COMPUTE' and 'COMPOSITE_COMPONENT' resource types are supported.");
        }

        if (state.resourceCount <= 0) {
            state.resourceCount = 1;
        }
    }

    @Override
    protected boolean validateStateOnStart(RequestBrokerState state, Operation startOpr) {
        validateStateOnStart(state);
        return createRequestTrackerIfNoneProvided(state, startOpr);
    }

    @Override
    protected void handleStartedStagePatch(RequestBrokerState state) {
        switch (state.taskSubStage) {
        case CREATED:
            if (isProvisionOperation(state)) {
                if (isCompositionProvisioning(state)) {
                    createCompositionTask(state);
                } else {
                    createReservationTasks(state);
                }
            } else if (isPostAllocationOperation(state)) {
                createAllocationTasks(state);
            } else {
                createResourceOperation(state);
            }
            break;
        case RESERVING:
            break;
        case RESERVED:
            createAllocationTasks(state);
            break;
        case ALLOCATING:
            break;
        case ALLOCATED:
            complete(state, SubStage.COMPLETED);
            break;
        case REQUEST_FAILED:
            if (isProvisionOperation(state)) {
                createReservationRemovalTask(state);
            } else if (isPostAllocationOperation(state)) {
                if (isComputeType(state)) {
                    createComputeRemovalTask(state, false);
                } else {
                    createContainerRemovalAllocationTasks(state, false);
                }
            } else if (isProvisioningContainerHostsOperation(state)) {
                createContainerHostRemovalTask(state);
            } else {
                sendSelfPatch(createUpdateSubStageTask(state, SubStage.ERROR));
            }
            break;
        case COMPLETED:
            complete(state, SubStage.COMPLETED);
            break;
        case RESERVATION_CLEANUP:
            break;
        case RESERVATION_CLEANED_UP:
            if (isComputeType(state)) {
                createComputeRemovalTask(state, true);
            } else {
                createContainerRemovalAllocationTasks(state, true);
            }
            break;
        case ERROR:
            completeWithError(state, SubStage.ERROR);
            break;
        default:
            break;
        }
    }

    @Override
    protected boolean validateStageTransition(Operation patch, RequestBrokerState patchBody,
            RequestBrokerState currentState) {
        currentState.groupResourcePolicyLink = mergeProperty(currentState.groupResourcePolicyLink,
                patchBody.groupResourcePolicyLink);
        currentState.resourceLinks = mergeProperty(currentState.resourceLinks,
                patchBody.resourceLinks);
        currentState.requestTrackerLink = mergeProperty(currentState.requestTrackerLink,
                patchBody.requestTrackerLink);

        return false;
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(RequestBrokerState state) {
        final CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logFine("No resourceLinks found for allocated resources.");
        }
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        List<String> resourceLinks;
    }

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        TaskStatusState statusTask = super.fromTask(state);
        RequestBrokerState currentState = (RequestBrokerState) state;
        if (currentState.resourceLinks == null
                || currentState.resourceLinks.isEmpty()) {
            return statusTask;
        }

        if (SubStage.CREATED == currentState.taskSubStage
                || SubStage.COMPLETED == currentState.taskSubStage) {
            statusTask.resourceLinks = currentState.resourceLinks;
        }

        return statusTask;
    }

    @Override
    public void failTask(String errMsg, Throwable e) {
        if (errMsg == null) {
            errMsg = "Unexpected State";
        }
        if (e != null) {
            logWarning("%s. Error: %s", errMsg, Utils.toString(e));
        } else {
            logWarning(errMsg);
        }
        RequestBrokerState body = new RequestBrokerState();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = TaskStage.STARTED;
        body.taskSubStage = SubStage.REQUEST_FAILED;
        if (e != null) {
            body.taskInfo.failure = Utils.toServiceErrorResponse(e);
        } else {
            ServiceErrorResponse rsp = new ServiceErrorResponse();
            rsp.message = errMsg;
            body.taskInfo.failure = rsp;
        }
        sendSelfPatch(body);
    }

    @Override
    protected void handleFailedStagePatch(RequestBrokerState state) {
        EventLogState eventLog = new EventLogState();
        eventLog.description = state.taskInfo.failure.message;
        if (eventLog.description == null) {
            eventLog.description = "Unexpected error, status: " + state.taskInfo.failure.statusCode;
            logWarning("Patch failure stack trace: %s", state.taskInfo.failure.stackTrace);
        }
        eventLog.eventLogType = EventLogType.ERROR;
        eventLog.resourceType = getClass().getName();
        eventLog.tenantLinks = state.tenantLinks;

        sendRequest(Operation.createPost(this, EventLogService.FACTORY_LINK)
                .setBody(eventLog)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failed to create event log: %s", Utils.toString(e));
                    } else {
                        state.addCustomProperty(TaskStatusState.FIELD_NAME_EVENT_LOG_LINK,
                                o.getBody(EventLogState.class).documentSelfLink);
                        updateRequestTracker(state);
                    }

                    super.handleFailedStagePatch(state);
                }));
    }

    @Override
    public void handleDelete(Operation delete) {
        RequestBrokerState state = getState(delete);
        if (state.requestTrackerLink != null && !state.requestTrackerLink.isEmpty()) {
            sendRequest(Operation.createDelete(this, state.requestTrackerLink)
                    .setBody(new ServiceDocument())
                    .setCompletion((o, e) -> {
                        if (Operation.STATUS_CODE_NOT_FOUND == o.getStatusCode()) {
                            logWarning("Request status not found: %s",
                                    state.requestTrackerLink);
                        } else if (e != null) {
                            logWarning(
                                    "Exception while deleting request status: %s. Error: %s",
                                    state.requestTrackerLink, Utils.toString(e));
                        } else {
                            logFine("Request status %s deleted.", state.requestTrackerLink);
                        }
                    }));
        }
        super.handleDelete(delete);
    }

    private void createResourceOperation(RequestBrokerState state) {
        if (isContainerType(state)) {
            if (isRemoveOperation(state)) {
                createContainerRemovalAllocationTasks(state, false);
            } else if (isContainerClusteringOperation(state)) {
                createContainerClusteringTasks(state);
            } else {
                createContainerOperationTasks(state);
            }
        } else if (isCompositeComponentType(state)) {
            createCompositeComponentOperationTask(state);
        } else if (isContainerHostType(state)) {
            if (isRemoveOperation(state)) {
                createContainerHostRemovalTask(state);
            } else if (isProvisioningContainerHostsOperation(state)) {
                createProvisioningContainerHostsTask(state);
            } else {
                failTask(null, new IllegalArgumentException("Not supported operation: "
                        + state.operation));
            }
        } else if (isComputeType(state)) {
            if (isRemoveOperation(state)) {
                createComputeRemovalTask(state, false);
            } else {
                createComputeOperationTasks(state);
            }
        } else if (isContainerNetworkType(state)) {
            if (isRemoveOperation(state)) {
                // TODO - handle the removal
                // createContainerNetworkRemovalTask(state);
                failTask(null, new IllegalArgumentException("Not supported operation YET: "
                        + state.operation));
            } else {
                failTask(null, new IllegalArgumentException("Not supported operation: "
                        + state.operation));
            }
        } else {
            failTask(null, new IllegalArgumentException("Not supported resourceType: "
                    + state.resourceType));
        }
    }

    private void createCompositeComponentOperationTask(RequestBrokerState state) {
        if (isRemoveOperation(state)) {
            createCompositeComponentRemovalTask(state);
        } else {
            QueryTask compositeQueryTask = QueryUtil.buildQuery(CompositeComponent.class, true);

            QueryUtil.addExpandOption(compositeQueryTask);
            QueryUtil.addListValueClause(compositeQueryTask,
                    CompositeComponent.FIELD_NAME_SELF_LINK,
                    state.resourceLinks);

            List<String> componentLinks = new ArrayList<String>();
            new ServiceDocumentQuery<CompositeComponent>(getHost(), CompositeComponent.class)
                    .query(compositeQueryTask, (r) -> {
                        if (r.hasException()) {
                            logSevere("Failed to create operation task for %s - %s",
                                    r.getDocumentSelfLink(), r.getException());
                        } else if (r.hasResult()) {
                            componentLinks.addAll(r.getResult().componentLinks);
                        } else {
                            if (componentLinks.isEmpty()) {
                                logSevere(
                                        "Failed to create operation task - composite component's container links are empty");
                            }
                            state.resourceLinks = componentLinks;
                            // TODO: Handle other types
                            state.resourceType = ResourceType.CONTAINER_TYPE.getName();
                            createContainerOperationTasks(state);
                        }
                    });
        }
    }

    private void createCompositeComponentRemovalTask(RequestBrokerState state) {
        if (state.resourceLinks == null) {
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.ERROR));
            return;
        }

        CompositeComponentRemovalTaskState removalState = new CompositeComponentRemovalTaskState();
        removalState.resourceLinks = state.resourceLinks;
        removalState.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                SubStage.COMPLETED, SubStage.ERROR);
        removalState.documentSelfLink = getSelfId();
        removalState.requestTrackerLink = state.requestTrackerLink;

        Operation post = Operation
                .createPost(this, CompositeComponentRemovalTaskService.FACTORY_LINK)
                .setBody(removalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create container removal task", ex);
                    }

                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                });
        sendRequest(post);
    }

    private void createProvisioningContainerHostsTask(RequestBrokerState state) {
        ProvisionContainerHostsTaskState provisionContainerHostTask = new ProvisionContainerHostsTaskState();
        provisionContainerHostTask.documentSelfLink = getSelfId();
        provisionContainerHostTask.computeDescriptionLink = state.resourceDescriptionLink;
        provisionContainerHostTask.resourceCount = state.resourceCount;
        provisionContainerHostTask.customProperties = state.customProperties;
        provisionContainerHostTask.requestTrackerLink = state.requestTrackerLink;
        provisionContainerHostTask.serviceTaskCallback = ServiceTaskCallback.create(
                state.documentSelfLink,
                TaskStage.STARTED, SubStage.ALLOCATED,
                TaskStage.STARTED, SubStage.REQUEST_FAILED);

        sendRequest(Operation.createPost(this, ProvisionContainerHostsTaskService.FACTORY_LINK)
                .setBody(provisionContainerHostTask)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create provisioning container hosts task", ex);
                        return;
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                }));
    }

    private void createContainerHostRemovalTask(RequestBrokerState state) {
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.ERROR));
            return;
        }
        ContainerHostRemovalTaskState hostRemovalState = new ContainerHostRemovalTaskState();
        hostRemovalState.resourceLinks = state.resourceLinks;
        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED;
        hostRemovalState.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.ALLOCATED,
                TaskStage.FAILED, SubStage.ERROR);
        hostRemovalState.documentSelfLink = getSelfId();
        hostRemovalState.customProperties = state.customProperties;
        hostRemovalState.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ContainerHostRemovalTaskFactoryService.SELF_LINK)
                .setBody(hostRemovalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create container host removal operation task", ex);
                        return;
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                }));

    }

    private void createComputeRemovalTask(RequestBrokerState state,
            boolean skipReleaseResourceQuota) {
        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED
                || state.taskSubStage == SubStage.RESERVATION_CLEANED_UP;

        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            sendSelfPatch(createUpdateSubStageTask(state,
                    errorState ? SubStage.ERROR : SubStage.ALLOCATED));
            return;
        }
        ComputeRemovalTaskState computeRemovalState = new ComputeRemovalTaskState();
        computeRemovalState.resourceLinks = state.resourceLinks;
        computeRemovalState.serviceTaskCallback = ServiceTaskCallback.create(
                state.documentSelfLink,
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.ALLOCATED,
                TaskStage.FAILED, SubStage.ERROR);
        computeRemovalState.documentSelfLink = getSelfId();
        computeRemovalState.customProperties = state.customProperties;
        computeRemovalState.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ComputeRemovalTaskService.FACTORY_LINK)
                .setBody(computeRemovalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create container host removal operation task", ex);
                        return;
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                }));

    }

    private void createContainerRemovalAllocationTasks(RequestBrokerState state,
            boolean skipReleaseResourcePolicy) {
        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED
                || state.taskSubStage == SubStage.RESERVATION_CLEANED_UP;

        if (state.resourceLinks == null) {
            sendSelfPatch(createUpdateSubStageTask(state,
                    errorState ? SubStage.ERROR : SubStage.ALLOCATED));
            return;
        }

        ContainerRemovalTaskState removalState = new ContainerRemovalTaskState();
        removalState.skipReleaseResourcePolicy = skipReleaseResourcePolicy;
        removalState.resourceLinks = state.resourceLinks;

        removalState.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.ALLOCATED,
                TaskStage.FAILED, SubStage.ERROR);
        removalState.documentSelfLink = getSelfId();
        removalState.requestTrackerLink = state.requestTrackerLink;
        Operation post = Operation.createPost(this, ContainerRemovalTaskFactoryService.SELF_LINK)
                .setBody(removalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create container removal task", ex);
                    }
                    if (!errorState) {
                        sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                    }
                });
        sendRequest(post);
    }

    private void createContainerOperationTasks(RequestBrokerState state) {
        ContainerOperationTaskState operationState = new ContainerOperationTaskState();
        operationState.resourceLinks = state.resourceLinks;
        operationState.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                TaskStage.STARTED, SubStage.ALLOCATED, TaskStage.FAILED, SubStage.ERROR);
        operationState.operation = state.operation;
        operationState.documentSelfLink = getSelfId();
        operationState.customProperties = state.customProperties;
        operationState.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ContainerOperationTaskFactoryService.SELF_LINK)
                .setBody(operationState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create container operation task", ex);
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                }));
    }

    private void createComputeOperationTasks(RequestBrokerState state) {
        ComputeOperationTaskState operationState = new ComputeOperationTaskState();
        operationState.resourceLinks = state.resourceLinks;
        operationState.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                TaskStage.STARTED, SubStage.ALLOCATED, TaskStage.FAILED, SubStage.ERROR);
        operationState.operation = state.operation;
        operationState.documentSelfLink = getSelfId();
        operationState.customProperties = state.customProperties;
        operationState.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ComputeOperationTaskService.FACTORY_LINK)
                .setBody(operationState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create container operation task", ex);
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                }));
    }

    private void createReservationTasks(RequestBrokerState state) {
        if (isComputeType(state)) {
            getComputeDescription(state, (cd) -> createComputeReservationTasks(state, cd));
        } else if (isContainerNetworkType(state)) {
            // No reservation needed here, moving on...
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.RESERVED));
        } else {
            getContainerDescription(state, (cd) -> createReservationTasks(state, cd));
        }
    }

    private void createReservationTasks(RequestBrokerState state,
            ContainerDescription containerDescription) {

        if (containerDescription == null) {
            getContainerDescription(state, (cd) -> createReservationTasks(state, cd));
            return;
        }

        ReservationTaskState rsrvTask = new ReservationTaskState();
        rsrvTask.documentSelfLink = Service.getId(state.documentSelfLink);
        rsrvTask.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                TaskStage.STARTED, SubStage.RESERVED, TaskStage.STARTED, SubStage.ERROR);

        long resourceCount;
        if (containerDescription._cluster != null && containerDescription._cluster > 0
                && !isContainerClusteringOperation(state)) {
            resourceCount = state.resourceCount * containerDescription._cluster;
        } else {
            resourceCount = state.resourceCount;
        }

        rsrvTask.resourceCount = resourceCount;
        rsrvTask.tenantLinks = state.tenantLinks;
        rsrvTask.resourceType = state.resourceType;
        rsrvTask.resourceDescriptionLink = state.resourceDescriptionLink;
        rsrvTask.customProperties = mergeCustomProperties(
                state.customProperties, containerDescription.customProperties);
        rsrvTask.requestTrackerLink = state.requestTrackerLink;

        if (state.groupResourcePolicyLink != null) {
            rsrvTask.groupResourcePolicyLink = state.groupResourcePolicyLink;
            rsrvTask.taskSubStage = ReservationTaskState.SubStage.RESERVATION_SELECTED;
            rsrvTask.resourcePoolsPerGroupPolicyLinks = new LinkedHashMap<>(0);
        }

        sendRequest(Operation.createPost(this, ReservationTaskFactoryService.SELF_LINK)
                .setBody(rsrvTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating reservation task", e);
                        return;
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.RESERVING));
                }));
    }

    private void createComputeReservationTasks(RequestBrokerState state,
            ComputeDescription computeDescription) {

        if (computeDescription == null) {
            getComputeDescription(state, (cd) -> createComputeReservationTasks(state, cd));
            return;
        }

        ComputeReservationTaskState rsrvTask = new ComputeReservationTaskState();
        rsrvTask.documentSelfLink = Service.getId(state.documentSelfLink);
        rsrvTask.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                TaskStage.STARTED, SubStage.RESERVED, TaskStage.STARTED, SubStage.ERROR);

        long resourceCount = state.resourceCount;

        rsrvTask.resourceCount = resourceCount;
        rsrvTask.tenantLinks = state.tenantLinks;
        rsrvTask.resourceDescriptionLink = state.resourceDescriptionLink;
        rsrvTask.customProperties = mergeCustomProperties(
                state.customProperties, computeDescription.customProperties);
        rsrvTask.requestTrackerLink = state.requestTrackerLink;

        if (state.groupResourcePolicyLink != null) {
            rsrvTask.groupResourcePolicyLink = state.groupResourcePolicyLink;
            rsrvTask.taskSubStage = ComputeReservationTaskState.SubStage.RESERVATION_SELECTED;
            rsrvTask.resourcePoolsPerGroupPolicyLinks = new LinkedHashMap<>(0);
        }

        sendRequest(Operation.createPost(this, ComputeReservationTaskService.FACTORY_LINK)
                .setBody(rsrvTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating reservation task", e);
                        return;
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.RESERVING));
                }));
    }

    private void createAllocationTasks(RequestBrokerState state) {
        if (isContainerType(state)) {

            getContainerDescription(state, (containerDesc) -> {
                ContainerAllocationTaskState allocationTask = new ContainerAllocationTaskState();
                allocationTask.documentSelfLink = Service.getId(state.documentSelfLink);
                allocationTask.serviceTaskCallback = ServiceTaskCallback.create(
                        state.documentSelfLink, TaskStage.STARTED, SubStage.ALLOCATED,
                        TaskStage.STARTED, SubStage.REQUEST_FAILED);
                allocationTask.customProperties = state.customProperties;
                allocationTask.resourceDescriptionLink = state.resourceDescriptionLink;

                if (containerDesc._cluster != null && containerDesc._cluster > 1
                        && state.resourceCount <= 1
                        && isProvisionOperation(state) && !isContainerClusteringOperation(state)) {
                    // deploy the default number of clustered container nodes
                    allocationTask.resourceCount = Long.valueOf(containerDesc._cluster);
                } else {
                    allocationTask.resourceCount = state.resourceCount;
                }

                allocationTask.resourceType = state.resourceType;
                allocationTask.tenantLinks = state.tenantLinks;
                allocationTask.groupResourcePolicyLink = state.groupResourcePolicyLink;
                allocationTask.requestTrackerLink = state.requestTrackerLink;
                allocationTask.resourceLinks = state.resourceLinks;
                allocationTask.postAllocation = isPostAllocationOperation(state);

                sendRequest(Operation
                        .createPost(this, ContainerAllocationTaskFactoryService.SELF_LINK)
                        .setBody(allocationTask)
                        .setContextId(getSelfId())
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                failTask("Failure creating resource allocation task", e);
                                return;
                            }
                            sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                        }));
            });

        } else if (isContainerNetworkType(state)) {
            if (!isPostAllocationOperation(state)) {
                // 1. allocate the network
                ContainerNetworkAllocationTaskState allocationTask = new ContainerNetworkAllocationTaskState();
                allocationTask.documentSelfLink = Service.getId(state.documentSelfLink);
                allocationTask.serviceTaskCallback = ServiceTaskCallback.create(
                        state.documentSelfLink, TaskStage.STARTED, SubStage.ALLOCATED,
                        TaskStage.STARTED, SubStage.ERROR);
                allocationTask.customProperties = state.customProperties;
                allocationTask.resourceDescriptionLink = state.resourceDescriptionLink;

                allocationTask.tenantLinks = state.tenantLinks;
                allocationTask.requestTrackerLink = state.requestTrackerLink;
                allocationTask.resourceLinks = state.resourceLinks;
                allocationTask.resourceCount = state.resourceCount;

                sendRequest(Operation
                        .createPost(this, ContainerNetworkAllocationTaskService.FACTORY_LINK)
                        .setBody(allocationTask)
                        .setContextId(getSelfId())
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                failTask("Failure creating resource allocation task", e);
                                return;
                            }
                            sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                        }));
            } else {
                // 2. provision the network
                ContainerNetworkProvisionTaskState provisionTask = new ContainerNetworkProvisionTaskState();
                provisionTask.documentSelfLink = Service.getId(state.documentSelfLink);
                provisionTask.serviceTaskCallback = ServiceTaskCallback.create(
                        state.documentSelfLink, TaskStage.STARTED, SubStage.COMPLETED,
                        TaskStage.STARTED, SubStage.REQUEST_FAILED);
                provisionTask.customProperties = state.customProperties;

                provisionTask.tenantLinks = state.tenantLinks;
                provisionTask.requestTrackerLink = state.requestTrackerLink;
                provisionTask.resourceLinks = state.resourceLinks;
                provisionTask.resourceCount = state.resourceCount;
                provisionTask.resourceDescriptionLink = state.resourceDescriptionLink;

                sendRequest(Operation
                        .createPost(this, ContainerNetworkProvisionTaskService.FACTORY_LINK)
                        .setBody(provisionTask)
                        .setContextId(getSelfId())
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                failTask("Failure creating resource provision task", e);
                                return;
                            }
                        }));
            }
        } else if (isComputeType(state)) {
            if (!isPostAllocationOperation(state)) {
                ComputeAllocationTaskState allocationTask = new ComputeAllocationTaskState();
                allocationTask.documentSelfLink = Service.getId(state.documentSelfLink);
                allocationTask.serviceTaskCallback = ServiceTaskCallback.create(
                        state.documentSelfLink, TaskStage.STARTED, SubStage.ALLOCATED,
                        TaskStage.STARTED, SubStage.REQUEST_FAILED);
                allocationTask.customProperties = state.customProperties;
                allocationTask.resourceDescriptionLink = state.resourceDescriptionLink;

                allocationTask.resourceCount = state.resourceCount;

                allocationTask.resourceType = state.resourceType;
                allocationTask.tenantLinks = state.tenantLinks;
                allocationTask.groupResourcePolicyLink = state.groupResourcePolicyLink;
                allocationTask.requestTrackerLink = state.requestTrackerLink;
                allocationTask.resourceLinks = state.resourceLinks;

                sendRequest(Operation
                        .createPost(this, ComputeAllocationTaskService.FACTORY_LINK)
                        .setBody(allocationTask)
                        .setContextId(getSelfId())
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                failTask("Failure creating resource allocation task", e);
                                return;
                            }
                            sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                        }));
            } else {
                // 2. provision the network
                ComputeProvisionTaskState ps = new ComputeProvisionTaskState();
                ps.documentSelfLink = Service.getId(state.documentSelfLink);
                ps.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                        TaskStage.STARTED, SubStage.COMPLETED, TaskStage.STARTED, SubStage.ERROR);
                ps.customProperties = state.customProperties;
                ps.tenantLinks = state.tenantLinks;
                ps.requestTrackerLink = state.requestTrackerLink;
                ps.resourceLinks = state.resourceLinks;

                sendRequest(Operation
                        .createPost(this, ComputeProvisionTaskService.FACTORY_LINK)
                        .setBody(ps)
                        .setContextId(getSelfId())
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                failTask("Failure creating resource provision task", e);
                                return;
                            }
                        }));
            }
        } else {
            failTask(null, new IllegalArgumentException("Not supported resourceType: "
                    + state.resourceType));
        }
    }

    private void createCompositionTask(RequestBrokerState state) {
        if (isContainerType(state) || isComputeType(state)) {
            CompositionTaskState compositionTask = new CompositionTaskState();
            compositionTask.documentSelfLink = Service.getId(state.documentSelfLink);
            compositionTask.serviceTaskCallback = ServiceTaskCallback.create(
                    state.documentSelfLink, TaskStage.STARTED,
                    SubStage.ALLOCATED, TaskStage.STARTED, SubStage.ERROR);
            compositionTask.customProperties = state.customProperties;
            compositionTask.resourceDescriptionLink = state.resourceDescriptionLink;
            compositionTask.resourceType = state.resourceType;
            compositionTask.tenantLinks = state.tenantLinks;
            compositionTask.requestTrackerLink = state.requestTrackerLink;

            sendRequest(Operation.createPost(this, CompositionTaskFactoryService.SELF_LINK)
                    .setBody(compositionTask)
                    .setContextId(getSelfId())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failure creating composition task", e);
                            return;
                        }
                        sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                    }));
        } else {
            failTask(null, new IllegalArgumentException("Not supported resourceType: "
                    + state.resourceType));
        }
    }

    private void createReservationRemovalTask(RequestBrokerState state) {
        if (state.groupResourcePolicyLink == null || state.groupResourcePolicyLink.isEmpty()) {
            RequestBrokerState body = new RequestBrokerState();
            body.taskInfo = new TaskState();
            body.taskInfo.stage = TaskStage.FAILED;
            body.taskSubStage = SubStage.ERROR;
            sendSelfPatch(body);
            return;
        }
        ReservationRemovalTaskState rsrvTask = new ReservationRemovalTaskState();
        rsrvTask.documentSelfLink = Service.getId(state.documentSelfLink);

        rsrvTask.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                TaskStage.STARTED, SubStage.RESERVATION_CLEANED_UP, TaskStage.FAILED,
                SubStage.ERROR);
        rsrvTask.resourceCount = state.resourceCount;
        rsrvTask.resourceDescriptionLink = state.resourceDescriptionLink;
        rsrvTask.groupResourcePolicyLink = state.groupResourcePolicyLink;
        rsrvTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ReservationRemovalTaskFactoryService.SELF_LINK)
                .setBody(rsrvTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Reservations can't be cleaned up. Error: " + Utils.toString(e));
                    }
                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.RESERVATION_CLEANUP));
                }));
    }

    private void createContainerClusteringTasks(RequestBrokerState state) {
        ContainerClusteringTaskState clusteringState = new ContainerClusteringTaskState();
        clusteringState.resourceCount = state.resourceCount;
        clusteringState.postAllocation = isPostAllocationOperation(state);
        clusteringState.customProperties = state.customProperties;
        clusteringState.tenantLinks = state.tenantLinks;
        clusteringState.resourceDescriptionLink = state.resourceDescriptionLink;
        clusteringState.requestTrackerLink = state.requestTrackerLink;
        clusteringState.resourceType = state.resourceType;
        clusteringState.documentDescription = state.documentDescription;
        clusteringState.contextId = state.getCustomProperty(FIELD_NAME_CONTEXT_ID_KEY);

        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED;
        clusteringState.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.ALLOCATED,
                TaskStage.FAILED, SubStage.ERROR);

        clusteringState.documentSelfLink = getSelfId();
        clusteringState.requestTrackerLink = state.requestTrackerLink;
        Operation post = Operation
                .createPost(this, ContainerClusteringTaskFactoryService.SELF_LINK)
                .setBody(clusteringState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create container clustering task.", ex);
                        return;
                    }
                    if (!errorState) {
                        sendSelfPatch(createUpdateSubStageTask(state, SubStage.ALLOCATING));
                    }
                });
        sendRequest(post);
    }

    private boolean isProvisionOperation(RequestBrokerState state) {
        return RequestBrokerState.PROVISION_RESOURCE_OPERATION.equals(state.operation);
    }

    private boolean isPostAllocationOperation(RequestBrokerState state) {
        return (isContainerType(state) || isContainerNetworkType(state) || isComputeType(state))
                && (ContainerOperationType.CREATE.id.equals(state.operation)
                        || NetworkOperationType.CREATE.id.equals(state.operation)
                        || ComputeOperationType.CREATE.id.equals(state.operation));
    }

    private boolean isCompositionProvisioning(RequestBrokerState state) {
        return (isContainerType(state) || isComputeType(state))
                && state.resourceDescriptionLink != null
                && state.resourceDescriptionLink.startsWith(ManagementUriParts.COMPOSITE_DESC);
    }

    private boolean isRemoveOperation(RequestBrokerState state) {
        if (RequestBrokerState.REMOVE_RESOURCE_OPERATION.equals(state.operation)) {
            return true;
        }

        if (isContainerType(state) || isCompositeComponentType(state)) {
            return ContainerOperationType.DELETE.id.equals(state.operation);
        }

        return false;
    }

    private boolean isContainerType(RequestBrokerState state) {
        return ResourceType.CONTAINER_TYPE.getName().equals(state.resourceType);
    }

    private boolean isContainerNetworkType(RequestBrokerState state) {
        return ResourceType.NETWORK_TYPE.getName().equals(state.resourceType);
    }

    private boolean isCompositeComponentType(RequestBrokerState state) {
        return ResourceType.COMPOSITE_COMPONENT_TYPE.getName().equals(state.resourceType);
    }

    private boolean isContainerHostType(RequestBrokerState state) {
        return ResourceType.CONTAINER_HOST_TYPE.getName().equals(state.resourceType);
    }

    private boolean isComputeType(RequestBrokerState state) {
        return ResourceType.COMPUTE_TYPE.getName().equals(state.resourceType);
    }

    private boolean isContainerClusteringOperation(RequestBrokerState state) {
        return RequestBrokerState.CLUSTER_RESOURCE_OPERATION.equals(state.operation)
                || state.getCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP) != null;
    }

    private boolean isProvisioningContainerHostsOperation(RequestBrokerState state) {
        return ProvisionContainerHostsTaskService.PROVISION_CONTAINER_HOSTS_OPERATITON
                .equals(state.operation);
    }

    private static final Map<ResourceType, List<String>> SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE;

    static {
        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE = new HashMap<>();
        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE.put(ResourceType.CONTAINER_TYPE, new ArrayList<>(
                Arrays.asList(ContainerAllocationTaskService.DISPLAY_NAME)));
        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE.put(ResourceType.COMPUTE_TYPE, new ArrayList<>(
                Arrays.asList(ComputeAllocationTaskService.DISPLAY_NAME)));
        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE.put(ResourceType.NETWORK_TYPE, new ArrayList<>(
                Arrays.asList(ContainerNetworkProvisionTaskService.DISPLAY_NAME)));
    }

    private static final Map<ResourceType, List<String>> SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE;

    static {
        SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE = new HashMap<>();
        SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE.put(ResourceType.CONTAINER_TYPE,
                new ArrayList<>(
                        Arrays.asList(ContainerAllocationTaskService.DISPLAY_NAME,
                                ReservationTaskService.DISPLAY_NAME,
                                PlacementHostSelectionTaskService.DISPLAY_NAME,
                                ResourceNamePrefixTaskService.DISPLAY_NAME)));
        SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE.put(ResourceType.COMPUTE_TYPE, new ArrayList<>(
                Arrays.asList(ComputeAllocationTaskService.DISPLAY_NAME,
                        ReservationTaskService.DISPLAY_NAME,
                        PlacementHostSelectionTaskService.DISPLAY_NAME,
                        ResourceNamePrefixTaskService.DISPLAY_NAME)));
        SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE.put(ResourceType.NETWORK_TYPE, new ArrayList<>(
                Arrays.asList(ContainerNetworkAllocationTaskService.DISPLAY_NAME,
                        ResourceNamePrefixTaskService.DISPLAY_NAME)));
    }

    private boolean createRequestTrackerIfNoneProvided(RequestBrokerState state, Operation op) {
        if (state.requestTrackerLink != null && !state.requestTrackerLink.isEmpty()) {
            logFine("Request tracker link provided: %s", state.requestTrackerLink);
            return false;
        }

        RequestStatus requestStatus = fromTask(new RequestStatus(), state);

        // add tracked leaf tasks depending on the request type
        if (isProvisionOperation(state)) {

            requestStatus.trackedExecutionTasksByResourceType = SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE;
            requestStatus.trackedAllocationTasksByResourceType = SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE;

            List<String> trackedTasks;

            if (isContainerType(state)) {
                trackedTasks = SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE
                        .get(ResourceType.CONTAINER_TYPE);
            } else if (isComputeType(state)) {
                trackedTasks = SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE
                        .get(ResourceType.COMPUTE_TYPE);
            } else if (isContainerNetworkType(state)) {
                trackedTasks = SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE
                        .get(ResourceType.NETWORK_TYPE);
            } else {
                trackedTasks = new ArrayList<>();
                for (List<String> vals : SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE.values()) {
                    trackedTasks.addAll(vals);
                }
            }
            requestStatus.addTrackedTasks(trackedTasks.toArray(new String[0]));
        } else if (isPostAllocationOperation(state)) {
            requestStatus.addTrackedTasks(ContainerAllocationTaskService.DISPLAY_NAME);
        } else {
            if (isRemoveOperation(state)) {
                if (isContainerHostType(state)) {
                    requestStatus.addTrackedTasks(ContainerHostRemovalTaskService.DISPLAY_NAME);
                } else {
                    requestStatus.addTrackedTasks(ContainerRemovalTaskService.DISPLAY_NAME);
                }
            } else if (isContainerClusteringOperation(state)) {
                requestStatus.addTrackedTasks(ContainerClusteringTaskService.DISPLAY_NAME);
            } else {
                requestStatus.addTrackedTasks(ContainerOperationTaskService.DISPLAY_NAME);
            }
        }

        sendRequest(Operation.createPost(this, RequestStatusFactoryService.SELF_LINK)
                .setBody(requestStatus)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failed to create request tracker for: "
                                + state.documentSelfLink, e);
                        op.fail(e);
                        return;
                    }
                    logFine("Created request tracker: %s", requestStatus.documentSelfLink);
                    state.requestTrackerLink = o.getBody(RequestStatus.class).documentSelfLink;
                    op.complete(); /* complete the original start operation */
                }));
        return true;// don't complete the start operation
    }

    private void getContainerDescription(RequestBrokerState state,
            Consumer<ContainerDescription> callbackFunction) {

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        String errMsg = String.format(
                                "Failure retrieving container description state: %s ",
                                state.resourceDescriptionLink);
                        failTask(errMsg, e);
                        return;
                    }

                    ContainerDescription desc = o.getBody(ContainerDescription.class);
                    callbackFunction.accept(desc);
                }));
    }

    private void getComputeDescription(RequestBrokerState state,
            Consumer<ComputeDescription> callbackFunction) {

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        String errMsg = String.format(
                                "Failure retrieving compute description state: %s ",
                                state.resourceDescriptionLink);
                        failTask(errMsg, e);
                        return;
                    }

                    ComputeDescription desc = o.getBody(ComputeDescription.class);
                    callbackFunction.accept(desc);
                }));
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        RequestBrokerState template = (RequestBrokerState) super.getDocumentTemplate();

        template.customProperties = new HashMap<String, String>(1);
        template.customProperties.put("propKey string", "customPropertyValue string");
        template.resourceType = "ContainerType string";
        template.resourceDescriptionLink = "resources/container-description/docker-nginx string";
        template.taskInfo = new TaskState();
        template.taskInfo.stage = TaskStage.CREATED;
        template.taskSubStage = SubStage.CREATED;
        template.resourceLinks = new ArrayList<>(1);
        template.resourceLinks.add("resourceLink (string)");

        setDocumentTemplateIndexingOptions(template, EnumSet.noneOf(PropertyIndexingOption.class),
                RequestBrokerState.FIELD_NAME_TASK_INFO,
                RequestBrokerState.FIELD_NAME_TASK_SUB_STAGE);

        setDocumentTemplateIndexingOptions(template, EnumSet.of(PropertyIndexingOption.STORE_ONLY),
                RequestBrokerState.FIELD_RESOURCE_POLICY_LINK);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SINGLE_ASSIGNMENT),
                RequestBrokerState.FIELD_NAME_RESOURCE_TYPE,
                RequestBrokerState.FIELD_NAME_OPERATION,
                RequestBrokerState.FIELD_RESOURCE_DESC_LINK,
                RequestBrokerState.FIELD_RESOURCE_TENANT_LINKS,
                RequestBrokerState.FIELD_RESOURCE_COUNT,
                RequestBrokerState.FIELD_RESOURCE_LINKS,
                RequestBrokerState.FIELD_RESOURCE_POLICY_LINK);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SERVICE_USE),
                RequestBrokerState.FIELD_RESOURCE_POLICY_LINK);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.OPTIONAL),
                RequestBrokerState.FIELD_RESOURCE_TENANT_LINKS,

                RequestBrokerState.FIELD_RESOURCE_COUNT,
                RequestBrokerState.FIELD_RESOURCE_LINKS);

        return template;
    }
}
