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
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.PUBLISH_ALL_PORTS;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_RETRIES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.VOLUMES_FROM_PROP_NAME;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import net.schmizz.sshj.SSHClient;

import com.vmware.admiral.adapter.docker.util.DockerDevice;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.adapter.docker.util.PropertyToSwitchNameMapper;
import com.vmware.admiral.adapter.docker.util.ssh.CommandBuilder;
import com.vmware.admiral.adapter.docker.util.ssh.Mappers;
import com.vmware.admiral.common.util.SshUtil;
import com.vmware.admiral.common.util.SshUtil.AsyncResult;
import com.vmware.admiral.common.util.SshUtil.ConsumedResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Docker command executor implementation based on using SSH and executing the docker cli on the
 * remote end
 */
public class SshDockerAdapterCommandExecutorImpl implements DockerAdapterCommandExecutor, Mappers {

    private static final String SSH_OP_OUT_PREFIX = "ssh-op-out";
    private static final String SSH_OP_ERR_PREFIX = "ssh-op-err";
    private static final String SSH_OP_EXIT_CODE_PREFIX = "ssh-op-exitCode";

    /*
     * Depending on the load, lowering this may result in better performance for a single operation,
     * but it will result in higher usage of ssh sessions as well. Change MaxSessions ssh property
     * accordingly.
     */
    private static final int SSH_POLL_DELAY_SECONDS = Integer.parseInt(
            System.getProperty("ssh.poll.delay", "10"));

    private ServiceHost host;

    private Map<String, SSHClient> clients = new HashMap<String, SSHClient>();

    private static Logger logger = Logger.getLogger(SshDockerAdapterCommandExecutorImpl.class
            .getName());

    private static final UnaryOperator<String> PROP_NAME_TO_LONG_SWITCH = new PropertyToSwitchNameMapper();

    Set<ExecutionState> execInProgress = ConcurrentHashMap.newKeySet();
    Set<ScpState> scpInProgress = ConcurrentHashMap.newKeySet();

