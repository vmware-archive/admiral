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

package com.vmware.admiral.service.test;

import java.util.Map;

import com.vmware.admiral.closures.drivers.DriverRegistry;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;

/**
 * Mock Closure Service to be used in unit and integration tests.
 */
public class MockClosureService extends ClosureService {

    public MockClosureService(DriverRegistry driverRegistry,
            long maintenanceTimeout) {
        super(driverRegistry, maintenanceTimeout);
    }

    protected Closure updateState(Operation patch, Closure requestedState) {
        Closure currentState = this.getState(patch);

        if (requestedState.resourceLinks != null) {
            currentState.resourceLinks = requestedState.resourceLinks;
        }
        if (requestedState.state != null) {
            currentState.state = requestedState.state;
        }
        if (requestedState.inputs != null) {
            currentState.inputs = requestedState.inputs;
        }
        if (requestedState.outputs != null) {
            currentState.outputs = requestedState.outputs;
        }
        if (requestedState.errorMsg != null) {
            currentState.errorMsg = requestedState.errorMsg;
        }
        if (requestedState.closureSemaphore != null) {
            currentState.closureSemaphore = requestedState.closureSemaphore;
        }

        if (requestedState.state == TaskState.TaskStage.STARTED) {
            currentState.lastLeasedTimeMillis = System.currentTimeMillis();
        }

        if (requestedState.serviceTaskCallback != null) {
            currentState.serviceTaskCallback = requestedState.serviceTaskCallback;
        }

        if (requestedState.logs != null) {
            currentState.logs = requestedState.logs;
        }

        // ON CREATE move immediately to FINISHED.
        ServiceTaskCallback.ServiceTaskCallbackResponse callbackResponse = patch
                .getBody(ServiceTaskCallback.ServiceTaskCallbackResponse.class);
        TaskState taskInfo = callbackResponse.taskInfo;

        if (TaskState.isFinished(taskInfo)) {
            final Map outputs = currentState.outputs;
            currentState.state = TaskState.TaskStage.FINISHED;
            currentState.inputs.forEach((k, v) -> {
                outputs.put(k, v);
            });
        }

        return currentState;
    }
}
