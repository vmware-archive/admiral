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

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.BINDS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.CAP_ADD_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.CAP_DROP_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.CPU_SHARES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DEVICES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DNS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DNS_SEARCH_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.EXTRA_HOSTS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.LINKS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.MEMORY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.MEMORY_SWAP_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.NETWORK_MODE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.PID_MODE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.PRIVILEGED_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.PUBLISH_ALL;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_RETRIES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.VOLUMES_FROM_PROP_NAME;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
import java.util.function.UnaryOperator;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import net.schmizz.sshj.SSHClient;

import com.vmware.admiral.adapter.docker.util.DockerDevice;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.adapter.docker.util.PropertyToSwitchNameMapper;
import com.vmware.admiral.adapter.docker.util.ssh.CommandBuilder;
import com.vmware.admiral.adapter.docker.util.ssh.Mappers;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.common.util.SshUtil;
import com.vmware.admiral.common.util.SshUtil.AsyncResult;
import com.vmware.admiral.common.util.SshUtil.ConsumedResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Docker command executor implementation based on using SSH and executing the docker cli on the
 * remote end
 */
public class SshDockerAdapterCommandExecutorImpl implements DockerAdapterCommandExecutor, Mappers {

    private static final String STATS_TEMPLATE = FileUtil.getResourceAsString(
            "/stats-template.json",
            true);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private static final String SSH_OP_OUT_PREFIX = "ssh-op-out";
    private static final String SSH_OP_ERR_PREFIX = "ssh-op-err";
    private static final String SSH_OP_EXIT_CODE_PREFIX = "ssh-op-exitCode";

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

    private static Logger logger = Logger.getLogger(SshDockerAdapterCommandExecutorImpl.class
            .getName());

    private static final UnaryOperator<String> PROP_NAME_TO_LONG_SWITCH = new PropertyToSwitchNameMapper();

    private ServiceHost host;

    private Map<String, SSHClient> clients = new HashMap<String, SSHClient>();

    public Set<GcData> gcData = ConcurrentHashMap.newKeySet();

