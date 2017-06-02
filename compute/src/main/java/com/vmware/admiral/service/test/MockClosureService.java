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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.closures.drivers.DriverRegistry;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;

/**
 * Mock Closure Service to be used in unit and integration tests.
 */
@SuppressWarnings("rawtypes")
public class MockClosureService extends ClosureService {

    public MockClosureService(DriverRegistry driverRegistry,
            long maintenanceTimeout) {
        super(driverRegistry, maintenanceTimeout);
    }

    @SuppressWarnings("unchecked")
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

        // If state is STARTED move to FINISHED
        if (currentState.state == TaskState.TaskStage.STARTED) {
            moveToCompleteState(currentState);
            return currentState;
        }

        // On image ready move to STARTED.
        if (isImageReady(patch)) {
            moveToStartedState(currentState);
        }

        return currentState;
    }

    private boolean isImageReady(Operation patch) {
        ServiceTaskCallback.ServiceTaskCallbackResponse callbackResponse = patch
                .getBody(ServiceTaskCallback.ServiceTaskCallbackResponse.class);
        TaskState taskInfo = callbackResponse.taskInfo;
        return TaskState.isFinished(taskInfo);
    }

    private void moveToStartedState(Closure currentState) {
        Closure startedState = new Closure();
        startedState.state = TaskState.TaskStage.STARTED;
        startedState.closureSemaphore = currentState.closureSemaphore;
        getHost().schedule(() -> sendSelfPatch(startedState), 1, TimeUnit.SECONDS);
    }

    private void moveToCompleteState(Closure currentState) {
        Closure completedState = new Closure();
        completedState.state = TaskState.TaskStage.FINISHED;
        completedState.closureSemaphore = currentState.closureSemaphore;
        completedState.inputs = currentState.inputs;
        final Map outputs = new HashMap();
        completedState.inputs.forEach((k, v) -> {
            outputs.put(k, v);
        });

        completedState.outputs = outputs;

        getHost().schedule(() -> sendSelfPatch(completedState), 3, TimeUnit.SECONDS);
    }
}
