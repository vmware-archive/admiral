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

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

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
import static com.vmware.admiral.compute.ContainerHostService.SSH_HOST_KEY_PROP_NAME;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.NotImplementedException;

import com.vmware.admiral.adapter.docker.util.DockerDevice;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.adapter.docker.util.PropertyToSwitchNameMapper;
import com.vmware.admiral.adapter.docker.util.ssh.CachingJschSessionPoolImpl;
import com.vmware.admiral.adapter.docker.util.ssh.CommandBuilder;
import com.vmware.admiral.adapter.docker.util.ssh.JSchLoggerAdapter;
import com.vmware.admiral.adapter.docker.util.ssh.JSchSessionPool;
import com.vmware.admiral.adapter.docker.util.ssh.Mappers;
import com.vmware.admiral.adapter.docker.util.ssh.SessionParams;
import com.vmware.admiral.adapter.docker.util.ssh.SshExecTask;
import com.vmware.admiral.adapter.docker.util.ssh.SshQueueExecutor;
import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Docker command executor implementation based on using SSH and executing the docker cli on the
 * remote end
 */
public class SshDockerAdapterCommandExecutorImpl implements DockerAdapterCommandExecutor, Mappers {
    private static Logger logger = Logger.getLogger(SshDockerAdapterCommandExecutorImpl.class
            .getName());

