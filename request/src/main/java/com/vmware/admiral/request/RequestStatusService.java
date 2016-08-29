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

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.vmware.admiral.request.composition.CompositionSubTaskService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
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

    public static class RequestStatus extends
            com.vmware.admiral.service.common.AbstractTaskStatefulService.TaskStatusState {

        public static final String FIELD_NAME_REQUEST_PROGRESS_BY_COMPONENT =
                "requestProgressByComponent";
        public static final String FIELD_NAME_COMPONENT_NAMES = "componentNames";

        /** Request progress (0-100%) */
        public Map<String, Map<String, Integer>> requestProgressByComponent;

        /** Current component in a composition, or null for a non-component phase */
        public String component;

        /** list of expected component names in a composition request */
        public List<String> componentNames;

        public List<String> trackedExecutionTasks;
        public List<String> trackedAllocationTasks;

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
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation post) {
        RequestStatus body = post.getBody(RequestStatus.class);
        body.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + EXPIRATION_MICROS;
        post.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        RequestStatus body = patch.getBody(RequestStatus.class);
        RequestStatus state = getState(patch);

        if (body.componentNames != null) {
            handleUpdateComponents(state, body);

        } else {
            handleUpdateProgress(state, body);
        }

        if (state.name == null && body.name != null) {
            state.name = body.name;
        }

        if (state.resourceLinks == null
                && body.resourceLinks != null
                && !body.resourceLinks.isEmpty()) {
            state.resourceLinks = body.resourceLinks;
        }

        setState(patch, state);
        patch.complete();
    }

    private void handleUpdateProgress(RequestStatus state, RequestStatus body) {
        state.phase = body.phase;
        String component = DEFAULT_COMPONENT_NAME;
        String requestId = Service.getId(state.documentSelfLink);
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
            return;
            // if the request is finished the progress should be updated to 100
        } else if (TaskStage.FINISHED.equals(state.taskInfo.stage)) {
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
                body.componentNames);

        if (body.eventLogLink != null) {
            body.eventLogLink = state.eventLogLink;
        }

        Map<String, Integer> allocationTemplate = body.componentNames.stream()
                .collect(Collectors.toMap((s) -> s + CompositionSubTaskService.ALLOC_SUFFIX,
                        (n) -> Integer.valueOf(0)));

        Map<String, Integer> executionTemplate = body.componentNames.stream()
                .collect(Collectors.toMap(Function.identity(), (n) -> Integer.valueOf(0)));

        Map<String, Integer> bothTemplate = new HashMap<>(allocationTemplate);
        bothTemplate.putAll(executionTemplate);

        for (String k : state.requestProgressByComponent.keySet()) {
            Map<String, Integer> template;
            if (state.trackedExecutionTasks.contains(k)) {
                if (state.trackedAllocationTasks.contains(k)) {
                    template = bothTemplate;
                } else {
                    template = executionTemplate;
                }

            } else if (state.trackedAllocationTasks.contains(k)) {
                template = allocationTemplate;

            } else {
                // ignore untracked key
                continue;
            }

            // progress may have already been recorded, so merge it into the new map
            template = new HashMap<>(template);
            Map<String, Integer> existingProgress = state.requestProgressByComponent.get(k);
            for (String component : template.keySet()) {
                Integer componentProgress = existingProgress.get(component);
                if (componentProgress != null) {
                    template.put(component, componentProgress);
                }
            }
            state.requestProgressByComponent.put(k, template);
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        RequestStatus template = (RequestStatus) super.getDocumentTemplate();
        template.phase = "Container Allocation";
        template.taskInfo = new TaskState();
        template.taskInfo.stage = TaskStage.CREATED;
        template.subStage = "CREATED";
        template.component = "mysql";
        template.progress = 0;

        template.documentDescription.propertyDescriptions.get(
                RequestStatus.FIELD_NAME_REQUEST_PROGRESS_BY_COMPONENT).indexingOptions = EnumSet
                .of(PropertyIndexingOption.STORE_ONLY);

        template.documentDescription.propertyDescriptions.get(
                RequestStatus.FIELD_NAME_COMPONENT_NAMES).indexingOptions = EnumSet
                .of(PropertyIndexingOption.STORE_ONLY);

        return template;
    }
}
