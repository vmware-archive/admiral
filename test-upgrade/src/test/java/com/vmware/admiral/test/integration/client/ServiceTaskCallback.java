/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration.client;

import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.test.integration.client.TaskState.TaskStage;

/**
 * Service Task Callback Factory to provide an instance of {@link ServiceTaskCallbackResponse}
 * callback Task service response. Used to abstract the calling Task service from the next Task
 * service as well as enable multiple parent Task services to use the same child Task.
 */
public class ServiceTaskCallback {
    public TaskStage taskStageComplete;
    public TaskStage taskStageFailed;
    public Object subStageComplete;
    public Object subStageFailed;
    public String serviceSelfLink;

    protected ServiceTaskCallback() {
        // serialization constructor
    }

    public static ServiceTaskCallback create(String serviceSelfLink) {
        return new ServiceTaskCallback(serviceSelfLink, TaskStage.FINISHED, "COMPLETED",
                TaskStage.FAILED, "ERROR");
    }

    protected ServiceTaskCallback(String serviceSelfLink,
            TaskStage taskStageComplete, String subStageComplete,
            TaskStage taskStageFailed, String subStageFailed) {
        this.serviceSelfLink = serviceSelfLink;
        this.taskStageComplete = taskStageComplete;
        this.taskStageFailed = taskStageFailed;
        this.subStageComplete = subStageComplete;
        this.subStageFailed = subStageFailed;
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
