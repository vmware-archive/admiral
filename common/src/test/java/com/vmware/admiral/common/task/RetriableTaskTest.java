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

package com.vmware.admiral.common.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.task.RetriableTask.RetriableTaskException;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.test.VerificationHost;

public class RetriableTaskTest {

    private VerificationHost host;

    @Before
    public void setUp() throws Throwable {
        host = createHost();
    }

    @Test
    public void testRetriableTaskConfigurationCannotChangeAfterExcecution() {

        final long retryDelay = 0;
        final int maxRetries = 0;
        final int expectedExecutions = 1; // no retries

        AtomicInteger counter = new AtomicInteger(0);

        RetriableTaskBuilder<Integer> task = new RetriableTaskBuilder<Integer>(
                "should-succeed-on-first-execution")
                        .withServiceHost(host)
                        .withRetryDelays(retryDelay)
                        .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                        .withMaximumRetries(maxRetries)
                        .withTaskFunction(t -> {
                            return DeferredResult.completed(counter.incrementAndGet());
                        });

        Integer result = testExecuteTask(task);
        assertNotNull(result);
        assertEquals(expectedExecutions, counter.get());
        assertEquals(expectedExecutions, result.intValue());

        try {
            task.withMaximumRetries(5);
            fail("Retriable tasks should not be able to change maximum retries after execution.");
        } catch (IllegalStateException ex) {
            assertAlreadyExecutedException(ex);
        }

        try {
            task.withRetryDelays(5L);
            fail("Retriable tasks should not be able to change retry delays after execution.");
        } catch (IllegalStateException ex) {
            assertAlreadyExecutedException(ex);
        }

        try {
            task.withRetryDelaysTimeUnit(TimeUnit.MINUTES);
            fail("Retriable tasks should not be able to change retry delays time unit after execution.");
        } catch (IllegalStateException ex) {
            assertAlreadyExecutedException(ex);
        }

        try {
            task.withServiceHost(new VerificationHost());
            fail("Retriable tasks should not be able to change service host after execution.");
        } catch (IllegalStateException ex) {
            assertAlreadyExecutedException(ex);
        }

        try {
            task.withTaskFunction((t) -> DeferredResult.completed(1));
            fail("Retriable tasks should not be able to change task function after execution.");
        } catch (IllegalStateException ex) {
            assertAlreadyExecutedException(ex);
        }

        try {
            task.withTaskId("new-task-id");
            fail("Retriable tasks should not be able to change task id after execution.");
        } catch (IllegalStateException ex) {
            assertAlreadyExecutedException(ex);
        }
    }

    @Test
    public void testRetriableTaskCanBeExecutedOnlyOnceFromBuilder() {

        final long retryDelay = 0;
        final int maxRetries = 0;
        final int expectedExecutions = 1; // no retries

        AtomicInteger counter = new AtomicInteger(0);

        RetriableTaskBuilder<Integer> task = new RetriableTaskBuilder<Integer>(
                "should-succeed-on-first-execution")
                        .withServiceHost(host)
                        .withRetryDelays(retryDelay)
                        .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                        .withMaximumRetries(maxRetries)
                        .withTaskFunction(t -> {
                            return DeferredResult.completed(counter.incrementAndGet());
                        });

        Integer result = testExecuteTask(task);
        assertNotNull(result);
        assertEquals(expectedExecutions, counter.get());
        assertEquals(expectedExecutions, result.intValue());

        try {
            testExecuteTask(task);
            fail("Retriable tasks should be able to be executed only once.");
        } catch (IllegalStateException ex) {
            assertAlreadyExecutedException(ex);
        }

    }

    @Test
    public void testRetriableTaskCanBeExecutedOnlyOnce() {

        final long retryDelay = 0;
        final int maxRetries = 0;
        final int expectedExecutions = 1; // no retries

        AtomicInteger counter = new AtomicInteger(0);

        RetriableTaskBuilder<Integer> task = new RetriableTaskBuilder<Integer>(
                "should-succeed-on-first-execution")
                        .withServiceHost(host)
                        .withRetryDelays(retryDelay)
                        .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                        .withMaximumRetries(maxRetries)
                        .withTaskFunction(t -> {
                            int currentExecution = counter.getAndIncrement();
                            if (currentExecution > 0) {
                                // a failsafe to avoid recurrsion in case
                                // the actual implementation gets broken
                                return DeferredResult.failed(new IllegalStateException(
                                        "Only a single execution was expected"));
                            }
                            // this should fail
                            return t.execute();
                        });


        try {
            testExecuteTask(task);
            fail("Retriable tasks should be able to be executed only once.");
        } catch (RetriableTaskException ex) {
            assertAlreadyExecutedException(ex);
        }

        assertEquals(expectedExecutions, counter.get());
    }

