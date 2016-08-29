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

package com.vmware.admiral.service.common;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Service Task Callback Factory to provide an instance of {@link ServiceTaskCallbackResponse}
 * callback Task service response. Used to abstract the calling Task service from the next Task
 * service as well as enable multiple parent Task services to use the same child Task.
 */
public class ServiceTaskCallback {
    private static final String EMPTY_CALLBACK_LINK = "No callback set";
    private TaskStage taskStageComplete;
    private TaskStage taskStageFailed;
    private Object subStageComplete;
    private Object subStageFailed;
    public String serviceSelfLink;

    protected ServiceTaskCallback() {
        // GSON serialization constructor
    }

    public static ServiceTaskCallback create(String serviceSelfLink,
            TaskStage taskStageComplete,
            Enum<?> subStageComplete, TaskStage taskStageFailed, Enum<?> subStageFailed) {
        return new ServiceTaskCallback(serviceSelfLink, taskStageComplete, subStageComplete,
                taskStageFailed, subStageFailed);
    }

    public static ServiceTaskCallback create(String serviceSelfLink,
            Enum<?> subStageComplete, Enum<?> subStageFailed) {
        return create(serviceSelfLink, TaskStage.FINISHED, subStageComplete,
                TaskStage.FAILED, subStageFailed);
    }

    public static ServiceTaskCallback create(String serviceSelfLink) {
        return create(serviceSelfLink, DefaultSubStage.COMPLETED, DefaultSubStage.ERROR);
    }

    public static ServiceTaskCallback createEmpty() {
        return create(EMPTY_CALLBACK_LINK, DefaultSubStage.COMPLETED, DefaultSubStage.ERROR);
    }

    public boolean isEmpty() {
        return serviceSelfLink == null || EMPTY_CALLBACK_LINK.equals(serviceSelfLink);
    }

    public boolean isExternal() {
        return serviceSelfLink != null && serviceSelfLink.startsWith(UriUtils.HTTP_SCHEME);
    }

    protected ServiceTaskCallback(String serviceSelfLink,
            TaskStage taskStageComplete, Enum<?> subStageComplete,
            TaskStage taskStageFailed, Enum<?> subStageFailed) {
        assertNotEmpty(serviceSelfLink, "serviceSelfLink");
        assertNotNull(taskStageComplete, "taskStageComplete");
        assertNotNull(taskStageFailed, "taskStageFailed");
        this.serviceSelfLink = serviceSelfLink;
        this.taskStageComplete = taskStageComplete;
        this.taskStageFailed = taskStageFailed;
        this.subStageComplete = subStageComplete;
        this.subStageFailed = subStageFailed;
    }

    public ServiceTaskCallbackResponse getFinishedResponse() {
        return new ServiceTaskCallbackResponse(taskStageComplete, subStageComplete, null);
    }

    public ServiceTaskCallbackResponse getFailedResponse(Throwable e) {
        return new ServiceTaskCallbackResponse(taskStageFailed, subStageFailed,
                Utils.toServiceErrorResponse(e));
    }

    public ServiceTaskCallbackResponse getFailedResponse(ServiceErrorResponse failure) {
        return new ServiceTaskCallbackResponse(taskStageFailed, subStageFailed, failure);
    }

    /**
     * Service Task Response Callback patch body definition.
     */
    public static class ServiceTaskCallbackResponse {
        public TaskState taskInfo;
        public Object taskSubStage;
        public Map<String, String> customProperties;

        protected ServiceTaskCallbackResponse() {
            // GSON serialization constructor
        }

        public ServiceTaskCallbackResponse(TaskStage taskStage, Object taskSubStage,
                ServiceErrorResponse failure) {
            this.taskInfo = new TaskState();
            this.taskInfo.stage = taskStage;
            this.taskInfo.failure = failure;
            this.taskSubStage = taskSubStage;
        }

        public void copy(ServiceTaskCallbackResponse serviceTaskCallbackResponse) {
            this.taskInfo = serviceTaskCallbackResponse.taskInfo;
            this.taskSubStage = serviceTaskCallbackResponse.taskSubStage;
            this.customProperties = serviceTaskCallbackResponse.customProperties;
        }

        public void addProperty(String propName, String propValue) {
            if (customProperties == null) {
                customProperties = new HashMap<>(2);
            }
            customProperties.put(propName, propValue);
        }
    }
}
