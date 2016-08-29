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

package com.vmware.admiral.adapter.docker.util.ssh;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.vmware.admiral.adapter.docker.util.ssh.CachingJschSessionPoolImpl.MaximumNumberOfChannelsPerSessionException;

/**
 * SSH queue task executor to limit the number of active task per host in order to keep the open
 * session and channels under control.
 */
public class SshQueueExecutor {
    private static final Logger logger = Logger.getLogger(SshQueueExecutor.class.getName());
    /**  Time between checks for a command to complete  */
    private static final long POLL_INTERVAL_MILLIS = Long.getLong(
            "cmp.adapter.docker.ssh.poll.millis", 500);

    /**  Time between checks for a command to complete  */
    private static final int MAX_NUMBER_OF_ACTIVE_TASK_PER_HOST = Integer.getInteger(
            "cmp.adapter.docker.ssh.max.tasks.per.host", 8);

    private final BiFunction<Runnable, Long, Future<?>> pollingScheduler;
    private final JSchSessionPool sessionPool;

    private final Map<SessionParams, AtomicInteger> activeTaskCountPerHost = new ConcurrentHashMap<>();
    private final Map<SessionParams, Queue<SshExecTask>> taskQueuePerHost = new ConcurrentHashMap<>();
    private final Map<SessionParams, AtomicBoolean> pollingTaskPerHost = new ConcurrentHashMap<>();

    public SshQueueExecutor(BiFunction<Runnable, Long, Future<?>> pollingScheduler,
            JSchSessionPool sessionPool) {
        this.pollingScheduler = pollingScheduler;
        this.sessionPool = sessionPool;
    }

    public synchronized void submit(SshExecTask task, SessionParams sessionParams) {
        if (!activeTaskCountPerHost.containsKey(sessionParams)) {
            activeTaskCountPerHost.put(sessionParams, new AtomicInteger());
        }
        if (!taskQueuePerHost.containsKey(sessionParams)) {
            taskQueuePerHost.put(sessionParams, new LinkedBlockingQueue<>());
        }
        if (!pollingTaskPerHost.containsKey(sessionParams)) {
            pollingTaskPerHost.put(sessionParams, new AtomicBoolean());
        }

        taskQueuePerHost.get(sessionParams).add(task);
        if (!pollingTaskPerHost.get(sessionParams).get()) {
            poll(sessionParams);
        }
    }

    private synchronized void poll(SessionParams sessionParams) {
        AtomicInteger count = activeTaskCountPerHost.get(sessionParams);
        AtomicBoolean scheduled = pollingTaskPerHost.get(sessionParams);
        if (count != null && count.get() > MAX_NUMBER_OF_ACTIVE_TASK_PER_HOST) {
            if (scheduled != null && scheduled.compareAndSet(false, true)) {
                logger.fine("scheduling when max number per hosts");
                pollingScheduler.apply(() -> {
                    if (scheduled != null) {
                        scheduled.set(false);
                    }
                    poll(sessionParams);
                }, POLL_INTERVAL_MILLIS);
            }
            return;
        }

        Queue<SshExecTask> queue = taskQueuePerHost.get(sessionParams);

        if (queue != null && !queue.isEmpty()) {
            SshExecTask task = queue.poll();
            Consumer<SshExecTask> completionHandler = task.getCompletionHandler();
            if (count != null) {
                count.incrementAndGet();
            }

            Consumer<SshExecTask> counterDecrement = (t) -> {
                if (count != null) {
                    count.decrementAndGet();
                }
            };

            task.withCompletionHandler(counterDecrement.andThen(completionHandler));
            try {
                task.run(sessionParams, sessionPool);
            } catch (MaximumNumberOfChannelsPerSessionException e) {
                task.withCompletionHandler(completionHandler);
                if (count != null) {
                    count.decrementAndGet();
                }
                if (task.errorCount.incrementAndGet() <= 3) {
                    submit(task, sessionParams);
                    return;
                } else {
                    throw e;
                }
            } catch (RuntimeException e) {
                if (count != null) {
                    count.decrementAndGet();
                }
                throw e;
            }
        }

        if ((queue == null || queue.isEmpty())
                && (count == null || count.get() <= 0)
                && (scheduled == null || !scheduled.get())) {
            taskQueuePerHost.remove(sessionParams);
            activeTaskCountPerHost.remove(sessionParams);
            pollingTaskPerHost.remove(sessionParams);
        }

        //get the other tasks from the queue until empty or hit max concurrent executions
        if (queue != null && !queue.isEmpty()) {
            poll(sessionParams);
        }
    }
}
