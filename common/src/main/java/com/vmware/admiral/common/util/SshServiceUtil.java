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

package com.vmware.admiral.common.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Logger;

import net.schmizz.sshj.SSHClient;

import com.vmware.admiral.common.security.EncryptionUtils;
import com.vmware.admiral.common.util.SshUtil.AsyncResult;
import com.vmware.admiral.common.util.SshUtil.ConsumedResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Ssh tools optimized for Xenon services usage. All operations execute asynchronously and poll for
 * result, do not hold an open channel, instead the command is executes as background process and
 * persists the output in temporary files until consumed. Such files are later scrapped by a gc.
 */
public class SshServiceUtil {
    private static Logger logger = Logger.getLogger(SshServiceUtil.class
            .getName());

    public static final String SSH_OP_OUT_PREFIX = "ssh-op-out";
    public static final String SSH_OP_ERR_PREFIX = "ssh-op-err";
    public static final String SSH_OP_EXIT_CODE_PREFIX = "ssh-op-exitCode";

    /*
     * Depending on the load, lowering this may result in better performance for a single operation,
     * but it will result in higher usage of ssh sessions as well. Change MaxSessions ssh property
     * accordingly.
     */
    public static final int SSH_POLL_MAX_DELAY_SECONDS = Integer.parseInt(
            System.getProperty("ssh.poll.max_delay", "60"));
    public static final int SSH_OPERATION_TIMEOUT_SHORT = Integer.parseInt(
            System.getProperty("ssh.operation.timeout.short", "15"));
    public static final int SSH_OPERATION_TIMEOUT_LONG = Integer.parseInt(
            System.getProperty("ssh.operation.timeout.long", "300"));
    public static final int SSH_GC_INTERVAL = Integer.parseInt(
            System.getProperty("ssh.gc.interval", "300"));

    private ServiceHost host;

    private Map<String, SSHClient> clients = new HashMap<>();
    public Set<GcData> gcData = ConcurrentHashMap.newKeySet();

    public SshServiceUtil(ServiceHost host) {
        this.host = host;
        host.schedule(() -> gcAndReschedule(), SSH_GC_INTERVAL, TimeUnit.SECONDS);
    }

    public void exec(String hostname, AuthCredentialsServiceState credentials,
            String command,
            final CompletionHandler completionHandler, int timeout,
            TimeUnit unit) {
        exec(hostname, credentials, command, completionHandler, s -> s, timeout, unit);
    }

    public void exec(String hostname, AuthCredentialsServiceState credentials,
            String command,
            final CompletionHandler completionHandler, Function<String, ?> mapper, int timeout,
            TimeUnit unit) {
        String uuid = UUID.randomUUID().toString();
        String outStreamFile = "/tmp/" + SSH_OP_OUT_PREFIX + uuid + ".txt";
        String errStreamFile = "/tmp/" + SSH_OP_ERR_PREFIX + uuid + ".txt";
        String exitCodeFile = "/tmp/" + SSH_OP_EXIT_CODE_PREFIX + uuid + ".txt";

        SSHClient client;
        try {
            client = getSshClient(hostname, credentials);
        } catch (IOException e) {
            completionHandler.handle(null, e);
            return;
        }

        command = String.format(
                "nohup /bin/sh -c '%s > %s 2> %s ; echo $? > %s' &>/dev/null & echo $!",
                escape(command), outStreamFile, errStreamFile, exitCodeFile);

        AsyncResult result = SshUtil.asyncExec(client, command);
        // Not setting completion handler at constructor so I can use the state id in it (better
        // log)
        ExecutionState state = new ExecutionState(result, null, null, timeout,
                unit);
        state.handler = (op, failure) -> {
            if (failure != null) {
                // Failed to start the process
                completionHandler.handle(op, failure);
                return;
            }
            String pid = op.getBody(String.class).replace("\n", "");
            ProcessResultCollector prc = new ProcessResultCollector(pid, completionHandler, mapper);
            logger.fine(String.format(
                    "Starting SSH process result collector with id %s for pid %s, origin %s",
                    prc.id, pid, state.id));
            pollForProcessCompletion(hostname, credentials, outStreamFile,
                    errStreamFile, exitCodeFile, prc);
        };

        logger.fine(
                String.format("SSH execution %s started on %s: %s", state.id, hostname, command));
        handleExecInProgress(state);
    }

