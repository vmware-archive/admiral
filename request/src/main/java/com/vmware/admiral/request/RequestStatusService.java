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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import io.swagger.annotations.ApiModelProperty;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.request.composition.CompositionGraph.ResourceNode;
import com.vmware.admiral.request.composition.CompositionSubTaskService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

/**
 * status of request with the latest task update info
 */
public class RequestStatusService extends StatefulService {
    public static final String DEFAULT_COMPONENT_NAME = "__DEFAULT__";
    private static final Map<String, Integer> TEMPLATE_PROGRESS_MAP = Collections.singletonMap(
            DEFAULT_COMPONENT_NAME, 0);

    private static final long EXPIRATION_MICROS = TimeUnit.MINUTES.toMicros(Long.getLong(
            "com.vmware.admiral.request.status.expiration.mins",
            TimeUnit.DAYS.toMinutes(7)));
    private static final int MAX_STATE_SIZE = 1024 * 224;

    public static class RequestStatus extends
            com.vmware.admiral.service.common.AbstractTaskStatefulService.TaskStatusState {

        public static final String FIELD_NAME_REQUEST_PROGRESS_BY_COMPONENT =
                "requestProgressByComponent";
        public static final String FIELD_NAME_COMPONENTS = "components";

        public static final String CUSTOM_PROP_NAME_REQUEST_TYPE = "__requestType";

        /** Request progress (0-100%) */
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Map<String, Map<String, Integer>> requestProgressByComponent;

        /** Current component in a composition, or null for a non-component phase */
        public String component;

        /** collection of expected components in a composition request */
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public List<ResourceNode> components;

        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Map<ResourceType, List<String>> trackedExecutionTasksByResourceType;
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Map<ResourceType, List<String>> trackedAllocationTasksByResourceType;

        /** (Optional) Custom properties that store additional data for the request status. */
        @Documentation(description = "Custom properties that store additional data for the request status.")
        @ApiModelProperty(value = "Custom properties that store additional data for the request status.", required = false)
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @PropertyOptions(indexing = { PropertyIndexingOption.CASE_INSENSITIVE,
                PropertyIndexingOption.EXPAND })
        @Since(ReleaseConstants.RELEASE_VERSION_1_4_2)
        public Map<String, String> customProperties;

        public void addTrackedTasks(String... taskNames) {
            if (requestProgressByComponent == null) {
                requestProgressByComponent = new HashMap<>();
            }
            for (String taskName : taskNames) {
                requestProgressByComponent.put(taskName, new HashMap<>(TEMPLATE_PROGRESS_MAP));
            }
        }
    }

