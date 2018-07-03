/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.content.kubernetes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.kubernetes.entities.pods.Container;
import com.vmware.admiral.compute.kubernetes.entities.pods.ContainerPort;
import com.vmware.admiral.compute.kubernetes.entities.pods.EnvVar;
import com.vmware.admiral.compute.kubernetes.entities.pods.PodSpec;
import com.vmware.admiral.compute.kubernetes.entities.pods.Probe;
import com.vmware.admiral.compute.kubernetes.entities.pods.RestartPolicy;
import com.vmware.admiral.compute.kubernetes.entities.pods.SecurityContext;
import com.vmware.admiral.compute.kubernetes.entities.pods.TCPSocketAction;

public class KubernetesConverterTest {
    public static final String CONTAINER_NAME = "nginx";
    public static final String CONTAINER_WORKING_DIR = "/root";

    @Test
    public void testFromPodContainerToContainerDescription() {

        Container podContainer = createPodContainer();
        PodSpec spec = new PodSpec();
        spec.restartPolicy = RestartPolicy.Always;

        ContainerDescription containerDescription = KubernetesConverter
                .fromPodContainerToContainerDescription(podContainer, spec);

        assertNotNull(containerDescription);
        assertEquals(podContainer.name, containerDescription.name);
        assertEquals(podContainer.image, containerDescription.image);
        assertEquals(podContainer.command, Arrays.asList(containerDescription.command[0]));
        assertEquals(podContainer.args, Arrays.asList(containerDescription.command[1]));
        assertEquals(podContainer.workingDir, containerDescription.workingDir);
        assertEquals(podContainer.ports.get(0).containerPort.toString(),
                containerDescription.portBindings[0].containerPort);
        assertEquals(podContainer.ports.get(0).hostPort.toString(),
                containerDescription.portBindings[0].hostPort);
        assertEquals(podContainer.ports.get(0).protocol, containerDescription.portBindings[0].protocol);
        assertEquals(podContainer.env.get(0).name + "=" + podContainer.env.get(0).value, containerDescription.env[0]);
        assertEquals(podContainer.securityContext.privileged, containerDescription.privileged);
        assertEquals(spec.restartPolicy.toString().toLowerCase(), containerDescription.restartPolicy);
        assertEquals(podContainer.livenessProbe.timeoutSeconds, Integer.valueOf(containerDescription.healthConfig.timeoutMillis / 1000));
    }

    @Test
    public void testFromPodContainerCommandToContainerDescriptionCommand() {
        List<String> commands = Arrays.asList("python3");
        List<String> args = Arrays.asList("start");

        List<String> cmds = KubernetesConverter.fromPodContainerCommandToContainerDescriptionCommand(commands, args);
        assertNotNull(cmds);

        String[] expectedResult = Stream.concat(Arrays.stream(commands.toArray()), Arrays.stream(args.toArray()))
                .toArray(String[]::new);

        assertNotNull(expectedResult);
        assertArrayEquals(expectedResult, cmds.toArray());
    }

    @Test
    public void testFromPodContainerPortsToContainerDescriptionPortBindings() {
        ContainerPort port = createContainerPort();

        List<ContainerPort> containerPorts = Arrays.asList(port);

        PortBinding[] portBindings = KubernetesConverter
                .fromPodContainerPortsToContainerDescriptionPortBindings(containerPorts);
        assertNotNull(portBindings);
        assertEquals(port.hostPort.toString(), portBindings[0].hostPort);
        assertEquals(port.protocol, portBindings[0].protocol);
    }

    @Test
    public void testFromPodContainerPortToPortBinding() {
        ContainerPort port = createContainerPort();

        PortBinding portBinding = KubernetesConverter.fromPodContainerPortToPortBinding(port);
        assertNotNull(portBinding);
        assertEquals(port.protocol, portBinding.protocol);
        assertEquals(port.hostPort.toString(), portBinding.hostPort);
    }

    @Test
    public void testFromPodContainerEnvVarToContainerDescriptionEnv() {
        EnvVar env = new EnvVar();
        env.value = "value";
        env.name = "name";

        List<EnvVar> envVars = Arrays.asList(env);

        String[] envs = KubernetesConverter.fromPodContainerEnvVarToContainerDescriptionEnv(envVars);
        assertNotNull(envs);
        assertEquals(String.format("%s=%s", env.name, env.value), envs[0]);
    }

    @Test
    public void testFromPodPrivilegedModeToContainerDescriptionPrivilegedMode() {

    }

    private Container createPodContainer() {
        Container podContainer = new Container();
        podContainer.name = CONTAINER_NAME;
        podContainer.image = CONTAINER_NAME;
        podContainer.command = Arrays.asList("app.sh");
        podContainer.args = Arrays.asList("start");
        podContainer.workingDir = CONTAINER_WORKING_DIR;

        ContainerPort port = new ContainerPort();
        port.containerPort = 80;
        port.protocol = "TCP";
        port.hostPort = 8080;
        podContainer.ports = Arrays.asList(port);

        EnvVar env = new EnvVar();
        env.name = "name";
        env.value = "value";
        podContainer.env = Arrays.asList(env);

        SecurityContext ctx = new SecurityContext();
        ctx.privileged = Boolean.TRUE;
        podContainer.securityContext = ctx;

        Probe probe = new Probe();
        probe.tcpSocket = new TCPSocketAction();
        probe.timeoutSeconds = 60;
        probe.failureThreshold = 3;
        podContainer.livenessProbe = probe;

        return podContainer;
    }

    private ContainerPort createContainerPort() {
        ContainerPort port = new ContainerPort();
        port.hostPort = 8080;
        port.protocol = "TCP";

        return port;
    }
}