    public void upload(String hostname, AuthCredentialsServiceState credentials, byte[] data,
            String remoteFile, CompletionHandler completionHandler) {
        Future<Throwable> result = SshUtil.asyncUpload(hostname, credentials,
                new ByteArrayInputStream(data), remoteFile);

        handleScpInProgress(
                new ScpState(hostname, credentials, remoteFile, result, completionHandler,
                        SSH_OPERATION_TIMEOUT_LONG, TimeUnit.SECONDS));
    }

    public class ProcessResultCollector {
        private String id;
        private String pid;
        private CompletionHandler completionHandler;
        private Function<String, ?> mapper;

        // Result data
        private String out = null;
        private String err = null;
        private int exitCode = -99;

        public ProcessResultCollector(String pid, CompletionHandler completionHandler,
                Function<String, ?> mapper) {
            super();
            this.id = UUID.randomUUID().toString();
            this.pid = pid;
            this.completionHandler = completionHandler;
            this.mapper = mapper;
        }

        public void setOut(String out) {
            this.out = out;
            checkDone();
        }

        public void setErr(String err) {
            this.err = err;
            checkDone();
        }

        public void setExitCode(int exitCode) {
            this.exitCode = exitCode;
            checkDone();
        }

        public void checkDone() {
            if (out != null && err != null && exitCode != -99) {
                handleExecResult(id, exitCode, null, out, err, completionHandler, mapper);
            }
        }
    }

    public void pollForProcessCompletion(String hostname, AuthCredentialsServiceState credentials,
            String outStreamFile,
            String errStreamFile, String exitCodeFile, ProcessResultCollector prc) {

        SSHClient client;
        try {
            client = getSshClient(hostname, credentials);
        } catch (IOException e) {
            prc.completionHandler.handle(null, e);
            return;
        }

        String command = String.format("ps -aux | awk '{print $2}' | { grep -w %s || true; }",
                prc.pid);
        AsyncResult result = SshUtil.asyncExec(client, command);
        ExecutionState state = new ExecutionState(result, (op, failure) -> {
            if (failure != null) {
                prc.completionHandler.handle(op, failure);
                return;
            }
            String pidOrEmpty = op.getBody(String.class).replace("\n", "");
            if (pidOrEmpty.equals("")) {
                // Process exited, collect data

                AsyncResult outOp = SshUtil.asyncExec(client, "cat " + outStreamFile);
                handleExecInProgress(new ExecutionState(outOp, (op1, failure1) -> {
                    if (failure1 != null) {
                        prc.completionHandler.handle(null, failure1);
                        return;
                    }
                    prc.setOut(op1.getBody(String.class));
                }, null, SSH_OPERATION_TIMEOUT_SHORT, TimeUnit.SECONDS));
                AsyncResult errOp = SshUtil.asyncExec(client, "cat " + errStreamFile);
                handleExecInProgress(new ExecutionState(errOp, (op1, failure1) -> {
                    if (failure1 != null) {
                        prc.completionHandler.handle(null, failure1);
                        return;
                    }
                    prc.setErr(op1.getBody(String.class));
                }, null, SSH_OPERATION_TIMEOUT_SHORT, TimeUnit.SECONDS));
                AsyncResult exitCodeOp = SshUtil.asyncExec(client, "cat " + exitCodeFile);
                handleExecInProgress(new ExecutionState(exitCodeOp, (op1, failure1) -> {
                    if (failure1 != null) {
                        prc.completionHandler.handle(null, failure1);
                        return;
                    }
                    prc.setExitCode(Integer.parseInt(op1.getBody(String.class).replace("\n", "")));
                }, null, SSH_OPERATION_TIMEOUT_SHORT, TimeUnit.SECONDS));

                gcData.add(new GcData(hostname, credentials, outStreamFile));
                gcData.add(new GcData(hostname, credentials, errStreamFile));
                gcData.add(new GcData(hostname, credentials, exitCodeFile));
            } else {
                // Try again in 10 seconds
                logger.fine("Reschedule process polling with id " + prc.id);
                host.schedule(() -> {
                    pollForProcessCompletion(hostname, credentials, outStreamFile, errStreamFile,
                            exitCodeFile, prc);
                }, 10, TimeUnit.SECONDS);
            }
        }, null, SSH_OPERATION_TIMEOUT_SHORT, TimeUnit.SECONDS);
        logger.fine(
                String.format("SSH execution %s started on %s: %s", state.id, hostname, command));
        handleExecInProgress(state);
    }

