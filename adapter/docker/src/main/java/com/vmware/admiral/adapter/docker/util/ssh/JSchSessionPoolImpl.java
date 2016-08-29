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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.jcraft.jsch.Session;

import com.vmware.admiral.common.util.ConcurrentUtil;

/**
 * Simple JSchSessionPool implementation that limits the number of active session and simply rejects
 * additional sessions by throwing an exception (no queuing of tasks)
 */
public class JSchSessionPoolImpl extends AbstractJSchSessionPool {
    private static final Logger logger = Logger.getLogger(JSchSessionPoolImpl.class.getName());

    private static final int MAX_CONCURRENT_SESSIONS = Integer.getInteger(
            "adapter.docker.ssh.sessions", 8);

    private final Queue<Session> activeSessions = new ConcurrentLinkedQueue<>();

    private final AtomicInteger activeSessionCount = new AtomicInteger(0);

    /**
     * Start or queue a new task
     *
     * @param newTask
     */
    @Override
    public Session getSession(SessionParams sessionParams) {
        if (!ConcurrentUtil.incrementIfLessThan(activeSessionCount, MAX_CONCURRENT_SESSIONS)) {
            throw new IllegalStateException("Maximum number of active sessions reached");
        }

        logger.finest("Creating a new Session: " + sessionParams);
        Session newSession = null;
        try {
            newSession = createNewSession(sessionParams);
        } catch (RuntimeException e) {
            logger.finest("Could not create a new session: " + e.getMessage());
            activeSessionCount.decrementAndGet();
            throw e;
        }

        activeSessions.add(newSession);
        logger.fine("Created a new Session: " + newSession);

        if (activeSessionCount.get() == Integer.MAX_VALUE) {
            // the pool was shut down during this method so make sure the new session was not missed
            // from cleanup
            safeDisconnect(newSession);
            throw new IllegalStateException("Pool was shut down");
        }

        return newSession;
    }

    /**
     * Signal completion of a task (called by the task itself upon completion
     *
     * @param completedTask
     */
    @Override
    public void closeSession(Session finishedSession) {
        logger.fine("Closing session: " + finishedSession);
        safeDisconnect(finishedSession);

        if (activeSessions.remove(finishedSession)) {
            activeSessionCount.decrementAndGet();

        } else {
            logger.finest("Session has already been removed from the active set: "
                    + finishedSession);
        }
    }

    @Override
    public void shutdown() {
        // prevent additional sessions from being created
        activeSessionCount.set(Integer.MAX_VALUE);

        Session session = null;
        while (((session = activeSessions.poll()) != null)) {
            safeDisconnect(session);
        }
    }

}
