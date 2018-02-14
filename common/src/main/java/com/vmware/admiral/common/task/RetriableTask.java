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

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vmware.admiral.common.task.RetriableTaskBuilder.RetriableTaskConfiguration;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Utils;

/**
 * A task that can be automatically retried a number of times with a configurable delay between
 * retries. See {@link RetriableTaskBuilder} for execution of {@link RetriableTask}s.
 *
 * @param <T>
 *            the type of the expected result for this task.
 */
public class RetriableTask<T> {

    protected static final String ERROR_MESSAGE_FORMAT_RETRIES_PREVENTED = "Retries are prevented. Failure: %s";
    protected static final String ERROR_MESSAGE_FORMAT_MAXIMUM_NUMBER_OF_RETRIES_EXCEEDED = "Maximum number of retries exceeded. Failure: %s";

    /**
     * A {@link RuntimeException} that indicates that a {@link RetriableTask} has completed with
     * failure.
     */
    @SuppressWarnings("serial")
    public static class RetriableTaskException extends RuntimeException {

        public RetriableTaskException() {
            super();
        }

        public RetriableTaskException(String message) {
            super(message);
        }

        public RetriableTaskException(Throwable cause) {
            super(cause);
        }

        public RetriableTaskException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    private RetriableTaskConfiguration<T> taskConfiguration;
    private AtomicBoolean executed;
    private AtomicBoolean retriesPrevented;

    /**
     * Internal use constructor. Instances are supposed to be created through the
     * {@link RetriableTaskBuilder}.
     */
    protected RetriableTask(RetriableTaskConfiguration<T> taskConfiguration) {
        this.taskConfiguration = taskConfiguration;
        this.retriesPrevented = new AtomicBoolean(false);
        this.executed = new AtomicBoolean(false);
    }

    /**
     * Prevents all following retries for this {@link RetriableTask} even if the maximum number of
     * retries has not been exceeded.
     */
    public void preventRetries() {
        preventRetries(true);
    }

    /**
     * Controls whether retries for this {@link RetriableTask} are allowed or not. Even if the
     * retries are not prevented, a failed {@link RetriableTask} will not be retried when the
     * maximum number of retries is exceeded.
     */
    public void preventRetries(boolean preventRetries) {
        this.retriesPrevented.set(preventRetries);
    }

    /**
     * Returns <code>true</code> if retries are prevented and <code>false</code> otherwise. Note
     * that this check does not account for the number of remaining retries available.
     */
    public boolean areRetriesPrevented() {
        return this.retriesPrevented.get();
    }

    /**
     * Execute this {@link RetriableTask}. The returned {@link DeferredResult} will either be
     * completed with the result of the task or failed with the reason for the failure.
     */
    public DeferredResult<T> execute() {
        if (executed.getAndSet(true)) {
            preventRetries();
            RetriableTaskException exception = new RetriableTaskException(
                    String.format("Cannot execute task '%s': task has already been executed",
                            taskConfiguration.getTaskId()));
            log(Level.WARNING, Utils.toString(exception));
            return DeferredResult.failed(exception);
        }

        return executeRetriableTask(0, taskConfiguration);
    }

    private DeferredResult<T> executeRetriableTask(int retriesCount,
            RetriableTaskConfiguration<T> taskConfig) {

        DeferredResult<T> deferredResult = new DeferredResult<>();

        try {
            // try to execute the task
            taskConfig.getTaskFunction().apply(this).whenComplete((taskResult, ex) -> {
                if (ex == null) {
                    // task returned a result successfully.
                    log(Level.INFO, "Task '%s' completed successfully.", taskConfig.getTaskId());
                    deferredResult.complete(taskResult);
                    return;
                }

                log(Level.FINE, "Task '%s' failed to produce result: %s", taskConfig.getTaskId(),
                        Utils.toString(ex));
                // task evaluated but failed. Retry if possible
                scheduleTaskRetry(ex, retriesCount, taskConfig)
                                .whenComplete((result, failure) -> {
                                    if (failure != null) {
                                        deferredResult.fail(stripCompletionException(failure));
                                    } else {
                                        deferredResult.complete(result);
                                    }
                                });
            });
        } catch (Throwable ex) {
            // task failed to evaluate. Retry if possible
            log(Level.FINE, "Task '%s' failed to evaluate task function: %s",
                    taskConfig.getTaskId(), Utils.toString(ex));
            scheduleTaskRetry(ex, retriesCount, taskConfig)
                            .whenComplete((result, failure) -> {
                                if (failure != null) {
                                    deferredResult.fail(stripCompletionException(failure));
                                } else {
                                    deferredResult.complete(result);
                                }
                            });
        }

        return deferredResult;
    }

