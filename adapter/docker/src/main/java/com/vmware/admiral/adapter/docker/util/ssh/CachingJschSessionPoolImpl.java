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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import com.vmware.admiral.common.util.ConcurrentUtil;
import com.vmware.xenon.common.Utils;

/**
 * A JschSessionPool implementation that keeps sessions open for a limited time after a task is
 * complete
 *
 * This implementation only allows one active Session instance per SessionParams (host, port, user,
 * etc.)
 */
public class CachingJschSessionPoolImpl extends JSchSessionPoolImpl {
    private static final Logger logger = Logger.getLogger(CachingJschSessionPoolImpl.class
            .getName());

    /**
     * Grace period to keep session open after the last task using it is finished in case additional
     * tasks need the same session
     */
    private static final long KEEP_SESSION_MILLIS = Long.getLong(
            "adapter.docker.ssh.grace.millis", 30000);

    /**
     * Time between session expiration check for a command to complete
     */
    private static final long POLL_INTERVAL_MILLIS = Long.getLong(
            "adapter.docker.ssh.session.expiration.poll.millis", 200);

    /**
     * Command to execute on session to test their validity
     */
    private static final String SESSION_TEST_COMMAND = System.getProperty(
            "adapter.docker.ssh.test.command", "true");

    /**
     * Maximum number of channels allowed per session
     *
     * This should be equal or less than the number of maximum sessions allowed by the SSH server
     * (which may have sessions not originated from this client)
     */
    private static final int MAX_CHANNELS_PER_SESSION = Integer.getInteger(
            "adapter.docker.ssh.channels.per.session", 8);

    private static class SessionInfo {
        /**
         * Number of tasks using the Session
         */
        private final AtomicInteger refCount;

        /**
         * Cleanup code that is already scheduled, held so it can be cancelled in case the grace
         * period needs to be extended, for example
         */
        private Future<?> scheduledCloser = null;

        private final SessionParams sessionParams;
        private long lastUsedTimeMicros;

        public SessionInfo(SessionParams sessionParams, AtomicInteger refCount) {
            this.sessionParams = sessionParams;
            this.refCount = refCount;
            this.lastUsedTimeMicros = Utils.getNowMicrosUtc();
        }
    }

    /**
     * used for scheduling the delayed close of sessions after a grace period
     */
    private final BiFunction<Runnable, Long, Future<?>> scheduler;

    /**
     * Mapping from SessionParams to an existing SessionInfo
     */
    private final Map<SessionParams, Session> cachedSessions = new HashMap<>();

    /**
     * Session information for cached sessions
     */
    private final Map<Session, SessionInfo> sessionInfoMap = new HashMap<>();

    /**
     * Coarse lock for all operations on the pool due to the use of multiple data structures that
     * need to be kept in sync
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     *
     * @param scheduler
     *            used for scheduling the delayed close of sessions after a grace period
     */
    public CachingJschSessionPoolImpl(BiFunction<Runnable, Long, Future<?>> scheduler) {
        this.scheduler = scheduler;
    }

