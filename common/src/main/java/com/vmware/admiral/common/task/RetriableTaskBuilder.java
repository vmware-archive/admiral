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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;

/**
 * A builder for creation and execution of {@link RetriableTask}s.
 *
 * @param <T>
 *            the expected result for the {@link RetriableTask} that is being built.
 */
public class RetriableTaskBuilder<T> {

    /**
     * A configuration for {@link RetriableTask}s.
     */
    public static class RetriableTaskConfiguration<T> {

        public static final int DEFAULT_MAX_RETRIES = 3;
        public static final List<Long> DEFAULT_RETRY_DELAYS = Collections
                .unmodifiableList(Arrays.asList(3L, 5L, 10L));
        public static final TimeUnit DEFAULT_DELAYS_TIME_UNIT = TimeUnit.SECONDS;

        private String taskId;
        private ServiceHost serviceHost;

        // if maxRetries is bigger than the size of retryDelays, the last delay will be repeated.
        private Integer maxRetries;
        // an unmodifiable list with the delays to be used in sequence
        private List<Long> retryDelays;
        private TimeUnit retryDelaysTimeUnit;

        private Function<RetriableTask<T>, DeferredResult<T>> taskFunction;

        private RetriableTaskConfiguration() {
            // constructor is hidden on purpose, the builder is
            // supposed to be used to create and manage the configuration
        }

        public String getTaskId() {
            return taskId;
        }

        public ServiceHost getServiceHost() {
            return serviceHost;
        }

        public Integer getMaxRetries() {
            return maxRetries;
        }

        public List<Long> getRetryDelays() {
            return retryDelays;
        }

        public TimeUnit getRetryDelaysTimeUnit() {
            return retryDelaysTimeUnit;
        }

        public Function<RetriableTask<T>, DeferredResult<T>> getTaskFunction() {
            return taskFunction;
        }

    }

    private RetriableTaskConfiguration<T> taskConfiguration;

    private boolean taskExecuted;

    public RetriableTaskBuilder(String taskId) {
        taskConfiguration = new RetriableTaskConfiguration<>();

        withTaskId(taskId)
                .withMaximumRetries(RetriableTaskConfiguration.DEFAULT_MAX_RETRIES)
                .withRetryDelays(RetriableTaskConfiguration.DEFAULT_RETRY_DELAYS)
                .withRetryDelaysTimeUnit(RetriableTaskConfiguration.DEFAULT_DELAYS_TIME_UNIT);
    }

    /**
     * Sets the ID of the task.
     */
    public RetriableTaskBuilder<T> withTaskId(String taskId) {
        assertTaskNotExecuted();
        AssertUtil.assertNotNullOrEmpty(taskId, "taskId");
        this.taskConfiguration.taskId = taskId;
        return this;
    }

    /**
     * Sets the {@link ServiceHost} that will be used for scheduling retries.
     */
    public RetriableTaskBuilder<T> withServiceHost(ServiceHost serviceHost) {
        assertTaskNotExecuted();
        AssertUtil.assertNotNull(serviceHost, "serviceHost");
        this.taskConfiguration.serviceHost = serviceHost;
        return this;
    }

    /**
     * Sets the maximum number of retries for the task. If 0, the task will not be retried. If
     * <code>null</code>, the number of retry delays will be implied. It is OK to specify for
     * maxRetries a number greater than the number of specified delays. The last delay will be
     * repeated in this case.
     */
    public RetriableTaskBuilder<T> withMaximumRetries(Integer maxRetries) {
        assertTaskNotExecuted();
        if (maxRetries != null) {
            AssertUtil.assertTrue(maxRetries >= 0, "maxRetries must be a non-negative integer");
        }
        this.taskConfiguration.maxRetries = maxRetries;
        return this;
    }

    /**
     * Sets the retry delays for this task. They will be used in sequence until the maximum number
     * of retries are exceeded. If the maximum number of retries is bigger than the number of
     * delays, the last delay will be repeated.
     */
    public RetriableTaskBuilder<T> withRetryDelays(Long... retryDelays) {
        assertTaskNotExecuted();
        AssertUtil.assertNotNull(retryDelays, "retryDelays");
        AssertUtil.assertTrue(retryDelays.length > 0,
                "retryDelays must contain at least one delay declaration");
        AssertUtil.assertTrue(Stream.of(retryDelays).noneMatch(l -> l == null || l < 0),
                "retryDelays must contain only non-negative integers");
        this.taskConfiguration.retryDelays = Collections
                .unmodifiableList(Arrays.asList(retryDelays));
        return this;
    }

