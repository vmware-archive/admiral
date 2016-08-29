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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

public class TaskServiceDocumentTest {

    @Test
    public void ensureSerializationAndCloning() throws Throwable {
        TestTaskServiceDocument task = new TestTaskServiceDocument();
        task.taskSubStage = DefaultSubStage.PROCESSING;
        task.taskInfo = new TaskState();
        task.taskInfo.stage = TaskStage.CREATED;

        task = Utils.clone(task);
        assertEquals(DefaultSubStage.PROCESSING, task.taskSubStage);
        assertEquals(TaskStage.CREATED, task.taskInfo.stage);

        String jsonTask = Utils.toJson(task);

        task = Utils.fromJson(jsonTask, TestTaskServiceDocument.class);
        assertEquals(DefaultSubStage.PROCESSING, task.taskSubStage);
        assertEquals(TaskStage.CREATED, task.taskInfo.stage);

    }

    private static class TestTaskServiceDocument extends TaskServiceDocument<DefaultSubStage> {
    }
}
