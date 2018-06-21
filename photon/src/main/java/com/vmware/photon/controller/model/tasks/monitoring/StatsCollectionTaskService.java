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

package com.vmware.photon.controller.model.tasks.monitoring;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.tasks.SubTaskService;
import com.vmware.photon.controller.model.tasks.SubTaskService.SubTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TaskUtils;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService.SingleResourceStatsCollectionTaskState;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Service to collect stats from compute instances under the resource pool. This task is a one shot
 * task that is not replicated or persisted. The caller takes care of invoking these tasks
 * periodically
 *
 */
public class StatsCollectionTaskService extends TaskService<StatsCollectionTaskService.StatsCollectionTaskState> {

    public static final String FACTORY_LINK = UriPaths.MONITORING + "/stats-collection-tasks";

    public static FactoryService createFactory() {
        TaskFactoryService fs =  new TaskFactoryService(StatsCollectionTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new StatsCollectionTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public static final String STATS_QUERY_RESULT_LIMIT = UriPaths.PROPERTY_PREFIX + "StatsCollectionTaskService.query.resultLimit";
    private static final String QUERY_RESULT_LIMIT = System.getProperty(STATS_QUERY_RESULT_LIMIT);
    private static final int DEFAULT_QUERY_RESULT_LIMIT = 50;
    private static final String PROP_NEXT_PAGE_LINK = "__nextPageLink";
    public static final String STATS_COLLECTION_EXPIRATION_HOURS = UriPaths.PROPERTY_PREFIX +
            "StatsCollectionTaskService.expiration.hours";
    private static final int DEFAULT_COLLECTION_EXPIRATION_HOURS = 12;

    public enum StatsCollectionStage {
        INIT, GET_RESOURCES
    }

    /**
     * This class defines the document state associated with a single StatsCollectionTaskService
     * instance.
     */
    public static class StatsCollectionTaskState extends TaskService.TaskServiceState {

        /**
         * Reference URI to the resource pool.
         */
        public String resourcePoolLink;

        public StatsCollectionStage taskSubStage;

        public URI statsAdapterReference;

        /**
         * cursor for obtaining compute services
         */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public String nextPageLink;

        /**
         * Queries which can customize default resource pool Query.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public List<Query> customizationClauses;

        /**
         * Task options.
         */
        public EnumSet<TaskOption> options;
    }

    public StatsCollectionTaskService() {
        super(StatsCollectionTaskState.class);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }
            StatsCollectionTaskState state = start.getBody(StatsCollectionTaskState.class);
            int expirationHours = Integer
                    .getInteger(STATS_COLLECTION_EXPIRATION_HOURS, DEFAULT_COLLECTION_EXPIRATION_HOURS);
            setExpiration(state, expirationHours, TimeUnit.HOURS);
            validateState(state);
            logInfo(() -> String.format("Starting stats collection task for: %s",
                    state.resourcePoolLink));
            start.complete();
            state.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
            state.taskSubStage = StatsCollectionStage.INIT;
            handleStagePatch(start, state);
        } catch (Throwable e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        StatsCollectionTaskState currentState = getState(patch);
        StatsCollectionTaskState patchState = getTaskBody(patch);
        updateState(currentState, patchState);
        patch.setBody(currentState);
        patch.complete();

        switch (currentState.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleStagePatch(patch, currentState);
            break;
        case FINISHED:
        case FAILED:
        case CANCELLED:
            if (TaskState.isFailed(currentState.taskInfo) ||
                    TaskState.isCancelled(currentState.taskInfo)) {
                if (currentState.failureMessage != null) {
                    logWarning(currentState.failureMessage);
                }
            }
            logInfo(() -> String.format("Finished stats collection task for: %s",
                    currentState.resourcePoolLink));

            if (currentState.options != null
                    && currentState.options.contains(TaskOption.SELF_DELETE_ON_COMPLETION)) {
                sendRequest(Operation
                        .createDelete(getUri()));
            }

            break;
        default:
            break;
        }
    }

    @Override
    public void handlePut(Operation put) {
        PhotonModelUtils.handleIdempotentPut(this, put);
    }

    private void validateState(StatsCollectionTaskState state) {
        if (state.resourcePoolLink == null) {
            throw new IllegalStateException("resourcePoolLink should not be null");
        }
    }

    @Override
    public void updateState(StatsCollectionTaskState currentState,
            StatsCollectionTaskState patchState) {
        if (patchState.taskInfo != null) {
            currentState.taskInfo = patchState.taskInfo;
        }
        if (patchState.taskSubStage != null) {
            currentState.taskSubStage = patchState.taskSubStage;
        }
        if (patchState.nextPageLink != null) {
            currentState.nextPageLink = patchState.nextPageLink;
        }
    }

    private void handleStagePatch(Operation op, StatsCollectionTaskState currentState) {
        switch (currentState.taskSubStage) {
        case INIT:
            initializeQuery(op, currentState, null);
            break;
        case GET_RESOURCES:
            getResources(op, currentState);
            break;
        default:
            break;
        }
    }

    private void initializeQuery(Operation op, StatsCollectionTaskState currentState,
            ResourcePoolState resourcePoolState) {

        // load the RP state, if not already
        if (resourcePoolState == null) {
            sendRequest(Operation.createGet(UriUtils.extendUri(ClusterUtil.getClusterUri(getHost(),
                    ServiceTypeCluster.DISCOVERY_SERVICE), currentState.resourcePoolLink))
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            if (e instanceof ServiceNotFoundException) {
                                logInfo(() -> String.format(
                                        "Resource pool %d seems to have been deleted",
                                        currentState.resourcePoolLink));
                            } else {
                                logWarning(
                                        () -> String.format("Error retrieving resource pool %d: %s",
                                                currentState.resourcePoolLink, Utils.toString(e)));
                            }
                            sendSelfPatch(new StatsCollectionTaskState(), TaskStage.FAILED,
                                    patchBody -> {
                                        patchBody.taskInfo.failure = Utils
                                                .toServiceErrorResponse(e);
                                    });
                            return;
                        }

                        ResourcePoolState loadedRpState = o.getBody(ResourcePoolState.class);
                        initializeQuery(op, currentState, loadedRpState);
                    }));
            return;
        }

        int resultLimit = DEFAULT_QUERY_RESULT_LIMIT;
        try {
            resultLimit = (QUERY_RESULT_LIMIT != null) ? Integer.parseInt(QUERY_RESULT_LIMIT)
                    : DEFAULT_QUERY_RESULT_LIMIT;
        } catch (NumberFormatException e) {
            // use the default;
            logWarning(STATS_QUERY_RESULT_LIMIT +
                    " is not a number; Using a default value of " + DEFAULT_QUERY_RESULT_LIMIT);
        }

        Query resourcePoolStateQuery = resourcePoolState.query;

        // Customize default resource pool Query.
        if (currentState.customizationClauses != null
                && !currentState.customizationClauses.isEmpty()) {
            currentState.customizationClauses.stream().forEach(q -> {
                resourcePoolStateQuery.addBooleanClause(q);
            });
        }

        QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
                .setQuery(resourcePoolStateQuery)
                .setResultLimit(resultLimit);
        QueryTask qTask = queryTaskBuilder.build();
        QueryUtils.startQueryTask(this, qTask, ServiceTypeCluster.DISCOVERY_SERVICE)
                .whenComplete((queryRsp, queryEx) -> {
                    if (queryEx != null) {
                        TaskUtils.sendFailurePatch(this, new StatsCollectionTaskState(), queryEx);
                        return;
                    }
                    StatsCollectionTaskState patchBody = new StatsCollectionTaskState();
                    if (queryRsp.results.nextPageLink == null) {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
                    } else {
                        patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.STARTED);
                        patchBody.taskSubStage = StatsCollectionStage.GET_RESOURCES;
                        patchBody.nextPageLink = queryRsp.results.nextPageLink;
                    }
                    TaskUtils.sendPatch(this, patchBody);
                });
    }

    private void getResources(Operation op, StatsCollectionTaskState currentState) {
        sendRequest(Operation
                .createGet(UriUtils.extendUri(ClusterUtil.getClusterUri(getHost(),
                        ServiceTypeCluster.DISCOVERY_SERVICE), currentState.nextPageLink))
                .setCompletion(
                        (getOp, getEx) -> {
                            if (getEx != null) {
                                TaskUtils.sendFailurePatch(this, new StatsCollectionTaskState(), getEx);
                                return;
                            }
                            QueryTask page = getOp.getBody(QueryTask.class);
                            if (page.results.documentLinks.size() == 0) {
                                StatsCollectionTaskState patchBody = new StatsCollectionTaskState();
                                patchBody.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
                                TaskUtils.sendPatch(this, patchBody);
                                return;
                            }
                            createSubTask(page.results.documentLinks, page.results.nextPageLink,
                                    currentState);
                        }));
    }

    private void createSubTask(List<String> computeResources, String nextPageLink,
            StatsCollectionTaskState currentState) {
        ServiceTaskCallback<StatsCollectionStage> callback = ServiceTaskCallback
                .create(UriUtils.buildPublicUri(getHost(), getSelfLink()));
        if (nextPageLink != null) {
            callback.onSuccessTo(StatsCollectionStage.GET_RESOURCES)
                    .addProperty(PROP_NEXT_PAGE_LINK, nextPageLink);
        } else {
            callback.onSuccessFinishTask();
        }

        SubTaskState<StatsCollectionStage> subTaskInitState = new SubTaskState<>();
        subTaskInitState.errorThreshold = 0;
        subTaskInitState.completionsRemaining = computeResources.size();
        subTaskInitState.serviceTaskCallback = callback;
        Operation startPost = Operation
                .createPost(this, SubTaskService.FACTORY_LINK)
                .setBody(subTaskInitState)
                .setCompletion((postOp, postEx) -> {
                    if (postEx != null) {
                        TaskUtils.sendFailurePatch(this, new StatsCollectionTaskState(), postEx);
                        return;
                    }
                    SubTaskState<?> body = postOp
                            .getBody(SubTaskState.class);
                    // kick off a collection task for each resource and track completion
                    // via the compute subtask
                    for (String computeLink : computeResources) {
                        createSingleResourceComputeTask(computeLink, body.documentSelfLink, currentState.statsAdapterReference);
                    }
                });
        sendRequest(startPost);
    }

    private void createSingleResourceComputeTask(String computeLink, String subtaskLink,
            URI statsAdapterReference) {
        SingleResourceStatsCollectionTaskState initState = new SingleResourceStatsCollectionTaskState();
        initState.parentTaskReference = UriUtils.buildPublicUri(getHost(), subtaskLink);
        initState.computeLink = computeLink;
        initState.statsAdapterReference = statsAdapterReference;
        SubTaskState<StatsCollectionStage> patchState = new SubTaskState<>();
        patchState.taskInfo = TaskUtils.createTaskState(TaskStage.FINISHED);
        initState.parentPatchBody = patchState;
        sendRequest(Operation
                    .createPost(this,
                            SingleResourceStatsCollectionTaskService.FACTORY_LINK)
                    .setBody(initState)
                    .setCompletion((factoryPostOp, factoryPostEx) -> {
                        if (factoryPostEx != null) {
                            TaskUtils.sendFailurePatch(this, new StatsCollectionTaskState(), factoryPostEx);
                        }
                    }));
    }

    private StatsCollectionTaskState getTaskBody(Operation op) {
        StatsCollectionTaskState body = op.getBody(StatsCollectionTaskState.class);
        if (ServiceTaskCallbackResponse.KIND.equals(body.documentKind)) {
            ServiceTaskCallbackResponse<?> cr = op.getBody(ServiceTaskCallbackResponse.class);
            body.nextPageLink = cr.getProperty(PROP_NEXT_PAGE_LINK);
        }
        return body;
    }
}