    @Test
    public void testRetriableTaskSuccessOnFirstExecution() {
        final long retryDelay = 0;
        final int maxRetries = 3;
        final int expectedExecutions = 1; // no retries

        AtomicInteger counter = new AtomicInteger(0);

        RetriableTaskBuilder<Integer> task = new RetriableTaskBuilder<Integer>(
                "should-succeed-on-first-execution")
                        .withServiceHost(host)
                        .withRetryDelays(retryDelay)
                        .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                        .withMaximumRetries(maxRetries)
                        .withTaskFunction(t -> {
                            return DeferredResult.completed(counter.incrementAndGet());
                        });

        Integer result = testExecuteTask(task);
        assertNotNull(result);
        assertEquals(expectedExecutions, counter.get());
        assertEquals(expectedExecutions, result.intValue());
    }

    @Test
    public void testRetriableTaskSuccessAfterRetry() {
        final long retryDelay = 0;
        final int maxRetries = 3;
        final int expectedExecutions = 2; // one retry

        AtomicInteger counter = new AtomicInteger(0);

        RetriableTaskBuilder<Integer> task = new RetriableTaskBuilder<Integer>(
                "should-succeed-after-single-retry")
                        .withServiceHost(host)
                        .withRetryDelays(retryDelay)
                        .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                        .withMaximumRetries(maxRetries)
                        .withTaskFunction(t -> {
                            int executions = counter.incrementAndGet();
                            return executions == expectedExecutions
                                    ? DeferredResult.completed(executions)
                                    : DeferredResult.failed(new Exception(
                                            String.format("expected %d executions but were %d",
                                                    expectedExecutions, executions)));
                        });

        Integer result = testExecuteTask(task);
        assertNotNull(result);
        assertEquals(expectedExecutions, counter.get());
        assertEquals(expectedExecutions, result.intValue());
    }

    @Test
    public void testRetriableTaskRetriesAfterFailedFunctionEvaluation() {
        final long retryDelay = 0;
        final int maxRetries = 3;
        final int expectedExecutions = 2; // one retry

        AtomicInteger counter = new AtomicInteger(0);

        RetriableTaskBuilder<Integer> task = new RetriableTaskBuilder<Integer>(
                "should-succeed-after-single-retry")
                        .withServiceHost(host)
                        .withRetryDelays(retryDelay)
                        .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                        .withMaximumRetries(maxRetries)
                        .withTaskFunction(t -> {
                            int executions = counter.incrementAndGet();
                            if (executions == expectedExecutions) {
                                return DeferredResult.completed(executions);
                            }
                            throw new RuntimeException(
                                    String.format("expected %d executions but were %d",
                                            expectedExecutions, executions));
                        });

        Integer result = testExecuteTask(task);
        assertNotNull(result);
        assertEquals(expectedExecutions, counter.get());
        assertEquals(expectedExecutions, result.intValue());
    }

    @Test
    public void testRetriableTaskSuccessWithNumberOfRetriesInferred() {
        final Long[] retryDelays = { 0L, 0L, 0L };
        // will be inferred from the number of elements in retryDelays
        final Integer maxRetries = null;
        final int expectedExecutions = retryDelays.length;

        AtomicInteger counter = new AtomicInteger(0);

        RetriableTaskBuilder<Integer> task = new RetriableTaskBuilder<Integer>(
                "should-succeed-after-single-retry")
                        .withServiceHost(host)
                        .withRetryDelays(retryDelays)
                        .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                        .withMaximumRetries(maxRetries)
                        .withTaskFunction(t -> {
                            int executions = counter.incrementAndGet();
                            return executions == expectedExecutions
                                    ? DeferredResult.completed(executions)
                                    : DeferredResult.failed(new Exception(
                                            String.format("expected %d executions but were %d",
                                                    expectedExecutions, executions)));
                        });

        Integer result = testExecuteTask(task);
        assertNotNull(result);
        assertEquals(expectedExecutions, counter.get());
        assertEquals(expectedExecutions, result.intValue());
    }

