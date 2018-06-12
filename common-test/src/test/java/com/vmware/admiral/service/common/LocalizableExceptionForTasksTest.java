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

package com.vmware.admiral.service.common;

import java.time.Duration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.service.common.LocalizableExceptionForTasksTest.TestTaskService.TestTaskServiceDocument;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.test.TestContext;

public class LocalizableExceptionForTasksTest extends BaseTestCase {

    public static final String ERROR_MESSAGE = "Random test error message: {0}";
    public static final String ERROR_MESSAGE_EN = "Random test error message: argValue";
    public static final String ERROR_MESSAGE_GERMAN = "Random test error message in German!: argValue";
    private static final String ERROR_MESSAGE_CODE = "random.message.code";
    private static final String ARG_VALUE = "argValue";

    private static LocalizableValidationException ex =
            new LocalizableValidationException(ERROR_MESSAGE, ERROR_MESSAGE_CODE, ARG_VALUE);

    @Before
    public void setUp() throws Throwable {
        Service serviceInstance;
        try {
            serviceInstance = TestTaskService.class.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create factory for " + TestTaskService.class, e);
        }
        host.startFactory(serviceInstance);

        waitForServiceAvailability(host, TestTaskService.FACTORY_LINK);
    }

    @Test
    public void testLocalizableExceptionWithTask() throws Throwable {

        TestContext waitContext = new TestContext(2, Duration.ofSeconds(30));

        String[] taskLink = new String[2];
        Operation postDE = Operation.createPost(host, TestTaskService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setReferer(host.getReferer())
                .addRequestHeader(Operation.ACCEPT_LANGUAGE_HEADER, "de")
                .setCompletion((o, e) -> {
                    TestTaskServiceDocument task = o.getBody(TestTaskServiceDocument.class);
                    taskLink[0] = task.documentSelfLink;
                    waitContext.complete();
                });

        Operation postEN = Operation.createPost(host, TestTaskService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setReferer(host.getReferer())
                .addRequestHeader(Operation.ACCEPT_LANGUAGE_HEADER, "en")
                .setCompletion((o, e) -> {
                    TestTaskServiceDocument task = o.getBody(TestTaskServiceDocument.class);
                    taskLink[1] = task.documentSelfLink;
                    waitContext.complete();
                });

        host.send(postDE);
        host.send(postEN);

        waitContext.await();

        TestTaskServiceDocument taskErrorDE = waitForTaskCompletion(taskLink[0], TestTaskServiceDocument.class);
        TestTaskServiceDocument taskErrorEN = waitForTaskCompletion(taskLink[1], TestTaskServiceDocument.class);

        Assert.assertEquals(ERROR_MESSAGE_GERMAN, taskErrorDE.taskInfo.failure.message);
        Assert.assertEquals(ERROR_MESSAGE_EN, taskErrorEN.taskInfo.failure.message);
    }

    public static class TestTaskService extends AbstractTaskStatefulService<TestTaskService.TestTaskServiceDocument, DefaultSubStage> {

        public static final String FACTORY_LINK = "/task/failing-task";

        public static class TestTaskServiceDocument extends TaskServiceDocument<DefaultSubStage> {

        }

        public TestTaskService() {
            super(TestTaskServiceDocument.class, DefaultSubStage.class, "Test Task");

            super.toggleOption(ServiceOption.PERSISTENCE, true);
            super.toggleOption(ServiceOption.REPLICATION, true);
            super.toggleOption(ServiceOption.OWNER_SELECTION, true);
            super.transientSubStages = DefaultSubStage.TRANSIENT_SUB_STAGES;
        }

        @Override
        protected void handleStartedStagePatch(TestTaskServiceDocument state) {
            switch (state.taskSubStage) {
            case CREATED:
                fail();
                break;
            case ERROR:
                completeWithError();
                break;
            case COMPLETED:
                complete();
                break;
            default:
                break;
            }
        }

        private void fail() {
            sendRequest(Operation.createGet(getHost(), FACTORY_LINK)
                    .setCompletion((o, e) -> {
                        failTask("Failure", ex);
                    }));
        }

    }

}