    private static final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
            new ThreadPoolExecutor.AbortPolicy());

    private static final UnaryOperator<String> PROP_NAME_TO_LONG_SWITCH = new PropertyToSwitchNameMapper();

    static {
        JSch.setLogger(new JSchLoggerAdapter(logger));
    }

    private final BiFunction<Runnable, Long, Future<?>> scheduler;
    private final JSchSessionPool sessionPool;
    private final SshQueueExecutor sshQueueExecutor;

    public SshDockerAdapterCommandExecutorImpl(ServiceHost host) {
        /*
         * scheduler that will be used to periodically poll running tasks for completion
         *
         * this is using the ServiceHost's thread pool so there is no possibility of completion
         * polling being starved by new tasks being started by the local executor
         */
        scheduler = (r, delayMillis) -> host.schedule(r, delayMillis, TimeUnit.MILLISECONDS);
        sessionPool = new CachingJschSessionPoolImpl(scheduler);
        sshQueueExecutor = new SshQueueExecutor(scheduler, sessionPool);
    }

    @Override
    public void handleMaintenance(Operation post) {
        // add periodic maintenance logic here
    }

    /**
     * Execute an SSH command
     *
     * Input will be read from the given input stream and output and error streams will be written
     * to the given output streams.
     *
     * The command error status will be returned.
     *
     * @param commandInput
     * @param command
     * @param in
     * @param out
     * @param err
     * @param completionHandler
     * @return command error status code
     * @throws JSchException
     */
    protected void exec(CommandInput commandInput, String command, InputStream in,
            OutputStream out, OutputStream err, Consumer<SshExecTask> completionHandler)
            throws JSchException {

        logger.fine("Executing command: " + command);

        URI dockerUri = commandInput.getDockerUri();
        AuthCredentialsServiceState credentials = commandInput.getCredentials();

        String hostKey = (String) commandInput.getProperties().get(SSH_HOST_KEY_PROP_NAME);

        SessionParams sessionParams = new SessionParams()
                .withHost(dockerUri.getHost())
                .withPort(dockerUri.getPort())
                .withUser(credentials.userEmail)
                .withHostKey(hostKey);

        switch (AuthCredentialsType.valueOf(credentials.type)) {
        case PublicKey:
            sessionParams.withPrivateKey(credentials.privateKey.getBytes());
            break;

        case Password:
            sessionParams.withPassword(credentials.privateKey);
            break;

        default:
            throw new IllegalArgumentException("Unsupported credentials type: "
                    + credentials.type);
        }

        SshExecTask task = new SshExecTask(scheduler)
                .withCommand(command)
                .withInput(in)
                .withOutput(out)
                .withError(err)
                .withCompletionHandler(completionHandler);

        sshQueueExecutor.submit(task, sessionParams);
    }

    /**
     * Execute a command asynchronously, no transformation of the output string
     *
     * @param commandInput
     * @param command
     * @param in
     * @param completionHandler
     */
    protected void execWithInput(CommandInput commandInput, String command, InputStream in,
            CompletionHandler completionHandler) {

        execWithInput(commandInput, command, in, completionHandler, (s) -> s);
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
     * @param in
     * @param completionHandler
     * @param mapper
     */
    protected void execWithInput(CommandInput commandInput, String command, InputStream in,
            CompletionHandler completionHandler, Function<String, ?> mapper) {

        final OperationContext parentContext = OperationContext.getOperationContext();

        executor.execute(() -> {
            final OperationContext childContext = OperationContext.getOperationContext();
            try {
                // set the operation context of the parent thread in the current thread
                OperationContext.restoreOperationContext(parentContext);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ByteArrayOutputStream err = new ByteArrayOutputStream();

                exec(commandInput, command, in, out, err, (task) -> {
                    int exitStatus = task.getExitStatus();
                    if (logger.isLoggable(Level.FINE)) {
                        Utils.log(logger, null, getClass().getName(), Level.FINE,
                                "command=[%s],status=[%d],stdout=[%s],stderr=[%s]", command,
                                exitStatus, out, err);
                    }

                    if (exitStatus == 0) {
                        String output = "";
                        boolean outRequested = commandInput.getProperties() == null
                                || commandInput.getProperties().get(
                                        DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME) == null
                                || Boolean.valueOf(commandInput.getProperties().get(
                                        DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME).toString());
                        if (outRequested) {
                            output = out.toString();
                        }

                        Operation op = Operation.createPatch(null).setBody(mapper.apply(output));
                        completionHandler.handle(op, null);

                    } else {
                        Throwable t = new RuntimeException(String.format(
                                "Error executing command=[%s],status=[%s],stdout=[%s],stderr=[%s]",
                                command, exitStatus, out, err));

                        completionHandler.handle(null, t);
                    }
                });
            } catch (Exception x) {
                completionHandler.handle(null, x);
            } finally {
                // restore the operation context of the child thread
                OperationContext.restoreOperationContext(childContext);
            }
        });
    }

    @Override
    public void loadImage(CommandInput input, CompletionHandler completionHandler) {
        byte[] data = (byte[]) input.getProperties().get(DOCKER_IMAGE_DATA_PROP_NAME);

        execWithInput(input, docker("load"), new ByteArrayInputStream(data), completionHandler);
    }

    @Override
    public void createImage(CommandInput input, CompletionHandler completionHandler) {
        String imageName = (String) input.getProperties().get(DOCKER_IMAGE_FROM_PROP_NAME);

        CommandBuilder cb = new CommandBuilder()
                .withCommand("pull")
                .withArguments(imageName);

        execWithInput(input, docker(cb), null, completionHandler);
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

        execWithInput(input, docker(cb), null, completionHandler,
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

        execWithInput(input, docker(cb), null, completionHandler);
    }

    @Override
    public void stopContainer(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("stop")
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_ID_PROP_NAME);

        execWithInput(input, docker(cb), null, completionHandler);
    }

    @Override
    public void inspectContainer(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("inspect")
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_ID_PROP_NAME);

        execWithInput(input, docker(cb), null, completionHandler,
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

        execWithInput(input, docker(cb), null, completionHandler);
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

        execWithInput(input, docker(cb), null, completionHandler);
    }

    @Override
    public void fetchContainerLog(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("logs")
                .withLongSwitchIfPresent(properties, TIMESTAMPS, TAIL, SINCE)
                .withArgumentIfPresent(properties, DOCKER_CONTAINER_ID_PROP_NAME);

        execWithInput(input, docker(cb), null, completionHandler);
    }

    @Override
    public void hostPing(CommandInput input, CompletionHandler completionHandler) {
        execWithInput(input, docker("version"), null, completionHandler);
    }

    @Override
    public void hostInfo(CommandInput input, CompletionHandler completionHandler) {
        execWithInput(input, docker("info"), null, completionHandler, SIMPLE_YAML_MAPPER);
    }

    @Override
    public void hostVersion(CommandInput input, CompletionHandler completionHandler) {
        execWithInput(input, docker("version"), null, completionHandler);
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

        execWithInput(input, docker(cb), null, completionHandler, psMapper);
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
        execWithInput(input, docker(cb), null, completionHandler,
                (s) -> Collections.singletonMap(DOCKER_NETWORK_ID_PROP_NAME,
                        s.replaceAll("[\r\n]+$", "")));
    }

    @Override
    public void removeNetwork(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder()
                .withCommand("network rm")
                .withArgumentIfPresent(properties, DOCKER_NETWORK_ID_PROP_NAME);

        execWithInput(input, docker(cb), null, completionHandler);
    }

    @Override
    public void stop() {
        // stop accepting new requests
        executor.shutdownNow();

        // close active sessions
        sessionPool.shutdown();
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
    public void createVolume(CommandInput input, CompletionHandler completionHandler) {
        Map<String, Object> properties = input.getProperties();
        CommandBuilder cb = new CommandBuilder().withCommand("volume create")
                .withLongSwitchIfPresent(properties, PROP_NAME_TO_LONG_SWITCH,
                        DOCKER_VOLUME_DRIVER_PROP_NAME)
                .withArgumentIfPresent(properties, DOCKER_VOLUME_NAME_PROP_NAME);

        execWithInput(input, docker(cb), null, completionHandler);

    }

    @Override
    public void removeVolume(CommandInput input, CompletionHandler completionHandler) {

        Map<String, Object> properties = input.getProperties();

        CommandBuilder cb = new CommandBuilder().withCommand("volume rm").withArgumentIfPresent(
                properties,
                DOCKER_VOLUME_NAME_PROP_NAME);

        execWithInput(input, docker(cb), null, completionHandler);
    }

    @Override
    public void listVolumes(CommandInput input, CompletionHandler completionHandler) {
        throw new NotImplementedException("List volumes is not implemented yet");
    }
}