    private static void handleExecResult(String id, int exitCode, Throwable error, String out,
            String err,
            CompletionHandler handler, Function<String, ?> mapper) {
        if (exitCode != 0) {
            if (error != null) { // SSH error, bad hostname, terminated
                                 // session,
                                 // etc.
                handler.handle(null, error);
            } else { // Executed, but bad status code
                String message = String.format(
                        "Error executing ssh command with id %s%nSTATUS: %s%nOUT=%s%nERR=%s",
                        id, exitCode, out, err);
                logger.info(message);
                Throwable t = new RuntimeException(message);
                handler.handle(null, t);
            }
        } else { // Operation completed successfully
            logger.fine(
                    String.format("Completed ssh command with id %s%nSTATUS: %s",
                            id,
                            exitCode));
            logger.fine(String.format("ID:%s%nOUT=%s%nERR=%s", id, out,
                    err));
            Object body = out;
            if (mapper != null) {
                body = mapper.apply(out);
            }
            Operation op = Operation.createPatch(null)
                    .setBody(body);
            handler.handle(op, null);
        }
    }

    private void handleExecInProgress(ExecutionState state) {
        if (state.result.isDone()) {
            ConsumedResult consumed = null;
            try {
                consumed = state.result.join().consume();
                handleExecResult(state.id, consumed.exitCode, consumed.error, consumed.out,
                        consumed.err, state.handler, state.mapper);
            } catch (IOException e) {
                state.handler.handle(null, e);
                return;
            }
        } else {
            int delay = state.nextDelay();
            if (state.isTimeout()) {
                state.handler.handle(null,
                        new TimeoutException("SSH operation " + state.id + " timed out"));
                return;
            }
            host.schedule(() -> {
                handleExecInProgress(state);
            }, delay, TimeUnit.SECONDS);
        }
    }

    private void handleScpInProgress(ScpState state) {
        if (state.result.isDone()) {
            Throwable error = null;
            try {
                error = state.result.get();
            } catch (InterruptedException | ExecutionException e) {
                try {
                    state.handler.handle(null, e);
                } catch (Exception e1) {
                    logger.info("Handler for SSH state " + state.id + " failed: "
                            + e1.getMessage());
                }
            }
            Operation op = Operation.createPatch(null)
                    .setBody(new ScpResult(state.hostname, state.credentials, state.target));
            try {
                state.handler.handle(op, error);
            } catch (Exception e) {
                logger.info("Handler for SSH state " + state.id + " failed.");
            }
        } else {
            int delay = state.nextDelay();
            if (state.isTimeout()) {
                state.handler.handle(null,
                        new TimeoutException("SCP operation " + state.id + " timed out"));
                return;
            }
            host.schedule(() -> {
                handleScpInProgress(state);
            }, delay, TimeUnit.SECONDS);
        }
    }

    private static class SshOperationState {
        public String id = UUID.randomUUID().toString();

        private int delay = 1;

        private long endTime;

        public SshOperationState(int timeout, TimeUnit unit) {
            endTime = unit.toMillis(timeout) + System.currentTimeMillis();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (id == null) {
                return false;
            }
            if (!(obj.getClass().equals(getClass()))) {
                return false;
            }

            return id.equals(((SshOperationState) obj).id);
        }

        @Override
        public int hashCode() {
            if (id == null) {
                return -1;
            }

            return id.hashCode();
        }

        public int nextDelay() {
            delay *= 2;
            if (delay > SSH_POLL_MAX_DELAY_SECONDS) {
                delay = SSH_POLL_MAX_DELAY_SECONDS;
            }
            return delay;
        }

        public boolean isTimeout() {
            if (System.currentTimeMillis() > endTime) {
                return true;
            }

            return false;
        }
    }

    public static class ExecutionState extends SshOperationState {
        public AsyncResult result;
        public CompletionHandler handler;
        public Function<String, ?> mapper;

        public ExecutionState(AsyncResult result, CompletionHandler handler,
                Function<String, ?> mapper, int timeout, TimeUnit unit) {
            super(timeout, unit);
            this.result = result;
            this.handler = handler;
            this.mapper = mapper;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (id == null) {
                return false;
            }
            if (!(obj.getClass().equals(getClass()))) {
                return false;
            }

            return id.equals(((ExecutionState) obj).id);
        }

        @Override
        public int hashCode() {
            if (id == null) {
                return -1;
            }

            return id.hashCode();
        }
    }