    @Test
    public void testRetriableTaskFailureExceedingMaxRetries() {
        final long retryDelay = 0;
        final int maxRetries = 3;
        final int expectedExecutions = maxRetries + 1; // exceed max retries

        AtomicInteger counter = new AtomicInteger(0);

        RetriableTaskBuilder<Integer> task = new RetriableTaskBuilder<Integer>(
                "should-fail-after-exceeding-max-retries")
                        .withServiceHost(host)
                        .withRetryDelays(retryDelay)
                        .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                        .withMaximumRetries(maxRetries)
                        .withTaskFunction(t -> {
                            int executions = counter.incrementAndGet();
                            return DeferredResult.failed(
                                    new Exception(
                                            String.format("Failing execution %d", executions)));
                        });

        try {
            testExecuteTask(task);
        } catch (RetriableTaskException ex) {
            assertTrue(
                    String.format("Expected error message to start with '%s'",
                            RetriableTask.ERROR_MESSAGE_FORMAT_MAXIMUM_NUMBER_OF_RETRIES_EXCEEDED),
                    ex.getMessage()
                            .startsWith(String.format(
                                    RetriableTask.ERROR_MESSAGE_FORMAT_MAXIMUM_NUMBER_OF_RETRIES_EXCEEDED,
                                    "")));
            assertEquals(expectedExecutions, counter.get());
        }
    }

    @Test
    public void testRetriableTaskFailureWithPreventedRetries() {
        final long retryDelay = 0;
        final int maxRetries = 3;
        final int expectedExecutions = 1; // retries will be prevent on the first execution

        AtomicInteger counter = new AtomicInteger(0);

        RetriableTaskBuilder<Integer> task = new RetriableTaskBuilder<Integer>(
                "should-fail-and-prevent-retries")
                        .withServiceHost(host)
                        .withRetryDelays(retryDelay)
                        .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                        .withMaximumRetries(maxRetries)
                        .withTaskFunction(t -> {
                            t.preventRetries();
                            int executions = counter.incrementAndGet();
                            return DeferredResult.failed(
                                    new Exception(
                                            String.format("Failing execution %d", executions)));
                        });

        try {
            testExecuteTask(task);
        } catch (RetriableTaskException ex) {
            assertTrue(
                    String.format("Expected error message to start with '%s'",
                            RetriableTask.ERROR_MESSAGE_FORMAT_RETRIES_PREVENTED),
                    ex.getMessage().startsWith(String
                            .format(RetriableTask.ERROR_MESSAGE_FORMAT_RETRIES_PREVENTED, "")));
            assertEquals(expectedExecutions, counter.get());
        }
    }

    @Test
    public void testStripCompletionException() {
        String testMessage = "test message";
        String wrappedMessage = "wrapped message";

        Exception testException = new Exception(testMessage);
        Exception wrappedOnce = new CompletionException(wrappedMessage, testException);
        Exception wrappedTwice = new CompletionException(wrappedMessage, wrappedOnce);

        assertEquals(testMessage,
                RetriableTask.stripCompletionException(testException).getMessage());
        assertEquals(testMessage,
                RetriableTask.stripCompletionException(wrappedOnce).getMessage());
        assertEquals(testMessage,
                RetriableTask.stripCompletionException(wrappedTwice).getMessage());
    }

    private <T> T testExecuteTask(RetriableTaskBuilder<T> task) {

        ArrayList<T> result = new ArrayList<>(1);

        host.testStart(1);
        task.execute().whenComplete((taskResult, ex) -> {
            if (ex != null) {
                host.failIteration(ex);
            } else {
                result.add(taskResult);
                host.completeIteration();
            }
        });
        host.testWait();

        return result.iterator().next();
    }

    private void assertAlreadyExecutedException(Exception ex) {
        String alreadyExecutedText = "has already been executed";
        assertTrue(
                String.format("expected error message to contain '%s' but was '%s'",
                        alreadyExecutedText, ex.getMessage()),
                ex.getMessage().contains(alreadyExecutedText));
    }

    private VerificationHost createHost() throws Throwable {
        ServiceHost.Arguments args = new ServiceHost.Arguments();
        args.sandbox = null; // ask runtime to pick a random storage location
        args.port = 0; // ask runtime to pick a random port
        args.isAuthorizationEnabled = false;

        VerificationHost h = new VerificationHost();
        h = VerificationHost.initialize(h, args);
        h.start();

        return h;
    }

}