    public SshDockerAdapterCommandExecutorImpl(ServiceHost host) {
        this.host = host;
        host.schedule(() -> gcAndReschedule(), SSH_GC_INTERVAL, TimeUnit.SECONDS);
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
                    .setBody(state.target);
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

    public static class GcData {
        public String hostname;
        public AuthCredentialsServiceState credentials;
        public String filePath;

        public GcData(String hostname, AuthCredentialsServiceState credentials, String filePath) {
            this.hostname = hostname;
            this.credentials = credentials;
            this.filePath = filePath;
        }

        public GcData(CommandInput commandInput, String filePath) {
            this.hostname = commandInput.getDockerUri().getHost();
            this.credentials = commandInput.getCredentials();
            this.filePath = filePath;
        }
    }

    public void gc() {
        logger.info("SSH gc triggered");
        Map<String, List<GcData>> tasks = new HashMap<String, List<GcData>>();
        List<GcData> toRemove = new ArrayList<GcData>();

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

    private void gcAndReschedule() {
        try {
            gc();
        } catch (Exception e) {
            logger.info("Failed to SSH garbage collect: " + e.getMessage());
        }
        host.schedule(() -> gc(), SSH_GC_INTERVAL, TimeUnit.SECONDS);
    }

    private class SshOperationState {
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

    private class ExecutionState extends SshOperationState {
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

    private class ScpState extends SshOperationState {
        public String target;
        public Future<Throwable> result;
        public CompletionHandler handler;

        public ScpState(String target, Future<Throwable> result, CompletionHandler handler,
                int timeout, TimeUnit unit) {
            super(timeout, unit);
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

    @Override
    public void handleMaintenance(Operation post) {
        // add periodic maintenance logic here
    }

    /**
     * Execute a command asynchronously, no transformation of the output string
     *
     * @param commandInput
     * @param command
     * @param in
     * @param completionHandler
     */
    protected void execWithInput(CommandInput commandInput, String command,
            CompletionHandler completionHandler) {

        execWithInput(commandInput, command, completionHandler, (s) -> s,
                SSH_OPERATION_TIMEOUT_SHORT, TimeUnit.SECONDS);
    }

    protected void execWithInput(CommandInput commandInput, String command,
            CompletionHandler completionHandler, Function<String, ?> mapper) {

        execWithInput(commandInput, command, completionHandler, mapper, SSH_OPERATION_TIMEOUT_SHORT,
                TimeUnit.SECONDS);
    }

    /**
     * Execute a command asynchronously, transforming the output string using the given mapper
     * before setting the Operation body in the given CompletionHandler
     *
     * An error status of 0 would be considered as success and the output stream will be returned.
     *
     * Any other error status would be considered as an error and an exception will be thrown with
     * the error stream as the message.
     *
     * @param commandInput
     * @param command
     * @param completionHandler
     * @param mapper
     * @param timeout
     * @param unit
     */
    protected void execWithInput(CommandInput commandInput, String command,
            final CompletionHandler completionHandler, Function<String, ?> mapper, int timeout,
            TimeUnit unit) {
        String outStreamFile = "/tmp/" + SSH_OP_OUT_PREFIX + UUID.randomUUID() + ".txt";
        String errStreamFile = "/tmp/" + SSH_OP_ERR_PREFIX + UUID.randomUUID() + ".txt";
        String exitCodeFile = "/tmp/" + SSH_OP_EXIT_CODE_PREFIX + UUID.randomUUID() + ".txt";

        String hostname = commandInput.getDockerUri().getHost();
        AuthCredentialsServiceState credentials = commandInput.getCredentials();

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
            pollForProcessCompletion(commandInput, outStreamFile,
                    errStreamFile, exitCodeFile, prc);
        };

        logger.fine(
                String.format("SSH execution %s started on %s: %s", state.id, hostname, command));
        handleExecInProgress(state);
    }

    private String escape(String command) {
        return command.replace("'", "'\"'\"'");
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

    public void pollForProcessCompletion(CommandInput commandInput,
            String outStreamFile,
            String errStreamFile, String exitCodeFile, ProcessResultCollector prc) {
        String hostname = commandInput.getDockerUri().getHost();
        AuthCredentialsServiceState credentials = commandInput.getCredentials();

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
                logger.fine("Reschedure process polling with id " + prc.id);
                host.schedule(() -> {
                    pollForProcessCompletion(commandInput, outStreamFile, errStreamFile,
                            exitCodeFile, prc);
                }, 10, TimeUnit.SECONDS);
            }
        }, null, SSH_OPERATION_TIMEOUT_SHORT, TimeUnit.SECONDS);
        logger.fine(
                String.format("SSH execution %s started on %s: %s", state.id, hostname, command));
        handleExecInProgress(state);
    }

    @Override
    public void loadImage(CommandInput input, CompletionHandler completionHandler) {
        uploadImage(input, (completedOp, failure) -> {
            if (failure != null) {
                completionHandler.handle(null, failure);
            } else {
                String tmpFilePath = completedOp.getBody(String.class);
                execWithInput(input, docker("load --input " + tmpFilePath),
                        (completedOp1, failure1) -> {
                            gcData.add(new GcData(input, tmpFilePath));
                            completionHandler.handle(completedOp, failure);
                        });
            }
        });
    }

    protected void uploadImage(CommandInput commandInput, CompletionHandler completionHandler) {
        String hostname = commandInput.getDockerUri().getHost();
        AuthCredentialsServiceState credentials = commandInput.getCredentials();

        String remoteFile = "/tmp/image" + System.currentTimeMillis();
        byte[] data = (byte[]) commandInput.getProperties().get(DOCKER_IMAGE_DATA_PROP_NAME);
        Future<Throwable> result = SshUtil.asyncUpload(hostname, credentials,
                new ByteArrayInputStream(data), remoteFile);
        handleScpInProgress(new ScpState(remoteFile, result, completionHandler,
                SSH_OPERATION_TIMEOUT_LONG, TimeUnit.SECONDS));
    }

    @Override
    public void createImage(CommandInput input, CompletionHandler completionHandler) {
        String imageName = (String) input.getProperties().get(DOCKER_IMAGE_FROM_PROP_NAME);

        CommandBuilder cb = new CommandBuilder()
                .withCommand("pull")
                .withArguments(imageName);

        execWithInput(input, docker(cb), completionHandler, (s) -> s, SSH_OPERATION_TIMEOUT_LONG,
                TimeUnit.SECONDS);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void createContainer(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();
        String imageName = (String) properties.get(DOCKER_CONTAINER_IMAGE_PROP_NAME);

        CommandBuilder cb = new CommandBuilder()
                .withCommand("create")
                .withLongSwitchIfPresent(properties, PROP_NAME_TO_LONG_SWITCH,
                        DOCKER_CONTAINER_NAME_PROP_NAME, DOCKER_CONTAINER_TTY_PROP_NAME,
                        DOCKER_CONTAINER_ENV_PROP_NAME, DOCKER_CONTAINER_USER_PROP_NAME,
                        DOCKER_CONTAINER_ENTRYPOINT_PROP_NAME)
                .withLongSwitchIfPresent(properties, DOCKER_CONTAINER_OPEN_STDIN_PROP_NAME,
                        "interactive")
                .withArguments(imageName);

        cb.withLongSwitchIfPresent(properties, DOCKER_CONTAINER_WORKING_DIR_PROP_NAME, "workdir");

        String fqdn = (String) properties.get(DOCKER_CONTAINER_HOSTNAME_PROP_NAME);
        if (fqdn != null) {
            String domainName = (String) properties.get(DOCKER_CONTAINER_DOMAINNAME_PROP_NAME);
            if (domainName != null) {
                fqdn += "." + domainName;
            }
            cb.withLongSwitch("hostname", fqdn);
        }

        Map<String, Object> hostConfig = (Map<String, Object>) properties
                .get(DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME);

        if (hostConfig == null) {
            hostConfig = new HashMap<String, Object>();
        }

        cb.withLongSwitchIfPresent(hostConfig, PROP_NAME_TO_LONG_SWITCH, MEMORY_PROP_NAME,
                MEMORY_SWAP_PROP_NAME, CPU_SHARES_PROP_NAME, DNS_PROP_NAME, DNS_SEARCH_PROP_NAME,
                VOLUMES_FROM_PROP_NAME, CAP_ADD_PROP_NAME, CAP_DROP_PROP_NAME,
                PRIVILEGED_PROP_NAME, PUBLISH_ALL);

        cb.withLongSwitchIfPresent(hostConfig, NETWORK_MODE_PROP_NAME, "net");
        cb.withLongSwitchIfPresent(hostConfig, LINKS_PROP_NAME, "link");
        cb.withLongSwitchIfPresent(hostConfig, PID_MODE_PROP_NAME, "pid");
        cb.withLongSwitchIfPresent(hostConfig, EXTRA_HOSTS_PROP_NAME, "add-host");

        Map<String, List<Map<String, String>>> portBindings = (Map<String, List<Map<String, String>>>) hostConfig
                .get(DOCKER_CONTAINER_PORT_BINDINGS_PROP_NAME);

        if (portBindings != null) {
            for (DockerPortMapping portMapping : DockerPortMapping.fromMap(portBindings)) {
                cb.withLongSwitch("publish", portMapping.toString());
            }
        }

        Map<String, Object> restartPolicy = (Map<String, Object>) hostConfig
                .get(RESTART_POLICY_PROP_NAME);

        String restartPolicyName = restartPolicy == null ? null : (String) restartPolicy
                .get(RESTART_POLICY_NAME_PROP_NAME);
        if (restartPolicyName != null) {
            Integer restartPolicyRetries = (Integer) restartPolicy
                    .get(RESTART_POLICY_RETRIES_PROP_NAME);

            cb.withLongSwitch("restart",
                    formatRestartPolicy(restartPolicyName, restartPolicyRetries));
        }

        Map<String, Object> volumes = (Map<String, Object>) properties
                .get(DOCKER_CONTAINER_VOLUMES_PROP_NAME);
        if (volumes != null) {
            volumes.keySet().forEach(v -> cb.withLongSwitch("volume", v));
        }
        cb.withLongSwitchIfPresent(hostConfig, BINDS_PROP_NAME, "volume");

        List<Map<String, String>> devices = (List) hostConfig.get(DEVICES_PROP_NAME);
        if (devices != null) {
            devices.forEach(d -> cb.withLongSwitch("device", DockerDevice.fromMap(d).toString()));
        }

        cb.withArgumentIfPresent(properties, DOCKER_CONTAINER_COMMAND_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler,
                (s) -> Collections.singletonMap(DOCKER_CONTAINER_ID_PROP_NAME,
                        s.replaceAll("[\r\n]+$", "")));
    }

    private String formatRestartPolicy(String name, Integer retries) {
        return String.format(retries == null ? "%s" : "%s:%d",
                name, retries);
    }

    @Override
    public void startContainer(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("start")
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_ID_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler);
    }

    @Override
    public void stopContainer(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("stop")
                .withLongSwitchIfPresent(properties, PROP_NAME_TO_LONG_SWITCH,
                        DOCKER_CONTAINER_STOP_TIME)
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_ID_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler);
    }

    @Override
    public void inspectContainer(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("inspect")
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_ID_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler,
                EXTRACT_FIRST_JSON_ELEMENT);
    }

    @Override
    public void execContainer(CommandInput input, CompletionHandler completionHandler) {
        String containerId = (String) input.getProperties().remove(DOCKER_CONTAINER_ID_PROP_NAME);
        if (containerId == null || containerId.isEmpty()) {
            completionHandler.handle(null,
                    new IllegalArgumentException("Container id not provided"));
            return;
        }

        String[] command = (String[]) input.getProperties().remove(DOCKER_EXEC_COMMAND_PROP_NAME);

        CommandBuilder cb = new CommandBuilder()
                .withCommand("exec")
                .withArguments(containerId)
                .withArguments(command);

        execWithInput(input, docker(cb), completionHandler);
    }

    private static class CliStats {
        private static final long BYTE_UNIT_MULTIPLIER = 1000L;

        public double cpuUsagePercent;
        public double memUsage;
        public double memLimit;
        public double netInput;
        public double netOutput;

        /**
         * @param cliOutput
         *            - contains 2 lines, header and data
         */
        public CliStats(String cliOutput) {
            String data = cliOutput.split("\n")[1]; // We don't really need the header
            String[] splitted = data.split(" +");
            cpuUsagePercent = Double
                    .parseDouble(splitted[1].substring(0, splitted[1].length() - 1));
            memUsage = Double
                    .parseDouble(splitted[2]) * getMultiplier(splitted[3]);
            memLimit = Double
                    .parseDouble(splitted[5]) * getMultiplier(splitted[6]);
            netInput = Double
                    .parseDouble(splitted[8]) * getMultiplier(splitted[9]);
            netOutput = Double
                    .parseDouble(splitted[11]) * getMultiplier(splitted[12]);
        }

        long getMultiplier(String unit) {
            if (unit == null) {
                return 1;
            }
            switch (unit.toUpperCase()) {
            case "":
                return 1;
            case "B":
                return 1;
            case "KB":
                return BYTE_UNIT_MULTIPLIER;
            case "MB":
                return BYTE_UNIT_MULTIPLIER * BYTE_UNIT_MULTIPLIER;
            case "GB":
                return BYTE_UNIT_MULTIPLIER * BYTE_UNIT_MULTIPLIER * BYTE_UNIT_MULTIPLIER;
            case "TB":
                return BYTE_UNIT_MULTIPLIER * BYTE_UNIT_MULTIPLIER * BYTE_UNIT_MULTIPLIER
                        * BYTE_UNIT_MULTIPLIER;
            default:
                throw new IllegalArgumentException("Unknown unit: " + unit);
            }
        }

        public String apiFormat() {
            // As the API is much more verbose then the CLI, load some 'default' values
            Map<String, JsonElement> stats = Utils.fromJson(STATS_TEMPLATE,
                    new TypeToken<Map<String, JsonElement>>() {
                    }.getType());
            stats.put("read", new JsonPrimitive(DATE_FORMAT.format(new Date())));
            stats.get("memory_stats").getAsJsonObject().addProperty("usage",
                    (long) memUsage);
            stats.get("memory_stats").getAsJsonObject().addProperty("limit",
                    (long) memLimit);
            stats.get("networks").getAsJsonObject().get("eth0").getAsJsonObject()
                    .addProperty("rx_bytes", (long) netInput);
            stats.get("networks").getAsJsonObject().get("eth0").getAsJsonObject()
                    .addProperty("tx_bytes", (long) netOutput);

            /*  @formatter:off
             *  CPU is where it gets wicked - we need to fake some values based on a %
             *  Currently the formula used in the ContainerStatsEvaluator is
             *  cpuUsage = (cpuDelta / systemDelta) * percpu_usage.size()) * 100.0;
             *  So we're going with a fake values to result in the following (for better precision)
             *  cpuUsage = (cpuUsagePercent*100 / 10000) * 1) * 100.0;
             *  @formatter:on
             */
            stats.get("cpu_stats").getAsJsonObject()
                    .get("cpu_usage").getAsJsonObject()
                    .addProperty("total_usage", (long) cpuUsagePercent * 100);
            stats.get("cpu_stats").getAsJsonObject()
                    .get("cpu_usage").getAsJsonObject()
                    .addProperty("system_cpu_usage", 10000);

            return Utils.toJson(stats);
        }
    }

    @Override
    public void fetchContainerStats(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("stats")
                .withLongSwitch(DOCKER_CONTAINER_NO_STREAM, null, PROP_NAME_TO_LONG_SWITCH)
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_ID_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler, (s) -> {
            return new CliStats(s).apiFormat();
        });
    }