    public RequestStatusService() {
        super(RequestStatus.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleCreate(Operation post) {
        RequestStatus body = post.getBody(RequestStatus.class);
        body.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(EXPIRATION_MICROS);
        post.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        RequestStatus body = patch.getBody(RequestStatus.class);
        RequestStatus state = getState(patch);

        if (body.components != null) {
            handleUpdateComponents(state, body);
        } else {
            handleUpdateProgress(state, body);
        }

        if (state.name == null && body.name != null) {
            state.name = body.name;
        }

        if ((state.resourceLinks == null
                && body.resourceLinks != null
                && !body.resourceLinks.isEmpty())
                || willChangeToK8s(state, body)) {
            state.resourceLinks = body.resourceLinks;
        }

        setState(patch, state);
        patch.complete();
    }

    private boolean willChangeToK8s(RequestStatus state, RequestStatus body) {
        return state.resourceLinks != null
                && state.resourceLinks.stream().anyMatch( l -> l.contains(ManagementUriParts.COMPOSITE_COMPONENT))
                && body.resourceLinks != null
                && body.resourceLinks.stream().anyMatch(l -> l.contains(ManagementUriParts.KUBERNETES_DEPLOYMENTS));
    }

    private void handleUpdateProgress(RequestStatus state, RequestStatus body) {
        state.phase = body.phase;
        String component = DEFAULT_COMPONENT_NAME;
        String requestId = getSelfId();
        if (body.eventLogLink != null) {
            state.eventLogLink = body.eventLogLink;
        }
        if (!body.documentSelfLink.equals(requestId)) {
            component = getComponentName(body.documentSelfLink, requestId);
            state.component = component;

        } else {
            state.component = null;
        }

        if (state.progress == 100) {
            if (DefaultSubStage.ERROR.name().equals(body.subStage) ||
                    TaskStage.FAILED.name().equals(body.subStage) ||
                    TaskState.isFailed(body.taskInfo)) {
                state.taskInfo = TaskState.createAsFailed();
            } else {
                state.taskInfo = TaskState.createAsFinished();
            }
            state.subStage = body.subStage;
            return;
            // if the request is finished the progress should be updated to 100
        } else if (TaskStage.FINISHED == state.taskInfo.stage) {
            state.progress = 100;
        }

        state.taskInfo = body.taskInfo;
        state.subStage = body.subStage;

        if (body.progress != null) {
            if (state.requestProgressByComponent == null) {
                state.requestProgressByComponent = new HashMap<>();
            }

            // only update progress for tasks explicitly added to the map
            if (state.requestProgressByComponent.containsKey(body.phase)) {
                Map<String, Integer> requestProgress = state.requestProgressByComponent
                        .get(body.phase);

                // only update the progress if it's higher than a previous update
                Integer existingProgress = requestProgress.get(component);
                if (existingProgress == null || existingProgress.compareTo(body.progress) < 0) {
                    requestProgress.put(component, body.progress);
                }
            }
        }

        // average progress of all tasks
        state.progress = (int) state.requestProgressByComponent.values().stream()
                .flatMap((m) -> m.values().stream())
                .mapToDouble(Number::intValue)
                .average()
                .orElse(0);

        logFine("Request progress: %d, task progress: %s", state.progress,
                state.requestProgressByComponent);
    }

    private String getComponentName(String selfLink, String requestId) {
        return selfLink.replaceFirst(requestId + "-", "");
    }

    public void handleUpdateComponents(RequestStatus state, RequestStatus body) {
        logInfo("Updating request tracker [%s] components to: %s", state.documentSelfLink,
                body.components);

        if (body.eventLogLink != null) {
            body.eventLogLink = state.eventLogLink;
        }

        for (ResourceNode rn : body.components) {
            ResourceType type = ResourceType.fromName(rn.resourceType);
            List<String> trackedExecutionTasks = state.trackedExecutionTasksByResourceType
                    .get(type);
            if (trackedExecutionTasks == null) {
                trackedExecutionTasks = new ArrayList<>();
            }
            List<String> trackedAllocationTasks = state.trackedAllocationTasksByResourceType
                    .get(type);
            if (trackedAllocationTasks == null) {
                trackedAllocationTasks = new ArrayList<>();
            }

            String name = UriUtilsExtended.getValueEncoded(rn.name);
            String allocName = name + CompositionSubTaskService.ALLOC_SUFFIX;

            for (String k : state.requestProgressByComponent.keySet()) {

                Map<String, Integer> existingProgress = state.requestProgressByComponent.get(k);
                Map<String, Integer> template;
                if (existingProgress == null) {
                    template = new HashMap<>();
                } else {
                    template = new HashMap<>(existingProgress);
                }

                if (trackedAllocationTasks.contains(k)) {
                    template.putIfAbsent(allocName, Integer.valueOf(0));
                }
                if (trackedExecutionTasks.contains(k)) {
                    template.putIfAbsent(name, Integer.valueOf(0));
                }

                state.requestProgressByComponent.put(k, template);
            }
        }

        for (Map<String, Integer> progress : state.requestProgressByComponent.values()) {
            progress.remove(DEFAULT_COMPONENT_NAME);
            progress.remove(UriUtilsExtended.getValueEncoded(DEFAULT_COMPONENT_NAME));
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        RequestStatus template = (RequestStatus) super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        template.phase = "Container Allocation";
        template.taskInfo = new TaskState();
        template.taskInfo.stage = TaskStage.CREATED;
        template.subStage = "CREATED";
        template.component = "mysql";
        template.progress = 0;
        // overwrite max size limit
        template.documentDescription.serializedStateSizeLimit = MAX_STATE_SIZE;

        return template;
    }
}
