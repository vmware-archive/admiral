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
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task used to Enumerate resources on a given compute host.
 */
public class ResourceEnumerationTaskService
        extends TaskService<ResourceEnumerationTaskService.ResourceEnumerationTaskState> {
    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/resource-enumeration-tasks";

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(ResourceEnumerationTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new ResourceEnumerationTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES
            .toMicros(10);

    /**
     * This class defines the document state associated with a single ResourceEnumerationTaskService
     * instance.
     */
    public static class ResourceEnumerationTaskState extends TaskService.TaskServiceState {
        public static final String FIELD_NAME_RESOURCE_POOL_LINK = "resourcePoolLink";
        public static final String FIELD_NAME_PARENT_COMPUTE_LINK = "parentComputeLink";

        /**
         * Reference URI to the resource pool.
         */
        public String resourcePoolLink;

        /**
         * Reference URI to the parent Compute instance.
         */
        public String parentComputeLink;

        /**
         * Enumeration Action Start, stop, refresh.
         */
        public EnumerationAction enumerationAction;

        /**
         * URI reference to resource pool management site.
         */
        public URI adapterManagementReference;

        /**
         * A list of tenant links which can access this task.
         */
        public List<String> tenantLinks;

        /**
         * Task options
         */
        public EnumSet<TaskOption> options;

        /**
         * Link to the cloud account endpoint.
         */
        public String endpointLink;
    }

    public ResourceEnumerationTaskService() {
        super(ResourceEnumerationTaskState.class);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation start) {
        try {
            if (!start.hasBody()) {
                start.fail(new IllegalArgumentException("body is required"));
                return;
            }

            ResourceEnumerationTaskState state = getBody(start);
            validateState(state);
            start.setBody(state).complete();

            sendSelfPatch(state, TaskStage.STARTED, null);
        } catch (Throwable e) {
            start.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ResourceEnumerationTaskState body = getBody(patch);
        ResourceEnumerationTaskState currentState = getState(patch);

        if (!validateTransition(patch, currentState, body)) {
            return;
        }

        logFine(() -> String.format("Moving from %s to %s", currentState.taskInfo.stage.toString(),
                body.taskInfo.stage.toString()));

        currentState.taskInfo = body.taskInfo;
        // go-dcp will actuate the state. When the document is created, the
        // enumeration service in
        // go-dcp will be PATCH'ed with the enumeration request, then it will
        // PATCH back CREATED
        // followed by FINISHED or FAILED when complete
        switch (currentState.taskInfo.stage) {
        case CREATED:
            logFine(() -> "Created enum task");
            break;
        case STARTED:
            logFine(() -> "Started enum task");
            currentState.taskInfo.stage = TaskStage.STARTED;
            sendEnumRequest(patch, currentState);
            break;
        case FINISHED:
            logFine(() -> "Task is complete");
            if (currentState.options.contains(TaskOption.SELF_DELETE_ON_COMPLETION)) {
                sendRequest(Operation
                        .createDelete(getUri()));
            }
            break;
        case FAILED:
        case CANCELLED:
            if (currentState.taskInfo.stage == TaskStage.CANCELLED) {
                logWarning(() -> "Task was cancelled");
            } else {
                logWarning(() -> String.format("Task failed: %s",
                        Utils.toJsonHtml(currentState.taskInfo.failure)));
            }
            if (currentState.options.contains(TaskOption.SELF_DELETE_ON_COMPLETION)) {
                sendRequest(Operation
                        .createDelete(getUri()));
            }
            break;
        default:
            logWarning(() -> "Unknown stage");
            break;
        }

        patch.setBody(currentState).complete();
    }

    @Override
    public void handlePut(Operation put) {
        PhotonModelUtils.handleIdempotentPut(this, put);
    }

    private void sendEnumRequest(Operation start, ResourceEnumerationTaskState state) {
        ComputeEnumerateResourceRequest req = new ComputeEnumerateResourceRequest();
        req.resourcePoolLink = state.resourcePoolLink;
        req.adapterManagementReference = state.adapterManagementReference;
        req.resourceReference = UriUtils.buildUri(getHost(), state.parentComputeLink);
        req.enumerationAction = state.enumerationAction;
        req.taskReference = UriUtils.buildUri(getHost(),
                state.documentSelfLink);
        req.isMockRequest = state.options.contains(TaskOption.IS_MOCK);
        req.preserveMissing = state.options.contains(TaskOption.PRESERVE_MISSING_RESOUCES);
        req.endpointLink = state.endpointLink;

        // Patch the enumerate service URI from the CHD
        CompletionHandler descriptionCompletion = (o, ex) -> {
            if (ex != null) {
                TaskUtils.sendFailurePatch(this, state, ex);
                start.fail(ex);
                return;
            }

            ComputeStateWithDescription csd = o
                    .getBody(ComputeStateWithDescription.class);

            if (csd.description.enumerationAdapterReference == null) {
                // no enumeration adapter associated with this resource, just patch completion
                sendSelfFinishedPatch(state);
                return;
            }
            sendRequest(Operation
                    .createPatch(csd.description.enumerationAdapterReference)
                    .setBody(req));
        };

        URI computeUri = UriUtils
                .extendUriWithQuery(
                        UriUtils.buildUri(this.getHost(), state.parentComputeLink),
                        UriUtils.URI_PARAM_ODATA_EXPAND,
                        Boolean.TRUE.toString());

        sendRequest(Operation.createGet(computeUri)
                .setCompletion(descriptionCompletion));
    }

    @Override
    protected boolean validateTransition(Operation patch,
            ResourceEnumerationTaskState currentTask, ResourceEnumerationTaskState patchBody) {
        boolean ok = super.validateTransition(patch, currentTask, patchBody);

        if (ok) {
            if (currentTask.taskInfo.stage == TaskStage.STARTED
                    && patchBody.taskInfo.stage == TaskStage.STARTED) {
                patch.fail(new IllegalArgumentException("Cannot start task again"));
                return false;
            }
        }

        return ok;
    }

    public static void validateState(ResourceEnumerationTaskState state) {
        if (state.resourcePoolLink == null) {
            throw new IllegalArgumentException("resourcePoolLink is required.");
        }

        if (state.adapterManagementReference == null) {
            throw new IllegalArgumentException(
                    "adapterManagementReference is required.");
        }

        if (state.options == null) {
            state.options = EnumSet.noneOf(TaskOption.class);
        }

        if (state.taskInfo == null || state.taskInfo.stage == null) {
            state.taskInfo = new TaskState();
            state.taskInfo.stage = TaskState.TaskStage.CREATED;
        }

        if (state.documentExpirationTimeMicros == 0) {
            state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                    + DEFAULT_TIMEOUT_MICROS;
        }
    }
}
