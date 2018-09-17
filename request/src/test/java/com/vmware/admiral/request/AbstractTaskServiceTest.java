/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.request.TestTaskService.TestTaskServiceDocument;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;

// AbstractTaskService validations test
public class AbstractTaskServiceTest extends RequestBaseTest {

    @Override
    @Before
    public void setUp() throws Throwable {
        host.startFactory(new TestTaskService());
        waitForServiceAvailability(TestTaskService.FACTORY_LINK);
    }

    @Test
    public void testCreateWithEmptyBody() throws Throwable {
        TestTaskService testService = new TestTaskService();
        Operation post = Operation.createPost(host, TestTaskService.FACTORY_LINK);
        testService.handleCreate(post);

        assertTrue(post.getErrorResponseBody() != null);
    }

    @Test
    public void testPatchWithEmptyBody() throws Throwable {
        TestTaskService testService = new TestTaskService();
        Operation post = Operation.createPatch(host, TestTaskService.FACTORY_LINK);
        testService.handlePatch(post);

        assertTrue(post.getErrorResponseBody() != null);
    }

    @Test
    public void testStartWithoutTaskInfo() throws Throwable {
        TestTaskService testService = new TestTaskService();
        testService.setHost(host);

        TestTaskServiceDocument serviceState = new TestTaskServiceDocument();

        Operation startPost = Operation.createPost(host, TestTaskService.FACTORY_LINK)
                .setBody(serviceState);
        testService.handleStart(startPost);

        assertTrue(startPost.getErrorResponseBody() != null);

        serviceState.taskInfo = TaskState.create();
        testService.setState(startPost, serviceState);
        startPost.setBody(serviceState);
        testService.handleStart(startPost);

        assertTrue(startPost.getErrorResponseBody() != null);
    }

    @Test
    public void testStartFailCallback() {
        TestTaskService testService = new TestTaskService();
        testService.setHost(host);
        Operation start = Operation.createPost(host, TestTaskService.FACTORY_LINK);

        TestTaskServiceDocument serviceState = new TestTaskServiceDocument();
        serviceState.taskInfo = TaskState.createAsStarted();
        serviceState.taskSubStage = DefaultSubStage.CREATED;
        serviceState.documentVersion = 5;
        serviceState.serviceTaskCallback = ServiceTaskCallback.create(TestTaskService.FACTORY_LINK);

        testService.setState(start, serviceState);
        start.setBody(serviceState);
        testService.handleStart(start);
        assertNull(start.getErrorResponseBody());

    }

    @Test
    public void testDeleteNullState() {
        TestTaskService testService = new TestTaskService();
        Operation delete = Operation.createDelete(host, TestTaskService.FACTORY_LINK);
        testService.handleDelete(delete);

        // Should not fail delete of null item
        assertNull(delete.getErrorResponseBody());

        TestTaskServiceDocument serviceState = new TestTaskServiceDocument();

        testService.setState(delete, serviceState);
        testService.handleDelete(delete);
        assertNull(delete.getErrorResponseBody());

        serviceState.taskInfo = TaskState.createAsFinished();
        testService.setState(delete, serviceState);
        testService.handleDelete(delete);
        assertNull(delete.getErrorResponseBody());
    }

    @Test
    public void testDeleteFailCallback() {
        TestTaskService testService = new TestTaskService();
        testService.setHost(host);
        Operation delete = Operation.createDelete(host, TestTaskService.FACTORY_LINK);

        TestTaskServiceDocument serviceState = new TestTaskServiceDocument();
        serviceState.taskInfo = TaskState.createAsStarted();
        serviceState.taskSubStage = DefaultSubStage.CREATED;
        serviceState.serviceTaskCallback = ServiceTaskCallback.create(TestTaskService.FACTORY_LINK);

        testService.setState(delete, serviceState);
        testService.handleDelete(delete);
        assertNull(delete.getErrorResponseBody());
    }

    @Test
    public void testHandleExpired() {
        TestTaskService testService = new TestTaskService();

        try {
            testService.handleStop(null);
            fail("should not reach here");
        } catch (NullPointerException ignored) {
        }

        ServiceDocument doc = new ServiceDocument();
        doc.documentExpirationTimeMicros = 13L;
        Operation o = Operation.createDelete(host, "/")
                .setBody(doc);

        testService.handleStop(o);
        assertEquals(13L, testService.expiration);
    }

    @Test
    public void testSendRequestStateToExternalUrl() {
        TestTaskService testService = new TestTaskService();
        testService.setHost(host);
        TestTaskServiceDocument state = new TestTaskServiceDocument();
        state.taskInfo = new TaskState();
        state.taskInfo.stage = TaskState.TaskStage.STARTED;
        state.serviceTaskCallback = ServiceTaskCallback.create("http://%%:ww/@!");
        testService.notifyCallerService(state);

        state.serviceTaskCallback = ServiceTaskCallback.create("http://s");
        testService.notifyCallerService(state);
    }

}
