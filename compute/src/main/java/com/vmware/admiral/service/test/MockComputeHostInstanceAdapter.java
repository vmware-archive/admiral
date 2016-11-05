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

package com.vmware.admiral.service.test;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.tasks.SubTaskService.SubTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

/**
 * Mock Compute Instance adapter service to be used in unit and integration tests.
 */
public class MockComputeHostInstanceAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING
            + "/mock_success_instance_adapter";

    @SuppressWarnings("rawtypes")
    @Override
    public void handleRequest(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        switch (op.getAction()) {
        case PATCH:
            ComputeInstanceRequest request = op
                    .getBody(ComputeInstanceRequest.class);
            op.complete();
            if (request.requestType == InstanceRequestType.DELETE) {
                Operation.createDelete(request.resourceReference).setCompletion((o, e) -> {
                    SubTaskState computeSubTaskState = new SubTaskState();
                    computeSubTaskState.taskInfo = new TaskState();
                    if (e != null) {
                        computeSubTaskState.taskInfo.failure = Utils.toServiceErrorResponse(e);
                        computeSubTaskState.taskInfo.stage = TaskState.TaskStage.FAILED;
                    } else {
                        computeSubTaskState.taskInfo.stage = TaskState.TaskStage.FINISHED;
                    }
                    sendRequest(Operation.createPatch(
                            request.taskReference).setBody(
                                    computeSubTaskState));
                }).sendWith(this);
                return;
            }
            SubTaskState computeSubTaskState = new SubTaskState();
            computeSubTaskState.taskInfo = new TaskState();
            computeSubTaskState.taskInfo.stage = TaskState.TaskStage.FINISHED;
            sendRequest(Operation.createPatch(
                    request.taskReference).setBody(
                            computeSubTaskState));
            break;
        default:
            super.handleRequest(op);
        }
    }
}