    public static class ScpState extends SshOperationState {
        public String hostname;
        public AuthCredentialsServiceState credentials;
        public String target;
        public Future<Throwable> result;
        public CompletionHandler handler;

        public ScpState(String hostname, AuthCredentialsServiceState credentials, String target,
                Future<Throwable> result, CompletionHandler handler,
                int timeout, TimeUnit unit) {
            super(timeout, unit);
            this.hostname = hostname;
            this.credentials = credentials;
            this.result = result;
            this.handler = handler;
            this.target = target;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (id == null) {
                return false;
            }
            if (!(obj.getClass().equals(getClass()))) {
                return false;
            }

            return id.equals(((ScpState) obj).id);
        }

        @Override
        public int hashCode() {
            if (id == null) {
                return -1;
            }

            return id.hashCode();
        }
    }

    public static class ScpResult {
        public String hostname;
        public AuthCredentialsServiceState credentials;
        public String target;

        public ScpResult(String hostname, AuthCredentialsServiceState credentials, String target) {
            this.hostname = hostname;
            this.credentials = credentials;
            this.target = target;
        }

        /**
         * Optionally, call this when you're done with the file and want it scrapped (frequently
         * used for temporary files)
         */
        public void scheduleForGc(SshServiceUtil sshServiceUtil) {
            sshServiceUtil.gcData.add(new GcData(hostname, credentials, target));
        }
    }

    public static class GcData {
        public String hostname;
        public AuthCredentialsServiceState credentials;
        public String filePath;

        public GcData(String hostname, AuthCredentialsServiceState credentials, String filePath) {
            this.hostname = hostname;
            this.credentials = credentials;
            this.filePath = filePath;
        }
    }

    private void gcAndReschedule() {
        try {
            gc();
        } catch (Exception e) {
            logger.info("Failed to SSH garbage collect: " + e.getMessage());
        }
        host.schedule(() -> gc(), SSH_GC_INTERVAL, TimeUnit.SECONDS);
    }

    public void gc() {
        logger.info("SSH gc triggered");
        Map<String, List<GcData>> tasks = new HashMap<>();
        List<GcData> toRemove = new ArrayList<>();

        // Sort files by hostname and credentials in tasks
        for (GcData data : gcData) {
            String id = getSshClientId(data.hostname, data.credentials);
            if (tasks.get(id) == null) {
                tasks.put(id, new ArrayList<GcData>());
            }
            tasks.get(id).add(data);
            toRemove.add(data);
        }
        gcData.removeAll(toRemove);

        // For each task, execute a gc command
        for (Collection<GcData> task : tasks.values()) {
            StringBuilder command = new StringBuilder("rm -f ");
            for (GcData data : task) {
                command.append(data.filePath + " ");
            }
            // All data in a task has the same ssh client id, hostname and credentials are identical
            GcData t = task.iterator().next();
            String hostname = t.hostname;
            AuthCredentialsServiceState credentials = t.credentials;
            SSHClient client = null;
            try {
                client = getSshClient(hostname, credentials);
            } catch (IOException e) {
                logger.info("SSH garbage collection failed: " + e.getMessage());
                return;
            }
            logger.fine(
                    String.format("Executing SSH garbage collection on %s: %s", hostname, command));
            AsyncResult result = SshUtil.asyncExec(client, command.toString());
            handleExecInProgress(new ExecutionState(result, (completedOp, failure) -> {
                if (failure != null) {
                    logger.info("SSH garbage collection failed: " + failure.getMessage());
                    return;
                }
                logger.info("SSH garbage collection for " + hostname + " success!");
            }, null, SSH_OPERATION_TIMEOUT_SHORT, TimeUnit.SECONDS));
        }
    }

    private SSHClient getSshClient(String hostname, AuthCredentialsServiceState creds)
            throws IOException {
        String id = getSshClientId(hostname, creds);
        synchronized (clients) {
            SSHClient client = clients.get(id);
            if (client == null || !client.isConnected()) {
                client = SshUtil.getDefaultSshClient(hostname, creds);
                clients.put(id, client);
                return client;
            }

            return client;
        }
    }

    private String getSshClientId(String hostname, AuthCredentialsServiceState creds) {
        return creds.userEmail + "@" + hostname + ":" + EncryptionUtils.decrypt(creds.privateKey);
    }

    private String escape(String command) {
        return command.replace("'", "'\"'\"'");
    }
}