    @Override
    public void removeContainer(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("rm -f -v")
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_ID_PROP_NAME);

        execWithInput(input, docker(cb), (completedOp, failure) -> {
            if (failure != null && failure.getMessage().contains("No such container")) {
                // The container is already gone, so it's fine
                completionHandler.handle(completedOp, null);
            } else {
                completionHandler.handle(completedOp, failure);
            }
        });
    }

    @Override
    public void fetchContainerLog(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("logs")
                .withLongSwitchIfPresent(properties, TIMESTAMPS, TAIL, SINCE)
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_ID_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler);
    }

    @Override
    public void hostPing(CommandInput input, CompletionHandler completionHandler) {
        hostVersion(input, completionHandler);
    }

    @Override
    public void hostInfo(CommandInput input, CompletionHandler completionHandler) {
        execWithInput(input, docker("info"), completionHandler, SIMPLE_YAML_MAPPER);
    }

    @Override
    public void hostVersion(CommandInput input, CompletionHandler completionHandler) {
        execWithInput(input, docker("version"), completionHandler);
    }

    @Override
    public void listContainers(CommandInput input, CompletionHandler completionHandler) {
        String idNameSeperator = "@@@";
        CommandBuilder cb = new CommandBuilder()
                .withCommand("inspect")
                .withLongSwitch("format", "{{.Id}}" + idNameSeperator + "{{.Name}}")
                .withArguments("$(docker ps --all --quiet --no-trunc)");

        // each line in the output is a container ID
        // map it to a list of maps with the Id key set
        Function<String, ?> psMapper = NEWLINE_DELIMITED_MAPPER.andThen((c) -> c.stream()
                .map((row) -> {
                    String[] idNameValues = row.split(idNameSeperator);
                    Map<String, Object> rowMap = new HashMap<>(2);
                    rowMap.put(DOCKER_CONTAINER_ID_PROP_NAME, idNameValues[0]);
                    rowMap.put(DOCKER_CONTAINER_NAMES_PROP_NAME,
                            Collections.singletonList(idNameValues[1]));
                    return rowMap;
                })
                .collect(Collectors.toList()));

        execWithInput(input, docker(cb), completionHandler, psMapper);
    }

    @Override
    public void createNetwork(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();
        CommandBuilder cb = new CommandBuilder()
                .withCommand("network create")
                .withLongSwitchIfPresent(properties, PROP_NAME_TO_LONG_SWITCH,
                        DOCKER_CONTAINER_NETWORK_DRIVER_PROP_NAME)
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME);
        // TODO other properties
        execWithInput(input, docker(cb), completionHandler,
                (s) -> Collections.singletonMap(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME,
                        s.replaceAll("[\r\n]+$", "")));
    }

    @Override
    public void removeNetwork(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("network rm")
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_NETWORK_ID_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler);
    }

    @Override
    public void listNetworks(CommandInput input, CompletionHandler completionHandler) {
        String idNameSeperator = "@@@";
        CommandBuilder cb = new CommandBuilder()
                .withCommand("inspect")
                .withLongSwitch("format", "{{.Id}}" + idNameSeperator + "{{.Name}}")
                .withArguments("$(docker network ls --quiet --no-trunc)");

        // each line in the output is a network ID
        // map it to a list of maps with the Id key set
        Function<String, ?> psMapper = NEWLINE_DELIMITED_MAPPER.andThen((c) -> c.stream()
                .map((row) -> {
                    String[] idNameValues = row.split(idNameSeperator);
                    Map<String, Object> rowMap = new HashMap<>(2);
                    rowMap.put(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME, idNameValues[0]);
                    rowMap.put(DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME,
                            Collections.singletonList(idNameValues[1]));
                    return rowMap;
                })
                .collect(Collectors.toList()));

        execWithInput(input, docker(cb), completionHandler, psMapper);
    }

    @Override
    public void inspectNetwork(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("network inspect")
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_ID_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler,
                EXTRACT_FIRST_JSON_ELEMENT);
    }

    private String docker(CommandBuilder subCommandBuilder) {
        return docker(subCommandBuilder.toString());
    }

    private String docker(String subCommand) {
        // TODO path to binary and switches should be configurable as custom properties
        CommandBuilder cb = new CommandBuilder()
                .withCommand("docker")
                .withArguments(subCommand);

        return cb.toString();
    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub

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
        return creds.userEmail + "@" + hostname + ":" + creds.privateKey;
    }

    @Override
    public void createVolume(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();
        CommandBuilder cb = new CommandBuilder().withCommand("volume create")
                .withLongSwitchIfPresent(properties, PROP_NAME_TO_LONG_SWITCH,
                        DOCKER_VOLUME_DRIVER_PROP_NAME)
                .withArgumentIfPresent(properties, DOCKER_VOLUME_NAME_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler);

    }

    @Override
    public void removeVolume(CommandInput input, CompletionHandler completionHandler) {

        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder().withCommand("volume rm").withArgumentIfPresent(
                properties,
                DOCKER_VOLUME_NAME_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler);
    }

    @Override
    public void listVolumes(CommandInput input, CompletionHandler completionHandler) {
        throw new UnsupportedOperationException("List volumes is not implemented yet");
    }

    @Override
    public void inspectVolume(CommandInput input, CompletionHandler completionHandler) {
        throw new UnsupportedOperationException("Inspect volume is not implemented yet");
    }
}
