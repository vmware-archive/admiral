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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.adapter.docker.service.CommandInput;
import com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor;
import com.vmware.admiral.adapter.docker.service.SshDockerAdapterCommandExecutorImpl;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.common.util.SshUtil;
import com.vmware.admiral.common.util.SshUtil.Result;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class SSHDockerAdapterCommandExecutorIT extends BaseTestCase {

    private static final String HOST_NAME = getSystemOrTestProp("docker.host.address");
    private static final URI HOST_URI = URI.create("ssh://" +
            HOST_NAME + ":" +
            getSystemOrTestProp("docker.host.port.SSH"));

    private static final int DEFAULT_TIMEOUT = 45;

    private List<String> containersToDelete = new ArrayList<String>();

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

        handler.join(60, TimeUnit.SECONDS);

        Assert.assertTrue("Operation failed to complete on time!", handler.done);
        Assert.assertNull("Unexpected failure!", handler.failure);
        Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
    }

    @Test
    public void inspect() throws InterruptedException, TimeoutException {
        String name = getRandomContainerName();
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

        CommandInput input = new CommandInput();
        input.withDockerUri(HOST_URI);
        input.withCredentials(getPasswordCredentials());
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_IMAGE_PROP_NAME,
                "alpine");
        String name = getRandomContainerName();
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_NAME_PROP_NAME,
                name);
        input.getProperties().put(DockerAdapterCommandExecutor.DOCKER_CONTAINER_COMMAND_PROP_NAME,
                "/bin/sh");

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
    public void start() throws InterruptedException, TimeoutException {
        String name = getRandomContainerName();
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
        String name = getRandomContainerName();
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
        String name = getRandomContainerName();
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
        String name = getRandomContainerName();
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
        String name = getRandomContainerName();
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

    @Test
    public void list() throws InterruptedException, TimeoutException {
        String name = getRandomContainerName();
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

    private String getRandomContainerName() {
        return "ssh-it-" + System.currentTimeMillis();
    }

    @Test
    @Ignore("Takes too long to execute with each build")
    public void inspectPerformance() throws InterruptedException, TimeoutException {
        String name = getRandomContainerName();
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
            }).run();
        }

        for (DefaultSshOperationResultCompletionHandler handler : handlers) {
            handler.join(240, TimeUnit.SECONDS);

            Assert.assertTrue("Operation failed to complete on time!", handler.done);
            Assert.assertNull("Unexpected failure!", handler.failure);
            Assert.assertNotNull("Body should contain STDOUT!", handler.op.getBody(String.class));
        }
    }

    @After
    public void cleanup() {
        if (!containersToDelete.isEmpty()) {
            String toDelete = "";
            for (String s : containersToDelete) {
                toDelete += s + " ";
            }
            Result result = SshUtil.exec(HOST_NAME, getPasswordCredentials(),
                    "docker rm -fv " + toDelete);
            Assert.assertTrue("Failed to cleanup containers", result.exitCode == 0);
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
