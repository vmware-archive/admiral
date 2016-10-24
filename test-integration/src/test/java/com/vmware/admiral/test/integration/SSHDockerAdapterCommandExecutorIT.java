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

package com.vmware.admiral.test.integration;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getSystemOrTestProp;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.adapter.docker.service.CommandInput;
import com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor;
import com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG;
import com.vmware.admiral.adapter.docker.service.SshDockerAdapterCommandExecutorImpl;
import com.vmware.admiral.adapter.docker.service.SshDockerAdapterCommandExecutorImpl.GcData;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.common.util.SshUtil;
import com.vmware.admiral.common.util.SshUtil.Result;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

@Ignore("https://jira-hzn.eng.vmware.com/browse/VBV-653")
public class SSHDockerAdapterCommandExecutorIT extends BaseTestCase {

    private static final String HOST_NAME = getSystemOrTestProp("docker.host.address");
    private static final URI HOST_URI = URI.create("ssh://" +
            HOST_NAME + ":" +
            getSystemOrTestProp("docker.host.port.SSH"));

    private static final int DEFAULT_TIMEOUT = 45;

    private final List<String> containersToDelete = new ArrayList<String>();
    private final List<String> networksToDelete = new ArrayList<String>();
    private Iterator<String> it;

    @Test
    public void pingWithPassword() throws InterruptedException, TimeoutException {
        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());

        final DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.hostPing(input, handler);

