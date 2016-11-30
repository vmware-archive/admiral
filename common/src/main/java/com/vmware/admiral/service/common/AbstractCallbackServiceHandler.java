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

import com.vmware.xenon.common.Operation;

/**
 * Abstract class to provide the base functionality of a simple task callback handler implementation.
 */
public abstract class AbstractCallbackServiceHandler extends
        AbstractTaskStatefulService<AbstractCallbackServiceHandler.CallbackServiceHandlerState,
        DefaultSubStage> {

    protected Runnable completionCallback;

    public static class CallbackServiceHandlerState
            extends com.vmware.admiral.service.common.TaskServiceDocument<DefaultSubStage> {

    }

    public AbstractCallbackServiceHandler() {
        super(CallbackServiceHandlerState.class, DefaultSubStage.class, getDisplayName());
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);

        this.setSelfDelete(true);
    }

    protected static String getDisplayName() {
        return "System Container Callback";
    }

    public void setCompletionCallback(Runnable completionCallback) {
        this.completionCallback = completionCallback;
    }

    @Override
    public void handleStart(Operation startPost) {
        // invoke handleCreate() from handleStart() since services inheriting
        // AbstractCallbackServiceHandler are started getHost().startService(...)
        // in which case handleCreate() is not being called.
        super.handleCreate(startPost);

        // startPost.complete() will not be called above since
        // validateStateOnStart() method bellow returns true,
        // which will prevent startPost operation to be completed in handleCreate()
        super.handleStart(startPost);
    }

    @Override
    protected boolean validateStateOnStart(CallbackServiceHandlerState state, Operation startPost)
            throws IllegalArgumentException {
        super.validateStateOnStart(state, startPost);
        return true;//make sure that super.handleCreate() above doesn't close the startPost operation
    }

    @Override
    protected void handleStartedStagePatch(CallbackServiceHandlerState state) {
    }
}
