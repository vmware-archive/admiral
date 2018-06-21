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

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task removes the compute service instances.
 */
public class ResourceRemovalTaskService
        extends TaskService<ResourceRemovalTaskService.ResourceRemovalTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/resource-removal-tasks";

    public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES
            .toMicros(10);

    /**
     * SubStage.
     */
    public static enum SubStage {
        WAITING_FOR_QUERY_COMPLETION, ISSUE_ADAPTER_DELETES, DELETE_DOCUMENTS, FINISHED, FAILED
    }

    /**
     * Represents the state of the removal task.
     */
    public static class ResourceRemovalTaskState extends TaskService.TaskServiceState {

        public static final String FIELD_NAME_NEXT_PAGE_LINK = "nextPageLink";

        /**
         * Task sub stage.
         */
        public SubStage taskSubStage;

        /**
         * Query specification used to find the compute resources for removal.
         */
        public QueryTask.QuerySpecification resourceQuerySpec;

        /**
         * Set by service. Link to resource query task.
         */
        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourceQueryLink;

        /**
         * Link to the next page of results.
         */
        public String nextPageLink;

        /**
         * For testing instance service deletion.
         */
        public boolean isMockRequest;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;

        /**
         * The error threshold.
         */
        public double errorThreshold;

        /**
         * Task options
         */
        public EnumSet<TaskOption> options;
    }

    public ResourceRemovalTaskService() {
        super(ResourceRemovalTaskState.class);
        super.toggleOption(Service.ServiceOption.REPLICATION, true);
        super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }

            ResourceRemovalTaskState state = start
                    .getBody(ResourceRemovalTaskState.class);
            validateState(state);

            if (TaskState.isCancelled(state.taskInfo)
                    || TaskState.isFailed(state.taskInfo)
                    || TaskState.isFinished(state.taskInfo)) {
                start.complete();
                return;
            }

            QueryTask q = new QueryTask();
            q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;
            q.querySpec = state.resourceQuerySpec;
            // make sure we expand the content
            if (!q.querySpec.options.contains(QueryOption.EXPAND_CONTENT)) {
                q.querySpec.options.add(QueryOption.EXPAND_CONTENT);
            }
            if (q.querySpec.resultLimit == null) {
                q.querySpec.resultLimit = QueryUtils.DEFAULT_RESULT_LIMIT;
            }
            q.documentSelfLink = UUID.randomUUID().toString();
            q.tenantLinks = state.tenantLinks;
            // create the query to find resources
            sendRequest(Operation
                    .createPost(this, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                    .setBody(q)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            // the task might have expired, with no results
                            // every becoming available
                            logWarning(() -> String.format("Failure retrieving query results: %s",
                                    e.toString()));
                            sendFailureSelfPatch(e);
                            return;
                        }
                    }));

            start.complete();

            sendSelfPatch(TaskState.TaskStage.STARTED, state.taskSubStage, s -> {
                // we do not wait for the query task creation to know its URI, the
                // URI is created
                // deterministically. The task itself is not complete but we check
                // for that in our state
                // machine
                s.resourceQueryLink = UriUtils.buildUriPath(
                        ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, q.documentSelfLink);
            });
        } catch (Throwable e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceRemovalTaskState body = getPatchBody(patch);
        ResourceRemovalTaskState currentState = getState(patch);

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
            logInfo("Task is complete");
            break;
        case FAILED:
            logSevere("Task failed: %s", Utils.toJsonHtml(currentState.taskInfo.failure));
            break;
        case CANCELLED:
            logInfo("Task is cancelled");
            break;
        default:
            break;
        }
    }

    private void handleStagePatch(ResourceRemovalTaskState currentState) {

        adjustStat(currentState.taskSubStage.toString(), 1);

        switch (currentState.taskSubStage) {
        case WAITING_FOR_QUERY_COMPLETION:
            getQueryResults(currentState.resourceQueryLink, queryTask -> {
                if (TaskState.isFailed(queryTask.taskInfo)) {
                    logWarning(() -> String.format("query task failed: %s",
                            Utils.toJsonHtml(queryTask.taskInfo.failure)));
                    sendFailureSelfPatch(queryTask.taskInfo.failure);
                    return;
                }

                if (TaskState.isFinished(queryTask.taskInfo)) {
                    if (queryTask.results.nextPageLink == null) {
                        logFine("Query returned no computes to delete");
                        sendSelfPatch(TaskState.TaskStage.FINISHED, SubStage.FINISHED, null);
                    } else {
                        sendSelfPatch(TaskState.TaskStage.STARTED,
                                currentState.options != null && currentState.options
                                        .contains(TaskOption.DOCUMENT_CHANGES_ONLY)
                                                ? SubStage.DELETE_DOCUMENTS
                                                : SubStage.ISSUE_ADAPTER_DELETES,
                                s -> s.nextPageLink = queryTask.results.nextPageLink);
                    }
                    return;
                }

                logFine(() -> "Resource query not complete yet, retrying");
                getHost().schedule(() -> handleStagePatch(currentState), 1, TimeUnit.SECONDS);
            });
            break;
        case ISSUE_ADAPTER_DELETES:
            getQueryResults(currentState.nextPageLink, queryTask -> {
                doInstanceDeletes(currentState, queryTask, null);
            });
            break;
        case DELETE_DOCUMENTS:
            // if next page is not set, execute the original paged query again
            if (currentState.nextPageLink == null) {
                getQueryResults(currentState.resourceQueryLink, queryTask -> {
                    sendSelfPatch(currentState.taskInfo.stage, currentState.taskSubStage, s -> {
                        s.nextPageLink = queryTask.results.nextPageLink;
                    });
                });
            } else {
                // handle current page
                getQueryResults(currentState.nextPageLink, queryTask -> {
                    deleteDocuments(currentState, queryTask);
                });
            }
            break;
        case FAILED:
            break;
        case FINISHED:
            break;
        default:
            break;
        }
    }

    private void deleteDocuments(ResourceRemovalTaskState currentState, QueryTask queryTask) {
        // handle empty pages
        if (queryTask.results.documentCount == 0) {
            sendSelfPatch(TaskState.TaskStage.FINISHED, SubStage.FINISHED, null);
            return;
        }

        Stream<Operation> deletes = queryTask.results.documents.values().stream()
                .map(d -> Utils.fromJson(d, ComputeState.class))
                .flatMap(c -> {
                    Stream<Operation> ops = Stream
                            .of(Operation.createDelete(this, c.documentSelfLink));
                    if (c.diskLinks != null && !c.diskLinks.isEmpty()) {
                        ops = Stream.concat(ops,
                                c.diskLinks.stream().map(l -> Operation.createDelete(this, l)));
                    }
                    if (c.networkInterfaceLinks != null && !c.networkInterfaceLinks.isEmpty()) {
                        ops = Stream.concat(ops, c.networkInterfaceLinks.stream()
                                .map(l -> Operation.createDelete(this, l)));
                    }
                    return ops;
                });
        OperationJoin.create(deletes)
                .setCompletion((ox, exc) -> {
                    // delete query
                    sendRequest(Operation.createDelete(this, currentState.resourceQueryLink));
                    if (exc != null) {
                        logSevere(() -> String.format("Failure deleting compute states from the"
                                + " local system", Utils.toString(exc)));
                        sendFailureSelfPatch(exc.values().iterator().next());
                        return;
                    }

                    if (queryTask.results.nextPageLink != null) {
                        sendSelfPatch(currentState.taskInfo.stage, currentState.taskSubStage, s -> {
                            s.nextPageLink = queryTask.results.nextPageLink;
                        });
                    } else {
                        sendSelfPatch(TaskState.TaskStage.FINISHED, SubStage.FINISHED, null);
                    }
                })
                .sendWith(this);
    }

    private void doInstanceDeletes(ResourceRemovalTaskState currentState,
            QueryTask queryTask, String subTaskLink) {

        // handle empty pages
        if (queryTask.results.documentCount == 0) {
            sendSelfPatch(currentState.taskInfo.stage, SubStage.DELETE_DOCUMENTS, s -> {
                s.nextPageLink = null;
            });
            return;
        }

        if (subTaskLink == null) {
            createSubTaskForDeleteCallbacks(currentState, queryTask,
                    link -> doInstanceDeletes(currentState, queryTask, link));
            return;
        }

        logFine(() -> String.format("Starting delete of %d compute resources using sub task %s",
                queryTask.results.documentLinks.size(), subTaskLink));
        // for each compute resource link in the results, expand it with the
        // description, and issue
        // a DELETE request to its associated instance service.

        for (String resourceLink : queryTask.results.documentLinks) {
            URI u = ComputeStateWithDescription
                    .buildUri(UriUtils.buildUri(getHost(), resourceLink));
            sendRequest(Operation
                    .createGet(u)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            // we do not fail task if one delete failed ...
                            // send a FINISHED patch which is what a sub task
                            // would do. Since the
                            // current state
                            // is still at REMOVING_RESOURCES, we will just
                            // increment a counter
                            ResourceOperationResponse subTaskPatchBody = ResourceOperationResponse
                                    .fail(resourceLink, e);
                            sendPatch(subTaskLink, subTaskPatchBody);
                            return;
                        }
                        sendInstanceDelete(resourceLink, subTaskLink, o, currentState);
                    }));
        }
    }

    /**
     * Before we proceed with issuing DELETE requests to the instance services we must create a sub
     * task that will track the DELETE completions. The instance service will issue a PATCH with
     * TaskStage.FINISHED, for every PATCH we send it, to delete the compute resource
     */
    private void createSubTaskForDeleteCallbacks(ResourceRemovalTaskState currentState,
            QueryTask queryTask, Consumer<String> subTaskLinkConsumer) {

        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback
                .create(UriUtils.buildPublicUri(getHost(), getSelfLink()));
        if (queryTask.results.nextPageLink != null) {
            callback.onSuccessTo(SubStage.ISSUE_ADAPTER_DELETES)
                    .addProperty(ResourceRemovalTaskState.FIELD_NAME_NEXT_PAGE_LINK,
                            queryTask.results.nextPageLink);
        } else {
            callback.onSuccessTo(SubStage.DELETE_DOCUMENTS);
        }

        SubTaskService.SubTaskState<SubStage> subTaskInitState = new SubTaskService.SubTaskState<SubStage>();

        // tell the sub task with what to patch us, on completion
        subTaskInitState.serviceTaskCallback = callback;
        subTaskInitState.completionsRemaining = queryTask.results.documentLinks.size();
        subTaskInitState.errorThreshold = currentState.errorThreshold;
        subTaskInitState.tenantLinks = currentState.tenantLinks;
        subTaskInitState.documentExpirationTimeMicros = currentState.documentExpirationTimeMicros;
        Operation startPost = Operation
                .createPost(this, SubTaskService.FACTORY_LINK)
                .setBody(subTaskInitState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(() -> String.format("Failure creating sub task: %s",
                                        Utils.toString(e)));
                                sendFailureSelfPatch(e);
                                return;
                            }
                            SubTaskService.SubTaskState<?> body = o
                                    .getBody(SubTaskService.SubTaskState.class);

                            subTaskLinkConsumer.accept(body.documentSelfLink);
                        });
        sendRequest(startPost);
    }

    private void sendInstanceDelete(String resourceLink, String subTaskLink,
            Operation o, ResourceRemovalTaskState currentState) {
        ComputeStateWithDescription chd = o.getBody(ComputeStateWithDescription.class);
        if (chd.description.instanceAdapterReference != null) {
            ComputeInstanceRequest deleteReq = new ComputeInstanceRequest();
            deleteReq.resourceReference = UriUtils.buildUri(getHost(), resourceLink);
            deleteReq.taskReference = UriUtils.buildUri(getHost(),
                    subTaskLink);
            deleteReq.requestType = ComputeInstanceRequest.InstanceRequestType.DELETE;
            deleteReq.isMockRequest = currentState.isMockRequest;
            sendRequest(Operation
                    .createPatch(chd.description.instanceAdapterReference)
                    .setBody(deleteReq)
                    .setCompletion(
                            (deleteOp, e) -> {
                                if (e != null) {
                                    logWarning(() -> String.format(
                                            "PATCH to instance service %s, failed: %s",
                                            deleteOp.getUri(), e.toString()));
                                    ResourceOperationResponse fail = ResourceOperationResponse
                                            .fail(resourceLink, e);
                                    sendPatch(subTaskLink, fail);
                                    return;
                                }
                            }));
        } else {
            logWarning(() -> String.format("Compute instance %s doesn't not have configured"
                    + " instanceAdapter. Only local resource will be deleted.",
                    resourceLink));
            ResourceOperationResponse subTaskPatchBody = ResourceOperationResponse
                    .finish(resourceLink);
            sendPatch(subTaskLink, subTaskPatchBody);
        }
    }

    public void getQueryResults(String resultsLink, Consumer<QueryTask> consumer) {
        sendRequest(Operation.createGet(this, resultsLink)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        // the task might have expired, with no results every
                        // becoming available
                        logWarning(() -> String.format("Failure retrieving query results: %s",
                                e.toString()));
                        sendFailureSelfPatch(e);
                        return;
                    }

                    consumer.accept(o.getBody(QueryTask.class));
                }));
    }

    private boolean validateTransitionAndUpdateState(Operation patch,
            ResourceRemovalTaskState body, ResourceRemovalTaskState currentState) {

        TaskState.TaskStage currentStage = currentState.taskInfo.stage;
        SubStage currentSubStage = currentState.taskSubStage;

        if (body.taskInfo == null || body.taskInfo.stage == null) {
            patch.fail(new IllegalArgumentException(
                    "taskInfo and stage are required"));
            return true;
        }

        if (currentStage.ordinal() > body.taskInfo.stage.ordinal()) {
            patch.fail(new IllegalArgumentException(
                    "stage can not move backwards:" + body.taskInfo.stage));
            return true;
        }

        if (currentStage.ordinal() == body.taskInfo.stage.ordinal()
                && (body.taskSubStage == null || currentSubStage.ordinal() > body.taskSubStage
                        .ordinal())) {
            patch.fail(new IllegalArgumentException(
                    "subStage can not move backwards:" + body.taskSubStage));
            return true;
        }

        currentState.taskInfo.stage = body.taskInfo.stage;
        currentState.taskSubStage = body.taskSubStage;

        // auto-merge fields based on annotations
        Utils.mergeWithState(this.getStateDescription(), currentState, body);

        // next page is always overridden (even with null)
        currentState.nextPageLink = body.nextPageLink;

        logFine(() -> String.format("Moving from %s(%s) to %s(%s)", currentSubStage, currentStage,
                body.taskSubStage, currentState.taskInfo.stage));

        return false;
    }

    private void sendFailureSelfPatch(Throwable e) {
        sendFailureSelfPatch(Utils.toServiceErrorResponse(e));
    }

    private void sendFailureSelfPatch(ServiceErrorResponse errorResponse) {
        sendSelfPatch(TaskState.TaskStage.FAILED, SubStage.FAILED, s -> {
            s.taskInfo.failure = errorResponse;
        });
    }

    private void sendSelfPatch(TaskState.TaskStage stage, SubStage subStage,
            Consumer<ResourceRemovalTaskState> patchBodyConfigurator) {
        ResourceRemovalTaskState body = new ResourceRemovalTaskState();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = stage;
        body.taskSubStage = subStage;
        if (patchBodyConfigurator != null) {
            patchBodyConfigurator.accept(body);
        }
        sendSelfPatch(body);
    }

    private void sendPatch(String link, Object body) {
        Operation patch = Operation
                .createPatch(this, link)
                .setBody(body)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logWarning(() -> String.format("Self patch failed: %s",
                                        Utils.toString(ex)));
                            }
                        });
        sendRequest(patch);
    }

    public static void validateState(ResourceRemovalTaskState state) {
        if (state.resourceQuerySpec == null) {
            throw new IllegalArgumentException("resourceQuerySpec is required");
        }

        if (state.taskInfo == null || state.taskInfo.stage == null) {
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskState.TaskStage.CREATED;
        }

        if (state.taskSubStage == null) {
            state.taskSubStage = SubStage.WAITING_FOR_QUERY_COMPLETION;
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + DEFAULT_TIMEOUT_MICROS;
        }
    }

    private static ResourceRemovalTaskState getPatchBody(Operation op) {
        ResourceRemovalTaskState body = op.getBody(ResourceRemovalTaskState.class);
        if (ServiceTaskCallbackResponse.KIND.equals(body.documentKind)) {
            ServiceTaskCallbackResponse<?> cr = op.getBody(ServiceTaskCallbackResponse.class);
            body.nextPageLink = cr.getProperty(ResourceRemovalTaskState.FIELD_NAME_NEXT_PAGE_LINK);
        }
        return body;
    }
}