    public int getActiveChannelsPerSession(SessionParams sessionParams) {
        try {
            mainLock.lock();
            Session cachedSession = cachedSessions.get(sessionParams);
            if (cachedSession == null) {
                return 0;
            }
            SessionInfo sessionInfo = sessionInfoMap.get(cachedSession);
            if (sessionInfo == null) {
                return 0;
            }

            return sessionInfo.refCount.get();
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    public Session getSession(SessionParams sessionParams) {
        try {
            mainLock.lock();

            logger.finest("Cached sessions count: " + cachedSessions.size());

            // check if a cached session is available
            Session cachedSession = cachedSessions.get(sessionParams);
            if (validateCachedSession(cachedSession)) {
                SessionInfo sessionInfo = sessionInfoMap.get(cachedSession);

                // increase the ref count for the session
                // try to avoid "no more sessions" error but this is not guaranteed since the server
                // may have other sessions opened
                boolean success = ConcurrentUtil.incrementIfLessThan(sessionInfo.refCount,
                        MAX_CHANNELS_PER_SESSION);

                if (!success) {
                    logger.warning("Maximum number of channels per session exceeded");
                    throw new MaximumNumberOfChannelsPerSessionException();
                }

                logger.fine(String.format("Reusing cached session (refcount=%d): %s",
                        sessionInfo.refCount.get() - 1, cachedSession));
                sessionInfo.lastUsedTimeMicros = Utils.getNowMicrosUtc();
                return cachedSession;
            }

            // no cached session found - create a new one
            Session newSession = super.getSession(sessionParams);

            SessionInfo sessionInfo = new SessionInfo(sessionParams, new AtomicInteger(1));
            sessionInfoMap.put(newSession, sessionInfo);
            cachedSessions.put(sessionParams, newSession);

            return newSession;

        } finally {
            mainLock.unlock();
        }
    }

    @Override
    public void closeSession(Session finishedSession) {
        // delay the actual disconnect for a while in case another task needs the same session
        try {
            mainLock.lock();

            SessionInfo sessionInfo = sessionInfoMap.get(finishedSession);
            sessionInfo.lastUsedTimeMicros = Utils.getNowMicrosUtc();

            // if a previous closer was already scheduled, cancel it since it's going to be
            // rescheduled (if we can unschedule the previous closer we can decrement the refcount
            // immediately, otherwise we must not decrement to prevent premature closure)
            Future<?> existingScheduleCloser = sessionInfo.scheduledCloser;
            if (existingScheduleCloser != null && existingScheduleCloser.cancel(false)) {
                int current = sessionInfo.refCount.decrementAndGet();
                logger.finest(String.format("Canceled session closer (refcount=%d): %s",
                        current, finishedSession));
            }

            if (!finishedSession.isConnected()) {
                // the session is already disconnected so run the cleanup immediately
                logger.fine("Session already disconnected: " + finishedSession);
                closeSessionImpl(finishedSession);
            } else {
                closeIfSessionExpired(sessionInfo, finishedSession);
            }

        } finally {
            mainLock.unlock();
        }
    }

    private void closeIfSessionExpired(SessionInfo sessionInfo, Session finishedSession) {
        if (!finishedSession.isConnected()
                || Utils.getNowMicrosUtc() > sessionInfo.lastUsedTimeMicros
                        + TimeUnit.MILLISECONDS.toMicros(KEEP_SESSION_MILLIS)) {
            closeSessionImpl(finishedSession);
            return;
        }

        sessionInfo.scheduledCloser = scheduler.apply(
                () -> closeIfSessionExpired(sessionInfo, finishedSession),
                POLL_INTERVAL_MILLIS);
    }

    @Override
    public void shutdown() {
        try {
            mainLock.lock();

            for (SessionInfo sessionInfo : sessionInfoMap.values()) {
                if (sessionInfo.scheduledCloser != null) {
                    sessionInfo.scheduledCloser.cancel(true);
                }
            }

            super.shutdown();

            cachedSessions.clear();
            sessionInfoMap.clear();

        } finally {
            mainLock.unlock();
        }
        super.shutdown();
    }

    /**
     * close the session
     *
     * @param session
     */
    private void closeSessionImpl(Session session) {
        try {
            mainLock.lock();
            SessionInfo sessionInfo = sessionInfoMap.get(session);
            if (sessionInfo != null) {
                int current = sessionInfo.refCount.get();
                if (current <= 0) {
                    if (sessionInfo.scheduledCloser != null) {
                        sessionInfo.scheduledCloser.cancel(false);
                    }

                    sessionInfoMap.remove(session);
                    cachedSessions.remove(sessionInfo.sessionParams);

                    super.closeSession(session);

                } else {
                    logger.finest(String.format(
                            "Session is still in use (refcount=%d), so not closing it: %s",
                            current, session));
                }
            }

        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Check that a session is valid
     *
     * @return true if the session is valid, false otherwise
     */
    private boolean validateCachedSession(Session cachedSession) {
        if (cachedSession != null) {
            if (cachedSession.isConnected()) {
                try {
                    if (SESSION_TEST_COMMAND != null) {
                        logger.finest("Executing session test command: " + cachedSession);
                        ChannelExec testChannel = (ChannelExec) cachedSession.openChannel("exec");
                        testChannel.setCommand(SESSION_TEST_COMMAND);
                        testChannel.connect();
                        testChannel.disconnect();
                    }
                    return true;

                } catch (JSchException x) {
                    logger.info("Failure running the session validation command: " + x.getMessage());
                }
            }

            closeSessionImpl(cachedSession);
        }
        return false;
    }

    public static class MaximumNumberOfChannelsPerSessionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MaximumNumberOfChannelsPerSessionException() {
            super("Maximum number of channels per session exceeded");
        }
    }
}
