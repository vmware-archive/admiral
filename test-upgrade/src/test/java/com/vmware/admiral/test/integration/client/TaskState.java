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

/**
 * Describes a service task
 *
 * TODO this class is copied from DCP and needs to be replaced or kept in sync
 */
public class TaskState {

    public static enum TaskStage {
        /**
         * Task is created
         */
        CREATED,

        /**
         * Task has started processing
         */
        STARTED,

        /**
         * Task finished successfully
         */
        FINISHED,

        /**
         * Task failed, failure reason is in the failure property
         */
        FAILED,

        /**
         * Task was cancelled, cancellation reason is in the failure property
         */
        CANCELLED,
    }

    /**
     * Current stage of the query
     */
    public TaskStage stage;

    /**
     * Value indicating whether task should complete the creation POST only after its complete.
     * Client enables this at the risk of waiting for the POST and consuming a connection. It should
     * not be enabled for tasks that do long running I/O with other services
     */
    public boolean isDirect;

    /**
     * Failure description for tasks that terminate in FAILED stage
     */
    public ServiceErrorResponse failure;

    public static boolean isFailed(TaskState taskInfo) {
        return taskInfo.stage == TaskStage.FAILED;
    }

    public static boolean isFinished(TaskState taskInfo) {
        return taskInfo.stage == TaskStage.FINISHED;
    }

}