        handler.join(DEFAULT_TIMEOUT, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    @Test
    public void pingWithPrivateKey() throws InterruptedException, TimeoutException {
        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPkeyCredentials());

        final DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.hostPing(input, handler);

        handler.join(DEFAULT_TIMEOUT, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    @Ignore("Failing intermittently: https://jira-hzn.eng.vmware.com/browse/VBV-608")
    @Test
    public void load() throws InterruptedException, TimeoutException, IOException {
        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPkeyCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_IMAGE_DATA_PROP_NAME,
                IOUtils.toByteArray(Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("alpine.tar")));

        final DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.loadImage(input, handler);

        handler.join(120, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    @Test
    public void inspect() throws InterruptedException, TimeoutException {
        String name = getRandomName();
        Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(),
                "docker create --name " + name + " alpine /bin/sh");
        Assert.assertTrue("Failed to create container", result.exitCode == 0);
        containersToDelete.add(name);

        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME,
                name);

        List<DefaultSshOperationResultCompletionHandler> handlers = new ArrayList<>();

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.inspectContainer(input, handler);
        handlers.add(handler);

        handler.join(45, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    @Test
    public void createImage() throws InterruptedException, TimeoutException {
        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_IMAGE_FROM_PROP_NAME,
                "alpine");

        List<DefaultSshOperationResultCompletionHandler> handlers = new ArrayList<>();

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.createImage(input, handler);
        handlers.add(handler);

        handler.join(45, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    @Test
    public void create() throws InterruptedException, TimeoutException {
        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        String name = getRandomName();
        createContainer(executor, name);
        containersToDelete.add(name);
    }

    @Test
    public void start() throws InterruptedException, TimeoutException {
        String name = getRandomName();
        Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(),
                "docker create --name " + name + " alpine /bin/sh");
        Assert.assertTrue("Failed to create container", result.exitCode == 0);
        containersToDelete.add(name);

        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME,
                name);

        List<DefaultSshOperationResultCompletionHandler> handlers = new ArrayList<>();

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.startContainer(input, handler);
        handlers.add(handler);

        handler.join(45, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    @Test
    public void stop() throws InterruptedException, TimeoutException {
        String name = getRandomName();
        Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(), "docker run -d --name " +
                name + " alpine /bin/sh -c 'sleep 120'");
        Assert.assertTrue("Failed to start container", result.exitCode == 0);
        containersToDelete.add(name);

        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME,
                name);
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_STOP_TIME,
                "0");

        List<DefaultSshOperationResultCompletionHandler> handlers = new ArrayList<>();

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.stopContainer(input, handler);
        handlers.add(handler);

        handler.join(45, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    @Test
    public void exec() throws InterruptedException, TimeoutException {
        String name = getRandomName();
        Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(), "docker run -d --name " +
                name + " alpine /bin/sh -c 'sleep 120'");
        Assert.assertTrue("Failed to start container", result.exitCode == 0);
        containersToDelete.add(name);

        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME,
                name);
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_EXEC_COMMAND_PROP_NAME,
                "echo hello".split(" "));

        List<DefaultSshOperationResultCompletionHandler> handlers = new ArrayList<>();

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.execContainer(input, handler);
        handlers.add(handler);

        handler.join(45, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    @Test
    public void remove() throws InterruptedException, TimeoutException {
        String name = getRandomName();
        Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(),
                "docker create --name " + name + " alpine /bin/sh");
        Assert.assertTrue("Failed to create container", result.exitCode == 0);
        containersToDelete.add(name);

        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME,
                name);

        List<DefaultSshOperationResultCompletionHandler> handlers = new ArrayList<>();

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.removeContainer(input, handler);
        handlers.add(handler);

        handler.join(45, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
        containersToDelete.remove(name);
    }

    @Test
    public void fetchLogs() throws InterruptedException, TimeoutException {
        String name = getRandomName();
        Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(), "docker run -d --name " +
                name + " alpine /bin/sh -c 'echo hello'");
        Assert.assertTrue("Failed to start container", result.exitCode == 0);
        containersToDelete.add(name);

        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME,
                name);
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_STOP_TIME,
                "0");

        List<DefaultSshOperationResultCompletionHandler> handlers = new ArrayList<>();

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.fetchContainerLog(input, handler);
        handlers.add(handler);

        handler.join(45, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
        Assert.assertTrue(handler.op.getBody(String.class).contains("hello"));
    }

    @Ignore("Failing intermittently: https://jira-hzn.eng.vmware.com/browse/VBV-609")
    @Test
    public void list() throws InterruptedException, TimeoutException {
        String name = getRandomName();
        Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(),
                "docker create --name " + name + " alpine /bin/sh");
        Assert.assertTrue("Failed to create container", result.exitCode == 0);
        containersToDelete.add(name);

        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME,
                name);

        List<DefaultSshOperationResultCompletionHandler> handlers = new ArrayList<>();

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.listContainers(input, handler);
        handlers.add(handler);

        handler.join(45, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
        Assert.assertTrue(handler.op.getBody(String.class).contains(name));
    }

    @Test
    public void createAndRemoveNetwork() throws InterruptedException, TimeoutException {
        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);
        String name = getRandomName();

        // Create a network
        String networkId = createBridgeNetwork(executor, name);
        networksToDelete.add(name);

        // Remove this network
        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(
                DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_ID_PROP_NAME,
                networkId);

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.removeNetwork(input, handler);
        networksToDelete.remove(name);

        handler.join(DEFAULT_TIMEOUT, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    @Test
    public void connectContainerToNetwork() throws InterruptedException, TimeoutException {
        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);
        String containerName = getRandomName();
        String networkName = getRandomName();

        // Create a network
        String networkId = createBridgeNetwork(executor, networkName);
        networksToDelete.add(networkName);

        // Create a container
        String containerId = createContainer(executor, containerName);
        containersToDelete.add(containerName);

        // Connect the container to the network
        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(
                DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_ID_PROP_NAME,
                networkId);
        input.getProperties().put(
                DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.CONTAINER_PROP_NAME,
                containerId);

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.connectContainerToNetwork(input, handler);

        handler.join(DEFAULT_TIMEOUT, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    private String getRandomName() {
        return "ssh-it-" + System.currentTimeMillis();
    }

    @Test
    @Ignore("Takes too long to execute with each build")
    public void inspectPerformance() throws InterruptedException, TimeoutException {
        String name = getRandomName();
        Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(),
                "docker create --name " + name + " alpine /bin/sh");
        Assert.assertTrue("Failed to create container", result.exitCode == 0);
        containersToDelete.add(name);

        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME,
                name);

        List<DefaultSshOperationResultCompletionHandler> handlers = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            new Thread(new Runnable() {

                @Override
                public void run() {
                    DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
                    executor.inspectContainer(input, handler);
                    handlers.add(handler);
                }
            }).start();
        }

        for (DefaultSshOperationResultCompletionHandler handler : handlers) {
            handler.join(240, TimeUnit.SECONDS);

            Assert.assertTrue("Operation failed to complete on time!", handler.done);
            Assert.assertNull("Unexpected failure!", handler.failure);
            Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
        }
    }

    @Test
    public void escapeTest() throws InterruptedException, TimeoutException {
        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_IMAGE_PROP_NAME,
                "alpine");
        String name = getRandomName();
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_NAME_PROP_NAME,
                name);
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_COMMAND_PROP_NAME,
                "/bin/sh");
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ENV_PROP_NAME,
                "'1234 \\'5678'");

        List<DefaultSshOperationResultCompletionHandler> handlers = new ArrayList<>();

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.createContainer(input, handler);
        handlers.add(handler);
        containersToDelete.add(name);

        handler.join(45, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    @Test
    public void stats() throws InterruptedException, TimeoutException {
        String name = getRandomName();
        Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(), "docker run -d --name " +
                name + " alpine /bin/sh -c 'sleep 120'");
        Assert.assertTrue("Failed to start container", result.exitCode == 0);
        containersToDelete.add(name);

        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME,
                name);

        List<DefaultSshOperationResultCompletionHandler> handlers = new ArrayList<>();

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.fetchContainerStats(input, handler);
        handlers.add(handler);

        handler.join(45, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
        Utils.fromJson(handler.op.getBody(String.class), Object.class);
    }

    @Test
    public void gc() throws InterruptedException, IOException {
        SshDockerAdapterCommandExecutorImpl executor = new SshDockerAdapterCommandExecutorImpl(
                host);

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.listContainers(input, handler);

        int retryCount = 0;
        while (executor.gcData.size() < 3 && retryCount < 45) {
            Thread.sleep(1000);
            retryCount++;
        }
        Assert.assertTrue("Timed out filling gcData", retryCount < 45);
        Assert.assertEquals("Unexpected number of files for gc", 3, executor.gcData.size());
        List<String> files = new ArrayList<String>();
        for (GcData data : executor.gcData) {
            files.add(data.filePath);
        }
        executor.gc();
        Assert.assertEquals("Unexpected number of files for gc", 0, executor.gcData.size());
        retryCount = 0;
        it = files.iterator();
        String currentFile = null;
        while (retryCount < 25 && it.hasNext()) {
            if (retryCount == 0) {
                currentFile = it.next();
            }
            Result res = SshUtil.exec(HOST_NAME, getPasswordCredentials(), "ls " + currentFile);
            if (res.consume().err.contains("No such file")) {
                retryCount = 0;
            }
            Thread.sleep(1000);
        }
        Assert.assertTrue("Failed to delete file on time: " + currentFile, retryCount < 25);
    }

    @After
    public void cleanup() {
        // cleanup containers
        if (!containersToDelete.isEmpty()) {
            String toDelete = "";
            for (String s : containersToDelete) {
                toDelete += s + " ";
            }
            Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(),
                    "docker rm -fv " + toDelete);
            Assert.assertTrue("Failed to cleanup containers", result.exitCode == 0);
        }

        // cleanup networks
        if (!networksToDelete.isEmpty()) {
            String toDelete = "";
            for (String s : networksToDelete) {
                toDelete += s + " ";
            }
            Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(),
                    "docker network rm " + toDelete);
            Assert.assertTrue("Failed to cleanup networks", result.exitCode == 0);
        }
    }

    private class DefaultSshOperationResultCompletionHandler implements CompletionHandler {
        public boolean done = false;
        public Throwable failure;
        public Operation op;

        @Override
        public void handle(Operation completedOp, Throwable failure) {
            this.failure = failure;
            this.op = completedOp;
            this.done = true;
        }

        public void join(long timeout, TimeUnit unit)
                throws InterruptedException, TimeoutException {
            long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
            while (!done && endTime > System.currentTimeMillis()) {
                Thread.sleep(1000);
            }

            if (!done) {
                throw new TimeoutException("Operation failed to complete on time!");
            }
        }
    }

    private String createContainer(SshDockerAdapterCommandExecutorImpl executor,
            String containerName) throws InterruptedException, TimeoutException {
        CommandInput containerInput = new CommandInput();
        containerInput.withDockerUri(HOST_URI);
        containerInput.withCredentials(getPasswordCredentials());
        containerInput.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_IMAGE_PROP_NAME,
                "alpine");
        containerInput.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_NAME_PROP_NAME,
                containerName);
        containerInput.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_COMMAND_PROP_NAME,
                "/bin/sh");

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.createContainer(containerInput, handler);

        handler.join(DEFAULT_TIMEOUT, TimeUnit.SECONDS);

        Assert.assertTrue("Create container failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        @SuppressWarnings("rawtypes")
        Map response = handler.op.getBody(Map.class);
        Assert.assertNotNull("Body should contain STDOUT!", response);
        Assert.assertTrue(response.containsKey(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME));
        return response.get(DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME).toString();
    }

    private String createBridgeNetwork(SshDockerAdapterCommandExecutorImpl executor, String networkName) throws InterruptedException, TimeoutException {
        CommandInput networkInput = new CommandInput();
        networkInput.withDockerUri(HOST_URI);
        networkInput.withCredentials(getPasswordCredentials());
        networkInput.getProperties().put(
                DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_DRIVER_PROP_NAME,
                "bridge");
        networkInput.getProperties().put(
                DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME,
                networkName);

        DefaultSshOperationResultCompletionHandler handler = new DefaultSshOperationResultCompletionHandler();
        executor.createNetwork(networkInput, handler);

        handler.join(DEFAULT_TIMEOUT, TimeUnit.SECONDS);

        Assert.assertTrue("Create network failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        @SuppressWarnings("rawtypes")
        Map response = handler.op.getBody(Map.class);
        Assert.assertNotNull("Body should contain STDOUT!", response);
        Assert.assertTrue(response.containsKey(DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_ID_PROP_NAME));
        return response.get(DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_ID_PROP_NAME).toString();
    }

    private AuthCredentialsServiceState getPasswordCredentials() {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.userEmail = getSystemOrTestProp("ssh.host.username");
        creds.privateKey = getSystemOrTestProp("ssh.host.password");
        creds.type = "Password";
        return creds;
    }

    private AuthCredentialsServiceState getPkeyCredentials() {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.userEmail = getSystemOrTestProp("ssh.host.username");
        creds.privateKey = FileUtil.getResourceAsString(
                getSystemOrTestProp("ssh.host.pkey"), true);
        creds.type = "PublicKey";
        return creds;
    }
}