    public SshDockerAdapterCommandExecutorImpl(ServiceHost host) {
        this.host = host;
        host.schedule(() -> {
            handleInProgress();
        }, SSH_POLL_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    public void handleInProgress() {
        Iterator<ExecutionState> execIt = execInProgress.iterator();
        while (execIt.hasNext()) {
            ExecutionState state = execIt.next();
            if (state.result.isDone()) {
                execIt.remove();
                ConsumedResult consumed = null;
                try {
                    consumed = state.result.join().consume();
                    handleExecResult(state.id, consumed.exitCode, consumed.error, consumed.out,
                            consumed.err, state.handler, state.mapper);
                } catch (IOException e) {
                    state.handler.handle(null, e);
                    return;
                }
            }
        }

        Iterator<ScpState> scpIt = scpInProgress.iterator();
        while (scpIt.hasNext()) {
            ScpState state = scpIt.next();
            if (state.result.isDone()) {
                scpIt.remove();
                Throwable error = null;
                try {
                    error = state.result.get();
                } catch (InterruptedException | ExecutionException e) {
                    state.handler.handle(null, e);
                }
                Operation op = Operation.createPatch(null)
                        .setBody(state.target);
                state.handler.handle(op, error);
            }
        }

        host.schedule(() -> {
            handleInProgress();
        }, SSH_POLL_DELAY_SECONDS, TimeUnit.SECONDS);
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
                Throwable t = new RuntimeException(String.format(
                        "Error executing ssh command with id %s%nSTATUS: %s%nOUT=%s%nERR=%s",
                        id, exitCode, out, err));
                handler.handle(null, t);
            }
        } else { // Operation completed successfully
            logger.info(
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

    private class SshOperationState {
        public String id = UUID.randomUUID().toString();

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
    }

    private class ExecutionState extends SshOperationState {
        public AsyncResult result;
        public CompletionHandler handler;
        public Function<String, ?> mapper;

        public ExecutionState(AsyncResult result, CompletionHandler handler,
                Function<String, ?> mapper) {
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

        public ScpState(String target, Future<Throwable> result, CompletionHandler handler) {
            super();
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

        execWithInput(commandInput, command, completionHandler, (s) -> s);
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
     */
    protected void execWithInput(CommandInput commandInput, String command,
            final CompletionHandler completionHandler, Function<String, ?> mapper) {
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
                command, outStreamFile, errStreamFile, exitCodeFile);

        AsyncResult result = SshUtil.asyncExec(client, command);
        // Not setting completion handler at constructor so I can use the state id in it (better
        // log)
        ExecutionState state = new ExecutionState(result, null, null);
        state.handler = (op, failure) -> {
            if (failure != null) {
                // Failed to start the process
                completionHandler.handle(op, failure);
                return;
            }
            String pid = op.getBody(String.class).replace("\n", "");
            ProcessResultCollector prc = new ProcessResultCollector(pid, completionHandler, mapper);
            logger.info(String.format(
                    "Starting SSH process result collector with id %s for pid %s, origin %s",
                    pid, prc.id, state.id));
            pollForProcessCompletion(commandInput, outStreamFile,
                    errStreamFile, exitCodeFile, prc);
        };

        logger.info(
                String.format("SSH execution %s started on %s: %s", state.id, hostname, command));
        execInProgress.add(state);
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
                execInProgress.add(new ExecutionState(outOp, (op1, failure1) -> {
                    if (failure1 != null) {
                        prc.completionHandler.handle(null, failure1);
                        return;
                    }
                    prc.setOut(op1.getBody(String.class));
                }, null));
                AsyncResult errOp = SshUtil.asyncExec(client, "cat " + errStreamFile);
                execInProgress.add(new ExecutionState(errOp, (op1, failure1) -> {
                    if (failure1 != null) {
                        prc.completionHandler.handle(null, failure1);
                        return;
                    }
                    prc.setErr(op1.getBody(String.class));
                }, null));
                AsyncResult exitCodeOp = SshUtil.asyncExec(client, "cat " + exitCodeFile);
                execInProgress.add(new ExecutionState(exitCodeOp, (op1, failure1) -> {
                    if (failure1 != null) {
                        prc.completionHandler.handle(null, failure1);
                        return;
                    }
                    prc.setExitCode(Integer.parseInt(op1.getBody(String.class).replace("\n", "")));
                }, null));
            } else {
                // Try again in 10 seconds
                host.schedule(() -> {
                    pollForProcessCompletion(commandInput, outStreamFile, errStreamFile,
                            exitCodeFile, prc);
                }, 10, TimeUnit.SECONDS);
            }
        }, null);
        logger.info(
                String.format("SSH execution %s started on %s: %s", state.id, hostname, command));
        execInProgress.add(state);
    }

    @Override
    public void loadImage(CommandInput input, CompletionHandler completionHandler) {
        uploadImage(input, (completedOp, failure) -> {
            if (failure != null) {
                completionHandler.handle(null, failure);
            } else {
                execWithInput(input, docker("load --input " + completedOp.getBody(String.class)),
                        completionHandler);
            }
        });
    }

    protected void uploadImage(CommandInput commandInput, CompletionHandler completionHandler) {
        String hostname = commandInput.getDockerUri().getHost();
        AuthCredentialsServiceState credentials = commandInput.getCredentials();

        String remoteFile = "/tmp/image" + System.currentTimeMillis();
        byte[] data = (byte[]) commandInput.getProperties().get(DOCKER_IMAGE_DATA_PROP_NAME);
        logger.info("" + data.length);
        Future<Throwable> result = SshUtil.asyncUpload(hostname, credentials,
                new ByteArrayInputStream(data), remoteFile);
        scpInProgress.add(new ScpState(remoteFile, result, completionHandler));
    }

    @Override
    public void createImage(CommandInput input, CompletionHandler completionHandler) {
        String imageName = (String) input.getProperties().get(DOCKER_IMAGE_FROM_PROP_NAME);

        CommandBuilder cb = new CommandBuilder()
                .withCommand("pull")
                .withArguments(imageName);

        execWithInput(input, docker(cb), completionHandler);
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
                PRIVILEGED_PROP_NAME, PUBLISH_ALL_PORTS);

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
                .withLongSwitchIfPresent(properties, PROP_NAME_TO_LONG_SWITCH, DOCKER_CONTAINER_STOP_TIME)
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

    @Override
    public void fetchContainerStats(CommandInput input, CompletionHandler completionHandler) {
        // TODO the CLI stats command is nowhere near the API version - skip for now
        Operation op = Operation.createPatch(null).setBody("{ \"cpu_stats\": {} }");
        completionHandler.handle(op, null);
    }

    @Override
    public void removeContainer(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("rm -f -v")
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_ID_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler);
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
                        DOCKER_NETWORK_DRIVER_PROP_NAME)
                .withArgumentIfPresent(properties, DOCKER_NETWORK_NAME_PROP_NAME);
        // TODO other properties
        execWithInput(input, docker(cb), completionHandler,
                (s) -> Collections.singletonMap(DOCKER_NETWORK_ID_PROP_NAME,
                        s.replaceAll("[\r\n]+$", "")));
    }

    @Override
    public void removeNetwork(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("network rm")
                .withArgumentIfPresent(properties, DOCKER_NETWORK_ID_PROP_NAME);

        execWithInput(input, docker(cb), completionHandler);
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
        String id = creds.userEmail + "@" + hostname + ":" + creds.privateKey;
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
