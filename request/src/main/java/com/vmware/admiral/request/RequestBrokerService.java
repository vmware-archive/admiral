/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
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
import static com.vmware.admiral.common.util.ServiceUtils.EXPIRATION_MICROS;
import static com.vmware.admiral.common.util.ServiceUtils.getExpirationTimeFromNowInMicros;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_ALLOCATION_REQUEST;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;
import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.admiral.adapter.common.ApplicationOperationType;
import com.vmware.admiral.adapter.common.ClosureOperationType;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.adapter.common.VolumeOperationType;
import com.vmware.admiral.adapter.pks.PKSConstants;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.SecurityContext;
import com.vmware.admiral.auth.idm.SecurityContext.ProjectEntry;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.RegistryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.TenantLinksUtil;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.util.ResourceDescriptionUtil;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.admiral.request.ClosureAllocationTaskService.ClosureAllocationTaskState;
import com.vmware.admiral.request.ClosureProvisionTaskService.ClosureProvisionTaskState;
import com.vmware.admiral.request.ClosureRemovalTaskService.ClosureRemovalTaskState;
import com.vmware.admiral.request.ClusteringTaskService.ClusteringTaskState;
import com.vmware.admiral.request.ContainerAllocationTaskService.ContainerAllocationTaskState;
import com.vmware.admiral.request.ContainerLoadBalancerAllocationTaskService.ContainerLoadBalancerAllocationTaskState;
import com.vmware.admiral.request.ContainerLoadBalancerProvisionTaskService.ContainerLoadBalancerProvisionTaskState;
import com.vmware.admiral.request.ContainerLoadBalancerRemovalTaskService.ContainerLoadBalancerRemovalTaskState;
import com.vmware.admiral.request.ContainerNetworkAllocationTaskService.ContainerNetworkAllocationTaskState;
import com.vmware.admiral.request.ContainerNetworkProvisionTaskService.ContainerNetworkProvisionTaskState;
import com.vmware.admiral.request.ContainerNetworkRemovalTaskService.ContainerNetworkRemovalTaskState;
import com.vmware.admiral.request.ContainerOperationTaskService.ContainerOperationTaskState;
import com.vmware.admiral.request.ContainerRemovalTaskService.ContainerRemovalTaskState;
import com.vmware.admiral.request.ContainerVolumeAllocationTaskService.ContainerVolumeAllocationTaskState;
import com.vmware.admiral.request.ContainerVolumeProvisionTaskService.ContainerVolumeProvisionTaskState;
import com.vmware.admiral.request.ContainerVolumeRemovalTaskService.ContainerVolumeRemovalTaskState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState.SubStage;
import com.vmware.admiral.request.RequestStatusService.RequestStatus;
import com.vmware.admiral.request.ReservationRemovalTaskService.ReservationRemovalTaskState;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState;
import com.vmware.admiral.request.composition.CompositeComponentRemovalTaskService;
import com.vmware.admiral.request.composition.CompositeComponentRemovalTaskService.CompositeComponentRemovalTaskState;
import com.vmware.admiral.request.composition.CompositionTaskFactoryService;
import com.vmware.admiral.request.composition.CompositionTaskService.CompositionTaskState;
import com.vmware.admiral.request.compute.ComputeOperationType;
import com.vmware.admiral.request.compute.ComputeRemovalTaskService;
import com.vmware.admiral.request.compute.ComputeRemovalTaskService.ComputeRemovalTaskState;
import com.vmware.admiral.request.kubernetes.CompositeKubernetesProvisioningTaskService;
import com.vmware.admiral.request.pks.PKSClusterProvisioningTaskService;
import com.vmware.admiral.request.pks.PKSClusterProvisioningTaskService.PKSProvisioningTaskState;
import com.vmware.admiral.request.pks.PKSClusterRemovalTaskService;
import com.vmware.admiral.request.pks.PKSClusterRemovalTaskService.PKSClusterRemovalTaskState;
import com.vmware.admiral.request.pks.PKSClusterResizeTaskService;
import com.vmware.admiral.request.pks.PKSClusterResizeTaskService.PKSClusterResizeTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost.ServiceAlreadyStartedException;
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
    protected static final String REGISTRY_WHITELIST_CHECK_FAILED_ERROR_CODE = "request.registry.whitelist.check.failed";
    protected static final String REGISTRY_WHITELIST_CHECK_FAILED_ERROR_FORMAT = "Registry whitelist check failed for image %s";

    public static class RequestBrokerState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<RequestBrokerState.SubStage> {
        public static final String PROVISION_RESOURCE_OPERATION = "PROVISION_RESOURCE";
        public static final String REMOVE_RESOURCE_OPERATION = "REMOVE_RESOURCE";
        public static final String CLUSTER_RESOURCE_OPERATION = "CLUSTER_RESOURCE";
        public static final String CONFIGURE_HOST_OPERATION = "CONFIGURE_HOST";
        public static final String RESIZE_RESOURCE = "RESIZE_RESOURCE";

        public enum SubStage {
            CREATED, RESOURCE_COUNTED, RESERVING, RESERVED, ALLOCATING, ALLOCATED, REQUEST_FAILED,
            RESERVATION_CLEANUP, RESERVATION_CLEANED_UP, COMPLETED, ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(RESERVING, ALLOCATING, RESERVATION_CLEANUP));
        }

        /**
         * (Required) Type of resource to create.
         */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED }, indexing = STORE_ONLY)
        public String resourceType;

        /**
         * (Required) The operation name/id to be performed
         */
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String operation;

        /**
         * (Required) The description that defines the requested resource.
         */
        @PropertyOptions(usage = SINGLE_ASSIGNMENT, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        /**
         * (Optional- default 1) Number of resources to provision.
         */
        @PropertyOptions(usage = { OPTIONAL, SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public long resourceCount;

        /**
         * Set by Task when resources are provisioned.
         */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, SERVICE_USE,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        public Long actualResourceCount;

        /**
         * Set by Task when resources are provisioned.
         */
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, SERVICE_USE,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String groupResourcePlacementLink;
    }

    public RequestBrokerService() {
        super(RequestBrokerState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(RequestBrokerState state) throws IllegalArgumentException {
        if (state.operation == null) {
            state.operation = RequestBrokerState.PROVISION_RESOURCE_OPERATION;
        }

        if (isProvisionOperation(state) || isClusteringOperation(state)) {
            if (!isPKSClusterType(state)) {
                assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");
            }
        } else {
            assertNotEmpty(state.resourceLinks, "resourceLinks");
        }

        if (!(isContainerType(state) || isContainerHostType(state) || isContainerNetworkType(state)
                || isContainerVolumeType(state)
                || isComputeType(state)
                || isCompositeComponentType(state) || isClosureType(state)
                || isContainerLoadBalancerType(state)
                || isPKSClusterType(state))) {
            throw new LocalizableValidationException(
                    String.format("Only [ %s ] resource types are supported.",
                            ResourceType.getAllTypesAsString()),
                    "request.supported.resource-types", ResourceType.getAllTypesAsString());
        }

        if (state.resourceCount <= 0) {
            state.resourceCount = 1;
        }
    }

    @Override
    protected boolean validateStateOnStart(RequestBrokerState state, Operation startOpr) {
        super.validateStateOnStart(state, startOpr);

        validateUserAuthorization(state, startOpr)
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        startOpr.fail(ex);
                        return;
                    }
                    boolean completed = createRequestTrackerIfNoneProvided(state, startOpr);
                    if (!completed) {
                        startOpr.complete();
                    }
                });

        return true;
    }

    @Override
    protected void handleStartedStagePatch(RequestBrokerState state) {
        switch (state.taskSubStage) {
        case CREATED:
            calculateActualRequestedResources(state, SubStage.RESOURCE_COUNTED);
            break;
        case RESOURCE_COUNTED:
            if (isProvisionOperation(state)) {
                if (isCompositeComponentType(state)) {
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
            String postAllocationOperation = getPostAllocationOperation(state);
            if (postAllocationOperation != null) {
                state.operation = postAllocationOperation;
                createAllocationTasks(state);
            } else {
                complete();
            }
            break;
        case REQUEST_FAILED:
            if (isProvisionOperation(state)) {
                createReservationRemovalTask(state);
            } else if (isPostAllocationOperation(state)) {
                if (isComputeType(state)) {
                    createComputeRemovalTask(state);
                } else if (isContainerLoadBalancerType(state)) {
                    createContainerLoadBalancerRemovalTask(state);
                } else if (isContainerNetworkType(state)) {
                    createContainerNetworkRemovalTask(state, true);
                } else if (isContainerVolumeType(state)) {
                    createContainerVolumeRemovalTask(state, true);
                } else {
                    createContainerRemovalTasks(state, false);
                }
            } else {
                proceedTo(SubStage.ERROR);
            }
            break;
        case RESERVATION_CLEANUP:
            break;
        case RESERVATION_CLEANED_UP:
            if (isContainerNetworkType(state)) {
                createContainerNetworkRemovalTask(state, true);
            } else if (isContainerVolumeType(state)) {
                createContainerVolumeRemovalTask(state, true);
            } else if (isClosureType(state)) {
                createClosureRemovalTasks(state);
            } else if (isPKSClusterType(state)) {
                createPKSClusterRemovalTasks(state,
                        !PKSClusterProvisioningTaskService.DESTROY_CLUSTERS_AFTER_FAILED_PROVISION,
                        true);
            } else {
                createContainerRemovalTasks(state, true);
            }
            break;
        case COMPLETED:
            complete();
            break;
        case ERROR:
            completeWithError();

            if (isCompositeComponentType(state)) {
                ResourceDescriptionUtil.deleteClonedCompositeDescription(getHost(),
                        state.resourceDescriptionLink);
            } else {
                if (!isPKSClusterType(state)) {
                    ResourceDescriptionUtil
                            .deleteResourceDescription(getHost(), state.resourceDescriptionLink);
                }
            }

            break;
        default:
            break;
        }
    }

    private void calculateActualRequestedResources(RequestBrokerState state, SubStage next) {
        if (isPKSClusterType(state)) {
            proceedTo(next, s -> {
                s.actualResourceCount = state.resourceCount;
                s.documentExpirationTimeMicros = getExpirationTimeFromNowInMicros(
                        EXPIRATION_MICROS * 2);
            });
            return;
        }

        if (isProvisionOperation(state)) {
            if (isContainerType(state)) {
                getContainerDescription(state, (cd) -> {
                    proceedTo(next, s -> {
                        s.actualResourceCount = getRequestContainerResourceCount(state, cd);
                        updateRequestTrackerName(state, cd.name);
                    });
                });
            } else {
                proceedTo(next, s -> s.actualResourceCount = state.resourceCount);
            }
        } else {
            proceedTo(next, s -> s.actualResourceCount = state.resourceCount);
        }
    }

    private void updateRequestTrackerName(RequestBrokerState state, String name) {
        RequestStatus requestStatus = new RequestStatus();
        requestStatus.name = name;
        requestStatus.documentSelfLink = state.requestTrackerLink;
        requestStatus.taskInfo = new TaskState();
        Operation.createPatch(getHost(), state.requestTrackerLink)
                .setBodyNoCloning(requestStatus)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logFine("Couldn't set name to the request status because of: %s", e.getMessage());
                    }
                }).sendWith(this);

    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(RequestBrokerState state) {
        final CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logFine("No resourceLinks found for allocated resources. %s", state.taskInfo.stage);
        }
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
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
    public void failTask(String msg, Throwable e) {
        String errMsg = msg != null ? msg : "Unexpected State";
        if (e != null) {
            logWarning("Fail task: %s. Error: %s", errMsg, Utils.toString(e));
        } else {
            logWarning("Fail task: %s", errMsg);
        }

        ServiceErrorResponse rsp;
        if (e != null) {
            rsp = getServiceErrorResponse(e);
        } else {
            rsp = new ServiceErrorResponse();
            rsp.message = errMsg;
        }

        proceedTo(SubStage.REQUEST_FAILED, s -> {
            s.taskInfo.failure = rsp;
        });
    }

    private void failRequest(RequestBrokerState state, String message, Throwable ex) {
        logSevere("%s, reason: %s", message, Utils.toString(ex));
        proceedTo(SubStage.ERROR, s -> {
            s.taskInfo.failure = getServiceErrorResponse(ex);
        });
    }

    @Override
    protected void handleFailedStagePatch(RequestBrokerState state) {
        EventLogState eventLog = new EventLogState();
        if (state.taskInfo.failure != null) {
            eventLog.description = state.taskInfo.failure.message;
            if (eventLog.description == null) {
                eventLog.description = "Unexpected error, status: "
                        + state.taskInfo.failure.statusCode;
                logWarning("Patch failure stack trace: %s", state.taskInfo.failure.stackTrace);
            }
        } else {
            eventLog.description = "Unexpected error, and empty failure body in substage: "
                    + state.taskSubStage;
            logWarning("Patch failure with unknown failure in substage: %s", state.taskSubStage);
        }
        eventLog.eventLogType = EventLogType.ERROR;
        eventLog.resourceType = getClass().getName();
        eventLog.tenantLinks = state.tenantLinks;

        sendRequest(Operation.createPost(this, EventLogService.FACTORY_LINK)
                .setBodyNoCloning(eventLog)
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
                    .setBodyNoCloning(new ServiceDocument())
                    .setCompletion((o, e) -> {
                        if (Operation.STATUS_CODE_NOT_FOUND == o.getStatusCode()) {
                            logWarning("Request status not found: %s", state.requestTrackerLink);
                        } else if (e != null) {
                            logWarning("Exception while deleting request status: %s. Error: %s",
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
                createContainerRemovalTasks(state, false);
            } else if (isClusteringOperation(state)) {
                createContainerClusteringTasks(state);
            } else {
                createContainerOperationTasks(state);
            }
        } else if (isCompositeComponentType(state)) {
            createCompositeComponentOperationTask(state);
        } else if (isContainerHostType(state)) {
            if (isRemoveOperation(state)) {
                createComputeRemovalTask(state, true);
            } else {
                failTask(null, new LocalizableValidationException("Not supported operation: "
                        + state.operation, "request.operation.not.supported", state.operation));
            }
        } else if (isComputeType(state)) {
            if (isRemoveOperation(state)) {
                createComputeRemovalTask(state);
            } else {
                failTask(null, new LocalizableValidationException("Not supported operation: "
                        + state.operation, "request.operation.not.supported", state.operation));
            }
        } else if (isContainerLoadBalancerType(state)) {
            if (isRemoveOperation(state)) {
                createContainerLoadBalancerRemovalTask(state);
            } else {
                failTask(null, new IllegalArgumentException("Not supported operation: "
                        + state.operation));
            }
        } else if (isContainerNetworkType(state)) {
            if (isRemoveOperation(state)) {
                createContainerNetworkRemovalTask(state);
            } else {
                failTask(null, new LocalizableValidationException("Not supported operation: "
                        + state.operation, "request.operation.not.supported", state.operation));
            }
        } else if (isContainerVolumeType(state)) {
            if (isRemoveOperation(state)) {
                createContainerVolumeRemovalTask(state);
            } else {
                failTask(null, new LocalizableValidationException("Not supported operation: "
                        + state.operation, "request.operation.not.supported", state.operation));
            }
        } else if (isClosureType(state)) {
            if (isRemoveOperation(state)) {
                // createClosureRemovalTask(state);
                createClosureRemovalTasks(state);
            } else {
                failTask(null, new LocalizableValidationException(
                        "Not supported operation for closure type: "
                                + state.operation,
                        "request.closure.operation.not.supported",
                        state.operation));
            }
        } else if (isPKSClusterType(state)) {
            if (isRemoveOperation(state)) {
                createPKSClusterRemovalTasks(state, false,false);
            } else if (isResizeOperation(state)) {
                createPKSClusterResizeOperation(state);
            } else {
                failTask(null, new LocalizableValidationException("Not supported operation: "
                        + state.operation, "request.operation.not.supported", state.operation));
            }
        } else {
            failTask(null, new LocalizableValidationException("Not supported resourceType: "
                    + state.resourceType, "request.resource-type.not.supported",
                    state.resourceType));
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

            Set<String> componentLinks = new HashSet<>();
            new ServiceDocumentQuery<>(getHost(), CompositeComponent.class)
                    .query(compositeQueryTask, (r) -> {
                        if (r.hasException()) {
                            logSevere("Failed to create operation task for %s - %s",
                                    r.getDocumentSelfLink(), Utils.toString(r.getException()));
                        } else if (r.hasResult()) {
                            componentLinks.addAll(r.getResult().componentLinks);
                        } else {
                            if (componentLinks.isEmpty()) {
                                logSevere("Failed to create operation task - composite component's"
                                        + " container links are empty");
                            }
                            state.resourceLinks = componentLinks;

                            state.resourceType = ResourceType.CONTAINER_TYPE.getName();
                            createContainerOperationTasks(state);
                        }
                    });
        }
    }

    private void createCompositeComponentRemovalTask(RequestBrokerState state) {
        if (state.resourceLinks == null) {
            proceedTo(SubStage.ERROR);
            return;
        }

        CompositeComponentRemovalTaskState removalState = new CompositeComponentRemovalTaskState();
        removalState.resourceLinks = state.resourceLinks;
        removalState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                SubStage.COMPLETED, SubStage.ERROR);
        removalState.documentSelfLink = getSelfId();
        removalState.requestTrackerLink = state.requestTrackerLink;
        removalState.tenantLinks = state.tenantLinks;

        Operation post = Operation
                .createPost(this, CompositeComponentRemovalTaskService.FACTORY_LINK)
                .setBodyNoCloning(removalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create container removal task", ex);
                    }

                    proceedTo(SubStage.ALLOCATING);
                });
        sendRequest(post);
    }

    private void createComputeRemovalTask(RequestBrokerState state) {
        createComputeRemovalTask(state, false);
    }

    private void createComputeRemovalTask(RequestBrokerState state,
            boolean skipReleaseResourceQuota) {
        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED
                || state.taskSubStage == SubStage.RESERVATION_CLEANED_UP;

        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            proceedTo(errorState ? SubStage.ERROR : SubStage.ALLOCATED);
            return;
        }
        ComputeRemovalTaskState computeRemovalState = new ComputeRemovalTaskState();
        computeRemovalState.resourceLinks = state.resourceLinks;
        computeRemovalState.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.ALLOCATED,
                TaskStage.FAILED, SubStage.ERROR);
        computeRemovalState.documentSelfLink = getSelfId();
        computeRemovalState.tenantLinks = state.tenantLinks;
        computeRemovalState.customProperties = state.customProperties;
        computeRemovalState.skipReleaseResourceQuota = skipReleaseResourceQuota;
        computeRemovalState.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ComputeRemovalTaskService.FACTORY_LINK)
                .setBodyNoCloning(computeRemovalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failRequest(state, "Failed to create compute removal operation task", ex);
                        return;
                    }
                    proceedTo(SubStage.ALLOCATING);
                }));
    }

    private void createContainerNetworkRemovalTask(RequestBrokerState state) {
        createContainerNetworkRemovalTask(state, false);
    }

    private void createContainerNetworkRemovalTask(RequestBrokerState state,
            boolean cleanupRemoval) {

        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED
                || state.taskSubStage == SubStage.RESERVATION_CLEANED_UP;

        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            proceedTo(errorState ? SubStage.ERROR : SubStage.ALLOCATED);
            return;
        }
        ContainerNetworkRemovalTaskState removalState = new ContainerNetworkRemovalTaskState();
        removalState.resourceLinks = state.resourceLinks;
        removalState.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.ALLOCATED,
                TaskStage.FAILED, SubStage.ERROR);
        removalState.documentSelfLink = getSelfId();
        removalState.customProperties = state.customProperties;
        if (!errorState) {
            removalState.requestTrackerLink = state.requestTrackerLink;
        }

        removalState.externalInspectOnly = (state.customProperties != null
                && "true".equalsIgnoreCase(state.customProperties
                        .get(ContainerNetworkRemovalTaskService
                                .EXTERNAL_INSPECT_ONLY_CUSTOM_PROPERTY)));
        removalState.cleanupRemoval = cleanupRemoval;

        sendRequest(Operation.createPost(this, ContainerNetworkRemovalTaskService.FACTORY_LINK)
                .setBodyNoCloning(removalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failRequest(state,
                                "Failed to create container network removal operation task", ex);
                        return;
                    }
                    proceedTo(SubStage.ALLOCATING);
                }));
    }

    private void createContainerVolumeRemovalTask(RequestBrokerState state) {
        createContainerVolumeRemovalTask(state, false);
    }

    private void createContainerVolumeRemovalTask(RequestBrokerState state,
            boolean cleanupRemoval) {

        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED
                || state.taskSubStage == SubStage.RESERVATION_CLEANED_UP;

        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            proceedTo(errorState ? SubStage.ERROR : SubStage.ALLOCATED);
            return;
        }
        ContainerVolumeRemovalTaskState removalState = new ContainerVolumeRemovalTaskState();
        removalState.resourceLinks = state.resourceLinks;
        removalState.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.ALLOCATED,
                TaskStage.FAILED, SubStage.ERROR);
        removalState.documentSelfLink = getSelfId();
        removalState.customProperties = state.customProperties;
        if (!errorState) {
            removalState.requestTrackerLink = state.requestTrackerLink;
        }

        removalState.externalInspectOnly = (state.customProperties != null
                && "true".equalsIgnoreCase(state.customProperties
                        .get(ContainerVolumeRemovalTaskService
                                .EXTERNAL_INSPECT_ONLY_CUSTOM_PROPERTY)));
        removalState.cleanupRemoval = cleanupRemoval;

        sendRequest(Operation.createPost(this, ContainerVolumeRemovalTaskService.FACTORY_LINK)
                .setBodyNoCloning(removalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failRequest(state,
                                "Failed to create container volume removal operation task", ex);
                        return;
                    }
                    proceedTo(SubStage.ALLOCATING);
                }));
    }

    private void createContainerRemovalTasks(RequestBrokerState state,
            boolean skipReleaseResourcePlacement) {
        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED
                || state.taskSubStage == SubStage.RESERVATION_CLEANED_UP;

        if (state.resourceLinks == null) {
            proceedTo(errorState ? SubStage.ERROR : SubStage.ALLOCATED);
            return;
        }

        ContainerRemovalTaskState removalState = new ContainerRemovalTaskState();
        removalState.skipReleaseResourcePlacement = skipReleaseResourcePlacement;
        removalState.resourceLinks = state.resourceLinks.stream()
                .filter((l) -> l.startsWith(ContainerFactoryService.SELF_LINK))
                .collect(Collectors.toSet());
        removalState.customProperties = state.customProperties;
        removalState.tenantLinks = state.tenantLinks;

        removalState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.ALLOCATED,
                TaskStage.FAILED, SubStage.ERROR);
        removalState.documentSelfLink = getSelfId();
        removalState.requestTrackerLink = state.requestTrackerLink;
        Operation post = Operation.createPost(this, ContainerRemovalTaskFactoryService.SELF_LINK)
                .setBodyNoCloning(removalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failRequest(state, "Failed to create container removal task", ex);
                        return;
                    }
                    if (!errorState) {
                        proceedTo(SubStage.ALLOCATING);
                    }
                });
        sendRequest(post);
    }

    private void createClosureRemovalTasks(RequestBrokerState state) {
        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED
                || state.taskSubStage == SubStage.RESERVATION_CLEANED_UP;

        if (state.resourceLinks == null) {
            SubStage stage = errorState ? SubStage.ERROR : SubStage.ALLOCATED;
            proceedTo(stage);
            return;
        }

        ClosureRemovalTaskState removalState = new ClosureRemovalTaskState();
        removalState.resourceLinks = state.resourceLinks.stream()
                .filter((l) -> l.startsWith(ClosureFactoryService.FACTORY_LINK))
                .collect(Collectors.toSet());

        removalState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.ALLOCATED,
                TaskStage.FAILED, SubStage.ERROR);
        removalState.documentSelfLink = getSelfId();
        removalState.requestTrackerLink = state.requestTrackerLink;
        Operation post = Operation.createPost(this, ClosureRemovalTaskFactoryService.SELF_LINK)
                .setBodyNoCloning(removalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failRequest(state, "Failed to create closure removal task", ex);
                        return;
                    }
                    if (!errorState) {
                        proceedTo(SubStage.ALLOCATING);
                    }
                });
        sendRequest(post);
    }

    private void createContainerOperationTasks(RequestBrokerState state) {
        ContainerOperationTaskState operationState = new ContainerOperationTaskState();
        operationState.resourceLinks = state.resourceLinks;
        operationState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.ALLOCATED, TaskStage.FAILED, SubStage.ERROR);
        operationState.operation = state.operation;
        operationState.documentSelfLink = getSelfId();
        operationState.customProperties = state.customProperties;
        operationState.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ContainerOperationTaskFactoryService.SELF_LINK)
                .setBodyNoCloning(operationState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create container operation task", ex);
                    }
                    proceedTo(SubStage.ALLOCATING);
                }));
    }

    private void createReservationTasks(RequestBrokerState state) {
        if (isContainerLoadBalancerType(state)) {
            // No reservation for now, moving on...
            proceedTo(SubStage.RESERVED);
        } else if (isContainerNetworkType(state) || isContainerVolumeType(state)) {
            // No reservation needed here, moving on...
            proceedTo(SubStage.RESERVED);
        } else if (isClosureType(state)) {
            // No reservation needed here, moving on...
            proceedTo(SubStage.RESERVED);
        } else if (isPKSClusterType(state)) {
            // No reservation needed here, moving on.directly to allocated
            proceedTo(SubStage.ALLOCATED);
        } else {
            getContainerDescription(state, (cd) -> createReservationTasks(state, cd));
        }
    }

    private void enforceRegistryWhitelist(ContainerDescription containerDescription,
            RequestBrokerState requestState, Consumer<Throwable> completionHandler) {

        RegistryUtil.isProvisioningAllowedByRegistryWhitelist(getHost(), requestState.tenantLinks,
                containerDescription.image, (isAllowed, error) -> {

                    if (error != null) {
                        completionHandler.accept(error);
                    } else if (!isAllowed) {
                        String message = String.format(
                                "Image %s is not stored in a white-listed registry.",
                                containerDescription.image);
                        completionHandler.accept(new IllegalStateException(message));
                    } else {
                        completionHandler.accept(null);
                    }
                });
    }

    private void createReservationTasks(RequestBrokerState state,
            ContainerDescription containerDescription) {

        if (containerDescription == null) {
            getContainerDescription(state, (cd) -> createReservationTasks(state, cd));
            return;
        }

        enforceRegistryWhitelist(containerDescription, state, (ex) -> {
            if (ex != null) {
                String error = String.format(
                        REGISTRY_WHITELIST_CHECK_FAILED_ERROR_FORMAT,
                        containerDescription.image);
                failTask(error, new LocalizableValidationException(error,
                        REGISTRY_WHITELIST_CHECK_FAILED_ERROR_CODE,
                        containerDescription.image));
            } else {
                doCreateReservationTasks(state, containerDescription);
            }
        });
    }

    private void doCreateReservationTasks(RequestBrokerState state,
            ContainerDescription containerDescription) {
        ReservationTaskState rsrvTask = new ReservationTaskState();
        rsrvTask.documentSelfLink = getSelfId();
        rsrvTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.RESERVED, TaskStage.STARTED, SubStage.ERROR);

        rsrvTask.resourceCount = state.actualResourceCount;
        rsrvTask.tenantLinks = state.tenantLinks;
        rsrvTask.resourceType = state.resourceType;
        rsrvTask.resourceDescriptionLink = state.resourceDescriptionLink;
        rsrvTask.customProperties = mergeCustomProperties(
                state.customProperties, containerDescription.customProperties);
        rsrvTask.requestTrackerLink = state.requestTrackerLink;

        if (state.groupResourcePlacementLink != null) {
            rsrvTask.groupResourcePlacementLink = state.groupResourcePlacementLink;
            rsrvTask.taskSubStage = ReservationTaskState.SubStage.RESERVATION_SELECTED;
            rsrvTask.resourcePoolsPerGroupPlacementLinks = new LinkedHashMap<>(0);
        }

        sendRequest(Operation.createPost(this, ReservationTaskFactoryService.SELF_LINK)
                .setBodyNoCloning(rsrvTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating reservation task", e);
                        return;
                    }
                    proceedTo(SubStage.RESERVING);
                }));
    }

    private long getRequestContainerResourceCount(RequestBrokerState state,
            ContainerDescription containerDescription) {
        long resourceCount;
        if (containerDescription._cluster != null && containerDescription._cluster > 0
                && !isClusteringOperation(state)) {
            resourceCount = state.resourceCount * containerDescription._cluster;
        } else {
            resourceCount = state.resourceCount;
        }
        return resourceCount;
    }

    private void createContainerAllocationTask(RequestBrokerState state) {
        getContainerDescription(state, (containerDesc) -> {
            ContainerAllocationTaskState allocationTask = new ContainerAllocationTaskState();
            allocationTask.documentSelfLink = getSelfId();
            allocationTask.serviceTaskCallback = ServiceTaskCallback.create(
                    getSelfLink(), TaskStage.STARTED, SubStage.ALLOCATED,
                    TaskStage.STARTED, SubStage.REQUEST_FAILED);
            allocationTask.customProperties = state.customProperties;
            allocationTask.resourceDescriptionLink = state.resourceDescriptionLink;

            if (isProvisionOperation(state) && !isClusteringOperation(state)) {
                // deploy the default number of clustered container nodes
                allocationTask.resourceCount = state.actualResourceCount;
            } else {
                allocationTask.resourceCount = state.resourceLinks != null
                        ? state.resourceLinks.size() : state.actualResourceCount;
            }

            allocationTask.resourceType = state.resourceType;
            allocationTask.tenantLinks = state.tenantLinks;
            allocationTask.groupResourcePlacementLink = state.groupResourcePlacementLink;
            allocationTask.requestTrackerLink = state.requestTrackerLink;
            allocationTask.resourceLinks = state.resourceLinks;
            allocationTask.postAllocation = isPostAllocationOperation(state);

            sendRequest(Operation
                    .createPost(this, ContainerAllocationTaskFactoryService.SELF_LINK)
                    .setBodyNoCloning(allocationTask)
                    .setContextId(getSelfId())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            if (e instanceof ServiceAlreadyStartedException) {
                                getHost().log(Level.WARNING, "Service %s already started.",
                                        allocationTask.documentSelfLink);
                                return;
                            }
                            failTask("Failure creating resource allocation task", e);
                            return;
                        }
                        proceedTo(SubStage.ALLOCATING);
                    }));
        });
    }

    private void createContainerNetworkAllocationTask(RequestBrokerState state) {
        // 1. allocate the network
        ContainerNetworkAllocationTaskState allocationTask =
                new ContainerNetworkAllocationTaskState();
        allocationTask.documentSelfLink = getSelfId();
        allocationTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(), TaskStage.STARTED, SubStage.ALLOCATED,
                TaskStage.STARTED, SubStage.ERROR);
        allocationTask.customProperties = state.customProperties;
        allocationTask.resourceDescriptionLink = state.resourceDescriptionLink;

        allocationTask.tenantLinks = state.tenantLinks;
        allocationTask.requestTrackerLink = state.requestTrackerLink;
        allocationTask.resourceLinks = state.resourceLinks;
        allocationTask.resourceCount = state.actualResourceCount;

        sendRequest(Operation
                .createPost(this, ContainerNetworkAllocationTaskService.FACTORY_LINK)
                .setBodyNoCloning(allocationTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating resource allocation task", e);
                        return;
                    }
                    proceedTo(SubStage.ALLOCATING);
                }));
    }

    private void createContainerNetworkProvisioningTask(RequestBrokerState state) {
        // 2. provision the network
        ContainerNetworkProvisionTaskState provisionTask = new ContainerNetworkProvisionTaskState();
        provisionTask.documentSelfLink = getSelfId();
        provisionTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(), TaskStage.STARTED, SubStage.COMPLETED,
                TaskStage.STARTED, SubStage.REQUEST_FAILED);
        provisionTask.customProperties = state.customProperties;

        provisionTask.tenantLinks = state.tenantLinks;
        provisionTask.requestTrackerLink = state.requestTrackerLink;
        provisionTask.resourceLinks = state.resourceLinks;
        provisionTask.resourceDescriptionLink = state.resourceDescriptionLink;

        sendRequest(Operation
                .createPost(this, ContainerNetworkProvisionTaskService.FACTORY_LINK)
                .setBodyNoCloning(provisionTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating resource provision task", e);
                        return;
                    }
                }));
    }

    private void createContainerLoadBalancerAllocationTask(RequestBrokerState state) {
        ContainerLoadBalancerAllocationTaskState allocationTask = new
                ContainerLoadBalancerAllocationTaskState();
        allocationTask.documentSelfLink = getSelfId();
        allocationTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(), TaskStage.STARTED, SubStage.ALLOCATED,
                TaskStage.STARTED, SubStage.ERROR);
        allocationTask.customProperties = state.customProperties;
        allocationTask.resourceDescriptionLink = state.resourceDescriptionLink;

        allocationTask.tenantLinks = state.tenantLinks;
        allocationTask.requestTrackerLink = state.requestTrackerLink;
        allocationTask.resourceLinks = state.resourceLinks;
        allocationTask.resourceCount = state.actualResourceCount;

        sendRequest(Operation
                .createPost(this, ContainerLoadBalancerAllocationTaskService.FACTORY_LINK)
                .setBody(allocationTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating container load balancer allocation task", e);
                        return;
                    }
                    proceedTo(SubStage.ALLOCATING);
                }));
    }

    private void createContainerLoadBalancerProvisioningTask(RequestBrokerState state) {
        ContainerLoadBalancerProvisionTaskState provisionTask = new
                ContainerLoadBalancerProvisionTaskState();
        provisionTask.documentSelfLink = getSelfId();
        provisionTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(), TaskStage.STARTED, SubStage.COMPLETED,
                TaskStage.STARTED, SubStage.REQUEST_FAILED);
        provisionTask.customProperties = state.customProperties;
        provisionTask.tenantLinks = state.tenantLinks;
        provisionTask.requestTrackerLink = state.requestTrackerLink;
        provisionTask.resourceLinks = state.resourceLinks;
        provisionTask.resourceDescriptionLink = state.resourceDescriptionLink;

        sendRequest(Operation
                .createPost(this, ContainerLoadBalancerProvisionTaskService.FACTORY_LINK)
                .setBody(provisionTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating load balancer provision task", e);
                    }
                }));
    }

    private void createContainerLoadBalancerRemovalTask(RequestBrokerState state) {
        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED
                || state.taskSubStage == SubStage.RESERVATION_CLEANED_UP;

        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            proceedTo(errorState ? SubStage.ERROR : SubStage.ALLOCATED);
            return;
        }
        ContainerLoadBalancerRemovalTaskState removalState = new ContainerLoadBalancerRemovalTaskState();
        removalState.resourceLinks = state.resourceLinks;
        removalState.documentSelfLink = getSelfId();
        removalState.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(),
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.ALLOCATED,
                TaskStage.FAILED, SubStage.ERROR);
        removalState.customProperties = state.customProperties;
        removalState.tenantLinks = state.tenantLinks;
        if (!errorState) {
            removalState.requestTrackerLink = state.requestTrackerLink;
        }

        sendRequest(Operation.createPost(this, ContainerLoadBalancerRemovalTaskService
                .FACTORY_LINK)
                .setBody(removalState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failRequest(state,
                                "Failed to create load balancer removal operation task", ex);
                        return;
                    }
                    proceedTo(SubStage.ALLOCATING);
                }));
    }

    private void createContainerVolumeAllocationTask(RequestBrokerState state,
            ContainerVolumeDescription volumeDescription) {

        if (volumeDescription == null) {
            getContainerVolumeDescription(state,
                    (vd) -> createContainerVolumeAllocationTask(state, vd));
            return;
        }

        ContainerVolumeAllocationTaskState allocationTask =
                new ContainerVolumeAllocationTaskState();
        allocationTask.documentSelfLink = getSelfId();
        allocationTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(), TaskStage.STARTED, SubStage.ALLOCATED,
                TaskStage.STARTED, SubStage.ERROR);
        allocationTask.customProperties = mergeCustomProperties(
                state.customProperties, volumeDescription.customProperties);
        allocationTask.resourceDescriptionLink = state.resourceDescriptionLink;

        allocationTask.tenantLinks = state.tenantLinks;
        allocationTask.requestTrackerLink = state.requestTrackerLink;
        allocationTask.resourceLinks = state.resourceLinks;
        allocationTask.resourceCount = state.actualResourceCount;

        sendRequest(Operation
                .createPost(this, ContainerVolumeAllocationTaskService.FACTORY_LINK)
                .setBodyNoCloning(allocationTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating resource allocation task", e);
                        return;
                    }
                    proceedTo(SubStage.ALLOCATING);
                }));
    }

    private void createContainerVolumeProvisioningTask(RequestBrokerState state) {

        ContainerVolumeProvisionTaskState provisionTask = new ContainerVolumeProvisionTaskState();
        provisionTask.documentSelfLink = getSelfId();
        provisionTask.serviceTaskCallback = ServiceTaskCallback.create(
                getSelfLink(), TaskStage.STARTED, SubStage.COMPLETED,
                TaskStage.STARTED, SubStage.REQUEST_FAILED);
        provisionTask.customProperties = state.customProperties;

        provisionTask.tenantLinks = state.tenantLinks;
        provisionTask.requestTrackerLink = state.requestTrackerLink;
        provisionTask.resourceType = state.resourceType;
        provisionTask.resourceLinks = state.resourceLinks;
        provisionTask.resourceDescriptionLink = state.resourceDescriptionLink;

        sendRequest(Operation
                .createPost(this, ContainerVolumeProvisionTaskService.FACTORY_LINK)
                .setBodyNoCloning(provisionTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating resource provision task", e);
                        return;
                    }
                }));

    }

    private void createAllocationTasks(RequestBrokerState state) {
        if (isContainerType(state)) {
            createContainerAllocationTask(state);
        } else if (isContainerNetworkType(state)) {
            if (!isPostAllocationOperation(state)) {
                createContainerNetworkAllocationTask(state);
            } else {
                createContainerNetworkProvisioningTask(state);
            }
        } else if (isContainerLoadBalancerType(state)) {
            if (!isPostAllocationOperation(state)) {
                createContainerLoadBalancerAllocationTask(state);
            } else {
                createContainerLoadBalancerProvisioningTask(state);
            }
        } else if (isContainerVolumeType(state)) {
            if (!isPostAllocationOperation(state)) {
                createContainerVolumeAllocationTask(state, null);
            } else {
                createContainerVolumeProvisioningTask(state);
            }
        } else if (isClosureType(state)) {
            if (!isPostAllocationOperation(state)) {
                createClosureAllocationTask(state);
            } else {
                createClosureProvisionTask(state);
            }
        } else if (isPKSClusterType(state)) {
            createPKSClusterProvisioningTask(state);
        } else {
            failTask(null, new LocalizableValidationException("Not supported resourceType: "
                    + state.resourceType, "request.resource-type.not.supported",
                    state.resourceType));
        }
    }

    private void createClosureProvisionTask(RequestBrokerState state) {
        ClosureProvisionTaskState allocationTask = new ClosureProvisionTaskState();
        allocationTask.documentSelfLink = getSelfId();
        allocationTask.serviceTaskCallback = ServiceTaskCallback.create(
                state.documentSelfLink, TaskStage.STARTED, SubStage.COMPLETED,
                TaskStage.STARTED, SubStage.REQUEST_FAILED);
        allocationTask.customProperties = state.customProperties;
        allocationTask.resourceDescriptionLink = state.resourceDescriptionLink;
        allocationTask.resourceLinks = state.resourceLinks;
        allocationTask.tenantLinks = state.tenantLinks;
        allocationTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation
                .createPost(this, ClosureProvisionTaskService.FACTORY_LINK)
                .setBodyNoCloning(allocationTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating resource provisioning task", e);
                        return;
                    }
                }));
    }

    private void createClosureAllocationTask(RequestBrokerState state) {
        ClosureAllocationTaskState allocationTask = new ClosureAllocationTaskState();
        allocationTask.documentSelfLink = getSelfId();
        allocationTask.serviceTaskCallback = ServiceTaskCallback.create(
                state.documentSelfLink, TaskStage.STARTED, SubStage.ALLOCATED,
                TaskStage.STARTED, SubStage.REQUEST_FAILED);
        allocationTask.customProperties = state.customProperties;
        allocationTask.resourceDescriptionLink = state.resourceDescriptionLink;
        allocationTask.resourceLinks = state.resourceLinks;
        allocationTask.tenantLinks = state.tenantLinks;
        allocationTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation
                .createPost(this, ClosureAllocationTaskService.FACTORY_LINK)
                .setBodyNoCloning(allocationTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating resource allocation task", e);
                        return;
                    }
                    proceedTo(SubStage.ALLOCATING);
                }));
    }

    private void createCompositionTask(RequestBrokerState state) {
        if (isCompositeComponentType(state)) {
            CompositionTaskState compositionTask = new CompositionTaskState();
            compositionTask.documentSelfLink = getSelfId();
            compositionTask.serviceTaskCallback = ServiceTaskCallback.create(
                    getSelfLink(), TaskStage.STARTED,
                    SubStage.ALLOCATED, TaskStage.STARTED, SubStage.ERROR);
            compositionTask.customProperties = state.customProperties;
            compositionTask.resourceDescriptionLink = state.resourceDescriptionLink;
            compositionTask.tenantLinks = state.tenantLinks;
            compositionTask.requestTrackerLink = state.requestTrackerLink;

            compositionTask.groupResourcePlacementLink = state.groupResourcePlacementLink;

            sendRequest(Operation.createPost(this, CompositionTaskFactoryService.SELF_LINK)
                    .setBodyNoCloning(compositionTask)
                    .setContextId(getSelfId())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failure creating composition task", e);
                            return;
                        }
                        proceedTo(SubStage.ALLOCATING);
                    }));
        } else {
            failTask(null, new LocalizableValidationException("Not supported resourceType: "
                    + state.resourceType, "request.resource-type.not.supported",
                    state.resourceType));
        }
    }

    private void createReservationRemovalTask(RequestBrokerState state) {
        if (state.groupResourcePlacementLink == null
                || state.groupResourcePlacementLink.isEmpty()) {
            proceedTo(SubStage.RESERVATION_CLEANED_UP);
            return;
        }
        ReservationRemovalTaskState rsrvTask = new ReservationRemovalTaskState();
        rsrvTask.documentSelfLink = getSelfId();

        rsrvTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.RESERVATION_CLEANED_UP, TaskStage.FAILED,
                SubStage.ERROR);
        rsrvTask.resourceCount = state.actualResourceCount;
        rsrvTask.resourceDescriptionLink = state.resourceDescriptionLink;
        rsrvTask.groupResourcePlacementLink = state.groupResourcePlacementLink;
        rsrvTask.requestTrackerLink = state.requestTrackerLink;
        rsrvTask.tenantLinks = state.tenantLinks;

        sendRequest(Operation.createPost(this, ReservationRemovalTaskFactoryService.SELF_LINK)
                .setBodyNoCloning(rsrvTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Reservations can't be cleaned up. Error: %s", Utils.toString(e));
                    }
                    proceedTo(SubStage.RESERVATION_CLEANUP);
                }));
    }

    private void createContainerClusteringTasks(RequestBrokerState state) {
        ClusteringTaskState clusteringState = new ClusteringTaskState();
        clusteringState.resourceCount = state.actualResourceCount;
        clusteringState.postAllocation = isPostAllocationOperation(state);
        clusteringState.customProperties = state.customProperties;
        clusteringState.tenantLinks = state.tenantLinks;
        clusteringState.resourceDescriptionLink = state.resourceDescriptionLink;
        clusteringState.requestTrackerLink = state.requestTrackerLink;
        clusteringState.resourceType = state.resourceType;
        clusteringState.documentDescription = state.documentDescription;
        clusteringState.contextId = state.getCustomProperty(FIELD_NAME_CONTEXT_ID_KEY);

        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED;
        clusteringState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.ALLOCATED,
                TaskStage.FAILED, SubStage.ERROR);

        clusteringState.documentSelfLink = getSelfId();
        clusteringState.requestTrackerLink = state.requestTrackerLink;
        Operation post = Operation
                .createPost(this, ClusteringTaskService.FACTORY_LINK)
                .setBodyNoCloning(clusteringState)
                .setContextId(getSelfId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failed to create container clustering task.", ex);
                        return;
                    }
                    if (!errorState) {
                        proceedTo(SubStage.ALLOCATING);
                    }
                });
        sendRequest(post);
    }

    private void createPKSClusterProvisioningTask(RequestBrokerState state) {
        String link = buildUriPath(PKSClusterProvisioningTaskService.FACTORY_LINK, getSelfId());
        createNewIfNotExist(link, () -> {
            PKSProvisioningTaskState task = new PKSProvisioningTaskState();
            task.documentSelfLink = getSelfId();
            task.serviceTaskCallback = ServiceTaskCallback.create(
                    state.documentSelfLink, TaskStage.STARTED, SubStage.COMPLETED,
                    TaskStage.STARTED, SubStage.REQUEST_FAILED);
            task.customProperties = state.customProperties;
            task.preferMasterIP = Boolean.parseBoolean(task
                    .removeCustomProperty(PKSConstants.PKS_PREFER_MASTER_IP_PROP_NAME));
            task.endpointLink = state.getCustomProperty(PKSConstants.PKS_ENDPOINT_PROP_NAME);
            task.tenantLinks = state.tenantLinks;
            task.requestTrackerLink = state.requestTrackerLink;
            // calculate task expiration to be shortly before parent task expiration
            task.documentExpirationTimeMicros = calculatePKSTaskExpirationTime(state);

            sendRequest(Operation
                    .createPost(this, PKSClusterProvisioningTaskService.FACTORY_LINK)
                    .setBodyNoCloning(task)
                    .setContextId(getSelfId())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failure in creating PKS resource provision task", e);
                            return;
                        }
                    }));
        });
    }

    private void createPKSClusterRemovalTasks(RequestBrokerState state, boolean removeOnly,
            boolean cleanupRemoval) {
        boolean errorState = state.taskSubStage == SubStage.REQUEST_FAILED
                || state.taskSubStage == SubStage.RESERVATION_CLEANED_UP;

        if (state.resourceLinks == null || state.resourceLinks.size() == 0) {
            SubStage stage = errorState ? SubStage.ERROR : SubStage.COMPLETED;
            proceedTo(stage);
            return;
        }

        String link = buildUriPath(PKSClusterRemovalTaskService.FACTORY_LINK, getSelfId());
        createNewIfNotExist(link, () -> {
            PKSClusterRemovalTaskState task = new PKSClusterRemovalTaskState();
            task.documentSelfLink = getSelfId();
            task.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                    TaskStage.STARTED, errorState ? SubStage.ERROR : SubStage.COMPLETED,
                    TaskStage.STARTED, SubStage.ERROR);

            task.customProperties = state.customProperties;
            task.endpointLink = state.getCustomProperty(PKSConstants.PKS_ENDPOINT_PROP_NAME);
            task.clusterName = state.getCustomProperty(PKSConstants.PKS_CLUSTER_NAME_PROP_NAME);
            task.resourceLink = state.resourceLinks.iterator().next();
            task.tenantLinks = state.tenantLinks;
            task.requestTrackerLink = state.requestTrackerLink;
            task.cleanupRemoval = cleanupRemoval;
            task.removeOnly = removeOnly;
            // calculate task expiration to be shortly before parent task expiration
            task.documentExpirationTimeMicros = calculatePKSTaskExpirationTime(state);

            sendRequest(Operation
                    .createPost(this, PKSClusterRemovalTaskService.FACTORY_LINK)
                    .setBodyNoCloning(task)
                    .setContextId(getSelfId())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failure in removing PKS resource task", e);
                        }
                    }));
        });
    }

    private void createPKSClusterResizeOperation(RequestBrokerState state) {
        String link = buildUriPath(PKSClusterResizeTaskService.FACTORY_LINK, getSelfId());
        createNewIfNotExist(link, () -> {
            PKSClusterResizeTaskState task = new PKSClusterResizeTaskState();
            task.documentSelfLink = getSelfId();
            task.serviceTaskCallback = ServiceTaskCallback.create(
                    state.documentSelfLink, TaskStage.STARTED, SubStage.COMPLETED,
                    TaskStage.STARTED, SubStage.REQUEST_FAILED);
            task.customProperties = state.customProperties;
            task.resourceLink = state.resourceLinks.iterator().next();
            task.tenantLinks = state.tenantLinks;
            task.requestTrackerLink = state.requestTrackerLink;
            // calculate task expiration to be shortly before parent task expiration
            task.documentExpirationTimeMicros = calculatePKSTaskExpirationTime(state);

            sendRequest(Operation
                    .createPost(this, PKSClusterResizeTaskService.FACTORY_LINK)
                    .setBodyNoCloning(task)
                    .setContextId(getSelfId())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failure in creating PKS resource resize task", e);
                            return;
                        }
                    }));
        });
    }

    private long calculatePKSTaskExpirationTime(RequestBrokerState state) {
        long t = (state.documentExpirationTimeMicros - Utils.getSystemNowMicrosUtc()) / 2;
        t = Utils.fromNowMicrosUtc(t) - TimeUnit.MINUTES.toMicros(5);
        return Math.max(t, Utils.fromNowMicrosUtc(TimeUnit.MINUTES.toMicros(5)));
    }

    /**
     * Calls given callback if document does not exist. This utility method is used to create new
     * task only if it has not been created yet.
     */
    private void createNewIfNotExist(String taskLink, Runnable callback) {
        sendRequest(Operation
                .createGet(this, taskLink)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        callback.run();
                        return;
                    }
                    if (e != null) {
                        failTask("Fail: " + e.getMessage(), e);
                        return;
                    }
                    logInfo("Task %s is already started.", taskLink);
                }));
    }

    private boolean isProvisionOperation(RequestBrokerState state) {
        return RequestBrokerState.PROVISION_RESOURCE_OPERATION.equals(state.operation);
    }

    private boolean isAllocationOperation(RequestBrokerState state) {
        return isProvisionOperation(state) && Boolean.TRUE.toString()
                .equals(state.getCustomProperty(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST));
    }

    private boolean isPostAllocationOperation(RequestBrokerState state) {
        return (isContainerType(state) || isContainerNetworkType(state) || isComputeType(state)
                || isContainerVolumeType(state) || isClosureType(state)
                || isContainerLoadBalancerType(state))
                && (ContainerOperationType.CREATE.id.equals(state.operation)
                        || NetworkOperationType.CREATE.id.equals(state.operation)
                        || VolumeOperationType.CREATE.id.equals(state.operation)
                        || ClosureOperationType.CREATE.id.equals(state.operation)
                        || ContainerLoadBalancerOperationType.CREATE.id.equals(state.operation));
    }

    private String getPostAllocationOperation(RequestBrokerState state) {

        if (!isProvisionOperation(state)) {
            // It's not a provision resource operation but other type of operation without a
            // post-allocation operation associated.
            return null;
        }

        if (state.customProperties != null && Boolean.parseBoolean(
                state.customProperties.get(FIELD_NAME_ALLOCATION_REQUEST))) {
            // It's a provision resource operation but the __allocation_request flag indicates that
            // only the allocation must happen so that skipping the post-allocation operation.
            return null;
        }

        if (isContainerNetworkType(state)) {
            return NetworkOperationType.CREATE.id;
        } else if (isContainerVolumeType(state)) {
            return VolumeOperationType.CREATE.id;
        } else if (isContainerLoadBalancerType(state)) {
            return ContainerLoadBalancerOperationType.CREATE.id;
        } else if (isPKSClusterType(state)) {
            return PKSOperationType.CREATE_CLUSTER.id;
        } else {
            // No ContainerType here since its "unified" ContainerAllocationTaskService handles it!
            return null;
        }
    }

    private boolean isRemoveOperation(RequestBrokerState state) {
        if (RequestBrokerState.REMOVE_RESOURCE_OPERATION.equals(state.operation)) {
            return true;
        }

        if (isContainerType(state) || isCompositeComponentType(state)) {
            return ContainerOperationType.DELETE.id.equals(state.operation) ||
                    ApplicationOperationType.DELETE.id.equals(state.operation);
        }

        if (isComputeType(state)) {
            return ComputeOperationType.DELETE.id.equals(state.operation);
        }

        if (isContainerNetworkType(state)) {
            return NetworkOperationType.DELETE.id.equals(state.operation);
        }

        if (isContainerVolumeType(state)) {
            return VolumeOperationType.DELETE.id.equals(state.operation);
        }

        if (isClosureType(state)) {
            return ClosureOperationType.DELETE.id.equals(state.operation);
        }

        if (isContainerLoadBalancerType(state)) {
            return ContainerLoadBalancerOperationType.DELETE.id.equals(state.operation);
        }

        return false;
    }

    private boolean isResizeOperation(RequestBrokerState state) {
        return RequestBrokerState.RESIZE_RESOURCE.equals(state.operation);
    }

    private boolean isContainerType(RequestBrokerState state) {
        return ResourceType.CONTAINER_TYPE.getName().equals(state.resourceType);
    }

    private boolean isContainerNetworkType(RequestBrokerState state) {
        return ResourceType.NETWORK_TYPE.getName().equals(state.resourceType);
    }

    private boolean isContainerVolumeType(RequestBrokerState state) {
        return ResourceType.VOLUME_TYPE.getName().equals(state.resourceType);
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

    private boolean isContainerLoadBalancerType(RequestBrokerState state) {
        return ResourceType.CONTAINER_LOAD_BALANCER_TYPE.getName().equals(state.resourceType);
    }

    private boolean isClusteringOperation(RequestBrokerState state) {
        return RequestBrokerState.CLUSTER_RESOURCE_OPERATION.equals(state.operation)
                || state.getCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP) != null;
    }

    private boolean isClosureType(RequestBrokerState state) {
        return ResourceType.CLOSURE_TYPE.getName().equals(state.resourceType);
    }

    private boolean isPKSClusterType(RequestBrokerState state) {
        return ResourceType.PKS_CLUSTER_TYPE.getName().equals(state.resourceType);
    }

    private static final Map<ResourceType, List<String>> SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE;

    static {
        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE = new HashMap<>();
        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE.put(ResourceType.CONTAINER_TYPE, new ArrayList<>(
                Arrays.asList(ContainerAllocationTaskService.DISPLAY_NAME)));
        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE.put(ResourceType.CONTAINER_LOAD_BALANCER_TYPE,
                new ArrayList<>(
                        Arrays.asList(ContainerLoadBalancerProvisionTaskService.DISPLAY_NAME)));
        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE
                .put(ResourceType.NETWORK_TYPE, new ArrayList<>(
                        Arrays.asList(ContainerNetworkProvisionTaskService.DISPLAY_NAME)));
        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE
                .put(ResourceType.VOLUME_TYPE, new ArrayList<>(
                        Arrays.asList(ContainerVolumeProvisionTaskService.DISPLAY_NAME)));
        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE.put(ResourceType.CLOSURE_TYPE, new ArrayList<>(
                Arrays.asList(ClosureProvisionTaskService.DISPLAY_NAME)));

        // A composite component that will be deployed/handled at once, instead of separating
        // into sub tasks for it's components. Used for tracking purposes.
        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE.put(ResourceType.JOIN_COMPOSITE_COMPONENT_TYPE,
                new ArrayList<>(
                        Arrays.asList(CompositeKubernetesProvisioningTaskService.DISPLAY_NAME)));

        SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE.put(ResourceType.PKS_CLUSTER_TYPE,
                new ArrayList<>(Arrays.asList(PKSClusterProvisioningTaskService.DISPLAY_NAME)));
    }

    private static final Map<ResourceType, List<String>> SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE;

    static {
        SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE = new HashMap<>();
        SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE.put(ResourceType.CONTAINER_TYPE,
                new ArrayList<>(
                        Arrays.asList(ContainerAllocationTaskService.DISPLAY_NAME,
                                ReservationTaskService.DISPLAY_NAME,
                                PlacementHostSelectionTaskService.DISPLAY_NAME,
                                ResourceNamePrefixTaskService.DISPLAY_NAME,
                                ContainerPortsAllocationTaskService.DISPLAY_NAME)));
        SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE.put(ResourceType.CONTAINER_LOAD_BALANCER_TYPE,
                new ArrayList<>(
                        Arrays.asList(ContainerLoadBalancerAllocationTaskService.DISPLAY_NAME,
                                ResourceNamePrefixTaskService.DISPLAY_NAME)));
        SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE
                .put(ResourceType.NETWORK_TYPE, new ArrayList<>(
                        Arrays.asList(ContainerNetworkAllocationTaskService.DISPLAY_NAME,
                                ResourceNamePrefixTaskService.DISPLAY_NAME)));
        SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE
                .put(ResourceType.VOLUME_TYPE, new ArrayList<>(
                        Arrays.asList(ContainerVolumeAllocationTaskService.DISPLAY_NAME,
                                ResourceNamePrefixTaskService.DISPLAY_NAME)));
        SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE.put(ResourceType.CLOSURE_TYPE, new ArrayList<>(
                Arrays.asList(ClosureAllocationTaskService.DISPLAY_NAME,
                        ClosureProvisionTaskService.DISPLAY_NAME)));
    }

    private boolean createRequestTrackerIfNoneProvided(RequestBrokerState state, Operation op) {
        if (state.requestTrackerLink != null && !state.requestTrackerLink.isEmpty()) {
            logFine("Request tracker link provided: %s", state.requestTrackerLink);
            return false;
        }
        RequestStatus requestStatus = fromTask(new RequestStatus(), state);

        // add tracked leaf tasks depending on the request type
        if (isProvisionOperation(state)) {
            boolean allocationOnly = isAllocationOperation(state);

            requestStatus.trackedExecutionTasksByResourceType =
                    SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE;
            requestStatus.trackedAllocationTasksByResourceType =
                    SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE;

            List<String> trackedTasks = new ArrayList<>();

            if (isContainerType(state)) {
                trackedTasks.addAll(SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE
                        .get(ResourceType.CONTAINER_TYPE));
                if (!allocationOnly) {
                    trackedTasks.addAll(
                            SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE.get(ResourceType.CONTAINER_TYPE));
                }
            } else if (isComputeType(state)) {
                trackedTasks.addAll(SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE
                        .get(ResourceType.COMPUTE_TYPE));
                if (!allocationOnly) {
                    trackedTasks.addAll(SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE
                            .get(ResourceType.COMPUTE_TYPE));
                }
            } else if (isContainerNetworkType(state)) {
                trackedTasks.addAll(SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE
                        .get(ResourceType.NETWORK_TYPE));
                if (!allocationOnly) {
                    trackedTasks.addAll(SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE
                            .get(ResourceType.NETWORK_TYPE));
                }
            } else if (isContainerVolumeType(state)) {
                trackedTasks.addAll(SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE
                        .get(ResourceType.VOLUME_TYPE));
                if (!allocationOnly) {
                    trackedTasks.addAll(SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE
                            .get(ResourceType.VOLUME_TYPE));
                }
            } else if (isClosureType(state)) {
                trackedTasks.addAll(SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE
                        .get(ResourceType.CLOSURE_TYPE));
                if (!allocationOnly) {
                    trackedTasks.addAll(SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE
                            .get(ResourceType.CLOSURE_TYPE));
                }
            } else if (isPKSClusterType(state)) {
                trackedTasks.addAll(SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE
                        .get(ResourceType.PKS_CLUSTER_TYPE));
            } else {
                for (List<String> vals : SUPPORTED_ALLOCATION_TASKS_BY_RESOURCE_TYPE.values()) {
                    trackedTasks.addAll(vals);
                }
                if (!allocationOnly) {
                    for (List<String> vals : SUPPORTED_EXEC_TASKS_BY_RESOURCE_TYPE.values()) {
                        trackedTasks.addAll(vals);
                    }
                }
            }
            trackedTasks.add(RequestBrokerService.DISPLAY_NAME);
            requestStatus.addTrackedTasks(trackedTasks.toArray(new String[0]));
        } else if (isPostAllocationOperation(state)) {
            if (isContainerType(state)) {
                requestStatus.addTrackedTasks(ContainerAllocationTaskService.DISPLAY_NAME);
            } else if (isContainerNetworkType(state)) {
                requestStatus.addTrackedTasks(ContainerNetworkProvisionTaskService.DISPLAY_NAME);
            } else if (isContainerVolumeType(state)) {
                requestStatus.addTrackedTasks(ContainerVolumeProvisionTaskService.DISPLAY_NAME);
            }
        } else {
            if (isRemoveOperation(state)) {
                if (isContainerHostType(state)) {
                    requestStatus.addTrackedTasks(ContainerHostRemovalTaskService.DISPLAY_NAME);
                    requestStatus.addTrackedTasks(ComputeRemovalTaskService.DISPLAY_NAME);
                } else if (isContainerNetworkType(state)) {
                    requestStatus.addTrackedTasks(ContainerNetworkRemovalTaskService.DISPLAY_NAME);
                } else if (isContainerVolumeType(state)) {
                    requestStatus.addTrackedTasks(ContainerVolumeRemovalTaskService.DISPLAY_NAME);
                } else if (isClosureType(state)) {
                    requestStatus.addTrackedTasks(ClosureRemovalTaskService.DISPLAY_NAME);
                } else if (isComputeType(state)) {
                    requestStatus.addTrackedTasks(ComputeRemovalTaskService.DISPLAY_NAME);
                } else if (isPKSClusterType(state)) {
                    requestStatus.addTrackedTasks(PKSClusterRemovalTaskService.DISPLAY_NAME);
                } else {
                    requestStatus.addTrackedTasks(ContainerRemovalTaskService.DISPLAY_NAME);
                }
            } else if (isResizeOperation(state)) {
                if (isPKSClusterType(state)) {
                    requestStatus.addTrackedTasks(PKSClusterResizeTaskService.DISPLAY_NAME);
                }
            } else if (isClusteringOperation(state)) {
                requestStatus.addTrackedTasks(ClusteringTaskService.DISPLAY_NAME);
            } else {
                requestStatus.addTrackedTasks(ContainerOperationTaskService.DISPLAY_NAME);
            }
        }

        sendRequest(Operation.createPost(this, RequestStatusFactoryService.SELF_LINK)
                .setBodyNoCloning(requestStatus)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failed to create request tracker for: "
                                + state.documentSelfLink, e);
                        op.fail(e);
                        return;
                    }
                    state.requestTrackerLink = o.getBody(RequestStatus.class).documentSelfLink;
                    logInfo("Created request tracker: %s", state.requestTrackerLink);
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

    private void getContainerVolumeDescription(RequestBrokerState state,
            Consumer<ContainerVolumeDescription> callbackFunction) {

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        String errMsg = String.format(
                                "Failure retrieving container volume description state: %s ",
                                state.resourceDescriptionLink);
                        failTask(errMsg, e);
                        return;
                    }

                    ContainerVolumeDescription desc = o.getBody(ContainerVolumeDescription.class);
                    callbackFunction.accept(desc);
                }));
    }

    private DeferredResult<Void> validateUserAuthorization(RequestBrokerState state, Operation
            startOp) {

        if (!this.getHost().isAuthorizationEnabled()) {
            return DeferredResult.completed(null);
        }

        if (startOp.getAuthorizationContext() != null
                && startOp.getAuthorizationContext().isSystemUser()) {
            return DeferredResult.completed(null);
        }

        Set<String> projectLinks = TenantLinksUtil.getProjectAndGroupLinks(state.tenantLinks);
        if (projectLinks.isEmpty()) {
            String projectHeader = OperationUtil.extractProjectFromHeader(startOp);
            if (projectHeader != null) {
                projectLinks.add(projectHeader);
            }
        }

        Operation getSecurityContext = Operation.createGet(this, ManagementUriParts.AUTH_SESSION)
                .setReferer(startOp.getUri());

        return sendWithDeferredResult(getSecurityContext, SecurityContext.class)
                .thenCompose(securityContext -> {
                    if (securityContext.roles.contains(AuthRole.CLOUD_ADMIN)) {
                        return DeferredResult.completed(null);
                    }
                    ProjectEntry foundEntry = securityContext.projects.stream()
                            .filter(entry -> projectLinks.contains(entry.documentSelfLink))
                            .findFirst()
                            .orElse(null);
                    if (foundEntry == null) {
                        return DeferredResult.failed(new IllegalAccessError("Principal does "
                                + "not belong to selected project."));
                    }
                    return isUserAuthorized(foundEntry, securityContext);
                });
    }

    private DeferredResult<Void> isUserAuthorized(ProjectEntry entry, SecurityContext context) {
        if (context.isProjectAdmin(entry.documentSelfLink)
                || context.isProjectMember(entry.documentSelfLink)) {
            return DeferredResult.completed(null);
        }

        return DeferredResult.failed(new IllegalAccessError("Project Viewer cannot request "
                + "operations over resources."));
    }

}
