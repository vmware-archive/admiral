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

package com.vmware.photon.controller.model.adapterapi;

import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

/**
 * Default response, to be used by adapters when reporting back to a given task.
 */
public class ResourceOperationResponse {
    public static final String KIND = Utils.buildKind(ResourceOperationResponse.class);

    public String resourceLink;

    public TaskState taskInfo;

    public String failureMessage;

    public String documentKind = KIND;

    public static ResourceOperationResponse finish(String resourceLink) {
        return response(resourceLink, TaskStage.FINISHED);
    }

    public static ResourceOperationResponse cancel(String resourceLink) {
        return response(resourceLink, TaskStage.CANCELLED);
    }

    public static ResourceOperationResponse fail(String resourceLink, Throwable t) {
        return fail(resourceLink, Utils.toServiceErrorResponse(t));
    }

    public static ResourceOperationResponse fail(String resourceLink,
            ServiceErrorResponse error) {
        ResourceOperationResponse r = response(resourceLink, TaskStage.FAILED);
        r.taskInfo.failure = error;
        return r;
    }

    private static ResourceOperationResponse response(String resourceLink, TaskStage stage) {
        ResourceOperationResponse r = new ResourceOperationResponse();
        r.resourceLink = resourceLink;
        r.taskInfo = new TaskState();
        r.taskInfo.stage = stage;
        return r;
    }
}