    /**
     * Sets the retry delays for this task. They will be used in sequence until the maximum number
     * of retries are exceeded. If the maximum number of retries is bigger than the number of
     * delays, the last delay will be repeated.
     */
    public RetriableTaskBuilder<T> withRetryDelays(List<Long> retryDelays) {
        assertTaskNotExecuted();
        AssertUtil.assertNotNull(retryDelays, "retryDelays");
        AssertUtil.assertTrue(retryDelays.size() > 0,
                "retryDelays must contain at least one delay declaration");
        AssertUtil.assertTrue(retryDelays.stream().noneMatch(l -> l == null || l < 0),
                "retryDelays must contain only non-negative integers");
        this.taskConfiguration.retryDelays = Collections.unmodifiableList(retryDelays);
        return this;
    }

    /**
     * Sets the {@link TimeUnit} of the retry delays.
     */
    public RetriableTaskBuilder<T> withRetryDelaysTimeUnit(TimeUnit delaysTimeUnit) {
        assertTaskNotExecuted();
        AssertUtil.assertNotNull(delaysTimeUnit, "delaysTimeUnit");
        this.taskConfiguration.retryDelaysTimeUnit = delaysTimeUnit;
        return this;
    }

    /**
     * Sets the task function for this task. This is a {@link Function} that defines the task
     * execution. When executed, it will receive a reference to the {@link RetriableTask} instance.
     * This instance can be used for example to prevent task retries in case of a non-retriable
     * failure. If the the {@link DeferredResult} returned by this function completes successfully,
     * the task will be completed with its result. If that {@link DeferredResult} fails or an
     * exception is thrown during the evaluation of the function, the task will be retried as long
     * as retries are not prevented and the maximum number of retries is not exceeded.
     */
    public RetriableTaskBuilder<T> withTaskFunction(
            Function<RetriableTask<T>, DeferredResult<T>> taskFunction) {
        assertTaskNotExecuted();
        AssertUtil.assertNotNull(taskFunction, "taskBody");
        this.taskConfiguration.taskFunction = taskFunction;
        return this;
    }

    /**
     * Execute the task.
     *
     * @return a {@link DeferredResult} that will either complete with the result of the task or
     *         fail with the reason for the failure.
     */
    public DeferredResult<T> execute() {
        try {
            validateTaskAndCalculateOptionalFields();
        } catch (Throwable e) {
            log(Level.WARNING, "Could not execute retriable task '%s': %s.",
                    taskConfiguration.taskId, Utils.toString(e));
            return DeferredResult.failed(e);
        }

        this.taskExecuted = true;
        return new RetriableTask<>(taskConfiguration).execute();
    }

    private void assertTaskNotExecuted() {
        if (taskExecuted) {
            throw new IllegalStateException(
                    String.format("Task '%s' is already executed.", taskConfiguration.getTaskId()));
        }
    }

    private void validateTaskAndCalculateOptionalFields() {
        assertTaskNotExecuted();
        AssertUtil.assertNotNullOrEmpty(taskConfiguration.taskId, "taskId");
        AssertUtil.assertNotNull(taskConfiguration.serviceHost, "serviceHost");
        AssertUtil.assertNotNull(taskConfiguration.taskFunction, "taskBody");
        AssertUtil.assertNotNull(taskConfiguration.retryDelays, "retryDelays");
        AssertUtil.assertNotNull(taskConfiguration.retryDelaysTimeUnit, "retryDelaysTimeUnit");

        if (taskConfiguration.maxRetries == null) {
            taskConfiguration.maxRetries = taskConfiguration.retryDelays.size();
        } else {
            AssertUtil.assertTrue(taskConfiguration.maxRetries >= 0,
                    "maxRetries must be a non-negative integer");
        }
    }

    private void log(Level level, String fmt, Object... args) {
        if (taskConfiguration != null && taskConfiguration.getServiceHost() != null) {
            taskConfiguration.getServiceHost().log(level, fmt, args);
        } else {
            Logger.getLogger(getClass().getName()).log(level, fmt, args);
        }
    }

}