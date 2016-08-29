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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import com.vmware.admiral.common.util.AssertUtil;

/**
 * Contains context for a single command executed using JSch ChannelExec
 */
public class SshExecTask {
    private static Logger logger = Logger.getLogger(SshExecTask.class.getName());

    /**
     * Time between checks for a command to complete
     */
    private static final long POLL_INTERVAL_MILLIS = Long.getLong(
            "adapter.docker.ssh.poll.millis", 500);

    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private String command;
    private final BiFunction<Runnable, Long, Future<?>> pollingScheduler;
    private Consumer<SshExecTask> completionHandler;
    private ChannelExec channel;
    private boolean cancelled = false;
    final AtomicInteger errorCount;

    /**
     * hold the last scheduled poll in case it needs to be cancelled
     */
    private Future<?> lastScheduledPoll = null;

    public SshExecTask(BiFunction<Runnable, Long, Future<?>> pollingScheduler) {
        AssertUtil.assertNotNull(pollingScheduler, "pollingScheduler");
        this.pollingScheduler = pollingScheduler;
        this.errorCount = new AtomicInteger();
    }

    public SshExecTask withCommand(String command) {
        this.command = command;
        return this;
    }

    public SshExecTask withInput(InputStream in) {
        this.in = in;
        return this;
    }

    public SshExecTask withOutput(OutputStream out) {
        this.out = out;
        return this;
    }

    public SshExecTask withError(OutputStream err) {
        this.err = err;
        return this;
    }

    public SshExecTask withCompletionHandler(Consumer<SshExecTask> completionHandler) {
        this.completionHandler = completionHandler;
        return this;
    }

    public Consumer<SshExecTask> getCompletionHandler() {
        return this.completionHandler;
    }

    public void run(SessionParams sessionParams, JSchSessionPool sessionPool) {
        AssertUtil.assertNotNull(sessionParams, "sessionParams");
        AssertUtil.assertNotNull(sessionParams, "sessionParams");
        if (in == null) {
            // replace with an empty input stream
            in = new ByteArrayInputStream(new byte[0]);
        }

        Consumer<SshExecTask> handler = this.completionHandler;
        Session session = sessionPool.getSession(sessionParams);
        Consumer<SshExecTask> sessionCloser = (t) -> sessionPool.closeSession(session);
        this.completionHandler = sessionCloser.andThen(handler);

        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(in);
            channel.setErrStream(err);
            channel.setOutputStream(out);
            channel.connect();

            pollCompletion();

        } catch (JSchException x) {
            if ("channel is not opened.".equals(x.getMessage())
                    && errorCount.incrementAndGet() <= 3) {
                this.completionHandler = handler;
                run(sessionParams, sessionPool);
            } else {
                logger.warning("Error running command: " + x.getMessage());
                completionHandler.accept(this);
            }
        }
    }

    public void cancel() {
        logger.warning("Canceling task: " + command);
        cancelled = true;

        // attempt to cancel schedule poll although it will be ignored anyway if fired
        if (lastScheduledPoll != null) {
            lastScheduledPoll.cancel(false);
        }

        finished();
    }

    public int getExitStatus() {
        return channel.getExitStatus();
    }

    /**
     * check if the command finished executing and invoke the completion handler, or schedule a
     * future check if not finished
     */
    private void pollCompletion() {
        if (cancelled) {
            // we might get in case the scheduled poll cancellation failed
            logger.finest("Aborting pollCompletion for cancelled task: " + command);
            return;
        }

        if (channel.isClosed()) {
            logger.finest("Command is complete: " + command);
            finished();

        } else {
            logger.finest("Command is running: " + command);
            lastScheduledPoll = pollingScheduler
                    .apply(() -> pollCompletion(), POLL_INTERVAL_MILLIS);
        }
    }

    private void finished() {
        safeDisconnect(channel);

        if (completionHandler != null) {
            completionHandler.accept(this);
        }
    }

    /**
     * ChannelExec's disconnect method catches all exceptions, so this is just in case it changes in
     * the future
     *
     * @param channel
     */
    private void safeDisconnect(ChannelExec channel) {
        try {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }

        } catch (Exception x) {
            logger.warning("Failed to disconnect channel: " + x.getMessage());
        }
    }

}