    private DeferredResult<T> scheduleTaskRetry(Throwable ex, int retriesCount,
            RetriableTaskConfiguration<T> taskConfig) {

        String taskId = taskConfig.getTaskId();
        long maxRetries = taskConfig.getMaxRetries();

        DeferredResult<T> deferredResult = new DeferredResult<>();

        // check if retries are not prevented
        if (areRetriesPrevented()) {
            log(Level.WARNING, "Cannot retry task '%s', retries are prevented: %s", taskId,
                    ex.getMessage());
            return DeferredResult.failed(new RetriableTaskException(
                    String.format(ERROR_MESSAGE_FORMAT_RETRIES_PREVENTED, ex.getMessage()), ex));
        }

        // check if additional retries are available
        if (retriesCount >= maxRetries) {
            log(Level.WARNING,
                    "Cannot retry task '%s', the maximum number of retries (%d) has been reached: %s",
                    taskId, maxRetries, ex.getMessage());
            return DeferredResult.failed(new RetriableTaskException(String.format(
                    ERROR_MESSAGE_FORMAT_MAXIMUM_NUMBER_OF_RETRIES_EXCEEDED, ex.getMessage()), ex));
        }

        // calculate the delay before the retry
        long taskDelay = calculateNextRetry(retriesCount, taskConfig);
        log(Level.WARNING,
                "Will retry task '%s' in %d %s (retry %d of %d). Failure: %s", taskId, taskDelay,
                taskConfig.getRetryDelaysTimeUnit().toString(), retriesCount + 1, maxRetries,
                ex.getMessage());

        // prepare and schedule the retry
        Runnable taskRetry = () -> {
            executeRetriableTask(retriesCount + 1, taskConfig)
                    .whenComplete((taskResult, failure) -> {
                        if (failure != null) {
                            deferredResult.fail(stripCompletionException(failure));
                        } else {
                            deferredResult.complete(taskResult);
                        }
                    });
        };

        if (taskDelay > 0) {
            // if delay is needed, schedule the task execution for later
            taskConfig.getServiceHost()
                    .schedule(taskRetry, taskDelay, taskConfig.getRetryDelaysTimeUnit());
        } else {
            // otherwise just run it now
            taskRetry.run();
        }

        return deferredResult;
    }

    private long calculateNextRetry(int retriesCount, RetriableTaskConfiguration<T> taskConfig) {
        List<Long> retryDelays = taskConfig.getRetryDelays();
        if (retryDelays == null) {
            return 0;
        }

        // get the next retry or repeat the last
        // one if there are no more delays defined
        return retryDelays.get(Math.min(retriesCount, retryDelays.size() - 1));
    }

    protected static Throwable stripCompletionException(Throwable originalException) {
        if (originalException == null) {
            return null;
        }

        if (!(originalException instanceof CompletionException)) {
            return originalException;
        }

        Throwable cause = stripCompletionException(originalException.getCause());
        return cause != null ? cause : originalException;
    }

    private void log(Level level, String fmt, Object... args) {
        if (taskConfiguration != null && taskConfiguration.getServiceHost() != null) {
            taskConfiguration.getServiceHost().log(level, fmt, args);
        } else {
            Logger.getLogger(getClass().getName()).log(level, fmt, args);
        }
    }

}
