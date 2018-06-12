/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.tasks;

import static com.vmware.photon.controller.model.resources.EndpointService.ENDPOINT_REMOVAL_REQUEST_REFERRER_NAME;
import static com.vmware.photon.controller.model.resources.EndpointService.ENDPOINT_REMOVAL_REQUEST_REFERRER_VALUE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.RouterService.RouterState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task removes the endpoint service instances.
 */
public class EndpointRemovalTaskService
        extends TaskService<EndpointRemovalTaskService.EndpointRemovalTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/endpoint-removal-tasks";

    public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES
            .toMicros(10);

    public static final String FIELD_NAME_ENDPOINT_LINK = "endpointLink";
    public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";

    static final Collection<String> RESOURCE_TYPES_TO_DELETE = Arrays.asList(
            Utils.buildKind(AuthCredentialsServiceState.class),
            Utils.buildKind(DiskState.class),
            Utils.buildKind(ComputeState.class),
            Utils.buildKind(ComputeDescription.class),
            Utils.buildKind(ComputeStateWithDescription.class),
            Utils.buildKind(NetworkState.class),
            Utils.buildKind(NetworkInterfaceState.class),
            Utils.buildKind(NetworkInterfaceDescription.class),
            Utils.buildKind(SecurityGroupState.class),
            Utils.buildKind(SubnetState.class),
            Utils.buildKind(StorageDescription.class),
            Utils.buildKind(ImageState.class),
            Utils.buildKind(RouterState.class));

    /**
     * SubStage.
     */
    public static enum SubStage {

        /**
         * Load endpoint data.
         */
        LOAD_ENDPOINT,

        /**
         * Stop scheduled enumeration task
         */
        STOP_ENUMERATION,

        /**
         * Delete resources from this endpoint. Only local resources are deleted.
         */
        DELETE_RESOURCES,

        /**
         * Delete the endpoint documents
         */
        ISSUE_ENDPOINT_DELETE,

        /**
         * Delete any additional resources which refer this endpoint through custom property
         * {@link ComputeProperties#ENDPOINT_LINK_PROP_NAME}, except for resource pools (they cannot
         * be deleted before resources which use them are deleted first)
         */
        DELETE_RELATED_RESOURCES,

        /**
         * Delete associated resource pools
         */
        DELETE_RELATED_RESOURCE_POOLS,

        /**
         * Delete associated resource groups
         */
        DELETE_RELATED_RESOURCE_GROUPS,

        FINISHED, FAILED
    }

    /**
     * Represents the state of the removal task.
     */
    public static class EndpointRemovalTaskState extends TaskService.TaskServiceState {

        @Documentation(description = "A link to endpoint to be deleted.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK }, indexing = STORE_ONLY)
        public String endpointLink;

        @Documentation(description = "Describes a service task sub stage.")
        @PropertyOptions(usage = { SERVICE_USE }, indexing = STORE_ONLY)
        public SubStage taskSubStage;

        @Documentation(description = "A list of tenant links which can access this task.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, OPTIONAL }, indexing = {
                PropertyIndexingOption.EXPAND })
        public List<String> tenantLinks;

        /**
         * Task options
         */
        public EnumSet<TaskOption> options = EnumSet.noneOf(TaskOption.class);

        /**
         * The error threshold.
         */
        public double errorThreshold;

        @Documentation(description = "EndpointState to delete. Set by the run-time.")
        @PropertyOptions(usage = { SERVICE_USE }, indexing = STORE_ONLY)
        public EndpointState endpoint;
    }

    public EndpointRemovalTaskService() {
        super(EndpointRemovalTaskState.class);
        super.toggleOption(Service.ServiceOption.REPLICATION, true);
        super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
        EndpointRemovalTaskState initialState = validateStartPost(post);
        if (initialState == null) {
            return;
        }

        if (!ServiceHost.isServiceCreate(post)) {
            return;
        }

        initializeState(initialState, post);
        initialState.taskInfo.stage = TaskStage.CREATED;
        post.setBody(initialState)
                .setStatusCode(Operation.STATUS_CODE_ACCEPTED)
                .complete();

        // self patch to start state machine
        sendSelfPatch(initialState, TaskStage.STARTED, null);
    }

    @Override
    public void handlePatch(Operation patch) {
        EndpointRemovalTaskState body = patch
                .getBody(EndpointRemovalTaskState.class);
        EndpointRemovalTaskState currentState = getState(patch);

        if (validateTransitionAndUpdateState(patch, body, currentState)) {
            return;
        }

        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleStagePatch(currentState);
            break;
        case FINISHED:
            logInfo(() -> "Task was completed");
            break;
        case FAILED:
        case CANCELLED:
            break;
        default:
            break;
        }
    }

    private void handleStagePatch(EndpointRemovalTaskState currentState) {

        adjustStat(currentState.taskSubStage.toString(), 1);

        switch (currentState.taskSubStage) {
        case LOAD_ENDPOINT:
            getEndpoint(currentState, SubStage.STOP_ENUMERATION);
            break;
        case STOP_ENUMERATION:
            stopEnumeration(currentState, SubStage.DELETE_RESOURCES);
            break;
        case DELETE_RESOURCES:
            deleteResources(currentState, SubStage.ISSUE_ENDPOINT_DELETE);
            break;
        case ISSUE_ENDPOINT_DELETE:
            doInstanceDeletes(currentState, SubStage.DELETE_RELATED_RESOURCES);
            break;
        case DELETE_RELATED_RESOURCES:
            deleteAssociatedDocuments(currentState, RESOURCE_TYPES_TO_DELETE,
                    SubStage.DELETE_RELATED_RESOURCE_POOLS);
            break;
        case DELETE_RELATED_RESOURCE_POOLS:
            deleteAssociatedDocuments(currentState,
                    Arrays.asList(Utils.buildKind(ResourcePoolState.class)),
                    SubStage.DELETE_RELATED_RESOURCE_GROUPS);
            break;
        case DELETE_RELATED_RESOURCE_GROUPS:
            /*
             * this needs to happen last, as there may be dependencies between resource groups and
             * other document types (e.g., security groups, resource pools)
             */
            deleteAssociatedDocuments(currentState,
                    Arrays.asList(Utils.buildKind(ResourceGroupState.class)), SubStage.FINISHED);
            break;
        case FAILED:
            break;
        case FINISHED:
            complete(currentState, SubStage.FINISHED);
            break;
        default:
            break;
        }
    }

    private void stopEnumeration(EndpointRemovalTaskState currentState, SubStage next) {
        String id = UriUtils.getLastPathSegment(currentState.endpointLink);
        logFine(() -> String.format("Stopping any scheduled task for endpoint %s",
                currentState.endpointLink));
        Operation.createDelete(this, UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK, id))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Unable to delete ScheduleTaskState for"
                                + " endpoint %s : %s", currentState.endpointLink,
                                e.getMessage()));
                    }

                    sendSelfPatch(TaskStage.STARTED, next);
                }).sendWith(this);
    }

    /**
     * Delete all top level objects, which are representing this endpoint, including endpoint
     * itself.
     */
    private void doInstanceDeletes(EndpointRemovalTaskState currentState, SubStage next) {
        EndpointState endpoint = currentState.endpoint;

        Operation crdOp = Operation.createDelete(this, endpoint.authCredentialsLink);
        Operation cdsOp = Operation.createDelete(this, endpoint.computeDescriptionLink);
        Operation csOp = Operation.createDelete(this, endpoint.computeLink);
        Operation epOp = Operation.createDelete(this, endpoint.documentSelfLink);
        // custom header identifier for endpoint service to validate before deleting endpoint
        epOp.addRequestHeader(ENDPOINT_REMOVAL_REQUEST_REFERRER_NAME,
                ENDPOINT_REMOVAL_REQUEST_REFERRER_VALUE);

        OperationJoin.create(crdOp, cdsOp, csOp, epOp).setCompletion((ops, exc) -> {
            if (exc != null) {
                // failing to delete the endpoint itself is considered a critical error
                Throwable endpointRemovalException = exc.get(epOp.getId());
                if (endpointRemovalException != null) {
                    sendFailureSelfPatch(endpointRemovalException);
                    return;
                }

                // other removal exceptions are just warnings
                logFine(() -> String.format("Failed delete some of the associated resources,"
                        + " reason %s", Utils.toString(exc)));
            }
            // all resources deleted; mark the operation as complete
            sendSelfPatch(TaskStage.STARTED, next);
        }).sendWith(this);
    }

    /**
     * Delete associated resource, e.g. enumeration task if started.
     */
    private void deleteAssociatedDocuments(EndpointRemovalTaskState state,
            Collection<String> documentKinds, SubStage next) {
        Query resourceQuery = getAssociatedDocumentsQuery(state, documentKinds);
        QueryTask resourceQueryTask = QueryTask.Builder.createDirectTask()
                .setQuery(resourceQuery)
                .setResultLimit(QueryUtils.DEFAULT_RESULT_LIMIT)
                .build();
        resourceQueryTask.tenantLinks = state.tenantLinks;

        Operation.createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setBody(resourceQueryTask)
                .setCompletion(
                        (queryOp, throwable) -> {
                            if (throwable != null) {
                                logWarning(throwable.getMessage());
                                sendSelfPatch(TaskStage.STARTED, next);
                                return;
                            }

                            QueryTask rsp = queryOp.getBody(QueryTask.class);
                            if (rsp.results.nextPageLink == null) {
                                sendSelfPatch(TaskStage.STARTED, next);
                                return;
                            }

                            deleteAssociatedDocumentsHelper(rsp.results.nextPageLink, next);
                        })
                .sendWith(this);
    }

    private void deleteAssociatedDocumentsHelper(String nextPageLink, SubStage next) {
        Operation.CompletionHandler completionHandler = (o, e) -> {
            if (e != null) {
                logWarning(e.getMessage());
                sendSelfPatch(TaskStage.STARTED, next);
                return;
            }

            QueryTask queryTask = o.getBody(QueryTask.class);

            List<Operation> deleteOps = new ArrayList<>();
            for (String selfLink : queryTask.results.documentLinks) {
                deleteOps.add(Operation.createDelete(
                        UriUtils.buildUri(getHost(), selfLink))
                        .setReferer(getUri()));
            }

            if (deleteOps.size() == 0) {
                sendSelfPatch(TaskStage.STARTED, next);
                return;
            }

            OperationJoin joinOp = OperationJoin.create(deleteOps);
            JoinedCompletionHandler joinHandler = (ops, exc) -> {
                if (exc != null) {
                    logWarning(() -> String.format("Failed delete some of the associated resources,"
                            + " reason %s", Utils.toString(exc)));
                }

                if (queryTask.results.nextPageLink == null) {
                    // all resources deleted;
                    sendSelfPatch(TaskStage.STARTED, next);
                    return;
                }
                deleteAssociatedDocumentsHelper(queryTask.results.nextPageLink, next);
            };
            joinOp.setCompletion(joinHandler);
            joinOp.sendWith(getHost());
        };
        sendRequest(Operation.createGet(this, nextPageLink)
                .setCompletion(completionHandler));
    }

    private Query getAssociatedDocumentsQuery(EndpointRemovalTaskState state,
            Collection<String> documentKinds) {
        Query resourceQuery = Query.Builder.create()
                .addInClause(ServiceDocument.FIELD_NAME_KIND, documentKinds)
                .addClause(Query.Builder.create()
                        .addFieldClause(FIELD_NAME_ENDPOINT_LINK, state.endpoint.documentSelfLink,
                                QueryTask.Query.Occurance.SHOULD_OCCUR)
                        .addCompositeFieldClause(FIELD_NAME_CUSTOM_PROPERTIES,
                                ComputeProperties.ENDPOINT_LINK_PROP_NAME,
                                state.endpoint.documentSelfLink,
                                QueryTask.Query.Occurance.SHOULD_OCCUR)
                        .build())
                .build();

        return resourceQuery;
    }

    /**
     * Delete computes discovered with this endpoint.
     */
    private void deleteResources(EndpointRemovalTaskState state, SubStage next) {
        QuerySpecification qSpec = new QuerySpecification();
        qSpec.query = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, state.endpoint.computeLink)
                .build();
        ResourceRemovalTaskState removalServiceState = new ResourceRemovalTaskState();
        removalServiceState.documentSelfLink = UUID.randomUUID().toString();
        removalServiceState.resourceQuerySpec = qSpec;
        removalServiceState.options = EnumSet.of(TaskOption.DOCUMENT_CHANGES_ONLY);
        removalServiceState.isMockRequest = state.options.contains(TaskOption.IS_MOCK);
        removalServiceState.tenantLinks = state.tenantLinks;

        StatefulService service = this;

        Operation
                .createPost(UriUtils.buildUri(getHost(),
                        ResourceRemovalTaskService.FACTORY_LINK))
                .setBody(removalServiceState)
                .setCompletion((resourcePostOp, resourcePostEx) -> {
                    Consumer<Operation> onSuccess = new Consumer<Operation>() {
                        Set<String> finishedTaskLinks = new HashSet<>();

                        @Override
                        public void accept(Operation op) {
                            ResourceRemovalTaskState deletionState = op
                                    .getBody(ResourceRemovalTaskState.class);

                            TaskUtils.handleSubscriptionNotifications(service, op,
                                    deletionState.documentSelfLink, deletionState.taskInfo,
                                    1, createPatchSubStageTask(TaskStage.STARTED, next, null),
                                    finishedTaskLinks,
                                    true);
                        }
                    };

                    TaskUtils.subscribeToNotifications(this, onSuccess, resourcePostOp
                            .getBody(ResourceRemovalTaskState.class).documentSelfLink);
                }).sendWith(this);
    }

    public void getEndpoint(EndpointRemovalTaskState currentState, SubStage next) {
        sendRequest(Operation.createGet(this, currentState.endpointLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        // the task might have expired, with no results every
                        // becoming available
                        logWarning(() -> String.format("Failure retrieving endpoint: %s, reason:"
                                + " %s", currentState.endpointLink, e.toString()));
                        sendFailureSelfPatch(e);
                        return;
                    }

                    EndpointState rsp = o.getBody(EndpointState.class);
                    EndpointRemovalTaskState state = createPatchSubStageTask(TaskStage.STARTED,
                            next, null);
                    state.endpoint = rsp;
                    sendSelfPatch(state);
                }));
    }

    private boolean validateTransitionAndUpdateState(Operation patch,
            EndpointRemovalTaskState body, EndpointRemovalTaskState currentState) {

        TaskState.TaskStage currentStage = currentState.taskInfo.stage;
        SubStage currentSubStage = currentState.taskSubStage;

        boolean isUpdate = false;

        if (body.endpoint != null) {
            currentState.endpoint = body.endpoint;
            isUpdate = true;
        }

        if (body.taskInfo == null || body.taskInfo.stage == null) {
            if (isUpdate) {
                patch.complete();
                return true;
            }
            patch.fail(new IllegalArgumentException("taskInfo and stage are required"));
            return true;
        }

        if (currentStage.ordinal() > body.taskInfo.stage.ordinal()) {
            patch.fail(new IllegalArgumentException("stage can not move backwards:"
                    + body.taskInfo.stage));
            return true;
        }

        if (currentStage.ordinal() == body.taskInfo.stage.ordinal()
                && (body.taskSubStage == null || currentSubStage.ordinal() > body.taskSubStage
                        .ordinal())) {
            patch.fail(new IllegalArgumentException("subStage can not move backwards:"
                    + body.taskSubStage));
            return true;
        }

        currentState.taskInfo.stage = body.taskInfo.stage;
        currentState.taskSubStage = body.taskSubStage;

        logFine(() -> String.format("Moving from %s(%s) to %s(%s)", currentSubStage, currentStage,
                body.taskSubStage, currentState.taskInfo.stage));

        return false;
    }

    private void sendFailureSelfPatch(Throwable e) {
        sendSelfPatch(createPatchSubStageTask(TaskState.TaskStage.FAILED, SubStage.FAILED, e));
    }

    private void sendSelfPatch(TaskState.TaskStage stage, SubStage subStage) {
        sendSelfPatch(createPatchSubStageTask(stage, subStage, null));
    }

    private EndpointRemovalTaskState createPatchSubStageTask(TaskState.TaskStage stage,
            SubStage subStage,
            Throwable e) {
        EndpointRemovalTaskState body = new EndpointRemovalTaskState();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = stage;
        body.taskSubStage = subStage;
        if (e != null) {
            body.taskInfo.failure = Utils.toServiceErrorResponse(e);
            logWarning(() -> String.format("Patching to failed: %s", Utils.toString(e)));
        }
        return body;
    }

    @Override
    protected EndpointRemovalTaskState validateStartPost(Operation taskOperation) {
        EndpointRemovalTaskState task = super.validateStartPost(taskOperation);
        if (task == null) {
            return null;
        }

        if (TaskState.isCancelled(task.taskInfo)
                || TaskState.isFailed(task.taskInfo)
                || TaskState.isFinished(task.taskInfo)) {
            return null;
        }

        if (!ServiceHost.isServiceCreate(taskOperation)) {
            return task;
        }

        if (task.endpointLink == null) {
            taskOperation.fail(new IllegalArgumentException("endpointLink is required"));
            return null;
        }

        return task;
    }

    @Override
    protected void initializeState(EndpointRemovalTaskState state, Operation taskOperation) {
        if (state.taskSubStage == null) {
            state.taskSubStage = SubStage.LOAD_ENDPOINT;
        }

        if (state.options == null) {
            state.options = EnumSet.noneOf(TaskOption.class);
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + DEFAULT_TIMEOUT_MICROS;
        }
        super.initializeState(state, taskOperation);
    }

    private void complete(EndpointRemovalTaskState state, SubStage completeSubStage) {
        if (!TaskUtils.isFailedOrCancelledTask(state)) {
            state.taskInfo.stage = TaskStage.FINISHED;
            state.taskSubStage = completeSubStage;
            sendSelfPatch(state);
        }
    }
}
