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
            assertTrue("expected error message to contain 'already executed'",
                    ex.getMessage().contains("already executed"));
        }
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
            assertEquals(
                    RetriableTask.ERROR_MESSAGE_MAXIMUM_NUMBER_OF_RETRIES_EXCEEDED,
                    ex.getMessage());
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
            assertEquals(
                    RetriableTask.ERROR_MESSAGE_RETRIES_PREVENTED,
                    ex.getMessage());
            assertEquals(expectedExecutions, counter.get());
        }
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
